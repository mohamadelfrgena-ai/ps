#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فحص رمزي (بدون مترجم): يكشف المراجع إلى أصناف/دوال لم تعد موجودة في المشروع،
وهذا ما لا يكشفه فحص النصوص. نوعان من المراجع:

  1) مرجع مؤهّل بالكامل:  com.addonstation.mobile.data.RealtimeClient(...)
     → يجب أن يكون آخر جزء رمزاً معرّفاً وأن تكون حزمته موجودة.
  2) مرجع باسم بسيط: RealtimeClient(...) أو isFavorite(...)
     → إن كان الاسم معرّفاً في المشروع، فيجب استيراده أو أن يكون في نفس الحزمة.

الاستخدام: python3 tools/check_symbols.py [مسار الجذر]
"""
import os
import re
import sys
from collections import defaultdict

ROOT = sys.argv[1] if len(sys.argv) > 1 else "."
KOTLIN = os.path.join(ROOT, "android/app/src/main/java")
BASE_PKG = "com.addonstation.mobile"
# أصناف يولّدها المترجم (ليست في الشيفرة المصدرية)
GENERATED = {"R", "BuildConfig"}

DECL_RE = re.compile(
    r"^(?:@\w+(?:\([^)]*\))?\s*)*"
    r"(?:private |internal |public |protected )?"
    r"(?:expect |actual )?(?:suspend )?"
    r"(?:fun|class|object|interface|enum class|data class|sealed class|sealed interface|"
    r"annotation class|abstract class|val|var|const val|typealias)\s+([A-Za-z_]\w*)",
    re.M,
)


def mask(src: str) -> str:
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


def main() -> int:
    files, packages, symbols = {}, set(), defaultdict(set)
    for root, _, fs in os.walk(KOTLIN):
        for f in fs:
            if not f.endswith(".kt"):
                continue
            path = os.path.join(root, f)
            raw = open(path, encoding="utf-8").read()
            src = mask(raw)
            pm = re.search(r"^package\s+([\w.]+)", src, re.M)
            pkg = pm.group(1) if pm else ""
            files[path] = (raw, src, pkg)
            packages.add(pkg)
            for m in DECL_RE.finditer(src):
                symbols[m.group(1)].add(pkg)

    problems = []

    # كل الحزم المتوقعة (بما فيها الحزم الوسيطة مثل ui.chat)
    def package_exists(pkg: str) -> bool:
        if pkg in packages:
            return True
        for p in packages:
            if p.startswith(pkg + "."):
                return True
        return False

    for path, (raw, src, pkg) in files.items():
        short = path.split("mobile/")[-1]
        imports = set(re.findall(r"^import\s+([\w.]+)", src, re.M))
        imported_names = {i.split(".")[-1] for i in imports}
        # مدخلات enum في نفس الملف (تُستدعى مثل المُنشئات)
        if "enum class" in src:
            for m in re.finditer(r"^\s{2,}([A-Z][A-Za-z0-9_]*)\s*[,(]", src, re.M):
                imported_names.add(m.group(1))

        # 1) مراجع مؤهّلة بالكامل
        for m in re.finditer(r"\b" + re.escape(BASE_PKG) + r"((?:\.[A-Za-z_]\w*)+)", src):
            chain = m.group(1).strip(".").split(".")
            pkg_guess = BASE_PKG
            ok = False
            for i, part in enumerate(chain):
                if part[:1].isupper():
                    if part in GENERATED:
                        ok = True
                    elif part in symbols and pkg_guess in symbols[part]:
                        ok = True
                    break
                pkg_guess = f"{pkg_guess}.{part}"
                if package_exists(pkg_guess):
                    ok = True
            if not ok:
                line = raw[:m.start()].count("\n") + 1
                problems.append(f"{short}:{line}  مرجع مؤهّل غير موجود: {BASE_PKG}{m.group(1)}")

        # 2) أسماء بسيطة معرّفة في المشروع لكن غير مستوردة
        for m in re.finditer(r"(?<![\w.])([A-Z][A-Za-z0-9_]*)\s*[({]", src):
            name = m.group(1)
            if name not in symbols or name in imported_names:
                continue
            if pkg in symbols[name]:
                continue
            if any(p in symbols[name] for p in packages if p in imports):
                continue
            line = raw[:m.start()].count("\n") + 1
            problems.append(
                f"{short}:{line}  الرمز {name} معرّف في {sorted(symbols[name])} لكنه غير مستورد هنا"
            )

    if problems:
        print(f"✗ مشاكل المراجع الرمزية: {len(problems)}")
        for p in sorted(set(problems)):
            print("   ", p)
        return 1
    print("✓ كل المراجع الرمزية قابلة للحل (لا مراجع إلى أصناف محذوفة)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
