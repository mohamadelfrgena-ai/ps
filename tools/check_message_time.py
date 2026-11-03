#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص **مكان وقت إرسال الرسالة** — يمنع رجوع الوقت إلى سطر الاسم.

المعتمد (بناءً على صورة المالك — شكل واتساب/مسنجر):
  1) الوقت في **نهاية الرسالة نفسها** (أسفل النص على حافة الفقاعة)، صغير وباهت.
  2) لا وقت بجانب اسم صاحب الرسالة في الأعلى.
  3) الوقت يظهر مع كل أنواع الرسائل: نص · ملصق · صورة · صوت/فيديو.
  4) صور الشات تُحمَّل برابطها الحقيقي (لا مربّع فارغ).
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
SCREEN = os.path.join(BASE, "ui", "chat", "ChatScreen.kt")

screen = io.open(SCREEN, encoding="utf-8").read() if os.path.exists(SCREEN) else ""
rows = []

# 1) بانية الوقت معرَّفة بالنمط الصغير الباهت
time_fn = screen[screen.find("private fun MessageTime("):]
time_fn = time_fn[:time_fn.find("\n}\n")] if "\n}\n" in time_fn else time_fn
rows.append(("بانية وقت الرسالة معرَّفة", "private fun MessageTime(" in screen))
rows.append(("الوقت بخطّ صغير باهت", "Spec.msgTime" in time_fn and "Spec.textMuted" in time_fn))

# 2) الوقت لم يعد في سطر الاسم: الوارد يستخدم MessageTime، والصادر يمرّ
#    عبر MessageStatus (الوقت مع علامة التسليم)؛ كلاهما داخل نهاية الرسالة.
status_fn = screen[screen.find("private fun MessageStatus("):]
status_fn = status_fn[:status_fn.find("\n}\n")] if "\n}\n" in status_fn else status_fn
author_start = screen.find("text = author?.displayName")
author_end = screen.find("if (author?.equippedItemId", author_start)
author_header = screen[author_start:author_end] if author_start >= 0 and author_end >= 0 else ""
rows.append(("لا وقت بجانب اسم صاحب الرسالة",
             screen.count("TimeFormat.timeOnly(") == 2
             and "TimeFormat.timeOnly(createdAt" in time_fn
             and "TimeFormat.timeOnly(createdAt" in status_fn
             and "TimeFormat.timeOnly(" not in author_header))

# 3) الوقت مع كل الأنواع — يُعرض اليوم عبر MessageStatus (الوقت حين تُؤكَّد
#    الرسالة، وحالة الإرسال حين تكون قيده أو فاشلة) في نهاية كل نوع.
def branch_has(tag: str) -> bool:
    block = screen[screen.find(tag):]
    end = block.find("\n            \"")
    block = block[:end] if end != -1 else block
    return "MessageStatus(" in block

rows.append(("وقت مع النص", branch_has("            else -> {")))
rows.append(("وقت مع الملصق", branch_has('            "sticker" -> {')))
rows.append(("وقت مع الصورة", branch_has('            "image" -> {')))
rows.append(("وقت مع الصوت/الفيديو", branch_has('            "video" -> {')
             and branch_has('            "audio" -> {')))
rows.append(("الوقت يظهر عبر بانية الحالة", "MessageStatus(" in screen
             and "else -> MessageTime(createdAt = createdAt" in screen))
rows.append(("الوقت في نهاية السطر (محاذاة سفلية)",
             "verticalAlignment = Alignment.Bottom" in screen))

# 4) صور الشات برابطها الحقيقي
rows.append(("صورة الشات تُحمَل برابطها الحقيقي",
             "var imageUrl by remember(message.id, message.imagePath)" in screen
             and "container.chat.imageUrl(it)" in screen
             and "url = imageUrl," in screen))

print(f"{'الاختبار':<40} | النتيجة")
print("-" * 56)
for name, ok in rows:
    print(f"{name:<40} | {'✅' if ok else '❌'}")

bad = [n for n, ok in rows if not ok]
print()
if bad:
    for n in bad:
        print("✗ الفشل:", n)
    print(f"\n✗ عدد المشاكل: {len(bad)}")
    sys.exit(1)
print(f"🟢 مكان وقت الرسالة مثبّت ({len(rows)}/{len(rows)}): نهاية الرسالة · كل الأنواع · بلا وقت في سطر الاسم.")
