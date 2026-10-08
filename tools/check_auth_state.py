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

  3) **خروج تلقائي غير مرغوب**: رفض توكن التحديث لا يمسح الجلسة محلياً؛
     لا يُسجَّل خروج صاحب الحساب إلا عند ضغطه على زر الخروج.

  4) **قراءة مشفّرة تُسقط الجلسة عند الفشل**: فشل فكّ تشفير المخزن (Keystore)
     يُعالَج بلا انهيار، ولا يحذف بيانات الجلسة تلقائياً.

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
            problems.append("ui/profile/ProfileScreens.kt: شاشة الدخول/الإنشاء مفقودة")
        if "profile_guest_title" not in text:
            problems.append("ui/profile/ProfileScreens.kt: شاشة الدخول/الإنشاء بلا عنوان من الموارد")
        start = text.find("private fun GuestProfileScreen(")
        end = text.find("/**\n * الشاشة A", start)
        guest_screen = text[start:end] if start >= 0 and end > start else ""
        if not guest_screen:
            problems.append("ui/profile/ProfileScreens.kt: تعذّر فحص شاشة الدخول/الإنشاء")
        elif "AnonymousAvatarImage(" in guest_screen:
            problems.append("ui/profile/ProfileScreens.kt: صورة المجهول ما زالت ظاهرة فوق أزرار الدخول/الإنشاء")

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

# 3) لا خروج تلقائياً: المسح محصور بدالة الخروج الصريح
refresh_start = client.find("suspend fun refreshSession()")
logout_start = client.find("suspend fun signOut()", refresh_start)
refresh_body = client[refresh_start:logout_start] if refresh_start >= 0 and logout_start > refresh_start else ""
logout_body = client[logout_start:client.find("suspend fun sendRecovery", logout_start)] if logout_start >= 0 else ""
if "sessionStore.clear()" in refresh_body:
    problems.append("data/SupabaseClient.kt: رفض التوكن يمسح الجلسة تلقائياً")
if "sessionStore.clear()" not in logout_body:
    problems.append("data/SupabaseClient.kt: الخروج الصريح لا يمسح الجلسة")

# 4) قراءة المخزن المشفّر آمنة وغير هدّامة
store = read("data/SessionStore.kt")
idx = store.find("private fun readFromDisk()")
end = store.find("\n    fun clear()", idx) if idx >= 0 else -1
tail = store[idx:end] if idx >= 0 and end > idx else ""
if idx < 0 or "catch" not in tail:
    problems.append("data/SessionStore.kt: قراءة المخزن بلا حماية من فشل فكّ التشفير")
if "prefs.edit().clear()" in tail:
    problems.append("data/SessionStore.kt: فشل فكّ التشفير يحذف بيانات الجلسة تلقائياً")

# 5) تغيير اللغة لا يعيد إنشاء النشاط ولا يلمس جلسة الحساب
settings = read("ui/settings/SettingsScreen.kt")
main_activity = read("MainActivity.kt")
if "applyAppLocale(language.code)" not in settings:
    problems.append("ui/settings/SettingsScreen.kt: تغيير اللغة لا يحدّث Compose مباشرة")
if "recreate()" in settings:
    problems.append("ui/settings/SettingsScreen.kt: تغيير اللغة ما زال يعيد إنشاء النشاط")
if "LocalConfiguration provides localizedConfiguration" not in main_activity:
    problems.append("MainActivity.kt: سياق موارد اللغة غير متاح فوراً لـCompose")

# 6) الشاشات المرتبطة بالحساب تُحدّث نفسها عند تغيّر حالة الدخول
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
guest_user_titles = {
    "values": "You are browsing as a user",
    "values-ar": "أنت تتصفّح كمستخدم",
    "values-pt": "Está a navegar como utilizador",
    "values-es": "Estás navegando como usuario",
    "values-ru": "Вы просматриваете как пользователь",
    "values-zh": "你正在以用户身份浏览",
    "values-fr": "Vous naviguez en tant qu’utilisateur",
    "values-tr": "Kullanıcı olarak göz atıyorsunuz",
}
for locale in ("values", "values-ar", "values-pt", "values-es", "values-ru", "values-zh", "values-fr", "values-tr"):
    path = os.path.join(RES, locale, "strings.xml")
    text = io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""
    missing = [k for k in KEYS if f'name="{k}"' not in text]
    if missing:
        problems.append(f"{locale}: نصوص ناقصة → {', '.join(missing)}")
    expected = guest_user_titles[locale]
    if f'<string name="profile_guest_title">{expected}</string>' not in text:
        problems.append(f"{locale}: عنوان غير المسجّل لا يستخدم كلمة user/مستخدم")

print("حارس حالة الدخول")
print("-" * 52)
print("فُحص: الملف الشخصي · الإعدادات · الإنشاء · الدخول · الجلسة · تبديل اللغة · المتجر · ٨ لغات")
print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عاد {len(problems)} سبباً من أسباب «التطبيق لا يعرف أني سجّلت».")
    sys.exit(1)
print("🟢 لا طرد قسري إلى الدخول · تغيير اللغة بلا إعادة إنشاء · لا خروج تلقائياً عند تعذّر التجديد ·")
print("   مسح الجلسة محصور بالخروج الصريح ·")
print("   الشاشات تُحدّث نفسها بعد الدخول · تأكيد البريد واضح · النصوص الثمانية مكتملة.")
