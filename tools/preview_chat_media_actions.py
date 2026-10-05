#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة: الملصق · الصورة · الفيديو تُعامل **كرسالة واحدة**

    • النص يظهر فوق الوسيط في نفس الرسالة (لا رسالتين)؛
    • **الضغط المطوّل** على الملصق أو الصورة أو الفيديو يفتح **قائمة الرسالة
      نفسها**: رد · نسخ · إبلاغ · حذف (٣ دقائق).

يولّد: chat_media_actions_preview.png
"""
from PIL import Image, ImageDraw, ImageFont  # type: ignore
import arabic_reshaper  # type: ignore
from bidi.algorithm import get_display  # type: ignore

W, H = 900, 620
BG = (17, 20, 26)
BUBBLE = (32, 37, 46)
ELEV = (40, 46, 56)
TEXT = (238, 241, 246)
MUTED = (150, 158, 170)
DIM = (120, 128, 140)
GREEN = (60, 200, 120)
BLUE = (90, 160, 240)
PANEL = (24, 28, 35)


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
F_NAME = font(14, True)
F_BODY = font(14)
F_TINY = font(12)
F_MENU = font(14)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

d.text((24, 16), ar("الملصق · الصورة · الفيديو = رسالة واحدة + قائمة بالضغط المطوّل"),
       font=F_TITLE, fill=TEXT)

y = 54


def message(y, name, caption, kind):
    """يرسم رسالة واحدة: صورة · اسم · النص · الوسيط · الوقت."""
    d.ellipse([(24, y), (64, y + 40)], fill=ELEV, outline=(70, 78, 92))
    d.text((74, y + 2), ar(name), font=F_NAME, fill=GREEN)
    d.text((74, y + 22), ar(caption), font=F_BODY, fill=TEXT)

    mx = 74
    my = y + 44
    if kind == "sticker":
        d.rounded_rectangle([(mx, my), (mx + 150, my + 150)], radius=12, fill=BUBBLE)
        for k in range(150 - 8):
            t = k / 142
            d.line([(mx + 4, my + 4 + k), (mx + 146, my + 4 + k)],
                   fill=(int(70 + 60 * t), int(120 + 40 * t), int(90 + 60 * t)))
        d.text((mx + 58, my + 62), "STK", font=F_BODY, fill=TEXT)
        h = 150
    elif kind == "image":
        d.rounded_rectangle([(mx, my), (mx + 240, my + 160)], radius=12, fill=BUBBLE)
        for k in range(152):
            t = k / 152
            d.line([(mx + 4, my + 4 + k), (mx + 236, my + 4 + k)],
                   fill=(int(60 + 50 * t), int(80 + 70 * t), int(130 - 30 * t)))
        d.text((mx + 104, my + 72), "IMG", font=F_BODY, fill=TEXT)
        h = 160
    else:  # video
        d.rounded_rectangle([(mx, my), (mx + 300, my + 52)], radius=12, fill=ELEV)
        d.text((mx + 14, my + 10), "VIDEO", font=F_BODY, fill=TEXT)
        d.text((mx + 90, my + 12), "00:24  ·  3.2 MB", font=F_TINY, fill=MUTED)
        h = 52
    d.text((mx + (150 if kind == "sticker" else 240 if kind == "image" else 300) + 8,
            my + h - 12), "10:42", font=F_TINY, fill=DIM)
    return my + h + 26


y = message(y, "Ahmed", "شوف هذا الملصق 👇", "sticker")
y = message(y, "Ahmed", "صورة من عالمي اليوم", "image")
y = message(y, "Ahmed", "لقطة من الإضافة الجديدة", "video")

# ── قائمة الضغط المطوّل ──────────────────────────────────────────────────────
menu = [("رد", GREEN), ("نسخ النص", TEXT), ("إبلاغ", TEXT), ("حذف (٣ دقائق)", (235, 120, 120))]
mh = 34
my = y + 6
d.rounded_rectangle([(74, my - 8), (74 + 260, my - 8 + mh * len(menu) + 16)],
                    radius=12, fill=PANEL, outline=(60, 68, 82))
d.text((90, my), ar("بالضغط المطوّل على الملصق/الصورة/الفيديو:"), font=F_TINY, fill=DIM)
for i, (label, color) in enumerate(menu):
    ly = my + 24 + i * mh
    d.rounded_rectangle([(90, ly), (330, ly + 28)], radius=8, fill=BUBBLE)
    d.text((104, ly + 6), ar(label), font=F_MENU, fill=color)

d.text((24, H - 26), ar("ضغطة سريعة على الملصق = عرضه/إرجاعه للوحة · ضغطة مطوّلة = القائمة"),
       font=F_TINY, fill=BLUE)

out = "chat_media_actions_preview.png"
img.save(out)
print("✓", out, img.size)
