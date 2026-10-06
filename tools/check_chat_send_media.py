#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فحص «إرسال الشات والصور والفيديو» — يثبت أن إصلاحات هذه الجولة موجودة في
الكود ويمنع رجوعها:

  1) رقم الرسالة المُرسَلة يُقرأ من الحقل الصحيح (`message_id`) — سبب «لازم
     أخرج وأرجع للغرفة عشان أتأكد إن رسالتي انبعتت».
  2) الرسالة الفاشلة **تبقى** ظاهرة موسومة + «إعادة المحاولة» + إسقاط،
     والاستطلاع يؤكّد الرسالة المؤقتة بالمطابقة (لا تكرار، ولا انتظار).
  3) شارة «رسائل بالأسفل» لرسائل الآخرين فقط، لا لرسائلي أنا.
  4) الصورة: مقاس ثابت (لا ينهار عرضها إلى صفر) + معاينة محلية + إعادة توقيع.
  5) الفيديو: بطاقة فيها إطار من الفيديو + ▶ + المدّة، وتُشغَّل بضغطة،
     وصورته المصغّرة تُرفع معه ليراها الجميع.
  6) معاينة المرفق داخل مربع الإرسال (كتليجرام) قبل الإرسال.
  7) نصوص الحالة الجديدة موجودة في اللغات الثماني.

الاستخدام: python3 tools/check_chat_send_media.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "android", "app", "src", "main")
JAVA = os.path.join(BASE, "java", "com", "addonstation", "mobile")
RES = os.path.join(BASE, "res")

SCREEN = os.path.join(JAVA, "ui", "chat", "ChatScreen.kt")
VM = os.path.join(JAVA, "ui", "chat", "ChatViewModel.kt")
PLAYER = os.path.join(JAVA, "ui", "chat", "VideoPlayer.kt")
REPO = os.path.join(JAVA, "data", "repo", "ChatRepository.kt")
VIDEO_UTIL = os.path.join(JAVA, "util", "VideoMeta.kt")


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


screen, vm, player, repo, video_util = (
    read(SCREEN), read(VM), read(PLAYER), read(REPO), read(VIDEO_UTIL),
)

rows = []


def has(name, ok):
    rows.append((name, bool(ok)))


# ── 1) رقم الرسالة من الخادم ────────────────────────────────────────────
has("رقم الرسالة يُقرأ من message_id", 'outcome.data["message_id"]' in repo)
has("الاسم القديم يبقى احتياطاً", 'outcome.data["id"]' in repo)

# ── 2) الفشل ظاهر + إعادة المحاولة + مطابقة الاستطلاع ───────────────────
send_fn = re.search(r"fun send\((?:.|\n)*?\n    \}", vm)
send_fn = send_fn.group(0) if send_fn else ""
has("الرسالة الفاشلة تبقى موسومة (لا تُمحى)",
    "it.copy(pending = false, failed = true)" in send_fn)
has("زر إعادة المحاولة في النموذج", "fun retry(" in vm and "it.copy(pending = true, failed = false)" in vm)
has("إسقاط الرسالة الفاشلة", "fun discard(" in vm)
has("المطابقة تؤكّد الرسالة المؤقتة", "sameDraft(" in vm and "refreshTails" in vm)
has("المطابقة تستبدلها في مكانها", "it.copy(message = message, pending = false, failed = false)" in vm)

# ── 3) الشارة للآخرين فقط ───────────────────────────────────────────────
has("شارة «رسائل بالأسفل» لا تُرفع برسائلي",
    "mapped.any { it.message.userId != myId }" in vm)

# ── 4) الصورة تظهر ──────────────────────────────────────────────────────
has("مقاس ثابت للصورة (لا عرض صفر)", "private fun ChatImageTile(" in screen
    and "screenWidthFraction(" in screen)
has("لم يبقَ القياس القديم المنهار", "widthIn(max = 240.dp)" not in screen)
has("معاينة محلية قبل توقيع الرابط",
    "model = localPreview?.let { java.io.File(it) }" in screen)
has("إعادة توقيع رابط الصورة عند الحاجة",
    "imageAttempt" in screen and "reloadImageUrl" in screen)
has("نسخة محلية تُحفظ عند اختيار الصورة", "cachePreviewFile(" in screen)

# ── 5) الفيديو ──────────────────────────────────────────────────────────
has("استخراج إطار الفيديو والمدّة", "fun extractVideoMeta(" in video_util
    and "MediaMetadataRetriever" in video_util)
has("بطاقة الفيديو داخل الرسالة", '"video" -> {' in screen and "VideoTile(" in screen)
has("زر تشغيل + المدّة على البطاقة", "PlayArrow" in player and "shortDurationLabel(" in player)
has("الصورة المصغّرة تُرفع مع الفيديو",
    "fun uploadVideoThumb(" in repo and "uploadVideoThumb(" in screen)
has("مسار الصورة المصغّرة مشتق من مسار الفيديو",
    "fun videoThumbPath(" in repo and "thumb.jpg" in repo)
has("تشغيل الفيديو بملء الشاشة",
    "fun FullScreenVideoPlayer(" in player and "FullScreenVideoPlayer(url = url)" in screen)
has("رابط الفيديو يُوقَّع عند الضغط", "container.chat.mediaUrl(path)" in screen)

# ── 6) معاينة المرفق في مربع الإرسال ────────────────────────────────────
has("معاينة المرفق داخل مربع الإرسال", "private fun PendingAttachmentPreview(" in screen)
has("المرفق لا يُرسل قبل اكتمال رفعه",
    "fun sendNow()" in screen and "if (pendingUpload != null)" in screen)
has("إعادة رفع المرفق الفاشل", "fun retryUpload(" in screen)
has("لم يبقَ مسار إرسال قديم للوسائط", "mediaToSend" not in screen)

# ── 7) النصوص في اللغات الثماني ─────────────────────────────────────────
NEW_KEYS = [
    "chat_uploading", "chat_upload_failed", "chat_send_failed",
    "chat_tap_to_retry", "chat_play_video", "chat_attachment_ready",
]
LANGS = {
    "values": "en", "values-ar": "ar", "values-es": "es", "values-fr": "fr",
    "values-pt": "pt", "values-ru": "ru", "values-tr": "tr", "values-zh": "zh",
}
missing = []
for folder, code in LANGS.items():
    body = read(os.path.join(RES, folder, "strings.xml"))
    for key in NEW_KEYS:
        if f'name="{key}"' not in body:
            missing.append(f"{code}:{key}")
has("نصوص الحالة الجديدة في 8 لغات", not missing)

# ── النتيجة ─────────────────────────────────────────────────────────────
print(f"{'الفحص':<46} | النتيجة")
print("-" * 62)
for name, ok in rows:
    print(f"{name:<46} | {'✅' if ok else '❌'}")

bad = [n for n, ok in rows if not ok]
print()
if bad:
    for n in bad:
        print("✗ الفشل:", n)
    if missing:
        print("✗ نصوص ناقصة:", " · ".join(missing))
    print(f"\n✗ عدد المشاكل: {len(bad)}")
    sys.exit(1)
print("🟢 الإرسال فوري ومؤكَّد · الصورة تظهر بمعاينتها · الفيديو بطاقة"
      " تُشغَّل · والمرفق يُعاين في مربع الإرسال كتليجرام.")
