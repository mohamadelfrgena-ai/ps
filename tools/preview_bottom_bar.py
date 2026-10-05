#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة إزالة كلمة «Home» من الشريط السفلي (زر المنزل المرتفع بلا نصّ تحته).

المصدر: ui/components/BottomNav.kt (NavItem — الفرع isCenter).
الإخراج: bottom_bar_preview.png
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "bottom_bar_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, BAR, ELEV, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#334152"
TEXT, MUTED, DIM, GREEN, ONGREEN, RED = (
    "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F", "#FF5A5A")

W, H = 900, 420
img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def text(x, y, s, size=13, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def latin(x, y, s, size=12, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), s, font=font(size, bold), fill=color, anchor=anchor)


def bar(y0, title, with_home_label, color=GREEN):
    """يرسم شريطاً سفلياً (62dp) مع زر المنزل المرتفع في الوسط."""
    x0, x1 = 40, W - 40
    text(x1, y0 - 34, title, 15, color, True, "rm")
    d.rounded_rectangle((x0, y0, x1, y0 + 62), radius=18, fill=BAR, outline=BORDER2, width=1)
    # أربع عناصر جانبية (من اليمين لليساء: الإعدادات · المجتمع · المتجر · الملف)
    for i, (label, sel) in enumerate((("الإعدادات", False), ("المجتمع", False),
                                      ("المتجر", False), ("الملف", False))):
        cx = x1 - 70 - i * ((x1 - x0 - 200) // 3)
        d.rounded_rectangle((cx - 22, y0 + 12, cx + 22, y0 + 38), radius=8,
                            fill=ELEV if sel else None)
        d.ellipse((cx - 11, y0 + 16, cx + 11, y0 + 34), outline=DIM if not sel else GREEN, width=2)
        text(cx, y0 + 50, label, 11, GREEN if sel else DIM, anchor="mm")
    # زر المنزل المرتفع في الوسط
    cx = (x0 + x1) // 2
    by = y0 - 20
    d.rounded_rectangle((cx - 29, by, cx + 29, by + 58), radius=19,
                        fill=GREEN, outline="#7BEBA9", width=2)
    # أيقونة المنزل (مثلث + مستطيل)
    d.polygon([(cx, by + 14), (cx - 13, by + 27), (cx + 13, by + 27)], fill=ONGREEN)
    d.rectangle((cx - 8, by + 27, cx + 8, by + 44), fill=ONGREEN)
    if with_home_label:
        text(cx, y0 + 50, "Home", 11, "#7BEBA9", True, "mm")
        d.line((cx - 26, y0 + 42, cx + 26, y0 + 42), fill=RED, width=3)
        d.line((cx - 26, y0 + 58, cx + 26, y0 + 58), fill=RED, width=3)
    else:
        text(cx, y0 + 50, "— بلا كلمة —", 10, DIM, anchor="mm")


text(W - 40, 26, "الشريط السفلي: إزالة كلمة «Home» من زر المنزل", 20, GREEN, True, "rm")
text(W - 40, 58, "الأيقونة الخضراء المرتفعة تبقى وحدها — باقي العناصر كما هي", 13, MUTED, "rm")
d.line((40, 84, W - 40, 84), fill=ELEV)

bar(190, "قبل — كانت كلمة Home تحت الزر", True, RED)
bar(320, "بعد — الزر وحده بلا كلمة", False, GREEN)

img.save(OUT)
print("saved", OUT, img.size)
