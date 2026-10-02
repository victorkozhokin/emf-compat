"""Native seated keyboard use with the opposite hand retained on the wheel."""
import sys,json
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
from cockpit_regression import fixture
ROOT=Path('build/cockpit-typewriter-review')
def cases():
    s=fixture()+[{'cmd':'clear @s'},{'cmd':'effect give @s instant_health 1 10 true'},{'cmd':'effect give @s saturation 2 10 true'},
       {'cmd':'setblock 369 150 7 create:brass_casing'},{'cmd':'setblock 371 150 7 create:brass_casing'},
       {'cmd':'setblock 369 151 7 simulated:linked_typewriter[facing=east]'},{'cmd':'setblock 371 151 7 simulated:linked_typewriter[facing=west]'},
       {'wait':20},{'typewriterBind':[369,151,7]},{'typewriterBind':[371,151,7]},{'wait':20},{'look':[180,22]},{'wait':20}]
    def capture(name,n=24):
        s.append({'log':'typingcase:'+name})
        for i in range(n):s.extend([{'wait':1},{'state':True},{'typewriterState':True},{'model':'player'},{'screenshot':name+f'-{i:03}'}])
        s.append({'log':'typingcase:end'})
    capture('wheel')
    for side,pos in [('right',[371,151,7]),('left',[369,151,7])]:
        s.extend([{'typewriterActivate':pos},{'wait':20}]);capture(side+'-ready')
        for code in [81,65,32]:
            s.append({'typewriterKey':[code,1]});capture(side+'-key-'+str(code),12)
            s.append({'typewriterKey':[code,0]});capture(side+'-linger-'+str(code),8)
        s.extend([{'look':[0,10]},{'wait':20}]);capture(side+'-look-away')
        s.extend([{'typewriterKey':[256,1]},{'wait':20},{'look':[180,22]}]);capture(side+'-return')
    s.extend([{'typewriterActivate':[371,151,7]},{'wait':20},{'cmd':'setblock 371 151 7 air'},{'wait':20}]);capture('removed')
    s.extend([{'cmd':'ride @s dismount'},{'wait':20}]);capture('dismount')
    return s
if __name__=='__main__':
    ROOT.mkdir(parents=True,exist_ok=True);s=cases();(ROOT/'steps.json').write_text(json.dumps(s,indent=2));mctest.wait_ready('Test',60)
    r=mctest.run_steps('Test',s,timeout=170);(ROOT/'results.json').write_text(json.dumps(r));print('errors',[x for x in r['results'] if 'error'in x]);assert not any('error'in x for x in r['results'])
