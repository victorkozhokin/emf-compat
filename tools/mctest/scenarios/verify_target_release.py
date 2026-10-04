"""Matched, timestamp-based comparison; quaternion distances rather than Euler magnitude.
Screenshots sample the last drawn frame; timing uncertainty is at most one render interval.
"""
import csv,html,json,math,statistics,subprocess,hashlib
from pathlib import Path
ROOT=Path('build/target-release-review')
CASES=[
 ('mining-disabled','Выключение MiningIK на замахе','mining-disabled-hold','mining-disabled-release','RIGHT_ARM'),
 ('mining-removed','Удаление добываемого блока','mining-removed-hold','mining-removed-release','RIGHT_ARM'),
 ('mining-away','Потеря направления на добываемый блок','mining-away-hold','mining-away-release','RIGHT_ARM'),
 ('door','Выключение контакта с дверью','door-hold','door-release','LEFT_ARM'),
 ('crank','Отпускание Crank','crank-hold','crank-release','RIGHT_ARM'),
 ('lever','Замена рычага обычным блоком','lever-hold','lever-release','RIGHT_ARM'),
 ('barrel','Смена двух целей у BlockUse','barrel-hold','barrel-switch','RIGHT_ARM'),
 ('table','Выключение опоры на стол','table-hold','table-disabled','RIGHT_ARM'),
 ('occupied','Передача занятых рук луку','occupied-hold','occupied','LEFT_ARM'),
 ('occluded','Исчезновение поверхности контакта','occluded-hold','occluded','RIGHT_ARM'),
 ('moving','Отпускание на движущемся Sable craft','moving-grip','moving-release','RIGHT_ARM'),
]
def quat(rot):
 x,y,z=[a/2 for a in rot];cx,cy,cz=math.cos(x),math.cos(y),math.cos(z);sx,sy,sz=math.sin(x),math.sin(y),math.sin(z)
 return (sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz)
def angle(a,b):
 dot=abs(sum(x*y for x,y in zip(quat(a),quat(b))))
 return math.degrees(2*math.acos(min(1,dot)))
def load(label):
 groups={};events={};states=[]
 for fn,st in [('results.json','steps.json'),('extra-results.json','extra-steps.json')]:
  rows=json.loads((ROOT/label/fn).read_text())['results'];steps=json.loads((ROOT/label/st).read_text());model=None;state=None
  for row in rows:
   step=steps[row['step']]
   if step.get('log','').startswith('event:'):events[step['log'][6:]]=row['captureNanos']
   if 'state'in row:state=row['state'];states.append(state)
   if 'model'in row:model=row
   if 'screenshot'in row and model:
    name=Path(row['screenshot']).stem.removeprefix('release-').rsplit('-',1)[0]
    groups.setdefault(name,[]).append({'ns':model['captureNanos'],'shotNs':row['captureNanos'],'model':model['model'],'state':state,'shot':row['screenshot']})
 assert all(s['health']==20 and s['hurtTime']==0 and s['gameMode']=='survival'and s['food']==20 for s in states),'unhealthy/non-survival test'
 return groups,events

def metrics(frames,initial,hand):
 allframes=frames;part='right_arm'if hand=='RIGHT_ARM'else'left_arm'
 changes=[];body=[];pivot=[];speed=[]
 for a,b in zip(allframes,allframes[1:]):
  d=angle(a['model']['parts'][part]['rot'],b['model']['parts'][part]['rot']);changes.append(d)
  body.append(angle(a['model']['parts']['body']['rot'],b['model']['parts']['body']['rot']))
  pivot.append(math.dist(a['model']['parts']['body']['pos'],b['model']['parts']['body']['pos']))
  dt=(b['ns']-a['ns'])/1e9
  if dt>0:speed.append(d/dt)
 final=frames[-1]['model']['parts'][part]['rot'];tail=[angle(f['model']['parts'][part]['rot'],final)for f in frames]
 settled=next((i for i in range(len(tail))if all(x<=1 for x in tail[i:])),len(tail)-1)
 return {'initialToFirstCaptureDegrees':angle(initial['model']['parts'][part]['rot'],frames[0]['model']['parts'][part]['rot']),'maxArmStepDegrees':max(changes),'maxBodyStepDegrees':max(body),'maxBodyPivotStepPixels':max(pivot),
         'maxArmSpeedDegreesPerSecond':max(speed),'settleWithinOneDegreeSeconds':(frames[settled]['ns']-initial['ns'])/1e9,
         'medianSampleMilliseconds':statistics.median((b['ns']-a['ns'])/1e6 for a,b in zip(frames,frames[1:])),
         'oldContactWeightDuringItemUse':max((f['model']['interaction'][hand]['weight'] for f in frames if f['state']['usingItem']),default=0),'finalContactOwner':frames[-1]['model']['interaction'][hand].get('owner'),'finalContactWeight':frames[-1]['model']['interaction'][hand]['weight'],'samples':len(frames),'durationSeconds':(frames[-1]['ns']-initial['ns'])/1e9}

def video(label,key,frames,event,initial):
 out=ROOT/label/(key+'.mp4');entries=[]
 sequence=[dict(initial,shotNs=event)]+frames
 for a,b in zip(sequence,sequence[1:]):
  entries.append(f"file '{Path(a['shot']).resolve()}'\nduration {max(.001,(b['shotNs']-a['shotNs'])/1e9):.9f}\n")
 entries.append(f"file '{Path(sequence[-1]['shot']).resolve()}'\nduration 0.05\nfile '{Path(sequence[-1]['shot']).resolve()}'\n")
 concat=ROOT/label/(key+'-frames.txt');concat.write_text(''.join(entries))
 subprocess.run(['/opt/homebrew/bin/ffmpeg','-y','-loglevel','error','-f','concat','-safe','0','-i',str(concat),
                 '-vf','crop=1900:1700:(iw-1900)/2:ih-1700,scale=960:-2,fps=60','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(out)],check=True)
 return out

def plot(series,hand):
 # Shared axes and actual time, samples available in the adjacent CSV/JSON.
 points=[]
 for label,frames,event in series:
  t=[(f['ns']-event)/1e9 for f in frames];rot=[math.degrees(f['model']['parts']['right_arm'if hand=='RIGHT_ARM'else'left_arm']['rot'][0])for f in frames]
  points.append((label,t,rot))
 xmin=0;xmax=max(max(x[1])for x in points);lo=min(min(x[2])for x in points);hi=max(max(x[2])for x in points);hi=max(hi,lo+1)
 lines=[]
 for label,t,r in points:
  path=' '.join(f'{60+v/xmax*770:.1f},{210-(a-lo)/(hi-lo)*170:.1f}'for v,a in zip(t,r))
  color='#f9a369'if label=='before'else'#70dbab';lines.append(f'<polyline fill="none" stroke="{color}" stroke-width="3" points="{path}"/>')
 return f'<svg viewBox="0 0 860 250" role="img" aria-label="Угол основной руки во времени"><path d="M60 30V210H840" stroke="#687888" fill="none"/>{"".join(lines)}<text x="60" y="240">0</text><text x="750" y="240">{xmax:.2f} с</text><text x="4" y="40">{hi:.0f}°</text><text x="4" y="208">{lo:.0f}°</text></svg>'

def main():
 loaded={label:load(label)for label in ['before','after']};output={};sections=[]
 for key,title,hold,release,hand in CASES:
  output[key]={};series=[];paths=[]
  for label,(groups,events)in loaded.items():
   frames=groups[release];initial=groups.get(key+'-event',groups[hold])[-1]
   assert initial['model']['interaction'][hand].get('owner'),(key,label,'no contact before release')
   eventKey={'door':'door-disabled','crank':'crank-away','lever':'lever-replaced','barrel':'barrel-switch','table':'table-disabled','moving':'moving-disabled'}.get(key,key)
   event=events[eventKey]
   if label=='after' and key not in ('barrel','occupied'):
    assert frames[-1]['model']['interaction'][hand].get('owner') is None,(key,'contact did not finish releasing')
   if label=='after' and key=='mining-removed':
    assert any(f['model']['interaction'][hand].get('releaseReason')=='target-removed'for f in frames),'removed block was not rejected'
   output[key][label]=metrics(frames,initial,hand)
   series.append((label,frames,event));paths.append(video(label,key,frames,event,initial))
   csvPath=ROOT/label/(key+'-trace.csv')
   with csvPath.open('w')as f:
    writer=csv.writer(f);writer.writerow(['seconds','game_mode','health','food','arm_x_rad','arm_y_rad','arm_z_rad','body_x_rad','body_y_rad','body_z_rad','weight','release_reason','release_elapsed_seconds','release_remaining'])
    part='right_arm'if hand=='RIGHT_ARM'else'left_arm'
    for p in frames:
     slot=p['model']['interaction'][hand];writer.writerow([(p['ns']-event)/1e9,p['state']['gameMode'],p['state']['health'],p['state']['food'],*p['model']['parts'][part]['rot'],*p['model']['parts']['body']['rot'],slot['weight'],slot.get('releaseReason',''),slot.get('release',{}).get('elapsed',''),slot.get('releaseRemaining','')])
  a,b=output[key]['before'],output[key]['after']
  summary=f'<table><tr><th>Измерение</th><th>ДО</th><th>ПОСЛЕ</th></tr><tr><td>Последний контакт → первый отсчёт после команды</td><td>{a["initialToFirstCaptureDegrees"]:.2f}°</td><td>{b["initialToFirstCaptureDegrees"]:.2f}°</td></tr><tr><td>Максимальный скачок руки после первого отсчёта</td><td>{a["maxArmStepDegrees"]:.2f}°</td><td>{b["maxArmStepDegrees"]:.2f}°</td></tr><tr><td>Максимальный скачок корпуса</td><td>{a["maxBodyStepDegrees"]:.2f}°</td><td>{b["maxBodyStepDegrees"]:.2f}°</td></tr><tr><td>Смещение корпуса за отсчёт</td><td>{a["maxBodyPivotStepPixels"]:.3f} px</td><td>{b["maxBodyPivotStepPixels"]:.3f} px</td></tr><tr><td>До 1° от финальной позы</td><td>{a["settleWithinOneDegreeSeconds"]:.2f} с</td><td>{b["settleWithinOneDegreeSeconds"]:.2f} с</td></tr></table>'
  if key=='mining-removed':
   summary+=f'<p>В старом билде контакт после удаления вообще не завершался: конечный вес {a["finalContactWeight"]:.3f}, владелец {a["finalContactOwner"]}. В новом — {b["finalContactWeight"]:.3f}, владелец отсутствует. Нулевой шаг старой руки здесь означает застывший захват воздуха, а не плавный выход. Первый переход также содержит остаточную фазу замаха и задержку команды.</p>'
  if key=='occupied':
   summary=f'<p>Новая игровая поза имеет приоритет: старый контакт освобождает руки сразу. Больший поворот к позе лука здесь ожидаем; он не оценивается как плавное отпускание в пустую стойку.</p><table><tr><th>Проверка</th><th>ДО</th><th>ПОСЛЕ</th></tr><tr><td>Максимальный вес старого контакта во время использования лука</td><td>{a["oldContactWeightDuringItemUse"]:.3f}</td><td>{b["oldContactWeightDuringItemUse"]:.3f}</td></tr></table>'
   assert b['oldContactWeightDuringItemUse']==0,'occupied hand still blended with an old contact'
  sections.append(f'<section id="{key}"><h2>{title}</h2>{summary}<div class="pair"><div><h3>ДО</h3><video src="before/{key}.mp4" muted playsinline controls preload="metadata"></video><a href="before/{key}-trace.csv">Измерения CSV</a></div><div><h3>ПОСЛЕ</h3><video src="after/{key}.mp4" muted playsinline controls preload="metadata"></video><a href="after/{key}-trace.csv">Измерения CSV</a></div></div><button onclick="playPair(this)">Синхронное воспроизведение</button>{plot(series,hand)}</section>')
 (ROOT/'metrics.json').write_text(json.dumps(output,indent=2));(ROOT/'index.html').write_text('''<!doctype html><html lang="ru"><meta charset="utf-8"><title>Общее отпускание — ДО / ПОСЛЕ</title><style>body{margin:0;background:#121a22;color:#e9f0f3;font:18px system-ui}main{max-width:1600px;margin:auto;padding:32px}h1{font-size:38px}section{margin:32px 0;padding:24px;background:#1b2733;border-radius:18px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:20px}video{width:100%;border-radius:12px}a{color:#83d9fe}table{border-collapse:collapse;width:100%;margin:18px 0}td,th{text-align:left;border-bottom:1px solid #405365;padding:8px}button{padding:10px 20px;margin-top:20px;font-size:16px;cursor:pointer}svg{max-width:860px;width:100%;display:block}svg text{fill:#cad6df;font:14px system-ui}</style><main><h1>Общее отпускание цели · 4 октября 2026</h1><p>Реальные игровые кадры: здоровое выживание, FA+Player, интерфейс скрыт. ДО — исходный билд ee204d0 (анимации 89bba75); ПОСЛЕ — новый общий механизм отпускания.</p><p>Видео восстановлены по фактическим временным меткам. Начало — команда смены/потери цели; есть задержка доставки игровой команды. Угловой скачок — расстояние между вращениями соседних отсчётов после команды (первый переход вынесен в JSON отдельно); это не измерение каждого render frame. В таблицах показаны и улучшения, и оставшиеся различия. Цвет графика: оранжевый ДО, зелёный ПОСЛЕ; угол X рабочей руки (у двери — левой).</p><p>Полные сырые данные: <a href="before/results.json">ДО</a>, <a href="after/results.json">ПОСЛЕ</a>, <a href="metrics.json">сводка JSON</a>. Данные дополнительных стендов: <a href="before/extra-results.json">ДО</a>, <a href="after/extra-results.json">ПОСЛЕ</a>.</p>'''+''.join(sections)+'''</main><script>function playPair(btn){let vs=[...btn.closest('section').querySelectorAll('video')];Promise.all(vs.map(v=>{v.pause();v.currentTime=0;return new Promise(r=>{if(v.readyState>=2)r();else v.addEventListener('loadeddata',r,{once:true});v.load()})})).then(()=>vs.forEach(v=>v.play()))}</script></html>''')
 print(json.dumps(output,indent=2))
if __name__=='__main__':main()
