"""Extend the existing Atlas with zone 11; never rebuild existing sections."""
import json
import os
from pathlib import Path

world = Path(os.environ.get("ATLAS_WORLD", "run/mctest/Test/saves/EMF ATLAS - Animation Campus"))
folder = world / "datapacks/emf_atlas/data/emf_atlas/function"
if not (world / "datapacks/emf_atlas/pack.mcmeta").exists():
    raise SystemExit("Existing Atlas required")
commands = ["kill @e[tag=atlas_wallhand]",
            "fill 2300 148 2058 2334 149 2110 deepslate_tiles",
            "fill 2300 150 2058 2334 150 2110 smooth_quartz",
            "fill 2300 151 2058 2334 155 2110 air",
            "fill 2295 150 2106 2334 150 2108 cyan_concrete"]
for x in (2300, 2334): commands.append(f"fill {x} 150 2058 {x} 150 2110 waxed_cut_copper")
for z in (2058, 2110): commands.append(f"fill 2300 150 {z} 2334 150 {z} waxed_cut_copper")
for x in range(2301, 2334, 6): commands.append(f"setblock {x} 150 2107 sea_lantern")


def label(x, y, z, text, scale=.6):
    content = json.dumps(json.dumps({"text": text, "color": "aqua"}, ensure_ascii=False), ensure_ascii=False)
    commands.append(f'summon text_display {x} {y} {z} {{Tags:["atlas","atlas_wallhand"],text:{content},billboard:"center",line_width:280,transformation:{{scale:[{scale}f,{scale}f,{scale}f]}}}}')


for x in (2305, 2315, 2325):
    commands += [f"fill {x-1} 151 2064 {x-1} 153 2085 stone_bricks",
                 f"fill {x+1} 151 2064 {x+1} 153 2085 stone_bricks",
                 f"fill {x} 150 2063 {x} 150 2087 yellow_concrete"]
for y in range(151, 154):
    commands.append(f"fill 2315 {y} 2064 2315 {y} 2085 iron_trapdoor[facing=east,open=true,half=bottom]")
commands += ["fill 2324 151 2074 2324 153 2077 air",
             "fill 2326 151 2081 2326 153 2085 air"]
label(2305.5, 154.5, 2089, "ОДНА СТЕНА / БОКОМ")
label(2315.5, 154.5, 2089, "УЗКИЙ ПРОХОД / SHIFT")
label(2325.5, 154.5, 2089, "РАЗРЫВ СТЕНЫ / ВЫХОД")
label(2317, 155, 2109, "11 / WALL CONTACT LAB", 1.1)
label(2317, 153.8, 2109, "Вход • касание • движение • отпускание", .65)
commands += ['setblock 2291 151 2103 command_block{Command:"tp @p 2317 151 2107 180 0",TrackOutput:0b}',
             "setblock 2291 152 2103 polished_blackstone_button[face=floor]"]
label(2291.5, 153, 2103, "11 / WALL CONTACT LAB")
(folder / "wallhand.mcfunction").write_text("\n".join(commands) + "\n")
print(folder / "wallhand.mcfunction")
