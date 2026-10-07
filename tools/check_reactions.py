#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص شكل **التفاعل على الرسالة** — يمنع رجوع الشكل القديم.

الشكل المعتمد (بأسلوب ديسكورد — بطلب المالك):
  1) بسولات دائرية تحت الرسالة: رمز + عدد، ارتفاع 26dp ونصف قطر 13.
  2) البسولة التي تفاعلتَ بها: خلفية مضيئة (GreenGlow) + إطار أخضر + رقم أخضر.
  3) زر «إضافة تفاعل» （＋ 😊） في آخر صفّ التفاعلات.
  4) لا صفّ ثابت (👍 ❤️ ＋) تحت كل رسالة — التفاعلات تظهر فقط إن وُجدت.
  5) لوحة الضغط المطوّل تُضيء الرموز التي أضافها المستخدم نفسه.
"""
import io
import os
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
BASE = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")
SCREEN = os.path.join(BASE, "ui", "chat", "ChatScreen.kt")
SHEETS = os.path.join(BASE, "ui", "chat", "ChatSheets.kt")


def read(path):
    return io.open(path, encoding="utf-8").read() if os.path.exists(path) else ""


screen, sheets = read(SCREEN), read(SHEETS)
rows = []


def block(src, start, end="\n}\n"):
    i = src.find(start)
    if i < 0:
        return ""
    j = src.find(end, i)
    return src[i:j if j > 0 else len(src)]


pill = block(screen, "private fun ReactionPill(")
add_pill = block(screen, "private fun AddReactionPill(")
reactions_row = block(screen, "if (ui.reactions.isNotEmpty()) {", "\n        }\n    }\n}")

rows.append(("بسولة التفاعل معرَّفة", "private fun ReactionPill(" in screen))
rows.append(("البسولة دائرية (نصف قطر 13)", "RoundedCornerShape(13.dp)" in pill))
rows.append(("ارتفاع البسولة 26dp", ".height(26.dp)" in pill))
rows.append(("تفاعلك بخلفية مضيئة", "AppColors.GreenGlow" in pill))
rows.append(("تفاعلك بإطار أخضر", "AppColors.Green" in pill and "1.5.dp" in pill))
rows.append(("رقم التفاعل أخضر لك", "if (selected) AppColors.Green else AppColors.Muted" in pill))
rows.append(("زر «إضافة تفاعل» معرَّف", "private fun AddReactionPill(" in screen))
rows.append(("وصف الزر من ملف اللغة", "R.string.chat_add_reaction" in add_pill))
rows.append(("الزر يفتح لوحة الرموز (الضغط المطوّل)", "AddReactionPill(onClick = onLongPress)" in screen))
rows.append(("البسولة تبدّل التفاعل بضغطة", "onClick = { onToggleReaction(emoji) }" in reactions_row))
rows.append(("الصفّ يظهر فقط مع تفاعلات موجودة", "if (ui.reactions.isNotEmpty())" in screen))
rows.append(("لا صفّ ثابت تحت كل رسالة", 'listOf("👍", "❤️").forEach' not in screen))
rows.append(("لوحة الضغط المطوّل تُضيء رموزك",
             "ui.myReactions.contains(emoji)" in sheets and "AppColors.GreenGlow" in sheets))

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
print(f"🟢 شكل التفاعل الجديد مثبّت ({len(rows)}/{len(rows)}): بسولات دائرية · إطار أخضر لتفاعلك · زر ＋😊 · بلا صفّ ثابت.")
