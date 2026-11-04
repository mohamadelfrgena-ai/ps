#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
حارس الإشعارات الحقيقية — يمنع أن تعود المنظومة إلى «تجريبية».

ما الذي يجعل الإشعار **حقيقياً**؟ ثلاثة أعمدة، وكلها محروسة هنا:

  أ) **الطريق لا ينقطع في التطبيق**: صلاحية الإشعارات معلنة، وخدمة FCM مُسجَّلة،
     وطلب الإذن يحدث **مرة واحدة بعد الدخول** (أندرويد 13+ يمنع العرض بلا إذن)،
     وتسجيل الجهاز يتم عند الدخول وعند وصول توكن جديد، ويُلغى عند الخروج.

  ب) **الطابور يُفرَّغ فعلاً**: `supabase/35_push_live.sql` يجدول التشغيل كل
     دقيقة (pg_cron) وينادي دالة الإرسال (pg_net)، ويُرسل الفوري (منشن/ردّ)
     عند الإدراج نفسه. بلا هذا تبقى الرسائل في الطابور حتى النداء اليدوي —
     وهذا بالضبط إحساس «إشعار تجريبي».

  ج) **الإرسال من الخادم لا من التطبيق**: دالة `send-push` تستعمل FCM v1 بمفتاح
     خدمة سرّي، ومفتاح الخدمة **لا يدخل التطبيق أبداً**. أي إشعار يُبنى محلياً
     في التطبيق بلا المرور بالخادم = إشعار تجريبي، ويجب أن يبقى للاختبار فقط.

الاستخدام:
    python3 tools/check_push_live.py     # 0 = سليم · 1 = عاد خلل
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
SUPABASE = os.path.join(ROOT, "supabase")
WORKFLOWS = os.path.join(ROOT, ".github", "workflows")

problems: list[str] = []


def read(path, base=ROOT):
    full = os.path.join(base, path)
    if not os.path.exists(full):
        problems.append(f"ملف مفقود: {path}")
        return ""
    return io.open(full, encoding="utf-8").read()


def no_comments(text):
    return re.sub(r"(?m)^\s*(//|\*|/\*|--).*$", "", text)


# ===========================================================================
# أ) التطبيق
# ===========================================================================
manifest = read("android/app/src/main/AndroidManifest.xml")
for needle, why in (
    ('android.permission.POST_NOTIFICATIONS', "صلاحية الإشعارات غير معلنة (أندرويد 13+ يمنع العرض بلا إذن)"),
    ('.push.PushService', "خدمة استقبال FCM غير مُسجَّلة"),
    ('com.google.firebase.MESSAGING_EVENT', "مرشّح رسائل FCM مفقود في الخدمة"),
):
    if needle not in manifest:
        problems.append(f"AndroidManifest.xml: {why}")

notif = read("push/PushNotifications.kt", SRC)
for needle, why in (
    ("fun configured(", "فحص تهيئة FCM مفقود"),
    ("fun permissionGranted(", "فحص إذن الإشعارات مفقود"),
    ("fun ensureChannels(", "إنشاء القنوات مفقود"),
    ('CHAT = "as_chat"', "قناة الشات مفقودة"),
    ('CONTENT = "as_content"', "قناة المحتوى مفقودة"),
    ('SOCIAL = "as_social"', "قناة الاجتماعي مفقودة"),
    ('ACCOUNT = "as_account"', "قناة الحساب مفقودة"),
    ('REMINDERS = "as_reminders"', "قناة التذكير مفقودة"),
    ("EXTRA_ROUTE", "توجيه الإشعار إلى الشاشة المطلوبة مفقود"),
):
    if needle not in notif:
        problems.append(f"push/PushNotifications.kt: {why}")

registrar = read("push/PushRegistrar.kt", SRC)
for needle, why in (
    ("suspend fun register(", "تسجيل الجهاز مفقود"),
    ("suspend fun unregister(", "إلغاء تسجيل الجهاز مفقود"),
    ("fun sendTokenToServer(", "تسجيل التوكن الجديد مفقود"),
    ("fun onSessionChanged(", "مزامنة الجهاز مع الدخول/الخروج مفقودة"),
    ("suspend fun heartbeat(", "نبضة الحضور (لتذكير الغياب) مفقودة"),
):
    if needle not in registrar:
        problems.append(f"push/PushRegistrar.kt: {why}")

service = read("push/PushService.kt", SRC)
if "onNewToken" not in service or "onMessageReceived" not in service:
    problems.append("push/PushService.kt: استقبال التوكن/الرسائل مفقود")

# طلب الإذن مرة واحدة بعد الدخول (أندرويد 13+)
nav = read("ui/nav/AppNav.kt", SRC)
if "NotificationPermissionPrompt" not in nav:
    problems.append("ui/nav/AppNav.kt: طلب إذن الإشعارات التلقائي مفقود (بلا إذن لا يظهر إشعار حقيقي)")
else:
    if not re.search(r"NotificationPermissionPrompt\(\s*signedIn\s*\)", no_comments(nav)):
        problems.append("ui/nav/AppNav.kt: طلب الإذن غير مربوط بحالة الدخول")
    if "AppPrefs.markNotificationAsked" not in nav:
        problems.append("ui/nav/AppNav.kt: قد يُسأل المستخدم أكثر من مرة (لا يُتذكَّر السؤال)")
    if "TIRAMISU" not in nav:
        problems.append("ui/nav/AppNav.kt: طلب الإذن بلا شرط إصدار أندرويد (13+)")

prefs = read("util/AppPrefs.kt", SRC)
if "notificationAsked" not in prefs or "markNotificationAsked" not in prefs:
    problems.append("util/AppPrefs.kt: تذكّر «سألنا مرة واحدة» مفقود")

# المزامنة عند الدخول/الخروج
for needle, why in (
    ("PushRegistrar.register(", "التسجيل عند الدخول"),
    ("PushRegistrar.heartbeat(", "نبضة الحضور عند الإقلاع"),
    ("PushRegistrar.onSessionChanged(ctx, false)", "إلغاء التسجيل عند الخروج"),
):
    if needle not in nav:
        problems.append(f"ui/nav/AppNav.kt: {why} مفقود")

# الإشعار التجريبي يجب أن يمرّ بالخادم (طابور حقيقي) لا أن يُبنى محلياً
settings = read("ui/settings/NotificationSettingsScreen.kt", SRC)
if "container.push.testMe()" not in settings:
    problems.append("ui/settings/NotificationSettingsScreen.kt: زر التجربة لم يعد يمرّ بالخادم (طابور حقيقي)")
if re.search(r"PushNotifications\.show\(\s*context\s*,\s*mapOf", no_comments(settings)):
    problems.append("ui/settings/NotificationSettingsScreen.kt: إشعار محلي مُصنَّع (تجريبي) عاد إلى الشاشة")

# لا مفتاح خدمة داخل التطبيق
leak = []
for root, _dirs, files in os.walk(os.path.join(ROOT, "android")):
    for f in files:
        if f.endswith((".kt", ".xml", ".json", ".properties")) and "google-services" not in f:
            path = os.path.join(root, f)
            try:
                text = io.open(path, encoding="utf-8", errors="ignore").read()
            except Exception:
                continue
            # نبحث عن **قيمة سرّية حقيقية** لا عن ذكر الاسم في تعليق أو تحذير:
            #   • JSON حساب خدمة فيه مفتاح خاص فعلي.
            #   • قيمة مفتاح سري طويلة (sb_secret_ + 16 محرفاً على الأقل).
            if re.search(r'"private_key"\s*:\s*"-----BEGIN', text) or \
               re.search(r"sb_secret_[A-Za-z0-9_\-]{16,}", text):
                leak.append(os.path.relpath(path, ROOT))
if leak:
    problems.append("مفاتيح سرّية داخل التطبيق: " + ", ".join(leak[:3]))

# ===========================================================================
# ب) الطابور يُفرَّغ فعلاً
# ===========================================================================
live = read("35_push_live.sql", SUPABASE)
if not live:
    pass
else:
    for needle, why in (
        ("pg_cron", "جدولة المهام (pg_cron) مفقودة"),
        ("pg_net", "النداء المتجه للخارج (pg_net) مفقود"),
        ("create or replace function public.push_dispatch_tick", "دالة تفريغ الطابور مفقودة"),
        ("'* * * * *'", "مهمة الدقيقة الواحدة (تشغيل كل دقيقة) مفقودة"),
        ("push_on_queue_urgent", "الإرسال الفوري (منشن/ردّ) مفقود"),
        ("push_live_enable", "دالة التشغيل بخطوة واحدة مفقودة"),
        ("push_doctor", "دالة الفحص (طبيب الإشعارات) مفقودة"),
        ("net.http_post", "نداء الشبكة من القاعدة مفقود"),
        ("pg_try_advisory_xact_lock", "قفل منع الإرسال المزدوج مفقود"),
        ("x-push-secret", "سرّ النداء (x-push-secret) مفقود"),
    ):
        if needle not in live:
            problems.append(f"supabase/35_push_live.sql: {why}")
    if re.search(r"grant\s+.*\s+to\s+anon", live):
        problems.append("supabase/35_push_live.sql: صلاحية ممنوحة لـ anon (يجب أن يبقى محجوباً)")

allinone = read("00_ALL_IN_ONE.sql", SUPABASE)
if allinone and "push_live_enable" not in allinone:
    problems.append("supabase/00_ALL_IN_ONE.sql: لم يُحدَّث بالملف 35 (شغّل tools/build_all_in_one_sql.py)")

# مسار البناء: يجب أن يستطيع الحصول على ملف Firebase **من الأسرار** (كي يقدر
# المالك تشغيله من الجوال بلا تحرير ملفات)، وأن يُحذّر بوضوح إن بُني APK بلا FCM.
build = read("build-apk.yml", WORKFLOWS)
if build:
    build_code = re.sub(r"(?m)^\s*#.*$", "", build)
    if "ADDONSTATION_GOOGLE_SERVICES_JSON" not in build_code:
        problems.append("build-apk.yml: لا يكتب google-services.json من الأسرار (خطوة Firebase من الجوال معطّلة)")
    if "::warning title=الإشعارات" not in build_code:
        problems.append("build-apk.yml: لا يُحذّر عند بناء APK بلا Firebase (نسخة بلا إشعارات تخرج بلا تنبيه)")

# ===========================================================================
# ج) الإرسال من الخادم
# ===========================================================================
edge = read("functions/send-push/index.ts", SUPABASE)
for needle, why in (
    ("fcm.googleapis.com/v1", "الإرسال ليس عبر FCM v1"),
    ("FCM_SERVICE_ACCOUNT", "مفتاح الخدمة غير مقروء من أسرار الدالة"),
    ("PUSH_CRON_SECRET", "سرّ النداء غير مقروء من أسرار الدالة"),
    ("x-push-secret", "التحقق من سرّ النداء مفقود"),
    ("push_reengage_scan", "تذكير الغياب غير مربوط بالدالة"),
    ("push_offer_scan", "عرض الاشتراك غير مربوط بالدالة"),
):
    if needle not in edge:
        problems.append(f"supabase/functions/send-push/index.ts: {why}")

base34 = read("34_push_notifications.sql", SUPABASE)
for needle, why in (
    ("trg_push_on_notification", "زناد إشعارات الشات مفقود"),
    ("trg_push_on_content", "زناد المحتوى الجديد مفقود"),
    ("push_register_device", "تسجيل الجهاز في القاعدة مفقود"),
    ("quiet_from", "ساعات الهدوء مفقودة"),
):
    if needle not in base34:
        problems.append(f"supabase/34_push_notifications.sql: {why}")

# دليل التشغيل من الجوال + سير التشغيل نفسه
for doc in ("docs/15-PUSH-LIVE-AR.md", "docs/16-PUSH-SETUP-PHONE-AR.md"):
    if not os.path.exists(os.path.join(ROOT, doc)):
        problems.append(f"{doc}: دليل التشغيل مفقود (المالك يحتاجه خطوة بخطوة)")

# سير التشغيل: يدوي فقط ولا يبني APK ولا يلمس .build-request
wf = read("push-live.yml", WORKFLOWS) if os.path.exists(os.path.join(WORKFLOWS, "push-live.yml")) else ""
if wf:
    wf_code = re.sub(r"(?m)^\s*#.*$", "", wf)
    if "workflow_dispatch" not in wf_code:
        problems.append(".github/workflows/push-live.yml: السير ليس يدوياً")
    for bad, why in (("Build debug APK", "يحتوي خطوة بناء APK"),
                     ("gradlew", "يشغّل Gradle"),
                     (".build-request", "يقرأ ملف طلب البناء")):
        if bad in wf_code:
            problems.append(f".github/workflows/push-live.yml: {why} ({bad})")

# ===========================================================================
print("حارس الإشعارات الحقيقية")
print("-" * 56)
print("فُحص: التطبيق (صلاحية · خدمة · إدارة توكن · طلب إذن) · الطابور (دقيقة/فوري) · دالة الإرسال")
print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عاد {len(problems)} خللاً — الإشعارات ستكون ناقصة أو «تجريبية».")
    sys.exit(1)
print("🟢 الطريق كامل: إذن يُطلب مرة واحدة · توكن يُسجَّل ويُلغى مع الحساب ·")
print("   طابور (يُفرَّغ كل دقيقة + فوري للمنشن) · إرسال FCM v1 من الخادم بلا أي سرّ في التطبيق.")
