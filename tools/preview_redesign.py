#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة إعادة التصميم (الرئيسية · قائمة الغرف · غرفة الشات) — بلا بناء APK.

المرجع: وثيقة `AddonStation_handoff/SPEC.md`، والألوان والمقاسات من
`ui/theme/Spec.kt` (نفس القيم حرفياً)، والأصول من `res/drawable-nodpi`
(هيرو الحقيقي · أيقونات الغرف الحقيقية · أيقونة التطبيق · عملة AC).

المقاسات مرسومة كما في الوثيقة: خلفية تصميم 390dp عرضاً، حشو شاشة 16dp
(قائمة الغرف 12dp)، أهداف لمس ≥44dp، ولا يُرسم شريط تنقّل سفلي (فراغ محجوز فقط).

الإخراج: redesign_preview.png في جذر المستودع.
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res", "drawable-nodpi")
FONTS = os.path.join(ROOT, "android", "app", "src", "main", "res", "font")
OUT = os.path.join(ROOT, "redesign_preview.png")

# ── رموز الوثيقة (Spec.kt) ────────────────────────────────────────────────────
BG = "#0A0F1C"
SURFACE = "#121A2B"
SURFACE_HIGH = "#1A2438"
BAR = "#0E1526"
BORDER = "#223049"
TEXT = "#EAF0FA"
MUTED = "#9DB0CF"
CHIP_LABEL = "#C4D1E8"
BLUE = "#2E7BEF"
BLUE_LINK = "#6FA8FF"
GOLD = "#F0C25E"
GOLD_DEEP = "#1B1405"
GOLD_BORDER = "#8A6A22"
EPIC = "#9B6BFF"
GREEN = "#7FD99A"
FREE_GREEN = "#3C9D5A"
PIN_BORDER = "#5A4416"
HERO_SUB = "#D3DDF0"
LEGEND_TEXT = "#E8D5A3"
WHITE = "#FFFFFF"
DIM_TEXT = "#5C6F82"

F_PIXEL = os.path.join(FONTS, "pixel_regular.ttf")
F_NUM = os.path.join(FONTS, "roboto_medium_numbers.ttf")
F_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
F_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

PW, PH, GAP, MARGIN = 390, 844, 46, 46
TITLE_H, CAPTION_H = 96, 74
FOOT_H = 58
COLS = 3
W = MARGIN * 2 + PW * COLS + GAP * (COLS - 1)
H = TITLE_H + PH + CAPTION_H + FOOT_H

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def t(s):
    """نصّ عربي جاهز للعرض (تشكيل + اتجاه)."""
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False, pixel=False, num=False):
    if pixel:
        return ImageFont.truetype(F_PIXEL, size)
    if num:
        return ImageFont.truetype(F_NUM, size)
    return ImageFont.truetype(F_BOLD if bold else F_REG, size)


def rr(box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def txt(x, y, s, size=13, color=TEXT, bold=False, pixel=False, anchor="lm", num=False):
    d.text((x, y), s, font=font(size, bold, pixel, num), fill=color, anchor=anchor)


def ar(x, y, s, size=13, color=TEXT, bold=False, anchor="rm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def circle(box, fill):
    d.ellipse(box, fill=fill)


def paste_img(name, box, radius=None, circle_mask=False, cover=False):
    """يلصق صورة من drawable-nodpi داخل الصندوق (قصّ للوسط أو تغطية)."""
    path = os.path.join(RES, name)
    if not os.path.exists(path):
        return
    im = Image.open(path).convert("RGB")
    w, h = box[2] - box[0], box[3] - box[1]
    if cover:
        scale = max(w / im.width, h / im.height)
    else:
        scale = min(w / im.width, h / im.height)
    im = im.resize((max(1, int(im.width * scale)), max(1, int(im.height * scale))), Image.LANCZOS)
    left = (im.width - w) // 2
    top = 0 if cover else (im.height - h) // 2
    im = im.crop((left, top, left + w, top + h))
    if circle_mask or radius is not None:
        mask = Image.new("L", (w, h), 0)
        md = ImageDraw.Draw(mask)
        if circle_mask:
            md.ellipse((0, 0, w - 1, h - 1), fill=255)
        else:
            md.rounded_rectangle((0, 0, w - 1, h - 1), radius=radius, fill=255)
        img.paste(im, (box[0], box[1]), mask)
    else:
        img.paste(im, (box[0], box[1]))


def phone(x0, caption_ar, caption_en):
    """إطار هاتف 390×844 + عنوان تحته."""
    rr((x0 - 8, TITLE_H - 8, x0 + PW + 8, TITLE_H + PH + 8), 26, fill="#05080F", outline=BORDER)
    rr((x0, TITLE_H, x0 + PW, TITLE_H + PH), 18, fill=BG)
    py = TITLE_H + PH + 26
    ar(x0 + PW, py, caption_ar, size=15, color=GOLD, bold=True, anchor="ra")
    txt(x0 + PW, py + 22, caption_en, size=11, color=MUTED, anchor="ra")


def nav_slot(y_abs, x0):
    """فراغ شريط التنقّل السفلي (الوثيقة: يُحجز ولا يُرسم داخل هذه الشاشات)."""
    d.rectangle((x0, y_abs, x0 + PW, TITLE_H + PH), fill="#070B14")
    d.line((x0, y_abs, x0 + PW, y_abs), fill=BORDER, width=1)
    ar(x0 + PW // 2, y_abs + 23, "فراغ شريط التنقّل (يُرسم خارج هذه الشاشات)", size=10,
       color="#4C5A72", anchor="mm")


def bell(cx, cy, color=TEXT):
    """جرس الإشعارات (رسم خطّي)."""
    d.arc((cx - 8, cy - 9, cx + 8, cy + 7), 180, 360, fill=color, width=2)
    d.line((cx - 8, cy - 1, cx - 8, cy + 5), fill=color, width=2)
    d.line((cx + 8, cy - 1, cx + 8, cy + 5), fill=color, width=2)
    d.line((cx - 9, cy + 5, cx + 9, cy + 5), fill=color, width=2)
    d.ellipse((cx - 2, cy + 7, cx + 2, cy + 11), fill=color)


def magnifier(cx, cy, color=MUTED):
    d.ellipse((cx - 7, cy - 7, cx + 5, cy + 5), outline=color, width=2)
    d.line((cx + 4, cy + 4, cx + 9, cy + 9), fill=color, width=2)


def pin_icon(cx, cy, color=GOLD):
    """دبّوس (رسم خطّي)."""
    d.line((cx - 6, cy - 7, cx + 6, cy - 7), fill=color, width=2)
    d.line((cx - 4, cy - 7, cx - 4, cy + 1), fill=color, width=2)
    d.line((cx + 4, cy - 7, cx + 4, cy + 1), fill=color, width=2)
    d.line((cx - 6, cy + 1, cx + 6, cy + 1), fill=color, width=2)
    d.line((cx, cy + 1, cx, cy + 8), fill=color, width=2)


def people_icon(cx, cy, color=TEXT):
    d.ellipse((cx - 9, cy - 7, cx - 3, cy - 1), outline=color, width=2)
    d.arc((cx - 11, cy + 1, cx - 1, cy + 11), 180, 360, fill=color, width=2)
    d.ellipse((cx + 1, cy - 6, cx + 7, cy), outline=color, width=2)
    d.arc((cx - 1, cy + 2, cx + 9, cy + 12), 180, 360, fill=color, width=2)


def download_icon(cx, cy, color=WHITE):
    d.line((cx, cy - 8, cx, cy + 3), fill=color, width=2)
    d.line((cx - 5, cy - 2, cx, cy + 3), fill=color, width=2)
    d.line((cx + 5, cy - 2, cx, cy + 3), fill=color, width=2)
    d.line((cx - 7, cy + 7, cx + 7, cy + 7), fill=color, width=2)


def send_icon(cx, cy, color=WHITE):
    d.polygon(((cx - 7, cy - 7), (cx + 8, cy), (cx - 7, cy + 7), (cx - 3, cy)), fill=color)


def image_icon(cx, cy, color=TEXT):
    d.rounded_rectangle((cx - 9, cy - 8, cx + 9, cy + 8), radius=3, outline=color, width=2)
    d.ellipse((cx - 5, cy - 5, cx - 1, cy - 1), fill=color)
    d.line((cx - 6, cy + 5, cx - 1, cy, ), fill=color, width=2)
    d.line((cx - 1, cy, cx + 6, cy + 5), fill=color, width=2)


def smiley_icon(cx, cy, color=TEXT):
    d.ellipse((cx - 9, cy - 9, cx + 9, cy + 9), outline=color, width=2)
    d.ellipse((cx - 4, cy - 4, cx - 2, cy - 2), fill=color)
    d.ellipse((cx + 2, cy - 4, cx + 4, cy - 2), fill=color)
    d.arc((cx - 5, cy - 1, cx + 5, cy + 7), 20, 160, fill=color, width=2)


def back_icon(cx, cy, color=TEXT):
    d.line((cx + 5, cy - 8, cx - 4, cy), fill=color, width=2)
    d.line((cx - 4, cy, cx + 5, cy + 8), fill=color, width=2)


def badge(x, y, label, bg, fg):
    w = 12 + len(label) * 7
    rr((x, y, x + w, y + 20), 8, fill=bg)
    txt(x + w / 2, y + 10, label, size=10, color=fg, bold=True, anchor="mm")


# ═══════════════════════════ (١) الصفحة الرئيسية ═══════════════════════════
def draw_home(x0):
    """تُرسم الرئيسية على طبقة مستقلة ثم تُقصّ عند ارتفاع الشاشة (فهي قائمة تُمرَّر)."""
    global img, d
    phone(x0, "الصفحة الرئيسية", "Home · SPEC §2")
    outer_img, outer_d = img, d
    layer = Image.new("RGB", (PW, PH), BG)
    img, d = layer, ImageDraw.Draw(layer)
    o = 0
    y = 0

    # 2.1 الشريط العلوي (حشو 16/16/12)
    paste_img("app_icon.png", (o + 16, y + 16, o + 56, y + 56), radius=10)
    txt(o + 66, y + 36, "ADDONSTATION", size=12, color=TEXT, pixel=True)
    # قرص AC: 36 ارتفاعاً · نصف قطر 22 (رصيد قصير — كما يظهر عادةً)
    rr((o + 226, y + 18, o + 306, y + 54), 22, fill=SURFACE_HIGH)
    paste_img("coin_ac.png", (o + 232, y + 24, o + 256, y + 48), circle_mask=True)
    txt(o + 300, y + 36, "250", size=14, color=GOLD, bold=True, anchor="rm", num=True)
    # جرس 44 دائري
    circle((o + 314, y + 14, o + 358, y + 58), SURFACE)
    bell(o + 336, y + 36)
    y += 72

    # 2.2 التحية + البحث
    txt(o + 16, y + 8, "Good evening", size=13, color=MUTED, bold=True)
    txt(o + 16, y + 32, "Welcome back, [Username]", size=22, color=TEXT, bold=True)
    y += 56
    rr((o + 16, y, o + PW - 16, y + 48), 14, fill=SURFACE, outline=BORDER)
    magnifier(o + 34, y + 24)
    txt(o + 52, y + 24, "Search addons and worlds…", size=13, color=MUTED)
    y += 60

    # 2.3 بانر الهيرو (250 ارتفاعاً · نصف قطر 20)
    hx0, hy0, hx1, hy1 = o + 16, y, o + PW - 16, y + 250
    paste_img("hero_home.jpg", (hx0, hy0, hx1, hy1), radius=20, cover=True)
    # تدرّج داكن من الأسفل (bottom bg 92%)
    grad = Image.new("RGB", (hx1 - hx0, hy1 - hy0), BG)
    mask = Image.new("L", (hx1 - hx0, hy1 - hy0), 0)
    md = ImageDraw.Draw(mask)
    for i in range(hy1 - hy0):
        a = 0 if i < (hy1 - hy0) * 0.42 else int(255 * min(1.0, (i - (hy1 - hy0) * 0.42) / ((hy1 - hy0) * 0.36)))
        md.line((0, i, hx1 - hx0, i), fill=a)
    box = img.crop((hx0, hy0, hx1, hy1))
    box.paste(grad, (0, 0), mask)
    img.paste(box, (hx0, hy0))
    txt(hx0 + 16, hy0 + 116, "DREAM IT.", size=17, color=GOLD, pixel=True)
    txt(hx0 + 16, hy0 + 140, "BUILD IT.", size=17, color=GOLD, pixel=True)
    txt(hx0 + 16, hy0 + 164, "CREATE IT.", size=17, color=GOLD, pixel=True)
    txt(hx0 + 16, hy0 + 188, "Addons and worlds ready for your adventure", size=12, color=HERO_SUB)
    # زرّا الهيرو: 44 ارتفاعاً · نصف قطر 12
    rr((hx0 + 16, hy1 - 52, hx0 + 122, hy1 - 8), 12, fill=BLUE)
    txt(hx0 + 69, hy1 - 30, "Start now", size=13, color=WHITE, bold=True, anchor="mm")
    rr((hx0 + 132, hy1 - 52, hx0 + 262, hy1 - 8), 12, outline=GOLD, width=1)
    txt(hx0 + 197, hy1 - 30, "Join the family", size=13, color=GOLD, bold=True, anchor="mm")
    y = hy1 + 16

    # 2.4 بطاقتا الخطة
    rr((o + 16, y, o + PW - 16, y + 76), 16, fill=SURFACE, outline=BORDER)
    ar(o + PW - 30, y + 22, "خطتك الحالية", size=11, color=MUTED, anchor="rm")
    ar(o + PW - 30, y + 42, "مجّاني", size=15, color=TEXT, bold=True, anchor="rm")
    ar(o + PW - 30, y + 60, "محتوى مجاني · وصول أساسي", size=10, color=MUTED, anchor="rm")
    rr((o + 30, y + 16, o + 128, y + 60), 12, fill=SURFACE_HIGH)
    txt(o + 79, y + 38, "Upgrade", size=13, color=TEXT, bold=True, anchor="mm")
    y += 88

    # البطاقة الذهبية (تدرّج 135°)
    card = Image.new("RGB", (PW - 32, 84), GOLD_DEEP)
    cd = ImageDraw.Draw(card)
    for i in range(PW - 32):
        f = i / (PW - 32)
        cd.line((i, 0, i, 84), fill=(int(0x3A + (0x1B - 0x3A) * f), int(0x2A + (0x14 - 0x2A) * f), int(0x0B + (0x05 - 0x0B) * f)))
    img.paste(card, (o + 16, y))
    rr((o + 16, y, o + PW - 16, y + 84), 16, outline=GOLD_BORDER)
    txt(o + 30, y + 22, "JOIN LEGENDARY", size=13, color=GOLD, pixel=True)
    txt(o + 30, y + 44, "Legendary addons · Legendary worlds ·", size=11, color=LEGEND_TEXT)
    txt(o + 30, y + 60, "Priority access", size=11, color=LEGEND_TEXT)
    rr((o + PW - 116, y + 22, o + PW - 30, y + 62), 12, fill=GOLD)
    txt(o + PW - 73, y + 42, "View", size=13, color=GOLD_DEEP, bold=True, anchor="mm")
    y += 96

    # 2.6 شرائح التصنيف: 40 ارتفاعاً · نصف قطر 20
    cx = o + 16
    for label, selected in (("All", True), ("Addons", False), ("Worlds", False), ("Favorites", False)):
        w = 20 + len(label) * 7 + 12
        if selected:
            rr((cx, y, cx + w, y + 40), 20, fill=BLUE)
        else:
            rr((cx, y, cx + w, y + 40), 20, fill=SURFACE, outline=BORDER)
        txt(cx + w / 2, y + 20, label, size=13, color=WHITE if selected else CHIP_LABEL,
            bold=selected, anchor="mm")
        cx += w + 8
    y += 58

    # 2.7 رأس قسم + 2.8 أحدث الإضافات (بطاقة 150 · صورة 100)
    txt(o + 16, y, "LATEST ADDONS", size=13, color=GOLD, pixel=True)
    ar(o + PW - 16, y, "عرض الكل", size=12, color=BLUE_LINK, bold=True, anchor="rm")
    y += 22
    cx = o + 16
    for i, (label, bbg, bfg) in enumerate((("NEW", BLUE, WHITE), ("LEGENDARY", GOLD, GOLD_DEEP))):
        rr((cx, y, cx + 150, y + 168), 14, fill=SURFACE)
        paste_img("thumb_placeholder.jpg", (cx, y, cx + 150, y + 100), radius=14, cover=True)
        if label != "none":
            badge(cx + 6, y + 6, label, bbg, bfg)
        ar(cx + 142, y + 122, "إضافة %d" % (i + 1), size=13, color=TEXT, bold=True, anchor="rm")
        ar(cx + 142, y + 144, "بواسطة المبدع", size=11, color=MUTED, anchor="rm")
        cx += 162
    y += 184

    # 2.9 الأكثر تنزيلاً: صفوف 64 صورة
    txt(o + 16, y, "MOST DOWNLOADED", size=13, color=GOLD, pixel=True)
    y += 22
    for i in range(2):
        rr((o + 16, y, o + PW - 16, y + 84), 14, fill=SURFACE)
        paste_img("thumb_placeholder.jpg", (o + 26, y + 10, o + 90, y + 74), radius=10, cover=True)
        ar(o + 178, y + 30, "إضافة رائجة %d" % (i + 1), size=13, color=TEXT, bold=True, anchor="rm")
        ar(o + 178, y + 52, "بواسطة المبدع", size=11, color=MUTED, anchor="rm")
        rr((o + PW - 60, y + 20, o + PW - 26, y + 64), 12, fill=BLUE)
        download_icon(o + PW - 43, y + 42)
        y += 94
    # ── نهاية الطبقة: تُقصّ عند ارتفاع الشاشة ثم تُعاد إلى اللوحة
    clipped = layer.crop((0, 0, PW, PH))
    img, d = outer_img, outer_d
    img.paste(clipped, (x0, TITLE_H))
    # فراغ شريط التنقّل السفلي (يُحجز ولا يُرسم داخل الشاشة) — فوق القصّ
    nav_slot(TITLE_H + PH - 46, x0)


# ═══════════════════════════ (٢) قائمة الغرف ═══════════════════════════
def room_row(o, y, icon, name, preview, time_s, unread=0, name_ar=False):
    rr((o + 12, y, o + PW - 12, y + 56), 14, fill=SURFACE if unread else None)
    paste_img(icon, (o + 20, y + 6, o + 64, y + 50), circle_mask=True)
    if name_ar:
        ar(o + PW - 20 - 56, y + 20, name, size=14, color=TEXT, bold=True, anchor="rm")
        ar(o + PW - 20 - 56, y + 38, preview, size=11, color=MUTED, anchor="rm")
    else:
        txt(o + 72, y + 20, name, size=15, color=TEXT, bold=True)
        txt(o + 72, y + 38, preview, size=11, color=MUTED)
    txt(o + PW - 20, y + 18, time_s, size=11, color=MUTED, anchor="rm")
    if unread:
        circle((o + PW - 42, y + 30, o + PW - 20, y + 52), BLUE)
        txt(o + PW - 31, y + 41, str(unread), size=10, color=WHITE, bold=True, anchor="mm")


def draw_rooms(x0):
    phone(x0, "قائمة الغرف", "Chat rooms · SPEC §3")
    o = x0
    y = TITLE_H + 16

    txt(o + 16, y, "COMMUNITY", size=16, color=GOLD, pixel=True)
    ar(o + PW - 16, y, "كل الغرف", size=12, color=MUTED, bold=True, anchor="rm")
    rr((o + PW - 158, y - 18, o + PW - 78, y + 18), 22, fill=SURFACE_HIGH)
    paste_img("coin_ac.png", (o + PW - 152, y - 12, o + PW - 128, y + 12), circle_mask=True)
    txt(o + PW - 84, y, "250", size=13, color=GOLD, bold=True, anchor="rm", num=True)
    y += 26

    # لافتة القواعد
    rr((o + 16, y, o + PW - 16, y + 52), 14, fill=SURFACE, outline=BORDER)
    txt(o + 26, y + 18, "⚠  Community rules", size=12, color=CHIP_LABEL, bold=True)
    txt(o + 26, y + 36, "Violations may lead to a mute or a ban", size=11, color=MUTED)
    y += 66

    txt(o + 16, y, "MAIN ROOMS", size=12, color=GOLD, pixel=True)
    y += 24
    main = (
        ("ic_room_general.png", "General", "General chat for the community", "12:40", 0, False),
        ("ic_room_minecraft.png", "Minecraft", "Minecraft talk: worlds, addons", "12:31", 3, False),
        ("ic_room_creator.png", "Creator", "For creators and developers", "11:58", 0, False),
        ("ic_room_support.png", "Support", "Technical help and troubleshooting", "09:12", 0, False),
        ("ic_room_social.png", "Social Media Makers", "For content makers on social media", "Fri", 0, False),
    )
    for icon, name, prev, tm, unread, is_ar in main:
        room_row(o, y, icon, name, prev, tm, unread, is_ar)
        y += 60

    y += 8
    txt(o + 16, y, "LANGUAGE ROOMS", size=12, color=GOLD, pixel=True)
    y += 24
    langs = (
        ("ic_room_lang_ar.png", "العربية", "الغرفة العربية", "12:38", 5, True),
        ("ic_room_lang_fr.png", "Français", "Salon français", "12:20", 0, False),
        ("ic_room_lang_pt.png", "Português", "Sala em português", "11:44", 0, False),
        ("ic_room_lang_ru.png", "Русский", "Русская комната", "10:05", 2, False),
        ("ic_room_lang_zh.png", "中文", "中文聊天室", "09:31", 0, False),
    )
    for icon, name, prev, tm, unread, is_ar in langs:
        if y + 60 > PH - 60:
            break
        room_row(o, y, icon, name, prev, tm, unread, is_ar)
        y += 60

    nav_slot(TITLE_H + PH - 46, o)


# ═══════════════════════════ (٣) غرفة الشات ═══════════════════════════
def draw_chat(x0):
    phone(x0, "غرفة الشات", "Chat room · SPEC §4")
    o = x0

    # 4.1 الشريط العلوي (barSurface · حدّ سفلي 1dp · ارتفاع ≥64)
    top = TITLE_H
    d.rectangle((o, top, o + PW, top + 64), fill=BAR)
    back_icon(o + 32, top + 32)
    paste_img("ic_room_general.png", (o + 56, top + 12, o + 96, top + 52), circle_mask=True)
    txt(o + 106, top + 26, "General", size=16, color=TEXT, bold=True)
    txt(o + 106, top + 44, "12 online", size=12, color=GREEN, bold=True)
    pin_icon(o + PW - 96, top + 32)
    people_icon(o + PW - 52, top + 32)
    d.line((o, top + 64, o + PW, top + 64), fill=BORDER, width=1)

    # 4.2 شريط المثبّتات (goldDeepBg · حدّ pinnedBorder)
    py = top + 65
    d.rectangle((o, py, o + PW, py + 34), fill=GOLD_DEEP)
    txt(o + 16, py + 17, "Pinned messages ·", size=11, color=GOLD, bold=True)
    txt(o + 130, py + 17, "Welcome to the General room", size=11, color=GOLD)
    d.line((o, py + 34, o + PW, py + 34), fill=PIN_BORDER, width=1)

    y = py + 50

    # فاصل يوم
    d.line((o + 12, y, o + PW // 2 - 34, y), fill=BORDER)
    ar(o + PW // 2, y, "اليوم", size=11, color=MUTED, anchor="mm")
    d.line((o + PW // 2 + 34, y, o + PW - 12, y), fill=BORDER)
    y += 20

    # رسالة واردة: صورة 40 + [اسم بلون الدور + وقت 11] + اللقب + النص
    paste_img("ic_default_profile.jpg", (o + 12, y, o + 52, y + 40), circle_mask=True)
    txt(o + 64, y + 10, "TheKing", size=14, color=EPIC, bold=True)
    rr((o + 64, y + 24, o + 146, y + 42), 11, fill=EPIC)
    txt(o + 105, y + 33, "EPIC", size=10, color=WHITE, bold=True, anchor="mm")
    # الوقت في نهاية الرسالة (طلب المالك السابق: أسفل النص على حافة الرسالة)
    txt(o + 64, y + 56, "Welcome everyone to the General room", size=14, color=TEXT)
    txt(o + 64 + d.textlength("Welcome everyone to the General room", font=font(14)) + 14, y + 59,
        "12:38", size=9, color=DIM_TEXT)
    y += 80

    # اقتباس رد: خلفية surface + شريط أزرق 3dp · ثم نصّ الردّ تحته (بلا تداخل)
    rr((o + 64, y, o + 300, y + 34), 6, fill=SURFACE)
    d.rectangle((o + 64, y, o + 67, y + 34), fill=BLUE)
    txt(o + 74, y + 17, "↩ Replying to Sara · Where do I start?", size=10, color=MUTED)
    y += 46
    txt(o + 64, y, "Start from Store and tap Download.", size=14, color=TEXT)
    y += 26

    # تفاعلات: أقراص 28 · نصف قطر 14
    # أقراص التفاعل كما كانت: تفاعلك بخلفية مضيئة وإطار أخضر ورقم أخضر
    rr((o + 64, y, o + 112, y + 26), 13, fill=SURFACE_HIGH, outline=BORDER)
    txt(o + 78, y + 13, "♥", size=12, color=MUTED, anchor="mm")
    txt(o + 100, y + 13, "2", size=10, color=MUTED, bold=True, anchor="mm")
    rr((o + 120, y, o + 168, y + 26), 13, fill="#123322", outline=GREEN, width=2)
    txt(o + 134, y + 13, "★", size=12, color=GREEN, anchor="mm")
    txt(o + 156, y + 13, "1", size=10, color=GREEN, bold=True, anchor="mm")
    y += 42

    # صورتان: واردة صورة 230×130 (radius 12) + رسالتي فقاعة زرقاء
    paste_img("thumb_placeholder.jpg", (o + 64, y, o + 294, y + 130), radius=12, cover=True)
    y += 146

    # رسالتي: فقاعة زرقاء يميناً · نصف قطر 16/16/4/16 · عرض ≤260
    bw = 236
    bx = o + PW - 12 - bw
    rr((bx, y, o + PW - 12, y + 58), 16, fill=BLUE)
    rr((o + PW - 28, y + 42, o + PW - 12, y + 58), 4, fill=BLUE)
    d.rectangle((bx + 6, y + 10, o + PW - 22, y + 48), fill=BLUE)
    ar(o + PW - 26, y + 18, "شكراً لك! سأجرّبها الآن.", size=14, color=WHITE, anchor="rm")
    # وقت رسالتي في نهاية الرسالة (عند حافتها، على الخلفية الداكنة)
    txt(bx - 8, y + 54, "12:41", size=10, color=DIM_TEXT, anchor="rm")
    y += 72

    # مؤشّر الكتابة (12sp باهت · حشو بداية 50)
    ar(o + PW - 50, y, "Sara تكتب…", size=11, color=MUTED, anchor="rm")

    # 4.4 شريط الكتابة (barSurface · حدّ علوي 1dp · حشو 8/8/18)
    cy = PH - 62
    d.rectangle((o, cy, o + PW, PH), fill=BAR)
    d.line((o, cy, o + PW, cy), fill=BORDER, width=1)
    circle((o + 8, cy + 9, o + 52, cy + 53), SURFACE)
    image_icon(o + 30, cy + 31)
    circle((o + 58, cy + 9, o + 102, cy + 53), SURFACE)
    smiley_icon(o + 80, cy + 31)
    rr((o + 108, cy + 9, o + 330, cy + 53), 22, fill=SURFACE, outline=BORDER)
    txt(o + 124, cy + 31, "Message General", size=13, color=MUTED)
    circle((o + PW - 52, cy + 9, o + PW - 8, cy + 53), BLUE)
    send_icon(o + PW - 30, cy + 31)


# ═══════════════════════════ الرسم والكتابة ═══════════════════════════
ar(W // 2, 40, "إعادة تصميم ثلاث شاشات — وثيقة AddonStation_handoff", size=22, color=TEXT, bold=True, anchor="mm")
txt(W // 2, 68, "Home · Chat rooms · Chat room   —   dark theme only   —   tokens from ui/theme/Spec.kt", size=13, color=MUTED, anchor="mm")

draw_home(MARGIN)
draw_rooms(MARGIN + PW + GAP)
draw_chat(MARGIN + (PW + GAP) * 2)

ar(W // 2, H - 30, "لا يُرسم شريط التنقّل السفلي داخل هذه الشاشات (فراغ محجوز فقط) · أهداف اللمس ≥44dp · مقاسات على خلفية 390dp",
   size=12, color=MUTED, anchor="mm")

img.save(OUT)
print("saved", OUT, img.size)
