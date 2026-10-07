"""Leaning on a fence and on a wall: stood up against it, the lean after a moment, and stepping away.
fence_lean.py <tag> prints the steps; read the [FenceLean] decisions in the log. On the pad at y 160."""
import json, sys
from ride_common import four_sides
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
MODEL = {"model": {"entity": "player", "depth": 2}}
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "ride @s dismount"}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False}},
     {"cmd": "fill 443 160 41 459 162 41 air"}, {"cmd": "fill 443 159 41 459 159 41 smooth_stone"},
     {"cmd": "fill 444 160 41 450 160 41 oak_fence"}, {"cmd": "fill 452 160 41 457 160 41 cobblestone_wall"},
     {"cmd": "setblock 458 160 39 oak_fence"},
     {"camera": "back"}, {"hideGui": True}, {"wait": 10}]
# up against the fence, square on; the same at a wall; at an angle; a lone post (no place for the hands)
for name, x, z, yaw in (("fence", 447.5, 41.07, 0), ("wall", 454.5, 41.07, 0), ("fence-angle", 447.2, 41.07, 20), ("post", 458.5, 39.07, 0),
                        ("fence-far", 447.5, 40.7, 0)):
    s += [{"cmd": f"tp @s {x} 160 {z} {yaw} 10"}, {"look": [yaw, 10]}, {"wait": 4}, {"log": f"lean {name}"}, MODEL, {"wait": 36}, MODEL] + \
         four_sides(f"lean-{tag}", name, pitch=12, distance=2.6)
# stepping back from the fence: how soon the hands are off it
s += [{"cmd": "tp @s 447.5 160 41.07 0 10"}, {"look": [0, 10]}, {"wait": 40}, {"log": "lean leave"}, {"hold": "back"}]
for i in range(8): s += [{"wait": 1}, MODEL]
s += [{"release": "back"}, {"wait": 20}]
# looking away without a step: the feet must come home by a step each ([FenceStance] step 0/1), not by a slide
s += [{"config": {"footgrounding.trace": True}}, {"cmd": "tp @s 447.5 160 41.07 0 10"}, {"look": [0, 10]}, {"wait": 50}, {"log": "lean look away"}, {"look": [75, 10]}, {"wait": 40},
      {"log": "lean looked away"}, {"config": {"footgrounding.trace": False}}]
s += [{"log": "lean end"}, {"hideGui": False}, {"cmd": "gamemode creative"}]
print(json.dumps(s))
