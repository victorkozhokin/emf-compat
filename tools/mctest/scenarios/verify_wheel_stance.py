"""Verify two-hand ownership, alternating regrips, grounded release and torso bounds.

python3 tools/mctest/scenarios/verify_wheel_stance.py build/wheel-stance-review
"""
import json
import math
import re
import sys
from pathlib import Path


def numbers(line):
    return {k:float(v) for k,v in re.findall(r"(\w+)=([-+\d.E]+)",line)}


def verify(directory):
    directory=Path(directory)
    cases={}
    name=None
    releasing=False
    for line in (directory/"final.log").read_text().splitlines():
        if "wheelcase:" in line:
            name=line.split("wheelcase:",1)[1]
            cases[name]={"weights":[],"regrips":[],"stances":[],"release":None}
            releasing=False
        if "wheelend:" in line:
            releasing=True
        if "wheelreleased:" in line:
            name=None
        if name:
            if "[WheelTrace]" in line and not releasing:
                cases[name]["weights"].append(numbers(line))
            if "[RegripTrace]" in line and not releasing:
                cases[name]["regrips"].append(numbers(line))
            if "[StanceTrace]" in line:
                if releasing:cases[name]["release"]=line
                else:cases[name]["stances"].append(line)
            assert "out-of-reach" not in line, line
    result={}
    for name,case in cases.items():
        weights=case["weights"][5:]
        assert len(weights)>20, name
        minimum=min(min(v["mainWeight"],v["supportWeight"]) for v in weights)
        assert minimum>.99,(name,minimum)
        release=case["release"]
        assert release is not None,name
        for side in ("right","left"):
            values=[float(v) for v in re.search(side+r"=\(([^)]*)\)",release)[1].split()]
            assert max(abs(v) for v in values)<.01,(name,side,values)
        assert abs(numbers(release)["load"])<.001,(name,release)
        assert any("turning=true" in line for line in case["stances"]),name
        if case["regrips"]:
            assert any(v["moving"]==0 for v in case["regrips"]),name
            assert any(v["moving"]==1 for v in case["regrips"]),name
            for values in case["regrips"]:
                assert not(values["rightLift"]>0 and values["leftLift"]>0),(name,values)
                assert abs(values["rightPhase"])<math.pi/2,(name,values)
                assert abs(values["leftPhase"])<math.pi/2,(name,values)
        result[name]={"minimumHandWeight":minimum,"transfers":max((v["transfers"] for v in case["regrips"]),default=0)}
    response=json.loads((directory/"final.json").read_text())
    assert not response.get("error"),response
    for step in response["results"]:
        assert "error" not in step,step
        if "model" in step:
            parts=step["model"]["parts"]
            hips=(parts["right_leg"]["pos"][1]+parts["left_leg"]["pos"][1])/2
            assert parts["body"]["pos"][1]<hips,"Torso moved below the hips"
    return result


if __name__=="__main__":
    print(json.dumps(verify(sys.argv[1]),indent=2))
