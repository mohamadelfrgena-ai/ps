#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة شاشة الملف الشخصي على السوني — حالة «مسجّل بحساب» (وليست زائر).

المصدر: ps4-addon-test/src/screens.cpp (drawProfile — الحالتان guest / signed-in).
الإخراج: profile_account_preview.png
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "profile_account_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, BAR, ELEV, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#334152"
TEXT, MUTED, DIM = "#E8EEF5", "#8EA0B3", "#5C6F82"
GREEN, GREEN_DK, RED = "#3DDC84", "#0C2E1B", "#FF5A5A"
GOLD, GOLD_HI, ON_GOLD = "#E9C46A", "#F7E3A1", "#241A05"

W, H = 960, 720
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def text(x, y, s, size=13, color=TEXT, bold=False, *, anchor="lm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def latin(x, y, s, size=12, color=TEXT, bold=False, *, anchor="lm"):
    d.text((x, y), s, font=font(size, bold), fill=color, anchor=anchor)


def avatar(cx, cy, r, signed_in=True):
    """أفاتار بحافة ذهبية (مسجّل) أو رمادية باهتة (زائر)."""
    if signed_in:
        d.ellipse((cx - r - 3, cy - r - 3, cx + r + 3, cy + r + 3), outline=GOLD_HI, width=3)
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill="#2C4366")
        d.ellipse((cx - r + 10, cy - r + 10, cx + r - 10, cy + r - 10), outline=GOLD, width=2)
        text(cx, cy, "◈", int(r * 0.9), GOLD_HI, anchor="mm")
    else:
        d.ellipse((cx - r, cy - r, cx + r, cy + r), fill="#1A2330", outline=BORDER2, width=2)
        text(cx, cy, "◈", int(r * 0.8), DIM, anchor="mm")


def chip(xr, y, label, fill, outline, txt_color, size=13, h=36, pad=18):
    """شارة بطرفها الأيمن عند xr (RTL)."""
    w = font(size, True).getlength(t(label)) + 2 * pad
    x = xr - w
    d.rounded_rectangle((x, y, xr, y + h), radius=h // 2, fill=fill, outline=outline, width=2)
    text(xr - pad, y + h / 2, label, size, txt_color, True, anchor="rm")
    return x


# ---------- الترويسة ----------
text(W - 44, 28, "الملف الشخصي — حالة «مسجّل بحساب»", 21, GOLD, True, anchor="rm")
text(W - 44, 58, "اسم الحساب يحلّ محل وضع الزائر + شارة «مسجّل بحساب» + المحفظة", 15, "#B8C7D6", anchor="rm")
text(W - 44, 84, "الجهاز المرتبط يظهر في الأسفل — وضع الزائر يبقى متاحاً بالتبديل", 15, "#B8C7D6", anchor="rm")
d.line((44, 106, W - 44, 106), fill=ELEV)

# ---------- الحالة الجديدة ----------
text(W - 44, 132, "الحالة الجديدة — مسجّل بحساب", 15, GREEN, True, anchor="rm")

x0, x1, y0 = 48, W - 48, 152
d.rounded_rectangle((x0, y0, x1, y0 + 316), radius=18, fill=BAR, outline=BORDER2, width=1)

# الأفاتار + الاسم + الشارة
avatar(x1 - 84, y0 + 84, 52, True)
text(x1 - 156, y0 + 52, "sliver fox", 23, TEXT, True, anchor="rm")
latin(x1 - 156, y0 + 84, "@sliverfox", 14, MUTED, anchor="rm")
chip(x1 - 156, y0 + 104, "✓ مسجّل بحساب", GREEN_DK, GREEN, GREEN, 13)

# المحفظة
wx = x0 + 40
d.rounded_rectangle((wx, y0 + 28, wx + 200, y0 + 92), radius=14, fill=ELEV, outline=GOLD, width=2)
text(wx + 180, y0 + 48, "المحفظة", 12, MUTED, anchor="rm")
latin(wx + 180, y0 + 74, "1,250 AC", 22, GOLD_HI, True, anchor="rm")
text(wx + 18, y0 + 61, "◈", 22, GOLD, anchor="lm")

# الإحصاءات
stats = (("المفضلة", "12"), ("التحميلات", "34"), ("الأكواد", "3"))
for i, (label, val) in enumerate(stats):
    bx = x0 + 40 + i * 176
    d.rounded_rectangle((bx, y0 + 112, bx + 160, y0 + 176), radius=12, fill=ELEV, outline=BORDER2, width=1)
    text(bx + 142, y0 + 132, label, 12, MUTED, anchor="rm")
    latin(bx + 142, y0 + 158, val, 20, TEXT, True, anchor="rm")

# الجهاز المرتبط
ly = y0 + 196
d.rounded_rectangle((x0 + 40, ly, x1 - 40, ly + 52), radius=12, fill="#152436", outline=BORDER2, width=1)
text(x1 - 60, ly + 26, "PlayStation 4 — مرتبط ✓", 14, TEXT, anchor="rm")
text(x0 + 60, ly + 26, "🎮", 18, GOLD, anchor="lm")
chip(x0 + 100, ly + 8, "متصل الآن", GREEN_DK, GREEN, GREEN, 11, h=36)

# الأزرار
btns = (("تبديل الحساب", MUTED, BORDER2), ("تسجيل الخروج", RED, "#5A2430"))
for i, (label, col, outl) in enumerate(btns):
    bx = x0 + 40 + i * 196
    d.rounded_rectangle((bx, y0 + 264, bx + 180, y0 + 304), radius=12, fill=None, outline=outl, width=2)
    text(bx + 90, y0 + 284, label, 13, col, anchor="mm")

# ---------- للمقارنة: الزائر ----------
text(W - 44, 496, "للمقارنة — وضع الزائر (يبقى كما هو)", 15, MUTED, True, anchor="rm")

gy = 516
d.rounded_rectangle((x0, gy, x1, gy + 128), radius=18, fill="#0E141C", outline=BORDER2, width=1)
avatar(x1 - 84, gy + 64, 40, False)
text(x1 - 144, gy + 44, "زائر", 19, DIM, True, anchor="rm")
text(x1 - 144, gy + 76, "تتصفّح الآن بدون حساب — سجّل الدخول لحفظ مكتبتك", 12, MUTED, anchor="rm")
d.rounded_rectangle((x0 + 40, gy + 38, x0 + 220, gy + 90), radius=14, fill=GOLD, outline=GOLD_HI, width=2)
text(x0 + 130, gy + 64, "تسجيل الدخول", 15, ON_GOLD, True, anchor="mm")

# ملاحظة سفلية
d.rounded_rectangle((44, 668, W - 44, 704), radius=12, fill=ELEV, outline=BORDER2, width=1)
text(W - 60, 686, "الشارة الذهبية/الخضراء تظهر عند تسجيل الدخول فقط · نفس ألوان وخطوط النظام · بلا أي تغيير على وضع الزائر", 12, GREEN, anchor="rm")

img.save(OUT)
print("saved", OUT, img.size)
