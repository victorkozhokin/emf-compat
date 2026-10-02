"""Real lead attachment in survival, with health/food/hurt probes and hidden GUI."""
import json
from pathlib import Path


def shots(name,count=12):
    s=[]
    for i in range(count):
        s += [{'wait':2},{'model':'player'},{'rope':True},{'screenshot':f'leash-{name}-{i:02}'}]
    return s+[{'state':True}]


def fixture():
    return [{'closeScreen':True},{'releaseAll':True},{'steeringRelease':True},{'cmd':'ride @s dismount'},
            {'cmd':'gamemode survival'},{'cmd':'kill @e[tag=leash_test]'},
            {'cmd':'fill 435 149 -5 475 149 20 smooth_stone'},{'cmd':'fill 435 150 -5 475 155 20 air'},
            {'cmd':'tp @s 450.5 150 7.5 -90 15'},
            {'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'effect give @s saturation 2 10 true'},{'wait':40},
            {'cmd':'summon cow 452.5 150 7.5 {NoAI:1b,Tags:["leash_test","leash_primary"]}'},
            {'cmd':'item replace entity @s weapon.mainhand with lead'},{'cmd':'item replace entity @s weapon.offhand with air'},
            {'config':{'leash.enabled':True,'lookat.enabled':False,'footgrounding.trace':True}},
            {'hideGui':True},{'hideScreen':True},{'camera':'back'},{'wait':30},
            {'look':[-90,15]},{'wait':3},{'click':'use'},{'wait':20},{'rope':True},
            {'orbit':[30,12,4]},{'wait':5}]


def cases():
    s=fixture()
    for name,x,y,z,crouch in [
        ('slack',453.5,150,7.5,False),('taut',457,150,7.5,False),
        ('taut-crouch',457,150,7.5,True),('side',453.5,150,12.5,False),
        ('behind',446.5,150,7.5,False),('high',454.5,152,7.5,False)]:
        s += [{'releaseAll':True},{'log':'leashcase:'+name},
              {'cmd':f'tp @e[tag=leash_primary,limit=1] {x} {y} {z}'},{'wait':10}]
        if crouch:s += [{'hold':'sneak'}]
        s+=shots(name)
    s += [{'releaseAll':True},{'log':'leashcase:jerk'},
          {'cmd':'tp @e[tag=leash_primary,limit=1] 456.3 150 7.5'},{'wait':15},
          {'hold':'back'}]+shots('jerk',6)+[{'releaseAll':True}]
    s += [{'log':'leashcase:relax'},{'cmd':'tp @s 450.5 150 7.5 -90 15'},
          {'cmd':'tp @e[tag=leash_primary,limit=1] 453.5 150 7.5'},{'wait':15}]+shots('relax')
    # Disabling must restore both ordinary pose ownership and vanilla rope endpoint.
    s += [{'log':'leashcase:busy'},{'cmd':'setblock 451 150 7 lever[face=floor,facing=west]'},
          {'look':[-90,55]},{'wait':10},{'click':'use'},{'wait':20}]+shots('busy',6)
    s += [{'log':'leashcase:busy-return'},{'cmd':'setblock 451 150 7 air'},{'look':[-90,15]},
          {'wait':25}]+shots('busy-return',6)
    s += [{'log':'leashcase:first-person'},{'camera':'first'},{'wait':30},{'rope':True},{'state':True},
          {'camera':'back'},{'wait':30}]
    s += [{'log':'leashcase:disabled' },{'config':{'leash.enabled':False}},{'wait':30}]+shots('disabled',6)
    s += [{'log':'leashcase:reenabled'},{'config':{'leash.enabled':True}},{'wait':30}]+shots('reenabled',6)
    s += [{'log':'leashcase:break'},{'cmd':'tp @e[tag=leash_primary,limit=1] 465.5 150 7.5'},
          {'wait':25}]+shots('break',6)
    # Fence attachment and actual removal must not leave a player holding pose.
    s += [{'log':'leashcase:detach'},{'cmd':'kill @e[tag=leash_test]'},{'wait':30}]+shots('detach',6)
    # Offhand attachment is explicit and uses the opposite hand from the first animal.
    s += [{'log':'leashcase:offhand'},{'cmd':'summon cow 452.5 150 7.5 {NoAI:1b,Tags:["leash_test","leash_primary"]}'},
          {'cmd':'item replace entity @s weapon.mainhand with air'},{'cmd':'item replace entity @s weapon.offhand with lead'},
          {'look':[-90,15]},{'wait':15},{'click':'use'},{'wait':25}]+shots('offhand')
    # Multiple leads share the same holding palm; choose the most loaded animal for effort.
    s += [{'log':'leashcase:multiple'},{'cmd':'summon cow 452.5 150 8.5 {NoAI:1b,Tags:["leash_test","leash_second"]}'},
          {'cmd':'data modify entity @e[tag=leash_second,limit=1] leash.UUID set from entity @s UUID'},
          {'wait':15},{'cmd':'tp @e[tag=leash_second,limit=1] 456 150 8.5'},{'wait':15}]+shots('multiple')
    s += [{'log':'leashcase:fence'},{'cmd':'setblock 453 150 7 oak_fence'},
          {'cmd':'execute as @e[tag=leash_test] run data modify entity @s leash set value [I;453,150,7]'},
          {'wait':30}]+shots('fence',6)
    s += [{'cmd':'kill @e[tag=leash_test]'},{'releaseAll':True},{'log':'leashend'}]
    return s


if __name__=='__main__':
    Path(__file__).with_name('atlas-leash.json').write_text(json.dumps(cases(),indent=2)+'\n')
