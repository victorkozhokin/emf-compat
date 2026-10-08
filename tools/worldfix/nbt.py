"""A small reader and writer of Minecraft's NBT (gzipped, big-endian): enough to edit a level.dat.

Values come back as (type id, value) pairs so that what is written is what was read."""
import gzip, struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTES, STRING, LIST, COMPOUND, INTS, LONGS = range(13)
_FMT = {BYTE: ">b", SHORT: ">h", INT: ">i", LONG: ">q", FLOAT: ">f", DOUBLE: ">d"}


class _In:
    def __init__(self, data):
        self.d, self.i = data, 0

    def take(self, n):
        out = self.d[self.i:self.i + n]
        self.i += n
        return out

    def num(self, fmt):
        return struct.unpack(fmt, self.take(struct.calcsize(fmt)))[0]

    def text(self):
        return self.take(self.num(">H")).decode("utf-8", "surrogatepass")


def _read(src, kind):
    if kind in _FMT:
        return src.num(_FMT[kind])
    if kind == BYTES:
        return src.take(src.num(">i"))
    if kind == STRING:
        return src.text()
    if kind == LIST:
        of, n = src.num(">b"), src.num(">i")
        return (of, [_read(src, of) for _ in range(n)])
    if kind == COMPOUND:
        out = {}
        while True:
            t = src.num(">b")
            if t == END:
                return out
            name = src.text()
            out[name] = (t, _read(src, t))
    if kind == INTS:
        n = src.num(">i")
        return list(struct.unpack(f">{n}i", src.take(4 * n)))
    if kind == LONGS:
        n = src.num(">i")
        return list(struct.unpack(f">{n}q", src.take(8 * n)))
    raise ValueError(f"unknown tag {kind}")


def _text(s):
    b = s.encode("utf-8", "surrogatepass")
    return struct.pack(">H", len(b)) + b


def _write(kind, v):
    if kind in _FMT:
        return struct.pack(_FMT[kind], v)
    if kind == BYTES:
        return struct.pack(">i", len(v)) + v
    if kind == STRING:
        return _text(v)
    if kind == LIST:
        of, items = v
        return struct.pack(">bi", of, len(items)) + b"".join(_write(of, x) for x in items)
    if kind == COMPOUND:
        return b"".join(struct.pack(">b", t) + _text(k) + _write(t, x) for k, (t, x) in v.items()) + b"\x00"
    if kind == INTS:
        return struct.pack(f">i{len(v)}i", len(v), *v)
    if kind == LONGS:
        return struct.pack(f">i{len(v)}q", len(v), *v)
    raise ValueError(f"unknown tag {kind}")


def load(path):
    src = _In(gzip.open(path, "rb").read())
    kind = src.num(">b")
    name = src.text()
    return name, _read(src, kind)


def save(path, name, root):
    with gzip.open(path, "wb") as out:
        out.write(struct.pack(">b", COMPOUND) + _text(name) + _write(COMPOUND, root))
