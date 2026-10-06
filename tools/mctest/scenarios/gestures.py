"""The short gestures that follow a real action, on the lit pad of the ATLAS world (450 150 7):
feeding, milking and shearing, an armour stand, a seed, armour put on, a shake after water, a chest
looked through. Each case is run four times, shot from behind, the left, the face and the right
every other tick - the second before the click too, when the hand is already held out - and marked `gesture <case>/<view>` in the log; the first run also reads the model
every tick. GESTURES=feed,milk runs only those. Writes gestures.json next to itself; edit this, not
the JSON."""
import json
import os

X, Y, Z = 450.5, 150, 7.5
VIEWS = (("back", 0), ("left", 90), ("front", 180), ("right", 270))
CLEAR = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "kill @e[tag=gesture_test]"},
         {"cmd": "kill @e[type=item,distance=..16]"}, {"cmd": "fill 446 150 5 454 153 9 air"},
         {"cmd": "fill 446 149 5 454 149 9 smooth_stone"}, {"cmd": "clear @s"}]
ANIMAL = '{NoAI:1b,Tags:["gesture_test"]}'

# name: (set-up commands, what is in the hand, the look's pitch, what to do, ticks the gesture lasts)
CASES = {
    "feed": (["summon cow 452.3 150 7.5 " + ANIMAL], "wheat", 12, "use", 62),
    # Fed, then the back turned on it and off: the hands come back to the body, not twisted round to the cow.
    "leave": (["summon cow 452.3 150 7.5 " + ANIMAL], "wheat", 12, "leave", 50),
    # Further off than two blocks from its side: fed all the same, but with no hand put out to it.
    "far": (["summon cow 453.75 150 7.5 " + ANIMAL], "wheat", 12, "use", 30),
    "milk": (["summon cow 452.3 150 7.5 " + ANIMAL], "bucket", 12, "use", 56),
    "shear": (["summon sheep 452.3 150 7.5 " + ANIMAL], "shears", 18, "use", 40),
    "stand": (['summon armor_stand 452.0 150 7.5 {Tags:["gesture_test"],Rotation:[90f]}'], "iron_chestplate", 8, "use", 30),
    "seed": (["setblock 451 149 7 farmland"], "wheat_seeds", 62, "use", 28),
    "armour": ([], None, 0, "armour", 150),
    "boots": ([], None, 0, "boots", 50),
    "chest": (["setblock 452 150 7 chest[facing=west]"], None, 30, "chest", 110),
    "shake": (["fill 446 150 6 447 151 8 water"], None, 0, "shake", 76),
}


def run(name, view, yaw, first):
    setup, item, pitch, act, ticks = CASES[name]
    distance = 1.3 if view == "front" and name in ("feed", "leave", "milk", "shear", "stand", "chest") else 3.5
    s = CLEAR + [{"cmd": c} for c in setup]
    s += [{"cmd": "tp @s %s %s %s -90 %d" % (X, Y, Z, pitch)}]
    if item:
        s += [{"cmd": "item replace entity @s weapon.mainhand with %s" % item}]
    s += [{"orbit": [yaw, 10, distance]}, {"wait": 25}, {"cameraLook": [-90, pitch]}, {"wait": 4},
          {"log": "gesture %s/%s" % (name, view)}]
    if act == "armour":
        s += [{"cmd": "item replace entity @s armor.%s with iron_%s" % p}
              for p in (("head", "helmet"), ("chest", "chestplate"), ("legs", "leggings"), ("feet", "boots"))]
    elif act == "boots":
        s += [{"cmd": "item replace entity @s armor.feet with iron_boots"}]
    elif act == "shake":
        s += [{"cmd": "tp @s 447.0 150 7.5 -90 0"}, {"wait": 40}, {"cmd": "tp @s %s %s %s -90 0" % (X, Y, Z)}, {"wait": 8}]
    elif act == "chest":
        s += [{"hideScreen": True}, {"click": "use"}]
    # With the thing in hand and the target under the crosshair the hand is poised first; the click comes a second in.
    poise = 20 if act in ("use", "leave") else 0
    for i in range(ticks + poise):
        s += [{"wait": 1}]
        if act == "leave" and i == poise + 12:
            s += [{"cameraLook": [90, 0]}, {"hold": "forward"}]
        if act in ("use", "leave") and i == poise:
            s += [{"log": "gesture %s/%s click" % (name, view)}, {"click": "use"}]
        if first:
            s += [{"model": "player"}]
        if i % 2 == 0 and i // 2 < 40:
            s += [{"screenshot": "gesture-%s-%s-%02d" % (name, view, i // 2)}]
        if act == "chest" and i == 70:
            s += [{"closeScreen": True}]
    return s + [{"closeScreen": True}, {"releaseAll": True}, {"wait": 12}]


only = [c for c in os.environ.get("GESTURES", "").split(",") if c]
steps = [{"cmd": "gamemode survival"}, {"cmd": "time set noon"}, {"cmd": "weather clear"},
         {"cmd": "effect give @s saturation 5 10 true"}, {"cmd": "effect give @s instant_health 1 10 true"},
         {"config": {"lookat.enabled": False, "pocket.enabled": False}}, {"hideGui": True}, {"camera": "back"}]
for name in CASES:
    if only and name not in only:
        continue
    for n, (view, yaw) in enumerate(VIEWS):
        steps += run(name, view, yaw, n == 0)
steps += CLEAR + [{"orbit": False}, {"hideGui": False}, {"hideScreen": False}, {"config": {"pocket.enabled": True}}]
json.dump(steps, open(os.path.join(os.path.dirname(__file__), "gestures.json"), "w"), indent=1)
