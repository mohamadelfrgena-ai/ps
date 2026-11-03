#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة **إطار الاختيار** في شاشة «تعديل الملف الشخصي» بعد الطلب:

    • الأصل المختار (أفاتار · غلاف · أداة) يُضاء بـ:
        - حدّ أخضر ساطع (2.5dp بدل 1dp الرمادي)،
        - هالة خضراء خفيفة حول الخلية،
        - خلفية مُلوّنة بالأخضر،
        - علامة ✓ في الزاوية،
        - واسمه مكتوب بالأخضر.
    • غير المختار يبقى بحدّ رمادي رفيع وخلفية عادية.

يولّد: selected_frame_preview.png
"""
from PIL import Image, ImageDraw, ImageFont  # type: ignore
import arabic_reshaper  # type: ignore
from bidi.algorithm import get_display  # type: ignore

W, H = 900, 560
BG = (11, 15, 20)
PANEL = (19, 26, 36)
ELEV = (26, 35, 48)
BORDER = (38, 50, 64)
TEXT = (232, 238, 245)
MUTED = (142, 160, 179)
GREEN = (61, 220, 132)
ON_GREEN = (4, 33, 15)


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
F_CELL = font(12)
F_SEC = font(13, True)
F_CHECK = font(15, True)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((24, 16), ar("إطار الاختيار — تعديل الملف الشخصي"), font=F_TITLE, fill=TEXT)
d.text(
    (24, 40),
    ar("المختار: حدّ أخضر ساطع + هالة + خلفية مُلوّنة + علامة ✓ واسم أخضر"),
    font=F_NOTE,
    fill=GREEN,
)
d.text(
    (24, 60),
    ar("غير المختار: حدّ رمادي رفيع بلا علامة"),
    font=F_NOTE,
    fill=MUTED,
)


def blend(base: tuple, color: tuple, alpha: float) -> tuple:
    """مزج لون فوق لون بنسبة شفافية alpha (0..1)."""
    return tuple(
        int(round(base[i] * (1 - alpha) + color[i] * alpha)) for i in range(3)
    )


def cell(box, label, selected, radius=14):
    x0, y0, x1, y1 = box
    if selected:
        # هالة خضراء خفيفة حول الخلية (خلف البطاقة)
        d.rounded_rectangle(
            [x0 - 5, y0 - 5, x1 + 5, y1 + 5],
            radius=radius + 5,
            fill=blend(BG, GREEN, 0.16),
        )
        d.rounded_rectangle(
            [x0 - 5, y0 - 5, x1 + 5, y1 + 5],
            radius=radius + 5,
            outline=blend(BG, GREEN, 0.28),
            width=3,
        )
    # جسم الخلية
    d.rounded_rectangle(
        [x0, y0, x1, y1],
        radius=radius,
        fill=blend(ELEV, GREEN, 0.14) if selected else ELEV,
        outline=GREEN if selected else BORDER,
        width=5 if selected else 2,
    )
    # صورة الأصل (مربّع/مستطيل داخلي)
    ix0, iy0, ix1, iy1 = x0 + 12, y0 + 12, x1 - 12, y1 - 40
    d.rounded_rectangle([ix0, iy0, ix1, iy1], radius=10, fill=PANEL)
    d.rounded_rectangle(
        [ix0, iy0, ix1, iy1], radius=10, outline=blend(PANEL, GREEN, 0.35), width=2
    )
    cx, cy = (ix0 + ix1) // 2, (iy0 + iy1) // 2
    r = min(ix1 - ix0, iy1 - iy0) // 4
    d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=blend(PANEL, GREEN, 0.30))
    # الاسم
    tw = d.textlength(ar(label), font=F_CELL)
    d.text(
        ((x0 + x1) / 2 - tw / 2, y1 - 30),
        ar(label),
        font=F_CELL,
        fill=GREEN if selected else MUTED,
    )
    if selected:
        # علامة ✓ في الزاوية
        bx0, by0, bx1, by1 = x0 + 4, y0 + 4, x0 + 28, y0 + 28
        d.ellipse([bx0, by0, bx1, by1], fill=GREEN, outline=PANEL, width=2)
        cw = d.textlength("✓", font=F_CHECK)
        d.text(
            ((bx0 + bx1) / 2 - cw / 2, (by0 + by1) / 2 - 10),
            "✓",
            font=F_CHECK,
            fill=ON_GREEN,
        )


# ── صف الأفاتار ─────────────────────────────────────────────────────────────
y = 96
d.text((24, y - 22), ar("تبويب «الأفاتار» — المختار هو الثاني"), font=F_SEC, fill=TEXT)
cw, gap, ch = 150, 12, 96
x = 24
names = ["PFP 1", "PFP 2", "PFP 3", "PFP 4", "PFP 5"]
for i, n in enumerate(names):
    cell([x, y, x + cw, y + ch], n, selected=(i == 1))
    x += cw + gap

# ── صف الأغلفة ──────────────────────────────────────────────────────────────
y = 232
d.text((24, y - 22), ar("تبويب «الأغلفة» — المختار هو الأول"), font=F_SEC, fill=TEXT)
cw2 = 400
x = 24
for i, n in enumerate(["Nature 01", "Random 07"]):
    cell([x, y, x + cw2, y + 150], n, selected=(i == 0))
    x += cw2 + gap

# ── صف الأدوات ──────────────────────────────────────────────────────────────
y = 424
d.text((24, y - 22), ar("تبويب «الأداة» — المختار هو الرابع"), font=F_SEC, fill=TEXT)
x = 24
items = ["Crown", "Wings", "Aura", "Enchanted Bedrock", "Halo"]
for i, n in enumerate(items):
    cell([x, y, x + cw, y + ch], n, selected=(i == 3))
    x += cw + gap

img.save("selected_frame_preview.png")
print("✓ selected_frame_preview.png")
