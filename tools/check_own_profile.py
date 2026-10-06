#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص «ملفي أنا: تعديل الملف الشخصي لا مراسلة ولا إضافة صديق».

ملاحظة المالك (حرفياً):
    «إذا شخص دخل على الملف الشخصي في الشات أو أي مكان لا تظهر له إضافة صديق
     أو message لأنه حساب ملفه الشخصي — لمّا يدخل [يظهر] المستطيل مكتوب
     تعديل الملف الشخصي، وإذا دخل عليه يودّيه إلى تعديل ملفه الشخصي.»

يفشل إذا:
  • لم يُميَّز ملف المستخدم نفسه في شاشة الملف (UserProfileScreen).
  • ظهر «مراسلة» أو «إضافة صديق» على ملفه هو.
  • لم يظهر زرّ «تعديل الملف الشخصي» بدلاً منهما، أو لم يفتح شاشة التعديل.
  • بقيت بطاقة الملف في الشات (DiscordProfileCardSheet) تراسل صاحبها.
  • غاب نص profile_edit_mine عن أي لغة من اللغات الثماني.
"""
import io
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
PROFILE = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                       "addonstation", "mobile", "ui", "profile", "ProfileScreens.kt")
SHEETS = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                      "addonstation", "mobile", "ui", "chat", "ChatSheets.kt")
NAV = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                   "addonstation", "mobile", "ui", "nav", "AppNav.kt")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

problems = []
profile = io.open(PROFILE, encoding="utf-8").read() if os.path.exists(PROFILE) else ""
sheets = io.open(SHEETS, encoding="utf-8").read() if os.path.exists(SHEETS) else ""
nav = io.open(NAV, encoding="utf-8").read() if os.path.exists(NAV) else ""
if not profile:
    problems.append("ProfileScreens.kt مفقود")
if not sheets:
    problems.append("ChatSheets.kt مفقود")


def func_body(text: str, name: str) -> str:
    """جسم الدالة name حتى قوسها المغلق (يتجاوز الأقواس داخل قائمة المعاملات)."""
    i = text.find("fun %s(" % name)
    if i < 0:
        return ""
    j = text.find(") {", i)
    if j < 0:
        return ""
    start = j + 2  # موضع القوس {
    depth = 0
    for k in range(start, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return text[start:k + 1]
    return text[start:]


def branch(text: str, marker: str) -> str:
    """الفرع الذي يبدأ بـ marker (if (...) {  …  }) حتى قوسه المغلق."""
    i = text.find(marker)
    if i < 0:
        return ""
    start = text.find("{", i + len(marker) - 1)
    if start < 0:
        return ""
    depth = 0
    for k in range(start, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return text[start:k + 1]
    return text[start:]


screen = func_body(profile, "UserProfileScreen")
card = func_body(sheets, "DiscordProfileCardSheet")

# 1) تمييز الملف الشخصي لصاحبه
if "isMyProfile" not in screen:
    problems.append("UserProfileScreen لا يُميّز أن الملف لصاحبه (isMyProfile)")
if "session()?.userId == userId" not in screen:
    problems.append("مقارنة «هذا ملفي» غير مبنيّة على معرّف الجلسة")

# 2) لا «مراسلة» ولا «إضافة صديق» على ملف صاحبه
if "if (isMyProfile)" not in screen:
    problems.append("UserProfileScreen لا يفرّع على حالة «ملفي أنا»")
mine = branch(screen, "if (isMyProfile)")
theirs = branch(screen, "} else {") if "} else {" in screen else ""
if not mine:
    problems.append("لا فرع «ملفي أنا» داخل UserProfileScreen")
else:
    if "profile_message" in mine or "profile_add_friend" in mine:
        problems.append("ما زال يظهر «مراسلة/إضافة صديق» على ملف المستخدم نفسه")
    if "profile_edit_mine" not in mine:
        problems.append("لا زرّ «تعديل الملف الشخصي» على ملف المستخدم نفسه")
    if "onEditProfile" not in mine:
        problems.append("زرّ تعديل الملف لا يفتح شاشة التعديل")
if theirs and ("profile_message" not in theirs or "profile_add_friend" not in theirs):
    problems.append("ملف غيري فقد أزرار المراسلة وإضافة الصديق (يجب أن تبقى)")

# 3) بطاقة الملف في الشات كذلك: لا مراسلة للنفس
if "isSelf(container, profile)" not in card:
    problems.append("بطاقة الملف في الشات لا تتحقق من صاحب الملف")
card_mine = branch(card, "if (isSelf(container, profile))")
if not card_mine:
    problems.append("بطاقة الملف في الشات بلا فرع لصاحب الملف")
else:
    if "profile_message" in card_mine:
        problems.append("بطاقة الملف في الشات ما زالت تراسل صاحبها")
    if "profile_edit_mine" not in card_mine:
        problems.append("بطاقة الملف في الشات بلا زرّ تعديل الملف")

# 4) التنقّل يوصّل الزرّ بشاشة التعديل
if "onEditProfile = { go(Routes.EDIT_PROFILE) }" not in nav:
    problems.append("التنقّل لا يوصّل زرّ التعديل بشاشة Routes.EDIT_PROFILE")
if "onEditProfile: () -> Unit = {}" not in profile:
    problems.append("UserProfileScreen لا يقبل onEditProfile بقيمة افتراضية")

# 5) النص في 8 لغات
missing = []
for path in sorted(glob.glob(os.path.join(RES, "values*", "strings.xml"))):
    if 'name="profile_edit_mine"' not in io.open(path, encoding="utf-8").read():
        missing.append(os.path.basename(os.path.dirname(path)))
if missing:
    problems.append("profile_edit_mine ناقص في: " + " · ".join(missing))

print("الفحص                                   | النتيجة")
print("-" * 60)
rows = [
    ("تمييز ملف صاحبه (isMyProfile)", "isMyProfile" in screen),
    ("بلا «مراسلة» على ملفي", bool(mine) and "profile_message" not in mine),
    ("بلا «إضافة صديق» على ملفي", bool(mine) and "profile_add_friend" not in mine),
    ("زرّ «تعديل الملف الشخصي»", "profile_edit_mine" in (mine or "")),
    ("الزرّ يفتح شاشة التعديل", "onEditProfile" in (mine or "")),
    ("ملف غيري يحتفظ بالزرّين",
     bool(theirs) and "profile_message" in theirs and "profile_add_friend" in theirs),
    ("بطاقة الشات: بلا مراسلة للنفس",
     bool(card_mine) and "profile_message" not in card_mine),
    ("بطاقة الشات: زرّ تعديل الملف", "profile_edit_mine" in (card_mine or "")),
    ("التنقّل إلى Routes.EDIT_PROFILE", "onEditProfile = { go(Routes.EDIT_PROFILE) }" in nav),
    ("النص في 8 لغات", not missing),
]
for name, ok in rows:
    print(f"{name:<40} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 من يفتح ملفه هو — في الشات أو أي مكان — لا يرى «مراسلة» ولا «إضافة صديق»، "
      "بل زرّ «تعديل الملف الشخصي» يفتح شاشة التعديل، وبـ8 لغات.")
