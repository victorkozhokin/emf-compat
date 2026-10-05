"""Seat/camera regression on live Sable physics, using the existing native cockpit fixture."""
from pathlib import Path
import cockpit_craft_regression as c
import mctest
from interaction_regression import scene
if __name__=='__main__':
    c.ROOT=Path('build/cockpit-camera-craft-review')
    mctest.wait_ready('Test',60)
    c.run(scene()+[{'cmd':'clear @s'},{'cmd':'effect give @s instant_health 1 10 true'},
                  {'cmd':'effect give @s saturation 1000 10 true'},{'releaseAll':True},{'hideGui':True},{'camera':'back'}])
    machine,lever=c.fixture('right');c.capture('right-stationary',24)
    c.run([{'click':'use'},{'wait':5},{'craft':{'action':'physical'}}]);c.engines(64,64)
    c.capture('right-accelerate',50,{'steeringDrag':8})
    c.run([{'typewriterActivate':machine},{'wait':20}]);c.capture('right-typing-while-moving',32,{'steeringDrag':8},65)
    c.run([{'typewriterKey':[256,1]},{'cameraLook':[220,15]}]);c.capture('right-camera-away',32,{'steeringDrag':-8})
    c.engines(0,0);c.run([{'craft':{'action':'incline','pitch':8,'roll':6}},{'wait':15}]);c.capture('right-inclined-deck',32)
    c.run([{'steeringRelease':True},{'cmd':'ride @s dismount'},{'craft':{'action':'remove'},},{'cmd':'tp @s 2315.5 151 2071.5'},{'wait':20}])
    print('completed',len(c.rows),'steps')
