"""Close-contact wheel takes; GUI windows stay open but their rendering is hidden."""
import json
from pathlib import Path

from interaction_regression import finish, scene, setup


def cases():
    result = {}
    for name, block, crouch in [
        ("valve-copper", "create:copper_valve_handle", False),
        ("valve-red", "create:red_valve_handle", False),
        ("valve-crouch", "create:copper_valve_handle", True),
    ]:
        result[name] = (setup(name, block + "[facing=west]", x=350.9, crouch=crouch)
                        + [{"click": "use"}, {"burst": {"count": 20, "every": 2, "name": name}},
                           {"state": True}] + finish(name, crouch))
    name = "steering"
    result[name] = (setup(name, "simulated:steering_wheel[facing=west,on_floor=false]", x=350.5, height=.2)
                    + [{"click": "use"}, {"wait": 5}, {"state": True}])
    for phase, delta, count in [("right", 50, 9), ("left", -50, 18), ("centre", 50, 9)]:
        for i in range(count):
            result[name] += [{"steeringDrag": delta}, {"wait": 2}, {"screenshot": f"{name}-{phase}-{i:02}"}]
        result[name] += [{"state": True}]
    result[name] += finish(name)
    for name, block, item, by, height in [
        ("barrel-clear", "barrel[facing=west]", "air", 151, .5),
        ("chest-clear", "chest[facing=west]", "air", 150, .8),
        ("lectern-clear", 'lectern[facing=west,has_book=true]{Book:{id:"minecraft:writable_book",count:1}}', "air", 150, .8),
        ("shelf-contact", "chiseled_bookshelf[facing=west]", "book", 151, .25),
    ]:
        result[name] = (setup(name, block, x=350.2 if name == "shelf-contact" else 350.3, item=item, by=by, height=height, aim_x=351)
                        + [{"click": "use"}, {"wait": 3}, {"state": True},
                           {"burst": {"count": 10, "every": 2, "name": name}}] + finish(name))
    for name, steps in result.items():
        if name.startswith(("valve", "steering")):
            result[name] = [{"orbit": [45, 10, 3]} if "orbit" in step else step for step in steps]
    return result


if __name__ == "__main__":
    for name, data in [("wheel-cases", cases()), ("wheel-scene", scene())]:
        Path(__file__).with_name(name + ".json").write_text(json.dumps(data, indent=2) + "\n")
