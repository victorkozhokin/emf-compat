"""A minecart run: powered straight, a bend, a drop off the rails' end onto a floor three blocks down.
ride_cart.py <tag> prints the steps. Reads best from [CartTrace] in the log (footgrounding.trace)."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": True}},
     {"cmd": "ride @s dismount"}, {"cmd": "kill @e[type=minecart,distance=..80]"},
     {"cmd": "tp @s 445.5 151 28.5 -90 0"}, {"camera": "back"}, {"hideGui": True}, {"wait": 20},
     {"cmd": "fill 440 145 26 464 156 46 air"}, {"cmd": "fill 440 149 26 464 149 37 smooth_stone"},
     {"cmd": "fill 440 146 38 464 146 46 smooth_stone"},
     {"cmd": "fill 444 149 30 452 149 30 redstone_block"}, {"cmd": "setblock 443 150 30 stone"},
     {"cmd": "fill 444 150 30 452 150 30 powered_rail"}, {"cmd": "fill 453 150 30 457 150 30 rail"},
     {"cmd": "fill 457 150 31 457 150 37 rail"}, {"wait": 10},
     {"cmd": "summon minecart 445.5 150.1 30.5"}, {"wait": 10},
     {"cmd": "ride @s mount @e[type=minecart,limit=1,sort=nearest]"}, {"wait": 30},
     {"orbit": [60, 15, 4.0]}, {"wait": 3}, {"screenshot": f"cart-{tag}-idle"},
     {"log": "ride cart-go"}, {"hold": "forward"}]
for i in range(14):
    s += [{"wait": 4}, {"screenshot": f"cart-{tag}-run{i:02d}"}]
s += [{"release": "forward"}, {"wait": 30}, {"screenshot": f"cart-{tag}-end"}, {"log": "ride end"},
      {"orbit": False}, {"hideGui": False}, {"config": {"footgrounding.trace": False}}]
print(json.dumps(s))
