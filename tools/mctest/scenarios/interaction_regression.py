"""Interaction regression takes in the mctest sandbox. Use exact FA+Player-v1.1 pack ID.

Each take rebuilds x=349..353,y=150..154,z=6..8. Scene adds the surrounding floor.
A camera sees the player and target side-on. Marks delimit traces, shots include contact/release.
"""
import json
import math
from pathlib import Path


def scene():
    return [{"closeScreen": True}, {"releaseAll": True},
            {"cmd": "tp @s 350 152 7.5"}, {"wait": 30},
            {"cmd": "fill 345 150 4 360 155 12 air"},
            {"cmd": "fill 345 149 4 360 149 12 smooth_stone"},
            {"cmd": "time set noon"}, {"cmd": "gamemode survival"},
            {"config": {"parcool.enabled": False, "footgrounding.trace": True,
                        "lookat.enabled": False, "blockuse.enabled": True,
                        "buttonpress.enabled": True, "doorhold.enabled": True,
                        "furniture.enabled": True}}]


def setup(name, block, by=151, item="air", height=0.5, x=350.7, crouch=False, aim_x=351.5):
    # Aim uses the actual crouching eye height, never the visually extended pose.
    pitch = -math.degrees(math.atan2(by + height - (150 + (1.27 if crouch else 1.62)), aim_x - x))
    steps = [{"closeScreen": True}, {"releaseAll": True},
             {"cmd": "tp @s 345 150 7.5"}, {"wait": 3},
             {"cmd": "fill 349 150 6 353 154 8 air"},
             {"cmd": "setblock 352 151 7 stone"},
             *([{"cmd": "setblock 351 150 7 stone"}] if "candle" in block and by == 151 else []),
             {"cmd": f"setblock 351 {by} 7 {block}"},
             *([{"cmd": f"setblock 351 {by + 1} 7 {block.replace('half=lower', 'half=upper')}"}] if "oak_door" in block else []),
             {"cmd": f"item replace entity @s weapon.mainhand with {item}"},
             {"cmd": f"tp @s {x} 150 7.5 -90 {pitch:.2f}"},
             {"look": [-90, pitch]}, {"camera": "back"}, {"orbit": [90, 10, 3]},
             {"hideGui": True}, {"hideScreen": True}, {"hold": "forward"}, {"wait": 1}, {"releaseAll": True}]
    if crouch:
        steps.append({"hold": "sneak"})
    return steps + [{"wait": 25}, {"cmd": f"tp @s {x} 150 7.5 -90 {pitch:.2f}"}, {"wait": 5}, {"log": f"interaction START {name}"}, {"state": True}]


def finish(name, crouch=False):
    return ([{"closeScreen": True}, {"releaseAll": True}] + ([{"hold": "sneak"}] if crouch else [])
            + [{"look": [-90, -80]},
            {"burst": {"count": 5, "every": 2, "name": name + "-release"}},
            {"state": True}, {"log": f"interaction END {name}"}])


def crank(name, crouch, by=151):
    return (setup(name, "create:hand_crank[facing=west]", by=by, x=351.0, crouch=crouch)
            + [{"hold": "use"}, {"burst": {"count": 20, "every": 3, "name": name}},
               {"state": True}, {"release": "use"}]
            + finish(name, crouch))


def cases():
    takes = {
        "crank-high-crouch": crank("crank-high-crouch", True),
        "crank-high-standing": crank("crank-high-standing", False),
        "crank-low-crouch": crank("crank-low-crouch", True, 150),
    }
    examples = [
        ("door-closed", "oak_door[half=lower,facing=west,open=false]", 150, "air", 1.0),
        ("door-open", "oak_door[half=lower,facing=west,open=true]", 150, "air", 1.0),
        ("button-wall", "stone_button[face=wall,facing=west]", 151, "air", 0.5),
        ("lever-wall", "lever[face=wall,facing=west]", 151, "air", 0.5),
        ("button-floor", "stone_button[face=floor]", 150, "air", 0.1),
        ("lever-floor", "lever[face=floor]", 150, "air", 0.2),
        ("barrel-west", "barrel[facing=west]", 151, "air", 0.5),
        ("candle-light", "candle", 151, "flint_and_steel", 0.3),
        ("candle-extinguish", "candle[lit=true]", 151, "air", 0.3),
        ("note", "note_block", 150, "air", 0.8),
        ("repeater", "repeater[facing=east]", 150, "air", 0.2),
        ("comparator", "comparator[facing=east]", 150, "air", 0.2),
        ("daylight", "daylight_detector", 150, "air", 0.3),
        ("pot-put", "flower_pot", 150, "poppy", 0.3),
        ("pot-take", "potted_poppy", 150, "air", 0.3),
        ("composter", "composter[level=8]", 150, "air", 0.8),
        ("jukebox", "jukebox", 150, "music_disc_cat", 0.8),
        ("shelf", "chiseled_bookshelf[facing=west]", 151, "book", 0.7),
        ("anchor", "respawn_anchor", 150, "glowstone", 0.8),
        ("campfire", "campfire", 150, "beef", 0.3),
        ("cake", "cake", 150, "air", 0.3),
        ("chest", "chest[facing=west]", 150, "air", 0.8),
        ("lectern", 'lectern[facing=west,has_book=true]{Book:{id:"minecraft:writable_book",count:1}}', 150, "air", 0.8),
    ]
    for name, block, by, item, height in examples:
        takes[name] = (setup(name, block, by, item, height, x=351.05 if name in ("button-wall", "lever-wall", "button-floor") else 350.7)
                       + [{"screenshot": name + "-hover"}, {"click": "use"},
                          {"burst": {"count": 6, "every": 2, "name": name}}] + finish(name))
    takes["lever-occluded"] = (setup("lever-occluded", "lever[face=wall,facing=west]")
        + [{"cmd": "setblock 353 151 7 stone"}, {"cmd": "setblock 351 151 7 stone"}, {"cmd": "setblock 352 151 7 lever[face=wall,facing=west]"},
           {"wait": 20}, {"screenshot": "lever-occluded"}] + finish("lever-occluded"))
    takes["mining"] = (setup("mining", "diamond_ore", item="iron_pickaxe")
        + [{"hold": "attack"}, {"burst": {"count": 10, "every": 2, "name": "mining"}}]
        + finish("mining"))
    takes["target-replaced"] = (setup("target-replaced", "repeater", by=150, height=0.1)
        + [{"cmd": "setblock 351 150 7 stone"}, {"wait": 5},
           {"cmd": "setblock 351 150 7 air"}, {"wait": 5}, {"screenshot": "target-replaced"}]
        + finish("target-replaced"))
    return takes


if __name__ == "__main__":
    dest = Path(__file__).parent
    for name, value in [("interaction-scene", scene()), ("interaction-cases", cases())]:
        (dest / (name + ".json")).write_text(json.dumps(value, indent=2) + "\n")
