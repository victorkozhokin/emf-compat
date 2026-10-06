"""Hands in plants on the ATLAS pad (450 150 7): a wheat field 7 x 9 walked through down its middle,
then along its outside edge, then a wall walked along and a door and a button used - the model read
every tick, the log marked `field <case>`. For comparing two builds: the same steps, the same numbers.
Writes plant_field.json next to itself."""
import json
import os

steps = [{"cmd": "gamemode survival"}, {"cmd": "time set noon"}, {"cmd": "effect give @s saturation 5 10 true"},
         {"cmd": "effect give @s instant_health 1 10 true"}, {"config": {"lookat.enabled": False, "pocket.enabled": False}},
         {"hideGui": True}, {"camera": "back"}, {"cmd": "kill @e[tag=gesture_test]"}, {"cmd": "kill @e[type=item,distance=..30]"},
         {"cmd": "fill 442 150 0 458 154 18 air"}, {"cmd": "fill 442 149 0 458 149 18 smooth_stone"},
         {"cmd": "fill 446 149 3 452 149 11 farmland[moisture=7]"}, {"cmd": "fill 446 150 3 452 150 11 wheat[age=7]"},
         {"cmd": "fill 456 150 2 456 152 12 stone"}]


def walk(name, x, yaw=0, ticks=56, z=1.5):
    s = [{"cmd": "tp @s %s 150 %s %s 0" % (x, z, yaw)}, {"look": [yaw, 0]}, {"wait": 25}, {"log": "field " + name}, {"hold": "forward"}]
    for i in range(ticks):
        s += [{"wait": 1}, {"model": "player"}]
        if i % 4 == 0:
            s += [{"screenshot": "field-%s-%02d" % (name, i // 4)}]
    return s + [{"release": "forward"}, {"wait": 15}]


steps += walk("inside", 449.5) + walk("edge", 445.2) + walk("wall", 455.2)
steps += [{"cmd": "fill 446 150 3 452 150 11 air"}, {"cmd": "fill 446 149 3 452 149 11 smooth_stone"}, {"cmd": "fill 456 150 2 456 152 12 air"},
          {"hideGui": False}, {"cmd": "tp @s 450.5 150 7.5 -90 0"}]
json.dump(steps, open(os.path.join(os.path.dirname(__file__), "plant_field.json"), "w"), indent=1)
