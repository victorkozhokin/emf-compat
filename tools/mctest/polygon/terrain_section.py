"""Add zone 10 to an existing Atlas. Writes only terrain.mcfunction; never rebuilds the campus.

ATLAS_WORLD=<world> python3 tools/mctest/polygon/terrain_section.py
Then /reload and /function emf_atlas:terrain. Existing sections and guide remain intact.
"""
import json
import os
from pathlib import Path

WORLD = Path(os.environ.get("ATLAS_WORLD", "run/mctest/Test/saves/EMF ATLAS - Animation Campus"))
FUN = WORLD / "datapacks/emf_atlas/data/emf_atlas/function"
if not (WORLD / "datapacks/emf_atlas/pack.mcmeta").exists():
    raise SystemExit("Existing Atlas datapack required")
commands = ["kill @e[tag=atlas_terrain]"]
def fill(x,y,z,X,Y,Z,b): commands.append(f"fill {x} {y} {z} {X} {Y} {Z} {b}")
def block(x,y,z,b): commands.append(f"setblock {x} {y} {z} {b}")
def label(x,y,z,title,color="aqua",scale=.6):
    content=json.dumps(json.dumps({"text":title,"color":color},ensure_ascii=False),ensure_ascii=False)
    commands.append(f'summon text_display {x} {y} {z} {{Tags:["atlas","atlas_terrain"],text:{content},billboard:"center",background:1073741824,line_width:280,transformation:{{scale:[{scale}f,{scale}f,{scale}f]}}}}')
def button(x,z,title,command):
    block(x,151,z,"command_block{Command:"+json.dumps(command)+",TrackOutput:0b}")
    block(x,152,z,"polished_blackstone_button[face=floor]")
    label(x+.5,153,z+.5,title)

# Independent eastern annex; core Atlas lies west of x=2237.
fill(2244,148,2058,2294,149,2110,"deepslate_tiles")
fill(2244,150,2058,2294,150,2110,"smooth_quartz")
fill(2244,151,2058,2294,158,2110,"air")
for x in [2244,2294]: fill(x,150,2058,x,150,2110,"waxed_cut_copper")
for z in [2058,2110]: fill(2244,150,z,2294,150,z,"waxed_cut_copper")
fill(2245,150,2106,2293,150,2108,"cyan_concrete")
for x in range(2246,2294,6): block(x,150,2107,"sea_lantern")
label(2269.5,156,2108,"10 / BALANCE LAB",scale=1.35)
label(2269.5,154,2108,"Узкие опоры • присяд • склоны • переходы",scale=.75)

for x,mat,title in [(2251,"oak_fence","ЗАБОР"),(2261,"cobblestone_wall","СТЕНА"),(2271,"iron_bars","РЕШЁТКА")]:
    fill(x,151,2067,x,151,2091,mat)
    fill(x-1,151,2092,x+1,151,2095,"stone")
    fill(x-1,152,2092,x+1,152,2095,"stone_slab[type=bottom]")
    label(x+.5,153.7,2098,title)
    label(x+.5,153.1,2098,"Стоя / Shift / вперёд / назад",scale=.45)
    button(x,2103,"СТАРТ",f"tp @p {x+.5} {152.5 if mat!='iron_bars' else 152} 2071.5 0 0")
    block(x,150,2071,"yellow_concrete")
    block(x,150,2082,"yellow_concrete")

# Stair ramp and slab ramp with wide landings for entry/exit measurements.
for i in range(6):
    for x,z,mat in [(2280,2066,"stone_stairs[facing=south]"),(2287,2066,"stone_slab[type=bottom]")]:
        y=151+i if x==2280 else 151+i//2
        fill(x,151,z+i,x+2,y,z+i,"stone")
        fill(x,y,z+i,x+2,y,z+i,mat if x==2280 or i%2==0 else "stone")
fill(2280,151,2072,2282,156,2075,"stone")
fill(2287,151,2072,2289,153,2075,"stone")
label(2281.5,154,2090,"СКЛОН / СТУПЕНИ")
label(2288.5,154,2090,"СКЛОН / ПОЛУБЛОКИ")
button(2281,2103,"СТАРТ", "tp @p 2281.5 151 2064.5 0 0")
button(2288,2103,"СТАРТ", "tp @p 2288.5 151 2064.5 0 0")
button(2247,2103,"К ВХОДУ", "tp @p 2144.5 151 2237.5 180 0")
button(2177,2241,"10 / BALANCE LAB", "tp @p 2269.5 151 2107.5 180 8")
# Small doorway through the east rim into the existing transverse walkway.
fill(2236,151,2108,2243,153,2110,"air")
fill(2236,150,2108,2243,150,2110,"cyan_terracotta")
commands.append('tellraw @a {"text":"EMF ATLAS • зона 10: узкие опоры и склоны готовы","color":"aqua"}')
FUN.mkdir(parents=True,exist_ok=True)
(FUN/"terrain.mcfunction").write_text("\n".join(commands)+"\n")
print(FUN/"terrain.mcfunction",len(commands),"commands")
