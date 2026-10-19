#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يولّد خلفيتي تسجيل الدخول وإنشاء الحساب بمقاسات الكثافات المختلفة.

شاشة البداية لم تعد جزءاً من هذا المولّد: التصميم الجديد يستخدم الصورة
الطولية الجاهزة `drawable-nodpi/splash_cave_chase.jpg` مباشرةً كي لا يعيد
أي تشغيل للأداة إنشاء موارد Splash القديمة التي طلب المالك حذفها.

الاستخدام:
    python3 tools/make_auth_backgrounds.py

المصادر:
    design/auth/login_bg_master.jpg
    design/auth/signup_bg_master.jpg

المخرجات:
    drawable-{mdpi,hdpi,xhdpi,xxhdpi}/login_bg.jpg
    drawable-{mdpi,hdpi,xhdpi,xxhdpi}/signup_bg.jpg
"""
import os
import sys

try:
    from PIL import Image
except ImportError:
    print("✗ يحتاج Pillow:  python3 -m pip install --break-system-packages pillow")
    sys.exit(1)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = ROOT + "/android/app/src/main/res"

# عرض الهاتف المرجعي 360dp × معامل الكثافة
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3)]
BASE_WIDTH_DP = 360

AUTH_JOBS = [
    ("login_bg", "auth/login_bg_master.jpg"),
    ("signup_bg", "auth/signup_bg_master.jpg"),
]


def band_job(out_name: str, master: str) -> int:
    """لوحة عريضة (16:9) بعرض الشاشة — تُستعمل أعلى شاشات الدخول والإنشاء."""
    src = Image.open(os.path.join(ROOT, "design", master)).convert("RGB")
    w, h = src.size
    print(f"\n{master}  ({w}×{h} · نسبة {w / h:.3f})  ← لوحة بعرض الشاشة")
    total = 0
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
        print(f"   drawable-{density:<8} {target_w:4d} × {target_h:4d}   {size / 1024:6.1f} KB")
    return total


def main() -> int:
    for out_name, master in AUTH_JOBS:
        path = os.path.join(ROOT, "design", master)
        if not os.path.exists(path):
            print(f"✗ الصورة الأصلية مفقودة: {path}")
            return 1
    total = 0
    for out_name, master in AUTH_JOBS:
        total += band_job(out_name, master)
    print(f"\n✔ الإجمالي: {total / 1024:.1f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
