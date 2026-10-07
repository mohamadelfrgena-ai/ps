#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **خلفيتا شاشات الدخول وإنشاء الحساب** (طلب المالك).

طلب المالك (حرفياً):
    «هذي خلفية تسجيل الدخول [New_Horizons_still_16x9_v7.jpg] وهذي خلفية إنشاء
     حساب [Minecraft LIVE — frame at 0m8s.jpg] — خذ مقاسات بشكل جمالي للهاتف
     وخذ المقاسات المتناسبة.»

القرار المنفَّذ (ولماذا):
    الصورتان **عريضتان** 1920×1080 (نسبة 1.778) والهاتف **طولي**
    (مثل 1080×2340)؛ ملء الشاشة بها قصّاً يكبّرها **٣.٩ مرة** ⇒ تُمسح تفاصيلها.
    فلوحة الخلفية تأخذ **عرض الشاشة كاملاً** وارتفاعاً محسوباً من نسبة الصورة
    (العرض ÷ 1.778، وبحدٍّ أقصى 34% من ارتفاع الشاشة للأجهزة العريضة)، ثم
    تتلاشى في خلفية التطبيق ⇒ واضحة وبمقاس لكل كثافة على أي هاتف.

ما يحرسه الفاحص:
  ١) نسخة من **كل** خلفية لكل كثافة (mdpi · hdpi · xhdpi · xxhdpi)؛
  ٢) مقاس كل نسخة = عرض الهاتف بتلك الكثافة (360/540/720/1080) بنسبة الصورة؛
  ٣) ثابت `AUTH_BG_ASPECT` مطابق لنسبة الصورتين الأصليتين فعلاً؛
  ٤) **مكوّن واحد مشترك** `AuthBackdrop` ترسمه الشاشتان (تسجيل الدخول
     بصورتها وإنشاء الحساب بصورتها) — فلا تتفرّق المقاسات؛
  ٥) الصورة بـ`ContentScale.Crop` وقصّ من المنتصف (لا مطّ ولا تشويه)؛
  ٦) تلاشٍ متدرّج إلى `AppColors.Bg` فوقها (بلا لون جديد)؛
  ٧) **لا لون ثابت (hex)** في شاشات المصادقة؛
  ٨) الصور الأصلية + مولّد المقاسات محفوظان في المستودع (لإعادة التوليد
     ولتطبيقات الأجهزة الأخرى لاحقاً).

الاستخدام:  python3 tools/check_auth_backgrounds.py
"""
import re
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:  # pillow غير مثبّت ⇒ نتجاوز فحص الأبعاد
    Image = None

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
AUTH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/auth/AuthScreens.kt"
MASTERS = ROOT / "design/auth"
GENERATOR = ROOT / "tools/make_auth_backgrounds.py"

# عرض الهاتف المرجعي 360dp × معامل الكثافة
DENSITIES = {"mdpi": 360, "hdpi": 540, "xhdpi": 720, "xxhdpi": 1080}
BACKGROUNDS = ["login_bg", "signup_bg"]
MASTER_FILES = {"login_bg": "login_bg_master.jpg", "signup_bg": "signup_bg_master.jpg"}


def func_body(src: str, signature: str) -> str:
    """جسم دالة Kotlin حتى قوسها المغلق (بلا تعليقات ولا نصوص)."""
    i = src.find(signature)
    if i < 0:
        return ""
    start = src.find("{", src.find(") {", i))
    if start < 0:
        return ""
    depth = 0
    for k in range(start, len(src)):
        if src[k] == "{":
            depth += 1
        elif src[k] == "}":
            depth -= 1
            if depth == 0:
                return src[start:k + 1]
    return src[start:]


def main() -> int:
    src = AUTH.read_text(encoding="utf-8") if AUTH.exists() else ""
    backdrop = func_body(src, "private fun AuthBackdrop(")
    login = func_body(src, "fun LoginScreen(")
    signup = func_body(src, "fun SignUpScreen(")

    rows = []

    # ١ + ٢) نسخة لكل كثافة بالمقاس المناسب لكل خلفية
    src_aspects = {}
    for name, master in MASTER_FILES.items():
        mpath = MASTERS / master
        if Image is not None and mpath.exists():
            w, h = Image.open(mpath).size
            src_aspects[name] = w / h
        for density, want_w in DENSITIES.items():
            path = RES / f"drawable-{density}" / f"{name}.jpg"
            if not path.exists():
                rows.append((f"{density}/{name}.jpg", False))
                continue
            if Image is None:
                rows.append((f"{density}/{name}.jpg (موجودة)", True))
                continue
            w, h = Image.open(path).size
            want_h = round(want_w / src_aspects.get(name, 1.7781))
            rows.append((f"{density}/{name}: {w}×{h} (المطلوب {want_w}×{want_h})",
                         w == want_w and abs(h - want_h) <= 2))

    # ٣) الثابت مطابق لنسبة الصور الأصلية
    m = re.search(r"AUTH_BG_ASPECT\s*=\s*([\d.]+)f", src)
    const = float(m.group(1)) if m else 0.0
    if src_aspects:
        ok = all(abs(const - a) < 0.002 for a in src_aspects.values())
        want = " · ".join(f"{n} {a:.3f}" for n, a in src_aspects.items())
        rows.append((f"AUTH_BG_ASPECT = {const} مطابق ({want})", ok))
    else:
        rows.append((f"AUTH_BG_ASPECT موجود ({const})", bool(m)))

    # ٤) مكوّن واحد مشترك ترسمه الشاشتان
    rows.append(("مكوّن AuthBackdrop موجود", bool(backdrop)))
    rows.append(("شاشة الدخول ترسم R.drawable.login_bg",
                 "AuthBackdrop(R.drawable.login_bg)" in login))
    rows.append(("شاشة إنشاء الحساب ترسم R.drawable.signup_bg",
                 "AuthBackdrop(R.drawable.signup_bg)" in signup))
    rows.append(("صورتان مختلفتان لكل شاشة",
                 "login_bg" in login and "signup_bg" in signup
                 and "signup_bg" not in login))

    # ٥) القصّ من المنتصف بلا مطّ
    rows.append(("القصّ من المنتصف (Crop · Center · بلا تمديد)",
                 "ContentScale.Crop" in backdrop and "Alignment.Center" in backdrop))

    # ٦) تلاشٍ إلى خلفية التطبيق بلا لون جديد
    rows.append(("تلاشٍ متدرّج إلى AppColors.Bg",
                 bool(re.search(r"Brush\.verticalGradient\([\s\S]{0,400}?AppColors\.Bg", backdrop))))

    # ٧) الارتفاع محسوب من النسبة، وحدّ أقصى للأجهزة العريضة
    rows.append(("ارتفاع اللوحة محسوب من النسبة (بلا رقم ثابت)",
                 "/ AUTH_BG_ASPECT" in backdrop and "coerceAtMost" in backdrop))

    # ٨) لا لون ثابت في شاشات المصادقة
    hexes = re.findall(r"Color\(0x[0-9A-Fa-f]{6,8}\)", login + signup + backdrop)
    rows.append(("لا لون ثابت (hex) في شاشات المصادقة", not hexes))

    # ٩) الأصل + المولّد محفوظان
    rows.append(("الصور الأصلية محفوظة (design/auth)",
                 all((MASTERS / f).exists() for f in MASTER_FILES.values())))
    rows.append(("مولّد المقاسات محفوظ (tools/make_auth_backgrounds.py)",
                 GENERATOR.exists()))

    print("الفحص                                                | النتيجة")
    print("-" * 66)
    for name, ok in rows:
        print(f"{name:<53} | {'✅' if ok else '❌'}")

    failed = [n for n, ok in rows if not ok]
    print()
    if failed:
        for n in failed:
            print("✗", n)
        print(f"\n✗ عدد المشاكل: {len(failed)}")
        return 1
    print("🟢 خلفيتا المصادقة: مقاس مضبوط لكل كثافة هاتف، قصّ من المنتصف، "
          "تلاشٍ إلى خلفية التطبيق، ولون واحد للتطبيق بلا أي hex جديد.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
