#!/usr/bin/env python3
"""Static integrity guard for the pinned profile-pack assets and migration."""

from __future__ import annotations

import hashlib
import json
import subprocess
import sys
from collections import Counter
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
MANIFEST_PATH = ROOT / "supabase/profile-packs.json"
FILES_ROOT = ROOT / "supabase/profile-pack-files"
MIGRATION_PATH = ROOT / "supabase/46_profile_avatar_packs.sql"
ALL_IN_ONE_PATH = ROOT / "supabase/00_ALL_IN_ONE.sql"
ALLOWED_SUFFIXES = {".jpg", ".jpeg", ".png", ".webp", ".gif"}
EXPECTED = {
    "Profiles": {"count": 24, "plan": "epic", "prefix": "profiles/profiles/"},
    "Profile 2": {"count": 11, "plan": "legendary", "prefix": "profiles/profile-2/"},
}


def sha256(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def fail(message: str) -> None:
    raise AssertionError(message)


def main() -> int:
    try:
        manifest = json.loads(MANIFEST_PATH.read_text(encoding="utf-8"))
        if manifest.get("version") != 1 or manifest.get("bucket") != "site-assets":
            fail("unexpected profile pack manifest version/bucket")
        assets = manifest.get("assets")
        packs = manifest.get("packs")
        if not isinstance(assets, list) or not isinstance(packs, list):
            fail("manifest assets/packs must be arrays")
        if len(assets) != 35 or len(packs) != 2:
            fail(f"expected 35 assets in two packs, found {len(assets)} assets/{len(packs)} packs")

        paths = [asset["path"] for asset in assets]
        if len(paths) != len(set(paths)):
            fail("duplicate remote path in manifest")
        if any(path.startswith("/") or ".." in Path(path).parts or "\\" in path for path in paths):
            fail("unsafe remote path in manifest")
        if "profiles/profiles/onefree.png" in paths:
            fail("onefree.png must remain separate from paid pack manifests")
        if any(asset.get("plan") == "free" for asset in assets):
            fail("a paid profile pack unexpectedly contains a free asset")
        if any(asset.get("animated") for asset in assets):
            fail("these two pinned profile packs are expected to contain only still images")

        counts = Counter(asset["collection"] for asset in assets)
        for collection, rule in EXPECTED.items():
            matching = [asset for asset in assets if asset["collection"] == collection]
            if counts[collection] != rule["count"]:
                fail(f"{collection}: expected {rule['count']} assets, found {counts[collection]}")
            if any(asset["plan"] != rule["plan"] for asset in matching):
                fail(f"{collection}: expected every asset to require {rule['plan']}")
            if any(not asset["path"].startswith(rule["prefix"]) for asset in matching):
                fail(f"{collection}: asset escaped expected namespace {rule['prefix']}")
            if sorted(asset["order"] for asset in matching) != list(range(1, rule["count"] + 1)):
                fail(f"{collection}: sort orders must be contiguous from 1")

        declared = set(paths)
        actual = {
            path.relative_to(FILES_ROOT).as_posix()
            for path in FILES_ROOT.rglob("*")
            if path.is_file()
        }
        if actual != declared:
            missing = sorted(declared - actual)
            extra = sorted(actual - declared)
            fail(f"pack files disagree with manifest; missing={missing[:3]}, extra={extra[:3]}")

        for asset in assets:
            path = FILES_ROOT / asset["path"]
            if path.suffix.lower() not in ALLOWED_SUFFIXES:
                fail(f"unsupported image extension: {asset['path']}")
            if path.stat().st_size != asset["bytes"]:
                fail(f"byte count mismatch: {asset['path']}")
            if sha256(path) != asset["sha256"]:
                fail(f"SHA-256 mismatch: {asset['path']}")
            if hashlib.md5(path.read_bytes()).hexdigest() != asset["md5"]:  # noqa: S324 - object integrity only
                fail(f"MD5/storage ETag mismatch: {asset['path']}")

        pack_map = {pack["label"]: pack for pack in packs}
        for collection, rule in EXPECTED.items():
            pack = pack_map.get(collection)
            if not pack or pack["count"] != rule["count"] or pack["plan"] != rule["plan"]:
                fail(f"pack metadata mismatch: {collection}")
            if pack["paths"] != [asset["path"] for asset in assets if asset["collection"] == collection]:
                fail(f"pack path index mismatch: {collection}")
            if len(pack.get("archive_sha256", "")) != 64 or len(pack.get("git_blob_sha", "")) != 40:
                fail(f"pack source is not pinned: {collection}")
            if not str(pack.get("source_url", "")).startswith("https://github.com/mohamadelfrgena-ai/vae/"):
                fail(f"unexpected pack source: {collection}")

        result = subprocess.run(
            [sys.executable, str(ROOT / "tools/build_profile_packs_sql.py"), "--check"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )
        if result.returncode:
            fail((result.stdout + result.stderr).strip())

        migration = MIGRATION_PATH.read_text(encoding="utf-8")
        for path in paths:
            if migration.count("'" + path.replace("'", "''") + "'") < 2:
                fail(f"migration does not both seed and verify {path}")
        required_sql = (
            "collection = 'Profiles'",
            "required_plan = 'epic'",
            "collection = 'Profile 2'",
            "required_plan = 'legendary'",
            "profiles/profiles/onefree.png",
            "sole enabled free avatar",
            "from (values",
            "left join storage.objects",
        )
        for fragment in required_sql:
            if fragment not in migration:
                fail(f"migration safety fragment missing: {fragment}")

        all_in_one = ALL_IN_ONE_PATH.read_text(encoding="utf-8")
        marker = "-- ##  القسم: 46_profile_avatar_packs.sql"
        if marker not in all_in_one:
            fail("00_ALL_IN_ONE.sql does not include migration 46")
        if all_in_one.count(marker) != 1:
            fail("00_ALL_IN_ONE.sql includes migration 46 more than once")

    except (AssertionError, KeyError, OSError, TypeError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    print("OK: 24 Epic Profiles + 11 Legendary Profile 2 images match their pinned manifest")
    print("OK: onefree.png is excluded from both paid packs and guarded as the sole free avatar")
    print("OK: migration 46 is generated, storage-gated, and included once in 00_ALL_IN_ONE.sql")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
