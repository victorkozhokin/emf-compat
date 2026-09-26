"""Writes the Animation Additions baseline: a course and the shots taken on it.

The course floats at y 150 over x 300..360, z 0..30 in any world, so it does not depend on what
was built by hand anywhere else; `aa-scene.json` clears the air there and builds it (run it once
per fresh world copy). `aa-shots.json` then takes every case, each marked `aa <case>` in the log,
with a burst of frames and, where it tells something, the same frames with the feature off.

    aa-scene.json   the course
    aa-shots.json   the baseline shots (long: run it with `mctest.py steps`, not the MCP call)
    aa-contact.json only the hands-on-things cases (buttons, lever, doors, chest, lectern)

What each case shows and what was expected when the baseline was taken is in README.md
("Animation Additions baseline"). The features all read their toggles from the config, so the
shots switch them in memory with `config` steps and put them back.

Needs: Animation Additions enabled in the sandbox, Fresh Animations + FA+Player, ParCool (the
charge case) and a player skin that shows the legs (launch with --name/--uuid). Launch with WATUT
disabled: it marks a scripted player AFK and bows the head.
"""
import json
import os

HERE = os.path.dirname(os.path.abspath(__file__))

FLOOR = 150          # the floor blocks; the player stands at FLOOR + 1
Y = FLOOR + 1


def cmd(c):
    return {"cmd": c}


def fill(x1, y1, z1, x2, y2, z2, block):
    return cmd(f"fill {x1} {y1} {z1} {x2} {y2} {z2} {block}")


# ---------------------------------------------------------------------------------------------
# The course

def scene():
    s = [
        {"hideGui": True}, {"releaseAll": True},
        cmd("gamerule sendCommandFeedback false"), cmd("gamerule doDaylightCycle false"),
        cmd("gamerule doWeatherCycle false"), cmd("gamerule doMobSpawning false"),
        cmd("gamerule fallDamage false"), cmd("weather clear"), cmd("time set noon"),
        cmd("gamemode creative"),
        # Load the chunks first: fill refuses unloaded ones.
        cmd(f"tp @s 330 {Y + 2} 15"), {"wait": 40},
        cmd("kill @e[type=!player,x=300,y=140,z=0,dx=60,dy=30,dz=30]"),
        fill(300, Y, 0, 360, Y + 4, 30, "air"),
        fill(300, Y + 5, 0, 360, Y + 8, 30, "air"),
        fill(300, FLOOR, 0, 360, FLOOR, 30, "smooth_stone"),

        # Steps under one foot: a slab and three snow layers (collision 4 px) side by side.
        fill(305, Y, 5, 306, Y, 9, "stone_slab"),
        fill(309, Y, 5, 310, Y, 9, "snow[layers=3]"),

        # Stairs up and down, 2 wide (z 14..15): three steps of half a block, a platform, three down.
        fill(305, Y, 14, 305, Y, 15, "stone_stairs[facing=east]"),
        fill(306, Y, 14, 306, Y, 15, "stone"),
        fill(306, Y + 1, 14, 306, Y + 1, 15, "stone_stairs[facing=east]"),
        fill(307, Y, 14, 307, Y + 1, 15, "stone"),
        fill(307, Y + 2, 14, 307, Y + 2, 15, "stone_stairs[facing=east]"),
        fill(308, Y, 14, 311, Y + 2, 15, "stone"),
        fill(312, Y, 14, 312, Y + 1, 15, "stone"),
        fill(312, Y + 2, 14, 312, Y + 2, 15, "stone_stairs[facing=west]"),
        fill(313, Y, 14, 313, Y, 15, "stone"),
        fill(313, Y + 1, 14, 313, Y + 1, 15, "stone_stairs[facing=west]"),
        fill(314, Y, 14, 314, Y, 15, "stone_stairs[facing=west]"),

        # The same climb in slabs (z 18..19).
        fill(305, Y, 18, 305, Y, 19, "stone_slab"),
        fill(306, Y, 18, 306, Y, 19, "stone"),
        fill(307, Y, 18, 307, Y, 19, "stone"),
        fill(307, Y + 1, 18, 307, Y + 1, 19, "stone_slab"),
        fill(308, Y, 18, 311, Y + 1, 19, "stone"),
        fill(312, Y, 18, 312, Y, 19, "stone"),
        fill(312, Y + 1, 18, 312, Y + 1, 19, "stone_slab"),
        fill(313, Y, 18, 313, Y, 19, "stone"),
        fill(314, Y, 18, 314, Y, 19, "stone_slab"),

        # A wall (z 5) with ripe wheat along it (z 8): the wall-and-plant conflict.
        fill(320, Y, 5, 326, Y + 2, 5, "stone_bricks"),
        fill(320, FLOOR, 8, 326, FLOOR, 8, "farmland[moisture=7]"),
        fill(320, Y, 8, 326, Y, 8, "wheat[age=7]"),

        # A wheat field.
        fill(330, FLOOR, 5, 336, FLOOR, 11, "farmland[moisture=7]"),
        fill(330, Y, 5, 336, Y, 11, "wheat[age=7]"),

        # Somebody to look at.
        cmd(f"summon villager 331.5 {Y} 21.5 {{NoAI:1b,Invulnerable:1b,PersistenceRequired:1b,Tags:[\"aa\"]}}"),

        # Horses on slab edges: one across an edge (front hooves up), one along it (left hooves up).
        fill(346, Y, 5, 347, Y, 9, "stone_slab"),
        fill(346, Y, 12, 347, Y, 16, "stone_slab"),
        # NoAI horses neither fall nor settle: summon them at the slab's height. Facing south
        # (rotation 0) or their body turns to the head over time.
        cmd(f"summon horse 346.6 {Y + 0.5} 4.4 {{NoAI:1b,Tame:1b,Variant:1,Rotation:[0f,0f],"
            f"SaddleItem:{{id:\"minecraft:saddle\",count:1}},Tags:[\"aa\",\"aa_tilt\"]}}"),
        cmd(f"summon horse 346.0 {Y + 0.5} 14.0 {{NoAI:1b,Tame:1b,Variant:3,Rotation:[0f,0f],"
            f"Tags:[\"aa\",\"aa_side\"]}}"),
        {"wait": 5},

        # Hands on things (x 350..359, z 20..28): a wall at z 28 with an oak button and a lever on
        # it, an oak button on the floor, a double door at z 24, a chest and a lectern.
        fill(351, Y, 28, 358, Y + 2, 28, "stone_bricks"),
        cmd(f"setblock 353 {Y + 1} 27 oak_button[face=wall,facing=north]"),
        cmd(f"setblock 356 {Y + 1} 27 lever[face=wall,facing=north]"),
        cmd(f"setblock 350 {Y} 22 oak_button[face=floor,facing=north]"),
        *door(352, 24, "left"), *door(353, 24, "right"),
        cmd(f"setblock 356 {Y} 21 chest[facing=north]"),
        cmd(f"setblock 359 {Y} 21 lectern[facing=north]"),
        {"wait": 5},
    ]
    return s


def door(x, z, hinge, open=False):
    state = f"facing=north,hinge={hinge},open={str(open).lower()}"
    return [cmd(f"setblock {x} {Y} {z} oak_door[half=lower,{state}]"),
            cmd(f"setblock {x} {Y + 1} {z} oak_door[half=upper,{state}]")]


# ---------------------------------------------------------------------------------------------
# The shots

FEATURES = ["footgrounding.enabled", "footgrounding.horses", "wallhand.enabled",
            "plantreach.enabled", "lookat.enabled", "buttonpress.enabled", "doorhold.enabled",
            "furniture.enabled"]


def case(name, x, y, z, yaw, pitch=0, orbit=(90, 5, 3.5), settle=False):
    """Starts a case: everything on, keys up, the player on the mark, the camera side-on.

    A teleport turns the head only; the body keeps its old heading until the player moves. With
    `settle` the player takes a tiny step the way they face first, so the body lines up - the
    wall hand looks along the body, not the camera.
    """
    s = [{"releaseAll": True}, {"orbit": False}, {"config": {f: True for f in FEATURES}},
         cmd(f"tp @s {x} {y} {z} {yaw} {pitch}"), {"look": [yaw, pitch]}]
    if settle:
        s += [{"hold": "forward"}, {"wait": 3}, {"release": "forward"}, {"wait": 5}]
    return s + [{"orbit": list(orbit)}, {"wait": 20}, {"log": f"aa {name}"}]


def shots(name, count=4, every=2):
    return [{"burst": {"count": count, "every": every, "name": name}}]


def without(feature, name, count=2, every=2):
    """The same frames with one feature off, then on again."""
    return [{"config": {feature: False}}, {"wait": 6}, *shots(name + "-off", count, every),
            {"config": {feature: True}}, {"wait": 6}]


def walk(name, ticks_before, count, every=2, sprint=False):
    s = [{"hold": "forward"}]
    if sprint:
        s.append({"hold": "sprint"})
    s += [{"wait": ticks_before}, *shots(name, count, every), {"releaseAll": True}]
    return s


def all_shots():
    s = [
        {"hideGui": True}, {"releaseAll": True},
        cmd("gamemode survival"), cmd("difficulty peaceful"),
        cmd("effect give @s resistance infinite 10 true"),
        cmd("effect give @s saturation infinite 10 true"),
        cmd("parcool action unlock @s all"), cmd("clear @s"),
    ]

    # Foot IK standing: one foot on the slab, the other over the floor; then on snow layers. The
    # step that settles the body walks along the edge, so the feet stay one on either side.
    s += case("foot/slab-edge", 304.85, Y + 0.5, 7.0, 0, orbit=(150, 5, 3), settle=True) + \
        shots("foot-slab", 1) + without("footgrounding.enabled", "foot-slab", 1)
    s += case("foot/snow-edge", 308.85, Y + 0.25, 7.0, 0, orbit=(150, 5, 3), settle=True) + \
        shots("foot-snow", 1) + without("footgrounding.enabled", "foot-snow", 1)
    # With armour: the armour model and the outer skin layer follow the raised leg.
    s += [cmd("item replace entity @s armor.legs with minecraft:iron_leggings"),
          cmd("item replace entity @s armor.feet with minecraft:iron_boots")]
    s += case("foot/slab-edge-armour", 304.85, Y + 0.5, 7.0, 0, orbit=(150, 5, 3), settle=True) + shots("foot-armour", 1)
    s += [cmd("clear @s")]

    # Onto the slab facing it, and along its edge with one foot up: the weight goes over with the
    # stride. Frame by frame, with the per-frame trace on.
    s += case("foot/slab-step-on", 302.5, Y, 7.5, -90) + [{"config": {"footgrounding.trace": True}}] + \
        walk("slab-on", 1, 24, 1) + [{"config": {"footgrounding.trace": False}}]
    s += case("foot/slab-along", 304.85, Y, 2.5, 0, orbit=(120, 5, 3.5)) + \
        [{"config": {"footgrounding.trace": True}}] + walk("slab-along", 1, 30, 1) + \
        [{"config": {"footgrounding.trace": False}}]

    # Stairs facing them, up and over and down; then the slab climb. Frame by frame.
    s += case("foot/stairs-walk", 302.5, Y, 14.99, -90) + walk("stairs", 2, 40, 1)
    s += case("foot/slab-stairs-walk", 302.5, Y, 18.99, -90) + walk("slabstairs", 2, 40, 1)

    # Crouch and the ParCool charge on flat ground: the legs stay where the pack puts them.
    s += case("foot/crouch-flat", 315.5, Y, 25.5, -90, orbit=(150, 5, 3), settle=True) + [{"hold": "sneak"}, {"wait": 30}] + \
        shots("crouch", 1) + without("footgrounding.enabled", "crouch", 1) + [{"releaseAll": True}]

    # Hands on the wall: facing it (both hands), and along it with wheat on the other side. An arm
    # is ~0.6 blocks long: the side wall has to be that close to a shoulder.
    # Side-on for the wall ahead (from behind the body hides the hands); from behind-left for the
    # wall beside, which then stands on the far side.
    s += case("wall/front", 323.5, Y, 6.7, 180, orbit=(90, 10, 3.5), settle=True) + [{"wait": 10}] + \
        shots("wall-front", 1) + without("wallhand.enabled", "wall-front", 1)
    s += case("wall/side-with-wheat", 324.5, Y, 6.6, 90, orbit=(-30, 15, 3.5), settle=True) + [{"wait": 10}] + \
        shots("wall-side", 1) + without("wallhand.enabled", "wall-side", 1)

    # Hands in the wheat: standing in the middle, then walking through.
    s += case("plants/field-centre", 333.5, Y - 0.0625, 8.0, 0, orbit=(150, 5, 3), settle=True) + [{"wait": 10}] + \
        shots("field", 1) + without("plantreach.enabled", "field", 1)
    s += case("plants/field-walk", 330.5, Y - 0.0625, 5.5, 0) + walk("field-walk", 4, 8)

    # Look-at: idle for 3 s with the villager ahead, then walking on, then turning the camera.
    s += case("lookat/idle", 333.5, Y, 16.0, 0, orbit=(150, 5, 3), settle=True) + [{"wait": 80}] + \
        shots("lookat-idle", 1) + [{"look": [40, 0]}, {"wait": 10}] + shots("lookat-reset", 1)

    # Horses standing on slab edges, then with a rider on the tilted one.
    s += case("horse/standing", 343.5, Y, 10.5, -90, orbit=(60, 10, 7)) + [{"wait": 10}] + \
        shots("horses", 1) + without("footgrounding.horses", "horses", 1)
    s += case("horse/rider-tilt", 343.5, Y, 4.4, -90) + \
        [cmd("ride @s mount @e[type=horse,tag=aa_tilt,limit=1]"), {"wait": 20},
         {"orbit": [90, 5, 5]}, {"wait": 5}] + shots("rider-tilt", 1) + \
        [cmd("ride @s dismount")]

    # Riding a live horse up the stairs.
    s += case("horse/ride-stairs", 302.5, Y, 14.5, -90) + [
        cmd("kill @e[type=horse,tag=aa_ride]"),
        cmd(f"summon horse 302.5 {Y} 14.5 {{Tame:1b,Variant:2,Rotation:[-90f,0f],"
            f"SaddleItem:{{id:\"minecraft:saddle\",count:1}},Tags:[\"aa\",\"aa_ride\"],"
            f"Attributes:[{{id:\"minecraft:generic.movement_speed\",base:0.2d}}]}}"),
        {"wait": 5}, cmd("ride @s mount @e[type=horse,tag=aa_ride,limit=1]"), {"wait": 20},
        {"orbit": [90, 5, 6]}, {"wait": 5},
    ] + walk("ride-stairs", 2, 16, 3) + [cmd("ride @s dismount"), cmd("kill @e[type=horse,tag=aa_ride]")]

    s += contact_shots()
    s += [{"releaseAll": True}, {"orbit": False}, {"config": {f: True for f in FEATURES}},
          {"log": "aa done"}]
    return s


def contact_shots():
    """Hands on things: buttons, a lever, doors, a chest, a lectern. Right-clicks need the
    survival player and the crosshair on the block; a chest's screen hides the player, so the
    open chest is judged by the log."""
    s = [cmd("gamemode survival"), *door(352, 24, "left"), *door(353, 24, "right")]
    # A button on the wall: the right hand points at it looked at, goes onto its middle pressed.
    s += case("contact/wall-button", 353.5, Y, 27.2, 0, 10, orbit=(95, 0, 2.5), settle=True) + \
        [{"look": [0, 10]}, {"wait": 10}] + shots("wall-button", 1) + [{"click": "use"}] + \
        shots("wall-button-press", 3, 2) + without("buttonpress.enabled", "wall-button", 1)
    # A lever: the hand on the end of the handle, over with it both ways.
    s += case("contact/lever", 356.85, Y, 27.0, 0, 5, orbit=(-60, 5, 2.6), settle=True) + \
        [{"look": [55, 5]}, {"wait": 10}] + shots("lever-off", 1) + [{"click": "use"}, {"wait": 12}] + \
        shots("lever-on", 1) + [{"click": "use"}, {"wait": 12}]
    # A button on the floor: a foot over it, down on it pressed.
    s += case("contact/floor-button", 350.5, Y, 22.1, 0, 75, orbit=(90, 15, 3)) + \
        [{"look": [0, 75]}, {"wait": 10}] + shots("floor-button", 1) + [{"click": "use"}] + \
        shots("floor-button-press", 3, 2)
    # A shut double door: each hand on its leaf's handle; then open, walking through.
    s += case("contact/door-shut", 353.0, Y, 23.6, 0, -10, orbit=(-140, 10, 3.5)) + \
        [{"wait": 10}] + shots("door-shut", 1) + without("doorhold.enabled", "door-shut", 1)
    s += [*door(352, 24, "left", True), *door(353, 24, "right", True)]
    s += case("contact/door-through", 353.0, Y, 22.3, 0, 10, orbit=(150, 15, 4.5)) + \
        walk("door-through", 2, 8, 2)
    s += [*door(352, 24, "left"), *door(353, 24, "right")]
    # A chest: the hands on the lid; opened, the left one holds it up, the right one on the chest.
    s += case("contact/chest", 356.5, Y, 20.3, 0, 50, orbit=(-70, 10, 3), settle=True) + \
        [{"look": [0, 50]}, {"wait": 10}] + shots("chest", 1) + \
        [{"click": "use"}, {"wait": 20}, {"log": "aa contact/chest-open"}, {"closeScreen": True}, {"wait": 20}]
    # A lectern, from the side it is read from: the hands on the book.
    s += case("contact/lectern", 359.5, Y, 20.3, 0, 35, orbit=(-70, 10, 3), settle=True) + \
        [{"look": [0, 35]}, {"wait": 10}] + shots("lectern", 1) + without("furniture.enabled", "lectern", 1)
    return s


def write(name, steps):
    with open(os.path.join(HERE, name), "w") as f:
        json.dump(steps, f, indent=1)
        f.write("\n")


if __name__ == "__main__":
    write("aa-scene.json", scene())
    write("aa-shots.json", all_shots())
    write("aa-contact.json", [{"hideGui": True}] + contact_shots() +
          [{"releaseAll": True}, {"orbit": False}, {"log": "aa done"}])
