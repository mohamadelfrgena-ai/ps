#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يضيف نصّ «إضافة تفاعل» (وصف زر ＋ بجانب تفاعلات الرسالة) إلى اللغات الثمانية.

المفتاح المضاف:
    chat_add_reaction      وصف زر البسولة الأخيرة في صف التفاعلات

وصفية: إعادة تشغيلها لا تُكرّر المفاتيح (تتخطّى الموجود).
"""
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

VALUES = {
    "chat_add_reaction": {
        "ar": "إضافة تفاعل",
        "en": "Add reaction",
        "es": "Añadir reacción",
        "fr": "Ajouter une réaction",
        "pt": "Adicionar reação",
        "ru": "Добавить реакцию",
        "tr": "Tepki ekle",
        "zh": "添加表情回应",
    },
}

FILES = {"en": "values"}
for code in ("ar", "es", "fr", "pt", "ru", "tr", "zh"):
    FILES[code] = f"values-{code}"


def main() -> int:
    added = 0
    for code, folder in FILES.items():
        path = os.path.join(RES, folder, "strings.xml")
        text = io.open(path, encoding="utf-8").read()
        block = []
        for key, per_locale in VALUES.items():
            tag = f'<string name="{key}">'
            if tag in text:
                continue
            block.append(f'    {tag}{per_locale[code]}</string>')
        if not block:
            continue
        text = text.replace("</resources>", "\n".join(block) + "\n</resources>")
        io.open(path, "w", encoding="utf-8").write(text)
        added += len(block)
        print(f"✓ {folder}: +{len(block)} مفتاح")
    print(f"🟢 أُضيف {added} مفتاحاً (لكل اللغات).\n   شغّل: python3 tools/check_i18n.py")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
