#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة شكل الرسالة الجديد في الشات (مستوحى من صورة المالك):

  ١) القديم (أُلغي): الوقت بجانب الاسم في الأعلى + صفّ ثابت (👍 ❤️ ＋) تحت كل رسالة.
  ٢) الجديد: **الوقت في نهاية الرسالة** (أسفل النص على حافة الفقاعة، صغير وباهت)
     + **بسولات تفاعل دائرية** (المُختارة بخلفية مضيئة وإطار أخضر ورقم أخضر)
     + زر «إضافة تفاعل» （＋ 😊） في آخر الصفّ.
  ٣) الضغط المطوّل: صفّ الرموز السريعة — الرموز التي أضفتها تظهر مضاءة بالأخضر.

المصدر: ChatScreen.kt (MessageTime · ReactionPill · AddReactionPill) · ChatSheets.kt.
الإخراج: reactions_preview.png
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "reactions_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SURFACE, ELEV, ELEV2, BORDER, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#212D3D", "#263240", "#334152"
TEXT, MUTED, DIM, GREEN, ONGREEN, RED = (
    "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F", "#FF5A5A")
GREENGLOW = "#123324"

PW, PH, MARGIN, GAP = 400, 760, 30, 26
TOP = 130
FOOTER = 120

img = Image.new("RGB", (MARGIN * 2 + PW * 3 + GAP * 2, TOP + PH + FOOTER), BG)
d = ImageDraw.Draw(img)


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded(box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def text(x, y, s, size=13, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def latin(x, y, s, size=12, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), s, font=font(size, bold), fill=color, anchor=anchor)


def emoji(x, y, s, size=13, anchor="lm"):
    d.text((x, y), s, font=font(size), fill=TEXT, anchor=anchor)


def phone(x0, caption, color=GREEN):
    x1 = x0 + PW
    rounded((x0, TOP, x1, TOP + PH), 26, fill=SURFACE, outline=BORDER2, width=2)
    latin(x0 + 18, TOP + 22, "2:37", 12, TEXT)
    latin(x1 - 18, TOP + 22, "71%", 11, MUTED, anchor="rm")
    rounded((x0 + 12, TOP + 44, x1 - 12, TOP + 86), 14, fill=ELEV)
    d.text(((x0 + x1) // 2, TOP + 65), t(caption), font=font(14, True), fill=color, anchor="mm")
    d.line((x0, TOP + 96, x1, TOP + 96), fill=BORDER)
    return x0, x1


def avatar(x, y, color="#3C5A78", size=34):
    rounded((x, y, x + size, y + size), size // 2, fill=color)
    d.ellipse((x + size // 2 - 6, y + size // 2 - 9, x + size // 2 + 6, y + size // 2 + 3), fill=BG)
    d.ellipse((x + size // 2 - 10, y + size // 2 + 4, x + size // 2 + 10, y + size // 2 + 22), fill=BG)


def name_row(x0, x1, y, name, when=None, col="#3C5A78"):
    avatar(x0 + 16, y, col)
    latin(x0 + 60, y + 8, name, 12, TEXT, True)
    if when:                       # الشكل القديم: الوقت بجانب الاسم
        latin(x1 - 18, y + 10, when, 10, DIM, anchor="rm")


def bubble(x0, x1, y, body, when=None, fill=ELEV, tail=True):
    """فقاعة رسالة: النص فوق، و**الوقت في نهايتها** أسفل النص على الحافة."""
    bx0, bx1 = x0 + 58, x1 - 22
    h = 62 if when else 32
    rounded((bx0, y, bx1, y + h), 12, fill=fill)
    if tail:                       # ذيل الفقاعة (أُسلوب واتساب في الصورة)
        d.polygon([(bx0 + 2, y + 4), (bx0 + 2, y + 22), (bx0 - 8, y + 16)], fill=fill)
    if when:
        text(bx1 - 12, y + 18, body, 12, TEXT, anchor="rm")
        latin(bx1 - 12, y + 46, when, 10, DIM, anchor="rm")
    else:
        text(bx1 - 12, y + h // 2, body, 12, TEXT, anchor="rm")
    return y + h


def pill(x, y, emo, count, mine=False, add=False, w=None):
    width = w or (46 if add else 58)
    rounded((x, y, x + width, y + 26), 13,
            fill=GREENGLOW if mine else ELEV,
            outline=GREEN if mine else BORDER,
            width=2 if mine else 1)
    if add:
        emoji(x + 14, y + 13, "😊", 12, "mm")
        d.text((x + width - 13, y + 13), "＋", font=font(12), fill=MUTED, anchor="mm")
    else:
        emoji(x + 15, y + 13, emo, 13, "mm")
        latin(x + width - 12, y + 13, str(count), 11, GREEN if mine else MUTED, anchor="mm")


def old_row(x, y):
    for i, e in enumerate("👍❤️"):
        emoji(x + 8 + i * 26, y + 10, e, 12, "mm")
    d.text((x + 74, y + 10), "＋", font=font(12), fill=DIM, anchor="mm")


def cross(box):
    x0, y0, x1, y1 = box
    d.line((x0, y0, x1, y1), fill=RED, width=6)
    d.line((x1, y0, x0, y1), fill=RED, width=6)


# ───────────────────────────────────────────────────────────── العنوان
text(MARGIN, 32, "شكل الرسالة الجديد — الوقت في نهايتها + بسولات التفاعل", 20, GREEN, True)
text(MARGIN, 60, "١) الوقت صار في نهاية الرسالة (أسفل النص على حافة الفقاعة) بدل سطر الاسم", 13, MUTED)
text(MARGIN, 82, "٢) البسولة التي تفاعلتَ بها: خلفية مضيئة + إطار أخضر + رقم أخضر · وآخر الصفّ زر （＋ 😊）", 12, MUTED)
text(MARGIN, 104, "٣) لا صفّ （👍 ❤️ ＋） ثابت تحت كل رسالة", 12, DIM)
d.line((MARGIN, 124, img.width - MARGIN, 124), fill=BORDER)

# ─────────────────────────────── ١) الشكل القديم (مُلغى)
x0, x1 = phone(MARGIN, "الشكل القديم (أُلغي)", RED)
y = TOP + 116
name_row(x0, x1, y, "sara_2026", "2:35")            # الوقت بجانب الاسم
y = bubble(x0, x1, y + 32, "وصلتني النسخة الأولى") + 8
old_row(x0 + 58, y)
y += 44
name_row(x0, x1, y, "khaled_dev", "2:36", "#6A4A2E")
y = bubble(x0, x1, y + 32, "تمام، أرسلها لك الآن") + 8
old_row(x0 + 58, y)
cross((x0 + 40, TOP + 130, x1 - 40, TOP + 500))
text(x0 + PW // 2, TOP + PH - 62, "الوقت في الأعلى + صفّ رموز ثابت", 11, RED, True, "mm")
text(x0 + PW // 2, TOP + PH - 40, "تحت كل رسالة حتى بلا تفاعلات", 11, DIM, "mm")
text(x0 + PW // 2, TOP + PH - 20, "⇒ أُلغي بالكامل", 11, DIM, "mm")

# ─────────────────────────────── ٢) الشكل الجديد
x0, x1 = phone(MARGIN + PW + GAP, "الشكل الجديد")
y = TOP + 116
name_row(x0, x1, y, "sara_2026")
y = bubble(x0, x1, y + 32, "الملصق الجديد حلو مرتب 👌", when="2:36") + 6
pill(x0 + 58, y, "🔥", 3, mine=True)
pill(x0 + 122, y, "❤️", 2, mine=True)
pill(x0 + 186, y, "😂", 1)
pill(x0 + 250, y, None, 0, add=True)
y += 40
name_row(x0, x1, y, "khaled_dev", None, "#6A4A2E")
y = bubble(x0, x1, y + 32, "مبروك 🔥", when="2:37") + 6
pill(x0 + 58, y, "🔥", 3)
y += 40
name_row(x0, x1, y, "nour_99", None, "#4A3B6A")
bubble(x0, x1, y + 32, "رسالة بلا تفاعلات ⇒ لا صفّ تحتها", when="2:37")
text(x0 + PW // 2, TOP + PH - 46, "الوقت： أسفل النص على حافة الفقاعة", 11, GREEN, True, "mm")
text(x0 + PW // 2, TOP + PH - 26, "اضغط البسولة لإضافة/إزالة تفاعلك", 11, DIM, "mm")

# ─────────────────────────────── ٣) الضغط المطوّل
x0, x1 = phone(MARGIN + (PW + GAP) * 2, "ضغطة مطوّلة ⇒ الرموز السريعة")
y = TOP + 116
name_row(x0, x1, y, "sara_2026")
y = bubble(x0, x1, y + 32, "الملصق الجديد حلو مرتب 👌", when="2:36") + 6
pill(x0 + 58, y, "🔥", 3, mine=True)
sheet = (x0 + 8, TOP + 320, x1 - 8, TOP + PH - 20)
rounded(sheet, 20, fill=ELEV, outline=BORDER2, width=2)
rounded((x0 + PW // 2 - 22, sheet[1] + 8, x0 + PW // 2 + 22, sheet[1] + 13), 3, fill=BORDER2)
sy = sheet[1] + 34
for i, e in enumerate("👍❤️😂🔥🎉😮"):
    cx = x0 + 34 + i * 56
    mine = e in "👍🔥"
    rounded((cx - 22, sy, cx + 22, sy + 44), 22,
            fill=GREENGLOW if mine else ELEV2,
            outline=GREEN if mine else None,
            width=2 if mine else 0)
    emoji(cx, sy + 22, e, 18, "mm")
sy += 62
for label, tint in (("رد", TEXT), ("نسخ النص", TEXT), ("إبلاغ", TEXT),
                    ("تثبيت", TEXT), ("حذف رسالتي", RED), ("ملف صاحب الرسالة", TEXT)):
    d.ellipse((x0 + 34, sy + 8, x0 + 52, sy + 26), outline=tint, width=2)
    text(x0 + 66, sy + 17, label, 13, tint)
    sy += 38

# ───────────────────────────────────────────────────────────── التذييل
fy = TOP + PH + 34
notes = [
    ("الوقت", "نهاية الرسالة — صغير 9.5sp وباهت"),
    ("البسولة", "كبسولة دائرية: رمز + عدد (26dp)"),
    ("تفاعلك", "خلفية مضيئة + إطار أخضر + رقم أخضر"),
    ("＋ 😊", "إضافة تفاعل · بلا صفّ ثابت"),
]
col = (img.width - MARGIN * 2) // len(notes)
for i, (head, body) in enumerate(notes):
    cx = img.width - MARGIN - col * i - col // 2
    d.ellipse((cx - 5, fy + 4, cx + 5, fy + 14), fill=GREEN)
    text(cx - 16, fy + 9, head, 14, TEXT, True, "rm")
    text(cx - 16, fy + 34, body, 11, MUTED, anchor="rm")

img.save(OUT)
print("saved", OUT, img.size)
