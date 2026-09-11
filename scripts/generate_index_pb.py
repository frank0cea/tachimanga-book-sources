#!/usr/bin/env python3
import gzip
import hashlib
import json
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import index_pb2  # noqa: E402

ROOT = Path(__file__).resolve().parents[1]
SOURCES = json.loads((ROOT / "scripts" / "sources.json").read_text(encoding="utf-8"))
OUT = ROOT / "repo"
BASE = "https://testingcf.jsdelivr.net/gh/frank0cea/tachimanga-book-sources@main/repo"


def source_id(name: str, lang: str, version_id: int = 1) -> int:
    digest = hashlib.md5(f"{name}/{lang}/{version_id}".encode("utf-8")).hexdigest()
    value = int(digest[:16], 16)
    value &= ~(1 << 63)
    return value


def main() -> None:
    extensions = []
    for item in SOURCES:
        apk_name = item["apk"]
        ext = index_pb2.Extension(
            name=item["name"],
            packageName=item["pkg"],
            resources=index_pb2.Resources(
                apkUrl=f"{BASE}/{apk_name}",
                iconUrl=f"{BASE}/{apk_name}",
            ),
            extensionLib="1.4",
            versionCode=item["code"],
            versionName=item["version"],
            contentWarning=index_pb2.CONTENT_WARNING_SAFE,
        )
        for src in item["sources"]:
            ext.sources.add(
                id=source_id(src["name"], src["lang"], src.get("versionId", 1)),
                name=src["name"],
                language=src["lang"],
                homeUrl=src["baseUrl"],
            )
        extensions.append(ext)

    index = index_pb2.Index(
        name="书源",
        badgeLabel="SY",
        signingKey="",
        contact=index_pb2.Contact(website="https://github.com/frank0cea/tachimanga-book-sources"),
        extensionList=index_pb2.ExtensionList(extensions=extensions),
    )
    payload = gzip.compress(index.SerializeToString(deterministic=True), mtime=0)
    (OUT / "index.pb").write_bytes(payload)
    print(f"Wrote {OUT / 'index.pb'} ({len(payload)} bytes, {len(extensions)} extensions)")


if __name__ == "__main__":
    main()
