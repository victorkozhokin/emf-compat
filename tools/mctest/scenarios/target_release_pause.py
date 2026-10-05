"""Observe the rendered release during a real PauseScreen and resume automatically."""
import sys,json,shutil
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import mctest
from interaction_regression import setup
from verify_target_release import angle
root=Path('build/target-release-review');steps=setup('pause','oak_door[half=lower,facing=west,open=false]',by=150,height=1,x=350.7)+[{'config':{'doorhold.enabled':True}},{'wait':30},{'model':'player'},{'config':{'doorhold.enabled':False}},{'wait':2},{'pauseMs':1000},{'model':'player'},{'config':{'doorhold.enabled':True}}]
r=mctest.run_steps('Test',steps,timeout=30)
assert not [x for x in r['results']if 'error'in x],r
probe=next(x['pauseProbe']for x in r['results']if 'pauseProbe'in x)
assert len(probe)==8 and all(x['paused']for x in probe),probe
assert len(set(x['gameTick']for x in probe))==1,probe
slots=[x['model']['interaction']['LEFT_ARM']for x in probe]
weights=[s['weight']for s in slots];elapsed=[s['release']['elapsed']for s in slots]
assert max(weights)-min(weights)<1e-6,weights
assert max(elapsed)-min(elapsed)<1e-6,elapsed
rot=[x['model']['parts']['left_arm']['rot']for x in probe];change=max(angle(a,b)for a,b in zip(rot,rot[1:]))
assert change<.1,change
for x in probe:
 src=Path(x['screenshot']);shutil.copy2(src,root/src.name);x['screenshot']=str((root/src.name).resolve())
summary={'samples':8,'pausedSeconds':1,'gameTick':probe[0]['gameTick'],'weightVariation':max(weights)-min(weights),'elapsedVariation':max(elapsed)-min(elapsed),'maxArmStepDegrees':change}
(root/'pause.json').write_text(json.dumps({'summary':summary,'steps':steps,'results':r['results']},indent=2));print(summary)
