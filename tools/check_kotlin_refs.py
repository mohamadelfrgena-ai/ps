#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فحص مراجع Kotlin داخل تطبيق أندرويد بدون مترجم (Hekto-checker):
    1) كل R.string/R.plurals المستخدمة موجودة في ملفات res/values/strings*.xml
  2) عدد وسائط stringResource يطابق عدد %n$s في النص
  3) أيقونات Material المستخدمة لها import صحيح
  4) كل container.<repo>.<member> المستخدمة في الشاشات معرّفة في المستودعات
  5) توازن الأقواس في كل ملف
  6) تحذير: stringResource داخل لامبدا غير @Composable (onClick/launch...)

الاستخدام:  python3 tools/check_kotlin_refs.py [مسار الجذر]
يرجع 0 إذا كل شيء سليم، و1 إذا وُجدت مشاكل.
"""
import os
import re
import sys

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
SRC = os.path.join(ROOT, "android/app/src/main")
KOTLIN = os.path.join(SRC, "java")
VALUES = os.path.join(SRC, "res/values")

CALLBACK = re.compile(r"(on[A-Za-z0-9_]*|launch|invoke|listener|callback)\s*=\s*$")


def mask_strings(src: str) -> str:
    """يستبدل النصوص والتعليقات بمسافات مع الحفاظ على أرقام الأسطر."""
    out = list(src)
    i, n = 0, len(src)
    while i < n:
        c = src[i]
        if c == '"':
            if src.startswith('"""', i):
                j = src.find('"""', i + 3)
                j = n if j < 0 else j + 3
                for k in range(i, j):
                    if out[k] != "\n":
                        out[k] = " "
                i = j
                continue
            i += 1
            while i < n:
                if src[i] == "\\":
                    out[i] = " "
                    if i + 1 < n and out[i + 1] != "\n":
                        out[i + 1] = " "
                    i += 2
                    continue
                if src[i] == '"':
                    i += 1
                    break
                if out[i] != "\n":
                    out[i] = " "
                i += 1
            continue
        if src.startswith("//", i):
            j = src.find("\n", i)
            j = n if j < 0 else j
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        if src.startswith("/*", i):
            j = src.find("*/", i + 2)
            j = n if j < 0 else j + 2
            for k in range(i, j):
                if out[k] != "\n":
                    out[k] = " "
            i = j
            continue
        i += 1
    return "".join(out)


def kotlin_files():
    for root, _, files in os.walk(KOTLIN):
        for f in files:
            if f.endswith(".kt"):
                yield os.path.join(root, f)


def load_keys():
    keys = {}
    plurals = set()
    for filename in os.listdir(VALUES):
        if not (filename.startswith("strings") and filename.endswith(".xml")):
            continue
        xml = open(os.path.join(VALUES, filename), encoding="utf-8").read()
        for m in re.finditer(r'<string name="([^"]+)"[^>]*>(.*?)</string>', xml, re.S):
            keys[m.group(1)] = m.group(2)
        plurals.update(re.findall(r'<plurals name="([^"]+)"', xml))
    return keys, plurals


def arg_count(body: str) -> int:
    depth, commas, last = 0, 0, None
    for ch in body:
        if ch in "([{":
            depth += 1
        elif ch in ")]}":
            depth -= 1
        elif ch == "," and depth == 0:
            commas += 1
        if not ch.isspace():
            last = ch
    if body.strip() == "":
        return 0
    return commas - 1 if last == "," else commas


def main() -> int:
    problems = []
    keys, plural_keys = load_keys()
    sources = {}
    for path in kotlin_files():
        sources[path] = open(path, encoding="utf-8").read()

    for path, raw in sources.items():
        short = path.split("mobile/")[-1]
        src = mask_strings(raw)

        # 1 + 2) R.string
        for m in re.finditer(r"stringResource\(\s*R\.string\.([A-Za-z0-9_]+)", src):
            key = m.group(1)
            if key not in keys:
                problems.append(f"{short}:{raw[:m.start()].count(chr(10)) + 1}  R.string.{key} غير معرّف")
                continue
            depth, i = 1, m.end()
            while i < len(src) and depth > 0:
                if src[i] == "(":
                    depth += 1
                elif src[i] == ")":
                    depth -= 1
                i += 1
            passed = arg_count(src[m.end():i - 1])
            needed = len(set(re.findall(r"%([0-9]+)\$[sd]", keys[key])))
            if passed != needed:
                problems.append(
                    f"{short}:{raw[:m.start()].count(chr(10)) + 1}  {key}: "
                    f"يمرّر {passed} وسيطاً والنص يحتاج {needed}"
                )

        # 2b) موارد الجمع (plurals)
        for m in re.finditer(r"pluralStringResource\(\s*R\.plurals\.([A-Za-z0-9_]+)", src):
            key = m.group(1)
            if key not in plural_keys:
                problems.append(f"{short}:{raw[:m.start()].count(chr(10)) + 1}  R.plurals.{key} غير معرّف")

        # 3) أيقونات Material
        imports = set(re.findall(r"^import\s+([\w.]+)", src, re.M))
        for m in re.finditer(r"\bIcons\.(AutoMirrored\.)?(Filled|Outlined|Rounded)\.([A-Za-z0-9_]+)", src):
            name = m.group(3)
            variant = "automirrored.filled" if m.group(1) else f"{m.group(2).lower()}"
            pkg = f"androidx.compose.material.icons.{variant}.{name}"
            if pkg not in imports:
                problems.append(
                    f"{short}:{raw[:m.start()].count(chr(10)) + 1}  import ناقص: {pkg}"
                )

        # 5) توازن الأقواس
        for open_c, close_c in (("{", "}"), ("(", ")"), ("[", "]")):
            if src.count(open_c) != src.count(close_c):
                problems.append(
                    f"{short}  أقواس غير متوازنة: {open_c}{src.count(open_c)} مقابل "
                    f"{close_c}{src.count(close_c)}"
                )

        # 6) stringResource داخل لامبدا غير Composable
        for m in re.finditer(r"\bstringResource\(", src):
            depth, i, header = 0, m.start() - 1, None
            while i >= 0:
                ch = src[i]
                if ch == "}":
                    depth += 1
                elif ch == "{":
                    if depth == 0:
                        header = src[max(0, i - 120):i]
                        break
                    depth -= 1
                i -= 1
            if header is None:
                continue
            tail = header.rstrip()
            last_line = tail.split("\n")[-1].strip()
            when_branch = re.match(r'^(else|\d+|".*"|[A-Za-z_][\w.]*)\s*->$', last_line) is not None
            if when_branch:
                continue
            if CALLBACK.search(tail):
                problems.append(
                    f"{short}:{raw[:m.start()].count(chr(10)) + 1}  تحذير: "
                    "stringResource داخل لامبدا غير Composable — ارفعه إلى val"
                )

    # 4) container.<repo>.<member>
    repo_files = {
        "auth": "data/repo/AuthRepository.kt",
        "profile": "data/repo/ProfileRepository.kt",
        "content": "data/repo/ContentRepository.kt",
        "plans": "data/repo/PlansRepository.kt",
        "ac": "data/repo/AcRepository.kt",
        "chat": "data/repo/ChatRepository.kt",
        "notifications": "data/repo/NotificationsRepository.kt",
        "creators": "data/repo/CreatorRepository.kt",
        "admin": "data/repo/AdminRepository.kt",
        "assets": "data/repo/AssetsRepository.kt",
        "push": "data/repo/PushRepository.kt",
        "supabase": "data/SupabaseClient.kt",
        "state": "data/AppState.kt",
        "prefs": "data/UserPrefs.kt",
    }
    defs = {}
    for name, rel in repo_files.items():
        path = os.path.join(KOTLIN, "com/addonstation/mobile", rel)
        if not os.path.exists(path):
            continue
        text = open(path, encoding="utf-8").read()
        defs[name] = set(re.findall(r"\b(?:suspend\s+)?fun\s+([a-zA-Z0-9_]+)\s*\(", text))
        defs[name] |= set(re.findall(r"\bval\s+([a-zA-Z0-9_]+)\s*[:=]", text))

    skip = {
        "realtime", "realtimeClient", "siteAssetUrl", "currentConfig",
        "isConfigured", "saveConfig", "appContext",
    }
    for path, raw in sources.items():
        short = path.split("mobile/")[-1]
        for m in re.finditer(r"container\.([a-zA-Z0-9_]+)\.([a-zA-Z0-9_]+)", raw):
            holder, member = m.group(1), m.group(2)
            if holder in skip:
                continue
            if holder not in defs:
                problems.append(f"{short}:{raw[:m.start()].count(chr(10)) + 1}  container.{holder} غير معروف")
            elif member not in defs[holder]:
                problems.append(
                    f"{short}:{raw[:m.start()].count(chr(10)) + 1}  container.{holder}.{member} غير معرّف"
                )

    if problems:
        print(f"✗ عدد المشاكل: {len(problems)}")
        for p in problems:
            print("   ", p)
        return 1
    print("✓ كل المراجع سليمة: R.string · الإضافات · الأيقونات · المستودعات · الأقواس")
    return 0


if __name__ == "__main__":
    sys.exit(main())
