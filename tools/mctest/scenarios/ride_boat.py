"""Rowing: both paddles, the left alone, the right alone; the model sampled every tick. ride_boat.py <tag> prints the steps."""
import json, sys
tag = sys.argv[1]
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": False, "wallhand.trace": False, "transport.trace": False}},
     {"cmd": "tp @s 450.5 150 7.5 -90 0"}, {"camera": "back"}, {"hideGui": True}, {"wait": 30},
     {"cmd": "kill @e[type=boat,distance=..60]"},
     {"cmd": "fill 436 150 2 466 154 13 air"}, {"cmd": "fill 436 148 2 466 149 13 smooth_stone"}, {"cmd": "fill 438 149 3 464 149 12 water"}, {"wait": 10},
     {"cmd": "summon boat 441.5 150 7.5 {Type:\"oak\",Rotation:[-90f,0f]}"}, {"wait": 10}, {"cmd": "ride @s mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 40}]
def take(name, key, ticks=34, shots=True):
    out = [{"log": "row " + name}, {"orbit": [90, 10, 3.4]}, {"hold": key}, {"wait": 24}]
    for i in range(ticks):
        out += [{"model": "player"}]
        if shots and i < 16: out += [{"screenshot": f"row-{tag}-{name}-{i:02}"}]
        out += [{"wait": 1}]
    return out + [{"release": key}, {"orbit": False}, {"log": "row " + name + " end"}, {"wait": 30}]
s += take("both", "forward")
s += [{"cmd": "tp @e[type=boat,limit=1,sort=nearest] 451.5 150 7.5 -90 0"}, {"wait": 20}]
s += take("left", "left", shots=True)
s += [{"cmd": "tp @e[type=boat,limit=1,sort=nearest] 451.5 150 7.5 -90 0"}, {"wait": 20}]
s += take("right", "right", shots=False)
s += [{"log": "row end"}, {"cmd": "ride @s dismount"}, {"cmd": "kill @e[type=boat,distance=..60]"}, {"cmd": "fill 438 149 3 464 149 12 smooth_stone"},
      {"hideGui": False}, {"cmd": "gamemode survival"}, {"cmd": "tp @s 450.5 150 7.5 -90 0"}]
print(json.dumps(s))
