"""Verify native rope contact, circular carrying, healthy capture and one planted foot."""
import json,re,shutil,math,subprocess
from pathlib import Path
ROOT=Path('build/aerorope-review')
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());result=json.loads((ROOT/'results.json').read_text())['results']
    assert len(result)==len(steps) and not [r for r in result if 'error'in r]
    groups={};phase=None
    for r in result:
        step=steps[r['step']]
        if 'log'in step and step['log'].startswith('aerorope:'):
            phase=step['log'].split(':')[1];phase=None if phase in ('end','begin')else phase
            if phase:groups[phase]={'states':[],'models':[],'shots':[],'trace':[]}
        if phase:
            for field,key in [('state','states'),('model','models'),('screenshot','shots')]:
                if field in r:groups[phase][key].append(r[field])
    log=Path('run/mctest/Test/logs/latest.log').read_text().split('aerorope:begin')[-1];phase=None
    for line in log.splitlines():
        mark=re.search(r'aerorope:(\S+)',line)
        if mark:
            phase=mark[1] if mark[1] in groups else None
            if phase:groups[phase]['trace']=[]
        if phase and '[TransportTrace]'in line:
            row={k:float(re.search(r'\b'+k+r'=([^ ]+)',line)[1]) for k in ('speed','owned','gap','helperGap','step','lift')}
            row['rope']='rope=true'in line;row['helper']='helper=true'in line
            groups[phase]['trace'].append(row)
    ss=[s for g in groups.values()for s in g['states']];assert len(ss)==840
    assert all(s['gameMode']=='survival' and s['health']==20 and s['food']==20 and s['hurtTime']==0 for s in ss)
    summary={}
    for name,g in groups.items():
        t=g['trace'];assert t and all(x['rope'] for x in t),name
        assert min(max(s['transportRight'],s['transportLeft']) for s in g['states'])>.95,name
        assert max(x['gap'] for x in t)<.13,(name,max(x['gap'] for x in t))
        # Right-handed fixture: left support foot cannot step while right foot is raised.
        assert all(x['lift']<.031 for x in t if x['step']==1),name
        points=[s['pos'] for s in g['states']];xs=[p[0]for p in points];zs=[p[2]for p in points]
        assert max(xs)-min(xs)>7.5 and max(zs)-min(zs)>7.5,name
        centre=((max(xs)+min(xs))/2,(max(zs)+min(zs))/2)
        radii=[math.hypot(p[0]-centre[0],p[2]-centre[1])for p in points]
        assert max(abs(r-4)for r in radii)<.25,name
        body=[m['parts']['body']['rot'] for m in g['models']]
        summary[name]={'frames':len(points),'speed':sum(x['speed']for x in t)/len(t),'palmGapMax':max(x['gap']for x in t),
                       'liftMaxPixels':max(x['lift']for x in t),'steps':sorted(set(int(x['step'])for x in t)),
                       'bodyPitchSpan':max(p[0]for p in body)-min(p[0]for p in body),
                       'helperContact':any(x['helper'] and x['helperGap']<.13 for x in t),
                       'radiusErrorMax':max(abs(r-4)for r in radii)}
    assert all(g['liftMaxPixels']<.031 and g['steps']==[-1] for g in summary.values()),summary
    assert summary['circle-fast']['helperContact']
    assert summary['circle-fast']['bodyPitchSpan']>summary['circle-slow']['bodyPitchSpan']
    summary.update(healthySamples=len(ss),commands=len(result),guiHidden=True,fps=20,
        fixture='Real Sable sub-level and native Aeronautics rope/carrying. Controlled kinematic circle and taut rope; free physics paused only in test copy.')
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2));shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for name,g in groups.items():
        for source in g['shots']:shutil.copyfile(source,shots/Path(source).name)
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(shots/(name+'-%03d.png')),
                        '-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(name+'.mp4'))],check=True)
    names={'circle-slow':'Два круга · 3.14 блока/с','circle-fast':'Два круга · 5.03 блока/с','circle-crouch':'Круг в присяде','circle-wide':'Общий вид платформы'}
    html='''<!doctype html><meta charset="utf-8"><title>EMF · Канат Aeronautics</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1150px;margin:24px auto}video{width:100%;border-radius:12px}p{color:#b7c6d7}</style><h1>Опора за канат Aeronautics</h1><p>Полная сборка Test · FA+Player · выживание, здоровье/голод 20, урон 0 · GUI скрыт · запись 20 кадров/с, скорость 1×.</p><p>Настоящий канат и палуба Sable. Круг и натянутый канат управляются тестовым стендом; свободная физика приостановлена только в копии мира. Радиус 4 блока. Это проверка анимации и перенесения игрока, а не корабля под двигателями.</p>'''
    for name,title in names.items():html+=f'<h2>{title}</h2><video controls loop muted preload="metadata" src="{name}.mp4"></video>'
    html+='<p><a href="verified.json">Проверенные игровые измерения</a></p>'
    (ROOT/'index.html').write_text(html)
    return summary
if __name__=='__main__':print(json.dumps(verify(),indent=2))
