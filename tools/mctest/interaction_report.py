"""Build a review gallery and check measured interaction invariants, not visual perfection.

python3 tools/mctest/interaction_report.py build/interaction-review run/mctest/Test/screenshots
Results are the JSON text returned by mc_steps, saved as *-results.json; after.log is latest.log.
The gallery keeps exact screenshots from those runs, avoiding earlier takes with identical names.
"""
import argparse
import html
import json
import re
import shutil
from pathlib import Path


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("review", type=Path)
    parser.add_argument("screenshots", type=Path)
    parser.add_argument("--cases", type=Path, default=Path("tools/mctest/scenarios/interaction-cases.json"))
    args = parser.parse_args()
    log = (args.review / "after.log").read_text()
    expected = json.loads(args.cases.read_text())
    completed = re.findall(r"interaction END ([\w-]+)", log)
    checks = {"all_cases_completed": set(expected) <= set(completed),
              "no_additive_hook_failure": "failed to apply additive pose" not in log,
              "no_crank_reflection_failure": "could not read the hand crank" not in log,
              "no_provider_failure": "provider BlockUse failed" not in log,
              "no_wheel_reflection_failure": "[BlockUse] cannot read" not in log}
    metrics = {}
    for name in expected:
        if not name.startswith(("crank-", "valve-", "steering")):
            continue
        take = log.split("interaction START " + name)[-1].split("interaction END")[0]
        rows = [tuple(map(float, m)) for m in re.findall(
            r"upright=([\d.E-]+) weight=([\d.E-]+).*distancePx=([\d.E-]+)", take)]
        held = [row for row in rows if row[1] > .99]
        if not held:
            checks[name + "_samples"] = False
            continue
        metrics[name] = {"samples": len(held), "max_distance_px": max(r[2] for r in held),
                         "min_upright": min(r[0] for r in held), "max_upright": max(r[0] for r in held)}
        support = [float(d) for w, d in re.findall(r"weight=([\d.E-]+).*supportDistancePx=([\d.E-]+)", take) if float(w) > .99]
        if support:
            metrics[name]["support_max_distance_px"] = max(support)
    if "crank-high-crouch" in metrics:
        checks["high_crank_within_palm_length"] = metrics["crank-high-crouch"]["max_distance_px"] <= 12
        checks["high_crank_stays_extended"] = metrics["crank-high-crouch"]["min_upright"] > .95
    if "crank-low-crouch" in metrics:
        checks["low_crank_does_not_stand_up"] = metrics["crank-low-crouch"]["max_upright"] < .01
    groups = {}
    for result in sorted(args.review.glob("*-results.json")):
        data = json.loads(result.read_text())
        if result.name == "crank-results.json":
            states = [r["state"] for r in data["results"] if "state" in r]
            checks["high_crank_gameplay_crouch_preserved"] = len(states) >= 3 and all(
                s["pose"] == "CROUCHING" and s["crouching"] for s in states[:3])
        if result.name == "wheels-results.json":
            states = [r["state"] for r in data["results"] if "state" in r]
            valve = [s for s in states if "getIndependentAngle" in s.get("target", {})]
            angles = [s["target"]["getRenderAngle"] for s in states
                      if "getRenderAngle" in s.get("target", {})]
            checks["valves_rotate_both_directions"] = len(valve) == 6 and all(
                abs(valve[i+1]["target"]["getIndependentAngle"] -
                    valve[i]["target"]["getIndependentAngle"]) >= 89 for i in (0, 2, 4))
            checks["valve_crouch_preserved"] = len(valve) == 6 and all(
                s["crouching"] and s["pose"] == "CROUCHING" for s in valve[4:])
            checks["steering_rotates_both_directions"] = bool(angles) and min(angles) < -.78 and max(angles) > .78
            checks["steering_returns_near_centre"] = bool(angles) and abs(angles[-1]) < .04
        if result.name == "contacts-results.json":
            states = [r["state"] for r in data["results"] if "state" in r]
            for block in ("barrel", "chest", "lectern"):
                opened = [s for s in states if s.get("target", {}).get("block") == "minecraft:" + block and "screen" in s]
                checks[block + "_menu_open_but_hidden"] = bool(opened) and all(s.get("screenHidden") for s in opened)
        for row in data["results"]:
            shot = row.get("screenshot")
            if not shot:
                continue
            label = re.sub(r"^\d+_", "", Path(shot).stem)
            case = next((n for n in sorted(expected, key=len, reverse=True)
                         if label == n or label.startswith((n + "_", n + "-"))), "other")
            groups.setdefault(case, []).append(shot)
    target = args.review / "frames"
    target.mkdir(exist_ok=True)
    for shots in groups.values():
        for shot in shots:
            source = args.screenshots / shot
            if source.exists():
                shutil.copy2(source, target / shot)
            else:
                checks["screenshot_exists:" + shot] = False
    report = {"checks": checks, "metrics": metrics, "completed": completed,
              "note": "Distance is shoulder-to-target, not a measured rendered palm contact error. "
                      "Rendered palm contact must also be reviewed in the frames."}
    (args.review / "metrics.json").write_text(json.dumps(report, indent=2) + "\n")
    body = ["<!doctype html><html lang='ru'><meta charset='utf-8'><title>Проверка взаимодействий</title>",
            ("<style>body{background:#14181e;color:#e6edf3;font:16px system-ui;max-width:1200px;margin:32px auto;padding:0 24px}"
            "article{border-top:1px solid #48515e;padding:20px 0}img{width:100%;max-width:920px}"
            "input{width:70%}button{padding:8px 18px}pre{white-space:pre-wrap}small{color:#abb8c9}</style>"),
            (f"<h1>Взаимодействия • FA+Player</h1><p>{len(expected)} игровых сценариев. Ползунок показывает исходные кадры. "
            "Прохождение числовых проверок не означает идеального контакта во всех сценах.</p>"),
            "<details><summary>Числовые проверки и ограничения</summary><pre>" + html.escape(json.dumps(report, indent=2, ensure_ascii=False)) + "</pre></details>"]
    for name, shots in groups.items():
        paths = ["frames/" + s for s in shots]
        body.append("<article data-frames='" + html.escape(json.dumps(paths), quote=True) + "'><h2>" + html.escape(name)
                    + "</h2><img src='" + html.escape(paths[0], quote=True) + "'><p><button>▶</button> "
                    + f"<input type='range' min='0' max='{len(paths)-1}' value='0'> <span>1 / {len(paths)}</span></p>"
                    + "<small>" + html.escape(shots[0]) + "</small></article>")
    body.append("""<script>document.querySelectorAll('article').forEach(a=>{
const frames=JSON.parse(a.dataset.frames),r=a.querySelector('input'),b=a.querySelector('button');let timer;
function draw(){a.querySelector('img').src=frames[+r.value];a.querySelector('span').textContent=(+r.value+1)+' / '+frames.length;a.querySelector('small').textContent=frames[+r.value]}
r.oninput=draw;b.onclick=()=>{if(timer){clearInterval(timer);timer=null;b.textContent='▶'}else{b.textContent='⏸';timer=setInterval(()=>{r.value=(+r.value+1)%frames.length;draw()},150)}};
});</script></html>""")
    (args.review / "index.html").write_text("\n".join(body))
    print(json.dumps(report, indent=2))
    if not all(checks.values()):
        raise SystemExit(1)


if __name__ == "__main__":
    main()
