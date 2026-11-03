#!/usr/bin/env python3
"""Brings the dependencies of versions already on Modrinth in line with projects.json.

Only the version the map lists (same loader, game version and version number) is touched, or,
while that one is not out yet, the newest one out for that loader and game version; older ones
keep what they were published with. A dependency the map lists is added or given the map's
type; one the version has that the map does not list is left as it is. Without --apply it only prints what would change.

    python3 tools/publish/sync_deps.py            # dry run, no token needed
    python3 tools/publish/sync_deps.py --apply    # token from the Keychain, as the TUI reads it

CurseForge cannot change the relations of an uploaded file through its API; the dry run lists
those files so they can be edited on the site.
"""
import json
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
import publish as mr  # noqa: E402
from publish_tui import keychain_read  # noqa: E402

MAP = Path(__file__).parent / "projects.json"


def wanted(mod: dict) -> dict[str, str]:
    return {d["project_id"]: d["dependency_type"] for d in mod["dependencies"]}


def versions(project_id: str) -> list[dict] | None:
    """The project's versions on Modrinth; None when it is not there yet. Retried on a network hiccup."""
    for attempt in range(3):
        try:
            return mr.api_get(f"/project/{project_id}/version")
        except urllib.error.HTTPError as e:
            if e.code == 404:
                return None
            raise
        except (urllib.error.URLError, TimeoutError):
            if attempt == 2:
                raise
            time.sleep(2)
    return None


def patch(version_id: str, deps: list[dict], token: str) -> None:
    req = urllib.request.Request(f"{mr.API}/version/{version_id}", method="PATCH",
                                 data=json.dumps({"dependencies": deps}).encode(),
                                 headers={"Authorization": token, "User-Agent": mr.UA,
                                          "Content-Type": "application/json"})
    with urllib.request.urlopen(req, timeout=30) as r:
        if r.status != 204:
            raise RuntimeError(f"HTTP {r.status}")


def main() -> int:
    apply = "--apply" in sys.argv
    data = json.loads(MAP.read_text())
    by_project: dict[str, list[dict]] = {}
    for mod in data["modules"]:
        by_project.setdefault(mod["project_id"], []).append(mod)

    todo = []
    for project_id, mods in by_project.items():
        published = versions(project_id)
        time.sleep(0.2)
        if published is None:
            continue  # not on Modrinth yet
        for mod in mods:
            same = [v for v in published
                    if mod["loader"] in v["loaders"] and mod["game_version"] in v["game_versions"]]
            match = [v for v in same if v["version_number"] == mod["version_number"]]
            if not match:
                match = sorted(same, key=lambda v: v["date_published"], reverse=True)[:1]
            if not match:
                continue
            v = match[0]
            have = {d["project_id"]: d["dependency_type"] for d in v["dependencies"] if d.get("project_id")}
            want = {**have, **wanted(mod)}
            if have == want:
                continue
            names = {d["project_id"]: d["slug"] for d in mod["dependencies"]}
            diff = []
            for pid in sorted(set(have) | set(want)):
                if have.get(pid) != want.get(pid):
                    diff.append(f'{names.get(pid, pid)}: {have.get(pid, "-")} -> {want.get(pid, "-")}')
            label = mod["name"].rsplit(" ", 1)[0] + " " + v["version_number"]
            print(f'{label:48} {mod["loader"]:9} {mod["game_version"]:8} ' + "; ".join(diff))
            keep = [d for d in v["dependencies"] if not d.get("project_id")]
            todo.append((v["id"], keep + [{"project_id": p, "dependency_type": t} for p, t in want.items()],
                         {**mod, "name": label}))

    print(f"\n{len(todo)} version(s) on Modrinth differ from the map.")
    if not todo:
        return 0
    if not apply:
        print("Dry run. Run with --apply to change them on Modrinth.")
        print("CurseForge: the same files need their relations edited by hand on the site.")
        return 0

    token = keychain_read("modrinth")
    if not token:
        print("No Modrinth token in the Keychain (emf-modrinth-token).")
        return 1
    failed = 0
    for version_id, deps, mod in todo:
        try:
            patch(version_id, deps, token)
            print(f'ok      {mod["name"]} {mod["loader"]} {mod["game_version"]}')
        except Exception as e:  # noqa: BLE001
            failed += 1
            print(f'FAILED  {mod["name"]} {mod["loader"]} {mod["game_version"]}: {e}')
        time.sleep(0.3)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
