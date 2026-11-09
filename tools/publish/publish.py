#!/usr/bin/env python3
"""Uploads built jars to Modrinth from the map in projects.json.

Dry run by default — it prints exactly what would be sent and touches nothing. A real upload needs
both `--upload` and a token in MODRINTH_TOKEN; the token is never printed or written anywhere.

    python3 tools/publish/publish.py                      # dry run, everything publishable
    python3 tools/publish/publish.py --only core          # one addon
    python3 tools/publish/publish.py --show-payload       # dry run + the JSON for each version
    python3 tools/publish/publish.py --upload             # the real thing

A version already on Modrinth with the same loader + game version + version number is skipped, so
running twice cannot create duplicates. Build first: the jars come from upload/, which `./gradlew
build` fills.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import re
import sys
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
MAP = Path(__file__).resolve().parent / "projects.json"
API = "https://api.modrinth.com/v2"
UA = "victorkozhokin/emf-compat publish script (github.com/victorkozhokin/emf-compat)"


def api_get(path: str) -> object:
    req = urllib.request.Request(f"{API}{path}", headers={"User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        return json.load(r)


def published_versions(project_id: str, cache: dict) -> set[tuple[str, str, str]]:
    """Every (loader, game_version, version_number) already on Modrinth for this project."""
    if project_id not in cache:
        out = set()
        for v in api_get(f"/project/{project_id}/version"):
            for loader in v["loaders"]:
                for game in v["game_versions"]:
                    out.add((loader, game, v["version_number"]))
        cache[project_id] = out
    return cache[project_id]


def changelog_section(path: Path, version: str) -> str | None:
    """The `## <version>` block of a changelog, without its heading."""
    if not path.exists():
        return None
    text = path.read_text(encoding="utf-8")
    m = re.search(rf"(?m)^##\s*\[?{re.escape(version)}\]?.*?$(.*?)(?=^##\s|\Z)", text, re.S)
    return m.group(1).strip() if m else None


def build_payload(mod: dict, changelog: str) -> dict:
    return {
        "name": mod["name"],
        "version_number": mod["version_number"],
        "changelog": changelog,
        "dependencies": [
            {"project_id": d["project_id"], "dependency_type": d["dependency_type"]}
            for d in mod["dependencies"]
        ],
        "game_versions": [mod["game_version"]],
        "version_type": "release",
        "loaders": [mod["loader"]],
        "featured": False,
        "status": "listed",
        "project_id": mod["project_id"],
        "file_parts": ["file"],
        "primary_file": "file",
    }


def upload(payload: dict, jar: Path, token: str) -> str:
    """POST /version as multipart: the JSON part plus the jar. Returns the new version id."""
    boundary = f"----emfcompat{uuid.uuid4().hex}"
    ctype = mimetypes.guess_type(jar.name)[0] or "application/java-archive"
    body = bytearray()
    body += f"--{boundary}\r\n".encode()
    body += b'Content-Disposition: form-data; name="data"\r\n'
    body += b"Content-Type: application/json\r\n\r\n"
    body += json.dumps(payload).encode() + b"\r\n"
    body += f"--{boundary}\r\n".encode()
    body += f'Content-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'.encode()
    body += f"Content-Type: {ctype}\r\n\r\n".encode()
    body += jar.read_bytes() + b"\r\n"
    body += f"--{boundary}--\r\n".encode()

    req = urllib.request.Request(f"{API}/version", data=bytes(body), method="POST", headers={
        "Authorization": token,
        "User-Agent": UA,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    })
    with urllib.request.urlopen(req, timeout=180) as r:
        return json.load(r)["id"]


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--upload", action="store_true", help="actually upload (needs MODRINTH_TOKEN)")
    ap.add_argument("--only", metavar="ADDON", help="one addon, e.g. core")
    ap.add_argument("--loader", help="forge / neoforge / fabric")
    ap.add_argument("--mc", metavar="VERSION", help="one game version, e.g. 1.21.1")
    ap.add_argument("--show-payload", action="store_true", help="print the JSON of every version")
    args = ap.parse_args()

    data = json.loads(MAP.read_text(encoding="utf-8"))
    mods = [m for m in data["modules"] if m["publish"]]
    for key, value in (("addon", args.only), ("loader", args.loader), ("game_version", args.mc)):
        if value:
            mods = [m for m in mods if m[key] == value]
    if not mods:
        print("nothing matches the filters")
        return 1

    # The shared map also contains CurseForge-only projects. They have no Modrinth endpoint.
    for mod in mods:
        if not mod.get("project_id"):
            print(f'skip (no Modrinth project): {mod["name"]} {mod["loader"]} {mod["game_version"]}')
    mods = [m for m in mods if m.get("project_id")]
    if not mods:
        print("nothing to publish on Modrinth; use publish_tui.py for CurseForge")
        return 0

    token = os.environ.get("MODRINTH_TOKEN", "")
    if args.upload and not token:
        print("MODRINTH_TOKEN is not set — refusing to upload", file=sys.stderr)
        return 2

    cache: dict[str, set] = {}
    planned, skipped, problems = [], [], []
    for mod in mods:
        jar = ROOT / mod["file"]
        changelog = changelog_section(ROOT / mod["changelog"], mod["version_number"])
        key = (mod["loader"], mod["game_version"], mod["version_number"])
        if not jar.is_file() or jar.stat().st_size == 0:
            problems.append(f'{mod["name"]}: no jar at {mod["file"]} — build first')
            continue
        if changelog is None:
            problems.append(f'{mod["name"]}: no "## {mod["version_number"]}" section in {mod["changelog"]}')
            continue
        (skipped if key in published_versions(mod["project_id"], cache) else planned).append((mod, jar, changelog))

    width = max((len(m["name"]) for m, _, _ in planned), default=10)
    print(f"== to upload: {len(planned)}   already there: {len(skipped)}   problems: {len(problems)}\n")
    for mod, jar, changelog in planned:
        deps = ", ".join(f'{d["slug"]}:{d["dependency_type"][:3]}' for d in mod["dependencies"])
        first = changelog.splitlines()[0] if changelog else ""
        print(f'  {mod["name"]:{width}}  {mod["loader"]:9}{mod["game_version"]:9}{jar.stat().st_size // 1024:5} KB')
        print(f'  {"":{width}}  deps: {deps}')
        print(f'  {"":{width}}  changelog: {first[:100]}')
        if args.show_payload:
            print(json.dumps(build_payload(mod, changelog), indent=2))
    for mod, _, _ in skipped:
        print(f'  skip (already published): {mod["name"]} {mod["loader"]} {mod["game_version"]}')
    for p in problems:
        print(f"  PROBLEM: {p}")
    if problems:
        print("\nfix the problems first — nothing was uploaded")
        return 1
    if not args.upload:
        print("\ndry run: nothing was sent. Add --upload (and MODRINTH_TOKEN) to publish.")
        return 0

    print(f"\nuploading {len(planned)} versions…")
    for mod, jar, changelog in planned:
        try:
            version_id = upload(build_payload(mod, changelog), jar, token)
            print(f'  ok   {mod["name"]} {mod["loader"]} {mod["game_version"]} → {version_id}')
        except urllib.error.HTTPError as e:
            print(f'  FAIL {mod["name"]} {mod["loader"]} {mod["game_version"]}: HTTP {e.code} {e.read()[:300]!r}')
            return 1
        time.sleep(1)  # Modrinth allows 300 requests a minute; no reason to crowd it
    return 0


if __name__ == "__main__":
    sys.exit(main())
