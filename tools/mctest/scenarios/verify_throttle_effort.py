"""Check real control travel, ownership, contact-gated regrips and survival vitals."""
import json
import math
import re
import sys
from pathlib import Path


def verify(directory):
    directory=Path(directory)
    commands=json.loads((directory/'steps.json').read_text())
    response=json.loads((directory/'final.json').read_text())
    assert len(commands)==len(response['results'])
    name=None;cases={}
    for command,result in zip(commands,response['results']):
        assert 'error' not in result,result
        marker=command.get('log','')
        if marker.startswith('effortcase:'):
            name=marker.split(':')[1];cases[name]={'models':[],'probes':[]}
        if marker.startswith('effortdone:') or marker=='effortend':name=None
        if 'state' in result:
            s=result['state'];assert s['gameMode']=='survival' and s['health']==20 and s['food']==20 and s['hurtTime']==0,s
        if name and 'model' in result:cases[name]['models'].append(result['model']['parts'])
        if name and 'throttle' in result:cases[name]['probes'].append(result['throttle'])
    traces={};name=None
    for line in (directory/'final.log').read_text().splitlines():
        if 'effortcase:' in line:name=line.split('effortcase:')[1];traces[name]=[]
        if 'effortdone:' in line or 'effortend' in line:name=None
        if name and '[ThrottleEffort]' in line:
            traces[name].append({k:float(x) for k,x in re.findall(r'(\w+)=([-+\d.E]+)',line)})
    for name in ['standing-idle','crouch-idle']:
        assert max(v['load'] for v in traces[name])<.01,(name,'Invented idle resistance')
    for name in ['standing-push','standing-pull','crouch-push','crouch-pull','side-push','side-pull']:
        probes=cases[name]['probes'];assert all(p['held'] for p in probes),(name,'Lost actual hold')
        assert min(p['signal'] for p in probes)<=1 and max(p['signal'] for p in probes)>=14,(name,'Full stroke not covered')
        assert max(v['load'] for v in traces[name])>.9,(name,'No effort')
        assert max(v['primaryWeight'] for v in traces[name])>.99,(name,'Primary hand not acquired')
    assert traces['standing-stop'][-1]['load']<.01,'Idle effort did not settle'
    assert max(abs(v['recoil']) for v in traces['reverse-pull'])>.1,'Reversal recoil missing'
    regrips=[v for name,rows in traces.items() for v in rows if v['regrip']>.05]
    assert regrips,'No regrip tested'
    assert all(v['helperWeight']>.95 and v['helperGap']<.08 for v in regrips),'Primary hand released without planted helper'
    assert max(v['regrip'] for v in traces['helper-busy'])==0
    assert max(v['helperWeight'] for v in traces['helper-busy'])<.01,'Occupied hand stolen'
    assert not traces['disabled'],'Disabled provider still applied a pose'
    for name in ['release','removed']:
        assert traces[name][-1]['primaryWeight']<.02 and traces[name][-1]['helperWeight']<.02,(name,'Ownership did not release')
    assert all(v['progress']==1 for v in traces['narrow']),'Setup step escaped narrow support'
    changes=[]
    for name in ['standing-push','standing-pull','crouch-push','crouch-pull']:
        models=cases[name]['models']
        for a,b in zip(models,models[1:]):
            for part in ['body','right_leg','left_leg']:
                delta=math.dist(a[part]['pos'],b[part]['pos']);changes.append(delta)
                assert delta<2.5,(name,part,'Abrupt pivot motion',delta)
    return {'runtimeSteps':len(commands),'capturedCases':sum(bool(c['models']) for c in cases.values()),
            'survival':True,'health':20,'food':20,'hurtTime':0,'fullStrokeStandingAndCrouched':True,
            'contactGatedRegrips':len(regrips),'maximumRegripHelperGapBlocks':max(v['helperGap']for v in regrips),
            'maximumSampledPivotChangePixels':max(changes),'occupiedHelperProtected':True,'narrowSupportProtected':True}


if __name__=='__main__':print(json.dumps(verify(sys.argv[1]),indent=2))
