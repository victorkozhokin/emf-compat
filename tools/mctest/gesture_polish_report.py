"""Build gesture before/after videos from archived frames at their actual capture times.
Usage: python3 tools/mctest/gesture_polish_report.py
"""
import json, math, subprocess, html, concurrent.futures
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
OUT=ROOT/'build/gesture-polish-review';OUT.mkdir(exist_ok=True)
NAMES={'feed':'Кормление и поглаживание','milk':'Доение','shear':'Стрижка','seed':'Посадка семян','stand':'Снаряжение стойки','boots':'Ботинки','armour':'Очередь частей брони','shake':'Отряхивание после воды','chest':'Поиск в сундуке'}
EDGES={'boots-standing':'Осмотр ботинок стоя','leggings-standing':'Осмотр поножей стоя','snow':'Рыхлый снег','mud':'Грязь','boots-crouch':'Ботинки в присяде','seed-crouch':'Посев в присяде','feed-left':'Кормление второй рукой','cancel':'Уход от животного','barrel-slots':'Боковая бочка: два действия со слотами'}
def read(tag):
 source=ROOT/f'build/gesture-polish-{tag}';steps=json.loads((source/'steps.json').read_text());rows=json.loads((source/'results.json').read_text())['results'];groups={};case=''
 for row in rows:
  step=steps[row['step']]
  if 'log'in step and step['log'].startswith('gesture ') and not step['log'].endswith('click'):case=step['log'].split()[1].split('/')[0]
  if tag.startswith('edges') and 'screenshot'in row:case=Path(row['screenshot']).stem.split('edge-')[-1].rsplit('-right-',1)[0]
  if case:groups.setdefault(case,[]).append(row)
 return source,rows,groups
jobs=[];metrics={};checks={}
for tag in ['before','after','edges','close']:
 source,rows,groups=read(tag)
 if tag in ('edges','close') and (ROOT/f'build/gesture-polish-{tag}-barrel/results.json').exists():
  replacement,extra,newgroups=read(tag+'-barrel')
  for name,group in newgroups.items():
   for row in group:row['_archive']=str(replacement/'shots');row['_resultFile']=str(replacement/'results.json')
   groups[name]=group
  rows=rows+extra
 states=[r['state']for r in rows if 'state'in r]
 checks[tag+'_no_driver_errors']=not any('error'in r for r in rows)
 checks[tag+'_healthy_survival']=bool(states) and all(s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['gameMode']=='survival' for s in states)
 for name,group in groups.items():
  frames=[r for r in group if 'screenshot'in r];models=[r['model']for r in group if 'model'in r]
  if not frames:continue
  checks[f'{tag}_{name}_finite']=all(math.isfinite(v)for m in models for p in m.get('parts',{}).values()for key in ['rot','pos']for v in p.get(key,[]))
  owner={'feed':'AnimalCare','milk':'AnimalCare','shear':'AnimalCare','seed':'HandTo','stand':'HandTo','boots':'ArmorDon','armour':'ArmorDon','shake':'ShakeOff','chest':'ContainerSearch','barrel-slots':'ContainerSearch','snow':'ShakeOff','mud':'ShakeOff','boots-crouch':'ArmorDon','seed-crouch':'HandTo','feed-left':'AnimalCare','cancel':'AnimalCare','boots-standing':'ArmorDon','leggings-standing':'ArmorDon','barrel':'ContainerSearch'}[name]
  checks[f'{tag}_{name}_gesture_observed']=any(v.get('owner')==owner for m in models for v in m.get('interaction',{}).values())
  bodies=[m['parts']['body']['rot']for m in models]
  metrics.setdefault(name,{})[tag]={'model_samples':len(models),'max_pitch_deg':round(max(b[0]for b in bodies)*180/math.pi,2),'max_component_step_deg':round(max(abs(math.remainder(bodies[i][axis]-bodies[i-1][axis],math.tau))for i in range(1,len(bodies))for axis in range(3))*180/math.pi,2)}
  target=OUT/f'{tag}-{name}.mp4';concat=OUT/f'{tag}-{name}.txt'
  lines=[]
  for i,row in enumerate(frames):
   p=Path(row.get('_archive',str(source/'shots')))/Path(row['screenshot']).name
   duration=(frames[i+1]['captureNanos']-row['captureNanos'])*1e-9 if i+1<len(frames)else .1
   lines.extend([f"file '{p}'",f'duration {max(.001,duration):.9f}'])
  lines.append(f"file '{p}'");concat.write_text('\n'.join(lines)+'\n')
  if not target.exists() or target.stat().st_mtime < Path(frames[0].get('_resultFile',str(source/'results.json'))).stat().st_mtime:jobs.append((concat,target))
def encode(job):
 c,t=job
 subprocess.run(['ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(c),'-vf','scale=1280:-2','-fps_mode','vfr','-c:v','libx264','-preset','fast','-crf','23','-pix_fmt','yuv420p','-movflags','+faststart',str(t)],check=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2)as pool:list(pool.map(encode,jobs))
report={'checks':checks,'metrics':metrics,'note':'Углы после всех слоёв; максимум соседнего шага включает вход/выход и намеренное движение. Это не отдельная оценка jitter или точности ладони. Видео сохраняет реальные интервалы снимков (~10 кадров/с). Curios проверен модульно, в игре не проверен.'}
(OUT/'metrics.json').write_text(json.dumps(report,ensure_ascii=False,indent=2))
body=['<!doctype html><html lang="ru"><meta charset="utf-8"><title>Жесты: до / после</title><style>body{background:#151b22;color:#e4ecf3;font:16px system-ui;margin:32px auto;max-width:1450px;padding:20px}article{border-top:1px solid #405060;padding:20px 0}.pair{display:grid;grid-template-columns:1fr 1fr;gap:12px}video{width:100%;background:#000}button{padding:10px;border-radius:6px}pre{white-space:pre-wrap}small{color:#adc0d0}</style><h1>Animation Additions · шлифовка жестов</h1><p>FA+Player — основа позы; A&S — референс ритма. Слева исходная версия, справа доработка. GUI скрыт, здоровое выживание. Записи собраны из игровых кадров по фактическому времени.</p><p>Исходный коммит: 3208e80. Кадры записаны примерно 10 раз/с: это обзор движения, а не видео для оценки плавности каждого render-кадра.</p>']
for name,title in NAMES.items():
 body.append(f'<article><h2>{title}</h2><button onclick="pair(this)">Сначала / воспроизвести обе</button><div class="pair">')
 for tag,label in [('before','До'),('after','После')]:body.append(f'<div><h3>{label}</h3><video controls loop preload="none" src="{tag}-{name}.mp4"></video></div>')
 body.append('</div></article>')
body.append('<h1>Дополнительные сценарии</h1>')
for name,title in EDGES.items():body.append(f'<article><h2>{title}</h2><video controls loop preload="none" src="edges-{name}.mp4"></video></article>')
body.append('<h1>Вплотную к объектам</h1><p>Отдельный прогон с удобной ориентацией животного и уменьшенным расстоянием; он не заменяет сравнение одинаковых условий до/после.</p>')
for name in ['feed','milk','shear','stand','seed','chest','barrel']:
 body.append(f'<article><h2>{NAMES.get(name,"Боковая бочка")}</h2><video controls loop preload="none" src="close-{name}.mp4"></video></article>')
body.append('<details><summary>Измерения и ограничения</summary><pre>'+html.escape(json.dumps(report,ensure_ascii=False,indent=2))+'</pre></details><script>function pair(b){b.closest("article").querySelectorAll("video").forEach(v=>{v.currentTime=0;v.play()})}</script></html>')
(OUT/'index.html').write_text('\n'.join(body));print(json.dumps({'checks':checks,'failed':[k for k,v in checks.items()if not v]},ensure_ascii=False))
if not all(checks.values()):raise SystemExit(1)
