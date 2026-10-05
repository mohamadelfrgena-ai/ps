#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
معاينة رأس الصفحة الرئيسية بعد تعديل مكان كلام الترحيب:

    «Good evening 👋»  /  «Welcome to»  /  «AddonStation»

النص ملتصق بصورة البروفايل: على يمينها في الإنجليزية (LTR) وعلى يسارها
في العربية (RTL) — `Alignment.End` + `TextAlign.End` يتبعان الاتجاه تلقائياً.

يولّد: home_greeting_preview.png
"""
from PIL import Image, ImageDraw, ImageFont  # type: ignore
import arabic_reshaper  # type: ignore
from bidi.algorithm import get_display  # type: ignore

W, H = 980, 430
BG = (17, 20, 26)
PANEL = (26, 30, 38)
MUTED = (150, 158, 170)
TEXT = (238, 241, 246)
GREEN = (60, 200, 120)
BLUE = (70, 140, 235)
WHITE = (255, 255, 255)
ACCENT = (90, 220, 150)


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


F_TITLE = font(15, True)
F_GREET = font(14)
F_NAME = font(20, True)
F_BRAND = font(15)
F_NOTE = font(13)

img = Image.new("RGB", (W, H), BG)
d = ImageDraw.Draw(img)


def panel(y: int, rtl: bool, title: str, lines, avatar_label: str, note: str):
    """رسم لوحة واحدة: نص الترحيب ملتصق بالصورة حسب الاتجاه."""
    pad = 24
    box = (pad, y, W - pad, y + 150)
    d.rounded_rectangle(box, radius=16, fill=PANEL, outline=(48, 54, 66), width=1)
    d.text((pad + 16, y + 12), title, font=F_TITLE, fill=ACCENT)

    avatar_size = 84
    gap = 12
    # الصورة عند حافة End دائماً (يمين في LTR · يسار في RTL) — لم نحرّكها عن مكانها
    ax = (box[0] + pad) if rtl else (box[2] - pad - avatar_size)
    ay = y + 46

    # صورة البروفايل (تدرّج + إطار + صورة مصغّرة رمزية)
    for i in range(avatar_size):
        t = i / max(1, avatar_size - 1)
        col = (
            int(GREEN[0] * (1 - t) + BLUE[0] * t),
            int(GREEN[1] * (1 - t) + BLUE[1] * t),
            int(GREEN[2] * (1 - t) + BLUE[2] * t),
        )
        d.line([(ax, ay + i), (ax + avatar_size, ay + i)], fill=col)
    d.rectangle([ax + 4, ay + 4, ax + avatar_size - 5, ay + avatar_size - 5],
                outline=WHITE, width=2)
    cx, cy = ax + avatar_size // 2, ay + avatar_size // 2
    d.ellipse([cx - 20, cy - 26, cx + 20, cy + 14], fill=(220, 226, 235))
    d.ellipse([cx - 30, cy + 14, cx + 30, cy + 58], fill=(220, 226, 235))
    d.text((ax + 8, ay + avatar_size + 6), avatar_label, font=F_NOTE, fill=MUTED)

    # النص محاذىً إلى End ⇒ يلتصق بالصورة في الاتجاهين:
    #   LTR: حافته اليمنى عند الصورة (تمتد يساراً)
    #   RTL: حافته اليسرى عند الصورة (تمتد يميناً)
    text_edge = (ax + avatar_size + gap) if rtl else (ax - gap)
    y0 = ay + 6
    for (txt, f, col, is_ar) in lines:
        s = ar(txt) if is_ar else txt
        bbox = d.textbbox((0, 0), s, font=f)
        tw = bbox[2] - bbox[0]
        tx = text_edge if rtl else (text_edge - tw)
        d.text((tx, y0), s, font=f, fill=col)
        y0 += (bbox[3] - bbox[1]) + 8

    # سهم يوضّح اتجاه القراءة
    sy = y + 128
    if rtl:
        d.line([(W - pad - 40, sy), (pad + 40, sy)], fill=ACCENT, width=2)
        d.polygon([(pad + 40, sy), (pad + 52, sy - 6), (pad + 52, sy + 6)], fill=ACCENT)
        lbl = ar("اتجاه القراءة: من اليمين إلى اليسار")
    else:
        d.line([(pad + 40, sy), (W - pad - 40, sy)], fill=ACCENT, width=2)
        d.polygon([(W - pad - 40, sy), (W - pad - 52, sy - 6), (W - pad - 52, sy + 6)], fill=ACCENT)
        lbl = "Reading direction: left → right"
    lb = d.textbbox((0, 0), lbl, font=F_NOTE)
    d.text(((W - (lb[2] - lb[0])) // 2, sy + 8), lbl, font=F_NOTE, fill=ACCENT)

    print(note)


# ١) الإنجليزية (LTR): النص على يمين الصورة، محاذاة لليمين (End)
panel(
    y=44,
    rtl=False,
    title="EN · LTR — text hugs the avatar (End = right) · avatar on the right",
    lines=[
        ("Good evening 👋".replace("👋", "*"), F_GREET, MUTED, False),
        ("Welcome to", F_NAME, TEXT, False),
        ("AddonStation", F_BRAND, WHITE, False),
    ],
    avatar_label="avatar",
    note="EN panel ok",
)

# ٢) العربية (RTL): النص على يسار الصورة، محاذاة لليسار (End = left)
panel(
    y=234,
    rtl=True,
    title=ar("AR · RTL — النص ملتصق بالصورة (End = يسار) · الصورة على اليسار"),
    lines=[
        ("مساء الخير", F_GREET, MUTED, True),
        ("مرحبًا بك في", F_NAME, TEXT, True),
        ("AddonStation", F_BRAND, WHITE, True),
    ],
    avatar_label=ar("الصورة"),
    note="AR panel ok",
)

d.text((24, 12), ar("رأس الصفحة الرئيسية — الترحيب بجانب صورة البروفايل"),
       font=F_TITLE, fill=WHITE)

out = "home_greeting_preview.png"
img.save(out)
print("✓", out, img.size)
