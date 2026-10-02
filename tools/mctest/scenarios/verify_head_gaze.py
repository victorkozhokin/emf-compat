"""Compare rendered gaze with/without torso clearance; check seated yaw freedom."""
import json
import sys
from pathlib import Path


def verify(directory):
    directory = Path(directory)
    steps = json.loads(Path(__file__).with_name('atlas-head-gaze.json').read_text())
    response = json.loads((directory / 'gaze.json').read_text())
    assert len(steps) == len(response['results'])
    frames = {}; name = None
    for command, result in zip(steps, response['results']):
        assert 'error' not in result, result
        if 'log' in command: name = command['log']
        if 'model' in result: frames[name] = result['model']['parts']
    drift = 0; turns = []
    for name, before in frames.items():
        if not name.endswith(':False'): continue
        after = frames[name.replace(':False', ':True')]
        delta = max(abs(a-b) for a,b in zip(before['head']['rot'], after['head']['rot']))
        drift = max(drift, delta)
        assert delta <= .005, (name, 'gaze changed with torso clearance', delta)
        turn = abs(before['body']['rot'][1] - after['body']['rot'][1])
        assert turn > .3, (name, 'fixture did not activate clearance', turn)
        turns.append(turn)
    assert len(turns) == 20
    yaw = json.loads((directory/'yaw.json').read_text())
    models = []
    for result in yaw['results']:
        assert 'error' not in result, result
        if 'model' in result: models.append(result['model']['parts'])
    assert len(models) == 13
    heads = [m['head']['rot'][1] for m in models]
    assert min(heads) < -1.3 and max(heads) > 1.3, 'head did not follow camera independently'
    assert max(map(abs, heads)) < 1.49, 'neck limit exceeded'
    for leg in ['right_leg', 'left_leg']:
        for axis in range(3):
            assert max(m[leg]['rot'][axis] for m in models)-min(m[leg]['rot'][axis] for m in models) < .03
            assert max(m[leg]['pos'][axis] for m in models)-min(m[leg]['pos'][axis] for m in models) < .3
    lookat = json.loads((directory/'lookat.json').read_text())
    for result in lookat['results']: assert 'error' not in result, result
    head = next(r['model']['parts']['head'] for r in lookat['results'] if 'model' in r)
    assert abs(head['rot'][1]) > .1, 'idle creature gaze was suppressed'
    return {'clearancePairs':len(turns), 'maximumHeadDriftRadians':drift,
            'seatedCameraDirections':len(models), 'idleCreatureGaze':True}


if __name__ == '__main__':
    print(json.dumps(verify(sys.argv[1]), indent=2))
