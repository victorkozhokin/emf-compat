"""Native Aeronautics rope + real Sable carrying, controlled circular kinematic fixture.
The fixture pauses free physics; normal native rope packets/render interpolation stay enabled.
Capture at one frame per client tick, GUI hidden, healthy survival, full Test mods.
"""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
ROOT=Path('build/aerorope-review')
def cases():
    s=[{'log':'aerorope:begin'},{'releaseAll':True},{'closeScreen':True},{'cmd':'gamemode survival'},{'cmd':'clear @s'},
       {'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'effect give @s saturation 2 10 true'},
       {'wait':40},{'hideGui':True},{'camera':'back'},
       {'config':{'transport.grip':True,'transport.trace':True,'wallhand.trace':False,'footgrounding.trace':False}},
       {'cmd':'tp @s 2380.5 151 2090.5 0 0'},{'wait':20},
       {'craft':{'action':'create','pos':[2380,156,2090]}},{'wait':40},
       {'craft':{'action':'place','local':[.5,1,-1]}},{'wait':40}]
    s +=[{'craft':{'action':'block','local':[i,1,1],'block':'minecraft:air'}} for i in range(-2,3)]
    s +=[{'craft':{'action':'block','local':[x,1,z],'block':'minecraft:stone'}} for x in range(-3,4) for z in range(-3,4)]
    s +=[{'wait':30},{'craft':{'action':'aeroRopeMount'}},{'wait':20},{'craft':{'action':'aeroRope'}},{'wait':40},{'craft':{'action':'place','local':[.75,2,1.0]}},
         {'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'effect give @s saturation 2 10 true'},
         {'look':[0,0]},{'orbit':[-65,12,3.3]},{'wait':30},
         {'craft':{'action':'stream','delta':[0,0,.025],'ticks':60}},{'wait':40}]
    def circle(name,period,n,orbit=None):
        if orbit:s.append({'orbit':orbit})
        s.extend([{'craft':{'action':'circle','radius':4,'period':period,'ticks':n+period}},
                  {'wait':period},{'log':'aerorope:'+name}])
        for i in range(n):s.extend([{'wait':1},{'state':True},{'model':'player'},{'screenshot':f'{name}-{i:03}'}])
        s.append({'log':'aerorope:end'})
    circle('circle-slow',160,320)
    circle('circle-fast',100,200)
    s.extend([{'hold':'sneak'},{'wait':30}]);circle('circle-crouch',160,160)
    s.extend([{'releaseAll':True},{'wait':30}]);circle('circle-wide',160,160,[-65,28,14,2379.25,159,2094.5])
    return s+[{'releaseAll':True}]
if __name__=='__main__':
    ROOT.mkdir(parents=True,exist_ok=True)
    s=cases();(ROOT/'steps.json').write_text(json.dumps(s,indent=2))
    mctest.wait_ready('Test',60)
    boundary=next(i for i,x in enumerate(s) if x.get('log','').startswith('aerorope:circle'))
    r=mctest.run_steps('Test',s[:boundary],timeout=60)
    assert not [x for x in r['results'] if 'error'in x],r
    rest=mctest.run_steps('Test',s[boundary:],timeout=240)
    for row in rest['results']:row['step']+=boundary
    r['results']+=rest['results']
    (ROOT/'results.json').write_text(json.dumps(r))
    errors=[x for x in r['results'] if 'error'in x]
    print('steps',len(r['results']),'errors',errors)
    assert not errors
