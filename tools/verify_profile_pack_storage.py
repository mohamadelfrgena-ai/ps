#!/usr/bin/env python3
"""Download every profile-pack object through its public URL and verify SHA-256."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor, as_completed
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DEFAULT_MANIFEST = ROOT / "supabase/profile-packs.json"


def fetch_and_verify(base_url: str, bucket: str, asset: dict) -> tuple[str, int]:
    encoded_path = urllib.parse.quote(asset["path"], safe="/")
    url = f"{base_url.rstrip('/')}/storage/v1/object/public/{bucket}/{encoded_path}"
    last_error: Exception | None = None
    for attempt in range(3):
        try:
            request = urllib.request.Request(url, headers={"Cache-Control": "no-cache"})
            with urllib.request.urlopen(request, timeout=60) as response:
                if response.status != 200:
                    raise RuntimeError(f"HTTP {response.status}")
                payload = response.read()
            if len(payload) != asset["bytes"]:
                raise RuntimeError(f"expected {asset['bytes']} bytes, got {len(payload)}")
            actual = hashlib.sha256(payload).hexdigest()
            if actual != asset["sha256"]:
                raise RuntimeError(f"SHA-256 mismatch ({actual})")
            return asset["path"], len(payload)
        except (OSError, RuntimeError, urllib.error.URLError) as error:
            last_error = error
            if attempt < 2:
                time.sleep(1 + attempt)
    raise RuntimeError(f"{asset['path']}: {last_error}")


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Verify public profile pack storage objects")
    parser.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    parser.add_argument("--manifest", type=Path, default=DEFAULT_MANIFEST)
    parser.add_argument("--workers", type=int, default=8)
    args = parser.parse_args(argv)

    if not args.url.startswith("https://"):
        parser.error("--url must be an https Supabase project URL")
    try:
        manifest = json.loads(args.manifest.read_text(encoding="utf-8"))
        bucket = manifest["bucket"]
        assets = manifest["assets"]
    except (OSError, KeyError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    failures: list[str] = []
    verified_bytes = 0
    workers = max(1, min(args.workers, 16))
    with ThreadPoolExecutor(max_workers=workers) as executor:
        futures = {
            executor.submit(fetch_and_verify, args.url, bucket, asset): asset["path"]
            for asset in assets
        }
        for future in as_completed(futures):
            try:
                path, size = future.result()
                verified_bytes += size
                print(f"OK {path} ({size} bytes)")
            except Exception as error:  # noqa: BLE001 - report all objects, then fail
                failures.append(str(error))
                print(f"ERROR {error}", file=sys.stderr)

    if failures:
        print(f"ERROR: {len(failures)} of {len(assets)} storage objects failed verification", file=sys.stderr)
        return 1
    print(f"OK: {len(assets)} public storage objects verified ({verified_bytes} bytes, SHA-256 exact)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
