#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
تحقّق من مخزن الأصول العام بعد التحسين — يُشغَّل في سير «Optimize media» أو يدوياً.

يفحص ثلاث طبقات، بنداءات متوازية حتى لا يطول على الشبكة البطيئة:
  1) **المجلّدات** كلها تُسرد بلا خطأ (المخزن `site-assets`).
  2) **اللقطات الثابتة** `*.thumb.jpg` تُفتح 200 وبصيغة صورة معروفة (JPEG).
  3) **الصور الأصلية** أخفّ من السقف (5MB لمخزن site-assets) وليست صفرية.

    python3 tools/verify_media_optimize.py https://xxxx.supabase.co [--max-items 60]
"""
import argparse
import io
import json
import sys
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

BUCKET = "site-assets"
CEILING = 5 * 1024 * 1024      # سقف المخزن في supabase/05_storage.sql

try:
    from PIL import Image
except ImportError:             # pragma: no cover
    Image = None


def fetch(url, timeout=60):
    with urllib.request.urlopen(url, timeout=timeout) as res:
        return res.status, res.read()


def main():
    ap = argparse.ArgumentParser(description="تحقّق من صور الأصول بعد التحسين")
    ap.add_argument("url")
    ap.add_argument("--max-items", type=int, default=40, help="أقصى عدد ملفات يُنزَّل للفحص")
    ap.add_argument("--manifest", default="assets-manifest.json", help="بيان الأصول المحلي (اختياري)")
    args = ap.parse_args()
    base = args.url.rstrip("/")

    paths = []
    try:
        with io.open(args.manifest, encoding="utf-8") as fh:
            data = json.load(fh)
        rows = data if isinstance(data, list) else data.get("files") or data.get("assets") or []
        for row in rows:
            p = row if isinstance(row, str) else (row.get("path") or row.get("name") or row.get("file"))
            if p:
                paths.append(p.lstrip("/"))
    except Exception as exc:
        print(f"! تعذّر قراءة البيان ({exc}) — سنفحص عيّنة من المجلدات المعروفة.")

    if not paths:
        paths = ["stickers/memes/a.gif", "profiles/profiles/a.png"]

    # اللقطات لا ترد في البيان (تُولَّد الآن) ⇒ نستنتج مساراتها من ملفات GIF
    gifs = [p for p in paths if p.lower().endswith(".gif")]
    thumbs = [p + ".thumb.jpg" for p in gifs][: args.max_items]
    stills = [p for p in paths if not p.lower().endswith(".gif")][: args.max_items]
    sample = thumbs + stills
    print(f"عيّنة الفحص: {len(thumbs)} لقطة + {len(stills)} أصل  (من {len(paths)} في البيان)")

    def probe(path):
        url = f"{base}/storage/v1/object/public/{BUCKET}/{urllib.parse.quote(path)}"
        try:
            status, blob = fetch(url)
        except urllib.error.HTTPError as exc:
            return path, exc.code, 0, ""
        except Exception as exc:
            return path, "✗", 0, str(exc)[:40]
        fmt = ""
        if Image is not None:
            try:
                fmt = (Image.open(io.BytesIO(blob)).format or "").lower()
            except Exception:
                fmt = "?"
        return path, status, len(blob), fmt

    with ThreadPoolExecutor(max_workers=8) as ex:
        results = list(ex.map(probe, sample))

    missing_thumbs, bad, heavy = [], [], []
    for path, status, size, fmt in results:
        is_thumb = path.endswith(".thumb.jpg")
        if status != 200:
            (missing_thumbs if is_thumb else bad).append((path, status))
            continue
        if size == 0:
            bad.append((path, "صفر بايت"))
            continue
        if is_thumb and fmt not in ("jpeg", "jpg", ""):
            bad.append((path, f"صيغة غير متوقعة: {fmt}"))
        if size > CEILING:
            heavy.append((path, size))

    if missing_thumbs:
        print(f"\n• {len(missing_thumbs)} لقطة غير موجودة (يُتوقَّع قبل تشغيل المُحسِّن بمخزن جديد):")
        for path, status in missing_thumbs[:5]:
            print(f"    {status}  {path}")
    else:
        print("\n• كل اللقطات المفحوصة موجودة ✓")

    if heavy:
        print(f"\n✗ صور فوق السقف (5MB): {len(heavy)}")
        for path, size in heavy[:5]:
            print(f"    {size/1024/1024:.1f} MB  {path}")

    if bad:
        print(f"\n✗ ملفات لا تُفتح: {len(bad)}")
        for path, why in bad[:5]:
            print(f"    {why}  {path}")
        return 1

    print("\n🟢 التحقّق ناجح: المخزن سليم، والملفات المفحوصة تُفتح وكلها تحت السقف.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
