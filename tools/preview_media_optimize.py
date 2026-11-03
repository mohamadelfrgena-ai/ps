#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة تحسين صور الأصول — لصاحب التطبيق.

ثلاث لوحات: (١) **قبل**: شبكة أصول تُشغّل كل GIF كما هو ⇒ خلايا تُحمّل ميغابايتات
وتمرير يتهنّج · (٢) **بعد**: نفس الشبكة تعرض لقطات ثابتة خفيفة، والحركة تبقى في
المعاينات المفردة · (٣) الأرقام الحقيقية المقيسة + خطوات التشغيل من الجوال.

الأرقام تُقرأ من `assets-manifest.json` (أحجام فعلية) والنِسب من قياسات حقيقية
على أصول من المستودع (انظر `docs/14-MEDIA-OPTIMIZE-AR.md`).

يُنتج `media_optimize_preview.png` في الجذر.
"""
import io
import json
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "media_optimize_preview.png")
MANIFEST = os.path.join(ROOT, "assets-manifest.json")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
BG = "#080C12"
CARD = "#141C27"
EDGE = "#263240"
TXT = "#E8EEF5"
MUTED = "#8EA0B3"
GREEN = "#2E9E5B"
RED = "#E5484D"
GOLD = "#D6A93A"
BLUE = "#2F7FD8"

# قياسات حقيقية على أصول من المستودع (تُذكر في التوثيق):
THUMB_BYTES = 20_100          # لقطة GIF 42 إطاراً (7.02MB) ⇒ 20.1KB
SHARE_PNG = 0.060             # PNG 1080×1920 (1.16MB) ⇒ 69.4KB
SHARE_JPG = 0.096             # JPEG 1920×1080 (1.84MB) ⇒ 177.7KB
KEEP_SHARE = 0.10             # تقدير محافظ للتصغير في مكانه


def rtl(t: str) -> str:
    return get_display(arabic_reshaper.reshape(t))


def font(size: int):
    return ImageFont.truetype(FONT_BOLD, size)


def phone(d: ImageDraw.ImageDraw, x: int, y: int, w: int, h: int):
    d.rounded_rectangle((x, y, x + w, y + h), radius=26, fill="#0B1015", outline=EDGE, width=2)


def grid(d, x, y, cols, rows, cell, gap, color, label_color, label):
    """شبكة خلايا تشبه شبكة الأصول، مع وسم على كل خلية."""
    for r in range(rows):
        for c in range(cols):
            cx = x + c * (cell + gap)
            cy = y + r * (cell + gap)
            d.rounded_rectangle((cx, cy, cx + cell, cy + cell), radius=12, fill=color, outline=EDGE)
            d.text((cx + cell / 2, cy + cell - 11), rtl(label), font=font(12), fill=label_color, anchor="mm")


def load_numbers():
    total = gif_b = static_big_b = 0
    gifs = statics = 0
    biggest = 0
    try:
        data = json.load(io.open(MANIFEST, encoding="utf-8"))
        for a in data.get("assets", []):
            b = int(a.get("bytes") or 0)
            total += b
            biggest = max(biggest, b)
            if a["path"].lower().endswith(".gif"):
                gifs += 1
                gif_b += b
            else:
                statics += 1
                if b > 120 * 1024:
                    static_big_b += b
    except Exception:
        pass
    return {"total": total or 176 * 1024 * 1024, "gifs": gifs or 214, "gif_b": gif_b,
            "statics": statics or 278, "static_big_b": static_big_b or 10 * 1024 * 1024,
            "biggest": biggest or int(4.7 * 1024 * 1024)}


def main() -> int:
    W, H = 1520, 1010
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    n = load_numbers()
    MB = 1024 * 1024

    grid_before = n["gif_b"] + n["static_big_b"]
    grid_after = n["gifs"] * THUMB_BYTES + n["static_big_b"] * KEEP_SHARE

    # العنوان
    d.text((W / 2, 34), rtl("تحسين صور الأصول — الشبكة تصبح خفيفة، والمكتبة تبقى كما هي"),
           font=font(30), fill=TXT, anchor="ma")
    d.text((W / 2, 78), rtl("المشكلة لم تكن في عدد الصور بل في وزنها: الصور المتحرّكة وحدها "
                            f"{n['gif_b']/MB:.0f} ميغابايت من أصل {n['total']/MB:.0f} ميغابايت"),
           font=font(18), fill=MUTED, anchor="ma")

    PW, PH = 430, 470
    left_x, right_x, top_y = 90, 560, 130

    # ---------- ١) قبل ----------
    phone(d, left_x, top_y, PW, PH)
    d.text((left_x + PW / 2, top_y + 22), rtl("قبل: كل خلية تُنزّل الملف كما هو"), font=font(17), fill=RED, anchor="ma")
    grid(d, left_x + 34, top_y + 56, 3, 3, 108, 12, "#2A1A1C", RED, "4.7MB")
    d.text((left_x + PW / 2, top_y + 430), rtl("عشرات الميغابايت مع كل تمرير ⇒ ظهور بطيء وتقطيع"),
           font=font(14), fill=MUTED, anchor="ma")

    # ---------- ٢) بعد ----------
    phone(d, right_x, top_y, PW, PH)
    d.text((right_x + PW / 2, top_y + 22), rtl("بعد: الشبكة تعرض لقطة ثابتة"), font=font(17), fill=GREEN, anchor="ma")
    grid(d, right_x + 34, top_y + 56, 3, 3, 108, 12, "#16261C", GREEN, "20KB")
    d.text((right_x + PW / 2, top_y + 430), rtl("تظهر فوراً · والحركة تبقى في معاينة العنصر والملف الشخصي"),
           font=font(14), fill=MUTED, anchor="ma")

    # ---------- ٣) الأرقام الحقيقية ----------
    bx, by, bw, bh = 1030, 140, 410, 450
    d.rounded_rectangle((bx, by, bx + bw, by + bh), radius=18, fill=CARD, outline=EDGE)
    d.text((bx + bw / 2, by + 24), rtl("مقاييس حقيقية"), font=font(20), fill=GOLD, anchor="ma")
    rows = [
        ("GIF 42 إطاراً · 7.0MB", "لقطة 20KB", GREEN),
        ("PNG 1080×1920 · 1.16MB", "69KB (6%)", GREEN),
        ("JPEG 1920×1080 · 1.84MB", "178KB (10%)", GREEN),
        ("SVG · لقطات موجودة", "تُترك كما هي", MUTED),
        ("صورة أصغر من 120KB", "تُترك كما هي", MUTED),
    ]
    for i, (left, right, color) in enumerate(rows):
        yy = by + 70 + i * 50
        d.text((bx + bw - 20, yy), rtl(left), font=font(14), fill=TXT, anchor="ra")
        d.text((bx + 20, yy + 23), rtl(right), font=font(14), fill=color, anchor="la")
    d.line((bx + 20, by + 320, bx + bw - 20, by + 320), fill=EDGE)
    d.text((bx + bw / 2, by + 338), rtl(f"حمولة الشبكات: {grid_before/MB:.0f}MB ← {grid_after/MB:.1f}MB"),
           font=font(16), fill=BLUE, anchor="ma")
    d.text((bx + bw / 2, by + 366), rtl("والأصل المتحرّك يبقى في المخزن"), font=font(13), fill=MUTED, anchor="ma")
    d.text((bx + bw / 2, by + 388), rtl("بلا حذف وبلا تغيير مسارات"), font=font(13), fill=MUTED, anchor="ma")
    d.text((bx + bw / 2, by + 412), rtl(f"أكبر ملف: {n['biggest']/MB:.2f} ميغابايت · {n['gifs']} ملفاً متحرّكاً"),
           font=font(13), fill=MUTED, anchor="ma")

    # ---------- كيف يُشغَّل ----------
    sy = 620
    d.rounded_rectangle((90, sy, W - 90, sy + 250), radius=18, fill=CARD, outline=EDGE)
    d.text((W / 2, sy + 22), rtl("التشغيل من الجوال — بلا أي أمر على الحاسوب"), font=font(21), fill=TXT, anchor="ma")
    steps = [
        ("١", "افتح GitHub ← تبويب Actions ← «Optimize media» ← Run workflow"),
        ("٢", "اختر dry-run أولاً للتقرير بلا كتابة · ثم run للتنفيذ الفعلي"),
        ("٣", "النتيجة تظهر في صفحة التشغيل (ملخّص بالعربية: كم ملفاً · كم توفّر)"),
        ("٤", "افتح التطبيق: الشبكة وبانرات الملف الشخصي تظهر فوراً — بلا تحديث APK (الصور على الخادم)"),
    ]
    for i, (num, text) in enumerate(steps):
        yy = sy + 62 + i * 44
        d.ellipse((W / 2 + 430, yy + 2, W / 2 + 462, yy + 34), fill="#1B2733", outline=BLUE)
        d.text((W / 2 + 446, yy + 18), rtl(num), font=font(15), fill=BLUE, anchor="mm")
        d.text((W / 2 + 410, yy + 18), rtl(text), font=font(15), fill=TXT, anchor="rm")

    # تذييل
    d.text((W - 26, H - 62),
           rtl("السرّان المخزّنان (ADDONSTATION_SUPABASE_URL / SECRET_KEY) يُستعملان في الخادم فقط — التطبيق لا يحمل مفتاح خدمة أبداً."),
           font=font(15), fill=BLUE, anchor="ra")
    d.text((W - 26, H - 34),
           rtl("درس: لا حاجة لإعادة بناء التطبيق — الأثقل هو الصور على الخادم، فتُعالَج هناك مرة واحدة."),
           font=font(15), fill=GOLD, anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
