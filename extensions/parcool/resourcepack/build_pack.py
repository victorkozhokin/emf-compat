#!/usr/bin/env python3
"""Builds the ParCool animation pack on top of an installed Fresh Animations: Player Extension.

The pack animates ParCool moves in FA+Player's procedural style (a_player_parcool.jpm, generated
by animations.py against the installed FA+Player's layer variables). It
has to be listed in player.jem to run, and player.jem is FreshLX's, whose terms forbid sharing
their assets unedited - so this repository ships only our module and patches the player models
from the FA+Player the user already has:

    python3 extensions/parcool/resourcepack/build_pack.py <FA+Player zip or folder> <resourcepacks dir>

With ``--builtin`` it writes the same pack into the mod's own resources instead, as the built-in
pack the jar ships (``resourcepacks/parcool_animations``; see BuiltinAnimationPack). FreshLX has
allowed us to ship their edited player models, so that copy goes into the repository and into the
jar - regenerate it whenever FA+Player is updated.

The cape is patched too, to follow the torso while ParCool poses it (see patch_cape), and FA's
variables, so a ParCool pole climb plays FA's ladder climb (see patch_variables).

The result is a folder pack, "EMF Compat ParCool Animations", to be placed above FA+Player.
"""
import json
import re
import shutil
import sys
import zipfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
PACK_NAME = "EMF Compat ParCool Animations"
# The folder inside the jar, and the id the mod looks for: see BuiltinAnimationPack.
BUILTIN_ID = "parcool_animations"
CEM = "assets/minecraft/emf/cem"
MODULE = "a_player_parcool.jpm"
AFTER = "a_player_movement.jpm"


def read_fa(source: Path, name: str) -> str:
    if source.is_dir():
        return (source / CEM / name).read_text(encoding="utf-8")
    with zipfile.ZipFile(source) as z:
        return z.read(f"{CEM}/{name}").decode("utf-8")


def fa_layer_vars(source: Path) -> set[str]:
    names = set()
    for jpm in ("a_player_idle.jpm", "a_player_movement.jpm"):
        for block in json.loads(read_fa(source, jpm))["animations"]:
            names.update(k for k in block if re.match(r"var\.(idl|mvmnt|vrtcl|fly)_", k))
    return names


def hip(side: float, axis: str) -> str:
    """Where the top of a leg belongs on the torso as posed right now: the point (side, 12, 0) below
    the torso's pivot, turned the way ModelPart turns it (x, then y, then z)."""
    x0 = f"(({side})*cos(body.ry) +12*sin(body.rx)*sin(body.ry))"
    y0 = "(12*cos(body.rx))"
    return {
        "x": f"body.tx +{x0}*cos(body.rz) -{y0}*sin(body.rz)",
        "y": f"body.ty +{x0}*sin(body.rz) +{y0}*cos(body.rz)",
        "z": f"body.tz -({side})*sin(body.ry) +12*sin(body.rx)*cos(body.ry)",
    }[axis]


def leg_attach() -> dict:
    """FA's legs are not children of the torso: each layer moves them on its own. Under ParCool's
    hang and climb the torso goes where FA's legs do not follow - it swings and shifts under the
    hands - so while one holds (``var.pc_att``, eased out after) the legs are put back on the hips.
    Runs after FA sets the legs and before the pants copy them."""
    block = {}
    for leg, side in (("right_leg", -2), ("left_leg", 2)):
        for axis in "xyz":
            part = f"{leg}.t{axis}"
            block[part] = f"{part} +( {hip(side, axis)} -{part} )*var.pc_att"
    return block


def patch(jem_text: str) -> str:
    jem = json.loads(jem_text)
    models = jem["models"]
    if any(m.get("model") == MODULE for m in models):
        return json.dumps(jem, indent=1)
    index = next(i for i, m in enumerate(models) if m.get("model") == AFTER)
    models.insert(index + 1, {"part": "root", "id": "root", "invertAxis": "xy",
                              "translate": [0, 0, 0], "model": MODULE})
    for model in models:
        blocks = model.get("animations", [])
        at = next((i for i, block in enumerate(blocks) if "right_leg.tx" in block), None)
        if at is not None:
            blocks.insert(at + 1, leg_attach())
            break
    else:
        raise SystemExit("FA+Player no longer sets right_leg.tx in its player model; the leg patch is out of date")
    jem["credit"] = jem.get("credit", "") + " | ParCool module: EMF Compat"
    return json.dumps(jem, indent=1)


CAPE = "player_cape.jem"


def patch_cape(jem_text: str) -> str:
    """FA hangs the cape off its own torso variables, not the torso part, so while ParCool holds the
    torso in its pose the cape stays where FA's torso would be and tears off the back. The torso's
    share in those variables is eased out by how much ParCool holds it (``parcool_body_held``)."""
    jem = json.loads(jem_text)
    held = "(1-parcool_body_held)"
    count = 0
    for model in jem["models"]:
        for block in model.get("animations", []):
            for key, expr in block.items():
                if not re.match(r"cloak2?\.", key):
                    continue
                new = re.sub(r"var\.(body_(?:rx|ry|rz|tx|ty|tz)|idl_bodyrx)\b",
                             lambda m: f"(var.{m.group(1)}*{held})", expr)
                if new != expr:
                    block[key] = new
                    count += 1
    if count == 0:
        raise SystemExit("FA+Player's cape does not read the torso variables it used to; the cape patch is out of date")
    return json.dumps(jem, indent=1)


VARIABLES = "a_player_variables.jpm"
CLIMBING = "(is_climbing || parcool_pole_climb>0)"


def patch_variables(jpm_text: str) -> str:
    """FA climbs a ladder only while vanilla says the player is climbing, and a chain is no ladder to
    vanilla. ParCool's pole climb counts as climbing too, so FA plays its own ladder climb for it
    (reading ``parcool_pole_climb`` is also what hands the move over from ParCool)."""
    jpm = json.loads(jpm_text)
    count = 0
    for block in jpm["animations"]:
        for key, expr in block.items():
            new = re.sub(r"\bis_climbing\b", CLIMBING, expr)
            if new != expr:
                block[key] = new
                count += 1
    if count == 0:
        raise SystemExit("FA+Player's variables no longer read is_climbing; the climb patch is out of date")
    return json.dumps(jpm, indent=1)


def main(argv: list[str]) -> None:
    builtin = "--builtin" in argv
    argv = [a for a in argv if a != "--builtin"]
    if len(argv) != 2:
        raise SystemExit(__doc__)
    source, target_dir = Path(argv[0]), Path(argv[1])
    out = target_dir / (BUILTIN_ID if builtin else PACK_NAME)
    if out.exists():
        shutil.rmtree(out)
    (out / CEM).mkdir(parents=True)
    for jem in ("player.jem", "player_slim.jem"):
        (out / CEM / jem).write_text(patch(read_fa(source, jem)), encoding="utf-8")
    (out / CEM / CAPE).write_text(patch_cape(read_fa(source, CAPE)), encoding="utf-8")
    (out / CEM / VARIABLES).write_text(patch_variables(read_fa(source, VARIABLES)), encoding="utf-8")
    sys.path.insert(0, str(HERE))
    import animations
    (out / CEM / MODULE).write_text(json.dumps(animations.build(fa_layer_vars(source)), indent=2),
                                    encoding="utf-8")
    (out / "pack.mcmeta").write_text(json.dumps({"pack": {
        "pack_format": 34, "supported_formats": {"min_inclusive": 15, "max_inclusive": 999},
        "description": ("ParCool moves for FA+Player. Turns itself on above FA+Player."
                        if builtin else
                        "ParCool moves for FA+Player - needs EMF Compat: ParCool. Place above FA+Player.")}},
        indent=2), encoding="utf-8")
    shutil.copyfile(HERE / "pack.png", out / "pack.png")
    (out / "credits.txt").write_text(
        "Player models edited from Fresh Animations: Player Extension by FreshLX\n"
        "https://modrinth.com/resourcepack/fa-player-extension\n"
        "ParCool animation module: EMF Compat (STRadaT)\n"
        "Shipped with FreshLX's permission\n"
        "\n"
        "player.jem, player_slim.jem, player_cape.jem and a_player_variables.jpm are (c) FreshLX.\n"
        "They are not under this mod's GPL-3.0 licence: Fresh Animations' own terms apply to them,\n"
        "and they may not be shared or reused further without FreshLX's permission.\n"
        "a_player_parcool.jpm is EMF Compat's own and is under GPL-3.0.\n", encoding="utf-8")
    print(out)


if __name__ == "__main__":
    main(sys.argv[1:])
