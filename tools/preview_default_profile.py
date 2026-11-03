#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""معاينة شاشتَي تسجيل الدخول وإنشاء الحساب بعد إضافة صورة البروفايل الافتراضية.

المصدر: AuthScreens.kt (LoginScreen · SignUpScreen) · Common.kt (DefaultProfileAvatar)
        res/drawable/ic_default_profile.xml
"""
import os

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "default_profile_screens.png")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SURFACE, ELEV, ELEV2, BORDER, BORDER2 = "#0B0F14", "#131A24", "#1A2330", "#212D3D", "#263240", "#334152"
TEXT, MUTED, DIM, GREEN, ONGREEN, BLUE = "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#04210F", "#6FB8FF"

PW, PH, TOP, GAP, MARGIN = 420, 860, 140, 34, 44


def cubic(p0, c1, c2, p3, n=60):
    pts = []
    for i in range(n + 1):
        t = i / n
        mt = 1 - t
        pts.append((
            (mt ** 3) * p0[0] + 3 * (mt ** 2) * t * c1[0] + 3 * mt * (t ** 2) * c2[0] + (t ** 3) * p3[0],
            (mt ** 3) * p0[1] + 3 * (mt ** 2) * t * c1[1] + 3 * mt * (t ** 2) * c2[1] + (t ** 3) * p3[1],
        ))
    return pts


def default_profile(size):
    """نفس هندسة الـ VectorDrawable داخل دائرة، كما ستظهر في التطبيق."""
    base = Image.new("RGB", (750, 730), "#808080")
    d = ImageDraw.Draw(base)
    d.ellipse((239, 189, 511, 461), fill="#D6D6D6", outline="#000000", width=11)
    left = cubic((105, 708), (105, 566), (228, 492), (375, 492))
    right = cubic((375, 492), (522, 492), (645, 566), (645, 708))
    d.polygon([(105, 730), (105, 708)] + left[1:] + right[1:] + [(645, 730)],
              fill="#D6D6D6", outline="#000000", width=11)
    img = base.resize((size, int(size * 730 / 750)))
    mask = Image.new("L", img.size, 0)
    ImageDraw.Draw(mask).ellipse((0, 0, img.width - 1, img.height - 1), fill=255)
    return img, mask


img = Image.new("RGB", (MARGIN * 2 + PW * 2 + GAP, TOP + PH + 210), BG)
d = ImageDraw.Draw(img)


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def text(x, y, s, size=13, color=TEXT, bold=False, anchor="lm"):
    d.text((x, y), t(s), font=font(size, bold), fill=color, anchor=anchor)


def latin(x, y, s, size=12, color=TEXT, anchor="lm"):
    d.text((x, y), s, font=font(size), fill=color, anchor=anchor)


def rounded(box, r, fill=None, outline=None, width=1):
    d.rounded_rectangle(box, radius=r, fill=fill, outline=outline, width=width)


def phone(x0, title):
    x1 = x0 + PW
    rounded((x0, TOP, x1, TOP + PH), 26, fill=SURFACE, outline=BORDER2, width=2)
    d.rectangle((x0 + 3, TOP + 3, x1 - 3, TOP + 40), fill=SURFACE)
    latin(x0 + 20, TOP + 21, "2:37", 12, TEXT)
    latin(x1 - 20, TOP + 21, "100%", 11, MUTED, anchor="rm")
    # ترويسة الشاشة
    latin(x0 + 18, TOP + 70, "‹", 20, TEXT)
    text(x0 + 40, TOP + 70, title, 15, TEXT, True)
    return x0, x1


def field(x0, x1, y, label, icon="", value=""):
    rounded((x0 + 18, y, x1 - 18, y + 54), 12, fill=ELEV, outline=BORDER2, width=1)
    text(x0 + 32, y + 18, label, 10, MUTED)
    if value:
        latin(x0 + 32, y + 38, value, 13, TEXT)
    if icon:
        rounded((x1 - 56, y + 14, x1 - 30, y + 40), 8, fill=ELEV2)
        latin(x1 - 43, y + 27, icon, 12, MUTED, anchor="mm")


def gradient_button(x0, x1, y, label):
    for i in range(6):
        k = i / 5
        col = (int(0x3D + (0x2F - 0x3D) * k), int(0xDC + (0xA8 - 0xDC) * k), int(0x84 + (0x5C - 0x84) * k))
        rounded((x0 + i, y + i, x1 - i, y + 50 - i), 14, fill=col)
    text((x0 + x1) // 2, y + 25, label, 14, ONGREEN, True, "mm")


def avatar(x0, y, size=92):
    pic, mask = default_profile(size)
    img.paste(pic, (int(x0), int(y)), mask)
    d.ellipse((x0, y, x0 + size, y + size), outline=BORDER2, width=2)


# ── 1) تسجيل الدخول ───────────────────────────────────────────────────
x0, x1 = phone(MARGIN, "تسجيل الدخول")
y = TOP + 104
avatar(x0 + (PW - 92) // 2, y)
y += 92 + 18
text((x0 + x1) // 2, y, "ADDONSTATION", 20, TEXT, True, "mm")
y += 26
text((x0 + x1) // 2, y, "سجّل دخولك إلى حسابك", 12, MUTED, anchor="mm")
y += 34
field(x0, x1, y, "البريد الإلكتروني", "@", "owner@example.com"); y += 64
field(x0, x1, y, "كلمة المرور", "*", "••••••••"); y += 76
gradient_button(x0 + 18, x1 - 18, y, "تسجيل الدخول"); y += 66
text((x0 + x1) // 2, y, "نسيت كلمة المرور؟", 12, BLUE, anchor="mm"); y += 40
text((x0 + x1) // 2, y, "ليس لديك حساب؟  إنشاء حساب", 12, GREEN, True, anchor="mm")
y += 52
rounded((x0 + 18, y, x1 - 18, y + 46), 12, fill=ELEV, outline=BORDER2)
text((x0 + x1) // 2, y + 23, "الدخول بحساب جوجل", 12, TEXT, anchor="mm")

# ── 2) إنشاء حساب ─────────────────────────────────────────────────────
x0, x1 = phone(MARGIN + PW + GAP, "إنشاء حساب")
y = TOP + 100
avatar(x0 + (PW - 92) // 2, y)
y += 92 + 16
text((x0 + x1) // 2, y, "أنشئ حسابك", 19, TEXT, True, "mm")
y += 26
text((x0 + x1) // 2, y, "خطوة واحدة وتصبح عضواً في مجتمعنا", 12, MUTED, anchor="mm")
y += 34
field(x0, x1, y, "اسم المستخدم", "", "owner"); y += 64
field(x0, x1, y, "البريد الإلكتروني", "@", "owner@example.com"); y += 64
field(x0, x1, y, "كلمة المرور", "*", "••••••••"); y += 76
gradient_button(x0 + 18, x1 - 18, y, "إنشاء الحساب"); y += 64
text((x0 + x1) // 2, y, "بإنشائك حساباً فأنت توافق على الشروط", 11, MUTED, anchor="mm")

# ── تذييل ─────────────────────────────────────────────────────────────
# ── 3) بعد الحساب: البروفايل الافتراضي المجاني ───────────────────────
y = TOP + PH + 24
text(img.width / 2, y, "وبعد تسجيل الدخول أو إنشاء الحساب:", 14, TEXT, True, "mm")
y += 34
free = Image.open(os.path.join(ROOT, "android", "app", "src", "main", "res",
                               "drawable-nodpi", "ic_user.png")).convert("RGBA").resize((96, 96))
mask = Image.new("L", (96, 96), 0)
ImageDraw.Draw(mask).ellipse((0, 0, 95, 95), fill=255)
img.paste(free, (int(img.width / 2 - 48), int(y)), mask)
d.ellipse((img.width / 2 - 48, y, img.width / 2 + 48, y + 96), outline=BORDER2, width=2)
text(img.width / 2 + 64, y + 28, "الحساب الجديد يأخذ الصورة الافتراضية المجانية",
     13, TEXT, True)
text(img.width / 2 + 64, y + 54, "وتظهر لكل من ما زال بلا صورة رفعها (شات · ملف · أعضاء)",
     12, MUTED)
text(img.width / 2 + 64, y + 76, "وعند رفع صورة شخصية تُستبدل تلقائياً", 12, MUTED)

img.save(OUT)
print("saved", OUT, img.size)
