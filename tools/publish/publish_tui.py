#!/usr/bin/env python3
"""Interactive publisher to Modrinth and CurseForge over the map in projects.json.

    python3 tools/publish/publish_tui.py

1. Tokens: pasted into masked fields (Tab switches) and checked; kept in memory only. A field left
   empty leaves that site out.
2. State: what each site already has, per addon. The core comes first: every addon asks for it.
3. An addon: its versions with a tick per site, the changelog of the one under the cursor and its
   dependencies on both sites.
4. Confirmation: everything ticked, in upload order; Y sends it, one file at a time.

Keys are shown at the bottom of every screen. Nothing is uploaded before the confirmation.
"""

from __future__ import annotations

import curses
import json
import os
import subprocess
import textwrap
import time
import urllib.error
import urllib.request

import curseforge as cf
import publish as mr

NEW, PUBLISHED, PROBLEM, OFF, NO_PROJECT = "new", "up to date", "problem", "off", "no project"
SITES = ("Modrinth", "CurseForge")
# The sites signed in to; every screen shows only these.
ACTIVE: list[str] = list(SITES)
# What the sign-in could not settle, shown on the list as well (yellow).
NOTICES: list[str] = []
COLOUR = {NEW: 1, PUBLISHED: 2, PROBLEM: 3, OFF: 2, NO_PROJECT: 3}


class Version:
    def __init__(self, mod: dict, project: dict):
        self.mod, self.project = mod, project
        self.jar = mr.ROOT / mod["file"]
        self.changelog = mr.changelog_section(mr.ROOT / mod["changelog"], mod["version_number"])
        problem = ""
        if not self.jar.is_file() or self.jar.stat().st_size == 0:
            problem = f'no jar at {mod["file"]} - build first'
        elif self.changelog is None:
            problem = f'no "## {mod["version_number"]}" section in {mod["changelog"]}'
        # Per site: state, tick and why it cannot go.
        self.state = {s: PROBLEM if problem else NEW for s in SITES}
        self.why = {s: problem for s in SITES}
        self.tick = {s: False for s in SITES}
        # The newest version of this build (loader + game version) each site has now, whatever its number.
        self.on_site = {s: None for s in SITES}
        # A site the addon has no project on yet: nothing to upload to until one is made there.
        if not mod.get("project_id"):
            self.state["Modrinth"], self.why["Modrinth"] = NO_PROJECT, "no Modrinth project yet"
        if not project.get("cf_id"):
            self.state["CurseForge"], self.why["CurseForge"] = NO_PROJECT, "no CurseForge project yet"

    @property
    def label(self) -> str:
        return f'{self.mod["loader"]:9}{self.mod["game_version"]:9}{self.mod["version_number"]}'


class Addon:
    def __init__(self, key: str, versions: list[Version]):
        # Oldest game version on top: 1.20.1 … 26.3; one version's loaders in a fixed order under it.
        self.key = key
        self.versions = sorted(versions, key=lambda v: (
            tuple(int(p) for p in v.mod["game_version"].split(".")),
            ("forge", "neoforge", "fabric").index(v.mod["loader"])))
        self.name = versions[0].mod["name"].rsplit(" ", 1)[0]
        self.short = self.name.removeprefix("EMF Compat: ")

    @property
    def unpublished(self) -> bool:
        """No project on any site."""
        return all(v.state[s] == NO_PROJECT for v in self.versions for s in ACTIVE)

    def count(self, site: str, state: str) -> int:
        return sum(v.state[site] == state for v in self.versions)

    def ticked(self) -> list[tuple[Version, str]]:
        return [(v, s) for v in self.versions for s in ACTIVE if v.tick[s]]


def put(win, y: int, x: int, text: str, attr: int = 0) -> None:
    """addstr that never raises at the window's edge."""
    h, w = win.getmaxyx()
    if 0 <= y < h and x < w - 1:
        try:
            win.addstr(y, x, text[: w - 1 - x], attr)
        except curses.error:
            pass


def footer(win, text: str) -> None:
    put(win, win.getmaxyx()[0] - 1, 0, text, curses.A_REVERSE)


def title(win, text: str) -> None:
    put(win, 0, 0, text, curses.A_BOLD)


def message(win, text: str) -> None:
    win.erase()
    title(win, "EMF Compat publisher")
    put(win, 2, 2, text)
    win.refresh()


# --- 1. tokens --------------------------------------------------------------------------------

def modrinth_user(token: str) -> str:
    req = urllib.request.Request(f"{mr.API}/user", headers={"Authorization": token, "User-Agent": mr.UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)["username"]


def read_key(win):
    """One key; an escape sequence a terminal wraps a paste in comes back as None, a lone Esc as Esc."""
    ch = win.get_wch()
    if ch != "\x1b":
        return ch
    win.nodelay(True)
    try:
        rest = win.get_wch()
    except curses.error:
        rest = None
    win.nodelay(False)
    if rest is None:
        return "\x1b"
    while rest != "~":
        rest = win.get_wch()
    return None


# Keychain entries (macOS `security`), the names a user creates them under by hand too:
#   security add-generic-password -a "$USER" -s emf-modrinth-token -w
KEYCHAIN = {"Modrinth": "emf-modrinth-token", "CurseForge": "emf-curseforge-token"}


def keychain_read(site: str) -> str:
    """The token stored for a site; empty when there is none or no Keychain at all."""
    try:
        r = subprocess.run(["security", "find-generic-password", "-s", KEYCHAIN[site], "-w"],
                           capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.TimeoutExpired):
        return ""
    return r.stdout.strip() if r.returncode == 0 else ""


def keychain_write(site: str, token: str) -> bool:
    """Stores a token, replacing an older one. It goes to `security` on stdin, never on its command line."""
    account = os.environ.get("USER", "emf-compat")
    command = f'add-generic-password -U -a "{account}" -s "{KEYCHAIN[site]}" -w "{token}"\n'
    try:
        r = subprocess.run(["security", "-i"], input=command, capture_output=True, text=True, timeout=15)
    except (OSError, subprocess.TimeoutExpired):
        return False
    return r.returncode == 0 and keychain_read(site) == token


def login(win) -> dict[str, str] | None:
    """Tokens of the sites to publish to, checked; None if the user left."""
    tokens = {s: keychain_read(s) for s in SITES}
    stored = dict(tokens)
    # A note per site: text and colour (2 plain, 1 good, 3 bad).
    notes = {s: ("from Keychain", 1) if tokens[s] else ("", 2) for s in SITES}
    # Which sites this run publishes to: a tick per site, Space toggles it. Only ticked sites are signed in.
    use = {s: bool(tokens[s]) for s in SITES}
    save, field = True, 0
    hints = {"Modrinth": "modrinth.com/settings/pats, scope: Create versions (+ Read user data to check it here)",
             "CurseForge": "CurseForge account settings - API Tokens"}
    curses.curs_set(1)
    while True:
        win.erase()
        title(win, "EMF Compat publisher")
        put(win, 2, 2, "Tick the sites to publish to (Space); paste a token where none came from Keychain.")
        for i, s in enumerate(SITES):
            y = 4 + i * 3
            put(win, y, 2, f"[{'x' if use[s] else ' '}] {s:11}", curses.A_BOLD if i == field else curses.A_DIM)
            put(win, y, 18, "•" * min(len(tokens[s]), 56) or "-", 0 if tokens[s] else curses.A_DIM)
            text, colour = notes[s]
            put(win, y + 1, 18, text or hints[s], curses.color_pair(colour) if text else curses.A_DIM)
        put(win, 11, 2, f"[{'x' if save else ' '}] Save new tokens to Keychain (Ctrl+K)")
        footer(win, " Tab: field  Space: site on/off  Enter: sign in  Ctrl+U: clear  Ctrl+K: save  Esc ")
        win.move(4 + field * 3, 18 + min(len(tokens[SITES[field]]), 56))
        win.refresh()
        ch = read_key(win)
        site = SITES[field]
        if ch is None:
            continue
        if ch == "\x1b":
            return None
        if ch in ("\t", curses.KEY_DOWN, curses.KEY_UP, curses.KEY_BTAB):
            field = 1 - field
        elif ch == "\x0b":
            save = not save
        elif ch == " ":
            use[site] = not use[site]
        elif ch in ("\n", "\r", curses.KEY_ENTER):
            if not any(use.values()):
                notes[site] = ("Tick at least one site (Space).", 3)
                continue
            if any(use[s] and not tokens[s] for s in SITES):
                for s in SITES:
                    if use[s] and not tokens[s]:
                        notes[s] = ("Ticked, but no token: paste one or untick the site.", 3)
                continue
            curses.curs_set(0)
            ok = {}
            for s in SITES:
                if not use[s]:
                    continue
                message(win, f"Checking the {s} token…")
                try:
                    if s == "Modrinth":
                        try:
                            notes[s] = (f"signed in as {modrinth_user(tokens[s])}", 1)
                        except urllib.error.HTTPError as e:
                            if e.code != 401:
                                raise
                            # Modrinth answers 401 alike for a bad token and for one without "Read user data",
                            # and a publishing token needs only "Create versions": it gets tried at the first upload.
                            notes[s] = ("not checked: the token cannot read the account; the first upload tries it", 4)
                            NOTICES.append("Modrinth token not checked (no Read user data scope) - the first upload tries it")
                    else:
                        cf.check_token(tokens[s])
                        notes[s] = ("accepted", 1)
                    ok[s] = tokens[s]
                except urllib.error.HTTPError as e:
                    notes[s], tokens[s] = (f"refused (HTTP {e.code})" + (" - the Keychain one is stale" if stored[s] else ""), 3), ""
                except OSError as e:
                    notes[s] = (f"no answer: {e}", 3)
            if len(ok) == sum(use.values()):
                if save:
                    for s, token in ok.items():
                        if token != stored[s]:
                            message(win, f"Saving the {s} token to Keychain…")
                            if not keychain_write(s, token):
                                notes[s] = ("accepted, but Keychain did not take it", 3)
                return ok
            curses.curs_set(1)
        elif ch in (curses.KEY_BACKSPACE, "\x7f", "\b"):
            tokens[site] = tokens[site][:-1]
        elif ch == "\x15":
            tokens[site], use[site] = "", False
            notes[site] = ("", 2)
        elif isinstance(ch, str) and ch.isprintable() and not ch.isspace():
            tokens[site] += ch
            use[site] = True
            notes[site] = ("", 2)


# --- 2. state ---------------------------------------------------------------------------------

def load_state(win, sites: dict[str, str]) -> tuple[list[Addon], list[str]]:
    data = json.loads(mr.MAP.read_text(encoding="utf-8"))
    order: dict[str, list[Version]] = {}
    # Every addon, published or not; the core first, addons with no project anywhere last.
    for mod in data["modules"]:
        order.setdefault(mod["addon"], []).append(Version(mod, data["projects"][mod["addon"]]))
    addons = [Addon(k, order[k]) for k in order]
    addons.sort(key=lambda a: (a.unpublished, a.key != "core", list(order).index(a.key)))
    mr_cache, cf_cache, errors = {}, {}, []
    for i, addon in enumerate(addons, 1):
        message(win, f"Reading the sites: {addon.name} ({i}/{len(addons)})")
        for v in addon.versions:
            for s in SITES:
                if v.state[s] == NO_PROJECT:
                    continue
                if s not in sites:
                    v.state[s], v.why[s] = OFF, "no token given"
                    continue
                if v.state[s] == PROBLEM:
                    continue
                try:
                    key = (v.mod["loader"], v.mod["game_version"], v.mod["version_number"])
                    if s == "Modrinth":
                        keys = mr.published_versions(v.mod["project_id"], mr_cache)
                    else:
                        keys = cf.published_keys(v.project["cf_id"], cf_cache)
                    there = key in keys
                    same_build = [k[2] for k in keys if k[:2] == key[:2]]
                    v.on_site[s] = max(same_build, key=number, default=None)
                except (urllib.error.URLError, OSError) as e:
                    v.state[s], v.why[s] = PROBLEM, f"could not read {s}: {e}"
                    errors.append(f"{addon.name} / {s}: {e}")
                    continue
                if there:
                    v.state[s] = PUBLISHED
                else:
                    # Ticked from the start only where projects.json says the addon goes out; the rest wait for a hand.
                    v.tick[s] = bool(v.mod["publish"])
    return addons, errors


def number(version: str) -> tuple:
    """A version number as something to compare: 2.10.0 after 2.9.1."""
    return tuple(int(p) if p.isdigit() else 0 for p in version.split("."))


def addon_list(win, addons: list[Addon], sites: dict[str, str], errors: list[str]) -> str:
    cursor = 0
    while True:
        win.erase()
        title(win, "EMF Compat publisher   sites: " + ", ".join(sites))
        put(win, 2, 2, f'{"":24}' + "".join(f"{s:^19}" for s in ACTIVE), curses.A_DIM)
        put(win, 3, 2, f'{"addon":24}' + f'{"new":>5}{"up to date":>11}{"":3}' * len(ACTIVE) + f'{"ticked":>7}', curses.A_DIM)
        for i, a in enumerate(addons):
            cells = []
            for s in ACTIVE:
                if a.count(s, NO_PROJECT) == len(a.versions):
                    cells.append(f'{"no project":>16}{"":3}')
                else:
                    problems = a.count(s, PROBLEM)
                    cells.append(f'{a.count(s, NEW):>5}{a.count(s, PUBLISHED):>11}{" !!" if problems else "":3}')
            line = f'{a.short:24}{"".join(cells)}{len(a.ticked()):>7}'
            attr = curses.A_REVERSE if i == cursor else 0
            if a.unpublished or any(a.count(s, PROBLEM) for s in ACTIVE):
                attr |= curses.color_pair(3)
            elif any(a.count(s, NEW) for s in ACTIVE):
                attr |= curses.color_pair(1)
            put(win, 4 + i, 2, line, attr)
        y = 5 + len(addons)
        put(win, y, 2, f"Ticked for upload: {sum(len(a.ticked()) for a in addons)} files   (!! - cannot go, see the addon)")
        put(win, y + 1, 2, "new / up to date: current builds missing from / already on the site", curses.A_DIM)
        for n in NOTICES:
            y += 1
            put(win, y, 2, n, curses.color_pair(4))
        for e in errors[:3]:
            y += 1
            put(win, y, 2, e, curses.color_pair(3))
        footer(win, " ↑↓: move  Space: tick addon  Enter: open  P: publish  R: reload  Q: quit ")
        win.refresh()
        ch = win.getch()
        if ch in (ord("q"), ord("Q"), 27):
            return "quit"
        if ch == curses.KEY_UP:
            cursor = max(0, cursor - 1)
        elif ch == curses.KEY_DOWN:
            cursor = min(len(addons) - 1, cursor + 1)
        elif ch in (10, 13, curses.KEY_ENTER, curses.KEY_RIGHT):
            addon_screen(win, addons[cursor], sites)
        elif ch == ord(" "):
            # The whole addon: anything ticked - untick it all; nothing ticked - tick every new build.
            a = addons[cursor]
            fill = not a.ticked()
            for v in a.versions:
                for s in ACTIVE:
                    v.tick[s] = fill and v.state[s] == NEW
        elif ch in (ord("p"), ord("P")):
            return "publish"
        elif ch in (ord("r"), ord("R")):
            return "reload"


# --- 3. one addon -----------------------------------------------------------------------------

def addon_screen(win, addon: Addon, sites: dict[str, str]) -> None:
    cursor, note = 0, ""

    def toggle(v: Version, site: str) -> None:
        if v.state[site] == NEW:
            v.tick[site] = not v.tick[site]

    while True:
        win.erase()
        h, w = win.getmaxyx()
        title(win, addon.name)
        put(win, 2, 2, "".join(f"{s:16}" for s in ACTIVE) + "version", curses.A_DIM)
        for i, v in enumerate(addon.versions):
            x = 2
            for s in ACTIVE:
                box = "[x]" if v.tick[s] else "[ ]" if v.state[s] == NEW else " - "
                attr = curses.color_pair(COLOUR[v.state[s]]) | (curses.A_REVERSE if i == cursor else 0)
                put(win, 3 + i, x, f"{box} {v.state[s]:11}", attr)
                x += 16
            put(win, 3 + i, x, v.label, curses.A_REVERSE if i == cursor else 0)
        v = addon.versions[cursor]
        y = 4 + len(addon.versions)
        put(win, y, 2, "On the site now: " + "   ".join(
            f"{s} {v.on_site[s] or '-'}" for s in ACTIVE if v.state[s] != NO_PROJECT), curses.A_DIM)
        y += 1
        put(win, y, 2, f"Jar: {v.mod['file']}", curses.A_DIM)
        for s in ACTIVE:
            if v.state[s] in (PROBLEM, NO_PROJECT):
                y += 1
                put(win, y, 2, f"{s}: {v.why[s]}", curses.color_pair(3))
        y += 2
        put(win, y, 2, f'{"Dependency":30}{"type":12}' + ("on CurseForge" if "CurseForge" in ACTIVE else ""), curses.A_BOLD)
        for d in v.mod["dependencies"]:
            y += 1
            line = f'{d["slug"]:28}{d["dependency_type"]:12}'
            lost = "CurseForge" in ACTIVE and not d.get("cf_slug")
            if "CurseForge" in ACTIVE:
                line += d.get("cf_slug") or "- left out: no CurseForge slug"
            put(win, y, 4, line, curses.color_pair(3) if lost else 0)
        y += 2
        put(win, y, 2, f'Changelog {v.mod["version_number"]}' + ("  (the same on both sites)" if len(ACTIVE) > 1 else ""), curses.A_BOLD)
        for para in (v.changelog or "").splitlines():
            for line in textwrap.wrap(para, max(20, w - 8)) or [""]:
                y += 1
                if y >= h - 2:
                    break
                put(win, y, 4, line)
        if note:
            put(win, h - 2, 2, note, curses.color_pair(3))
        footer(win, " Space: both  M/C: one site  A: all  N: none  U: on CF already  P: publish  Esc "
               if len(ACTIVE) > 1 else " Space: tick  A: all new  N: none  U: already there  P: publish  Esc: back ")
        win.refresh()
        ch = win.getch()
        note = ""
        if ch in (27, curses.KEY_LEFT, ord("q"), ord("Q")):
            return
        if ch == curses.KEY_UP:
            cursor = max(0, cursor - 1)
        elif ch == curses.KEY_DOWN:
            cursor = min(len(addon.versions) - 1, cursor + 1)
        elif ch == ord(" "):
            for s in ACTIVE:
                toggle(v, s)
        elif ch in (ord("m"), ord("M")) and "Modrinth" in ACTIVE:
            toggle(v, "Modrinth")
        elif ch in (ord("c"), ord("C")) and "CurseForge" in ACTIVE:
            toggle(v, "CurseForge")
        elif ch in (ord("a"), ord("A")):
            for x in addon.versions:
                for s in ACTIVE:
                    x.tick[s] = x.state[s] == NEW
        elif ch in (ord("n"), ord("N")):
            for x in addon.versions:
                for s in ACTIVE:
                    x.tick[s] = False
        elif ch in (ord("u"), ord("U")) and "CurseForge" in ACTIVE and v.state["CurseForge"] == NEW:
            # Already on CurseForge though CFWidget has not caught up (it caches): write it down, skip it from now on.
            cf.remember(v.project["cf_id"], (v.mod["loader"], v.mod["game_version"], v.mod["version_number"]))
            v.state["CurseForge"], v.tick["CurseForge"] = PUBLISHED, False
            v.on_site["CurseForge"] = v.mod["version_number"]
            note = "Marked as already on CurseForge (cf_uploaded.json)."
            continue
        elif ch in (ord("p"), ord("P")):
            # This addon's ticked builds only; the rest of the list keeps its ticks for later.
            queue = addon.ticked()
            if not queue:
                note = "Nothing ticked in this addon - every build is up to date or cannot go."
                continue
            if confirm(win, queue):
                upload_all(win, queue, sites)


# --- 4. confirmation and upload ---------------------------------------------------------------

def confirm(win, queue: list[tuple[Version, str]]) -> bool:
    top = 0
    while True:
        win.erase()
        h, _ = win.getmaxyx()
        title(win, f"Upload {len(queue)} files, in this order?")
        rows = h - 4
        for i, (v, s) in enumerate(queue[top: top + rows]):
            put(win, 2 + i, 2, f'{top + i + 1:3}. {s:11}{v.mod["name"]:40}{v.mod["loader"]:9}{v.mod["game_version"]:9}'
                               f'{v.jar.stat().st_size // 1024:5} KB')
        footer(win, " Y: upload   ↑↓: scroll   any other key: back ")
        win.refresh()
        ch = win.getch()
        if ch in (ord("y"), ord("Y")):
            return True
        if ch == curses.KEY_DOWN:
            top = min(max(0, len(queue) - rows), top + 1)
        elif ch == curses.KEY_UP:
            top = max(0, top - 1)
        else:
            return False


def send(v: Version, site: str, token: str) -> str:
    if site == "Modrinth":
        return mr.upload(mr.build_payload(v.mod, v.changelog), v.jar, token)
    meta = cf.build_metadata(v.mod, v.project, v.changelog, v.jar.name)
    key = (v.mod["loader"], v.mod["game_version"], v.mod["version_number"])
    return str(cf.upload(v.project["cf_id"], meta, v.jar, token, key))


def upload_all(win, queue: list[tuple[Version, str]], sites: dict[str, str]) -> None:
    lines: list[tuple[str, int]] = []

    def show(done: bool) -> None:
        win.erase()
        h, _ = win.getmaxyx()
        title(win, "Done" if done else "Uploading…")
        for i, (text, colour) in enumerate(lines[-(h - 4):]):
            put(win, 2 + i, 2, text, curses.color_pair(colour))
        if done:
            footer(win, " any key: back to the list ")
        win.refresh()

    for v, s in queue:
        name = f'{s:11}{v.mod["name"]} {v.mod["loader"]} {v.mod["game_version"]}'
        lines.append((f"…    {name}", 0))
        show(False)
        try:
            new_id = send(v, s, sites[s])
            lines[-1] = (f"ok   {name} → {new_id}", 1)
            v.state[s], v.tick[s] = PUBLISHED, False
        except urllib.error.HTTPError as e:
            lines[-1] = (f"FAIL {name}: HTTP {e.code} {e.read()[:200]!r}", 3)
            lines.append(("Stopped here; nothing after it was sent.", 3))
            break
        except OSError as e:
            lines[-1] = (f"FAIL {name}: {e}", 3)
            lines.append(("Stopped here; nothing after it was sent.", 3))
            break
        time.sleep(1)  # neither site needs crowding
    show(True)
    win.getch()


def main(win) -> None:
    curses.use_default_colors()
    curses.init_pair(1, curses.COLOR_GREEN, -1)
    curses.init_pair(2, curses.COLOR_WHITE, -1)
    curses.init_pair(3, curses.COLOR_RED, -1)
    curses.init_pair(4, curses.COLOR_YELLOW, -1)
    win.keypad(True)
    sites = login(win)
    if not sites:
        return
    ACTIVE[:] = [s for s in SITES if s in sites]
    curses.curs_set(0)
    addons, errors = load_state(win, sites)
    while True:
        action = addon_list(win, addons, sites, errors)
        if action == "quit":
            return
        if action == "reload":
            addons, errors = load_state(win, sites)
            continue
        queue = [item for a in addons for item in a.ticked()]
        if not queue:
            errors = ["Nothing ticked - every build is up to date or cannot go."] + [e for e in errors if not e.startswith("Nothing ticked")]
            continue
        if confirm(win, queue):
            upload_all(win, queue, sites)


if __name__ == "__main__":
    os.environ.setdefault("ESCDELAY", "25")  # Esc answers at once, not after curses' default second
    curses.wrapper(main)
