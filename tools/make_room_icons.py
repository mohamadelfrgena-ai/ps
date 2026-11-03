#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يولّد أيقونات غرف الشات (13 غرفة) بدوائر ملوّنة/أعلام كما في المرجع:
5 غرف رئيسية بأيقونات مرسومة + 7 أعلام + العلم التركي لإكمال مجموعة اللغات.
الناتج PNG شفاف بمقاس 160×160 — بلا إيموجي.
"""
from PIL import Image, ImageDraw
import math
import os
import sys

S = 640          # لوحة الرسم ثم تُصغَّر
OUT = 160
WHITE = (255, 255, 255, 255)
NAVY = (26, 47, 128, 255)
RED = (206, 17, 38, 255)
GREEN = (0, 105, 62, 255)
YELLOW = (255, 203, 5, 255)
GREY = (176, 182, 190, 255)


def p(v):
    return int(round(v * S))


def circle(color):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ImageDraw.Draw(img).ellipse([0, 0, S - 1, S - 1], fill=color)
    return img, ImageDraw.Draw(img)


def clip_circle(layer):
    """يقصّ طبقة إلى دائرة (للأعلام)."""
    mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(mask).ellipse([0, 0, S - 1, S - 1], fill=255)
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    out.paste(layer, (0, 0), mask)
    return out


def lines_flag(colors):
    """علم بخطوط أفقية متساوية."""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    h = S / len(colors)
    for i, c in enumerate(colors):
        d.rectangle([0, int(i * h), S, int((i + 1) * h) + 1], fill=c)
    return clip_circle(layer)


# ------------------------------------------------------------ الغرف الرئيسية

def room_general():
    img, d = circle((150, 190, 240, 255))         # أزرق سماوي فاتح
    # فقاعتان محادثة بيضاوان متداخلتان
    d.rounded_rectangle([p(0.14), p(0.28), p(0.62), p(0.62)], radius=p(0.09), fill=WHITE)
    d.polygon([(p(0.22), p(0.60)), (p(0.22), p(0.74)), (p(0.34), p(0.62))], fill=WHITE)
    d.rounded_rectangle([p(0.40), p(0.20), p(0.88), p(0.52)], radius=p(0.09), fill=WHITE)
    d.polygon([(p(0.80), p(0.50)), (p(0.80), p(0.62)), (p(0.68), p(0.52))], fill=WHITE)
    # فجوة تفصل الفقاعتين
    d.rounded_rectangle([p(0.36), p(0.26), p(0.66), p(0.56)], radius=p(0.10), fill=(150, 190, 240, 255))
    d.rounded_rectangle([p(0.18), p(0.32), p(0.58), p(0.58)], radius=p(0.08), fill=WHITE)
    d.rounded_rectangle([p(0.44), p(0.24), p(0.86), p(0.50)], radius=p(0.08), fill=WHITE)
    return img


def room_minecraft():
    """كتلة عشب/تراب بأسلوب البكسل — مقطوعة على شكل دائرة."""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S, S], fill=(0x7A, 0x52, 0x2C, 255))          # تراب
    cell = S / 8
    # بكسلات التراب (تدرّجات بنّية)
    shades = [(0x8B, 0x5E, 0x33), (0x6B, 0x47, 0x25), (0x9A, 0x6A, 0x3B)]
    for gy in range(8):
        for gx in range(8):
            if gy < 3:
                continue
            c = shades[(gx * 3 + gy * 5) % 3]
            d.rectangle([gx * cell, gy * cell, (gx + 1) * cell + 1, (gy + 1) * cell + 1], fill=c + (255,))
    # طبقة العشب
    d.rectangle([0, 0, S, int(2.6 * cell)], fill=(0x5D, 0x9C, 0x35, 255))
    d.rectangle([0, 0, S, int(1.2 * cell)], fill=(0x7C, 0xC5, 0x40, 255))
    # أسنان العشب النازلة
    greens = [(0x5D, 0x9C, 0x35), (0x4E, 0x86, 0x2C)]
    for gx in range(8):
        if (gx * 7) % 3 == 0:
            d.rectangle([gx * cell, int(2.6 * cell), (gx + 1) * cell + 1, int(3.4 * cell)],
                        fill=greens[gx % 2] + (255,))
    return clip_circle(layer)


def room_creator():
    img, d = circle((139, 92, 220, 255))          # بنفسجي
    # قلم أبيض مائل
    d.polygon([(p(0.30), p(0.72)), (p(0.66), p(0.24)), (p(0.78), p(0.34)), (p(0.42), p(0.80))], fill=WHITE)
    d.polygon([(p(0.28), p(0.74)), (p(0.40), p(0.80)), (p(0.34), p(0.86))], fill=WHITE)
    d.line([(p(0.66), p(0.24)), (p(0.78), p(0.34))], fill=(139, 92, 220, 255), width=p(0.012))
    # لمعة صفراء
    d.polygon([(p(0.74), p(0.56)), (p(0.79), p(0.66)), (p(0.89), p(0.71)), (p(0.79), p(0.76)),
               (p(0.74), p(0.86)), (p(0.69), p(0.76)), (p(0.59), p(0.71)), (p(0.69), p(0.66))],
              fill=(255, 214, 102, 255))
    return img


def room_support():
    img, d = circle((38, 176, 168, 255))          # أخضر مزرق
    # حلقة بيضاء مفتوحة (استجابة)
    d.arc([p(0.18), p(0.18), p(0.82), p(0.82)], start=140, end=400, fill=WHITE, width=p(0.075))
    # قلم مائل يعبر الحلقة
    d.polygon([(p(0.36), p(0.64)), (p(0.62), p(0.30)), (p(0.74), p(0.40)), (p(0.48), p(0.74))], fill=WHITE)
    d.polygon([(p(0.34), p(0.66)), (p(0.46), p(0.74)), (p(0.40), p(0.80))], fill=WHITE)
    d.ellipse([p(0.60), p(0.26), p(0.78), p(0.44)], outline=WHITE, width=p(0.030))
    return img


def room_social():
    """دائرة وردية + حلقة بيضاء وخمس أيقونات منصّات حول يوتيوب في المنتصف."""
    img, d = circle((233, 88, 122, 255))
    # حلقة رابطة
    d.ellipse([p(0.24), p(0.24), p(0.76), p(0.76)], outline=WHITE, width=p(0.022))

    def tile(cx, cy, size, color, glyph):
        half = size / 2
        d.rounded_rectangle([p(cx - half), p(cy - half), p(cx + half), p(cy + half)],
                            radius=p(size * 0.28), fill=color)
        glyph(d, cx, cy, size)

    def g_play(dd, cx, cy, size):
        s = size * 0.40
        dd.polygon([(p(cx - s * 0.42), p(cy - s)), (p(cx - s * 0.42), p(cy + s)), (p(cx + s * 0.62), p(cy))],
                   fill=WHITE)

    def g_note(dd, cx, cy, size):
        s = size * 0.30
        dd.ellipse([p(cx - s), p(cy + s * 0.35), p(cx - s * 0.15), p(cy + s * 1.2)], fill=WHITE)
        dd.polygon([(p(cx - s * 0.15), p(cy + s * 0.35)), (p(cx + s * 0.55), p(cy - s * 0.55)),
                    (p(cx + s * 0.55), p(cy + s * 0.35)), (p(cx - s * 0.15), p(cy + s * 1.2))], fill=WHITE)
        dd.ellipse([p(cx + s * 0.0), p(cy + s * 0.05), p(cx + s * 0.9), p(cy + s * 0.95)], fill=WHITE)

    def g_insta(dd, cx, cy, size):
        s = size * 0.28
        dd.rounded_rectangle([p(cx - s), p(cy - s), p(cx + s), p(cy + s)], radius=p(s * 0.55),
                             outline=WHITE, width=p(size * 0.055))
        dd.ellipse([p(cx - s * 0.40), p(cy - s * 0.40), p(cx + s * 0.40), p(cy + s * 0.40)],
                   outline=WHITE, width=p(size * 0.055))
        dd.ellipse([p(cx + s * 0.45), p(cy - s * 0.72), p(cx + s * 0.72), p(cy - s * 0.45)], fill=WHITE)

    def g_twitter(dd, cx, cy, size):
        s = size * 0.34
        dd.polygon([(p(cx - s), p(cy - s * 0.75)), (p(cx + s * 0.15), p(cy + s * 0.15)),
                    (p(cx + s), p(cy + s * 0.75)), (p(cx + s * 0.95), p(cy + s * 0.85)),
                    (p(cx - s * 0.15), p(cy - s * 0.2)), (p(cx - s * 0.95), p(cy + s * 0.8))], fill=WHITE)

    def g_facebook(dd, cx, cy, size):
        s = size * 0.30
        dd.rounded_rectangle([p(cx - s * 0.35), p(cy - s), p(cx + s * 0.35), p(cy + s)],
                             radius=p(s * 0.18), fill=WHITE)
        dd.rectangle([p(cx - s * 1.0), p(cy - s * 1.05), p(cx + s * 1.0), p(cy - s * 0.9)], fill=(233, 88, 122, 255))

    # الوسط: يوتيوب
    tile(0.50, 0.50, 0.30, (230, 33, 23, 255), g_play)
    # حوله: تيك توك، إنستغرام، تويتر، فيسبوك
    tile(0.28, 0.30, 0.22, (18, 18, 18, 255), g_note)
    tile(0.72, 0.29, 0.22, (198, 60, 165, 255), g_insta)
    tile(0.28, 0.72, 0.22, (29, 161, 242, 255), g_twitter)
    tile(0.72, 0.72, 0.22, (59, 89, 152, 255), g_facebook)
    return img


# ------------------------------------------------------------------ الأعلام

def flag_en():
    """علم الاتحاد + بيغ بن."""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S, S], fill=NAVY)
    d.line([(0, 0), (S, S)], fill=WHITE, width=p(0.13))
    d.line([(S, 0), (0, S)], fill=WHITE, width=p(0.13))
    d.line([(0, 0), (S, S)], fill=RED, width=p(0.06))
    d.line([(S, 0), (0, S)], fill=RED, width=p(0.06))
    d.rectangle([p(0.41), 0, p(0.59), S], fill=WHITE)
    d.rectangle([0, p(0.41), S, p(0.59)], fill=WHITE)
    d.rectangle([p(0.45), 0, p(0.55), S], fill=RED)
    d.rectangle([0, p(0.45), S, p(0.55)], fill=RED)
    # بيغ بن على اليمين: قاعدة + جسم + ساعة + سقف مدبّب + قمة
    d.polygon([(p(0.86), p(0.30)), (p(0.86), p(0.90)), (p(0.60), p(0.90)), (p(0.62), p(0.72)),
               (p(0.60), p(0.46)), (p(0.64), p(0.34))], fill=GREY)
    # السقف المدبّب
    d.polygon([(p(0.60), p(0.34)), (p(0.73), p(0.08)), (p(0.86), p(0.30))], fill=GREY)
    d.line([(p(0.73), p(0.08)), (p(0.73), p(0.03))], fill=GREY, width=p(0.014))
    # الساعة
    d.ellipse([p(0.645), p(0.44), p(0.815), p(0.61)], fill=(248, 248, 244, 255), outline=(120, 126, 134, 255),
              width=p(0.014))
    d.line([(p(0.73), p(0.525)), (p(0.73), p(0.465))], fill=(70, 74, 80, 255), width=p(0.014))
    d.line([(p(0.73), p(0.525)), (p(0.785), p(0.545))], fill=(70, 74, 80, 255), width=p(0.014))
    # نوافذ صغيرة
    for yy in (0.68, 0.78):
        d.rectangle([p(0.665), p(yy), p(0.705), p(yy + 0.035)], fill=(150, 156, 164, 255))
        d.rectangle([p(0.755), p(yy), p(0.795), p(yy + 0.035)], fill=(150, 156, 164, 255))
    return clip_circle(layer)


def flag_ar():
    """العلم السعودي: خلفية خضراء + الشهادتان (سطر خطي مبسّط) + سيف أفقي مسنّن."""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S, S], fill=(0x16, 0x5D, 0x2E, 255))   # الأخضر السعودي

    # --- الشهادتان: سطران مبسّطان يدوران حولهما زخرفة صغيرة ---
    # السطر الأول (أعرض وأطول)
    d.rounded_rectangle([p(0.27), p(0.285), p(0.735), p(0.355)], radius=p(0.032), fill=WHITE)
    # امتدادات تشبه تشكيل الحروف: قمم صغيرة أعلى السطر
    for cx in (0.335, 0.425, 0.515, 0.605):
        d.polygon([(p(cx - 0.028), p(0.285)), (p(cx), p(0.242)), (p(cx + 0.028), p(0.285))], fill=WHITE)
    # نقاط/لمعات أسفل السطر الأول
    for cx in (0.315, 0.41, 0.50, 0.59, 0.665):
        d.ellipse([p(cx - 0.013), p(0.372), p(cx + 0.013), p(0.398)], fill=WHITE)
    # السطر الثاني (أقصر، يدلّ على بقية الشهادة)
    d.rounded_rectangle([p(0.36), p(0.435), p(0.70), p(0.485)], radius=p(0.025), fill=WHITE)
    for cx in (0.43, 0.52, 0.61):
        d.polygon([(p(cx - 0.024), p(0.435)), (p(cx), p(0.40)), (p(cx + 0.024), p(0.435))], fill=WHITE)

    # --- السيف الأفقي المسنّن أسفل الشهادتين ---
    blade_top, blade_bottom = 0.615, 0.675
    d.rectangle([p(0.20), p(blade_top), p(0.80), p(blade_bottom)], fill=WHITE)
    # نصل مدبّب إلى اليسار
    d.polygon([(p(0.20), p(0.645)), (p(0.115), p(blade_top)), (p(0.115), p(blade_bottom))], fill=WHITE)
    # مقبض السيف إلى اليمين
    d.rounded_rectangle([p(0.80), p(0.596), p(0.865), p(0.694)], radius=p(0.014), fill=WHITE)
    # شفرة السيف (واقي يد) أعلى المقبض
    d.rectangle([p(0.845), p(0.565), p(0.868), p(0.725)], fill=WHITE)
    return clip_circle(layer)


def flag_ru():
    return lines_flag([(255, 255, 255, 255), (0, 57, 166, 255), (213, 43, 30, 255)])


def flag_fr():
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S / 3, S], fill=(0, 35, 149, 255))
    d.rectangle([S / 3, 0, 2 * S / 3, S], fill=(255, 255, 255, 255))
    d.rectangle([2 * S / 3, 0, S, S], fill=(237, 41, 57, 255))
    # برج إيفل: ساقان مفتوحتان + منصّتان + قمة
    d.polygon([(p(0.485), p(0.14)), (p(0.515), p(0.14)), (p(0.545), p(0.38)), (p(0.455), p(0.38))],
              fill=(38, 52, 116, 255))
    d.polygon([(p(0.455), p(0.38)), (p(0.545), p(0.38)), (p(0.60), p(0.60)), (p(0.515), p(0.60)),
               (p(0.50), p(0.48)), (p(0.485), p(0.60)), (p(0.40), p(0.60))], fill=(38, 52, 116, 255))
    d.rectangle([p(0.40), p(0.585), p(0.60), p(0.625)], fill=(38, 52, 116, 255))
    # الساقان
    d.polygon([(p(0.415), p(0.625)), (p(0.465), p(0.625)), (p(0.435), p(0.88)), (p(0.305), p(0.88))],
              fill=(38, 52, 116, 255))
    d.polygon([(p(0.535), p(0.625)), (p(0.585), p(0.625)), (p(0.695), p(0.88)), (p(0.565), p(0.88))],
              fill=(38, 52, 116, 255))
    d.rectangle([p(0.30), p(0.865), p(0.70), p(0.90)], fill=(38, 52, 116, 255))
    return clip_circle(layer)


def flag_pt():
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S * 0.4, S], fill=(0, 102, 0, 255))
    d.rectangle([S * 0.4, 0, S, S], fill=(255, 0, 0, 255))
    # كرة أرميلار صفراء
    cx, cy = 0.40, 0.50
    d.ellipse([p(cx - 0.16), p(cy - 0.16), p(cx + 0.16), p(cy + 0.16)], outline=YELLOW, width=p(0.028))
    d.ellipse([p(cx - 0.20), p(cy - 0.10), p(cx + 0.20), p(cy + 0.10)], outline=YELLOW, width=p(0.022))
    # درع أبيض بحدّ أحمر ونقاط زرقاء
    d.polygon([(p(cx - 0.13), p(cy - 0.05)), (p(cx + 0.13), p(cy - 0.05)), (p(cx + 0.11), p(cy + 0.10)),
               (p(cx), p(cy + 0.16)), (p(cx - 0.11), p(cy + 0.10))],
              fill=(255, 255, 255, 255), outline=(255, 0, 0, 255), width=p(0.018))
    for dx, dy in ((-0.07, 0.02), (0.0, 0.02), (0.07, 0.02), (-0.035, 0.08), (0.035, 0.08)):
        d.ellipse([p(cx + dx - 0.018), p(cy + dy - 0.018), p(cx + dx + 0.018), p(cy + dy + 0.018)],
                  fill=(0, 51, 153, 255))
    return clip_circle(layer)


def flag_zh():
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S, S], fill=(222, 41, 16, 255))

    def star(cx, cy, r, color=YELLOW, rot=-90):
        pts = []
        for i in range(10):
            ang = math.radians(rot + i * 36)
            rr = r if i % 2 == 0 else r * 0.42
            pts.append((p(cx + rr * math.cos(ang)), p(cy + rr * math.sin(ang))))
        d.polygon(pts, fill=color)

    star(0.31, 0.27, 0.115)
    for (sx, sy) in ((0.44, 0.19), (0.48, 0.28), (0.46, 0.37), (0.40, 0.44)):
        star(sx, sy, 0.042, rot=-70)
    # بوابة/معبد مبسّط
    d.polygon([(p(0.30), p(0.63)), (p(0.60), p(0.63)), (p(0.66), p(0.70)), (p(0.24), p(0.70))], fill=YELLOW)
    d.rectangle([p(0.29), p(0.70), p(0.61), p(0.74)], fill=YELLOW)
    d.rectangle([p(0.32), p(0.74), p(0.58), p(0.86)], fill=YELLOW)
    d.rectangle([p(0.42), p(0.74), p(0.48), p(0.86)], fill=(222, 41, 16, 255))
    return clip_circle(layer)


def flag_es():
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S, S * 0.25], fill=(198, 11, 30, 255))
    d.rectangle([0, S * 0.25, S, S * 0.75], fill=(255, 196, 0, 255))
    d.rectangle([0, S * 0.75, S, S], fill=(198, 11, 30, 255))
    # شعار مبسّط (أكبر): درع أحمر بإطار ذهبي + تاج
    d.polygon([(p(0.24), p(0.38)), (p(0.42), p(0.38)), (p(0.40), p(0.60)), (p(0.33), p(0.68)),
               (p(0.26), p(0.60))], fill=(198, 11, 30, 255))
    d.polygon([(p(0.245), p(0.385)), (p(0.415), p(0.385)), (p(0.395), p(0.595)), (p(0.33), p(0.675)),
               (p(0.265), p(0.595))], outline=(240, 200, 120, 255), width=p(0.016))
    d.rectangle([p(0.27), p(0.44), p(0.39), p(0.50)], fill=(240, 200, 120, 255))
    d.rectangle([p(0.30), p(0.50), p(0.36), p(0.57)], fill=(240, 200, 120, 255))
    d.polygon([(p(0.255), p(0.38)), (p(0.33), p(0.31)), (p(0.405), p(0.38))], fill=(240, 200, 120, 255))
    return clip_circle(layer)


def flag_tr():
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    d.rectangle([0, 0, S, S], fill=(227, 10, 23, 255))
    d.ellipse([p(0.24), p(0.24), p(0.68), p(0.76)], fill=(255, 255, 255, 255))
    d.ellipse([p(0.32), p(0.27), p(0.74), p(0.73)], fill=(227, 10, 23, 255))
    pts = []
    for i in range(10):
        ang = math.radians(-90 + i * 36)
        rr = 0.095 if i % 2 == 0 else 0.040
        pts.append((p(0.68 + rr * math.cos(ang)), p(0.50 + rr * math.sin(ang))))
    d.polygon(pts, fill=(255, 255, 255, 255))
    return clip_circle(layer)


ROOMS = {
    "ic_room_general": room_general,
    "ic_room_minecraft": room_minecraft,
    "ic_room_creator": room_creator,
    "ic_room_support": room_support,
    "ic_room_social": room_social,
    "ic_room_lang_en": flag_en,
    "ic_room_lang_ar": flag_ar,
    "ic_room_lang_ru": flag_ru,
    "ic_room_lang_fr": flag_fr,
    "ic_room_lang_pt": flag_pt,
    "ic_room_lang_zh": flag_zh,
    "ic_room_lang_es": flag_es,
    "ic_room_lang_tr": flag_tr,
}

if __name__ == "__main__":
    out = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out, exist_ok=True)
    for name, fn in ROOMS.items():
        img = fn().resize((OUT, OUT), Image.LANCZOS)
        path = os.path.join(out, name + ".png")
        img.save(path, "PNG", optimize=True)
        print("✓", path, os.path.getsize(path), "بايت")
