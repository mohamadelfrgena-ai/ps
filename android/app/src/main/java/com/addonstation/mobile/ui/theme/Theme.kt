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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.util.DevicePerformance

/** الأشكال: بطاقة 16، كبير 22، أزرار 13، بلاطات صغيرة 11–15. */
val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(11.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(13.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(26.dp),
)

private fun darkScheme() = darkColorScheme(
    // اللون الأساسي أزرق (كباقي التطبيق) — منه يأتي مؤشر الكتابة ودوائر التحميل الافتراضية.
    primary = HomePalettes.Dark.blue,
    onPrimary = Color.White,
    primaryContainer = AppColors.BlueDark,
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

private fun lightScheme() = lightColorScheme(
    primary = HomePalettes.Light.blue,
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
    // كل شاشات التطبيق تمرّ من هنا، فتُضبط المقاسات على مقاس شاشة الهاتف مرة واحدة:
    // الأحجام والخطوط والمسافات كلها بنسبة واحدة (انظر Sizing.kt).
    // الألوان تُقرأ من اللوحة النشطة (داكنة/فاتحة) قبل بناء نظام Material،
    // فتتبدّل كل الشاشات لأن AppColors نفسه مرتبط بالوضع.
    AppColors.apply(darkTheme)
    val scheme = remember(darkTheme) { if (darkTheme) darkScheme() else lightScheme() }

    CompositionLocalProvider(
        LocalHomePalette provides if (darkTheme) HomePalettes.Dark else HomePalettes.Light,
    ) {
        ProvideAppSizing {
            MaterialTheme(
                colorScheme = scheme,
                typography = AppTypography,
                shapes = AppShapes,
                content = content,
            )
        }
    }
}

/**
 * خلفية التطبيق: لون أساسي + توهجان أزرقان ناعمان
 * (أعلى 10٪ داكن / 8٪ فاتح، وأسفل 6٪) — لا أخضر في الخلفية.
 */
@Composable
fun AppBackground(
    dark: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val base = if (dark) AppColors.Bg else AppColors.LightBg
    val topGlow = if (dark) HomePalettes.Dark.blue.copy(alpha = 0.10f) else HomePalettes.Light.blue.copy(alpha = 0.08f)
    val blue = if (dark) AppColors.Blue.copy(alpha = 0.06f) else AppColors.Blue.copy(alpha = 0.10f)
    val context = LocalContext.current
    val lowRam = remember(context.applicationContext) {
        DevicePerformance.profile(context).lowRamDevice
    }
    val topBrush = remember(topGlow) {
        Brush.verticalGradient(
            colors = listOf(topGlow, Color.Transparent),
            startY = 0f,
            endY = 1200f,
        )
    }
    val bottomBrush = remember(blue) {
        Brush.verticalGradient(
            colors = listOf(Color.Transparent, blue),
            startY = 900f,
            endY = 2400f,
        )
    }
    // طبقتا التوهج ثابتتان لكنهما تستهلكان تعبئة شاشة كاملة في كل frame.
    // الهاتف منخفض الذاكرة يأخذ اللون الصافي الأخف؛ بقية الأجهزة تحتفظ بالشكل.
    val backgroundModifier = if (lowRam) {
        modifier.fillMaxSize().background(base)
    } else {
        modifier
            .fillMaxSize()
            .background(base)
            .background(topBrush)
            .background(bottomBrush)
    }
    Box(modifier = backgroundModifier) {
        content()
    }
}
