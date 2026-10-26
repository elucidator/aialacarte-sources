#!/usr/bin/env python3
"""Presenter acceptance checks; no hidden checks are exported to attendees."""
import argparse
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[2]/'scripts'))
from process_check_import import Apps, api


def generation(workspace, reference):
    with Apps(workspace,count=1) as apps:
        url=apps.urls[0]; api(url,'/api/demo/reset','POST',{})
        if not reference:
            result=api(url,'/api/sessions?availableOnly=true')[1]
            assert result['totalElements']==3 and len(result['items'])==3
            assert 'available-only' not in __import__('urllib.request',fromlist=['urlopen']).urlopen(url).read().decode()
            print('PASS: generation starting workspace has no availability filter or checkbox')
            return
        code,result=api(url,'/api/sessions?availableOnly=true&page=1&size=1')
        assert code==200 and result['totalElements']==2 and [s['id'] for s in result['items']]==[2]
        assert api(url,'/api/sessions?availableOnly=true&page=9&size=1')[1]['totalElements']==2
        for invalid in ['TRUE','yes','1','', 'False']:
            code,error=api(url,'/api/sessions?availableOnly='+invalid)
            assert code==400 and error['code']=='INVALID_INPUT',(invalid,code,error)
        assert api(url,'/api/sessions?availableOnly=false')[1]['totalElements']==3
        code,booking=api(url,'/api/bookings','POST',{'sessionId':2},'alice','filtered')
        assert code==201
        result=api(url,'/api/sessions?availableOnly=true')[1]
        assert result['totalElements']==1 and [s['id'] for s in result['items']]==[1]
        assert api(url,f"/api/bookings/{booking['id']}",'DELETE')[0]==204
        assert api(url,'/api/sessions?availableOnly=true')[1]['totalElements']==2
        api(url,'/api/demo/reset','POST',{})
        print('PASS: filtered count/order/pagination, invalid booleans, default false and cancellation release')


def review(workspace, reference):
    with Apps(workspace,count=1) as apps:
        url=apps.urls[0]; api(url,'/api/demo/reset','POST',{})
        code,booking=api(url,'/api/bookings','POST',{'sessionId':1},'alice','review')
        assert code==201
        api(url,'/api/demo/mode','POST',{'mode':'lost-ack'})
        assert api(url,'/api/demo/dispatch','POST',{})[0]==200
        data=api(url,'/api/demo/activity')[1]
        assert len(data['accepted'])==1 and len(data['attempts'])==1 and data['pending']==1
    # Persisted queue/gateway state must survive process restart.
    with Apps(workspace,count=1) as apps:
        url=apps.urls[0]
        api(url,'/api/demo/mode','POST',{'mode':'normal'})
        api(url,'/api/demo/dispatch','POST',{})
        data=api(url,'/api/demo/activity')[1]
        expected=1 if reference else 2
        assert len(data['accepted'])==expected and len(data['attempts'])==2 and data['pending']==0,data
        bookings=api(url,'/api/bookings')[1]
        assert len(bookings)==1 and bookings[0]['id']==booking['id']
        assert len([e for e in data['events'] if e['kind']=='ACKNOWLEDGED'])==1
        api(url,'/api/demo/reset','POST',{})
        print(f'PASS: {expected} gateway acceptance(s), two attempts, one acknowledgement and one booking across restart')

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('exercise',choices=['1','4']);p.add_argument('workspace');p.add_argument('--reference',action='store_true')
    a=p.parse_args(); (generation if a.exercise=='1' else review)(a.workspace,a.reference)
