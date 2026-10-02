"""Long standing/crouched wall walks, capturing each tick with full Test settings."""
import json
from pathlib import Path

def cases():
    s=[{"closeScreen":True},{"releaseAll":True},{"cmd":"gamemode survival"},{"cmd":"clear @s"},
       {"cmd":"effect give @s instant_health 1 10 true"},{"cmd":"effect give @s saturation 2 10 true"},
       {"wait":40},{"hideGui":True},{"camera":"back"},
       {"config":{"wallhand.squeeze":True,"wallhand.enabled":True,
                  "wallhand.trace":True,"footgrounding.trace":True}}]
    for pose in ["standing","crouching"]:
        for name,x,yaw in [("centre",2315.5,0),("off-centre",2315.59375,0),("turned",2315.5,12)]:
            s += [{"releaseAll":True},{"cmd":f"tp @s {x} 151 2071.5 {yaw} 0"},
                  {"look":[yaw,0]},{"orbit":[0,8,3.5]}]
            if pose=="crouching":s += [{"hold":"sneak"}]
            s += [{"wait":30}]
            for phase,count in [("still",30),("walk",60)]:
                if phase=="walk":s += [{"hold":"forward"}]
                take=f"{pose}-{name}-{phase}"
                s += [{"log":"wallvideo:"+take}]
                for i in range(count):
                    s += [{"wait":1},{"state":True},{"model":"player"},{"screenshot":f"{take}-{i:02}"}]
            s += [{"release":"forward"},{"wait":20}]
    return s+[{"releaseAll":True}]

if __name__=="__main__":
    p=Path("build/wall-video-review");p.mkdir(parents=True,exist_ok=True)
    (p/"steps.json").write_text(json.dumps(cases(),indent=2)+"\n")
