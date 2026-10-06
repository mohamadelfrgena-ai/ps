#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص «الشات للمسجّلين فقط» (طلب المالك) — ويمنع رجوعه إلى حالة الضيف.

المطلوب حرفياً: «الشات لا يمكن الدخول إليه إلا لما يقوم بتسجيل الدخول، ولمّا
يدخل وهو غير مسجّل يُنقل إلى تسجيل الدخول.»

يفشل إذا:
  • لم يُحجَب مسارا الشات (community و chat/{id}) عن غير المسجّل في التنقّل.
  • لم يُنقل غير المسجّل إلى شاشة الدخول (Routes.LOGIN) عند محاولة الدخول.
  • بقي طريقٌ إلى الشات لا يمرّ بالبوابة (نداء تنقّل مباشر خارج AppNav).
  • لم يُحفظ مكان المستخدم ليعود إليه بعد نجاح الدخول.
  • تُركت وجهة الشات في رصة التنقّل (حلقة رجوع: شات ← دخول ← شات …).
  • لم تُغلَّف CommunityGate و ChatScreen ببوابة SignInGate.
  • غاب نص التوضيح chat_login_required عن أي لغة من اللغات الثماني.
"""
import io
import glob
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
NAV = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                   "addonstation", "mobile", "ui", "nav", "AppNav.kt")
AUTH = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "ui", "auth", "AuthScreens.kt")
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

problems = []
nav = io.open(NAV, encoding="utf-8").read() if os.path.exists(NAV) else ""
auth = io.open(AUTH, encoding="utf-8").read() if os.path.exists(AUTH) else ""
if not nav:
    problems.append("AppNav.kt مفقود")


def strip_noise(text: str) -> str:
    """حذف النصوص الحرفية والتعليقات حتى لا تُربك مطابقة الأقواس والفحص."""
    text = re.sub(r'"""[\s\S]*?"""', '""', text)
    text = re.sub(r'"(?:\\.|[^"\\])*"', '""', text)
    text = re.sub(r"/\*[\s\S]*?\*/", "", text)
    text = re.sub(r"//[^\n]*", "", text)
    return text


def block(text: str, marker: str, open_re: str = None) -> str:
    """محتوى الكتلة التي تبدأ بـ marker حتى قوسها المغلق المقابل.

    open_re نمطٌ لقوس الفتح الحقيقي للكتلة — يُستخدم حين يسبق القوسَ قوسٌ
    آخر داخلي (مثل navArgument("channelId") { … }) فيُربك عدّ الأقواس.
    """
    i = text.find(marker)
    if i < 0:
        return ""
    if open_re:
        m = re.search(open_re, text[i:])
        if not m:
            return ""
        start = i + m.start() + m.group().index("{")
    else:
        start = text.find("{", i)
    if start < 0:
        return ""
    depth = 0
    for j in range(start, len(text)):
        if text[j] == "{":
            depth += 1
        elif text[j] == "}":
            depth -= 1
            if depth == 0:
                return text[start:j + 1]
    return text[start:]


clean = strip_noise(nav)
community_block = block(clean, "composable(Routes.COMMUNITY)")
chat_block = block(clean, "route = Routes.CHAT", open_re=r"\)\s*\{\s*entry\s*->")
gate_block = block(clean, "private fun SignInGate")

# 1) حجب المسارين: قائمة الغرف + الغرفة نفسها
if "destination == Routes.COMMUNITY" not in clean:
    problems.append("لا فحص لوجهة «المجتمع/الشات» في isChatRoute")
# النصوص الحرفية تُحذف في clean، فنفحص سلسلة الغرفة في النص الخام
if 'destination.startsWith("chat/")' not in nav:
    problems.append("لا فحص لوجهة الغرفة chat/{id} في isChatRoute")

# 2) التنقّل كله يمرّ بالبوابة: go() لروابط الغرف، و goTab() لتبويب المجتمع
if "if (isChatRoute(route) && !signedIn)" not in clean:
    problems.append("go() لا يوقف الروابط إلى الشات لغير المسجّل")
if "if (target == MainTab.Community && !signedIn)" not in clean:
    problems.append("goTab() لا يوقف تبويب المجتمع لغير المسجّل")
if "requireSignIn(route)" not in clean or "requireSignIn(target.route)" not in clean:
    problems.append("نداء requireSignIn غير موجود في مسارَي التنقّل")

# 3) النقل إلى شاشة الدخول + حفظ الوجهة للعودة بعد الدخول
if "fun requireSignIn(intended: String)" not in clean:
    problems.append("دالة requireSignIn غير موجودة")
if "afterLoginRoute = intended" not in clean:
    problems.append("لا تُحفظ الوجهة المطلوبة قبل النقل إلى الدخول")
if "navController.navigate(Routes.LOGIN)" not in clean:
    problems.append("لا تنقّل إلى شاشة الدخول (Routes.LOGIN)")
if "fun continueAfterLogin()" not in clean:
    problems.append("لا دالة تعيد المستخدم إلى وجهته بعد الدخول")
if "popUpTo(Routes.LOGIN) { inclusive = true }" not in clean:
    problems.append("شاشة الدخول لا تُخرج من الرصة بعد نجاح الدخول")

# 4) لا حلقة رجوع: وجهة الشات تُخرج من الرصة قبل فتح الدخول
if "standingOnChat" not in clean or "navController.popBackStack()" not in clean:
    problems.append("وجهة الشات لا تُخرج من الرصة ⇒ حلقة رجوع (شات ← دخول ← شات)")

# 5) بوابة واحدة تُغلّف الشاشتين
if "SignInGate(" not in community_block:
    problems.append("قائمة الغرف (CommunityGate) ليست خلف بوابة SignInGate")
if "SignInGate(" not in chat_block:
    problems.append("شاشة الغرفة (ChatScreen) ليست خلف بوابة SignInGate")
if "CommunityGate(" not in community_block and "community.CommunityGate(" not in nav:
    problems.append("CommunityGate غير مستدعى من التنقّل")
if "ChatScreen(" not in chat_block:
    problems.append("ChatScreen غير مستدعى من التنقّل")

# 6) البوابة نفسها: تعرض المحتوى للمسجّل فقط، وتنقل غير المسجّل مرةً واحدة
if gate_block:
    if "if (signedIn)" not in gate_block:
        problems.append("SignInGate لا تشترط تسجيل الدخول لعرض المحتوى")
    if "onRequireSignIn(intended)" not in gate_block:
        problems.append("SignInGate لا تنقل غير المسجّل إلى الدخول")
    if "LaunchedEffect(" not in gate_block:
        problems.append("SignInGate تنقل المستخدم خارج أثر جانبي (LaunchedEffect)")
    if "LoadingBox()" not in gate_block:
        problems.append("SignInGate لا تعرض شيئاً أثناء النقل (الشات يجب ألّا يُرى)")
else:
    problems.append(" SignInGate غير معرّفة في AppNav.kt".strip())

# 7) لا طريق إلى الشات يلتفّ على البوابة (نداء تنقّل مباشر من خارج التنقّل)
bypass = []
for path in sorted(glob.glob(os.path.join(SRC, "**", "*.kt"), recursive=True)):
    if path == NAV:
        continue
    code = strip_noise(io.open(path, encoding="utf-8").read())
    if re.search(r"Routes\.chat\(", code) or re.search(r"Routes\.COMMUNITY", code):
        bypass.append(os.path.relpath(path, ROOT))
if bypass:
    problems.append("مسار شات يُستدعى خارج التنقّل (خارج البوابة): " + " · ".join(bypass))

# 8) القاعدة نفسها تمنع الزائر (لا الواجهة وحدها)
SQL = os.path.join(ROOT, "supabase", "30_chat_members_only.sql")
ALL = os.path.join(ROOT, "supabase", "00_ALL_IN_ONE.sql")
BUILD = os.path.join(ROOT, "tools", "build_all_in_one_sql.py")
sql = io.open(SQL, encoding="utf-8").read() if os.path.exists(SQL) else ""
allinone = io.open(ALL, encoding="utf-8").read() if os.path.exists(ALL) else ""
build = io.open(BUILD, encoding="utf-8").read() if os.path.exists(BUILD) else ""
if not sql:
    problems.append("supabase/30_chat_members_only.sql مفقود")
for table, policy in (
    ("public.chat_messages", "messages_read"),
    ("public.chat_reactions", "reactions_read"),
    ("public.chat_channels", "channels_read"),
):
    want = "create policy %s on %s\n  for select using (auth.uid() is not null);" % (policy, table)
    if want not in sql:
        problems.append("سياسة %s لا تحصر القراءة بالمسجّلين" % policy)
if "30_chat_members_only.sql" not in build:
    problems.append("الملف 30 غير مدرج في build_all_in_one_sql.py")
if "٣٠) الشات للمسجّلين فقط" not in allinone:
    problems.append("الملف 30 غير مضمّن في 00_ALL_IN_ONE.sql")

# 9) سطر التوضيح على شاشة الدخول موجود في 8 لغات ومستعمل
missing = []
for path in sorted(glob.glob(os.path.join(RES, "values*", "strings.xml"))):
    if 'name="chat_login_required"' not in io.open(path, encoding="utf-8").read():
        missing.append(os.path.basename(os.path.dirname(path)))
if missing:
    problems.append("chat_login_required ناقص في: " + " · ".join(missing))
if "notice: String? = null" not in auth:
    problems.append("LoginScreen لا يقبل سطر توضيح (notice)")
if "notice != null" not in auth:
    problems.append("LoginScreen لا يعرض سطر التوضيح")

print("الفحص                                   | النتيجة")
print("-" * 60)
rows = [
    ("مسارا الشات محجوبان عن غير المسجّل",
     "destination == Routes.COMMUNITY" in clean and 'destination.startsWith("chat/")' in nav),
    ("go() يوقف روابط الغرف", "if (isChatRoute(route) && !signedIn)" in clean),
    ("goTab() يوقف تبويب المجتمع", "if (target == MainTab.Community && !signedIn)" in clean),
    ("النقل إلى شاشة الدخول", "navController.navigate(Routes.LOGIN)" in clean),
    ("حفظ الوجهة للعودة بعد الدخول", "afterLoginRoute = intended" in clean),
    ("العودة إلى الوجهة بعد الدخول", "fun continueAfterLogin()" in clean),
    ("لا حلقة رجوع (إخراج الشات من الرصة)",
     "standingOnChat" in clean and "navController.popBackStack()" in clean),
    ("قائمة الغرف خلف البوابة", "SignInGate(" in community_block),
    ("شاشة الغرفة خلف البوابة", "SignInGate(" in chat_block),
    ("المحتوى للمسجّلين فقط داخل البوابة", "if (signedIn)" in gate_block),
    ("لا طريق يلتفّ على البوابة", not bypass),
    ("القاعدة تمنع الزائر (رسائل/غرف/تفاعلات)",
     all(x in sql for x in ("messages_read", "reactions_read", "channels_read"))
     and "auth.uid() is not null" in sql),
    ("الملف 30 داخل حزمة SQL الجامعة", "٣٠) الشات للمسجّلين فقط" in allinone),
    ("سطر التوضيح في 8 لغات", not missing),
    ("شاشة الدخول تعرض التوضيح",
     "notice: String? = null" in auth and "notice != null" in auth),
]
for name, ok in rows:
    print(f"{name:<40} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 الشات كلّه خلف تسجيل الدخول: تبويب المجتمع وكل غرفة لا يُفتحان لغير مسجّل، "
      "يُنقل إلى الدخول ثم يعود إلى مكانه — بلا حلقة رجوع وبـ8 لغات.")
