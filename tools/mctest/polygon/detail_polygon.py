"""Finish landscaping and additional functional fixtures after build_polygon.py."""
from build_polygon import FUN, commands, cmd, fill, block, text, sign, chest, button
commands.clear()
# Trees and shaded seating in walkway pockets, kept away from interaction targets.
for x,z in [(2113,2108),(2173,2108),(2113,2164),(2173,2164),(2083,2224),(2204,2224)]:
    fill(x-2,151,z-2,x+2,151,z+2,'waxed_cut_copper')
    fill(x-1,151,z-1,x+1,151,z+1,'grass_block')
    fill(x,152,z,x,156,z,'oak_log')
    fill(x-2,156,z-2,x+2,157,z+2,'oak_leaves[persistent=true]')
    fill(x-1,158,z-1,x+1,158,z+1,'flowering_azalea_leaves[persistent=true]')
    block(x-3,151,z+1,'dark_oak_stairs[facing=east]')
    block(x+3,151,z+1,'dark_oak_stairs[facing=west]')
# Ribbed copper canopies at pavilion entrances, with recessed lighting.
for x in [2060,2120,2180]:
    for z in [2060,2116,2172]:
        for a in range(x+1,x+47,4): fill(a,157,z+39,a,157,z+43,'waxed_cut_copper_slab')
        for a in [x+5,x+41]: block(a,156,z+43,'sea_lantern')
# Arrival gate: two pylons, arch and planted side beds.
for x in [2125,2163]:
    fill(x,151,2244,x+1,160,2245,'deepslate_tiles')
    fill(x,152,2243,x,159,2243,'waxed_cut_copper')
fill(2125,161,2244,2164,162,2245,'deepslate_tiles')
fill(2125,163,2244,2164,163,2245,'waxed_cut_copper_slab')
for x in [2089,2199]:
    fill(x,151,2231,x+7,151,2238,'quartz_bricks')
    fill(x+1,151,2232,x+6,151,2237,'moss_block')
    for a in range(x+1,x+7,2):block(a,152,2234,'azalea')
# NEA: ladder, scaffold, bed, swimming pool, map, food/drink, rowing.
fill(2217,151,2187,2217,155,2187,'stone_bricks')
fill(2217,151,2188,2217,155,2188,'ladder[facing=south]')
fill(2220,151,2187,2220,155,2187,'scaffolding')
block(2219,151,2195,'red_bed[part=foot,facing=north]')
block(2219,151,2194,'red_bed[part=head,facing=north]')
text(2220,157,2188,'NEA / CLIMB / SLEEP','light_purple',.65)
# Complete interaction samples, with labels and consumables.
for i,b in enumerate(['oak_trapdoor[half=bottom,facing=south]','oak_fence_gate[facing=south]','bell[attachment=floor,facing=south]','lever[face=floor]']):
    block(2185+i*6,151,2071,b)
# Horse footing: a small uneven stepping route inside the paddock.
fill(2151,151,2121,2153,151,2124,'stone_slab')
fill(2158,151,2127,2161,151,2129,'snow[layers=4]')
# Concrete aircraft assembly apron remains intentionally unassembled.
fill(2064,150,2148,2080,150,2150,'gray_concrete')
text(2072,152,2149,'ASSEMBLY / свободная площадка','yellow',.6)
# Complete all three glider families and usable firearm.
cmd('item replace block 2124 151 2151 container.12 with reliable_gliders:glider')
cmd('item replace block 2184 151 2151 container.9 with tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:ak47",GunCurrentAmmoCount:30}]')
cmd('item replace block 2184 151 2151 container.10 with tacz:ammo[minecraft:custom_data={AmmoId:"tacz:762x39"}] 60')
cmd('item replace block 2184 151 2151 container.7 with irons_spellbooks:diamond_spell_book[irons_spellbooks:spell_container={maxSpells:10,mustEquip:1b,spellWheel:1b,data:[{index:0,id:"irons_spellbooks:firebolt",level:3},{index:1,id:"irons_spellbooks:heal",level:3},{index:2,id:"irons_spellbooks:electrocute",level:3}]}]')
# Book filling / enchanting workbench for spell setups and repair.
block(2221,151,2151,'irons_spellbooks:inscription_table')
block(2225,151,2151,'anvil')
# Additional healthy test target: no AI, damage allowed, renewable through a local button.
button(2212,2149,'ЦЕЛЬ / сброс','function emf_atlas:target')
(FUN/'target.mcfunction').write_text('kill @e[tag=atlas_target]\nsummon husk 2216.5 151 2132.5 {Tags:["atlas","atlas_target"],NoAI:1b,Silent:1b,PersistenceRequired:1b,Health:200f,attributes:[{id:"minecraft:generic.max_health",base:200d}]}\n')
cmd('function emf_atlas:target')
# Egress from flight deck and area test platform.
button(2157,2150,'К входу','tp @p 2144.5 151 2237.5 180 0',175)
# Help / survival refill in each wing so long tests do not require leaving the pavilion.
for x,z in [(2060,2060),(2120,2060),(2180,2060),(2060,2116),(2120,2116),(2180,2116),(2060,2172),(2120,2172),(2180,2172)]:
    block(x+40,151,z+37,'barrel[facing=up]')
    cmd(f'item replace block {x+40} 151 {z+37} container.0 with cooked_beef 64')
    cmd(f'item replace block {x+40} 151 {z+37} container.1 with golden_carrot 64')
    sign(x+40,151,z+35,['Запас еды','Тестируй стоя','и в присяде','Обе руки'])
cmd('tellraw @a {"text":"EMF ATLAS • Детали и дополнительные стенды готовы","color":"green"}')
(FUN/'detail.mcfunction').write_text('\n'.join(commands)+'\n')
print(len(commands),'detail commands')
