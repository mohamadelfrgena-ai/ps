#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة «صندوق التحميل» بشاشة البداية (ps4-addon-test/src/screens.cpp — splashLoadingBox).

المطلوب (2026-10-29): حذف شريط التحميل القديم (الصندوق الذهبي أسفل اليسار)، ووضع
صندوق التحميل على هيئة مؤشر الحفظ التلقائي في ماينكرفت (برميل + سهم متحرك) حيث
يشير السهم الأحمر — أسفل اليمين في العربية.

الإخراج: loading_box_preview.png
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "loading_box_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, PANEL, ELEV, BORDER2 = "#0B0F14", "#050608", "#1A2330", "#334152"
TEXT, MUTED, DIM = "#E8EEF5", "#8EA0B3", "#5C6F82"
GREEN, RED, GOLD = "#3DDC84", "#FF5A5A", "#E9C46A"

W, H = 1920, 1180
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


def loading_icon(x, y, bob):
    """نفس هندسة drawLoadingBoxIcon في screens.cpp (px = بكسل حقيقي 1:1)."""
    # السهم
    ay = y + bob
    d.rectangle((x + 24, ay, x + 48, ay + 34), fill="#4A4A4A")
    d.rectangle((x + 26, ay + 2, x + 46, ay + 34), fill="#F5EDC0")
    head = (60, 46, 32, 18, 6)
    for r, hw in enumerate(head):
        hy = ay + 34 + r * 8
        d.rectangle((x + 36 - hw // 2, hy, x + 36 - hw // 2 + hw, hy + 9), fill="#4A4A4A")
    for r, hw in enumerate(head):
        hw -= 6
        hy = ay + 34 + r * 8
        if hw > 0:
            d.rectangle((x + 36 - hw // 2, hy + 1, x + 36 + hw // 2, hy + 8), fill="#F5EDC0")
    # البرميل
    by = y + 78
    d.rounded_rectangle((x + 2, by, x + 74, by + 58), radius=10, fill="#0C0905")
    d.rounded_rectangle((x + 5, by + 3, x + 71, by + 55), radius=8, fill="#C8842F")
    d.rounded_rectangle((x + 8, by + 6, x + 68, by + 20), radius=6, fill="#DEA452")
    d.rectangle((x + 5, by + 24, x + 71, by + 32), fill="#402A10")
    d.rectangle((x + 5, by + 40, x + 27, by + 47), fill="#402A10")
    d.rectangle((x + 51, by + 40, x + 73, by + 47), fill="#402A10")
    d.rectangle((x + 15, by + 21, x + 29, by + 39), fill="#14100A")
    d.rectangle((x + 17, by + 23, x + 27, by + 37), fill="#9AA0A8")
    d.rectangle((x + 37, by + 43, x + 43, by + 55), fill="#784E1C")


def splash_corner(x0, y0, w, h, new=True):
    """ركن الشاشة الافتتاحية (خلفية سوداء) — new=True بعد / False قبل."""
    d.rounded_rectangle((x0, y0, x0 + w, y0 + h), radius=16, fill=PANEL, outline=BORDER2, width=2)
    # نص الترخيص (إشارة بصرية فقط)
    d.rectangle((x0 + w - 520, y0 + 24, x0 + w - 40, y0 + 32), fill="#22262C")
    d.rectangle((x0 + w - 520, y0 + 44, x0 + w - 120, y0 + 52), fill="#1B1F24")
    if new:
        # AFTER: الأيقونة + النص معاً أسفل اليمين (نفس مواضع screens.cpp الحقيقية)
        loading_icon(x0 + w - 190, y0 + h - 200, 0)
        text(x0 + w - 214, y0 + h - 128, "جار التحميل…", 24, "#ECECEC", anchor="rm")
        latin(x0 + w - 510, y0 + h - 126, "50%", 22, "#FFD65A", True, anchor="rm")
    else:
        # BEFORE: الصندوق القديم أسفل اليسار + النص متشتت أسفل اليمين
        bx, by = x0 + 40, y0 + h - 120
        d.rounded_rectangle((bx, by, bx + 215, by + 78), radius=22, fill="#10141C", outline="#FFD65AAA", width=2)
        d.rounded_rectangle((bx + 13, by + 54, bx + 202, by + 62), radius=4, fill="#FFD65A32")
        d.rounded_rectangle((bx + 13, by + 54, bx + 110, by + 62), radius=4, fill="#FFD65AE6")
        text(x0 + w - 214, y0 + h - 128, "جار التحميل…", 24, "#ECECEC", anchor="rm")
        latin(x0 + w - 510, y0 + h - 126, "50%", 22, "#FFD65A", True, anchor="rm")


# ---------- الترويسة ----------
text(W - 44, 30, "صندوق التحميل — شاشة البداية", 22, GOLD, True, anchor="rm")
text(W - 44, 62, "مؤشر الحفظ التلقائي في ماينكرفت (برميل + سهم متحرك) حيث يشير السهم الأحمر", 15, "#B8C7D6", anchor="rm")
text(W - 44, 90, "الشريط القديم أسفل اليسار حُذف تماماً — كل شيء مجموعة واحدة أسفل اليمين", 15, "#B8C7D6", anchor="rm")
d.line((44, 118, W - 44, 118), fill=ELEV)

# ---------- بعد ----------
text(W - 44, 150, "بعد — صندوق التحميل (أسفل اليمين في العربية)", 16, GREEN, True, anchor="rm")
splash_corner(44, 172, W - 88, 380, new=True)

# ---------- قبل ----------
text(W - 44, 596, "قبل — الشريط القديم (حُذف)", 16, RED, True, anchor="rm")
splash_corner(44, 618, W - 88, 300, new=False)
# علامة إلغاء على الصندوق القديم
d.line((70, 700, 245, 810), fill=RED, width=5)
d.line((245, 700, 70, 810), fill=RED, width=5)

# ---------- الحركة ----------
d.line((44, 952, W - 44, 952), fill=ELEV)
text(W - 44, 986, "الحركة: السهم يهبط ويعود كل 0.35 ثانية (خطوتان — أسلوب ماينكرفت) · الأيقونة مرسومة بالكود (بلا صور خارجية)", 15, MUTED, anchor="rm")
for i, bob in enumerate((0, 10)):
    loading_icon(1560 - i * 190, 1010, bob)
    text(1560 - i * 190 + 36, 1150, "الإطار 1" if i == 0 else "الإطار 2", 13, DIM, anchor="mm")

img.save(OUT)
print("saved", OUT, img.size)
