"""Summarize FootTrace numerically. Indicators flag frames to inspect, not visual pass/fail.

Usage: python3 tools/mctest/footik_report.py latest.log --out build/footik-review/after.json
"""
import argparse
import json
import math
import re
from pathlib import Path


def summarize(path):
    cases = {}
    case = "unmarked"
    active = True
    for line in Path(path).read_text(errors="replace").splitlines():
        if "footik START " in line:
            case = line.split("footik START ", 1)[1].strip()
            active = True
        if "footik END " in line:
            active = False
        if "[FootTrace]" not in line or not active:
            continue
        row = {}
        for key, value in re.findall(r"(\w+)=([^ ]+)", line.split("[FootTrace]", 1)[1]):
            try:
                row[key] = float(value)
            except ValueError:
                pass
        cases.setdefault(case, []).append(row)
    result = {}
    for name, rows in cases.items():
        def peak(key, samples=rows):
            return max((r.get(key, 0) for r in samples if math.isfinite(r.get(key, 0))), default=0)

        result[name] = {
            "samples": len(rows),
            "animated_pose_samples": sum("rp" in r and "lp" in r for r in rows),
            "stride_samples": sum(math.isfinite(r.get("fR", math.nan)) and math.isfinite(r.get("fL", math.nan)) for r in rows),
            "peak_body_drop_px": peak("low"),
            "peak_right_lift_px": peak("rb"), "peak_left_lift_px": peak("lb"),
            "both_lifts_over_1_5_px": sum(r.get("rb", 0) > 1.5 and r.get("lb", 0) > 1.5 for r in rows),
            # Both animated soles on the same surface but the drawn body still lowered.
            "level_floor_body_lag_over_1_px": sum(
                abs(r.get("fR", math.nan)) < 0.1 and abs(r.get("fL", math.nan)) < 0.1
                and r.get("low", 0) > 1 for r in rows),
            # Specific course plateau, safely beyond the lip: no lower support is possible here.
            "slab_plateau_sink_over_1_px": sum(
                306 <= r.get("x", 0) <= 310 and abs(r.get("y", 0) - 150.5) < 0.01
                and 5 <= r.get("z", 0) <= 9 and r.get("low", 0) > 1 for r in rows),
        }
    return result


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--out", type=Path)
    args = parser.parse_args()
    text = json.dumps(summarize(args.log), indent=2)
    if args.out:
        args.out.parent.mkdir(parents=True, exist_ok=True)
        args.out.write_text(text + "\n")
    print(text)
