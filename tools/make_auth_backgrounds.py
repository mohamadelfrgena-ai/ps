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
    drawable-{mdpi,hdpi,xhdpi,xxhdpi}/splash_bg.jpg    (ملء شاشة طولي)

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
    ملء شاشة **طولي** لشاشة الترحيب من صورة **عريضة**.

    المشكلة: هاتف طولي (9:19.5) وصورة عريضة (16:9) ⇒ القصّ المباشر يُظهر
    **26% فقط** من عرض الصورة (نحو ربعها) وتُمطَّر البقية خارج الشاشة.

    الحل (تركيب واحد يُخبَز في ملف JPEG واحد — بلا أي تعقيد في Kotlin):
      ١) خلفية ملء الشاشة: الصورة مكبَّرة لتغطية الشاشة، **باهتة ومغبَّشة**
         (GaussianBlur) ومُعتَّمة قليلاً ⇒ لا فراغ أسود ولا شريط جانبي،
         والألوان نفسها تمتدّ على كل الشاشة بإحساس سينمائي.
      ٢) الصورة كاملة (16:9) ظاهرة أعلى الشاشة بعرضها الكامل، **حادّة**
         (UnsharpMask) — فلا يُقتطع من لقطة المالك شيء.
      ٣) تلاشٍ ناعم (تدرّج ألفا) أسفل الصورة ⇒ تذوب في الخلفية بلا خط فاصل.

    الحصيلة: كل الصورة ظاهرة · شاشة ممتلئة بلا فراغ · ومقاس مضبوط لكل كثافة.
    """
    src = Image.open(os.path.join(ROOT, "design", SPLASH_MASTER)).convert("RGB")
    w, h = src.size
    print(f"\n{SPLASH_MASTER}  ({w}×{h} · نسبة {w / h:.3f})  ← ملء شاشة طولي")
    total = 0
    for density, factor in DENSITIES:
        out_w = round(BASE_WIDTH_DP * factor)
        out_h = round(out_w / PHONE_ASPECT)
        canvas_h = out_h

        # ١) الخلفية: تغطية الشاشة ثم تغبيش واعتام خفيف
        cover = max(out_w / w, canvas_h / h)
        big = src.resize((max(out_w, round(w * cover)), max(canvas_h, round(h * cover))), Image.LANCZOS)
        left = (big.width - out_w) // 2
        top = (big.height - canvas_h) // 2
        bg = big.crop((left, top, left + out_w, top + canvas_h))
        bg = bg.filter(ImageFilter.GaussianBlur(radius=out_w / 22))
        bg = ImageEnhance.Brightness(bg).enhance(0.72)

        # ٢) الصورة كاملة أعلى الشاشة بعرضها (16:9) وحادّة
        band_h = round(out_w * h / w)
        band = src.resize((out_w, band_h), Image.LANCZOS)
        band = band.filter(ImageFilter.UnsharpMask(radius=2, percent=58, threshold=3))

        # ٣) قناع تلاشٍ على حافة الصورة السفلية (20% من ارتفاعها)
        feather = max(24, round(band_h * 0.20))
        mask = Image.new("L", (out_w, band_h), 255)
        mpx = mask.load()
        for y in range(band_h - feather, band_h):
            value = int(255 * (1 - (y - (band_h - feather)) / feather))
            for x in range(out_w):
                mpx[x, y] = value
        bg.paste(band, (0, 0), mask)

        folder = os.path.join(RES, f"drawable-{density}")
        os.makedirs(folder, exist_ok=True)
        out = os.path.join(folder, "splash_bg.jpg")
        bg.save(out, "JPEG", quality=88, optimize=True, progressive=True)
        size = os.path.getsize(out)
        total += size
        print(f"   drawable-{density:<8} {out_w:4d} × {canvas_h:4d}   {size / 1024:6.1f} KB"
              f"   (الصورة {out_w}×{band_h} أعلى + خلفية مغبَّشة)")
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
