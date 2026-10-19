#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
حارس الأداء (Jank Guard) — يمنع عودة أسباب «اللاق والتقطيع والثِقَل» التي
أُصلحت في جولة الأداء، لأن كلاًّ منها كان يبدو في الكود بريئاً:

  1) توليف فرعي (SubcomposeAsyncImage) في المكوّنات التي تُعرض بالمئات داخل
     القوائم والشبكات (الأفاتار · الغلاف · الأصول) ⇒ تقطيع عند التمرير.
     البديل المعتمد: AsyncImage + placeholder/error من painterResource.

  2) قراءة الجلسة من Keystore في كل نداء: `SessionStore.current()` يجب أن
     تقرأ من الذاكرة، ومخزن EncryptedSharedPreferences يُقرأ مرة واحدة فقط
     ⇒ كانت مئات عمليات فكّ التشفير في الثانية.

  3) البحث الخطّي في قائمة الأصول لعرض الأداة المجهّزة (٤٩٢ أصلاً لكل رسالة
     في كل إطار) ⇒ يجب استعمال فهرس `itemUrlIndex` (O(1)).

  4) الصفحة الرئيسية بعمود تمرير عادي (verticalScroll) ⇒ بناء كل الأقسام مع
     أول إطار. المعتمد: LazyColumn.

  5) حساب نصّ فاصل التاريخ مرّتين لكل رسالة في كل إطار ⇒ يجب الاعتماد على
     `ChatMessageUi.dayKey` (يُحسب مرة واحدة) ومقارنته نصّياً.

  6) شاشة البداية: المقدمة الأولى 6.6s، ثم حلقة 1.5s؛ العبارات المترجمة
     تظهر في كل تشغيل. الشريط يجمع مراحل الإقلاع الفعلية مع حدّ زمني أدنى،
     وينتظر الجاهزية أو انتهاء المهلة، مع نبض متحرك وتخطي صريح واحترام تقليل الحركة.

الاستخدام:
    python3 tools/check_jank_guard.py        # 0 = سليم · 1 = عاد أحد الأسباب
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")

problems: list[str] = []


def read(rel: str) -> str:
    path = os.path.join(SRC, rel)
    if not os.path.exists(path):
        problems.append(f"ملف مفقود: {rel}")
        return ""
    return io.open(path, encoding="utf-8").read()


def code_lines(text: str) -> str:
    """الكود بلا تعليقات (كي لا يُحاسب الحارس على شرح يذكر النمط الممنوع)."""
    return re.sub(r"(?m)^\s*(//|\*|/\*).*$", "", text)


# 1) لا توليف فرعي في مكوّنات القوائم
common = read("ui/components/Common.kt")
if "SubcomposeAsyncImage(" in code_lines(common):
    problems.append("ui/components/Common.kt: عاد SubcomposeAsyncImage — استعمل AsyncImage مع placeholder/error")
if "SubcomposeAsyncImage" in common and "import coil.compose.SubcomposeAsyncImage" in common:
    problems.append("ui/components/Common.kt: الاستيراد الخاص بالتوليف الفرعي عاد")
if re.search(r"\.size\(\s*px\s*\)", common) is None:
    problems.append("ui/components/Common.kt: طلب الصورة بلا مقاس (size) ⇒ فكّ ترميز بأبعاد الأصل")

# 2) الجلسة من الذاكرة لا من Keystore
store = read("data/SessionStore.kt")
if "loaded" not in store or "cache" not in store:
    problems.append("data/SessionStore.kt: ذاكرة الجلسة الداخلية مفقودة (قراءة Keystore في كل نداء)")
reads = [m for m in re.finditer(r"prefs\.(getString|getLong|getInt|getBoolean)", store)]
read_fn = re.search(r"private fun readFromDisk\(\)[\s\S]*?\n    \}", store)
allowed = read_fn.group(0) if read_fn else ""
for m in reads:
    if m.group(0) not in allowed:
        problems.append(f"data/SessionStore.kt: قراءة المخزن خارج الدالة readFromDisk ({m.group(0)})")

# 3) فهرس الأدوات O(1)
env = read("ui/AppEnv.kt")
if "itemUrlIndex" not in env:
    problems.append("ui/AppEnv.kt: equippedItemUrl بلا فهرس (بحث خطّي في كل ٤٩٢ أصلاً)")
if "siteAssets.value.firstOrNull" in env:
    problems.append("ui/AppEnv.kt: عاد المسح الخطّي لقائمة الأصول")
state = read("data/AppState.kt")
if "buildItemUrlIndex" not in state:
    problems.append("data/AppState.kt: دالة بناء فهرس الأدوات مفقودة")

# 4) الرئيسية قائمة كسولة
home = read("ui/home/HomeScreen.kt")
if "verticalScroll" in code_lines(home):
    problems.append("ui/home/HomeScreen.kt: عاد العمود المتمرّر (verticalScroll) — استعمل LazyColumn")
if "LazyColumn(" not in home:
    problems.append("ui/home/HomeScreen.kt: الأقسام ليست داخل LazyColumn")

# 5) فاصل التاريخ مرة واحدة لكل رسالة
chat = read("ui/chat/ChatScreen.kt")
label_calls = len(re.findall(r"TimeFormat\.dayLabel\(", code_lines(chat)))
if label_calls > 1:
    problems.append(f"ui/chat/ChatScreen.kt: dayLabel تُنادى {label_calls} مرات (يجب ألّا تتجاوز مرة واحدة)")
if "dayKey" not in chat:
    problems.append("ui/chat/ChatScreen.kt: لا يستعمل dayKey لمقارنة أيام الرسائل")
if "session()" in chat:
    per_item = re.findall(r"myId = myId", chat)
    live = re.findall(r"supabase\.session\(\)", code_lines(chat))
    if len(live) > 2:
        problems.append(f"ui/chat/ChatScreen.kt: قراءة الجلسة {len(live)} مرات داخل الشاشة (يجب مرة واحدة + مرة للوحة الإجراءات)")
    if not per_item:
        problems.append("ui/chat/ChatScreen.kt: myId لم يُمرَّر للعناصر (قراءة لكل رسالة)")
models = read("data/model/Models.kt")
if "val dayKey" not in models:
    problems.append("data/model/Models.kt: ChatMessageUi.dayKey مفقود")
fmt = read("util/TimeFormat.kt")
if "fun localDayKey(" not in fmt:
    problems.append("util/TimeFormat.kt: localDayKey مفقودة")

# 6) البداية: النصوص في كل وضع، والتقدم مربوط بمراحل الإقلاع والجاهزية
splash = read("ui/splash/SplashScreen.kt")
for token, description in (
    ("FULL_TIMELINE_MS = 5_200", "مدة المقدمة الجديدة 5.2s"),
    ("COMPACT_LOOP_MS = 1_500", "حلقة التشغيلات التالية 1.5s"),
    ("MIN_SPLASH_MS = 1_500L", "الحد الأدنى لعرض البداية 1.5s"),
    ("LaunchedEffect(introComplete, skipRequested, startupReady)", "الخروج مرتبط باكتمال الحركة وجاهزية الإقلاع"),
    ("if (!introComplete || completionHandled || (!startupReady && !skipRequested)) return@LaunchedEffect", "الخروج ينتظر الجاهزية إلا عند التخطي ولا يتكرر"),
    ("introComplete = true", "علامة اكتمال المقدمة"),
    ("skipRequested", "تخطي الحركة بالنقر"),
    ("AppPrefs.splashCompleted(context)", "تذكّر اكتمال أول تشغيل"),
    ("waitingPulse.animateTo(", "نبض متحرك لشريط التحميل"),
    ("ProgressBarRangeInfo(semanticProgress", "التقدم متاح لقارئات الشاشة"),
    ("if (compactMode) 0.14f else 0.61f", "حدّ زمني أدنى وفق المقدمة الجديدة"),
    ("startupReady || skipRequested -> 1f", "اكتمال الشريط عند الجاهزية أو التخطي"),
):
    if token not in splash:
        problems.append(f"ui/splash/SplashScreen.kt: {description} غير موجود")
for key in ("splash_dream_it", "splash_build_it", "splash_create_it"):
    if f"R.string.{key}" not in splash:
        problems.append(f"ui/splash/SplashScreen.kt: العبارة المترجمة {key} غير مستخدمة")
if "if (!compactMode)" in splash:
    problems.append("ui/splash/SplashScreen.kt: العبارات/الوصف ما زالت محجوبة في التشغيلات المختصرة")
if "startupReady" not in splash or "loadingProgress" not in splash:
    problems.append("ui/splash/SplashScreen.kt: حالة الإقلاع/التقدم غير موصولة بالشريط")
nav = read("ui/nav/AppNav.kt")
if "if (!splashDone)" not in nav:
    problems.append("ui/nav/AppNav.kt: شاشة البداية ليست طبقة فوق المحتوى (المحتوى يُبنى خلفها)")
if "startupReady by remember" not in nav or "startupProgress by remember" not in nav:
    problems.append("ui/nav/AppNav.kt: حالة الجاهزية/التقدم غير معرفة عند الإقلاع")
if "withTimeoutOrNull(10_000L)" not in nav:
    problems.append("ui/nav/AppNav.kt: الإقلاع قد يعلّق شاشة البداية بلا حدّ زمني")
for token in ("val refresh = async", "val assets = async", "val plans = async", "val paymentMethods = async"):
    if token not in nav:
        problems.append(f"ui/nav/AppNav.kt: تحميل الإقلاع المتوازي مفقود ({token})")

print("حارس الأداء (Jank Guard)")
print("-" * 52)
print(f"فُحص: Common · SessionStore · AppEnv · AppState · Home · Chat · Splash · AppNav")
print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عاد {len(problems)} سبباً من أسباب التقطيع/الثِقَل — أصلحها قبل البناء.")
    sys.exit(1)
print("🟢 لا توليف فرعي في القوائم · الجلسة من الذاكرة · فهرس أدوات O(1) ·")
print("   الرئيسية كسولة · فاصل التاريخ مرة واحدة · المقدمة الجديدة 5.2s والحلقة 1.5s،")
print("   العبارات المترجمة ظاهرة في كل تشغيل · الشريط يعكس تقدم الإقلاع وينبض حتى الجاهزية · المهلة والتخطي يمنعان الانتظار المفتوح.")
