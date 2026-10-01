"""Assert and review atlas-wallhand.json results saved as build/wall-after.json."""
from pathlib import Path
import html
import json
import math
import re
import shutil

root = Path(__file__).resolve().parents[2]
out = root / "build/wall-review"
out.mkdir(parents=True, exist_ok=True)
result = json.loads((root / "build/wall-after.json").read_text())["results"]
log = (root / "run/mctest/Test/logs/latest.log").read_text()
marker = log.rfind("wallrun:final")
if marker >= 0: log = log[marker:]
cases = {}
case = "setup"
for line in log.splitlines():
    marker = re.search(r"wallcase:([a-z-]+)", line)
    if marker:
        case = marker[1]
    if "[WallContactTrace]" not in line:
        continue
    row = dict(re.findall(r"(arm|valid|weight|pitch|roll|gap|turn|side)=([^ ]+)", line))
    for key in ("weight", "pitch", "roll", "gap", "turn", "side"):
        row[key] = float(row[key])
    cases.setdefault(case, []).append(row)

errors = [row for row in result if "error" in row]
finite = all(math.isfinite(row[key]) for rows in cases.values() for row in rows
             for key in ("weight", "pitch", "roll", "turn", "side"))
steady = [row for row in cases.get("narrow", [])[-80:] if row["valid"] == "true" and row["weight"] > .9]
jitter = {row["side"] for row in cases.get("jitter", [])}
summary = {"driver_steps": len(result), "driver_errors": errors, "finite_angles": finite,
           "hook_failure": "failed to apply additive pose" in log,
           "jitter_turn_sides": sorted(jitter),
           "steady_contact_samples": len(steady),
           "steady_max_abs_palm_gap_pixels": max((abs(row["gap"]) for row in steady), default=None),
           "cases": {}}
for name, rows in cases.items():
    summary["cases"][name] = {"samples": len(rows),
        "valid_arms": sorted({row["arm"] for row in rows if row["valid"] == "true"}),
        "settled_valid_arms": sorted({row["arm"] for row in rows[-80:] if row["valid"] == "true"}),
        "last_weights": {arm: next((row["weight"] for row in reversed(rows) if row["arm"] == arm), 0)
                         for arm in ("R", "L")}}
assert not errors and finite and not summary["hook_failure"]
assert len(jitter) == 1, "squeeze turn side oscillates in an almost symmetric corridor"
assert steady and {row["arm"] for row in steady} == {"R", "L"}, "missing settled wall contact"
assert summary["steady_max_abs_palm_gap_pixels"] < .5, "settled palm misses or penetrates its face"
assert summary["cases"]["exit"]["last_weights"]["R"] < .02
assert summary["cases"]["exit"]["last_weights"]["L"] < .02
assert summary["cases"]["one-wall"]["settled_valid_arms"] == ["L"], "far hand crosses torso to one wall"
assert summary["cases"]["held"]["settled_valid_arms"] == ["L"], "held hand/contact ownership is wrong"
assert summary["cases"]["both-held"]["settled_valid_arms"] == [], "held items cannot brace a wall"
(out / "metrics.json").write_text(json.dumps(summary, indent=2))
shutil.copy2(root / "run/mctest/Test/logs/latest.log", out / "after.log")
for shot in (root / "run/mctest/Test/screenshots").glob("wall-after*.png"):
    shutil.copy2(shot, out / shot.name)
page = '''<!doctype html><meta charset="utf-8"><title>Wall contact lab</title>
<style>body{background:#15191e;color:#e3e8ed;font:16px system-ui;margin:24px}
main{display:grid;grid-template-columns:repeat(auto-fit,minmax(440px,1fr));gap:16px}
figure{margin:0}img{width:100%}pre{white-space:pre-wrap}figcaption{padding:8px}
input{width:75%}a{color:#a9d4ff}</style><h1>Узкий проход: касание и отпускание</h1>
<p>FA+Player, GUI скрыт, камера вдоль прохода. Фазы проверяются по реальной стене у ладони.
Измеряется центр ладони с отступом на толщину руки; это не проверка столкновения всей сетки.</p>'''
page += '<pre>' + html.escape(json.dumps(summary, ensure_ascii=False, indent=2)) + '</pre><main>'
for shot in sorted(out.glob("wall-after*.png")):
    if re.search(r"_\d\d\.png$", shot.name):
        continue
    page += f'<figure><a href="{shot.name}"><img loading="lazy" src="{shot.name}"></a><figcaption>{html.escape(shot.stem)}</figcaption></figure>'
for name in ("entry", "walk", "backward", "exit", "break", "turn", "crouch-walk", "sprint"):
    frames = [shot.name for shot in sorted(out.glob(f"wall-after-{name}_*.png"))]
    if not frames: continue
    page += f'<figure class="sequence" data-frames=\'{json.dumps(frames)}\'><img src="{frames[0]}"><figcaption>{name} <button>▶ / ❚❚</button> <input type="range" min="0" max="{len(frames)-1}" value="0"></figcaption></figure>'
page += '''</main><script>document.querySelectorAll('.sequence').forEach(card=>{
const frames=JSON.parse(card.dataset.frames),img=card.querySelector('img'),range=card.querySelector('input');
let timer;const show=i=>{range.value=i;img.src=frames[i]};range.oninput=()=>show(+range.value);
card.querySelector('button').onclick=()=>{if(timer){clearInterval(timer);timer=null}else{
timer=setInterval(()=>show((+range.value+1)%frames.length),100)}}});</script>'''
(out / "index.html").write_text(page)
print(json.dumps(summary, indent=2))
