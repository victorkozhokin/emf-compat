"""Record native mixed-control results and videos, including any contact failures."""
import json,re,math,shutil,subprocess
from pathlib import Path
from verify_cockpit_typewriter import palm
ROOT=Path('build/cockpit-mixed-review')
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());rows=json.loads((ROOT/'results.json').read_text())['results'];groups={};phase=None;errors=[]
    if len(rows)!=len(steps):errors.append({'error':'incomplete scenario','rows':len(rows),'steps':len(steps)})
    for r in rows:
        if 'error'in r:errors.append(r)
        s=steps[r['step']]
        if 'log'in s:
            phase=s['log'].split(':')[-1];phase=None if phase=='end'else phase
            if phase:groups[phase]={'states':[],'models':[],'shots':[],'native':[],'throttle':[],'trace':[]}
        if phase:
            for field,key in [('state','states'),('model','models'),('screenshot','shots'),('typewriter','native'),('throttle','throttle')]:
                if field in r:groups[phase][key].append(r[field])
    phase=None
    for line in Path('run/mctest/Test/logs/latest.log').read_text().splitlines():
        mark=re.search(r'mixedcase:(\S+)',line)
        if mark:phase=mark[1]if mark[1]in groups else None
        if phase and '[CockpitTrace]'in line:
            v={k:float(a)for k,a in re.findall(r'(\w+)=([-+\d.E]+)',line)}
            for k in ['shown','typing','throttleHeld']:v[k]=k+'=true'in line
            for hand in ['right','left']:v[hand+'Target']=list(map(float,re.search(hand+r'Target=\(([^)]*)\)',line)[1].split()))
            groups[phase]['trace'].append(v)
    summary={};healthy=0
    for name,g in groups.items():
        t=g['trace'];typing='-typing-'in name or name.endswith('typing-again');throttle='throttle'in name
        def check(ok,msg):
            if not ok:errors.append({'case':name,'error':msg})
        check(bool(t),'missing trace')
        healthy+=len(g['states']);check(all(s['gameMode']=='survival'and s['health']==20 and s['food']==20 and s['hurtTime']==0 for s in g['states']),'health/food/hurt regression')
        if not t or not g['models']:continue
        check(all(v['rightMix']==0 or v['leftMix']==0 for v in t),'both hands leave wheel')
        check(all(v['shown']and min(v['rightWeight'],v['leftWeight'])>.99 for v in t),'pose ownership lost')
        check(all(.499<=v['rimRight']<=.55 and .499<=v['rimLeft']<=.55 for v in t),'rim point inside wheel')
        expected=0 if name.startswith('right')else 1
        if throttle:expected=1-expected
        if typing:
            check(all(n['mode']=='ACTIVE'for n in g['native']),'native typing inactive')
            check(all(v['typing']and v['request']==expected for v in t),'typing side/pose incorrect')
        if throttle:
            check(all(n['held']for n in g['throttle']),'native throttle inactive')
            check(all(v['throttleHeld']and v['request']==expected for v in t),'throttle side/pose incorrect')
        if '-typing-key-'in name or name.endswith('typing-again'):
            code=int(name.rsplit('-',1)[-1])if '-typing-key-'in name else 65;index={81:0,65:6,32:13}[code]
            check(all(index in n['pressed']for n in g['native']),'native key not pressed');check(t[-1]['key']==index,'key pose lost')
        if name.endswith('return'):check(t[-1]['rightMix']==t[-1]['leftMix']==0,'hands did not return')
        last=t[-1];model=g['models'][-1]['parts']
        snapshots=[m.get('cockpit',{})for m in g['models']]
        if any('cockpit'in m for m in g['models']):
            check(all(c.get('shown')for c in snapshots),'missing current render-frame cockpit pose')
            gaps={h:max(c.get(h+'Gap',float('inf'))for c in snapshots)for h in ['right','left']}
            check(all(abs(c['facingErrorDegrees'])<.1 and c['seatGap']<.03 for c in snapshots),'seating/facing regression')
        else:
            gaps={h:math.dist(palm(model[h+'_arm']),last[h+'Target'])/16 for h in ['right','left']}
        check(max(gaps.values())<.13,'stable palm-target gap exceeds 0.13 blocks')
        summary[name]={'frames':len(g['models']),'palmGapBlocks':gaps,'key':last['key'],'request':last['request'],'signals':sorted({n['signal']for n in g['throttle']if 'signal'in n})}
    summary['healthySamples']=healthy;summary['wheelDragEvents']=sum('steeringDrag'in s for s in steps);summary['errors']=errors
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2));shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for g in groups.values():
        for src in g['shots']:shutil.copyfile(src,shots/Path(src).name)
    for side in ['right','left']:
        clip=shots/side;clip.mkdir(exist_ok=True);i=0
        for name,g in groups.items():
            if name.startswith(side):
                for src in g['shots']:shutil.copyfile(src,clip/f'{i:03}.png');i+=1
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(clip/'%03d.png'),'-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(side+'.mp4'))],check=True)
    html='<!doctype html><meta charset="utf-8"><title>Машинка, рычаг и руль</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1150px;margin:24px auto}video{width:100%;border-radius:12px}img{width:48%;margin:1%}</style><h1>Низкая машинка и противоположный throttle</h1><p>FA+Player · полная сборка Test · здоровое выживание · GUI скрыт. Нативные клавиши Q/A/Space и штатное управление рулём и рычагом.</p><p>Руль → набор с вращением руля в обе стороны → рычаг вперёд/назад → вращение руля → повторный набор с вращением → возврат. Монтаж фрагментов 1×, 20 кадров/с, паузы вырезаны.</p>'
    html+=f'<p>Проверено {healthy} кадров; событий вращения руля: {summary["wheelDragEvents"]}; отклонений проверки: {len(errors)}. <a href="verified.json">Подробные измерения</a>.</p>'
    for side,label in [('right','Машинка справа · рычаг слева'),('left','Машинка слева · рычаг справа')]:html+=f'<h2>{label}</h2><video controls loop muted src="{side}.mp4"></video>'
    for name,g in groups.items():
        src=Path(g['shots'][-1]).name;html+=f'<p>{name}</p><a href="shots/{src}"><img src="shots/{src}"></a>'
    (ROOT/'index.html').write_text(html)
    print(json.dumps(summary,indent=2));assert not errors,errors
if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=ROOT);args=parser.parse_args();ROOT=args.output
    verify()
