#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يولّد أيقونة المستخدم: مربّع أزرق بحواف دائرية (squircle) بداخله شخص أبيض،
مع ظل ناعم داخل المربّع — كما في الصورة المرجعية المرسلة.

الناتج: ic_user.png شفاف بمقاس 256×256 (يُصغَّر من لوحة 640).
الاستخدام: python3 tools/make_user_icon.py <مجلد drawable-nodpi>
"""
from PIL import Image, ImageDraw, ImageFilter
import os
import sys

S = 640           # لوحة الرسم
OUT = 256         # المقاس النهائي
WHITE = (255, 255, 255, 255)
# تدرّج الأزرق: أفتح في الأعلى وأغمق في الأسفل
BLUE_TOP = (0x46, 0x93, 0xF7)
BLUE_BOTTOM = (0x14, 0x66, 0xD6)


def p(v):
    return int(round(v * S))


def squircle_mask(size, radius_ratio):
    """قناع مربّع بحواف دائرية بنسبة نصف قطر."""
    mask = Image.new("L", (size, size), 0)
    ImageDraw.Draw(mask).rounded_rectangle(
        [0, 0, size - 1, size - 1], radius=int(round(size * radius_ratio)), fill=255
    )
    return mask


def gradient(size, top, bottom):
    """تدرّج رأسي بخطوط أفقية (بلا numpy)."""
    grad = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(grad)
    for y in range(size):
        t = y / max(1, size - 1)
        color = tuple(int(round(top[i] + (bottom[i] - top[i]) * t)) for i in range(3)) + (255,)
        d.line([(0, y), (size - 1, y)], fill=color)
    return grad


def person_silhouette(color):
    """ظلّ/جسم الشخص: رأس دائري + كتفان بشكل قبة (نصف بيضاوي) بقاعدة مسطّحة."""
    layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    # الرأس
    d.ellipse([p(0.383), p(0.245), p(0.617), p(0.479)], fill=color)
    # الكتفان: النصف العلوي من بيضاوي (زوايا 180→360) ⇒ قمة منحنية وقاعدة أفقية مسطّحة
    d.pieslice([p(0.285), p(0.560), p(0.715), p(1.010)], start=180, end=360, fill=color)
    return layer


def build():
    icon = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # المربّع الأزرق المتدرّج داخل الحواف
    tile = gradient(S, BLUE_TOP, BLUE_BOTTOM)
    mask = squircle_mask(S, 0.235)
    icon.paste(tile, (0, 0), mask)

    # ظل ناعم أسفل الشخص (داخل المربّع فقط)
    shadow = person_silhouette((6, 26, 60, 70))
    shadow = shadow.filter(ImageFilter.GaussianBlur(10)).transform(
        (S, S), Image.AFFINE, (1, 0, 0, 0, 1, -10)
    )
    shadow_masked = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    shadow_masked.paste(shadow, (0, 0), mask)
    icon = Image.alpha_composite(icon, shadow_masked)

    # الشخص الأبيض
    icon = Image.alpha_composite(icon, person_silhouette(WHITE))

    # إبقاء الحدود نظيفة ثم تصغير
    return icon.resize((OUT, OUT), Image.LANCZOS)


def main():
    out_dir = sys.argv[1] if len(sys.argv) > 1 else "."
    os.makedirs(out_dir, exist_ok=True)
    img = build()
    path = os.path.join(out_dir, "ic_user.png")
    img.save(path, optimize=True)
    print(f"✓ {path} ({os.path.getsize(path):,} bytes، {OUT}×{OUT})")


if __name__ == "__main__":
    main()
