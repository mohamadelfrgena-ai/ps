#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يرسم معاينة للنافذتين الجديدتين (سياسة الخصوصية · شروط وأحكام الاستخدام).

يقرأ النصوص **من ملف الموارد نفسه** (values-ar/strings.xml) فلا يختلف العرض
عن ما سيظهر في التطبيق، ويكتب النتيجة في policy_dialogs_preview.png.
"""
import io
import os
import re
import textwrap

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRINGS = os.path.join(ROOT, "android", "app", "src", "main", "res", "values-ar", "strings.xml")
OUT = os.path.join(ROOT, "policy_dialogs_preview.png")

SURFACE, BG, TEXT, MUTED, GREEN, BORDER = "#131A24", "#0B0F16", "#E8EEF5", "#8EA0B3", "#3DDC84", "#263240"
FONT_PATH = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def resource(name: str) -> str:
    xml = io.open(STRINGS, encoding="utf-8").read()
    m = re.search(r'<string name="%s">(.*?)</string>' % re.escape(name), xml, re.S)
    if not m:
        raise SystemExit(f"✗ المفتاح غير موجود: {name}")
    text = m.group(1)
    text = text.replace("\\'", "'").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
    return text.replace("\\n", "\n")


def rtl(text: str) -> str:
    return get_display(arabic_reshaper.reshape(text))


def draw_dialog(draw: ImageDraw.ImageDraw, box, title: str, body: str, font, font_small, font_bold):
    x0, y0, x1, y1 = box
    draw.rounded_rectangle(box, radius=22, fill=SURFACE, outline=BORDER, width=1)
    draw.text(((x0 + x1) / 2, y0 + 26), rtl(title), font=font_bold, fill=TEXT, anchor="ma",
              direction=None, features=None)
    y = y0 + 74
    max_width = x1 - x0 - 56
    for paragraph in body.split("\n"):
        if not paragraph.strip():
            y += 12
            continue
        words, line = paragraph.split(), ""
        for word in words:
            trial = (line + " " + word).strip()
            if draw.textlength(rtl(trial), font=font_small) > max_width:
                draw.text((x1 - 28, y), rtl(line), font=font_small, fill=MUTED, anchor="ra")
                y += 26
                line = word
            else:
                line = trial
        if line:
            draw.text((x1 - 28, y), rtl(line), font=font_small, fill=MUTED, anchor="ra")
            y += 26
    # زر «حسناً»
    draw.text((x1 - 36, y1 - 44), rtl("حسناً"), font=font_bold, fill=GREEN, anchor="ra")


def main() -> int:
    privacy_title, privacy_body = resource("set_privacy"), resource("set_privacy_text")
    terms_title, terms_body = resource("set_terms"), resource("set_terms_text")

    font_bold = ImageFont.truetype(FONT_PATH, 30)
    font_small = ImageFont.truetype(FONT_PATH, 19)

    width, height = 1180, 900
    img = Image.new("RGB", (width, height), BG)
    draw = ImageDraw.Draw(img)
    draw.text((width / 2, 26), rtl("AddonStation — سياسة الخصوصية وشروط الاستخدام (النسخة النهائية)"),
              font=font_bold, fill=GREEN, anchor="ma")

    gap = 40
    card_w = (width - gap * 3) // 2
    top, bottom = 96, height - 48
    draw_dialog(draw, (gap, top, gap + card_w, bottom), privacy_title, privacy_body,
                font_bold, font_small, font_bold)
    x0 = gap * 2 + card_w
    draw_dialog(draw, (x0, top, x0 + card_w, bottom), terms_title, terms_body,
                font_bold, font_small, font_bold)

    img.save(OUT)
    print(f"✓ {OUT}  ({width}×{height})")
    print(f"   سياسة الخصوصية: {len(privacy_body)} حرفاً · شروط وأحكام الاستخدام: {len(terms_body)} حرفاً")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
