#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""ثلاثة أوضاع لوضع أيقونة المستخدم «جنب حقل اسم المستخدم» — للاختيار.

كل وضع يحاكي نفس بطاقة الحساب في شاشة الإعدادات (قبل زر Save username):
  (أ) داخل الحقل، ملاصقة لاسم المستخدم في أول السطر  ← ما هو منفَّذ حالياً
  (ب) خارج الحقل تماماً، على حافته الأولى (بلاطة صغيرة)
  (ج) فوق الحقل في سطر عنوان صغير: [أيقونة] اسم المستخدم
ورأس الحساب بلا أيقونة في الثلاث (أُزيلت منه).
المصدر: SettingsScreen.kt
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "user_icon_placement.png")
DRAW = os.path.join(ROOT, "android/app/src/main/res/drawable-nodpi")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SURFACE, ELEV2, BORDER, BORDER2 = "#0B0F14", "#131A24", "#212D3D", "#263240", "#334152"
TEXT, MUTED, DIM, GREEN, ONGREEN = "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F"
BLUE, RED = "#1E6FD9", "#D93838"

CW, CH, GAP = 360, 420, 40
X0, Y0 = 60, 210
img = None
d = None


def rtl(t):
    return get_display(arabic_reshaper.reshape(t))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def icon(name, size):
    im = Image.open(os.path.join(DRAW, name + ".png")).convert("RGBA")
    return im.resize((size, size), Image.LANCZOS)


def arrow(x1, y1, x2, y2, color, w=5):
    d.line([(x1, y1), (x2, y2)], fill=color, width=w)
    import math
    a = math.atan2(y2 - y1, x2 - x1)
    for s in (0.55, -0.55):
        d.line([(x2, y2), (x2 - 16 * math.cos(a - s), y2 - 16 * math.sin(a - s))], fill=color, width=w)


def card(cx, letter, title):
    """بطاقة فيها رأس الحساب (بلا أيقونة) + حقل الاسم + زر الحفظ."""
    x1 = cx + CW
    d.rounded_rectangle((cx, Y0, x1, Y0 + CH), radius=18, fill=SURFACE, outline=BORDER, width=2)
    # رأس الحساب بلا أيقونة المستخدم
    d.ellipse((x1 - 62, Y0 + 20, x1 - 22, Y0 + 60), fill=ELEV2, outline=GREEN, width=2)
    d.text((x1 - 42, Y0 + 40), letter, font=font(16, True), fill=MUTED, anchor="mm")
    d.text((x1 - 72, Y0 + 30), "Mrforest", font=font(16, True), fill=TEXT, anchor="rm")
    d.text((x1 - 72, Y0 + 50), "mohamadelfrgena@gmail.com", font=font(10), fill=MUTED, anchor="rm")
    # عنوان البطاقة
    d.text((cx + 18, Y0 + 86), "Account", font=font(16, True), fill=TEXT, anchor="lt")
    # زر الحفظ
    d.rounded_rectangle((cx + 20, Y0 + 300, x1 - 20, Y0 + 350), radius=12, fill=GREEN)
    d.text(((cx + x1) // 2, Y0 + 325), "Save username", font=font(15, True), fill=ONGREEN, anchor="mm")
    # تسمية الاختيار
    d.text(((cx + x1) // 2, Y0 - 34), rtl(title), font=font(21, True), fill=BLUE, anchor="mm")
    return x1


def variant_a(cx):
    """(أ) داخل الحقل: ملاصقة لاسم المستخدم في أول السطر."""
    x1 = card(cx, "M", "(أ) داخل الحقل — جنب الاسم مباشرة")
    fy = Y0 + 160
    d.rounded_rectangle((cx + 20, fy - 28, x1 - 20, fy + 28), radius=10, outline=BORDER2, width=2)
    d.text((cx + 32, fy - 16), "Username", font=font(11), fill=MUTED, anchor="lm")
    ic = icon("ic_user", 26)
    img.paste(ic, (int(cx + 32), int(fy - 12)), ic)
    d.text((cx + 68, fy + 2), "Mrforest", font=font(16), fill=TEXT, anchor="lm")
    arrow(cx + 20, fy - 46, cx + 44, fy - 16, BLUE, w=4)
    return (cx + 44, fy - 12)


def variant_b(cx):
    """(ب) خارج الحقل: بلاطة صغيرة على حافته الأولى."""
    x1 = card(cx, "M", "(ب) خارج الحقل — بلاطة على حافته")
    fy = Y0 + 160
    d.rounded_rectangle((cx + 72, fy - 28, x1 - 20, fy + 28), radius=10, outline=BORDER2, width=2)
    d.text((cx + 84, fy - 16), "Username", font=font(11), fill=MUTED, anchor="lm")
    d.text((cx + 84, fy + 2), "Mrforest", font=font(16), fill=TEXT, anchor="lm")
    tile = cx + 22
    d.rounded_rectangle((tile, fy - 28, tile + 40, fy + 28), radius=10, fill=ELEV2, outline=BORDER)
    ic = icon("ic_user", 24)
    img.paste(ic, (int(tile + 8), int(fy - 12)), ic)
    arrow(tile - 8, fy - 48, tile + 18, fy - 20, BLUE, w=4)


def variant_c(cx):
    """(ج) فوق الحقل: سطر عنوان صغير فيه الأيقونة واسم الحقل."""
    x1 = card(cx, "M", "(ج) سطر فوق الحقل فيه الأيقونة")
    fy = Y0 + 168
    ic = icon("ic_user", 22)
    img.paste(ic, (int(cx + 22), int(fy - 66)), ic)
    d.text((cx + 54, fy - 55), rtl("اسم المستخدم"), font=font(14, True), fill=TEXT, anchor="lm")
    d.rounded_rectangle((cx + 20, fy - 20, x1 - 20, fy + 30), radius=10, outline=BORDER2, width=2)
    d.text((cx + 34, fy + 5), "Mrforest", font=font(16), fill=TEXT, anchor="lm")
    arrow(cx + 33, fy - 74, cx + 33, fy - 50, BLUE, w=4)


def main():
    global img, d
    img = Image.new("RGB", (X0 * 2 + CW * 3 + GAP * 2, 780), "#F7F9FC")
    d = ImageDraw.Draw(img)
    d.text((img.size[0] // 2, 60), rtl("أين تريد أيقونة المستخدم بالضبط؟ (أمام حقل اسم المستخدم)"),
           font=font(26, True), fill="#0F1B26", anchor="mm")
    d.text((img.size[0] // 2, 100), rtl("الأيقونة تُحذف من رأس الحساب في الثلاث — والفرق بمكانها الجديد فقط"),
           font=font(16), fill="#56697C", anchor="mm")
    d.text((img.size[0] // 2, 132), rtl("(رأس الحساب بلا أيقونة في الثلاث)"),
           font=font(16), fill="#56697C", anchor="mm")

    variant_a(X0)
    variant_b(X0 + CW + GAP)
    variant_c(X0 + (CW + GAP) * 2)

    d.text((img.size[0] // 2, Y0 + CH + 70), rtl("اختر: (أ) أو (ب) أو (ج) — وأنا أنفّذه فوراً"),
           font=font(21, True), fill="#0F1B26", anchor="mm")
    d.text((img.size[0] // 2, Y0 + CH + 108), rtl("(أ) هو الموجود حالياً في الكود"),
           font=font(16), fill=RED, anchor="mm")
    img.save(OUT)
    print("✔", OUT, img.size)


if __name__ == "__main__":
    main()
