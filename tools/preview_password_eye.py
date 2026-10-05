#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة طلب المالك: **عين 👁 داخل حقل كلمة المرور** + **حقل تأكيد** في إنشاء الحساب

    • الدخول وإنشاء الحساب وكلمة المرور الجديدة: عين داخل الحقل تُظهر النص
      وتُخفيه بلمسة؛
    • إنشاء الحساب صار فيه حقل ثانٍ «كرر كلمة المرور»، وإن اختلفا لا يُنشأ
      الحساب وتظهر رسالة التوضيح.

يولّد: password_eye_preview.png
"""
from PIL import Image, ImageDraw, ImageFont  # type: ignore
import arabic_reshaper  # type: ignore
from bidi.algorithm import get_display  # type: ignore

W, H = 940, 600
BG = (14, 18, 24)
PHONE_BG = (11, 15, 20)
FIELD_BG = (19, 26, 36)
BORDER = (38, 50, 64)
TEXT = (232, 238, 245)
MUTED = (142, 160, 179)
DIM = (110, 128, 145)
GREEN = (61, 220, 132)
GOLD = (255, 212, 121)


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
F_HEAD = font(13, True)
F_LABEL = font(11)
F_TEXT = font(13)
F_DOTS = font(16, True)
F_ANN = font(12)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((24, 16), ar("كلمة المرور — عين الإظهار داخل الحقل"), font=F_TITLE, fill=TEXT)
d.text(
    (24, 42),
    ar("العين في كل الشاشات: الدخول · إنشاء الحساب · كلمة المرور الجديدة"),
    font=F_NOTE,
    fill=GREEN,
)
d.text(
    (24, 64),
    ar("وإنشاء الحساب صار فيه حقل ثانٍ: «كرر كلمة المرور»"),
    font=F_NOTE,
    fill=MUTED,
)


def lock(x, y, s=14, color=MUTED):
    """قفل صغير بجانب الحقل."""
    d.rounded_rectangle([x, y + s // 3, x + s, y + s], radius=3, fill=color)
    d.arc([x + 2, y, x + s - 2, y + s // 2 + 2], start=180, end=360, fill=color, width=2)


def eye(x, y, s=16, open_eye=True, color=MUTED):
    """عين: مفتوحة (= النص ظاهر) أو مشطوبة (= النص مخفي)."""
    cx, cy = x + s / 2, y + s / 2
    d.ellipse([x, cy - s / 3, x + s, cy + s / 3], outline=color, width=2)
    if open_eye:
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=color)
    else:
        d.line([x - 2, cy + s / 3 + 2, x + s + 2, cy - s / 3 - 2], fill=color, width=2)


def field(box, label, value, show, note=None, note_color=GOLD):
    x0, y0, x1, y1 = box
    d.rounded_rectangle([x0, y0, x1, y1], radius=12, fill=FIELD_BG, outline=BORDER, width=2)
    d.text((x0 + 14, y0 + 9), ar(label), font=F_LABEL, fill=MUTED)
    lock(x0 + 14, y0 + 32)
    if show:
        d.text((x0 + 38, y0 + 28), value, font=F_TEXT, fill=TEXT)
    else:
        dots = "• " * max(3, len(value) // 2)
        d.text((x0 + 38, y0 + 28), dots, font=F_DOTS, fill=TEXT)
    eye(x1 - 30, y0 + 26, open_eye=show, color=GREEN if show else DIM)
    if note:
        d.text((x0 + 14, y1 + 6), ar(note), font=F_ANN, fill=note_color)


PW, PH = 400, 430

# ── يسار: شاشة الدخول ───────────────────────────────────────────────────────
x0, y0 = 40, 100
d.rounded_rectangle([x0, y0, x0 + PW, y0 + PH], radius=18, fill=PHONE_BG, outline=BORDER, width=2)
d.text((x0 + 16, y0 + 16), ar("تسجيل الدخول"), font=F_HEAD, fill=TEXT)
y = y0 + 50
field([x0 + 16, y, x0 + PW - 16, y + 60], "البريد الإلكتروني", "user@mail.com", False)
y += 78
field(
    [x0 + 16, y, x0 + PW - 16, y + 60],
    "كلمة المرور",
    "AdD0n@2026",
    False,
    note="👁 العين داخل الحقل — لمسة تُظهرها",
    note_color=GREEN,
)
d.text((x0 + 16, y0 + PH + 12), ar("الدخول — الحقل مخفي افتراضياً"), font=F_HEAD, fill=MUTED)

# ── يمين: شاشة إنشاء الحساب ─────────────────────────────────────────────────
x1 = 500
d.rounded_rectangle([x1, y0, x1 + PW, y0 + PH], radius=18, fill=PHONE_BG, outline=BORDER, width=2)
d.text((x1 + 16, y0 + 16), ar("إنشاء حساب"), font=F_HEAD, fill=TEXT)
y = y0 + 50
field([x1 + 16, y, x1 + PW - 16, y + 60], "اسم المستخدم", "AddonPro", False)
y += 78
field([x1 + 16, y, x1 + PW - 16, y + 60], "البريد الإلكتروني", "user@mail.com", False)
y += 78
field(
    [x1 + 16, y, x1 + PW - 16, y + 60],
    "كلمة المرور",
    "AdD0n@2026",
    True,
    note="👁 مفتوحة — النص يظهر كما كتبته",
    note_color=GREEN,
)
y += 78
field(
    [x1 + 16, y, x1 + PW - 16, y + 60],
    "كرر كلمة المرور",
    "AdD0n@2026",
    False,
    note="حقل جديد للتأكيد — وإن اختلفا: «كلمتا المرور غير متطابقتين»",
    note_color=GOLD,
)
d.text((x1 + 16, y0 + PH + 12), ar("إنشاء الحساب — العين في الحقلين"), font=F_HEAD, fill=MUTED)

d.text(
    (24, H - 30),
    ar("تعمل في: الدخول · إنشاء الحساب · كلمة المرور الجديدة (شاشة الاستعادة)"),
    font=F_NOTE,
    fill=MUTED,
)

img.save("password_eye_preview.png")
print("✓ password_eye_preview.png")
