#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة زر تبديل الوضع الداكن/الفاتح + نقل أيقونة المستخدم إلى حقل الاسم.

يرسم نفس شاشة الإعدادات مرّتين:
  1) بالوضع الداكن (الألوان كما هي في وثيقة التصميم).
  2) بالوضع الفاتح (اللوحة الفاتحة الجديدة) — لإثبات أن التبديل يغيّر كل
     الألوان فعلاً (الخلفية، البطاقات، النصوص، الأيقونات، شريط الحالة).
ويضع أسهماً مطابقة للقبّطة: الأحمر عند رأس الحساب (أُزيلت منه الأيقونة)،
والأزرق عند حقل تغيير الاسم (صارت أيقونة المستخدم بجواره).
المصدر: Color.kt · Theme.kt · SettingsScreen.kt · AppNav.kt
"""
import math
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "theme_toggle_preview.png")
DRAW = os.path.join(ROOT, "android/app/src/main/res/drawable-nodpi")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

# لوحتا الألوان كما في Color.kt بعد الإصلاح
DARK = {
    "bg": "#0B0F14", "surface": "#131A24", "elev": "#1A2330", "elev2": "#212D3D",
    "border": "#263240", "border2": "#334152", "text": "#E8EEF5", "muted": "#8EA0B3",
    "dim": "#5C6F82", "green": "#3DDC84", "green2": "#2FAE67", "ongreen": "#04210F",
}
LIGHT = {
    "bg": "#F2F5F9", "surface": "#FFFFFF", "elev": "#FFFFFF", "elev2": "#E7EDF4",
    "border": "#DCE4EC", "border2": "#C4D1DF", "text": "#0F1B26", "muted": "#56697C",
    "dim": "#7B8EA1", "green": "#17A75A", "green2": "#128A48", "ongreen": "#FFFFFF",
}

W, H, TOP = 380, 800, 168
RED, BLUE = "#D93838", "#1E6FD9"
img = None
d = None


def rtl(text):
    return get_display(arabic_reshaper.reshape(text))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded(box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def arrow(x1, y1, x2, y2, color, w=4):
    d.line([(x1, y1), (x2, y2)], fill=color, width=w)
    a = math.atan2(y2 - y1, x2 - x1)
    for s in (0.55, -0.55):
        d.line([(x2, y2), (x2 - 15 * math.cos(a - s), y2 - 15 * math.sin(a - s))], fill=color, width=w)


def note(x, y, color, text, anchor="mm"):
    d.text((x, y), rtl(text), font=font(19, True), fill=color, anchor=anchor)


def screen(x0, pal, mode, dark=True):
    """هاتف بهيئة الوضع المطلوب (نفس المحتوى في اللقطتين)."""
    w = W
    x1 = x0 + w
    rounded((x0, TOP, x1, TOP + H), 30, fill=pal["bg"], outline=pal["border2"], width=2)

    # شريط الحالة: أيقوناته تتبع الوضع (داكنة فوق خلفية فاتحة)
    d.rectangle((x0 + 3, TOP + 3, x1 - 3, TOP + 46), fill=pal["bg"])
    stat = pal["text"] if dark else "#10171F"
    d.text((x0 + 20, TOP + 24), "9:41", font=font(13), fill=stat, anchor="lm")
    d.text((x1 - 20, TOP + 24), "▮▮", font=font(12), fill=stat, anchor="rm")

    y = TOP + 46
    d.text((x0 + 20, y + 22), "‹", font=font(24, True), fill=pal["text"], anchor="mm")
    d.text((x0 + 44, y + 22), rtl("الإعدادات"), font=font(19, True), fill=pal["text"], anchor="lm")
    d.text((x1 - 22, y + 22), rtl("1,240 رصيد"), font=font(15, True), fill=pal["green"], anchor="rm")
    d.text((x0 + w // 2, TOP + H - 30), rtl(mode), font=font(19, True), fill=pal["muted"], anchor="mm")
    y += 58

    # ① رأس الحساب — بلا أيقونة المستخدم
    head_y = y
    rounded((x0 + 14, y, x1 - 14, y + 74), 16, fill=pal["surface"], outline=pal["border"])
    d.ellipse((x1 - 66, y + 15, x1 - 18, y + 63), fill=pal["elev2"], outline=pal["green"], width=2)
    d.text((x1 - 42, y + 39), "MF", font=font(14, True), fill=pal["muted"], anchor="mm")
    d.text((x1 - 80, y + 28), rtl("Mrforest"), font=font(17, True), fill=pal["text"], anchor="rm")
    d.text((x1 - 80, y + 50), "email@gmail.com", font=font(12), fill=pal["muted"], anchor="rm")
    rounded((x0 + 26, y + 30, x0 + 86, y + 52), 8, fill=pal["green"])
    d.text((x0 + 56, y + 41), rtl("أونلاين"), font=font(11), fill=pal["ongreen"], anchor="mm")
    y += 96

    # ② بطاقة الحساب: حقل اسم المستخدم + أيقونة المستخدم
    d.text((x0 + 20, y), rtl("الحساب"), font=font(14, True), fill=pal["muted"], anchor="lt")
    y += 26
    rounded((x0 + 14, y, x1 - 14, y + 150), 16, fill=pal["surface"], outline=pal["border"])
    fy = y + 46
    d.rounded_rectangle((x0 + 28, fy - 24, x1 - 28, fy + 26), radius=10, outline=pal["border2"], width=2)
    icon = Image.open(os.path.join(DRAW, "ic_user.png")).convert("RGBA").resize((24, 24), Image.LANCZOS)
    img.paste(icon, (int(x1 - 60), int(fy - 11)), icon)
    d.text((x1 - 78, fy + 2), rtl("اسم المستخدم"), font=font(15), fill=pal["text"], anchor="rm")
    d.text((x0 + 38, fy - 14), rtl("الاسم"), font=font(11), fill=pal["muted"], anchor="lm")
    rounded((x0 + 28, y + 88, x1 - 28, y + 136), 13, fill=pal["green"])
    d.text(((x0 + x1) // 2, y + 112), rtl("حفظ الاسم"), font=font(15, True), fill=pal["ongreen"], anchor="mm")
    field_y = fy
    y += 172

    # ③ زر التبديل في صف «مظهر التطبيق»
    d.text((x0 + 20, y), rtl("التفضيلات"), font=font(14, True), fill=pal["muted"], anchor="lt")
    y += 26
    switch_y = y
    for label, sub, on, icon_name in (
        (("مظهر التطبيق", "فاتح", True, "ic_set_appearance")),
        (("اللغة", "العربية", False, "ic_set_language")),
    ):
        rounded((x0 + 14, y, x1 - 14, y + 58), 14, fill=pal["surface"], outline=pal["border"])
        im = Image.open(os.path.join(DRAW, icon_name + ".png")).convert("RGBA").resize((30, 30), Image.LANCZOS)
        img.paste(im, (int(x0 + 24), int(y + 14)), im)
        d.text((x1 - 68, y + 21), rtl(label), font=font(15, True), fill=pal["text"], anchor="rm")
        d.text((x1 - 68, y + 42), rtl(sub), font=font(12), fill=pal["muted"], anchor="rm")
        tw, th, tx, ty = 48, 27, x0 + 26, y + 16
        rounded((tx, ty, tx + tw, ty + th), th // 2,
                fill=pal["green"] if on else pal["elev2"],
                outline=None if on else pal["border2"], width=2)
        d.ellipse((tx + (tw - 21 if on else 3), ty + 3, tx + (tw - 3 if on else 21), ty + 24), fill="#FFFFFF")
        y += 68
    return {"head": (x0 + 44, head_y + 30), "field": (x0 + w - 62, field_y),
            "switch": (x0 + 50, switch_y + 30)}


def main():
    global img, d
    img = Image.new("RGB", (1080, 1120), "#F7F9FC")
    d = ImageDraw.Draw(img)
    d.text((540, 34), rtl("شاشة الإعدادات — أيقونة المستخدم تنتقل إلى حقل الاسم + زر تبديل الوضع"),
           font=font(24, True), fill="#0F1B26", anchor="mm")
    d.text((540, 68), rtl("الألوان صارت مرتبطة بالوضع المختار، فالتبديل يغيّر الخلفية والبطاقات والنصوص والأيقونات"),
           font=font(15), fill="#56697C", anchor="mm")

    left = screen(60, DARK, rtl("الوضع الداكن"), dark=True)
    right = screen(640, LIGHT, rtl("الوضع الفاتح"), dark=False)

    # ① سهم أحمر: الأيقونة أُزيلت من رأس الحساب
    note(150, 120, RED, "1. أزلت الأيقونة من هنا")
    arrow(150, 132, left["head"][0], left["head"][1], RED)
    # ② سهم أزرق: أيقونة المستخدم بجانب حقل الاسم
    note(560, 120, BLUE, "2. أيقونة المستخدم هنا")
    arrow(560, 132, left["field"][0], left["field"][1], BLUE)
    # ③ زر التبديل
    note(left["switch"][0] + 170, left["switch"][1] + 150, "#3DDC84", "3. زر التبديل يحفظ الاختيار", anchor="lm")
    arrow(left["switch"][0] + 130, left["switch"][1] + 128, left["switch"][0] + 6, left["switch"][1] + 30, "#3DDC84", w=3)
    note(850, 120, "#128A48", "نفس الشاشة بالألوان الفاتحة")
    arrow(850, 134, right["head"][0] + 6, right["head"][1] - 52, "#128A48", w=3)

    d.text((540, TOP + H + 44), rtl("زر واحد في صف «مظهر التطبيق» يبدّل بين الوضعين والاختيار يبقى بعد إغلاق التطبيق"),
           font=font(18, True), fill="#0F1B26", anchor="mm")
    d.text((540, TOP + H + 74), rtl("وأيقونات شريط الحالة تتبع الوضع المختار لا وضع الجهاز"),
           font=font(15), fill="#56697C", anchor="mm")
    img.save(OUT)
    print("✔", OUT, img.size)


if __name__ == "__main__":
    main()
