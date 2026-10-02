#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""فحص النقر المزدوج: كل زر إجراء ينفّذ طلباً للشبكة يجب أن يُعطَّل أثناء التنفيذ.

يرصد الحالة الشائعة: علم انشغال (busy/sending/...) موجود، لكن لم يُربط بزر الإجراء،
فتُرسل نقرتان طلبين (تسجيل دخول، إنشاء حساب، بريد استعادة، طلب صداقة، رسالة مكرّرة…).

قاعدة الفحص: داخل الدالة التي تحوي الزر، إن كان there متغيّر انشغال يُكتب داخل
onClick أو داخل coroutine، فيجب أن يظهر في GradientButton عبر `enabled = !<اسم>`.
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "android", "app", "src", "main", "java", "com", "addonstation", "mobile", "ui")
BUSY = re.compile(r"\bvar\s+(\w+)\s+by\s+remember\s*\{\s*mutableStateOf\(\s*(?:false|0|\"\")")
# مفاتيح واجهة (فتح/إغلاق لوحات) ليست أعلام انشغال
UI_TOGGLE = r"(?:show|is|has|open|can|should|hide|with)[A-Z]?\w*"
ACTIONS = re.compile(r"stringResource\(R\.string\.(\w*(?:sign_in|create_account|send_recovery|accept|send|buy|download|save|redeem|confirm|approve|hide|order|submit)\w*)\)")


def calls(text, start):
    """نصّ كل GradientButton داخل دالة، مع رقم السطر."""
    out = []
    for m in re.finditer(r"GradientButton\(", text):
        i, depth = m.end(), 1
        while depth and i < len(text):
            if text[i] == "(":
                depth += 1
            elif text[i] == ")":
                depth -= 1
            i += 1
        body = text[m.start():i]
        line = text[:m.start()].count("\n") + 1 + start
        label = ACTIONS.search(body)
        out.append((line, body, label.group(1) if label else None))
    return out


def scan(path):
    s = io.open(path, encoding="utf-8").read()
    lines = s.split("\n")
    start_line = 0
    # حدود كل @Composable fun
    funs = [(m.start(), m.group(1), s[:m.start()].count("\n") + 1)
            for m in re.finditer(r"@Composable\s*\nfun\s+(\w+)", s)]
    problems = []
    for idx, (pos, name, ln) in enumerate(funs):
        end = funs[idx + 1][0] if idx + 1 < len(funs) else len(s)
        chunk = s[pos:end]
        base = ln - 1
        flags = {b: True for b in BUSY.findall(chunk)}
        for b in BUSY.findall(s):
            flags.setdefault(b, True)
        for line, body, label in calls(chunk, base):
            if "enabled" in body:
                continue
            # العلم يُرفع داخل الزر نفسه ⇒ نقرتان تنفّذان الطلب مرتين
            touched = [b for b in flags
                       if re.search(r"\b%s\s*=\s*true\b" % b, body)
                       and not re.search(r"^%s$" % UI_TOGGLE, b)]
            if not touched:
                continue
            if any(re.search(r"if\s*\(\s*%s\b" % b, body) for b in touched):
                continue      # محمي بحارس داخل onClick
            problems.append((os.path.relpath(path, SRC), line, name, label, touched))
    return problems


def main():
    bad = []
    for root, _, files in os.walk(SRC):
        for f in files:
            if f.endswith(".kt"):
                bad += scan(os.path.join(root, f))
    print("الفحص | النتيجة")
    print("-" * 52)
    print(f"{'أزرار الإجراء بلا تعطيل أثناء التنفيذ':<44} | {'✅' if not bad else '❌'}")
    if bad:
        print()
        for path, line, fn, label, flags in bad:
            print(f"  ✗ {path}:{line}  {fn}()  زر «{label}»  أعلام: {', '.join(flags)}")
        print(f"\n✗ عدد المشاكل: {len(bad)}")
        sys.exit(1)
    print("🟢 كل أزرار الطلبات تُعطَّل أثناء تنفيذها — لا تنفيذ مزدوج بنقرة مزدوجة.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
