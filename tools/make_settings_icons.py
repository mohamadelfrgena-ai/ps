#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يولّد أيقونات شاشة الإعدادات (10 أيقونات) بنفس تصميم المجموعة المرجعية:
مربع ملوّن بأطراف دائرية + رمز أبيض داخله. الناتج PNG شفاف بمقاس 160×160.
"""
from PIL import Image, ImageDraw, ImageFont

S = 640                    # لوحة الرسم (4× ثم تُصغَّر لتنعيم الحواف)
OUT = 160
WHITE = (255, 255, 255, 255)

BLUE   = (30, 123, 243, 255)
PURPLE = (108, 92, 231, 255)
VIOLET = (123, 97, 255, 255)
RED    = (240, 68, 56, 255)
GREEN  = (34, 179, 92, 255)
ORANGE = (245, 166, 35, 255)
TEAL   = (18, 181, 165, 255)
DARK   = (52, 56, 62, 255)
LIGHT  = (242, 244, 247, 255)
DOTRED = (255, 75, 75, 255)

FONT_BOLD = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def p(v):
    """نسبة من مقاس اللوحة."""
    return int(round(v * S))


def base(color, radius=0.235):
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    d.rounded_rectangle([0, 0, S - 1, S - 1], radius=p(radius), fill=color)
    return img, d


def text_center(d, xy, txt, size, fill, font=FONT_BOLD):
    f = ImageFont.truetype(font, p(size))
    box = d.textbbox((0, 0), txt, font=f)
    d.text((p(xy[0]) - (box[2] - box[0]) / 2 - box[0], p(xy[1]) - (box[3] - box[1]) / 2 - box[1]),
           txt, font=f, fill=fill)


# ----------------------------------------------------------------- الأيقونات

def ic_notifications():
    img, d = base(BLUE)
    d.arc([p(0.10), p(0.30), p(0.42), p(0.66)], start=110, end=250, fill=WHITE, width=p(0.045))
    d.arc([p(0.58), p(0.30), p(0.90), p(0.66)], start=-70, end=70, fill=WHITE, width=p(0.045))
    d.pieslice([p(0.30), p(0.20), p(0.70), p(0.60)], 180, 360, fill=WHITE)
    d.rectangle([p(0.30), p(0.40), p(0.70), p(0.58)], fill=WHITE)
    d.rounded_rectangle([p(0.23), p(0.55), p(0.77), p(0.64)], radius=p(0.045), fill=WHITE)
    d.ellipse([p(0.43), p(0.66), p(0.57), p(0.78)], fill=WHITE)
    d.ellipse([p(0.66), p(0.10), p(0.88), p(0.32)], fill=DOTRED)
    return img


def ic_appearance():
    img, d = base(DARK)
    d.polygon([(S, p(0.06)), (S, S), (p(0.06), S)], fill=LIGHT)
    # هلال القمر في الجزء الداكن
    d.ellipse([p(0.14), p(0.16), p(0.42), p(0.44)], fill=WHITE)
    d.ellipse([p(0.24), p(0.10), p(0.52), p(0.38)], fill=DARK)
    # نجيمتان
    for (cx, cy, r) in ((0.50, 0.16, 0.030), (0.40, 0.28, 0.020)):
        d.polygon([(p(cx), p(cy - r)), (p(cx + r * 0.6), p(cy)), (p(cx), p(cy + r)),
                   (p(cx - r * 0.6), p(cy))], fill=WHITE)
    # شمس ساطعة في الجزء الفاتح
    cx, cy = 0.68, 0.68
    d.ellipse([p(cx - 0.135), p(cy - 0.135), p(cx + 0.135), p(cy + 0.135)], fill=DARK)
    import math
    for i in range(8):
        a = math.radians(i * 45 + 22.5)
        x1, y1 = p(cx + 0.20 * math.cos(a)), p(cy + 0.20 * math.sin(a))
        x2, y2 = p(cx + 0.28 * math.cos(a)), p(cy + 0.28 * math.sin(a))
        d.line([(x1, y1), (x2, y2)], fill=DARK, width=p(0.035))
    return img


def ic_help():
    img, d = base(PURPLE)
    d.arc([p(0.18), p(0.16), p(0.82), p(0.80)], start=180, end=360, fill=WHITE, width=p(0.075))
    d.rounded_rectangle([p(0.14), p(0.42), p(0.30), p(0.72)], radius=p(0.06), fill=WHITE)
    d.rounded_rectangle([p(0.70), p(0.42), p(0.86), p(0.72)], radius=p(0.06), fill=WHITE)
    d.arc([p(0.40), p(0.54), p(0.78), p(0.88)], start=0, end=80, fill=WHITE, width=p(0.045))
    d.ellipse([p(0.34), p(0.76), p(0.46), p(0.88)], fill=WHITE)
    text_center(d, (0.50, 0.50), "?", 0.30, WHITE)
    return img


def ic_report():
    img, d = base(RED)
    # أرجل الحشرة
    for (y1, y2) in ((0.42, 0.38), (0.54, 0.54), (0.66, 0.72)):
        d.line([(p(0.30), p(y1)), (p(0.14), p(y2))], fill=WHITE, width=p(0.038))
        d.line([(p(0.70), p(y1)), (p(0.86), p(y2))], fill=WHITE, width=p(0.038))
    # قرون الاستشعار
    d.line([(p(0.42), p(0.26)), (p(0.32), p(0.12))], fill=WHITE, width=p(0.038))
    d.line([(p(0.58), p(0.26)), (p(0.68), p(0.12))], fill=WHITE, width=p(0.038))
    d.ellipse([p(0.36), p(0.16), p(0.64), p(0.38)], fill=WHITE)     # الرأس
    d.ellipse([p(0.26), p(0.34), p(0.74), p(0.84)], fill=WHITE)     # الجسم
    d.line([(p(0.50), p(0.36)), (p(0.50), p(0.82))], fill=RED, width=p(0.022))
    d.ellipse([p(0.30), p(0.46), p(0.70), p(0.56)], fill=RED)
    d.ellipse([p(0.30), p(0.60), p(0.70), p(0.70)], fill=RED)
    # شارة التنبيه
    d.ellipse([p(0.56), p(0.56), p(0.94), p(0.94)], fill=WHITE)
    text_center(d, (0.75, 0.74), "!", 0.26, RED)
    return img


def ic_contact():
    img, d = base(GREEN)
    # فقاعة خلفية
    d.rounded_rectangle([p(0.40), p(0.16), p(0.88), p(0.52)], radius=p(0.10), fill=WHITE)
    # فقاعة أمامية + ذيل
    d.rounded_rectangle([p(0.12), p(0.30), p(0.68), p(0.70)], radius=p(0.11), fill=GREEN)
    d.rounded_rectangle([p(0.14), p(0.28), p(0.66), p(0.68)], radius=p(0.11), fill=WHITE)
    d.polygon([(p(0.24), p(0.66)), (p(0.24), p(0.84)), (p(0.40), p(0.66))], fill=WHITE)
    for cx in (0.28, 0.40, 0.52):
        d.ellipse([p(cx - 0.035), p(0.45), p(cx + 0.035), p(0.52)], fill=GREEN)
    return img


def ic_version():
    img, d = base(BLUE)
    d.rounded_rectangle([p(0.30), p(0.10), p(0.70), p(0.90)], radius=p(0.10), fill=WHITE)
    d.rounded_rectangle([p(0.345), p(0.17), p(0.655), p(0.83)], radius=p(0.05), fill=BLUE)
    d.rounded_rectangle([p(0.42), p(0.125), p(0.58), p(0.145)], radius=p(0.012), fill=BLUE)
    text_center(d, (0.50, 0.50), "v1.0", 0.135, WHITE)
    return img


def ic_terms():
    img, d = base(ORANGE)
    d.rounded_rectangle([p(0.18), p(0.12), p(0.64), p(0.88)], radius=p(0.05), fill=WHITE)
    d.polygon([(p(0.52), p(0.12)), (p(0.64), p(0.12)), (p(0.64), p(0.24))], fill=ORANGE)
    for i, y in enumerate((0.30, 0.42, 0.54)):
        d.rounded_rectangle([p(0.26), p(y), p(0.56 if i < 2 else 0.46), p(y + 0.045)],
                            radius=p(0.022), fill=ORANGE)
    # درع + علامة صح
    d.polygon([(p(0.70), p(0.46)), (p(0.94), p(0.56)), (p(0.90), p(0.78)), (p(0.70), p(0.94)),
               (p(0.50), p(0.78)), (p(0.46), p(0.56))], fill=WHITE)
    d.line([(p(0.60), p(0.70)), (p(0.68), p(0.79)), (p(0.82), p(0.60))], fill=ORANGE, width=p(0.05), joint="curve")
    return img


def ic_privacy():
    img, d = base(TEAL)
    d.polygon([(p(0.50), p(0.08)), (p(0.88), p(0.24)), (p(0.84), p(0.60)), (p(0.50), p(0.92)),
               (p(0.16), p(0.60)), (p(0.12), p(0.24))], fill=WHITE)
    # قفل بشكل مفرّغ داخل الدرع
    d.arc([p(0.36), p(0.34), p(0.64), p(0.62)], start=180, end=360, fill=TEAL, width=p(0.06))
    d.rounded_rectangle([p(0.32), p(0.48), p(0.68), p(0.76)], radius=p(0.05), fill=TEAL)
    d.ellipse([p(0.46), p(0.56), p(0.54), p(0.64)], fill=WHITE)
    d.rectangle([p(0.485), p(0.62), p(0.515), p(0.70)], fill=WHITE)
    return img


def ic_language():
    img, d = base(VIOLET)
    # كرة أرضية بيضاء مع خطوط طول/عرض بلون المربع (شفافية بصرية)
    d.ellipse([p(0.13), p(0.13), p(0.87), p(0.87)], fill=WHITE)
    d.ellipse([p(0.30), p(0.13), p(0.70), p(0.87)], outline=VIOLET, width=p(0.040))  # خط طول متوسط
    d.line([(p(0.50), p(0.13)), (p(0.50), p(0.87))], fill=VIOLET, width=p(0.030))    # خط طول رئيسي
    for y in (0.32, 0.50, 0.68):                                                     # خطوط عرض
        d.line([(p(0.15), p(y)), (p(0.85), p(y))], fill=VIOLET, width=p(0.028))
    # خط العرض الرئيسي (خط الاستواء) أعرض قليلاً
    d.line([(p(0.13), p(0.50)), (p(0.87), p(0.50))], fill=VIOLET, width=p(0.045))
    return img


def ic_email():
    img, d = base(BLUE)
    d.rounded_rectangle([p(0.10), p(0.26), p(0.90), p(0.74)], radius=p(0.07), fill=WHITE)
    d.line([(p(0.15), p(0.32)), (p(0.50), p(0.55))], fill=BLUE, width=p(0.055))
    d.line([(p(0.85), p(0.32)), (p(0.50), p(0.55))], fill=BLUE, width=p(0.055))
    return img


ICONS = {
    "ic_set_notifications": ic_notifications,
    "ic_set_appearance": ic_appearance,
    "ic_set_help": ic_help,
    "ic_set_report": ic_report,
    "ic_set_contact": ic_contact,
    "ic_set_version": ic_version,
    "ic_set_terms": ic_terms,
    "ic_set_privacy": ic_privacy,
    "ic_set_language": ic_language,
    "ic_set_email": ic_email,
}

if __name__ == "__main__":
    import os
    import sys

    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out_dir, exist_ok=True)
    made = []
    for name, fn in ICONS.items():
        img = fn().resize((OUT, OUT), Image.LANCZOS)
        path = os.path.join(out_dir, name + ".png")
        img.save(path, "PNG", optimize=True)
        made.append(path)
        print("✓", path, os.path.getsize(path), "بايت")
