"""Check clearance relief, fixed soles, stair lag and survival vitals from real captures."""
import json, re, sys
from pathlib import Path

def traces(path):
    cases={};name=None
    for line in Path(path).read_text().splitlines():
        if '[mctest] stairs-' in line and ' START' in line:
            name=line.split('[mctest] ')[1].split(' START')[0];cases[name]=[]
        if '[mctest] stairs-' in line and ' END' in line:name=None
        if name and '[FootTrace]' in line:
            cases[name].append({k:float(v) for k,v in re.findall(r'(\w+)=([-+\d.E]+)',line)})
    return cases

def verify(directory):
    p=Path(directory);r=json.loads((p/'after.json').read_text())['results']
    assert not [v for v in r if 'error' in v]
    states=[v['state'] for v in r if 'state' in v]
    assert states and all(v['health']==20 and v['food']==20 and v['hurtTime']==0 and v['gameMode']=='survival' for v in states)
    pelvis=[]
    log=(p/'after.log').read_text()
    start=log.rfind('[mctest] clearancecase:wall-right-False')
    assert start>=0,'Missing final clearance take'
    for line in log[start:].splitlines():
        if '[PelvisTrace]' in line:
            pelvis.append({k:float(v) for k,v in re.findall(r'(\w+)=([-+\d.E]+)',line)})
    assert pelvis and max(v['crouchRelief'] for v in pelvis)>.5
    assert max(v['crouchRelief'] for v in pelvis)<=2.0001
    assert max(v['soleDrift'] for v in pelvis)<.001
    assert max(v['attachmentGap'] for v in pelvis)<.001
    after=traces(p/'after.log');before=traces(p/'baseline.log')
    # Both hip probes already reached the new level while the old plant still
    # requested a deep drop: this is the reproduced ascent defect, not a frame-time comparison.
    def supported(rows):
        return [v for v in rows if 150.5<=v['y']<=152 and 304.7<=v['x']<=307.1 and max(v['R'],v['L'])<.75]
    comparisons={}
    for name in ['stairs-up','stairs-back']:
        a=supported(after[name]);b=supported(before[name]);assert a and b
        old=max(v['tl'] for v in b);new=max(v['tl'] for v in a)
        assert old>5,(name,'Baseline defect not reproduced',old)
        assert new<2.26,(name,'Old plant still sinks supported torso',new)
        comparisons[name]={'beforeTargetDropPixels':old,'afterTargetDropPixels':new,
                           'beforeDrawnDropPixels':max(v['low'] for v in b),'afterDrawnDropPixels':max(v['low'] for v in a)}
    for name in ['stairs-down','stairs-sprint','stairs-crouch']:assert after[name]
    return {'steps':len(r),'vitalsSamples':len(states),'survivalHealthy':True,
            'maximumSoleDriftPixels':max(v['soleDrift'] for v in pelvis),
            'maximumTorsoAttachmentGapPixels':max(v['attachmentGap'] for v in pelvis),
            'maximumCrouchReliefPixels':max(v['crouchRelief'] for v in pelvis),'ascent':comparisons}

if __name__=='__main__':print(json.dumps(verify(sys.argv[1]),indent=2))
