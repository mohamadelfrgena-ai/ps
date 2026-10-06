#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص «زرّ الرجوع»: رجوعٌ واحد نظيف، بلا لاق ولا تقطّع ولا مشاكل.

طلب المالك: «إصلاحات زرّ الرجوع إلى الخلف والامتثال له — بدون لاق وتقطيع
ومشاكل». والفحص يمنع رجوع المشكلات:

  ١) الرجوع المزدوج: ضغطتان سريعتان تُخرجان المستخدم خطوتين (شاشة تقفز).
  ٢) الرجوع لا يُغلق ما فوق الشاشة (ورقة · عارض صورة · عارض فيديو) بل يُقفلها كلها.
  ٣) لوحة المفاتيح تُغلق أثناء الانتقال (أكبر سبب لتقطّع الرجوع من الشات).
  ٤) رجوعٌ فارغ: مؤقّت تحميل وثلاث رحلات شبكة عند كل رجوع من غرفة إلى القائمة.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAV = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                   "addonstation", "mobile", "ui", "nav", "AppNav.kt")
CHAT = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "ui", "chat", "ChatScreen.kt")
SHARED = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                      "addonstation", "mobile", "ui", "common", "SharedScreens.kt")
VIDEO = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                     "addonstation", "mobile", "ui", "chat", "VideoPlayer.kt")
COMMUNITY = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                         "addonstation", "mobile", "ui", "community", "CommunityScreens.kt")
REPO = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "data", "repo", "ChatRepository.kt")

problems = []
nav = io.open(NAV, encoding="utf-8").read() if os.path.exists(NAV) else ""
chat = io.open(CHAT, encoding="utf-8").read() if os.path.exists(CHAT) else ""
shared = io.open(SHARED, encoding="utf-8").read() if os.path.exists(SHARED) else ""
video = io.open(VIDEO, encoding="utf-8").read() if os.path.exists(VIDEO) else ""
community = io.open(COMMUNITY, encoding="utf-8").read() if os.path.exists(COMMUNITY) else ""
repo = io.open(REPO, encoding="utf-8").read() if os.path.exists(REPO) else ""
for name, text in (("AppNav.kt", nav), ("ChatScreen.kt", chat), ("ChatRepository.kt", repo),
                   ("CommunityScreens.kt", community), ("SharedScreens.kt", shared),
                   ("VideoPlayer.kt", video)):
    if not text:
        problems.append("%s مفقود" % name)


def func_body(text: str, name: str) -> str:
    i = text.find("fun %s(" % name)
    if i < 0:
        return ""
    j = text.find(") {", i)
    if j < 0:
        return ""
    start = j + 2
    depth = 0
    for k in range(start, len(text)):
        if text[k] == "{":
            depth += 1
        elif text[k] == "}":
            depth -= 1
            if depth == 0:
                return text[start:k + 1]
    return text[start:]


back_fn = func_body(nav, "back")

# ١) رجوع واحد: كل الشاشات تمرّ بـ back() المهلة، لا بـ popBackStack مباشرة
left_direct = len(re.findall(r"onBack = \{ navController\.popBackStack\(\) \}", nav))
if left_direct:
    problems.append("%d شاشة ما زالت ترجع بـ popBackStack مباشرة (بلا مهلة الرجوع)" % left_direct)
if "fun back()" not in nav:
    problems.append("لا دالة رجوع موحّدة back() في التنقّل")
if back_fn and "SystemClock.elapsedRealtime()" not in back_fn:
    problems.append("دالة الرجوع بلا مهلة زمنية (الرجوع المزدوج سيعود)")
if back_fn and "lastBackAt" not in back_fn:
    problems.append("دالة الرجوع لا تحفظ زمن آخر رجوع")

# ٢) لوحة المفاتيح تُخفى قبل الرجوع لا بعده
if back_fn and "hideSoftInputFromWindow" not in back_fn:
    problems.append("الرجوع لا يُخفي لوحة المفاتيح قبل الانتقال (سبب التقطّع)")

# ٣) ما فوق الشاشة يُغلق أولاً: أوراق الشات والعارضان
for state, label in (
    ("mediaSheet", "ورقة الوسائط"),
    ("showAttach", "ورقة المرفقات"),
    ("showMembers", "ورقة الأعضاء"),
    ("showPinned", "ورقة المثبّتات"),
    ("actionsFor", "قائمة الرسالة"),
    ("showEmoji", "لوحة الإيموجي"),
    ("videoViewerUrl", "عارض الفيديو"),
    ("imageViewerUrl", "عارض الصورة"),
):
    if not re.search(r"BackHandler\(enabled = [^\n]*%s" % re.escape(state), chat):
        problems.append("الرجوع لا يُغلق %s قبل مغادرة الغرفة" % label)
if "androidx.activity.compose.BackHandler" not in shared:
    problems.append("عارض الصورة (مشترك) لا يعالج الرجوع")
if "androidx.activity.compose.BackHandler" not in video:
    problems.append("عارض الفيديو لا يعالج الرجوع")

# ٤) رجوع بلا انتظار: القائمة تُبذر من الذاكرة، ولا مؤقّت تحميل فوق بيانات موجودة
if "cachedChannels()" not in community:
    problems.append("قائمة الغرف لا تُبذر بالذاكرة المحفوظة (مؤقّت تحميل عند كل رجوع)")
if "if (loading && channels.isEmpty())" not in community:
    problems.append("قائمة الغرف تُفرغ نفسها أثناء التحديث (وميض عند الرجوع)")
if "channelsCache" not in repo:
    problems.append("لا ذاكرة مؤقّتة لقائمة الغرف في ChatRepository")
if "cashRoomEnsured" not in repo:
    problems.append("ضمان غرفة النقدية ما زال يُستدعى مع كل فتح (رحلة شبكة إضافية)")
if "fun invalidateChannels()" not in repo:
    problems.append("لا إبطال للذاكرة بعد تغيير الغرف من لوحة الإدارة")

print("الفحص                                   | النتيجة")
print("-" * 60)
rows = [
    ("دالة رجوع موحّدة back() في التنقّل", "fun back()" in nav),
    ("كل الشاشات ترجع بها (لا popBackStack)", left_direct == 0),
    ("مهلة تمنع الرجوع المزدوج",
     bool(back_fn) and "lastBackAt" in back_fn and "SystemClock.elapsedRealtime()" in back_fn),
    ("إخفاء لوحة المفاتيح قبل الرجوع",
     bool(back_fn) and "hideSoftInputFromWindow" in back_fn),
    ("الرجوع يُغلق أوراق الشات أولاً",
     len(re.findall(r"BackHandler\(enabled =", chat)) >= 8),
    ("عارض الصورة يعالج الرجوع", "androidx.activity.compose.BackHandler" in shared),
    ("عارض الفيديو يعالج الرجوع", "androidx.activity.compose.BackHandler" in video),
    ("قائمة الغرف تُبذر من الذاكرة", "cachedChannels()" in community),
    ("لا مؤقّت تحميل فوق بيانات موجودة", "loading && channels.isEmpty()" in community),
    ("ذاكرة الغرف + ضمان مرة واحدة",
     "channelsCache" in repo and "cashRoomEnsured" in repo),
]
for name, ok in rows:
    print(f"{name:<40} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 الرجوع: خطوة واحدة بلا قفز، يُغلق ما فوق الشاشة قبلها، ويُخفي لوحة المفاتيح "
      "قبل الانتقال — وقائمة الغرف تعود فوراً بلا شبكة ولا مؤقّت.")
