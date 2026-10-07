"""Decision lines of two suite runs, scenario by scenario: cmp.py <before> <after>."""
import re, sys, collections
from pathlib import Path
S = Path(__file__).resolve().parents[3] / 'build' / 'suite'
def load(label):
    out = collections.OrderedDict()
    for phase in ('atlas', 'test', 'parcool'):
        cur = None
        for line in (S / label / f'{phase}.log').read_text(errors='replace').splitlines():
            m = re.search(r'\[mctest\] suite(-end)? (.+)$', line)
            if m: cur = None if m.group(1) else m.group(2); out.setdefault(cur, []) if cur else None; continue
            if cur is None: continue
            m = re.search(r'\[EMFCompat\w*/?\]: (\[\w+\] .*)$', line)
            if not m: continue
            t = m.group(1)
            if '=' in t or 'Trace' in t or re.search(r'\d\.\d', t) or t.startswith('[Hands]'): continue
            out[cur].append(t.replace('Leash', 'Lead'))
    return out
a, b = load(sys.argv[1] if len(sys.argv) > 2 else 'old'), load(sys.argv[2] if len(sys.argv) > 2 else 'new')
tot = [0, 0, 0]; rows = []
for k in a:
    x, y = collections.Counter(a[k]), collections.Counter(b.get(k, []))
    minus, plus = x - y, y - x
    tot[0] += sum(x.values()); tot[1] += sum(minus.values()); tot[2] += sum(plus.values())
    if minus or plus: rows.append((sum(minus.values()) + sum(plus.values()), k, sum(x.values()), minus, plus))
print('scenarios', len(a), 'decision lines old', tot[0], 'only-old', tot[1], 'only-new', tot[2], 'differing scenarios', len(rows))
for n, k, total, minus, plus in sorted(rows, key=lambda r: -r[0]):
    def fam(c): return dict(collections.Counter({re.sub(r'^(\[\w+\]) \S+ ', r'\1 ', t): v for t, v in c.items()}).most_common(5))
    print(f'-- {k}: {total} lines, -{sum(minus.values())} +{sum(plus.values())}'); print('   old only:', fam(minus)); print('   new only:', fam(plus))
