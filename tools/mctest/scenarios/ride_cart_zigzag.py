"""The user's minecart track of 07.10, closed into a loop: a powered straight, three zigzags of tight bends, a long
straight to a three-block drop onto rails below, back along the lower floor and up a powered slope to the start.
ride_cart_zigzag.py <tag> [shots] [build] prints the steps: `build` only lays the track and seats the player.
Reads from [CartTrace] (footgrounding.trace)."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
flags = sys.argv[2:]
rails = []
def lay(points, y=150, kind="rail", under=None):
    for x, z, shape in points:
        if under: rails.append({"cmd": f"setblock {x} {y - 1} {z} {under}"})
        rails.append({"cmd": f"setblock {x} {y} {z} {kind}[shape={shape}]"})
P = dict(kind="powered_rail", under="redstone_block")
# the upper level: the start straight, the zigzags, the long straight to the edge
lay([(443, 30, "east_west")])
lay([(x, 30, "east_west") for x in range(444, 453)], **P)
lay([(453, 30, "east_west"), (454, 30, "east_west"), (455, 30, "north_west"), (455, 29, "north_south"), (455, 28, "north_south"),
     (455, 27, "south_east"), (456, 27, "east_west"), (457, 27, "south_west"), (457, 28, "north_south"), (457, 29, "north_south"),
     (457, 30, "north_south"), (457, 31, "north_east"), (458, 31, "east_west"), (459, 31, "north_west"), (459, 30, "north_south"),
     (459, 29, "north_south"), (459, 28, "north_south"), (459, 27, "south_east"), (460, 27, "east_west"), (461, 27, "south_west")])
lay([(461, z, "north_south") for z in range(28, 38)])
# the lower level, three blocks down: where the cart comes down, round and back
# (a cart lands with next to no speed left: powered from where it comes down)
lay([(461, z, "north_south") for z in range(38, 41)], y=147)
lay([(461, z, "north_south") for z in range(41, 47)], y=147, **P)
lay([(461, 47, "north_west")], y=147)
lay([(x, 47, "east_west") for x in range(455, 461)], y=147, **P)
lay([(x, 47, "east_west") for x in range(449, 455)], y=147, **P)
lay([(x, 47, "east_west") for x in range(443, 449)], y=147)
lay([(442, 47, "north_east")], y=147)
lay([(442, z, "north_south") for z in range(41, 47)], y=147, **P)
# up the slope to the upper level and round to the start
lay([(442, 40, "ascending_north")], y=147, **P)
lay([(442, 39, "ascending_north")], y=148, **P)
lay([(442, 38, "ascending_north")], y=149, **P)
lay([(442, z, "north_south") for z in range(34, 38)], **P)
lay([(442, z, "north_south") for z in range(31, 34)])
lay([(442, 30, "south_east")])
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": "build" not in flags}},
     {"cmd": "ride @s dismount"}, {"cmd": "kill @e[type=minecart,distance=..80]"},
     {"cmd": "tp @s 445.5 151 28.5 -90 0"}, {"camera": "back"}, {"hideGui": "build" not in flags}, {"wait": 20},
     {"cmd": "fill 438 145 24 466 156 50 air"}, {"cmd": "fill 438 149 24 466 149 37 smooth_stone"},
     {"cmd": "fill 438 146 38 466 146 50 smooth_stone"}] + rails + [
     {"wait": 10}, {"cmd": "kill @e[type=item,distance=..80]"},
     {"cmd": "summon minecart 445.5 150.1 30.5"}, {"wait": 10},
     {"cmd": "ride @s mount @e[type=minecart,limit=1,sort=nearest]"}, {"wait": 30}]
if "build" not in flags:
    s += [{"orbit": [0, 88, 3.4]}, {"wait": 3}, {"log": "ride cart-go"}, {"hold": "forward"}, {"wait": 20}, {"release": "forward"}]
    if "shots" in flags:
        for i in range(60):
            s += [{"wait": 2}, {"screenshot": f"zig-{tag}-{i:02d}"}]
        s += [{"wait": 280}]
    else:
        s += [{"wait": 400}]
    s += [{"state": True}, {"log": "ride end"}, {"orbit": False}, {"hideGui": False},
          {"config": {"footgrounding.trace": False}}]
print(json.dumps(s))
