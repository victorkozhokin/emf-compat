"""Makes a copy of the ATLAS world (saved by 1.21.1 with the NeoForge profile's mods) openable by 1.20.1.

    python3 tools/worldfix/atlas_to_1_20_1.py "<world folder>"

A game does not take a world back to an older version, and refuses one that names a dimension of a
mod it does not have. So, in that folder: every dimension that is not the game's own is dropped
from level.dat, the level is marked as saved by 1.20.1, the player is dropped (the items are in a
format 1.20.1 does not read - the player starts at the world's spawn), and the datapack's function
folder gets the name 1.20.1 looks for. The chunks are left as they are: blocks 1.20.1 does not
know come out as air."""
import shutil, sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import nbt

world = Path(sys.argv[1])
name, root = nbt.load(world / "level.dat")
data = root["Data"][1]
dims = data["WorldGenSettings"][1]["dimensions"][1]
for key in [k for k in dims if not k.startswith("minecraft:")]:
    del dims[key]
    print("dropped dimension", key)
data["DataVersion"] = (nbt.INT, 3465)
data["Version"] = (nbt.COMPOUND, {"Snapshot": (nbt.BYTE, 0), "Series": (nbt.STRING, "main"),
                                  "Id": (nbt.INT, 3465), "Name": (nbt.STRING, "1.20.1")})
data.pop("Player", None)
packs = data["DataPacks"][1]
kept = [p for p in packs["Enabled"][1][1] if p == "vanilla" or p.startswith("file/")]
packs["Enabled"] = (nbt.LIST, (nbt.STRING, kept))
nbt.save(world / "level.dat", name, root)
for stale in ("level.dat_old", "session.lock"):
    (world / stale).unlink(missing_ok=True)
for folder in ("playerdata", "advancements", "stats"):
    shutil.rmtree(world / folder, ignore_errors=True)
for functions in world.glob("datapacks/*/data/*/function"):
    functions.rename(functions.with_name("functions"))
    print("renamed", functions)
print("done:", world)
