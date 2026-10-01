#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة شاشة البداية بعد جعل صورة القرية (الغروب) خلفيةً.

يرسم نفس تركيب SplashScreen: صورة مقصوصة من الأعلى + تعتيم متدرّج + الشعار +
القاعدة الرفيعة + الكلمات الثلاث + العلامة التجارية. النصوص من ملفات الموارد الفعلية.
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFilter, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
BG = os.path.join(RES, "drawable-nodpi", "splash_bg.jpg")
LOGO = os.path.join(RES, "drawable-nodpi", "splash_logo.png")
OUT = os.path.join(ROOT, "splash_background_preview.png")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

SCURVE = [(0.0, 140), (0.26, 79), (0.45, 90), (0.65, 107), (0.85, 122), (1.0, 146)]


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def string_of(locale, name):
    path = os.path.join(RES, locale, "strings.xml")
    txt = io.open(path, encoding="utf-8").read()
    m = re.search(r'<string name="%s">([^<]*)</string>' % re.escape(name), txt)
    return m.group(1).replace("\\'", "'") if m else name


def scrim(w, h):
    layer = Image.new("L", (1, h))
    px = layer.load()
    for y in range(h):
        t = y / max(1, h - 1)
        for i in range(len(SCURVE) - 1):
            a, ta = SCURVE[i]
            b, tb = SCURVE[i + 1]
            if a <= t <= b:
                v = ta + (tb - ta) * ((t - a) / max(1e-6, tb - a))
                break
        else:
            v = SCURVE[-1][1]
        px[0, y] = int(v)
    layer = layer.resize((w, h))
    black = Image.new("RGB", (w, h), (0, 0, 0))
    return black, layer


def screen(locale, label, w=430, h=880):
    """يعيد صورة شاشة كاملة بنفس طبقات SplashScreen."""
    base = Image.open(BG).convert("RGB")
    # ContentScale.Crop + TopCenter
    k = max(w / base.width, h / base.height)
    nw, nh = int(base.width * k + 1), int(base.height * k + 1)
    base = base.resize((nw, nh), Image.LANCZOS)
    base = base.crop(((nw - w) // 2, (nh - h) // 2, (nw - w) // 2 + w, (nh - h) // 2 + h))
    black, mask = scrim(w, h)
    img = Image.composite(black, base, mask)

    # خطوط مسح باهتة (ScanLines)
    d = ImageDraw.Draw(img, "RGBA")
    for i in range(0, h, 19):
        d.line((0, i, w, i), fill=(255, 255, 255, 8 if i % 57 else 12), width=1)

    f_line = ImageFont.truetype(FONT_BOLD, 25)
    f_brand = ImageFont.truetype(FONT_BOLD, 21)

    y = int(h * 0.30)
    logo = Image.open(LOGO).convert("RGBA")
    lk = (w * 0.92) / logo.width
    logo = logo.resize((int(logo.width * lk), int(logo.height * lk)), Image.LANCZOS)
    shadow = Image.new("RGBA", logo.size, (0, 0, 0, 190))
    img.paste(shadow, ((w - logo.width) // 2, y + 3), shadow)
    img.paste(logo, ((w - logo.width) // 2, y), logo)
    y += logo.height + 12
    d.rectangle((w // 2 - 60, y, w // 2 + 60, y + 2), fill=(0, 112, 204, 153))

    y += 40
    for key in ("splash_dream_it", "splash_build_it", "splash_create_it"):
        text = string_of(locale, key)
        if locale.startswith("values-ar") or locale.startswith("values-ru") or locale.startswith("values-zh"):
            text = rtl(text)
        for dx, dy in ((2, 2),):
            d.text((w // 2 + dx, y + dy), text, font=f_line, fill=(0, 0, 0, 220), anchor="mm")
        d.text((w // 2, y), text, font=f_line, fill=(255, 255, 255), anchor="mm")
        y += 41

    y += 32
    brand = string_of(locale, "brand_wordmark")
    d.text((w // 2, y), brand, font=f_brand, fill=(255, 255, 255), anchor="mm")
    for i in range(180):
        t = i / 179
        a = int(255 * (1 - abs(2 * t - 1)))
        d.line((w // 2 - 90 + i, y + 20, w // 2 - 90 + i + 1, y + 22), fill=(0, 112, 204, a))
    return img, label


def main() -> int:
    screens = [screen("values", "English"), screen("values-ar", "العربية"),
               screen("values-ru", "Русский")]
    pad, top = 30, 66
    w, h = screens[0][0].size
    W = pad * 4 + w * 3
    H = top + h + 84
    img = Image.new("RGB", (W, H), "#080C12")
    d = ImageDraw.Draw(img)
    d.rectangle((0, 0, W, top - 14), fill="#111823")
    f_h = ImageFont.truetype(FONT_BOLD, 22)
    d.text((W - 22, 16), rtl("شاشة البداية — صورة القرية عند الغروب خلفيةً للشعار والكلمات"),
           font=f_h, fill="#E8EEF5", anchor="ra")
    for i, (scr, label) in enumerate(screens):
        x = pad + i * (w + pad)
        img.paste(scr, (x, top))
        d.rounded_rectangle((x - 2, top - 2, x + w + 2, top + h + 2), radius=26, outline="#263240", width=2)
        d.text((x + w // 2, top + h + 22), label, font=ImageFont.truetype(FONT_BOLD, 16),
               fill="#8EA0B3", anchor="mm")
    bw, bh = Image.open(BG).size
    bkb = os.path.getsize(BG) / 1024
    note = [
        "الصورة: drawable-nodpi/splash_bg.jpg (%d×%d · %d KB) — مقصوصة من المنتصف (Center) ليبقى الأفق والشمس والقرية في أي مقاس شاشة."
        % (bw, bh, round(bkb)),
        "تعتيم متدرّج (شفافية 31%–55%) فوقها ليقرأ الشعار الأبيض والكلمات بوضوح، مع بقاء حركات التلاشي والتكبير وخطوط المسح والجسيمات كما هي.",
    ]
    for i, line in enumerate(note):
        d.text((W - 22, top + h + 48 + i * 20), rtl(line), font=ImageFont.truetype(FONT_BOLD, 14),
               fill="#5A6B7D", anchor="ra")
    img = img.filter(ImageFilter.SMOOTH)
    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
