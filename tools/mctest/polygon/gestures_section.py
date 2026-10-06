"""Add zones 12 and 13, the field station's signposting and the campus decor to an existing Atlas.

ATLAS_WORLD=<world> python3 tools/mctest/polygon/gestures_section.py
Then /reload and, each on its own:
  /function emf_atlas:gestures        zone 12 CARE / GESTURES, south of zone 10
  /function emf_atlas:mining_gallery  zone 13 MINING GALLERY, south of zone 11
  /function emf_atlas:field           paths, labels and buttons round the cockpit, lead and effort pads
  /function emf_atlas:decor           banners, trees, hub markers and the hub buttons to all of these
Nothing here rebuilds the campus or reaches into another stand; every entity carries its section's
tag, so a section run again replaces only itself. The chunks have to be loaded: stand there, or
`forceload add` the section's corners first.
"""
import json
import os
from pathlib import Path

WORLD = Path(os.environ.get("ATLAS_WORLD", "run/mctest/Test/saves/EMF ATLAS - Animation Campus"))
FUN = WORLD / "datapacks/emf_atlas/data/emf_atlas/function"
if not (WORLD / "datapacks/emf_atlas/pack.mcmeta").exists():
    raise SystemExit("Existing Atlas datapack required")
HUB = "tp @p 2144.5 151 2237.5 180 0"


class Section:
    """One function's commands; its labels and creatures carry `tag`."""

    def __init__(self, tag, floor=150, front="north"):
        self.tag, self.floor, self.front, self.c = tag, floor, front, [f"kill @e[tag={tag}]"]

    def cmd(self, s):
        self.c.append(s)

    def fill(self, x, y, z, X, Y, Z, b):
        self.c.append(f"fill {x} {y} {z} {X} {Y} {Z} {b}")

    def ring(self, x, y, z, X, Z, b):
        """The four edges of a rectangle, one block high."""
        for a, c, A, C in ((x, z, X, z), (x, Z, X, Z), (x, z, x, Z), (X, z, X, Z)):
            self.fill(a, y, c, A, y, C, b)

    def block(self, x, y, z, b):
        self.c.append(f"setblock {x} {y} {z} {b}")

    def label(self, *_, **__):
        """Nothing: names hung in the air were taken out; what has to be said is on a sign."""

    @staticmethod
    def lines(title):
        """A title cut into a sign's four lines of some fifteen letters."""
        out = [""]
        for word in title.split():
            if out[-1] and len(out[-1]) + len(word) + 1 > 15:
                out.append("")
            out[-1] = (out[-1] + " " + word).strip()
        return out[:4]

    def messages(self, lines):
        return ",".join("'" + json.dumps({"text": v, "color": "black"}, ensure_ascii=False) + "'" for v in (lines + [""] * 4)[:4])

    def sign(self, x, z, lines, rotation=8, y=None):
        self.block(x, self.floor + 1 if y is None else y, z, f"oak_sign[rotation={rotation}]{{is_waxed:1b,front_text:{{messages:[{self.messages(lines)}]}}}}")

    def plate(self, x, y, z, title, front=None):
        """A sign on the side of the block at x y z that the visitor comes to."""
        front = front or self.front
        dz = -1 if front == "north" else 1
        self.block(x, y, z + dz, f"oak_wall_sign[facing={front}]{{is_waxed:1b,front_text:{{messages:[{self.messages(self.lines(title))}]}}}}")

    def button(self, x, z, title, command, y=None, front=None):
        y = self.floor + 1 if y is None else y
        self.block(x, y, z, "command_block{Command:" + json.dumps(command, ensure_ascii=False) + ",TrackOutput:0b}")
        self.block(x, y + 1, z, "polished_blackstone_button[face=floor]")
        self.plate(x, y, z, title, front)

    def barrel(self, x, z, items, title, y=None):
        y = self.floor + 1 if y is None else y
        self.block(x, y, z, "air")
        self.block(x, y, z, "barrel[facing=up]")
        self.plate(x, y, z, title)
        for i, item in enumerate(items[:27]):
            self.c.append(f"item replace block {x} {y} {z} container.{i} with {item}")

    def lamp(self, x, z, height=4):
        y = self.floor + 1
        self.fill(x, y, z, x, y + height - 1, z, "polished_deepslate_wall")
        self.block(x, y + height, z, "sea_lantern")
        self.block(x, y + height + 1, z, "waxed_cut_copper_slab")

    def platform(self, x, z, X, Z, stripe):
        """The annexes' common floor: deepslate under quartz, a copper rim, a lit stripe along the north edge."""
        self.fill(x, 148, z, X, 149, Z, "deepslate_tiles")
        self.fill(x, 150, z, X, 150, Z, "smooth_quartz")
        self.fill(x, 151, z, X, 160, Z, "air")
        self.ring(x, 150, z, X, Z, "waxed_cut_copper")
        self.fill(x + 1, 150, z + 1, X - 1, 150, z + 3, stripe)
        for a in range(x + 4, X - 2, 6):
            self.block(a, 150, z + 2, "sea_lantern")
        for a, c in ((x, z), (X, z), (x, Z), (X, Z)):
            self.lamp(a, c)

    def write(self, name):
        (FUN / f"{name}.mcfunction").write_text("\n".join(self.c) + "\n")
        print(FUN / f"{name}.mcfunction", len(self.c), "commands")


# ---------------------------------------------------------------- zone 12: care and gestures
g = Section("atlas_gestures")
restock = Section("atlas_gestures_live")  # what a test uses up: the creatures, the stands, the beds, the basins
X0, Z0, X1, Z1 = 2244, 2111, 2294, 2166
g.platform(X0, Z0, X1, Z1, "lime_concrete")
g.label(2269.5, 158, 2116.5, "12 / CARE & GESTURES", scale=2)
g.label(2269.5, 156.4, 2116.5, "Кормление • дойка • стрижка • стойка • посев • броня • отряхивание • сундуки", scale=1)
# Row A: the pens. Open the gate and go in - a hand is put out only from within two blocks.
PENS = [("cow", "КОРОВА", "пшеница / ведро", ""), ("sheep", "ОВЦА", "пшеница / ножницы", ""),
        ("goat", "КОЗА", "пшеница / ведро", ""), ("pig", "СВИНЬЯ", "морковь", ""),
        ("chicken", "КУРИЦА", "семена: низкая цель", ""), ("horse", "ЛОШАДЬ", "золотая морковь: высокая", "Tame:1b,")]
for i, (kind, title, how, extra) in enumerate(PENS):
    a, c = 2247 + i * 7, 2117
    g.fill(a, 150, c, a + 5, 150, c + 7, "grass_block")
    g.ring(a, 151, c, a + 5, c + 7, "oak_fence")
    g.fill(a + 2, 151, c, a + 3, 151, c, "oak_fence_gate[facing=south]")
    g.block(a, 152, c, "lantern")
    g.block(a + 5, 152, c, "lantern")
    g.block(a + 5, 151, c + 7, "air")
    g.block(a + 5, 151, c + 7, "hay_block")
    g.sign(a + 1, c - 1, [title, "", *g.lines(how)])
    restock.cmd(f'summon {kind} {a + 3} 151 {c + 4} {{Tags:["atlas","atlas_gestures_live"],PersistenceRequired:1b,Invulnerable:1b,{extra}FallDistance:0f}}')
    if kind in ("cow", "sheep"):
        restock.cmd(f'summon {kind} {a + 2} 151 {c + 5} {{Tags:["atlas","atlas_gestures_live"],PersistenceRequired:1b,Invulnerable:1b,Age:-6000000}}')
g.barrel(2290, 2119, ["wheat 64", "wheat_seeds 64", "carrot 64", "golden_carrot 32", "bucket 16", "shears", "shears", "lead 8", "beetroot 32", "apple 16"], "Корм / ведро / ножницы")
g.sign(2290, 2121, ["Жест начинается", "ДО клика: цель", "под прицелом,", "не дальше 2 бл."], rotation=8)
# Row B: armour stands, a seed bed, containers.
g.fill(2247, 150, 2130, 2258, 150, 2134, "polished_andesite")
for i, (dy, title) in enumerate([(0, "НА ПОЛУ"), (1, "НА БЛОКЕ"), (0, "С РУКАМИ"), (0, "НИЗКАЯ")]):
    a = 2248 + i * 3
    if dy:
        g.block(a, 151, 2132, "smooth_stone")
    extra = "ShowArms:1b," if i == 2 else "Small:1b," if i == 3 else ""
    restock.cmd(f'summon armor_stand {a}.5 {151 + dy} 2132.5 {{Tags:["atlas","atlas_gestures_live"],{extra}Rotation:[180f],Invulnerable:1b}}')
    g.sign(a, 2130, g.lines(title))
g.barrel(2259, 2132, ["iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "leather_helmet", "leather_chestplate", "leather_leggings", "leather_boots",
                      "carved_pumpkin", "elytra", "shield", "iron_sword"], "На стойку")
g.sign(2259, 2130, ["СТОЙКА", "Предмет: ПКМ", "по нужной части", "рука идёт туда"])
BED = "fill 2264 150 2130 2272 150 2134 farmland[moisture=7]"
g.fill(2263, 150, 2129, 2273, 150, 2135, "dark_oak_planks")
restock.cmd("fill 2264 151 2130 2272 151 2134 air")
restock.cmd(BED)
restock.cmd("fill 2264 150 2132 2272 150 2132 water")
g.barrel(2274, 2132, ["wheat_seeds 64", "carrot 64", "potato 64", "beetroot_seeds 64", "melon_seeds 16", "pumpkin_seeds 16", "nether_wart 16", "bone_meal 64", "iron_hoe"], "Семена")
g.sign(2274, 2130, ["ПОСЕВ", "Иди вдоль ряда,", "зажав ПКМ:", "рука не встаёт"])
g.label(2268.5, 153, 2132.5, "ПОСЕВ / РЯД")
BOXES = ["chest[facing=north]", "barrel[facing=north]", "shulker_box", "ender_chest[facing=north]", "trapped_chest[facing=north]"]
for i, b in enumerate(BOXES):
    g.block(2278 + i * 2, 151, 2132, "air")
    g.block(2278 + i * 2, 151, 2132, b)
g.fill(2288, 151, 2132, 2289, 151, 2132, "air")
g.block(2288, 151, 2132, "chest[facing=north,type=left]")
g.block(2289, 151, 2132, "chest[facing=north,type=right]")
g.block(2291, 151, 2132, "polished_andesite")
g.block(2291, 152, 2132, "air")
g.block(2291, 152, 2132, "barrel[facing=north]")
g.label(2284.5, 153.4, 2132.5, "КОНТЕЙНЕРЫ / ПОИСК ВНУТРИ")
g.sign(2284, 2130, ["СУНДУК", "Открой и держи:", "одна рука на краю,", "другая ищет"])
# Row C: something to be shaken off, armour for oneself, things to pocket.
BASINS = [(2248, "water", "ВОДА", "prismarine_bricks"), (2256, "powder_snow", "РЫХЛЫЙ СНЕГ", "snow_block"), (2264, "mud", "ГРЯЗЬ", "packed_mud")]
for a, what, title, rim in BASINS:
    g.fill(a - 1, 150, 2141, a + 5, 150, 2147, rim)
    g.fill(a, 149, 2142, a + 4, 149, 2146, rim)
    restock.cmd(f"fill {a} 150 2142 {a + 4} 150 2146 {what}")
    if what == "water":
        g.fill(a, 148, 2142, a + 4, 148, 2146, rim)
        restock.cmd(f"fill {a} 149 2142 {a + 4} 149 2145 water")
    g.sign(a + 2, 2140, [title, "Постой внутри", "2 секунды, выйди", "и встань"])
g.label(2258.5, 153.6, 2140.5, "ОТРЯХИВАНИЕ: постой внутри 2 с, выйди и встань", "white", .8)
g.fill(2274, 150, 2141, 2280, 150, 2147, "polished_andesite")
g.barrel(2275, 2144, ["iron_helmet", "iron_chestplate", "iron_leggings", "iron_boots", "diamond_helmet", "diamond_chestplate", "diamond_leggings", "diamond_boots",
                      "leather_boots", "turtle_helmet", "elytra", "carved_pumpkin"], "Броня на себя")
g.button(2277, 2144, "СНЯТЬ БРОНЮ", "function emf_atlas:gestures_strip")
g.sign(2279, 2144, ["БРОНЯ", "ПКМ с предметом", "или через", "инвентарь (E)"])
g.fill(2284, 150, 2141, 2291, 150, 2147, "light_gray_concrete")
g.button(2285, 2144, "РАССЫПАТЬ ПРЕДМЕТЫ", "function emf_atlas:gestures_scatter")
g.sign(2285, 2142, ["КАРМАН", "Собери предметы,", "встань: через 2 с", "жест за спину"])
# The way in and out.
g.sign(2268, 2115, ["ЗОНА 12", "CARE & GESTURES", "уход и жесты", ""])
g.button(2246, 2114, "К ВХОДУ", HUB)
g.button(2249, 2114, "ВОССТАНОВИТЬ", "function emf_atlas:gestures_restock")
g.button(2252, 2114, "SURVIVAL", "gamemode survival @p")
g.button(2255, 2114, "CREATIVE", "gamemode creative @p")
g.button(2292, 2114, "13 / MINING →", "tp @p 2317.5 151 2112.5 0 8")
# Planters between the rows and along the far edge.
for a in range(2248, 2292, 8):
    for c in (2126, 2138, 2152):
        g.block(a, 151, c, "flowering_azalea_leaves[persistent=true]")
for a in range(2250, 2290, 10):
    g.fill(a - 1, 151, 2160, a + 1, 151, 2162, "waxed_cut_copper")
    g.block(a, 151, 2161, "grass_block")
    g.fill(a, 152, 2161, a, 155, 2161, "birch_log")
    g.fill(a - 2, 155, 2159, a + 2, 156, 2163, "birch_leaves[persistent=true]")
    g.fill(a - 1, 157, 2160, a + 1, 157, 2162, "birch_leaves[persistent=true]")
g.block(2262, 151, 2157, "dark_oak_stairs[facing=south]")
g.block(2276, 151, 2157, "dark_oak_stairs[facing=south]")
# Rebuilt in place, the old lanterns and signs drop as items.
g.cmd("kill @e[type=item,x=2244,y=148,z=2111,dx=51,dy=14,dz=56]")
g.cmd("function emf_atlas:gestures_restock")
g.cmd('tellraw @a {"text":"EMF ATLAS • зона 12: уход за животными и жесты готовы","color":"aqua"}')
g.write("gestures")
restock.write("gestures_restock")
(FUN / "gestures_strip.mcfunction").write_text("\n".join(f"item replace entity @p armor.{s} with air" for s in ("head", "chest", "legs", "feet")) + "\n")
ITEMS = ["cobblestone", "oak_log", "wheat", "iron_ingot", "apple", "stick", "coal", "feather"]
(FUN / "gestures_scatter.mcfunction").write_text("kill @e[type=item,x=2283,y=150,z=2140,dx=9,dy=4,dz=8]\n" + "\n".join(
    f'summon item {2286.5 + i % 4 * 1.3} 151.5 {2143.5 + i // 4 * 2} {{Item:{{id:"minecraft:{item}",count:1}},PickupDelay:20}}' for i, item in enumerate(ITEMS)) + "\n")

# ---------------------------------------------------------------- zone 13: the mining gallery
m = Section("atlas_mining")
reset = []
m.platform(2300, Z0, 2334, Z1, "light_gray_concrete")
m.label(2317.5, 159.6, 2116.5, "13 / MINING GALLERY", scale=2)
m.label(2317.5, 158, 2116.5, "Инструмент • высота удара • стойка • три случайных удара", scale=1)
BAYS = [("КИРКА", "iron_pickaxe", ["stone", "deepslate", "iron_ore", "cobblestone", "obsidian"]),
        ("ТОПОР", "iron_axe", ["oak_log", "spruce_planks", "birch_log", "dark_oak_log", "oak_planks"]),
        ("ЛОПАТА", "iron_shovel", ["dirt", "clay", "coarse_dirt", "snow_block", "gravel"]),
        ("МОТЫГА", "iron_hoe", ["hay_block", "moss_block", "oak_leaves[persistent=true]", "sculk", "nether_wart_block"])]
for i, (title, tool, mats) in enumerate(BAYS):
    a, c = 2302 + i * 8, 2124
    m.fill(a - 1, 151, c + 2, a + 5, 155, c + 2, "deepslate_bricks")
    m.fill(a - 1, 150, c - 3, a + 5, 150, c + 1, "gray_concrete")
    for j, mat in enumerate(mats):
        # The wall: every height from the feet to over the head.
        reset.append(f"fill {a + j} 151 {c} {a + j} 154 {c + 1} {mat}")
    # In the floor before it, and overhead at one end.
    reset.append(f"fill {a} 150 {c - 2} {a + 1} 150 {c - 1} {mats[0]}")
    reset.append(f"fill {a + 3} 154 {c - 2} {a + 4} 154 {c - 1} {mats[0]}")
    m.block(a - 1, 151, c - 3, "polished_deepslate_wall")
    m.block(a + 5, 151, c - 3, "polished_deepslate_wall")
    m.block(a - 1, 152, c - 3, "lantern")
    m.block(a + 5, 152, c - 3, "lantern")
    m.label(a + 2.5, 156.6, c + 1.5, title, "yellow", .9)
    m.sign(a + 2, c - 4, [title, "стена: от ног", "до головы; в полу", "справа, навес слева"])
# A lone block at each height with room all round, for the camera.
for j, (dy, title) in enumerate([(-1, "В ПОЛУ"), (0, "У НОГ"), (1, "ГРУДЬ"), (2, "НАД ГОЛОВОЙ")]):
    a = 2304 + j * 8
    if dy > 0:
        m.fill(a, 151, 2140, a, 150 + dy, 2140, "polished_deepslate_wall")
    reset.append(f"setblock {a} {151 + dy} 2140 stone")
    m.block(a, 150, 2136, "yellow_concrete")
    m.sign(a, 2137, [title, "один блок:", "камера с любой", "стороны"])
m.label(2317.5, 155.6, 2140.5, "ОДИН БЛОК: камера с четырёх сторон", "white", .8)
m.barrel(2331, 2136, ["iron_pickaxe", "iron_axe", "iron_shovel", "iron_hoe", "diamond_pickaxe", "diamond_axe", "wooden_pickaxe", "wooden_axe", "shears", "torch 64"], "Инструменты")
m.sign(2316, 2115, ["ЗОНА 13", "MINING GALLERY", "добыча", ""])
m.button(2302, 2114, "К ВХОДУ", HUB)
m.button(2305, 2114, "ВОССТАНОВИТЬ", "function emf_atlas:mining_gallery_reset")
m.button(2308, 2114, "SURVIVAL", "gamemode survival @p")
m.button(2311, 2114, "CREATIVE", "gamemode creative @p")
m.button(2332, 2114, "← 12 / CARE", "tp @p 2269.5 151 2112.5 0 8")
m.sign(2314, 2114, ["Ломай в", "выживании:", "в творческом", "удар один"])
for a in range(2304, 2334, 10):
    m.fill(a - 1, 151, 2158, a + 1, 151, 2160, "waxed_cut_copper")
    m.block(a, 151, 2159, "grass_block")
    m.fill(a, 152, 2159, a, 155, 2159, "spruce_log")
    m.fill(a - 2, 154, 2157, a + 2, 155, 2161, "spruce_leaves[persistent=true]")
    m.fill(a - 1, 156, 2158, a + 1, 157, 2160, "spruce_leaves[persistent=true]")
m.cmd("kill @e[type=item,x=2300,y=148,z=2111,dx=35,dy=14,dz=56]")
m.cmd("function emf_atlas:mining_gallery_reset")
m.cmd('tellraw @a {"text":"EMF ATLAS • зона 13: галерея добычи готова","color":"aqua"}')
m.write("mining_gallery")
(FUN / "mining_gallery_reset.mcfunction").write_text("\n".join(reset) + "\n")

# ---------------------------------------------------------------- the field station
# The cockpit (366..374), lead (435..475) and effort (477..491) pads stand far from the campus at
# y 149 and are cleared by their own functions and by the scenarios that use them (450 150 7 is the
# gesture and mining scenarios' pad). Nothing is put inside them: a path between, names above, a
# way back.
f = Section("atlas_field", floor=149, front="south")
# The pads hang in the void: the path is a bridge with a kerb, and widens into a landing half way.
f.fill(375, 148, 5, 434, 148, 9, "deepslate_tiles")
f.fill(375, 149, 6, 434, 149, 8, "polished_andesite")
f.fill(375, 149, 5, 434, 149, 5, "polished_deepslate")
f.fill(375, 149, 9, 434, 149, 9, "polished_deepslate")
f.fill(375, 150, 5, 434, 150, 5, "polished_deepslate_wall")
f.fill(375, 150, 9, 434, 150, 9, "polished_deepslate_wall")
f.fill(397, 148, 1, 411, 148, 13, "deepslate_tiles")
f.fill(397, 149, 1, 411, 149, 13, "polished_deepslate")
f.fill(398, 149, 2, 410, 149, 12, "smooth_quartz")
f.fill(397, 150, 1, 411, 150, 13, "air")
f.ring(397, 150, 1, 411, 13, "polished_deepslate_wall")
f.fill(397, 150, 6, 397, 150, 8, "air")
f.fill(411, 150, 6, 411, 150, 8, "air")
for a in range(378, 434, 8):
    f.block(a, 149, 7, "sea_lantern")
for a in (382, 426):
    for c in (5, 9):
        f.fill(a, 150, c, a, 152, c, "polished_deepslate_wall")
        f.block(a, 153, c, "sea_lantern")
for a, c in ((397, 1), (411, 1), (397, 13), (411, 13)):
    f.fill(a, 150, c, a, 152, c, "polished_deepslate_wall")
    f.block(a, 153, c, "sea_lantern")
for a in (399, 409):
    f.block(a, 150, 11, "flowering_azalea_leaves[persistent=true]")
f.label(404.5, 155, 7.5, "E M F  /  F I E L D   S T A T I O N", scale=2.2)
f.label(404.5, 153.4, 7.5, "Отдельные площадки сценариев: кабина • поводок и жесты • усилие", "white", 1)
f.label(370.5, 157, 7.5, "F1 / COCKPIT", scale=1.4)
f.label(455.5, 158, 7.5, "F2 / LEASH • GESTURES • MINING PAD", scale=1.4)
f.label(484.5, 157.5, 8.5, "F3 / EFFORT", scale=1.4)
f.sign(404, 3, ["FIELD STATION", "F1 кабина", "F2 поводок, жесты", "F3 усилие"], rotation=0)
f.button(402, 4, "К ВХОДУ АТЛАСА", HUB)
f.button(404, 4, "F1 / COCKPIT", "tp @p 370.5 150 9.5 180 0")
f.button(406, 4, "F2 / LEASH PAD", "tp @p 450.5 150 7.5 -90 0")
f.button(408, 4, "F3 / EFFORT", "tp @p 484.5 150 11.5 180 0")
f.button(400, 4, "12 / CARE", "tp @p 2269.5 151 2112.5 0 8")
f.sign(404, 11, ["Площадки чистят", "себя сами:", "не строй внутри,", "сценарии сотрут"])
f.cmd('tellraw @a {"text":"EMF ATLAS • полевая станция размечена","color":"aqua"}')
f.write("field")

# ---------------------------------------------------------------- campus decor and the hub's directory
d = Section("atlas_decor")
ZONES = [("cyan", 2060, 2060), ("lime", 2120, 2060), ("orange", 2180, 2060), ("yellow", 2060, 2116), ("light_blue", 2120, 2116),
         ("red", 2180, 2116), ("purple", 2060, 2172), ("green", 2120, 2172), ("pink", 2180, 2172)]
for i, (col, x, z) in enumerate(ZONES):
    # Each pavilion's colour on its front pylons and before its button at the hub.
    for a in (x, x + 47):
        d.block(a, 155, z + 44, f"{col}_wall_banner[facing=south]")
        d.block(a, 153, z + 44, "lantern[hanging=false]")
        d.block(a, 152, z + 44, "polished_deepslate_wall")
        d.block(a, 151, z + 44, "polished_deepslate_wall")
    d.fill(2113 + i * 7, 150, 2239, 2115 + i * 7, 150, 2239, f"{col}_concrete")
# The annexes' buttons in one row after the nine, each marked the same way.
d.fill(2175, 150, 2238, 2207, 150, 2243, "polished_deepslate")
d.fill(2176, 150, 2239, 2206, 150, 2242, "smooth_quartz")
MORE = [(2177, "white", None, None), (2184, "cyan", "11 / WALL CONTACT", "tp @p 2317 151 2107 180 0"),
        (2191, "lime", "12 / CARE & GESTURES", "tp @p 2269.5 151 2112.5 0 8"),
        (2198, "light_gray", "13 / MINING GALLERY", "tp @p 2317.5 151 2112.5 0 8"),
        (2205, "black", "FIELD STATION", "tp @p 404.5 150 7.5 -90 0")]
for a, col, title, command in MORE:
    d.fill(a - 1, 150, 2239, a + 1, 150, 2239, f"{col}_concrete")
    if title:
        d.button(a, 2241, title, command)
d.label(2191, 154.2, 2241.5, "ПРИСТРОЙКИ  10 – 13  •  ПОЛЕВАЯ СТАНЦИЯ", "gold", 1.1)
d.label(2144.5, 153.6, 2241.5, "ЗОНЫ  01 – 09", "gold", 1.1)
# From the old mining stand in zone 08 to the gallery that replaces it.
d.button(2156, 2184, "13 / MINING GALLERY", "tp @p 2317.5 151 2112.5 0 8", front="south")
# A way back from zone 11, which had none.
d.button(2302, 2103, "К ВХОДУ", HUB, front="south")
# Trees outside the west and north rim, and benches by the fountain.
# The campus hangs in the void, so the trees get a lawn to stand on: a green belt along both rims.
d.fill(2043, 148, 2043, 2051, 148, 2246, "deepslate_tiles")
d.fill(2052, 148, 2043, 2236, 148, 2051, "deepslate_tiles")
d.fill(2043, 149, 2043, 2051, 149, 2246, "grass_block")
d.fill(2052, 149, 2043, 2236, 149, 2051, "grass_block")
d.fill(2043, 149, 2043, 2043, 149, 2246, "polished_deepslate")
d.fill(2043, 149, 2043, 2236, 149, 2043, "polished_deepslate")
FLOWERS = ["poppy", "dandelion", "azure_bluet", "oxeye_daisy", "cornflower", "allium"]
for i, c in enumerate(range(2052, 2244, 4)):
    d.block(2049 if i % 2 else 2045, 150, c, FLOWERS[i % 6])
    if c < 2236:
        d.block(c, 150, 2049 if i % 2 else 2045, FLOWERS[(i + 3) % 6])
for a, z in [(2047, c) for c in range(2058, 2244, 12)] + [(c, 2047) for c in range(2058, 2236, 12)]:
    if True:
        d.fill(a - 1, 149, z - 1, a + 1, 149, z + 1, "moss_block")
        d.fill(a, 150, z, a, 154, z, "dark_oak_log")
        d.fill(a - 2, 154, z - 2, a + 2, 155, z + 2, "dark_oak_leaves[persistent=true]")
        d.fill(a - 1, 156, z - 1, a + 1, 156, z + 1, "azalea_leaves[persistent=true]")
for a in (2136, 2152):
    d.fill(a - 1, 151, 2233, a + 1, 151, 2233, "dark_oak_stairs[facing=north]")
    d.block(a - 2, 151, 2233, "flowering_azalea_leaves[persistent=true]")
    d.block(a + 2, 151, 2233, "flowering_azalea_leaves[persistent=true]")
d.cmd('tellraw @a {"text":"EMF ATLAS • оформление и указатели обновлены","color":"aqua"}')
d.write("decor")
