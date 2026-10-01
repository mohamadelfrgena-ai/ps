#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص إزالة أداة البحث من داخل الشات (طلب المستخدم) — ويمنع رجوعها.

يفشل إذا:
  • ظهرت أيقونة البحث أو معامل onSearch في ترويسة الشات.
  • بقي حقل «بحث في الغرفة» أو تصفية الرسائل بالبحث في ChatScreen.kt.
  • بقي مفتاح chat_search_in_room في أي ملف لغة (٨ لغات).
  • حُذف بحث الإيموجي بالخطأ (يجب أن يبقى داخل منتقي الرموز).
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

# 3) المفتاح محذوف من كل ملفات اللغات
left = []
for path in sorted(glob.glob(os.path.join(RES, "values*", "strings.xml"))):
    text = io.open(path, encoding="utf-8").read()
    if "chat_search_in_room" in text:
        left.append(os.path.basename(os.path.dirname(path)))
if left:
    problems.append("مفتاح chat_search_in_room باقٍ في: " + " · ".join(left))

# 4) بحث الإيموجي باقٍ (لم نحذف أكثر من المطلوب)
if "chat_search_emoji" not in sheets and "chat_search_emoji" not in io.open(
        os.path.join(RES, "values", "strings.xml"), encoding="utf-8").read():
    problems.append("بحث الإيموجي حُذف بالخطأ — يجب أن يبقى داخل منتقي الرموز")

print("الفحص | النتيجة")
print("-" * 46)
rows = [
    ("لا أيقونة بحث في ترويسة الشات", "Icons.Filled.Search" not in chat),
    ("لا حقل «بحث في الغرفة»", "chat_search_in_room" not in chat),
    ("لا تصفية رسائل بالبحث", not re.search(r"messages\.filter\s*\{", chat)),
    ("المفتاح محذوف من 8 لغات", not left),
    ("بحث الإيموجي باقٍ (منتقي الرموز)", "chat_search_emoji" in sheets),
]
for name, ok in rows:
    print(f"{name:<38} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 أداة البحث أُزيلت من الشات بالكامل (ترويسة وحقل وتصفية)، وبحث الإيموجي باقٍ.")
