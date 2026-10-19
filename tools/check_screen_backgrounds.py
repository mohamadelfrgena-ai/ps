#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: الأصول والخلفيات بعد اعتماد SPEC_AUTH_SETTINGS.md.

القواعد:
  ١) شاشة الدخول تستخدم `login_bg.jpg`، وإنشاء الحساب يستخدم `signup_bg.jpg`؛
     كلاهما يشتركان برأس واحد يقبل الصورة كـ resource، مع أيقونة التطبيق والقصّ المركزي والتلاشي الداكن؛
  ٢) ارتفاع الرأس 210dp كما في §1/§2، والأصل الكامل موجود في drawable-nodpi؛
  ٣) مقاسات الكثافات تبقى صالحة للأماكن التي تستخدم `login_bg` و`signup_bg`؛
  ٤) شاشة البداية تستخدم صورة الكهف الطولية الجديدة وحدها، ولا تبقي موارد
     التصميم السابق أو مولّده داخل التطبيق.

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
SPLASH_SCENE = RES / "drawable-nodpi/splash_cave_chase.jpg"
APP_ICON = RES / "drawable-nodpi/app_icon.png"
PNG_SIGNATURE = bytes((137, 80, 78, 71))
OLD_SPLASH_RESOURCES = [
    RES / "drawable-nodpi/splash_bg_legacy.jpg",
    RES / "drawable-nodpi/splash_bg_original.jpg",
    RES / "drawable-nodpi/splash_timeline_bg.jpg",
    RES / "drawable-nodpi/splash_logo.png",
    RES / "drawable-nodpi/minecraft_wordmark.png",
    *(RES / f"drawable-{density}" / "splash_bg.jpg" for density in DENSITY_WIDTH),
]


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
    gen = GENERATOR.read_text(encoding="utf-8") if GENERATOR.exists() else ""
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
    rows.append(("رأس تسجيل الدخول يستخدم login_bg الافتراضية",
                 bool(header) and "AuthSettingsHeader(onBack = onBack)" in login
                 and "backgroundRes: Int = R.drawable.login_bg" in auth_ui))
    rows.append(("رأس إنشاء الحساب يختار signup_bg صراحةً",
                 "AuthSettingsHeader(onBack = onBack, backgroundRes = R.drawable.signup_bg)" in signup))
    rows.append(("الترويسة ترسم الصورة المحددة وتستخدم app_icon",
                 "painterResource(backgroundRes)" in header and "R.drawable.app_icon" in header))
    rows.append(("قصّ مركزي بلا مطّ (ContentScale.Crop)", "ContentScale.Crop" in header))
    rows.append(("تلاشٍ إلى خلفية المصادقة عند 25% ثم 96%",
                 "0.0f to AppColors.AuthBg.copy(alpha = 0.25f)" in header
                 and "0.96f to AppColors.AuthBg" in header))
    rows.append(("ارتفاع الترويسة 210dp من رمز الوثيقة",
                 "Spec.authHeaderH" in header and "val authHeaderH = 210.dp" in spec))
    rows.append(("لا لون ثابت (hex) في شاشات المصادقة",
                 not re.findall(r"Color\(0x[0-9A-Fa-f]{6,8}\)", login + signup + header)))

    # ── ٣) شاشة البداية الجديدة: أصل طولي واحد بلا موارد قديمة ──────────
    scene_size = size_of(SPLASH_SCENE)
    rows.append(("صورة الكهف الجديدة موجودة بمقاس 1080×1920",
                 scene_size == (1080, 1920)))
    rows.append(("صورة البداية مضغوطة بلا تضخيم غير ضروري (أقل من 300KB)",
                 SPLASH_SCENE.exists() and SPLASH_SCENE.stat().st_size < 300 * 1024))
    rows.append(("SplashScreen تستخدم صورة الكهف الجديدة وحدها",
                 "R.drawable.splash_cave_chase" in splash
                 and "ContentScale.Crop" in splash))
    rows.append(("ألوان DREAM/BUILD/CREATE مأخوذة من ضوء الكهف والماس واللافا",
                 all(token in splash for token in (
                     "SceneText = Color(0xFFF5F8FC)",
                     "SceneDiamond = Color(0xFF36D7E0)",
                     "SceneLava = Color(0xFFFFB12B)",
                 ))))
    rows.append(("الكلمات الثلاث تظهر بالتتابع في التصميم الجديد",
                 all(f"R.string.{key}" in splash for key in (
                     "splash_dream_it", "splash_build_it", "splash_create_it"
                 ))
                 and splash.count("SplashSloganLine(") >= 4))
    rows.append(("الحركة الجديدة تشمل كشف المشهد والتقريب الهادئ واللمعة",
                 "sceneProgress.animateTo(" in splash
                 and "lerp(1.09f, 1f, sceneProgress.value)" in splash
                 and "drawFourPointSparkle(SceneLava)" in splash))
    rows.append(("أيقونة التطبيق باقية ضمن هوية الشاشة الجديدة",
                 APP_ICON.exists() and APP_ICON.read_bytes()[:4] == PNG_SIGNATURE
                 and "R.drawable.app_icon" in splash))

    old_present = [path.relative_to(ROOT).as_posix() for path in OLD_SPLASH_RESOURCES if path.exists()]
    rows.append(("موارد شاشة البداية القديمة محذوفة من التطبيق", not old_present))
    rows.append(("مولّد خلفيات المصادقة لا يعيد إنشاء Splash القديم",
                 GENERATOR.exists()
                 and "def splash_job(" not in gen
                 and "splash_bg_master" not in gen))
    rows.append(("مصادر تصميم Splash القديمة محذوفة",
                 not (DESIGN / "splash/splash_bg_master.jpg").exists()
                 and not (DESIGN / "splash/splash_bg_old_852x1356.jpg").exists()))

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
    print("🟢 خلفيات المصادقة سليمة · شاشة البداية تستخدم صورة الكهف الطولية الجديدة "
          "مع ألوان الضوء/الماس/اللافا · وكل موارد التصميم السابق محذوفة.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
