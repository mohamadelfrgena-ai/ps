#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Static regression checks for the user's live light/dark appearance request."""
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
SRC = ROOT / "android/app/src/main/java/com/addonstation/mobile"
SETTINGS = (SRC / "ui/settings/SettingsScreen.kt").read_text(encoding="utf-8")
NAV = (SRC / "ui/nav/AppNav.kt").read_text(encoding="utf-8")
COLORS = (SRC / "ui/theme/Color.kt").read_text(encoding="utf-8")
SPEC = (SRC / "ui/theme/Spec.kt").read_text(encoding="utf-8")
CHAT = (SRC / "ui/chat/ChatScreen.kt").read_text(encoding="utf-8")

rows = []
row_start = SETTINGS.find('title = stringResource(R.string.set_theme)')
row_end = SETTINGS.find("AuthSettingsDivider()", row_start)
theme_row = SETTINGS[row_start:row_end] if row_start >= 0 and row_end >= 0 else ""
set_theme = SETTINGS[SETTINGS.find("fun setDarkTheme("):SETTINGS.find("fun requestPasswordReset(")]

rows.append(("appearance setting is an inline switch", "AuthSettingsSwitch(" in theme_row and "trailing = {" in theme_row))
rows.append(("the whole setting row also toggles appearance", "onClick = { setDarkTheme(!dark) }" in theme_row))
rows.append(("the old theme-picker sheet is removed", "ThemePickerSheet" not in SETTINGS and "themeSheet" not in SETTINGS))
rows.append(("theme selection persists to DataStore and boot mirror",
             "container.prefs.setDarkTheme(value)" in set_theme and "AppPrefs.writeDark(context, value)" in set_theme))
rows.append(("root observes the preference and applies the theme live",
             "container.prefs.darkTheme.collectAsState(initial = true)" in NAV
             and "AddonStationTheme(darkTheme = dark)" in NAV))
rows.append(("auth/settings colors follow the selected palette",
             "private val authPalette: HomePalette" in COLORS
             and "get() = if (isDark) HomePalettes.Dark else HomePalettes.Light" in COLORS))
rows.append(("home/chat/profile design tokens have adaptive light values",
             "private val homePalette: HomePalette" in SPEC
             and "get() = if (AppColors.isDark) HomePalettes.Dark else HomePalettes.Light" in SPEC))
rows.append(("picker is laid out after the composer, not over the chat",
             CHAT.find("EmojiStickerPanel(") > CHAT.find("ChatInputBar(")
             and CHAT.find("EmojiStickerPanel(") < CHAT.find("actionsFor?.let { ui ->")))
rows.append(("emoji and sticker choices restore the keyboard",
             "text += emoji" in CHAT and "focusRequester.requestFocus()" in CHAT
             and "keyboardController?.show()" in CHAT))

failed = [name for name, ok in rows if not ok]
for name, ok in rows:
    print(f"{'✅' if ok else '❌'} {name}")
if failed:
    raise SystemExit(f"\n{len(failed)} appearance regression check(s) failed")
print("\n🟢 Appearance toggle and light/dark palette wiring are present.")
