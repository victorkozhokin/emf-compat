"""Brush a wheat rim in both directions and crouched, with a visible contact-side camera."""
import json,shutil,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
out=Path(sys.argv[1]);out.mkdir(parents=True,exist_ok=True)
steps=[{'releaseAll':True},{'closeScreen':True},{'cmd':'gamemode survival'},{'cmd':'effect give @s saturation 10000 10 true'},{'cmd':'effect give @s instant_health 1 10 true'},{'hideGui':True},{'camera':'back'},{'config':{'pocket.enabled':False,'lookat.enabled':False,'containersearch.enabled':False}}]
for name,x,yaw,crouch in [('right',450.5,-90,False),('left',455.5,90,False),('crouch',450.5,-90,True)]:
 steps += [{'releaseAll':True},{'cmd':'fill 448 150 4 458 153 11 air'},{'cmd':'fill 448 149 4 458 149 11 smooth_stone'},{'cmd':'fill 450 149 8 455 149 8 farmland'},{'cmd':'fill 450 150 8 455 150 8 wheat[age=7]'},{'cmd':f'tp @s {x} 150 7.5 {yaw} 0'},{'wait':5},{'look':[yaw,0]},{'orbit':[-155-yaw,18,3]},{'log':'plant-edge:'+name}]
 if crouch:steps += [{'hold':'sneak'}]
 for phase,ticks in [('settle',30),('walk',42),('release',35)]:
  if phase=='walk':steps += [{'hold':'forward'}]
  if phase=='release':steps += [{'releaseAll':True},{'cmd':'fill 450 150 8 455 150 8 air'}]
  for i in range(ticks):
   steps += [{'wait':1},{'state':True},{'model':'player'}]
   if i%2==0:steps += [{'screenshot':f'plant-edge-{name}-{phase}-{i:02d}'}]
steps += [{'releaseAll':True},{'config':{'pocket.enabled':True,'lookat.enabled':True,'containersearch.enabled':True}},{'orbit':False},{'hideGui':False}]
(out/'steps.json').write_text(json.dumps(steps,indent=2))
mctest.wait_ready('Test',60);r=mctest.run_steps('Test',steps,timeout=180);(out/'results.json').write_text(json.dumps(r));(out/'shots').mkdir(exist_ok=True)
for row in r['results']:
 if 'screenshot'in row:shutil.copy2(row['screenshot'],out/'shots'/Path(row['screenshot']).name)
errors=[r for r in r['results']if 'error'in r];print('errors',errors)
if errors:raise SystemExit(1)
