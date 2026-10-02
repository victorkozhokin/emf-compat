"""Grounded two-hand wheel takes. GUI and screens hidden; close oblique camera."""
import json
from pathlib import Path
from interaction_regression import scene, setup


def shots(name, count=24, every=2, drag=None):
    steps = []
    for i in range(count):
        if drag is not None:
            steps.append({"steeringDrag": drag})
        steps += [{"wait": every}, {"model": "player"}, {"screenshot": f"wheel-stance-{name}-{i:02}"}]
    return steps


def cases():
    steps = [{"steeringRelease":True}] + scene()
    for name, block, by, crouch, x, height in [
        ("valve-standing", "create:copper_valve_handle[facing=west]",151,False,350.9,.5),
        ("valve-crouch", "create:red_valve_handle[facing=west]",150,True,350.9,.5),
        ("steering-standing", "simulated:steering_wheel[facing=west,on_floor=false]",151,False,350.5,.2),
        ("steering-crouch", "simulated:steering_wheel[facing=west,on_floor=false]",150,True,350.5,.2),
        ("steering-floor", "simulated:steering_wheel[facing=west,on_floor=true]",150,False,350.3,.7),
    ]:
        take = setup(name,block,by=by,x=x,height=height,crouch=crouch)
        lastlook = next(s["look"] for s in take if "look" in s)
        take = [{"orbit":[60,10,3]} if "orbit" in s else s for s in take]
        take += [{"look":lastlook},{"log":"wheelcase:"+name},{"click":"use"}]
        steps += take
        if name.startswith("valve"):
            steps += shots(name)
            # Create reverses its valve when crouching; test the opposite direction too.
            steps += [{"release":"sneak"} if crouch else {"hold":"sneak"},{"click":"use"}]
            steps += shots(name+"-reverse")
        else:
            steps += [{"wait":5}] + shots(name,drag=40) + shots(name+"-reverse",count=48,drag=-40)
            # Keep the driver's look independent of the rim while the mod's hold is active.
            steps += [{"look":[-70,-10]}] + shots(name+"-look-away",count=8,drag=40)
            steps += [{"steeringRelease":True}]
        steps += [{"log":"wheelend:"+name},{"closeScreen":True},{"releaseAll":True},
                  {"look":[-90,-80]},{"wait":45},{"model":"player"},
                  {"screenshot":"wheel-stance-"+name+"-release"},{"log":"wheelreleased:"+name}]
    return steps


if __name__ == "__main__":
    Path(__file__).with_name("atlas-wheel-stance.json").write_text(json.dumps(cases(),indent=2)+"\n")
