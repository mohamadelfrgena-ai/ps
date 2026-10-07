#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة شاشات الملف الشخصي الثلاث — بلا بناء APK.

المرجع: حقيبة `AddonStation_profiles` ووثيقتها `SPEC_PROFILES.md`، والألوان
والمقاسات من `ui/theme/Spec.kt` (نفس القيم حرفياً)، والأصول من
`res/drawable-nodpi` (بانر التصميم · أفاتار افتراضي · عملة AC).

  A «ملفي» (§3) · B «ملف عضو» (§4) · C «ملفي كما يراه الآخرون» (§5)

الإخراج: profile_screens_preview.png في جذر المستودع.
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res", "drawable-nodpi")
FONTS = os.path.join(ROOT, "android", "app", "src", "main", "res", "font")
OUT = os.path.join(ROOT, "profile_screens_preview.png")

# ── رموز وثيقة الملف الشخصي (Spec.kt) ────────────────────────────────────────
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
GREEN = "#7FD99A"
DANGER = "#FF8791"
DANGER_BORDER = "#B3414B"
DASH_BORDER = "#2E3C58"
DOT_START = (140, 242, 173)
DOT_END = (47, 179, 92)
WHITE = "#FFFFFF"

F_PIXEL = os.path.join(FONTS, "pixel_regular.ttf")
F_NUM = os.path.join(FONTS, "roboto_medium_numbers.ttf")
F_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
F_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

PW = 390                       # عرض الشاشة (dp)
GAP, MARGIN = 42, 44
TITLE_H, CAPTION_H, FOOT_H = 104, 96, 74
COLS = 3
W = MARGIN * 2 + PW * COLS + GAP * (COLS - 1)

# الصور تُرسم أولاً على طبقات (لاستخراج أطول شاشة) ثم تُركَّب في النهاية
screen_a = Image.new("RGB", (PW, 1400), BG)
screen_b = Image.new("RGB", (PW, 1400), BG)
screen_c = Image.new("RGB", (PW, 1400), BG)


def t(s):
    """نصّ عربي جاهز للعرض (تشكيل + اتجاه)."""
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False, pixel=False, num=False):
    if pixel:
        return ImageFont.truetype(F_PIXEL, size)
    if num:
        return ImageFont.truetype(F_NUM, size)
    return ImageFont.truetype(F_BOLD if bold else F_REG, size)


def txt(d, x, y, s, size=13, color=TEXT, bold=False, pixel=False, anchor="lm", num=False):
    d.text((x, y), s, font=font(size, bold, pixel, num), fill=color, anchor=anchor)


def ar(d, x, y, s, size=13, color=TEXT, bold=False, anchor="rm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def rr(d, box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def dashed_rr(d, box, r, color=DASH_BORDER, dash=7, gap=5, width=1):
    """إطار متقطّع كما يرسمه PathEffect.dashPathEffect في التطبيق."""
    x0, y0, x1, y1 = box
    step = dash + gap
    x = x0 + r
    while x < x1 - r:
        d.line((x, y0, min(x + dash, x1 - r), y0), fill=color, width=width)
        d.line((x, y1, min(x + dash, x1 - r), y1), fill=color, width=width)
        x += step
    y = y0 + r
    while y < y1 - r:
        d.line((x0, y, x0, min(y + dash, y1 - r)), fill=color, width=width)
        d.line((x1, y, x1, min(y + dash, y1 - r)), fill=color, width=width)
        y += step
    d.arc((x0, y0, x0 + 2 * r, y0 + 2 * r), 180, 270, fill=color, width=width)
    d.arc((x1 - 2 * r, y0, x1, y0 + 2 * r), 270, 360, fill=color, width=width)
    d.arc((x0, y1 - 2 * r, x0 + 2 * r, y1), 90, 180, fill=color, width=width)
    d.arc((x1 - 2 * r, y1 - 2 * r, x1, y1), 0, 90, fill=color, width=width)


def paste_img(img, name, box, radius=None, circle_mask=False, cover=False):
    path = os.path.join(RES, name)
    if not os.path.exists(path):
        return
    im = Image.open(path).convert("RGB")
    w, h = box[2] - box[0], box[3] - box[1]
    scale = max(w / im.width, h / im.height) if cover else min(w / im.width, h / im.height)
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


def banner(img, d, y, more=False, has_image=True):
    """§2.1 البانر: 150 · عرض كامل · صورة (crop) · تدرّج شفاف 40% ← خلفية 90%."""
    h = 150
    if has_image:
        paste_img(img, "profile_banner_default.jpg", (0, y, PW, y + h), cover=True)
    else:
        rr(d, (0, y, PW, y + h), 0, fill=SURFACE_HIGH)
    layer = Image.new("RGBA", (PW, h), (0, 0, 0, 0))
    ld = ImageDraw.Draw(layer)
    r, g, b = 10, 15, 28
    for i in range(h):
        a = 0 if i < h * 0.40 else int(0.90 * 255 * ((i - h * 0.40) / (h * 0.60)))
        ld.line((0, i, PW, i), fill=(r, g, b, a))
    img.paste(Image.alpha_composite(img.crop((0, y, PW, y + h)).convert("RGBA"), layer).convert("RGB"), (0, y))
    # زرّ الرجوع (يسار) و«المزيد» (يمين · شاشة B فقط): 44×44 · bg 60% · أيقونة 22
    for side, icon in (("back", "back"), ("more", "more") if more else (None, None)):
        if side is None:
            continue
        cx = 10 + 22 if side == "back" else PW - 10 - 22
        circle_bg(img, cx, y + 10 + 22, 22, (10, 15, 28, 153))
        if icon == "back":
            d.line((cx + 5, y + 32, cx - 4, y + 40), fill=WHITE, width=2)
            d.line((cx - 4, y + 40, cx + 5, y + 48), fill=WHITE, width=2)
        else:
            for dy in (-6, 0, 6):
                d.ellipse((cx - 2, y + 40 + dy - 2, cx + 2, y + 40 + dy + 2), fill=WHITE)
    return y + h


def circle_bg(img, cx, cy, r, color_rgba):
    """دائرة بشفافية (طبقة RGBA مؤقتة)."""
    layer = Image.new("RGBA", (2 * r, 2 * r), (0, 0, 0, 0))
    ImageDraw.Draw(layer).ellipse((0, 0, 2 * r - 1, 2 * r - 1), fill=color_rgba)
    img.paste(layer.convert("RGB"), (cx - r, cy - r), layer.split()[3])


def avatar(img, d, x, y, status="online"):
    """§2.2 الأفاتار 96 بحدّ 5dp بلون الخلفية + نقطة حالة 30 في الرُّكن."""
    size = 96
    rr(d, (x, y, x + size, y + size), 48, fill=SURFACE_HIGH)
    paste_img(img, "ic_default_profile.jpg", (x, y, x + size, y + size), circle_mask=True)
    # حدّ 5dp بلون الخلفية (دائري · مرسوم على المحيط الداخلي)
    for i in range(5):
        d.ellipse((x + i, y + i, x + size - 1 - i, y + size - 1 - i), outline=BG)
    # نقطة الحالة: توهّج + تدرّج + حدّ 5dp داخلي
    cx, cy, r = x + size - 1 - 15, y + size - 1 - 15, 15
    glow = Image.new("RGBA", (2 * (r + 16), 2 * (r + 16)), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glow)
    for k in range(16, 0, -1):
        a = int(120 * (1 - k / 16) ** 1.5)
        gd.ellipse((16 - k, 16 - k, 16 + 2 * r + k, 16 + 2 * r + k), outline=(140, 242, 173, a))
    img.paste(glow.convert("RGB"), (cx - r - 16, cy - r - 16), glow.split()[3])
    d.ellipse((cx - r - 2, cy - r - 2, cx + r + 2, cy + r + 2), outline=(140, 242, 173))
    # القرص: تدرّج شعاعي بسيط (حلقات من الفاتح إلى الغامق)
    steps = [GOLD] if status == "away" else ([DANGER] if status == "hidden" else None)
    for k in range(r, 0, -1):
        f = k / r
        if steps:
            col = steps[0]
        else:
            col = tuple(int(DOT_START[i] + (DOT_END[i] - DOT_START[i]) * (1 - f)) for i in range(3))
        d.ellipse((cx - k, cy - k, cx + k, cy + k), fill=col)
    for i in range(5):
        d.ellipse((cx - r + i, cy - r + i, cx + r - i, cy + r - i), outline=BG)


def chip(d, x, y, label, bg, fg, size=11):
    w = (len(label) + 2) * 7 + 8
    h = 20
    rr(d, (x, y, x + w, y + h), 8, fill=bg)
    txt(d, x + w / 2, y + h / 2 + 1, label, size=size, color=fg, bold=True, anchor="mm")
    return w


def chip_ar(d, x_right, y, label, bg, fg, size=11):
    """شريحة عربية (يُحسب عرضها من النصّ المُشكَّل)."""
    shown = t(label)
    f = font(size, True)
    w = int(d.textlength(shown, font=f)) + 18
    rr(d, (x_right - w, y, x_right, y + 20), 8, fill=bg)
    d.text((x_right - w / 2, y + 10), shown, font=f, fill=fg, anchor="mm")
    return w


def card(d, y, h, pad=14):
    rr(d, (16, y, PW - 16, y + h), 16, fill=SURFACE, outline=BORDER)
    return y + h


def small_caps(d, x, y, label, color=GOLD):
    txt(d, x, y, label, size=11, color=color, bold=True, pixel=False)


def fill_level(d, y, fraction):
    """شريط تقدّم المستوى: تدرّج أزرق ← أزرق فاتح، ويمتلئ من اليمين (RTL)."""
    total = int((PW - 60) * fraction)
    for i in range(total):
        f = i / max(1, total)
        col = (int(46 + (111 - 46) * f), int(123 + (168 - 123) * f), int(239 + (255 - 239) * f))
        d.line((PW - 30 - i, y + 62, PW - 30 - i, y + 70), fill=col)


def stat_column(d, cx, y, value, label):
    txt(d, cx, y, value, size=20, color=TEXT, bold=True, anchor="mm", num=True)
    ar(d, cx, y + 22, label, size=11, color=MUTED, bold=True, anchor="mm")


# ═══════════════════════ الشاشة A — «ملفي» (§3) ═══════════════════════
def draw_a():
    img = Image.new("RGB", (PW, 1400), BG)
    d = ImageDraw.Draw(img)
    y = banner(img, d, 0)                                     # ١) البانر (رجوع وحده)
    av = 96
    ay = y - 46
    avatar(img, d, 16, ay)                                    # ٢) أفاتار 96 يتجاوز 46
    rr(d, (PW - 16 - 132, y - 44, PW - 16, y), 12, fill=BLUE)  # «تعديل الملف» محاذى لأسفل الأفاتار
    ar(d, PW - 16 - 66, y - 22, "تعديل الملف", size=14, color=WHITE, bold=True, anchor="mm")
    y = y + (av - 46) + 12

    # ٣) بطاقة الهوية
    ar(d, PW - 16, y + 8, "محمد الفرجاني", size=22, color=TEXT, bold=True)
    y += 34
    ar(d, PW - 16, y + 6, "@mohamad", size=13, color=MUTED, bold=True)
    ar(d, PW - 118, y + 6, " · متصل الآن", size=13, color=GREEN, bold=True)
    y += 24
    xr = PW - 16
    xr -= chip_ar(d, xr, y, "عضو مميّز", GOLD, GOLD_DEEP) + 6
    xr -= chip_ar(d, xr, y, "مبدع", BLUE, WHITE) + 6
    xr -= chip_ar(d, xr, y, "المستوى 7", SURFACE_HIGH, CHIP_LABEL) + 6
    chip_ar(d, xr, y, "ADMIN", GOLD, GOLD_DEEP)
    y += 30
    rr(d, (16, y, PW - 16, y + 38), 12, fill=BAR)
    ar(d, PW - 28, y + 19, "متاح للتعاون في الإضافات", size=13, color=CHIP_LABEL, anchor="rm")
    y += 50

    # مدخل الشاشة C
    eye_w = int(d.textlength(t("عرض كما يراه الآخرون"), font=font(13, True)))
    ex = PW - 16 - eye_w - 22
    ar(d, PW - 16, y + 8, "عرض كما يراه الآخرون", size=13, color=BLUE_LINK, bold=True)
    d.ellipse((ex, y + 3, ex + 20, y + 19), outline=BLUE_LINK)
    d.ellipse((ex + 7, y + 8, ex + 13, y + 14), fill=BLUE_LINK)
    y += 30

    # ٤) بطاقة المحفظة (تحت الهوية مباشرة)
    card(d, y, 88)
    paste_img(img, "coin_ac.png", (30, y + 22, 74, y + 66), radius=22)
    d.text((92, y + 28), t("محفظة AC · الرصيد الحالي"), font=font(11, True), fill=MUTED)
    txt(d, 92, y + 50, "1240", size=22, color=GOLD, bold=True, num=True)
    d.text((92, y + 74), t("12 يوم متتالٍ"), font=font(11, True), fill=MUTED)
    rr(d, (PW - 16 - 128, y + 22, PW - 16 - 14, y + 66), 12, fill=GOLD)
    ar(d, PW - 16 - 71, y + 44, "مكافأة اليوم +10", size=13, color=GOLD_DEEP, bold=True, anchor="mm")
    y += 100

    # ٥) شريط الإحصاءات (ثلاثة أعمدة بفواصل 1dp)
    card(d, y, 70, pad=0)
    dx = 16 + (PW - 32) / 3
    for i, (v, l) in enumerate((("128", "الأصدقاء"), ("47", "التنزيلات"), ("9", "المفضلة"))):
        stat_column(d, 16 + dx * i + dx / 2, y + 26, v, l)
        if i:
            d.line((16 + dx * i, y + 14, 16 + dx * i, y + 56), fill=BORDER)
    y += 82

    # ٦) بطاقة المستوى
    card(d, y, 78)
    small_caps(d, PW - 30, y + 20, "المستوى والسمعة", GOLD)
    ar(d, PW - 30, y + 52, "المستوى 7 · 40 من 100 للمستوى التالي", size=13, color=TEXT, bold=True)
    rr(d, (30, y + 62, PW - 30, y + 70), 4, fill=SURFACE_HIGH)
    fill_level(d, y, 0.40)                                    # يمتلئ من اليمين (RTL)
    y += 90

    # ٧) بطاقة النبذة (+ «عضو منذ»)
    card(d, y, 132)
    small_caps(d, PW - 30, y + 22, "نبذة عني", GOLD)
    ar(d, PW - 30, y + 52, "أبني أدوات وإضافات لماين كرافت —", size=14, color=CHIP_LABEL)
    ar(d, PW - 30, y + 74, "وأشارك كل جديد في المجتمع.", size=14, color=CHIP_LABEL)
    d.line((30, y + 98, PW - 30, y + 98), fill=BORDER)
    small_caps(d, PW - 30, y + 108, "عضو منذ", GOLD)
    ar(d, PW - 30, y + 122, "12 آذار 2024", size=14, color=TEXT, bold=True)
    y += 144

    # ٨) شبكة 2×2 (فراغ 10)
    tw = (PW - 32 - 10) / 2
    tiles = [("المفضلة", BLUE_LINK), ("التنزيلات", BLUE_LINK), ("طلباتي", BLUE_LINK), ("محتواي", BLUE_LINK)]
    for i, (label, col) in enumerate(tiles):
        col_i, row_i = i % 2, i // 2
        x0 = 16 + col_i * (tw + 10)
        y0 = y + row_i * 66
        rr(d, (x0, y0, x0 + tw, y0 + 56), 14, fill=SURFACE, outline=BORDER)
        ar(d, x0 + tw / 2, y0 + 28, label, size=13, color=TEXT, bold=True, anchor="mm")
    y += 66 * 2 - 10 + 12

    # ٩) تسجيل الخروج
    rr(d, (16, y, PW - 16, y + 48), 12, outline=DANGER_BORDER)
    ar(d, PW / 2, y + 24, "تسجيل الخروج", size=14, color=DANGER, bold=True, anchor="mm")
    return img, y + 48 + 24


# ═══════════════════════ الشاشة B — «ملف عضو» (§4) ═══════════════════════
def draw_b():
    img = Image.new("RGB", (PW, 1400), BG)
    d = ImageDraw.Draw(img)
    y = banner(img, d, 0, more=True)
    avatar(img, d, 16, y - 46, status="away")
    y = y + (96 - 46) + 12

    ar(d, PW - 16, y + 8, "يوسف", size=22, color=TEXT, bold=True)
    y += 34
    ar(d, PW - 16, y + 6, "@yusuf", size=13, color=MUTED, bold=True)
    ar(d, PW - 92, y + 6, " · بعيد", size=13, color=GOLD, bold=True)
    y += 24
    xr = PW - 16
    xr -= chip_ar(d, xr, y, "مبدع", BLUE, WHITE) + 6
    chip_ar(d, xr, y, "المستوى 3", SURFACE_HIGH, CHIP_LABEL)
    y += 42

    # صفّ الأزرار: «مراسلة العضو» مخفيّ بقرار المالك ⇒ زرّ الصداقة وحده
    rr(d, (16, y, PW - 16, y + 48), 12, outline=BLUE)
    ar(d, PW / 2, y + 24, "طلب صداقة", size=14, color=BLUE_LINK, bold=True, anchor="mm")
    y += 60

    # الإحصاءات · المستوى · النبذة
    card(d, y, 70, pad=0)
    dx = (PW - 32) / 3
    for i, (v, l) in enumerate((("36", "الأصدقاء"), ("12", "التنزيلات"), ("4", "المفضلة"))):
        stat_column(d, 16 + dx * i + dx / 2, y + 26, v, l)
        if i:
            d.line((16 + dx * i, y + 14, 16 + dx * i, y + 56), fill=BORDER)
    y += 82

    card(d, y, 78)
    small_caps(d, PW - 30, y + 20, "المستوى والسمعة", GOLD)
    ar(d, PW - 30, y + 52, "المستوى 3 · 55 من 100 للمستوى التالي", size=13, color=TEXT, bold=True)
    rr(d, (30, y + 62, PW - 30, y + 70), 4, fill=SURFACE_HIGH)
    fill_level(d, y, 0.55)
    y += 90

    card(d, y, 110)
    small_caps(d, PW - 30, y + 22, "نبذة عني", GOLD)
    ar(d, PW - 30, y + 52, "هذا العضو لم يكتب نبذة بعد.", size=14, color=CHIP_LABEL)
    d.line((30, y + 76, PW - 30, y + 76), fill=BORDER)
    small_caps(d, PW - 30, y + 86, "عضو منذ", GOLD)
    ar(d, PW - 30, y + 100, "03 أيار 2025", size=14, color=TEXT, bold=True)
    y += 122

    # بطاقة السلامة (ثلاثة صفوف 52 · فواصل 1dp)
    card(d, y, 156, pad=0)
    rows = [("كتم هذا العضو", TEXT), ("حظر العضو", DANGER), ("إبلاغ", DANGER)]
    for i, (label, col) in enumerate(rows):
        yy = y + i * 52
        ar(d, PW - 30, yy + 26, label, size=14, color=col, bold=True)
        if i:
            d.line((30, yy, PW - 30, yy), fill=BORDER)
    y += 168

    # بطاقة الإشراف (للأدمن/المشرف وحدهما)
    card(d, y, 178, pad=0)
    small_caps(d, PW - 30, y + 22, "الإشراف", GOLD)
    rows = [("كتم العضو 24 ساعة", TEXT), ("حظر العضو من الشات", DANGER), ("حذف رسالة (أدوات الإشراف)", BLUE_LINK)]
    for i, (label, col) in enumerate(rows):
        yy = y + 40 + i * 44
        ar(d, PW - 30, yy + 22, label, size=14, color=col, bold=True)
        if i:
            d.line((30, yy, PW - 30, yy), fill=BORDER)
    return img, y + 178 + 24


# ═══════════════════ الشاشة C — «كما يراه الآخرون» (§5) ═══════════════════
def draw_c():
    img = Image.new("RGB", (PW, 1400), BG)
    d = ImageDraw.Draw(img)
    y = banner(img, d, 0)
    avatar(img, d, 16, y - 46)
    rr(d, (PW - 16 - 132, y - 44, PW - 16, y), 12, fill=BLUE)
    ar(d, PW - 16 - 66, y - 22, "تعديل الملف", size=14, color=WHITE, bold=True, anchor="mm")
    y = y + (96 - 46) + 12

    ar(d, PW - 16, y + 8, "محمد الفرجاني", size=22, color=TEXT, bold=True)
    y += 34
    ar(d, PW - 16, y + 6, "@mohamad", size=13, color=MUTED, bold=True)
    ar(d, PW - 118, y + 6, " · متصل الآن", size=13, color=GREEN, bold=True)
    y += 24
    xr = PW - 16
    xr -= chip_ar(d, xr, y, "عضو مميّز", GOLD, GOLD_DEEP) + 6
    xr -= chip_ar(d, xr, y, "مبدع", BLUE, WHITE) + 6
    chip_ar(d, xr, y, "المستوى 7", SURFACE_HIGH, CHIP_LABEL)
    y += 42

    card(d, y, 70, pad=0)
    dx = (PW - 32) / 3
    for i, (v, l) in enumerate((("128", "الأصدقاء"), ("47", "التنزيلات"), ("9", "المفضلة"))):
        stat_column(d, 16 + dx * i + dx / 2, y + 26, v, l)
        if i:
            d.line((16 + dx * i, y + 14, 16 + dx * i, y + 56), fill=BORDER)
    y += 82

    card(d, y, 78)
    small_caps(d, PW - 30, y + 20, "المستوى والسمعة", GOLD)
    ar(d, PW - 30, y + 52, "المستوى 7 · 40 من 100 للمستوى التالي", size=13, color=TEXT, bold=True)
    rr(d, (30, y + 62, PW - 30, y + 70), 4, fill=SURFACE_HIGH)
    fill_level(d, y, 0.40)
    y += 90

    card(d, y, 132)
    small_caps(d, PW - 30, y + 22, "نبذة عني", GOLD)
    ar(d, PW - 30, y + 52, "أبني أدوات وإضافات لماين كرافت —", size=14, color=CHIP_LABEL)
    ar(d, PW - 30, y + 74, "وأشارك كل جديد في المجتمع.", size=14, color=CHIP_LABEL)
    d.line((30, y + 98, PW - 30, y + 98), fill=BORDER)
    small_caps(d, PW - 30, y + 108, "عضو منذ", GOLD)
    ar(d, PW - 30, y + 122, "12 آذار 2024", size=14, color=TEXT, bold=True)
    y += 144

    # بطاقة المحفظة المتقطّعة: عملة 36 بشفافية 50% · عنوان · سطر «مخفية عن المستخدمين»
    box = (16, y, PW - 16, y + 68)
    rr(d, box, 16, fill=SURFACE)
    dashed_rr(d, box, 16)
    coin = Image.open(os.path.join(RES, "coin_ac.png")).convert("RGB").resize((36, 36), Image.LANCZOS)
    dim = Image.blend(coin, Image.new("RGB", (36, 36), SURFACE), 0.5)
    img.paste(dim, (30, y + 16))
    ar(d, PW - 78, y + 26, "محفظة AC", size=14, color=CHIP_LABEL, bold=True)
    ar(d, PW - 78, y + 48, "مخفية عن بقية المستخدمين", size=12, color=MUTED)
    return img, y + 68 + 24


heights = {}
for key, fn in (("A", draw_a), ("B", draw_b), ("C", draw_c)):
    im, end = fn()
    im = im.crop((0, 0, PW, end))
    heights[key] = im
PH = max(im.height for im in heights.values())
H = TITLE_H + PH + CAPTION_H + FOOT_H

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)

captions = {
    "A": ("ملفي — الشاشة A", "A · My profile — SPEC_PROFILES §3"),
    "B": ("ملف عضو — الشاشة B (من الشات)", "B · Member profile — §4 (Message hidden by owner)"),
    "C": ("ملفي كما يراه الآخرون — الشاشة C", "C · View as others — §5 (dashed wallet)"),
}
for i, key in enumerate(("A", "B", "C")):
    x0 = MARGIN + i * (PW + GAP)
    rr(d, (x0 - 8, TITLE_H - 8, x0 + PW + 8, TITLE_H + PH + 8), 26, fill="#05080F", outline=BORDER)
    rr(d, (x0, TITLE_H, x0 + PW, TITLE_H + PH), 18, fill=BG)
    img.paste(heights[key], (x0, TITLE_H))
    cap_ar, cap_en = captions[key]
    ar(d, x0 + PW, TITLE_H + PH + 30, cap_ar, size=15, color=GOLD, bold=True, anchor="ra")
    txt(d, x0 + PW, TITLE_H + PH + 54, cap_en, size=11, color=MUTED, anchor="ra")

ar(d, W // 2, 42, "شاشات الملف الشخصي الثلاث — وثيقة AddonStation_profiles", size=22, color=TEXT, bold=True, anchor="mm")
txt(d, W // 2, 74, "My profile · Member profile · View as others   —   tokens from ui/theme/Spec.kt   —   390dp",
    size=13, color=MUTED, anchor="mm")

ar(d, W // 2, H - 44, "زرّ «مراسلة العضو» مخفيّ مؤقتاً بقرارك (لا محادثات خاصة في الخادم بعد) · "
                      "الأرقام من دالة الخادم profile_stats", size=12, color=MUTED, anchor="mm")
ar(d, W // 2, H - 22, "المستوى = كل 100 AC مكتسَب · تعديلات غرفة الشات السابقة كما هي · "
                      "الشاشات تُمرَّر (مقاس 390dp)", size=12, color=MUTED, anchor="mm")

img.save(OUT)
print("saved", OUT, img.size)
