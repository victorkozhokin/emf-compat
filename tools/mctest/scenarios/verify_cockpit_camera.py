"""Compare identical seat/camera/steering takes and retain timing-correct videos."""
import json,math,shutil,subprocess
from pathlib import Path
from verify_cockpit_smooth import groups,body_delta
ROOT=Path('build/cockpit-camera-review')
def measurements(g):
    cs=[r['model']['cockpit']for r in g['models']]
    p=[r['model']['parts']['body']['pos']for r in g['models']]
    return {'frames':len(cs),'bodyMaxStepDegrees':body_delta(g),
            'bodyMaxPositionStepPixels':max(max(abs(a-b)for a,b in zip(x,y))for x,y in zip(p,p[1:])),
            'rightGapMaxBlocks':max(c['rightGap']for c in cs),'leftGapMaxBlocks':max(c['leftGap']for c in cs),
            'seatGapMaxBlocks':max(c['seatGap']for c in cs),
            'facingErrorMaxDegrees':max(abs(c['facingErrorDegrees'])for c in cs),
            'inertiaMaxRadians':max(max(abs(c['inertiaPitch']),abs(c['inertiaRoll']))for c in cs)}
def verify():
    ROOT.mkdir(exist_ok=True,parents=True);summary={};errors=[]
    datasets={}
    for tag in ['before','after']:
        source=Path('build/cockpit-camera-'+tag);gs,rows=groups(source);datasets[tag]=gs
        summary[tag]={name:measurements(g)for name,g in gs.items()}
        if any('error'in r for r in rows):errors.append(tag+': driver errors')
        for name,g in gs.items():
            if len(g['models'])!=120:errors.append(tag+': incomplete '+name)
            for row in g['states']:
                s=row['state']
                if not(s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['gameMode']=='survival'):errors.append(tag+': health '+name)
            cs=[r['model']['cockpit']for r in g['models']]
            if not all(c.get('shown')for c in cs):errors.append(tag+': cockpit lost '+name)
            if tag=='after':
                m=summary[tag][name]
                if max(m['rightGapMaxBlocks'],m['leftGapMaxBlocks'])>.15:errors.append('after: palm detached '+name)
                if name=='camera' and (m['bodyMaxStepDegrees']>.35 or m['bodyMaxPositionStepPixels']>.08):errors.append('after: camera shakes torso')
                if name.startswith('steering') and (m['bodyMaxStepDegrees']>2.5 or m['bodyMaxPositionStepPixels']>.5):errors.append('after: steering torso discontinuity')
                if m['seatGapMaxBlocks']>.03 or m['facingErrorMaxDegrees']>.1:errors.append('after: seat/facing '+name)
        dest=source/'shots';dest.mkdir(exist_ok=True)
        for row in rows:
            if 'screenshot'in row:
                target=dest/Path(row['screenshot']).name
                if not target.exists():
                    if tag=='after' and Path(row['screenshot']).exists():
                        shutil.copyfile(row['screenshot'],target)
                    else:errors.append(tag+': archived screenshot missing')
    summary['errors']=errors;(ROOT/'verified.json').write_text(json.dumps(summary,indent=2))
    labels={'idle':'Неподвижно','camera':'Повороты камеры','orbit':'Облёт камерой наблюдения','steering':'Руление','steering-camera':'Руление и камера одновременно'}
    html='<!doctype html><meta charset="utf-8"><title>Cockpit — посадка и камера: до/после</title><style>body{background:#151b22;color:#edf2f6;font:17px system-ui;max-width:1200px;margin:30px auto;padding:0 20px}.pair{display:flex;gap:12px}.pair div{width:50%}video{width:100%;border-radius:12px}table{border-collapse:collapse;width:100%}td,th{padding:10px;text-align:left;border-bottom:1px solid #46505e}a{color:#95dfd0}</style><h1>Посадка: повороты камеры и руление</h1><p>Одинаковые сценарии до/после · FA+Player · Survival · полные здоровье и сытость · GUI скрыт. Движение камеры не задаёт принудительно поворот корпуса: его обновляет сама игра.</p>'
    html+='<p><a href="../cockpit-camera-mixed-regression/index.html">Машинка и рычаги с обеих сторон</a> · <a href="../cockpit-camera-craft-review/index.html">Движущаяся палуба</a></p>'
    html+=f'<p>Проверок кадров: 600 до и 600 после. Ошибок: {len(errors)}. <a href="verified.json">Подробные замеры</a>.</p><table><tr><th>Сцена</th><th>Макс. шаг корпуса ДО, °</th><th>ПОСЛЕ, °</th><th>Сдвиг ДО, пикс.</th><th>ПОСЛЕ, пикс.</th></tr>'
    for name in labels:
        b=summary['before'][name];a=summary['after'][name]
        html+=f'<tr><td>{labels[name]}</td><td>{b["bodyMaxStepDegrees"]:.3f}</td><td>{a["bodyMaxStepDegrees"]:.3f}</td><td>{b["bodyMaxPositionStepPixels"]:.3f}</td><td>{a["bodyMaxPositionStepPixels"]:.3f}</td></tr>'
    html+='</table><p>Максимальный шаг между записанными кадрами, около одного игрового тика. Дыхание и намеренный перенос веса не считаются ошибкой автоматически. Видео по времени снятия кадров, без ускорения.</p>'
    for name in ['camera','steering','steering-camera']:
        html+=f'<h2>{labels[name]}</h2><div class="pair">'
        for tag in ['before','after']:
            rows=datasets[tag][name]['shots'];entries=[]
            for i,row in enumerate(rows):
                path=(Path('build/cockpit-camera-'+tag)/'shots'/Path(row['screenshot']).name).resolve()
                dt=(rows[i+1]['captureNanos']-row['captureNanos'])*1e-9 if i+1<len(rows)else .05
                entries += ["file '"+str(path).replace("'","'\\''")+"'",f'duration {max(.02,min(.25,dt)):.6f}']
            entries.append(entries[-2]);manifest=ROOT/(name+'-'+tag+'.txt');manifest.write_text('\n'.join(entries)+'\n')
            video=name+'-'+tag+'.mp4'
            subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-f','concat','-safe','0','-i',str(manifest),'-vf','fps=30','-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/video)],check=True)
            html+=f'<div><p>{"ДО"if tag=="before"else"ПОСЛЕ"}</p><video controls muted loop src="{video}"></video></div>'
        html+='</div>'
    (ROOT/'index.html').write_text(html)
    print(json.dumps(summary,indent=2));assert not errors,errors
if __name__=='__main__':verify()
