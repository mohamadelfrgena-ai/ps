#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة إصلاحات الشات الأربع.

المصدر: ChatViewModel.send (إدراج متفائل) · ChatScreen.kt (دمج النص مع الملصق/الوسائط،
الرد بضغطة بسيطة، ضغطة سريعة على الملصق) · ChatSheets.kt (لوحة الملصقات مع تحديد المُعاد).

اللوحات:
  1) الرسالة تظهر فوراً بعد الضغط على إرسال (باهتة حتى تأكيد الخادم).
  2) نص + ملصق (أو وسائط) في رسالة واحدة — لا رسالتين.
  3) ضغطة سريعة على ملصقي ⇒ يعود إلى لوحة الملصقات محدَّداً، جاهزاً للإرسال.
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "chat_tweaks_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SURFACE, ELEV, ELEV2, BORDER, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#212D3D", "#263240", "#334152"
TEXT, MUTED, DIM, GREEN, ONGREEN, GOLD, BLUE = (
    "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F", "#FFD479", "#6FB8FF")

PW, PH, TOP, GAP, MARGIN = 420, 880, 150, 30, 40

img = Image.new("RGB", (MARGIN * 2 + PW * 3 + GAP * 2, TOP + PH + 120), BG)
d = ImageDraw.Draw(img)


def t(s):
    """تشكيل عربي + ترتيب ثنائي — مرة واحدة فقط لكل نص."""
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded(box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def text(x, y, s, size=13, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def latin(x, y, s, size=12, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), s, font=font(size, bold), fill=color, anchor=anchor)


def phone(x0, caption):
    x1 = x0 + PW
    rounded((x0, TOP, x1, TOP + PH), 26, fill=SURFACE, outline=BORDER2, width=2)
    d.rectangle((x0 + 3, TOP + 3, x1 - 3, TOP + 40), fill=SURFACE)
    latin(x0 + 20, TOP + 21, "2:37", 12, TEXT)
    latin(x1 - 20, TOP + 21, "100%", 11, MUTED, anchor="rm")
    d.text((x0 + PW // 2, TOP + 66), t(caption), font=font(15, True), fill=GREEN, anchor="mm")
    return x0, x1


def avatar(x, y, color, size=34):
    rounded((x, y, x + size, y + size), size // 2, fill=color)
    d.ellipse((x + size // 2 - 6, y + size // 2 - 8, x + size // 2 + 6, y + size // 2 + 4), fill=BG)


def sticker_tile(box, tone="#2C3A4B", label=None, border=None):
    rounded(box, 12, fill=tone, outline=border, width=3 if border else 1)
    x0, y0, x1, y1 = box
    if label:
        d.text(((x0 + x1) // 2, (y0 + y1) // 2), label, font=font(22, True), fill=MUTED, anchor="mm")


def input_bar(x0, x1, y, value=""):
    rounded((x0 + 14, y, x1 - 14, y + 52), 26, fill=ELEV, outline=BORDER)
    if value:
        text(x1 - 32, y + 26, value, 13, TEXT, anchor="rm")
    d.ellipse((x0 + 96, y + 12, x0 + 132, y + 48), fill=ELEV2)
    d.ellipse((x0 + 150, y + 12, x0 + 186, y + 48), fill=ELEV2)
    rounded((x0 + 20, y + 10, x0 + 76, y + 42), 16, fill=GREEN)
    d.text((x0 + 48, y + 26), "↑", font=font(17, True), fill=ONGREEN, anchor="mm")


# ── 1) الرسالة تظهر فوراً ─────────────────────────────────────────────
x0, x1 = phone(MARGIN, "الرسالة تظهر فوراً بعد الإرسال")
y = TOP + 96
for name, color, body, when in (("sara_2026", "#3C5A78", "وصلتني النسخة الأولى، الحمد لله", "2:36"),
                                ("khaled_dev", "#6A4A2E", "تمام، أرسلها لك الآن", "2:37")):
    avatar(x0 + 16, y, "#3C5A78")
    text(x1 - 60, y + 10, name, 12, TEXT, True, "rm")
    latin(x1 - 16, y + 10, when, 10, DIM, anchor="rm")
    box = (x0 + 30, y + 26, x1 - 18, y + 58)
    rounded(box, 12, fill=ELEV)
    text(x1 - 30, y + 42, body, 13, TEXT, anchor="rm")
    y += 74

# الرسالة المُرسَلة الآن: تظهر باهتة لحظة إرسالها (قبل رد الخادم)
box = (x0 + 60, y, x1 - 18, y + 34)
rounded(box, 12, fill=ELEV2)
text(x1 - 30, y + 17, "موافق، أرسلت لك الملف", 13, "#7C8A9C", anchor="rm")
avatar(x1 - 34, y - 46, "#6A4A2E")
d.ellipse((x0 + 74, y + 10, x0 + 88, y + 24), outline=GOLD, width=2)
text(x1 - 18, y + 48, "تظهر لحظة الضغط على الإرسال، باهتة حتى تأكيد الخادم", 10, GOLD, anchor="rm")
y += 78
input_bar(x0, x1, TOP + PH - 76)

# ── 2) نص + ملصق = رسالة واحدة ───────────────────────────────────────
x0, x1 = phone(MARGIN + PW + GAP, "النص والملصق رسالة واحدة")
y = TOP + 96
avatar(x1 - 56, y, "#3C5A78")
text(x0 + 60, y + 10, "Mrforest", 12, TEXT, True, "lm")
latin(x0 + 20, y + 10, "2:37", 10, DIM, anchor="lm")

# فقاعة واحدة: سطر النص ثم الملصق داخلها
bx0, bx1 = x0 + 16, x1 - 50
rounded((bx0, y + 24, bx1, y + 148), 14, fill=ELEV)
text(bx1 - 12, y + 44, "شكراً، وهذا الملصق لك", 13, TEXT, anchor="rm")
sticker_tile((bx0 + 12, y + 60, bx0 + 92, y + 138))
y += 162
text(x1 - 18, y, "رسالة واحدة تحمل النص والملصق معاً", 11, MUTED, anchor="rm")
y += 26

# نفس الشيء مع وسائط (صوت/فيديو)
rounded((bx0, y, bx1, y + 96), 14, fill=ELEV)
text(bx1 - 12, y + 20, "وده تسجيل صوتي", 13, TEXT, anchor="rm")
rounded((bx0 + 12, y + 34, bx0 + 40, y + 82), 12, fill=BLUE)
d.polygon([(bx0 + 22, y + 48), (bx0 + 22, y + 68), (bx0 + 34, y + 58)], fill=BG)
d.rounded_rectangle((bx0 + 46, y + 54, bx1 - 14, y + 62), radius=4, fill=DIM)
y += 110
text(x1 - 18, y, "النص + الصوت/الفيديو ⇒ رسالة واحدة أيضاً", 11, MUTED, anchor="rm")
input_bar(x0, x1, TOP + PH - 76)

# ── 3) ضغطة سريعة على ملصقي ⇒ يعود للوحة الملصقات ───────────────────
x0, x1 = phone(MARGIN + (PW + GAP) * 2, "ضغطة سريعة على ملصقي")
y = TOP + 96
avatar(x1 - 56, y, "#6A4A2E")
text(x0 + 60, y + 10, "Mrforest", 12, TEXT, True, "lm")
sticker_tile((x0 + 16, y + 26, x0 + 96, y + 104))
d.rounded_rectangle((x0 + 106, y + 34, x1 - 20, y + 96), radius=12, fill=ELEV)
d.rounded_rectangle((x0 + 112, y + 62, x0 + 148, y + 66), radius=2, fill=GOLD)
text(x1 - 28, y + 48, "الملصق رجع مكانه", 12, TEXT, anchor="rm")
y += 124

# لوحة الملصقات
sheet = (x0 + 8, y, x1 - 8, TOP + PH - 86)
rounded(sheet, 20, fill=SURFACE, outline=BORDER2, width=2)
rounded(((sheet[0] + sheet[2]) // 2 - 22, sheet[1] + 10,
         (sheet[0] + sheet[2]) // 2 + 22, sheet[1] + 15), 3, fill=BORDER2)
ty = sheet[1] + 40
tab_w = (x1 - x0 - 56) // 2
rounded((x0 + 24, ty, x0 + 24 + tab_w, ty + 36), 18, fill=ELEV2)
text(x0 + 24 + tab_w // 2, ty + 18, "الملصقات", 12, GREEN, True, "mm")
rounded((x0 + 32 + tab_w, ty, x1 - 24, ty + 36), 18, fill=BG)
text(x0 + 32 + tab_w + tab_w // 2, ty + 18, "الإيموجي", 12, MUTED, anchor="mm")
ty += 48
text(x1 - 26, ty + 8, "Memes", 11, MUTED, anchor="rm")
ty += 22
tones = ["#2C3A4B", "#33465C", "#3B4A3A", "#4A3B4A", "#2C3A4B", "#3B4A5C",
         "#33465C", "#4A4233", "#3B4A3A", "#33334A", "#2C3A4B", "#3B4A5C"]
cell = (x1 - x0 - 52) // 4
for i, tone in enumerate(tones):
    cx = x0 + 24 + (i % 4) * (cell + 4)
    cy = ty + (i // 4) * (74 + 6)
    sticker_tile((cx, cy, cx + cell, cy + 74), tone, border=GREEN if i == 5 else None)
rounded((x0 + 24, TOP + PH - 172, x1 - 24, TOP + PH - 126), 24, fill=ELEV)
text(x1 - 38, TOP + PH - 149, "الملصق المُعاد محدَّد بالأخضر — جاهز للإرسال", 12, TEXT, anchor="rm")
rounded((x0 + 24, TOP + PH - 112, x1 - 24, TOP + PH - 66), 24, fill=GREEN)
text(x1 - 38, TOP + PH - 89, "إرسال الملصق", 12, ONGREEN, True, anchor="rm")

# ── التذييل ───────────────────────────────────────────────────────────
fy = TOP + PH + 44
notes = [
    ("ضغط مطوّل على الرسالة", "قائمة الإبلاغ والحذف — كما هي"),
    ("ضغطة بسيطة على الرسالة", "رد عليها مباشرة"),
    ("ضغطة على ملصقك", "يشيله ويعيده للوحة الملصقات"),
    ("ملصق غيرك", "يفتح بالحجم الكامل فقط"),
]
col = (img.width - MARGIN * 2) // len(notes)
for i, (head, body) in enumerate(notes):
    cx = img.width - MARGIN - col * i - col // 2
    d.ellipse((cx - 5, fy - 5, cx + 5, fy + 5), fill=GREEN)
    text(cx - 16, fy, head, 14, TEXT, True, "rm")
    text(cx - 16, fy + 24, body, 12, MUTED, anchor="rm")

img.save(OUT)
print("saved", OUT, img.size)
