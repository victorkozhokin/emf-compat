# EMF Atlas: cockpit prototype; standalone test platform at 370 150 7.
kill @e[type=create:seat,x=366,y=149,z=3,dx=8,dy=5,dz=8]
fill 366 149 3 374 149 11 smooth_stone
fill 366 150 3 374 154 11 air
setblock 370 150 7 create:red_seat
setblock 370 151 5 create:brass_casing
setblock 370 151 6 simulated:steering_wheel[facing=south,on_floor=false]
setblock 369 150 7 simulated:throttle_lever[facing=north,face=floor]
setblock 371 150 7 simulated:throttle_lever[facing=north,face=floor]
fill 366 149 3 374 149 3 polished_deepslate
fill 366 149 11 374 149 11 polished_deepslate
fill 366 149 3 366 149 11 polished_deepslate
fill 374 149 3 374 149 11 polished_deepslate
kill @e[type=text_display,tag=emf_cockpit_label]
summon text_display 370.5 152.8 5.5 {Tags:["emf_cockpit_label"],billboard:"center",text:'{"text":"STEERING + 2 THROTTLE","color":"aqua"}'}
tp @s 370.5 150 8.2 180 60
tellraw @s {"text":"Сядь на красную подушку. Руль спереди; Throttle справа и слева. Чтобы перейти к другому контролу, отпусти текущий захват Simulated.","color":"aqua"}
