"""Native keys, one hand on rim, seated feet, and rendered contact at each stable key."""
import json,re,math,shutil,subprocess
from pathlib import Path
ROOT=Path('build/cockpit-typewriter-review')
def palm(part):
    x,y,z=part['rot'];p=part['pos'];sx,cx=math.sin(x),math.cos(x);sy,cy=math.sin(y),math.cos(y);sz,cz=math.sin(z),math.cos(z)
    return [p[0]+11*(-sz*cx+cz*sy*sx),p[1]+11*(cz*cx+sz*sy*sx),p[2]+11*cy*sx]
def verify():
    steps=json.loads((ROOT/'steps.json').read_text());rows=json.loads((ROOT/'results.json').read_text())['results'];groups={};phase=None
    assert len(steps)==len(rows) and not any('error'in r for r in rows)
    for r in rows:
        s=steps[r['step']]
        if 'log'in s:
            phase=s['log'].split(':')[-1];phase=None if phase=='end' else phase
            if phase:groups[phase]={'states':[],'models':[],'shots':[],'native':[],'trace':[]}
        if phase:
            for field,key in [('state','states'),('model','models'),('screenshot','shots'),('typewriter','native')]:
                if field in r:groups[phase][key].append(r[field])
    phase=None
    for line in Path('run/mctest/Test/logs/latest.log').read_text().splitlines():
        mark=re.search(r'typingcase:(\S+)',line)
        if mark:phase=mark[1] if mark[1] in groups else None
        if phase and '[CockpitTrace]'in line:
            v={k:float(a)for k,a in re.findall(r'(\w+)=([-+\d.E]+)',line)};v['shown']='shown=true'in line;v['typing']='typing=true'in line
            for hand in ['right','left']:v[hand+'Target']=list(map(float,re.search(hand+r'Target=\(([^)]*)\)',line)[1].split()))
            groups[phase]['trace'].append(v)
    summary={};healthy=0;contact_errors=[]
    for name,g in groups.items():
        t=g['trace'];assert t,name
        healthy+=len(g['states']);assert all(s['gameMode']=='survival' and s['health']==20 and s['food']==20 and s['hurtTime']==0 for s in g['states']),name
        assert all(v['rightMix']==0 or v['leftMix']==0 for v in t),name
        if name!='dismount':assert all(v['shown'] and min(v['rightWeight'],v['leftWeight'])>.99 for v in t),name
        else:assert not t[-1]['shown'];continue
        assert all(.499<=v['rimRight']<=.55 and .499<=v['rimLeft']<=.55 for v in t),name
        active=name.startswith(('left-','right-')) and not name.endswith('return')
        if active:
            hand=0 if name.startswith('right')else 1
            assert all(v['typing'] and v['request']==hand for v in t),name
            assert t[-1]['rightMix'if hand==0 else 'leftMix']==1,name
            assert all(n['mode']=='ACTIVE' for n in g['native']),name
        if '-key-'in name:
            code=int(name.rsplit('-',1)[-1]);index={81:0,65:6,32:13}[code]
            assert all(index in n['pressed'] for n in g['native']),(name,g['native'])
            assert t[-1]['key']==index,name
        if '-linger-'in name:assert t[-1]['key']>=0,name
        if name.endswith(('look-away','ready')):assert t[-1]['key']==-1,name
        if name.endswith('return')or name=='removed':assert t[-1]['rightMix']==t[-1]['leftMix']==0,name
        model=g['models'][-1]['parts'];last=t[-1]
        gaps={h:math.dist(palm(model[h+'_arm']),last[h+'Target'])/16 for h in ['right','left']}
        if max(gaps.values())>=.13:contact_errors.append({'case':name,'palmGapBlocks':gaps})
        if active:
            rim='left' if hand==0 else 'right'
            if not all(math.dist(palm(m['parts'][rim+'_arm']),last[rim+'Target'])/16<.13 for m in g['models']):contact_errors.append({'case':name,'error':'Rim contact lost during key movement'})
        # Allow the existing FA seated idle sway (baseline reaches .3 model pixels).
        for leg in ['right_leg','left_leg']:
            first=g['models'][0]['parts'][leg]
            assert all(math.dist(m['parts'][leg]['pos'],first['pos'])<.4 for m in g['models']),name
        summary[name]={'frames':len(g['models']),'palmGapBlocks':gaps,'key':last['key'],'rightMix':last['rightMix'],'leftMix':last['leftMix']}
    assert healthy==336,healthy
    summary['contactErrors']=contact_errors;summary['healthySamples']=healthy;(ROOT/'verified.json').write_text(json.dumps(summary,indent=2));shutil.copyfile('run/mctest/Test/logs/latest.log',ROOT/'game.log')
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for g in groups.values():
        for src in g['shots']:shutil.copyfile(src,shots/Path(src).name)
    for side in ['right','left']:
        clip=shots/side;clip.mkdir(exist_ok=True);i=0
        for name,g in groups.items():
            if name.startswith(side):
                for src in g['shots']:shutil.copyfile(src,clip/f'{i:03}.png');i+=1
        subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(clip/'%03d.png'),'-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/(side+'.mp4'))],check=True)
    html='<!doctype html><meta charset="utf-8"><title>Руль и машинка</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1150px;margin:24px auto}video{width:100%;border-radius:12px}img{width:48%;margin:1%}</style><h1>Пишущая машинка сбоку от руля</h1><p>FA+Player · полная сборка Test · здоровое выживание · GUI скрыт. Штатная активация машинки на клиенте/сервере, привязанные Q/A/Space, настоящие события клавиш и пакеты. Никакого подменённого состояния анимации.</p><p>Монтаж: ожидание → Q → A → пробел → взгляд назад → возврат на руль. Каждый фрагмент 1×, 20 кадров/с; паузы между фрагментами вырезаны.</p>'
    for side,label in [('right','Справа'),('left','Слева')]:html+=f'<h2>{label}</h2><video controls loop muted src="{side}.mp4"></video>'
    for name,g in groups.items():
        src=Path(g['shots'][-1]).name;html+=f'<a href="shots/{src}" title="{name}"><img src="shots/{src}"></a>'
    html+='<p><a href="verified.json">Измерения контактов и проверенные сценарии</a></p>'; (ROOT/'index.html').write_text(html)
    assert not contact_errors,contact_errors
    return summary
if __name__=='__main__':
    import argparse
    parser=argparse.ArgumentParser();parser.add_argument('--output',type=Path,default=ROOT);args=parser.parse_args();ROOT=args.output
    print(json.dumps(verify(),indent=2))
