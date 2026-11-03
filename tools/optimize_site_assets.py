#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
مُحسِّن صور الأصول — يجعل مكتبة `site-assets` **خفيفة** من مكان واحد (بلا هاتف).

لماذا؟
    مكتبة الأصول كانت ~176 ميجابايت، وفيها صور حتى 4.8 ميجابايت، و214 ملف GIF.
    النتيجة على الهاتف: شبكة الأصول تُحمّل ببطء، والتمرير يتهنّج، والبيانات
    تُستهلك بسرعة. زر «تصغير صور الأصول» داخل التطبيق يفعل ذلك أيضاً، لكنه
    يحتاج جلسة أدمن على الهاتف وينقطع مع مئات الصور. هذه الأداة تفعل نفس العمل
    **من سطر أوامر / GitHub Actions** بمفتاح الخادم: أسرع، وبلا انقطاع، وتُنتج
    تقريراً بالأرقام.

ما تفعله لكل ملف في المخزن (المخزن `site-assets` العام):
    1) **GIF (أو أصل مُعلَّم animated)**: لا تلمس الأصل (الحركة مطلوبة)، بل
       تولّد بجانبه لقطة ثابتة `<المسار>.thumb.jpg` (بمقاس 384 بكسل، JPEG).
       شبكات التطبيق تستعمل اللقطة تلقائياً (انظر `CoverImage`/`AssetThumb`)،
       وGIF الأصلي يبقى يُعرض في المعاينات المفردة فقط.
    2) **صورة ثابتة كبيرة** (jpg/jpeg/png/webp أكبر من `--min-bytes`): تُصغَّر
       إلى أقصى بُعد حسب نوعها (ملصق/بروفايل 512 · أدوات وأغلفة 1280) وتُعاد
       ترميزها: PNG بلا شفافية ⇒ JPEG (أخفّ بكثير)، وPNG بشفافية ⇒ PNG
       مضغوط، وباقي الصيغ تبقى بصيغتها. تُرفع فقط إن صارت **أصغر فعلاً**.
    3) **SVG** وملفات `.thumb.jpg` ⇒ تُترك كما هي.

الاستخدام:
    # تجربة بلا كتابة (تقرير فقط)
    python3 tools/optimize_site_assets.py --url https://xxx.supabase.co \
        --key sb_secret_… --dry-run

    # التنفيذ الفعلي (يُفضّل من GitHub Actions حيث المفتاح مخزّن كسرّ)
    python3 tools/optimize_site_assets.py --url … --key … [--limit 50]

    # فحص ذاتي بلا شبكة: يولّد صوراً وهمية ويتأكّد من منطق التصغير واللقطات
    python3 tools/optimize_site_assets.py --selftest

ملاحظة مهمّة: المفتاح السري يبقى **على الخادم/جهازك فقط** ولا يدخل التطبيق أبداً.
"""
import argparse
import io
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request

try:
    from PIL import Image, ImageSequence
except ImportError:  # pragma: no cover
    print("✗ يحتاج Pillow:  python3 -m pip install --break-system-packages pillow")
    sys.exit(1)

BUCKET = "site-assets"

# أقصى بُعد لكل نوع أصل (بالبكسل) — مبنيّ على أكبر مقاس عرض فعلي في التطبيق:
#   ملصق/بروفايل: خانة الشبكة ~104dp ⇒ 312px على أعلى كثافة · 512 يكفي وأكثر.
#   أداة/غلاف: صفحة العنصر بعرض الشاشة 195dp ارتفاعاً ⇒ 1170px · 1280 يكفي.
MAX_SIDE = {
    "stickers": 512,
    "profiles": 512,
    "banners": 1280,
    "items": 1280,
}
DEFAULT_MAX_SIDE = 768

THUMB_SIDE = 384          # لقطة الـGIF الثابتة
THUMB_QUALITY = 82
JPEG_QUALITY = 84
MIN_BYTES = 120 * 1024    # أصغر من ذلك: لا فائدة من إعادة الترميز
KEEP_RATIO = 0.90         # لا نرفع إلا إذا صغر الملف 10% على الأقل


# --------------------------------------------------------------------------- شبكة

def http(method, url, key=None, data=None, content_type=None, timeout=90):
    req = urllib.request.Request(url, data=data, method=method)
    if key:
        req.add_header("apikey", key)
        req.add_header("Authorization", "Bearer " + key)
    if content_type:
        req.add_header("Content-Type", content_type)
    req.add_header("Accept", "application/json")
    with urllib.request.urlopen(req, timeout=timeout) as res:
        return res.read()


def list_objects(base_url, key, prefix=""):
    """يسرد كل ملفات المخزن (يتنقّل داخل المجلدات) — ترقيم صفحات 100."""
    body = json.dumps({
        "prefix": prefix,
        "limit": 100,
        "offset": 0,
        "sortBy": {"column": "name", "order": "asc"},
    }).encode("utf-8")
    out = []
    offset = 0
    while True:
        body = json.dumps({
            "prefix": prefix,
            "limit": 100,
            "offset": offset,
            "sortBy": {"column": "name", "order": "asc"},
        }).encode("utf-8")
        raw = http("POST", f"{base_url}/storage/v1/object/list/{BUCKET}", key, body, "application/json")
        rows = json.loads(raw.decode("utf-8") or "[]")
        if not rows:
            break
        for row in rows:
            name = row.get("name") or ""
            if not name:
                continue
            path = f"{prefix}{name}"
            # المجلدات تأتي بلا معرّف/ميتاداتا ⇒ ننزل داخلها
            if row.get("id") is None and not (row.get("metadata") or {}):
                out.extend(list_objects(base_url, key, path + "/"))
            else:
                meta = row.get("metadata") or {}
                out.append({
                    "path": path,
                    "size": int(meta.get("size") or meta.get("contentLength") or 0),
                    "mime": meta.get("mimetype") or "",
                })
        if len(rows) < 100:
            break
        offset += 100
    return out


def upload(base_url, key, path, data, content_type):
    q = urllib.parse.quote(path.lstrip("/"))
    req = urllib.request.Request(f"{base_url}/storage/v1/object/{BUCKET}/{q}", data=data, method="POST")
    req.add_header("apikey", key)
    req.add_header("Authorization", "Bearer " + key)
    req.add_header("Content-Type", content_type)
    req.add_header("x-upsert", "true")
    req.add_header("cache-control", "max-age=3600")
    with urllib.request.urlopen(req, timeout=120) as res:
        return res.status


# --------------------------------------------------------------------------- صور

def max_side_for(path):
    head = path.split("/", 1)[0]
    return MAX_SIDE.get(head, DEFAULT_MAX_SIDE)


def open_image(data):
    im = Image.open(io.BytesIO(data))
    im.load()
    return im


def flatten_for_jpeg(im):
    """يُرجع نسخة RGB: الشفافية تُركَّب على الأسود (نفس ما يفعله التطبيق)."""
    if im.mode in ("RGBA", "LA", "P"):
        rgba = im.convert("RGBA")
        bg = Image.new("RGB", rgba.size, (0, 0, 0))
        bg.paste(rgba, mask=rgba.split()[-1])
        return bg
    return im.convert("RGB")


def has_real_alpha(im):
    if im.mode not in ("RGBA", "LA", "P"):
        return False
    rgba = im.convert("RGBA")
    lo, _hi = rgba.split()[-1].getextrema()
    return lo < 250          # يوجد بكسل فعلاً شفاف/نصف شفاف


def scaled(im, max_side):
    w, h = im.size
    longest = max(w, h)
    if longest <= max_side:
        return im
    ratio = max_side / float(longest)
    return im.resize((max(1, round(w * ratio)), max(1, round(h * ratio))), Image.LANCZOS)


def encode_jpeg(im, quality=JPEG_QUALITY):
    out = io.BytesIO()
    flatten_for_jpeg(im).save(out, "JPEG", quality=quality, optimize=True, progressive=True)
    return out.getvalue(), "image/jpeg"


def encode_png(im):
    """PNG بشفافية: نُبقي القناة لكن نُقلّل الألوان (توفير كبير بلا فرق مرئي)."""
    out = io.BytesIO()
    if im.mode in ("RGBA", "LA", "P"):
        rgba = im.convert("RGBA")
        try:
            rgba = rgba.quantize(colors=256, method=Image.FASTOCTREE)
        except Exception:  # pragma: no cover
            pass
        rgba.save(out, "PNG", optimize=True)
    else:
        im.convert("RGB").save(out, "PNG", optimize=True)
    return out.getvalue(), "image/png"


def make_thumbnail(data):
    """لقطة ثابتة من أول إطار (للـGIF) بمقاس THUMB_SIDE — JPEG."""
    im = Image.open(io.BytesIO(data))
    frame = next(ImageSequence.Iterator(im)).convert("RGBA")
    return encode_jpeg(scaled(frame, THUMB_SIDE), THUMB_QUALITY)


def optimize_still(data, path):
    """
    يُرجع (bytes, mime) للنسخة المصغّرة، أو None إن لم يكن هناك توفير يُذكر.
    """
    im = open_image(data)
    target = scaled(im, max_side_for(path))
    if has_real_alpha(im):
        blob, mime = encode_png(target)
    else:
        blob, mime = encode_jpeg(target)
    return blob, mime


# --------------------------------------------------------------------------- التشغيل

def process(base_url, key, dry_run=False, limit=None, only_thumbnails=False, verbose=True):
    files = list_objects(base_url, key)
    files.sort(key=lambda f: -f["size"])
    if limit:
        files = files[:limit]

    before_total = sum(f["size"] for f in files)
    stats = {"thumbs": 0, "shrunk": 0, "skipped": 0, "failed": 0}
    saved = 0
    rows = []

    for f in files:
        path, size = f["path"], f["size"]
        lower = path.lower()
        if lower.endswith(".svg") or lower.endswith(".thumb.jpg"):
            stats["skipped"] += 1
            continue
        is_gif = lower.endswith(".gif") or f["mime"] == "image/gif"
        try:
            public = f"{base_url}/storage/v1/object/public/{BUCKET}/{urllib.parse.quote(path)}"
            data = urllib.request.urlopen(public, timeout=90).read()
        except Exception as exc:
            stats["failed"] += 1
            if verbose:
                print(f"  ✗ تنزيل فاشل: {path} ({exc})")
            continue

        try:
            if is_gif:
                blob, mime = make_thumbnail(data)
                target_path = path + ".thumb.jpg"
                if not dry_run:
                    upload(base_url, key, target_path, blob, mime)
                stats["thumbs"] += 1
                saved += max(0, size - len(blob))
                rows.append((path, size, len(blob), "لقطة ثابتة"))
                continue

            if only_thumbnails or size <= MIN_BYTES:
                stats["skipped"] += 1
                continue

            blob, mime = optimize_still(data, path)
            if len(blob) >= size * KEEP_RATIO:
                stats["skipped"] += 1
                continue
            if not dry_run:
                upload(base_url, key, path, blob, mime)
            stats["shrunk"] += 1
            saved += size - len(blob)
            rows.append((path, size, len(blob), "تصغير"))
        except Exception as exc:
            stats["failed"] += 1
            if verbose:
                print(f"  ✗ معالجة فاشلة: {path} ({exc})")

    return {
        "files": len(files),
        "before": before_total,
        "after": before_total - saved,
        "saved": saved,
        "stats": stats,
        "rows": rows,
    }


def report(result, dry_run):
    kb = lambda n: f"{n / 1024:.0f} KB"
    print()
    print("=" * 62)
    print("مُحسِّن صور الأصول — " + ("تجربة بلا كتابة" if dry_run else "تنفيذ فعلي"))
    print("=" * 62)
    print(f"ملفات مفحوصة: {result['files']}")
    print(f"لقطات ثابتة وُلِّدت (GIF): {result['stats']['thumbs']}")
    print(f"صور صُغّرت في مكانها:      {result['stats']['shrunk']}")
    print(f"تُركت كما هي:              {result['stats']['skipped']}")
    print(f"فشلت:                      {result['stats']['failed']}")
    print(f"الحجم قبل: {kb(result['before'])} · بعده: {kb(result['after'])} "
          f"· التوفير: {kb(result['saved'])} ({result['saved'] * 100.0 / max(1, result['before']):.1f}%)")
    top = sorted(result["rows"], key=lambda r: r[1] - r[2], reverse=True)[:12]
    if top:
        print("\nأكبر المكاسب:")
        for path, before, after, kind in top:
            print(f"  {kind:10s} {before/1024:8.0f} KB → {after/1024:7.0f} KB  {path}")
    print()


# --------------------------------------------------------------------------- فحص ذاتي

def selftest():
    """يتحقّق من منطق التصغير/اللقطات على صور مُصنَّعة — بلا شبكة."""
    import tempfile
    ok = True

    def check(name, cond):
        nonlocal ok
        print(("  ✓ " if cond else "  ✗ ") + name)
        ok = ok and cond

    tmp = tempfile.mkdtemp()
    # ١) GIF بسيط بحركتين ⇒ لقطة ثابتة خفيفة
    gif_path = os.path.join(tmp, "anim.gif")
    frames = [Image.new("P", (900, 600), i) for i in (2, 5, 9)]
    frames[0].save(gif_path, save_all=True, append_images=frames[1:], duration=200)
    gif_bytes = io.open(gif_path, "rb").read()
    thumb, mime = make_thumbnail(gif_bytes)
    tim = Image.open(io.BytesIO(thumb))
    check("لقطة الـGIF صيغتها JPEG", mime == "image/jpeg")
    check("لقطة الـGIF بمقاس صحيح (384 كحدّ)", max(tim.size) == THUMB_SIDE)
    check("لقطة الـGIF أخفّ من الأصل", len(thumb) < len(gif_bytes))

    # ٢) PNG فوتوغرافي كبير بلا شفافية ⇒ JPEG أصغر بكثير
    #    (ضجيج يملأ كل بكسل: أقرب تمثيل لصورة فوتوغرافية حقيقية — وهذا هو
    #     الحال السائد في المكتبة: أغلفة وأدوات مصدَّرة PNG ثقيلة)
    import random
    random.seed(7)
    im = Image.frombytes("RGB", (1600, 1200),
                         random.randbytes(1600 * 1200 * 3))
    photo = os.path.join(tmp, "big.png")
    im.save(photo, "PNG")
    raw = io.open(photo, "rb").read()
    blob, mime2 = optimize_still(raw, "items/games/cover.png")
    check("PNG بلا شفافية ⇒ JPEG", mime2 == "image/jpeg")
    check("النسخة أصغر 50% على الأقل", len(blob) < len(raw) * 0.5)
    check("أقصى بُعد للأداة 1280", max(Image.open(io.BytesIO(blob)).size) == 1280)

    # ٢ب) صورة مسطّحة صغيرة الأصل لا تُرفع (بوّابة KEEP_RATIO تمنع التضخيم)
    flat = Image.new("RGB", (1600, 1200), (20, 20, 20))
    fb = io.BytesIO()
    flat.save(fb, "PNG")
    blobf, _ = optimize_still(fb.getvalue(), "items/games/flat.png")
    check("لا تضخيم: تُرفض النسخة الأكبر من الأصل", len(blobf) >= len(fb.getvalue()) * KEEP_RATIO)

    # ٣) PNG بشفافية ⇒ يبقى PNG وشفافيته محفوظة
    half = os.path.join(tmp, "alpha.png")
    a = Image.new("RGBA", (800, 800), (255, 0, 0, 0))
    a.paste((0, 200, 0, 255), (100, 100, 700, 700))
    a.save(half, "PNG")
    rawa = io.open(half, "rb").read()
    bloba, mime3 = optimize_still(rawa, "stickers/memes/alpha.png")
    check("PNG بشفافية ⇒ يبقى PNG", mime3 == "image/png")
    check("الشفافية محفوظة", has_real_alpha(Image.open(io.BytesIO(bloba))))
    check("أقصى بُعد للملصق 512", max(Image.open(io.BytesIO(bloba)).size) == 512)

    # ٤) صورة صغيرة لا تُلمس
    small = os.path.join(tmp, "small.jpg")
    Image.new("RGB", (300, 300), (10, 20, 30)).save(small, "JPEG", quality=60)
    raws = io.open(small, "rb").read()
    check("الصغيرة تُترك (تُحسب في skipped)", len(raws) <= MIN_BYTES)

    # ٥) جدول المقاسات
    check("ملصق/بروفايل 512 · أدوات/أغلفة 1280",
          max_side_for("stickers/x/a.gif") == 512 and
          max_side_for("profiles/pfp/a.png") == 512 and
          max_side_for("items/games/a.png") == 1280 and
          max_side_for("banners/nature/a.gif") == 1280)

    print("\n" + ("🟢 الفحص الذاتي ناجح — المنطق سليم (لقطات · تصغير · شفافية · مقاسات)."
                  if ok else "🔴 الفحص الذاتي فشل."))
    return 0 if ok else 1


def main():
    ap = argparse.ArgumentParser(description="مُحسِّن صور الأصول في مخزن site-assets")
    ap.add_argument("--url", default=os.environ.get("SUPABASE_URL", ""))
    ap.add_argument("--key", default=os.environ.get("SUPABASE_SECRET_KEY", ""))
    ap.add_argument("--dry-run", action="store_true", help="تقرير بلا رفع")
    ap.add_argument("--limit", type=int, default=None, help="أقصى عدد ملفات (للاختبار)")
    ap.add_argument("--thumbs-only", action="store_true", help="لقطات الـGIF فقط بلا تصغير")
    ap.add_argument("--selftest", action="store_true", help="فحص ذاتي بلا شبكة")
    args = ap.parse_args()

    if args.selftest:
        return selftest()

    if not args.url or not args.key:
        print("✗ يلزم --url و--key (أو SUPABASE_URL و SUPABASE_SECRET_KEY في البيئة)")
        return 2
    base = args.url.rstrip("/")
    result = process(base, args.key, dry_run=args.dry_run, limit=args.limit,
                     only_thumbnails=args.thumbs_only)
    report(result, args.dry_run)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
