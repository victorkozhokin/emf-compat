"""Verify real seated handovers, actual control motion and sequential rim ownership."""
import json
import re
import sys
from pathlib import Path


def verify(directory):
    directory=Path(directory)
    cases={};name=None
    for line in (directory/'final.log').read_text().splitlines():
        if 'cockpitcase:' in line:
            name=line.split('cockpitcase:')[1];cases[name]=[]
        if 'cockpitend' in line:name=None
        if name and '[CockpitTrace]' in line:
            values={k:float(v) for k,v in re.findall(r'(\w+)=([-+\d.E]+)',line)}
            values['held']='throttleHeld=true' in line
            values['shown']='shown=true' in line
            cases[name].append(values)
    assert len(cases)==12,cases.keys()
    for name,frames in cases.items():
        assert frames,name
        for v in frames:
            assert v['rightMix']==0 or v['leftMix']==0,(name,'Both hands leave the rim',v)
            if v['shown']:
                assert .499 <= v['rimRight'] <= .55 and .499 <= v['rimLeft'] <= .55,(name,'Grip left rim for hub',v)
        if name!='dismount':
            assert all(v['shown'] for v in frames),(name,'Pose dropped')
            assert min(min(v['rightWeight'],v['leftWeight']) for v in frames)>.99,(name,'Ownership dropped')
    for name,hand in [('right',0),('right-drag',0),('right-look-away',0),('left',1),('left-drag',1)]:
        assert all(v['request']==hand for v in cases[name] if v['held']),(name,'Wrong held hand')
        assert all(v['request']==-1 for v in cases[name] if not v['held']),(name,'Camera hover freed a hand')
        assert any(v['held'] for v in cases[name]),(name,'Only hover tested')
        assert cases[name][-1]['rightMix' if hand==0 else 'leftMix']==1,name
    for name in ['return-right','return-both','remove-throttle']:
        assert cases[name][-1]['rightMix']==cases[name][-1]['leftMix']==0,(name,'Hand not returned')
    direct=cases['direct-switch']
    assert any(v['leftMix']>0 for v in direct) and any(v['rightMix']>0 for v in direct),'Missing direct transfer'
    assert not cases['dismount'][-1]['shown'],'Seated pose remains after dismount'
    response=json.loads((directory/'final.json').read_text())
    assert 'error' not in response,response
    signals=[];angles=[];models=0
    for step in response['results']:
        assert 'error' not in step,step
        if 'state' in step:
            t=step['state'].get('target',{})
            if 'getState' in t:signals.append(t['getState'])
            if 'getRenderAngle' in t:angles.append(t['getRenderAngle'])
        if 'model' in step:
            models+=1;p=step['model']['parts']
            assert p['body']['pos'][1]<(p['right_leg']['pos'][1]+p['left_leg']['pos'][1])/2,'Body below hips'
    assert min(signals)==0 and max(signals)==15,signals
    assert max(angles)-min(angles)>.5,angles
    return {'runtimeSteps':len(response['results']),'modelSnapshots':models,'cases':len(cases),
            'throttleSignals':[min(signals),max(signals)],'simultaneousRimReleases':0}


if __name__=='__main__':
    print(json.dumps(verify(sys.argv[1]),indent=2))
