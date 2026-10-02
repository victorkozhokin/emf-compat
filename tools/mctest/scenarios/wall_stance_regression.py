"""Wall crouch retreat and foot repositioning; use the existing Atlas stands."""
import json
from pathlib import Path

def frames(name,count=16):
    s=[{"log":"wallstancecase:"+name}]
    for i in range(count):
        s += [{"wait":2},{"state":True},{"model":"player"},{"screenshot":f"{name}-{i:02}"}]
    return s

def cases():
    s=[{"closeScreen":True},{"releaseAll":True},{"cmd":"gamemode survival"},{"cmd":"clear @s"},
       {"cmd":"effect give @s instant_health 1 10 true"},{"cmd":"effect give @s saturation 2 10 true"},
       {"wait":40},{"hideGui":True},{"camera":"back"},
       {"config":{"wallhand.squeeze":True,"wallhand.trace":True,"footgrounding.trace":True,
                  "parcool.enabled":False,"lookat.enabled":False}}]
    for name,x,z,yaw in [("narrow-forward",2315.59375,2071.5,0),
                         ("narrow-reverse",2315.59375,2080.5,180),
                         ("external-right",2307.31,2071.5,0),
                         ("external-left",2303.69,2071.5,0),
                         ("fence",2251.94,2071.5,0)]:
        s += [{"releaseAll":True},{"cmd":f"tp @s {x} 151 {z} {yaw} 0"},{"look":[yaw,0]},
              {"orbit":[0 if name.startswith("narrow") else 15,8,3.5]},
              {"hold":"sneak"},{"wait":12}]
        s += frames(name+"-settle",24)
        s += [{"hold":"forward"}]+frames(name+"-walk",20)
        s += [{"releaseAll":True},{"hold":"sneak"},{"wait":12},{"hold":"back"}]+frames(name+"-back",12)
        s += [{"releaseAll":True}]+frames(name+"-standing",10)
    s += [{"releaseAll":True},{"cmd":"tp @s 2318.5 151 2090.5 0 0"},{"look":[0,0]},
          {"hold":"sneak"},{"wait":30}]+frames("clear",12)
    s += [{"config":{"wallhand.squeeze":False}},{"cmd":"tp @s 2315.59375 151 2071.5 0 0"},
          {"wait":30}]+frames("disabled",12)
    s += [{"releaseAll":True},{"config":{"wallhand.squeeze":True,"wallhand.trace":False,"footgrounding.trace":False}}]
    return s

if __name__=="__main__":Path(__file__).with_name("atlas-wall-stance.json").write_text(json.dumps(cases(),indent=2)+"\n")
