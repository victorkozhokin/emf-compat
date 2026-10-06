"""Five native early interactions, identical cameras and healthy survival, before/after."""
import json, shutil, sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
steps=[{'releaseAll':True},{'closeScreen':True},{'cmd':'gamemode survival'},{'cmd':'effect give @s saturation 10000 10 true'},{'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'time set noon'},{'cmd':'weather clear'},{'hideGui':True},{'camera':'back'},{'config':{'pocket.enabled':False,'lookat.enabled':False,'containersearch.enabled':False}}]
def record(name,phase,ticks):
 for i in range(ticks):
  steps.extend([{'wait':1},{'state':True},{'model':'player'}])
  if i%2==0:steps.append({'screenshot':f'early-{name}-{phase}-{i:02d}'})
for name in ['plant','door','furniture','button','wall']:
 steps += [{'releaseAll':True},{'closeScreen':True},{'cmd':'clear @s'},{'cmd':'fill 448 150 4 454 153 11 air'},{'cmd':'fill 448 149 4 454 149 11 smooth_stone'},{'cmd':'tp @s 450.5 150 7.5 -90 0'},{'wait':5},{'look':[-90,0]},{'orbit':[35,12,3.3,True]},{'wait':25}]
 if name=='plant':steps += [{'cmd':'fill 450 149 8 453 149 8 farmland'},{'cmd':'fill 450 150 8 453 150 8 wheat[age=7]'}]
 if name=='door':steps += [{'cmd':'setblock 451 150 7 oak_door[facing=east,half=lower,hinge=left]'},{'cmd':'setblock 451 151 7 oak_door[facing=east,half=upper,hinge=left]'}]
 if name=='furniture':steps += [{'cmd':'setblock 451 150 7 lectern[facing=west,has_book=true]'},{'cmd':'tp @s 450.75 150 7.5 -90 30'},{'look':[-90,30]}]
 if name=='button':steps += [{'cmd':'setblock 451 151 7 stone'},{'cmd':'setblock 450 151 7 stone_button[face=wall,facing=west]'}]
 if name=='wall':steps += [{'cmd':'fill 450 150 8 454 152 8 stone'}]
 pitch={'plant':0,'door':25,'furniture':30,'button':0,'wall':0}[name]
 steps += [{'cameraLook':[-90,pitch]},{'log':'early:'+name}]
 record(name,'approach',36)
 if name in ['door','button']:steps += [{'click':'use'}]
 if name=='plant':steps += [{'hold':'forward'}]
 record(name,'work',28)
 exit_yaw=180 if name=='plant' else 90
 steps += [{'releaseAll':True},{'cameraLook':[exit_yaw,0]},{'orbit':[-55-exit_yaw,12,3.3]}]
 if name in ['plant','door','wall']:steps += [{'hold':'forward'}]
 record(name,'release',40)
 steps += [{'releaseAll':True}]
steps += [{'config':{'pocket.enabled':True,'lookat.enabled':True,'containersearch.enabled':True}},{'orbit':False},{'hideGui':False}]
(out/'steps.json').write_text(json.dumps(steps,indent=2))
mctest.wait_ready('Test',60)
r=mctest.run_steps('Test',steps,timeout=240);(out/'results.json').write_text(json.dumps(r));(out/'shots').mkdir(exist_ok=True)
for row in r['results']:
 if 'screenshot'in row:shutil.copyfile(row['screenshot'],out/'shots'/Path(row['screenshot']).name)
errors=[row for row in r['results']if 'error'in row];print('errors',errors)
if errors:raise SystemExit(1)
