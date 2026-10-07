"""Plant reach limited to crops (and the "all plants" option) and sign reading, on a pad at y 160 over the cart
track. plants_signs.py <tag> prints the steps; read the [PlantReach] and [SignRead] decisions in the log."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
MODEL = {"model": {"entity": "player", "depth": 2}}
def look_at(px, py, pz, tx, ty, tz):
    import math
    dx, dy, dz = tx - px, ty - (py + 1.62), tz - pz
    return [round(math.degrees(math.atan2(-dx, dz)), 1), round(-math.degrees(math.atan2(dy, math.hypot(dx, dz))), 1)]
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "ride @s dismount"}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "plantreach.all": False}},
     {"cmd": "fill 443 159 28 459 164 41 air"}, {"cmd": "fill 443 159 28 459 159 41 smooth_stone"},
     {"cmd": "fill 446 159 30 450 159 34 farmland[moisture=7]"}, {"cmd": "fill 446 160 30 450 160 34 wheat[age=7]"},
     {"cmd": "fill 452 159 30 456 159 34 grass_block"}, {"cmd": "fill 452 160 30 456 160 34 tall_grass[half=lower]"}, {"cmd": "fill 452 161 30 456 161 34 tall_grass[half=upper]"},
     {"cmd": "fill 453 160 31 455 161 31 air"}, {"cmd": "fill 453 160 31 455 160 31 poppy"},
     {"cmd": "setblock 448 160 38 oak_sign[rotation=8]{front_text:{messages:['\"Read\"','\"me\"','\"\"','\"\"']}}"},
     {"cmd": "setblock 452 161 39 stone"}, {"cmd": "setblock 452 161 38 oak_wall_sign[facing=north]"},
     {"cmd": "setblock 456 162 38 stone"}, {"cmd": "setblock 456 161 38 oak_hanging_sign[rotation=8]"},
     {"camera": "back"}, {"hideGui": True}, {"wait": 10}]
# plants
# (on foot: a creative player teleported a sixteenth over farmland hangs there flying, and nothing is in reach)
s += [{"cmd": "gamemode survival"}]
for name, at, conf in (("wheat", (448.5, 159.9375, 32.5), None), ("grass", (454.5, 160, 32.5), None),
                       ("flower", (454.5, 160, 31.5), None), ("grass-all", (454.5, 160, 32.5), True),
                       ("wheat-all", (448.5, 159.9375, 32.5), True)):
    if conf is not None: s += [{"config": {"plantreach.all": conf}}]
    s += [{"cmd": "tp @s %s %s %s 0 0" % at}, {"wait": 5}, {"log": f"case {name}"}, {"wait": 25},
          {"orbit": [150, 15, 2.6]}, {"wait": 3}, {"screenshot": f"ps-{tag}-{name}"}, {"orbit": False}]
s += [{"config": {"plantreach.all": False}}, {"cmd": "gamemode creative"}]
# signs: the body square to the south, the look turned onto the sign
for name, at, sign in (("post", (446.5, 160, 36.0), (448.5, 160.83, 38.5)), ("wall", (450.5, 160, 36.2), (452.5, 161.5, 38.9)),
                       ("hanging", (454.2, 160, 36.0), (456.5, 161.35, 38.5))):
    yaw, pitch = look_at(*at, *sign)
    s += [{"cmd": "tp @s %s %s %s 0 0" % at}, {"wait": 10}, {"log": f"case sign-{name}"}, MODEL, {"look": [yaw, pitch]}, {"wait": 30}, MODEL,
          {"orbit": [150, 15, 2.8]}, {"wait": 3}, {"screenshot": f"ps-{tag}-sign-{name}"},
          {"orbit": [0, 80, 3.2]}, {"wait": 3}, {"screenshot": f"ps-{tag}-sign-{name}-top"}, {"orbit": False},
          {"look": [yaw + 60, 0]}, {"wait": 20}, MODEL]
s += [{"log": "case end"}, {"hideGui": False}]
print(json.dumps(s))
