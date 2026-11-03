#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص نافذة حذف الرسالة (٣ دقائق): تطابق القاعدة مع الواجهة + حدود السلوك.

يفشل إذا:
  • اختلفت المدة بين قاعدة البيانات (chat_delete_window_seconds) والتطبيق
    (CHAT_DELETE_WINDOW_SECONDS في ui/chat/ChatTimeRules.kt).
  • لم يكن فحص الوقت موجوداً في دالة chat_delete_my_message أو في ملف 17.
  • اختلفت نتيجة المحاكاة عند الحدود (179 ثانية / 180 / 181).
  • كان خيار الحذف في ورقة الخيارات بلا شرط زمني.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KOTLIN_RULES = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                            "addonstation", "mobile", "ui", "chat", "ChatTimeRules.kt")
SHEETS = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                      "addonstation", "mobile", "ui", "chat", "ChatSheets.kt")
SQL_MAIN = os.path.join(ROOT, "supabase", "03_functions_chat.sql")
SQL_ADDON = os.path.join(ROOT, "supabase", "17_message_delete_window.sql")
ALL_IN_ONE = os.path.join(ROOT, "supabase", "00_ALL_IN_ONE.sql")

problems = []


def read(path):
    if not os.path.exists(path):
        problems.append(f"الملف مفقود: {os.path.relpath(path, ROOT)}")
        return ""
    return io.open(path, encoding="utf-8").read()


def window_from_sql(text):
    m = re.search(r"chat_delete_window_seconds\(\)\s*\n?\s*returns integer.*?select\s+(\d+)",
                  text, re.S)
    return int(m.group(1)) if m else None


kotlin = read(KOTLIN_RULES)
sheets = read(SHEETS)
sql_main = read(SQL_MAIN)
sql_addon = read(SQL_ADDON)
all_in_one = read(ALL_IN_ONE)

# 1) المدة في القاعدة وفي التطبيق
k_win = re.search(r"CHAT_DELETE_WINDOW_SECONDS:\s*Long\s*=\s*(\d+)L", kotlin)
k_win = int(k_win.group(1)) if k_win else None
s_main = window_from_sql(sql_main)
s_addon = window_from_sql(sql_addon)

if k_win is None:
    problems.append("ChatTimeRules.kt: لم أجد CHAT_DELETE_WINDOW_SECONDS")
if s_main is None:
    problems.append("03_functions_chat.sql: لم أجد chat_delete_window_seconds")
if s_addon is None:
    problems.append("17_message_delete_window.sql: لم أجد chat_delete_window_seconds")
if None not in (k_win, s_main, s_addon) and not (k_win == s_main == s_addon):
    problems.append(f"اختلاف المدة: القاعدة={s_main} · ملف 17={s_addon} · التطبيق={k_win}")
if k_win is not None and k_win != 180:
    problems.append(f"المدة ليست ٣ دقائق: {k_win} ثانية")

# 2) القاعدة تفحص العمر وترفض بسبب واضح
for name, text in (("03_functions_chat.sql", sql_main), ("17_message_delete_window.sql", sql_addon)):
    if "delete_window_expired" not in text:
        problems.append(f"{name}: لا يعيد delete_window_expired عند انتهاء المدة")
    if not re.search(r"now\(\)\s*-\s*v_created\s*>\s*make_interval", text):
        problems.append(f"{name}: لا يوجد فحص زمني على created_at")
if "17_message_delete_window.sql" not in all_in_one:
    problems.append("الملف الجامع 00_ALL_IN_ONE.sql لا يتضمن 17_message_delete_window.sql")

# 3) الواجهة: الخيار مشروط زمنياً ولا يُظهر بعد المدة
if "canDeleteMine" not in sheets:
    problems.append("ChatSheets.kt: خيار الحذف بلا شرط زمني (canDeleteMine)")
if not re.search(r"if \(ui\.message\.userId == myId && ui\.message\.status != \"deleted\"\)", sheets):
    problems.append("ChatSheets.kt: شرط ملكية الرسالة غير مكتمل")

# 4) محاكاة الحدود (نفس منطق Kotlin: ceil للدقائق المتبقية)
def remaining(age, win=180):
    return max(0, win - age)


def minutes(age, win=180):
    r = remaining(age, win)
    return 0 if r <= 0 else max(1, -(-r // 60))


cases = [(0, True, 3), (40, True, 3), (60, True, 2), (120, True, 1),
         (179, True, 1), (180, False, 0), (181, False, 0), (600, False, 0)]
print("عمر الرسالة | يمكن الحذف؟ | الدقائق المتبقية")
print("-" * 46)
for age, want_ok, want_min in cases:
    ok = remaining(age) > 0
    mins = minutes(age)
    print(f"{age:>5} ثانية | {'نعم' if ok else 'لا ':^10} | {mins}")
    if (ok, mins) != (want_ok, want_min):
        problems.append(f"محاكاة خاطئة عند {age} ثانية: ({ok}, {mins}) بدل ({want_ok}, {want_min})")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print(f"🟢 نافذة الحذف = {k_win} ثانية (٣ دقائق) · القاعدة والواجهة متطابقتان · الحدود سليمة.")
