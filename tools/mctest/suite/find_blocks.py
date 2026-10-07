"""Finds blocks in a saved world by name: find_blocks.py <world dir> <x1> <z1> <x2> <z2> <substring> [y1 y2].
Reads the region files directly (run `save-all flush` first). Prints x y z name{properties}."""
import sys, zlib, struct, io
from pathlib import Path

def nbt(buf):
    def r(fmt, n):
        return struct.unpack(fmt, buf.read(n))[0]
    def name():
        return buf.read(r('>H', 2)).decode('utf8', 'replace')
    def payload(t):
        if t == 1: return r('>b', 1)
        if t == 2: return r('>h', 2)
        if t == 3: return r('>i', 4)
        if t == 4: return r('>q', 8)
        if t == 5: return r('>f', 4)
        if t == 6: return r('>d', 8)
        if t == 7: return buf.read(r('>i', 4))
        if t == 8: return name()
        if t == 9:
            inner, n = r('>b', 1), r('>i', 4)
            return [payload(inner) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                k = r('>b', 1)
                if k == 0: return out
                key = name()
                out[key] = payload(k)
        if t == 11:
            n = r('>i', 4); return list(struct.unpack('>%di' % n, buf.read(4 * n)))
        if t == 12:
            n = r('>i', 4); return list(struct.unpack('>%dq' % n, buf.read(8 * n)))
        raise ValueError(t)
    t = r('>b', 1); name()
    return payload(t)

def chunks(path):
    data = path.read_bytes()
    for i in range(1024):
        off = struct.unpack('>I', data[i * 4:i * 4 + 4])[0]
        if off == 0: continue
        at = (off >> 8) * 4096
        n, kind = struct.unpack('>IB', data[at:at + 5])
        if kind != 2: continue
        yield nbt(io.BytesIO(zlib.decompress(data[at + 5:at + 4 + n])))

def main():
    world = Path(sys.argv[1]); x1, z1, x2, z2 = map(int, sys.argv[2:6]); want = sys.argv[6]
    y1, y2 = (int(sys.argv[7]), int(sys.argv[8])) if len(sys.argv) > 8 else (-64, 320)
    for rx in range(x1 >> 9, (x2 >> 9) + 1):
        for rz in range(z1 >> 9, (z2 >> 9) + 1):
            path = world / 'region' / f'r.{rx}.{rz}.mca'
            if not path.exists(): continue
            for c in chunks(path):
                cx, cz = c['xPos'], c['zPos']
                if cx * 16 > x2 or cx * 16 + 15 < x1 or cz * 16 > z2 or cz * 16 + 15 < z1: continue
                for s in c.get('sections', []):
                    states = s.get('block_states')
                    if not states: continue
                    pal = states['palette']
                    if not any(want in p['Name'] for p in pal): continue
                    longs = states.get('data')
                    bits = max(4, (len(pal) - 1).bit_length())
                    per = 64 // bits
                    for i in range(4096):
                        idx = 0 if not longs else (longs[i // per] & 0xFFFFFFFFFFFFFFFF) >> (i % per * bits) & ((1 << bits) - 1)
                        p = pal[idx]
                        if want not in p['Name']: continue
                        x, y, z = cx * 16 + (i & 15), s['Y'] * 16 + (i >> 8), cz * 16 + (i >> 4 & 15)
                        if x1 <= x <= x2 and z1 <= z <= z2 and y1 <= y <= y2:
                            props = ','.join(f'{k}={v}' for k, v in p.get('Properties', {}).items())
                            print(x, y, z, p['Name'] + ('{' + props + '}' if props else ''))

main()
