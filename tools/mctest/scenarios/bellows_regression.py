"""Native empty-hand bellows strokes; never invoke setManualPress from the driver."""
import sys,json,math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import mctest
from interaction_regression import scene
ROOT=Path('build/bellows-review');steps=[];rows=[]
def run(batch):
    offset=len(steps);r=mctest.run_steps('Test',batch,timeout=120)
    for row in r['results']:row['step']+=offset
    steps.extend(batch);rows.extend(r['results']);ROOT.mkdir(parents=True,exist_ok=True)
    (ROOT/'steps.json').write_text(json.dumps(steps));(ROOT/'results.json').write_text(json.dumps({'results':rows}))
    assert not any('error'in row for row in r['results']),[row for row in r['results']if'error'in row]

def take(name,n=40,clicks=False,by=150):
    batch=[{'log':'bellowscase:'+name}]
    for i in range(n):
        if clicks and i%15==0:batch.append({'click':'use'})
        batch +=[{'wait':1},{'state':True},{'bellowsState':[351,by,7]},{'model':'player'},{'screenshot':f'bellows-{name}-{i:03}'}]
    run(batch+[{'log':'bellowscase:end'}])

def fixture(facing='north',crouch=False,by=150,side=False):
    x,z,yaw=(351.5,6.69,0)if side else(350.69,7.5,-90)
    top=by+(1 if facing not in ['up','down']else .5)
    pitch=-math.degrees(math.atan2(top-(150+(1.27 if crouch else 1.62)),.81))
    run([{'releaseAll':True},{'cmd':'tp @s 345 150 7.5'},{'wait':3},{'cmd':'fill 349 150 5 353 154 9 air'},
        {'cmd':f'setblock 351 {by} 7 supplementaries:bellows[facing={facing}]'},
        {'cmd':'item replace entity @s weapon.mainhand with air'},{'cmd':'item replace entity @s weapon.offhand with air'},
        {'cmd':f'tp @s {x} 150 {z} {yaw} {pitch}'},{'look':[yaw,pitch]},{'orbit':[80,10,2.7]},
        {'hideGui':True},{'hideScreen':True}]+([{'hold':'sneak'}]if crouch else[])+[{'wait':25}])
if __name__=='__main__':
    mctest.wait_ready('Test',60);run(scene()+[{'camera':'back'},{'cmd':'clear @s'}])
    fixture();take('idle',25);take('north-press',60,True)
    run([{'look':[-90,-75]}]);take('release',30)
    fixture();run([{'click':'use'},{'hold':'sneak'}]);take('crouch-press',35)
    run([{'mainArm':'left'}]);fixture();take('left-press',45,True);run([{'mainArm':'right'}])
    for facing in ['east','south','west']:fixture(facing,side=facing=='west');take(facing+'-press',45,True)
    fixture('west');take('blown-release',45,True)
    fixture('up',by=151);take('up-press',45,True,by=151)
    fixture('down',by=151);take('down-press',45,True,by=151)
    fixture();run([{'cmd':'setblock 351 150 8 redstone_block'},{'wait':25}]);take('redstone',35)
    fixture();run([{'cmd':'item replace entity @s weapon.offhand with minecraft:shield'},{'wait':20}]);take('occupied',25)
    fixture();run([{'cmd':'setblock 351 150 7 air'},{'wait':20}]);run([{'model':'player'},{'state':True}])
    print('completed',len(rows))
