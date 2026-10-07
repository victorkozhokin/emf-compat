"""Counts the jumps of a hand's aim, frame to frame, in each walk of scenarios/plants_walk.py:
plant_jumps.py <latest.log>. A jump is more than 6 degrees in one frame; "taken up" is how often a hand started a reach."""
import re,sys,math
L=sys.argv[1]
case=None; prev={}; out={}
for line in open(L,errors='replace'):
    m=re.search(r'\[mctest\] walk (\S+)',line)
    if m: case=m.group(1); prev={}; out[case]={'n':0,'R':[], 'L':[], 'on':{'R':0,'L':0},'has':{'R':0,'L':0}}; continue
    if case is None or case=='end': continue
    m=re.search(r'\[PlantPoint\] at=\(([\d.-]+) ([\d.-]+)\) R=(.*?) L=(.*?) aimR=(.*?) aimL=(.*)$',line)
    if not m: continue
    pz=float(m.group(2)); o=out[case]; o['n']+=1
    for h,aim in (('R',m.group(5).strip()),('L',m.group(6).strip())):
        if aim=='-':
            prev[h]=None; continue
        o['has'][h]+=1
        a=tuple(map(float,aim.split()))
        if prev.get(h) is None: o['on'][h]+=1
        else:
            da=math.degrees(max(abs(a[0]-prev[h][0]),abs(a[1]-prev[h][1])))
            if da>6: o[h].append((round(pz,2),round(da,1)))
        prev[h]=a
for c,o in out.items():
    if c=='end': continue
    print('%-13s frames %3d | with a reach R %3d L %3d | taken up R %d L %d | aim jumps >6deg/frame R %d L %d %s'%(c,o['n'],o['has']['R'],o['has']['L'],o['on']['R'],o['on']['L'],len(o['R']),len(o['L']),(o['R']+o['L'])[:5]))
