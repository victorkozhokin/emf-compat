"""Native edge cases for gesture contact and exposure; runs in a copied Test world."""
import json, sys, shutil, os
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
ns={};exec(compile(Path(__file__).with_name('gestures.py').read_text().split('\nonly =')[0],'gestures.py','exec'),ns)
ns['CASES']['leggings']=([],None,0,'boots',80)
ns['CASES']['boots']=tuple(ns['CASES']['boots'][:-1])+(80,)
s=[{'cmd':'gamemode survival'},{'cmd':'effect give @s saturation 10000 10 true'},{'cmd':'effect give @s instant_health 1 10 true'},{'hideGui':True},{'camera':'back'},{'config':{'pocket.enabled':False,'lookat.enabled':False}}]
for label,case in [('boots-standing','boots'),('leggings-standing','leggings'),('snow','shake'),('mud','shake'),('boots-crouch','boots'),('seed-crouch','seed'),('feed-left','feed'),('cancel','leave'),('barrel-slots','chest')]:
 if os.environ.get('EDGE_CASES') and label not in os.environ['EDGE_CASES'].split(','):continue
 run=ns['run'](case,'right',270,True);capture=0
 for step in run:
  step=dict(step)
  if label=='leggings-standing' and 'armor.feet'in step.get('cmd',''):step['cmd']=step['cmd'].replace('armor.feet','armor.legs').replace('iron_boots','iron_leggings')
  if 'orbit'in step:step['orbit']=[235,12,3.3]
  if label=='snow' and step.get('cmd','').endswith('6 447 151 8 water'):step['cmd']='fill 446 150 6 447 151 8 powder_snow'
  if label=='mud' and step.get('cmd','').endswith('6 447 151 8 water'):step['cmd']='fill 446 149 6 447 149 8 mud'
  if label=='barrel-slots' and step.get('cmd','')=='setblock 452 150 7 chest[facing=west]':step['cmd']='setblock 452 150 7 barrel[facing=west]'
  if label=='barrel-slots' and step.get('cmd','').startswith('tp @s 450.5'):step['cmd']='tp @s 451.0 150 7.5 -90 45'
  if label=='barrel-slots' and 'cameraLook'in step:step['cameraLook']=[-90,45]
  if label=='feed-left' and 'weapon.mainhand'in step.get('cmd',''):step['cmd']=step['cmd'].replace('weapon.mainhand','weapon.offhand')
  if 'model'in step:
   s.append({'state':True});capture+=1
   if label=='barrel-slots' and capture==30:s.append({'menuClick':{'slot':0,'button':0,'type':'PICKUP'}})
   if label=='barrel-slots' and capture==45:s.append({'menuClick':{'slot':1,'button':0,'type':'PICKUP'}})
  if 'screenshot'in step:step['screenshot']=step['screenshot'].replace('gesture-'+case,'edge-'+label)
  s.append(step)
  if step.get('cmd','').startswith(('tp @s 450.5','tp @s 451.0')):
   if label.endswith('crouch'):s.append({'hold':'sneak'})
   if label=='snow':s.append({'cmd':'item replace entity @s armor.feet with leather_boots'})
   if label=='barrel-slots':s.append({'cmd':'item replace block 452 150 7 container.0 with minecraft:stone 32'})
s+=ns['CLEAR']+[{'orbit':False},{'hideGui':False},{'hideScreen':False},{'config':{'pocket.enabled':True,'lookat.enabled':True}}]
root=Path(sys.argv[1]);root.mkdir(parents=True,exist_ok=True);(root/'steps.json').write_text(json.dumps(s,indent=2))
mctest.wait_ready('Test',60)
r=mctest.run_steps('Test',s,timeout=240);(root/'results.json').write_text(json.dumps(r));(root/'shots').mkdir(exist_ok=True)
for row in r['results']:
 if 'screenshot'in row:shutil.copyfile(row['screenshot'],root/'shots'/Path(row['screenshot']).name)
print('errors',[x for x in r['results']if 'error'in x])
