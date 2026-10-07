#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص **نطاق Kotlin**: يمسك الأخطاء التي لا يراها أي فاحص نصّي آخر ويرفضها
المُصرِّف (Kotlin) — وأشهرها خطأان كلّفانا دورتَي بناء في CI:

  ١) **استعمال محلّي قبل تعريفه**: في Kotlin لا يمكن استعمال `val`/`var` أو دالة
     محلّية قبل سطر تعريفها داخل الدالة نفسها (بخلاف خصائص الكلاس ودواله).
        LaunchedEffect(x) { openPushTarget(x) }   ← الاستعمال
        fun openPushTarget(raw: String) { … }     ← التعريف (متأخر ⇒ خطأ)

  ٢) **دالة/متغيّر مستعمل وغير معرَّف في الملف ولا مستورد**: مثل نداء
     `ContentGrid(...)` أو `PlanTab(...)` لدوال لم تُكتب أصلاً.

ماذا يفحص بالتفصيل؟
  • داخل كل دالة: يجمع التعريفات المحلّية (val/var/fun/destructuring) بترتيبها،
    ثم يبحث عن استعمال لأيٍّ منها **قبل** موضع تعريفه ⇒ خطأ.
  • يتجاهل: وسائط الدالة، أعضاء الكلاس، متغيّرات `for`/`catch`، أسماء ملفوفة
    بـ«`» (backticks)، وأي اسم معرَّف في الكلاس أو الملف نفسه (لأنه ليس محلّياً).
  • لا يفحص المكتبات الخارجية (استيرادات Android/Compose) — الفاحصات الأخرى
    تغطّي الاستيرادات الناقصة للأيقونات والألوان والنصوص.

الاستخدام:
    python3 tools/check_kotlin_scope.py          # فحص كل ملفات المشروع
    python3 tools/check_kotlin_scope.py FILE.kt  # ملف واحد
"""
import io
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
KOTLIN = os.path.join(ROOT, "android/app/src/main/java")
SKIP_DIRS = {"build"}

DECL = re.compile(r"\b(val|var)\s+([A-Za-z_][A-Za-z0-9_]*)")
DESTRUCT = re.compile(r"\b(val|var)\s*\(([^)]*)\)\s*=")
LOCAL_FUN = re.compile(r"(?m)^\s*(?:private\s+|internal\s+|public\s+|inline\s+|suspend\s+)*fun\s+([A-Za-z_][A-Za-z0-9_]*)"
                       r"(?:\s*<[^>]*>)?\s*\(")
FUN_HEAD = re.compile(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^>]*>)?\s*\(")
CONST = re.compile(r"\bconst\s+val\s+([A-Za-z_][A-Za-z0-9_]*)", re.M)
IDENT = re.compile(r"\b([A-Za-z_][A-Za-z0-9_]*)\b")
KEYWORDS = {
    "if", "else", "when", "for", "while", "do", "return", "break", "continue", "try", "catch",
    "finally", "throw", "fun", "val", "var", "class", "object", "interface", "true", "false",
    "null", "is", "in", "as", "this", "super", "it", "by", "where", "import", "package", "typealias",
    "private", "internal", "public", "protected", "override", "open", "sealed", "data", "enum",
    "annotation", "companion", "init", "constructor", "suspend", "inline", "noinline", "crossinline",
    "reified", "operator", "infix", "external", "lateinit", "abstract", "final", "vararg", "out",
    "const", "expect", "actual", "label", "when", "also", "let", "run", "apply", "with",
}


def strip_code(src: str) -> str:
    """يُفرّغ التعليقات والنصوص مع الحفاظ على أطوال السطور ومواضع الأحرف."""
    def blank(m):
        return re.sub(r"[^\n]", " ", m.group(0))
    src = re.sub(r"/\*.*?\*/", blank, src, flags=re.S)
    src = re.sub(r"//[^\n]*", blank, src)
    src = re.sub(r'""".*?"""', blank, src, flags=re.S)
    src = re.sub(r'"(\\.|[^"\\])*"', blank, src)
    # محرف واحد فقط بين الفاصلتين (لا نلتقط نصاً طويلاً بالخطأ: 'a' و'\\n' وليس 'it’s')
    src = re.sub(r"'(\\.|[^'\\\n])'", blank, src)
    return src


def function_body_ranges(src: str):
    """
    نطاقات أجسام الدوال كلها [(بداية, نهاية)] — أي تعريف يقع داخل أحدها فهو
    **محلّي** (يخضع لقاعدة «لا استعمال قبل التعريف»)، وما كان خارجها فهو عضو
    كلاس أو تعريف على مستوى الملف.
    """
    ranges = []
    for m in re.finditer(r"\bfun\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:<[^>]*>)?\(", src):
        i = m.end() - 1
        depth = 0
        while i < len(src):
            if src[i] == "(":
                depth += 1
            elif src[i] == ")":
                depth -= 1
                if depth == 0:
                    break
            i += 1
        j = src.find("{", i)
        if j == -1:
            continue
        d, k = 1, j + 1
        while k < len(src) and d > 0:
            if src[k] == "{":
                d += 1
            elif src[k] == "}":
                d -= 1
            k += 1
        ranges.append((j, k))
    return ranges


def file_declarations(src: str) -> set:
    """
    الأسماء المعرَّفة **خارج أجسام الدوال** (مستوى الملف أو الكلاس).
    أي تعريف داخل جسم دالة محلّي لا يُحتسب — وهذا جوهر الفاحص: لو احتسبناه
    لما أمسكنا خطأ «الاستعمال قبل التعريف».
    """
    bodies = function_body_ranges(src)

    def is_outer(pos: int) -> bool:
        return not any(a <= pos < b for a, b in bodies)

    names = set()
    for m in FUN_HEAD.finditer(src):
        if is_outer(m.start()):
            names.add(m.group(1))
    for m in DECL.finditer(src):
        if is_outer(m.start()):
            names.add(m.group(2))
    for m in CONST.finditer(src):
        if is_outer(m.start()):
            names.add(m.group(1))
    for m in re.finditer(r"\b(?:class|object|interface|typealias)\s+([A-Za-z_][A-Za-z0-9_]*)", src):
        if is_outer(m.start()):
            names.add(m.group(1))
    return names


def all_parameters(src: str) -> set:
    """
    أسماء وسائط **كل** الدوال في الملف (بما فيها المحلّية والمتشعّبة) ووسائط
    اللامبدات ({ a, b -> … }). تُعتبر معرَّفة دائماً: الوسيط محلّي داخل دالته،
    فلا يُعقل أن نتّهم استعمال اسمه بأنه «قبل التعريف» في دالة أخرى تشمله.
    """
    names = set()
    for m in re.finditer(r"\bfun\s+[A-Za-z_][A-Za-z0-9_]*\s*(?:<[^>]*>)?\(", src):
        i = m.end() - 1
        depth, buf = 0, []
        while i < len(src):
            ch = src[i]
            if ch == "(":
                depth += 1
            elif ch == ")":
                depth -= 1
                if depth == 0:
                    break
            if depth >= 1:
                buf.append(ch)
            i += 1
        for part in re.split(r",(?![^(<]*[)>])", "".join(buf)):
            mm = re.search(r"([a-z_][A-Za-z0-9_]*)\s*(?::|$)", part.strip())
            if mm:
                names.add(mm.group(1))
    for m in re.finditer(r"\{([^{}\n]*)->", src):
        for part in m.group(1).split(","):
            n = part.strip().split(":")[0].strip()
            if n.isidentifier():
                names.add(n)
    return names


def check_file(path: str):
    raw = io.open(path, encoding="utf-8").read()
    src = strip_code(raw)
    lines = src.split("\n")
    outer = file_declarations(src) | all_parameters(src)
    problems = []

    # ── نبحث عن كل تعريف دالة: نأخذ جسمها من القوس المفتوح إلى ما يقابله
    for m in re.finditer(r"\bfun\s+([A-Za-z_][A-Za-z0-9_]*)\s*(?:<[^>]*>)?\s*\(", src):
        name = m.group(1)
        # القوس المفتوح للجسم: أول '{' بعد آخر ')' من قائمة الوسائط
        i = m.end()
        depth_paren = 1
        while i < len(src) and depth_paren > 0:
            if src[i] == "(":
                depth_paren += 1
            elif src[i] == ")":
                depth_paren -= 1
            elif src[i] == "=" and depth_paren == 0:
                break
            i += 1
        j = src.find("{", i)
        if j == -1:
            continue
        # نتحقّق أنه قوس جسمٍ لا قوسُ لامبدا افتراضية
        between = src[i:j]
        if "=" in between and "{" not in between:
            continue
        depth = 1
        k = j + 1
        while k < len(src) and depth > 0:
            if src[k] == "{":
                depth += 1
            elif src[k] == "}":
                depth -= 1
            k += 1
        body = src[j + 1:k - 1]
        if not body.strip():
            continue
        body_start = j + 1

        # وسائط الدالة (تُعتبر معرَّفة قبل الجسم)
        params = set(re.findall(r"[,(]\s*(?:vararg\s+)?([a-z_][A-Za-z0-9_]*)\s*:", src[m.end():i]))
        params |= set(re.findall(r"^\s*([a-z_][A-Za-z0-9_]*)\s*:", src[m.end():i], re.M))

        declared = {}          # name -> offset inside body (أول تعريف محلّي)
        for d in DESTRUCT.finditer(body):
            for n in d.group(2).split(","):
                n = n.strip().split(":")[0].strip()
                if n.isidentifier():
                    declared.setdefault(n, d.start())
        for d in DECL.finditer(body):
            declared.setdefault(d.group(2), d.start())
        for d in LOCAL_FUN.finditer(body):     # دوال محلّية (مسافة بادئة)
            declared.setdefault(d.group(1), d.start())

        # استعمالات: أي معرّف محض غير مسبوق بنقطة وغير داخل تعريف نفسه
        for use in IDENT.finditer(body):
            n = use.group(1)
            if n in KEYWORDS or n not in declared or n == name:
                continue
            if n in params or n in outer:
                continue
            if use.start() >= declared[n]:
                continue
            before = body[max(0, use.start() - 1)]
            if before == ".":           # نداء عضو (a.b)
                continue
            # إسناد «name = …» داخل apply/also = عضو في المستقبِل لا متغيّر محلّي
            if re.match(r"\s*=(?!=)", body[use.end():use.end() + 6]):
                continue
            after = body[use.end():use.end() + 1]
            if after == ":" and re.match(r"\s*[A-Z]", before):   # وسيط مُسمّى
                pass
            line_no = raw[:body_start + use.start()].count("\n") + 1
            problems.append((line_no, f"استعمال «{n}» قبل تعريفه داخل الدالة"))

    return problems


def module_symbols() -> set:
    """كل الأسماء المعرَّفة في المشروع كله (دوال · أصناف · وسائط بنية…)."""
    names = set()
    for dirpath, dirnames, files in os.walk(KOTLIN):
        dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
        for n in files:
            if not n.endswith(".kt"):
                continue
            src = strip_code(io.open(os.path.join(dirpath, n), encoding="utf-8").read())
            names |= set(FUN_HEAD.findall(src))
            names |= set(d for _, d in DECL.findall(src))
            names |= set(re.findall(r"\b(?:class|object|interface|typealias|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)", src))
            names |= set(re.findall(r"^\s*([A-Z][A-Za-z0-9_]*)\s*[,;]?\s*$", src, re.M))  # عناصر enum
    return names


def allowlist() -> set:
    path = os.path.join(ROOT, "tools/kotlin_external_allowlist.txt")
    if not os.path.exists(path):
        return set()
    out = set()
    for line in io.open(path, encoding="utf-8"):
        line = line.split("#")[0].strip()
        if line:
            out.add(line)
    return out


# أنواع ودوال Kotlin/Android القياسية: تُنادى بلا استيراد داخل نفس الحزمة
STDLIB = {
    "Exception", "IllegalStateException", "IllegalArgumentException", "RuntimeException",
    "String", "StringBuilder", "ByteArray", "CharArray", "Regex", "Triple", "Pair",
    "List", "Map", "Set", "MutableList", "MutableMap", "MutableSet", "Array", "IntArray",
    "LongArray", "FloatArray", "HashMap", "LinkedHashMap", "ArrayList", "HashSet",
    "Int", "Long", "Float", "Double", "Boolean", "Char", "Number", "Any", "Unit", "Nothing",
    "Result", "Sequence", "Comparable", "Throwable", "Error", "Runnable", "Math", "System",
    "StackTraceElement", "Thread", "RegexOption", "Instant", "Date", "Locale", "Calendar",
    "Uri", "Intent", "Context", "JSONObject", "JSONArray", "UUID", "Base64", "SimpleDateFormat",
}


def undefined_calls(path: str, known: set, allowed: set):
    """نداء `اسم(` بحرف كبير غير معرَّف في المشروع ولا مستورد ⇒ خطأ تصريف."""
    raw = io.open(path, encoding="utf-8").read()
    src = strip_code(raw)
    imports = set(m.group(2) for m in re.finditer(r"^import\s+([\w.]+)\.([A-Za-z_][A-Za-z0-9_]*)", src, re.M))
    local = file_declarations(src)
    problems = []
    for m in re.finditer(r"(?<![.\w])([A-Z][A-Za-z0-9_]*)\s*\(", src):
        name = m.group(1)
        if name in known or name in allowed or name in imports or name in local:
            continue
        if name in STDLIB:
            continue
        if name in KEYWORDS or name.isupper():          # ثوابت عامة مثل JSON/HTTP
            continue
        line_no = raw[:m.start()].count("\n") + 1
        problems.append((line_no, f"نداء «{name}» غير معرَّف في المشروع ولا مستورد"))
    return problems


def main() -> int:
    targets = sys.argv[1:]
    files = []
    if targets:
        files = targets
    else:
        for dirpath, dirnames, names in os.walk(KOTLIN):
            dirnames[:] = [d for d in dirnames if d not in SKIP_DIRS]
            for n in names:
                if n.endswith(".kt"):
                    files.append(os.path.join(dirpath, n))

    known = module_symbols()
    allowed = allowlist()
    total = 0
    for f in sorted(files):
        problems = check_file(f)
        if not targets:
            problems += undefined_calls(f, known, allowed)
        if problems:
            rel = os.path.relpath(f, ROOT)
            print(f"✗ {rel}")
            for line, text in problems[:20]:
                print(f"    :{line}  {text}")
            total += len(problems)
    if total:
        print(f"\n✗ عدد المشاكل: {total} — هذه أخطاء ترفضها Kotlin وقت التصريف.")
        return 1
    print("✓ نطاق Kotlin سليم: لا استعمال قبل التعريف ولا نداء لدالة غير معرَّفة.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
