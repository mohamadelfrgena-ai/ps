package com.addonstation.app.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.app.R

// Brand palette (sampled from the original mobile CSS)
val BrandBg = Color(0xFF0B0F14)
val BrandSurface = Color(0xFF131A24)
val BrandElevated = Color(0xFF1A2330)
val BrandBorder = Color(0xFF263240)
val BrandText = Color(0xFFE8EEF5)
val BrandMuted = Color(0xFF8EA0B3)
val BrandDim = Color(0xFF5C6F82)
val BrandBlue = Color(0xFF6FB8FF)
val BrandDiamond = Color(0xFF38BDF8)
val BrandGreen = Color(0xFF3DDC84)
val BrandEmerald = Color(0xFF10B981)
val BrandGold = Color(0xFFFFD479)
val BrandPurple = Color(0xFFB98CFF)
val BrandRed = Color(0xFFFF5A5A)

val LightBg = Color(0xFFF2F5F9)
val LightSurface = Color(0xFFFFFFFF)
val LightElevated = Color(0xFFE8EEF5)
val LightBorder = Color(0xFFD3DCE6)
val LightText = Color(0xFF10161D)
val LightMuted = Color(0xFF54657A)

private val DarkScheme = darkColorScheme(
    primary = BrandDiamond,
    onPrimary = Color(0xFF062027),
    secondary = BrandBlue,
    tertiary = BrandGreen,
    background = BrandBg,
    onBackground = BrandText,
    surface = BrandSurface,
    onSurface = BrandText,
    surfaceVariant = BrandElevated,
    onSurfaceVariant = BrandMuted,
    outline = BrandBorder,
    outlineVariant = BrandBorder,
    error = BrandRed,
    onError = Color.White,
    surfaceContainer = BrandSurface,
    surfaceContainerHigh = BrandElevated,
    surfaceContainerLowest = BrandBg
)

private val LightScheme = lightColorScheme(
    primary = Color(0xFF0284C7),
    onPrimary = Color.White,
    secondary = Color(0xFF1D6FD1),
    tertiary = Color(0xFF0E9F6E),
    background = LightBg,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightElevated,
    onSurfaceVariant = LightMuted,
    outline = LightBorder,
    outlineVariant = LightBorder,
    error = Color(0xFFD92D20),
    surfaceContainer = LightSurface,
    surfaceContainerHigh = LightElevated,
    surfaceContainerLowest = LightBg
)

val Monocraft = FontFamily(Font(R.font.monocraft, FontWeight.Normal))

private val AppTypography = Typography(
    displaySmall = TextStyle(fontFamily = Monocraft, fontSize = 26.sp, lineHeight = 32.sp),
    headlineSmall = TextStyle(fontFamily = Monocraft, fontSize = 20.sp, lineHeight = 26.sp),
    titleLarge = TextStyle(fontFamily = Monocraft, fontSize = 17.sp, lineHeight = 22.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 15.sp, lineHeight = 22.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 13.sp),
    labelSmall = TextStyle(fontSize = 11.sp)
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** Extra brand colors not covered by Material3 roles. */
data class ExtraColors(
    val gold: Color = BrandGold,
    val diamond: Color = BrandDiamond,
    val emerald: Color = BrandEmerald,
    val purple: Color = BrandPurple,
    val dim: Color = BrandDim,
    val border: Color = BrandBorder,
    val elevated: Color = BrandElevated,
    val rankFree: Color = BrandGreen,
    val rankEpic: Color = BrandDiamond,
    val rankLegendary: Color = BrandGold,
    val rankCreator: Color = BrandPurple,
    val rankModerator: Color = BrandBlue,
    val rankAdmin: Color = BrandDiamond
)

val LocalExtra = compositionLocalOf { ExtraColors() }

fun rankColor(rank: String, extra: ExtraColors): Color = when (rank) {
    "admin" -> extra.rankAdmin
    "moderator" -> extra.rankModerator
    "creator" -> extra.rankCreator
    "legendary" -> extra.rankLegendary
    "epic" -> extra.rankEpic
    else -> extra.rankFree
}

@Composable
fun AddonStationTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    val scheme = if (darkTheme) DarkScheme else LightScheme
    val extra = if (darkTheme) ExtraColors() else ExtraColors(
        dim = LightMuted,
        border = LightBorder,
        elevated = LightElevated
    )
    CompositionLocalProvider(LocalExtra provides extra) {
        MaterialTheme(colorScheme = scheme, typography = AppTypography, shapes = AppShapes, content = content)
    }
}
