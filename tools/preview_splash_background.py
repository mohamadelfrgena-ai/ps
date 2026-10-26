#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة شاشة البداية السينمائية الجديدة فوق صورة الكهف الطولية.

يرسم تقريباً طبقات SplashScreen الحالية: المشهد + التعتيم السينمائي + شارة
AddonStation + DREAM/BUILD/CREATE بألوان الضوء والماس واللافا.
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFilter, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
BG = os.path.join(RES, "drawable-nodpi", "splash_cave_chase.jpg")
APP_ICON = os.path.join(RES, "drawable-nodpi", "app_icon.png")
OUT = os.path.join(ROOT, "splash_background_preview.png")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"

SCURVE = [(0.0, 225), (0.20, 110), (0.42, 20), (0.70, 0), (0.86, 112), (1.0, 240)]


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
    # ContentScale.Crop + Center
    k = max(w / base.width, h / base.height)
    nw, nh = int(base.width * k + 1), int(base.height * k + 1)
    base = base.resize((nw, nh), Image.LANCZOS)
    base = base.crop(((nw - w) // 2, (nh - h) // 2, (nw - w) // 2 + w, (nh - h) // 2 + h))
    black, mask = scrim(w, h)
    img = Image.composite(black, base, mask)

    d = ImageDraw.Draw(img, "RGBA")
    f_line = ImageFont.truetype(FONT_BOLD, 27)
    f_brand = ImageFont.truetype(FONT_BOLD, 14)

    # شارة AddonStation الزجاجية أعلى المشهد.
    d.rounded_rectangle((w // 2 - 104, 24, w // 2 + 104, 76), radius=22,
                        fill=(7, 11, 18, 190), outline=(245, 248, 252, 46), width=1)
    icon = Image.open(APP_ICON).convert("RGBA").resize((40, 40), Image.LANCZOS)
    img.paste(icon, (w // 2 - 96, 30), icon)
    brand = string_of(locale, "brand_wordmark").upper()
    d.text((w // 2 - 42, 50), brand, font=f_brand, fill="#F5F8FC", anchor="lm")

    # الكلمات بالتتابع: ضوء، ماسة، لافا.
    y = int(h * 0.16)
    colours = ("#F5F8FC", "#36D7E0", "#FFB12B")
    for key, colour in zip(("splash_dream_it", "splash_build_it", "splash_create_it"), colours):
        text = string_of(locale, key)
        if locale == "values-ar":
            text = rtl(text)
        d.text((w // 2 + 2, y + 2), text, font=f_line, fill=(0, 0, 0, 235), anchor="mm")
        d.text((w // 2, y), text, font=f_line, fill=colour, anchor="mm")
        y += 40
    d.rounded_rectangle((w // 2 - 65, y + 3, w // 2 - 3, y + 6), radius=2, fill="#36D7E0")
    d.rounded_rectangle((w // 2 + 3, y + 3, w // 2 + 65, y + 6), radius=2, fill="#FFB12B")

    # وصف المنصة وشريط التحميل الملوّن قرب الحافة السفلية.
    tagline = string_of(locale, "home_guest_card_title_plain")
    if locale == "values-ar":
        tagline = rtl(tagline)
    d.text((w // 2, h - 80), tagline, font=ImageFont.truetype(FONT_BOLD, 11),
           fill="#C7D1DC", anchor="mm")
    x = w // 2 - 118
    for i in range(10):
        colour = "#F5F8FC" if i < 3 else "#36D7E0" if i < 7 else "#FFB12B"
        d.rounded_rectangle((x + i * 24, h - 55, x + i * 24 + 20, h - 47), radius=2, fill=colour)
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
    d.text((W - 22, 16), rtl("شاشة البداية الجديدة — مشهد الكهف الطولي مع حركة DREAM / BUILD / CREATE"),
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
        "الخلفية: drawable-nodpi/splash_cave_chase.jpg (%d×%d · %d KB) — الصورة الطولية الجديدة التي أرسلها المالك."
        % (bw, bh, round(bkb)),
        "الألوان: ضوء أبيض · ماسة سماوية · لافا ذهبية، مع تقريب هادئ وتعتيم علوي/سفلي يحافظ على وضوح المشهد.",
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
