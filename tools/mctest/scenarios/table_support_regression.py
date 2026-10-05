"""Native Navigation Table: contact/load/release, diagonal approach, crouch, real item use.
Launch the owned Test sandbox with --name STRadaT --uuid e750dfdd-f54d-418b-abd4-6776ce404f09 --no-cape
to reproduce the profile owner's FA+Player pose; default Dev has a different base skin pose.
"""
import sys,json,math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import mctest
from interaction_regression import scene
ROOT=Path('build/table-support-review');steps=[];rows=[]
def run(batch):
    offset=len(steps);r=mctest.run_steps('Test',batch,timeout=120)
    for row in r['results']:row['step']+=offset
    steps.extend(batch);rows.extend(r['results'])
    ROOT.mkdir(parents=True,exist_ok=True);(ROOT/'steps.json').write_text(json.dumps(steps));(ROOT/'results.json').write_text(json.dumps({'results':rows}))
    assert not any('error'in r for r in r['results']),r
    return r['results']
def take(name,n):
    s=[{'log':'tablecase:'+name}]
    for i in range(n):s +=[{'wait':1},{'state':True},{'tableState':[351,151 if name=='vertical-table'else 150,7]},{'model':'player'},{'screenshot':f'table-{name}-{i:03}'}]
    s.append({'log':'tablecase:end'});return run(s)
def fixture(x=350.65,z=7.5,crouch=False,by=150,facing='up'):
    pitch=-math.degrees(math.atan2(by+.81-(150+(1.27 if crouch else 1.62)),math.hypot(351.5-x,7.5-z)));yaw=-90 if z==7.5 else -math.degrees(math.atan2(351.5-x,7.5-z))
    run([{'releaseAll':True},{'cmd':'tp @s 345 150 7.5'},{'wait':3},{'cmd':'fill 349 150 5 353 154 9 air'},{'cmd':f'setblock 351 {by} 7 simulated:navigation_table[facing={facing}]'},{'cmd':'item replace entity @s weapon.mainhand with air'},
         {'cmd':f'tp @s {x} 150 {z} {yaw} {pitch}'},{'look':[yaw,pitch]},{'orbit':[80,10,2.7]},{'hideGui':True},{'hideScreen':True}]+([{'hold':'sneak'}]if crouch else[]))
if __name__=='__main__':
    mctest.wait_ready('Test',60);run([{'cmd':'clear @s'}]+scene()+[{'camera':'back'},{'hideGui':True}])
    fixture();take('standing-contact',65)
    run([{'cmd':'item replace entity @s weapon.mainhand with minecraft:compass[simulated:target="simulated:compass"]'},{'wait':15},{'click':'use'}]);take('put-compass',18)
    run([{'cmd':'data get block 351 150 7'},{'wait':8},{'click':'use'}]);take('take-compass',18)
    run([{'look':[-90,-75]}]);take('release',20)
    fixture(x=350.69,z=6.69);take('diagonal',40)
    fixture(crouch=True);take('crouching',40)
    run([{'mainArm':'left'},{'wait':10}]);fixture();take('left-hand',45)
    run([{'mainArm':'right'},{'wait':10}])
    fixture(x=349.2);take('too-far',24)
    fixture(facing='west',by=151);take('vertical-table',24)
    print('completed',len(rows))
