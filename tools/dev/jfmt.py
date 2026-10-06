"""Whitespace-only tidy of densely written Java: spaces round assignment and comparison operators,
after commas and keywords, before an opening brace; one statement to a line. Literals and comments
are left alone, and the result is checked to differ from the source in whitespace only."""
import re, sys

OPS = r">>>=|<<=|>>=|==|!=|<=|>=|&&|\|\||\+=|-=|\*=|/=|%=|&=|\|=|\^=|->|="


def segments(src):
    """(is_code, text) pieces: code apart from string/char literals, text blocks and comments."""
    out, i, n, start = [], 0, len(src), 0
    while i < n:
        c = src[i]
        two = src[i:i + 2]
        if two == "//":
            j = src.find("\n", i)
            j = n if j < 0 else j
        elif two == "/*":
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
        elif src[i:i + 3] == '"""':
            j = src.find('"""', i + 3)
            j = n if j < 0 else j + 3
        elif c == '"' or c == "'":
            j = i + 1
            while j < n and src[j] != c:
                j += 2 if src[j] == "\\" else 1
            j += 1
        else:
            i += 1
            continue
        if start < i:
            out.append((True, src[start:i]))
        out.append((False, src[i:j]))
        i = start = j
    if start < n:
        out.append((True, src[start:]))
    return out


NUM = re.compile(r"(?<![\w.])(?:\d[\w.]*|\.\d[\w]*)(?:(?<=[eE])[+-]\d\w*)?")


def angles(code):
    """Spaces round < and > where they compare; the brackets of a generic type are left alone."""
    out, open_generics, i, n = [], 0, 0, len(code)
    while i < n:
        c = code[i]
        if code[i:i + 3] == ">>>" or code[i:i + 2] in ("<<", ">>", "<=", ">=", "->"):
            width = 3 if code[i:i + 3] == ">>>" else 2
            out.append(code[i:i + width])
            i += width
            continue
        if c == "<":
            before = re.search(r"([A-Za-z_][\w]*)?\s*$", code[:i]).group(1)
            after = code[i + 1:i + 2]
            generic = (before is not None and (before[0].isupper() or before in ("static", "public", "private", "protected", "final"))
                       or code[:i].rstrip().endswith(".")) and (after.isalpha() or after in "?>")
            if generic:
                open_generics += 1
                out.append(c)
            else:
                while out and out[-1] in " \t":
                    out.pop()
                out.append(" < ")
                i += 1
                while i < n and code[i] in " \t":
                    i += 1
                continue
        elif c == ">":
            if open_generics:
                open_generics -= 1
                out.append(c)
            else:
                while out and out[-1] in " \t":
                    out.pop()
                out.append(" > ")
                i += 1
                while i < n and code[i] in " \t":
                    i += 1
                continue
        else:
            if c in ";{}":
                open_generics = 0
            out.append(c)
        i += 1
    return "".join(out)


def arithmetic(code):
    """Spaces round a binary + - * / %: after a name, a number or a closing bracket, never a sign."""
    kept = []

    def keep(m):
        kept.append(m.group(0))
        return "\x00%d\x00" % (len(kept) - 1)

    code = NUM.sub(keep, code)
    code = re.sub(r"(?<=[\w)\]\x00])[ \t]*(?<![+\-*/])([*/%]|\+(?![+=])|-(?![\-=>]))[ \t]*(?=[\w(.\x00!~+\-])",
                  lambda m: " " + m.group(1) + " ", code)
    code = re.sub(r"\bimport ([\w.]+) \* ;", r"import \1*;", code)
    code = re.sub(r"(\w)\. \* ;", r"\1.*;", code)
    code = re.sub(r"\b(return|case|throw|else|yield) ([+-]) (?=[\w(.\x00])", r"\1 \2", code)
    return re.sub(r"\x00(\d+)\x00", lambda m: kept[int(m.group(1))], code)


def tidy(code):
    code = re.sub(r"(?<=\S)[ \t]*(%s)[ \t]*(?=\S)" % OPS, lambda m: " " + m.group(1) + " ", code)
    code = re.sub(r"(?m)^([ \t]*)(%s)[ \t]*(?=\S)" % OPS, lambda m: m.group(1) + m.group(2) + " ", code)
    code = re.sub(r",(?=[^\s])", ", ", code)
    code = re.sub(r"\b(if|for|while|switch|catch|synchronized)\(", r"\1 (", code)
    code = re.sub(r"\)\{", ") {", code)
    code = re.sub(r"\)(?=[A-Za-z_])", ") ", code)
    code = re.sub(r"\b(else|try|finally|do)\{", r"\1 {", code)
    code = re.sub(r"\}(else|catch|finally|while)\b", r"} \1", code)
    code = re.sub(r";(?=[^\s})])", "; ", code)
    code = re.sub(r"(\)|\belse|\btry|\bfinally|->) \{[ \t]*([^{}\n]*;)[ \t]*\}", r"\1 { \2 }", code)
    return arithmetic(angles(code))


def colons(line_code):
    """Spaces round the colon of a ternary or a for-each, never a label's or a method reference's."""
    if re.match(r"\s*(case\b|default\b)", line_code):
        return line_code
    ternary = re.search(r"(?<![<,(\s])\s*\?(?!\s*[>,)])|[^<,\s(]\s+\?\s", line_code) is not None
    foreach = re.search(r"\bfor \([^;]*$|\bfor \([^;()]*\)", line_code) is not None
    if not (ternary or foreach):
        return line_code
    if ternary:
        line_code = re.sub(r"(?<=[^\s<,(])[ \t]*\?[ \t]*(?=[^\s>,)])", " ? ", line_code)
    return re.sub(r"(?<![:\s])[ \t]*:[ \t]*(?![:\s])", " : ", line_code)


ATOM = re.compile("\x01(\\d+)\x01")


def split_statements(line, kinds):
    """`a; b;` at statement level becomes two lines; a for header and an inline block stay whole."""
    indent = re.match(r"[ \t]*", line).group(0)
    depth = brace = 0
    cuts = []
    for k, ch in enumerate(line):
        if ch in "([":
            depth += 1
        elif ch in ")]":
            depth -= 1
        elif ch == "{":
            brace += 1
        elif ch == "}":
            brace -= 1
        elif ch == ";" and depth == 0 and brace == 0:
            cuts.append(k + 1)
    if not cuts:
        return [line]
    out, start = [], 0
    for cut in cuts:
        out.append(line[start:cut])
        start = cut
    rest = line[start:]
    m = ATOM.fullmatch(rest.strip())
    if rest.strip() and not (m and kinds[int(m.group(1))] == "comment"):
        out.append(rest)
    elif rest.strip():
        out[-1] += rest
    if len(out) < 2 or any(p.count("{") != p.count("}") for p in out):
        return [line]
    return [out[0].rstrip()] + [indent + p.strip() for p in out[1:]]


def squeeze(text):
    return "".join(t if not code else re.sub(r"\s+", "", t) for code, t in segments(text))


def fmt(src):
    pieces = segments(src)
    kept, kinds, text = [], [], []
    for code, t in pieces:
        if code:
            text.append(t)
        else:
            kept.append(t)
            kinds.append("comment" if t.startswith("/") else "literal")
            text.append("\x01%d\x01" % (len(kept) - 1))
    body = tidy("".join(text))
    lines = []
    for line in body.split("\n"):
        lines += split_statements(colons(line), kinds)
    out = "\n".join(l.rstrip() if l.strip() else "" for l in lines)
    out = ATOM.sub(lambda m: kept[int(m.group(1))], out)
    assert squeeze(out) == squeeze(src), "not a whitespace-only change"
    return out


if __name__ == "__main__":
    changed = 0
    for path in sys.argv[1:]:
        src = open(path, encoding="utf-8").read()
        try:
            out = fmt(src)
        except AssertionError as e:
            print("SKIP", path, e)
            continue
        if out != src:
            open(path, "w", encoding="utf-8").write(out)
            changed += 1
    print(changed, "files changed of", len(sys.argv) - 1)
