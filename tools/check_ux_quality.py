#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص «جودة التجربة» — يمنع تكرار defects حقيقية اكتُشفت في جولة مراجعة:

1) نصوص واجهة مكتوبة مباشرة داخل Kotlin (Text("…")) بدل الموارد ⇒ لا تتبع لغة التطبيق.
   المسموح: 工作尺量/رموز/علامات فقط (AC · ∞ · LYD · رموز تعبيرية · بريدCorporation).
2) شاشات فيها حقول نص بلا imePadding ⇒ لوحة المفاتيح تحجب النموذج.
   (الاستثناء: شاشة الشات — هي الوحيد الذي يتعامل مع لوحة المفاتيح بنفسه.)
3) استيراد مكرر في نفس الملف.
4) حقول نص بلا keyboardOptions ⇒ لوحة مفاتيح الحروف فوق حقل أرقام أو بريد.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile")

# نصوص مسموحة (وحدات/رموز/كلمات麻将 لا تُترجم)
ALLOW = {
    "AC", "∞", "LYD", "GB", "MB", "KB", "ID", "AddonStation", "•••", "—", "·",
    "↩", "→", "←", "‹", "›", "×", "✓", "✕", "⚠", "⚠️", "•", "▾", "▸", "◂", "▴", "▾",
    "📥", "🔔", "🌐", "🎨", "🕒", "📎", "🎤", "🎬", "🖼", "🗑", "📋", "🔗", "🔒", "🔑", "⭐",
    "↓", "↑", "→", "💬", "😀", "😊", "❤", "🔥", "💎", "👑", "🏆", "🚀", "🎉", "✅", "❌", "👏", "🙏", "💪",
    "🎮", "🧊", "⚽", "🍃", "🎨", "🧡", "💛", "💜", "🤔", "😎", "🤣", "😅", "🥳", "😭", "🤯",
}
# الملفات المعفاة من الفحص (الشات يتعامل مع لوحة المفاتيح داخله)
IME_EXEMPT = {"ui/chat/ChatScreen.kt", "ui/chat/ChatSheets.kt"}

problems = []
files = []
for base, _dirs, names in os.walk(SRC):
    for n in names:
        if n.endswith(".kt"):
            files.append(os.path.join(base, n))

for path in files:
    rel = os.path.relpath(path, SRC)
    text = io.open(path, encoding="utf-8").read()
    lines = text.split("\n")

    # 1) نصوص ثابتة في الواجهة (باستثناء قالب يحمل قيماً متغيّرة)
    for i, line in enumerate(lines, 1):
        m = re.search(r'Text\(\s*"((?:[^"\\]|\\.)*)"', line)
        if not m:
            continue
        val = m.group(1).strip()
        if not val or val in ALLOW:
            continue
        if "${" in val and val.count("{") > val.count("}"):
            continue                                  # قالب ببديل داخله علامة اقتباس (‎?: "…")
        # احذف الاستبدالات المتغيّرة ${…} و$name ثم احذف الرموز والوحدات المسموحة
        residue = val
        for _ in range(4):
            residue = re.sub(r"\$\{[^{}]*\}", "", residue)
        residue = re.sub(r"\$\w+", "", residue)
        residue = re.sub(r"[\U0001F000-\U0001FAFF☀-➿️•·—–\-_+@#%/\\.,:;()\[\]\d\s]", "", residue)
        for token in ALLOW:
            if re.fullmatch(r"[A-Za-zÀ-ÿ]+", token):
                residue = residue.replace(token, "")
        if not residue:                            # القالب أرقام/رموز فقط ⇒ مقبول
            continue
        problems.append(f"نص ثابت في الواجهة · {rel}:{i} → \"{val}\"")

    # 2) استيراد مكرر
    imports = [l for l in lines if l.startswith("import ")]
    dup = {x for x in imports if imports.count(x) > 1}
    for d in sorted(dup):
        problems.append(f"استيراد مكرر · {rel} → {d}")

    # 4) كل حقل نص يحدّد نوع لوحة المفاتيح (رقم · بريد · كلمة مرور · رابط)
    for i, line in enumerate(lines):
        if "OutlinedTextField(" not in line and not re.search(r"\bTextField\(", line):
            continue
        depth, started, end = 0, False, i
        for k in range(i, min(i + 30, len(lines))):
            depth += lines[k].count("(") - lines[k].count(")")
            if "(" in lines[k]:
                started = True
            if started and depth <= 0:
                end = k
                break
        block = "\n".join(lines[i:end + 1])
        if "keyboardOptions" not in block:
            problems.append(f"حقل نص بلا نوع لوحة مفاتيح · {rel}:{i + 1}")

    # 3) حقول نص بلا imePadding
    if rel in IME_EXEMPT:
        continue
    has_field = ("OutlinedTextField(" in text) or re.search(r"\bTextField\(", text)
    if has_field and "imePadding()" not in text:
        problems.append(f"حقول نص بلا imePadding (لوحة المفاتيح تحجب النموذج) · {rel}")

print("فحص جودة التجربة")
print("-" * 52)
print(f"ملفات Kotlin المفحوصة: {len(files)}")
print(f"النصوص المسموحة (وحدات/رموز): {len(ALLOW)}")
print()
if problems:
    for p in problems:
        print("✗", p)
    print(f"\n✗ عدد المشاكل: {len(problems)}")
    sys.exit(1)
print("🟢 لا نصوص ثابتة في الواجهة · لا استيرادات مكرّرة · كل الحقول لها نوع لوحة مفاتيح · كل الشاشات تعالج لوحة المفاتيح.")
