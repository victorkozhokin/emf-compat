"""The user's minecart track of 07.10: a powered straight, three zigzags of tight bends, a long straight to a
three-block drop. ride_cart_zigzag.py <tag> [shots] prints the steps; reads from [CartTrace] (footgrounding.trace)."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
rails = []
def run(points, kind="rail"):
    for x, z, shape in points:
        rails.append({"cmd": f"setblock {x} 150 {z} {kind}[shape={shape}]"})
run([(x, 30, "east_west") for x in range(444, 453)], "powered_rail")
run([(453, 30, "east_west"), (454, 30, "east_west"), (455, 30, "north_west"), (455, 29, "north_south"), (455, 28, "north_south"),
     (455, 27, "south_east"), (456, 27, "east_west"), (457, 27, "south_west"), (457, 28, "north_south"), (457, 29, "north_south"),
     (457, 30, "north_south"), (457, 31, "north_east"), (458, 31, "east_west"), (459, 31, "north_west"), (459, 30, "north_south"),
     (459, 29, "north_south"), (459, 28, "north_south"), (459, 27, "south_east"), (460, 27, "east_west"), (461, 27, "south_west")])
run([(461, z, "north_south") for z in range(28, 38)])
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": True}},
     {"cmd": "ride @s dismount"}, {"cmd": "kill @e[type=minecart,distance=..80]"},
     {"cmd": "tp @s 445.5 151 28.5 -90 0"}, {"camera": "back"}, {"hideGui": True}, {"wait": 20},
     {"cmd": "fill 440 145 24 466 156 46 air"}, {"cmd": "fill 440 149 24 466 149 37 smooth_stone"},
     {"cmd": "fill 440 146 38 466 146 46 smooth_stone"},
     {"cmd": "fill 444 149 30 452 149 30 redstone_block"}, {"cmd": "setblock 443 150 30 stone"}] + rails + [
     {"wait": 10}, {"cmd": "kill @e[type=item,distance=..80]"},
     {"cmd": "summon minecart 445.5 150.1 30.5"}, {"wait": 10},
     {"cmd": "ride @s mount @e[type=minecart,limit=1,sort=nearest]"}, {"wait": 30},
     {"orbit": [0, 88, 3.4]}, {"wait": 3}, {"log": "ride cart-go"}, {"hold": "forward"}]
if "shots" in sys.argv[2:]:
    for i in range(60):
        s += [{"wait": 2}, {"screenshot": f"zig-{tag}-{i:02d}"}]
else:
    s += [{"wait": 120}]
s += [{"release": "forward"}, {"wait": 30}, {"log": "ride end"},
      {"orbit": False}, {"hideGui": False}, {"config": {"footgrounding.trace": False}}]
print(json.dumps(s))
