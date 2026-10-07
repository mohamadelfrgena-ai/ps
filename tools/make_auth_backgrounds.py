#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يولّد **خلفيات شاشات الدخول وإنشاء الحساب** بمقاسات الهاتف.

لماذا نحتاج المولّد؟
    الصور الأصلية 1920×1080 (نسبة 16:9)، وعرض الهاتف بوحدة البكسل يتغيّر
    بتغيّر الكثافة: mdpi 360 · hdpi 540 · xhdpi 720 · xxhdpi 1080.
    نسخة واحدة تُكبَّر على كل الأجهزة ⇒ إما تمطيط (تشوّه) أو حجم ملف ضخم
    بلا فائدة. المولّد يُنتج **نسخة مضبوطة لكل كثافة** ⇒ حِدّة كاملة بأقلّ حجم.

الاستخدام:
    python3 tools/make_auth_backgrounds.py

المصادر (الصور الأصلية بجودتها الكاملة):
    design/auth/login_bg_master.jpg   ← خلفية تسجيل الدخول
    design/auth/signup_bg_master.jpg  ← خلفية إنشاء الحساب
والمخرجات:
    android/app/src/main/res/drawable-{mdpi,hdpi,xhdpi,xxhdpi}/{login,signup}_bg.jpg
"""
import os
import sys

try:
    from PIL import Image
except ImportError:
    print("✗ يحتاج Pillow:  python3 -m pip install --break-system-packages pillow")
    sys.exit(1)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
MASTERS = ROOT + "/design/auth"
RES = ROOT + "/android/app/src/main/res"

# عرض الهاتف المرجعي 360dp × معامل الكثافة (xxxhdpi يُترك: أندرويد يكبّر
# xxhdpi بنفس النتيجة وبحجم ملف أقل — يُضاف هنا لو احتجناه لاحقاً).
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3)]
BASE_WIDTH_DP = 360

JOBS = [("login_bg", "login_bg_master.jpg"), ("signup_bg", "signup_bg_master.jpg")]


def main() -> int:
    total = 0
    for out_name, master_name in JOBS:
        path = os.path.join(MASTERS, master_name)
        if not os.path.exists(path):
            print(f"✗ الصورة الأصلية مفقودة: {path}")
            return 1
        src = Image.open(path).convert("RGB")
        w, h = src.size
        print(f"\n{master_name}  ({w}×{h} · نسبة {w / h:.3f})")
        for density, factor in DENSITIES:
            target_w = round(BASE_WIDTH_DP * factor)
            target_h = round(target_w * h / w)
            folder = os.path.join(RES, f"drawable-{density}")
            os.makedirs(folder, exist_ok=True)
            out = os.path.join(folder, out_name + ".jpg")
            src.resize((target_w, target_h), Image.LANCZOS).save(
                out, "JPEG", quality=88, optimize=True, progressive=True
            )
            size = os.path.getsize(out)
            total += size
            print(f"   drawable-{density:<8} {target_w:4d} × {target_h:3d}   {size / 1024:6.1f} KB")
    print(f"\n✔ الإجمالي: {total / 1024:.1f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
