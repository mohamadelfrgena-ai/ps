#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يضيف نصوص «معاينة الملصق داخل مربع الإرسال» إلى ملفات اللغات الثمانية.

المفاتيح المضافة:
    chat_remove_sticker        وصف زر ✕ الأحمر الذي يُلغي الملصق المختار
    chat_sticker_ready         سطر صغير تحت المعاينة: اكتب نصاً إن أردت ثم أرسل

وصفية: إعادة تشغيلها لا تُكرّر المفاتيح (تتخطّى الموجود).
"""
import io
import os

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

VALUES = {
    "chat_remove_sticker": {
        "ar": "إلغاء الملصق",
        "en": "Remove sticker",
        "es": "Quitar pegatina",
        "fr": "Retirer l’autocollant",
        "pt": "Remover figurinha",
        "ru": "Убрать стикер",
        "tr": "Çıkartmayı kaldır",
        "zh": "移除贴纸",
    },
    "chat_sticker_ready": {
        "ar": "اكتب نصاً مع الملصق إن أردت، ثم اضغط إرسال",
        "en": "Add a message with the sticker if you like, then send",
        "es": "Escribe un mensaje con la pegatina si quieres y envía",
        "fr": "Écris un message avec l’autocollant si tu veux, puis envoie",
        "pt": "Escreva uma mensagem com a figurinha se quiser e envie",
        "ru": "При желании добавь текст к стикеру и отправь",
        "tr": "İstersen çıkartmaya bir mesaj yaz, sonra gönder",
        "zh": "需要的话可以写上文字，然后发送",
    },
}

FILES = {"en": "values"}          # الافتراضي (values) هو الإنجليزية
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
