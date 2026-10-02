"""Real Throttle travel and support in survival; never simulate a failed control action."""
import json
from pathlib import Path


def shots(name,count=18,drag=None):
    s=[{'log':'effortcase:'+name}]
    for i in range(count):
        if drag is not None:s += [{'throttleDrag':drag}]
        s += [{'wait':2},{'throttle':True},{'model':'player'},{'screenshot':f'effort-{name}-{i:02}'}]
    return s+[{'state':True},{'log':'effortdone:'+name}]


def setup(name,crouch=False,item='air',facing='north',side=False):
    s=[{'releaseAll':True},{'steeringRelease':True},{'wait':15},
       {'cmd':'fill 482 150 6 484 153 8 air'},
       {'cmd':f'setblock 483 150 7 simulated:throttle_lever[facing={facing},face=floor]'},
       {'cmd':'item replace entity @s weapon.mainhand with air'},
       {'cmd':f'item replace entity @s weapon.offhand with {item}'},
       {'cmd':f'tp @s {484.115 if side else 483.5} 150 {7.5 if side else 8.115} {90 if side else 180} 20'},
       {'look':[90 if side else 180,20]},{'wait':20},{'log':'effortcase:'+name},
       {'throttleHold':[483,150,7]}]
    if crouch:s += [{'hold':'sneak'}]
    return s+[{'wait':18}]


def cases():
    s=[{'closeScreen':True},{'releaseAll':True},{'steeringRelease':True},{'cmd':'ride @s dismount'},
       {'cmd':'gamemode survival'},{'cmd':'fill 477 149 3 490 149 12 smooth_stone'},
       {'cmd':'fill 477 150 3 490 155 12 air'},
       {'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'effect give @s saturation 2 10 true'},
       {'config':{'buttonpress.heavy_throttle':True,'footgrounding.trace':True,'lookat.enabled':False,
                  'parcool.enabled':False,'buttonpress.enabled':True,'blockuse.enabled':True}},
       {'hideGui':True},{'hideScreen':True},{'camera':'back'},{'wait':40},{'orbit':[55,10,2.5]}]
    s+=setup('standing-idle')+shots('standing-idle',12)
    s+=[{'log':'effortcase:standing-push'}]+shots('standing-push',30,-6)
    s+=[{'log':'effortcase:standing-stop'}]+shots('standing-stop',16)
    s+=[{'log':'effortcase:standing-pull'}]+shots('standing-pull',30,6)
    s+=[{'log':'effortcase:reverse-push'}]+shots('reverse-push',12,-6)
    s+=[{'log':'effortcase:reverse-pull'}]+shots('reverse-pull',12,6)
    s+=[{'log':'effortcase:release'},{'steeringRelease':True},{'wait':30}]+shots('release',8)
    s+=setup('crouch-idle',True)+shots('crouch-idle',8)
    s+=[{'log':'effortcase:crouch-push'}]+shots('crouch-push',30,-6)
    s+=[{'log':'effortcase:crouch-pull'}]+shots('crouch-pull',30,6)
    s+=setup('helper-busy',item='stone')+shots('helper-busy',30,-6)
    s+=setup('side-idle',facing='west',side=True)+shots('side-idle',8)
    s+=[{'log':'effortcase:side-push'}]+shots('side-push',30,-6)
    s+=[{'log':'effortcase:side-pull'}]+shots('side-pull',30,6)
    s+=[{'log':'effortcase:look-away'},{'look':[45,20]}]+shots('look-away',12)
    s+=[{'log':'effortcase:disabled'},{'config':{'buttonpress.heavy_throttle':False}},{'wait':30}]+shots('disabled',8)
    s+=[{'config':{'buttonpress.heavy_throttle':True}}]+setup('remove-block')
    s+=shots('remove-block',8,-6)+[{'cmd':'setblock 483 150 7 air'},{'wait':30}]+shots('removed',8)
    s+=setup('narrow-setup')
    s+=[{'steeringRelease':True},{'cmd':'setblock 483 150 7 stone'},
        {'cmd':'setblock 483 151 7 simulated:throttle_lever[facing=north,face=floor]'},
        {'cmd':'setblock 483 150 8 oak_fence'},
        {'cmd':'tp @s 483.5 151.5 8.5 180 20'},{'look':[180,20]},{'wait':20},
        {'throttleHold':[483,151,7]},{'wait':15}]+shots('narrow',30,-6)
    s+=[{'releaseAll':True},{'steeringRelease':True},{'log':'effortend'}]
    return s


if __name__=='__main__':Path(__file__).with_name('atlas-throttle-effort.json').write_text(json.dumps(cases(),indent=2)+'\n')
