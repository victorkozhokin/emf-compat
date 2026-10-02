"""Verify survival vitals, real attachments, ownership and the rendered palm endpoint."""
import json
import math
import re
import sys
from pathlib import Path


def verify(directory):
    directory=Path(directory)
    commands=json.loads(Path(__file__).with_name('atlas-leash.json').read_text())
    response=json.loads((directory/'final.json').read_text())
    assert len(commands)==len(response['results'])
    cases={};name=None
    for command,result in zip(commands,response['results']):
        assert 'error' not in result,result
        if 'log' in command and command['log'].startswith('leashcase:'):
            name=command['log'].split(':')[1];cases[name]={'ropes':[],'models':[]}
        if 'state' in result:
            state=result['state']
            assert state['gameMode']=='survival',state
            assert state['health']==20 and state['food']==20 and state['hurtTime']==0,state
        if name and 'rope' in result:cases[name]['ropes'].append(result['rope'])
        if name and 'model' in result:cases[name]['models'].append(result['model']['parts'])
    for name in ['slack','taut','taut-crouch','side','behind','high','jerk','relax','busy','busy-return','disabled','reenabled','offhand']:
        assert all(r['count']==1 for r in cases[name]['ropes']),(name,'Lost leash')
    assert all(r['count']==2 for r in cases['multiple']['ropes']),'Multiple attachment missing'
    for name in ['break','detach','fence']:
        assert all(r['count']==0 for r in cases[name]['ropes']),(name,'Still player-held')
    traces={};name=None
    for line in (directory/'final.log').read_text().splitlines():
        if 'leashcase:' in line:name=line.split('leashcase:')[1];traces[name]=[]
        if 'leashend' in line:name=None
        if name and '[LeashTrace]' in line:
            v={k:float(x) for k,x in re.findall(r'(\w+)=([-+\d.E]+)',line)}
            v['right']='right=true' in line;traces[name].append(v)
    assert traces['slack'][-1]['load']<.01
    assert traces['taut'][-1]['load']>.99
    assert traces['relax'][-1]['load']<.01
    assert max(v['jerk'] for v in traces['jerk'])>.1,'Outward jerk not exercised'
    assert traces['busy'][-1]['weight']<.01,'Leash fought active control'
    assert traces['busy-return'][-1]['weight']>.99
    assert traces['offhand'][-1]['right'] is False,'Consumed offhand lead forgot its hand'
    gaps=[]
    for name in ['slack','taut','taut-crouch','behind','relax','busy-return','offhand','multiple']:
        last=traces[name][-1]
        assert last['weight']>.99,(name,last)
        palm=[last['palmX'],last['palmY'],last['palmZ']]
        endpoint=cases[name]['ropes'][-1]['endpoint']
        gap=math.dist(palm,endpoint);gaps.append(gap)
        assert gap<.03,(name,'Rope endpoint detached from captured palm',gap)
    assert cases['taut']['models'][-1]['right_arm']['rot'] != cases['slack']['models'][-1]['right_arm']['rot']
    return {'runtimeSteps':len(response['results']),'cases':len(cases),'mode':'survival',
            'health':20,'food':20,'hurtTime':0,'maximumSampledPalmGapBlocks':max(gaps),
            'offhand':True,'multipleAnimals':True,'activeControlPriority':True}


if __name__=='__main__':print(json.dumps(verify(sys.argv[1]),indent=2))
