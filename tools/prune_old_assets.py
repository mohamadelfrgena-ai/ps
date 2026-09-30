#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يحذف أصول الواجهة **القديمة** من مخزن Supabase (site-assets) بعد استبدالها بالجديدة.

الأصول القديمة معروفة بالضبط: هي المسارات المذكورة في ملفَي الزرع القديمين
    supabase/07_seed_assets.sql   (105 أداة + 181 ملصقاً)
    supabase/12_avatars.sql       (25 أفاتاراً)
لذلك الحذف **مستهدَف** ولا يلمس أي ملف آخر في المخزن (مثل أغلفة المحتوى التي
يرفعها المستخدمون داخل items/<uid>/).

الاستخدام:
    # تجربة: اطبع ما سيُحذف فقط
    python3 tools/prune_old_assets.py --url https://xxxx.supabase.co --key sb_secret_xxx --dry-run

    # الحذف الفعلي
    python3 tools/prune_old_assets.py --url ... --key ...
"""
import argparse
import json
import os
import re
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from typing import List, Set

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BUCKET = "site-assets"
LEGACY_FILES = ("supabase/07_seed_assets.sql", "supabase/12_avatars.sql")
# مسارات الأصول التي نعرفها في الملفات القديمة (نوعها من امتدادها)
PATH_RE = re.compile(r"'((?:items|stickers|profiles|banners|avatars)/[^']+\.(?:png|jpg|jpeg|gif|webp|svg))'", re.I)


def headers(key: str) -> dict:
    h = {"apikey": key}
    if key.startswith("eyJ"):
        h["Authorization"] = f"Bearer {key}"
    return h


def legacy_paths(extra_manifests: List[str]) -> Set[str]:
    """يجمع مسارات الأصول القديمة من ملفات الزرع القديمة (وبيانات سابقة إن مُرّرت)."""
    paths: Set[str] = set()
    for rel in LEGACY_FILES:
        full = os.path.join(ROOT, rel)
        if not os.path.exists(full):
            continue
        with open(full, encoding="utf-8") as fh:
            paths.update(PATH_RE.findall(fh.read()))
    # بيان جديد (إن مُرّر) لحماية ملفاته من الحذف حتى لو تشابه المسار
    keep: Set[str] = set()
    for manifest in extra_manifests:
        if manifest and os.path.exists(manifest):
            with open(manifest, encoding="utf-8") as fh:
                keep.update(a["path"] for a in json.load(fh)["assets"])
    return paths - keep


def delete(url: str, key: str, bucket: str, path: str):
    """يحذف ملفاً من المخزن. يرجع (الحالة، هل هو غير موجود أصلاً؟)."""
    endpoint = f"{url.rstrip('/')}/storage/v1/object/{bucket}/{urllib.parse.quote(path, safe='/')}"
    req = urllib.request.Request(endpoint, method="DELETE", headers=headers(key))
    try:
        with urllib.request.urlopen(req, timeout=60) as resp:
            return resp.status, False
    except urllib.error.HTTPError as exc:
        body = exc.read().decode("utf-8", "replace")
        # Supabase يرجع 400/404 مع «not found» للملفات المحذوفة سابقاً — ليست مشكلة
        gone = exc.code == 404 or "not_found" in body or "Object not found" in body
        return exc.code, gone


def main() -> int:
    ap = argparse.ArgumentParser(description="حذف أصول site-assets القديمة")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")))
    ap.add_argument("--bucket", default=BUCKET)
    ap.add_argument("--manifest", default=os.path.join(ROOT, "assets-manifest.json"),
                    help="بيان الأصول الجديدة (تُستثنى مساراته من الحذف)")
    ap.add_argument("--dry-run", action="store_true")
    args = ap.parse_args()

    if not args.url.startswith("http"):
        print("✗ مرّر الرابط: --url https://xxxx.supabase.co")
        return 2
    if not args.key and not args.dry_run:
        print("✗ مرّر المفتاح السري: --key sb_secret_…")
        return 2

    paths = sorted(legacy_paths([args.manifest]))
    print(f"→ أصول قديمة معروفة: {len(paths)} ملفاً")
    if not paths:
        return 0
    if args.dry_run:
        for p in paths[:20]:
            print(f"   [تجربة] سيُحذف: {p}")
        if len(paths) > 20:
            print(f"   … و{len(paths) - 20} ملفاً آخر")
        print("✓ تجربة فقط — لم يُحذف شيء.")
        return 0

    ok = missing = failed = 0
    for index, path in enumerate(paths, 1):
        status, gone = delete(args.url, args.key, args.bucket, path)
        if 200 <= status < 300:
            ok += 1
        elif gone:
            missing += 1  # محذوف مسبقاً — لا مشكلة
        else:
            failed += 1
            print(f"   [{index}/{len(paths)}] ✗ {path} → HTTP {status}")
        time.sleep(0.02)
    print(f"\n✓ حُذف {ok} · محذوف مسبقاً {missing} · فشل {failed}")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
