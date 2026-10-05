"""Real Sable deck + native player carrying; no animation overrides.
Motion is controlled once/server tick with Sable physics paused only in the test copy.
"""
import json
from pathlib import Path

def cases():
    s=[{'releaseAll':True},{'closeScreen':True},{'cmd':'gamemode survival'},
       {'cmd':'clear @s'},{'cmd':'effect give @s instant_health 1 10 true'},
       {'cmd':'effect give @s saturation 2 10 true'},{'wait':40},{'hideGui':True},
       {'camera':'back'},{'config':{'transport.grip':True,'transport.trace':True,
                                  'wallhand.trace':False,'footgrounding.trace':False}}]
    def take(name,n=30):
        s.append({'log':'transportqa:'+name})
        for i in range(n):
            s.extend([{'wait':1},{'state':True},{'model':'player'},{'screenshot':f'{name}-{i:02}'}])
        s.append({'log':'transportqa:end'})
    def stream(delta=None,accel=None,yaw=None,ticks=100):
        a={'action':'stream','ticks':ticks}
        if delta is not None:a['delta']=delta
        if accel is not None:a['acceleration']=accel
        if yaw is not None:a['yaw']=yaw
        s.append({'craft':a})
    def fixture(kind,x):
        s.extend([{'releaseAll':True},{'craft':{'action':'create','pos':[x,156,2110]}},{'wait':40}])
        if kind!='rail':
            s.extend([{'craft':{'action':'block','local':[i,1,1],'block':'minecraft:air'}} for i in range(-2,3)])
        if kind=='rope':
            s.append({'craft':{'action':'block','local':[0,5,1],'block':'minecraft:stone'}})
            s.extend([{'craft':{'action':'block','local':[0,y,1],'block':'supplementaries:rope'}} for y in range(4,0,-1)])
        if kind=='block':
            s.extend([{'craft':{'action':'block','local':[0,y,1],'block':'minecraft:stone'}} for y in (1,2)])
        s.extend([{'wait':40},{'craft':{'action':'place','local':[.5,1,1.1 if kind=='rail' else .8]}},
                  {'look':[0,0]},{'orbit':[35,14,3]},{'wait':30}])
    fixture('rail',2400)
    take('stationary',12)
    stream([0,0,.025],ticks=180);take('rail-steady',40)
    stream([0,0,.025],[0,0,.025],ticks=24);take('rail-accel',20)
    stream([0,0,.4],[0,0,-.02],ticks=20);take('rail-brake',20)
    s.append({'hold':'sneak'});s.append({'wait':30})
    stream([.025,0,0],[.025,0,0],ticks=24);take('rail-crouch',20)
    s.extend([{'releaseAll':True},{'wait':30}])
    stream(yaw=1.2,ticks=80);take('rail-turn',40)
    s.append({'config':{'transport.grip':False}});take('disabled',20)
    s.append({'config':{'transport.grip':True}});stream([0,0,.025],ticks=160);take('restored',30)
    s.append({'cmd':'item replace entity @s weapon.mainhand with minecraft:iron_sword'});take('main-occupied',30)
    s.append({'cmd':'item replace entity @s weapon.offhand with minecraft:shield'});take('both-occupied',20)
    s.extend([{'cmd':'clear @s'},{'wait':20}]);stream([0,0,.025],ticks=160);take('hands-free',30)
    s.append({'hold':'back'});take('walking-away',30);s.append({'releaseAll':True});take('released',20)
    fixture('block',2420);stream([0,0,.025]);take('plain-block',35)
    fixture('rope',2440);stream([0,0,.025],ticks=140);take('rope-grip',40)
    stream(yaw=1.2,ticks=60);take('rope-turn',30)
    s.extend([{'craft':{'action':'remove'}},{'cmd':'tp @s 2315.5 151 2071.5 0 0'},
              {'look':[0,0]},{'wait':30}]);take('ordinary-ground',15)
    return s+[{'releaseAll':True}]

if __name__=='__main__':
    p=Path('build/transport-review');p.mkdir(parents=True,exist_ok=True)
    (p/'steps.json').write_text(json.dumps(cases(),indent=2)+'\n')
