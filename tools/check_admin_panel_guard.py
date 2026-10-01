#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص حماية لوحة الإدارة: للأدمن فقط + محمية من ثلاثة جهات.

يفشل إذا:
  • لم توجد بوابة الدخول admin_panel_access() في القاعدة أو لم تُسجّل الفتح/الرفض.
  • كان أي مسار من مسارات اللوحة في التنقّل (AppNav) بلا AdminGate.
  • لم تكن البوابة في التطبيق تتحقق من: الدور · الخادم · كلمة المرور · القفل التلقائي.
  • لم يُسحب التنفيذ من الزائر (anon) على دوال الإدارة.
  • لم يكن زر «قفل اللوحة الآن» موجوداً أو لم تُثبَّت سياسات RLS لجداول اللوحة.
  • نقص أي نص من نصوص البوابة في أي لغة (٨ لغات).
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SQL_GUARD = os.path.join(ROOT, "supabase", "19_admin_panel_guard.sql")
ALL_IN_ONE = os.path.join(ROOT, "supabase", "00_ALL_IN_ONE.sql")
NAV = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                   "addonstation", "mobile", "ui", "nav", "AppNav.kt")
GATE = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "ui", "admin", "AdminGate.kt")
ADMIN_SCREENS = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                             "addonstation", "mobile", "ui", "admin", "AdminScreens.kt")
ADMIN_REPO = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                          "addonstation", "mobile", "data", "repo", "AdminRepository.kt")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

problems = []


def read(path):
    if not os.path.exists(path):
        problems.append(f"الملف مفقود: {os.path.relpath(path, ROOT)}")
        return ""
    return io.open(path, encoding="utf-8").read()


guard = read(SQL_GUARD)
all_in_one = read(ALL_IN_ONE)
nav = read(NAV)
gate = read(GATE)
screens = read(ADMIN_SCREENS)
repo = read(ADMIN_REPO)

# 1) القاعدة: بوابة الدخول + التسجيل + سحب تنفيذ الزائر
if "create or replace function public.admin_panel_access()" not in guard:
    problems.append("19_admin_panel_guard.sql: لا تعرّف admin_panel_access()")
if "panel_opened" not in guard:
    problems.append("19_admin_panel_guard.sql: لا يُسجّل فتح اللوحة في سجلّ التدقيق")
if "panel_denied" not in guard:
    problems.append("19_admin_panel_guard.sql: لا يُسجّل محاولات غير الأدمن")
if not re.search(r"revoke execute on function %s from anon", guard):
    problems.append("19_admin_panel_guard.sql: لا يسحب التنفيذ من الزائر (anon)")
if "19_admin_panel_guard.sql" not in all_in_one:
    problems.append("الملف الجامع 00_ALL_IN_ONE.sql لا يتضمن 19_admin_panel_guard.sql")
# تثبيت RLS لجدولين حساسين على الأقل
for table in ("app_settings", "admin_audit_log", "ac_redeem_codes", "payment_orders"):
    if f"on public.{table}" not in guard:
        problems.append(f"19_admin_panel_guard.sql: لا يثبّت سياسة RLS على {table}")

# 2) التطبيق: كل مسار إداري مغطّى بالبوابة
admin_routes = re.findall(r"composable\(Routes\.(ADMIN[A-Z_]*)\)\s*\{", nav)
gated = len(re.findall(r"com\.addonstation\.mobile\.ui\.admin\.AdminGate\(", nav))
if not admin_routes:
    problems.append("AppNav: لم أجد مسارات الإدارة")
elif gated < len(admin_routes):
    problems.append(f"AppNav: {len(admin_routes)} مساراً إدارياً و{gated} بوابة فقط — مسار بلا حماية")

# 3) البوابة: الأقفال الأربعة
checks = [
    ("التحقق من الدور (admin فقط)", 'profile?.role != "admin"' in gate),
    ("تحقق الخادم عبر panel_access", "panelAccess()" in gate),
    ("طلب كلمة المرور", "PasswordVisualTransformation" in gate),
    ("التحقق بكلمة المرور عبر Supabase", "container.auth.signIn" in gate),
    ("قفل تلقائي بعد مدة", "UNLOCK_MINUTES" in gate),
    ("إيقاف بعد محاولات خاطئة", "COOLDOWN_SECONDS" in gate and "registerFailure" in gate),
    ("قفل فوري (زر)", "fun lock()" in gate and "AdminSession.lock()" in screens),
]
for name, ok in checks:
    if not ok:
        problems.append(f"AdminGate: ناقص — {name}")

if "admin_panel_access" not in repo:
    problems.append("AdminRepository: لا يستدعي admin_panel_access")

# 4) النصوص في اللغات الثماني
keys = ["admin_gate_title", "admin_gate_hint", "admin_gate_password", "admin_gate_unlock",
        "admin_gate_wrong", "admin_gate_too_many", "admin_gate_checking",
        "admin_gate_cooldown", "admin_gate_locked_note", "admin_gate_denied_body",
        "admin_lock_now"]
langs = {"values": "en", "values-ar": "ar", "values-es": "es", "values-fr": "fr",
         "values-pt": "pt", "values-ru": "ru", "values-tr": "tr", "values-zh": "zh"}
missing = {}
for folder, code in langs.items():
    path = os.path.join(RES, folder, "strings.xml")
    if not os.path.exists(path):
        missing[code] = ["<ملف مفقود>"]
        continue
    text = io.open(path, encoding="utf-8").read()
    absent = [k for k in keys if f'name="{k}"' not in text]
    if absent:
        missing[code] = absent
if missing:
    for code, ks in missing.items():
        problems.append(f"نصوص ناقصة في {code}: " + ", ".join(ks))

print("الفحص | النتيجة")
print("-" * 52)
rows = [
    ("بوابة admin_panel_access في القاعدة", "admin_panel_access" in guard),
    ("سجلّ تدقيق للفتح والرفض", "panel_opened" in guard and "panel_denied" in guard),
    ("الزائر لا يستدعي دوال الإدارة", "from anon" in guard),
    ("الملف 19 داخل الجامع", "19_admin_panel_guard.sql" in all_in_one),
    (f"كل مسارات اللوحة محمية ({gated}/{len(admin_routes)})", gated >= len(admin_routes) and gated > 0),
    ("الدور + الخادم + كلمة المرور + القفل", all(ok for _, ok in checks)),
    ("نصوص البوابة في 8 لغات", not missing),
]
for name, ok in rows:
    print(f"{name:<46} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 لوحة الإدارة للأدمن فقط ومحمية: دور + تحقق خادم + كلمة مرور + قفل تلقائي + تدقيق.")
