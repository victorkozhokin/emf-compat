"""Verify actual contact/ownership and stance in a controlled real Sable fixture."""
import json,re,shutil,statistics
from pathlib import Path

ROOT=Path('build/transport-review')
def verify():
    steps=json.loads((ROOT/'steps.json').read_text())
    result=json.loads((ROOT/'results.json').read_text())['results']
    assert len(result)==len(steps)
    assert not [r for r in result if 'error' in r]
    groups={};phase=None
    for r in result:
        step=steps[r['step']]
        if 'log' in step and step['log'].startswith('transportqa:'):
            phase=step['log'].split(':')[1];phase=None if phase=='end' else phase
            if phase:groups[phase]={'states':[],'models':[],'shots':[],'trace':[]}
        if phase:
            for field in ('state','model','screenshot'):
                if field in r:groups[phase][{'state':'states','model':'models','screenshot':'shots'}[field]].append(r[field])
    log=Path('run/mctest/Test/logs/latest.log').read_text();phase=None
    def triple(text):return [float(x) for x in re.findall(r'[-+]?\d*\.?\d+(?:[Ee][-+]?\d+)?',text)]
    for line in log.splitlines():
        mark=re.search(r'transportqa:(\S+)',line)
        if mark:phase=None if mark[1]=='end' else mark[1]
        if phase and '[TransportTrace]' in line:
            row={key:float(re.search(r'\b'+key+r'=([^ ]+)',line)[1]) for key in ('speed','relative','load','owned','gap','helperGap','step')}
            row['helper']='helper=true' in line
            for key in ('right','left','local','world'):row[key]=triple(re.search(key+r'=\(([^)]+)\)',line)[1])
            groups[phase]['trace'].append(row)
    states=[s for g in groups.values() for s in g['states']]
    assert len(states)==452
    assert all(s['gameMode']=='survival' and s['health']==20 and s['food']==20 and s['hurtTime']==0 for s in states)
    def weight(s):return max(s['transportRight'],s['transportLeft'])
    for name in ('stationary','disabled','both-occupied','released','ordinary-ground'):
        assert max(weight(s) for s in groups[name]['states'][-5:])<.03,name
    occupied=groups['both-occupied']['states'][5:]
    assert all(s['mainItem']=='minecraft:iron_sword' and s['offItem']=='minecraft:shield' for s in occupied)
    assert max(s['transportRight'] for s in groups['main-occupied']['states'][-5:])<.03
    for name in ('rail-steady','plain-block','rope-grip','rail-turn','rope-turn'):
        t=groups[name]['trace'];assert t and min(x['gap'] for x in t)<.01,name
        assert t[-1]['gap']<.04 and t[-1]['owned']>.95,name
    force=groups['rail-accel']['trace']+groups['rail-brake']['trace']+groups['rail-crouch']['trace']
    assert any(t['helper'] and t['helperGap']<.08 and t['gap']<.04 for t in force)
    assert {0,1}.issubset({int(t['step']) for t in force})
    assert max(t['left'][0]-t['right'][0] for t in force)>2.2
    assert max(t['gap'] for t in force if t['owned']>.95)<.13
    for name in ('rail-turn','rope-turn'):
        t=groups[name]['trace'];assert len(t)>8
        assert max(sum((a-b)**2 for a,b in zip(x['local'],t[0]['local'])) for x in t)<1e-8
        assert sum((a-b)**2 for a,b in zip(t[-1]['world'],t[0]['world']))>.01
    pitch={name:[m['parts']['body']['rot'][0] for m in groups[name]['models']] for name in ('rail-accel','rail-brake')}
    assert min(pitch['rail-accel'])<-.05 and max(pitch['rail-brake'])>.05,pitch
    summary={'commands':len(result),'healthySamples':len(states),'scenarios':len(groups),
             'primaryGapMaxUnderLoad':max(t['gap'] for t in force if t['owned']>.95),
             'twoHandsContact':True,'sequentialBraceSteps':True,'rotatingLocalContactStable':True,
             'occupiedHandsRelease':True,'guiHidden':True,'fixture':'Real Sable sub-level; controlled server-tick poses, physics paused only in test copy; native carrying/collision'}
    (ROOT/'verified.json').write_text(json.dumps(summary,indent=2)+'\n')
    shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for g in groups.values():
        for source in g['shots']:shutil.copyfile(source,shots/Path(source).name)
    names={'stationary':'До движения','rail-steady':'Захват поручня и постановка ног','rail-accel':'Разгон: вторая рука, перенос веса',
           'rail-brake':'Торможение','rail-crouch':'Боковой разгон в присяде','rail-turn':'Поворот платформы',
           'disabled':'Отключение механики','restored':'Возвращение захвата','main-occupied':'Меч в основной руке',
           'both-occupied':'Обе руки заняты','hands-free':'Освобождение рук','walking-away':'Уход от опоры',
           'released':'После отпускания','plain-block':'Опора на обычный блок','rope-grip':'Канат Supplementaries',
           'rope-turn':'Поворот с канатом','ordinary-ground':'Обычная земля'}
    gallery=[{'name':names[k],'images':['shots/'+Path(p).name for p in g['shots']]} for k,g in groups.items()]
    html='''<!doctype html><meta charset="utf-8"><title>EMF · Опора в транспорте</title>
<style>body{background:#151b22;color:#eef2f7;font:17px system-ui;margin:24px auto;max-width:1150px}button,select,input{font:inherit;margin:5px;padding:8px;border-radius:6px}img{width:100%;display:block;border-radius:12px}p{color:#b7c6d7}</style>
<h1>Опора в движущемся транспорте</h1><p>Полная сборка Test, FA+Player. Выживание: здоровье и голод 20, урон 0. GUI скрыт. Настоящий sub-level Sable с управляемым движением; физика стенда приостановлена в тестовой копии.</p>
<select id="scene"></select><button id="play">▶ 1×</button><input id="frame" type="range" min="0" value="0"><span id="counter"></span><img id="shot">
<p>Кадры сняты каждый тик: воспроизведение 20 кадров/с. Контакт ладони, поочерёдные подшаги, занятые руки и координаты захвата проверены отдельно по игровым данным.</p>
<script>const data=DATA;let n=0,i=0,playing=false,last=0;const scene=document.querySelector('#scene'),shot=document.querySelector('#shot'),frame=document.querySelector('#frame'),counter=document.querySelector('#counter');data.forEach((x,j)=>scene.add(new Option(x.name,j)));function show(){frame.max=data[n].images.length-1;frame.value=i;shot.src=data[n].images[i];counter.textContent=(i+1)+' / '+data[n].images.length}scene.onchange=()=>{n=+scene.value;i=0;show()};frame.oninput=()=>{i=+frame.value;show()};document.querySelector('#play').onclick=()=>{playing=!playing;document.querySelector('#play').textContent=playing?'⏸':'▶ 1×';last=performance.now()};function loop(t){if(playing&&t-last>=50){i=(i+Math.floor((t-last)/50))%data[n].images.length;last=t;show()}requestAnimationFrame(loop)}show();requestAnimationFrame(loop);</script>'''
    (ROOT/'index.html').write_text(html.replace('DATA',json.dumps(gallery,ensure_ascii=False)))
    return summary
if __name__=='__main__':print(json.dumps(verify(),indent=2))
