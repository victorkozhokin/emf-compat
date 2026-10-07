"""Fishing on a pond on the pad at y 160: build it, cast, and shots of the cast and the wait. The bite cannot be
scripted - poll the log for "[Fishing] <name> bite", then click use (see docs/animation-todo.md). fishing.py <tag>."""
import json, sys
tag = sys.argv[1] if len(sys.argv) > 1 else "base"
print(json.dumps([{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode survival"}, {"cmd": "time set noon"}, {"cmd": "weather clear"},
    {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": True}},
    {"cmd": "fill 443 157 8 459 162 19 air"}, {"cmd": "fill 443 157 8 459 159 19 smooth_stone"}, {"cmd": "fill 444 158 9 458 159 17 water"},
    {"cmd": "tp @s 451.5 160 18.6 180 15"}, {"look": [180, 15]},
    {"cmd": "item replace entity @s weapon.mainhand with fishing_rod[enchantments={levels:{\"minecraft:lure\":3}}]"},
    {"camera": "back"}, {"hideGui": True}, {"wait": 20}, {"orbit": [90, 10, 3.0]}, {"log": "fish cast"}, {"click": "use"},
    {"burst": {"count": 8, "every": 2, "name": f"fish-{tag}-cast"}}, {"wait": 30}, {"screenshot": f"fish-{tag}-wait-side"},
    {"orbit": False}, {"hideGui": False}, {"config": {"footgrounding.trace": False}}]))
