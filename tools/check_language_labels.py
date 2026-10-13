#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Prevent language labels/rooms from disappearing through truncation or stale DB metadata."""
from pathlib import Path
import re
import sys

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android/app/src/main/java/com/addonstation/mobile"
locale = (JAVA / "util/LocaleManager.kt").read_text(encoding="utf-8")
settings = (JAVA / "ui/settings/SettingsScreen.kt").read_text(encoding="utf-8")
settings_ui = (JAVA / "ui/components/AuthSettingsUi.kt").read_text(encoding="utf-8")
community = (JAVA / "ui/community/CommunityScreens.kt").read_text(encoding="utf-8")
channel_names = (JAVA / "ui/community/ChannelNames.kt").read_text(encoding="utf-8")

codes = ("ar", "en", "es", "fr", "pt", "ru", "zh", "tr")
problems = []

def check(name: str, ok: bool) -> None:
    print(f"{'✅' if ok else '❌'} {name}")
    if not ok:
        problems.append(name)

supported = re.findall(r'AppLanguage\("([a-z]{2})",\s*"([^"\n]+)"', locale)
check("الأسماء الأصلية للغات الثماني موجودة وغير فارغة",
      len(supported) == 8 and {code for code, _ in supported} == set(codes)
      and all(label.strip() for _, label in supported))
check("منتقي اللغات يعرض الاسم كاملاً ويسمح بالالتفاف عند تكبير الخط",
      "private fun LanguagePickerSheet(" in settings
      and "items(order.size)" in settings
      and "maxLines = 2" in settings and "softWrap = true" in settings
      and "overflow = TextOverflow.Clip" in settings
      and 'if (code == \"zh\") \"中文\"' in settings)
check("اسم اللغة المحدد لا يُقصّ بسطر واحد في صف الإعدادات",
      "maxLines = 2" in settings_ui and "softWrap = true" in settings_ui
      and "widthIn(max = 160.dp)" in settings_ui and "TextOverflow.Clip" in settings_ui)
check("تمييز لغة التطبيق من إعداد Compose الحالي، لا لغة الجهاز فقط",
      "LocaleManager.byCode(appLocaleCode()).code" in community)
check("إظهار غرف اللغة حتى إن كانت category القديمة غير صحيحة",
      "isLanguageRoom(it.id, it.name)" in community and "it.category.equals(\"lang\", ignoreCase = true)" in community)
check("أسماء غرف اللغة تُحسم بمعرّف الغرفة لجميع اللغات الثماني",
      all(f'"lang-{code}" to ' in channel_names for code in codes))

if problems:
    print(f"\n❌ {len(problems)} فحوص لغة تحتاج إصلاحًا")
    sys.exit(1)
print("\n🟢 أسماء اللغات الثماني ظاهرة في الإعدادات وغرف المجتمع دون قصّ أحادي السطر.")
