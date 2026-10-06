"""Compare archived native left-foot inspection recordings at actual capture times."""
import concurrent.futures, html, json, math, subprocess
from pathlib import Path
ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / 'build/inspection-turn-review'
OUT.mkdir(exist_ok=True)
CASES = {'boots-standing': 'Ботинки', 'leggings-standing': 'Поножи', 'boots-crouch': 'Ботинки в присяде'}
jobs, checks, metrics = [], {}, {}
for tag, directory in [('before', 'gesture-polish-edges'), ('after', 'inspection-turn-after')]:
    source = ROOT / 'build' / directory
    steps = json.loads((source / 'steps.json').read_text())
    rows = json.loads((source / 'results.json').read_text())['results']
    checks[tag + '_driver'] = not any('error' in row for row in rows)
    for name in CASES:
        frames = [r for r in rows if 'screenshot' in r and Path(r['screenshot']).name.startswith('edge-' + name + '-right-')]
        last = frames[-1]['step']
        first = max(i for i, s in enumerate(steps[:frames[0]['step']]) if 'log' in s)
        samples = [r for r in rows if first <= r['step'] <= last]
        models = [r['model'] for r in samples if 'model' in r]
        states = [r['state'] for r in samples if 'state' in r]
        checks[tag + '_' + name + '_health'] = bool(states) and all(s['health'] == 20 and s['food'] == 20 and s['hurtTime'] == 0 and s['gameMode'] == 'survival' for s in states)
        checks[tag + '_' + name + '_finite'] = bool(models) and all(math.isfinite(v) for m in models for p in m['parts'].values() for k in ['pos', 'rot'] for v in p.get(k, []))
        checks[tag + '_' + name + '_owner'] = any(v.get('owner') == 'ArmorDon' for m in models for v in m.get('interaction', {}).values())
        legs = [m['parts']['left_leg']['rot'] for m in models]
        metrics.setdefault(name, {})[tag] = {'samples': len(models), 'left_leg_yaw_range_degrees': [round(min(p[1] for p in legs) * 180 / math.pi, 2), round(max(p[1] for p in legs) * 180 / math.pi, 2)]}
        lines = []
        for i, frame in enumerate(frames):
            path = source / 'shots' / Path(frame['screenshot']).name
            duration = (frames[i + 1]['captureNanos'] - frame['captureNanos']) * 1e-9 if i + 1 < len(frames) else .1
            lines += [f"file '{path}'", f'duration {max(.001, duration):.9f}']
        lines.append(f"file '{path}'")
        concat = OUT / f'{tag}-{name}.txt'
        concat.write_text('\n'.join(lines) + '\n')
        jobs.append((concat, OUT / f'{tag}-{name}.mp4'))
def encode(job):
    concat, target = job
    subprocess.run(['ffmpeg', '-hide_banner', '-loglevel', 'error', '-y', '-f', 'concat', '-safe', '0', '-i', str(concat), '-vf', 'scale=1280:-2', '-fps_mode', 'vfr', '-c:v', 'libx264', '-preset', 'fast', '-crf', '23', '-pix_fmt', 'yuv420p', '-movflags', '+faststart', str(target)], check=True)
with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
    list(pool.map(encode, jobs))
report = {'checks': checks, 'metrics': metrics, 'note': 'Окончательная поза FA+Player после всех слоёв. Диапазон yaw включает исходную стойку и подшаг. Запись ~10 кадров/с по фактическим captureNanos; не проверяет плавность каждого render-кадра.'}
(OUT / 'metrics.json').write_text(json.dumps(report, ensure_ascii=False, indent=2))
body = ['<!doctype html><html lang="ru"><meta charset="utf-8"><title>Осмотр ноги · два поворота</title><style>body{background:#151b22;color:#e4ecf3;font:16px system-ui;max-width:1500px;margin:32px auto;padding:20px}.pair{display:grid;grid-template-columns:1fr 1fr;gap:16px}video{width:100%}article{border-top:1px solid #456;padding:18px 0}button{padding:10px}pre{white-space:pre-wrap}</style><h1>Осмотр ботинок и поножей</h1><p>Поворот → короткая пауза с лёгким движением → второй поворот → пауза → плавное возвращение в стойку. Только левая нога; опущенная голова сохранена. Длительность 2.6 → 3.2 секунды.</p><p>Одинаковые действия и камера, GUI скрыт, здоровое выживание. Кадры записаны примерно 10 раз/с по фактическому времени.</p>']
for name, title in CASES.items():
    body.append(f'<article><h2>{title}</h2><button onclick="this.closest(\'article\').querySelectorAll(\'video\').forEach(v=>{{v.currentTime=0;v.play()}})">Сначала / воспроизвести обе</button><div class="pair">')
    for tag, label in [('before', 'До · 1ffa5a0'), ('after', 'После')]:
        body.append(f'<div><h3>{label}</h3><video controls loop preload="metadata" src="{tag}-{name}.mp4"></video></div>')
    body.append('</div></article>')
body.append('<details><summary>Проверки и замеры</summary><pre>' + html.escape(json.dumps(report, ensure_ascii=False, indent=2)) + '</pre></details></html>')
(OUT / 'index.html').write_text('\n'.join(body))
print(json.dumps(report, ensure_ascii=False))
if not all(checks.values()):
    raise SystemExit(1)
