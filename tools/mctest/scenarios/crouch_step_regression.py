"""Crouch clearance and grounded stair ascent regression; sandbox world only."""
from footik_regression import scene


def cases():
    out = scene() + [
        {"cmd":"effect give @s minecraft:instant_health 1 10 true"},
        {"cmd":"effect give @s minecraft:saturation 2 10 true"}, {"wait":40},
        {"cmd":"clear @s"}, {"hideGui":True}, {"camera":"back"},
        {"config":{"wallhand.squeeze":True,"wallhand.trace":True}},
    ]
    for name,x,z in [("wall-right",2307.31,2071.5), ("wall-left",2303.69,2071.5),
                     ("narrow",2315.59375,2071.5), ("fence",2251.94,2071.5)]:
        for enabled in [False,True]:
            label=f"{name}-{enabled}"
            out += [{"releaseAll":True}, {"cmd":f"tp @s {x} 151 {z} 0 0"},
                    {"look":[0,0]}, {"hold":"sneak"}, {"orbit":[180 if name=="narrow" else 145,8,3.5]},
                    {"config":{"wallhand.squeeze":enabled}}, {"wait":40},
                    {"log":f"clearancecase:{label}"}, {"model":"player"},
                    {"state":True}, {"screenshot":label}]
            if enabled:
                out += [{"hold":"forward"}]
                for i in range(10):
                    out += [{"wait":2}, {"model":"player"}, {"state":True},
                            {"screenshot":f"{label}-walk-{i:02}"}]
                out += [{"releaseAll":True}, {"wait":30}, {"model":"player"}, {"state":True}]
    out += [{"config":{"wallhand.squeeze":True}}]
    for name,x,y,key,yaw,extra,count in [
        ("stairs-up",303.8,150,"forward",-90,None,45),
        ("stairs-down",308,152,"forward",90,None,45),
        ("stairs-back",303.8,150,"back",90,None,45),
        ("stairs-sprint",303.8,150,"forward",-90,"sprint",30),
        ("stairs-crouch",304.3,150,"forward",-90,"sneak",70),
    ]:
        out += [{"releaseAll":True},{"cmd":f"tp @s {x} {y} 15 {yaw} 0"},
                {"look":[yaw,0]}, {"wait":30},{"orbit":[90,5,3]},
                {"log":name+" START"}, {"hold":key}]
        if extra:out += [{"hold":extra}]
        for i in range(count):
            out += [{"wait":1},{"state":True},{"model":"player"}]
            if i%3==0:out += [{"screenshot":f"{name}-{i:02}"}]
        out += [{"releaseAll":True},{"log":name+" END"}]
    out += [{"config":{"wallhand.trace":False,"footgrounding.trace":False}}]
    return out

if __name__ == "__main__":
    import json
    from pathlib import Path
    Path(__file__).with_name("atlas-crouch-step.json").write_text(json.dumps(cases(),indent=2)+"\n")
