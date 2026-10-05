"""Mining with the body: every tool at a block by the feet, at the chest and overhead, on the lit
pad of the ATLAS world (450 150 7). Obsidian, so the breaking goes on as long as the button is
held. Each case is marked `mining <tool>/<height>` in the log, with the model read and a
shot taken every tick from four sides: behind, the left, the face, the right. Writes mining-body.json next to itself; edit this, not the JSON."""
import json
import os

X, Y, Z = 450.5, 150, 7.5
# Height name -> the block's place and the pitch that looks at its middle from the eyes.
HEIGHTS = {"floor": ((451, 149, 7), 58), "feet": ((452, 150, 7), 29), "chest": ((452, 151, 7), 3),
           "overhead": ((452, 152, 7), -24)}
TOOLS = {"pickaxe": "iron_pickaxe", "axe": "iron_axe", "shovel": "iron_shovel", "hoe": "iron_hoe"}
CASES = [(t, h) for t in TOOLS for h in ("feet", "chest", "overhead")] + [("pickaxe", "floor"), ("shovel", "floor")]


# Behind (the plain third-person view), the left side, the face, the right side.
VIEWS = (("back", 0), ("left", 90), ("front", 180), ("right", 270))


def case(tool, height, trace):
    (bx, by, bz), pitch = HEIGHTS[height]
    name = "%s-%s" % (tool, height)
    s = [{"releaseAll": True},
         {"cmd": "fill 451 150 6 453 153 8 air"}, {"cmd": "fill 449 149 5 454 149 9 smooth_stone"},
         {"cmd": "setblock %d %d %d obsidian" % (bx, by, bz)},
         {"cmd": "tp @s %s %s %s -90 %d" % (X, Y, Z, pitch)},
         {"cmd": "item replace entity @s weapon.mainhand with %s" % TOOLS[tool]},
         {"config": {"footgrounding.trace": trace}},
         {"orbit": [0, 8, 3.5]}, {"wait": 12}, {"cameraLook": [-90, pitch]}, {"wait": 4},
         {"log": "mining %s/%s" % (tool, height)}, {"screenshot": "mining-%s-rest" % name},
         {"hold": "attack"}, {"wait": 14}]
    # From four sides, a swing and a half each at every tick: a swing is six ticks.
    for view, yaw in VIEWS:
        # The block is a block and a half ahead: the camera at the face has to stand this side of it.
        s += [{"orbit": [yaw, 8, 1.2 if view == "front" else 3.5]}, {"wait": 2}]
        for i in range(9):
            s += [{"wait": 1}, {"model": "player"}, {"screenshot": "mining-%s-%s-%d" % (name, view, i)}]
    # Let go: the arm stays over the block for a while, eased off; then the feet step back.
    s += [{"release": "attack"}, {"log": "mining %s/%s released" % (tool, height)}, {"wait": 20}]
    for view, yaw in VIEWS:
        s += [{"orbit": [yaw, 8, 1.2 if view == "front" else 3.5]}, {"wait": 3}, {"screenshot": "mining-%s-%s-held" % (name, view)}]
    s += [{"wait": 60}, {"model": "player"}]
    return s


steps = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"},
         {"cmd": "weather clear"}, {"cmd": "effect give @s saturation 5 10 true"},
         {"cmd": "effect give @s instant_health 1 10 true"},
         {"config": {"mining.enabled": True, "lookat.enabled": False, "pocket.enabled": False}},
         {"hideGui": True}, {"camera": "back"}, {"cmd": "tp @s %s %s %s -90 0" % (X, Y, Z)}, {"wait": 30},
         {"wait": 5}]
# MINING_CASES=pickaxe-chest,axe-chest runs only those.
ONLY = [c for c in os.environ.get("MINING_CASES", "").split(",") if c]
CASES = [c for c in CASES if not ONLY or "%s-%s" % c in ONLY]
for n, (tool, height) in enumerate(CASES):
    steps += case(tool, height, n == 0)
steps += [{"config": {"footgrounding.trace": False}}, {"releaseAll": True}, {"cmd": "fill 451 150 6 453 153 8 air"},
          {"cmd": "fill 449 149 5 454 149 9 smooth_stone"}, {"orbit": False}, {"hideGui": False}]
json.dump(steps, open(os.path.join(os.path.dirname(__file__), "mining-body.json"), "w"), indent=2)
