#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة حذف الإيموجي المكرّر من عناوين الأقسام:

    قبل:  ✦ ✦ خطط الاشتراك        (النص يبدأ بـ✦ + الكود يضيفه مرة ثانية)
    بعد:  ✦ خطط الاشتراك

يولّد: duplicate_emoji_preview.png
"""
from PIL import Image, ImageDraw, ImageFont  # type: ignore
import arabic_reshaper  # type: ignore
from bidi.algorithm import get_display  # type: ignore

W = 900
BG = (17, 20, 26)
PANEL = (26, 30, 38)
TEXT = (238, 241, 246)
MUTED = (150, 158, 170)
RED = (235, 90, 90)
GREEN = (80, 210, 140)
GREEN_UI = (60, 200, 120)

ROWS = [
    ("⚡", "إجراءات سريعة"),
    ("✦", "خطط الاشتراك"),
    ("⭐", "المميزة"),
    ("🆕", "أحدث الإضافات"),
    ("🔥", "موصى به لك"),
    ("📝", "النبذة"),
    ("⚡", "معلومات الحساب"),
    ("🖼", "إدارة الأفاتارات والملصقات"),
    ("➕", "إضافة غرفة"),
    ("📢", "إعلان"),
]


def ar(s: str) -> str:
    return get_display(arabic_reshaper.reshape(s))


def font(sz: int, bold: bool = False):
    for p in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans%s.ttf" % ("-Bold" if bold else ""),
        "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
    ):
        try:
            return ImageFont.truetype(p, sz)
        except Exception:
            continue
    return ImageFont.load_default()


F_TITLE = font(16, True)
F_ROW = font(15)
F_HEAD = font(14, True)

row_h = 34
H = 110 + row_h * (len(ROWS) + 1)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((24, 18), ar("حذف الإيموجي المكرّر في عناوين الأقسام"), font=F_TITLE, fill=TEXT)
d.text((24, 44), ar("الصفحة الرئيسية · الملف الشخصي · لوحة الأدمن"), font=F_HEAD, fill=GREEN_UI)

col_before, col_after = 40, 470
y = 84

d.text((col_before, y), ar("قبل ✗ (مكرّر)"), font=F_HEAD, fill=RED)
d.text((col_after, y), ar("بعد ✓ (مرة واحدة)"), font=F_HEAD, fill=GREEN)
y += 26

for emoji, label in ROWS:
    before = f"{emoji} {emoji} {label}"
    after = f"{emoji} {label}"
    d.text((col_before, y), ar(before), font=F_ROW, fill=MUTED)
    d.text((col_after, y), ar(after), font=F_ROW, fill=TEXT)
    y += row_h

# شريط سفلي يوضّح القاعدة
y += 6
d.rounded_rectangle([(24, y), (W - 24, y + 30)], radius=10, fill=PANEL)
d.text((col_before, y + 8),
       ar("القاعدة: SectionHeader يعرض «الإيموجي + العنوان» — فلا تُضِف الإيموجي مرتين"),
       font=F_ROW, fill=GREEN_UI)

out = "duplicate_emoji_preview.png"
img.save(out)
print("✓", out, img.size)
