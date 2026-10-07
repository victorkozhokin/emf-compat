"""Rowing with the paddles forced half and a quarter of a stroke apart (hold one side, then add forward). Read [BoatTrace] in the log."""
import json
s = [{"closeScreen": True}, {"releaseAll": True}, {"cmd": "gamemode creative"},
     {"config": {"debug.decisions": True, "lookat.enabled": False, "footgrounding.trace": True, "wallhand.trace": False, "transport.trace": False}},
     {"camera": "back"}, {"cmd": "fill 432 149 1 470 149 14 water"}, {"cmd": "kill @e[type=boat,distance=..80]"},
     {"cmd": "summon boat 440.5 150 7.5 {Type:\"oak\",Rotation:[-90f,0f]}"}, {"wait": 10}, {"cmd": "ride @s mount @e[type=boat,limit=1,sort=nearest]"}, {"wait": 40},
     {"log": "desync sync"}, {"hold": "forward"}, {"wait": 50}, {"release": "forward"}, {"wait": 20},
     {"log": "desync half"}, {"hold": "right"}, {"wait": 8}, {"hold": "forward"}, {"wait": 2}, {"release": "right"}, {"wait": 60}, {"release": "forward"}, {"wait": 20},
     {"log": "desync quarter"}, {"hold": "left"}, {"wait": 4}, {"hold": "forward"}, {"wait": 2}, {"release": "left"}, {"wait": 60}, {"release": "forward"},
     {"log": "desync end"}, {"config": {"footgrounding.trace": False}}, {"wait": 5}]
print(json.dumps(s))
