#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة إصلاح **تداخل الشريط السفلي في لوحة الإدارة** مع شريط التنقّل:

    • قبل: شريط اللوحة (5 عناصر) ينزل إلى حافة الشاشة ⇒ يقع **تحت** أزرار
      النظام (رجوع · منزل · التطبيقات) فيتداخل معها وتُغطّى الكلمات.
    • بعد: حاوية الشريط تحجز `bottomBarInsets()` =
      `navigationBars ∪ systemGestures(السفلى)` وتلوّنها بلون التطبيق،
      ويبقى الشريط **فوق** المنطقة ⇒ لا تداخل — تلقائياً مع **الأزرار الثلاثة**
      و**الإيماءات** في كل الأجهزة.

يولّد: admin_bottom_bar_preview.png
"""
from PIL import Image, ImageDraw, ImageFont  # type: ignore
import arabic_reshaper  # type: ignore
from bidi.algorithm import get_display  # type: ignore

W, H = 940, 560
BG = (14, 18, 24)
PHONE_BG = (11, 15, 20)
PANEL = (19, 26, 36)
ELEV = (26, 35, 48)
BORDER = (38, 50, 64)
TEXT = (232, 238, 245)
MUTED = (142, 160, 179)
DIM = (110, 128, 145)
GREEN = (61, 220, 132)
RED = (255, 90, 90)
SYSBAR = (24, 28, 34)
SYSICON = (150, 160, 172)


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


F_TITLE = font(17, True)
F_NOTE = font(13)
F_HEAD = font(12)
F_LABEL = font(10)
F_TAB = font(12, True)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((24, 16), ar("لوحة الإدارة — الشريط السفلي وشريط التنقّل"), font=F_TITLE, fill=TEXT)
d.text(
    (24, 42),
    ar("الإصلاح: bottomBarInsets() = navigationBars ∪ systemGestures ⇒ يعمل مع الأزرار والإيماءات"),
    font=F_NOTE,
    fill=GREEN,
)

PW, PH = 400, 420
NAV_H = 34          # ارتفاع منطقة أزرار النظام
BAR_H = 58          # ارتفاع شريط اللوحة
TABS = ["الإدارة", "المحتوى", "المدفوعات", "الإشراف", "المزيد"]


def phone(x0, y0, title, title_color, ok):
    x1, y1 = x0 + PW, y0 + PH
    d.rounded_rectangle([x0, y0, x1, y1], radius=18, fill=PHONE_BG, outline=BORDER, width=2)
    # ترويسة اللوحة
    d.rounded_rectangle([x0 + 2, y0 + 2, x1 - 2, y0 + 42], radius=16, fill=ELEV)
    d.text((x0 + 14, y0 + 14), ar("لوحة الإدارة"), font=F_HEAD, fill=TEXT)
    # محتوى الصورة (بطاقات وهمية)
    cy = y0 + 56
    for i in range(5):
        d.rounded_rectangle([x0 + 14, cy, x1 - 14, cy + 44], radius=10, fill=PANEL)
        d.rounded_rectangle([x0 + 22, cy + 10, x0 + 58, cy + 34], radius=8, fill=ELEV)
        d.text((x0 + 68, cy + 16), ar("بطاقة " + str(i + 1)), font=F_NOTE, fill=MUTED)
        cy += 54
    # منطقة أزرار النظام (شريط التنقّل)
    nav_top = y1 - NAV_H
    if ok:
        # الشريط فوق المنطقة، والمنطقة ملوّنة بلون التطبيق
        bar_bottom = nav_top
    else:
        # الشريط ينزل إلى حافة الشاشة ⇒ يتداخل
        bar_bottom = y1
    bar_top = bar_bottom - BAR_H
    d.rounded_rectangle(
        [x0 + 2, bar_top, x1 - 2, bar_bottom],
        radius=8,
        fill=(15, 20, 27),
        outline=BORDER,
        width=2,
    )
    step = (PW - 8) / len(TABS)
    for i, t in enumerate(TABS):
        cx = x0 + 4 + step * (i + 0.5)
        sel = i == 0
        color = GREEN if sel else DIM
        # أيقونة (مربّع صغير) + الكلمة
        d.rounded_rectangle(
            [cx - 8, bar_top + 12, cx + 8, bar_top + 28], radius=4, outline=color, width=2
        )
        tw = d.textlength(ar(t), font=F_LABEL)
        d.text((cx - tw / 2, bar_top + 33), ar(t), font=F_LABEL, fill=color)
    # أزرار النظام فوق كل شيء (كما يراها المستخدم)
    d.rectangle([x0 + 2, nav_top, x1 - 2, y1 - 2], fill=SYSBAR)
    # رجوع ◁ · منزل ○ · التطبيقات ▢
    d.polygon(
        [(x0 + 70, y1 - 19), (x0 + 82, y1 - 27), (x0 + 82, y1 - 11)], fill=SYSICON
    )
    d.ellipse([x0 + 186, y1 - 27, x0 + 214, y1 - 11], outline=SYSICON, width=2)
    d.rectangle([x0 + 306, y1 - 27, x0 + 330, y1 - 11], outline=SYSICON, width=2)
    if not ok:
        # علامة التداخل
        d.rounded_rectangle(
            [x0 + 2, nav_top, x1 - 2, y1 - 2], outline=RED, width=3
        )
    d.text((x0, y1 + 10), ar(title), font=F_TAB, fill=title_color)


phone(30, 80, "قبل — الشريط تحت أزرار النظام (تداخل)", RED, ok=False)
phone(510, 80, "بعد — الشريط فوقها والمنطقة بلون التطبيق", GREEN, ok=True)

d.text(
    (24, H - 34),
    ar("الحاوية: .background(AppColors.Bg).windowInsetsPadding(bottomBarInsets())"),
    font=F_NOTE,
    fill=MUTED,
)

img.save("admin_bottom_bar_preview.png")
print("✓ admin_bottom_bar_preview.png")
