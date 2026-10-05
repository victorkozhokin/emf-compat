"""Validate and review atlas-balance-regression.json with the profile's FA+Player packs.

Run after saving driver results to build/balance-regression-after.json.
Model-space measurements describe the straight leg centre, not its entire rendered sole.
"""
from pathlib import Path
import html
import json
import math
import re
import shutil

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "build/balance-regression-review"
OUT.mkdir(parents=True, exist_ok=True)
steps = json.loads((ROOT / "tools/mctest/scenarios/atlas-balance-regression.json").read_text())
results = json.loads((ROOT / "build/balance-regression-after.json").read_text())["results"]
log = (ROOT / "run/mctest/Test/logs/latest.log").read_text()


def sole(part):
    pitch, yaw, roll = part["rot"]
    sx, cx = math.sin(pitch), math.cos(pitch)
    sy, cy = math.sin(yaw), math.cos(yaw)
    sz, cz = math.sin(roll), math.cos(roll)
    offset = (12 * (sx * sy * cz - cx * sz),
              12 * (sx * sy * sz + cx * cz), 12 * sx * cy)
    return tuple(a + b for a, b in zip(part["pos"], offset))


models = {}
label = ""
for result in results:
    step = steps[result["step"]]
    label = step.get("screenshot", label)
    if "model" in result:
        models[label] = result["model"]["parts"]

summary = {"driver_steps": len(results),
           "driver_errors": [r for r in results if "error" in r],
           "states_on_ground": all(r["state"]["onGround"] for r in results if "state" in r),
           "animation_hook_failure": "failed to apply additive pose" in log,
           "supports": {}}
for block in ("fence", "wall"):
    parts = models[f"regression-{block}-idle-after"]
    right, left = sole(parts["right_leg"]), sole(parts["left_leg"])
    run = [p for name, parts in models.items() if f"{block}-sprint-after" in name
           for key, p in parts.items() if key in ("right_leg", "left_leg")]
    summary["supports"][block] = {
        "idle_sole_distance_pixels": math.dist(right, left),
        "idle_along_sole_distance_pixels": abs(right[2] - left[2]),
        "max_sprint_pitch_degrees": max(abs(p["rot"][0]) * 180 / math.pi for p in run),
        "minimum_sprint_hip_y": min(p["pos"][1] for p in run),
    }
    assert math.dist(right, left) > 2, f"{block}: idle soles collapsed together"
    assert max(abs(p["rot"][0]) for p in run) < math.pi / 2, f"{block}: sprint leg inverted"

trace = [dict((k, float(v)) for k, v in re.findall(
    r"(points|narrow|pitch|roll|rx|rz|lx|lz)=([^ ]+)", line))
    for line in log.splitlines() if "[TerrainTrace]" in line]
summary["finite_terrain_trace"] = all(math.isfinite(v) for row in trace for v in row.values())
assert summary["finite_terrain_trace"] and not summary["driver_errors"]
assert summary["states_on_ground"] and not summary["animation_hook_failure"]
(OUT / "metrics.json").write_text(json.dumps(summary, indent=2))
shutil.copy2(ROOT / "run/mctest/Test/logs/latest.log", OUT / "after.log")
for shot in (ROOT / "run/mctest/Test/screenshots").glob("regression-*.png"):
    shutil.copy2(shot, OUT / shot.name)

page = '''<!doctype html><meta charset="utf-8"><title>Balance Lab — 01.10.2026</title>
<style>body{background:#15191e;color:#e3e8ed;font:16px system-ui;margin:24px}
main{display:grid;grid-template-columns:repeat(auto-fit,minmax(440px,1fr));gap:16px}
figure{margin:0}img{width:100%}figcaption{padding:8px}pre{white-space:pre-wrap}a{color:#a9d4ff}
input{width:85%}button{padding:8px}</style><h1>Balance Lab: забор, стенка, спринт</h1>
<p>GUI скрыт. Стойка вдоль и поперёк опоры, присяд и спринт; профиль Test с FA+Player.
Кадры сохранены без обработки. Для бега — 18 кадров с интервалом 2 игровых тика.</p>'''
page += '<pre>' + html.escape(json.dumps(summary, ensure_ascii=False, indent=2)) + '</pre><main>'
for block, title in (("fence", "Забор"), ("wall", "Каменная стенка"), ("floor", "Обычный пол")):
    for pose in ("idle", "crouch", "crosswise", "settle"):
        name = f"regression-{block}-{pose}-after.png"
        if (OUT / name).exists():
            page += f'<figure><a href="{name}"><img loading="lazy" src="{name}"></a><figcaption>{title}: {pose}</figcaption></figure>'
    names = [f"regression-{block}-sprint-after_{i:02}.png" for i in range(18)]
    page += f'<figure class="sequence" data-frames=\'{json.dumps(names)}\'><img src="{names[0]}"><figcaption>{title}: спринт <button>▶ / ❚❚</button> <input type="range" min="0" max="17" value="0"></figcaption></figure>'
page += '''</main><script>document.querySelectorAll('.sequence').forEach(card=>{
const frames=JSON.parse(card.dataset.frames),img=card.querySelector('img'),range=card.querySelector('input');
let timer;const show=i=>{range.value=i;img.src=frames[i]};range.oninput=()=>show(+range.value);
card.querySelector('button').onclick=()=>{if(timer){clearInterval(timer);timer=null}else{
timer=setInterval(()=>show((+range.value+1)%frames.length),100)}}});</script>'''
(OUT / "index.html").write_text(page)
print(json.dumps(summary, ensure_ascii=False, indent=2))
