#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""حارس أسماء الأعمدة في ملفات الخادم.

لماذا؟ لأن `33_user_block.sql` كتب `full_name` في جدول `profiles` — والعمود
الحقيقي `display_name` (في `01_schema.sql`). العطل مرّ من كل الفحوص لأن الاسم
داخل دالة، ولم يقابل أحدٌ الأسماء بالسكيما. النتيجة: فشل تنفيذ 33 على القاعدة.

ما يفحصه؟
  ① لا `full_name` في أي ملف ترحيل (الصحيح `display_name`).
  ② كل `new.<col>` / `old.<col>` داخل دالة زناد موجود فعلاً في جدول الزناد —
     لأن عموداً ناقصاً هنا لا يظهر إلا وقت التشغيل (أسوأ أنواع الأعطال).
  ③ كل `<جدول>.<column>` مذكور في ملفات الخادم معروف في السكيما.
  ④ الأعمدة الحسّاسة التي يعتمد عليها العمل موجودة في السكيما.
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SUPABASE = os.path.join(ROOT, "supabase")

SERVER_FILES = [
    "33_user_block.sql",
    "34_push_notifications.sql",
    "35_push_live.sql",
    "36_profile_stats.sql",
    "37_profile_reports.sql",
    "38_user_mute.sql",
]

problems = []


def read(name: str) -> str:
    with open(os.path.join(SUPABASE, name), encoding="utf-8") as fh:
        return fh.read()


def migrations():
    for name in sorted(os.listdir(SUPABASE)):
        if name.endswith(".sql") and name != "00_ALL_IN_ONE.sql":
            yield name


def check(label: str, ok: bool, detail: str = ""):
    print(f"{'✅' if ok else '✗'} {label}" + (f" — {detail}" if detail and not ok else ""))
    if not ok:
        problems.append(label)


# ── خريطة الجداول وأعمدتها من كل الترحيلات ──────────────────────────────────
TABLES = {}
for name in migrations():
    text = read(name)
    for m in re.finditer(r"create table if not exists public\.([a-z_]+)\s*\((.*?)\n\);",
                         text, re.S):
        table, body = m.group(1), m.group(2)
        cols = set(re.findall(r"^\s{2}([a-z_]+)\s+", body, re.M))
        TABLES.setdefault(table, set()).update(cols)
        # أعمدة تُضاف لاحقاً في الملف نفسه
    for m in re.finditer(r"alter table public\.([a-z_]+)\s*\n?\s*add column if not exists ([a-z_]+)",
                         text):
        TABLES.setdefault(m.group(1), set()).add(m.group(2))

check(f"خريطة الجداول بُنيت ({len(TABLES)} جدولاً)", len(TABLES) > 20)

# ── ① الاسم الممنوع ─────────────────────────────────────────────────────────
offenders = [n for n in migrations() if "full_name" in read(n)]
check("لا `full_name` في أي ملف ترحيل (الصحيح `display_name`)",
      not offenders, "وجدناه في: " + " · ".join(offenders))

# ── ② أعمدة new/old داخل دوال الزناد ────────────────────────────────────────
trigger_rows = []
for name in SERVER_FILES:
    text = read(name)
    for m in re.finditer(
            r"create trigger\s+([a-z_]+)\s+[^;]*?on public\.([a-z_]+)\s*"
            r"(?:for each row\s*)?execute function public\.([a-z_]+)",
            text, re.S):
        trig, table, fn = m.group(1), m.group(2), m.group(3)
        body = re.search(
            rf"create or replace function public\.{fn}\(\)\s*returns trigger(.*?)\$fn\$\s*;",
            text, re.S)
        if not body:
            body = re.search(
                rf"create or replace function public\.{fn}\(\)\s*returns trigger(.*?)\$\$\s*;",
                text, re.S)
        if body and table in TABLES:
            for col in set(re.findall(r"\b(?:new|old)\.([a-z_]+)\b", body.group(1))):
                if col not in TABLES[table]:
                    trigger_rows.append(f"{name}: {trig} على {table} ⇒ {col} غير موجود")
check(f"أعمدة new/old في دوال الزناد مطابقة لجداولها",
      not trigger_rows, " · ".join(trigger_rows[:4]))

# ── ③ <جدول>.<column> في ملفات الخادم ───────────────────────────────────────
bad_refs = []
for name in SERVER_FILES:
    text = read(name)
    for table, col in re.findall(r"\bpublic\.([a-z_]+)\.([a-z_]+)\b", text):
        if table in TABLES and col not in TABLES[table]:
            bad_refs.append(f"{name}: {table}.{col}")
check("كل `<جدول>.<عمود>` مذكور معروف في السكيما", not bad_refs,
      " · ".join(sorted(set(bad_refs))[:4]))

# ── ④ الأعمدة الحسّاسة ──────────────────────────────────────────────────────
must = {
    "profiles": ["display_name", "username", "avatar_url", "plan", "role",
                 "plan_expires_at", "last_seen_at"],
    "chat_messages": ["id", "user_id", "status", "created_at"],
    "chat_notifications": ["recipient_id", "actor_id", "type", "message_id",
                           "channel_id", "meta"],
    "push_prefs": ["user_id", "muted_users"],
}
for table, cols in must.items():
    known = TABLES.get(table, set())
    missing = [c for c in cols if c not in known]
    check(f"أعمدة {table} الحسّاسة موجودة ({len(cols)})", not missing, str(missing))

# ── ⑤ ما يعتمد عليه ملفّا الكتم والإبلاغ ────────────────────────────────────
check("38 يضيف عمود push_prefs.muted_users",
      "muted_users" in TABLES.get("push_prefs", set()))
check("37 يضيف `target_user_id` إلى chat_reports",
      "target_user_id" in read("37_profile_reports.sql"))

print()
if problems:
    print(f"✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 أسماء الأعمدة: بلا full_name · أعمدة الزناد مطابقة · كل الإشارات معروفة.")
