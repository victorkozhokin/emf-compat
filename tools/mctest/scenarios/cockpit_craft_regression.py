"""Live native propellers and Sable physics; vertical support is test-only.
Uses native seat/block interactions and records same-render cockpit contact snapshots.
"""
import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import mctest
from interaction_regression import scene
ROOT=Path('build/cockpit-craft-review')
steps=[];rows=[]
def run(batch):
    offset=len(steps);r=mctest.run_steps('Test',batch,timeout=120)
    for row in r['results']:row['step']+=offset
    steps.extend(batch);rows.extend(r['results'])
    ROOT.mkdir(parents=True,exist_ok=True);(ROOT/'steps.json').write_text(json.dumps(steps,indent=2));(ROOT/'results.json').write_text(json.dumps({'results':rows},indent=2))
    assert not any('error'in x for x in r['results']),r
    return r['results']
def capture(name,n=40,drag=None,key=None):
    s=[{'log':'livecockpit:'+name}]
    if key is not None:s.append({'typewriterKey':[key,1]})
    for i in range(n):
        if drag and i<n-8:s.append(drag)
        s +=[{'wait':1},{'state':True},{'typewriterState':True},{'throttle':True},{'model':'player'},{'craft':{'action':'inspect'}},{'screenshot':name+f'-{i:03}'}]
    if key is not None:s.append({'typewriterKey':[key,0]})
    s.append({'log':'livecockpit:end'});run(s)
def fixture(side):
    initial=run([{'craft':{'action':'create','pos':[2400 if side=='right'else 2440,156,2110]}},{'craft':{'action':'place','local':[.5,1,2.5]}},{'wait':60},{'craft':{'action':'inspect'}}])
    plot=next(r['craft']['plot']for r in initial if 'craft'in r);x,y,z=map(int,plot.split(', '));tx=x+(1 if side=='right'else -1);lx=x-(1 if side=='right'else -1)
    s=[]
    for xx in range(-2,3):s.append({'cmd':f'setblock {x+xx} 1 {z+1} air'})
    blocks=[(x,1,z,'create:red_seat'),(x,2,z-2,'create:brass_casing'),(x,2,z-1,'simulated:steering_wheel[facing=south,on_floor=false]'),
            (tx,1,z,'simulated:linked_typewriter[facing='+('west'if side=='right'else 'east')+']'),(lx,1,z,'simulated:throttle_lever[facing=north,face=floor]')]
    for xx in [-2,2]:blocks +=[(x+xx,1,z+3,'create:creative_motor[facing=north]'),(x+xx,1,z+2,'aeronautics:wooden_propeller[facing=north]')]
    for a,b,c,block in blocks:s.append({'cmd':f'setblock {a} {b} {c} {block}'})
    s +=[{'wait':40},{'craft':{'action':'place','local':[.5,1,1.2]}},{'look':[180,60]},{'wait':20},{'click':'use'},{'wait':20},{'look':[180,22]},{'orbit':[45,20,3]},{'wait':20},
          {'typewriterBind':[tx,1,z]},{'craft':{'action':'engine','side':'right','rpm':0}},{'craft':{'action':'engine','side':'left','rpm':0}},{'wait':20}]
    run(s);return [tx,1,z],[lx,1,z]
def engines(left,right):run([{'craft':{'action':'engine','side':'left','rpm':left}},{'craft':{'action':'engine','side':'right','rpm':right}}])
if __name__=='__main__':
    mctest.wait_ready('Test',60);run(scene()+[{'releaseAll':True},{'hideGui':True},{'camera':'back'}])
    for side in ['right','left']:
        machine,lever=fixture(side);capture(side+'-stationary',24)
        run([{'click':'use'},{'wait':5},{'craft':{'action':'physical'}}]);engines(64,64);capture(side+'-accelerate',50,{'steeringDrag':15})
        run([{'typewriterActivate':machine},{'wait':20}]);capture(side+'-typing-while-moving',32,{'steeringDrag':10},65)
        engines(-64,-64);capture(side+'-brake',50,{'steeringDrag':-10},32)
        run([{'typewriterKey':[256,1]},{'steeringRelease':True},{'throttleHold':lever}]);capture(side+'-throttle-moving',40,{'throttleDrag':-10})
        run([{'steeringRelease':True},{'look':[180,22]},{'wait':20},{'click':'use'}]);engines(-64,64);capture(side+'-turn',70,{'steeringDrag':15})
        engines(0,0);run([{'craft':{'action':'incline','pitch':8,'roll':6}},{'wait':15}]);capture(side+'-inclined-deck',40)
        run([{'look':[90,15]}]);capture(side+'-camera-away',24)
        run([{'steeringRelease':True},{'cmd':'ride @s dismount'},{'craft':{'action':'remove'}},{'cmd':'tp @s 2315.5 151 2071.5'},{'wait':20}])
    print('completed',len(rows),'steps')
