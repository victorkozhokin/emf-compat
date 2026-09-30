# /// script
# requires-python = ">=3.11"
# dependencies = ["mcp>=1.20,<2", "pillow>=11"]
# ///
"""MCP server over mctest.py: launch a Modrinth profile in a sandbox, drive it, look at it.

Registered in the project's .mcp.json; run by hand with ``uv run tools/mctest/server.py``.
"""

from __future__ import annotations

import io
import functools
import json
import sys
import time
from pathlib import Path

from mcp.server.fastmcp import FastMCP, Image
from PIL import Image as PILImage
from PIL import ImageDraw

sys.path.insert(0, str(Path(__file__).resolve().parent))
import mctest  # noqa: E402

mcp = FastMCP("mctest", instructions=(
    "Launches Modrinth profiles (~/Modrinth/profiles) in a sandbox under run/mctest with the "
    "project's freshly built EMF Compat jars swapped in, and drives the game through the mctest "
    "driver mod. Typical loop: mc_launch -> mc_steps (camera front, hold sneak, click attack, "
    "burst screenshots) -> mc_log -> mc_stop. The sandbox is rebuilt on every launch; the "
    "profile itself is never modified."))


def _crop(path: Path, crop: float, size: int, label: str | None = None) -> PILImage.Image:
    img = None
    for _ in range(30):  # the game may still be writing the file
        try:
            img = PILImage.open(path)
            img.load()
            break
        except Exception:
            time.sleep(0.1)
    if img is None:
        raise RuntimeError(f"could not read {path}")
    img = img.convert("RGB")
    if crop < 1.0:
        w, h = img.size
        cw, ch = int(w * crop), int(h * crop)
        cx, cy = w // 2, int(h * 0.55)  # the player sits a little below the middle
        left = max(0, min(w - cw, cx - cw // 2))
        top = max(0, min(h - ch, cy - ch // 2))
        img = img.crop((left, top, left + cw, top + ch))
    img.thumbnail((size, size))
    if label:
        draw = ImageDraw.Draw(img)
        draw.rectangle((0, 0, 8 + 7 * len(label), 16), fill=(0, 0, 0))
        draw.text((4, 2), label, fill=(255, 255, 255))
    return img


def _to_image(img: PILImage.Image) -> Image:
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return Image(data=buf.getvalue(), format="png")


def _sheet(frames: list[PILImage.Image], columns: int) -> PILImage.Image:
    w, h = frames[0].size
    rows = (len(frames) + columns - 1) // columns
    sheet = PILImage.new("RGB", (w * min(columns, len(frames)), h * rows), (0, 0, 0))
    for i, f in enumerate(frames):
        sheet.paste(f, ((i % columns) * w, (i // columns) * h))
    return sheet


def _expand(steps: list[dict], prefix: str) -> list[dict]:
    out = []
    for step in steps:
        if "burst" in step:
            b = step["burst"]
            count, every, name = int(b.get("count", 6)), int(b.get("every", 1)), b.get("name", "burst")
            for i in range(count):
                out.append({"screenshot": f"{prefix}_{name}_{i:02d}"})
                if b.get("fade"):
                    out.append({"fade": True})
                if i < count - 1:
                    out.append({"wait": every})
        elif "screenshot" in step:
            out.append({**step, "screenshot": f"{prefix}_{step['screenshot']}"})
        elif "until" in step and "shots" in step["until"]:
            out.append({"until": {**step["until"], "shots": f"{prefix}_{step['until']['shots']}"}})
        else:
            out.append(step)
    return out


def tool(fn):
    """``@mcp.tool()`` that answers a launcher ``SystemExit`` as a tool error.

    mctest.py reports bad input the CLI way, with SystemExit (a profile without the mod ``enable``
    names, a game that is not running). That is a BaseException: FastMCP does not catch it, and it
    took the whole server down instead of failing the one call.
    """
    @functools.wraps(fn)
    def wrapper(*args, **kwargs):
        try:
            return fn(*args, **kwargs)
        except SystemExit as e:
            raise RuntimeError(str(e)) from None
    return mcp.tool()(wrapper)


@tool
def mc_profiles() -> str:
    """Lists the Modrinth profiles with loader, version, memory, and whether a test driver exists."""
    lines = []
    for p in mctest.list_profiles():
        driver = "driver" if mctest._driver_jar(p) else "NO DRIVER"
        saves = p.path / "saves"
        worlds = sorted(d.name for d in saves.iterdir() if d.is_dir()) if saves.is_dir() else []
        lines.append(f"{p.name!r} ({p.title}) {p.loader} {p.version_id} {p.memory_mb}M {driver} worlds={worlds}")
    return "\n".join(lines)


@tool
def mc_launch(profile: str, world: str | None = None, fresh_world: bool = False,
              wait: bool = True, timeout: int = 300, enable: list[str] | None = None,
              disable: list[str] | None = None, name: str | None = None,
              uuid: str | None = None) -> str:
    """Rebuilds the sandbox for a profile and starts the game offline.

    Our emf_compat jars are replaced by the newest builds in upload/ (run the Gradle build
    first). `world` is a world of the profile, copied into the sandbox once and reused;
    `fresh_world` copies it again. With `wait`, blocks until the player is in the world.
    `enable` names mods the profile keeps as `.disabled` (any part of the file name) and
    switches them on for this sandbox only — the profile itself is never changed. `disable` does
    the opposite for mods the profile runs (e.g. one that takes over first-person hands).
    `name` is the offline player name (default "Dev"); `uuid` a real account's UUID, which makes
    the game fetch that account's skin.
    """
    report = mctest.launch(profile, world, fresh_world=fresh_world, enable=enable, disable=disable,
                           player_name=name or mctest.OFFLINE_NAME, player_uuid=uuid)
    if wait and world:
        report["ready"] = mctest.wait_ready(profile, timeout)
    return json.dumps(report, indent=1)


@tool
def mc_steps(profile: str, steps: list[dict], crop: float = 0.45, size: int = 640,
             sheet: bool = True, columns: int = 4) -> list:
    """Runs a script in game and returns the results plus the screenshots it took.

    Steps run in order on the client thread; `wait` counts ticks (20/s). A screenshot shows
    the last rendered frame, so wait at least a tick after changing something.
      {"cmd": "give @s iron_sword"}   {"chat": "hi"}
      {"hold": "sneak"} {"release": "sneak"} {"releaseAll": true}
          keys: forward back left right jump sneak sprint attack use drop swap inventory
      {"click": "attack"}  {"slot": 0}  {"look": [yaw, pitch]}
      {"camera": "first|back|front"}  {"hideGui": true}  {"closeScreen": true}
  {"orbit": [yawOffset, pitch, distance]} / {"orbit": false}  - side-on camera (NeoForge 1.21.1)
  {"parcool": true}  - ParCool 4 animations, blend factor and driven parts (NeoForge 1.21.1)
  {"bot": {"spawn": "Bob", "at": [x,y,z], "look": [yaw,pitch]}}  - another player (NeoForge 1.21.1):
      a server player on a dead-end connection, seen by our client as a RemotePlayer. Then
      {"bot": {"at": .., "look": .., "sneak": true, "item": "minecraft:stick"}}, {"bot": {"use": [x,y,z],
      "face": "west"}} (right click + swing), {"bot": {"swing": true}}, {"bot": {"remove": true}};
      "name" when there are several. It does not move by itself.
  state.target has "pos" and "world" (the hit carried out of a Sable craft's plot into the world)
      {"wait": 10}  {"state": true}  {"log": "marker in latest.log"}
      {"screenshot": "name"}
      {"burst": {"count": 8, "every": 1, "name": "attack", "fade": true}}  - screenshots every
          N ticks; with "fade", each frame also records the core's fade state
      {"fade": true}  - pose sources on the player and each part's fade weight ("0.62 in/out")
      {"config": {"core.smoothPoseTransitions": false}}  - core options, in memory only
      {"packs": ["FreshAnimations", "FA+Player"]}  - the resource packs to run with, in order
          (later wins); everything else off. The reload happens after the script answers, so
          make this the last step of its own call and let the next call wait for it.
      {"model": "villager"}  or  {"model": {"entity": "player", "depth": 3}}  - the model the
          renderer uses: each ModelPart field, what it really is, how many cubes it has left
          and its transform. Under EMF a part a pack replaced reports cubes: 0 and keeps the
          geometry in a custom child - that is what misplaces hats and worn items.
    Images are cropped around the player (`crop` = fraction of the frame kept, 1.0 = all)
    and, with `sheet`, laid out on one contact sheet in shooting order.

    Attacks: a left click on a block within reach is mining, not an attack — Better Combat
    then plays nothing and vanilla just swings. `state.target` shows what the crosshair is on;
    look up (`{"look": [yaw, -30]}`) or clear the area (`fill ~-5 ~ ~-5 ~5 ~3 ~5 air`, the world
    is a sandbox copy) before attacking. Crouching lowers the eyes onto grass. With the
    `front` camera the player looks away from the camera, into what is behind them on screen.
    """
    prefix = time.strftime("%H%M%S")
    result = mctest.run_steps(profile, _expand(steps, prefix))
    shots = []
    for r in result.get("results", []):
        if r.get("screenshot"):
            shots.append((r["screenshot"], r["step"]))
            r["screenshot"] = Path(r["screenshot"]).name
        if r.get("screenshots"):  # an `until` that shot while it waited
            shots += [(p, r["step"]) for p in r["screenshots"]]
            r["screenshots"] = len(r["screenshots"])
    out: list = [json.dumps(result, indent=1)]
    if not shots:
        return out
    frames = [_crop(Path(p), crop, size, Path(p).stem.removeprefix(prefix + "_")) for p, _ in shots]
    if sheet and len(frames) > 1:
        out.append(_to_image(_sheet(frames, columns)))
    else:
        out += [_to_image(f) for f in frames]
    return out


@tool
def mc_screenshot(profile: str, crop: float = 1.0, size: int = 1024) -> list:
    """Takes one screenshot of the current frame (whole frame by default)."""
    return mc_steps(profile, [{"screenshot": "shot"}], crop=crop, size=size, sheet=False)


@tool
def mc_status(profile: str) -> str:
    """Whether the game runs, and what the driver last reported (in world, screen, fps)."""
    return json.dumps(mctest.status(profile), indent=1)


@tool
def mc_log(profile: str, lines: int = 80, grep: str | None = None) -> str:
    """Tail of the sandbox's latest.log, optionally filtered by a regex."""
    return mctest.log_tail(profile, lines, grep)


@tool
def mc_stop(profile: str) -> str:
    """Stops the game started by mc_launch."""
    return mctest.stop(profile)


if __name__ == "__main__":
    mcp.run()
