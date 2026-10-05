"""Checks real cockpit targets, health, motion continuity and writes a review with videos."""
import json,math,shutil,subprocess
from pathlib import Path
ROOT=Path('build/cockpit-smooth-review')
def groups(root):
    steps=json.loads((root/'steps.json').read_text());rows=json.loads((root/'results.json').read_text())['results'];out={};phase=None
    for row in rows:
        step=steps[row['step']]
        if 'log'in step:
            phase=step['log'].split(':')[-1];phase=None if phase=='end'else phase
            if phase:out[phase]={'models':[],'states':[],'shots':[]}
        if phase:
            for key,dest in [('model','models'),('state','states'),('screenshot','shots')]:
                if key in row:out[phase][dest].append(row)
    return out,rows

def body_delta(g):
    vals=[]
    for a,b in zip(g['models'],g['models'][1:]):
        x=a['model']['parts']['body']['rot'];y=b['model']['parts']['body']['rot']
        vals.append(max(abs(math.atan2(math.sin(v-u),math.cos(v-u))) for u,v in zip(x,y))*180/math.pi)
    return max(vals,default=0)

def verify():
    gs,rows=groups(ROOT);old,_=groups(Path('build/cockpit-seat-clearance-mixed'));errors=[];summary={};healthy=0
    if any('error'in r for r in rows):errors.append('driver errors')
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for name,g in gs.items():
        cs=[r['model'].get('cockpit',{}) for r in g['models']]
        if not cs:continue
        if not all(c.get('shown')for c in cs):errors.append(name+': cockpit lost')
        for row in g['states']:
            s=row['state'];healthy+=1
            if not(s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['gameMode']=='survival'):errors.append(name+': health/food/hurt')
        if not all(c['rightMix']==0 or c['leftMix']==0 for c in cs):errors.append(name+': both hands leave wheel')
        if name.startswith('wheel-') and not all(c['request']==-1 and c['rightMix']==0 and c['leftMix']==0 for c in cs):errors.append(name+': proximity activated throttle')
        if name in ['hover-right','hover-left'] and not all(c['request']==(0 if name.endswith('right')else 1)for c in cs):errors.append(name+': aimed throttle not acquired')
        for hand in ['right','left']:
            gap=max(c[hand+'Gap']for c in cs)
            if gap>.15:errors.append(f'{name}: transient {hand} gap {gap:.3f}')
            settled=max(c[hand+'Gap']for c in cs[-6:])
            if settled>.13:errors.append(f'{name}: settled {hand} gap {settled:.3f}')
        # Same model snapshots measure target movement, rather than relying on asynchronously paired logs.
        active_side=0 if name.startswith('right-typing')else 1 if name.startswith('left-typing')else None
        if active_side is not None and '-key-'in name:
            arm='left_arm'if active_side==0 else 'right_arm'
            angles=[r['model']['parts'][arm]['rot']for r in g['models']]
            if max(math.dist(a,angles[0])for a in angles)<.03:errors.append(name+': wheel hand frozen')
        if name.endswith('wheel-start')or name.endswith('wheel-resume'):
            if body_delta(g)>5:errors.append(name+': torso jump exceeds 5 degrees per recorded tick')
        summary[name]={'frames':len(cs),'bodyMaxStepDegrees':body_delta(g),'rightGapMaxBlocks':max(c['rightGap']for c in cs),'leftGapMaxBlocks':max(c['leftGap']for c in cs),'requests':sorted({c['request']for c in cs})}
        if name in old:summary[name]['archivedBodyMaxStepDegrees']=body_delta(old[name])
        for row in g['shots']:shutil.copyfile(row['screenshot'],shots/Path(row['screenshot']).name)
    summary['healthySamples']=healthy;summary['errors']=errors
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2))
    html='<!doctype html><meta charset="utf-8"><title>Cockpit — плавность и наведение</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1150px;margin:24px auto;padding:0 20px}video{width:100%;border-radius:12px}a{color:#95ded0}</style><h1>Cockpit: плавность, наведение, вторая рука</h1><p>FA+Player · здоровое выживание · GUI скрыт. Рычаг только при прямом наведении; машинка активируется штатно; вторая рука следует рулю.</p>'
    html+=f'<p>Здоровых замеров: {healthy}. Ошибок: {len(errors)}. <a href="verified.json">Замеры, включая сравнение с архивной записью</a>. Архивный дубль не является строгим синхронным A/B-тестом.</p>'
    for label,prefix in [('Наведение: рядом с рычагами → правый → левый','hover'),('Машинка справа · рычаг слева','right'),('Машинка слева · рычаг справа','left')]:
        selected=[g for name,g in gs.items()if (name.startswith(('wheel-','hover-'))if prefix=='hover'else name.startswith(prefix+'-'))]
        entries=[]
        for g in selected:
            ss=g['shots']
            for i,row in enumerate(ss):
                path=(shots/Path(row['screenshot']).name).resolve()
                dt=(ss[i+1]['captureNanos']-row['captureNanos'])*1e-9 if i+1<len(ss)else .05
                entries.extend(["file '"+str(path).replace("'","'\\''")+"'",f'duration {max(.02,min(.25,dt)):.6f}'])
        if not entries:continue
        entries.append(entries[-2]);manifest=ROOT/(prefix+'-concat.txt');manifest.write_text('\n'.join(entries)+'\n')
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(manifest),'-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(prefix+'.mp4'))],check=True)
        html+=f'<h2>{label}</h2><video controls muted src="{prefix}.mp4"></video>'
    html+='<p>Скорость по времени снятых кадров; паузы между сценариями вырезаны.</p>'
    (ROOT/'index.html').write_text(html)
    shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    print(json.dumps(summary,indent=2));assert not errors,errors
if __name__=='__main__':verify()
