"""A mob as the second rider of a boat the player rows. ride_boat_mob.py <tag> [mob ...] prints the steps."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
mobs = sys.argv[2:] or ["villager", "pig", "cow", "wolf"]
def shots(name):
    out = []
    for ang, lab in ((90, "side"), (180, "front"), (0, "back"), (-90, "other")):
        out += [{"orbit": [ang, 18, 3.6]}, {"wait": 3}, {"screenshot": f"mob-{tag}-{name}-{lab}"}]
    return out + [{"orbit": [90, 80, 4.0]}, {"wait": 3}, {"screenshot": f"mob-{tag}-{name}-top"}, {"orbit": False}]
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": True}},
     {"cmd": "tp @s 450.5 150 7.5 -90 0"}, {"camera": "back"}, {"hideGui": True}, {"wait": 30},
     {"cmd": "fill 440 150 3 462 154 12 air"}, {"cmd": "fill 440 148 3 462 149 12 smooth_stone"},
     {"cmd": "fill 444 149 5 458 149 9 water"}, {"wait": 10}]
for mob in mobs:
    s += [{"cmd": "ride @s dismount"}, {"cmd": "kill @e[type=boat,distance=..60]"}, {"cmd": "kill @e[type=chest_boat,distance=..60]"},
          {"cmd": "kill @e[tag=boatmob]"}, {"wait": 5},
          {"cmd": "summon boat 447.5 150 7.5 {Type:\"oak\",Rotation:[-90f,0f]}"}, {"wait": 10},
          {"cmd": "ride @s mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 10},
          {"cmd": "summon %s 447.5 151 7.5 {Tags:[\"boatmob\"],NoAI:0b,Silent:1b}" % mob}, {"wait": 3},
          {"cmd": "ride @e[tag=boatmob,limit=1] mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 40},
          {"log": f"ride mob-{mob}-idle"}] + shots(f"{mob}-idle")
    s += [{"hold": "forward"}, {"wait": 12}] + shots(f"{mob}-row") + [{"release": "forward"}, {"wait": 20}]
s += [{"log": "ride end"}, {"hideGui": False}, {"config": {"footgrounding.trace": False}}]
print(json.dumps(s))
