# Additional Atlas stand; construct manually with /function emf_atlas:effort.
kill @e[tag=emf_atlas_effort]
fill 477 149 3 491 149 13 smooth_stone
fill 477 150 3 491 155 13 air
fill 477 149 3 491 149 3 polished_deepslate
fill 477 149 13 491 149 13 polished_deepslate
fill 477 149 3 477 149 13 polished_deepslate
fill 491 149 3 491 149 13 polished_deepslate
setblock 483 150 7 simulated:throttle_lever[facing=north,face=floor]
setblock 488 150 7 simulated:throttle_lever[facing=west,face=floor]
setblock 479 150 7 stone
setblock 479 151 7 simulated:throttle_lever[facing=north,face=floor]
setblock 479 150 8 oak_fence
setblock 483 149 8 cyan_concrete
setblock 489 149 7 cyan_concrete
summon text_display 483.5 154 7.5 {Tags:["emf_atlas_effort"],billboard:"center",text:'{"text":"THROTTLE / УСИЛИЕ","color":"aqua","bold":true}'}
summon text_display 483.5 153.5 7.5 {Tags:["emf_atlas_effort"],billboard:"center",text:'{"text":"Полный ход туда / обратно · стоя / Shift · свободная вторая рука","color":"white"}'}
summon text_display 488.5 153 7.5 {Tags:["emf_atlas_effort"],billboard:"center",text:'{"text":"Боковой подход / смена направления","color":"yellow"}'}
summon text_display 479.5 154 7.5 {Tags:["emf_atlas_effort"],billboard:"center",text:'{"text":"Забор: без подшага за пределы опоры","color":"yellow"}'}
tp @s 483.5 150 8.115 180 20
