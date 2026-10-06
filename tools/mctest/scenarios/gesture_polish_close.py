"""Close-contact take: real native use, healthy survival, unobstructed objects and hidden GUI."""
import json, sys, shutil, os
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
ns={};exec(compile(Path(__file__).with_name('gestures.py').read_text().split('\nonly =')[0],'gestures.py','exec'),ns)
ns['CASES']['barrel']=(["setblock 452 150 7 barrel[facing=west]"],None,45,'chest',110)
s=[{'cmd':'gamemode survival'},{'cmd':'time set noon'},{'cmd':'weather clear'},{'cmd':'effect give @s saturation 10000 10 true'},{'cmd':'effect give @s instant_health 1 10 true'},{'hideGui':True},{'camera':'back'},{'config':{'pocket.enabled':False,'lookat.enabled':False}}]
for case in ['feed','milk','shear','stand','seed','chest','barrel']:
 if os.environ.get('CLOSE_CASES') and case not in os.environ['CLOSE_CASES'].split(','):continue
 for step in ns['run'](case,'right',270,True):
  step=dict(step)
  if 'orbit'in step:step['orbit']=[235,12,3.1]
  command=step.get('cmd','')
  if command.startswith('summon cow'):step['cmd']=command.replace('452.3','451.9').replace('{NoAI:1b,','{NoAI:1b,Rotation:[90f,0f],')
  if command.startswith('summon sheep'):step['cmd']=command.replace('452.3','451.75').replace('{NoAI:1b,','{NoAI:1b,Rotation:[90f,0f],')
  if command.startswith('summon armor_stand'):step['cmd']=command.replace('452.0','451.45')
  if case in ('chest','barrel'):
   if command.startswith('tp @s'):step['cmd']='tp @s 451.05 150 7.5 -90 45'
   if 'cameraLook'in step:step['cameraLook']=[-90,45]
  if 'screenshot'in step:step['screenshot']=step['screenshot'].replace('gesture-','close-')
  if 'model'in step:s.append({'state':True})
  s.append(step)
s+=ns['CLEAR']+[{'orbit':False},{'hideGui':False},{'hideScreen':False},{'config':{'pocket.enabled':True,'lookat.enabled':True}}]
root=Path(sys.argv[1]);root.mkdir(parents=True,exist_ok=True);(root/'steps.json').write_text(json.dumps(s,indent=2));mctest.wait_ready('Test',60);r=mctest.run_steps('Test',s,timeout=240);(root/'results.json').write_text(json.dumps(r));(root/'shots').mkdir(exist_ok=True)
for row in r['results']:
 if 'screenshot'in row:shutil.copyfile(row['screenshot'],root/'shots'/Path(row['screenshot']).name)
print('errors',[x for x in r['results']if 'error'in x])
