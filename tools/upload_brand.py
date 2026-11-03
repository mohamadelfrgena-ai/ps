#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
رفع **شعار AddonStation** إلى مخزن `site-assets` العام تحت `brand/`.

الغاية: قوالب بريد Supabase (تأكيد الحساب · استعادة كلمة المرور · إلخ) لا تقبل
صوراً مضمّنة (base64) — تحتاج **رابطاً عاماً**. فنرفع الشعار إلى مخزن المشروع
العام ونستخدم رابطه في القوالب:

    https://<ref>.supabase.co/storage/v1/object/public/site-assets/brand/addonstation-logo.png

الاستخدام:
    python3 tools/upload_brand.py --dir ./brand
    python3 tools/upload_brand.py --dir ./brand --url https://xxxx.supabase.co --key sb_secret_…
    python3 tools/upload_brand.py --dir ./brand --dry-run     # طباعة بلا رفع

لا مكتبات خارجية (urllib فقط).
"""
import argparse
import mimetypes
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUCKET = "site-assets"
PREFIX = "brand"
ALLOWED_EXT = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg"}


def upload_file(url: str, key: str, path: str, data: bytes, ctype: str, bucket: str = BUCKET):
    """يرفع ملفاً إلى مخزن Supabase ويعيد (الحالة، النص)."""
    headers = {"apikey": key, "x-upsert": "true", "cache-control": "3600"}
    if key.startswith("eyJ"):
        headers["Authorization"] = f"Bearer {key}"
    headers["Content-Type"] = ctype
    endpoint = f"{url.rstrip('/')}/storage/v1/object/{bucket}/" + urllib.parse.quote(path, safe="/")
    req = urllib.request.Request(endpoint, data=data, method="POST", headers=headers)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")


def public_url(url: str, path: str, bucket: str = BUCKET) -> str:
    return f"{url.rstrip('/')}/storage/v1/object/public/{bucket}/{urllib.parse.quote(path, safe='/')}"


def main() -> int:
    ap = argparse.ArgumentParser(description="رفع شعار AddonStation إلى site-assets/brand")
    ap.add_argument("--dir", default=os.path.join(ROOT, "brand"), help="مجلد الشعارات")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""), help="رابط المشروع")
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", ""), help="المفتاح السري sb_secret_…")
    ap.add_argument("--dry-run", action="store_true", help="طباعة ما سيُرفع بلا رفع")
    args = ap.parse_args()

    if not os.path.isdir(args.dir):
        print(f"✗ المجلد غير موجود: {args.dir}")
        return 2

    files = []
    for name in sorted(os.listdir(args.dir)):
        path = os.path.join(args.dir, name)
        if os.path.isfile(path) and os.path.splitext(name)[1].lower() in ALLOWED_EXT:
            files.append(path)
    if not files:
        print("✗ لا ملفات صور داخل المجلد")
        return 2

    if args.dry_run or not (args.url and args.key):
        print("— وضع تجريبي (بلا رفع) —" if not (args.url and args.key) else "— تجربة —")
        for path in files:
            name = os.path.basename(path)
            print(f"  • {name} ({os.path.getsize(path):,} بايت) → {PREFIX}/{name}")
        if not (args.url and args.key):
            print("  (مرّر --url و--key للرفع الفعلي)")
        return 0

    ok = 0
    print("— الروابط العامة بعد الرفع —")
    for path in files:
        name = os.path.basename(path)
        ctype = mimetypes.guess_type(name)[0] or "application/octet-stream"
        with open(path, "rb") as fh:
            data = fh.read()
        status, text = upload_file(args.url, args.key, f"{PREFIX}/{name}", data, ctype)
        if 200 <= status < 300:
            ok += 1
            print(f"  ✓ {public_url(args.url, f'{PREFIX}/{name}')}")
        else:
            print(f"  ✗ {name} → HTTP {status}: {text[:300]}")
            return 1

    print(f"\n✓ رُفع {ok} ملف/ملفات إلى {BUCKET}/{PREFIX}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
