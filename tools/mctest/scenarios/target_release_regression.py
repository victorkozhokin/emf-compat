"""Matched target-release takes, timestamped models and healthy survival screenshots."""
import sys,json,shutil
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import mctest
from interaction_regression import scene,setup

def main(label):
    root=Path('build/target-release-review')/label;root.mkdir(parents=True,exist_ok=True)
    mctest.wait_ready('Test',60)
    rows=[];steps=[]
    def run(batch):
        off=len(steps);r=mctest.run_steps('Test',batch,timeout=120)
        assert not any('error' in x for x in r['results']),[x for x in r['results']if 'error'in x]
        for x in r['results']:
            x['step']+=off
            if 'screenshot'in x:
                path=Path(x['screenshot']);shutil.copy2(path,root/path.name);x['screenshot']=str((root/path.name).resolve())
        rows.extend(r['results']);steps.extend(batch)
        (root/'results.json').write_text(json.dumps({'results':rows}));(root/'steps.json').write_text(json.dumps(steps))
    def frames(name,n):
        run([{'log':'releasecase:'+name}]+[x for i in range(n)for x in
             [{'wait':1},{'state':True},{'model':'player'},{'screenshot':f'release-{name}-{i:03}'}]])
    run(scene()+[{'cmd':'clear @s'},{'config':{'mining.enabled':True}}])
    for reason in ['disabled','removed','away']:
        by={'disabled':151,'removed':150,'away':152}[reason]
        run(setup('mining-'+reason,'obsidian',by=by,item='wooden_pickaxe',x=350.7)+[{'hold':'attack'},{'wait':8}])
        frames('mining-'+reason+'-hold',6)
        change=({'config':{'mining.enabled':False}}if reason=='disabled'else
                {'cmd':f'setblock 351 {by} 7 air'}if reason=='removed'else {'look':[-90,-80]})
        run([{'until':{'swingTime':2,'timeout':20}},{'state':True},{'model':'player'},{'screenshot':'release-mining-'+reason+'-event-000'},{'log':'event:mining-'+reason},change,{'release':'attack'}]);frames('mining-'+reason+'-release',38)
        run([{'config':{'mining.enabled':True}}])
    run(setup('door','oak_door[half=lower,facing=west,open=false]',by=150,height=1,x=350.7))
    frames('door-hold',8);run([{'log':'event:door-disabled'},{'config':{'doorhold.enabled':False}}]);frames('door-release',38)
    run([{'config':{'doorhold.enabled':True}}])
    run(setup('crank','create:hand_crank[facing=west]',x=350.69)+[{'hold':'use'},{'wait':12}])
    frames('crank-hold',12);run([{'log':'event:crank-away'},{'release':'use'},{'look':[-90,-80]}]);frames('crank-release',38)
    run(setup('lever','lever[face=wall,facing=west]',x=350.69)+[{'click':'use'},{'wait':4}])
    frames('lever-hold',8);run([{'log':'event:lever-replaced'},{'cmd':'setblock 351 151 7 stone'}]);frames('lever-release',38)
    print(label,'completed',len(rows))
if __name__=='__main__':main(sys.argv[1])
