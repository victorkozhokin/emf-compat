"""Walking beside and through a wheat field, for the hand's point on the plants frame by frame.
plants_walk.py <tag> prints the steps; read [PlantPoint] in the log (footgrounding.trace)."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "ride @s dismount"}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
     {"cmd": "fill 443 159 20 459 164 41 air"}, {"cmd": "fill 443 159 20 459 159 41 smooth_stone"},
     {"cmd": "fill 446 159 24 450 159 36 farmland[moisture=7]"}, {"cmd": "fill 446 160 24 450 160 36 wheat[age=7]"},
     {"camera": "back"}, {"hideGui": True},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "plantreach.all": False, "footgrounding.trace": True}}]
# along the outside of the field at three distances from it, then along its edge row and through its middle
for name, x in (("beside-near", 445.7), ("beside-touch", 445.85), ("beside", 445.45), ("beside-far", 445.2), ("edge-row", 446.5), ("middle", 448.5)):
    # (onto the pad's stone before the field: a sixteenth lower, as for farmland, is inside it, and the player falls through)
    s += [{"cmd": f"tp @s {x} 160 22.5 0 0"}, {"look": [0, 0]}, {"wait": 10}, {"log": f"walk {name}"},
          {"hold": "forward"}, {"until": {"z>": 38.0, "timeout": 120}}, {"release": "forward"}, {"wait": 10}]
# drifting in towards the field and into it, and out of it again: the hand's place has to change without a break
for name, x, yaw in (("drift-in", 444.6, -9), ("drift-out", 447.4, 9)):
    s += [{"cmd": f"tp @s {x} 160 22.5 {yaw} 0"}, {"look": [yaw, 0]}, {"wait": 10}, {"log": f"walk {name}"},
          {"hold": "forward"}, {"until": {"z>": 38.0, "timeout": 120}}, {"release": "forward"}, {"wait": 10}]
s += [{"log": "walk end"}, {"config": {"footgrounding.trace": False}}, {"hideGui": False}, {"cmd": "gamemode creative"}]
print(json.dumps(s))
