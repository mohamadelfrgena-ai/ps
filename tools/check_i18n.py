#!/usr/bin/env python3
"""
check_i18n.py — التحقق من أن كل مفتاح نصي موجود في اللغات الثمانية.

الاستخدام:
    python3 tools/check_i18n.py

يفشل (exit code 1) إذا:
  - نقص مفتاح في إحدى اللغات.
  - يوجد مفتاح زائد غير موجود في اللغة الافتراضية (values/strings.xml).
  - اختلفت العناصر النائبة {name} {date} {price} بين اللغات لنفس المفتاح.
"""

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "android" / "app" / "src" / "main" / "res"

DEFAULT_DIR = "values"
LOCALES = ["ar", "pt", "es", "ru", "zh", "fr", "tr"]

PLACEHOLDER = re.compile(r"\{([a-zA-Z_][a-zA-Z0-9_]*)\}")


def load(locale: str) -> dict[str, set[str]]:
    path = RES / (DEFAULT_DIR if locale == "default" else f"values-{locale}") / "strings.xml"
    if not path.exists():
        return {}
    text = path.read_text(encoding="utf-8")
    # نتجاهل التعليقات حتى لا تُخرب الاستخراج
    text = re.sub(r"<!--.*?-->", "", text, flags=re.S)
    # عناصر مثل <string name="x">قيمة</string> قد تحتوي &amp; و \'
    try:
        tree = ET.fromstring(text)
    except ET.ParseError as exc:  # pragma: no cover
        print(f"❌ خطأ في تحليل XML داخل {path}: {exc}")
        sys.exit(1)

    out: dict[str, set[str]] = {}
    for el in tree.findall("string"):
        name = el.get("name")
        if not name:
            continue
        out[name] = set(PLACEHOLDER.findall("".join(el.itertext())))
    return out


def main() -> int:
    base = load("default")
    if not base:
        print("❌ لم يتم العثور على القيم الافتراضية في values/strings.xml")
        return 1

    problems: list[str] = []
    print(f"ℹ️  اللغة الافتراضية (values): {len(base)} مفتاح")

    for loc in LOCALES:
        data = load(loc)
        if not data:
            problems.append(f"values-{loc}/strings.xml غير موجود أو فارغ")
            print(f"⏳ {loc:>2}: غير موجود")
            continue

        missing = sorted(set(base) - set(data))
        extra = sorted(set(data) - set(base))
        mismatch = sorted(
            k for k in set(base) & set(data) if base[k] != data[k]
        )

        status = "✅" if not (missing or extra or mismatch) else "❌"
        print(f"{status} {loc:>2}: {len(data)} مفتاح"
              f"{' — ناقص: ' + str(len(missing)) if missing else ''}"
              f"{' — زائد: ' + str(len(extra)) if extra else ''}"
              f"{' — عناصر نائبة مختلفة: ' + str(len(mismatch)) if mismatch else ''}")

        if missing:
            problems.append(f"{loc}: مفاتيح ناقصة → {', '.join(missing[:20])}"
                            + (" …" if len(missing) > 20 else ""))
        if extra:
            problems.append(f"{loc}: مفاتيح زائدة → {', '.join(extra[:20])}")
        for key in mismatch:
            problems.append(f"{loc}: العناصر النائبة مختلفة في '{key}' "
                            f"(الافتراضي {sorted(base[key])} مقابل {sorted(data[key])})")

    if problems:
        print("\n🔴 مشاكل:")
        for p in problems:
            print("  -", p)
        return 1

    print("\n🟢 كل اللغات مكتملة ومتطابقة.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
