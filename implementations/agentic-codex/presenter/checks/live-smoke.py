#!/usr/bin/env python3
"""Smoke checks only a supplied local demo app; resets its disposable application data."""
import sys
from pathlib import Path
from urllib.parse import urlparse
sys.path.insert(0,str(Path(__file__).resolve().parents[2]/'scripts'))
from process_check_import import api
url=sys.argv[1]
assert urlparse(url).hostname in ['127.0.0.1','localhost'], 'Local disposable demo only'
assert api(url,'/health')[0]==200
assert api(url,'/api/demo/reset','POST',{})[0]==200
assert [s['activeBookings'] for s in api(url,'/api/sessions')[1]['items']]==[22,19,40]
code,booking=api(url,'/api/bookings','POST',{'sessionId':2},'alice','container-book')
assert code==201
assert api(url,'/api/bookings','POST',{'sessionId':2},'bob','container-full')[0]==409
assert api(url,'/api/bookings','POST',{'sessionId':2},'alice','container-book')==(200,booking)
assert api(url,f"/api/bookings/{booking['id']}",'DELETE')[0]==204
assert api(url,f"/api/bookings/{booking['id']}",'DELETE')[0]==204
assert api(url,'/api/bookings','POST',{'sessionId':2},'alice','container-book')[1]['status']=='CANCELLED'
assert api(url,'/api/sessions')[1]['items'][1]['activeBookings']==19
api(url,'/api/demo/reset','POST',{})
assert api(url,'/api/bookings','POST',{'sessionId':1},'alice','container-notify')[0]==201
api(url,'/api/demo/mode','POST',{'mode':'lost-ack'});api(url,'/api/demo/dispatch','POST',{})
assert api(url,'/api/demo/activity')[1]['pending']==1
api(url,'/api/demo/mode','POST',{'mode':'normal'});api(url,'/api/demo/dispatch','POST',{})
data=api(url,'/api/demo/activity')[1]
assert len(data['attempts'])==2 and len(data['accepted'])==1 and data['pending']==0
assert len([e for e in data['events'] if e['kind']=='ACKNOWLEDGED'])==1
assert len(api(url,'/api/bookings')[1])==1
api(url,'/api/demo/reset','POST',{})
assert [s['activeBookings'] for s in api(url,'/api/sessions')[1]['items']]==[22,19,40]
print('PASS: live container health, seeds, booking/full/replay/cancel, two delivery attempts with one acceptance/acknowledgement; exact seeds restored')
