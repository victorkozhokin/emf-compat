"""Instant vanilla lever toggles, subtle grounded effort, hidden GUI."""
import json
from pathlib import Path
from interaction_regression import scene,setup


def cases():
    steps=scene()
    for name,block,by,crouch,x in [
        ("wall","lever[face=wall,facing=west]",151,False,350.7),
        ("floor","lever[face=floor,facing=west]",150,False,350.7),
        ("floor-crouch","lever[face=floor,facing=west]",150,True,350.7),
        ("high","lever[face=wall,facing=west]",152,False,350.7),
        ("side","lever[face=wall,facing=west]",151,False,350.7),
    ]:
        take=setup(name,block,by=by,crouch=crouch,x=x,height=.2 if "floor" in name else .5)
        if by==152:
            at=next(i for i,s in enumerate(take) if "setblock 351" in s.get("cmd",""))
            take.insert(at,{"cmd":"setblock 352 152 7 stone"})
        take=[{"orbit":[60,10,3]} if "orbit" in s else s for s in take]
        if name=="side":
            # Keep the torso facing along the lane; the lever sits beside the right shoulder.
            take += [{"cmd":"tp @s 350.7 150 7.88 -90 8.8"},{"look":[-90,8.8]},{"wait":15}]
        steps += take + [{"log":"levercase:"+name},{"model":"player"},{"screenshot":"lever-pose-"+name+"-hover"}]
        for toggle in ("on","off"):
            if name=="side":
                # Point the eyes at the lever for the click, then restore the body lane.
                steps += [{"look":[-115.4,8.8]}]
            steps += [{"click":"use"},{"wait":1},{"state":True}]
            if name=="side":steps += [{"look":[-90,8.8]}]
            for i in range(12):
                steps += [{"wait":1},{"model":"player"},{"screenshot":f"lever-pose-{name}-{toggle}-{i:02}"}]
            steps += [{"wait":15}]
        steps += [{"log":"leverend:"+name},{"cmd":f"setblock 351 {by} 7 air"},{"wait":45},
                  {"model":"player"},{"screenshot":"lever-pose-"+name+"-release"},
                  {"log":"leverreleased:"+name},{"releaseAll":True}]
    return steps


def fence_case():
    steps=scene()+setup("lever-fence","lever[face=wall,facing=west]",by=152,x=350.5)
    return steps+[
        {"cmd":"setblock 352 152 7 stone"},{"cmd":"setblock 350 150 7 oak_fence"},
        {"cmd":"tp @s 350.5 151.5 7.5 -90 35.5"},{"look":[-90,35.5]},
        {"orbit":[60,10,3]},{"wait":30},{"state":True},{"log":"levercase:fence"},
        {"click":"use"},{"wait":15},{"model":"player"},{"screenshot":"lever-pose-fence"},
        {"state":True},{"log":"leverend:fence"}]


if __name__=="__main__":
    Path(__file__).with_name("atlas-lever-pose.json").write_text(json.dumps(cases(),indent=2)+"\n")
    Path(__file__).with_name("atlas-lever-fence.json").write_text(json.dumps(fence_case(),indent=2)+"\n")
