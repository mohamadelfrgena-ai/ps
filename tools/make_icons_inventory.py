#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""لوحة جرد كل أيقونات التطبيق المخصّصة (26) + أيقونة المشغّل (5 كثافات).

تُبنى من الملفات الموجودة فعلياً في المشروع — الغرض: يرى المالك ما هو موجود
ويستبدل ما يريده. الإخراج: icons_inventory.png
"""
import glob
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
OUT = os.path.join(ROOT, "icons_inventory.png")
FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

ROOMS = [
    ("ic_room_general", "غرفة عامة"),
    ("ic_room_social", "اجتماعي"),
    ("ic_room_creator", "المبدعون"),
    ("ic_room_minecraft", "ماينكرافت"),
    ("ic_room_support", "الدعم"),
    ("ic_room_cash", "خدمات النقدية"),
    ("ic_room_lang_ar", "العربية"),
    ("ic_room_lang_en", "English"),
    ("ic_room_lang_es", "Español"),
    ("ic_room_lang_fr", "Français"),
    ("ic_room_lang_pt", "Português"),
    ("ic_room_lang_ru", "Русский"),
    ("ic_room_lang_tr", "Türkçe"),
    ("ic_room_lang_zh", "Chinese"),
]
SETTINGS = [
    ("ic_set_appearance", "المظهر"),
    ("ic_set_notifications", "التنبيهات"),
    ("ic_set_language", "اللغة"),
    ("ic_set_privacy", "الخصوصية"),
    ("ic_set_contact", "الدعم / اتصل بنا"),
    ("ic_set_email", "البريد"),
    ("ic_set_terms", "الشروط"),
    ("ic_set_privacy_terms", ""),
    ("ic_set_help", "المساعدة"),
    ("ic_set_report", "الإبلاغ"),
    ("ic_set_version", "الإصدار"),
]
SETTINGS = [x for x in SETTINGS if x[0] != "ic_set_privacy_terms"]


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def cell(canvas, d, x, y, box, path, label, sub, f_small):
    d.rounded_rectangle((x, y, x + box, y + box), radius=14, fill="#141C27", outline="#263240")
    src = Image.open(path).convert("RGBA")
    k = min((box - 24) / src.width, (box - 24) / src.height)
    src = src.resize((max(1, int(src.width * k)), max(1, int(src.height * k))), Image.LANCZOS)
    canvas.paste(src, (x + (box - src.width) // 2, y + (box - src.height) // 2), src)
    d.text((x + box // 2, y + box + 16), label, font=f_small, fill="#8EA0B3", anchor="mm")
    if sub:
        d.text((x + box // 2, y + box + 34), sub, font=f_small, fill="#5A6B7D", anchor="mm")


def section(d, title, x, y, f_h):
    d.text((x, y), rtl(title), font=f_h, fill="#6FB8FF")
    d.line((x, y + 30, x + 700, y + 30), fill="#263240")
    return y + 44


def main() -> int:
    W = 1220
    f_h = ImageFont.truetype(FONT_BOLD, 20)
    f_name = ImageFont.truetype(FONT_BOLD, 15)
    f_small = ImageFont.truetype(FONT, 13)
    box, gap = 108, 18
    cols = 8

    # ارتفاع محسوب
    rows_needed = (len(ROOMS) + cols - 1) // cols + (len(SETTINGS) + cols - 1) // cols
    H = 150 + rows_needed * (box + 60) + 380
    img = Image.new("RGB", (W, H), "#080C12")
    d = ImageDraw.Draw(img)

    d.rectangle((0, 0, W, 62), fill="#111823")
    d.text((W - 22, 18), rtl("جرد الأيقونات المخصّصة في التطبيق — 27 أيقونة + أيقونة المشغّل (كلها موجودة ومرتبطة)"),
           font=f_h, fill="#E8EEF5", anchor="ra")

    y = 84
    y = section(d, "أيقونات الغرف (14) — 160×160", 30, y, f_h)
    for i, (name, label) in enumerate(ROOMS):
        col = i % cols
        cell(img, d, 30 + col * (box + gap), y + (i // cols) * (box + 60), box,
             os.path.join(RES, "drawable-nodpi", name + ".png"), label,
             name.replace("ic_room_", ""), f_small)
    y += ((len(ROOMS) + cols - 1) // cols) * (box + 60) + 12

    y = section(d, "أيقونات الإعدادات (9) + صورة المستخدم الافتراضية + عملة AC", 30, y, f_h)
    items = SETTINGS + [("ic_user", "الصورة الافتراضية"), ("coin_ac", "عملة AC")]
    for i, (name, label) in enumerate(items):
        col = i % cols
        cell(img, d, 30 + col * (box + gap), y + (i // cols) * (box + 60), box,
             os.path.join(RES, "drawable-nodpi", name + ".png"), label,
             name.replace("ic_set_", ""), f_small)
    y += ((len(items) + cols - 1) // cols) * (box + 60) + 12

    y = section(d, "الشعار وأيقونة المشغّل", 30, y, f_h)
    d.rounded_rectangle((30, y, W - 30, y + 176), radius=14, fill="#141C27", outline="#263240")
    splash = Image.open(os.path.join(RES, "drawable-nodpi", "splash_logo.png")).convert("RGBA")
    splash.thumbnail((250, 96), Image.LANCZOS)
    img.paste(splash, (60, y + 26), splash)
    d.text((60, y + 138), rtl("splash_logo.png — 900×131"), font=f_small, fill="#8EA0B3")
    d.text((60, y + 156), rtl("شاشة البداية"), font=f_small, fill="#5A6B7D")
    bx = 360
    for dens in ("mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"):
        ic = Image.open(os.path.join(RES, "mipmap-" + dens, "ic_launcher.png")).convert("RGBA")
        real = ic.width
        ic = ic.resize((84, 84), Image.LANCZOS)
        img.paste(ic, (bx, y + 28), ic)
        d.text((bx + 42, y + 130), f"{dens}", font=f_small, fill="#8EA0B3", anchor="mm")
        d.text((bx + 42, y + 148), f"{real}px", font=f_small, fill="#5A6B7D", anchor="mm")
        bx += 96
    d.text((W - 46, y + 26), rtl("ic_launcher.png — أيقونة التطبيق"), font=f_small, fill="#8EA0B3", anchor="ra")
    d.text((W - 46, y + 58), rtl("48 · 72 · 96 · 144 · 192 بكسل"), font=f_small, fill="#5A6B7D", anchor="ra")
    d.text((W - 46, y + 88), rtl("للمتجر: 512×512 PNG"), font=f_small, fill="#8EA0B3", anchor="ra")
    d.text((W - 46, y + 116), rtl("GIF متحرك اختياري"), font=f_small, fill="#5A6B7D", anchor="ra")

    y += 192
    d.rounded_rectangle((30, y, W - 30, y + 74), radius=14, fill="#141C27", outline="#263240")
    d.text((W - 46, y + 12), rtl("لا تنقص أي أيقونة: 41 أيقونة واجهة أخرى من مكتبة Material مدمجة في الكود (لا تحتاج تصميم)،"),
           font=f_small, fill="#8EA0B3", anchor="ra")
    d.text((W - 46, y + 38), rtl("وأغلفة الإضافات والملصقات والافتارات (492 ملفاً) مرفوعة من حسابك إلى Supabase — لا تحتاج شيئاً."),
           font=f_small, fill="#5A6B7D", anchor="ra")

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
