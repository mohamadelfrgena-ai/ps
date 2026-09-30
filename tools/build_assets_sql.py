#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يبني ملف SQL لاستبدال أصول الواجهة (supabase/15_assets_v2.sql) من بيان assets-manifest.json.

البيان يولّده tools/prepare_new_assets.py من الأرشيف المقسّم، ويحمل لكل أصل:
النوع والاسم والمسار والقسم والخطة وهل هو متحرّك (GIF).

ما يفعله الملف المولَّد:
    1) يضيف عمودَي collection و collection_order إلى site_assets (آمن للتكرار)
    2) يحذف كل الأصول القديمة (أفاتارات + ملصقات + أدوات) وحزم الملصقات القديمة
    3) ينشئ حزم الملصقات الجديدة (6 حزم)
    4) يُدرج الأصول الجديدة كلها (492) موزّعة على الأقسام
    5) يستعلام تحقق في النهاية

الاستخدام:
    python3 tools/build_assets_sql.py
    python3 tools/build_assets_sql.py --manifest assets-manifest.json --out supabase/15_assets_v2.sql
"""
import argparse
import collections
import io
import json
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_MANIFEST = os.path.join(ROOT, "assets-manifest.json")
DEFAULT_OUT = os.path.join(ROOT, "supabase", "15_assets_v2.sql")

TYPE_MAP = {"avatars": "avatar", "banners": "banner", "stickers": "sticker", "items": "item"}


def esc(text: str) -> str:
    return text.replace("'", "''")


def build(manifest: dict) -> str:
    assets = manifest["assets"]
    sections = manifest["sections"]

    # ترتيب الأقسام: نوع الأصل ثم ترتيب القسم
    section_order = {}
    for s in sections:
        section_order[(s["type"], s["id"])] = s["order"]

    grouped = collections.OrderedDict()
    for kind in ("avatars", "stickers", "items"):
        rows = [a for a in assets if a["type"] == kind]
        rows.sort(key=lambda a: (section_order.get((a["type"], a["section"]), 999), a["order"]))
        grouped[kind] = rows

    lines = []
    add = lines.append

    total = len(assets)
    animated = sum(1 for a in assets if a["animated"])
    add("-- ============================================================================")
    add("--  AddonStation Mobile — 15) أصول الواجهة الجديدة (استبدال كامل)")
    add("--")
    add(f"--  يولّده tools/build_assets_sql.py من assets-manifest.json — لا تعدّله يدوياً.")
    add("--")
    add(f"--  المحتوى: {len(grouped['avatars'])} أفاتاراً · {len(grouped['stickers'])} ملصقاً ·"
        f" {len(grouped['items'])} أداة  =  {total} أصلاً")
    add(f"--  منها {animated} ملفاً متحرّكاً (GIF) يعمل داخل التطبيق مع المعاينات.")
    add("--")
    add("--  ⚠️ هذا الملف **يحذف كل الأصول القديمة** (أفاتارات/ملصقات/أدوات) ثم يزرع الجديدة.")
    add("--     الملفات نفسها تُرفع إلى مخزن site-assets عبر سير عمل GitHub:")
    add("--        Actions → Upload new assets → Run workflow → mode = upload")
    add("--     (السير ينزّل الأرشيف من المستودع العام ويوزّعه على الأقسام ثم يرفعه)")
    add("--")
    add("--  الأقسام: كل مجلد من مجلداتك صار قسماً مستقلاً في التطبيق.")
    add("--  آمن لإعادة التنفيذ (idempotent).")
    add("-- ============================================================================")
    add("")
    add("-- ---------------------------------------------------------------------------")
    add("-- 1) عمودا القسم: يجعل ورقة الأصول تُعرض في فقرات منفصلة")
    add("-- ---------------------------------------------------------------------------")
    add("alter table public.site_assets add column if not exists collection text;")
    add("alter table public.site_assets add column if not exists collection_order int not null default 0;")
    add("create index if not exists site_assets_collection_idx")
    add("  on public.site_assets(asset_type, collection_order, sort_order);")
    add("")
    add("-- ---------------------------------------------------------------------------")
    add("-- 2) حذف كل الأصول القديمة وحزم الملصقات القديمة")
    add("-- ---------------------------------------------------------------------------")
    add("delete from public.site_assets;")
    add("delete from public.sticker_packs;")
    add("")
    add("-- فهرس يحمي من التكرار عند إعادة التنفيذ (يُنشأ بعد التنظيف كي لا يفشل)")
    add("create unique index if not exists site_assets_file_url_uidx on public.site_assets (file_url);")
    add("")
    add("-- ---------------------------------------------------------------------------")
    add("-- 2.5) صلاحيات دور service_role — حتى يستطيع سير الرفع الآلي كتابة الأصول")
    add("--      (يقابل الخطوة 4.5 في supabase/11_grants.sql)")
    add("-- ---------------------------------------------------------------------------")
    add("grant usage on schema public to service_role;")
    add("grant select, insert, update, delete on public.site_assets to service_role;")
    add("grant select, insert, update, delete on public.sticker_packs to service_role;")
    add("")
    add("-- ---------------------------------------------------------------------------")
    add("-- 3) حزم الملصقات الجديدة (كل قسم ملصقات = حزمة)")
    add("-- ---------------------------------------------------------------------------")
    add("insert into public.sticker_packs (id, name, icon_url, sort_order, active)")
    add("values")
    stickers_sections = [s for s in sections if s["type"] == "stickers"]
    sticker_rows = []
    for s in stickers_sections:
        sample = next((a for a in grouped["stickers"] if a["section"] == s["id"]), None)
        icon = f"/site-assets/{sample['path']}" if sample else None
        icon_sql = f"'{esc(icon)}'" if icon else "null"
        sticker_rows.append(
            f"  ('{esc(s['id'])}', '{esc(s['label'])}', {icon_sql}, {s['order']}, true)"
        )
    add(",\n".join(sticker_rows))
    add("on conflict (id) do update")
    add("  set name = excluded.name,")
    add("      icon_url = excluded.icon_url,")
    add("      sort_order = excluded.sort_order,")
    add("      active = excluded.active;")
    add("")

    for kind, label in (("avatars", "الأفاتارات"), ("stickers", "الملصقات"), ("items", "الأدوات")):
        rows = grouped[kind]
        add("-- ---------------------------------------------------------------------------")
        add(f"-- 4) {label} — {len(rows)} أصلاً في {len(set(a['section'] for a in rows))} قسماً")
        add("-- ---------------------------------------------------------------------------")
        add("insert into public.site_assets")
        add("  (asset_type, name, file_url, sticker_pack, required_plan, animated, collection, collection_order, sort_order)")
        add("values")
        values = []
        for index, a in enumerate(rows, 1):
            pack = f"'{esc(a['section'])}'" if kind == "stickers" else "null"
            values.append(
                "  ('{type}', '{name}', '{path}', {pack}, '{plan}', {anim}, '{collection}', {corder}, {order})".format(
                    type=TYPE_MAP[kind],
                    name=esc(a["name"]),
                    path=esc(a["path"]),
                    pack=pack,
                    plan=esc(a["plan"]),
                    anim="true" if a["animated"] else "false",
                    collection=esc(a["section_label"]),
                    corder=section_order[(kind, a["section"])],
                    order=index,
                )
            )
        add(",\n".join(values))
        add("on conflict (file_url) do update")
        add("  set name = excluded.name,")
        add("      asset_type = excluded.asset_type,")
        add("      sticker_pack = excluded.sticker_pack,")
        add("      required_plan = excluded.required_plan,")
        add("      animated = excluded.animated,")
        add("      collection = excluded.collection,")
        add("      collection_order = excluded.collection_order,")
        add("      sort_order = excluded.sort_order,")
        add("      enabled = true;")
        add("")

    add("-- ---------------------------------------------------------------------------")
    add("-- تحقق: يجب أن ترى 21 قسماً والأعداد كما في الجدول أدناه")
    add("-- ---------------------------------------------------------------------------")
    add("select asset_type as النوع, collection as القسم, count(*) as العدد,")
    add("       count(*) filter (where animated) as متحرك")
    add("  from public.site_assets")
    add(" group by asset_type, collection, collection_order")
    add(" order by asset_type, collection_order;")
    add("")
    add("-- الإجمالي المتوقّع: avatar = {a} · sticker = {s} · item = {i}".format(
        a=len(grouped["avatars"]), s=len(grouped["stickers"]), i=len(grouped["items"])))
    add("select asset_type, count(*) as total, count(*) filter (where animated) as animated")
    add("  from public.site_assets group by asset_type order by asset_type;")
    add("")
    return "\n".join(lines) + "\n"


def main() -> int:
    ap = argparse.ArgumentParser(description="بناء ملف SQL لأصول الواجهة الجديدة")
    ap.add_argument("--manifest", default=DEFAULT_MANIFEST, help="مسار البيان")
    ap.add_argument("--out", default=DEFAULT_OUT, help="ملف SQL الناتج")
    args = ap.parse_args()

    if not os.path.exists(args.manifest):
        print(f"✗ البيان غير موجود: {args.manifest}")
        print("  شغّل أولاً: python3 tools/prepare_new_assets.py --download")
        return 2

    with io.open(args.manifest, encoding="utf-8") as fh:
        manifest = json.load(fh)

    sql = build(manifest)
    os.makedirs(os.path.dirname(args.out) or ".", exist_ok=True)
    with io.open(args.out, "w", encoding="utf-8") as fh:
        fh.write(sql)

    print(f"✓ {args.out}  ({len(sql.splitlines()):,} سطراً · {len(manifest['assets'])} أصلاً ·"
          f" {len(manifest['sections'])} قسماً)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
