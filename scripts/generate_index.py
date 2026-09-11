#!/usr/bin/env python3
"""Build Tachimanga index.min.json from assembled APKs."""

from __future__ import annotations

import hashlib
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SOURCES = json.loads((ROOT / "scripts" / "sources.json").read_text(encoding="utf-8"))
OUT = ROOT / "repo-dist"


def source_id(name: str, lang: str, version_id: int = 1) -> str:
    key = f"{name}/{lang}/{version_id}"
    digest = hashlib.md5(key.encode("utf-8")).hexdigest()
    value = int(digest[:16], 16)
    value &= ~(1 << 63)
    return str(value)


def find_apk(pkg_suffix: str) -> Path | None:
    matches = list(ROOT.glob(f"**/outputs/apk/**/*{pkg_suffix}*.apk"))
    matches += list(ROOT.glob(f"**/*{pkg_suffix}*.apk"))
    return matches[0] if matches else None


def main() -> None:
    if OUT.exists():
        shutil.rmtree(OUT)
    OUT.mkdir(parents=True)

    index = []
    missing = []
    for item in SOURCES:
        pkg = item["pkg"]
        suffix = pkg.split("extension.", 1)[-1]
        apk_path = find_apk(suffix)
        if apk_path is None:
            missing.append(pkg)
            continue
        apk_name = item["apk"]
        shutil.copy2(apk_path, OUT / apk_name)
        sources = []
        for src in item["sources"]:
            sources.append(
                {
                    "name": src["name"],
                    "lang": src["lang"],
                    "id": source_id(src["name"], src["lang"], src.get("versionId", 1)),
                    "baseUrl": src["baseUrl"],
                    "versionId": src.get("versionId", 1),
                }
            )
        index.append(
            {
                "name": f"Tachiyomi: {item['name']}",
                "pkg": pkg,
                "apk": apk_name,
                "lang": item["lang"],
                "code": item["code"],
                "version": item["version"],
                "nsfw": 0,
                "sources": sources,
            }
        )

    (OUT / "index.min.json").write_text(
        json.dumps(index, ensure_ascii=False, separators=(",", ":")),
        encoding="utf-8",
    )
    (OUT / "index.json").write_text(
        json.dumps(index, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    print(f"Wrote {len(index)} extensions to {OUT}")
    if missing:
        raise SystemExit("Missing APKs for: " + ", ".join(missing))


if __name__ == "__main__":
    main()
