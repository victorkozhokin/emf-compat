"""Assert native movement, keys, seat/feet and same-render contacts; retain failures in the gallery."""
import json,math,shutil,subprocess
from pathlib import Path
ROOT=Path('build/cockpit-craft-review')
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());rows=json.loads((ROOT/'results.json').read_text())['results'];groups={};phase=None;errors=[]
    assert len(rows)==len(steps),(len(rows),len(steps))
    for row in rows:
        if 'error'in row:errors.append(row)
        command=steps[row['step']]
        if 'log'in command:
            phase=command['log'].split(':')[-1];phase=None if phase=='end'else phase
            if phase:groups[phase]={k:[]for k in ['state','model','craft','typewriter','throttle','screenshot']}
        if phase:
            for k in groups[phase]:
                if k in row:groups[phase][k].append(row[k])
    summary={};samples=0
    for name,g in groups.items():
        def check(ok,message):
            if not ok:errors.append({'case':name,'error':message})
        snapshots=[m.get('cockpit',{})for m in g['model']];samples+=len(snapshots)
        check(bool(snapshots)and all(m.get('emf')for m in g['model']),'missing native FA model')
        check(all(s['gameMode']=='survival'and s['health']==20 and s['food']==20 and s['hurtTime']==0 for s in g['state']),'unhealthy survival')
        check(all(c.get('shown')and c.get('craft')for c in snapshots),'seated craft pose lost')
        check(all(c['rightMix']<.001 or c['leftMix']<.001 for c in snapshots),'both hands left wheel')
        gaps={h:max(c[h+'Gap']for c in snapshots)for h in ['right','left']}
        check(max(gaps.values())<.13,'render-frame palm contact exceeds 0.13 blocks')
        check(all(abs(c['facingErrorDegrees'])<.1 and abs(c['deckUpErrorDegrees'])<.1 for c in snapshots),'orientation does not follow deck')
        check(all(max(c['rightSeatPenetration'],c['leftSeatPenetration'])<.02 for c in snapshots),'leg volume penetrates native cushion')
        check(all(abs(c['pelvisAboveSeat']-2.25/16)<.03 for c in snapshots[-8:]),'hip not supported by seat cushion')
        check(max(c['seatGap']for c in snapshots)<.03,'pelvis separates from native seat anchor')
        physical=not name.endswith('stationary')
        check(all(c['physical']==physical and (not physical or c['handleValid'])for c in g['craft']),'not live native rigid body')
        if physical:check(max(c['speed']for c in snapshots)>.1,'platform did not move')
        if 'typing'in name or name.endswith('brake'):
            code=6 if 'typing'in name else 13
            check(all(c['mode']=='ACTIVE'for c in g['typewriter'])and any(code in c['pressed']for c in g['typewriter']),'native A/Space not pressed')
            check(snapshots[-1]['key']==code,'key animation missing')
        if 'throttle'in name:check(all(t['held']for t in g['throttle']),'native throttle not held')
        if name.endswith('accelerate'):check(max(c['inertiaPitch']for c in snapshots)>.005,'acceleration has no body response')
        if name.endswith('brake'):check(min(c['inertiaPitch']for c in snapshots)<-.005,'braking has no reverse body response')
        q=g['craft'][-1]['orientation']
        summary[name]={'frames':len(snapshots),'maximumPalmGapBlocks':gaps,'endPalmGapBlocks':{h:snapshots[-1][h+'Gap']for h in ['right','left']},'maximumSeatGapBlocks':max(c['seatGap']for c in snapshots),'maximumFacingErrorDegrees':max(abs(c['facingErrorDegrees'])for c in snapshots),'maximumDeckUpErrorDegrees':max(abs(c['deckUpErrorDegrees'])for c in snapshots),'speedRange':[min(c['speed']for c in snapshots),max(c['speed']for c in snapshots)],'pitchResponseRange':[min(c['inertiaPitch']for c in snapshots),max(c['inertiaPitch']for c in snapshots)],'rollResponseRange':[min(c['inertiaRoll']for c in snapshots),max(c['inertiaRoll']for c in snapshots)],'endNativeOrientation':q}
    summary.update(healthySamples=samples,wheelDragEvents=sum('steeringDrag'in s for s in steps),errors=errors)
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2));shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    for side in ['right','left']:
        shots=ROOT/side;shots.mkdir(exist_ok=True);i=0
        for name,g in groups.items():
            if name.startswith(side):
                for src in g['screenshot']:shutil.copyfile(src,shots/f'{i:04}.png');i+=1
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(shots/'%04d.png'),'-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(side+'.mp4'))],check=True)
    html='<!doctype html><meta charset="utf-8"><title>Кабина на живом корабле</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1150px;margin:24px auto}video{width:100%;border-radius:12px}</style><h1>Руль, машинка и throttle на движущейся палубе</h1><p>Полная сборка Test, FA+Player. Выживание, здоровье и сытость 20, GUI скрыт. Камера следует за игроком.</p><p>Нативные пропеллеры: разгон → набор A → торможение и Space → throttle → дифференциальный поворот → наклон палубы → поворот камеры. Физика Sable включена; тестовый стабилизатор удерживает высоту и задаёт ограниченный крен, движение и поворот не задаются телепортацией.</p>'
    html+=f'<p>{samples} кадров, {summary["wheelDragEvents"]} вращений руля, ошибок: {len(errors)}. <a href="verified.json">Все измерения, включая переходные кадры</a>. Видео: фрагменты 1×, 20 кадров/с.</p>'
    for side,label in [('right','Машинка справа, throttle слева'),('left','Машинка слева, throttle справа')]:html+=f'<h2>{label}</h2><video controls loop muted src="{side}.mp4"></video>'
    (ROOT/'index.html').write_text(html);print(json.dumps(summary,indent=2));assert not errors,errors
if __name__=='__main__':verify()
