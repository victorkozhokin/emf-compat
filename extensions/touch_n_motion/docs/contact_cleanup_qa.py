"""From repo root, against an already launched fresh issue63 sandbox. Never force-load a plot.
Requires tools/mctest and its native Sable rope fixture. Only the sandbox world is changed.
"""
import json
import shutil
import sys
import time
from collections import defaultdict
from pathlib import Path
sys.path[:0] = ['tools/mctest', 'tools/mctest/scenarios']
import mctest
from aeronautics_rope_regression import cases
PROFILE = 'issue63'
OUT = Path('build/contact-cleanup')
OUT.mkdir(parents=True, exist_ok=True)
rows, steps = [], []
def run(batch):
    offset = len(steps)
    result = mctest.run_steps(PROFILE, batch, timeout=90)
    for row in result['results']: row['step'] += offset
    rows.extend(result['results']); steps.extend(batch)
    (OUT/'results.json').write_text(json.dumps({'results': rows}))
    (OUT/'steps.json').write_text(json.dumps(steps, indent=2))
    assert not any('error' in row for row in result['results']), result
    for row in result['results']:
        if 'state' not in row: continue
        s = row['state']
        assert s['gameMode'] == 'creative' and s['health'] > 0, s
        assert s['onGround'] and s['pos'][1] > 154, ('player left the deck', s)
    return result
mctest.wait_ready(PROFILE, 60)
run([{'closeScreen': True}, {'releaseAll': True}, {'cmd': 'gamemode creative'},
     {'cmd': 'time set day'}, {'cmd': 'gamerule doDaylightCycle false'},
     {'cmd': 'forceload add 2352 2064 2416 2128'}, {'cmd': 'tp @s 2380 160 2090'}, {'wait': 40},
     {'cmd': 'fill 2360 149 2070 2400 149 2110 minecraft:stone'}, {'cmd': 'tp @s 2380.5 150 2090.5'}, {'wait': 20}])
setup = cases()
setup = [step for step in setup if step.get('cmd') != 'gamemode survival']
for step in setup:
    if step.get('craft', {}).get('local') == [.75, 2, 1.0]: step['craft']['local'] = [.95, 2, 1.5]
cut = next(i for i, step in enumerate(setup) if step.get('craft', {}).get('action') == 'circle')
# Prepare the native strand, without waiting through the old long circle sequence.
setup = setup[:cut]
setup += [{'config': {'transport.onAcceleration': False, 'footgrounding.trace': True}},
          {'craft': {'action': 'stream', 'delta': [0, 0, .025], 'ticks': 1600}}]
run(setup)
def take(name, count=25):
    batch = [{'log': 'contact-cleanup:' + name}]
    for i in range(count):
        batch += [{'wait': 1}, {'state': True}, {'model': 'player'}]
        if i % 5 == 0: batch += [{'screenshot': name + '-' + str(i)}]
    run(batch)
# Reacquire from zero weight; no editing of the model, pose or animation timers.
run([{'config': {'transport.grip': False}}, {'wait': 35}, {'config': {'transport.grip': True}}])
take('acquire', 35)
run([{'hold': 'sneak'}]); take('crouch', 30)
run([{'releaseAll': True}, {'wait': 20}])
# Hand over the support when the primary hand becomes occupied, then let it go completely.
run([{'cmd': 'item replace entity @s weapon.mainhand with minecraft:iron_sword'},
     {'craft': {'action': 'place', 'local': [.05, 2, 1.5]}}, {'wait': 20},
     {'craft': {'action': 'place', 'local': [.05, 2, 1.5]}}, {'wait': 20}, {'look': [0, 0]}, {'wait': 10}]); take('other-hand', 30)
run([{'cmd': 'item replace entity @s weapon.offhand with minecraft:shield'}]); take('release', 40)
run([{'cmd': 'clear @s'}, {'look': [0, 0]}, {'wait': 20}]); take('reacquire', 30)
run([{'config': {'transport.grip': False}}]); take('smooth-release', 45)
run([{'config': {'transport.grip': True}}]); take('final-reacquire', 30)
run([{'releaseAll': True}, {'config': {'footgrounding.trace': False, 'transport.trace': False,
                                    'transport.onAcceleration': True}},
     {'craft': {'action': 'stream', 'ticks': 1, 'delta': [0, 0, 0]}}, {'wait': 5}])
shutil.copyfile('run/mctest/'+PROFILE+'/logs/latest.log', OUT/'game.log')
shots = OUT/'shots'; shots.mkdir(exist_ok=True)
for row in rows:
    if 'screenshot' in row: shutil.copyfile(row['screenshot'], shots/Path(row['screenshot']).name)
print('recorded', len(rows), 'steps, creative throughout; only world chunks force-loaded')

# Verify outcomes, not merely that the driver accepted the steps.
by_phase = defaultdict(list)
phase = None
for row in rows:
    step = steps[row['step']]
    if step.get('log', '').startswith('contact-cleanup:'): phase = step['log'].split(':')[-1]
    if phase and 'model' in row: by_phase[phase].append(row['model'])
for name, hand in [('acquire', 'RIGHT_ARM'), ('crouch', 'RIGHT_ARM'), ('other-hand', 'LEFT_ARM'),
                   ('reacquire', 'LEFT_ARM'), ('final-reacquire', 'LEFT_ARM')]:
    assert len(by_phase[name]) >= 5, name
    assert all(m['interaction'][hand].get('owner') == 'TransportGrip' and
               m['interaction'][hand]['weight'] > .99 for m in by_phase[name][-5:]), name
for name in ['release', 'smooth-release']:
    assert all(max(m['interaction'][h]['weight'] for h in ['LEFT_ARM', 'RIGHT_ARM']) < .001
               for m in by_phase[name][-5:]), name
assert any(m['interaction']['LEFT_ARM'].get('releasing') for m in by_phase['smooth-release'])
if '--quit' in sys.argv:
    profile = mctest.get_profile(PROFILE)
    run([{'quitGame': True}])  # NeoForge driver extension: Minecraft.stop(), no SIGKILL fallback.
    until = time.monotonic() + 20
    while mctest.running_pid(profile) and time.monotonic() < until: time.sleep(.25)
    assert not mctest.running_pid(profile), 'Native shutdown stalled; process left intact for diagnosis'
    shutil.copyfile(profile.sandbox/'logs/latest.log', OUT/'native-exit.log')
    assert 'All dimensions are saved' in (OUT/'native-exit.log').read_text()
    print('native shutdown completed and all dimensions saved')
