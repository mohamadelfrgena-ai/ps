#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة طلبات المالك الأربعة (بدون بناء APK) — تُبنى من الكود نفسه:

    1) أيقونات الغرف كلها من مجلد icons (13 أيقونة) — الشريط العلوي في الصورة.
    2) اختيار الملصق ⇒ معاينته **داخل مربع الإرسال** + زر ✕ أحمر على يمينها،
       ثم كتابة نص إن أراد ثم إرسال  →  ChatScreen.kt (pendingSticker)
    3) الرسالة بعد الإرسال: **النص وتحته الملصق مباشرة** — رسالة واحدة.
    4) شاشة «شروط الدخول للشات» بمقاسات ملائمة للشاشة + أيقونة اسم المستخدم
       خارج مربع الاسم وعلى جانبه (مثل أيقونة البريد) — CommunityScreens.kt
       و SettingsScreen.kt

الإخراج: owner_requests_preview.png
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res", "drawable-nodpi")
OUT = os.path.join(ROOT, "owner_requests_preview.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SURFACE, ELEV, ELEV2, BORDER, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#212D3D", "#263240", "#334152"
TEXT, MUTED, DIM, GREEN, ONGREEN, GOLD, BLUE, RED = (
    "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F", "#FFD479", "#6FB8FF", "#FF5A5A")

PW, PH, MARGIN, GAP = 380, 780, 30, 26
HEADER = 210          # شريط أيقونات الغرف أعلى الصورة
TOP = HEADER + 40     # بداية الهواتف
FOOTER = 130

img = Image.new("RGB", (MARGIN * 2 + PW * 4 + GAP * 3, TOP + PH + FOOTER), BG)
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


def phone(x0, caption):
    x1 = x0 + PW
    rounded((x0, TOP, x1, TOP + PH), 26, fill=SURFACE, outline=BORDER2, width=2)
    latin(x0 + 18, TOP + 22, "2:37", 12, TEXT)
    latin(x1 - 18, TOP + 22, "71%", 11, MUTED, anchor="rm")
    # شريط الغرفة (أيقونة + اسم)
    rounded((x0 + 12, TOP + 44, x1 - 12, TOP + 86), 14, fill=ELEV)
    d.text(((x0 + x1) // 2, TOP + 65), t(caption), font=font(14, True), fill=GREEN, anchor="mm")
    line((x0, TOP + 96, x1, TOP + 96), BORDER)
    return x0, x1


def line(box, color, width=1):
    d.line(box, fill=color, width=width)


def avatar(x, y, color, size=34):
    rounded((x, y, x + size, y + size), size // 2, fill=color)
    d.ellipse((x + size // 2 - 6, y + size // 2 - 9, x + size // 2 + 6, y + size // 2 + 3), fill=BG)
    d.ellipse((x + size // 2 - 10, y + size // 2 + 4, x + size // 2 + 10, y + size // 2 + 22), fill=BG)


def sticker_tile(box, tone="#2C3A4B", label=None, radius=12):
    rounded(box, radius, fill=tone, outline=BORDER)
    x0, y0, x1, y1 = box
    if label:
        d.text(((x0 + x1) // 2, (y0 + y1) // 2), label, font=font(20, True), fill=MUTED, anchor="mm")


def send_button(cx, cy, color=GREEN):
    d.ellipse((cx - 22, cy - 22, cx + 22, cy + 22), fill=color)
    d.polygon([(cx - 8, cy - 9), (cx + 9, cy), (cx - 8, cy + 9)], fill=ONGREEN if color == GREEN else DIM)


# ══════════════════════════════════════════════════════════════════════
# الشريط العلوي: أيقونات الغرف الـ13 كما صارت في التطبيق
# ══════════════════════════════════════════════════════════════════════
text(MARGIN, 34, "١) أيقونات الغرف — من مجلد icons (١٣ أيقونة)", 20, GREEN, True)
text(MARGIN, 62, "كل غرفة بأيقونتها: عامة · ماينكرافت · المبدعون · الدعم · صنّاع المحتوى · خدمات النقدية · ٧ أعلام لغات",
     13, MUTED)

ROOMS = [
    ("ic_room_general", "عام"), ("ic_room_minecraft", "ماينكرافت"), ("ic_room_creator", "المبدعون"),
    ("ic_room_support", "الدعم"), ("ic_room_social", "صنّاع المحتوى"), ("ic_room_cash", "خدمات النقدية"),
    ("ic_room_lang_ar", "العربية"), ("ic_room_lang_en", "English"), ("ic_room_lang_es", "Español"),
    ("ic_room_lang_fr", "Français"), ("ic_room_lang_pt", "Português"), ("ic_room_lang_ru", "Русский"),
    ("ic_room_lang_zh", "中文"), ("ic_room_lang_tr", "Türkçe"),
]
cell_w = (img.width - MARGIN * 2) // len(ROOMS)
for i, (name, label) in enumerate(ROOMS):
    cx = MARGIN + i * cell_w + cell_w // 2
    rounded((cx - 32, 84, cx + 32, 148), 14, fill=ELEV, outline=BORDER)
    path = os.path.join(RES, name + ".png")
    if os.path.exists(path):
        src = Image.open(path).convert("RGBA").resize((52, 52), Image.LANCZOS)
        img.paste(src, (cx - 26, 90), src)
    text(cx, 158, label, 11, MUTED, anchor="mm")
text(MARGIN, 182, "الأيقونة التركية لم تكن في المجلد فتُركت كما هي — أرسلها وأضيفها", 11, DIM)
line((MARGIN, 202, img.width - MARGIN, 202), BORDER)

# ══════════════════════════════════════════════════════════════════════
# ٢) الملصق في مربع الإرسال (معاينة + ✕ أحمر)
# ══════════════════════════════════════════════════════════════════════
x0, x1 = phone(MARGIN, "٢) الملصق ينتظر في مربع الإرسال")
y = TOP + 116
avatar(x0 + 16, y, "#3C5A78")
latin(x0 + 60, y + 8, "sara_2026", 12, TEXT, True)
latin(x1 - 18, y + 10, "2:36", 10, DIM, anchor="rm")
rounded((x0 + 58, y + 28, x1 - 20, y + 58), 12, fill=ELEV)
text(x1 - 32, y + 43, "جرّب الملصقات الجديدة", 12, TEXT, anchor="rm")
y += 76

# لوحة الملصقات (نصف مفتوحة) فوق مربع الإرسال
sheet = (x0 + 8, y, x1 - 8, y + 210)
rounded(sheet, 20, fill=ELEV, outline=BORDER2, width=2)
rounded((x0 + PW // 2 - 22, sheet[1] + 8, x0 + PW // 2 + 22, sheet[1] + 13), 3, fill=BORDER2)
latin(x0 + 24, sheet[1] + 34, "الملصقات", 12, GREEN, True)
tones = ["#2C3A4B", "#33465C", "#3B4A3A", "#4A3B4A", "#2C3A4B", "#3B4A5C", "#33465C", "#4A4233"]
cw = (PW - 60) // 4
for i, tone in enumerate(tones):
    cx = x0 + 24 + (i % 4) * (cw + 4)
    cy = sheet[1] + 56 + (i // 4) * 78
    sticker_tile((cx, cy, cx + cw, cy + 70), tone)

# مربع الإرسال: معاينة الملصق داخله + ✕ أحمر على يمينها
by = TOP + PH - 176
rounded((x0 + 12, by, x1 - 12, by + 96), 14, fill=ELEV, outline=BORDER)
sticker_tile((x0 + 24, by + 12, x0 + 116, by + 84), "#3B4A5C")
# زر ✕ الأحمر على أقصى يمين المعاينة
d.ellipse((x0 + 100, by + 4, x0 + 126, by + 30), fill=RED)
d.line((x0 + 107, by + 11, x0 + 119, by + 23), fill="#FFFFFF", width=3)
d.line((x0 + 119, by + 11, x0 + 107, by + 23), fill="#FFFFFF", width=3)
text(x1 - 26, by + 40, "اكتب نصاً مع الملصق إن أردت، ثم اضغط إرسال", 10, MUTED, anchor="rm")
# صف الحقل + الأزرار
rounded((x0 + 20, by + 104, x1 - 66, by + 148), 22, fill=ELEV2, outline=BORDER)
d.ellipse((x0 + 36, by + 112, x0 + 68, by + 144), fill=ELEV2, outline=BORDER)      # ＋ إرفاق
text(x0 + 60, by + 126, "اكتب رسالة…", 11, DIM, anchor="lm")
d.ellipse((x1 - 128, by + 112, x1 - 96, by + 144), fill=ELEV2, outline=BORDER)     # 😊
send_button(x1 - 52, by + 128, GREEN)
text(x1 - 20, TOP + PH - 18, "زر الإرسال يعمل بالملصق وحده (بلا نصّ)", 10, GREEN, anchor="rm")

# ══════════════════════════════════════════════════════════════════════
# ٣) الرسالة بعد الإرسال: النص وتحته الملصق (رسالة واحدة)
# ══════════════════════════════════════════════════════════════════════
x0, x1 = phone(MARGIN + PW + GAP, "٣) النص فوقه والملصق تحته")
y = TOP + 116
avatar(x0 + 16, y, "#6A4A2E")
latin(x0 + 60, y + 8, "Mrforest", 12, TEXT, True)
latin(x1 - 18, y + 10, "2:37", 10, DIM, anchor="rm")
bubble = (x0 + 58, y + 28, x0 + 310, y + 210)
rounded(bubble, 14, fill=ELEV)
text(bubble[2] - 14, y + 46, "شكراً، وهذا الملصق لك", 13, TEXT, anchor="rm")
sticker_tile((bubble[0] + 12, y + 62, bubble[0] + 132, y + 198), "#3B4A5C", label="🙂")
text(x1 - 20, y + 226, "رسالة واحدة: النص أولاً وتحته الملصق مباشرة", 11, GOLD, anchor="rm")

y += 258
avatar(x0 + 16, y, "#3C5A78")
latin(x0 + 60, y + 8, "sara_2026", 12, TEXT, True)
rounded((x0 + 58, y + 28, x0 + 250, y + 58), 12, fill=ELEV)
text(x0 + 232, y + 43, "وصلت، حلو مرتب 👌", 12, TEXT, anchor="rm")

by = TOP + PH - 76
rounded((x0 + 20, by, x1 - 66, by + 44), 22, fill=ELEV2, outline=BORDER)
send_button(x1 - 52, by + 22, ELEV2)
text(x1 - 20, TOP + PH - 18, "بعد الإرسال يعود مربع الإرسال فارغاً", 10, DIM, anchor="rm")

# ══════════════════════════════════════════════════════════════════════
# ٤) شاشة شروط الدخول بمقاسات ملائمة للشاشة
# ══════════════════════════════════════════════════════════════════════
x0, x1 = phone(MARGIN + (PW + GAP) * 2, "٤) شروط الدخول للشات")
y = TOP + 132
text(x0 + PW // 2, y, "⚠️ شروط وقواعد المجتمع", 17, TEXT, True, "mm")
y += 34
card = (x0 + 18, y, x1 - 18, TOP + PH - 150)
rounded(card, 16, fill=ELEV, outline=BORDER)
cy = card[1] + 18
rules = ["احترام جميع الأعضاء", "لا إهانات أو مضايقات", "لا عنصرية أو تمييز",
         "لا روابط أو إعلانات مشبوهة", "لا تشارك معلوماتك الشخصية", "لا محتوى غير لائق",
         "لا سياسة", "قد تؤدي الانتهاكات إلى كتم أو حظر"]
for r in rules:
    text(card[2] - 16, cy, "• " + r, 12, MUTED, anchor="rm")
    cy += 30
# مؤشر تمرير: البطاقة تتمرّر والزرّان ثابتان
d.rounded_rectangle((card[2] - 8, card[1] + 14, card[2] - 4, card[3] - 14), radius=2, fill=BORDER2)
text(x0 + 22, card[3] + 16, "البطاقة تأخذ المساحة المتبقية وتتمرّر", 10, DIM)
rounded((x0 + 18, TOP + PH - 124, x1 - 18, TOP + PH - 78), 13, fill=GREEN)
text(x0 + PW // 2, TOP + PH - 101, "قبول والدخول", 13, ONGREEN, True, "mm")
rounded((x0 + 18, TOP + PH - 66, x1 - 18, TOP + PH - 20), 13, fill=ELEV2, outline=BORDER2)
text(x0 + PW // 2, TOP + PH - 43, "رفض والعودة للرئيسية", 13, TEXT, "mm")

# ══════════════════════════════════════════════════════════════════════
# ٥) أيقونة اسم المستخدم خارج المربع (مثل أيقونة البريد)
# ══════════════════════════════════════════════════════════════════════
x0, x1 = phone(MARGIN + (PW + GAP) * 3, "الإعدادات › الحساب")
y = TOP + 116
rounded((x0 + 14, y, x1 - 14, y + 74), 16, fill=ELEV, outline=BORDER)
avatar(x0 + 26, y + 20, "#3DDC84")
latin(x0 + 72, y + 26, "Mrforest", 14, TEXT, True)
latin(x0 + 72, y + 48, "silver@addonstation", 11, MUTED)
y += 90
text(x0 + 18, y, "الحساب", 13, MUTED, True)
y += 24

# صف الاسم: الأيقونة **خارج** المربع وعلى جانبه
rounded((x0 + 14, y, x1 - 14, y + 96), 16, fill=ELEV, outline=BORDER)
icon_box = (x0 + 26, y + 26, x0 + 60, y + 60)
path = os.path.join(RES, "ic_user.png")
if os.path.exists(path):
    src = Image.open(path).convert("RGBA").resize((34, 34), Image.LANCZOS)
    img.paste(src, (icon_box[0], icon_box[1]), src)
else:
    rounded(icon_box, 11, fill=ELEV2)
field = (x0 + 72, y + 18, x1 - 28, y + 74)
rounded(field, 12, fill=SURFACE, outline=BORDER2)
latin(x0 + 84, y + 28, "اسم المستخدم", 10, BLUE)
latin(x0 + 84, y + 48, "Mrforest", 13, TEXT)
text(x1 - 22, y + 108, "الأيقونة صارت خارج المربع — مثل أيقونة البريد تماماً", 10, GREEN, anchor="rm")

y += 130
rounded((x0 + 14, y, x1 - 14, y + 60), 16, fill=ELEV, outline=BORDER)
path = os.path.join(RES, "ic_set_email.png")
if os.path.exists(path):
    src = Image.open(path).convert("RGBA").resize((34, 34), Image.LANCZOS)
    img.paste(src, (x0 + 26, y + 13), src)
else:
    rounded((x0 + 26, y + 13, x0 + 60, y + 47), 11, fill=ELEV2)
latin(x0 + 72, y + 22, "البريد الإلكتروني", 13, TEXT)
latin(x0 + 72, y + 42, "mrforest@example.com", 11, MUTED)
y += 76
rounded((x0 + 14, y, x1 - 14, y + 46), 13, fill=GREEN)
text(x0 + PW // 2, y + 23, "حفظ الاسم", 13, ONGREEN, True, "mm")

# ══════════════════════════════════════════════════════════════════════
# التذييل
# ══════════════════════════════════════════════════════════════════════
fy = TOP + PH + 34
notes = [
    ("الملصق", "يُختار ⇒ معاينة في مربع الإرسال ⇒ ✕ أحمر لإلغائه"),
    ("الإرسال", "نص + ملصق في رسالة واحدة (النص فوقه والملصق تحته)"),
    ("الشروط", "مقاسات تتبع الشاشة: البطاقة تتمرّر والزرّان ثابتان"),
    ("اسم المستخدم", "أيقونته خارج المربع على جانبه — مثل أيقونة البريد"),
]
col = (img.width - MARGIN * 2) // len(notes)
for i, (head, body) in enumerate(notes):
    cx = img.width - MARGIN - col * i - col // 2
    d.ellipse((cx - 5, fy + 4, cx + 5, fy + 14), fill=GREEN)
    text(cx - 16, fy + 9, head, 14, TEXT, True, "rm")
    text(cx - 16, fy + 34, body, 11, MUTED, anchor="rm")

img.save(OUT)
print("saved", OUT, img.size)
