# Separate lead stand; run manually with /function emf_atlas:lead.
kill @e[tag=emf_atlas_lead]
fill 435 149 -5 475 149 20 smooth_stone
fill 435 150 -5 475 155 20 air
fill 435 149 -5 475 149 -5 polished_deepslate
fill 435 149 20 475 149 20 polished_deepslate
fill 435 149 -5 435 149 20 polished_deepslate
fill 475 149 -5 475 149 20 polished_deepslate
fill 450 149 1 450 149 17 cyan_concrete
fill 456 149 1 456 149 17 yellow_concrete
setblock 453 150 11 oak_fence
summon cow 453.5 150 7.5 {NoAI:1b,PersistenceRequired:1b,Tags:["emf_atlas_lead"]}
summon text_display 450.5 154 7.5 {Tags:["emf_atlas_lead"],billboard:"center",text:'{"text":"ПОВОДОК / LEAD","color":"aqua","bold":true}'}
summon text_display 450.5 153.5 7.5 {Tags:["emf_atlas_lead"],billboard:"center",text:'{"text":"Привяжи корову · отходи · Shift · вторая рука · забор","color":"white"}'}
summon text_display 456.5 151 3.5 {Tags:["emf_atlas_lead"],billboard:"center",text:'{"text":"6 блоков — натяжение","color":"yellow"}'}
give @s lead 8
tp @s 450.5 150 7.5 -90 15
