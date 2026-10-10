#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: الأصول والخلفيات بعد اعتماد SPEC_AUTH_SETTINGS.md.

القواعد:
  ١) صورة الدخول المحددة في المواصفة (`login_bg.jpg`) هي الرأس المشترك للدخول
     وإنشاء الحساب، وتُرسم مع أيقونة التطبيق والقصّ المركزي والتلاشي الداكن؛
  ٢) ارتفاع الرأس 210dp كما في §1/§2، والأصل الكامل موجود في drawable-nodpi؛
  ٣) مقاسات الكثافات القديمة تبقى صالحة للأماكن التي تستخدم `login_bg` و`signup_bg`؛
  ٤) شاشة الترحيب تستخدم خلفية وتسلسل الأصول المرفقة، مع الاحتفاظ
     بخلفية القصّ القديمة كما هي للتوافق والإرجاع.

الاستخدام: python3 tools/check_screen_backgrounds.py
"""
import re
import sys
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
AUTH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/auth/AuthScreens.kt"
AUTH_UI = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/components/AuthSettingsUi.kt"
SPEC = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/theme/Spec.kt"
SPLASH = ROOT / "android/app/src/main/java/com/addonstation/mobile/ui/splash/SplashScreen.kt"
DESIGN = ROOT / "design"
GENERATOR = ROOT / "tools/make_auth_backgrounds.py"

DENSITY_WIDTH = {"mdpi": 360, "hdpi": 540, "xhdpi": 720, "xxhdpi": 1080}
BANDS = {"login_bg": "auth/login_bg_master.jpg", "signup_bg": "auth/signup_bg_master.jpg"}
SPLASH_MASTER = DESIGN / "splash/splash_bg_master.jpg"
SPLASH_OLD_MASTER = DESIGN / "splash/splash_bg_old_852x1356.jpg"
SPLASH_LEGACY = RES / "drawable-nodpi/splash_bg_legacy.jpg"
SPLASH_ORIGINAL = RES / "drawable-nodpi/splash_bg_original.jpg"
SPLASH_LOGO = RES / "drawable-nodpi/splash_logo.png"
APP_ICON = RES / "drawable-nodpi/app_icon.png"
PNG_SIGNATURE = bytes((137, 80, 78, 71))
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
    auth_ui = AUTH_UI.read_text(encoding="utf-8") if AUTH_UI.exists() else ""
    spec = SPEC.read_text(encoding="utf-8") if SPEC.exists() else ""
    splash = SPLASH.read_text(encoding="utf-8") if SPLASH.exists() else ""
    header = func_body(auth_ui, "fun AuthSettingsHeader(")
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

    # ── ٢) ترويسة §1/§2: صورة واحدة login_bg بارتفاع 210dp ────────────────
    nodpi_header = RES / "drawable-nodpi/login_bg.jpg"
    rows.append(("login_bg موجودة في drawable-nodpi (الأصل الكامل)", bool(size_of(nodpi_header))))
    rows.append(("ترويسة AuthSettingsHeader مشتركة بين الدخول والإنشاء",
                 bool(header) and "AuthSettingsHeader(onBack = onBack)" in login
                 and "AuthSettingsHeader(onBack = onBack)" in signup))
    rows.append(("الترويسة تستخدم login_bg + app_icon من المواصفة",
                 "R.drawable.login_bg" in header and "R.drawable.app_icon" in header))
    rows.append(("قصّ مركزي بلا مطّ (ContentScale.Crop)", "ContentScale.Crop" in header))
    rows.append(("تلاشٍ إلى خلفية المصادقة عند 25% ثم 96%",
                 "0.0f to AppColors.AuthBg.copy(alpha = 0.25f)" in header
                 and "0.96f to AppColors.AuthBg" in header))
    rows.append(("ارتفاع الترويسة 210dp من رمز الوثيقة",
                 "Spec.authHeaderH" in header and "val authHeaderH = 210.dp" in spec))
    rows.append(("لا لون ثابت (hex) في شاشات المصادقة",
                 not re.findall(r"Color\(0x[0-9A-Fa-f]{6,8}\)", login + signup + header)))

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
    original_size = size_of(SPLASH_ORIGINAL)
    rows.append(("صورة المشهد المعتمدة موجودة (540×750)",
                 original_size == (540, 750)))
    rows.append(("شعار Minecraft المرفق موجود كـ PNG",
                 SPLASH_LOGO.exists()
                 and SPLASH_LOGO.read_bytes()[:4] == PNG_SIGNATURE))
    rows.append(("أيقونة التطبيق المرفقة موجودة كـ PNG",
                 APP_ICON.exists()
                 and APP_ICON.read_bytes()[:4] == PNG_SIGNATURE))
    rows.append(("SplashScreen تستخدم صورة المشهد المعتمدة",
                 "R.drawable.splash_bg_original" in splash))
    rows.append(("SplashScreen تستخدم شعار Minecraft المرفق",
                 "R.drawable.splash_logo" in splash))

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
    print("🟢 خلفيات المصادقة بمقاس كل كثافة · شاشة البداية تستخدم صورة المشهد "
          "وشعار Minecraft وأيقونة التطبيق من المواصفة المعتمدة · الخلفية القديمة محفوظة للإرجاع.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
