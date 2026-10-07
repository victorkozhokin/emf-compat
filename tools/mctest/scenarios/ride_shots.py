"""Four-sided shots in a boat and in a minecart, standing and moving. ride_shots.py <tag> prints the steps."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
def shots(name):
    out = []
    for ang, lab in ((90, "side"), (180, "front"), (0, "back"), (-90, "other")):
        out += [{"orbit": [ang, 12, 3.6]}, {"wait": 3}, {"screenshot": f"ride-{tag}-{name}-{lab}"}]
    return out + [{"orbit": False}]
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"}, {"cmd": "time set noon"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": False, "wallhand.trace": False, "transport.trace": False}},
     {"cmd": "tp @s 450.5 150 7.5 -90 0"}, {"camera": "back"}, {"hideGui": True}, {"wait": 30},
     {"cmd": "kill @e[type=#minecraft:boat,distance=..40]"}, {"cmd": "kill @e[type=minecart,distance=..40]"},
     {"cmd": "fill 440 150 3 462 154 12 air"}, {"cmd": "fill 440 148 3 462 149 12 smooth_stone"},
     # a pool for the boat, rails for the cart
     {"cmd": "fill 444 149 5 458 149 9 water"}, {"wait": 10},
     {"cmd": "summon boat 450.5 150 7.5 {Type:\"oak\",Rotation:[-90f,0f]}"}, {"wait": 10}, {"cmd": "ride @s mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 30},
     {"log": "ride boat-idle"}] + shots("boat-idle")
s += [{"log": "ride boat-row"}, {"hold": "forward"}, {"wait": 14}] + shots("boat-row") + [{"wait": 7}] + shots("boat-row2") + [{"release": "forward"}]
s += [{"log": "ride boat-turn"}, {"hold": "left"}, {"wait": 14}] + shots("boat-turn") + [{"release": "left"}, {"wait": 10}]
s += [{"cmd": "ride @s dismount"}, {"cmd": "kill @e[type=#minecraft:boat,distance=..40]"}, {"cmd": "fill 444 149 5 458 149 9 smooth_stone"},
      {"cmd": "fill 444 150 11 460 150 11 rail"}, {"cmd": "setblock 444 150 11 powered_rail"}, {"wait": 10},
      {"cmd": "summon minecart 452.5 150.1 11.5"}, {"wait": 10}, {"cmd": "tp @s 452.5 151 10.5 -90 0"}, {"wait": 5},
      {"cmd": "ride @s mount @e[type=minecart,limit=1,sort=nearest]"}, {"wait": 30}, {"log": "ride cart-idle"}] + shots("cart-idle")
s += [{"log": "ride cart-go"}, {"hold": "forward"}, {"wait": 12}] + shots("cart-go") + [{"release": "forward"}, {"wait": 20}]
s += [{"log": "ride end"}, {"cmd": "ride @s dismount"}, {"cmd": "kill @e[type=minecart,distance=..40]"}, {"cmd": "fill 444 150 11 460 150 11 air"},
      {"hideGui": False}, {"cmd": "gamemode survival"}, {"cmd": "tp @s 450.5 150 7.5 -90 0"}]
print(json.dumps(s))
