#!/usr/bin/env python3
"""Upload the October 2026 banner/avatar/verification asset request.

The database catalog and access rules live in
``supabase/48_creator_verification_and_featured_banners.sql``. This tool only
moves object bytes and removes the superseded public objects. It is dry-run by
default; pass ``--apply`` explicitly in a trusted environment that provides a
Supabase secret/service-role key.

No credential is printed or persisted.
"""
from __future__ import annotations

import argparse
import hashlib
import json
import mimetypes
import os
from pathlib import Path
import re
import sys
import urllib.error
import urllib.parse
import urllib.request

PUBLIC_BUCKET = "site-assets"
PRIVATE_BUCKET = "admin-assets"
DEFAULT_BANNER = "06aa4e3ff6eab5eecad52378a694114a.jpg"
ADMIN_VERIFICATION = {
    "16841-twitterverified.png",
    "7762-verified-blue.gif",
}
IMAGE_EXTENSIONS = {".png", ".jpg", ".jpeg", ".webp", ".gif"}

# These are the only objects in the retired Nature package. Keeping the list
# explicit makes deletion reviewable and prevents a broad prefix accident.
NATURE_OBJECTS = [
    "banners/nature/o2rigbig.png",
    "banners/nature/o36rigbig.png",
    "banners/nature/o3rigbig.png",
    "banners/nature/orig5big.png",
    "banners/nature/origbig.png",
    "banners/nature/Summer1.png",
    "banners/nature/Summer2.png",
    "banners/nature/Summer3.png",
    "banners/nature/Summer4.png",
    "banners/nature/Summer5.png",
    "banners/nature/Summer6.png",
    "banners/nature/Summer7.png",
    "banners/nature/Summer8.png",
]

# Explicitly remove the unrestricted copies called out in the request. Their
# replacements are uploaded to admin-assets under admin-private/... paths.
LEGACY_PUBLIC_VERIFICATION = [
    "items/random-things/16841-twitterverified.png",
    "items/random-things/7762-verified-blue.gif",
]

EXPECTED_COUNTS = {"banner": 32, "avatar": 3, "verification": 21}


def headers(key: str, content_type: str | None = None) -> dict[str, str]:
    result = {"apikey": key, "Accept": "application/json"}
    # Legacy service-role keys are JWTs; new sb_secret keys belong in apikey.
    if key.startswith("eyJ"):
        result["Authorization"] = "Bearer " + key
    if content_type:
        result["Content-Type"] = content_type
    return result


def request(
    method: str,
    url: str,
    key: str,
    payload: bytes | None = None,
    content_type: str | None = None,
    extra: dict[str, str] | None = None,
) -> tuple[int, bytes]:
    merged = headers(key, content_type)
    merged.update(extra or {})
    req = urllib.request.Request(url, data=payload, method=method, headers=merged)
    try:
        with urllib.request.urlopen(req, timeout=180) as response:
            return response.status, response.read()
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace")[:500]
        raise RuntimeError(f"HTTP {exc.code}: {body}") from exc


def safe_name(name: str) -> str:
    source = Path(name)
    stem = re.sub(r"[^A-Za-z0-9._-]+", "-", source.stem).strip("-")
    if not stem:
        raise ValueError(f"Unsafe empty filename after normalization: {name!r}")
    return stem + source.suffix.lower()


def encoded(path: str) -> str:
    return urllib.parse.quote(path.strip().lstrip("/"), safe="/")


def upload(base: str, key: str, bucket: str, remote: str, local: Path) -> None:
    payload = local.read_bytes()
    mime = mimetypes.guess_type(local.name)[0] or "application/octet-stream"
    endpoint = f"{base.rstrip('/')}/storage/v1/object/{bucket}/{encoded(remote)}"
    request(
        "POST",
        endpoint,
        key,
        payload=payload,
        content_type=mime,
        extra={"x-upsert": "true", "cache-control": "3600"},
    )


def list_objects(base: str, key: str, bucket: str, prefix: str) -> list[str]:
    """List every direct object under one reviewed storage prefix."""
    endpoint = f"{base.rstrip('/')}/storage/v1/object/list/{bucket}"
    found: list[str] = []
    offset = 0
    while True:
        payload = json.dumps({
            "prefix": prefix.strip("/"),
            "limit": 1000,
            "offset": offset,
            "sortBy": {"column": "name", "order": "asc"},
        }).encode("utf-8")
        _, body = request(
            "POST",
            endpoint,
            key,
            payload=payload,
            content_type="application/json",
        )
        rows = json.loads(body.decode("utf-8"))
        if not isinstance(rows, list):
            raise RuntimeError(f"Unexpected storage listing response for {prefix}")
        names = [row.get("name") for row in rows if isinstance(row, dict)]
        names = [name for name in names if isinstance(name, str) and name]
        found.extend(f"{prefix.strip('/')}/{name.lstrip('/')}" for name in names)
        if len(rows) < 1000:
            break
        offset += len(rows)
    return found


def delete_objects(base: str, key: str, bucket: str, paths: list[str]) -> None:
    if not paths:
        return

    endpoint = f"{base.rstrip('/')}/storage/v1/object/{bucket}"
    request(
        "DELETE",
        endpoint,
        key,
        payload=json.dumps({"prefixes": paths}).encode("utf-8"),
        content_type="application/json",
    )


def digest(path: Path) -> str:
    hasher = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            hasher.update(chunk)
    return hasher.hexdigest()


def collect(banner_dir: Path, verification_dir: Path):
    banners = sorted(
        path for path in banner_dir.iterdir()
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
    )
    avatar_dir = banner_dir / "New folder (6)"
    avatars = sorted(
        path for path in avatar_dir.iterdir()
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
    )
    verification = sorted(
        path for path in verification_dir.iterdir()
        if path.is_file() and path.suffix.lower() in IMAGE_EXTENSIONS
    )

    counts = {
        "banner": len(banners),
        "avatar": len(avatars),
        "verification": len(verification),
    }
    if counts != EXPECTED_COUNTS:
        raise RuntimeError(f"Unexpected archive contents: {counts}, expected {EXPECTED_COUNTS}")
    if not any(path.name == DEFAULT_BANNER for path in banners):
        raise RuntimeError(f"Required default banner is absent: {DEFAULT_BANNER}")
    if {path.name for path in verification}.intersection(ADMIN_VERIFICATION) != ADMIN_VERIFICATION:
        raise RuntimeError("The two required admin verification files are absent")

    operations: list[tuple[str, str, Path, str]] = []
    for path in banners:
        operations.append((PUBLIC_BUCKET, f"banners/featured/{safe_name(path.name)}", path, "banner"))
    for path in avatars:
        operations.append((PUBLIC_BUCKET, f"profiles/animated/{safe_name(path.name)}", path, "avatar"))
    for path in verification:
        if path.name in ADMIN_VERIFICATION:
            bucket = PRIVATE_BUCKET
            remote = f"admin-private/items/verification/{safe_name(path.name)}"
            kind = "admin verification"
        else:
            bucket = PUBLIC_BUCKET
            remote = f"items/verification/{safe_name(path.name)}"
            kind = "creator verification"
        operations.append((bucket, remote, path, kind))
    return operations


def verify_object(base: str, key: str, bucket: str, remote: str, local: Path) -> None:
    endpoint = f"{base.rstrip('/')}/storage/v1/object/{bucket}/{encoded(remote)}"
    _, body = request("GET", endpoint, key)
    if len(body) != local.stat().st_size:
        raise RuntimeError(
            f"Verification size mismatch for {bucket}/{remote}: "
            f"{len(body)} != {local.stat().st_size}"
        )
    if hashlib.sha256(body).hexdigest() != digest(local):
        raise RuntimeError(f"Verification hash mismatch for {bucket}/{remote}")


def main() -> int:
    parser = argparse.ArgumentParser(description="Upload requested profile assets safely")
    parser.add_argument("--banner-dir", type=Path, required=True)
    parser.add_argument("--verification-dir", type=Path, required=True)
    parser.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    parser.add_argument(
        "--key",
        default=os.environ.get("SUPABASE_SECRET_KEY", os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")),
    )
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()

    if not args.banner_dir.is_dir() or not args.verification_dir.is_dir():
        print("ERROR: extracted banner or verification directory is missing")
        return 2
    operations = collect(args.banner_dir, args.verification_dir)
    total = sum(local.stat().st_size for _, _, local, _ in operations)
    print(f"Validated {len(operations)} assets ({total / 1024 / 1024:.2f} MiB)")
    for bucket, remote, local, kind in operations:
        print(f"  {kind}: {local.name} -> {bucket}/{remote}")

    if not args.apply:
        print("Dry run only. Pass --apply to upload and remove superseded objects.")
        return 0
    if not args.url.startswith("https://") or not args.key:
        print("ERROR: SUPABASE_URL and SUPABASE_SECRET_KEY are required for --apply")
        return 2
    if args.key.startswith("sb_publishable_"):
        print("ERROR: a publishable key cannot deploy storage assets")
        return 2

    completed: list[tuple[str, str, Path]] = []
    for index, (bucket, remote, local, kind) in enumerate(operations, 1):
        upload(args.url, args.key, bucket, remote, local)
        completed.append((bucket, remote, local))
        print(f"[{index}/{len(operations)}] uploaded {bucket}/{remote}")

    # Verify every byte before deleting a superseded object.
    for index, (bucket, remote, local) in enumerate(completed, 1):
        verify_object(args.url, args.key, bucket, remote, local)
        print(f"[{index}/{len(completed)}] verified {bucket}/{remote}")

    # Enumerate the retired prefix after all replacement bytes have verified.
    # This removes the whole Nature package, including any production object
    # that was not present in the historical source manifest.
    remote_nature = list_objects(
        args.url,
        args.key,
        PUBLIC_BUCKET,
        "banners/nature",
    )
    retired = sorted(set(NATURE_OBJECTS + remote_nature + LEGACY_PUBLIC_VERIFICATION))
    delete_objects(args.url, args.key, PUBLIC_BUCKET, retired)
    print(
        f"Removed {len(set(NATURE_OBJECTS + remote_nature))} retired Nature objects and "
        f"{len(LEGACY_PUBLIC_VERIFICATION)} legacy unrestricted verification objects"
    )
    print("Storage deployment complete; catalog rules are in SQL migration 48.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
