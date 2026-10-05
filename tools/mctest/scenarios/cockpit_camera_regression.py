"""Isolate camera input, observer orbit, and steering from native seated pose changes."""
import sys,json,math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
from cockpit_regression import fixture

def cases():
    s=fixture()+[{'cmd':'clear @s'},{'cmd':'effect give @s instant_health 1 10 true'},
                 {'cmd':'effect give @s saturation 1000 10 true'},{'click':'use'},{'wait':10}]
    for name in ['idle','camera','orbit','steering','steering-camera']:
        s += [{'cameraLook':[180,22]},{'orbit':[45,20,3,True]},{'wait':20},{'log':'seatcase:'+name}]
        for i in range(120):
            if name in ['camera','steering-camera']:s.append({'cameraLook':[180+55*math.sin(i*math.pi/60),22+10*math.sin(i*math.pi/30)]})
            if name=='orbit':s.append({'orbit':[225+50*math.sin(i*math.pi/60),20,3]})
            if name.startswith('steering'):s.append({'steeringDrag':8 if i<60 else -8})
            s += [{'wait':1},{'model':'player'},{'state':True},{'screenshot':f'seat-{name}-{i:03}'}]
        s.append({'log':'seatcase:end'})
    return s
if __name__=='__main__':
    root=Path(sys.argv[1]);root.mkdir(parents=True,exist_ok=True)
    s=cases();(root/'steps.json').write_text(json.dumps(s,indent=2))
    mctest.wait_ready('Test',60)
    r=mctest.run_steps('Test',s,timeout=200);(root/'results.json').write_text(json.dumps(r))
    print('errors',[x for x in r['results']if 'error'in x])
