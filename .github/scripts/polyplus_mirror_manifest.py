#!/usr/bin/env python3
"""Build polyplus-mirror.json from the CI-built jars (run from the repo root).

Reads every jar under ./jars (any nesting), validates the expected
``polyplus-<mod_version>+<mc_version>.jar`` naming, computes the sha1 of each
jar, and writes:

  ./polyplus-mirror.json   the manifest the AethelONE launcher fetches
  ./dist/<jar>             a flat copy of the jars for the release upload
"""

import hashlib
import json
import os
import re
import shutil
import sys
import urllib.parse
from pathlib import Path

JAR_RE = re.compile(r"^polyplus-(?P<mod>[0-9][0-9A-Za-z.\-]*)\+(?P<mc>[0-9A-Za-z.\-]+)\.jar$")
EXPECTED_VERSIONS = 10


def sha1_of(path: Path) -> str:
    digest = hashlib.sha1()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1 << 20), b""):
            digest.update(chunk)
    return digest.hexdigest()


def main() -> int:
    jars = sorted(path for path in Path("jars").rglob("*.jar") if path.is_file())
    if len(jars) != EXPECTED_VERSIONS:
        print(f"expected {EXPECTED_VERSIONS} jars, found {len(jars)}", file=sys.stderr)
        for jar in jars:
            print(f"  {jar}", file=sys.stderr)
        return 1

    repo = os.environ.get("GITHUB_REPOSITORY", "aethelreborn/AethelOnePLUS")
    entries = []
    seen_mc = set()
    for jar in jars:
        match = JAR_RE.match(jar.name)
        if not match:
            print(f"unexpected jar name: {jar.name}", file=sys.stderr)
            return 1
        mc = match.group("mc")
        if mc in seen_mc:
            print(f"duplicate mc version: {mc}", file=sys.stderr)
            return 1
        seen_mc.add(mc)
        entries.append(
            {
                "mc_version": mc,
                "loader": "ornithe" if mc == "1.8.9" else "fabric",
                "file_name": jar.name,
                "url": f"https://github.com/{repo}/releases/download/continuous/{urllib.parse.quote(jar.name)}",
                "sha1": sha1_of(jar),
                "size": jar.stat().st_size,
                "mod_version": match.group("mod"),
            }
        )

    entries.sort(key=lambda entry: entry["mc_version"])
    manifest = {
        "source_repo": repo,
        "source_ref": os.environ.get("GITHUB_REF_NAME", ""),
        "run_id": os.environ.get("GITHUB_RUN_ID", ""),
        "versions": entries,
    }

    dist = Path("dist")
    if dist.exists():
        shutil.rmtree(dist)
    dist.mkdir()
    for jar in jars:
        shutil.copy2(jar, dist / jar.name)

    Path("polyplus-mirror.json").write_text(json.dumps(manifest, indent=2, sort_keys=True) + "\n")
    print(json.dumps(manifest, indent=2, sort_keys=True))
    return 0


if __name__ == "__main__":
    sys.exit(main())
