#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة: (1) كلمات شاشة الترحيب عريضة في كل اللغات (2) الأشرطة داخل حدود شاشة الهاتف.

النصوص تقرأ من ملفات الموارد الحقيقية (values · values-ar · values-ru)، والخط العريض
في المعاينة يحاكي `displayStyle` (خط البكسل للاتيني · خط النظام العريض لغيره).
"""
import io
import os
import re

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
DRAWABLES = os.path.join(RES, "drawable-nodpi")
OUT = os.path.join(ROOT, "splash_and_insets_preview.png")

BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"
BG, PANEL, TEXT, MUTED, GREEN, DIM, BORDER = (
    "#05080D", "#0E141D", "#E8EEF5", "#8EA0B3", "#3DDC84", "#5A6B7D", "#263240")
SPLASH_SCENE = os.path.join(DRAWABLES, "splash_cave_chase.jpg")


def rtl(s: str) -> str:
    return get_display(arabic_reshaper.reshape(s))


def string_of(locale: str, name: str) -> str:
    path = os.path.join(RES, locale, "strings.xml")
    xml = io.open(path, encoding="utf-8").read()
    m = re.search(r'<string name="%s">(.*?)</string>' % re.escape(name), xml, re.S)
    return m.group(1).replace("\\'", "'") if m else "?"


def draw_splash(img, draw, x0, y0, w, h, locale, label):
    scene = Image.open(SPLASH_SCENE).convert("RGB")
    scale = max(w / scene.width, h / scene.height)
    sw, sh = round(scene.width * scale), round(scene.height * scale)
    scene = scene.resize((sw, sh), Image.LANCZOS)
    scene = scene.crop(((sw - w) // 2, (sh - h) // 2, (sw - w) // 2 + w, (sh - h) // 2 + h))
    scene = Image.blend(scene, Image.new("RGB", (w, h), "#070B12"), 0.22)
    img.paste(scene, (x0, y0))
    draw.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=24, outline=BORDER, width=2)

    draw.rounded_rectangle((x0 + w / 2 - 104, y0 + 20, x0 + w / 2 + 104, y0 + 70),
                           radius=22, fill="#111823", outline="#45505E")
    draw.text((x0 + w / 2, y0 + 45), "ADDONSTATION", font=ImageFont.truetype(BOLD, 18),
              fill=TEXT, anchor="mm")
    y = y0 + 112
    colours = ("#F5F8FC", "#36D7E0", "#FFB12B")
    for key, colour in zip(("splash_dream_it", "splash_build_it", "splash_create_it"), colours):
        text = string_of(locale, key)
        font = ImageFont.truetype(BOLD, 30)
        shown = rtl(text) if locale == "values-ar" else text
        draw.text((x0 + w / 2 + 2, y + 2), shown, font=font, fill="#070B12", anchor="mm")
        draw.text((x0 + w / 2, y), shown, font=font, fill=colour, anchor="mm")
        y += 42
    draw.text((x0 + w / 2, y0 + h - 24), f"{label} · DREAM / BUILD / CREATE",
              font=ImageFont.truetype(REG, 13), fill=MUTED, anchor="mm")


def draw_phone(img, draw, x0, y0, w, h, title):
    draw.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=28, fill=BG, outline=BORDER, width=2)

    # شريط حالة الهاتف (البطارية والواي فاي) — النظام يرسمه
    status_h = 34
    draw.rectangle((x0 + 2, y0 + 2, x0 + w - 2, y0 + status_h), fill="#0A0E14")
    f_icon = ImageFont.truetype(REG, 15)
    draw.text((x0 + 18, y0 + 8), "12:45", font=f_icon, fill=TEXT)
    draw.text((x0 + w - 74, y0 + 8), "▮▮▮  ⟲  ▰", font=f_icon, fill=TEXT)

    # الشريط العلوي للتطبيق: يبدأ **أسفل** شريط الحالة (windowInsetsPadding = statusBars)
    bar_y = y0 + status_h
    bar_h = 56
    draw.rectangle((x0 + 2, bar_y, x0 + w - 2, bar_y + bar_h), fill="#111823")
    draw.text((x0 + w - 18, bar_y + 18), rtl("الإعدادات"), font=ImageFont.truetype(BOLD, 19),
              fill=TEXT, anchor="ra")
    ic = Image.open(os.path.join(DRAWABLES, "coin_ac.png")).convert("RGBA").resize((24, 24))
    draw.rounded_rectangle((x0 + 16, bar_y + 14, x0 + 16 + 68, bar_y + 42), radius=14,
                           fill="#212D3D", outline=BORDER)
    img.paste(ic, (x0 + 22, bar_y + 17), ic)
    draw.text((x0 + 50, bar_y + 21), "1,250 AC", font=ImageFont.truetype(REG, 14), fill=TEXT)

    # المحتوى
    draw.text((x0 + w - 18, bar_y + bar_h + 22), rtl("حول التطبيق"), font=ImageFont.truetype(BOLD, 16),
              fill=GREEN, anchor="ra")
    draw.rounded_rectangle((x0 + 16, bar_y + bar_h + 46, x0 + w - 16, bar_y + bar_h + 210), radius=16,
                           fill=PANEL, outline=BORDER)
    for i, key in enumerate(("set_terms", "set_privacy", "set_version")):
        draw.text((x0 + w - 30, bar_y + bar_h + 66 + i * 52), rtl(string_of("values-ar", key)),
                  font=ImageFont.truetype(REG, 16), fill=TEXT, anchor="ra")

    # الشريط السفلي: نهايته عند بداية شريط تنقّل النظام
    nav_h = 30
    nav_top = y0 + h - nav_h
    draw.rectangle((x0 + 2, nav_top, x0 + w - 2, y0 + h - 2), fill="#0A0E14")
    draw.line((x0 + w / 2 - 34, nav_top + 15, x0 + w / 2 + 34, nav_top + 15), fill=DIM, width=3)
    draw.text((x0 + w / 2, nav_top + 22), rtl("شريط التنقّل (System Navigation Bar)"),
              font=ImageFont.truetype(REG, 11), fill=DIM, anchor="ma")
    bar_top = nav_top - 62
    draw.rectangle((x0 + 2, bar_top, x0 + w - 2, nav_top), fill="#111823")
    tabs = ["المتجر", "المجتمع", "الرئيسية", "الإعدادات", "الملف"]
    for i, t in enumerate(tabs):
        cx = x0 + 2 + (w - 4) * (i + 0.5) / len(tabs)
        draw.text((cx, bar_top + 34), rtl(t), font=ImageFont.truetype(REG, 13), fill=MUTED, anchor="mm")
    draw.ellipse((x0 + w / 2 - 26, bar_top - 16, x0 + w / 2 + 26, bar_top + 36), fill=GREEN)

    draw.text((x0 + w / 2, y0 + h + 18), title, font=ImageFont.truetype(REG, 16), fill=MUTED, anchor="ma")


def draw_chat(draw, x0, y0, w, h, title):
    draw.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=28, fill=BG, outline=BORDER, width=2)
    status_h, nav_h = 34, 30
    draw.rectangle((x0 + 2, y0 + 2, x0 + w - 2, y0 + status_h), fill="#0A0E14")
    draw.text((x0 + 18, y0 + 8), "12:45", font=ImageFont.truetype(REG, 15), fill=TEXT)
    draw.text((x0 + w - 74, y0 + 8), "▮▮▮  ⟲  ▰", font=ImageFont.truetype(REG, 15), fill=TEXT)

    # ترويسة الشات: صارت تحجز مساحة شريط الحالة
    hy = y0 + status_h
    draw.rectangle((x0 + 2, hy, x0 + w - 2, hy + 48), fill="#111823")
    draw.text((x0 + w - 20, hy + 14), rtl("غرفة المجتمع"), font=ImageFont.truetype(BOLD, 16),
              fill=TEXT, anchor="ra")

    # مربع الإرسال: فوق شريط تنقّل النظام (navigationBars ∪ ime)
    nav_top = y0 + h - nav_h
    draw.rectangle((x0 + 2, nav_top, x0 + w - 2, y0 + h - 2), fill="#0A0E14")
    draw.line((x0 + w / 2 - 34, nav_top + 15, x0 + w / 2 + 34, nav_top + 15), fill=DIM, width=3)
    input_h = 54
    input_top = nav_top - input_h
    draw.rectangle((x0 + 2, input_top, x0 + w - 2, nav_top), fill="#111823")
    draw.rounded_rectangle((x0 + 14, input_top + 9, x0 + w - 62, input_top + 45), radius=18,
                           fill="#1B2634", outline=BORDER)
    draw.text((x0 + w - 74, input_top + 18), rtl("اكتب رسالة…"), font=ImageFont.truetype(REG, 14),
              fill=DIM, anchor="ra")
    draw.ellipse((x0 + w - 52, input_top + 9, x0 + w - 16, input_top + 45), fill=GREEN)
    draw.text((x0 + w - 34, input_top + 26), rtl("إرسال"), font=ImageFont.truetype(REG, 11),
              fill="#04210F", anchor="mm")

    draw.text((x0 + w / 2, y0 + h + 18), title, font=ImageFont.truetype(REG, 16), fill=MUTED, anchor="ma")


def main() -> int:
    W, H = 1500, 1080
    img = Image.new("RGB", (W, H), "#03060A")
    d = ImageDraw.Draw(img)
    d.text((W / 2, 36), rtl("AddonStation — كلمات الترحيب عريضة في كل لغة · والأشرطة داخل حدود الشاشة"),
           font=ImageFont.truetype(BOLD, 26), fill=GREEN, anchor="ma")

    # الصف الأول: شاشة الترحيب بثلاث لغات
    xs, ys, cw, ch = 60, 82, 440, 340
    draw_splash(img, d, xs, ys, cw, ch, "values", "English")
    draw_splash(img, d, xs + cw + 40, ys, cw, ch, "values-ar", "العربية")
    draw_splash(img, d, xs + 2 * (cw + 40), ys, cw, ch, "values-ru", "Русский")

    # الصف الثاني: هاتف الرئيسية + هاتف الشات
    pw, ph = 360, 560
    draw_phone(img, d, 120, 500, pw, ph, rtl("التبويبات: العلوي أسفل شريط الحالة · السفلي فوق شريط التنقّل"))
    draw_chat(d, 700, 500, pw, ph, rtl("الشات: مربع الإرسال فوق شريط تنقّل النظام"))

    img.save(OUT)
    print(f"✓ {OUT} ({W}×{H})")
    print("   أسطر الترحيب: عريضة (Bold) في الإنجليزية والعربية والروسية")
    print("   الأشرطة: topBar = statusBars + 56dp · bottomNav فوق navigationBars · inputBar فوق navigationBars")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
