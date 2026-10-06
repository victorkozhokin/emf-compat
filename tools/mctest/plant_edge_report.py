"""Contact accuracy while walking, rather than only standing beside wheat."""
import json,math,statistics,subprocess,html
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/plant-edge-review';OUT.mkdir(exist_ok=True)
def end(p):
 x,y,z=p['rot'];return [p['pos'][0]+10*(math.cos(z)*math.sin(y)*math.sin(x)-math.sin(z)*math.cos(x)),p['pos'][1]+10*(math.sin(z)*math.sin(y)*math.sin(x)+math.cos(z)*math.cos(x)),p['pos'][2]+10*math.cos(y)*math.sin(x)]
metrics={};checks={}
for tag in ['before','after']:
 source=ROOT/f'build/plant-edge-{tag}';steps=json.loads((source/'steps.json').read_text());rows=json.loads((source/'results.json').read_text())['results'];groups={};name=None
 checks[tag+'_driver']=not any('error'in r for r in rows)
 for row in rows:
  log=steps[row['step']].get('log','')
  if log.startswith('plant-edge:'):name=log.split(':')[1]
  if name:groups.setdefault(name,[]).append(row)
 for name,rs in groups.items():
  ms=[r['model']for r in rs if 'model'in r];states=[r['state']for r in rs if 'state'in r];hand='LEFT_ARM'if name=='left'else'RIGHT_ARM';arm='left_arm'if name=='left'else'right_arm'
  checks[tag+'_'+name+'_visible']=all('parts'in m and 'interaction'in m for m in ms)
  checks[tag+'_'+name+'_healthy']=all(s['health']==20 and s['food']==20 and s['hurtTime']==0 for s in states)
  errors=[];turns=[]
  for i,m in enumerate(ms[34:68],34):
   if 'parts'not in m:continue
   v=m['interaction'][hand];p=m.get('handContacts',{}).get('PlantReach:'+hand)
   if v.get('owner')!='PlantReach' or v.get('weight',0)<.95 or v.get('releasing')or p is None:continue
   errors.append(math.dist(end(m['parts'][arm]),p[:3])*.9375/16)
   a=ms[i-1]
   if 'parts'in a:turns.append(max(abs(math.remainder(m['parts'][arm]['rot'][k]-a['parts'][arm]['rot'][k],math.tau))for k in range(3))*180/math.pi)
  checks[tag+'_'+name+'_released']=all(v.get('owner')!='PlantReach'or v.get('weight',0)<.005 for v in ms[-1]['interaction'].values())
  metrics.setdefault(name,{})[tag]={'active_walk_samples':len(errors),'walk_mean_contact_error_blocks':round(statistics.mean(errors),4)if errors and tag=='after' else None,'walk_max_contact_error_blocks':round(max(errors),4)if errors and tag=='after' else None,'walk_max_arm_component_step_degrees':round(max(turns),2)if turns else None}
  if tag=='after':checks[tag+'_'+name+'_walking_contact']=len(errors)>=20 and max(errors)<.03
  fs=[r for r in rs if 'screenshot'in r and Path(r['screenshot']).name.startswith('plant-edge-'+name+'-')];lines=[]
  for i,r in enumerate(fs):
   p=source/'shots'/Path(r['screenshot']).name;dt=(fs[i+1]['captureNanos']-r['captureNanos'])*1e-9 if i+1<len(fs)else .1
   lines += [f"file '{p}'",f'duration {max(.001,dt):.9f}']
  lines += [f"file '{p}'"];concat=OUT/f'{tag}-{name}.txt';concat.write_text('\n'.join(lines)+'\n')
  subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(concat),'-vf','crop=1400:1000:560:360,scale=1280:-2','-fps_mode','vfr','-c:v','libx264','-crf','22','-pix_fmt','yuv420p','-movflags','+faststart',str(OUT/f'{tag}-{name}.mp4')],check=True)
report={'checks':checks,'metrics':metrics,'note':'ДО — 3a82d31, ПОСЛЕ — исправленная боковая кромка. Замер ошибки ДО не сопоставим: старая диагностика перезаписывала цель при копировании на броню; null вместо недостоверного числа. Ошибка измерена на финальной ладони при активной ходьбе (34–67). Исчезновение растений в конце проверяет отпускание. Здоровое выживание, GUI скрыт. Реальная съёмка ~10 FPS по captureNanos.'};(OUT/'metrics.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
body=['<!doctype html><meta charset="utf-8"><title>PlantReach · скользящее касание колосьев</title><style>body{background:#151b22;color:#eef;font:16px system-ui;max-width:1400px;margin:30px auto;padding:16px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:16px}video{width:100%}</style><h1>PlantReach: ладонь скользит по краю колосьев</h1><p>Ближайшая кромка следует вдоль игрока; высота контакта подбирается под длину прямой руки. Походка FA+Player сохраняется. Сравнение с предыдущим ошибочным бил дом.</p>']
for name,label in [('right','Ряд справа'),('left','Ряд слева · обратный проход'),('crouch','В присяде')]:
 body.append(f'<h2>{label}</h2><button onclick="this.nextElementSibling.querySelectorAll(\'video\').forEach(v=>{{v.currentTime=0;v.play()}})">Запустить обе</button><div class="pair">')
 for tag in ['before','after']:body.append(f'<div><p>{"ДО"if tag=="before"else"ПОСЛЕ"}</p><video controls loop src="{tag}-{name}.mp4"></video></div>')
 body.append('</div>')
body.append('<pre>'+html.escape(json.dumps(report,ensure_ascii=False,indent=2))+'</pre>');(OUT/'index.html').write_text('\n'.join(body).replace('бил дом','билдом'))
print(json.dumps(report,ensure_ascii=False));assert all(checks.values()),[k for k,v in checks.items()if not v]
