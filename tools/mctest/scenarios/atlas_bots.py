"""Ten bot players cycling the blocks of EMF ATLAS zone 03 (INTERACTIONS), for load and multiplayer tests.

Writes atlas-bots.json (spawn and start) and atlas-bots-stop.json (remove them all). Each bot stands at
its block, looks at it, and right-clicks it on the server's tick every `every` ticks; the periods
differ and the starts are staggered, so the presses do not line up. They go on between scripts until
stopped or the world is left. World: "EMF ATLAS - Animation Campus" (tools/mctest/polygon).
"""
import json
from pathlib import Path

HERE = Path(__file__).parent

# The foot takes a floor button within 9 px of the hip, which is 1.9 px to the side: Dan stands on its edge; the comparator's torch is at its far side.
# name, block, stand at, look at, the click: block, face, hit point; every, close (a container shut again)
BOTS = [
    ('Ada', 'wall stone button, low', [2185.5, 151, 2066.5], [2185.5, 151.5, 2065.1],
     [2185, 151, 2065], 'south', [2185.5, 151.5, 2065.12], 30, 0),
    ('Ben', 'wall oak button, high', [2203.5, 151, 2066.5], [2203.5, 153.5, 2065.1],
     [2203, 153, 2065], 'south', [2203.5, 153.5, 2065.12], 36, 0),
    ('Cid', 'wall lever, middle', [2212.5, 151, 2066.6], [2212.5, 152.5, 2065.2],
     [2212, 152, 2065], 'south', [2212.5, 152.5, 2065.2], 40, 0),
    ('Dan', 'floor button', [2185.5, 151, 2070.12], [2185.5, 151.05, 2070.6],
     [2185, 151, 2070], 'up', [2185.5, 151.06, 2070.5], 34, 0),
    ('Eve', 'floor lever', [2203.5, 151, 2072.7], [2203.5, 151.3, 2071.5],
     [2203, 151, 2071], 'up', [2203.5, 151.3, 2071.5], 44, 0),
    ('Fay', 'oak door', [2211.5, 151, 2071.3], [2211.5, 152, 2070.5],
     [2211, 151, 2070], 'south', [2211.5, 151.5, 2070.19], 50, 0),
    ('Gus', 'chest (open, shut)', [2184.5, 151, 2077.4], [2184.5, 151.8, 2076.5],
     [2184, 151, 2076], 'south', [2184.5, 151.5, 2076.95], 60, 30),
    ('Hal', 'repeater', [2184.5, 151, 2084.4], [2184.5, 151.1, 2083.5],
     [2184, 151, 2083], 'up', [2184.5, 151.125, 2083.5], 32, 0),
    ('Ivy', 'comparator', [2191.5, 151, 2084.15], [2191.5, 151.1, 2083.5],
     [2191, 151, 2083], 'up', [2191.5, 151.125, 2083.5], 38, 0),
    ('Jon', 'note block', [2205.5, 151, 2084.4], [2205.5, 152, 2083.5],
     [2205, 151, 2083], 'up', [2205.5, 152, 2083.5], 26, 0),
]


def start():
    steps = [{"closeScreen": True},
             {"config": {"buttonpress.enabled": True, "blockuse.enabled": True, "doorhold.enabled": True,
                         "furniture.enabled": True, "lookat.enabled": False}},
             {"log": "atlas-bots START"}]
    for i, (name, what, at, look, pos, face, hit, every, close) in enumerate(BOTS):
        steps.append({"bot": {"spawn": name, "at": at, "lookAt": look,
                              "cycle": {"use": pos, "face": face, "hit": hit, "every": every,
                                        "offset": i * 7, "close": close}}})
    # The observer: in the middle of the zone, south of the grid, looking north over it.
    steps += [{"cmd": "gamemode creative"}, {"cmd": "tp @s 2203.5 151 2096.5 180 20"}, {"look": [180, 20]},
              {"wait": 20}, {"log": "atlas-bots RUNNING " + ", ".join(f"{b[0]}={b[1]}" for b in BOTS)}]
    return steps


def stop():
    return [{"bot": {"name": b[0], "remove": True}} for b in BOTS] + [{"log": "atlas-bots STOP"}]


if __name__ == "__main__":
    (HERE / "atlas-bots.json").write_text(json.dumps(start(), indent=1, ensure_ascii=False) + "\n")
    (HERE / "atlas-bots-stop.json").write_text(json.dumps(stop(), indent=1, ensure_ascii=False) + "\n")
    print(len(BOTS), "bots ->", HERE / "atlas-bots.json")
