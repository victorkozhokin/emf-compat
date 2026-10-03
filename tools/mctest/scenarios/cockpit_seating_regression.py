"""Native Create seats at different heights, reachable footrests and free camera."""
import sys,json,math,shutil,subprocess
from pathlib import Path
sys.path.insert(0,str(Path(__file__).resolve().parents[1]));import mctest
from cockpit_regression import fixture
ROOT=Path('build/cockpit-seating-review')
def main():
    mctest.wait_ready('Test',60);steps=[];rows=[];errors=[];summary={}
    def run(s):
        r=mctest.run_steps('Test',s,timeout=120);offset=len(steps)
        for row in r['results']:row['step']+=offset
        steps.extend(s);rows.extend(r['results']);errors.extend(row for row in r['results']if'error'in row)
        return r['results']
    for name,block,rest in [('cushion','create:red_seat',False),('cushion-footrest','create:red_seat',True)]:
        run(fixture()+[{'cmd':'ride @s dismount'},{'cmd':'setblock 370 150 7 air'},{'wait':5},{'cmd':f'setblock 370 150 7 {block}'},{'cmd':'setblock 369 150 7 air'},{'cmd':'setblock 371 150 7 air'}]+([{'cmd':'setblock 370 150 6 smooth_stone_slab[type=bottom]'}]if rest else[])+[{'cmd':'tp @s 370.5 150 8.2 180 60'},{'look':[180,60]},{'wait':10},{'click':'use'},{'wait':20},{'look':[180,32]},{'wait':20},{'click':'use'},{'orbit':[90,10,3]},{'wait':20}])
        for yaw in [180,90,-90,180]:
            s=[{'look':[yaw,22]}]
            for i in range(20):s +=[{'wait':1},{'state':True},{'model':'player'},{'screenshot':f'seat-{name}-{yaw}-{i:02}'}]
            out=run(s);models=[r['model']for r in out if'model'in r];states=[r['state']for r in out if'state'in r]
            snapshots=[m.get('cockpit',{})for m in models];key=f'{name}-{yaw}-{len(summary)}'
            shown=all(c.get('shown')for c in snapshots)
            if not shown:errors.append({'case':key,'error':'native seated wheel pose unavailable'})
            if shown:
                gaps={h:max(c[h+'Gap']for c in snapshots)for h in ['right','left']}
                if max(gaps.values())>=.13:errors.append({'case':key,'error':'palm contact','gap':gaps})
                if max(c['seatGap']for c in snapshots)>=.03:errors.append({'case':key,'error':'seat anchor'})
                if any(max(c['rightSeatPenetration'],c['leftSeatPenetration'])>.02 for c in snapshots):errors.append({'case':key,'error':'rendered leg volume penetrates chair'})
                if any(abs(c['pelvisAboveSeat']-2.25/16)>.03 for c in snapshots[-8:]):errors.append({'case':key,'error':'pelvis is below or above seat cushion'})
                if any(abs(c['rightThighPitch']+math.pi/2)>.05 or abs(c['leftThighPitch']+math.pi/2)>.05 for c in snapshots):errors.append({'case':key,'error':'standing thighs while seated'})
                if max(abs(c['facingErrorDegrees'])for c in snapshots)>.1:errors.append({'case':key,'error':'free camera changes facing'})
                summary[key]={'maximumPalmGapBlocks':gaps,'seatGapBlocks':max(c['seatGap']for c in snapshots),'nativePlayerY':states[-1]['pos'][1],'maximumSeatPenetrationBlocks':max(max(c['rightSeatPenetration'],c['leftSeatPenetration'])for c in snapshots),'pelvisAboveSeatBlocks':snapshots[-1]['pelvisAboveSeat'],'thighPitchDegrees':[math.degrees(snapshots[-1]['rightThighPitch']),math.degrees(snapshots[-1]['leftThighPitch'])]}
            if not all(s['gameMode']=='survival'and s['health']==20 and s['food']==20 and s['hurtTime']==0 for s in states):errors.append({'case':key,'error':'unhealthy survival'})
    ROOT.mkdir(parents=True,exist_ok=True);(ROOT/'steps.json').write_text(json.dumps(steps));(ROOT/'results.json').write_text(json.dumps({'results':rows}));summary['errors']=errors;(ROOT/'verified.json').write_text(json.dumps(summary,indent=2))
    shots=ROOT/'shots';shots.mkdir(exist_ok=True)
    for i,r in enumerate(x for x in rows if'screenshot'in x):shutil.copyfile(r['screenshot'],shots/f'{i:04}.png')
    subprocess.run(['/opt/homebrew/bin/ffmpeg','-hide_banner','-loglevel','error','-y','-framerate','20','-i',str(shots/'%04d.png'),'-c:v','libx264','-preset','fast','-crf','20','-pix_fmt','yuv420p','-movflags','+faststart',str(ROOT/'seats.mp4')],check=True)
    (ROOT/'index.html').write_text('<!doctype html><meta charset="utf-8"><title>Посадка и опора ног</title><style>body{background:#151b22;color:#eef2f7;font:17px system-ui;max-width:1150px;margin:24px auto}video{width:100%}</style><h1>Подушка Create и подставка</h1><p>Подушка Create без подставки и со ступенью для ног. Выживание, GUI скрыт. Повороты камеры при удержании руля; таз над настоящей подушкой сиденья, сидячее положение бёдер. Проверяется пересечение объёма ног с креслом; ступни жёсткой ноги не притягиваются к полу через подушку.</p><video controls loop muted src="seats.mp4"></video><p><a href="verified.json">Измерения</a></p>')
    print(json.dumps(summary,indent=2));assert not errors,errors
if __name__=='__main__':main()
