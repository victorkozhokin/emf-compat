"""Verify same-render palms/soles and native item changes; publish an honest 1x gallery."""
import json,shutil,subprocess,math,sys
from pathlib import Path
ROOT=Path('build/table-support-review')
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());rows=json.loads((ROOT/'results.json').read_text())['results']
    groups={};phase=None;errors=[];summary={}
    def check(ok,msg):
        if not ok:errors.append(msg)
    check(len(rows)==len(steps),'incomplete scenario')
    for row in rows:
        check('error'not in row,str(row)if'error'in row else'')
        step=steps[row['step']]
        if 'log'in step:
            phase=step['log'].split(':')[-1];phase=None if phase=='end'else phase
            if phase:groups[phase]={'state':[],'model':[],'screenshot':[],'table':[]}
        if phase:
            for k in groups[phase]:
                if k in row:groups[phase][k].append(row[k])
    for name,g in groups.items():
        ms=[m.get('tableSupport',{})for m in g['model']];stable=[m for m in ms if m.get('load',0)>.9 and m.get('owned',0)>.98 and m.get('engaged')]
        check(len(g['state'])==len(ms)==len(g['screenshot'])==len(g['table']),name+': incomplete samples')
        check(all(s['gameMode']=='survival'and s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['screenHidden']for s in g['state']),name+': unhealthy/hidden GUI regression')
        check(all(m.get('maxSoleDriftPixels',0)<.01 for m in ms),name+': soles displaced')
        if name in ['too-far','vertical-table']:check(not ms[-1] and not any(m.get('engaged') or m.get('load',0)>0 for m in ms),name+': unsupported surface acquired')
        elif name=='release':
            check(ms[-1]['load']==0 and ms[-1]['owned']<.1,'release incomplete')
            check(any(m.get('owned',0)>.98 and m.get('load',1)==0 for m in ms),'palm released before unloading')
        else:
            check(bool(stable),name+': no settled weight transfer')
            check(ms[-1].get('load')==1,name+': load did not settle')
            check(all(m['leftGap'if m['mainRight']else'rightGap']<.13 for m in stable),name+': loaded supporting palm detached')
            if name not in ['put-compass','take-compass']:check(all(max(m['rightGap'],m['leftGap'])<.13 for m in stable),name+': idle palm detached')
        bodySteps=[]
        def quaternion(a):
            x,y,z=a;cx,sx=math.cos(x/2),math.sin(x/2);cy,sy=math.cos(y/2),math.sin(y/2);cz,sz=math.cos(z/2),math.sin(z/2)
            return (sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz)
        for a,b in zip(g['model'][15:],g['model'][16:]):
            if any(m.get('tableSupport',{}).get('load',0)<.99 for m in [a,b]):continue
            qa,qb=quaternion(a['parts']['body']['rot']),quaternion(b['parts']['body']['rot'])
            bodySteps.append(math.degrees(2*math.acos(min(1,abs(sum(x*y for x,y in zip(qa,qb)))))))
        if name in ['standing-contact','diagonal','crouching','left-hand']:
            check(max(bodySteps,default=0)<1,name+': abrupt sustained body correction')
        if name=='left-hand':check(all(not m.get('mainRight',True)for m in ms),'left-hand ownership incorrect')
        if name=='put-compass':check(all(t['item']=='minecraft:compass'and t['count']==1 for t in g['table']),'native compass was not placed')
        if name=='take-compass':check(all(t['count']==0 for t in g['table']),'native compass was not removed')
        summary[name]={'frames':len(ms),'settledFrames':len(stable),'lastLoad':ms[-1].get('load'),
            'maxSupportingPalmGap':max((m['leftGap'if m['mainRight']else'rightGap']for m in stable),default=0),
            'maxSettledBodyStepDegrees':max(bodySteps,default=0),
            'maxSoleDriftPixels':max((m.get('maxSoleDriftPixels',0)for m in ms),default=0)}
    summary['errors']=errors;summary['healthySamples']=sum(len(g['state'])for g in groups.values())
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2));shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    if '--metrics-only' in sys.argv:
        print(json.dumps(summary,indent=2));assert not errors,errors;return
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    labels={'standing-contact':'Стоя: контакт → перенос веса','put-compass':'Установка компаса','take-compass':'Снятие компаса','release':'Разгрузка → отпускание','diagonal':'Подход к углу','crouching':'В присяде','left-hand':'Левша','too-far':'За пределом досягаемости','vertical-table':'Вертикальный стол: опора отключена'}
    html='<!doctype html><meta charset="utf-8"><title>Опора на стол</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1100px;margin:24px auto}video{width:100%;border-radius:12px}a{color:#8ccef7}</style><h1>Navigation Table · опора на стол</h1><p>Настоящая сборка Test, FA+Player, здоровое выживание, GUI скрыт. Снято в тестовой копии. Видео 1×, 20 кадров/с; паузы между сценариями вырезаны.</p>'
    html+=f'<p>Проверено {summary["healthySamples"]} кадров. Отклонений: {len(errors)}. <a href="verified.json">Измерения контакта и ступней</a>.</p><p>Руки сначала находят столешницу, затем таз переносит вес при закреплённых ступнях. При отпускании вес возвращается до ухода ладоней. Во время установки предмета работает основная рука, другая сохраняет опору. Первые кадры захвата и возврата показывают весь переход; требование контакта проверяется после захвата.</p>'
    for name,g in groups.items():
        clip=shots/name;clip.mkdir(exist_ok=True)
        for i,src in enumerate(g['screenshot']):shutil.copyfile(src,clip/f'{i:03}.png')
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(clip/'%03d.png'),'-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(name+'.mp4'))],check=True)
        html+=f'<h2>{labels[name]}</h2><video controls loop muted src="{name}.mp4"></video>'
    (ROOT/'index.html').write_text(html)
    print(json.dumps(summary,indent=2));assert not errors,errors
if __name__=='__main__':verify()
