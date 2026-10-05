"""Validate observed clicks, bounded support step, release and captured torso bounds."""
import json
import math
import re
import sys
from pathlib import Path


def verify(directory):
    directory=Path(directory)
    cases={}
    name=None
    releasing=False
    for line in (directory/'final.log').read_text().splitlines():
        if 'levercase:' in line:
            name=line.split('levercase:')[1]
            cases[name]={'active':[],'release':[]}
            releasing=False
        if 'leverend:' in line:releasing=True
        if 'leverreleased:' in line:name=None
        if name and '[LeverPoseTrace]' in line:
            numbers={k:float(v) for k,v in re.findall(r'(\w+)=([-+\d.E]+)',line)}
            numbers['pressing']='pressing=true' in line
            cases[name]['release' if releasing else 'active'].append(numbers)
    assert len(cases)==5,cases.keys()
    summary={}
    for name,case in cases.items():
        active=case['active']
        assert any(s['pressing'] for s in active),(name,'No actual toggle')
        offsets=[math.hypot(s['footX'],s['footZ']) for s in active]
        assert max(offsets)>.85,(name,'Step not observed')
        assert max(offsets)<=.901,(name,'Step too large')
        assert any(0<s['progress']<1 for s in active),(name,'No intermediate step')
        assert all(math.hypot(s['loadX'],s['loadZ'])<=.551 for s in active),name
        release=case['release'][-1]
        assert math.hypot(release['footX'],release['footZ'])<.001,(name,release)
        assert math.hypot(release['loadX'],release['loadZ'])<.001,(name,release)
        summary[name]={'maximumStepPixels':max(offsets),'releaseFootPixels':math.hypot(release['footX'],release['footZ'])}
    response=json.loads((directory/'final.json').read_text())
    assert 'error' not in response,response
    for step in response['results']:
        assert 'error' not in step,step
        if 'model' in step:
            parts=step['model']['parts']
            hips=(parts['right_leg']['pos'][1]+parts['left_leg']['pos'][1])/2
            assert parts['body']['pos'][1]<hips,'Torso below hips'
    fence=directory/'fence.log'
    if fence.exists():
        lines=fence.read_text().split('levercase:fence')[-1].split('leverend:fence')[0]
        trace=[line for line in lines.splitlines() if '[LeverPoseTrace]' in line]
        assert any('pressing=true' in line for line in trace),'Fence click not observed'
        for line in trace:
            values={k:float(v) for k,v in re.findall(r'(\w+)=([-+\d.E]+)',line)}
            assert math.hypot(values['footX'],values['footZ'])<.001,'Unsafe fence step'
        response=json.loads((directory/'fence.json').read_text())
        assert not any('error' in step for step in response['results']),response
        states=[step['state'] for step in response['results'] if 'state' in step]
        assert states[-1]['onGround'] and states[-1]['pos'][1]==151.5,states[-1]
        summary['fence']={'stepRejected':True,'grounded':True}
    return summary


if __name__=='__main__':
    print(json.dumps(verify(sys.argv[1]),indent=2))
