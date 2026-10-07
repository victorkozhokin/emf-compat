"""Runs a port of Touch'n Motion from its build (Loom's runClient) with the mctest driver in it, for a loader and
version no Modrinth profile exists for, and drives it the way tools/mctest/mctest.py does.

    python3 tools/tnm_port/devrun.py launch [world]     # prepares run/mctest/<name>, starts the game
    python3 tools/tnm_port/devrun.py steps '<json>'      # queues steps for the driver, prints its answer
    python3 tools/tnm_port/devrun.py status | stop

The run folder gets the resource packs and the options of the Modrinth profile "Test" (the packs copied), a copy
of one of its worlds, and the mod's settings with decisions logged."""
import json, os, shutil, signal, subprocess, sys, time
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
NAME, MODULE = "Fabric-1.21.1", ":touch-n-motion-fabric-1.21.1"
RUN = REPO / "run" / "mctest" / NAME
PROFILE = Path.home() / "Modrinth" / "profiles" / "Test"
PACKS = ["FreshAnimations_v1.10.4", "FA+Player-v1.1.zip", "JustExpressions_v1.2.1.zip"]
OPTIONS = {"pauseOnLostFocus": "false", "fullscreen": "false", "onboardAccessibility": "false", "skipMultiplayerWarning": "true",
           "joinedFirstServer": "true", "tutorialStep": "none", "narrator": "0", "soundCategory_master": "0.0", "maxFps": "60",
           "toggleCrouch": "false", "toggleSprint": "false"}


def pid():
    f = RUN / "mctest" / "pid"
    if not f.exists():
        return None
    p = int(f.read_text())
    try:
        os.killpg(p, 0)
        return p
    except (ProcessLookupError, PermissionError):
        return None


def launch(world="test"):
    if pid():
        raise SystemExit("already running; stop it first")
    for d in ("mctest/inbox", "mctest/outbox", "resourcepacks", "saves", "config"):
        (RUN / d).mkdir(parents=True, exist_ok=True)
    for pack in PACKS:
        # Copied, not linked: the game refuses a pack that is a link to outside its folder.
        src, dst = PROFILE / "resourcepacks" / pack, RUN / "resourcepacks" / pack
        if dst.is_symlink():
            dst.unlink()
        if not dst.exists() and src.exists():
            shutil.copytree(src, dst) if src.is_dir() else shutil.copy2(src, dst)
    if not (RUN / "saves" / world).exists():
        shutil.copytree(PROFILE / "saves" / world, RUN / "saves" / world)
    opts = dict(OPTIONS)
    opts["resourcePacks"] = json.dumps(["vanilla", "fabric"] + [f"file/{p}" for p in reversed(PACKS)])
    lines = (PROFILE / "options.txt").read_text().splitlines()
    out, seen = [], set()
    for line in lines:
        key = line.split(":", 1)[0]
        if key in opts:
            out.append(f"{key}:{opts[key]}")
            seen.add(key)
        elif not key.startswith("key_") or key.startswith("key_key."):
            out.append(line)
    out += [f"{k}:{v}" for k, v in opts.items() if k not in seen]
    (RUN / "options.txt").write_text("\n".join(out) + "\n")
    cfg = RUN / "config" / "emf_compat.json"
    if not cfg.exists():
        cfg.write_text(json.dumps({"configVersion": 1, "booleans": {"debug.decisions": True}, "numbers": {}}, indent=2))
    for stale in list((RUN / "mctest" / "inbox").glob("*")) + list((RUN / "mctest" / "outbox").glob("*")):
        stale.unlink()
    with open(RUN / "mctest" / "launcher.out", "w") as log:
        proc = subprocess.Popen([str(REPO / "gradlew"), f"{MODULE}:runClient", f"-PmctestRun={RUN}", f"-PmctestWorld={world}", "-q"],
                                cwd=REPO, stdout=log, stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL, start_new_session=True)
    (RUN / "mctest" / "pid").write_text(str(proc.pid))
    return {"pid": proc.pid, "run": str(RUN), "log": str(RUN / "logs" / "latest.log")}


def status():
    f = RUN / "mctest" / "status.json"
    out = {"running": bool(pid()), "driver": None}
    if f.exists():
        try:
            out["driver"] = json.loads(f.read_text())
            out["driver_age_s"] = round(time.time() - f.stat().st_mtime, 1)
        except json.JSONDecodeError:
            pass
    return out


def steps(script, timeout=120.0):
    if not pid():
        raise SystemExit("not running")
    sid = f"{int(time.time() * 1000)}"
    ticks = sum(int(s.get("wait", 0)) for s in script)
    timeout = max(timeout, ticks / 20 * 1.5 + 60)
    inbox, outbox = RUN / "mctest" / "inbox", RUN / "mctest" / "outbox"
    tmp = inbox / f"{sid}.tmp"
    tmp.write_text(json.dumps({"id": sid, "steps": script}))
    tmp.rename(inbox / f"{sid}.json")
    answer = outbox / f"{sid}.json"
    deadline = time.time() + timeout
    while time.time() < deadline:
        if answer.exists():
            result = json.loads(answer.read_text())
            answer.unlink()
            time.sleep(0.5)
            return result
        if not pid():
            return {"error": "game exited"}
        time.sleep(0.05)
    return {"error": f"no answer in {timeout}s", "status": status()}


def stop():
    p = pid()
    if not p:
        return "not running"
    os.killpg(p, signal.SIGTERM)
    for _ in range(50):
        if not pid():
            return f"stopped {p}"
        time.sleep(0.3)
    os.killpg(p, signal.SIGKILL)
    return f"killed {p}"


if __name__ == "__main__":
    cmd, rest = sys.argv[1], sys.argv[2:]
    if cmd == "launch":
        print(json.dumps(launch(*rest[:1]), indent=1))
    elif cmd == "steps":
        print(json.dumps(steps(json.loads(rest[0])), indent=1))
    elif cmd == "status":
        print(json.dumps(status(), indent=1))
    elif cmd == "stop":
        print(stop())
