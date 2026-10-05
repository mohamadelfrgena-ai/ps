#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة **مكان مربّع البروفايل**: نصفه داخل الغلاف (banner) ونصفه خارجه
(كما في صورة المالك) في ثلاثة مواضع:
    • الملف الشخصي (حسابي)        — غلاف 140dp · مربّع 74dp بإطار متدرّج
    • الملف الشخصي (مستخدم آخر)   — غلاف 140dp · مربّع 72dp
    • بطاقة المستخدم في الشات     — غلاف 100dp · مربّع 80dp (دائري)

المصدر: ui/profile/ProfileScreens.kt · ui/chat/ChatSheets.kt
الإخراج: profile_header_preview.png
"""
import io
import os
import tempfile
import zipfile

from PIL import Image, ImageDraw, ImageFont
import arabic_reshaper
from bidi.algorithm import get_display

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "profile_header_preview.png")
ZIP = os.path.join(ROOT, "banners", "Banner.zip")
AVATAR = os.path.join(ROOT, "android", "app", "src", "main", "res",
                      "drawable-nodpi", "ic_default_profile.jpg")

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
FONT_REG = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"

BG, SCREEN, ELEV2, BORDER = "#070A0E", "#0B1017", "#1B2532", "#26333F"
TEXT, MUTED, DIM, GREEN, BLUE = "#E8EEF5", "#8EA0B3", "#5C6F82", "#3DDC84", "#4AA8FF"

S = 1.5                      # مقياس الرسم: 1dp ≈ 1.5 بكسل
W, H = 940, 700


def t(s):
    return get_display(arabic_reshaper.reshape(s))


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT_REG, size)


def rounded_mask(size, radius):
    w, h = size
    m = Image.new("L", (w * 4, h * 4), 0)
    ImageDraw.Draw(m).rounded_rectangle((0, 0, w * 4 - 1, h * 4 - 1), radius=radius * 4, fill=255)
    return m.resize((w, h), Image.LANCZOS)


def vgradient(size, c1, c2):
    """تدرّج رأسي بسيط بين لونين."""
    w, h = size
    g = Image.new("RGB", (1, h))
    d = ImageDraw.Draw(g)
    r1, g1, b1 = Image.new("RGB", (1, 1), c1).getpixel((0, 0))
    r2, g2, b2 = Image.new("RGB", (1, 1), c2).getpixel((0, 0))
    for y in range(h):
        k = y / max(1, h - 1)
        d.point((0, y), fill=(int(r1 + (r2 - r1) * k), int(g1 + (g2 - g1) * k),
                              int(b1 + (b2 - b1) * k)))
    return g.resize((w, h))


def crop_to(img, size):
    tw, th = size
    w, h = img.size
    target = tw / th
    if w / h > target:
        nw = int(h * target)
        img = img.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = int(w / target)
        img = img.crop((0, max(0, (h - nh) // 3), w, max(0, (h - nh) // 3) + nh))
    return img.resize((tw, th), Image.LANCZOS)


def load_banner():
    if os.path.exists(ZIP):
        with zipfile.ZipFile(ZIP) as z:
            hit = next((n for n in z.namelist() if n.endswith("/Summer1.png")), None)
            if hit:
                return Image.open(io.BytesIO(z.read(hit))).convert("RGB")
    return Image.new("RGB", (800, 450), "#2E4A3A")


def header(img, ox, oy, width, title, note, banner_h_dp, avatar_dp, rounded_avatar):
    """يرسم غلافاً + مربّع بروفايل **نصفه داخل الغلاف ونصفه خارجه**."""
    d = ImageDraw.Draw(img)
    bh = int(banner_h_dp * S)
    av = int(avatar_dp * S)
    bx, by = ox, oy

    # الغلاف
    d.rounded_rectangle((bx, by, bx + width, by + bh), radius=int(20 * S),
                        fill=ELEV2, outline=BORDER, width=1)
    img.paste(crop_to(BANNER, (width - 2, bh - 2)), (bx + 1, by + 1),
              rounded_mask((width - 2, bh - 2), int(20 * S)))

    # خط متقطّع على حافة الغلاف السفلية (يوضّح النصفين)
    for x in range(bx, bx + width, 14):
        d.line((x, by + bh, min(x + 7, bx + width), by + bh), fill=GREEN, width=1)

    # مربّع البروفايل: منتصفه على حافة الغلاف تماماً
    ax = bx + int(16 * S)
    ay = by + bh - av // 2
    if rounded_avatar:
        ring = Image.new("RGB", (av, av), ELEV2)
        img.paste(ring, (ax, ay), rounded_mask((av, av), av // 2))
        face = Image.open(AVATAR).convert("RGB").resize((av, av), Image.LANCZOS)
        img.paste(face, (ax, ay), rounded_mask((av, av), av // 2))
        d.ellipse((ax, ay, ax + av - 1, ay + av - 1), outline=SCREEN, width=int(5 * S))
    else:
        grad = vgradient((av, av), GREEN, BLUE)
        img.paste(grad, (ax, ay), rounded_mask((av, av), int(22 * S)))
        pad = int(3 * S)
        face = Image.open(AVATAR).convert("RGB").resize((av - 2 * pad, av - 2 * pad), Image.LANCZOS)
        img.paste(face, (ax + pad, ay + pad),
                  rounded_mask((av - 2 * pad, av - 2 * pad), int(19 * S)))

    # الاسم تحت المربّع البارز
    d.text((bx + int(16 * S), by + bh + av // 2 + int(12 * S)), t("اسم المستخدم"),
           font=font(15, True), fill=TEXT, anchor="lm")
    d.text((bx + width, by + bh + av // 2 + int(12 * S)), t(note), font=font(9),
           fill=DIM, anchor="rm")


def main():
    global BANNER
    BANNER = load_banner()
    img = Image.new("RGB", (W, H), BG)
    d = ImageDraw.Draw(img)
    d.text((W // 2, 30), t("مربّع البروفايل: نصفه داخل الغلاف ونصفه خارجه"),
           font=font(17, True), fill=TEXT, anchor="mm")
    d.text((W // 2, 54), t("الخط المتقطّع الأخضر = حافة الغلاف السفلية"),
           font=font(10), fill=MUTED, anchor="mm")

    pw = 420
    gap = 30
    x0 = (W - (2 * pw + gap)) // 2
    y0 = 90

    header(img, x0, y0, pw, "", "غلاف 140 · مربّع 74 مربّع دائري", 140, 74, False)
    header(img, x0 + pw + gap, y0, pw, "", "غلاف 140 · مربّع 72 دائري", 140, 72, True)
    header(img, x0, y0 + 320, pw, "", "بطاقة الشات: غلاف 100 · مربّع 80", 100, 80, True)

    d.text((W // 2, H - 16),
           t("البروز = نصف حجم المربّع (37dp في الملف الشخصي · 40dp في بطاقة الشات)"),
           font=font(10), fill=DIM, anchor="mm")
    img.save(OUT)
    print(f"✓ {OUT}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
