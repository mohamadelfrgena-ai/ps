#!/usr/bin/env python3
"""Create a short-lived Supabase Storage URL and verify its downloaded bytes."""

from __future__ import annotations

import argparse
import hashlib
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request


def server_headers(key: str) -> dict[str, str]:
    headers = {"apikey": key, "Content-Type": "application/json"}
    if key.startswith("eyJ"):
        headers["Authorization"] = f"Bearer {key}"
    return headers


def sign(url: str, key: str, bucket: str, path: str) -> str:
    encoded = urllib.parse.quote(f"{bucket}/{path}", safe="/")
    endpoint = f"{url.rstrip('/')}/storage/v1/object/sign/{encoded}"
    request = urllib.request.Request(
        endpoint,
        data=json.dumps({"expiresIn": 60}).encode("utf-8"),
        method="POST",
        headers=server_headers(key),
    )
    try:
        with urllib.request.urlopen(request, timeout=60) as response:
            payload = json.loads(response.read().decode("utf-8"))
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise RuntimeError(f"signed URL request failed with HTTP {error.code}: {detail[:500]}") from error
    signed = payload.get("signedURL") or payload.get("signedUrl")
    if not isinstance(signed, str) or not signed:
        raise RuntimeError("Storage did not return a signed URL")
    if signed.startswith("https://"):
        return signed
    if signed.startswith("/storage/v1/"):
        return f"{url.rstrip('/')}{signed}"
    return f"{url.rstrip('/')}/storage/v1{signed if signed.startswith('/') else '/' + signed}"


def download(signed_url: str) -> bytes:
    # Deliberately omit apikey/Authorization: this is how the Android client
    # consumes the self-contained, short-lived signed URL.
    request = urllib.request.Request(signed_url, method="GET")
    try:
        with urllib.request.urlopen(request, timeout=180) as response:
            return response.read()
    except urllib.error.HTTPError as error:
        detail = error.read().decode("utf-8", "replace")
        raise RuntimeError(f"signed download failed with HTTP {error.code}: {detail[:500]}") from error


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Verify a Supabase Storage signed download")
    parser.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    parser.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", ""))
    parser.add_argument("--bucket", required=True)
    parser.add_argument("--path", required=True)
    parser.add_argument("--expected-size", required=True, type=int)
    parser.add_argument("--expected-sha256", required=True)
    args = parser.parse_args(argv)

    if not args.url.startswith("https://"):
        parser.error("--url must be an https Supabase project URL")
    if not args.key or args.key.startswith("sb_publishable_"):
        parser.error("a Supabase server key is required")
    if args.expected_size <= 0:
        parser.error("--expected-size must be positive")

    try:
        signed_url = sign(args.url, args.key, args.bucket, args.path)
        payload = download(signed_url)
    except (OSError, RuntimeError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 1

    digest = hashlib.sha256(payload).hexdigest()
    if len(payload) != args.expected_size:
        print(f"ERROR: size mismatch: {len(payload)} != {args.expected_size}", file=sys.stderr)
        return 1
    if digest.lower() != args.expected_sha256.lower():
        print(f"ERROR: sha256 mismatch: {digest} != {args.expected_sha256}", file=sys.stderr)
        return 1

    print(
        json.dumps(
            {
                "bucket": args.bucket,
                "path": args.path,
                "bytes": len(payload),
                "sha256": digest,
                "signed_download": "verified",
            },
            ensure_ascii=True,
            sort_keys=True,
        )
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
