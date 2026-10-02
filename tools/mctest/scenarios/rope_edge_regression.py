"""Settled deck stance and mirrored held edge poses on native ropes and Sable decks.
Create each deck's final geometry before its cord: changing Sable mass properties
under an already paused rope fixture can invalidate its captured transform.
"""
import json,sys
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]))
import mctest
from aeronautics_rope_regression import cases
ROOT=Path('build/rope-edge-review')
def steps():
    s=[]
    def fixture(x,z,edge):
        old=cases();mount=next(i for i,t in enumerate(old) if t.get('craft',{}).get('action')=='aeroRopeMount')
        setup=[json.loads(json.dumps(t).replace('2380',str(x)).replace('2090',str(z))) for t in old[:mount]]
        setup[0]={'log':'rope-edge:setup'};s.extend(setup)
        if edge:s.extend({'craft':{'action':'block','local':[xx,y,zz],'block':'minecraft:air'}} for xx in range(-3,4) for y in (0,1) for zz in range(1,4))
        s.extend([{'wait':40},{'craft':{'action':'aeroRopeMount'}},{'wait':20},{'craft':{'action':'aeroRope'}},{'wait':40},
                  {'craft':{'action':'place','local':[.75,2,.8 if edge else 1]}},{'look':[0,0]},{'orbit':[-85,8,3.3]},{'wait':40},
                  {'craft':{'action':'stream','delta':[0,0,.025],'ticks':60}},{'wait':40}])
    def capture(name,period=200):
        s.extend([{'craft':{'action':'circle','radius':4,'period':period,'ticks':period+160}},{'wait':period},{'log':'rope-edge:'+name}])
        for i in range(160):s.extend([{'wait':1},{'state':True},{'model':'player'},{'screenshot':f'{name}-{i:03}'}])
        s.append({'log':'rope-edge:end'})
    fixture(2380,2090,False);capture('deck',160)
    fixture(2420,2130,True);capture('edge')
    s.extend([{'cmd':'give @s minecraft:stick 1'},{'slot':0},{'craft':{'action':'place','local':[.25,2,.8]}},{'wait':60}]);capture('edge-left')
    s.extend([{'cmd':'clear @s'},{'craft':{'action':'stream','delta':[0,0,0],'ticks':1}}])
    s.extend({'craft':{'action':'block','local':[x,1,z],'block':'minecraft:stone'}} for x in range(-3,4) for z in range(1,4))
    s.extend([{'wait':40},{'craft':{'action':'place','local':[.75,2,1]}},{'wait':40}]);capture('return')
    return s+[{'releaseAll':True}]
if __name__=='__main__':
    ROOT.mkdir(parents=True,exist_ok=True);s=steps();(ROOT/'steps.json').write_text(json.dumps(s,indent=2))
    mctest.wait_ready('Test',60);result=mctest.run_steps('Test',s,timeout=220)
    (ROOT/'results.json').write_text(json.dumps(result));errors=[r for r in result['results'] if 'error'in r]
    print('commands',len(result['results']),'errors',errors);assert not errors
