"""Low side typewriter, opposite floor throttle, and native wheel rotation."""
import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
from cockpit_regression import fixture
ROOT=Path('build/cockpit-mixed-review')
def cases():
    s=[]
    def capture(name,n=24,drag=None):
        s.append({'log':'mixedcase:'+name})
        for i in range(n):
            if drag and not ('steeringDrag'in drag and i>=n-6):s.append(drag)
            s.extend([{'wait':1},{'state':True},{'typewriterState':True},{'throttle':True},{'model':'player'},{'screenshot':name+f'-{i:03}'}])
        s.append({'log':'mixedcase:end'})
    for side,tx,lx,facing,yaw in [('right',371,369,'west',90),('left',369,371,'east',-90)]:
        s+=fixture()+[{'cmd':'clear @s'},{'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'effect give @s saturation 2 10 true'},
            {'cmd':f'setblock {tx} 150 7 simulated:linked_typewriter[facing={facing}]'},
            {'wait':20},{'typewriterBind':[tx,150,7]},{'wait':20},{'look':[180,22]},{'click':'use'},{'wait':5}]
        capture(side+'-wheel-start',32,{'steeringDrag':20})
        s+=[{'typewriterActivate':[tx,150,7]},{'wait':20}]
        capture(side+'-typing-ready')
        for code in [81,65,32]:
            s.append({'typewriterKey':[code,1]})
            capture(side+'-typing-key-'+str(code),16,{'steeringDrag':-15 if code==65 else 15})
            s.append({'typewriterKey':[code,0]});capture(side+'-typing-linger-'+str(code),8)
        s+=[{'typewriterKey':[256,1]},{'steeringRelease':True},{'look':[yaw,45]},{'throttleHold':[lx,150,7]}]
        capture(side+'-switch-to-throttle',32)
        capture(side+'-throttle-forward',24,{'throttleDrag':-10})
        capture(side+'-throttle-reverse',24,{'throttleDrag':10})
        s+=[{'steeringRelease':True},{'look':[180,22]},{'wait':20},{'click':'use'},{'wait':5}]
        capture(side+'-wheel-resume',40,{'steeringDrag':-20})
        s+=[{'typewriterActivate':[tx,150,7]},{'wait':20},{'typewriterKey':[65,1]}]
        capture(side+'-typing-again',24,{'steeringDrag':15})
        s+=[{'typewriterKey':[65,0]},{'typewriterKey':[256,1]},{'steeringRelease':True},{'wait':20}]
        capture(side+'-return',24)
    return s
if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=ROOT);args=parser.parse_args();ROOT=args.output
    ROOT.mkdir(parents=True,exist_ok=True);s=cases();(ROOT/'steps.json').write_text(json.dumps(s,indent=2));mctest.wait_ready('Test',60)
    r=mctest.run_steps('Test',s,timeout=240);(ROOT/'results.json').write_text(json.dumps(r));print('errors',[x for x in r['results']if 'error'in x]);assert not any('error'in x for x in r['results'])
