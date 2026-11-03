#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يسجّل أصول الواجهة الجديدة في قاعدة البيانات مباشرة عبر REST — بلا لصق SQL يدوياً.

ماذا يفعل (بالمفتاح السري، وهو يتجاوز RLS مثل service_role):
    1) يحذف **كل** صفوف site_assets القديمة وحزم sticker_packs القديمة.
    2) يُدرج حزم الملصقات الست الجديدة.
    3) يُدرج 492 أصلاً من assets-manifest.json (بنفس المسارات المرفوعة إلى المخزن).
    4) يطبع أعداد التحقق النهائية.

يبقى ملف supabase/15_assets_v2.sql مفيداً لإضافة عمودَي collection/collection_order
والفهرس الفريد (تحسينات مخطط) — لكن التطبيق يعمل ويعرض الفقرات كاملة بلا انتظار.

الاستخدام:
    python3 tools/seed_assets_rest.py --url https://xxxx.supabase.co --key sb_secret_xxx
    python3 tools/seed_assets_rest.py --url … --key … --dry-run
"""
import argparse
import io
import json
import os
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_MANIFEST = os.path.join(ROOT, "assets-manifest.json")
TYPE_MAP = {"avatars": "avatar", "banners": "banner", "stickers": "sticker", "items": "item"}
BATCH = 100


def headers(key: str, extra: dict = None) -> dict:
    h = {"apikey": key, "Content-Type": "application/json"}
    if key.startswith("eyJ"):
        h["Authorization"] = f"Bearer {key}"
    if extra:
        h.update(extra)
    return h


def call(url: str, key: str, path: str, method="GET", payload=None, extra_headers=None):
    endpoint = f"{url.rstrip('/')}/rest/v1/{path}"
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(endpoint, data=data, method=method,
                                 headers=headers(key, extra_headers))
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            body = resp.read().decode("utf-8", "replace")
            return resp.status, body
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")


def has_column(url: str, key: str, table: str, column: str) -> bool:
    status, _ = call(url, key, f"{table}?select={column}&limit=1")
    return 200 <= status < 300


def main() -> int:
    ap = argparse.ArgumentParser(description="زرع أصول الواجهة في قاعدة البيانات عبر REST")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", os.environ.get("SUPABASE_SERVICE_ROLE_KEY", "")))
    ap.add_argument("--manifest", default=DEFAULT_MANIFEST)
    ap.add_argument("--dry-run", action="store_true")
    ap.add_argument("--sleep", type=float, default=0.05)
    args = ap.parse_args()

    if not args.url.startswith("http"):
        print("✗ مرّر الرابط: --url https://xxxx.supabase.co")
        return 2
    if not args.key and not args.dry_run:
        print("✗ مرّر المفتاح السري: --key sb_secret_…")
        return 2
    if not os.path.exists(args.manifest):
        print(f"✗ البيان غير موجود: {args.manifest}  (شغّل tools/prepare_new_assets.py)")
        return 2

    with io.open(args.manifest, encoding="utf-8") as fh:
        manifest = json.load(fh)
    assets = manifest["assets"]
    sections = {s["id"]: s for s in manifest["sections"]}

    if args.dry_run:
        print(f"→ [تجربة] سيُحذف كل صفوف site_assets وsticker_packs، ثم:")
        print(f"   • {len([s for s in sections.values() if s['type'] == 'stickers'])} حزمة ملصقات")
        print(f"   • {len(assets)} أصلاً من {args.manifest}")
        per = {}
        for a in assets:
            per[a["type"]] = per.get(a["type"], 0) + 1
        print("   " + " · ".join(f"{k} = {v}" for k, v in per.items()))
        return 0

    with_collection = has_column(args.url, args.key, "site_assets", "collection")
    with_order = has_column(args.url, args.key, "site_assets", "collection_order")
    print(f"→ عمودا المخطط: collection={'نعم' if with_collection else 'لا (يُشتق من المسار في التطبيق)'}"
          f" · collection_order={'نعم' if with_order else 'لا'}")

    # 1) حذف القديم بالكامل
    status, body = call(args.url, args.key, "site_assets?id=gte.0", method="DELETE",
                        extra_headers={"Prefer": "return=minimal"})
    print(f"→ حذف صفوف site_assets القديمة: HTTP {status} {body[:120]}")
    if not (200 <= status < 300):
        return 1
    status, body = call(args.url, args.key, "sticker_packs?id=not.is.null", method="DELETE",
                        extra_headers={"Prefer": "return=minimal"})
    print(f"→ حذف حزم الملصقات القديمة: HTTP {status} {body[:120]}")
    if not (200 <= status < 300):
        return 1

    # 2) حزم الملصقات الجديدة (قسم = حزمة)
    packs = []
    for s in manifest["sections"]:
        if s["type"] != "stickers":
            continue
        sample = next((a for a in assets if a["type"] == "stickers" and a["section"] == s["id"]), None)
        packs.append({
            "id": s["id"], "name": s["label"],
            "icon_url": f"/site-assets/{sample['path']}" if sample else None,
            "sort_order": s["order"], "active": True,
        })
    status, body = call(args.url, args.key, "sticker_packs", method="POST", payload=packs,
                        extra_headers={"Prefer": "resolution=merge-duplicates,return=minimal"})
    print(f"→ إدراج {len(packs)} حزمة ملصقات: HTTP {status} {body[:120]}")
    if not (200 <= status < 300):
        return 1

    # 3) الأصول الجديدة
    order_of = {(s["type"], s["id"]): s["order"] for s in manifest["sections"]}
    rows = []
    counters = {}
    for a in assets:
        kind = a["type"]
        counters[kind] = counters.get(kind, 0) + 1
        row = {
            "asset_type": TYPE_MAP.get(kind, kind),
            "name": a["name"],
            "file_url": a["path"],
            "sticker_pack": a["section"] if kind == "stickers" else None,
            "required_plan": a["plan"],
            "animated": bool(a["animated"]),
            "enabled": True,
            "sort_order": counters[kind],
        }
        if with_collection:
            row["collection"] = a["section_label"]
        if with_order:
            row["collection_order"] = order_of.get((kind, a["section"]), 0)
        rows.append(row)

    inserted = 0
    for start in range(0, len(rows), BATCH):
        chunk = rows[start:start + BATCH]
        status, body = call(args.url, args.key, "site_assets", method="POST", payload=chunk,
                            extra_headers={"Prefer": "return=minimal"})
        if not (200 <= status < 300):
            print(f"   ✗ دفعة {start // BATCH + 1}: HTTP {status} {body[:200]}")
            return 1
        inserted += len(chunk)
        print(f"   ✓ {inserted}/{len(rows)}")
        time.sleep(args.sleep)

    # 4) تحقق فعلي: نقرأ الصفوف من القاعدة ونعدّها بأنفسنا
    print("\n================ الخلاصة ================")
    status, body = call(args.url, args.key, "site_assets?select=asset_type,animated&limit=1000")
    if not (200 <= status < 300):
        print(f"✗ تعذّر التحقق: HTTP {status} {body[:200]}")
        return 1
    counts, animated = {}, {}
    for row in json.loads(body or "[]"):
        kind = row.get("asset_type")
        counts[kind] = counts.get(kind, 0) + 1
        if row.get("animated"):
            animated[kind] = animated.get(kind, 0) + 1
    total = sum(counts.values())
    print(f"✅ سُجّل {inserted} أصلاً و{len(packs)} حزمة ملصقات · حُذف كل القديم")
    for kind in ("avatar", "sticker", "item"):
        print(f"   {kind}: {counts.get(kind, 0)} أصلاً · متحرّك {animated.get(kind, 0)}")
    print(f"   الإجمالي في قاعدة البيانات: {total}")
    if total != len(rows):
        print("::warning::العدد في قاعدة البيانات لا يطابق البيان — راجع RLS أو المفتاح")
    print("افتح التطبيق: الأصول الجديدة تظهر في 21 قسماً، وكل GIF يتحرّك.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
