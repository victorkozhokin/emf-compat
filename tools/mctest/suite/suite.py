"""Runs every step-file scenario on the build that is in place and keeps each one's results and the logs
in build/suite/<label>. Usage: suite.py <label> [atlas] [test] [parcool]. About 25 minutes for all three;
start it detached (nohup ... &), a tool call is cut off after ten minutes. Compare two labels with cmp.py,
run the ready verifiers on one with verify.py."""
import json, sys, time, subprocess, shutil, importlib.util
from pathlib import Path
ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location("mctest", ROOT / "tools/mctest/mctest.py"); mctest = importlib.util.module_from_spec(spec); sys.modules["mctest"] = mctest; spec.loader.exec_module(mctest)
SC = ROOT / "tools/mctest/scenarios"; OUT = ROOT / "build" / "suite" / sys.argv[1]; OUT.mkdir(parents=True, exist_ok=True)
LOG = ROOT / "run/mctest/Test/logs/latest.log"
AA = ["icys-better-horses", "watut", "emf_compat_watut", "ParCool-1.21.1-4.0", "emf_compat_parcool"]
PRE = [{"closeScreen": True}, {"releaseAll": True}, {"config": {"debug.decisions": True, "footgrounding.trace": False, "wallhand.trace": False, "transport.trace": False}}]
ATLAS = ["atlas-balance-regression", "atlas-counterbalance", "atlas-crank-stance", "atlas-crank-stance-edge", "atlas-crouch-step", "atlas-head-gaze", "atlas-head-lookat",
         "atlas-lead", "atlas-lever-pose", "atlas-lever-fence", "atlas-low-reach", "atlas-low-reach-release", "atlas-pelvis-attachment", "atlas-pelvis-attachment-rear",
         "atlas-pelvis-clearance", "atlas-standing-reach", "atlas-terrain-balance", "atlas-terrain-edge", "atlas-terrain-edge-walk", "atlas-throttle-effort",
         "atlas-wall-stance", "atlas-wallhand", "atlas-wallhand-free-arm", "atlas-wheel-stance", "atlas-cockpit", "atlas-cockpit-facing",
         "interaction-scene", "interaction-cases", "wheel-scene", "wheel-cases", "pocket-stash", "terrain-balance",
         "gestures", "gestures_remote", "hands_sync", "plant_field", "mining-body"]
PHASES = {"atlas": ("EMF ATLAS - Animation Campus", AA, ATLAS),
          "test": ("test", AA, ["aa-scene", "aa-shots", "aa-contact", "footik-scene", "footik-cases"]),
          "parcool": ("ParCool Course", ["icys-better-horses", "watut", "emf_compat_watut"], ["parcool-all-scene", "parcool-all-moves"])}
def run(steps):
    rows = []
    steps = mctest.expand_bursts(steps)
    for i in range(0, len(steps), 300):
        r = mctest.run_steps("Test", steps[i:i + 300], timeout=300)
        if "error" in r: return rows, r["error"]
        rows += r.get("results", [])
    return rows, None
summary = {}
for phase in (sys.argv[2:] or list(PHASES)):
    world, disable, names = PHASES[phase]
    mctest.stop("Test")
    mctest.launch("Test", world, disable=disable, player_name="STRadaT", player_uuid="e750dfddf54d418babd46776ce404f09")
    ready = mctest.wait_ready("Test", 400)
    print(phase, "ready", ready.get("inWorld"), flush=True)
    time.sleep(5)
    for name in names:
        data = json.loads((SC / (name + ".json")).read_text())
        parts = [(name, data)] if isinstance(data, list) else [(f"{name}:{k}", v) for k, v in data.items()]
        t0 = time.time(); err = None; failed = 0; n = 0
        for label, steps in parts:
            if not mctest.running_pid(mctest.get_profile("Test")):
                err = "game not running"; break
            rows, e = run(PRE + [{"log": "suite " + label}] + steps + [{"releaseAll": True}, {"closeScreen": True}, {"log": "suite-end " + label}])
            n += len(rows); failed += sum(1 for r in rows if isinstance(r, dict) and ("error" in r or any(v == "FAIL" for v in r.values() if isinstance(v, str))))
            (OUT / (label.replace(":", "__").replace("/", "_") + ".results.json")).write_text(json.dumps({"results": rows, "error": e}))
            if e: err = e; break
        summary[name] = {"seconds": round(time.time() - t0), "steps": n, "failed_steps": failed, "error": err}
        print(name, summary[name], flush=True)
        if err and "exited" in str(err):
            mctest.launch("Test", world, disable=disable, player_name="STRadaT", player_uuid="e750dfddf54d418babd46776ce404f09"); mctest.wait_ready("Test", 400)
    shutil.copy(LOG, OUT / f"{phase}.log")
    mctest.stop("Test")
(OUT / "summary.json").write_text(json.dumps(summary, indent=1))
print("suite done", flush=True)
