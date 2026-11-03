package com.addonstation.mobile.ui.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * مقاسات الشاشة المتكيّفة — تجعل التطبيق مضبوطاً على مقاس شاشة الهاتف أياً كان:
 *
 * • الهواتف الضيقة (320–380dp مثل Galaxy A الصغير) → تُصغَّر الأبعاد قليلاً فلا يزدحم الكلام
 *   ولا تنكسر الصفوف.
 * • الهواتف المعتادة (380–430dp مثل Pixel/Galaxy S) → مقاس 1:1 كما وثيقة التصميم.
 * • الهواتف الكبيرة والطيّات (430–600dp) → تُكبَّر قليلاً فتملأ الشاشة بلا فراغ عريض.
 * • الأجهزة اللوحية والشاشات العريضة (≥ 600dp) → بلا تحجيم (التنسيق نفسه يكفيها) وقد
 *   تُوسّع الشبكات أعمدةً إضافية.
 *
 * التحجيم يمرّ عبر `LocalDensity` فيكتسب كل شيء في التطبيق النسبة نفسها: الأحجام والخطوط
 * والمسافات والأيقونات — فتبقى النسب بين العناصر ثابتة على كل الأنواع.
 */

/** عرض مرجعي (dp) الذي صُمّمت عليه الواجهة — الهاتف المعتاد. */
const val REFERENCE_WIDTH_DP = 392f

/** نسبة التحجيم الحالية (1.0 = بلا تحجيم). */
val LocalAppScale = staticCompositionLocalOf { 1f }

/** هل الشاشة عريضة (لوحية أو طيّة أفقية)؟ تُستخدم لتوسيع الشبكات. */
val LocalWideScreen = staticCompositionLocalOf { false }

/** مقاس الشاشة المتاح فعلياً بوحدات dp بعد التحجيم. */
@Composable
fun appScreenWidthDp(): Float {
    val config = LocalConfiguration.current
    return config.screenWidthDp / LocalAppScale.current
}

@Composable
fun appScreenHeightDp(): Float {
    val config = LocalConfiguration.current
    return config.screenHeightDp / LocalAppScale.current
}

/**
 * عدد أعمدة شبكة مناسبة لعرض شاشة الهاتف، مع احتساب هوامش الصفحة والتباعد بين الخلايا:
 *
 *   • لا تقل الخلية عن [minCellDp] (فلا تزدحم الأيقونة والاسم).
 *   • إن صارت الخلية أعرض من [maxCellDp] نضيف عموداً (فلا يهدر العرض على الشاشات الكبيرة).
 *
 * النتيجة على هواتف شائعة (هوامش 28 · تباعد 6 · خلية 62–86):
 *   320dp → 4 أعمدة · 360–480dp → 5 أعمدة · ≥600dp (لوحي) → 6 أعمدة.
 */
@Composable
fun adaptiveColumns(
    minCellDp: Int,
    maxCellDp: Int = minCellDp + 22,
    maxColumns: Int = 8,
    horizontalPaddingDp: Int = 0,
    spacingDp: Int = 0,
): Int {
    val available = appScreenWidthDp() - horizontalPaddingDp
    if (available <= 0f) return 1

    fun cellWidth(columns: Int): Float =
        (available - (columns - 1) * spacingDp) / columns

    var columns = ((available + spacingDp) / (minCellDp + spacingDp))
        .toInt()
        .coerceIn(1, maxColumns)
    while (columns > 1 && cellWidth(columns) < minCellDp) columns--
    while (columns < maxColumns && cellWidth(columns) > maxCellDp && cellWidth(columns + 1) >= minCellDp) columns++
    return columns.coerceIn(1, maxColumns)
}

/** ارتفاع قائمة/شبكة كنسبة من ارتفاع الشاشة (بدل الأرقام الثابتة التي تفيض على الشاشات). */
@Composable
fun screenHeightFraction(fraction: Float, minDp: Int, maxDp: Int): Dp {
    val height = appScreenHeightDp() * fraction
    return height.coerceIn(minDp.toFloat(), maxDp.toFloat()).dp
}

/** عرض عنصر كنسبة من عرض الشاشة (مثل فقاعة الملصق في الشات). */
@Composable
fun screenWidthFraction(fraction: Float, minDp: Int, maxDp: Int): Dp {
    val width = appScreenWidthDp() * fraction
    return width.coerceIn(minDp.toFloat(), maxDp.toFloat()).dp
}

/** تباعد يتبع النسبة نفسها (6dp على المرجع = 6.6dp على هاتف كبير). */
@Composable
fun adaptiveDp(value: Int): Dp = (value * LocalAppScale.current).dp

/** هل الشاشة عريضة فعلاً (تُستخدم لتغيير التخطيط لا الأبعاد فقط)؟ */
@Composable
fun isWideScreen(): Boolean = LocalWideScreen.current

// ---------------------------------------------------------------------------------
// التطبيق الفعلي: يُلفّ به المحتوى في AddonStationTheme
// ---------------------------------------------------------------------------------
@Composable
fun ProvideAppSizing(content: @Composable () -> Unit) {
    val config = LocalConfiguration.current
    val base = LocalDensity.current

    val width = config.screenWidthDp
    val height = config.screenHeightDp
    val wide = width >= 600

    // نسبة العرض إلى العرض المرجعي، مضبوطة: لا تصغير مبالغ فيه ولا تكبير يفيض عمودياً.
    val widthScale = width / REFERENCE_WIDTH_DP
    // حماية الشاشات القصيرة: لا نكبّر إن كان الارتفاع قليلاً.
    val heightGuard = if (height in 1..700) (height / 760f).coerceIn(0.9f, 1f) else 1f
    val scale = if (wide) 1f else (widthScale * heightGuard).coerceIn(0.86f, 1.16f)

    val scaledDensity = remember(base.density, scale) {
        // الخطوط تبقى محترمةً لإعداد المستخدم (fontScale كما هو) — الأبعاد فقط تتحجّم.
        Density(density = base.density * scale, fontScale = base.fontScale)
    }

    CompositionLocalProvider(
        LocalAppScale provides scale,
        LocalWideScreen provides wide,
        LocalDensity provides scaledDensity,
        content = content,
    )
}

/** تباعد موحّد للشبكات المتكيّفة. */
@Composable
fun adaptiveSpacing(base: Int = 6): Arrangement.Horizontal = Arrangement.spacedBy(adaptiveDp(base))
