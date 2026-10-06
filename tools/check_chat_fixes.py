#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص إصلاحات الشات الخمسة — يقرأ الكود نفسه ويثبت أن كل إصلاح موجود، ويمنع رجوعه.

طلبات المالك:
  1) الرسالة تظهر فوراً بعد الضغط على الإرسال (لا يحتاج يطلع واد للغرفة).
  2) الضغط المطوّل على رسالة ⇒ قائمة فيها إبلاغ + رد + حذف (الحذف لرسالته فقط).
  3) ضغطة بسيطة على أي رسالة ⇒ رد عليها تلقائياً.
  4) ضغطة سريعة على ملصقه ⇒ يُشال ويعود إلى لوحة الملصقات.
  5) النص + الملصق/الصورة/الوسائط ⇒ رسالة واحدة لا رسالتين.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
SCREEN = os.path.join(BASE, "ui", "chat", "ChatScreen.kt")
SHEETS = os.path.join(BASE, "ui", "chat", "ChatSheets.kt")
VM = os.path.join(BASE, "ui", "chat", "ChatViewModel.kt")
MODELS = os.path.join(BASE, "data", "model", "Models.kt")


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


screen, sheets, vm, models = read(SCREEN), read(SHEETS), read(VM), read(MODELS)
problems = []
for name, body in (("ChatScreen.kt", screen), ("ChatSheets.kt", sheets),
                   ("ChatViewModel.kt", vm), ("Models.kt", models)):
    if not body:
        problems.append(f"{name} مفقود")

# ── 1) الرسالة تظهر فوراً (إدراج متفائل) ────────────────────────────────
send_fn = re.search(r"fun send\((?:.|\n)*?\n    \}", vm)
send_fn = send_fn.group(0) if send_fn else ""
rows = []
if send_fn:
    ins_at = send_fn.find("_messages.value = _messages.value + ChatMessageUi(")
    call_at = send_fn.find("container.chat.send(")
    rows.append(("الإدراج الفوري قبل نداء الخادم", ins_at != -1 and call_at != -1 and ins_at < call_at))
    rows.append(("الرسالة المؤقتة برقم سالب", "var tempId = -System.currentTimeMillis()" in send_fn))
    rows.append(("استبدالها بالرسالة الحقيقية", "it.copy(message = sent, pending = false)" in send_fn))
    # الرسالة الفاشلة **تبقى** ظاهرة (موسومة) ولا تُمحى — والـ filterNot بقي
    # لنسخة واحدة فقط: حين يسبق الاستقبال اللحظي ردّ الإرسال (لا تكرار).
    rows.append(("لا تكرار عند سباق الاستقبال", "filterNot { it.message.id == tempId }" in send_fn
                 and "it.message.id == sent.id" in send_fn))
    rows.append(("علامة «قيد الإرسال» في النموذج", "val pending: Boolean = false" in models))
    rows.append(("الرسالة الباهتة تُعرض أخفّ", ".alpha(if (ui.pending)" in screen))
else:
    rows.append(("دالة الإرسال في ChatViewModel", False))

# الاستقبال لا يعتمد على المستخدم: آخر رسالة حقيقية فقط + استطلاع لا يُسقط نفسه
rows.append(("تحديث الذيل يتجاهل الرسالة المؤقتة", "it.message.id > 0L" in vm))
rows.append(("حلقة التحديث لا تموت عند فشل واحد", "runCatching { refreshTails() }" in vm))
#Message must also be *seen*: the list follows my own send even when the
# "new messages below" flag is on (this was the "I have to leave the room" bug).
rows.append(("عدّاد تمرير لكل رسالة أرسلها", "_scrollTick.value += 1" in vm and "val scrollTick" in vm))
rows.append(("الشاشة تمرّر إلى رسالتي دائماً", "LaunchedEffect(scrollTick)" in screen
             and "if (scrollTick > 0 && messages.isNotEmpty())" in screen))
rows.append(("شارة «رسائل بالأسفل» تُصفَّر عند الإرسال",
             "_newMessagesBelow.value = false\n        _scrollTick.value += 1" in vm))
rows.append(("التمرير يتبع ما دمت في أسفل القائمة", "(atBottom || !newBelow)" in screen))
rows.append(("بلوغ آخر رسالة يعيد تفعيل التمرير التلقائي",
             "snapshotFlow {" in screen and "vm.markReadUpToBottom()" in screen
             and "atBottom = bottom" in screen))

# ── 2) الضغط المطوّل ⇒ إبلاغ + رد + حذف ───────────────────────────────
actions = re.search(r"fun MessageActionsSheet\((?:.|\n)*?\n\}", sheets)
actions = actions.group(0) if actions else ""
rows.append(("خيار «رد» في قائمة الضغط المطوّل", "Icons.Filled.Reply" in actions))
rows.append(("خيار «إبلاغ» في القائمة", "Icons.Filled.Flag" in actions))
rows.append(("خيار «حذف رسالتي» في القائمة", "Icons.Filled.Delete" in actions))
rows.append(("الحذف شرطه أنه رسالتي", "ui.message.userId == myId" in actions))
rows.append(("الضغط المطوّل ما زال يفتح القائمة", "onLongPress = { actionsFor = ui }" in screen))

# ── 3) ضغطة بسيطة ⇒ رد ────────────────────────────────────────────────
rows.append(("الضغطة البسيطة تردّ لا تفتح القائمة",
             ".combinedClickable(onClick = onReply, onLongClick = onLongPress)" in screen))
rows.append(("الرد يفعّل شريط الرد", "onReply = { replyTo = ui.message }" in screen))

# ── 4) ضغطة سريعة على ملصقي ⇒ يعود إلى لوحة الملصقات ─────────────────
rows.append(("نقر الملصق يمرّر إلى onStickerTap", "if (takeBack != null) takeBack()" in screen))
rows.append(("النقر يشيل الرسالة من الشات", "vm.deleteMine(ui.message.id)" in screen))
rows.append(("ثم تُفتح لوحة الملصقات", "resentSticker = sticker" in screen and "showEmoji = true" in screen))
rows.append(("اللوحة تُحدّد الملصق المُعاد", "focusSticker" in sheets and "AppColors.Green else Color.Transparent" in sheets))
rows.append(("التبويب يفتح على الملصقات", 'mutableStateOf(if (focusSticker != null) 1 else 0)' in sheets))

# ── 5) النص + الملصق/الوسائط = رسالة واحدة ────────────────────────────
# (جديد بطلب المالك: اختيار الملصق لا يُرسله فوراً — بل يضعه في مربع الإرسال
#  بمعاينة وزر ✕ أحمر، فيُكتب النص تحته إن أراد ثم يُضغط إرسال، فتخرج
#  رسالة واحدة: النص فوقه والملصق تحته مباشرة.)
sticker_pick = re.search(r"onSticker = \{ stickerUrl ->(?:.|\n)*?\n            \},", screen)
sticker_pick = sticker_pick.group(0) if sticker_pick else ""
rows.append(("اختيار الملصق يضعه في مربع الإرسال", "pendingSticker = stickerUrl" in sticker_pick))
rows.append(("لوحة الملصقات تُغلق بعد الاختيار", "showEmoji = false" in sticker_pick))
rows.append(("حالة الملصق المنتظر في الشاشة",
             "var pendingSticker by remember { mutableStateOf<String?>(null) }" in screen))
rows.append(("معاينة الملصق داخل مربع الإرسال",
             "pendingStickerUrl = pendingSticker?.let { container.siteAssetUrl(it) }" in screen))
rows.append(("زر ✕ أحمر لإلغاء الملصق",
             "PendingStickerPreview(" in screen and "AppColors.Red" in screen
             and "R.string.chat_remove_sticker" in screen))
rows.append(("الإرسال يعمل بالملصق وحده",
             "canSend = text.isNotBlank() || attachment != null || pendingSticker != null" in screen))
# مسار إرسال **واحد** لكل الأنواع (نص · صورة · فيديو · صوت · ملصق): sendNow()
send_now = re.search(r"fun sendNow\(\)(?:.|\n)*?\n    \}", screen)
send_now = send_now.group(0) if send_now else ""
rows.append(("مسار إرسال واحد لكل الأنواع", send_now != ""))
rows.append(("الملصق يُرسل مع النص المكتوب", "content = body.ifEmpty { null }" in send_now
             and "stickerUrl = sticker" in send_now))
rows.append(("نوع الرسالة sticker مع النص", 'sticker != null -> "sticker"' in send_now))
rows.append(("الصورة/الفيديو يُرسلان مع النص المكتوب",
             "imagePath = if (attach?.kind == \"image\") attach.remotePath else null" in send_now
             and "mediaPath = if (attach != null && attach.kind != \"image\") attach.remotePath else null" in send_now))
# الرسالة نفسها: النص فوقه والملصق تحته مباشرة
sticker_block = re.search(r'"sticker" -> \{(?:.|\n)*?\n            \}', screen)
sticker_block = sticker_block.group(0) if sticker_block else ""
rows.append(("عرض النص فوق الملصق في نفس الرسالة",
             "message.content.orEmpty()" in sticker_block
             and "CoverImage(url = url" in sticker_block
             and sticker_block.find("message.content.orEmpty()") < sticker_block.find("CoverImage(url = url")))
rows.append(("النص يُمسح فوراً لا بعد النجاح", "text = \"\"            // مسح فوري" in screen
             or "text = \"\"                // مسح فوري" in screen))
rows.append(("ما كتبه يعود لو فشل الإرسال", "val replySnapshot = replyTo" in send_now
             and "if (text.isEmpty()) text = body" in send_now
             and "if (attachment == null) attachment = attach" in send_now))
rows.append(("الملصق يعود لو فشل الإرسال",
             "if (pendingSticker == null) pendingSticker = sticker" in screen))
rows.append(("الوسائط تحمل النص المكتوب",
             re.search(r"content = body\.ifEmpty \{ null \},[\s\S]*?mediaMime = attach\?\.mime,", send_now)
             is not None))
rows.append(("الرسالة المحذوفة تختفي لكل الأنواع", "return@messageBody" in screen))

# ── طباعة الجدول ───────────────────────────────────────────────────────
print(f"{'الاختبار':<44} | النتيجة")
print("-" * 60)
for name, ok in rows:
    print(f"{name:<44} | {'✅' if ok else '❌'}")
bad = [n for n, ok in rows if not ok]
print()
if problems or bad:
    for p in problems:
        print("✗", p)
    for n in bad:
        print("✗ الفشل:", n)
    print(f"\n✗ عدد المشاكل: {len(problems) + len(bad)}")
    sys.exit(1)
print(f"🟢 إصلاحات الشات الخمسة كلها موجودة في الكود ({len(rows)}/{len(rows)}): "
      "ظهور فوري · قائمة إبلاغ/رد/حذف · رد بضغطة بسيطة · إرجاع الملصق · رسالة واحدة للنص مع الملصق/الوسائط.")
