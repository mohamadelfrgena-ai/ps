#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة قسم «حول التطبيق» في الإعدادات بعد التعديل.

الترتيب الجديد: شروط وأحكام الاستخدام → سياسة الخصوصية → إصدار التطبيق (بلا سهم «›»).
يقرأ الأسماء من ملف الموارد العربي نفسه، ويرسم أيقونات الإعدادات الحقيقية من drawable-nodpi.
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
STRINGS = os.path.join(RES, "values-ar", "strings.xml")
DRAWABLES = os.path.join(RES, "drawable-nodpi")
OUT = os.path.join(ROOT, "settings_about_preview.png")

BG, CARD, TEXT, MUTED, GREEN, DIM, BORDER = "#0B0F16", "#151E2A", "#E8EEF5", "#8EA0B3", "#3DDC84", "#5A6B7D", "#263240"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
VERSION = "1.2.0"


def resource(name: str) -> str:
    xml = io.open(STRINGS, encoding="utf-8").read()
    m = re.search(r'<string name="%s">(.*?)</string>' % re.escape(name), xml, re.S)
    if not m:
        raise SystemExit(f"✗ المفتاح غير موجود: {name}")
    text = m.group(1).replace("\\'", "'").replace("&amp;", "&")
    return text.replace("\\n", "\n")


def rtl(s: str) -> str:
    return get_display(arabic_reshaper.reshape(s))


def icon(name: str, size: int):
    path = os.path.join(DRAWABLES, f"{name}.png")
    if not os.path.exists(path):
        return None
    img = Image.open(path).convert("RGBA").resize((size, size), Image.LANCZOS)
    return img


def main() -> int:
    title_terms = resource("set_terms")
    title_privacy = resource("set_privacy")
    title_version = resource("set_version")

    f_head = ImageFont.truetype(FONT, 24)
    f_body = ImageFont.truetype(FONT, 22)
    f_small = ImageFont.truetype(FONT, 18)
    f_chev = ImageFont.truetype(FONT, 26)

    W, H = 760, 470
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)

    # خلفية التطبيق العلوية + عنوان الشاشة
    d.rectangle((0, 0, W, 74), fill="#111823")
    d.text((W - 24, 24), rtl("الإعدادات"), font=f_head, fill=TEXT, anchor="ra")

    # عنوان القسم
    d.text((W - 24, 104), rtl(resource("set_about")), font=f_small, fill=GREEN, anchor="ra")

    # بطاقة القسم
    x0, y0, x1, y1 = 20, 132, W - 20, 132 + 3 * 84 + 16
    d.rounded_rectangle((x0, y0, x1, y1), radius=18, fill=CARD, outline=BORDER, width=1)

    rows = [
        ("ic_set_terms", title_terms, None, True),
        ("ic_set_privacy", title_privacy, None, True),
        ("ic_set_version", title_version, f"AddonStation Mobile v{VERSION}", False),
    ]
    y = y0 + 8
    for index, (icon_name, title, subtitle, chevron) in enumerate(rows):
        if index:
            d.line((x0 + 60, y, x1 - 14, y), fill="#1E2836", width=1)
        ic = icon(icon_name, 34)
        if ic is not None:
            img.paste(ic, (x1 - 12 - 34, y + 24), ic)
        d.text((x1 - 58, y + (34 if subtitle else 32)), rtl(title), font=f_body, fill=TEXT, anchor="ra")
        if subtitle:
            d.text((x1 - 58, y + 58), subtitle, font=f_small, fill=MUTED, anchor="ra")
        if chevron:
            d.text((x0 + 16, y + 26), "›", font=f_chev, fill=DIM)
        y += 84

    img.save(OUT)
    print(f"✓ {OUT}")
    print("   الترتيب: شروط وأحكام الاستخدام → سياسة الخصوصية → إصدار التطبيق (بلا ›)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
