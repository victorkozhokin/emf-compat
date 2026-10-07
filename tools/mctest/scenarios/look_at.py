"""Looking at a creature out of idleness: nothing while walking past and for the idle time after
stopping, then the head and - too far round for the neck - the body in steps; the way back on a
turn of the camera, tick by tick. look_at.py <tag> prints the steps; the idle time is the default 5 s.
Read the [LookAt] decisions and [LookTurn] traces in the log. On the pad at y 160."""
import json, sys
from ride_common import four_sides
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
MODEL = {"model": {"entity": "player", "depth": 2}}
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "ride @s dismount"}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": True, "footgrounding.trace": True}},
     {"cmd": "fill 440 160 36 459 162 44 air"}, {"cmd": "fill 440 159 36 459 159 44 smooth_stone"},
     {"cmd": "kill @e[type=villager,distance=..40]"}, {"cmd": "kill @e[type=armor_stand,distance=..40]"},
     {"camera": "back"}, {"hideGui": True}, {"wait": 10}]
# name, where the villager stands from a player at 447.5 38.5 looking +z (right is -x)
for name, vx, vz in (("ahead-right", 445.5, 42.0), ("far-right", 443.6, 39.2)):
    s += [{"cmd": "kill @e[type=villager,distance=..40]"}, {"cmd": f"summon villager {vx} 160 {vz} {{NoAI:1b,Silent:1b}}"},
          {"cmd": "tp @s 447.5 160 38.5 0 0"}, {"look": [0, 0]}, {"wait": 4}, {"log": f"look {name} start"}, {"wait": 80}, {"log": f"look {name} 4s"}, MODEL,
          {"wait": 30}]
    for i in range(40): s += [{"wait": 1}, MODEL]
    s += [{"log": f"look {name} on"}] + four_sides(f"look-{tag}", name, pitch=20, distance=3.2, top=(80, 4.0))
    s += [{"wait": 10}, {"log": f"look {name} camera"}, {"look": [12, 0]}]
    for i in range(30): s += [{"wait": 1}, MODEL]
    s += [{"log": f"look {name} back"}]
# walking past and stopping: nothing for the idle time
s += [{"cmd": "tp @s 447.5 160 36.5 0 0"}, {"look": [0, 0]}, {"wait": 4}, {"log": "look walk"}, {"hold": "forward"}, {"wait": 12}, {"release": "forward"},
      {"wait": 60}, {"log": "look walk 3s after"}, {"wait": 60}, {"log": "look walk 6s after"}, MODEL]
s += [{"log": "look end"}, {"hideGui": False}, {"cmd": "gamemode creative"}]
print(json.dumps(s))
