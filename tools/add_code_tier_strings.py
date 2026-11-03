#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""يضيف نصوص توليد الأكواد المحسّنة (العدد الافتراضي وتأكيد الفئة) إلى اللغات الثمانية.
الأداة وصفية: إعادة تشغيلها لا تُكرّر شيئاً.
"""
import io
import os
import re

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "android", "app", "src", "main", "res")

# key → {lang: value}
NEW = {
 "admin_codes_summary": {
   "ar": "%1$s كود × %2$s AC = %3$s AC إجمالاً",
   "en": "%1$s codes × %2$s AC = %3$s AC total",
   "es": "%1$s códigos × %2$s AC = %3$s AC en total",
   "fr": "%1$s codes × %2$s AC = %3$s AC au total",
   "pt": "%1$s códigos × %2$s AC = %3$s AC no total",
   "ru": "%1$s кодов × %2$s AC = %3$s AC всего",
   "tr": "%1$s kod × %2$s AC = toplam %3$s AC",
   "zh": "%1$s 个代码 × %2$s AC = 共 %3$s AC",
 },
 "admin_codes_bad_amount": {
   "ar": "أدخل قيمة 100 AC على الأقل.",
   "en": "Enter at least 100 AC.",
   "es": "Introduce al menos 100 AC.",
   "fr": "Saisis au moins 100 AC.",
   "pt": "Informe pelo menos 100 AC.",
   "ru": "Введите не меньше 100 AC.",
   "tr": "En az 100 AC gir.",
   "zh": "请输入至少 100 AC。",
 },
 "ac_redeem_detected": {
   "ar": "فئة الكود: %1$s AC",
   "en": "Code tier: %1$s AC",
   "es": "Valor del código: %1$s AC",
   "fr": "Valeur du code : %1$s AC",
   "pt": "Valor do código: %1$s AC",
   "ru": "Номинал кода: %1$s AC",
   "tr": "Kod değeri: %1$s AC",
   "zh": "代码面值：%1$s AC",
 },
}

FOLDER = {"ar": "values-ar", "en": "values", "es": "values-es", "fr": "values-fr",
          "pt": "values-pt", "ru": "values-ru", "tr": "values-tr", "zh": "values-zh"}

# مكان الإدراج: بعد هذا المفتاح
AFTER = {
    "admin_codes_summary": "admin_codes_count",
    "admin_codes_bad_amount": "admin_codes_summary",
    "ac_redeem_detected": "ac_redeem_hint",
}


def main():
    for lang, folder in FOLDER.items():
        path = os.path.join(RES, folder, "strings.xml")
        text = io.open(path, encoding="utf-8").read()
        added = 0
        for key, values in NEW.items():
            if re.search(r'<string name="%s"' % re.escape(key), text):
                continue
            anchor = AFTER[key]
            pattern = re.compile(r'^[ \t]*<string name="%s">.*?</string>[ \t]*$' % re.escape(anchor), re.M)
            match = pattern.search(text)
            assert match, f"{lang}: مرساة {anchor} غير موجودة"
            line = f'    <string name="{key}">{values[lang]}</string>'
            text = text[:match.end()] + "\n" + line + text[match.end():]
            added += 1
        io.open(path, "w", encoding="utf-8").write(text)
        print(f"✓ {folder:12} +{added} نصاً")


if __name__ == "__main__":
    main()
