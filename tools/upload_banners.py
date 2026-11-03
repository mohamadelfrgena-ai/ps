#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
إضافة **بانرات الملف الشخصي** من ملف المالك `Banner.zip`:

    • يرفع الصور إلى مخزن `site-assets` تحت `banners/nature/…` و`banners/random/…`
    • ويولّد ملف SQL يسجّلها في `site_assets` بنوع `banner`:
        – القسم (collection) = اسم المجلد الأصلي: «Nature 🌿» و«Random 🎲»
          ⇒ تظهر البانرات في **فقرتين منفصلتين** داخل «تعديل الملف الشخصي».
        – `required_plan = 'epic'` ⇒ **مقفولة 🔒** ولا تُفتح إلا باشتراك Epic 💎
          أو Legendary 👑 (الخادم يرفضها لغير المشترك أيضاً).
        – `animated = true` لكل ملف ‎.gif‎ ⇒ يتحرّك في المعاينة وفي الملف الشخصي.

لا مكتبات خارجية للرفع (urllib فقط). PIL اختيارية لتوليد لقطة ثابتة للـGIF.

أمثلة:
    # ١) توليد ملف SQL فقط (بلا رفع)
    python3 tools/upload_banners.py --zip ~/Downloads/Banner.zip --sql-only

    # ٢) رفع فعلي + توليد SQL
    python3 tools/upload_banners.py --zip ~/Downloads/Banner.zip \
        --url https://xxxx.supabase.co --key sb_secret_…

    # ٣) من مجلد مستخرج + تجربة بلا رفع
    python3 tools/upload_banners.py --dir ./Banner --dry-run
"""
import argparse
import json
import mimetypes
import os
import re
import sys
import zipfile

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DEFAULT_SQL = os.path.join(ROOT, "supabase", "21_banners.sql")
BUCKET = "site-assets"
ALLOWED_EXT = {".png", ".jpg", ".jpeg", ".webp", ".gif"}
REQUIRED_PLAN = "epic"          # مقفولة ⇒ لا تُفتح إلا باشتراك Epic فما فوق
COLLECTION_ORDER = {"nature": 22, "random": 23, "admin": 24}

# مجلد المالك ← (اسم المجلد في المخزن، اسم القسم المعروض في التطبيق)
FOLDERS = {
    "nature": ("nature", "Nature 🌿"),
    "random": ("random", "Random 🎲"),
    "admin":  ("admin",  "Admin 👑"),        # أغلفة حصرية للإدارة
}

# أكبر حجم مقبول للرفع قبل التصغير التلقائي (حدّ المخزن في بعض المشاريع أصغر)
MAX_UPLOAD_BYTES = 4 * 1024 * 1024

UNSAFE = re.compile(r"[^A-Za-z0-9._-]+")
NUM_PREFIX = re.compile(r"^(\d+[-_])+")
MULTI_DASH = re.compile(r"-{2,}")


# --------------------------------------------------------------------- أدوات
def detect_folder(display: str) -> str:
    """يُحوّل اسم مجلد المالك (مثل «Nature 🌿») إلى مفتاح (nature)."""
    ascii_name = display.encode("ascii", "ignore").decode().strip().lower()
    for key in FOLDERS:
        if key in ascii_name:
            return key
    # مجلد غير معروف ⇒ نولّد slug آمناً
    slug = UNSAFE.sub("-", ascii_name).strip("-") or "misc"
    return MULTI_DASH.sub("-", slug)


def safe_file_name(name: str) -> str:
    """اسم ملف آمن في رابط (بلا مسافات ولا رموز) مع الحفاظ على الامتداد."""
    base, ext = os.path.splitext(name)
    base = UNSAFE.sub("-", base).strip("-.")
    base = MULTI_DASH.sub("-", base)
    if not base:
        base = "banner"
    return base + ext.lower()


def pretty_name(name: str, index: int) -> str:
    """اسم مقروء يُعرض تحت الصورة في الشبكة."""
    base = os.path.splitext(name)[0]
    base = NUM_PREFIX.sub("", base)                  # 16349-480013- → «»
    base = re.sub(r"[-_]+", " ", base).strip()
    base = re.sub(r"\s{2,}", " ", base)
    if not base or base.replace(" ", "").isdigit():
        base = f"Banner {index}"
    if len(base) > 46:
        base = base[:43].rstrip() + "…"
    return base


def esc(text: str) -> str:
    return text.replace("'", "''")


# ------------------------------------------------------------------ القراءة
def read_source(zip_path: str, dir_path: str):
    """يرجع قائمة (المجلد المعروض، اسم الملف، البايتات)."""
    items = []
    if zip_path:
        with zipfile.ZipFile(zip_path) as z:
            for info in z.infolist():
                if info.is_dir():
                    continue
                parts = info.filename.replace("\\", "/").split("/")
                if len(parts) < 3:
                    continue                      # ملفات خارج المجلدين
                folder_display, file_name = parts[-2], parts[-1]
                ext = os.path.splitext(file_name)[1].lower()
                if ext not in ALLOWED_EXT:
                    continue
                items.append((folder_display, file_name, z.read(info)))
    else:
        for folder_display in sorted(os.listdir(dir_path)):
            full = os.path.join(dir_path, folder_display)
            if not os.path.isdir(full):
                continue
            for file_name in sorted(os.listdir(full)):
                ext = os.path.splitext(file_name)[1].lower()
                if ext in ALLOWED_EXT:
                    with open(os.path.join(full, file_name), "rb") as fh:
                        items.append((folder_display, file_name, fh.read()))
    return items


# ------------------------------------------------------------- توليد SQL
def build_rows(items, plan=REQUIRED_PLAN):
    """يرجع (صفوف الأصول كقواميس، قائمة (المسار في المخزن، البايتات، النوع))."""
    rows, uploads = [], []
    counters = {}
    for folder_display, file_name, data in items:
        key = detect_folder(folder_display)
        slug, collection = FOLDERS.get(key, (key, folder_display))
        counters[key] = counters.get(key, 0) + 1
        index = counters[key]
        safe = safe_file_name(file_name)
        path = f"banners/{slug}/{safe}"
        ext = os.path.splitext(safe)[1].lower()
        animated = ext == ".gif"
        rows.append({
            "asset_type": "banner",
            "name": pretty_name(file_name, index),
            "file_url": path,
            "sticker_pack": None,
            "required_plan": plan,
            "animated": animated,
            "enabled": True,
            "collection": collection,
            "collection_order": COLLECTION_ORDER.get(key, 99),
            "sort_order": index,
        })
        uploads.append((path, data, mimetypes.guess_type(safe)[0] or "application/octet-stream"))
    return rows, uploads


SQL_HEADER = """-- ============================================================================
--  AddonStation Mobile — 21) بانرات الملف الشخصي (من ملف المالك Banner.zip)
--
--  الطلب: «أضف البانرات في تعديل الملف الشخصي، وهي غير مجانية وبقفل، ولا تُفتح
--          إلا باشتراك. في داخلها مجلدان ⇒ اجعلهم فقرات. وأي بانر gif اجعله
--          متحرّكاً في المعاينة والاستخدام.»
--
--  ما يفعله هذا الملف (آمن لإعادة التنفيذ):
--      1) يسجّل {count} بانراً في site_assets بنوع banner.
--      2) قسمان (فقرتان) حسب مجلد المالك: «Nature 🌿» و«Random 🎲».
--      3) required_plan = '{plan}' ⇒ مقفولة 🔒 ولا تُفتح إلا باشتراك Epic 💎
--         أو Legendary 👑 (الخادم يرفضها لغير المشترك داخل set_profile_asset).
--      4) animated = true لكل ملف gif ⇒ يتحرّك في المعاينة وفي الملف الشخصي.
--
--  ⚠️ قبل تنفيذ هذا الملف: ارفع الصور إلى المخزن أولاً:
--       python3 tools/upload_banners.py --zip Banner.zip \\
--           --url https://xxxx.supabase.co --key sb_secret_…
--     (وإلا ظهرت البانرات بأسمائها بلا صور).
--
--  يُنفَّذ مرة واحدة في Supabase → SQL Editor.
-- ============================================================================

"""

SQL_FOOTER = """
-- ---------------------------------------------------------------------------
-- تحقّق: عدد البانرات في كل قسم + هل هي مقفولة
-- ---------------------------------------------------------------------------
select collection   as "القسم",
       count(*)     as "العدد",
       count(*) filter (where required_plan <> 'free') as "المقفول",
       count(*) filter (where animated)                as "متحرك"
  from public.site_assets
 where asset_type = 'banner'
 group by collection, collection_order
 order by collection_order;

do $$
declare
  v_total int;
begin
  select count(*) into v_total from public.site_assets where asset_type = 'banner';
  if v_total = 0 then
    raise exception '✗ لا توجد بانرات مسجّلة — نفّذ هذا الملف بعد رفع الصور.';
  end if;
  raise notice '✅ 21_banners: % بانراً مسجّلاً (مقفولة باشتراك · gif متحرّك).', v_total;
end;
$$;
"""


def sql_value(row) -> str:
    return (
        "  ('{atype}', '{name}', '{path}', null, '{plan}', {animated}, "
        "'{collection}', {corder}, {sorder})".format(
            atype=row["asset_type"],
            name=esc(row["name"]),
            path=esc(row["file_url"]),
            plan=row["required_plan"],
            animated="true" if row["animated"] else "false",
            collection=esc(row["collection"]),
            corder=row["collection_order"],
            sorder=row["sort_order"],
        )
    )


def sql_text(rows, plan=REQUIRED_PLAN) -> str:
    body = ("insert into public.site_assets\n"
            "  (asset_type, name, file_url, sticker_pack, required_plan, animated,\n"
            "   collection, collection_order, sort_order)\nvalues\n")
    body += ",\n".join(sql_value(r) for r in rows)
    body += """
on conflict (file_url) do update
  set name = excluded.name,
      asset_type = excluded.asset_type,
      sticker_pack = excluded.sticker_pack,
      required_plan = excluded.required_plan,
      animated = excluded.animated,
      collection = excluded.collection,
      collection_order = excluded.collection_order,
      sort_order = excluded.sort_order,
      enabled = true;
"""
    return SQL_HEADER.format(count=len(rows), plan=plan) + body + SQL_FOOTER


def write_sql(rows, out_path, plan=REQUIRED_PLAN) -> str:
    text = sql_text(rows, plan)
    with open(out_path, "w", encoding="utf-8") as fh:
        fh.write(text)
    return text


def api(url: str, key: str, path: str, method: str = "GET", payload=None, extra=None):
    """نداء REST إلى PostgREST (نفس أسلوب tools/seed_assets_rest.py)."""
    import urllib.error
    import urllib.request

    headers_ = {"apikey": key, "Content-Type": "application/json"}
    if key.startswith("eyJ"):
        headers_["Authorization"] = f"Bearer {key}"
    if extra:
        headers_.update(extra)
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(f"{url.rstrip('/')}/rest/v1/{path}", data=data,
                                 method=method, headers=headers_)
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")
    except Exception as exc:  # noqa: BLE001 — شبكة/عنوان خاطئ: نُبلغ بلطف ولا نُسقط السكربت
        return 0, str(exc)


def seed_rows(url: str, key: str, rows) -> int:
    """يسجّل الصفوف في site_assets عبر REST (بلا لصق SQL) — upsert على file_url."""
    # إن كانت الأعمدة الجديدة غير موجودة نتجاهلها (التطبيق يشتقّ القسم من المسار)
    has_collection = 200 <= api(url, key, "site_assets?select=collection&limit=1")[0] < 300
    has_order = 200 <= api(url, key, "site_assets?select=collection_order&limit=1")[0] < 300
    print(f"→ أعمدة المخطط: collection={'نعم' if has_collection else 'لا'} · "
          f"collection_order={'نعم' if has_order else 'لا'}")

    payload = []
    for r in rows:
        row = {k: v for k, v in r.items() if k != "collection"}
        if has_collection:
            row["collection"] = r["collection"]
        if has_order:
            row["collection_order"] = r["collection_order"]
        payload.append(row)

    status, body = api(
        url, key, "site_assets?on_conflict=file_url", method="POST", payload=payload,
        extra={"Prefer": "resolution=merge-duplicates,return=minimal"},
    )
    if not (200 <= status < 300):
        print(f"✗ فشل التسجيل: HTTP {status} {body[:300]}")
        return 1
    print(f"✓ سُجّل/حُدِّث {len(payload)} غلافاً في site_assets")

    status, body = api(
        url, key,
        "site_assets?select=collection,animated,required_plan&asset_type=eq.banner&limit=1000",
    )
    if 200 <= status < 300:
        try:
            saved = json.loads(body or "[]")
        except Exception:
            saved = []
        groups = {}
        for row in saved:
            g = groups.setdefault(row.get("collection") or "—", [0, 0, 0])
            g[0] += 1
            if row.get("animated"):
                g[1] += 1
            if (row.get("required_plan") or "free") != "free":
                g[2] += 1
        print("\n================ تحقّق ================")
        for name, (n, anim, locked) in groups.items():
            print(f"   {name}: {n} غلافاً · متحرّك {anim} · مقفول {locked}")
    return 0



def shrink_to_fit(data: bytes, path: str, target: int):
    """تصغير **نسبي** للصورة (بلا إعادة رسم) لتناسب حدّ حجم المخزن.

    • GIF المتحرّك يُصغَّر إطاراً بإطار فيبقى متحرّكاً؛
    • الصور الثابتة تُصغَّر بنسب متدرّجة حتى تدخل في `target`؛
    • يُعيد None إن تعذّر (PIL غير مثبّتة أو صورة غير مدعومة).
    """
    try:
        import io
        from PIL import Image, ImageSequence
    except Exception:
        return None
    try:
        src = Image.open(io.BytesIO(data))
        src.load()
    except Exception:
        return None

    is_gif = path.lower().endswith(".gif")
    ext = os.path.splitext(path)[1].lower()

    for scale in (0.75, 0.55, 0.4, 0.28, 0.2):
        out = io.BytesIO()
        try:
            if is_gif:
                frames, durations = [], []
                for frame in ImageSequence.Iterator(src):
                    w, h = frame.size
                    f = frame.convert("RGBA").resize(
                        (max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS
                    ).convert("P", palette=Image.ADAPTIVE)
                    frames.append(f)
                    durations.append(int(frame.info.get("duration") or 100))
                if not frames:
                    return None
                frames[0].save(
                    out, format="GIF", save_all=True, append_images=frames[1:],
                    duration=durations, loop=0, optimize=True,
                )
            else:
                w, h = src.size
                im = src.convert("RGBA").resize(
                    (max(1, int(w * scale)), max(1, int(h * scale))), Image.LANCZOS
                )
                if ext in (".jpg", ".jpeg"):
                    im.convert("RGB").save(out, format="JPEG", quality=84, optimize=True)
                elif ext == ".webp":
                    im.save(out, format="WEBP", quality=84, method=5)
                else:
                    im.convert("RGB").save(out, format="PNG", optimize=True)
        except Exception:
            continue
        if out.tell() <= target:
            return out.getvalue()
    return None

# ------------------------------------------------------------------- الرفع
def upload_file(url, key, path, data, ctype, bucket=BUCKET):
    import urllib.error
    import urllib.parse
    import urllib.request

    headers_ = {"apikey": key, "x-upsert": "true", "cache-control": "3600"}
    if key.startswith("eyJ"):
        headers_["Authorization"] = f"Bearer {key}"
    headers_["Content-Type"] = ctype
    endpoint = f"{url.rstrip('/')}/storage/v1/object/{bucket}/" + urllib.parse.quote(path, safe="/")
    req = urllib.request.Request(endpoint, data=data, method="POST", headers=headers_)
    try:
        with urllib.request.urlopen(req, timeout=180) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as exc:
        return exc.code, exc.read().decode("utf-8", "replace")


def main() -> int:
    ap = argparse.ArgumentParser(description="رفع بانرات الملف الشخصي + توليد SQL")
    ap.add_argument("--zip", help="ملف Banner.zip من المالك")
    ap.add_argument("--dir", help="مجلد مستخرج (بدل --zip)")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""), help="رابط المشروع")
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", ""), help="المفتاح السري sb_secret_…")
    ap.add_argument("--sql", default=DEFAULT_SQL, help="مسار ملف SQL الناتج")
    ap.add_argument("--sql-only", action="store_true", help="توليد SQL فقط بلا رفع")
    ap.add_argument("--seed", action="store_true",
                    help="تسجيل الأغلفة في site_assets عبر REST بعد الرفع (بلا لصق SQL)")
    ap.add_argument("--seed-only", action="store_true",
                    help="تسجيل الأغلفة فقط (الصور مرفوعة مسبقاً)")
    ap.add_argument("--plan", default=REQUIRED_PLAN,
                    help="الخطة المطلوبة للأغلفة: epic (افتراضي) أو admin (حصرية للإدارة)")
    ap.add_argument("--dry-run", action="store_true", help="طباعة ما سيُرفع بلا رفع")
    args = ap.parse_args()

    if not args.zip and not args.dir:
        print("✗ حدّد المصدر: --zip Banner.zip  أو  --dir ./Banner")
        return 2

    items = read_source(args.zip, args.dir)
    if not items:
        print("✗ لم تُوجد صور داخل المصدر")
        return 2

    rows, uploads = build_rows(items, args.plan)
    total_mb = sum(len(d) for _, d, _ in uploads) / 1024 / 1024
    gifs = sum(1 for p, _, _ in uploads if p.lower().endswith(".gif"))
    print(f"→ البانرات: {len(rows)}  (متحركة gif: {gifs})   الحجم: {total_mb:.1f} MB")

    write_sql(rows, args.sql, args.plan)
    print(f"✓ كُتب ملف SQL: {args.sql}")

    if args.sql_only:
        print("✓ SQL فقط — لم يُرفع شيء. للرفع أضف --url و--key.")
        return 0

    if args.seed_only:
        # الصور مرفوعة مسبقاً: نسجّل الصفوف فقط (بلا رفع)
        if not args.url.startswith("http") or not args.key:
            print("✗ للتسجيل مرّر --url https://xxxx.supabase.co و--key sb_secret_…")
            return 2
        return seed_rows(args.url, args.key, rows)

    if not args.url or not args.url.startswith("http"):
        print("✗ مرّر رابط المشروع: --url https://xxxx.supabase.co")
        return 2
    if not args.key:
        print("✗ مرّر المفتاح السري: --key sb_secret_… (أو SUPABASE_SECRET_KEY)")
        return 2
    if args.key.startswith("sb_publishable_"):
        print("✗ هذا مفتاح عام — الرفع يحتاج المفتاح السري sb_secret_…")
        return 2

    if args.dry_run:
        for path, data, _ in uploads[:10]:
            print(f"   [تجربة] {len(data)/1024:.0f} KB → {path}")
        print("✓ تجربة فقط — لم يُرفع شيء.")
        return 0

    ok = failed = skipped = 0
    uploaded_paths = []
    for index, (path, data, ctype) in enumerate(uploads, 1):
        note = ""
        # ملف أكبر من حدّنا المبدئي ⇒ نصغّره نسبياً قبل الرفع
        if len(data) > MAX_UPLOAD_BYTES:
            shrunk = shrink_to_fit(data, path, MAX_UPLOAD_BYTES)
            if shrunk:
                note = f"  ⇠ صُغِّر {len(data) / 1024 / 1024:.1f}MB ⇒ {len(shrunk) / 1024 / 1024:.1f}MB"
                data = shrunk
        status, body = upload_file(args.url, args.key, path, data, ctype)
        # رفض بسبب حدّ حجم المخزن ⇒ تصغير أقوى ثم إعادة المحاولة
        if not (200 <= status < 300) and "maximum allowed size" in (body or "").lower():
            for target in (2 * 1024 * 1024, 1024 * 1024, 512 * 1024):
                shrunk = shrink_to_fit(data, path, target)
                if not shrunk:
                    break
                status, body = upload_file(args.url, args.key, path, shrunk, ctype)
                if 200 <= status < 300:
                    note = f"  ⇠ صُغِّر إلى {len(shrunk) / 1024 / 1024:.1f}MB"
                    break
        if 200 <= status < 300:
            ok += 1
            uploaded_paths.append(path)
            print(f"   [{index}/{len(uploads)}] ✓ {path}{note}")
        else:
            detail = ""
            try:
                detail = json.loads(body).get("message", "")
            except Exception:
                detail = (body or "")[:120]
            if "maximum allowed size" in (body or "").lower():
                skipped += 1
                print(
                    f"   [{index}/{len(uploads)}] ⚠ تخطّي (أكبر من حدّ المخزن "
                    f"{len(data) / 1024 / 1024:.1f}MB): {path} {detail}"
                )
            else:
                failed += 1
                print(f"   [{index}/{len(uploads)}] ✗ {path} — HTTP {status} {detail}")

    print("\n================ الخلاصة ================")
    print(f"✅ نجح: {ok}    ❌ فشل: {failed}    ⚠ مُتخطّى (أكبر من الحدّ): {skipped}")
    if ok and args.seed:
        # نسجّل **ما رُفع فعلاً** فقط، حتى لا يظهر غلاف بلا صورة
        done = set(uploaded_paths)
        rows_ok = [r for r in rows if r["file_url"] in done]
        return seed_rows(args.url, args.key, rows_ok)
    if ok and failed == 0 and skipped == 0:
        print("الآن نفّذ في Supabase → SQL Editor:")
        print(f"   {os.path.relpath(args.sql, ROOT)}")
        print("أو سجّلها بلا لصق SQL:  python3 tools/upload_banners.py --zip Banner.zip --url … --key … --seed-only")
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
