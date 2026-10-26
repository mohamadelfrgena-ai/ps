#!/usr/bin/env python3
"""Upload one explicitly named object to Supabase Storage with a server key.

The secret is read from SUPABASE_SECRET_KEY by default and is never printed.
New sb_secret_* keys belong only in the apikey header; legacy service-role JWTs
also require Authorization: Bearer.
"""

from __future__ import annotations

import argparse
import json
import mimetypes
import os
import pathlib
import sys
import urllib.error
import urllib.parse
import urllib.request


def safe_segmented_path(value: str, label: str) -> str:
    cleaned = value.strip().strip("/")
    parts = cleaned.split("/")
    if not cleaned or any(part in ("", ".", "..") for part in parts) or "\\" in cleaned or "\x00" in cleaned:
        raise ValueError(f"unsafe {label}: {value!r}")
    return cleaned


def request_headers(key: str, content_type: str) -> dict[str, str]:
    headers = {
        "apikey": key,
        "Content-Type": content_type,
        "x-upsert": "true",
        "cache-control": "3600",
    }
    if key.startswith("eyJ"):
        headers["Authorization"] = f"Bearer {key}"
    return headers


def upload(url: str, key: str, bucket: str, object_path: str, source: pathlib.Path, content_type: str) -> tuple[int, str]:
    encoded = urllib.parse.quote(f"{bucket}/{object_path}", safe="/")
    endpoint = f"{url.rstrip('/')}/storage/v1/object/{encoded}"
    request = urllib.request.Request(
        endpoint,
        data=source.read_bytes(),
        method="POST",
        headers=request_headers(key, content_type),
    )
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            return response.status, response.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise RuntimeError(f"storage upload failed with HTTP {error.code}: {detail[:500]}") from error


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Upload one object to Supabase Storage")
    parser.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    parser.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", ""))
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--path", required=True)
    parser.add_argument("--file", required=True, type=pathlib.Path)
    parser.add_argument("--content-type", default="")
    args = parser.parse_args(argv)

    if not args.url.startswith("https://"):
        parser.error("--url must be an https Supabase project URL")
    if not args.key:
        parser.error("a server key is required through --key or SUPABASE_SECRET_KEY")
    if args.key.startswith("sb_publishable_"):
        parser.error("a publishable key cannot upload protected content")
    if not args.file.is_file():
        parser.error(f"source file does not exist: {args.file}")

    try:
        bucket = safe_segmented_path(args.bucket, "bucket")
        if "/" in bucket:
            raise ValueError("bucket must contain one path segment")
        object_path = safe_segmented_path(args.path, "object path")
        content_type = args.content_type or mimetypes.guess_type(args.file.name)[0] or "application/octet-stream"
        status, body = upload(args.url, args.key, bucket, object_path, args.file, content_type)
    except (OSError, RuntimeError, ValueError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    try:
        response = json.loads(body) if body else {}
    except json.JSONDecodeError:
        response = {"response": body[:300]}
    print(
        json.dumps(
            {
                "status": status,
                "bucket": bucket,
                "path": object_path,
                "bytes": args.file.stat().st_size,
                "content_type": content_type,
                "storage_response": response,
            },
            ensure_ascii=True,
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
