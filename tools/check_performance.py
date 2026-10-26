#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص إصلاحات الأداء الجذريّة (طلب المالك: «إصلاحات تقنية جذريّة للمشاكل»).

الأصول التي يعالجها هذا الفحص (ويمنع رجوعها):

  ١) **قائمة الشات بلا مفتاح** ⇒ Compose يعيد تركيب كل الرسائل الظاهرة عند أي
     تغيير، وتنتقل حالات remember (روابط الصور الموقَّعة) إلى رسائل أخرى.
  ٢) **استطلاع الشات كل ثانيتين** ⇒ طلبان كل ثانيتين + **إعادة تركيب الشاشة كل
     ثانيتين** (أصل التقطّع واستنزاف البطارية)، حتى في الخلفية.
  ٣) **إسناد الحالة بلا تغيير** ⇒ إعادة تركيب بلا سبب.
  ٤) **N+1 في قائمة المبدعين** ⇒ حتى ٦١ رحلة شبكة لفتح الشاشة الرئيسية.
  ٥) **لا ذاكرة مؤقّتة** ⇒ كل تبديل تبويب أو رجوع = رحلات شبكة ومؤقّت تحميل.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHAT = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "ui", "chat", "ChatScreen.kt")
VM = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                  "addonstation", "mobile", "ui", "chat", "ChatViewModel.kt")
CONTENT = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                       "addonstation", "mobile", "data", "repo", "ContentRepository.kt")
CREATORS = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                        "addonstation", "mobile", "data", "repo", "CreatorRepository.kt")
TTL = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                   "addonstation", "mobile", "data", "repo", "TtlCache.kt")
HOME = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "ui", "home", "HomeScreen.kt")

problems = []
files = {
    "ChatScreen.kt": CHAT,
    "ChatViewModel.kt": VM,
    "ContentRepository.kt": CONTENT,
    "CreatorRepository.kt": CREATORS,
    "TtlCache.kt": TTL,
    "HomeScreen.kt": HOME,
}
texts = {}
for name, path in files.items():
    texts[name] = io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""
    if not texts[name]:
        problems.append("%s مفقود" % name)

chat, vm = texts["ChatScreen.kt"], texts["ChatViewModel.kt"]
content, creators = texts["ContentRepository.kt"], texts["CreatorRepository.kt"]
ttl, home = texts["TtlCache.kt"], texts["HomeScreen.kt"]

# ١) مفتاح لقائمة الرسائل (ونوع محتوى لإعادة الاستعمال)
if "itemsIndexedCompat(" not in chat or "key = { it.message.id }" not in chat:
    problems.append("قائمة الرسائل بلا مفتاح (key) ⇒ إعادة تركيب كل الرسائل")
helper = re.search(r"private inline fun <T> [^\n]*itemsIndexedCompat\([\s\S]{0,600}?\n\}\n", chat)
helper_text = helper.group(0) if helper else ""
if helper_text:
    if "key = { index -> key(items[index]) }" not in helper_text:
        problems.append("itemsIndexedCompat لا يمرّر المفتاح إلى items()")
    if "contentType" not in helper_text:
        problems.append("itemsIndexedCompat بلا contentType")
else:
    problems.append("itemsIndexedCompat غير معرّفة")

# ٢) استطلاع متكيّف: يتوقّف في الخلفية، ويخفّ مع Realtime
if "screenActive" not in vm:
    problems.append("الاستطلاع لا يعرف هل الشاشة معروضة (يعمل في الخلفية)")
if "if (!screenActive) continue" not in vm:
    problems.append("الاستطلاع لا يتوقّف عن العمل حين تكون الشاشة غير معروضة")
if "realtimeHealthy" not in vm:
    problems.append("الاستطلاع لا يخفّ حين يكون اتصال Realtime سليماً")
periods = re.findall(r"(\d+)_000L", vm)
if not any(p == "10" for p in periods):
    problems.append("لا مهلة نوم طويلة للخلفية في الاستطلاع")
if "setScreenActive(true)" not in chat or "setScreenActive(false)" not in chat:
    problems.append("الشاشة لا تُعلِم النموذج بعرضها/مغادرتها")

# ٣) لا إسناد بلا تغيير
if "if (names != _typing.value)" not in vm:
    problems.append("أسماء الكتابة تُسند إلى الحالة كل دورة ⇒ إعادة تركيب بلا سبب")
if vm.count("!_newMessagesBelow.value") < 2:
    problems.append("شارة «رسائل بالأسفل» تُسند بلا فحص ⇒ إعادة تركيب بلا سبب")

# ٤) لا N+1 في المبدعين
if "created_by=eq.${profile.id}" in creators:
    problems.append("ما زال هناك طلب محتوى لكل مبدع على حدة (N+1)")
if "created_by,content_type,downloads_count" not in creators:
    problems.append("إحصاءات المبدعين لا تُجلب في طلب واحد")
if "TtlCache<List<CreatorCard>>" not in creators:
    problems.append("قائمة المبدعين بلا ذاكرة مؤقّتة")

# ٥) ذاكرة TTL مشتركة + بذر الشاشات
if "class TtlCache<T>" not in ttl:
    problems.append("TtlCache غير معرّفة")
if "suspend fun <T> through(" not in ttl:
    problems.append("دالة through المشتركة غير معرّفة")
for fn in ("cachedFeatured", "cachedLatest", "cachedRecommended"):
    if fn not in content:
        problems.append("ContentRepository بلا %s()" % fn)
for seed in ("cachedFeatured()", "cachedLatest()", "cachedRecommended()", "cachedCreators()"):
    if seed not in home:
        problems.append("الشاشة الرئيسية لا تُبذر من الذاكرة (%s)" % seed)
if not re.search(r"loading\s*&&\s*\w+\.isEmpty\(\)", home):
    problems.append("الشاشة الرئيسية تُظهر مؤقّت تحميل فوق بيانات موجودة")

print("الفحص                                   | النتيجة")
print("-" * 60)
rows = [
    ("قائمة الرسائل بمفتاح (key)", "key = { it.message.id }" in chat),
    ("المفتاح يصل إلى items()", "key = { index -> key(items[index]) }" in helper_text),
    ("نوع محتوى لإعادة الاستعمال", "contentType" in helper_text),
    ("الاستطلاع يتوقّف في الخلفية", "if (!screenActive) continue" in vm),
    ("الاستطلاع يخفّ مع Realtime", "realtimeHealthy" in vm),
    ("الشاشة تُعلِم النموذج", "setScreenActive(true)" in chat and "setScreenActive(false)" in chat),
    ("لا إسناد حالة بلا تغيير", "if (names != _typing.value)" in vm),
    ("لا N+1 في المبدعين", "created_by=eq.${profile.id}" not in creators),
    ("المبدعون بطلب واحد", "created_by,content_type,downloads_count" in creators),
    ("ذاكرة TTL مشتركة", "class TtlCache<T>" in ttl and "suspend fun <T> through(" in ttl),
    ("الرئيسية تُبذر من الذاكرة", all(x in home for x in ("cachedFeatured()", "cachedCreators()"))),
    ("لا مؤقّت فوق بيانات موجودة", "loading && featured.isEmpty()" in home),
]
for name, ok in rows:
    print(f"{name:<40} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 الأداء: قائمة الرسائل بمفاتيح · استطلاع متكيّف يتوقّف في الخلفية · "
      "بلا N+1 · وبذر الشاشات من ذاكرة TTL.")
