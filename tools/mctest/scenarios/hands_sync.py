"""The server part of Animation Additions, on the integrated server: a bot is another player, and
the driver says for it what a real second player's game would (`bot.hands`, `bot.act`) - through
the addon's own channel, so the packet and everything after it are the real ones. Each case is
run told and untold; the log is marked `sync <case> told|untold` and the decisions tell the rest.
ATLAS: the crank wall of zone 04 and the pad at 450 150 7. Writes hands_sync.json next to itself."""
import json
import os

steps = [{"cmd": "gamemode creative"}, {"cmd": "time set noon"}, {"config": {"debug.decisions": True, "lookat.enabled": False}},
         {"hideGui": True}, {"camera": "first"}]


def case(name, hands, ticks=50, act=None):
    """The same wait twice: with the bot's hands told every ten ticks, then with nothing told."""
    s = []
    for told in (True, False):
        s += [{"bot": {"name": "Bob", "hands": {}}}, {"wait": 40}, {"log": "sync %s %s" % (name, "told" if told else "untold")}]
        for i in range(ticks):
            if told and i % 10 == 0:
                s += [{"bot": {"name": "Bob", "hands": hands}}]
            if told and act and i == 22:
                s += [{"bot": {"name": "Bob", "act": act}}]
            s += [{"wait": 1}]
            if i % 5 == 0:
                s += [{"screenshot": "sync-%s-%s-%02d" % (name, "told" if told else "untold", i // 5)}]
    return s


# A crank, the bot's back to it: only its own game could say it holds the use key on it.
steps += [{"cmd": "tp @s 2069.5 151 2125.5 150 5"}, {"wait": 30},
          {"bot": {"spawn": "Bob", "at": [2066.5, 151, 2122.4], "look": [0, 0]}}, {"wait": 30}]
steps += case("crank", {"use": True, "block": [2066, 151, 2121], "face": "south", "hit": [2066.5, 151.5, 2121.9]})
# A block mined, the bot swinging at nothing the server counts as breaking.
steps += [{"cmd": "tp @s 450.5 150 11.5 180 5"}, {"wait": 20}, {"cmd": "fill 446 150 5 454 153 9 air"}, {"cmd": "fill 446 149 5 454 149 9 smooth_stone"},
          {"cmd": "setblock 452 151 7 stone"}, {"cmd": "setblock 452 150 7 stone"}, {"cmd": "setblock 449 149 7 farmland[moisture=7]"},
          {"bot": {"name": "Bob", "at": [450.5, 150, 7.5], "look": [-90, 0], "item": "minecraft:iron_pickaxe"}}, {"wait": 30}]
steps += case("mine", {"attack": True, "block": [452, 151, 7], "face": "west", "hit": [452.0, 151.5, 7.5]})
# A seed planted: the bed under its crosshair as its game has it, and the click itself.
steps += [{"bot": {"name": "Bob", "at": [450.5, 150, 7.5], "look": [90, 60], "item": "minecraft:wheat_seeds"}}, {"wait": 20}]
steps += case("seed", {"block": [449, 149, 7], "face": "up", "hit": [449.5, 149.94, 7.5]}, act={"kind": 4, "at": [449.5, 149.94, 7.5]})
steps += [{"bot": {"name": "Bob", "remove": True}}, {"cmd": "fill 446 150 5 454 153 9 air"}, {"cmd": "fill 446 149 5 454 149 9 smooth_stone"},
          {"hideGui": False}, {"camera": "back"}, {"cmd": "gamemode survival"}, {"cmd": "tp @s 450.5 150 7.5 -90 0"}]
json.dump(steps, open(os.path.join(os.path.dirname(__file__), "hands_sync.json"), "w"), indent=1)
