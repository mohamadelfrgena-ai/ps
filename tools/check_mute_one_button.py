#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص: كتم العضو — «زرّ واحد» يُسقط الرسائل والإشعارات · ميزة اشتراك Legendary.

طلب المالك (حرفياً):
    «نحتاج زر واحد وكتم الاشعارات وينفتح في اشتراك ليغسي
     وايضا يكون في الاعدت التطبيق زر اطفاء وتشغيل بدون كثر كلام فاضي»

فيتحقّق هذا الفاحص من الأربعة بنداً بنداً:
  ① زرّ واحد: صفّ كتم واحد في بطاقة السلامة (لا زرّ للرسائل وزرّ للإشعارات).
  ② كتم الإشعارات: جدول user_mutes + زناد يُسقط صفّ الإشعار (38_user_mute.sql)
     فيمنع الجرس والدفع معاً، والتطبيق يُخفي رسائله في الشات.
  ③ ينفتح في اشتراك Legendary: can_mute_members على الخادم + الواجهة تفتح
     صفحة اشتراك Legendary بدل التنفيذ.
  ④ زرّ إطفاء/تشغيل في الإعدادات: عمود push_prefs.muted_users + مفتاح في شاشة
     إعدادات الإشعارات.
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
stop = screen_b.find("if (me.isStaff)")
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

# ── ② كتم الإشعارات ─────────────────────────────────────────────────────────
check("② جدول user_mutes في 38_user_mute.sql",
      "create table if not exists public.user_mutes" in sql)
check("② زناد يُسقط إشعارات المكتوم قبل إدراجها (لا جرس ولا دفع)",
      "create trigger trg_push_drop_muted_actor" in sql
      and "before insert on public.chat_notifications" in sql
      and "return null;" in sql)
check("② الزناد يقارن المستلم بالمرسل عبر is_muted_by",
      "is_muted_by(new.recipient_id, new.actor_id)" in sql)
check("② دالة is_muted_by تحترم مفتاح الإعدادات (push_prefs.muted_users)",
      "pr.muted_users" in sql)
check("② التطبيق يُخفي رسائل المكتوم في الشات (فلترة قائمة المكتومين)",
      "_mutedIds" in vm and "user_mutes" not in vm and "mutedMemberIds()" in vm)
check("② قائمة المكتومين تُحدَّث محلياً من الخادم (فتُخفى الرسائل فوراً)",
      "container.prefs.setMutedUsers(ids)" in vm and "_mutedIds.value = ids" in vm)

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

# ── ④ زرّ إطفاء/تشغيل في الإعدادات ─────────────────────────────────────────
check("④ عمود push_prefs.muted_users في 38",
      "add column if not exists muted_users boolean not null default true" in sql)
check("④ push_set_prefs يستقبل p_muted_users (مع إسقاط النسخة القديمة)",
      "p_muted_users   boolean default null" in sql
      and "drop function if exists public.push_set_prefs(" in sql)
check("④ الحفظ من التطبيق يمرّر المفتاح",
      '"p_muted_users" to p(mutedUsers)' in push_repo)
check("④ النموذج يحمل الحقل (push_prefs.muted_users)",
      'val mutedUsers: Boolean = true' in models)
check("④ مفتاح في شاشة إعدادات الإشعارات",
      "R.string.notif_muted_users" in notif and "mutedUsers = next.mutedUsers" in notif)
check("④ المفتاح مرتبط بالاشتراك (enabled = canMute)",
      re.search(r"checked = prefs\?\.mutedUsers != false,\s*\n\s*enabled = canMute", notif) is not None)

# ── الترحيل والفحص والنصوص ──────────────────────────────────────────────────
check("38 مُدرَج في 00_ALL_IN_ONE (باني الملف الجامع)",
      "38_user_mute.sql" in read(os.path.join(ROOT, "tools", "build_all_in_one_sql.py"))
      and "user_mutes" in all_in_one)
check("السير ينفّذ 38 تلقائياً مع البقية",
      ('run_sql("supabase/38_user_mute.sql")' in workflow)
      or ("run_server_sql.py" in workflow
          and "38_user_mute.sql" in read(os.path.join(ROOT, "tools", "run_server_sql.py"))))
check("الفاحص 99 فيه بند 56 لـ38_user_mute",
      "select 56," in verify and "38_user_mute" in verify)

KEYS = ["mute_member_locked", "mute_member_needs_plan", "notif_mute_section",
        "notif_muted_users", "notif_muted_users_desc", "notif_muted_users_locked"]
missing = []
for name in sorted(os.listdir(RES)):
    file = os.path.join(RES, name, "strings.xml")
    if not name.startswith("values") or not os.path.exists(file):
        continue
    text = io.open(file, encoding="utf-8").read()
    if any('name="%s"' % k not in text for k in KEYS):
        missing.append(name)
check("النصوص الجديدة في كل اللغات الثماني", not missing, "ناقص في: " + " · ".join(missing))

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
print("🟢 الكتم: زرّ واحد ⇒ لا رسائل ولا إشعارات · يفتح اشتراك Legendary لغيره · "
      "ومفتاح إطفاء/تشغيل في الإعدادات.")
