"""Measure actual FA palm contact and detect accumulated high-Crank scaling.

Usage: python3 tools/mctest/scenarios/crank_contact_review.py path/to/final.log
Run atlas-standing-reach.json with footgrounding.trace enabled first.
"""
import json
import re
import sys
from pathlib import Path


def review(path):
    cases = {}
    current = None
    scales = []
    for line in Path(path).read_text().splitlines():
        if "standingcase:" in line:
            current = line.split("standingcase:", 1)[1]
            cases[current] = []
        if "standingend:" in line:
            current = None
        if "[TiptoeTrace]" in line or "[PalmTrace]" in line:
            values = {k: float(v) for k, v in re.findall(r"(\w+)=([\d.E+-]+)", line)}
            assert all(abs(v) < 1e6 for v in values.values()), values
            if "[TiptoeTrace]" in line:
                scales.append(values)
                assert .999 <= values["bodyScale"] <= 1.1001, values
                assert .999 <= values["rightScale"] <= 1.1201, values
                assert .999 <= values["leftScale"] <= 1.1201, values
            elif current and values["weight"] >= .99:
                cases[current].append(values)
    result = {}
    for name, samples in cases.items():
        if "lever" in name:
            continue
        assert len(samples) >= 20, (name, len(samples))
        result[name] = {"samples": len(samples), "maxPalmGapPixels": max(v["gap"] for v in samples)}
    assert result["standing-low"]["maxPalmGapPixels"] < .05, result
    assert max(v["weight"] for v in scales) > .99, "High reach never activated"
    assert scales[-1]["weight"] < .001, "High pose did not release"
    for key in ("bodyScale", "rightScale", "leftScale"):
        assert abs(scales[-1][key] - 1) < 1e-4, scales[-1]
    result["releasedScales"] = scales[-1]
    return result


if __name__ == "__main__":
    print(json.dumps(review(sys.argv[1]), indent=2))
