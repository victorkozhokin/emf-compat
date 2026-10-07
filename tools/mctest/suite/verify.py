"""Runs the verifiers that take a suite run's results as they are: verify.py <label>."""
import json, sys, subprocess, shutil
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]; label = sys.argv[1]; OUT = ROOT / 'build' / 'suite' / label
SC = ROOT / 'tools' / 'mctest' / 'scenarios'
log = (OUT / 'atlas.log').read_text(errors='replace')
def piece(name):
    a = log.rfind('suite ' + name + '\n'); a = log.rfind('\n', 0, a) + 1
    b = log.find('suite-end ' + name + '\n', a)
    return log[a:b]
def rows(name):
    r = json.loads((OUT / (name + '.results.json')).read_text())['results']
    return {'results': r[4:-3]}
def setup(d, name, prefix='final', steps=False):
    d.mkdir(parents=True, exist_ok=True)
    (d / (prefix + '.json')).write_text(json.dumps(rows(name))); (d / (prefix + '.log')).write_text(piece(name))
    if steps: shutil.copy(SC / (name + '.json'), d / 'steps.json')
V = OUT / 'verify'
setup(V / 'lead', 'atlas-lead'); setup(V / 'lever', 'atlas-lever-pose'); setup(V / 'lever', 'atlas-lever-fence', 'fence')
setup(V / 'wall', 'atlas-wall-stance', steps=True); setup(V / 'wheel', 'atlas-wheel-stance'); setup(V / 'throttle', 'atlas-throttle-effort', steps=True)
for script, d in (('verify_lead', 'lead'), ('verify_lever_pose', 'lever'), ('verify_wall_stance', 'wall'), ('verify_wheel_stance', 'wheel'), ('verify_throttle_effort', 'throttle')):
    r = subprocess.run([sys.executable, str(SC / (script + '.py')), str(V / d)], capture_output=True, text=True, cwd=str(SC))
    out = (r.stdout.strip() or r.stderr.strip())
    (V / (script + '.txt')).write_text(r.stdout + r.stderr)
    print('==', script, 'exit', r.returncode); print(out[-700:])
