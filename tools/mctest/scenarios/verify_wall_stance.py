"""Verify real pose captures and sequential foot placement at Atlas walls."""
import json,re,math,sys
from pathlib import Path

def verify(directory):
    p=Path(directory);commands=json.loads((p/'steps.json').read_text());results=json.loads((p/'final.json').read_text())['results']
    assert len(commands)==len(results);name=None;models={};states=[]
    for cmd,result in zip(commands,results):
        assert 'error' not in result,result
        if cmd.get('log','').startswith('wallstancecase:'):
            name=cmd['log'].split(':')[1];models[name]=[]
        if 'state' in result:states.append(result['state'])
        if 'model' in result:models[name].append(result['model']['parts'])
    assert all(s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['gameMode']=='survival' for s in states)
    traces={};name=None
    for line in (p/'final.log').read_text().splitlines():
        if 'wallstancecase:' in line:name=line.split('wallstancecase:')[1];traces[name]=[]
        if name and '[WallStance]' in line:
            values={k:float(x) for k,x in re.findall(r'(\w+)=([-+\d.E]+)',line)}
            values['moving']='moving=true' in line
            for side in ['right','left']:
                hit=re.search(side+r'=\(\s*([-+\d.E]+)\s+([-+\d.E]+)\s+([-+\d.E]+)\)',line)
                assert hit,line;values[side]=tuple(float(x) for x in hit.groups())
            traces[name].append(values)
    rows=[v for r in traces.values() for v in r]
    assert rows and max(math.hypot(v['right'][0],v['right'][2]) for v in rows)>.3
    assert max(math.hypot(v['left'][0],v['left'][2]) for v in rows)>.3
    for v in rows:
        assert abs(v['right'][1]*v['left'][1])<1e-6,'Both feet lifted together'
        for side in ['right','left']:assert math.hypot(v[side][0],v[side][2])<=1.6001
    # An aborted unsafe step can retain its last progress; step=-1 means idle.
    settled=traces['narrow-forward-settle'][-4:]
    assert all(v['step']==-1 for v in settled),'Stationary feet keep stepping'
    for side in ['right','left']:
        assert all(math.dist(v[side],settled[0][side])<.001 for v in settled),'Stationary foot target drifts'
    for case in ['clear','disabled']:
        a=traces[case];assert a and abs(a[-1]['turn'])<.001
        assert all(math.dist(a[-1][side],(0,0,0))<.05 for side in ['right','left'])
    pelvis=[{k:float(x) for k,x in re.findall(r'(\w+)=([-+\d.E]+)',line)} for line in (p/'final.log').read_text().splitlines() if '[PelvisTrace]' in line]
    assert pelvis
    assert max(v['retreat'] for v in pelvis)>2
    assert all(-.001<=v['retreat']<=3.501 and -.001<=v['crouchRelief']<=3.251 and v['soleDrift']<.001 for v in pelvis)
    changes=[]
    for case,frames in models.items():
        if 'settle' not in case and 'walk' not in case and 'back' not in case:continue
        for a,b in zip(frames,frames[1:]):
            for part in ['body','right_leg','left_leg']:
                delta=math.dist(a[part]['pos'],b[part]['pos']);changes.append(delta)
                assert delta<3.5,(case,part,'Abrupt pivot displacement',delta)
    return {'commands':len(commands),'poseCases':len(models),'survivalSamples':len(states),
            'maximumRetreatPixels':max(v['retreat'] for v in pelvis),'maximumLiftPixels':max(v['crouchRelief'] for v in pelvis),'healthySurvival':True,'oneSteppingFootAtATime':True,'maximumPlacementPixels':max(math.hypot(v[side][0],v[side][2]) for v in rows for side in ['right','left']),
            'maximumSampledPivotChangePixels':max(changes),'clearAndDisabledReturnToNeutral':True}

if __name__=='__main__':print(json.dumps(verify(sys.argv[1]),indent=2))
