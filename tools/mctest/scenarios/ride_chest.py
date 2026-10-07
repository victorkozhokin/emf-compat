"""Four-sided shots in a boat with a chest, at rest and rowing. ride_chest.py <tag> [raft] prints the steps."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
wood = "bamboo" if "raft" in sys.argv[2:] else "oak"
def shots(name):
    out = []
    for ang, lab in ((90, "side"), (180, "front"), (0, "back"), (-90, "other")):
        out += [{"orbit": [ang, 18, 3.6]}, {"wait": 3}, {"screenshot": f"chest-{tag}-{name}-{lab}"}]
    return out + [{"orbit": [90, 80, 4.0]}, {"wait": 3}, {"screenshot": f"chest-{tag}-{name}-top"}, {"orbit": False}]
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": True}},
     {"cmd": "tp @s 450.5 150 7.5 -90 0"}, {"camera": "back"}, {"hideGui": True}, {"wait": 30},
     {"cmd": "kill @e[type=boat,distance=..60]"}, {"cmd": "kill @e[type=chest_boat,distance=..60]"},
     {"cmd": "fill 440 150 3 462 154 12 air"}, {"cmd": "fill 440 148 3 462 149 12 smooth_stone"},
     {"cmd": "fill 444 149 5 458 149 9 water"}, {"wait": 10},
     {"cmd": "summon chest_boat 447.5 150 7.5 {Type:\"%s\",Rotation:[-90f,0f]}" % wood}, {"wait": 10},
     {"cmd": "ride @s mount @e[type=chest_boat,limit=1,sort=nearest]"}, {"wait": 40},
     {"log": "ride chest-idle"}] + shots("idle")
s += [{"log": "ride chest-row"}, {"hold": "forward"}, {"wait": 12}] + shots("row") + [{"wait": 6}] + shots("row2") + [{"release": "forward"}, {"wait": 20}]
s += [{"camera": "first"}, {"wait": 5}, {"screenshot": f"chest-{tag}-first"}, {"camera": "back"}]
s += [{"log": "ride end"}, {"hideGui": False}]
print(json.dumps(s))
