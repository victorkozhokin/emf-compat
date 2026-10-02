"""Verify long captures of upper-body clearance, rather than a short settle only."""
import json,math,re,sys
from pathlib import Path

def verify(directory):
    p=Path(directory);steps=json.loads((p/'steps.json').read_text())
    rows=json.loads((p/'verified.json').read_text())['results']
    assert len(steps)==len(rows)
    poses={};name=None;states=[]
    for cmd,result in zip(steps,rows):
        assert 'error' not in result,result
        if 'log' in cmd:name=cmd['log'].split(':',1)[1];poses[name]=[]
        if 'state' in result:states.append(result['state'])
        if 'model' in result:poses[name].append(result['model']['parts'])
    assert len(states)==540
    assert all(v['health']==20 and v['food']==20 and v['hurtTime']==0 and v['gameMode']=='survival' for v in states)
    metrics={}
    for name,frames in poses.items():
        changes=[math.dist(a['body']['pos'],b['body']['pos']) for a,b in zip(frames,frames[1:])]
        metrics[name]={'maximumPivotChangePerTickPixels':max(changes),
                       'meanPivotChangePerTickPixels':sum(changes)/len(changes)}
        if name.endswith('-walk'):
            # Crouch entry/stride has more authored translation than standing.
            limit=.5 if name.startswith('standing-') else .9
            assert max(changes)<limit,(name,'upper body still snaps between capture ticks',max(changes))
    traces={};name=None
    for line in (p/'verified.log').read_text().splitlines():
        if 'wallvideo:' in line:name=line.split('wallvideo:')[1];traces[name]=[]
        if name and '[PelvisTrace]' in line:
            traces[name].append({k:float(v) for k,v in re.findall(r'(\w+)=([-+\d.E]+)',line)})
    for name,values in traces.items():
        assert values and max(v['soleDrift'] for v in values)<.001
        assert max(v['attachmentGap'] for v in values)<.001
        if name.startswith('crouching-') and name.endswith('-walk'):
            active=[v['crouchRelief'] for v in values if v['retreat']>3.4]
            assert active and min(active)>3.15,(name,'stride cancels crouch clearance',min(active))
    return {'commands':len(steps),'cases':len(poses),'healthySurvivalSamples':len(states),
            'walkingClearanceMaintained':True,'metrics':metrics}

if __name__=='__main__':print(json.dumps(verify(sys.argv[1]),indent=2))
