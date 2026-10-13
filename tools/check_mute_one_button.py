#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص: كتم العضو — «زرّ واحد» يمنع الرسائل · ميزة اشتراك Legendary.

طلب المالك أولاً: «نحتاج زر واحد وكتم الاشعارات وينفتح في اشتراك ليغسي»
ثم قراره الأخير: «اعني الشعارات رجعها كما كنت» ⇒ الإشعارات رجعت كما كانت.

فيتحقّق هذا الفاحص من الأربعة بنداً بنداً:
  ① زرّ واحد: صفّ كتم واحد في بطاقة السلامة (لا زرّ للرسائل وزرّ للإشعارات).
  ② الإشعارات كما كانت: لا زناد يُسقط إشعارات المكتوم ولا عمود `muted_users`
     (38 نظيف · و39 يُزيل ما نُفِّذ سابقاً)، والكتم يمنع الرسائل في التطبيق.
  ③ ينفتح في اشتراك Legendary: can_mute_members على الخادم + الواجهة تفتح
     صفحة اشتراك Legendary بدل التنفيذ.
  ④ لا مفتاح في الإعدادات: شاشة إعدادات الإشعارات رجعت كما كانت (لا قسم كتم،
     ولا حقل في النموذج، ولا وسيط في push_set_prefs).
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
SUPABASE = os.path.join(ROOT, "supabase")

MUTE_SQL = os.path.join(SUPABASE, "38_user_mute.sql")
REVERT_SQL = os.path.join(SUPABASE, "39_notif_revert.sql")
VERIFY = os.path.join(SUPABASE, "99_VERIFY_ALL.sql")
ALL_IN_ONE = os.path.join(SUPABASE, "00_ALL_IN_ONE.sql")
WORKFLOW = os.path.join(ROOT, ".github", "workflows", "push-live.yml")
PROFILE = os.path.join(SRC, "ui", "profile", "ProfileScreens.kt")
NOTIF = os.path.join(SRC, "ui", "settings", "NotificationSettingsScreen.kt")
NAV = os.path.join(SRC, "ui", "nav", "AppNav.kt")
CHAT_REPO = os.path.join(SRC, "data", "repo", "ChatRepository.kt")
CHAT_VM = os.path.join(SRC, "ui", "chat", "ChatViewModel.kt")
PUSH_REPO = os.path.join(SRC, "data", "repo", "PushRepository.kt")
MODELS = os.path.join(SRC, "data", "model", "Models.kt")


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def strip_sql(text):
    """يُزيل تعليقات SQL كي لا ينجح الفحص بسبب كلمةٍ في شرح."""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"--[^\n]*", "", text)


def body(text, name):
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


sql = read(MUTE_SQL)
revert = read(REVERT_SQL)
verify = read(VERIFY)
all_in_one = read(ALL_IN_ONE)
workflow = read(WORKFLOW)
profile = strip_comments(read(PROFILE))
notif = strip_comments(read(NOTIF))
nav = read(NAV)
repo = strip_comments(read(CHAT_REPO))
vm = strip_comments(read(CHAT_VM))
push_repo = strip_comments(read(PUSH_REPO))
models = strip_comments(read(MODELS))

problems = []
rows = []


def check(name, ok, why=""):
    rows.append((name, bool(ok)))
    if not ok:
        problems.append(name + ((" — " + why) if why else ""))


# ── ① زرّ واحد ──────────────────────────────────────────────────────────────
screen_b = body(profile, "UserProfileScreen")
# بطاقة السلامة وحدها: من أول بطاقة (حشو 0) إلى بطاقة الإشراف (للمشرفين)
start = screen_b.find("SpecCard(padding = 0.dp)")
stop = screen_b.find("if (myProfile?.isStaff == true)")
safety = screen_b[start:stop] if start >= 0 and stop > start else ""
# صفّ الكتم = صفّ واحد مكتوب بـ SpecActionRow ونصّه يتبدّل (كتم ↔ إلغاء الكتم)
mute_rows = len(re.findall(
    r"SpecActionRow\(\s*\n?\s*text = stringResource\(if \(muted\) R\.string\.chat_unmute_user",
    safety,
))
check("① صفّ كتم واحد في بطاقة السلامة (لا زرّان)", mute_rows == 1,
      f"عدد صفوف الكتم: {mute_rows}")
check("① صفّ الكتم بنصّه المتبدّل (كتم ↔ إلغاء الكتم)",
      "R.string.chat_unmute_user" in screen_b and "R.string.chat_mute_user" in screen_b)
check("① الكتم يمرّ عبر الخادم (لا محليّ وحده)",
      "container.chat.muteMember(" in screen_b and "container.chat.unmuteMember(" in screen_b)
check("① لا يُبقي زرّ كتم محلياً قديماً (لا كتابة مباشرة في الإعدادات)",
      "container.prefs.setMutedUsers(ids)" in screen_b)

# ── ② الإشعارات كما كانت (لا مساس بها) ──────────────────────────────────────
check("② جدول user_mutes في 38_user_mute.sql (خادم الكتم)",
      "create table if not exists public.user_mutes" in sql)
sql_only = strip_sql(sql)
revert_only = strip_sql(revert)
check("② 38 نظيف من الإشعارات: لا زناد إسقاط ولا دالة إسقاط",
      "trg_push_drop_muted_actor" not in sql_only
      and "push_drop_muted_actor" not in sql_only
      and "is_muted_by" not in sql_only)
check("② 38 لا يضيف عموداً ولا يمسّ push_prefs",
      "push_prefs" not in sql_only and "add column" not in sql_only)
check("② 39 يُزيل ما نُفِّذ سابقاً (زناد + دالتان + عمود)",
      "drop trigger if exists trg_push_drop_muted_actor on public.chat_notifications" in revert
      and "drop function if exists public.push_drop_muted_actor();" in revert
      and "drop function if exists public.is_muted_by(uuid, uuid);" in revert
      and "alter table public.push_prefs drop column if exists muted_users;" in revert)
check("② 39 يُعيد push_set_prefs بعشرة وسائط (كما في 34)",
      "boolean, boolean, boolean, boolean, boolean, boolean, boolean, int, int, int, boolean"
      in revert_only and "p_muted_users" not in revert_only)
check("② التطبيق يُخفي رسائل المكتوم في الشات (فلترة قائمة المكتومين)",
      "_mutedIds" in vm and "mutedMemberIds()" in vm)
check("② قائمة المكتومين تُحدَّث محلياً من الخادم (فتُخفى الرسائل فوراً)",
      "container.prefs.setMutedUsers(muted)" in vm and "_mutedIds.value = muted" in vm)
others = [f for f in sorted(os.listdir(SUPABASE))
          if f.endswith(".sql") and f not in ("39_notif_revert.sql", "00_ALL_IN_ONE.sql")]
check("② دالة is_muted_by لم تبقَ إلا في ملف التنظيف 39",
      all("is_muted_by" not in strip_sql(read(os.path.join(SUPABASE, f))) for f in others))

# ── ③ اشتراك Legendary ──────────────────────────────────────────────────────
check("③ الخادم: can_mute_members = اشتراك Legendary",
      re.search(r"effective_plan\(p_user\) = 'legendary'", sql) is not None)
check("③ الخادم يرفض غير المشترك بـ legendary_required",
      "legendary_required" in sql)
check("③ الواجهة تعرف الحالة (canMute) وتفتح صفحة الاشتراك",
      "val canMute" in screen_b and "onOpenPlans()" in screen_b)
check("③ الشارة الظاهرة: «Legendary» على صفّ الكتم",
      "R.string.mute_member_locked" in screen_b)
check("③ المسار يفتح اشتراك Legendary نفسه",
      'onOpenPlans = { go(Routes.checkout("legendary")) }' in nav)
check("③ السبب يُشرح في سطر واحد (لا كلام كثير)",
      "R.string.mute_member_needs_plan" in screen_b)

# ── ④ لا مفتاح في الإعدادات (الشاشة رجعت كما كانت) ─────────────────────────
check("④ شاشة إعدادات الإشعارات بلا قسم كتم ولا مفتاح",
      "notif_mute_section" not in notif and "notif_muted_users" not in notif
      and "mutedUsers" not in notif)
check("④ النموذج PushPrefs بلا حقل mutedUsers",
      "mutedUsers" not in models)
check("④ PushRepository لا يمرّر p_muted_users",
      "p_muted_users" not in push_repo and "mutedUsers" not in push_repo)

# ── الترحيل والفحص والنصوص ──────────────────────────────────────────────────
check("38 و39 مُدرَجان في 00_ALL_IN_ONE (باني الملف الجامع)",
      "38_user_mute.sql" in read(os.path.join(ROOT, "tools", "build_all_in_one_sql.py"))
      and "39_notif_revert.sql" in read(os.path.join(ROOT, "tools", "build_all_in_one_sql.py"))
      and "user_mutes" in all_in_one and "39_notif_revert" in all_in_one)
check("السير ينفّذ 38 و39 تلقائياً مع البقية",
      "run_server_sql.py" in workflow
      and "38_user_mute.sql" in read(os.path.join(ROOT, "tools", "run_server_sql.py"))
      and "39_notif_revert.sql" in read(os.path.join(ROOT, "tools", "run_server_sql.py")))
check("الفاحص 99 فيه بند 56 لـ38_user_mute",
      "select 56," in verify and "38_user_mute" in verify)

# النصوص: مفتاحان فقط لزرّ الكتم — ومفاتيح الإشعارات الأربعة **مُزالة**
KEYS = ["mute_member_locked", "mute_member_needs_plan"]
GONE = ["notif_mute_section", "notif_muted_users", "notif_muted_users_desc",
        "notif_muted_users_locked"]
missing, leftovers = [], []
for name in sorted(os.listdir(RES)):
    file = os.path.join(RES, name, "strings.xml")
    if not name.startswith("values") or not os.path.exists(file):
        continue
    text = io.open(file, encoding="utf-8").read()
    if any('name="%s"' % k not in text for k in KEYS):
        missing.append(name)
    if any('name="%s"' % k in text for k in GONE):
        leftovers.append(name)
check("نصوص زرّ الكتم في كل اللغات الثماني", not missing, "ناقص في: " + " · ".join(missing))
check("نصوص مفتاح الإشعارات مُزالة من كل اللغات", not leftovers,
      "بقي في: " + " · ".join(leftovers))

print("الفحص                                                     | النتيجة")
print("-" * 66)
for name, ok in rows:
    print(f"{name:<57} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 الكتم: زرّ واحد لرسائل العضو · يفتح اشتراك Legendary لغيره · "
      "والإشعارات كما كانت (لا إسقاط ولا مفتاح في الإعدادات).")
