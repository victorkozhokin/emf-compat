"""Native comparison of five early interactions, with actual frame timing and final-pose metrics."""
import concurrent.futures,html,json,math,statistics,subprocess
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];OUT=ROOT/'build/early-interactions-review';OUT.mkdir(exist_ok=True)
CASES={'plant':('PlantReach','Растения: касание и проход вдоль ряда'),'door':('DoorHold','Дверь: ручка, открытие и проход'),'furniture':('Furniture','Пюпитр: руки у книги и опора'),'button':('ButtonPress','Кнопка: наведение, нажатие и возврат'),'wall':('WallHand','Внешняя стена: контакт одной рукой и уход')}
def limb_end(part,length):
 x,y,z=part['rot'];sx,cx=math.sin(x),math.cos(x);sy,cy=math.sin(y),math.cos(y);sz,cz=math.sin(z),math.cos(z)
 return [part['pos'][0]+(cz*sy*sx-sz*cx)*length,part['pos'][1]+(sz*sy*sx+cz*cx)*length,part['pos'][2]+cy*sx*length]
jobs=[];checks={};metrics={}
for tag in ['before','after']:
 source=ROOT/f'build/early-interactions-{tag}'
 steps=json.loads((source/'steps.json').read_text());rows=json.loads((source/'results.json').read_text())['results'];groups={};name=None
 checks[tag+'_driver']=not any('error'in r for r in rows)
 for row in rows:
  step=steps[row['step']]
  if step.get('log','').startswith('early:'):name=step['log'].split(':')[1]
  if name:groups.setdefault(name,[]).append(row)
 for name,(owner,title)in CASES.items():
  group=groups[name];allmodels=[r['model']for r in group if 'model'in r];models=[m for m in allmodels if 'parts'in m and 'interaction'in m];checks[f'{tag}_{name}_visible']=len(models)==len(allmodels);states=[r['state']for r in group if 'state'in r]
  frames=[r for r in group if 'screenshot'in r and Path(r['screenshot']).name.startswith(f'early-{name}-')]
  checks[f'{tag}_{name}_health']=bool(states)and all(s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['gameMode']=='survival'for s in states)
  checks[f'{tag}_{name}_finite']=all(math.isfinite(v)for m in models for p in m['parts'].values()for key in ['pos','rot']for v in p.get(key,[]))
  checks[f'{tag}_{name}_owner']=any(v.get('owner')==owner for m in models for v in m['interaction'].values())
  checks[f'{tag}_{name}_withdrawn']=all(v.get('owner')!=owner or v.get('weight',0)<.005 for v in models[-1]['interaction'].values())
  errors=[]
  for m in models[20:36]:
   for hand,v in m['interaction'].items():
    key=owner+':'+hand;point=m.get('handContacts',{}).get(key)
    if point is None or v.get('owner')!=owner or v.get('weight',0)<.95 or v.get('releasing'):continue
    arm=m['parts']['right_arm'if hand=='RIGHT_ARM'else'left_arm']
    errors.append(math.dist(limb_end(arm,point[3]),point[:3])*.9375/16)
  body=[m['parts']['body']['rot']for m in models]
  plant_steps=[]
  if name=='plant':
   for i in range(37,64):
    a,b=models[i-1],models[i]
    va,vb=a['interaction']['RIGHT_ARM'],b['interaction']['RIGHT_ARM']
    if va.get('owner')==owner and vb.get('owner')==owner and not va.get('releasing')and not vb.get('releasing'):
     plant_steps.append(max(abs(math.remainder(b['parts']['right_arm']['rot'][k]-a['parts']['right_arm']['rot'][k],math.tau))for k in range(3))*180/math.pi)
  metrics.setdefault(name,{})[tag]={'model_samples':len(models),'steady_contact_max_blocks':round(max(errors),4)if errors else None,'steady_contact_mean_blocks':round(statistics.mean(errors),4)if errors else None,'steady_sole_center_max_height_blocks':round(max(abs(24-limb_end(m['parts'][leg],12)[1])*.9375/16 for m in models[20:36] for leg in ['right_leg','left_leg']),4),'max_body_pitch_degrees':round(max(abs(p[0])for p in body)*180/math.pi,2),'max_body_component_step_degrees':round(max(abs(math.remainder(body[i][k]-body[i-1][k],math.tau))for i in range(1,len(body))for k in range(3))*180/math.pi,2),'plant_walk_active_arm_max_step_degrees':round(max(plant_steps),2)if plant_steps else None}
  if tag=='after' and name=='plant':checks['after_plant_walk_smooth']=bool(plant_steps) and max(plant_steps)<5
  if tag=='after'and errors:checks[f'after_{name}_steady_contact']=max(errors)<.08
  lines=[]
  for i,row in enumerate(frames):
   p=source/'shots'/Path(row['screenshot']).name
   dt=(frames[i+1]['captureNanos']-row['captureNanos'])*1e-9 if i+1<len(frames)else .1
   lines.extend([f"file '{p}'",f'duration {max(.001,dt):.9f}'])
  lines.append(f"file '{p}'");concat=OUT/f'{tag}-{name}.txt';concat.write_text('\n'.join(lines)+'\n');jobs.append((concat,OUT/f'{tag}-{name}.mp4'))
def encode(job):
 concat,target=job
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(concat),'-vf','crop=1400:1000:560:360,scale=1280:-2','-fps_mode','vfr','-c:v','libx264','-preset','fast','-crf','23','-pix_fmt','yuv420p','-movflags','+faststart',str(target)],check=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2)as pool:list(pool.map(encode,jobs))
report={'checks':checks,'metrics':metrics,'note':'Одинаковая подготовка и реальные действия; GUI скрыт, здоровое выживание. Ошибка контакта — конец прямой руки после всех слоёв, в установившейся стойке (выборка 20–35). До нет целей PlantReach/WallHand в диагностике; null означает отсутствие замера. Максимальный соседний шаг включает намеренные движения, не является самостоятельной оценкой jitter. Видео ~10 кадров/с по фактическим captureNanos.'}
(OUT/'metrics.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
body=['<!doctype html><html lang="ru"><meta charset="utf-8"><title>Ранние взаимодействия · до / после</title><style>body{background:#151b22;color:#e4ecf3;font:16px system-ui;max-width:1500px;margin:32px auto;padding:20px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:16px}video{width:100%}article{border-top:1px solid #456;padding:18px 0}button{padding:10px}pre{white-space:pre-wrap}</style><h1>Ранние взаимодействия: контакт, опора и возврат</h1><p>Пять отдельных сцен. Общий принцип: опора → корпус → контакт от окончательного плеча → плавное отпускание. Принятые Crank и Steering Wheel не менялись.</p><p>На подходе камера закреплена в свободном месте; при выходе следует за игроком, обе ступни видны, GUI скрыт. Одинаковые условия здорового выживания. Кадры приближены одинаковым кадрированием, без изменения игровых дистанций. Записи ~10 кадров/с по фактическому времени; не заменяют проверку каждого render-кадра.</p>']
for name,(owner,title)in CASES.items():
 body.append(f'<article><h2>{title}</h2><button onclick="this.closest(\'article\').querySelectorAll(\'video\').forEach(v=>{{v.currentTime=0;v.play()}})">Сначала / воспроизвести обе</button><div class="pair">')
 for tag,label in [('before','До'),('after','После')]:body.append(f'<div><h3>{label}</h3><video controls loop preload="metadata" src="{tag}-{name}.mp4"></video></div>')
 body.append('</div></article>')
body.append('<details><summary>Проверки и замеры</summary><pre>'+html.escape(json.dumps(report,ensure_ascii=False,indent=2))+'</pre></details></html>');(OUT/'index.html').write_text('\n'.join(body))
print(json.dumps(report,ensure_ascii=False));failed=[k for k,v in checks.items()if not v]
if failed:print('FAILED',failed);raise SystemExit(1)
