#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
رفع أصول المتجر (الأدوات 105 + الملصقات 181 + الأفاتار الافتراضي) إلى مخزن
Supabase العام `site-assets` — بلا أي مكتبات خارجية (urllib فقط).

ملاحظة أمنية: هذا السكربت يعمل على حاسوبك ويحتاج **المفتاح السري** (sb_secret_…)
أو مفتاح service_role القديم. لا تضع هذا المفتاح في التطبيق أبداً ولا ترفعه إلى Git.

أمثلة:
    # 1) تجربة بدون رفع فعلي (يطبع ما سيُرفع فقط)
    python3 tools/upload_seed_assets.py --url https://xxxx.supabase.co --key sb_secret_xxx --dry-run

    # 2) الرفع الفعلي
    python3 tools/upload_seed_assets.py --url https://xxxx.supabase.co --key sb_secret_xxx

    # 3) رفع قسم واحد فقط
    python3 tools/upload_seed_assets.py --url ... --key ... --only items

    # أو من متغيّرات البيئة
    export SUPABASE_URL=https://xxxx.supabase.co
    export SUPABASE_SECRET_KEY=sb_secret_xxx
    python3 tools/upload_seed_assets.py
"""
import argparse
import json
import mimetypes
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import List, Optional, Tuple

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SOURCE = os.path.join(ROOT, "seed-assets")
BUCKET = "site-assets"
ALLOWED_EXT = {".png", ".jpg", ".jpeg", ".webp", ".gif", ".svg"}


def headers(key: str, content_type: Optional[str] = None) -> dict:
    """ترويسات Supabase: المفتاح العام/السري يمرّ في apikey.
    المفاتيح الجديدة (sb_…) ليست JWT فلا تُوضع في Authorization — تُوضع فقط لو كان المفتاح القديم (eyJ…)."""
    h = {"apikey": key, "x-upsert": "true", "cache-control": "3600"}
    if key.startswith("eyJ"):
        h["Authorization"] = f"Bearer {key}"
    if content_type:
        h["Content-Type"] = content_type
    return h


def request(url: str, key: str, data: Optional[bytes] = None, method: str = "GET", content_type: Optional[str] = None):
    req = urllib.request.Request(url, data=data, method=method, headers=headers(key, content_type))
    with urllib.request.urlopen(req, timeout=120) as resp:
        body = resp.read().decode("utf-8", "replace")
        return resp.status, body


def encode_path(path: str) -> str:
    """ترميز المسار للاستخدام في رابط: المسافات وأسماء مثل «Waguri-chan is Minecraft.jpg»."""
    return urllib.parse.quote(path, safe="/")


def upload(url: str, key: str, bucket: str, path: str, file_path: str) -> Tuple[int, str]:
    ctype = mimetypes.guess_type(file_path)[0] or "application/octet-stream"
    with open(file_path, "rb") as fh:
        payload = fh.read()
    endpoint = f"{url.rstrip('/')}/storage/v1/object/{bucket}/{encode_path(path)}"
    return request(endpoint, key, payload, method="POST", content_type=ctype)


def collect(source: str, only: Optional[str]) -> List[Tuple[str, str]]:
    """يرجع قائمة (المسار داخل المخزن، مسار الملف محلياً)."""
    out = []  # type: List[Tuple[str, str]]
    for root, dirs, files in os.walk(source):
        dirs[:] = [d for d in dirs if d != ".git"]
        rel_dir = os.path.relpath(root, source).replace("\\", "/")
        if rel_dir == ".":
            rel_dir = ""
        if only and not (rel_dir == only or rel_dir.startswith(only + "/")):
            continue
        for name in sorted(files):
            ext = os.path.splitext(name)[1].lower()
            if ext not in ALLOWED_EXT:
                continue  # تجاهل README.txt وأي ملف غير صورة
            local = os.path.join(root, name)
            remote = f"{rel_dir}/{name}" if rel_dir else name
            out.append((remote, local))
    return out


def main() -> int:
    ap = argparse.ArgumentParser(description="رفع أصول site-assets إلى Supabase")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""), help="رابط المشروع https://xxxx.supabase.co")
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")),
                    help="المفتاح السري sb_secret_… (أو service_role القديم)")
    ap.add_argument("--bucket", default=BUCKET, help="اسم المخزن (افتراضي site-assets)")
    ap.add_argument("--source", default=DEFAULT_SOURCE, help="مجلد الأصول محلياً (افتراضي seed-assets)")
    ap.add_argument("--only", default=None, help="قسم واحد فقط: items أو stickers أو profiles")
    ap.add_argument("--dry-run", action="store_true", help="طبع القائمة بدون رفع")
    ap.add_argument("--sleep", type=float, default=0.05, help="انتظار بين الملفات (ثوانٍ)")
    args = ap.parse_args()

    if not args.url or not args.url.startswith("http"):
        print("✗ مرّر رابط المشروع: --url https://xxxx.supabase.co")
        return 2
    if not args.key and not args.dry_run:
        print("✗ مرّر المفتاح السري: --key sb_secret_… (أو SUPABASE_SECRET_KEY في البيئة)")
        return 2
    if args.key.startswith("sb_publishable_") or args.key.startswith("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJvbGUiOiJhbm9u"):
        print("✗ هذا مفتاح عام (publishable/anon) — الرفع يحتاج المفتاح السري sb_secret_…")
        return 2
    if not os.path.isdir(args.source):
        print(f"✗ المجلد غير موجود: {args.source}")
        return 2

    files = collect(args.source, args.only)
    if not files:
        print("✗ لا ملفات صور في المصدر")
        return 2

    total_bytes = sum(os.path.getsize(p) for _, p in files)
    print(f"→ المصدر: {args.source}")
    print(f"→ المخزن: {args.bucket}   الملفات: {len(files)}   الحجم: {total_bytes / 1024 / 1024:.1f} MB")
    if args.dry_run:
        for remote, local in files[:15]:
            print(f"   [تجربة] {local}  →  {remote}")
        if len(files) > 15:
            print(f"   … و{len(files) - 15} ملفاً آخر")
        print("✓ تجربة فقط — لم يُرفع شيء. أزل --dry-run للرفع الفعلي.")
        return 0

    ok = failed = 0
    errors = []  # type: List[Tuple[str, str]]
    for index, (remote, local) in enumerate(files, 1):
        for attempt in (1, 2):
            try:
                status, _ = upload(args.url, args.key, args.bucket, remote, local)
                if 200 <= status < 300:
                    ok += 1
                    print(f"   [{index}/{len(files)}] ✓ {remote}")
                else:
                    raise RuntimeError(f"HTTP {status}")
                break
            except Exception as exc:  # noqa: BLE001 — لا نوقف الرفع بسبب ملف واحد
                detail = ""
                if isinstance(exc, urllib.error.HTTPError):
                    try:
                        detail = json.loads(exc.read().decode("utf-8", "replace")).get("message", "")
                    except Exception:
                        detail = ""
                if attempt == 2:
                    failed += 1
                    errors.append((remote, f"{exc} {detail}".strip()))
                    print(f"   [{index}/{len(files)}] ✗ {remote} — {exc} {detail}")
                else:
                    time.sleep(1.0)
        time.sleep(args.sleep)

    print("\n================ الخلاصة ================")
    print(f"✅ نجح: {ok}    ❌ فشل: {failed}")
    if errors:
        print("أول 10 أخطاء:")
        for remote, err in errors[:10]:
            print(f"   {remote}: {err}")
        print("\nإن كان الخطأ 403 أو 'Invalid Compact JWS': تأكد أنك تستخدم المفتاح السري،")
        print("وإن استمر فالخطأ غالباً بسبب صلاحيات المخزن — نفّذ supabase/05_storage.sql ثم أعد المحاولة.")
    else:
        print("تحقّق الآن في Supabase → Storage → site-assets، ثم في SQL Editor نفّذ:")
        print("   select asset_type, count(*) from public.site_assets group by 1 order by 1;")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
