"""Summarize terrain traces and link the unmodified GUI-free screenshots.

Run after scenarios/terrain-balance.json: python3 tools/mctest/terrain_report.py
"""
from pathlib import Path
import html
import json
import math
import re
import shutil
import sys

ROOT = Path(__file__).resolve().parents[2]
sandbox = ROOT / "run/mctest/Test"
atlas = "--atlas" in sys.argv
output = ROOT / ("build/atlas-crouch-review" if atlas else "build/terrain-review")
output.mkdir(parents=True, exist_ok=True)
log = (sandbox / "logs/latest.log").read_text(errors="replace")
rows = []
for line in log.splitlines():
    if "[TerrainTrace]" not in line:
        continue
    row = {key: float(value) for key, value in re.findall(r"(points|narrow|pitch|roll|rx|rz|lx|lz)=([^ ]+)", line)}
    if row:
        rows.append(row)
results = json.loads((ROOT / ("build/atlas-crouch-after.json" if atlas else "build/terrain-run.json")).read_text())
errors = [step for step in results["results"] if "error" in step]
summary = {
    "driver_steps": len(results["results"]),
    "driver_errors": errors,
    "terrain_frames": len(rows),
    "finite": all(math.isfinite(v) for row in rows for v in row.values()),
    "max_narrow": max((row["narrow"] for row in rows), default=0),
    "max_foot_shift_pixels": max((math.hypot(row[x], row[z]) for row in rows
                                  for x, z in [("rx", "rz"), ("lx", "lz")]), default=0),
    "animation_hook_failure": "failed to apply additive pose" in log,
    "limits": ["Inclined Sable platform requires a separate live regression; unit-tested slope math only.",
               "Fence outline correction lowers the visual model; the gameplay hitbox remains at collision height."],
}
if atlas:
    summary["observed_poses"] = [step["state"]["pose"] for step in results["results"] if "state" in step]
    marker = log.find("atlas fence crouching after")
    settled = [line for line in log[:marker].splitlines() if "[SoleTrace]" in line and "pose=CROUCHING" in line]
    if settled:
        values = dict((key, float(value)) for key, value in re.findall(r"(correction|target)=([^ ]+)", settled[-1]))
        summary["settled_crouch_correction_pixels"] = values["correction"]
        summary["settled_contact_residual_pixels"] = abs(values["target"] - values["correction"])
(output / "metrics.json").write_text(json.dumps(summary, indent=2))
shutil.copy2(sandbox / "logs/latest.log", output / "after.log")
shots = sorted((sandbox / "screenshots").glob("atlas-*.png" if atlas else "terrain-*.png"))
cards = []
for shot in shots:
    if shot.name == "atlas-fence-standing-before.png":
        continue  # Resource reload overlay obscured this frame.
    shutil.copy2(shot, output / shot.name)
    name = html.escape(shot.stem)
    cards.append(f'<figure><a href="{shot.name}"><img loading="lazy" src="{shot.name}" alt="{name}"></a><figcaption>{name}</figcaption></figure>')
page = '''<!doctype html><meta charset="utf-8"><title>Terrain balance — 01.10.2026</title>
<style>body{background:#15191e;color:#e3e8ed;font:16px system-ui;margin:24px}main{display:grid;grid-template-columns:repeat(auto-fit,minmax(440px,1fr));gap:16px}figure{margin:0}img{width:100%}figcaption{padding:8px}pre{white-space:pre-wrap}a{color:#a9d4ff}</style>
<h1>Узкие опоры и склоны — проверка</h1><p>FA+Player · GUI скрыт · отдельная копия мира. Клик по кадру открывает оригинал.</p>
<p>Проходы между стенами относятся к другому эксперименту. Наклонённая платформа Sable пока проверена только на уровне математики.</p>'''
page += '<pre>' + html.escape(json.dumps(summary, indent=2, ensure_ascii=False)) + '</pre><main>' + ''.join(cards) + '</main>'
(output / "index.html").write_text(page)
print(json.dumps(summary, indent=2))
