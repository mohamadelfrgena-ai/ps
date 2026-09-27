package com.addonstation.app.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection

data class Palette(
    val bg: Color,
    val surface: Color,
    val elevated: Color,
    val border: Color,
    val text: Color,
    val muted: Color,
    val green: Color,
    val greenInk: Color,
    val gold: Color,
    val blue: Color,
    val red: Color,
    val purple: Color
)

val DarkPalette = Palette(
    bg = Color(0xFF0B0F14),
    surface = Color(0xFF131A24),
    elevated = Color(0xFF1A2330),
    border = Color(0xFF263240),
    text = Color(0xFFE8EEF5),
    muted = Color(0xFF8EA0B3),
    green = Color(0xFF3DDC84),
    greenInk = Color(0xFF052311),
    gold = Color(0xFFFFD479),
    blue = Color(0xFF6FB8FF),
    red = Color(0xFFFF5A5A),
    purple = Color(0xFFB98CFF)
)

val LightPalette = Palette(
    bg = Color(0xFFF2F5F8),
    surface = Color(0xFFFFFFFF),
    elevated = Color(0xFFE7EDF4),
    border = Color(0xFFD7E0E9),
    text = Color(0xFF17222E),
    muted = Color(0xFF5D6D7D),
    green = Color(0xFF159957),
    greenInk = Color(0xFFFFFFFF),
    gold = Color(0xFFB8860B),
    blue = Color(0xFF1264A3),
    red = Color(0xFFD83C4A),
    purple = Color(0xFF7654BB)
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun AddonTheme(dark: Boolean, rtl: Boolean, content: @Composable () -> Unit) {
    val palette = if (dark) DarkPalette else LightPalette
    val scheme = if (dark) {
        darkColorScheme(
            primary = palette.green,
            onPrimary = palette.greenInk,
            background = palette.bg,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text
        )
    } else {
        lightColorScheme(
            primary = palette.green,
            onPrimary = palette.greenInk,
            background = palette.bg,
            surface = palette.surface,
            onBackground = palette.text,
            onSurface = palette.text
        )
    }
    CompositionLocalProvider(
        LocalPalette provides palette,
        LocalLayoutDirection provides if (rtl) LayoutDirection.Rtl else LayoutDirection.Ltr
    ) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

@Composable
fun rememberDark(name: String): Boolean = when (name) {
    "light" -> false
    "dark" -> true
    else -> isSystemInDarkTheme()
}
