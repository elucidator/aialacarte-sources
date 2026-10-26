#!/usr/bin/env python3
"""Verify the exercise's unhealthy state and scoped recovery in a newly created Compose project."""
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import time
import uuid
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
from process_check_import import api
from process_check_import import module as process_module

ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('exporter',ROOT/'presenter/export.py');exporter=importlib.util.module_from_spec(spec);spec.loader.exec_module(exporter)
project='conference-ex5-check-'+uuid.uuid4().hex[:10]
app_port,db_port=process_module.port(),process_module.port()
env={**os.environ,'APP_PORT':str(app_port),'DB_PORT':str(db_port)}


def command(args,cwd=None,check=True):
    result=subprocess.run(args,cwd=cwd,env=env,stdout=subprocess.PIPE,stderr=subprocess.STDOUT,text=True,timeout=240)
    if check and result.returncode:raise RuntimeError(result.stdout)
    return result


def running():return set(command(['docker','ps','--format','{{.ID}}']).stdout.split())


def wait_state(root,state,seconds=150):
    deadline=time.monotonic()+seconds
    while time.monotonic()<deadline:
        container=command(['docker','compose','-p',project,'ps','-q','app'],root).stdout.strip()
        if container:
            result=command(['docker','inspect','--format','{{.State.Health.Status}}',container]).stdout.strip()
            if result==state:return container
        time.sleep(.5)  # bounded container health polling
    raise TimeoutError(f'Container did not become {state}')

before=running()
with tempfile.TemporaryDirectory(prefix='conference-compose-check-') as directory:
    root=Path(directory)/'exercise';exporter.export(5,root)
    try:
        print(f'Creating new project {project}; ports app={app_port}, db={db_port}',flush=True)
        result=command(['docker','compose','-p',project,'up','-d','--build'],root)
        (ROOT/'verification-output/compose-build.log').write_text(result.stdout)
        container=wait_state(root,'unhealthy')
        url=f'http://127.0.0.1:{app_port}'
        assert api(url,'/health')[0]==200
        assert api(url,'/status')[0]==404
        exec_good=command(['docker','exec',container,'curl','--fail','--silent','http://localhost:8080/health'])
        assert 'ready' in exec_good.stdout
        exec_bad=command(['docker','exec',container,'curl','--fail','--silent','http://localhost:8080/status'],check=False)
        assert exec_bad.returncode!=0
        print('PASS: real process ready, installed curl works, configured probe fails, Compose unhealthy',flush=True)
        command(['git','apply','--check',str(ROOT/'presenter/reference-patches/exercise-5.patch')],root)
        command(['git','apply',str(ROOT/'presenter/reference-patches/exercise-5.patch')],root)
        command(['docker','compose','-p',project,'up','-d','--no-build','--force-recreate','app'],root)
        wait_state(root,'healthy')
        assert api(url,'/health')[0]==200
        code,booking=api(url,'/api/bookings','POST',{'sessionId':2},'alice','recovered')
        assert code==201
        assert api(url,f"/api/bookings/{booking['id']}",'DELETE')[0]==204
        print('PASS: patched Compose recreates only its app and becomes healthy; booking/cancellation work',flush=True)
    finally:
        command(['docker','compose','-p',project,'down','-v'],root)
        assert before <= running(), 'A pre-existing running container was changed or stopped.'
        assert not command(['docker','ps','-a','--filter','label=com.docker.compose.project='+project,'-q']).stdout.strip()
        assert not command(['docker','volume','ls','--filter','label=com.docker.compose.project='+project,'-q']).stdout.strip()
print('PASS: only newly created exercise resources removed; pre-existing running containers preserved')
