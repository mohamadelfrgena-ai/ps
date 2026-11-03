#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص إزالة أدوات البحث من داخل الشات (طلب المستخدم) — ويمنع رجوعها.

يفشل إذا:
  • ظهرت أيقونة البحث أو معامل onSearch في ترويسة الشات.
  • بقي حقل «بحث في الغرفة» أو تصفية الرسائل بالبحث في ChatScreen.kt.
  • بقي حقل «ابحث عن إيموجي» أو تصفية القائمة في منتقي الرموز (ChatSheets.kt).
  • بقي مفتاح chat_search_in_room أو chat_search_emoji في أي ملف لغة (٨ لغات).
"""
import io
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHAT = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "ui", "chat", "ChatScreen.kt")
SHEETS = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                      "addonstation", "mobile", "ui", "chat", "ChatSheets.kt")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

problems = []
chat = io.open(CHAT, encoding="utf-8").read() if os.path.exists(CHAT) else ""
sheets = io.open(SHEETS, encoding="utf-8").read() if os.path.exists(SHEETS) else ""
if not chat:
    problems.append("ChatScreen.kt مفقود")

# 1) لا أثر لأداة البحث في الشات
for needle, label in (
    ("onSearch", "معامل onSearch"),
    ("showSearch", "حالة showSearch"),
    ("searchQuery", "حالة searchQuery"),
    ("chat_search_in_room", "نص «بحث في الغرفة»"),
    ("Icons.Filled.Search", "أيقونة البحث"),
):
    if needle in chat:
        problems.append(f"ChatScreen.kt ما زال يحوي {label}")

# 2) لا تصفية للرسائل بالبحث
if re.search(r"messages\.filter\s*\{", chat):
    problems.append("ChatScreen.kt ما زال يصفّي الرسائل (فلترة بحث)")

# 3) بحث منتقي الرموز أيضاً أُزيل
#    ملاحظة: `run search@ { … }` وسمُ قفزة في كوتلن (label) لا حقلَ بحث،
#    فتُحذف الأوسمة قبل الفحص حتى لا يُحسب بلاغاً كاذباً.
def strip_labels(text: str) -> str:
    # `return@search` (قفزة) ثم `search@ {` (وسم حلقة/نطاق)
    text = re.sub(r"\b[A-Za-z_]\w*@[A-Za-z_]\w*", "L", text)
    return re.sub(r"\b[A-Za-z_]\w*@", "L", text)


sheets = strip_labels(sheets)
for needle, label in (
    ("search", "حالة/حقل البحث"),
    ("filtered", "تصفية قائمة الإيموجي"),
    ("chat_search_emoji", "نص «ابحث عن إيموجي»"),
):
    if needle in sheets:
        problems.append(f"ChatSheets.kt (منتقي الرموز) ما زال يحوي {label}")

# 4) المفتاحان محذوفان من كل ملفات اللغات
left = []
for path in sorted(glob.glob(os.path.join(RES, "values*", "strings.xml"))):
    text = io.open(path, encoding="utf-8").read()
    if "chat_search_in_room" in text or "chat_search_emoji" in text:
        left.append(os.path.basename(os.path.dirname(path)))
if left:
    problems.append("مفتاح بحث باقٍ في: " + " · ".join(left))

print("الفحص | النتيجة")
print("-" * 46)
rows = [
    ("لا أيقونة بحث في ترويسة الشات", "Icons.Filled.Search" not in chat),
    ("لا حقل «بحث في الغرفة»", "chat_search_in_room" not in chat),
    ("لا تصفية رسائل بالبحث", not re.search(r"messages\.filter\s*\{", chat)),
    ("منتقي الرموز بلا بحث", not any(x in sheets for x in ("search", "filtered"))),
    ("المفتاحان محذوفان من 8 لغات", not left),
]
for name, ok in rows:
    print(f"{name:<38} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 لا أداة بحث في الشات إطلاقاً: لا في الترويسة ولا في الرسائل ولا في منتقي الرموز — وبلا أي نص بحث في اللغات الثماني.")
