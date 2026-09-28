package com.addonstation.mobile.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** الأشكال: بطاقة 16، كبير 22، أزرار 13، بلاطات صغيرة 11–15. */
val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
)

private val DarkScheme = darkColorScheme(
    primary = AppColors.Green,
    onPrimary = AppColors.OnGreen,
    primaryContainer = AppColors.Green2,
    onPrimaryContainer = AppColors.Text,
    secondary = AppColors.Blue,
    onSecondary = AppColors.BlueText,
    tertiary = AppColors.Gold,
    onTertiary = AppColors.GoldText,
    background = AppColors.Bg,
    onBackground = AppColors.Text,
    surface = AppColors.Surface,
    onSurface = AppColors.Text,
    surfaceVariant = AppColors.Elevated,
    onSurfaceVariant = AppColors.Muted,
    outline = AppColors.Border,
    outlineVariant = AppColors.Border2,
    error = AppColors.Red,
    onError = Color.White,
    scrim = Color(0xCC000000),
)

private val LightScheme = lightColorScheme(
    primary = AppColors.Green2,
    onPrimary = Color.White,
    secondary = AppColors.BlueDark,
    onSecondary = Color.White,
    tertiary = AppColors.GoldDark,
    onTertiary = AppColors.GoldText,
    background = AppColors.LightBg,
    onBackground = AppColors.LightText,
    surface = AppColors.LightSurface,
    onSurface = AppColors.LightText,
    surfaceVariant = AppColors.LightElevated,
    onSurfaceVariant = AppColors.Muted,
    outline = AppColors.LightBorder,
    error = AppColors.RedDark,
)

@Composable
fun AddonStationTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkScheme else LightScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}

/**
 * خلفية التطبيق: لون أساسي + توهجان شعاعيان ناعمان
 * (أخضر أعلى اليمين 7٪، أزرق أسفل اليسار 6٪) كما في الوثيقة.
 */
@Composable
fun AppBackground(
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val base = if (dark) AppColors.Bg else AppColors.LightBg
    val green = if (dark) AppColors.Green.copy(alpha = 0.07f) else AppColors.Green.copy(alpha = 0.10f)
    val blue = if (dark) AppColors.Blue.copy(alpha = 0.06f) else AppColors.Blue.copy(alpha = 0.10f)
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(base)
            .background(
                Brush.verticalGradient(
                    colors = listOf(green, Color.Transparent),
                    startY = 0f,
                    endY = 1200f,
                )
            )
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, blue),
                    startY = 900f,
                    endY = 2400f,
                )
            )
    ) {
        content()
    }
}
