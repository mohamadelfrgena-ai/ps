#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
حارس حالة الدخول — يمنع عودة السلوك الذي شكا منه المالك:

  «لما أسجّل، لازم التطبيق بالكامل يعرف أني سجّلت دخول أو أنشأت حساب،
   لأنه لما أضغط على الإعدادات أو ملف شخصي تظهر لي صفحة التسجيل مرة أخرى.»

الأسباب التي عولجت (ولا يجوز أن تعود):

  1) **طرد قسري إلى شاشة الدخول**: شاشة الملف الشخصي كانت تنفّذ
     `LaunchedEffect(signedIn) { onLogin() }` بمجرّد أن تكون الحالة «بلا حساب»
     ⇒ المستخدم يُقتلع من التبويب. المعتمد الآن: شاشة ضيف واضحة بزرّين.

  2) **إنشاء حساب بتأكيد بريد**: `supabase/signup` لا يُرجع جلسة حين يكون
     «Confirm email» مفعّلاً، فيبقى المستخدم خارج التطبيق. المعتمد الآن: بطاقة
     تأكيد صريحة + «أكّدت بريدي — سجّلني» + «أعِد إرسال الرابط».

  3) **جلسة ميتة تُترك**: توكن تحديث مرفوض من الخادم (400/401) يجب أن يُنظَّف
     فوراً كي لا يبقى المستخدم في «يظنّ نفسه مسجّلاً» وكل طلب يفشل.

  4) **قراءة مشفّرة تُسقط الجلسة عند الفشل**: فشل فكّ تشفير المخزن (Keystore)
     يجب أن يُعالَج بلا انهيار وبلا حالة غامضة.

  5) **الشاشات التي تعتمد على الحساب** يجب أن تُحدّث نفسها عند تغيّر حالة
     الدخول (`signedIn` ضمن مفاتيح التحميل)، وإلا عرضت بيانات ضيف بعد الدخول.

الاستخدام:
    python3 tools/check_auth_state.py        # 0 = سليم · 1 = عاد أحد الأسباب
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

problems: list[str] = []


def read(rel: str) -> str:
    path = os.path.join(SRC, rel)
    if not os.path.exists(path):
        problems.append(f"ملف مفقود: {rel}")
        return ""
    return io.open(path, encoding="utf-8").read()


# 1) لا طرد قسري إلى شاشة الدخول عند غياب الحساب
for rel in ("ui/profile/ProfileScreens.kt", "ui/settings/SettingsScreen.kt"):
    text = read(rel)
    code = re.sub(r"(?m)^\s*(//|\*|/\*).*$", "", text)   # بلا تعليقات
    if re.search(r"LaunchedEffect\([^)]*signedIn[^)]*\)\s*\{\s*onLogin\(\)", code):
        problems.append(f"{rel}: عاد الطرد القسري إلى شاشة الدخول (LaunchedEffect + onLogin)")
    if rel.endswith("ProfileScreens.kt"):
        if "GuestProfileScreen" not in text:
            problems.append("ui/profile/ProfileScreens.kt: شاشة الضيف مفقودة")
        if "profile_guest_title" not in text:
            problems.append("ui/profile/ProfileScreens.kt: شاشة الضيف بلا عنوان من الموارد")

# 1ب) بعد تسجيل الخروج: لا شاشة دخول مفروضة
prof = read("ui/profile/ProfileScreens.kt")
code_prof = re.sub(r"(?m)^\s*(//|\*|/\*).*$", "", prof)
signout = re.search(r"container\.auth\.signOut\(\)[\s\S]{0,400}?\}\s*\)?", code_prof)
if signout and "onLogin()" in signout.group(0):
    problems.append("ui/profile/ProfileScreens.kt: الخروج يُظهر شاشة الدخول قسراً (العودة للرئيسية كضيف)")

# 1ج) توكن الجهاز يُلغى عند الخروج (وإلا وصلت إشعارات الحساب السابق لجهاز آخر)
nav = read("ui/nav/AppNav.kt")
if "PushRegistrar.onSessionChanged" not in nav and "onSessionChanged(ctx, false)" not in nav:
    problems.append("ui/nav/AppNav.kt: لا إلغاء لتوكن الجهاز عند الخروج (onSessionChanged)")

# 2) إنشاء حساب: معالجة تأكيد البريد
signup = read("ui/auth/AuthScreens.kt")
if "pendingConfirm" not in signup:
    problems.append("ui/auth/AuthScreens.kt: لا معالجة لحالة «الحساب أُنشئ وينتظر تأكيد البريد»")
for key in ("auth_confirm_retry", "auth_confirm_resend", "auth_confirm_hint"):
    if key not in signup:
        problems.append(f"ui/auth/AuthScreens.kt: عنصر {key} مفقود من بطاقة التأكيد")

repo = read("data/repo/AuthRepository.kt")
if "fun resendConfirmation" not in repo:
    problems.append("data/repo/AuthRepository.kt: دالة resendConfirmation مفقودة")
if "container.state.setSignedIn(true)" not in signup:
    problems.append("ui/auth/AuthScreens.kt: الدخول لا يُبلّغ حالة التطبيق (setSignedIn)")

client = read("data/SupabaseClient.kt")
if '"resend"' not in client:
    problems.append("data/SupabaseClient.kt: نقطة إعادة إرسال رابط التأكيد (resend) مفقودة")

# 3) الجلسة الميتة تُنظَّف
if "sessionStore.clear()" not in client:
    problems.append("data/SupabaseClient.kt: لا تنظيف للجلسة عند رفض الخادم توكن التحديث")
if not re.search(r"status == 400 \|\| err\.status == 401", client):
    problems.append("data/SupabaseClient.kt: لا تمييز بين «توكن مرفوض» و«شبكة مقطوعة»")

# 4) قراءة المخزن المشفّر آمنة
store = read("data/SessionStore.kt")
idx = store.find("private fun readFromDisk()")
tail = store[idx: idx + 900] if idx >= 0 else ""
if idx < 0 or "catch" not in tail:
    problems.append("data/SessionStore.kt: قراءة المخزن بلا حماية من فشل فكّ التشفير")

# 5) الشاشات المرتبطة بالحساب تُحدّث نفسها عند تغيّر حالة الدخول
for rel, needle in (
    ("ui/store/StoreScreens.kt", "reloadTick, signedIn"),
    ("ui/account/AccountScreens.kt", "reloadTick, signedIn"),
):
    code = re.sub(r"(?m)^\s*(//|\*|/\*).*$", "", read(rel))
    if needle not in code:
        problems.append(f"{rel}: التحميل لا يعتمد على حالة الدخول ({needle})")

# 6) النصوص موجودة في اللغات الثماني
KEYS = (
    "auth_confirm_title", "auth_confirm_text", "auth_confirm_retry",
    "auth_confirm_resend", "auth_confirm_resent", "auth_confirm_hint",
    "profile_guest_title", "profile_guest_text", "splash_skip",
)
for locale in ("values", "values-ar", "values-pt", "values-es", "values-ru", "values-zh", "values-fr", "values-tr"):
    path = os.path.join(RES, locale, "strings.xml")
    text = io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""
    missing = [k for k in KEYS if f'name="{k}"' not in text]
    if missing:
        problems.append(f"{locale}: نصوص ناقصة → {', '.join(missing)}")

print("حارس حالة الدخول")
print("-" * 52)
print("فُحص: الملف الشخصي · الإعدادات · الإنشاء · الدخول · الجلسة · المتجر · الحساب · ٨ لغات")
print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عاد {len(problems)} سبباً من أسباب «التطبيق لا يعرف أني سجّلت».")
    sys.exit(1)
print("🟢 لا طرد قسري إلى الدخول · تأكيد البريد له مخرَج واضح · الجلسة الميتة تُنظَّف ·")
print("   الشاشات تُحدّث نفسها بعد الدخول · النصوص الثمانية مكتملة.")
