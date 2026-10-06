"""Another player's gestures, told from what can be seen of them: a bot on the ATLAS pad (450 150 7)
with the thing in hand looks at its target - the hand should go out; then it swings (the click, for
feeding and the stand) or really plants a seed - the work should follow. Our own player only watches.
Shots `remote-<case>-NN` every other tick, the log marked `remote <case>` / `remote <case> act`.
Writes gestures_remote.json next to itself."""
import json
import os

CLEAR = [{"cmd": "kill @e[tag=gesture_test]"}, {"cmd": "fill 446 150 5 454 153 9 air"}, {"cmd": "fill 446 149 5 454 149 9 smooth_stone"}]
ANIMAL = '{NoAI:1b,Tags:["gesture_test"]}'
# name: (set-up, the bot's item, what it looks at, how the act comes)
CASES = {
    "feed": (["summon cow 452.3 150 7.5 " + ANIMAL], "minecraft:wheat", [452.3, 151.0, 7.5], {"swing": True}),
    "milk": (["summon cow 452.3 150 7.5 " + ANIMAL], "minecraft:bucket", [452.3, 151.0, 7.5], {"swing": True}),
    # A swing that turns out to be a blow: the cow is hurt a tick later, and the hand comes straight back.
    "blow": (["summon cow 452.3 150 7.5 " + ANIMAL], "minecraft:wheat", [452.3, 151.0, 7.5], {"swing": True}),
    "shear": (["summon sheep 452.3 150 7.5 " + ANIMAL], "minecraft:shears", [452.3, 150.9, 7.5], {"swing": True}),
    "stand": (['summon armor_stand 452.0 150 7.5 {Tags:["gesture_test"],Rotation:[90f]}'], "minecraft:iron_chestplate", [452.0, 151.2, 7.5], {"swing": True}),
    "seed": (["setblock 451 149 7 farmland[moisture=7]"], "minecraft:wheat_seeds", [451.5, 149.94, 7.5], {"use": [451, 149, 7], "face": "up"}),
    # A swing with nothing to do it to: no gesture.
    "idle": ([], "minecraft:wheat", [452.3, 151.0, 7.5], {"swing": True}),
}
steps = [{"cmd": "gamemode creative"}, {"cmd": "time set noon"}, {"config": {"lookat.enabled": False}}, {"hideGui": True}, {"camera": "first"},
         ][:5]
steps += CLEAR + [{"cmd": "tp @s 451.2 150 3.2 0 12"}, {"wait": 20}, {"bot": {"spawn": "Bob", "at": [450.5, 150, 7.5], "look": [-90, 0]}}, {"wait": 20}]
for name, (setup, item, target, act) in CASES.items():
    steps += CLEAR + [{"cmd": c} for c in setup]
    steps += [{"bot": {"name": "Bob", "at": [450.5, 150, 7.5], "look": [0, 0], "item": "minecraft:air"}}, {"wait": 30},
              {"log": "remote " + name}, {"bot": {"name": "Bob", "at": [450.5, 150, 7.5], "lookAt": target, "item": item}}]
    for i in range(70):
        steps += [{"wait": 1}]
        if i == 24:
            steps += [{"log": "remote %s act" % name}, {"bot": dict(act, name="Bob")}]
            if name == "blow":
                steps += [{"wait": 1}, {"cmd": "damage @e[tag=gesture_test,limit=1] 1"}]
        if i % 2 == 0:
            steps += [{"screenshot": "remote-%s-%02d" % (name, i // 2)}]
steps += CLEAR + [{"bot": {"name": "Bob", "remove": True}}, {"hideGui": False}, {"camera": "back"}, {"cmd": "gamemode survival"}, {"cmd": "tp @s 450.5 150 7.5 -90 0"}]
json.dump(steps, open(os.path.join(os.path.dirname(__file__), "gestures_remote.json"), "w"), indent=1)
