"""Record repeatable native gestures with model snapshots and archived screenshots."""
import json,sys,shutil
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
source=(Path(__file__).with_name('gestures.py')).read_text().split('\nonly =')[0]
ns={};exec(compile(source,'gestures.py','exec'),ns)
def steps():
 s=[{'cmd':'gamemode survival'},{'cmd':'time set noon'},{'cmd':'weather clear'},
    {'cmd':'effect give @s saturation 10000 10 true'},{'cmd':'effect give @s instant_health 1 10 true'},
    {'config':{'lookat.enabled':False,'pocket.enabled':False}},{'hideGui':True},{'camera':'back'}]
 for name in ['feed','milk','shear','seed','stand','boots','armour','shake','chest']:
  run=ns['run'](name,'right',270,True)
  # Three-quarter view retains both soles and the contact rather than hiding behind the torso.
  for step in run:
   if 'orbit' in step:step['orbit']=[235,12,3.3]
   if 'model' in step: s.append({'state':True})
   s.append(step)
  if name=='boots':s.append({'wait':25})
 s += ns['CLEAR']+[{'orbit':False},{'hideGui':False},{'hideScreen':False},{'config':{'pocket.enabled':True,'lookat.enabled':True}}]
 return s
if __name__=='__main__':
 root=Path(sys.argv[1]);root.mkdir(parents=True,exist_ok=True)
 sequence=steps();(root/'steps.json').write_text(json.dumps(sequence,indent=2))
 print(mctest.wait_ready('Test',60),flush=True)
 result=mctest.run_steps('Test',sequence,timeout=240)
 (root/'results.json').write_text(json.dumps(result))
 dest=root/'shots';dest.mkdir(exist_ok=True)
 for row in result.get('results',[]):
  if 'screenshot' in row and Path(row['screenshot']).exists():shutil.copyfile(row['screenshot'],dest/Path(row['screenshot']).name)
 print('errors',[r for r in result.get('results',[])if 'error'in r],flush=True)
