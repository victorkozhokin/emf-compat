"""Build the EMF Compat Atlas campus as an editable Minecraft 1.21.1 data pack."""
import json
import os
from pathlib import Path

# The world the pack is written into: the one the campus was first built in, or ATLAS_WORLD -
# the finished map itself (run/mctest/Test/saves/EMF ATLAS - Animation Campus) to extend it in place.
OUT = Path(os.environ.get('ATLAS_WORLD', 'run/mctest/Test/saves/ParCool Test')) / 'datapacks/emf_atlas'
FUN = OUT / 'data/emf_atlas/function'
FUN.mkdir(parents=True, exist_ok=True)
(OUT / 'pack.mcmeta').write_text(json.dumps({'pack': {'pack_format': 48, 'description': 'EMF ATLAS • interactive animation campus'}}, ensure_ascii=False))
commands = []
def cmd(s): commands.append(s)
def fill(x,y,z,X,Y,Z,b): cmd(f'fill {x} {y} {z} {X} {Y} {Z} {b}')
def block(x,y,z,b): cmd(f'setblock {x} {y} {z} {b}')
def text(x,y,z,t,color='white',scale=1):
    content=json.dumps({'text':t,'color':color},ensure_ascii=False)
    cmd(f"summon text_display {x} {y} {z} {{Tags:[\"atlas\"],text:{json.dumps(content,ensure_ascii=False)},billboard:\"center\",background:1073741824,line_width:280,view_range:1.5f,transformation:{{scale:[{scale}f,{scale}f,{scale}f]}}}}")
def sign(x,y,z,lines):
    msgs=','.join("'"+json.dumps({'text':v,'color':'black'},ensure_ascii=False)+"'" for v in (lines+['']*4)[:4])
    block(x,y,z,'oak_sign[rotation=0]{is_waxed:1b,front_text:{messages:['+msgs+']}}')
def chest(x,z,items,label,y=151):
    block(x,y,z,'barrel[facing=up]')
    text(x+.5,y+1.35,z+.5,label,'gold',.65)
    for i,item in enumerate(items[:27]): cmd(f'item replace block {x} {y} {z} container.{i} with {item}')
def button(x,z,label,command,y=151):
    nbt=json.dumps(command,ensure_ascii=False)
    block(x,y,z,f'command_block{{Command:{nbt},TrackOutput:0b}}')
    block(x,y+1,z,'polished_blackstone_button[face=floor]')
    text(x+.5,y+1.6,z+.5,label,'aqua',.6)
def animal(kind,x,z,extra=''):
    cmd(f'summon {kind} {x} 151 {z} {{Tags:["atlas"],PersistenceRequired:1b,Invulnerable:1b,{extra}FallDistance:0f}}')

# All map content is in region 4,4, independent of the source world's old test rigs.
cmd('kill @e[tag=atlas]')
for x in range(2052,2237,24):
    fill(x,147,2052,min(x+23,2236),149,2246,'deepslate_tiles')
    fill(x,150,2052,min(x+23,2236),150,2246,'smooth_stone')
# outer rim, inset copper stripe and water rills
for x in [2052,2236]:
    fill(x,150,2052,x,150,2246,'waxed_cut_copper')
    fill(x,151,2052,x,151,2246,'polished_deepslate_wall')
for z in [2052,2246]:
    fill(2052,150,z,2236,150,z,'waxed_cut_copper')
    fill(2052,151,z,2236,151,z,'polished_deepslate_wall')
for x in [2113,2173]:
    fill(x,150,2054,x+2,150,2222,'cyan_terracotta')
    for z in range(2056,2223,8): block(x+1,150,z,'sea_lantern')
for z in [2108,2164,2220]:
    fill(2054,150,z,2234,150,z+2,'cyan_terracotta')
    for x in range(2056,2235,8): block(x,150,z+1,'sea_lantern')
# Lampposts and planters along public paths.
for x in [2055,2115,2175,2233]:
    for z in [2056,2112,2168,2224]:
        fill(x,151,z,x,154,z,'polished_deepslate_wall')
        block(x,155,z,'sea_lantern')
        block(x,156,z,'waxed_cut_copper_slab')
        for dx in [-2,2]:
            block(x+dx,151,z,'flowering_azalea_leaves[persistent=true]')

zones=[
 ('01','FOOT IK / MOTION','cyan',2060,2060,'Ступни • разгон • торможение'),
 ('02','PARCOOL','lime',2120,2060,'Прыжки • вис • перекат • скольжение'),
 ('03','INTERACTIONS','orange',2180,2060,'Руки • кнопки • двери • предметы'),
 ('04','CREATE / AERO','yellow',2060,2116,'Crank • Valve • Steering • Throttle'),
 ('05','RIDING / FLIGHT','light_blue',2120,2116,'Верхом • вода • полёт • сидение'),
 ('06','COMBAT / MAGIC','red',2180,2116,'Удары • прицел • заклинания'),
 ('07','ITEMS / STAGE','purple',2060,2172,'Музыка • камера • Carry On'),
 ('08','CONTACT / MINING','green',2120,2172,'Стены • растения • добыча • взгляд'),
 ('09','CORE / CROSS-MOD','pink',2180,2172,'Слои • броня • эмоции • переходы'),
]
for num,title,col,x,z,desc in zones:
    fill(x,150,z,x+47,150,z+43,'quartz_block')
    fill(x+1,150,z+1,x+46,150,z+42,'smooth_quartz')
    fill(x+1,150,z+39,x+46,150,z+41,col+'_concrete')
    for dx in [0,47]:
        fill(x+dx,151,z,x+dx,156,z,'deepslate_tiles')
        fill(x+dx,151,z+43,x+dx,156,z+43,'deepslate_tiles')
    fill(x,157,z+43,x+47,157,z+43,'deepslate_tiles')
    fill(x,158,z+43,x+47,158,z+43,'waxed_cut_copper_slab')
    text(x+23.5,155.5,z+43.5,num+'  /  '+title,'white',1.35)
    text(x+23.5,153.5,z+43.5,desc,'aqua',.75)
    button(x+44,z+40,'К входу','tp @p 2144.5 151 2237.5 180 0')
    sign(x+2,151,z+40,['F5: вид сбоку','Ближе к стенду','Проверяй ладонь','и обе ступни'])

# 01: stepped surfaces, ascent/descent, boundaries, run/stop lane.
x,z=2060,2060
for lane,(mat,h) in enumerate([('stone_slab',0),('snow[layers=2]',0),('snow[layers=5]',0),('stone',0)]):
    a=x+5+lane*10
    fill(a,151,z+6,a+3,151,z+11,mat)
    fill(a,151,z+17,a+3,151,z+21,'stone_stairs[facing=south]')
    fill(a,151,z+22,a+3,152,z+25,'stone')
    sign(a,151,z+30,['01 / Foot IK',['Полублок','Снег 2/8','Снег 5/8','Полный блок'][lane],'Шаг вверх/вниз','Стой у края'])
fill(x+3,150,z+34,x+43,150,z+36,'gray_concrete')
for a in range(x+5,x+44,4): block(a,150,z+35,'white_concrete')
text(x+23,152,z+35,'SPRINT → STOP → TURN 180°','aqua',.75)
fill(x+40,148,z+3,x+43,150,z+5,'air')
sign(x+39,151,z+7,['Обрыв','Присяд у края','Без ложной','опоры в воздухе'])
# 02 parkour lanes.
x,z=2120,2060
for a in range(x+4,x+44,7): fill(a,151,z+5,a+2,151+(a-x)//14,z+7,'polished_andesite')
fill(x+4,151,z+14,x+37,153,z+14,'smooth_sandstone')
fill(x+4,151,z+18,x+37,153,z+18,'smooth_sandstone')
fill(x+7,153,z+24,x+24,153,z+27,'waxed_cut_copper')
fill(x+30,151,z+24,x+30,156,z+29,'stone_bricks')
fill(x+31,156,z+24,x+36,156,z+29,'stone_brick_slab')
fill(x+38,151,z+25,x+38,155,z+25,'scaffolding')
for a in range(x+6,x+43,9): sign(a,151,z+33,['ParCool','Клавиши мода','см. Настройки','свой биндинг'])
text(x+20,155,z+16,'WALL RUN / NARROW PASS','green',.8)
text(x+16,154.8,z+26,'CRAWL / SLIDE','green',.8)
text(x+34,158,z+26,'HANG / CLIMB','green',.8)
# 03: wall controls, floor and ceiling, interaction grid.
x,z=2180,2060
fill(x+3,151,z+4,x+43,154,z+4,'polished_andesite')
for i,mat in enumerate(['stone_button','oak_button','lever']):
    for j,y in enumerate([151,152,153]): block(x+5+i*12+j*3,y,z+5,mat+'[face=wall,facing=south]')
block(x+5,151,z+10,'stone_button[face=floor]')
fill(x+12,154,z+9,x+14,154,z+11,'smooth_quartz')
block(x+13,153,z+10,'stone_button[face=ceiling]')
blocks=['chest[facing=south]','barrel[facing=south]','lectern[facing=south]','chiseled_bookshelf[facing=south]','crafting_table','stonecutter[facing=south]','repeater[facing=south]','comparator[facing=south]','daylight_detector','note_block','jukebox','cake','composter','flower_pot','candle','respawn_anchor','vault']
for i,b in enumerate(blocks):
    a=x+4+(i%6)*7;c=z+16+(i//6)*7
    block(a,151,c,b)
    sign(a,151,c+2,[b.split('[')[0][:18],'ПКМ / Shift','Встань вплотную','Смотри сбоку'])
for i,typ in enumerate(['oak','iron']):
    a=x+31+i*7
    block(a,151,z+10,typ+'_door[facing=south,half=lower]')
    block(a,152,z+10,typ+'_door[facing=south,half=upper]')
    block(a-1,150,z+10,'gold_block');block(a-1,151,z+10,'lever[face=floor]')
chest(x+4,z+37,['book 16','writable_book','flower_pot','poppy 16','bone_meal 64','wheat_seeds 64','honeycomb','flint_and_steel','music_disc_cat','glowstone 16','trial_key','ominous_trial_key'],'Набор взаимодействий')
# 03, more: the blocks a hand uses by what it holds (cauldrons, hives, candles, TNT) and the tables.
# Also written as its own function, interactions.mcfunction, to add them to a map already built;
# restock.mcfunction puts back what a use takes away (honey, water, the candle, the TNT).
more=len(commands)
used=[('cauldron','Ведро / бутыль','с водой'),
 ('water_cauldron[level=3]','Ведро, бутылка','крашеная броня'),
 ('lava_cauldron','Пустое ведро',''),
 ('powder_snow_cauldron[level=3]','Пустое ведро',''),
 ('beehive[facing=south,honey_level=5]','Бутылка','или ножницы'),
 ('bee_nest[facing=south,honey_level=5]','Бутылка','или ножницы'),
 ('candle_cake','Огниво / пустая','рука / съесть'),
 ('candle[candles=4]','Огниво / пустая','рука'),
 ('tnt','Огниво','или огн. заряд'),
 ('crafter[orientation=south_up]','ПКМ: слоты','вкл/выкл, предмет'),
 ('enchanting_table','Смотри на книгу','обе руки'),
 ('cartography_table','ПКМ: рука','водит по карте')]
restock=[]
for i,(b,how,more_how) in enumerate(used):
    # The first takes the grid's last free place; the rest a row of their own, 3 apart.
    a,c=(x+39,z+30) if i==0 else (x+4+i*3,z+37)
    block(a,151,c,b)
    restock.append(f'setblock {a} 151 {c} {b}')
    sign(a,151,c+2,[b.split('[')[0][:18],how,more_how,'Встань вплотную'])
    # Lit TNT is taken away at once: the stand shows the hand at the fuse, and the campus stays whole.
    if b=='tnt': block(a,149,c,'repeating_command_block{Command:"kill @e[type=tnt,distance=..4]",auto:1b,TrackOutput:0b}')
(FUN/'restock.mcfunction').write_text('\n'.join(restock)+'\n')
button(x+43,z+35,'Восстановить стенды','function emf_atlas:restock')
chest(x+43,z+30,['bucket','water_bucket','lava_bucket','powder_snow_bucket','glass_bottle 16','potion[potion_contents={potion:"minecraft:water"}]','shears','flint_and_steel','fire_charge 16','leather_chestplate[dyed_color={rgb:11546150}]','honeycomb 16','oak_planks 64','cobblestone 64','lapis_lazuli 64','book 16','filled_map','paper 16','glass_pane 16'],'Вёдра / огонь / столы')
(FUN/'interactions.mcfunction').write_text('\n'.join(commands[more:])+'\n')
# 04: crank wall in three heights; valves, wheel, throttle and chain conveyor.
x,z=2060,2116
fill(x+3,151,z+4,x+43,154,z+4,'andesite')
for i,y in enumerate([151,152,153]):
    a=x+6+i*11
    block(a,y,z+5,'create:hand_crank[facing=south]')
    sign(a,151,z+8,['CRANK / '+str(y-150),'Стоя + присяд','Удерживай ПКМ','Полный оборот'])
for i,col in enumerate(['copper','red','blue']): block(x+5+i*7,152,z+15,'create:'+col+'_valve_handle[facing=south]')
fill(x+3,151,z+14,x+22,153,z+14,'andesite')
for a in [x+29,x+38]:
    block(a,151,z+15,'simulated:steering_wheel[facing=south,on_floor=false]')
    block(a,150,z+15,'andesite')
block(x+39,151,z+19,'create:white_seat')
sign(x+29,151,z+19,['STEERING','ПКМ по ободу','Двигай мышью','Нижняя рука'])
block(x+26,151,z+26,'simulated:throttle_lever')
for a in [x+5,x+17]: fill(a,151,z+27,a,155,z+27,'create:metal_girder')
block(x+5,156,z+27,'create:chain_conveyor{Connections:[[12,0,0]]}')
block(x+17,156,z+27,'create:chain_conveyor{Connections:[[-12,0,0]]}')
# 04, more: the blocks an item is put on or into by hand. Also its own function, create_items.mcfunction,
# to add them to a map already built; the restock button's function puts the stands back.
more=len(commands)
rests=[('create:depot','Предмет: ПКМ','пустой: забрать'),
 ('create:item_drain','Ведро / бутыль','пустой: забрать'),
 ('create:basin{InputItems:{Size:9,Items:[{Slot:0,id:"minecraft:iron_ingot",count:8}]}}','Пустой рукой','забрать всё'),
 ('create:blaze_burner[blaze=smouldering,facing=south]','Уголь','или Blaze Cake')]
for i,(b,how,more_how) in enumerate(rests):
    a=x+5+i*4
    block(a,151,z+21,b)
    restock.append(f'setblock {a} 151 {z+21} air')
    restock.append(f'setblock {a} 151 {z+21} {b}')
    sign(a,151,z+23,[b.split(':')[1].split('[')[0].split('{')[0][:18],how,more_how,'Встань вплотную'])
# The value boxes: a speed set, a filter or a frequency item put in.
panels=[('create:rotation_speed_controller[axis=x]','Панель скорости','удерживай ПКМ'),
 ('create:creative_motor[facing=up]','Панель скорости','удерживай ПКМ'),
 ('create:brass_funnel[facing=up]','Фильтр: ПКМ','предметом'),
 ('create:content_observer[facing=south]','Фильтр: ПКМ','предметом'),
 ('create:redstone_link[facing=up]','Два слота','частоты')]
for i,(b,how,more_how) in enumerate(panels):
    a=x+30+i*3
    block(a,151,z+24,b)
    restock.append(f'setblock {a} 151 {z+24} air')
    restock.append(f'setblock {a} 151 {z+24} {b}')
    sign(a,151,z+26,[b.split(':')[1].split('[')[0][:18],how,more_how,'Смотри на панель'])
# Used sitting: a seat right behind a steering wheel. Controls: the button of contraption controls
# in the world; a train's controls are held only on an assembled train - the barrel has what one takes.
block(x+29,151,z+16,'create:white_seat')
sign(x+31,151,z+16,['СИДЯ','ПКМ по подушке','смотри на руль','удерживай ПКМ'])
for i,(b,how,more_how) in enumerate([('create:contraption_controls[facing=south]','ПКМ: кнопка','вкл / выкл'),('create:controls[facing=south]','Только на поезде','собери состав')]):
    a=x+30+i*3
    block(a,151,z+29,b)
    sign(a,151,z+31,[b.split(':')[1].split('[')[0][:18],how,more_how,'Стоя и сидя'])
chest(x+37,z+29,['create:track 64','create:track_station','create:controls 2','create:railway_casing 16','create:white_seat 4','create:super_glue','create:wrench','create:train_door 2','create:contraption_controls 2'],'Поезд: пути / станция')
(FUN/'restock.mcfunction').write_text('\n'.join(restock)+'\n')
button(x+25,z+23,'Восстановить стенды','function emf_atlas:restock')
chest(x+23,z+19,['iron_ingot 16','create:andesite_alloy 16','oak_planks 16','coal 16','create:blaze_cake 4','water_bucket','bucket','honey_bottle 4','potion[potion_contents={potion:"minecraft:water"}]','create:wrench','create:filter 4','redstone 16'],'Предметы / топливо')
(FUN/'create_items.mcfunction').write_text('\n'.join(commands[more:])+'\n')
chest(x+4,z+36,['create:wrench','create:extendo_grip','create:potato_cannon','potato 64','create:clipboard','create:linked_controller','create:cardboard_helmet','create:cardboard_chestplate','create:cardboard_leggings','create:cardboard_boots','create:super_glue','create:controls','create:track','create:track_station'],'Create / сборка транспорта')
text(x+13,158,z+27,'CHAIN CONVEYOR','yellow',.8)
# 05 water, mounts, seats and elevated flight deck.
x,z=2120,2116
fill(x+3,148,z+3,x+24,150,z+19,'prismarine_bricks')
fill(x+4,149,z+4,x+23,150,z+18,'water')
for a in [x+7,x+18]: cmd(f'summon boat {a} 151 {z+10} {{Type:"oak",Tags:["atlas"]}}')
fill(x+29,151,z+3,x+44,151,z+17,'oak_fence')
fill(x+30,151,z+4,x+43,151,z+16,'air')
animal('horse',x+34,z+8,'Tame:1b,SaddleItem:{id:"minecraft:saddle",count:1},')
animal('donkey',x+39,z+8,'Tame:1b,SaddleItem:{id:"minecraft:saddle",count:1},')
block(x+35,151,z+17,'oak_fence_gate')
for i in range(8): fill(x+30,151+i,z+24+i,x+33,151+i,z+24+i,'stone_brick_stairs[facing=south]')
fill(x+27,159,z+32,x+43,159,z+38,'waxed_cut_copper')
fill(x+27,160,z+38,x+43,160,z+38,'glass_pane')
button(x+37,z+35,'Башня полёта','tp @p 2155.5 175 2150.5 180 0',160)
fill(x+33,173,z+32,x+38,174,z+36,'quartz_block')
text(x+35,178,z+34,'GLIDE / ELYTRA','aqua',1)
chest(x+4,z+35,['saddle','lead 16','elytra','firework_rocket 64','paraglider:paraglider','vc_gliders:paraglider_wood','fishing_rod','carrot_on_a_stick','trident','create:copper_diving_helmet','create:copper_backtank','create:copper_diving_boots'],'Вода / верхом / полёт')
for i in range(4): block(x+6+i*4,151,z+25,'oak_stairs[facing=north]')
text(x+12,153,z+25,'TAKE A SEAT / ПКМ','aqua',.8)
for a in range(x+3,x+23): block(a,151,z+30,'powered_rail[shape=east_west]');block(a,150,z+30,'redstone_block')
cmd(f'summon minecart {x+6}.5 151.1 {z+30}.5 {{Tags:["atlas"]}}')
# 06 combat ranges and mineable test blocks.
x,z=2180,2116
for lane in range(4):
    a=x+6+lane*10
    fill(a-2,150,z+6,a+2,150,z+28,'light_gray_concrete')
    fill(a-2,151,z+4,a+2,154,z+4,'deepslate_bricks')
    block(a,153,z+5,'target')
    for d in [10,18,26]: block(a,150,z+d,'red_concrete')
    cmd(f'summon armor_stand {a}.5 151 {z+10}.5 {{Tags:["atlas"],ShowArms:1b,NoGravity:1b,Invulnerable:1b}}')
    sign(a,151,z+30,[['MELEE','BOW / SHIELD','TACZ','IRON SPELLS'][lane],'Стоя + присяд','Атака → ходьба','Возврат позы'])
chest(x+4,z+35,['iron_sword','diamond_axe','shield','bow','crossbow','arrow 64','trident','irons_spellbooks:diamond_spell_book','irons_spellbooks:blaze_spell_book','tacz:modern_kinetic_gun','tacz:ammo_box'],'Оружие / магия')
block(x+20,151,z+35,'tacz:gun_smith_table')
text(x+32,153,z+36,'Better Combat: отдельный запуск\nв профиле сейчас H&S','gold',.7)
# 07 covered stage, photography wall and Carry On stock.
x,z=2060,2172
fill(x+3,151,z+3,x+43,151,z+18,'dark_oak_planks')
fill(x+3,152,z+3,x+43,157,z+3,'black_concrete')
for a in range(x+5,x+44,6): block(a,157,z+4,'sea_lantern')
fill(x+3,158,z+3,x+43,158,z+8,'waxed_cut_copper_slab')
text(x+23,155,z+4,'EMF  /  SOUND STAGE','light_purple',1.5)
chest(x+5,z+15,['immersive_melodies:lute','immersive_melodies:flute','immersive_melodies:piano','immersive_melodies:trumpet','immersive_melodies:tiny_drum','immersive_melodies:vi elle'.replace(' ',''),'immersive_melodies:triangle','immersive_melodies:bagpipe','immersive_melodies:handpan','immersive_melodies:didgeridoo','immersive_melodies:ender_bass'],'Инструменты',152)
chest(x+35,z+15,['exposure:camera','exposure:color_film','exposure:black_and_white_film','spyglass','filled_map','written_book'],'Камера / NEA',152)
for i,b in enumerate(['chest','barrel','furnace']): block(x+5+i*5,151,z+27,b)
animal('pig',x+22,z+27,'NoAI:1b,')
sign(x+8,151,z+31,['CARRY ON','Свободные руки','Shift + ПКМ','Нести / поставить'])
chest(x+31,z+29,['supplementaries:flute','supplementaries:slingshot','supplementaries:rope 32','supplementaries:soap','supplementaries:bubble_blower'],'Supplementaries')
# 08 wall/plant/mining/look at.
x,z=2120,2172
fill(x+3,151,z+3,x+3,154,z+21,'stone_bricks')
fill(x+6,151,z+3,x+6,154,z+21,'stone_bricks')
fill(x+13,150,z+4,x+16,150,z+23,'grass_block')
for a in range(z+4,z+24,3):
    block(x+13,151,a,'fern');block(x+16,151,a,'poppy')
text(x+10,155,z+13,'WALL HAND / PLANT REACH','green',.75)
for i,b in enumerate(['stone','oak_log','dirt','hay_block']): fill(x+23+i*5,151,z+5,x+25+i*5,153,z+7,b)
reset=[f'fill {x+23+i*5} 151 {z+5} {x+25+i*5} 153 {z+7} {b}' for i,b in enumerate(['stone','oak_log','dirt','hay_block'])]
(FUN/'mining.mcfunction').write_text('\n'.join(reset)+'\n')
button(x+32,z+12,'Восстановить блоки','function emf_atlas:mining')
chest(x+24,z+16,['iron_pickaxe','iron_axe','iron_shovel','iron_hoe','torch 64'],'Mining IK')
animal('villager',x+28,z+28,'NoAI:1b,Silent:1b,')
animal('cat',x+36,z+28,'NoAI:1b,Silent:1b,')
sign(x+29,151,z+32,['LOOK AT','Стой 3 секунды','Потом начни','любое действие'])
# 09 cross-mod transition laboratory.
x,z=2180,2172
fill(x+4,150,z+4,x+42,150,z+29,'gray_concrete')
for a in range(x+5,x+43,4):
    fill(a,150,z+5,a,150,z+28,'white_concrete')
for a in range(x+8,x+40,10): block(a,151,z+17,'stone_slab')
for a in [x+5,x+40]: block(a,151,z+8,'chest')
chest(x+5,z+34,['diamond_helmet','diamond_chestplate','diamond_leggings','diamond_boots','carved_pumpkin','shield','bow','arrow 64','cooked_beef 64','potion','spyglass','writable_book','torch 64'],'Броня / руки / NEA')
text(x+23,154,z+7,'WALK → CROUCH → USE → ATTACK → IDLE','light_purple',.8)
sign(x+27,151,z+34,['Quark: эмоции','WATUT: меню','Второй игрок','проверяет позы'])
sign(x+35,151,z+34,['Проверить','правую / левую','руку, броню','F5 и F1'])
# Entrance hub: compass, fountain, teleports and settings.
fill(2118,150,2226,2170,150,2243,'polished_deepslate')
fill(2121,150,2229,2167,150,2240,'smooth_quartz')
fill(2139,151,2228,2149,151,2231,'waxed_cut_copper')
fill(2140,151,2229,2148,151,2230,'water')
text(2144.5,160,2234,'E M F   /   A T L A S','aqua',3)
text(2144.5,157,2234,'ANIMATION RESEARCH CAMPUS','white',1.2)
text(2144.5,155,2234,'9 зон · F5 для наблюдения · кнопки — навигация','gold',.8)
for i,(num,title,col,x,z,desc) in enumerate(zones):
    button(2114+i*7,2241,num+' / '+title,f'tp @p {x+23}.5 151 {z+37}.5 180 8')
button(2102,2234,'SURVIVAL','gamemode survival @p')
button(2107,2234,'CREATIVE','gamemode creative @p')
button(2180,2234,'ДЕНЬ','time set noon')
button(2185,2234,'ВЕЧЕР','time set 12500')
button(2190,2234,'ЛЕЧЕНИЕ','effect give @p instant_health 1 10 true')
button(2195,2234,'ПИТАНИЕ','effect give @p saturation 1 10 true')
sign(2122,151,2235,['EMF ATLAS','FA+Player стиль','Не закрывай вид','меню при съёмке'])
sign(2165,151,2235,['Дальность рук','Подходи близко','Касание ладони','важнее луча'])
# guide on lectern; pages use vanilla text components.
pages=['EMF ATLAS\\n\\nПолигон анимаций. 9 зон, телепорты у входа. Для просмотра: F5; для чистого кадра: F1. Меню закрывает позу — наблюдайте вторым игроком или тест-драйвером.',
'01 Foot IK: подъём/спуск, снег, край. Бегите, остановитесь и развернитесь.\\n02 ParCool: прыжки, перекат, скольжение, wall run и вис. Биндинги — в настройках ParCool.',
'03 Руки: кнопки трёх высот, пол/потолок, рычаги, двери и блоки. Набор предметов в бочке.\\n04 Create: Crank стоя/из приседа, Valve, руль, throttle и цепь. Руль: ПКМ по ободу, затем мышь.',
'05 Верхом, лодка, вагонетка, сидение, ныряние, парапланы и элитры. На верхней площадке кнопка башни.\\n06 H&S, лук, щит, TACZ, Iron Spells. Оружие и заклинания выбираются в интерфейсах модов.',
'07 Инструменты Immersive Melodies, Exposure, Supplementaries, Carry On.\\n08 Стены, растения, Mining IK, Look At. Кнопка восстанавливает добываемые блоки.\\n09 Броня, NEA, Quark, WATUT и сочетания.',
'Сравнивайте: стоя/присяд; основная/левая рука; броня/без брони; ходьба/использование; вход/выход из позы.\\nBetter Combat выключен в Test: проверять отдельным запуском. WATUT требует второго наблюдателя.',
'Карта содержит стационарные стенды Aeronautics. Движущийся аппарат нужно собрать отдельно на свободной площадке. Логика анимаций находится в установленных модах, а не в мире.']
book='{id:"minecraft:written_book",count:1,components:{"minecraft:written_book_content":{title:"EMF ATLAS / GUIDE",author:"EMF Compat",pages:['+','.join(json.dumps(json.dumps({'text':p.replace('\\n','\n')},ensure_ascii=False),ensure_ascii=False) for p in pages)+']}}}'
block(2144,151,2235,'lectern[facing=south,has_book=true]{Book:'+book+'}')
# Add an actual readable book to interaction lectern.
block(2198,151,2076,'lectern[facing=south,has_book=true]{Book:'+book+'}')
for rule,value in [('doDaylightCycle','false'),('doWeatherCycle','false'),('doMobSpawning','false'),('mobGriefing','false'),('doFireTick','false'),('keepInventory','true'),('fallDamage','false'),('spawnRadius','0'),('sendCommandFeedback','false'),('commandBlockOutput','false')]:cmd(f'gamerule {rule} {value}')
cmd('time set 6000');cmd('weather clear');cmd('difficulty normal')
cmd('setworldspawn 2144 151 2237 180')
cmd('spawnpoint @a 2144 151 2237 180')
cmd('tp @a 2144.5 151 2237.5 180 0')
cmd('gamemode creative @a')
cmd('tellraw @a {"text":"EMF ATLAS • Полигон готов. Путеводитель на кафедре, телепорты у входа.","color":"aqua"}')
(FUN/'build.mcfunction').write_text('\n'.join(commands)+'\n')
Path('build/atlas').mkdir(parents=True,exist_ok=True)
Path('build/atlas/manifest.json').write_text(json.dumps({'zones':zones,'commands':len(commands),'bounds':[2052,147,2052,2236,178,2246]},ensure_ascii=False,indent=2))
print(len(commands),'commands →',FUN)
