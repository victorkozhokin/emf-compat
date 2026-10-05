"""Real Create seat with a front wheel and left/right floor Throttles; GUI hidden."""
import json
from pathlib import Path
from interaction_regression import scene


def fixture():
    return [{"steeringRelease":True},{"cmd":"ride @s dismount"}]+scene()+[
        {"cmd":"kill @e[type=create:seat,x=366,y=149,z=3,dx=8,dy=5,dz=8]"},
        {"cmd":"fill 366 149 3 374 149 11 smooth_stone"},
        {"cmd":"fill 366 150 3 374 154 11 air"},
        {"cmd":"setblock 370 150 7 create:red_seat"},
        {"cmd":"setblock 370 151 5 create:brass_casing"},
        {"cmd":"setblock 370 151 6 simulated:steering_wheel[facing=south,on_floor=false]"},
        {"cmd":"setblock 369 150 7 simulated:throttle_lever[facing=north,face=floor]"},
        {"cmd":"setblock 371 150 7 simulated:throttle_lever[facing=north,face=floor]"},
        {"cmd":"tp @s 370.5 150 8.2 180 60"},{"look":[180,60]},
        {"camera":"back"},{"hideGui":True},{"hideScreen":True},{"wait":15},
        {"click":"use"},{"wait":20},{"look":[180,22]},
        {"orbit":[45,20,3,True]},{"wait":15},{"state":True}]


def shots(name,count=18,drag=None):
    steps=[]
    for i in range(count):
        if drag is not None:steps.append(drag)
        steps += [{"wait":1},{"model":"player"},{"screenshot":f"cockpit-{name}-{i:02}"}]
    return steps+[{"state":True}]


def cases():
    steps=fixture()
    steps += [{"log":"cockpitcase:wheel"},{"click":"use"},{"wait":5}]+shots('wheel',drag={"steeringDrag":20})
    steps += [{"steeringRelease":True},{"log":"cockpitcase:right"},{"look":[-90,45]},{"wait":2},{"throttleHold":[371,150,7]},{"wait":2}]+shots('right')
    steps += [{"log":"cockpitcase:right-drag"}]+shots('right-drag',drag={"throttleDrag":-10})
    steps += [{"log":"cockpitcase:right-look-away"},{"look":[180,22]}]+shots('right-look-away',count=8)
    steps += [{"steeringRelease":True},{"log":"cockpitcase:return-right"}]+shots('return-right')
    steps += [{"log":"cockpitcase:left"},{"look":[90,45]},{"wait":2},{"throttleHold":[369,150,7]},{"wait":2}]+shots('left')
    steps += [{"log":"cockpitcase:left-drag"}]+shots('left-drag',drag={"throttleDrag":-10})
    steps += shots('left-reverse',drag={"throttleDrag":10})
    steps += [{"steeringRelease":True},{"log":"cockpitcase:direct-switch"},{"look":[-90,45]},{"wait":2},{"throttleHold":[371,150,7]},{"wait":2}]+shots('direct-switch',count=24)
    steps += [{"steeringRelease":True},{"log":"cockpitcase:return-both"},{"look":[180,22]}]+shots('return-both')
    steps += [{"log":"cockpitcase:wheel-resume"},{"click":"use"},{"wait":2}]+shots('wheel-resume',drag={"steeringDrag":-20})
    steps += [{"steeringRelease":True},{"log":"cockpitcase:remove-throttle"},{"look":[-90,45]},{"wait":2},{"throttleHold":[371,150,7]},{"wait":2},{"wait":10},
              {"cmd":"setblock 371 150 7 air"}]+shots('remove-throttle')
    steps += [{"steeringRelease":True},{"log":"cockpitcase:dismount"},{"cmd":"ride @s dismount"},{"wait":20},{"model":"player"},
              {"screenshot":"cockpit-dismount"},{"log":"cockpitend"}]
    return steps


def facing_sweep():
    steps=fixture()+[{"click":"use"},{"wait":5}]
    for i,yaw in enumerate([180,150,120,90,60,30,0,-30,-60,-90,-120,-150,180]):
        steps += [{"look":[yaw,10]},{"wait":8},{"model":"player"},
                  {"screenshot":f"cockpit-facing-yaw-{i:02}"},{"state":True}]
    return steps+[{"steeringRelease":True},{"cmd":"ride @s dismount"},{"wait":15}]


if __name__=='__main__':
    Path(__file__).with_name('atlas-cockpit.json').write_text(json.dumps(cases(),indent=2)+'\n')
    Path(__file__).with_name('atlas-cockpit-facing.json').write_text(json.dumps(facing_sweep(),indent=2)+'\n')
