"""Ranges and biggest per-tick steps of the torso and arms from a ride_boat run: ride_boat_report.py <results.json> <steps.json>."""
import json, sys, math
out = json.load(open(sys.argv[1])); rows = out["results"]
steps = json.loads(open(sys.argv[2]).read())
name = None; data = {}
for st, r in zip(steps, rows):
    if "log" in st and st["log"].startswith("row ") and not st["log"].endswith("end"): name = st["log"][4:]
    if "model" in r and name:
        parts = r["model"]["parts"] if isinstance(r["model"], dict) and "parts" in r["model"] else r["model"]
        data.setdefault(name, []).append(parts)
def rot(p, n):
    q = p.get(n) if isinstance(p, dict) else next((x for x in p if x.get("name") == n), None)
    return q["rot"] if q else None
for name, frames in data.items():
    print("==", name, len(frames), "samples")
    for part in ("body", "right_arm", "left_arm", "head"):
        series = [rot(f, part) for f in frames]
        if series[0] is None: print("  no", part); continue
        for axis, lab in enumerate("xyz"):
            v = [math.degrees(s[axis]) for s in series]
            jumps = [abs(v[i + 1] - v[i]) for i in range(len(v) - 1)]
            print(f"  {part:9}.{lab}: min {min(v):7.1f} max {max(v):7.1f} range {max(v)-min(v):5.1f}  biggest step {max(jumps):5.1f}")
