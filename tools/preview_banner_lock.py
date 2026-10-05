#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة تبويب «الأغلفة» في تعديل الملف الشخصي بعد الطلب:

    • حذف 🔒 من **جنب الكلمة** (رقاقة التبويب صارت «الأغلفة» بلا قفل)؛
    • القفل **فوق الغلاف نفسه** (تظليل + 🔒 في وسط الخلية)؛
    • الغلاف **لا يُفتح ولا يُختار إلا باشتراك** Epic 💎 / Legendary 👑
      (الواجهة تظهر سبب القفل، والقاعدة ترفض أصلاً).

يولّد: banner_lock_preview.png
"""
from PIL import Image, ImageDraw, ImageFont  # type: ignore
import arabic_reshaper  # type: ignore
from bidi.algorithm import get_display  # type: ignore

W, H = 900, 470
BG = (17, 20, 26)
PANEL = (26, 30, 38)
ELEV = (34, 39, 48)
BORDER = (52, 58, 70)
TEXT = (238, 241, 246)
MUTED = (150, 158, 170)
DIM = (120, 128, 140)
GREEN = (60, 200, 120)


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
F_CHIP = font(14)
F_CELL = font(13)
F_NOTE = font(13)
F_LOCK = font(30)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((24, 16), ar("تبويب «الأغلفة» — تعديل الملف الشخصي"), font=F_TITLE, fill=TEXT)
d.text((24, 40), ar("القفل انتقل من جنب الكلمة إلى الغلاف نفسه"),
       font=F_NOTE, fill=GREEN)

# ── رقاقات التبويبات ────────────────────────────────────────────────────────
y = 74
chips = [("الأفاتار / الأداة", False), ("الأغلفة", True), ("الأداة بجانب الاسم", False)]
x = 24
for label, selected in chips:
    tw = d.textbbox((0, 0), ar(label), font=F_CHIP)[2] + 26
    d.rounded_rectangle([(x, y), (x + tw, y + 34)], radius=17,
                        fill=(40, 60, 52) if selected else PANEL,
                        outline=GREEN if selected else BORDER, width=1)
    d.text((x + 13, y + 9), ar(label), font=F_CHIP,
           fill=GREEN if selected else MUTED)
    x += tw + 8

# ✗ ما كان: قفل بجانب الكلمة
d.text((x + 12, y + 10), ar("✗ كان بجانب الكلمة: «الأغلفة» + قفل"), font=F_NOTE, fill=DIM)

# ── شبكة الأغلفة المقفولة ───────────────────────────────────────────────────
y = 132
cols, gap, pad = 2, 12, 24
cw = (W - pad * 2 - gap * (cols - 1)) // cols
chh = int(cw * 9 / 16)

for i in range(4):
    cx = pad + (i % cols) * (cw + gap)
    cy = y + (i // cols) * (chh + gap)
    # خلية الغلاف
    d.rounded_rectangle([(cx, cy), (cx + cw, cy + chh)], radius=12,
                        fill=ELEV, outline=BORDER, width=1)
    # «صورة» الغلاف: تدرّج بسيط
    for k in range(chh - 8):
        t = k / max(1, chh - 9)
        col = (int(60 + 90 * t), int(80 + 60 * t), int(110 - 20 * t))
        d.line([(cx + 4, cy + 4 + k), (cx + cw - 4, cy + 4 + k)], fill=col)
    # القفل فوق الغلاف: تظليل + 🔒 في المنتصف
    overlay = Image.new("RGBA", (cw, chh), (20, 24, 30, 140))
    img.paste(overlay, (cx, cy), overlay)
    d2 = ImageDraw.Draw(img)
    # رسم قفل (لا خط إيموجي في هذه البيئة): حلقة + جسم + ثقب المفتاح
    lx, ly = cx + cw // 2, cy + chh // 2 - 4
    d2.arc([lx - 11, ly - 30, lx + 11, ly - 6], start=180, end=360, fill=TEXT, width=5)
    d2.rounded_rectangle([(lx - 17, ly - 8), (lx + 17, ly + 24)], radius=6,
                         fill=TEXT, outline=TEXT)
    d2.ellipse([(lx - 4, ly + 3), (lx + 4, ly + 11)], fill=(30, 34, 42))
    d2.rounded_rectangle([(cx + 6, cy + chh - 24), (cx + 84, cy + chh - 6)],
                         radius=8, fill=(18, 21, 27))
    d2.text((cx + 14, cy + chh - 22), ar("Epic"), font=F_CELL, fill=MUTED)

d = ImageDraw.Draw(img)
# ── ملاحظة القفل ────────────────────────────────────────────────────────────
ny = y + 2 * (chh + gap) + 10
d.rounded_rectangle([(pad, ny), (W - pad, ny + 62)], radius=12, fill=PANEL)
d.text((pad + 14, ny + 12),
       ar("الأغلفة كلها مقفولة — لا تُفتح إلا باشتراك Epic أو Legendary"),
       font=F_NOTE, fill=MUTED)
d.text((pad + 14, ny + 34),
       ar("النقر على غلاف مقفول يُظهر السبب فقط، والقاعدة ترفض الاختيار أصلاً"),
       font=F_NOTE, fill=DIM)

out = "banner_lock_preview.png"
img.save(out)
print("✓", out, img.size)
