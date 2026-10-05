"""Wheel continuity and exact throttle hover, with real mixed controls and healthy survival."""
import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
from cockpit_regression import fixture
from cockpit_mixed_controls import cases as mixed
ROOT=Path('build/cockpit-smooth-review')
def cases():
    s=fixture()+[{'cmd':'clear @s'},{'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'effect give @s saturation 1000 10 true'},{'click':'use'},{'wait':5}]
    def sample(name,n,drag=None):
        s.append({'log':'smoothcase:'+name})
        for i in range(n):
            if drag:s.append(drag)
            s.extend([{'wait':1},{'model':'player'},{'state':True},{'screenshot':f'{name}-{i:03}'}])
        s.append({'log':'smoothcase:end'})
    sample('wheel-near-throttles',32,{'steeringDrag':15})
    s.extend([{'look':[-90,45]},{'wait':12}]);sample('hover-right',20)
    s.extend([{'look':[180,22]},{'wait':16}]);sample('wheel-after-hover',32,{'steeringDrag':-15})
    s.extend([{'look':[90,45]},{'wait':12}]);sample('hover-left',20)
    s.extend([{'look':[180,22]},{'wait':16}]);sample('wheel-after-left',32,{'steeringDrag':15})
    return s+mixed()
if __name__=='__main__':
    ROOT.mkdir(parents=True,exist_ok=True)
    s=cases();(ROOT/'steps.json').write_text(json.dumps(s,indent=2))
    mctest.wait_ready('Test',60)
    r=mctest.run_steps('Test',s,timeout=300);(ROOT/'results.json').write_text(json.dumps(r))
    errors=[x for x in r['results']if 'error'in x];print('driver errors:',errors);assert not errors
