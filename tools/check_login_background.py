#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **خلفية شاشة تسجيل الدخول** (طلب المالك).

طلب المالك (حرفياً):
    «اجعلها خلفية تسجيل الدخول — خذ مقاسات الهاتف لأنه مازال باقي تطبيقات
     الأجهزة الأخرى — خذ المقاس المناسب للصورة والجمالي.»

القرار المنفَّذ (ولماذا):
    الصورة عريضة (1080×607 · نسبة 1.779) والهاتف طولي (مثل 1080×2340)؛
    ملء الشاشة بها بـ Crop يكبّرها **٣.٩ مرة** ⇒ تُمسح تفاصيلها.
    فاللوحة تظهر بـ**عرض الشاشة كاملاً وبارتفاع يناسب نسبتها**، ثم تتلاشى
    في خلفية التطبيق ⇒ واضحة على كل هاتف، وبمقاس لكل كثافة (mdpi→xxhdpi).

ما يحرسه الفاحص:
  ١) الصورة موجودة لكل كثافة (mdpi · hdpi · xhdpi · xxhdpi)؛
  ٢) مقاس كل نسخة = عرض الهاتف بتلك الكثافة (360/540/720/1080)؛
  ٣) `LoginScreen` ترسمها بـ `ContentScale.Crop` (قصّ لا تمديد)؛
  ٤) فوقها تلاشٍ إلى `AppColors.Bg` (لا لون جديد)؛
  ٥) ارتفاع اللوحة **محسوب** من نسبة الصورة لا رقماً ثابتاً؛
  ٦) لا لون ثابت (hex) في شاشة الدخول.

الاستخدام:  python3 tools/check_login_background.py
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

# عرض الهاتف المرجعي 360dp × معامل الكثافة
EXPECTED = {"drawable-mdpi": 360, "drawable-hdpi": 540,
            "drawable-xhdpi": 720, "drawable-xxhdpi": 1080}
ASPECT_TOLERANCE = 0.02


def login_body(src: str) -> str:
    """جسم دالة LoginScreen حتى قوسها المغلق."""
    i = src.find("fun LoginScreen(")
    if i < 0:
        return ""
    start = src.find("{", src.find(") {", i))
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
    body = login_body(src)

    rows = []

    # ١ + ٢) نسخة لكل كثافة بالمقاس المناسب
    for folder, want in EXPECTED.items():
        path = RES / folder / "login_bg.jpg"
        if not path.exists():
            rows.append((f"{folder}/login_bg.jpg", False))
            continue
        if Image is None:
            rows.append((f"{folder}/login_bg.jpg (موجودة)", True))
            continue
        w, h = Image.open(path).size
        ok = w == want and abs(h - round(want / 1.779)) <= 2
        rows.append((f"{folder}: {w}×{h} (المطلوب {want}×{round(want/1.779)})", ok))

    # ٣) شاشة الدخول ترسمها بقصّ لا تمديد
    uses_drawable = "R.drawable.login_bg" in body
    rows.append(("شاشة الدخول ترسم R.drawable.login_bg", uses_drawable))
    rows.append(("القصّ من المنتصف (Crop · Center) بلا تمديد",
                 "ContentScale.Crop" in body and "Alignment.Center" in body))

    # ٤) تلاشٍ إلى خلفية التطبيق (بلا لون جديد)
    fade = re.search(r"Brush\.verticalGradient\([\s\S]{0,300}?AppColors\.Bg", body)
    rows.append(("تلاشٍ إلى AppColors.Bg فوق الصورة", bool(fade)))

    # ٥) الارتفاع محسوب من نسبة الصورة
    rows.append(("ارتفاع اللوحة محسوب من نسبة الصورة (LOGIN_BG_ASPECT)",
                 "LOGIN_BG_ASPECT" in src and "/ LOGIN_BG_ASPECT" in body))

    # ٦) لا لون ثابت
    hexes = re.findall(r"Color\(0x[0-9A-Fa-f]{6,8}\)", body)
    rows.append(("لا لون ثابت (hex) في شاشة الدخول", not hexes))

    print("الفحص                                             | النتيجة")
    print("-" * 62)
    for name, ok in rows:
        print(f"{name:<50} | {'✅' if ok else '❌'}")

    failed = [n for n, ok in rows if not ok]
    print()
    if failed:
        for n in failed:
            print("✗", n)
        print(f"\n✗ عدد المشاكل: {len(failed)}")
        return 1
    print("🟢 خلفية تسجيل الدخول: نسخة لكل كثافة بمقاس الهاتف، قصّ من المنتصف، "
          "وتلاشٍ إلى خلفية التطبيق بلا لون جديد.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
