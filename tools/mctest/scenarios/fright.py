"""Fright at a sound: a start (sculk sensor), a scare (creeper's fuse), a terror (shrieker), each from ahead,
from the right and from behind - the model every tick through the fright and out of it, and shots at the held pose.
fright.py <tag> prints the steps; read the [Fright] decisions and [FrightTrace]/[FrightStance] in the log. On the pad at y 160."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
MODEL = {"model": {"entity": "player", "depth": 2}}
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "ride @s dismount"}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": True, "fright.getUsedTo": False}},
     {"cmd": "fill 440 160 36 459 162 44 air"}, {"cmd": "fill 440 159 36 459 159 44 smooth_stone"},
     {"cmd": "kill @e[type=villager,distance=..40]"}, {"cmd": "tp @s 447.5 160 38.5 0 0"}, {"look": [0, 0]},
     {"camera": "back"}, {"hideGui": True}, {"wait": 40}]
SOUNDS = (("start", "block.sculk_sensor.clicking"), ("scare", "entity.creeper.primed"), ("terror", "block.sculk_shrieker.shriek"))
PLACES = (("ahead", "447.5 161 41.5"), ("right", "444.5 161 38.5"), ("behind", "447.5 161 35.5"))
for name, sound in SOUNDS:
    for place, at in PLACES:
        s += [{"log": f"fright {name} {place}"}, {"cmd": f"playsound minecraft:{sound} master @s {at} 1"}]
        for i in range(70): s += [{"wait": 1}, MODEL]
        s += [{"wait": 20}]
    # the held pose from four sides: a sound ahead for each view
    for label, angle in (("front", 180), ("side", 90), ("back", 0), ("other", 270)):
        s += [{"orbit": [angle, 12, 3.2]}, {"wait": 50}, {"cmd": f"playsound minecraft:{sound} master @s 444.5 161 38.5 1"}, {"wait": 14},
              {"screenshot": f"fright-{tag}-{name}-{label}"}, {"wait": 30}]
    s += [{"camera": "back"}]
s += [{"log": "fright end"}, {"hideGui": False}, {"cmd": "gamemode creative"}, {"config": {"fright.getUsedTo": True, "footgrounding.trace": False}}]
print(json.dumps(s))
