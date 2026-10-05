"""Check the shortened live-platform seat regression and retain its capture timing."""
import json,shutil,subprocess
from pathlib import Path
ROOT=Path('build/cockpit-camera-craft-review')
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());rows=json.loads((ROOT/'results.json').read_text())['results']
    gs={};name=None;errors=[]
    if len(rows)!=len(steps)or any('error'in r for r in rows):errors.append('incomplete or failed driver scenario')
    for row in rows:
        s=steps[row['step']]
        if 'log'in s:
            name=s['log'].split(':')[-1];name=None if name=='end'else name
            if name:gs[name]={k:[]for k in ['state','model','craft','screenshot']}
        if name:
            for k in gs[name]:
                if k in row:gs[name][k].append(row)
    out={};count=0;entries=[];dest=ROOT/'shots';dest.mkdir(exist_ok=True)
    for name,g in gs.items():
        cs=[r['model'].get('cockpit',{})for r in g['model']];count+=len(cs)
        if not cs or not all(c.get('shown')and c.get('craft')for c in cs):errors.append(name+': craft pose lost');continue
        for r in g['state']:
            s=r['state']
            if not(s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['gameMode']=='survival'):errors.append(name+': unhealthy')
        gap=max(max(c['rightGap'],c['leftGap'])for c in cs)
        settled=max(max(c['rightGap'],c['leftGap'])for c in cs[-6:])
        if gap>.15 or settled>.13:errors.append(name+': palm detached')
        if not all(abs(c['facingErrorDegrees'])<.1 and abs(c['deckUpErrorDegrees'])<.1 and c['seatGap']<.03 for c in cs):errors.append(name+': deck/seat drift')
        if not all(max(c['rightSeatPenetration'],c['leftSeatPenetration'])<.02 for c in cs):errors.append(name+': thigh penetration')
        if not all(abs(c['pelvisAboveSeat']-2.25/16)<.03 for c in cs[-6:]):errors.append(name+': pelvis not supported')
        physical=not name.endswith('stationary')
        if not all(r['craft']['physical']==physical and(not physical or r['craft']['handleValid'])for r in g['craft']):errors.append(name+': native physics absent')
        if physical and max(c['speed']for c in cs)<.1:errors.append(name+': platform did not move')
        out[name]={'frames':len(cs),'maximumPalmGapBlocks':gap,'settledPalmGapBlocks':settled,'maximumFacingErrorDegrees':max(abs(c['facingErrorDegrees'])for c in cs),'maximumDeckUpErrorDegrees':max(abs(c['deckUpErrorDegrees'])for c in cs),'maximumSeatGapBlocks':max(c['seatGap']for c in cs),'maximumSpeed':max(c['speed']for c in cs)}
        shots=g['screenshot']
        for i,r in enumerate(shots):
            target=dest/Path(r['screenshot']).name;shutil.copyfile(r['screenshot'],target)
            dt=(shots[i+1]['captureNanos']-r['captureNanos'])*1e-9 if i+1<len(shots)else .05
            entries += ["file '"+str(target.resolve()).replace("'","'\\''")+"'",f'duration {max(.02,min(.25,dt)):.6f}']
    out.update(healthySamples=count,errors=errors);(ROOT/'verified.json').write_text(json.dumps(out,indent=2))
    if entries:
        entries.append(entries[-2]);manifest=ROOT/'concat.txt';manifest.write_text('\n'.join(entries)+'\n')
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(manifest),'-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/'craft.mp4')],check=True)
    (ROOT/'index.html').write_text(f'<!doctype html><meta charset="utf-8"><title>Cockpit — посадка на палубе</title><style>body{{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1200px;margin:30px auto;padding:0 20px}}video{{width:100%}}a{{color:#95dfd0}}</style><h1>Посадка и камера на движущейся палубе</h1><p>Survival · FA+Player · GUI скрыт · {count} замеров · ошибок {len(errors)}.</p><p>Нативная физика Sable и пропеллеры. Стенд поддерживает высоту и ограничивает наклон. Покой → разгон и руление → машинка в движении → поворот камеры → наклон палубы.</p><video controls muted src="craft.mp4"></video><p><a href="verified.json">Замеры контактов, таза, кресла и ориентации</a></p>')
    print(json.dumps(out,indent=2));assert not errors,errors
if __name__=='__main__':verify()
