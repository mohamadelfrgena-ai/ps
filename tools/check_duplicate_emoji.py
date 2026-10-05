#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
فاحص: يمنع تكرار الإيموجي في عناوين الأقسام (SectionHeader).

العيب: `SectionHeader` يكتب العنوان هكذا

        text = if (leadingEmoji != null) "$leadingEmoji $title" else title

فإذا كان **النص نفسه يبدأ بالإيموجي** (`<string name="home_plans">✦ خطط…`)
وأضفناه مرة ثانية من الكود (`leadingEmoji = "✦"`) ظهر مرّتين: «✦ ✦ خطط…».

هذا الفاحص يمسح كل نداءات `SectionHeader(...)` في ملفات Kotlin، ويقارن
`leadingEmoji` ببداية سلسلة النص في **كل اللغات**:
    • إن بدأت كل اللغات بنفس الإيموجي ⇒ تكرار ✗ (احذف leadingEmoji من الكود)
    • وإن لم تبدأ ⇒ الإيموجي من الكود لازم ✓ (لا تقرير)
"""
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
RES = ROOT / "android/app/src/main/res"
SRC = ROOT / "android/app/src/main/java"

EMOJI_START = re.compile(
    r"^\s*(?:️)?("
    r"[\U0001F000-\U0001FAFF]"
    r"|[\u2600-\u27BF]"
    r"|[\u2B00-\u2BFF]"
    r"|[\u2190-\u21FF]"
    r"|[\u2700-\u27bf]"
    r")"
)

STRING_RE = re.compile(r'<string\s+name="([^"]+)"[^>]*>(.*?)</string>', re.S)
CALL_RE = re.compile(r"\bSectionHeader\s*\(")
TITLE_RES_RE = re.compile(r'title\s*=\s*stringResource\(\s*R\.string\.(\w+)\s*\)')
TITLE_LIT_RE = re.compile(r'title\s*=\s*"(.*?)"\s*\+\s*stringResource\(\s*R\.string\.(\w+)\s*\)')
EMOJI_ARG_RE = re.compile(r'leadingEmoji\s*=\s*"([^"]*)"')


def load_strings() -> dict[str, dict[str, str]]:
    out: dict[str, dict[str, str]] = {}
    for f in sorted(RES.glob("values*/strings.xml")):
        locale = f.parent.name
        text = f.read_text(encoding="utf-8")
        for name, value in STRING_RE.findall(text):
            out.setdefault(name, {})[locale] = value
    return out


def call_block(text: str, start: int) -> str:
    """يقطع نص النداء من `SectionHeader(` حتى القوس المقابل."""
    i = text.index("(", start)
    depth = 0
    for j in range(i, len(text)):
        if text[j] == "(":
            depth += 1
        elif text[j] == ")":
            depth -= 1
            if depth == 0:
                return text[start : j + 1]
    return text[start : start + 400]


def main() -> int:
    strings = load_strings()
    bad: list[str] = []
    checked = 0

    for kt in sorted(SRC.rglob("*.kt")):
        text = kt.read_text(encoding="utf-8")
        for m in CALL_RE.finditer(text):
            block = call_block(text, m.start())
            em = EMOJI_ARG_RE.search(block)
            if not em:
                continue
            emoji = em.group(1).strip()
            if not emoji:
                continue

            lit = TITLE_LIT_RE.search(block)
            res = TITLE_RES_RE.search(block)
            name = None
            if lit:
                # العنوان = "إيموجي " + النص ⇒ تكرار إن تساوى مع leadingEmoji
                prefix = lit.group(1).strip()
                if prefix == emoji:
                    bad.append(
                        f"{kt.relative_to(ROOT)}: «{prefix} » مكرّر مع leadingEmoji=\"{emoji}\""
                    )
                continue
            if not res:
                continue
            name = res.group(1)
            checked += 1

            per_locale = strings.get(name)
            if not per_locale:
                continue
            values = list(per_locale.values())
            starts = [bool(EMOJI_START.match(v)) for v in values]
            if not any(starts):
                continue  # لا إيموجي في النص ⇒ الكود هو مصدره الوحيد ✓
            if all(starts) and all(v.strip().startswith(emoji) for v in values):
                bad.append(
                    f"{kt.relative_to(ROOT)}: R.string.{name} يبدأ بـ«{emoji}» في كل اللغات "
                    f"ومع ذلك مُمرَّر leadingEmoji=\"{emoji}\" ⇒ إيموجي مكرّر"
                )
            elif any(starts) and not all(starts):
                bad.append(
                    f"{kt.relative_to(ROOT)}: R.string.{name} — الإيموجي في بعض اللغات فقط "
                    "والكود يضيفه ⇒ سيظهر مكرّراً في تلك اللغات"
                )

    if bad:
        print("✗ إيموجي مكرّر في عناوين الأقسام:")
        for b in bad:
            print("   ", b)
        print(f"\nاحذف leadingEmoji من النداء (الإيموجي موجود داخل النص) — فُحص {checked} عنواناً.")
        return 1

    print(f"✓ لا إيموجي مكرّر في عناوين الأقسام ({checked} عنواناً مُراجَعاً)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
