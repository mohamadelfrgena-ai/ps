#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فحص **إشراف الشات**: حذف أي رسالة + نطاق غرف المشرف.

طلب المالك (حرفياً):
    «تمكين الأدمن والمشرف من حذف أي رسالة في الشات مهما كانت مدة إرسالها،
     ويتم منح المشرفين بالوصول للإشراف إلى غرف معينة — أقدر أخليه مشرفاً
     على غرفة واحدة أو كل الغرف.»

يفشل إذا:
  • لم يوجد جدول chat_moderator_rooms أو حارس chat_can_moderate_channel.
  • لم توجد دالة chat_delete_message_staff **بلا أي نافذة زمنية**.
  • لم يُقيَّد «إخفاء/تثبيت» بنطاق غرف المشرف.
  • لم يُدرج الملف 29 في الجامع 00_ALL_IN_ONE.sql.
  • نافذة الثلاث دقائق على المستخدم العادي اختفت (الملف 17 كما هو).
  • التطبيق لا يستعمل النطاق (canModerateRoom · deleteMessageAsStaff
    · setModeratorRooms) أو لا يعرض خيار الحذف الإشرافي.
  • نقص نص من نصوص الإشراف في أي لغة من الثماني.

الاستخدام: python3 tools/check_chat_moderation.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "android", "app", "src", "main")
JAVA = os.path.join(BASE, "java", "com", "addonstation", "mobile")
RES = os.path.join(BASE, "res")
SUPA = os.path.join(ROOT, "supabase")

SQL_MOD = os.path.join(SUPA, "29_chat_moderation_rooms.sql")
SQL_WINDOW = os.path.join(SUPA, "17_message_delete_window.sql")
ALL_IN_ONE = os.path.join(SUPA, "00_ALL_IN_ONE.sql")
REPO = os.path.join(JAVA, "data", "repo", "ChatRepository.kt")
MODELS = os.path.join(JAVA, "data", "model", "Models.kt")
VM = os.path.join(JAVA, "ui", "chat", "ChatViewModel.kt")
SCREEN = os.path.join(JAVA, "ui", "chat", "ChatScreen.kt")
SHEETS = os.path.join(JAVA, "ui", "chat", "ChatSheets.kt")
ADMIN = os.path.join(JAVA, "ui", "admin", "AdminScreens.kt")


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


sql_mod, sql_window, all_in_one = read(SQL_MOD), read(SQL_WINDOW), read(ALL_IN_ONE)
repo, models, vm, screen, sheets, admin = (
    read(REPO), read(MODELS), read(VM), read(SCREEN), read(SHEETS), read(ADMIN),
)

rows = []


def has(name, ok):
    rows.append((name, bool(ok)))


# ── 1) نطاق الإشراف في القاعدة ──────────────────────────────────────────
has("جدول نطاق إشراف المشرفين", "public.chat_moderator_rooms" in sql_mod)
has("عمود «كل الغرف»", "all_rooms" in sql_mod)
has("حارس الغرفة الوحيد chat_can_moderate_channel",
    "create or replace function public.chat_can_moderate_channel(" in sql_mod)
has("الأدمن يشرف على كل الغرف", "public.is_admin(v_uid) then" in sql_mod)
has("المشرف مقصور على غرفه", "r.all_rooms or r.channel_id = p_channel_id" in sql_mod)

# ── 2) حذف أي رسالة بلا نافذة زمنية ─────────────────────────────────────
staff_fn = re.search(
    r"create or replace function public\.chat_delete_message_staff\((?:.|\n)*?\n\$\$;", sql_mod)
staff_fn = staff_fn.group(0) if staff_fn else ""
has("دالة حذف إشرافية", staff_fn != "")
has("بلا أي فحص زمني على created_at", "make_interval" not in staff_fn and "v_created" not in staff_fn)
has("تحترم نطاق الغرفة", "chat_can_moderate_channel(v_msg.channel_id)" in staff_fn)
has("تُسجَّل في سجل الإدارة", "admin_audit_log" in staff_fn)

# ── 3) إخفاء/تثبيت داخل النطاق ──────────────────────────────────────────
hide_fn = re.search(
    r"create or replace function public\.chat_moderate_message\((?:.|\n)*?\n\$\$;", sql_mod)
hide_fn = hide_fn.group(0) if hide_fn else ""
pin_fn = re.search(
    r"create or replace function public\.chat_toggle_pin\((?:.|\n)*?\n\$\$;", sql_mod)
pin_fn = pin_fn.group(0) if pin_fn else ""
has("الإخفاء بنطاق الغرفة", "chat_can_moderate_channel(v_msg.channel_id)" in hide_fn)
has("التثبيت بنطاق الغرفة", "chat_can_moderate_channel(v_msg.channel_id)" in pin_fn)

# ── 4) نافذة الثلاث دقائق على صاحب الرسالة وحده (لا تُمسّ) ──────────────
has("نافذة المستخدم العادي كما هي (١٨٠ ثانية)",
    "chat_delete_window_seconds" in sql_window and "180" in sql_window)
has("الملف 17 داخل الجامع", "17_message_delete_window.sql" in all_in_one)
has("الملف 29 داخل الجامع", "29_chat_moderation_rooms.sql" in all_in_one)

# ── 5) التطبيق ──────────────────────────────────────────────────────────
has("نطاق الإشراف في الحالة", "moderatedChannels" in models and "moderatesAll" in models)
has("قراءة النطاق من my_chat_status",
    'obj["moderated_channels"]' in repo and 'obj["moderates_all"]' in repo)
has("حكم محلي بنطاق الغرفة", "fun canModerateRoom(" in repo)
has("حذف إشرافي من المستودع", "suspend fun deleteMessageAsStaff(" in repo)
has("تعيين غرف المشرف", "suspend fun setModeratorRooms(" in repo)
has("قراءة غرف المشرف", "suspend fun moderatorRooms(" in repo)
has("حذف إشرافي من النموذج", "fun deleteAsStaff(" in vm)
has("أزرار الإشراف بنطاق الغرفة", "val canModerateRoom = container.chat.canModerateRoom(" in screen)
has("ورقة الخيارات تعرض حذف الإشراف",
    "onDeleteStaff" in sheets and "chat_delete_staff" in sheets)
has("خيار حذف المشرف في الشاشة", "onDeleteStaff = {" in screen)
has("لوحة الإدارة: نافذة إشراف الغرف",
    "private fun ModeratorRoomsDialog(" in admin and "admin_rooms_manage" in admin)

# ── 6) النصوص في اللغات الثماني ─────────────────────────────────────────
NEW_KEYS = [
    "chat_delete_staff", "chat_delete_staff_note", "chat_delete_failed",
    "admin_rooms_manage", "admin_rooms_all", "admin_rooms_choose",
    "admin_rooms_saved", "admin_rooms_hint",
]
LANGS = {
    "values": "en", "values-ar": "ar", "values-es": "es", "values-fr": "fr",
    "values-pt": "pt", "values-ru": "ru", "values-tr": "tr", "values-zh": "zh",
}
missing = []
for folder, code in LANGS.items():
    body = read(os.path.join(RES, folder, "strings.xml"))
    for key in NEW_KEYS:
        if f'name="{key}"' not in body:
            missing.append(f"{code}:{key}")
has("نصوص الإشراف في 8 لغات", not missing)

# ── النتيجة ─────────────────────────────────────────────────────────────
print(f"{'الفحص':<44} | النتيجة")
print("-" * 60)
for name, ok in rows:
    print(f"{name:<44} | {'✅' if ok else '❌'}")

bad = [n for n, ok in rows if not ok]
print()
if bad:
    for n in bad:
        print("✗ الفشل:", n)
    if missing:
        print("✗ نصوص ناقصة:", " · ".join(missing))
    print(f"\n✗ عدد المشاكل: {len(bad)}")
    sys.exit(1)
print("🟢 الإدارة والمشرفون يحذفون أي رسالة بلا حدّ زمني · والمشرف مقصور"
      " على غرفه (غرفة أو عدّة أو كلها) — قاعدةً وتطبيقاً وبـ8 لغات.")
