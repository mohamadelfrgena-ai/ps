#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **خلفيات الشاشات** — الدخول · إنشاء الحساب · الترحيب (طلب المالك).

طلب المالك (حرفياً):
    «هذي خلفية تسجيل الدخول [New_Horizons_still_16x9_v7.jpg] وهذي خلفية إنشاء
     حساب [Minecraft LIVE — frame at 0m8s.jpg] — خذ مقاسات بشكل جمالي للهاتف
     وخذ المقاسات المتناسبة.»
    «وهذه شاشة الترحيب غيرها وخذ المقاسات الجمالية والمناسبة وأيضاً حافظ على
     الصورة القديمة ربما أقوم بإرجاعها.»
    «صورة شاشة الترحيب قصّها حتى تناسب مقاسات شاشة الهاتف»

القواعد التي يحرسها الفاحص:
  ١) **لوحات المصادقة** (الدخول · إنشاء الحساب): نسخة لكل كثافة بمقاس الهاتف
     (360/540/720/1080 عرضاً) بنسبة الصورة (16:9)؛
  ٢) شاشة الدخول وشاشة إنشاء الحساب **بصورتين مختلفتين** عبر مكوّن واحد
     مشترك `AuthBackdrop`؛
  ٣) **خلفية الترحيب** (splash): **قصّ طولي من منتصف الأصل** بنسبة شاشة الهاتف
     9:19.5، بنسخة لكل كثافة — لا صورة واحدة تُكبَّر، ولا تغبيش ولا تركيب
     بطبقتين (المولّد يقتطع العمود الأوسط بكامل ارتفاع الأصل)؛
  ٤) الصور الأصلية (1920×1080) محفوظة في `design/` والمولّد محفوظ؛
  ٥) **الصورة القديمة لشاشة الترحيب محفوظة كما هي** (`splash_bg_legacy.jpg`
     + أصلها في `design/splash/`) والإرجاع إليها موثّق في الكود — الشرط
     الصريح «ربما أقوم بإرجاعها»؛
  ٦) لا لون ثابت (hex) في شاشات المصادقة، والتلاشي من `AppColors.Bg`.

الاستخدام:  python3 tools/check_screen_backgrounds.py
"""
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
AUTH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/auth/AuthScreens.kt"
SPLASH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/splash/SplashScreen.kt"
DESIGN = ROOT / "design"
GENERATOR = ROOT / "tools/make_auth_backgrounds.py"

DENSITY_WIDTH = {"mdpi": 360, "hdpi": 540, "xhdpi": 720, "xxhdpi": 1080}
BANDS = {"login_bg": "auth/login_bg_master.jpg", "signup_bg": "auth/signup_bg_master.jpg"}
SPLASH_MASTER = DESIGN / "splash/splash_bg_master.jpg"
SPLASH_OLD_MASTER = DESIGN / "splash/splash_bg_old_852x1356.jpg"
SPLASH_LEGACY = RES / "drawable-nodpi/splash_bg_legacy.jpg"
PHONE_ASPECT = 9 / 19.5


def func_body(src: str, signature: str) -> str:
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


def py_func_body(src: str, signature: str) -> str:
    """
    جسم دالة **بايثون** (المولّد مكتوب بايثون لا Kotlin): من سطر التعريف حتى
    أول سطر جديد يبدأ في العمود صفر — وهو أول دالة/جملة تالية.
    """
    i = src.find(signature)
    if i < 0:
        return ""
    m = re.search(r"\n(?=\S)", src[i:])
    return src[i:i + m.start()] if m else src[i:]


def jpeg_size(path: Path):
    """
    أبعاد صورة JPEG بلا أي مكتبة خارجية: نتتبّع علامات JPEG حتى SOF.

    لماذا لا Pillow؟ لأن الفاحص يجب أن يعمل في أي بيئة (سير GitHub مثلاً)
    بلا تثبيت شيء — والاعتماد على مكتبة غير مثبّتة يجعل الفحص يفشل بلا سبب.
    """
    if not path.exists():
        return None
    data = path.read_bytes()
    if data[:2] != b"\xff\xd8":
        return None
    i, n = 2, len(data)
    while i + 9 < n:
        if data[i] != 0xFF:
            i += 1
            continue
        marker = data[i + 1]
        if marker in (0xD8, 0x01) or 0xD0 <= marker <= 0xD7:
            i += 2
            continue
        seg_len = int.from_bytes(data[i + 2:i + 4], "big")
        # SOF0..SOF15 عدا DHT (C4) وJPG (C8) وDAC (CC)
        if 0xC0 <= marker <= 0xCF and marker not in (0xC4, 0xC8, 0xCC):
            height = int.from_bytes(data[i + 5:i + 7], "big")
            width = int.from_bytes(data[i + 7:i + 9], "big")
            return (width, height)
        i += 2 + seg_len
    return None


# توافق: بعض الفحوص كانت تنادي size_of
size_of = jpeg_size


def main() -> int:
    auth = AUTH.read_text(encoding="utf-8") if AUTH.exists() else ""
    splash = SPLASH.read_text(encoding="utf-8") if SPLASH.exists() else ""
    backdrop = func_body(auth, "private fun AuthBackdrop(")
    login = func_body(auth, "fun LoginScreen(")
    signup = func_body(auth, "fun SignUpScreen(")
    rows = []

    # ── ١) لوحات المصادقة: نسخة لكل كثافة بنسبة الصورة ────────────────────
    for name, master in BANDS.items():
        msize = size_of(DESIGN / master)
        if not msize:
            rows.append((f"الأصل {master} موجود", False))
            continue
        aspect = msize[0] / msize[1]
        for density, want_w in DENSITY_WIDTH.items():
            path = RES / f"drawable-{density}" / f"{name}.jpg"
            got = size_of(path)
            if not got:
                rows.append((f"{density}/{name}.jpg موجودة", False))
                continue
            want_h = round(want_w / aspect)
            rows.append((f"{density}/{name}: {got[0]}×{got[1]} (المطلوب {want_w}×{want_h})",
                         got[0] == want_w and abs(got[1] - want_h) <= 2))

    # ── ٢) مكوّن مشترك وصورتان مختلفتان ───────────────────────────────────
    rows.append(("مكوّن AuthBackdrop مشترك", bool(backdrop)))
    rows.append(("الدخول → login_bg · الإنشاء → signup_bg (صورتان مختلفتان)",
                 "AuthBackdrop(R.drawable.login_bg)" in login
                 and "AuthBackdrop(R.drawable.signup_bg)" in signup
                 and "login_bg" not in signup))
    rows.append(("قصّ من المنتصف بلا مطّ (Crop · Center)",
                 "ContentScale.Crop" in backdrop and "Alignment.Center" in backdrop))
    rows.append(("تلاشٍ متدرّج إلى AppColors.Bg",
                 bool(re.search(r"Brush\.verticalGradient\([\s\S]{0,400}?AppColors\.Bg", backdrop))))
    rows.append(("ارتفاع اللوحة محسوب من النسبة (بلا رقم ثابت)",
                 "AUTH_BG_ASPECT" in backdrop and "coerceAtMost" in backdrop))
    rows.append(("لا لون ثابت (hex) في شاشات المصادقة",
                 not re.findall(r"Color\(0x[0-9A-Fa-f]{6,8}\)", login + signup + backdrop)))

    # ── ٣) خلفية الترحيب: ملء شاشة طولي بمقاس كل كثافة ────────────────────
    smaster = size_of(SPLASH_MASTER)
    if not smaster:
        rows.append(("أصل خلفية الترحيب موجود", False))
    else:
        aspect = smaster[0] / smaster[1]
        for density, want_w in DENSITY_WIDTH.items():
            path = RES / f"drawable-{density}" / "splash_bg.jpg"
            got = size_of(path)
            if not got:
                rows.append((f"{density}/splash_bg.jpg موجودة", False))
                continue
            want_h = round(want_w / PHONE_ASPECT)
            rows.append((f"{density}/splash_bg: {got[0]}×{got[1]} (المطلوب {want_w}×{want_h})",
                         got[0] == want_w and abs(got[1] - want_h) <= 2))

    # ── ٣ب) القصّ من المنتصف بلا تركيب/تغبيش (طلب المالك: «قصّها») ──────────
    gen = GENERATOR.read_text(encoding="utf-8") if GENERATOR.exists() else ""
    job = py_func_body(gen, "def splash_job(")
    rows.append(("مولّد الترحيب يقصّ من الأصل (crop) لا يركّب طبقتين",
                 "src.crop(" in job and "paste(" not in job))
    rows.append(("لا تغبيش في خلفية الترحيب (لا GaussianBlur)",
                 bool(job) and "GaussianBlur" not in job))
    rows.append(("القصّ من المنتصف تماماً: (العرض − عرض الهاتف) ÷ 2",
                 "(w - crop_w) // 2" in job))
    rows.append(("الارتفاع كامل بلا اقتطاع (0 → h)",
                 "crop((left, 0, left + crop_w, h))" in job))
    rows.append(("نافذة القص بنسبة الهاتف لا بنسبة الأصل (PHONE_ASPECT)",
                 "round(h * PHONE_ASPECT)" in job))
    rows.append(("تحسين حِدّة بعد التكبير (UnsharpMask)",
                 "UnsharpMask" in job))

    # ── ٤) الصورة القديمة محفوظة (شرط «ربما أقوم بإرجاعها») ───────────────
    legacy = size_of(SPLASH_LEGACY)
    rows.append((f"الصورة القديمة محفوظة كما هي ({legacy[0]}×{legacy[1]})" if legacy
                 else "الصورة القديمة محفوظة كما هي", bool(legacy)))
    if legacy:
        # مطابقة بايتاً ببايت بلا أي مكتبة صور
        same = (SPLASH_OLD_MASTER.exists()
                and SPLASH_LEGACY.read_bytes() == SPLASH_OLD_MASTER.read_bytes())
        rows.append(("نسخة legacy مطابقة للأصل القديم (بايتاً بايت)", same))
        rows.append(("المحفوظ هو الصورة القديمة الطولية (852×1356)",
                     legacy == (852, 1356)))
    rows.append(("الإرجاع موثّق في الكود (splash_bg_legacy)",
                 "splash_bg_legacy" in splash))
    rows.append(("شاشة الترحيب ما زالت تعرض splash_bg",
                 "R.drawable.splash_bg" in splash))

    # ── ٥) المولّد + اللوحات بلا صورة واحدة مكبَّرة ──────────────────────
    rows.append(("مولّد المقاسات محفوظ (tools/make_auth_backgrounds.py)",
                 GENERATOR.exists()))
    rows.append(("لا صورة ترحيب قديمة عالقة في drawable-nodpi",
                 not (RES / "drawable-nodpi/splash_bg.jpg").exists()))

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
    print("🟢 خلفيات الشاشات الثلاث بمقاس كل كثافة هاتف · خلفية الترحيب مقصوصة "
          "من منتصف الأصل بنسبة شاشة الهاتف بلا تغبيش · الصورة القديمة محفوظة "
          "كما هي والإرجاع إليها موثّق.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
