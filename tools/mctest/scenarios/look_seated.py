"""Looking at a creature while sat down with Take a Seat (its key, X by default): the head must stay on the neck.
look_seated.py <tag> prints the steps. On the pad at y 160."""
import json, sys
from ride_common import four_sides
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
MODEL = {"model": {"entity": "player", "depth": 2}}
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "ride @s dismount"}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": True}},
     {"cmd": "fill 440 160 36 459 162 44 air"}, {"cmd": "fill 440 159 36 459 159 44 smooth_stone"},
     {"cmd": "kill @e[type=villager,distance=..40]"}, {"cmd": "summon villager 444.5 160 40.5 {NoAI:1b,Silent:1b}"},
     {"cmd": "tp @s 447.5 161 38.5 0 0"}, {"look": [0, 0]}, {"camera": "back"}, {"hideGui": True}, {"wait": 40},
     {"click": "key.takeaseat.sit"}, {"wait": 60}, {"log": "seated before"}, MODEL] + four_sides(f"seat-{tag}", "before", pitch=15, distance=3.0)
s += [{"wait": 70}, {"log": "seated looking"}, MODEL] + four_sides(f"seat-{tag}", "looking", pitch=15, distance=3.0)
s += [{"look": [15, 0]}, {"wait": 20}, {"log": "seated back"}, MODEL, {"click": "key.takeaseat.sit"}, {"click": "sneak"}, {"wait": 10}, {"hideGui": False}, {"cmd": "gamemode creative"}]
print(json.dumps(s))
