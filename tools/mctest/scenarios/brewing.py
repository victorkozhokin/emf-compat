"""A brewing stand as a block used through its screen: the hand waiting at the holder on the player's
side, a bottle hung and taken off, an ingredient on the rod, powder on the base.
brewing.py <tag> prints the steps; read the BlockUse decisions in the log. On the pad at y 160."""
import json, sys
from ride_common import four_sides
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
MODEL = {"model": {"entity": "player", "depth": 2}}
AT = "447 160 41"
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "ride @s dismount"}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False}},
     {"cmd": "fill 443 160 41 459 162 41 air"}, {"cmd": "fill 443 159 41 459 159 41 smooth_stone"},
     {"cmd": f"setblock {AT} brewing_stand"}, {"cmd": "clear @s"},
     {"camera": "back"}, {"hideGui": True}, {"wait": 10}]
# looked at from three sides: the hand at the holder on that side
for name, x, z, yaw in (("south", 447.5, 39.9, 0), ("east", 448.6, 41.5, 90), ("west", 446.4, 41.5, -90)):
    s += [{"cmd": f"tp @s {x} 160 {z} {yaw} 40"}, {"look": [yaw, 40]}, {"wait": 4}, {"log": f"brew hover {name}"}, {"wait": 20}, MODEL] + \
         four_sides(f"brew-{tag}", name, pitch=12, distance=2.6)
# the screen up: what goes in and out of it
s += [{"cmd": "tp @s 447.5 160 40.4 0 50"}, {"look": [0, 50]}, {"wait": 10}, {"click": "use"}, {"wait": 10}, {"log": "brew screen"}]
for name, cmd in (("bottle0", "container.0 with potion"), ("bottle2", "container.2 with potion"), ("ingredient", "container.3 with nether_wart 4"),
                  ("fuel", "container.4 with blaze_powder 2"), ("take0", "container.0 with air"), ("take-ingredient", "container.3 with air")):
    s += [{"cmd": f"item replace block {AT} {cmd}"}, {"log": f"brew {name}"}, {"wait": 4}, {"screenshot": f"brew-{tag}-{name}"}, MODEL, {"wait": 26}]
s += [{"closeScreen": True}, {"log": "brew end"}, {"hideGui": False}, {"cmd": "gamemode creative"}]
print(json.dumps(s))
