"""Native compression/contact measurements and GUI-free videos of bellows interactions."""
import json,shutil,subprocess,math
from pathlib import Path
ROOT=Path('build/bellows-review')
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());rows=json.loads((ROOT/'results.json').read_text())['results'];groups={};phase=None;errors=[];summary={}
    def check(ok,msg):
        if not ok:errors.append(msg)
    check(len(rows)==len(steps),'incomplete scenario')
    for row in rows:
        check('error'not in row,str(row)if'error'in row else'')
        step=steps[row['step']]
        if 'log'in step:
            phase=step['log'].split(':')[-1];phase=None if phase=='end'else phase
            if phase:groups[phase]={'state':[],'model':[],'screenshot':[],'bellows':[]}
        if phase:
            for k in groups[phase]:
                if k in row:groups[phase][k].append(row[k])
    for name,g in groups.items():
        models=[m.get('bellows',{})for m in g['model']];owned=[m for m in models if m.get('owned',0)>.98]
        check(all(s['gameMode']=='survival'and s['health']==20 and s['food']==20 and s['hurtTime']==0 and s['screenHidden']for s in g['state']),name+': health/GUI regression')
        check(all(m.get('maxSoleDriftPixels',0)<.01 for m in models),name+': planted soles drifted')
        check(all(m.get('headOverlapPixels',0)<.01 for m in owned),name+': head entered bellows casing')
        # The compression is real motion; measure residual steps after acquisition separately.
        bodySteps=[]
        def quaternion(a):
            x,y,z=a;cx,sx=math.cos(x/2),math.sin(x/2);cy,sy=math.cos(y/2),math.sin(y/2);cz,sz=math.cos(z/2),math.sin(z/2)
            return (sx*cy*cz-cx*sy*sz,cx*sy*cz+sx*cy*sz,cx*cy*sz-sx*sy*cz,cx*cy*cz+sx*sy*sz)
        for a,b in zip(g['model'][15:],g['model'][16:]):
            qa,qb=quaternion(a['parts']['body']['rot']),quaternion(b['parts']['body']['rot'])
            bodySteps.append(math.degrees(2*math.acos(min(1,abs(sum(x*y for x,y in zip(qa,qb)))))))
        if name in ['idle','north-press','left-press','east-press','south-press','west-press']:
            check(max(bodySteps,default=0)<1,name+': abrupt repeated body correction')
        native=g['bellows'];check(len(models)==len(native)==len(g['state'])==len(g['screenshot']),name+': missing samples')
        check(all(-.1251<=n['height']<=.0001 for n in native),name+': unexpected native compression')
        if name in ['redstone','occupied','release','vertical-side']:
            check(not models[-1] and not any(m.get('engaged')for m in models),name+': unexpected manual hold')
            if name=='redstone':check(max(n['height']for n in native)-min(n['height']for n in native)>.03,'redstone cycle not exercised')
        elif name=='blown-release':
            check(any(m.get('engaged')for m in models),'wind case did not begin manual use')
            check(not models[-1].get('engaged') and models[-1].get('owned',0)<.01,'hands did not release when native wind moved player')
            check(abs(g['state'][-1]['pos'][0]-g['state'][0]['pos'][0])>.2,'native wind did not move player')
        else:
            check(bool(owned),name+': hand ownership not acquired')
            check(all(max(m['rightGap'],m['leftGap'])<.13 for m in owned),name+': palm detached from moving plate')
            if name=='idle':check(not any(m.get('load',0)>0 for m in models),'idle invented pressure')
            else:
                check(any(m.get('engaged')for m in models),name+': native manual use not attributed')
                check(min(n['height']for n in native)<-.07,name+': native cycle did not compress')
                if name not in ['up-press','down-press']:check(max(m.get('load',0)for m in models)>.5,name+': no grounded weight transfer')
            if name=='left-press':check(all(not m['mainRight']for m in owned),'left-handed grip lost')
        summary[name]={'frames':len(models),'maxLoad':max((m.get('load',0)for m in models),default=0),
            'maxPalmGap':max((max(m.get('rightGap',0),m.get('leftGap',0))for m in owned),default=0),
            'maxSettledBodyStepDegrees':max(bodySteps,default=0),
            'maxHeadOverlapPixels':max((m.get('headOverlapPixels',0)for m in owned),default=0),
            'nativeHeightRange':[min(n['height']for n in native),max(n['height']for n in native)]}
    summary['healthySamples']=sum(len(g['state'])for g in groups.values());summary['errors']=errors
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2));shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    labels={'idle':'Подготовка: руки на крышке','north-press':'Ручное сжатие: руки, корпус и таз','release':'Возврат и отпускание','crouch-press':'Приседание во время сжатия','left-press':'Левша','east-press':'Восток','south-press':'Юг','west-press':'Запад: боковой подход','up-press':'Мехи направлены вверх','down-press':'Мехи направлены вниз','blown-release':'Поток воздуха отталкивает игрока: отпускание','redstone':'Редстоун: мехи работают без позы рук','occupied':'Вторая рука занята','vertical-side':'Подход сбоку вертикальной крышки: захват отключён'}
    html='<!doctype html><meta charset="utf-8"><title>Supplementaries · мехи</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1100px;margin:24px auto}video{width:100%;border-radius:12px}a{color:#8ccef7}</style><h1>Supplementaries · ручная работа мехами</h1><p>FA+Player, полная сборка Test, здоровое выживание. GUI скрыт. Настоящие клики и нативное движение крышки; видео 1×, 20 кадров/с.</p>'
    html+=f'<p>{summary["healthySamples"]} кадров, отклонений: {len(errors)}. <a href="verified.json">Плавность, зазор головы и контакты</a>.</p><p>Коррекция корпуса продолжает предыдущую позу и ограничена по скорости. Вертикальная крышка доступна со своей открытой стороны; подход через корпус блока не вызывает захват. Проверяется объём головы, а не только ладони. Две ладони на подвижной крышке, перенос веса после контакта, закреплённые ступни. Присяд включён после клика: Shift+клик в этой сборке поднимает блок через Carry On. В серии с потоком воздуха игрок движется штатной физикой мехов; потеря досягаемости завершает контакт.</p>'
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for name,g in groups.items():
        clip=shots/name;clip.mkdir(exist_ok=True)
        for i,src in enumerate(g['screenshot']):shutil.copyfile(src,clip/f'{i:03}.png')
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(clip/'%03d.png'),'-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(name+'.mp4'))],check=True)
        html+=f'<h2>{labels[name]}</h2><video controls loop muted src="{name}.mp4"></video>'
    (ROOT/'index.html').write_text(html);print(json.dumps(summary,indent=2));assert not errors,errors
if __name__=='__main__':verify()
