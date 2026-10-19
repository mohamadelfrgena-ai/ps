#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص ترويسة الرسائل (المجموعة الواردة فقط) ومحاذاة الفقاعة وأداة الشات.

الترتيب المطلوب (اختاره المالك):
    • الصورة (40dp) جنبها الاسم كما هو.
    • **اللقب** (TheKing · Epic · Legendary …) ينزل **سطراً تحت الاسم**.
    • **الأداة** المجهّزة **بجانبهما** — بحجمٍ أكبر (32dp): أكبر من 16dp القديم
      وأقلّ قليلاً من الصورة (40dp) طلباً للشكل الجمالي.

يفشل إذا:
  • عاد الاسم واللقب إلى سطر واحد بجانب الصورة.
  • صغرت الأداة عن 32dp أو كبرت حتى صارت بحجم الصورة أو أكبر.
  • تغيّر مقاس الصورة عن 40dp.
  • ظهر لقب «Free 🍃» تحت اسم صاحب الخطة المجانية (طلب المالك: لا شيء له).
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
CHAT = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                    "addonstation", "mobile", "ui", "chat", "ChatScreen.kt")

problems = []
chat = io.open(CHAT, encoding="utf-8").read() if os.path.exists(CHAT) else ""
if not chat:
    problems.append("ChatScreen.kt مفقود")


def branch(text: str, marker: str) -> str:
    """الكتلة التي تبدأ بـ marker حتى قوسها المغلق المقابل."""
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


header = branch(chat, "if (showAuthorHeader) {")
name_col = branch(header, "Column(modifier = Modifier.weight(1f, fill = false))")

AVATAR_DP, ITEM_DP, OLD_ITEM_DP = 40, 32, 16

if not header:
    problems.append("لا كتلة ترويسة الرسائل (if showAuthorHeader) في ChatScreen.kt")

# لا نكرر اسم المالك فوق رسائله؛ رؤوس الأعضاء تظهر مرة واحدة عند بداية المجموعة الواردة.
if "val showAuthorHeader = !isMine && !grouped" not in chat:
    problems.append("ترويسة المؤلف لا تقتصر على بداية مجموعة الرسائل الواردة")
if "currentProfile = profile" not in chat or \
        "currentProfile?.takeIf { isMine && it.id == myId }" not in chat:
    problems.append("رسالة المالك لا تستخدم ملف الجلسة كبديل إذا غاب المؤلف من الخريطة")

# 1) الاسم واللقب في عمود واحد: الاسم فوق، واللقب تحته
if not name_col:
    problems.append("الاسم واللقب ليسا في عمود واحد (اللقب يجب أن يكون تحت الاسم)")
else:
    # الخط: وثيقة التصميم تعتمد Spec.msgAuthor (14sp/800) — والقديم AppText.chatName
    if "AppText.chatName" not in name_col and "Spec.msgAuthor" not in name_col:
        problems.append("اسم صاحب الرسالة ليس داخل عمود الترويسة")
    if "RankBadge(" not in name_col:
        problems.append("اللقب (RankBadge) ليس تحت الاسم داخل العمود نفسه")

# 2) المجّاني بلا لقب: «Free 🍃» لا تظهر تحت الاسم أبداً
if header:
    if "hasRankTitle(" not in header:
        problems.append("لقب الرتبة غير محكوم ببوابة hasRankTitle (المجّاني سيظهر له لقب)")
    gate = re.search(r"if \([^)]*(showRankTitle|hasRankTitle)[^)]*\)", header)
    if not gate:
        problems.append("نداء RankBadge ليس داخل شرط (يجب ألّا يظهر للمجّاني)")
# تعريف البوابة نفسها: epic و legendary فقط (لا free) + الرتب الخاصة
sheets_path = os.path.join(ROOT, "android", "app", "src", "main", "java", "com",
                           "addonstation", "mobile", "ui", "chat", "ChatSheets.kt")
sheets = io.open(sheets_path, encoding="utf-8").read() if os.path.exists(sheets_path) else ""
# جسم الدالة: من تعريفها حتى أول سطر فارغ (التعبير قد يمتدّ أسطراً)
_i = sheets.find("fun hasRankTitle(")
_j = sheets.find("\n\n", _i)
gate_text = sheets[_i:_j] if _i >= 0 and _j > _i else ""
if not gate_text:
    problems.append("دالة hasRankTitle غير معرّفة في ChatSheets.kt")
else:
    for plan in ("epic", "legendary"):
        if plan not in gate_text:
            problems.append("بوابة اللقب لا تُظهر خطة %s" % plan)
    if '"free"' in gate_text:
        problems.append("بوابة اللقب تُظهر الخطة المجانية (يجب ألّا تظهر)")
    for role in ("admin", "moderator", "creator"):
        if role not in gate_text:
            problems.append("بوابة اللقب تُخفي رتبة %s" % role)

# 3) الأداة بجانبهما (خارج العمود) وبمقاسها الجديد
if header:
    if "equippedItemUrl" not in header:
        problems.append("الأداة المجهّزة غير معروضة في الترويسة")
    # الأداة تُقاس بـ Modifier.size(32.dp) والصورة بمعامل size = 40.dp
    if not re.search(r"size\(\s*%d\.dp\s*\)" % ITEM_DP, header):
        problems.append("حجم الأداة ليس %ddp" % ITEM_DP)
    if re.search(r"size\(\s*%d\.dp\s*\)" % OLD_ITEM_DP, header) or \
            "size = %d.dp" % OLD_ITEM_DP in header:
        problems.append("ما زال حجم الأداة القديم %ddp" % OLD_ITEM_DP)
    if "size = %d.dp" % AVATAR_DP not in header:
        problems.append("مقاس صورة البروفايل تغيّر عن %ddp" % AVATAR_DP)
    if name_col and "equippedItemUrl" in name_col:
        problems.append("الأداة دخلت عمود الاسم — يجب أن تكون بجانبهما لا داخله")

# 3) أقلّ شوي من الصورة (شرط الجمال الذي طلبه المالك)
too_big = [
    int(m.group(1))
    for m in re.finditer(r"size = (\d+)\.dp", header or "")
    if int(m.group(1)) > AVATAR_DP
]

print("الفحص                                   | النتيجة")
print("-" * 60)
rows = [
    ("الصورة بمقاسها 40dp", "size = 40.dp" in (header or "")),
    ("الاسم فوق · اللقب تحته (عمود واحد)",
     bool(name_col) and "Spec.msgAuthor" in name_col and "RankBadge(" in name_col),
    ("الأداة بجانبهما لا داخلهما",
     bool(name_col) and "equippedItemUrl" not in name_col and "equippedItemUrl" in (header or "")),
    ("حجم الأداة 32dp (كان 16dp)", bool(re.search(r"size\(\s*32\.dp\s*\)", header or ""))),
    ("لا أثر للحجم القديم 16dp", not re.search(r"size\(?\s*=?\s*16\.dp", header or "")),
    ("الأداة أصغر من الصورة (أقلّ شوي)", not too_big),
    ("المجّاني بلا لقب (لا Free 🍃)",
     bool(gate_text) and '"free"' not in gate_text and "epic" in gate_text and "legendary" in gate_text),
    ("اللقب محكوم بالبوابة في الترويسة", "hasRankTitle(" in (header or "")),
    ("ترويسة المؤلف تظهر مرة عند بداية مجموعة واردة فقط", "val showAuthorHeader = !isMine && !grouped" in chat
     and "if (showAuthorHeader)" in chat),
    ("بيانات المرسل تستعين بملفه الحالي عند الحاجة",
     "currentProfile = profile" in chat
     and "currentProfile?.takeIf { isMine && it.id == myId }" in chat),
    ("خلفية الأداة شفافة بلا إطار مضاف", "backgroundColor = Color.Transparent" in (header or "")),
    ("عمود الرسالة يعكس جهة رسائلي", "horizontalAlignment = if (isMine) Alignment.End else Alignment.Start" in chat),
    ("فقاعة النص الصادر عند نهاية السطر", "horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End)" in chat),
    ("كل أنواع الوسائط بمحاذاة جهة المرسل", chat.count("horizontalAlignment = if (isMine) Alignment.End else Alignment.Start") >= 5),
]
for name, ok in rows:
    print(f"{name:<40} | {'✅' if ok else '❌'}")

print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 ترويسة الشات: صورة 40 · اسم · لقبه تحته (إن كان مشتركاً أو صاحب رتبة) · وأداة 32 بجانبهما — والمجّاني بلا سطر تحت اسمه.")
