#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة **صورة المجهول (الزائر)** — صورة المالك default-profile.jpg:

    تُعرض لمن ليس له حساب: قبل إنشاء الحساب/تسجيل الدخول، وبعد تسجيل الخروج،
    وللزائر في الرئيسية · الإعدادات · لوحة المستخدم في المجتمع.

المصدر: ui/components/Common.kt (AnonymousAvatarImage · DefaultProfileAvatar)
        ui/auth/AuthScreens.kt · ui/home/HomeScreen.kt
        ui/settings/SettingsScreen.kt · ui/community/CommunityScreens.kt
الإخراج: guest_avatar_preview.png
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "guest_avatar_preview.png")
AVATAR = os.path.join(ROOT, "android", "app", "src", "main", "res",
                      "drawable-nodpi", "ic_default_profile.jpg")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SCREEN, ELEV, ELEV2, BORDER = "#070A0E", "#0B1017", "#151D28", "#1B2532", "#26333F"
TEXT, MUTED, DIM, GREEN = "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84"

W, H = 960, 560
CELL_W, CELL_H = 440, 210


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def circle(im, box, src):
    """يلصق الصورة داخل دائرة (قناع) في المربع المطلوب."""
    w = box[2] - box[0]
    h = box[3] - box[1]
    mask = Image.new("L", (w * 3, h * 3), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, w * 3 - 1, h * 3 - 1), fill=255)
    mask = mask.resize((w, h), Image.LANCZOS)
    face = src.resize((w, h), Image.LANCZOS)
    im.paste(face, box, mask)


def rounded(im, box, src, radius):
    w = box[2] - box[0]
    h = box[3] - box[1]
    mask = Image.new("L", (w * 3, h * 3), 0)
    ImageDraw.Draw(mask).rounded_rectangle((0, 0, w * 3 - 1, h * 3 - 1),
                                           radius=radius * 3, fill=255)
    mask = mask.resize((w, h), Image.LANCZOS)
    im.paste(src.resize((w, h), Image.LANCZOS), box, mask)


def panel(img, ox, oy, title, note, kind):
    d = ImageDraw.Draw(img)
    d.rounded_rectangle((ox, oy, ox + CELL_W, oy + CELL_H), radius=18,
                        fill=SCREEN, outline=BORDER, width=1)
    d.text((ox + CELL_W - 18, oy + 20), t(title), font=font(14, True), fill=GREEN, anchor="rm")

    face = Image.open(AVATAR).convert("RGB")
    cx = ox + 92

    if kind == "auth":                      # شاشة الدخول/الإنشاء: دائرة 92
        cy = oy + 118
        s = 108
        d.ellipse((cx - s // 2 - 2, cy - s // 2 - 2, cx + s // 2 + 2, cy + s // 2 + 2),
                  fill=ELEV2, outline=BORDER)
        circle(img, (cx - s // 2, cy - s // 2, cx + s // 2, cy + s // 2), face)
        d.text((cx + 130, cy - 18), t("مرحباً بك"), font=font(15, True), fill=TEXT, anchor="lm")
        d.text((cx + 130, cy + 6), t("سجّل الدخول أو أنشئ حساباً"), font=font(11),
               fill=MUTED, anchor="lm")
        d.text((cx + 130, cy + 26), t("(صورة المجهول)"), font=font(10), fill=DIM, anchor="lm")

    elif kind == "home":                    # الرئيسية: مربّع دائري 62
        cy = oy + 112
        s = 84
        d.rounded_rectangle((cx - s // 2 - 3, cy - s // 2 - 3, cx + s // 2 + 3, cy + s // 2 + 3),
                            radius=22, fill=ELEV2, outline=BORDER)
        rounded(img, (cx - s // 2, cy - s // 2, cx + s // 2, cy + s // 2), face, 19)
        d.text((cx + 110, cy - 16), t("زائر"), font=font(15, True), fill=TEXT, anchor="lm")
        d.text((cx + 110, cy + 8), t("سجّل الدخول لفتح كل الميزات"), font=font(11),
               fill=MUTED, anchor="lm")

    elif kind == "settings":                # الإعدادات: دائرة 54 بإطار أخضر
        cy = oy + 112
        s = 74
        d.ellipse((cx - s // 2 - 3, cy - s // 2 - 3, cx + s // 2 + 3, cy + s // 2 + 3),
                  fill=GREEN)
        circle(img, (cx - s // 2, cy - s // 2, cx + s // 2, cy + s // 2), face)
        d.text((cx + 100, cy - 16), t("زائر"), font=font(15, True), fill=TEXT, anchor="lm")
        d.text((cx + 100, cy + 8), t("رأس الحساب في الإعدادات"), font=font(11),
               fill=MUTED, anchor="lm")

    else:                                   # لوحة المستخدم في المجتمع: دائرة 40
        cy = oy + 112
        s = 56
        d.rounded_rectangle((ox + 18, cy - 34, ox + CELL_W - 18, cy + 34), radius=12,
                            fill=ELEV, outline=BORDER)
        circle(img, (cx - s // 2, cy - s // 2, cx + s // 2, cy + s // 2), face)
        d.text((cx + 60, cy - 14), t("زائر"), font=font(14, True), fill=TEXT, anchor="lm")
        d.text((cx + 60, cy + 8), t("لوحة المستخدم أسفل المجتمع"), font=font(10),
               fill=MUTED, anchor="lm")

    d.text((ox + 18, oy + CELL_H - 20), t(note), font=font(9), fill=DIM, anchor="lm")


def main():
    if not os.path.exists(AVATAR):
        print("✗ صورة المجهول غير موجودة:", AVATAR)
        return 2
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((W // 2, 32), t("صورة المجهول (الزائر) — قبل الدخول/الإنشاء وبعد تسجيل الخروج"),
           font=font(16, True), fill=TEXT, anchor="mm")

    panel(img, 30, 70, "١) تسجيل الدخول / إنشاء حساب",
          "نفس الصورة في الشاشتين (دائرة 92)", "auth")
    panel(img, W - 30 - CELL_W, 70, "٢) الرئيسية (رأس الشاشة)",
          "مربّع دائري 62 — الزائر فقط", "home")
    panel(img, 30, 70 + CELL_H + 30, "٣) الإعدادات (رأس الحساب)",
          "دائرة 54 بإطار أخضر", "settings")
    panel(img, W - 30 - CELL_W, 70 + CELL_H + 30, "٤) المجتمع (لوحة المستخدم)",
          "دائرة 40 أسفل الشاشة", "community")

    d.text((W // 2, H - 16),
           t("المسجّل تبقى صورته الخاصة (onefree افتراضياً) — والمجهول وحده بهذه الصورة"),
           font=font(10), fill=DIM, anchor="mm")
    img.save(OUT)
    print(f"✓ {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
