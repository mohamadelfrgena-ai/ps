#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص «الوسائط للأدمن فقط»: الفيديو والصوت والتسجيل الصوتي لا يرسلها غير الأدمن.

يفشل إذا:
  • لم يكن الفحص موجوداً في send_chat_message (القاعدة الحاسمة).
  • لم يوجد المشغّل الحامي في 18_media_admin_only.sql أو لم يُدرج في الجامع.
  • لم تكن بطاقة الصوت/الفيديو مقفلة ومعطّلة لغير الأدمن.
  • لم يُمنع مسار اختيار الوسائط ومنتقي الملفات في الشات.
  • نقص نص `chat_media_admin_only` في أي لغة (٨ لغات) أو في ErrorText/ApiError.
"""
import io
import os
import re
import sys
import glob

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHAT_SQL = os.path.join(ROOT, "supabase", "03_functions_chat.sql")
GUARD_SQL = os.path.join(ROOT, "supabase", "18_media_admin_only.sql")
ALL_IN_ONE = os.path.join(ROOT, "supabase", "00_ALL_IN_ONE.sql")
SHEETS = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                      "addonstation", "mobile", "ui", "chat", "ChatSheets.kt")
SCREEN = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                      "addonstation", "mobile", "ui", "chat", "ChatScreen.kt")
API_ERROR = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                         "addonstation", "mobile", "data", "ApiError.kt")
ERROR_TEXT = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                          "addonstation", "mobile", "ui", "ErrorText.kt")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

problems = []


def read(path):
    if not os.path.exists(path):
        problems.append(f"الملف مفقود: {os.path.relpath(path, ROOT)}")
        return ""
    return io.open(path, encoding="utf-8").read()


chat_sql = read(CHAT_SQL)
guard_sql = read(GUARD_SQL)
all_in_one = read(ALL_IN_ONE)
sheets = read(SHEETS)
screen = read(SCREEN)
api_error = read(API_ERROR)
error_text = read(ERROR_TEXT)

# 1) القاعدة الحاسمة داخل دالة الإرسال
if not re.search(r"if p_message_type in \('audio','video'\) and v_role <> 'admin'", chat_sql):
    problems.append("send_chat_message: لا يمنع audio/video على غير الأدمن")
if "'admin_only_media'" not in chat_sql:
    problems.append("send_chat_message: لا يرد بسبب admin_only_media")

# 2) الحامي على مستوى الجدول
if "trg_chat_media_admin_only" not in guard_sql:
    problems.append("18_media_admin_only.sql: المشغّل الحامي غير موجود")
if not re.search(r"new\.message_type in \('audio','video'\)", guard_sql):
    problems.append("18_media_admin_only.sql: المشغّل لا يفحص نوع الرسالة")
if "18_media_admin_only.sql" not in all_in_one:
    problems.append("الملف الجامع 00_ALL_IN_ONE.sql لا يتضمن 18_media_admin_only.sql")

# 2ب) المخزن: رفع الوسائط للأدمن فقط (حماية ثالثة)
STORAGE = os.path.join(ROOT, "supabase", "05_storage.sql")
storage = read(STORAGE)
pat = r"chat_media_insert[\s\S]{0,320}is_admin\(auth\.uid\(\)\)"
if not re.search(pat, storage):
    problems.append("05_storage.sql: سياسة رفع chat-media غير مقيَّدة بالأدمن")
if not re.search(pat, guard_sql):
    problems.append("18_media_admin_only.sql: لا يعيد تعريف سياسة رفع المخزن للأدمن")

# 3) التطبيق: الخيار للأدمن فقط + منع المسارين
media_card_guarded = (
    "title = stringResource(R.string.chat_attach_media)" in sheets
    and "locked = !isAdmin" in sheets
    and "lockedLabel = stringResource(R.string.chat_admin_only_short)" in sheets
    and ".clickable(enabled = !locked, onClick = onClick)" in sheets
)
if not media_card_guarded:
    problems.append("AttachSheet: بطاقة الصوت/الفيديو لا تُقفل وتُعطّل لغير الأدمن")
if "val isAdmin = profile?.role == \"admin\"" not in screen:
    problems.append("ChatScreen: لا يحسب isAdmin من الدور (admin فقط)")
if screen.count("if (isAdmin)") + screen.count("if (!isAdmin)") < 3:
    problems.append("ChatScreen: مسارات فتح الوسائط/المنتقي غير محمية كلها")
if "mediaAdminMsg" not in screen:
    problems.append("ChatScreen: لا يعرض نص المنع لغير الأدمن")

# 4) نوع الخطأ ونصّه في التطبيق
if "MEDIA_ADMIN_ONLY" not in api_error or "admin_only_media" not in api_error:
    problems.append("ApiError: لا يحوّل admin_only_media إلى نوع خطأ")
if "MEDIA_ADMIN_ONLY" not in error_text:
    problems.append("ErrorText: لا يعرض نص الخطأ الجديد")

# 5) النص في اللغات الثماني
langs = {"values": "en", "values-ar": "ar", "values-es": "es", "values-fr": "fr",
         "values-pt": "pt", "values-ru": "ru", "values-tr": "tr", "values-zh": "zh"}
missing = []
for folder, code in langs.items():
    path = os.path.join(RES, folder, "strings.xml")
    if not os.path.exists(path):
        missing.append(f"{code}(مفقود)")
        continue
    if 'name="chat_media_admin_only"' not in io.open(path, encoding="utf-8").read():
        missing.append(code)
if missing:
    problems.append("نص chat_media_admin_only ناقص في: " + " · ".join(missing))

print("الفحص | النتيجة")
print("-" * 44)
checks = [
    ("send_chat_message ترفض الوسائط لغير الأدمن", "if p_message_type in ('audio','video')" in chat_sql),
    ("ترد بسبب admin_only_media", "'admin_only_media'" in chat_sql),
    ("مشغّل الحماية على الجدول", "trg_chat_media_admin_only" in guard_sql),
    ("الملف 18 داخل الجامع", "18_media_admin_only.sql" in all_in_one),
    ("رفع الوسائط للأدمن فقط في المخزن", bool(re.search(pat, storage))),
    ("بطاقة الصوت/الفيديو مقفلة لغير الأدمن", media_card_guarded),
    ("isAdmin = admin فقط (لا مشرف)", 'profile?.role == "admin"' in screen),
    ("نص المنع في 8 لغات", not missing),
]
for name, ok in checks:
    print(f"{name:<40} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 الوسائط (فيديو · صوت · تسجيل صوتي) للأدمن فقط — قاعدةً وتطبيقاً، بـ8 لغات.")
