#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
استيراد **أيقونات غرف الشات من المالك** (مجلد `icons` داخل «New folder (2).zip»
من مستودع vae) كما هي — بلا رسم ولا توليد ولا إعادة تصميم.

المبدأ (نفس مبدأ import_owner_icons.py):
    • قصّ حول المحتوى فقط (حدود الشفافية) ثم تربيع بلا تمديد.
    • تحجيم واحد متساوٍ إلى 160×160 بجودة عالية (LANCZOS).
    • لا دمج ألوان ولا إضافة خلفية: الشفافية تُحفظ كما أرسلها المالك.

كل أيقونة في المجلد تُسمّى باسم غرفتها، فيُطابقها هذا السكربت تلقائياً:

    general.png            → ic_room_general.png
    minecraft.png          → ic_room_minecraft.png
    creator.png            → ic_room_creator.png
    support.png            → ic_room_support.png
    social_media_makers.png→ ic_room_social.png
    arabic.png             → ic_room_lang_ar.png
    english.png            → ic_room_lang_en.png
    spanish.png            → ic_room_lang_es.png
    french.png             → ic_room_lang_fr.png
    portuguese.png         → ic_room_lang_pt.png
    russian.png            → ic_room_lang_ru.png
    chinese.png            → ic_room_lang_zh.png
    cash services.png      → ic_room_cash.png        (غرفة «خدمات النقدية»)

الأيقونة الوحيدة التي لا يوجد لها ملف في المجلد هي التركية (ic_room_lang_tr) —
تُترك كما هي بلا مساس.

الاستخدام:
    python3 tools/import_room_icons_v2.py "New folder (2).zip"
    python3 tools/import_room_icons_v2.py /مسار/مجلد/icons
"""
import os
import shutil
import sys
import tempfile
import zipfile

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DST = os.path.join(
    ROOT, "android", "app", "src", "main", "res", "drawable-nodpi",
)

SIZE = 160          # مقاس الأيقونة النهائي (nodpi ⇒ بلا تحجيم حسب كثافة الشاشة)
PAD = 0.02          # هامش صغير حول المحتوى بعد القصّ

# اسم ملف المالك ← اسم المورد في التطبيق
MAPPING = {
    "general.png": "ic_room_general.png",
    "minecraft.png": "ic_room_minecraft.png",
    "creator.png": "ic_room_creator.png",
    "support.png": "ic_room_support.png",
    "social_media_makers.png": "ic_room_social.png",
    "arabic.png": "ic_room_lang_ar.png",
    "english.png": "ic_room_lang_en.png",
    "spanish.png": "ic_room_lang_es.png",
    "french.png": "ic_room_lang_fr.png",
    "portuguese.png": "ic_room_lang_pt.png",
    "russian.png": "ic_room_lang_ru.png",
    "chinese.png": "ic_room_lang_zh.png",
    "cash services.png": "ic_room_cash.png",
}


def alpha_bbox(im: Image.Image):
    """أصغر مستطيل يحيط بالبكسلات غير الشفّافة."""
    alpha = im.convert("RGBA").split()[3]
    return alpha.getbbox()


def square_trim(im: Image.Image) -> Image.Image:
    """يقصّ مربّعاً حول المحتوى (بلا تمديد ولا تغيير نسبة)."""
    im = im.convert("RGBA")
    box = alpha_bbox(im) or (0, 0, im.width, im.height)
    x0, y0, x1, y1 = box
    w, h = x1 - x0, y1 - y0
    side = max(w, h)
    cx, cy = (x0 + x1) // 2, (y0 + y1) // 2
    pad = int(side * PAD)
    side = min(max(side + 2 * pad, 1), max(im.width, im.height))
    left = max(0, min(cx - side // 2, im.width - side)) if im.width > side else 0
    top = max(0, min(cy - side // 2, im.height - side)) if im.height > side else 0
    return im.crop((left, top, left + side, top + side))


def prepare(src_path: str) -> Image.Image:
    im = Image.open(src_path)
    im = square_trim(im)
    return im.resize((SIZE, SIZE), Image.LANCZOS)


def collect(source: str) -> str:
    """يرجع مجلداً يحوي ملفات الأيقونات (من أرشيف مضغوط أو مجلد جاهز)."""
    if os.path.isdir(source):
        return source
    if not os.path.exists(source):
        sys.exit(f"⛔ المسار غير موجود: {source}")
    work = tempfile.mkdtemp(prefix="room-icons-")
    with zipfile.ZipFile(source) as z:
        for info in z.infolist():
            name = info.filename.rsplit("/", 1)[-1]
            if not name.lower().endswith(".png"):
                continue
            if "/icons/" not in info.filename.replace("\\", "/"):
                continue
            with z.open(info) as src, open(os.path.join(work, name), "wb") as dst:
                shutil.copyfileobj(src, dst)
    return work


def main() -> int:
    source = sys.argv[1] if len(sys.argv) > 1 else "New folder (2).zip"
    folder = collect(source)
    os.makedirs(DST, exist_ok=True)

    written, missing = [], []
    for src_name, dst_name in MAPPING.items():
        path = os.path.join(folder, src_name)
        if not os.path.exists(path):
            missing.append(src_name)
            continue
        prepare(path).save(os.path.join(DST, dst_name), "PNG", optimize=True)
        written.append(dst_name)

    print(f"الأيقونات المكتوبة ({len(written)}/{len(MAPPING)}):")
    for name in written:
        print("  ✓", name)
    if missing:
        print("غير موجودة في المصدر (تُركت بلا تغيير):")
        for name in missing:
            print("  –", name)
    return 0


if __name__ == "__main__":
    sys.exit(main())
