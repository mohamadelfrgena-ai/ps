#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
يولّد **خلفيات الشاشات** بمقاسات الهاتف — مصدر واحد للحقيقة.

لماذا مولّد؟
    الصور الأصلية عريضة (1920×1080 · 16:9)، وعرض الهاتف بوحدة البكسل يتغيّر
    بتغيّر الكثافة (360dp × معامل الكثافة = 360 · 540 · 720 · 1080). نسخة
    واحدة تُكبَّر على كل الأجهزة ⇒ إما تمطيط وتشويه، أو حجم ملف ضخم بلا فائدة.
    المولّد يُنتج **نسخة مضبوطة لكل كثافة** ⇒ حِدّة كاملة بأقلّ حجم.

الاستخدام:
    python3 tools/make_auth_backgrounds.py

المصادر (الصور الأصلية بجودتها الكاملة):
    design/auth/login_bg_master.jpg     ← خلفية تسجيل الدخول
    design/auth/signup_bg_master.jpg    ← خلفية إنشاء الحساب
    design/splash/splash_bg_master.jpg  ← خلفية شاشة الترحيب (الإطلاق)

المخرجات:
    drawable-{mdpi,hdpi,xhdpi,xxhdpi}/login_bg.jpg     (لوحة 16:9 أعلى الشاشة)
    drawable-{mdpi,hdpi,xhdpi,xxhdpi}/signup_bg.jpg    (لوحة 16:9 أعلى الشاشة)
    drawable-{mdpi,hdpi,xhdpi,xxhdpi}/splash_bg.jpg    (قصّ طولي من منتصف الصورة)

ملاحظة عن الصورة القديمة لشاشة الترحيب:
    محفوظة كما هي في `drawable-nodpi/splash_bg_legacy.jpg` (والصورة الأصلية
    في `design/splash/splash_bg_old_852x1356.jpg`) — الإرجاع إليها بنقرة:
    بدّل `R.drawable.splash_bg` بـ`R.drawable.splash_bg_legacy` في
    SplashScreen.kt.
"""
import os
import sys

try:
    from PIL import Image, ImageEnhance, ImageFilter
except ImportError:
    print("✗ يحتاج Pillow:  python3 -m pip install --break-system-packages pillow")
    sys.exit(1)

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = ROOT + "/android/app/src/main/res"

# عرض الهاتف المرجعي 360dp × معامل الكثافة
DENSITIES = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3)]
BASE_WIDTH_DP = 360

# نسبة شاشة الهاتف الحديثة: 9:19.5 (الطول = العرض × 2.1667).
# تُبقي هامشاً معقولاً للأجهزة الأقصر (9:16) والأطول (9:21) ⇒ القصّ عند
# التشغيل (Crop · Center) يقتطع أطرافاً صغيرة لا تُخلّ بالصورة.
PHONE_ASPECT = 9 / 19.5

AUTH_JOBS = [
    ("login_bg", "auth/login_bg_master.jpg"),
    ("signup_bg", "auth/signup_bg_master.jpg"),
]
SPLASH_MASTER = "splash/splash_bg_master.jpg"


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


def splash_job() -> int:
    """
    خلفية شاشة الترحيب: **قصّ طولي من منتصف الصورة الأصلية** بمقاس الهاتف.

    طلب المالك (حرفياً): «صورة شاشة الترحيب قصّها حتى تناسب مقاسات شاشة الهاتف»
    — والصورة المرفقة هي نفسها الأصل `splash_bg_master.jpg` (مطابقة بايتاً ببايت).

    لماذا قصّ لا تركيب؟
        الأصل 1920×1080 (16:9) والهاتف 9:19.5. القصّ المركزي إلى نسبة الهاتف
        يُظهر عموداً بعرض **498** بكسل من أصل 1920 (26%) بكامل ارتفاعه 1080 —
        وهذا العمود يقع على المجموعة كاملةً: الأريكة والطاولة في الوسط،
        والبطلان أمامهما، والبطل ذو القبعة في يمين-وسط الكادر، والأشجار
        والزهور على الجانبين. فالناتج كادر طولي طبيعي: بلا فراغ أسود، بلا
        تغبيش، بلا خطّ فاصل، ولقطة المالك سليمة التركيب.

    ولماذا المنتصف تحديداً؟ لأن موضع العمود الأمثل = (1920 − 498) ÷ 2 = **711**
        وهو منتصف الصورة تماماً ⇒ لا رقم سحري في الكود: قصّ مركزي صِرف.

    الجودة: 1080 → 2340 تكبير ×2.17 (لأن أطول شاشة هاتف هي 2340 بكسل)،
        ويُخفَّف حدّته بـUnsharpMask خفيف (radius=2 · percent=55) فيبدو حاداً
        على الجهاز بلا هالة (halo). والبكسل الفعلي للأصل كامل: لا اقتطاع
        للارتفاع إطلاقاً (1080 من 1080).
    """
    src = Image.open(os.path.join(ROOT, "design", SPLASH_MASTER)).convert("RGB")
    w, h = src.size
    crop_w = min(w, round(h * PHONE_ASPECT))          # نافذة بنسبة الهاتف
    left = (w - crop_w) // 2                           # 711 للأصل 1920×1080
    window = src.crop((left, 0, left + crop_w, h))
    print(f"\n{SPLASH_MASTER}  ({w}×{h} · نسبة {w / h:.3f})  ← قصّ طولي")
    print(f"   نافذة القص: {crop_w}×{h} من ({left}،0) — المنتصف تماماً")
    total = 0
    for density, factor in DENSITIES:
        out_w = round(BASE_WIDTH_DP * factor)
        out_h = round(out_w / PHONE_ASPECT)
        shot = window.resize((out_w, out_h), Image.LANCZOS)
        shot = shot.filter(ImageFilter.UnsharpMask(radius=2, percent=55, threshold=3))
        folder = os.path.join(RES, f"drawable-{density}")
        os.makedirs(folder, exist_ok=True)
        out = os.path.join(folder, "splash_bg.jpg")
        shot.save(out, "JPEG", quality=88, optimize=True, progressive=True)
        size = os.path.getsize(out)
        total += size
        print(f"   drawable-{density:<8} {out_w:4d} × {out_h:4d}   {size / 1024:6.1f} KB")
    return total


def main() -> int:
    for out_name, master in AUTH_JOBS:
        path = os.path.join(ROOT, "design", master)
        if not os.path.exists(path):
            print(f"✗ الصورة الأصلية مفقودة: {path}")
            return 1
    if not os.path.exists(os.path.join(ROOT, "design", SPLASH_MASTER)):
        print(f"✗ الصورة الأصلية مفقودة: design/{SPLASH_MASTER}")
        return 1

    total = 0
    for out_name, master in AUTH_JOBS:
        total += band_job(out_name, master)
    total += splash_job()
    print(f"\n✔ الإجمالي: {total / 1024:.1f} KB")
    return 0


if __name__ == "__main__":
    sys.exit(main())
