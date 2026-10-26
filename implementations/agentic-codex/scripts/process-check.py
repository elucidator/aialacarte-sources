#!/usr/bin/env python3
"""Independent HTTP checks using two actual JVM processes and one PostgreSQL database.
Only use a disposable database: reset clears its fictional application data.
"""
import concurrent.futures
import json
import os
from pathlib import Path
import socket
import subprocess
import tempfile
import threading
import time
import urllib.error
import urllib.request


def api(base, path, method='GET', body=None, who='alice', key=None):
    headers = {'Content-Type': 'application/json', 'X-Demo-Attendee': who}
    if key: headers['Idempotency-Key'] = key
    request = urllib.request.Request(base + path, data=None if body is None else json.dumps(body).encode(), headers=headers, method=method)
    try: response = urllib.request.urlopen(request, timeout=12)
    except urllib.error.HTTPError as error: response = error
    with response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else None


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0)); return sock.getsockname()[1]


class Apps:
    def __init__(self, root, count=2, profiles='local-demo', extra=None):
        self.root, self.count, self.profiles, self.extra = Path(root), count, profiles, extra or {}
        self.processes, self.logs, self.urls = [], [], []
    def __enter__(self):
        try:
            for _ in range(self.count):
                chosen = port()
                env = {**os.environ, 'PORT':str(chosen), 'SPRING_PROFILES_ACTIVE':self.profiles, **self.extra}
                log = tempfile.TemporaryFile(mode='w+b'); self.logs.append(log)
                process = subprocess.Popen(['java','-Xmx256m','-jar',str(self.root/'build/libs/conference-planner.jar')], env=env, stdout=log, stderr=log)
                self.processes.append(process); self.urls.append(f'http://127.0.0.1:{chosen}')
            deadline = time.monotonic() + 90
            for process, url, log in zip(self.processes, self.urls, self.logs):
                while time.monotonic() < deadline:
                    if process.poll() is not None:
                        log.seek(0); raise RuntimeError(log.read().decode())
                    try:
                        if api(url,'/health')[0] == 200: break
                    except (OSError, urllib.error.URLError): pass
                    threading.Event().wait(.2)  # bounded readiness polling, not race coordination
                else:
                    log.seek(0); raise TimeoutError('App readiness timed out:\n'+log.read().decode())
            return self
        except BaseException:
            self.__exit__(None,None,None); raise
    def __exit__(self,*_):
        for p in self.processes:
            if p.poll() is None: p.terminate()
        for p in self.processes:
            try: p.wait(timeout=10)
            except subprocess.TimeoutExpired: p.kill(); p.wait(timeout=5)
        for log in self.logs: log.close()


def parallel(actions):
    gate = threading.Barrier(len(actions), timeout=5)
    def run(action): gate.wait(); return action()
    with concurrent.futures.ThreadPoolExecutor(max_workers=len(actions)) as pool:
        futures = [pool.submit(run, action) for action in actions]
        return [f.result(timeout=20) for f in futures]


def main(root):
    with Apps(root) as apps:
        a,b = apps.urls
        for iteration in range(12):
            assert api(a,'/api/demo/reset','POST',{})[0] == 200
            results = parallel([lambda: api(a,'/api/bookings','POST',{'sessionId':2},'alice',f'a-{iteration}'),
                                lambda: api(b,'/api/bookings','POST',{'sessionId':2},'bob',f'b-{iteration}')])
            assert sorted(r[0] for r in results) == [201,409], results
            assert api(a,'/api/sessions')[1]['items'][1]['activeBookings'] == 20
        api(a,'/api/demo/reset','POST',{})
        replay = parallel([lambda url=url: api(url,'/api/bookings','POST',{'sessionId':1},'alice','same') for url in [a,b,a,b,a,b]])
        assert sorted(r[0] for r in replay) == [200]*5+[201], replay
        assert len({r[1]['id'] for r in replay}) == 1
        booking = replay[0][1]
        cancelled = parallel([lambda url=url: api(url,f"/api/bookings/{booking['id']}",'DELETE') for url in [a,b,a,b]])
        assert all(r[0] == 204 for r in cancelled)
        assert api(a,'/api/sessions')[1]['items'][0]['activeBookings'] == 22
        assert api(b,'/api/bookings','POST',{'sessionId':1},'alice','same')[1]['status'] == 'CANCELLED'
        api(a,'/api/demo/reset','POST',{})
        api(a,'/api/bookings','POST',{'sessionId':1},'alice','notify')
        api(a,'/api/demo/mode','POST',{'mode':'lost-ack'})
        api(a,'/api/demo/dispatch','POST',{})
        activity = api(b,'/api/demo/activity')[1]
        assert len(activity['accepted']) == 1 and activity['pending'] == 1
        # Kill only the JVMs started by this context; PostgreSQL and all other containers stay intact.
    with Apps(root) as restarted:
        a,b = restarted.urls
        api(a,'/api/demo/mode','POST',{'mode':'normal'})
        results = parallel([lambda url=url: api(url,'/api/demo/dispatch','POST',{}) for url in [a,b]])
        assert all(r[0] == 200 for r in results)
        activity = api(a,'/api/demo/activity')[1]
        assert len(activity['accepted']) == 1 and len(activity['attempts']) == 2 and activity['pending'] == 0
        api(a,'/api/demo/reset','POST',{})
    with Apps(root, count=1, profiles='') as normal:
        url = normal.urls[0]
        for path, method in [('activity','GET'),('reset','POST'),('mode','POST'),('dispatch','POST')]:
            assert api(url,'/api/demo/'+path,method,None if method=='GET' else {})[0] == 404
    print('PASS: 12 two-process last-place races; concurrent replay and cancellation; restart-safe delivery; demo profile isolation')

if __name__ == '__main__':
    import sys
    main(sys.argv[1] if len(sys.argv)>1 else Path(__file__).resolve().parents[1])
