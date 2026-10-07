"""A bot as the passenger in the bow while we row, then the other way round. ride_boat_passenger.py <tag> prints the steps."""
import json, sys
tag = sys.argv[1]
def shots(name):
    out = []
    for ang, lab in ((90, "side"), (180, "front"), (0, "back"), (60, "quarter")):
        out += [{"orbit": [ang, 18, 4.2]}, {"wait": 3}, {"screenshot": f"pass-{tag}-{name}-{lab}"}]
    return out + [{"orbit": False}]
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": False, "wallhand.trace": False, "transport.trace": False}},
     {"cmd": "tp @s 450.5 150 7.5 -90 0"}, {"camera": "back"}, {"hideGui": True}, {"wait": 30},
     {"cmd": "kill @e[type=boat,distance=..80]"}, {"cmd": "fill 432 149 1 470 149 14 water"}, {"wait": 10},
     {"cmd": "summon boat 440.5 150 7.5 {Type:\"oak\",Rotation:[-90f,0f]}"}, {"wait": 10},
     {"bot": {"spawn": "Bob", "at": [440.5, 150, 5.0], "look": [-90, 0]}}, {"wait": 10},
     # I row, Bob rides
     {"cmd": "ride @s mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 10}, {"cmd": "ride Bob mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 40},
     {"log": "pass idle"}] + shots("idle")
s += [{"log": "pass row"}, {"hold": "forward"}, {"wait": 18}] + shots("row-a") + [{"wait": 5}] + shots("row-b") + [{"release": "forward"}, {"wait": 20}]
# the other way: Bob rows (he cannot paddle), I ride
s += [{"cmd": "ride @s dismount"}, {"cmd": "ride Bob dismount"}, {"wait": 10}, {"cmd": "tp @e[type=boat,limit=1,sort=nearest] 446.5 150 7.5 -90 0"}, {"wait": 10},
      {"cmd": "ride Bob mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 10}, {"cmd": "ride @s mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 40},
      {"log": "pass me-in-bow"}, {"state": True}] + shots("me-bow")
s += [{"log": "pass end"}, {"cmd": "ride @s dismount"}, {"bot": {"name": "Bob", "remove": True}}, {"cmd": "kill @e[type=boat,distance=..80]"},
      {"hideGui": False}, {"cmd": "tp @s 450.5 150 7.5 -90 0"}]
print(json.dumps(s))
