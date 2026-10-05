"""Additional matched handoff, support unloading and native moving-craft checks."""
import sys,json,shutil,math
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import mctest
from interaction_regression import scene,setup

def main(label):
 root=Path('build/target-release-review')/label;root.mkdir(parents=True,exist_ok=True)
 rows=[];steps=[]
 def run(batch):
  off=len(steps);r=mctest.run_steps('Test',batch,timeout=120)
  assert not [x for x in r['results']if 'error'in x],r
  for x in r['results']:
   x['step']+=off
   if 'screenshot'in x:
    path=Path(x['screenshot']);shutil.copy2(path,root/path.name);x['screenshot']=str((root/path.name).resolve())
  rows.extend(r['results']);steps.extend(batch)
  (root/'extra-results.json').write_text(json.dumps({'results':rows}));(root/'extra-steps.json').write_text(json.dumps(steps))
 def take(name,n):
  run([{'log':'releasecase:'+name}]+[x for i in range(n)for x in [{'wait':1},{'state':True},{'model':'player'},{'screenshot':f'release-{name}-{i:03}'}]])
 run(scene()+[{'cmd':'clear @s'},{'config':{'blockuse.enabled':True,'doorhold.enabled':True,'mining.enabled':True}}])
 run(setup('same-provider','barrel[facing=west]',x=350.7)+[{'cmd':'setblock 351 151 8 barrel[facing=west]'},{'wait':30}])
 take('barrel-hold',12);run([{'log':'event:barrel-switch'},{'look':[-40,6]}]);take('barrel-switch',38)
 run(setup('table','simulated:navigation_table[facing=up]',by=150,height=.81,x=350.65)+[{'wait':60}])
 take('table-hold',12);run([{'log':'event:table-disabled'},{'config':{'blockuse.enabled':False}}]);take('table-disabled',38)
 run([{'config':{'blockuse.enabled':True}}])
 run(setup('occupied','oak_door[half=lower,facing=west,open=false]',by=150,height=1,x=350.7))
 take('occupied-hold',12);run([{'log':'event:occupied'},{'cmd':'item replace entity @s weapon.mainhand with minecraft:bow'},{'cmd':'item replace entity @s weapon.offhand with minecraft:arrow'},{'hold':'use'}]);take('occupied',25)
 run([{'releaseAll':True},{'cmd':'clear @s'}])
 run(setup('occluded','barrel[facing=west]',x=350.7))
 take('occluded-hold',12);run([{'log':'event:occluded'},{'cmd':'setblock 351 151 7 stone'}]);take('occluded',38)
 # Native Sable carrying and render transforms. No fake player pose or animation overrides.
 run([{'releaseAll':True},{'config':{'transport.grip':True}},{'craft':{'action':'create','pos':[2480,156,2110]}},{'wait':40},
      {'craft':{'action':'place','local':[.5,1,1.1]}},{'look':[0,0]},{'orbit':[35,14,3]},{'wait':40},
      {'craft':{'action':'stream','delta':[0,0,.025],'ticks':160}},{'wait':35}])
 take('moving-grip',25);run([{'log':'event:moving-disabled'},{'config':{'transport.grip':False}}]);take('moving-release',38)
 run([{'config':{'transport.grip':True}},{'craft':{'action':'remove'}},{'cmd':'tp @s 350 150 7.5'},{'releaseAll':True}])
 print(label,'extra completed',len(rows))
if __name__=='__main__':main(sys.argv[1])
