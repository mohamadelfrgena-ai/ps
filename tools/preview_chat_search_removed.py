#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة إزالة أداة البحث من الشات: الترويسة قبل ← بعد.

النصوص من موارد التطبيق الفعلية (values-ar). المصدر: ChatScreen.kt (ChatHeader).
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
STRINGS = os.path.join(ROOT, "android", "app", "src", "main", "res", "values-ar", "strings.xml")
ICONS = os.path.join(ROOT, "android", "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "chat_search_removed_preview.png")

BG, CARD, TEXT, MUTED, GREEN, RED, DIM, BORDER, BLUE = (
    "#080C12", "#151E2A", "#E8EEF5", "#8EA0B3", "#3DDC84", "#FF6B6B", "#5A6B7D", "#263240", "#6FB8FF")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def s(key):
    txt = io.open(STRINGS, encoding="utf-8").read()
    m = re.search(r'<string name="%s">([^<]*)</string>' % re.escape(key), txt)
    return m.group(1).replace("\\'", "'") if m else key


def icon(name, size):
    path = os.path.join(ICONS, name + ".png")
    if not os.path.exists(path):
        return None
    return Image.open(path).convert("RGBA").resize((size, size), Image.LANCZOS)


def mini_icon(key, size=15, color="#8EA0B3"):
    """أيقونات صغيرة مرسومة (بحث · أعضاء · مثبتة) — لأن خط الخادم لا يرسم الإيموجي."""
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    m = size // 2
    if key == "search":
        d.ellipse((2, 2, size - 5, size - 5), outline=color, width=2)
        d.line((size - 6, size - 6, size - 2, size - 2), fill=color, width=2)
    elif key == "members":
        d.ellipse((m - 4, 1, m + 2, 7), outline=color, width=2)
        d.arc((m - 7, 7, m + 5, size + 3), start=180, end=360, fill=color, width=2)
        d.ellipse((1, 4, 5, 9), outline=color, width=1)
        d.arc((-3, 9, 5, size + 2), start=180, end=360, fill=color, width=1)
    elif key == "pin":
        d.line((m, 2, m, size - 2), fill=color, width=2)
        d.polygon([(m - 5, 3), (m + 5, 3), (m, 9)], outline=color)
    elif key == "back":
        d.line((size - 3, m, 3, m), fill=color, width=2)
        d.line((3, m, 8, m - 4), fill=color, width=2)
        d.line((3, m, 8, m + 4), fill=color, width=2)
    return im


def round_btn(draw_img, d, x, y, size=30, bg="#1A2330", key=None, tint="#8EA0B3"):
    d.rounded_rectangle((x, y, x + size, y + size), radius=10, fill=bg, outline=BORDER)
    if key:
        ic = mini_icon(key, 15, tint)
        draw_img.paste(ic, (x + (size - ic.width) // 2, y + (size - ic.height) // 2), ic)


def header(img, d, f_name, f_desc, x, y, w, title, desc, icons, highlight=None):
    h = 76
    d.rounded_rectangle((x, y, x + w, y + h), radius=16, fill=CARD, outline=BORDER)
    # زر الرجوع في أقصى اليمين (اتجاه عربي)
    d.rounded_rectangle((x + w - 40, y + 22, x + w - 10, y + 52), radius=10, fill="#1A2330", outline=BORDER)
    back = mini_icon("back", 15, "#E8EEF5")
    img.paste(back, (x + w - 25 - back.width // 2, y + 37 - back.height // 2), back)
    # أيقونة الغرفة + الاسم + الوصف
    ic = icon("ic_room_lang_en", 20)
    name_x = x + w - 92
    if ic is not None:
        img.paste(ic, (name_x - ic.width + 2, y + 17), ic)
    d.text((name_x - 26, y + 14), title, font=f_name, fill=TEXT, anchor="ra")
    d.text((name_x - 26, y + 40), desc, font=f_desc, fill=MUTED, anchor="ra")
    # الأزرار في أقصى اليسار
    bx = x + 12
    for key in icons:
        color = RED if key == highlight else MUTED
        red_bg = "#2A1A1E" if key == highlight else "#1A2330"
        round_btn(img, d, bx, y + 22, bg=red_bg, key=key, tint=color)
        if key == highlight:
            d.rounded_rectangle((bx - 3, y + 19, bx + 33, y + 55), radius=12, outline=RED)
        bx += 38
    return h


def main() -> int:
    W, H = 1180, 560
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    f_big = ImageFont.truetype(FONT_BOLD, 25)
    f_h = ImageFont.truetype(FONT_BOLD, 19)
    f_name = ImageFont.truetype(FONT_BOLD, 19)
    f_desc = ImageFont.truetype(FONT_REG, 13)
    f_small = ImageFont.truetype(FONT_REG, 16)

    d.rectangle((0, 0, W, 66), fill="#111823")
    d.text((W - 22, 19), rtl("شاشة الشات — إزالة أداة البحث"), font=f_big, fill=TEXT, anchor="ra")

    d.text((W - 24, 88), rtl("قبل  (كانت أيقونة بحث 🔍 في الترويسة + حقل «بحث في الغرفة»)"),
           font=f_h, fill=RED, anchor="ra")
    header(img, d, f_name, f_desc, 20, 118, W - 40,
           "English", "Talk in English here",
           ["search", "members", "pin"], highlight="search")
    d.rounded_rectangle((W // 2 - 150, 192, W // 2 + 150, 222), radius=10, fill="#101720", outline=BORDER)
    d.text((W // 2 + 4, 207), rtl("بحث في الغرفة"), font=f_small, fill=DIM, anchor="mm")
    mg = mini_icon("search", 14, "#5A6B7D")
    img.paste(mg, (W // 2 - 140, 200), mg)

    d.text((W - 24, 258), rtl("بعد  (الترويسة: رجوع · الغرفة · الأعضاء 👥 · المثبّتة 📌)"),
           font=f_h, fill=GREEN, anchor="ra")
    header(img, d, f_name, f_desc, 20, 288, W - 40,
           "English", "Talk in English here",
           ["members", "pin"])

    y = 396
    d.rounded_rectangle((20, y, W - 20, y + 130), radius=16, fill=CARD, outline=BORDER)
    d.text((W - 34, y + 14), rtl("ما أُزيل بالضبط"), font=f_h, fill=BLUE, anchor="ra")
    for i, line in enumerate([
        "١) أيقونة البحث 🔍 من ترويسة الشات (ChatHeader · HeaderIcon).",
        "٢) حقل «بحث في الغرفة» (OutlinedTextField · chat_search_in_room) — وحُذف نصه من اللغات الثماني.",
        "٣) تصفية قائمة الرسائل بالبحث — القائمة تُعرض الآن كما هي كاملة.",
        "وبحث الإيموجي داخل منتقي الرموز باقٍ كما هو (ليس أداة بحث في الشات).",
    ]):
        d.text((W - 34, y + 44 + i * 22), rtl(line), font=f_small, fill=MUTED, anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
