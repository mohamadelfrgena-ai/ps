#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""حارس شريط الحالة: يضمن أن التطبيق يحجز منطقة شريط النظام (الساعة/البطارية/الواي فاي)
مرة واحدة عند الجذر، وألّا تحجزها شاشات بصمت فتصبح مسافة مزدوجة.

الاستخدام: python3 tools/check_status_bar.py
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
UI = os.path.join(ROOT, "android/app/src/main/java/com/addonstation/mobile/ui")
NAV = os.path.join(UI, "nav/AppNav.kt")
EXPECTED_STAMP = "windowInsetsTopHeight(WindowInsets.statusBars)"

failures = []

# 1) الحجز المركزي موجود في AppRoot
nav = io.open(NAV, encoding="utf-8").read()
if EXPECTED_STAMP not in nav:
    failures.append("AppRoot لا يحجز منطقة شريط الحالة (windowInsetsTopHeight)")

# 2) لا شاشة تحجز شريط الحالة بنفسها (كانت سببه مسافة مزدوجة بعد الإصلاح المركزي)
allowed = {"nav/AppNav.kt", "chat/ChatSheets.kt"}  # اللوح نافذة مستقلة
for base, _dirs, files in os.walk(UI):
    for name in files:
        if not name.endswith(".kt"):
            continue
        path = os.path.join(base, name)
        rel = os.path.relpath(path, UI)
        if rel in allowed:
            continue
        body = io.open(path, encoding="utf-8").read()
        if "WindowInsets.statusBars" in body and EXPECTED_STAMP not in body:
            failures.append(f"{rel}: يحجز شريط الحالة بنفسه ⇒ مسافة مزدوجة")

# 3) الخطوط الثلاثة الأساسية في التخطيط موجودة (لا استبدال صامت)
for token in ("Spacer(", ".fillMaxWidth()", "Box(Modifier.fillMaxSize())"):
    if token not in nav:
        failures.append(f"AppRoot: العنصر المطلوب {token} غير موجود")

if failures:
    print("✗ حارس شريط الحالة:")
    for f in failures:
        print("   -", f)
    sys.exit(1)

print("🟢 شريط الحالة: الحجز المركزي موجود، ولا حجز مكرر في الشاشات.")
