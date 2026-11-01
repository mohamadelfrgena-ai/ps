#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Upload content release assets (covers + packs) to the public site-assets bucket.

Why: the GitHub repo is private, so console/phone clients cannot fetch
https://github.com/.../releases/download/... URLs. This script mirrors the same
asset names into Supabase Storage under content/<slug>/<file> where they are
publicly reachable via /storage/v1/object/public/site-assets/...

Layout:
    <src>/<slug>/<file>  ->  site-assets/content/<slug>/<file>

Usage (CI supplies the secrets):
    python3 tools/upload_content_storage.py --url https://xxx.supabase.co \
        --key sb_secret_… --src dl --verify

Re-running is safe (x-upsert: true).
"""
import argparse
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

BUCKET = "site-assets"


def headers(key: str, content_type=None) -> dict:
    h = {"apikey": key, "x-upsert": "true", "cache-control": "3600"}
    if key.startswith("eyJ"):
        h["Authorization"] = f"Bearer {key}"
    if content_type:
        h["Content-Type"] = content_type
    return h


def request(url: str, key: str, data=None, method="GET", content_type=None):
    req = urllib.request.Request(url, data=data, method=method, headers=headers(key, content_type))
    with urllib.request.urlopen(req, timeout=180) as resp:
        return resp.status, resp.read()


def public_url(base: str, path: str) -> str:
    return "{}/storage/v1/object/public/{}/{}".format(base.rstrip("/"), BUCKET, urllib.parse.quote(path, safe="/"))


def main() -> int:
    ap = argparse.ArgumentParser(description="Upload content assets to site-assets")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")))
    ap.add_argument("--src", default="dl", help="root folder with <slug>/<file> trees")
    ap.add_argument("--prefix", default="content", help="bucket prefix (default content)")
    ap.add_argument("--verify", action="store_true", help="GET each public URL after upload")
    ap.add_argument("--sleep", type=float, default=0.05)
    args = ap.parse_args()

    if not args.url.startswith("http"):
        print("✗ missing --url https://xxx.supabase.co")
        return 2
    if not args.key:
        print("✗ missing --key sb_secret_…")
        return 2
    if args.key.startswith("sb_publishable_"):
        print("✗ publishable key cannot upload — need the secret key")
        return 2
    if not os.path.isdir(args.src):
        print("✗ source folder not found:", args.src)
        return 2

    ok = fail = 0
    for slug in sorted(os.listdir(args.src)):
        slug_dir = os.path.join(args.src, slug)
        if not os.path.isdir(slug_dir):
            continue
        for name in sorted(os.listdir(slug_dir)):
            local = os.path.join(slug_dir, name)
            if not os.path.isfile(local):
                continue
            rel = "{}/{}/{}".format(args.prefix, slug, name)
            ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
            endpoint = "{}/storage/v1/object/{}/{}".format(args.url.rstrip("/"), BUCKET, urllib.parse.quote(rel, safe="/"))
            with open(local, "rb") as fh:
                payload = fh.read()
            try:
                status, _ = request(endpoint, args.key, payload, method="POST", content_type=ctype)
                print("✓ %s (%d bytes) -> %s" % (rel, len(payload), status))
                ok += 1
            except urllib.error.HTTPError as e:
                print("✗ %s -> HTTP %d %s" % (rel, e.code, e.read()[:120]))
                fail += 1
            except Exception as e:  # noqa: BLE001
                print("✗ %s -> %s" % (rel, e))
                fail += 1
            time.sleep(args.sleep)

    print("uploaded ok=%d fail=%d" % (ok, fail))
    if fail:
        return 1

    if args.verify:
        vfail = 0
        for slug in sorted(os.listdir(args.src)):
            slug_dir = os.path.join(args.src, slug)
            if not os.path.isdir(slug_dir):
                continue
            for name in sorted(os.listdir(slug_dir)):
                rel = "{}/{}/{}".format(args.prefix, slug, name)
                try:
                    status, body = request(public_url(args.url, rel), args.key)
                    print("public %s -> %d (%d bytes)" % (rel, status, len(body)))
                except urllib.error.HTTPError as e:
                    print("✗ public %s -> HTTP %d" % (rel, e.code))
                    vfail += 1
        if vfail:
            return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
