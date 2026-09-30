"""Repeatable FA+Player foot IK course. Generates scene and individually runnable takes.

Run scene first, then a take with mc_steps. The player travels +X (backward take faces -X).
Screenshots every tick are intentionally dense; trace includes swing/contact and predicted Y.
Only modifies the mctest sandbox world, at x=300..320, y=149..154, z=4..26.
"""
import json
from pathlib import Path


def scene():
    return [
        {"closeScreen": True}, {"releaseAll": True},
        {"cmd": "tp @s 304 152 7.5"}, {"wait": 30},
        {"cmd": "fill 300 150 4 320 154 26 air"},
        {"cmd": "fill 300 149 4 320 149 26 smooth_stone"},
        {"cmd": "fill 305 150 5 310 150 9 stone_slab"},
        {"cmd": "fill 305 150 14 305 150 16 stone_stairs[facing=east]"},
        {"cmd": "fill 306 150 14 306 150 16 stone"},
        {"cmd": "fill 306 151 14 306 151 16 stone_stairs[facing=east]"},
        {"cmd": "fill 307 150 14 312 151 16 stone"},
        {"cmd": "fill 308 150 20 308 153 21 stone"},
        {"cmd": "time set noon"}, {"cmd": "weather clear"},
        {"cmd": "gamerule doDaylightCycle false"},
        {"cmd": "gamemode survival"},
        {"config": {"footgrounding.enabled": True, "footgrounding.trace": True,
                    "parcool.enabled": False, "lookat.enabled": False}},
    ]


def take(name, x, y, z, key="forward", yaw=-90, count=32, extra=()):
    return [
        {"closeScreen": True}, {"releaseAll": True},
        {"cmd": f"tp @s {x} {y} {z} {yaw} 0"}, {"look": [yaw, 0]},
        {"camera": "back"}, {"orbit": [90, 5, 3]}, {"hideGui": True},
        {"wait": 20}, {"log": f"footik START {name}"},
        *extra, {"hold": key},
        {"burst": {"count": count, "every": 1, "name": f"footik-{name}"}},
        {"releaseAll": True}, {"state": True}, {"log": f"footik END {name}"},
        {"wait": 10},
    ]


def cases():
    return {
        "slab-up": take("slab-up", 302.5, 150, 7.5, count=24),
        "slab-back": take("slab-back", 302.5, 150, 7.5, key="back", yaw=90),
        "slab-down": take("slab-down", 309, 150.5, 7.5),
        "stairs": take("stairs", 302.5, 150, 15, count=40),
        "flat": take("flat", 302.5, 150, 24),
        "crouch": take("crouch", 302.5, 150, 24, extra=[{"hold": "sneak"}]),
        "wall": take("wall", 305, 150, 20.5),
        "jump": take("jump", 304, 150, 7.5, count=40, extra=[{"hold": "jump"}]),
    }


if __name__ == "__main__":
    out = Path(__file__).parent
    (out / "footik-scene.json").write_text(json.dumps(scene(), indent=2) + "\n")
    (out / "footik-cases.json").write_text(json.dumps(cases(), indent=2) + "\n")
