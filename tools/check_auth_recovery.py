#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: **تدفّق استعادة كلمة المرور** و**الدخول التلقائي بعد التسجيل**.

الأعطال التي يمنع رجوعها (كلها كانت سبب «الرابط يصل ولا يعمل»):

  ١) `sendRecovery` بلا `redirect_to`
     ⇒ يبني Supabase الرابط على «Site URL» فيفتح متصفّحاً لا التطبيق.

  ٢) `acceptRecoveryLink` يقبل صيغة واحدة فقط (access_token داخل #)
     ⇒ تفشل صيغة `token_hash` الحديثة، ويفشل الرابط إن نقله البريد إلى ?.

  ٣) `MainActivity` يحتفظ بالرابط في متغيّر عادي ويقرأه من onCreate فقط
     ⇒ الرابط القادم عبر `onNewIntent` (التطبيق مفتوح أصلاً) لا يصل الواجهة.

  ٤) معالجة الرابط داخل أثر مفتاحه `Unit`
     ⇒ نفس العطل: يتغيّر الرابط بعد الإقلاع فلا يُعالَج.

  ٥) لا فلاتر نوايا للرابط addonstation://recovery في AndroidManifest.

  ٦) بعد الدخول/التسجيل لا ينتقل المستخدم إلى الصفحة الرئيسية.
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java"
MANIFEST = ROOT / "android/app/src/main/AndroidManifest.xml"


def read(rel: str) -> str:
    return (ROOT / rel).read_text(encoding="utf-8")


def main() -> int:
    client = read("android/app/src/main/java/com/addonstation/mobile/data/SupabaseClient.kt")
    activity = read("android/app/src/main/java/com/addonstation/mobile/MainActivity.kt")
    nav = read("android/app/src/main/java/com/addonstation/mobile/ui/nav/AppNav.kt")
    manifest = MANIFEST.read_text(encoding="utf-8")

    checks = []

    # ١) redirect_to في إرسال رابط الاستعادة
    m = re.search(r"fun sendRecovery.*?\n    \}", client, re.S)
    block = m.group(0) if m else ""
    checks.append((
        "sendRecovery يرسل redirect_to (وإلا فُتح الموقع لا التطبيق)",
        "redirect_to" in block and "RECOVERY_REDIRECT" in block,
    ))

    # ٢) قبول كل صيغ الرابط
    m2 = re.search(r"fun acceptRecoveryLink.*?\n    \}\n", client, re.S)
    link_block = m2.group(0) if m2 else ""
    checks.append((
        "acceptRecoveryLink يقبل access_token و token_hash",
        "access_token" in link_block and "token_hash" in link_block,
    ))
    checks.append((
        "acceptRecoveryLink يقرأ الجزء بعد # وبعد ? معاً",
        "substringAfter('#'" in client and "substringAfter('?'" in client,
    ))
    checks.append((
        "حمولة JWT تملأ معرّف المستخدم والبريد (لا جلسة ناقصة)",
        "fun jwtClaims" in client and '"sub"' in client,
    ))

    # ٣) MainActivity: حالة قابلة للملاحظة + onNewIntent
    checks.append((
        "MainActivity يحتفظ بالرابط في حالة Compose (لا متغيّراً عادياً)",
        "MutableState<String?> = mutableStateOf(null)" in activity,
    ))
    m3 = re.search(r"fun onNewIntent.*?\n    \}", activity, re.S)
    new_intent = m3.group(0) if m3 else ""
    checks.append((
        "onNewIntent يحدّث حالة الرابط (التطبيق مفتوح أصلاً)",
        "recoveryLink.value = linkOf(intent)" in new_intent,
    ))

    # ٤) أثر مستقل مفتاحه الرابط
    checks.append((
        "AppNav يعالج الرابط في أثر مفتاحه recoveryLink",
        "LaunchedEffect(recoveryLink)" in nav,
    ))

    # ٥) فلتر النوايا في البيان
    checks.append((
        "AndroidManifest يستقبل addonstation://recovery",
        'android:scheme="addonstation"' in manifest and 'android:host="recovery"' in manifest,
    ))

    # ٦) الصفحة الرئيسية بعد الدخول والتسجيل
    login = re.search(r"LoginScreen\(.*?\n                \)", nav, re.S)
    signup = re.search(r"SignUpScreen\(.*?\n                \)", nav, re.S)
    checks.append((
        "بعد الدخول ⟵ الصفحة الرئيسية (MainTab.Home)",
        bool(login) and "MainTab.Home" in login.group(0),
    ))
    checks.append((
        "بعد إنشاء الحساب ⟵ الصفحة الرئيسية (MainTab.Home)",
        bool(signup) and "MainTab.Home" in signup.group(0),
    ))

    print("الفحص | النتيجة")
    print("----------------------------------------------")
    bad = [label for label, ok in checks if not ok]
    for label, ok in checks:
        print(f"{label:<58} | {'✅' if ok else '❌'}")

    if bad:
        print(f"\n✗ عدد المشاكل: {len(bad)}")
        return 1
    print("\n🟢 تدفّق الاستعادة سليم: redirect_to · كل صيغ الرابط · onNewIntent · "
          "أثر مستقل · فلتر النوايا · والصفحة الرئيسية بعد الدخول والتسجيل.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
