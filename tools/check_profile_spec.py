#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص: شاشات الملف الشخصي الثلاث على وثيقة `SPEC_PROFILES.md` حرفياً.

الوثيقة جاءت من المالك في حقيبة `AddonStation_profiles.zip` مع طلبه:
«ولانغير التصميم المذكور في الملف» — فهي عقد، وهذا الفاحص يحرسه:

  A «ملفي» · B «ملف عضو» (من الشات) · C «ملفي كما يراه الآخرون»

يمنع الرجوع في:
  • رموز الوثيقة (الألوان والمقاسات) في `Spec.kt`.
  • مكوّنات §2 المشتركة (بانر 150 · أفاتار 96 يتجاوز 46 · نقطة 30 · بطاقة 16 …).
  • ترتيب عناصر الشاشة A، وبطاقة السلامة في B، والبطاقة المتقطّعة في C.
  • قرارات المالك: زرّ «مراسلة [الاسم]» مخفيّ · الإحصاءات من دالة الخادم ·
    المستوى = كل 100 AC مكتسَب = مستوى · وفحص الشات مربوط برمز الوقت الحالي.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")
SUPABASE = os.path.join(ROOT, "supabase")

SPEC = os.path.join(SRC, "ui", "theme", "Spec.kt")
COMPONENTS = os.path.join(SRC, "ui", "components", "ProfileSpecUi.kt")
WALLET_UI = os.path.join(SRC, "ui", "components", "WalletSpecUi.kt")
SCREENS = os.path.join(SRC, "ui", "profile", "ProfileScreens.kt")
NAV = os.path.join(SRC, "ui", "nav", "AppNav.kt")
STATS_SQL = os.path.join(SUPABASE, "36_profile_stats.sql")
REPORTS_SQL = os.path.join(SUPABASE, "37_profile_reports.sql")
VERIFY = os.path.join(SUPABASE, "99_VERIFY_ALL.sql")


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


def strip_comments(text):
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.S)
    return re.sub(r"//[^\n]*", "", text)


def body(text, name):
    """جسم الدالة name من `fun name(` حتى قوسها المغلق."""
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


spec = strip_comments(read(SPEC))
comp = strip_comments(read(COMPONENTS))
wallet_ui = strip_comments(read(WALLET_UI))
screens = strip_comments(read(SCREENS))
nav = read(NAV)
stats_sql = read(STATS_SQL)
reports_sql = read(REPORTS_SQL)
verify = read(VERIFY)

problems = []
rows = []


def check(name, ok, why=""):
    rows.append((name, bool(ok)))
    if not ok:
        problems.append(name + ((" — " + why) if why else ""))


# ── ١) رموز الوثيقة في Spec.kt (جدول §1 حرفياً) ──────────────────────────────
TOKENS = {
    "danger": ("FFFF8791", "FFB3262E"),
    "dangerBorder": ("FFB3414B", "FFB32626"),
    "onlineFill": ("FF3FBF6A", "FF17A75A"),
    "onlineText": ("FF7FD99A", "FF1E7A44"),
    "awayAmber": ("FFF0C25E", "FF8A5B00"),
    "offlineGrey": ("FF6B7A94", "FF53627F"),
    "dashedBorder": ("FF2E3C58", "FFD5DEEC"),
}
missing = [
    name for name, (dark, light) in TOKENS.items()
    if f"val {name}: Color get() = if (AppColors.isDark) Color(0x{dark}) else Color(0x{light})" not in spec
]
check("رموز ألوان Spec.kt ذات تباين داكن/فاتح (§1)", not missing, "ناقص: " + " · ".join(missing))

SIZES = {
    "bannerH": "150.dp",
    "avatarLg": "96.dp",
    "avatarOverlap": "46.dp",
    "dotSize": "30.dp",
    "profileCardRadius": "16.dp",
    "badgeRadius": "8.dp",
    "actionBtnH": "48.dp",
    "gridBtnH": "56.dp",
    "safetyRowH": "52.dp",
    "statBarH": "8.dp",
}
missing = [k for k, v in SIZES.items() if not re.search(r"val %s = %s" % (k, re.escape(v)), spec)]
check("مقاسات الوثيقة في Spec.kt (§1 · §2)", not missing, "ناقص: " + " · ".join(missing))

check("تسمية القسم الصغيرة: بكسل 11 · تباعد 1 (§1)",
      re.search(r"val smallCaps = TextStyle\(fontFamily = PixelFont, fontSize = 11\.sp, "
                r"fontWeight = FontWeight\.Bold, letterSpacing = 1\.sp\)", spec) is not None)

# ── ٢) المكوّنات المشتركة (§2) ───────────────────────────────────────────────
check("٢٫١ البانر: تدرّج شفاف 40% ← خلفية 90%",
      "0.40f to Color.Transparent" in comp and "Spec.bg.copy(alpha = 0.90f)" in comp)
check("٢٫١ البانر الافتراضي من الحقيبة (profile_banner_default)",
      "profile_banner_default" in comp)
check("٢٫١ الزرّ الدائري فوق البانر 44×44 · خلفية 60% · أيقونة 22",
      re.search(r"\.size\(Spec\.touch\)[\s\S]{0,120}?Spec\.bg\.copy\(alpha = 0\.60f\)", comp) is not None
      and "Modifier.size(22.dp)" in comp)
check("٢٫٢ الأفاتار 96 بحدّ 5dp بلون الخلفية ولا يُسحقه صفّ التداخل",
      re.search(r"size: Dp = Spec\.avatarLg,[\s\S]{0,200}?borderWidth: Dp = 5\.dp", comp) is not None
      and "requiredSize(size)" in comp
      and "border(borderWidth, Spec.bg, CircleShape)" in comp)
check("٢٫٢ تداخل الأفاتار 46dp مع تعويض قصّ الصف في الشاشات الثلاث",
      screens.count("Box(Modifier.offset(y = -(Spec.avatarOverlap / 2)))") >= 3)
edit_screens = [body(screens, name) for name in ("ProfileScreen", "UserProfileScreen", "ProfileAsOthersScreen")]
check("أزرار تعديل الملف في A/B/C: ارتفاع 44dp وحشو أفقي 20dp",
      all("buttonHeight = 44.dp" in screen and "horizontalPadding = 20.dp" in screen
          for screen in edit_screens)
      and "padding(horizontal = horizontalPadding)" in body(comp, "SpecFillButton"))
status_dot = comp[comp.find("private fun Modifier.specStatusDot"):comp.find("fun SpecIdentityCard")]
check("٢٫٢ نقطة الاتصال خضراء مصمتة بلا هالة أو إطار رمادي",
      "SpecStatus.ONLINE -> Spec.onlineFill" in status_dot
      and "drawCircle(color = fill, radius = r)" in status_dot
      and "Spec.dotStart" not in status_dot
      and "Spec.dotEnd" not in status_dot
      and "r + 14.dp.toPx()" not in status_dot
      and "if (status != SpecStatus.ONLINE)" in status_dot)
check("٢٫٢ «غير متصل» رمادي · «مخفيّ الحالة» بلا نقطة",
      "Spec.offlineGrey" in comp and "if (status != SpecStatus.ONLINE)" in status_dot
      and "status != SpecStatus.HIDDEN" in comp)
check("٢٫٣ سطر الهوية: @username 13/700 + «· الحالة»",
      "Spec.profileHandle" in comp and "statusLabel(status)" in comp)
check("٢٫٣ الشرائح: التفاف · فراغ 6 · هامش علوي 10 · حشو 9×3",
      "FlowRow(" in comp and "spacedBy(6.dp)" in comp and "padding(top = 10.dp)" in comp
      and "padding(horizontal = 9.dp, vertical = 3.dp)" in comp)
check("٢٫٣ شرائح Premium/Creator/Level/ADMIN/MOD",
      all(k in comp for k in ("R.string.badge_premium", "R.string.badge_creator",
                              "R.string.badge_level", "R.string.badge_admin", "R.string.badge_mod")))
check("٢٫٣ صندوق الحالة المخصّصة: barSurface · 12 · 10×12",
      re.search(r"RoundedCornerShape\(12\.dp\)[\s\S]{0,120}?background\(Spec\.barSurface\)"
                r"[\s\S]{0,120}?padding\(horizontal = 12\.dp, vertical = 10\.dp\)", comp) is not None)
check("٢٫٤ شريط الإحصاءات: ثلاثة أعمدة بفواصل 1dp",
      comp.count("Modifier.width(1.dp).height(46.dp).background(Spec.border)") == 2)
check("٢٫٥ شريط المستوى: 8 ارتفاع · تدرّج blue ← blueLight",
      "Spec.statBarH" in comp
      and "Brush.horizontalGradient(listOf(Spec.blue, Spec.blueLink))" in comp)
check("٢٫٦ النبذة: الفاصل 1dp + «عضو منذ»",
      "SpecAboutCard" in comp and "R.string.profile_member_since_label" in comp)
compact_wallet = body(wallet_ui, "WalletCompactCard")
check("٢٫٧ محفظة الملف: عملة 56 · رصيد متحرك · مكافأة وشريط الأيام",
      "WalletCoinImage(56.dp)" in compact_wallet and ".height(44.dp)" in compact_wallet
      and "StreakProgressSegments(" in compact_wallet and "R.string.profile_day_streak" in compact_wallet
      and "onOpenWallet" in compact_wallet and "animateIntAsState" in compact_wallet
      and "tween(durationMillis = 700)" in compact_wallet and "R.string.profile_daily_reward" in compact_wallet
      and "R.string.ac_claim" in compact_wallet and "R.string.profile_daily_claimed" in compact_wallet)
check("محفظة الملف بلا بقعة الضوء الزخرفية بجانب السهم",
      "Brush.radialGradient(" not in compact_wallet
      and "AuthGold.copy(alpha = 0.25f)" not in compact_wallet)
check("إطار متقطّع 1dp (§5) بـ dashPathEffect",
      "PathEffect.dashPathEffect" in comp and "specDashedBorder" in comp)

# ── ٣) الشاشة A (§3) ────────────────────────────────────────────────────────
a = body(screens, "ProfileScreen")
order = ["SpecProfileBanner", "SpecProfileAvatar", "R.string.profile_edit", "SpecIdentityCard",
         "SpecWalletCard", "SpecAboutCard", "SpecStatsStrip", "SpecLevelCard",
         "SpecGridTile", "SpecDangerButton"]
pos = [a.find(k) for k in order]
check("A: ترتيب العناصر التسعة كالوثيقة §3",
      all(p >= 0 for p in pos) and pos == sorted(pos),
      "ترتيب ناقص أو معكوس: " + str(pos))
check("A: البانر بزرّ الرجوع وحده (لا «المزيد»)",
      re.search(r"SpecProfileBanner\([\s\S]{0,200}?onBack = onBack", a) is not None
      and "more = {" not in a)
check("A: المحفظة مباشرة تحت بطاقة الهوية",
      a.find("SpecIdentityCard") < a.find("SpecWalletCard") < a.find("SpecAboutCard"))
check("A: المحفظة و«عنّي» قبل الإحصاءات",
      a.find("SpecWalletCard") < a.find("SpecAboutCard") < a.find("SpecStatsStrip"))
check("A: شبكة 2×2 بالأربعة (المفضّلة · التنزيلات · طلباتي · محتواي)",
      all(k in a for k in ("R.string.profile_tab_favorites", "R.string.profile_stats_downloads",
                           "R.string.profile_orders", "R.string.profile_stats_content")))
check("A: مدخل «عرض كما يراني الآخرون» (شاشة C §5)",
      "ProfileAccountActionsCard(" in a and "onViewAsOthers" in a
      and "R.string.profile_view_as_others" in screens)
check("A: بطاقة إجراءات الحساب (المحفظة · الطلبات · عرض الآخرين)",
      "ProfileAccountActionsCard(" in a
      and all(k in screens for k in ("R.string.settings_account_actions", "R.string.profile_wallet_title",
                                     "R.string.profile_orders", "R.string.profile_hidden_from_others")))
check("A: «تسجيل الخروج» بإطار الخطر 48h",
      "SpecDangerButton" in a and "R.string.profile_logout" in a)
check("A: الأرقام من دالة الخادم (stats) لا من قوائم محجوبة",
      "container.profile.stats(" in a)
check("A: «مكافأة اليوم» تُعطَّل بعد الاستلام (حالة الخادم)",
      "dailyClaimed" in a and "statsTick" in a)

# ── ٤) الشاشة B (§4) ────────────────────────────────────────────────────────
b = body(screens, "UserProfileScreen")
check("B: البانر برجوع يسار + «المزيد» يمين",
      "onBack = onBack" in b and "SpecBannerIconButton" in b and "Icons.Filled.MoreVert" in b)
check("B: بلا محفظة ولا طلبات ولا مفضّلة ولا إعدادات",
      "SpecWalletCard" not in b and "SpecGridTile" not in b and "onOpenWallet" not in b)
check("B: بطاقة السلامة الثلاثية (كتم · حظر · إبلاغ)",
      all(k in b for k in ("chat_mute_user", "block_user", "chat_report")))
check("B: تأكيد الحظر + رسالة النجاح",
      "block_confirm_title" in b and "block_done" in b)
check("B: اختيار سبب الإبلاغ (أربعة أسباب)",
      all(k in b for k in ("chat_reason_spam", "chat_reason_insult",
                           "chat_reason_inappropriate", "chat_reason_other")))
check("B: بطاقة الإشراف للأدمن/المشرف وحدهما",
      "if (myProfile?.isStaff == true)" in b and "R.string.profile_moderation" in b
      and "R.string.profile_delete_message" in b)
check("B: الحظر يُبدَّل بحالة واحدة معطَّلة/«إلغاء الحظر»",
      "blockedPair" in b and "R.string.profile_blocked_state" in b)
check("قرار المالك: زرّ «مراسلة [الاسم]» مخفيّ (لا محادثات خاصة في الخادم)",
      "R.string.profile_message" not in screens and "onMessage" not in b)
check("B: زرّ الصداقة بحالاته (طلب · أُرسل · أصدقاء · قبول/رفض)",
      all(k in screens for k in ("R.string.profile_friend_request", "R.string.profile_request_sent",
                                 "R.string.profile_friends_state", "R.string.profile_accept",
                                 "R.string.profile_reject")))
check("B: «أصدقاء ✓» تُلغي الصداقة بعد تأكيد",
      "removeFriend" in b and "R.string.profile_unfriend" in b)
check("المستوى في الشاشات من الخادم (لا حساب محلي مخترع)",
      "stats?.level" in a and "stats?.level" in b)

# ── ٥) الشاشة C (§5) ────────────────────────────────────────────────────────
c = body(screens, "ProfileAsOthersScreen")
check("C: البانر برجوع فقط",
      "SpecProfileBanner" in c and "onBack = onBack" in c and "more = {" not in c)
check("C: صفّ الأفاتار مع «تعديل الملف»",
      "SpecProfileAvatar" in c and "R.string.profile_edit" in c)
check("C: بطاقة المحفظة المتقطّعة بدل الحقيقية",
      "SpecHiddenWalletCard" in c and "SpecWalletCard" not in c)
check("C: بلا صفّ أزرار · بلا بطاقة سلامة · بلا خروج",
      "SpecDangerButton" not in c and "block_user" not in c and "chat_report" not in c
      and "FriendActionButtons" not in c)
check("C: الهوية والإحصاءات والمستوى والنبذة كما يراها الزائر",
      all(k in c for k in ("SpecIdentityCard", "SpecStatsStrip", "SpecLevelCard", "SpecAboutCard")))

# ── ٦) المسارات والنصوص ─────────────────────────────────────────────────────
check("مساران جديدان: «محتواي» و«كما يراني الآخرون»",
      'const val MY_CONTENT = "my-content"' in nav
      and 'const val PROFILE_AS_OTHERS = "profile-as-others"' in nav)
check("التنقّل يربط الشاشات الثلاث بمساراتها",
      "onViewAsOthers = { go(Routes.PROFILE_AS_OTHERS) }" in nav
      and "ProfileAsOthersScreen(" in nav and "onOpenMyContent = { go(Routes.MY_CONTENT) }" in nav)
NEW_KEYS = ["profile_wallet_title", "profile_daily_reward", "profile_daily_claimed",
            "profile_view_as_others", "profile_hidden_from_others", "profile_orders",
            "profile_logout", "profile_member_since_label", "profile_moderation",
            "profile_ban_user", "profile_delete_message",
            "profile_action_done", "chat_unmute_user", "chat_nobody_online",
            "badge_premium", "badge_admin", "badge_mod", "profile_status_online"]
missing_langs = []
for path in sorted(os.listdir(RES)):
    if not path.startswith("values"):
        continue
    file = os.path.join(RES, path, "strings.xml")
    if not os.path.exists(file):
        continue
    text = io.open(file, encoding="utf-8").read()
    if any('name="%s"' % k not in text for k in NEW_KEYS):
        missing_langs.append(path)
check("النصوص الجديدة في كل اللغات الثماني", not missing_langs,
      "ناقص في: " + " · ".join(missing_langs))

# ── ٧) الخادم (٣٦ · ٣٧) ─────────────────────────────────────────────────────
check("٣٦: دالة إحصاءات عامة (security definer · أعداد فقط)",
      "create or replace function public.profile_stats" in stats_sql
      and "security definer" in stats_sql and "auth.uid()" in stats_sql)
check("٣٦: قاعدة المستوى — كل 100 AC مكتسَب = مستوى (وتعديل الإدارة فوقها)",
      re.search(r"greatest\(n\.stored_level, 1 \+ \(n\.earned / 100\)\)", stats_sql) is not None
      and "(n.earned % 100)" in stats_sql)
check("٣٦: أوصاف الأعمدة التسعة (تشمل streak و daily_*)",
      all(k in stats_sql for k in ("friends", "downloads", "favorites", "xp_in_level",
                                   "xp_to_next", "streak", "daily_claimed", "daily_reward")))
check("٣٦: سلسلة الأيام محسوبة من أيام استلام المهام",
      "ac_task_claims" in stats_sql)
check("٣٧: الإبلاغ عن عضو (عمود + دالة + منع النفس + مرة كل 24 ساعة)",
      "target_user_id" in reports_sql and "chat_report_user" in reports_sql
      and "cannot_report_self" in reports_sql and "24 hours" in reports_sql)
check("٣٧: سبب من الأربعة فقط (كما في chat_reports)",
      "p_reason not in ('spam', 'insult', 'inappropriate', 'other')" in reports_sql)
check("الفاحص 99 يعرف الملفين (بندان 54 و55)",
      "36_profile_stats" in verify and "37_profile_reports" in verify
      and "select 54," in verify and "select 55," in verify)

# ── ٨) الشات: تحقق إيجابي مناسب للتصميم الحالي لا يعتمد على رمز قديم ─────────
chat_screen = read(os.path.join(SRC, "ui", "chat", "ChatScreen.kt"))
check("الشات يبني طوابع الوقت على نمط Spec الحالي",
      "Spec.msgTime" in chat_screen)

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
print("🟢 الشاشات الثلاث على وثيقة SPEC_PROFILES: الرموز والمقاسات والترتيب وقرارات "
      "المالك (المراسلة مخفيّة · إحصاءات من الخادم · 100 AC = مستوى) كلها مثبّتة.")
