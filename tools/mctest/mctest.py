"""Launches a Modrinth profile in a throwaway sandbox and drives it through the test-driver mod.

The profile itself is never written to. Each launch mirrors it into ``run/mctest/<profile>``:
its mods (with our own jars swapped for the freshly built ones from ``upload/``), its config,
options and resource packs, and a copy of one of its worlds. The game is started offline with
the Java runtime, libraries and assets the Modrinth App already downloaded, so no account token
is ever involved.

In game the test driver (``tools/mctest/driver``) polls ``mctest/inbox`` once per client tick,
runs the steps it finds there and answers in ``mctest/outbox``. See ``run_steps``.

Usable as a library (the MCP server in ``server.py``) and from the command line::

    python3 tools/mctest/mctest.py profiles
    python3 tools/mctest/mctest.py launch Test --world test
    python3 tools/mctest/mctest.py steps Test '[{"camera":"front"},{"wait":10},{"screenshot":"a"}]'
    python3 tools/mctest/mctest.py stop Test
"""

from __future__ import annotations

import contextlib
import json
import os
import platform
import re
import shutil
import signal
import sqlite3
import subprocess
import sys
import time
import uuid
from dataclasses import dataclass
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
MODRINTH = Path.home() / "Modrinth"
META = MODRINTH / "meta"
PROFILES = MODRINTH / "profiles"
APP_DB = Path.home() / "Library/Application Support/ModrinthApp/app.db"
SANDBOXES = REPO / "run" / "mctest"
DRIVER_BUILD = REPO / "tools" / "mctest" / "driver"

OFFLINE_NAME = "Dev"

# Written into the sandbox's options.txt on every launch. The game must keep running while its
# window is in the background, and nothing should pop up or make noise.
OPTION_OVERRIDES = {
    "pauseOnLostFocus": "false",
    "fullscreen": "false",
    "onboardAccessibility": "false",
    "skipMultiplayerWarning": "true",
    "joinedFirstServer": "true",
    "tutorialStep": "none",
    "narrator": "0",
    "soundCategory_master": "0.0",
    "maxFps": "60",
    # The driver holds keys by re-asserting them every tick, which a toggle mapping would flip.
    "toggleCrouch": "false",
    "toggleSprint": "false",
    # Chat display off, but not "hidden": with HIDDEN the server answers every command with
    # "Chat disabled in client options" and runs none of them. Screenshots are taken with
    # hideGui, which covers the chat line anyway; where that is not available (26.2), start the
    # script with "gamerule sendCommandFeedback false".
    "chatVisibility": "1",
}

# Profile folders copied (small, the game may write them) or linked (large, read-only in practice).
COPIED_DIRS = ["config", "defaultconfigs"]
LINKED_DIRS = ["resourcepacks", "shaderpacks"]


# --------------------------------------------------------------------------------------------
# Profiles

@dataclass
class Profile:
    name: str          # folder name under ~/Modrinth/profiles
    title: str         # name shown in the app
    game_version: str
    loader: str        # vanilla / fabric / forge / neoforge / quilt
    loader_version: str | None
    memory_mb: int
    extra_jvm_args: list[str]

    @property
    def path(self) -> Path:
        return PROFILES / self.name

    @property
    def version_id(self) -> str:
        if self.loader == "vanilla" or not self.loader_version:
            return self.game_version
        return f"{self.game_version}-{self.loader_version}"

    @property
    def sandbox(self) -> Path:
        return SANDBOXES / re.sub(r"[^A-Za-z0-9._-]+", "_", self.name)


def list_profiles() -> list[Profile]:
    con = sqlite3.connect(f"file:{APP_DB}?mode=ro", uri=True)
    try:
        rows = con.execute(
            "select i.path, i.name, s.game_version, s.loader, s.loader_version, json(o.overrides) "
            "from instances i join instance_content_sets s on s.id = i.applied_content_set_id "
            "left join instance_launch_overrides o on o.instance_id = i.id order by i.path").fetchall()
    finally:
        con.close()
    out = []
    for path, name, gv, loader, lv, overrides in rows:
        ov = json.loads(overrides) if overrides else {}
        memory = (ov.get("memory") or {}).get("maximum") or 4096
        out.append(Profile(path, name, gv, loader, lv, memory, ov.get("extra_launch_args") or []))
    return out


def get_profile(name: str) -> Profile:
    profiles = list_profiles()
    for p in profiles:
        if name in (p.name, p.title):
            return p
    lowered = [p for p in profiles if name.lower() in (p.name.lower(), p.title.lower())]
    if len(lowered) == 1:
        return lowered[0]
    raise SystemExit(f"no such profile: {name!r}; have {[p.name for p in profiles]}")


# --------------------------------------------------------------------------------------------
# Sandbox

# Both namings are matched: the installed jar may still be emf_compat_<addon>_<mc>_<version>.jar
# from before the loader went into the file name, while upload/ now writes the loader in.
_OUR_JAR = re.compile(r"^emf_compat_(?P<addon>.+?)"
                      r"(?:_(?:fabric|neoforge|forge))?"
                      r"_(?P<mc>\d+\.\d+(?:\.\d+)?)_[^_]+\.jar$")


def _fresh_build_of(jar_name: str, loader: str) -> Path | None:
    """The newest jar in upload/ that is the same addon for the same loader and Minecraft version."""
    m = _OUR_JAR.match(jar_name)
    if not m:
        return None
    addon, mc = m.group("addon"), m.group("mc")
    candidates = [p for p in (REPO / "upload").glob(f"*/{loader}/{mc}/emf_compat_{addon}_*.jar")
                  if p.name.startswith((f"emf_compat_{addon}_{mc}_", f"emf_compat_{addon}_{loader}_{mc}_"))]
    return max(candidates, key=lambda p: p.stat().st_mtime) if candidates else None


def _driver_jar(profile: Profile) -> Path | None:
    libs = DRIVER_BUILD / f"{profile.loader}-{profile.game_version}" / "build" / "libs"
    jars = [p for p in libs.glob("*.jar") if not p.name.endswith("-sources.jar")]
    return max(jars, key=lambda p: p.stat().st_mtime) if jars else None


def _patch_options(path: Path, extra: dict[str, str] | None = None) -> None:
    overrides = {**OPTION_OVERRIDES, **(extra or {})}
    lines = path.read_text(encoding="utf-8").splitlines() if path.exists() else []
    seen = set()
    for i, line in enumerate(lines):
        key = line.split(":", 1)[0]
        if key in overrides:
            lines[i] = f"{key}:{overrides[key]}"
            seen.add(key)
    lines += [f"{k}:{v}" for k, v in overrides.items() if k not in seen]
    path.write_text("\n".join(lines) + "\n", encoding="utf-8")


def prepare_sandbox(profile: Profile, world: str | None, use_project_jars: bool = True,
                    fresh_world: bool = False, enable: list[str] | None = None,
                    disable: list[str] | None = None, emf_log: bool = False, no_cape: bool = False) -> dict:
    src, dst = profile.path, profile.sandbox
    dst.mkdir(parents=True, exist_ok=True)
    report = {"sandbox": str(dst), "swapped": [], "enabled": [], "disabled": [], "driver": None,
              "world": None}
    unmatched = {w.lower() for w in disable or []}

    mods = dst / "mods"
    if mods.exists():
        shutil.rmtree(mods)
    mods.mkdir()
    for jar in sorted((src / "mods").iterdir()):
        if not jar.name.endswith(".jar"):
            continue  # .disabled and friends stay off, exactly as in the profile
        # And the other way round: a mod the profile runs can sit out one test run, e.g. one that
        # takes over first-person hand rendering and hides what an addon does there.
        off = [w for w in disable or [] if w.lower() in jar.name.lower()]
        if off:
            unmatched.difference_update(w.lower() for w in off)
            report["disabled"].append(jar.name)
            continue
        fresh = _fresh_build_of(jar.name, profile.loader) if use_project_jars else None
        if fresh is not None:
            shutil.copy2(fresh, mods / fresh.name)
            if fresh.name != jar.name or fresh.stat().st_mtime > jar.stat().st_mtime:
                report["swapped"].append(f"{jar.name} -> upload/{fresh.relative_to(REPO / 'upload')}")
        else:
            (mods / jar.name).symlink_to(jar)
    if unmatched:
        raise SystemExit(f"profile {profile.name!r} has no enabled mod matching {sorted(unmatched)}")
    # A mod the profile keeps switched off can be switched on for one test run — the sandbox is
    # ours, the profile keeps its .disabled file untouched.
    for want in enable or []:
        matches = [j for j in sorted((src / "mods").iterdir())
                   if j.name.endswith(".disabled") and want.lower() in j.name.lower()]
        if not matches:
            raise SystemExit(f"profile {profile.name!r} has no disabled mod matching {want!r}")
        for jar in matches:
            name = jar.name[: -len(".disabled")]
            # One of ours switched on gets the fresh build too, like the enabled ones above.
            fresh = _fresh_build_of(name, profile.loader) if use_project_jars else None
            if fresh is not None:
                shutil.copy2(fresh, mods / fresh.name)
                report["enabled"].append(f"{name} -> upload/{fresh.relative_to(REPO / 'upload')}")
            else:
                (mods / name).symlink_to(jar)
                report["enabled"].append(name)
    driver = _driver_jar(profile)
    if driver is not None:
        shutil.copy2(driver, mods / driver.name)
        report["driver"] = driver.name

    for name in COPIED_DIRS:
        if (dst / name).exists():
            shutil.rmtree(dst / name)
        if (src / name).is_dir():
            shutil.copytree(src / name, dst / name, symlinks=True)
    for name in LINKED_DIRS:
        link = dst / name
        if link.is_symlink() or link.exists():
            link.unlink() if link.is_symlink() else shutil.rmtree(link)
        if (src / name).is_dir():
            link.symlink_to(src / name)
    if emf_log:
        # EMF's own model-creation and ASM logs name the expression a pack fails on; they are only
        # read at startup, so they go into the sandbox's copy of the config before launch.
        emf_config = dst / "config" / "entity_model_features.json"
        if emf_config.exists():
            data = json.loads(emf_config.read_text())
            data["logModelCreationData"] = True
            data["logASM"] = True
            emf_config.write_text(json.dumps(data, indent=2))
    # The features' decision lines ("stride", "hover-R") are off for players; the baselines read
    # them, so the sandbox's copy of the config has them on. The profile's own is not touched.
    compat_config = dst / "config" / "emf_compat.json"
    data = json.loads(compat_config.read_text()) if compat_config.exists() else {"configVersion": 1}
    data.setdefault("booleans", {})["debug.decisions"] = True
    compat_config.parent.mkdir(parents=True, exist_ok=True)
    compat_config.write_text(json.dumps(data, indent=2))
    for name in ["options.txt", "servers.dat"]:
        if (src / name).exists():
            shutil.copy2(src / name, dst / name)
    # A cape hides the torso and legs from behind; --no-cape turns the player's off for the shots.
    _patch_options(dst / "options.txt", {"modelPart_cape": "false"} if no_cape else None)

    if world:
        target = dst / "saves" / world
        if fresh_world and target.exists():
            shutil.rmtree(target)
        if not target.exists():
            source = src / "saves" / world
            if not source.is_dir():
                raise SystemExit(f"profile {profile.name!r} has no world {world!r}: "
                                 f"{sorted(p.name for p in (src / 'saves').iterdir())}")
            shutil.copytree(source, target, ignore=shutil.ignore_patterns("session.lock"))
        report["world"] = world

    shutil.rmtree(dst / "screenshots", ignore_errors=True)
    for sub in ["inbox", "outbox"]:
        d = dst / "mctest" / sub
        if d.exists():
            shutil.rmtree(d)
        d.mkdir(parents=True)
    (dst / "mctest" / "status.json").unlink(missing_ok=True)
    return report


# --------------------------------------------------------------------------------------------
# Launch

def _rules_allow(rules: list | None, features: dict) -> bool:
    if not rules:
        return True
    allowed = False
    arch = "arm64" if platform.machine() == "arm64" else "x86_64"
    for rule in rules:
        ok = True
        os_rule = rule.get("os") or {}
        if "name" in os_rule and os_rule["name"] not in ("osx", "macos"):
            ok = False
        if "arch" in os_rule and os_rule["arch"] not in (arch, "arm64" if arch == "arm64" else "x86_64"):
            ok = False
        for key, want in (rule.get("features") or {}).items():
            if want is None:
                continue
            if bool(features.get(key)) != bool(want):
                ok = False
        if ok:
            allowed = rule["action"] == "allow"
    return allowed


def _library_path(lib: dict) -> Path:
    artifact = (lib.get("downloads") or {}).get("artifact")
    if artifact and artifact.get("path"):
        return META / "libraries" / artifact["path"]
    # Maven coordinates: group:artifact:version[:classifier][@ext]
    coords, _, ext = lib["name"].partition("@")
    parts = coords.split(":")
    group, art, version = parts[0], parts[1], parts[2]
    classifier = f"-{parts[3]}" if len(parts) > 3 else ""
    return (META / "libraries" / group.replace(".", "/") / art / version
            / f"{art}-{version}{classifier}.{ext or 'jar'}")


def _java_for(major: int) -> Path:
    for home in sorted(META.glob(f"java_versions/zulu{major}*")):
        java = home / "Contents" / "Home" / "bin" / "java"
        if java.exists():
            return java
    raise SystemExit(f"no Java {major} in {META / 'java_versions'} — launch the profile once from the Modrinth App")


def build_command(profile: Profile, world: str | None, width: int, height: int,
                  player_name: str = OFFLINE_NAME, player_uuid: str | None = None) -> list[str]:
    vid = profile.version_id
    vjson = META / "versions" / vid / f"{vid}.json"
    if not vjson.exists():
        raise SystemExit(f"{vjson} is missing — launch the profile once from the Modrinth App")
    d = json.loads(vjson.read_text())
    features = {"has_custom_resolution": True, "is_quick_play_singleplayer": bool(world)}

    classpath = []
    for lib in d["libraries"]:
        if lib.get("include_in_classpath") is False or not _rules_allow(lib.get("rules"), features):
            continue
        path = _library_path(lib)
        if path.exists() and str(path) not in classpath:
            classpath.append(str(path))
    client_jar = META / "versions" / vid / f"{vid}.jar"
    classpath.append(str(client_jar))

    natives = META / "natives" / vid
    natives.mkdir(parents=True, exist_ok=True)
    # Offline, the default skin comes from the UUID, so the name picks it (--name Player: wide Steve).
    # A real account's UUID (--uuid) makes the game fetch that account's own skin instead.
    offline_uuid = (player_uuid or uuid.uuid3(uuid.NAMESPACE_DNS, "mctest:" + player_name).hex).replace("-", "")
    values = {
        "auth_player_name": player_name, "version_name": vid, "game_directory": str(profile.sandbox),
        "assets_root": str(META / "assets"), "assets_index_name": d["assetIndex"]["id"],
        "auth_uuid": offline_uuid, "auth_access_token": "0", "clientid": "", "auth_xuid": "",
        "user_type": "legacy", "version_type": d.get("type", "release"),
        "natives_directory": str(natives), "launcher_name": "mctest", "launcher_version": "1",
        "classpath": ":".join(classpath), "classpath_separator": ":",
        "library_directory": str(META / "libraries"),
        "resolution_width": str(width), "resolution_height": str(height),
        "quickPlaySingleplayer": world or "", "quickPlayPath": "", "quickPlayMultiplayer": "",
        "quickPlayRealms": "",
    }

    def expand(arg: str) -> str:
        return re.sub(r"\$\{([^}]+)\}", lambda m: values.get(m.group(1), m.group(0)), arg)

    def collect(args: list) -> list[str]:
        out = []
        for a in args:
            if isinstance(a, str):
                out.append(expand(a))
            elif _rules_allow(a.get("rules"), features):
                v = a["value"]
                out += [expand(x) for x in (v if isinstance(v, list) else [v])]
        return out

    jvm = [f"-Xmx{profile.memory_mb}M", *profile.extra_jvm_args, "-Dmctest=1"]
    logging = (d.get("logging") or {}).get("client")
    if logging:
        cfg = META / "log_configs" / logging["file"]["id"]
        if cfg.exists():
            jvm.append(logging["argument"].replace("${path}", str(cfg)))
    jvm += collect(d["arguments"]["jvm"])
    game = collect(d["arguments"]["game"])
    java = _java_for(d["javaVersion"]["majorVersion"])
    return [str(java), *jvm, d["mainClass"], *game]


def _pid_file(profile: Profile) -> Path:
    return profile.sandbox / "mctest" / "pid"


def running_pid(profile: Profile) -> int | None:
    f = _pid_file(profile)
    if not f.exists():
        return None
    pid = int(f.read_text().strip() or 0)
    try:
        # When this very process launched the game, a stopped game lingers as a zombie until it
        # is reaped, and kill(pid, 0) would keep reporting it alive.
        if os.waitpid(pid, os.WNOHANG)[0] == pid:
            raise ProcessLookupError
    except ChildProcessError:
        pass  # someone else's child: nothing to reap
    except ProcessLookupError:
        f.unlink(missing_ok=True)
        return None
    try:
        os.kill(pid, 0)
        return pid
    except (OSError, ValueError):
        f.unlink(missing_ok=True)
        return None


def launch(name: str, world: str | None = None, width: int = 1280, height: int = 720,
           use_project_jars: bool = True, fresh_world: bool = False,
           enable: list[str] | None = None, disable: list[str] | None = None, emf_log: bool = False,
           player_name: str = OFFLINE_NAME, player_uuid: str | None = None, no_cape: bool = False) -> dict:
    profile = get_profile(name)
    if running_pid(profile):
        raise SystemExit(f"{profile.name} is already running (pid {running_pid(profile)}); stop it first")
    report = prepare_sandbox(profile, world, use_project_jars, fresh_world, enable, disable, emf_log,
                             no_cape=no_cape)
    cmd = build_command(profile, world, width, height, player_name, player_uuid)
    # The child keeps its own handle on the log, so the parent's can close with the block.
    with open(profile.sandbox / "mctest" / "launcher.out", "w") as log:
        proc = subprocess.Popen(cmd, cwd=profile.sandbox, stdout=log, stderr=subprocess.STDOUT,
                                stdin=subprocess.DEVNULL, start_new_session=True)
    _pid_file(profile).write_text(str(proc.pid))
    report.update({"pid": proc.pid, "profile": profile.name, "version": profile.version_id,
                   "log": str(profile.sandbox / "logs" / "latest.log")})
    return report


def stop(name: str, timeout: float = 15.0) -> str:
    profile = get_profile(name)
    pid = running_pid(profile)
    if not pid:
        return "not running"
    os.killpg(pid, signal.SIGTERM)
    deadline = time.time() + timeout
    while time.time() < deadline:
        if not running_pid(profile):
            return f"stopped {pid}"
        time.sleep(0.3)
    with contextlib.suppress(ProcessLookupError, PermissionError):  # already gone
        os.killpg(pid, signal.SIGKILL)
    _pid_file(profile).unlink(missing_ok=True)
    return f"killed {pid}"


# --------------------------------------------------------------------------------------------
# Driving the game

def status(name: str) -> dict:
    profile = get_profile(name)
    f = profile.sandbox / "mctest" / "status.json"
    out = {"running": bool(running_pid(profile)), "driver": None}
    if f.exists():
        try:
            out["driver"] = json.loads(f.read_text())
            out["driver_age_s"] = round(time.time() - f.stat().st_mtime, 1)
        except json.JSONDecodeError:
            pass
    return out


def wait_ready(name: str, timeout: float = 300.0) -> dict:
    """Blocks until the driver reports the player in a world, the game dies, or time runs out."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        st = status(name)
        if not st["running"]:
            return {"ready": False, "reason": "game exited", "log_tail": log_tail(name, 40)}
        drv = st.get("driver")
        if drv and drv.get("inWorld") and st.get("driver_age_s", 99) < 5:
            return {"ready": True, **drv}
        screen = (drv or {}).get("screen") or ""
        if st.get("driver_age_s", 99) < 5 and re.search(r"Error|Warning|Crash", screen.rsplit(".", 1)[-1]):
            # A loader error or warning screen holds the game before quick play ever starts.
            return {"ready": False, "reason": f"blocked on {screen}",
                    "log_tail": log_tail(name, 30, r"warn|error|exception")}
        time.sleep(1.0)
    return {"ready": False, "reason": "timeout", "status": status(name)}


def expand_bursts(steps: list[dict]) -> list[dict]:
    """Turns ``{"burst": {...}}`` into screenshots (and fade probes) with waits between them.

    The driver has no burst step; server.py expands it the same way, so a saved scenario runs
    unchanged from MCP and from this CLI.
    """
    out = []
    for step in steps:
        if "burst" not in step:
            out.append(step)
            continue
        b = step["burst"]
        count, every, name = int(b.get("count", 6)), int(b.get("every", 1)), b.get("name", "burst")
        for i in range(count):
            out.append({"screenshot": f"{name}_{i:02d}"})
            if b.get("fade"):
                out.append({"fade": True})
            if i < count - 1:
                out.append({"wait": every})
    return out


def run_steps(name: str, steps: list[dict], timeout: float = 120.0) -> dict:
    """Queues a script for the driver and waits for its answer.

    Steps run one after another on the client thread; ``wait`` counts client ticks (20 per
    second). Supported steps: ``{"cmd": "time set noon"}``, ``{"chat": "hi"}``,
    ``{"hold": "sneak"}`` / ``{"release": "sneak"}`` (forward back left right jump sneak sprint
    attack use), ``{"click": "attack"}``, ``{"slot": 0}``, ``{"camera": "first|back|front"}``,
    ``{"look": [yaw, pitch]}``, ``{"wait": 10}``, ``{"screenshot": "name"}``, ``{"state": true}``,
    ``{"closeScreen": true}``, ``{"releaseAll": true}``.
    """
    profile = get_profile(name)
    if not running_pid(profile):
        raise SystemExit(f"{profile.name} is not running")
    sid = f"{int(time.time() * 1000)}"
    # a long script (a whole course) outlives the default: allow its own waits plus a minute
    ticks = sum(int(s.get("wait", 0)) + int((s.get("until") or {}).get("timeout", 0)) for s in steps)
    timeout = max(timeout, ticks / 20 * 1.5 + 60)
    inbox = profile.sandbox / "mctest" / "inbox"
    outbox = profile.sandbox / "mctest" / "outbox"
    tmp = inbox / f"{sid}.tmp"
    tmp.write_text(json.dumps({"id": sid, "steps": steps}))
    tmp.rename(inbox / f"{sid}.json")  # atomic: the driver never sees half a file
    answer = outbox / f"{sid}.json"
    deadline = time.time() + timeout
    while time.time() < deadline:
        if answer.exists():
            result = json.loads(answer.read_text())
            answer.unlink()
            # Screenshots are written off-thread after the step returns; wait for the files.
            for r in result.get("results", []):
                for shot in [r.get("screenshot")] + list(r.get("screenshots") or []):
                    if not shot:
                        continue
                    p = Path(shot)
                    for _ in range(50):
                        if p.exists() and p.stat().st_size > 0:
                            break
                        time.sleep(0.1)
            return result
        if not running_pid(profile):
            return {"error": "game exited", "log_tail": log_tail(name, 40)}
        time.sleep(0.05)
    return {"error": f"no answer in {timeout}s", "status": status(name)}


def log_tail(name: str, lines: int = 80, grep: str | None = None) -> str:
    profile = get_profile(name)
    f = profile.sandbox / "logs" / "latest.log"
    if not f.exists():
        f = profile.sandbox / "mctest" / "launcher.out"
        if not f.exists():
            return ""
    text = f.read_text(encoding="utf-8", errors="replace").splitlines()
    if grep:
        rx = re.compile(grep, re.I)
        text = [line for line in text if rx.search(line)]
    return "\n".join(text[-lines:])


# --------------------------------------------------------------------------------------------
# CLI

def _main(argv: list[str]) -> None:
    if not argv:
        print(__doc__)
        return
    cmd, *rest = argv
    if cmd == "profiles":
        for p in list_profiles():
            print(f"{p.name:24} {p.title:24} {p.loader:9} {p.version_id:20} {p.memory_mb}M")
    elif cmd == "launch":
        world = rest[rest.index("--world") + 1] if "--world" in rest else None
        enable = rest[rest.index("--enable") + 1].split(",") if "--enable" in rest else None
        disable = rest[rest.index("--disable") + 1].split(",") if "--disable" in rest else None
        name = rest[rest.index("--name") + 1] if "--name" in rest else OFFLINE_NAME
        print(json.dumps(launch(rest[0], world, fresh_world="--fresh-world" in rest,
                                enable=enable, disable=disable, emf_log="--emf-log" in rest,
                                player_name=name,
                                player_uuid=rest[rest.index("--uuid") + 1] if "--uuid" in rest else None,
                                no_cape="--no-cape" in rest), indent=1))
    elif cmd == "wait":
        print(json.dumps(wait_ready(rest[0]), indent=1))
    elif cmd == "steps":
        steps = expand_bursts(json.loads(rest[1]))
        print(json.dumps(run_steps(rest[0], steps, timeout=max(120.0, len(steps) * 2.0)), indent=1))
    elif cmd == "status":
        print(json.dumps(status(rest[0]), indent=1))
    elif cmd == "log":
        print(log_tail(rest[0], int(rest[1]) if len(rest) > 1 else 80, rest[2] if len(rest) > 2 else None))
    elif cmd == "stop":
        print(stop(rest[0]))
    elif cmd == "command":
        print(" ".join(build_command(get_profile(rest[0]), None, 1280, 720)))
    else:
        raise SystemExit(f"unknown command {cmd!r}")


if __name__ == "__main__":
    _main(sys.argv[1:])
