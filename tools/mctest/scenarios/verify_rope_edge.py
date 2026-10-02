"""Assert that a raised foot is a held edge pose, never a repeating deck gait."""
import json,re,shutil,subprocess,math
from pathlib import Path
ROOT=Path('build/rope-edge-review')
NAMES={'deck':'Палуба · ноги не перебирают','edge':'Край · правая рука, левая нога опорная','edge-left':'Зеркально · левая рука, правая нога опорная','return':'Возврат обеих ног на палубу'}
def sole(part):
    x,y,z=part['rot'];p=part['pos'];sx,cx=math.sin(x),math.cos(x);sy,cy=math.sin(y),math.cos(y);sz,cz=math.sin(z),math.cos(z)
    return [p[0]+12*(-sz*cx+cz*sy*sx),p[1]+12*(cz*cx+sz*sy*sx),p[2]+12*cy*sx]
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());rows=json.loads((ROOT/'results.json').read_text())['results'];groups={};phase=None
    assert len(steps)==len(rows) and not any('error'in r for r in rows)
    for r in rows:
        t=steps[r['step']]
        if 'log'in t:
            phase=t['log'].split(':')[-1];phase=phase if phase in NAMES else None
            if phase:groups[phase]={'states':[],'models':[],'shots':[],'trace':[]}
        if phase:
            for field,key in [('state','states'),('model','models'),('screenshot','shots')]:
                if field in r:groups[phase][key].append(r[field])
    log=Path('run/mctest/Test/logs/latest.log').read_text();phase=None
    for line in log.splitlines():
        mark=re.search(r'rope-edge:(\S+)',line)
        if mark:
            phase=mark[1] if mark[1] in groups else None
            if phase:groups[phase]['trace']=[]
        if phase and '[TransportTrace]'in line:
            row={k:float(re.search(r'\b'+k+r'=([^ ]+)',line)[1]) for k in ('owned','gap','step','lift')};row['rope']='rope=true'in line
            groups[phase]['trace'].append(row)
    summary={}
    for name,g in groups.items():
        ss=g['states'];tt=g['trace'];assert len(ss)==160 and tt,name
        assert all(s['gameMode']=='survival' and s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['onGround'] for s in ss),name
        assert all(t['rope'] and t['owned']>.95 and t['gap']<.13 and t['step']==-1 for t in tt),(name,tt)
        lifts=[t['lift']for t in tt]
        if name.startswith('edge'):assert min(lifts)>1.7 and max(lifts)-min(lifts)<.03,(name,lifts)
        else:assert max(lifts)<.031,(name,lifts)
        if name=='edge-left':assert all(s['transportLeft']>.95 and s['transportRight']<.05 for s in ss)
        if name=='edge':assert all(s['transportRight']>.95 for s in ss)
        # Check the rendered model, not just the internal lift variable.
        rendered=[(sole(m['parts']['right_leg']),sole(m['parts']['left_leg']))for m in g['models']]
        raised=1 if name=='edge-left' else 0;support=1-raised
        if name.startswith('edge'):
            assert min(r[support][1]-r[raised][1] for r in rendered)>1.4,name
            assert min(r[raised][2]-r[support][2] for r in rendered)>1.7,name
        support_span=[max(r[support][i]for r in rendered)-min(r[support][i]for r in rendered)for i in range(3)]
        assert max(support_span)<.65,(name,support_span)
        summary[name]={'frames':len(ss),'palmGapMax':max(t['gap']for t in tt),'liftRangePixels':[min(lifts),max(lifts)],'supportSoleSpanPixels':support_span,'steps':[-1]}
    summary['healthySamples']=sum(len(g['states'])for g in groups.values());assert summary['healthySamples']==640
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2));shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for name,g in groups.items():
        for src in g['shots']:shutil.copyfile(src,shots/Path(src).name)
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(shots/(name+'-%03d.png')),'-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(name+'.mp4'))],check=True)
    html='<!doctype html><meta charset="utf-8"><title>Канат · опора у края</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1150px;margin:24px auto}video{width:100%;border-radius:12px}</style><h1>Канат: устойчивая поза у края</h1><p>FA+Player · полная сборка Test · здоровое выживание · GUI скрыт · 20 кадров/с, скорость 1×. Настоящие палуба Sable и канат Aeronautics; движение по кругу и форма каната управляются тестовым стендом, свободная физика приостановлена в копии мира.</p>'
    for name,title in NAMES.items():html+=f'<h2>{title}</h2><video controls loop muted src="{name}.mp4"></video>'
    html+='<p><a href="verified.json">Измерения контакта и опорной ступни</a></p>'; (ROOT/'index.html').write_text(html)
    return summary
if __name__=='__main__':print(json.dumps(verify(),indent=2))
