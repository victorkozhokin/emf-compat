"""CurseForge side of the publisher: the same modules as projects.json, uploaded through the
CurseForge Upload API (https://support.curseforge.com/support/solutions/articles/9000197321).

The Upload API cannot list a project's files, so "already there" comes from two places: CFWidget
(api.cfwidget.com, a public mirror of project pages; it caches, so it can lag behind), and the
ledger cf_uploaded.json, where every file this tool uploads is written down at once.
"""

from __future__ import annotations

import json
import mimetypes
import re
import urllib.error
import urllib.request
import uuid
from pathlib import Path

UPLOAD_API = "https://minecraft.curseforge.com/api"
WIDGET = "https://api.cfwidget.com"
LEDGER = Path(__file__).resolve().parent / "cf_uploaded.json"
UA = "victorkozhokin/emf-compat publish script (github.com/victorkozhokin/emf-compat)"
LOADERS = {"forge": "Forge", "neoforge": "NeoForge", "fabric": "Fabric"}
RELATION = {"required": "requiredDependency", "optional": "optionalDependency",
            "incompatible": "incompatible", "embedded": "embeddedLibrary"}


def check_token(token: str) -> None:
    """Raises if CurseForge refuses the token; the game versions list needs one."""
    req = urllib.request.Request(f"{UPLOAD_API}/game/versions", headers={"X-Api-Token": token, "User-Agent": UA})
    with urllib.request.urlopen(req, timeout=30) as r:
        r.read(1)


def ledger() -> dict[str, list[list[str]]]:
    return json.loads(LEDGER.read_text(encoding="utf-8")) if LEDGER.exists() else {}


def remember(cf_id: int, key: tuple[str, str, str]) -> None:
    book = ledger()
    keys = book.setdefault(str(cf_id), [])
    if list(key) not in keys:
        keys.append(list(key))
    LEDGER.write_text(json.dumps(book, indent=1, sort_keys=True) + "\n", encoding="utf-8")


def file_key(name: str, versions: list[str]) -> tuple[str, str, str] | None:
    """(loader, game version, version number) of a file on CurseForge: the loader and game version from its
    tags, the number from its name - old uploads are named by an older scheme, so the name alone does not do."""
    number = re.search(r"[_-](\d+\.\d+\.\d+)\.jar$", name)
    loader = next((k for k, v in LOADERS.items() if v in versions), None)
    games = [v for v in versions if re.fullmatch(r"\d+(\.\d+)+", v)]
    if not (number and loader and len(games) == 1):
        return None
    return loader, games[0], number.group(1)


def published_keys(cf_id: int, cache: dict) -> set[tuple[str, str, str]]:
    """(loader, game version, version number) of every file already on this CurseForge project, as far as
    CFWidget and the ledger know."""
    if cf_id not in cache:
        keys = {tuple(k) for k in ledger().get(str(cf_id), [])}
        req = urllib.request.Request(f"{WIDGET}/{cf_id}", headers={"User-Agent": UA})
        with urllib.request.urlopen(req, timeout=60) as r:
            for f in json.load(r).get("files", []):
                key = file_key(f["name"], f.get("versions", []))
                if key:
                    keys.add(key)
        cache[cf_id] = keys
    return cache[cf_id]


def relations(mod: dict) -> tuple[list[dict], list[str]]:
    """CurseForge relations of a module, and the dependencies left out for want of a CurseForge slug."""
    out, missing = [], []
    for d in mod["dependencies"]:
        if not d.get("cf_slug"):
            missing.append(d["slug"])
            continue
        out.append({"slug": d["cf_slug"], "type": RELATION.get(d["dependency_type"], "optionalDependency")})
    return out, missing


def build_metadata(mod: dict, project: dict, changelog: str, file_name: str) -> dict:
    rel, _ = relations(mod)
    meta = {
        "changelog": changelog,
        "changelogType": "markdown",
        "displayName": file_name,
        "gameVersionNames": [mod["game_version"], LOADERS[mod["loader"]], *project.get("cf_environments", ["Client"])],
        "releaseType": "release",
    }
    if rel:
        meta["relations"] = {"projects": rel}
    return meta


def upload(cf_id: int, metadata: dict, jar: Path, token: str, key: tuple[str, str, str]) -> int:
    """POST upload-file as multipart: the metadata JSON plus the jar. Returns the new file id."""
    boundary = f"----emfcompat{uuid.uuid4().hex}"
    ctype = mimetypes.guess_type(jar.name)[0] or "application/java-archive"
    body = bytearray()
    body += f"--{boundary}\r\n".encode()
    body += b'Content-Disposition: form-data; name="metadata"\r\n'
    body += b"Content-Type: application/json\r\n\r\n"
    body += json.dumps(metadata).encode() + b"\r\n"
    body += f"--{boundary}\r\n".encode()
    body += f'Content-Disposition: form-data; name="file"; filename="{jar.name}"\r\n'.encode()
    body += f"Content-Type: {ctype}\r\n\r\n".encode()
    body += jar.read_bytes() + b"\r\n"
    body += f"--{boundary}--\r\n".encode()
    req = urllib.request.Request(f"{UPLOAD_API}/projects/{cf_id}/upload-file", data=bytes(body), method="POST", headers={
        "X-Api-Token": token,
        "User-Agent": UA,
        "Content-Type": f"multipart/form-data; boundary={boundary}",
    })
    with urllib.request.urlopen(req, timeout=180) as r:
        file_id = json.load(r)["id"]
    remember(cf_id, key)
    return file_id
