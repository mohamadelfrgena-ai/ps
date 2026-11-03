package com.addonstation.mobile.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

/**
 * لوحة ألوان واحدة كاملة للوضع الداكن أو الفاتح.
 *
 * ملاحظة: قيم الوضع الداكن هي قيم وثيقة التصميم **حرفياً** (لم تتغيّر).
 */
@Immutable
data class AppPalette(
    val bg: Color, val surface: Color, val elevated: Color, val elevated2: Color,
    val border: Color, val border2: Color,
    val text: Color, val muted: Color, val dim: Color,
    val green: Color, val green2: Color, val greenLight: Color, val greenDark: Color,
    val gold: Color, val goldDark: Color, val goldLight: Color, val goldText: Color,
    val blue: Color, val blueDark: Color, val blueLight: Color, val blueText: Color,
    val red: Color, val redDark: Color, val redLight: Color,
    val purple: Color, val adminBlue: Color, val freeGray: Color,
    val greenGlow: Color, val blueGlow: Color,
    val purpleBadgeBg: Color, val goldBadgeBg: Color, val adminBadgeBg: Color,
    val grayBadgeBg: Color, val mentionTint: Color, val announcementBg: Color,
    val dangerBg: Color, val onGreen: Color,
)

/** ألوان شاشة Home v2 والشريط المشترك؛ تُحقن بالـ CompositionLocal من الثيم الحالي. */
@Immutable
data class HomePalette(
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHigh: Color,
    val barSurface: Color,
    val border: Color,
    val text: Color,
    val textMuted: Color,
    val chipText: Color,
    val blue: Color,
    val accentBlue: Color,
    val blueLink: Color,
    val goldFill: Color,
    val goldText: Color,
    val planStart: Color,
    val planEnd: Color,
    val planBorder: Color,
    val planSecondary: Color,
    val epic: Color,
    val inactive: Color,
    val homeInactiveBg: Color,
    val homeInactiveIcon: Color,
    val notificationBadge: Color,
    val heroOverlay: Color,
    val heroSubtitle: Color,
    val buttonText: Color,
    val goldInk: Color,
    val cardShadow: Color,
    val barShadow: Color,
)

object HomePalettes {
    val Dark = HomePalette(
        isDark = true,
        bg = Color(0xFF0A0F1C), surface = Color(0xFF121A2B), surfaceHigh = Color(0xFF1A2438),
        barSurface = Color(0xFF0E1526), border = Color(0xFF223049), text = Color(0xFFEAF0FA),
        textMuted = Color(0xFF9DB0CF), chipText = Color(0xFFC4D1E8), blue = Color(0xFF2E7BEF), accentBlue = Color(0xFF4C9AFF),
        blueLink = Color(0xFF6FA8FF), goldFill = Color(0xFFF0C25E), goldText = Color(0xFFF0C25E),
        planStart = Color(0xFF3A2A0B), planEnd = Color(0xFF1B1405), planBorder = Color(0xFF8A6A22),
        planSecondary = Color(0xFFE8D5A3), epic = Color(0xFF9B6BFF), inactive = Color(0xFF8EA0BF),
        homeInactiveBg = Color(0xFF1A2438), homeInactiveIcon = Color(0xFFC4D1E8),
        notificationBadge = Color(0xFFFF6B78), heroOverlay = Color(0xFF0A0F1C),
        heroSubtitle = Color(0xFFD3DDF0), buttonText = Color.White, goldInk = Color(0xFF1B1405),
        cardShadow = Color.Transparent, barShadow = Color.Black.copy(alpha = 0.50f),
    )
    val Light = HomePalette(
        isDark = false,
        bg = Color(0xFFF4F7FC), surface = Color(0xFFFFFFFF), surfaceHigh = Color(0xFFE8EEF8),
        barSurface = Color(0xFFFFFFFF), border = Color(0xFFD5DEEC), text = Color(0xFF0E1526),
        textMuted = Color(0xFF53627F), chipText = Color(0xFF33425F), blue = Color(0xFF2563D6), accentBlue = Color(0xFF1D4FB8),
        blueLink = Color(0xFF1D4FB8), goldFill = Color(0xFFF0C25E), goldText = Color(0xFF7A5600),
        planStart = Color(0xFFFFF3D2), planEnd = Color(0xFFFFE7A8), planBorder = Color(0xFFE0B24A),
        planSecondary = Color(0xFF5C4300), epic = Color(0xFF7B4FD1), inactive = Color(0xFF53627F),
        homeInactiveBg = Color(0xFFE8EEF8), homeInactiveIcon = Color(0xFF33425F),
        notificationBadge = Color(0xFFE5484D), heroOverlay = Color(0xFF0A0F1C),
        heroSubtitle = Color(0xFFD3DDF0), buttonText = Color.White, goldInk = Color(0xFF1B1405),
        cardShadow = Color(0x140E1526), barShadow = Color(0x2E0E1526),
    )
}

val LocalHomePalette = androidx.compose.runtime.staticCompositionLocalOf { HomePalettes.Dark }

/**
 * ألوان التطبيق — تُقرأ من اللوحة النشطة (داكنة أو فاتحة).
 *
 * كل الألوان `val` لها `get()` يقرأ حالة التركيب، فأي composable يقرأ أي لون
 * يُعاد تركيبه تلقائياً عند التبديل بين الوضعين (بلا `key` وبلا فقدان حالة).
 * أسماء الخصائص لم تتغيّر، فكل مواضع الاستدعاء في الشاشات تعمل بلا تعديل.
 */
object AppColors {

    /** اللوحة الداكنة = قيم وثيقة التصميم حرفياً (لم تتغيّر حرفاً). */
    val DarkPalette = AppPalette(
        bg = Color(0xFF0B0F14), surface = Color(0xFF131A24),
        elevated = Color(0xFF1A2330), elevated2 = Color(0xFF212D3D),
        border = Color(0xFF263240), border2 = Color(0xFF334152),
        text = Color(0xFFE8EEF5), muted = Color(0xFF8EA0B3),
        dim = Color(0xFF5C6F82), green = Color(0xFF3DDC84),
        green2 = Color(0xFF2FAE67), greenLight = Color(0xFF58F29B),
        greenDark = Color(0xFF197046), gold = Color(0xFFFFD479),
        goldDark = Color(0xFFE0A93C), goldLight = Color(0xFFFFE39A),
        goldText = Color(0xFF3A2802), blue = Color(0xFF6FB8FF),
        blueDark = Color(0xFF3A7BD5), blueLight = Color(0xFF7EC2FF),
        blueText = Color(0xFF041A30), red = Color(0xFFFF5A5A),
        redDark = Color(0xFFD63A3A), redLight = Color(0xFFFF7A7A),
        purple = Color(0xFFB98CFF), adminBlue = Color(0xFF7FD4FF),
        freeGray = Color(0xFFAAB9C8), greenGlow = Color(0x1F3DDC84),
        blueGlow = Color(0x0F6FB8FF), purpleBadgeBg = Color(0x29B98CFF),
        goldBadgeBg = Color(0x24FFD479), adminBadgeBg = Color(0x247FD4FF),
        grayBadgeBg = Color(0x1FAAB9C8), mentionTint = Color(0x1A6FB8FF),
        announcementBg = Color(0x14FFD479), dangerBg = Color(0x1AFF5A5A),
        onGreen = Color(0xFF04210F),
    )

    /** اللوحة الفاتحة: خلفية فاتحة، نصوص داكنة، وألوان أعمق لتباين كافٍ. */
    val LightPalette = AppPalette(
        bg = Color(0xFFF4F7FC), surface = Color(0xFFFFFFFF),
        elevated = Color(0xFFFFFFFF), elevated2 = Color(0xFFE7EDF4),
        border = Color(0xFFDCE4EC), border2 = Color(0xFFC4D1DF),
        text = Color(0xFF0F1B26), muted = Color(0xFF56697C),
        dim = Color(0xFF7B8EA1), green = Color(0xFF17A75A),
        green2 = Color(0xFF128A48), greenLight = Color(0xFF3ED07E),
        greenDark = Color(0xFF0C6133), gold = Color(0xFFC79309),
        goldDark = Color(0xFF9A6F06), goldLight = Color(0xFFE8B93B),
        goldText = Color(0xFFFFFFFF), blue = Color(0xFF1E6FD9),
        blueDark = Color(0xFF1455AE), blueLight = Color(0xFF4E9BF0),
        blueText = Color(0xFFFFFFFF), red = Color(0xFFD93838),
        redDark = Color(0xFFB32626), redLight = Color(0xFFE86969),
        purple = Color(0xFF7B4FD1), adminBlue = Color(0xFF0E7C9B),
        freeGray = Color(0xFF64798D), greenGlow = Color(0x1A17A75A),
        blueGlow = Color(0x1A1E6FD9), purpleBadgeBg = Color(0x267B4FD1),
        goldBadgeBg = Color(0x26C79309), adminBadgeBg = Color(0x260E7C9B),
        grayBadgeBg = Color(0x1F64798D), mentionTint = Color(0x1A1E6FD9),
        announcementBg = Color(0x26C79309), dangerBg = Color(0x1AD93838),
        onGreen = Color(0xFFFFFFFF),
    )

    private var active: AppPalette by mutableStateOf(DarkPalette)

    /** يفعّل لوحة الوضع المطلوب (يستدعيه AddonStationTheme). */
    fun apply(darkTheme: Boolean) {
        val next = if (darkTheme) DarkPalette else LightPalette
        if (next !== active) active = next
    }

    /** الوضع النشط الآن (dark = الوضع الداكن). */
    val isDark: Boolean get() = active === DarkPalette

    val Bg: Color get() = active.bg
    val Surface: Color get() = active.surface
    val Elevated: Color get() = active.elevated
    val Elevated2: Color get() = active.elevated2
    val Border: Color get() = active.border
    val Border2: Color get() = active.border2
    val Text: Color get() = active.text
    val Muted: Color get() = active.muted
    val Dim: Color get() = active.dim
    val Green: Color get() = active.green
    val Green2: Color get() = active.green2
    val GreenLight: Color get() = active.greenLight
    val GreenDark: Color get() = active.greenDark
    val Gold: Color get() = active.gold
    val GoldDark: Color get() = active.goldDark
    val GoldLight: Color get() = active.goldLight
    val GoldText: Color get() = active.goldText
    val Blue: Color get() = active.blue
    val BlueDark: Color get() = active.blueDark
    val BlueLight: Color get() = active.blueLight
    val BlueText: Color get() = active.blueText
    val Red: Color get() = active.red
    val RedDark: Color get() = active.redDark
    val RedLight: Color get() = active.redLight
    val Purple: Color get() = active.purple
    val AdminBlue: Color get() = active.adminBlue
    val FreeGray: Color get() = active.freeGray
    val GreenGlow: Color get() = active.greenGlow
    val BlueGlow: Color get() = active.blueGlow
    val PurpleBadgeBg: Color get() = active.purpleBadgeBg
    val GoldBadgeBg: Color get() = active.goldBadgeBg
    val AdminBadgeBg: Color get() = active.adminBadgeBg
    val GrayBadgeBg: Color get() = active.grayBadgeBg
    val MentionTint: Color get() = active.mentionTint
    val AnnouncementBg: Color get() = active.announcementBg
    val DangerBg: Color get() = active.dangerBg
    val OnGreen: Color get() = active.onGreen

    // أسماء الوضع الفاتح (مرجعية سريعة للوحة الفاتحة نفسها — مصدر واحد للألوان)
    val LightBg: Color get() = LightPalette.bg
    val LightSurface: Color get() = LightPalette.surface
    val LightElevated: Color get() = LightPalette.elevated2
    val LightBorder: Color get() = LightPalette.border
    val LightText: Color get() = LightPalette.text

    /** لوحة المصادقة والإعدادات تتبع المظهر النشط، مثل بقية التطبيق. */
    private val authPalette: HomePalette
        get() = if (isDark) HomePalettes.Dark else HomePalettes.Light

    val AuthWhite = Color(0xFFFFFFFF)
    val AuthBg: Color get() = authPalette.bg
    val AuthSurface: Color get() = authPalette.surface
    val AuthSurfaceHigh: Color get() = authPalette.surfaceHigh
    val AuthBarSurface: Color get() = authPalette.barSurface
    val AuthBorder: Color get() = authPalette.border
    val AuthDivider: Color get() = if (isDark) Color(0xFF1D2940) else Color(0xFFD5DEEC)
    val AuthText: Color get() = authPalette.text
    val AuthMuted: Color get() = authPalette.textMuted
    val AuthChipLabel: Color get() = authPalette.chipText
    val AuthBlue: Color get() = authPalette.blue
    val AuthBlueLight: Color get() = authPalette.blueLink
    /** لون ذهبي فاتح للأسطح والأزرار، منفصل عن الذهبي الداكن المقروء كنص في الوضع الفاتح. */
    val AuthGoldFill = Color(0xFFF0C25E)
    val AuthGold: Color get() = authPalette.goldText
    val AuthGoldDark = Color(0xFF1B1405)
    val AuthGoldBorder: Color get() = authPalette.planBorder
    val AuthGoldLine: Color get() = if (isDark) Color(0xFF5A4416) else Color(0xFFE0B24A)
    val AuthGoldSoft: Color get() = authPalette.planSecondary
    val AuthWalletTop: Color get() = if (isDark) Color(0xFF3E2D0C) else Color(0xFFFFF3D2)
    val AuthWalletBottom: Color get() = authPalette.planEnd
    val AuthStreakBg: Color get() = if (isDark) Color(0xFF1A1710) else Color(0xFFFFF8E5)
    val AuthStreakBorder: Color get() = if (isDark) Color(0xFF4A3A18) else Color(0xFFE8C66A)
    val AuthStreakMuted: Color get() = if (isDark) Color(0xFF8A7A52) else Color(0xFF7A5600)
    val AuthClaimDisabled: Color get() = if (isDark) Color(0xFF3A3220) else Color(0xFFE8EDF4)
    val AuthNavInactive: Color get() = authPalette.inactive
    val AuthGreen: Color get() = if (isDark) Color(0xFF3FBF6A) else Color(0xFF1E7A44)
    val AuthGreenText: Color get() = if (isDark) Color(0xFF7FD99A) else Color(0xFF1E7A44)
    val AuthPurple: Color get() = authPalette.epic
    val AuthTeal: Color get() = if (isDark) Color(0xFF2DD4BF) else Color(0xFF11776F)
    val AuthAccentBlue: Color get() = authPalette.accentBlue
    val AuthRed: Color get() = if (isDark) Color(0xFFFF6B78) else Color(0xFFB3262E)
    val AuthRedText: Color get() = if (isDark) Color(0xFFFF8791) else Color(0xFFB3262E)
    val AuthRedButton: Color get() = if (isDark) Color(0xFFB3414B) else Color(0xFFB32626)
    val AuthRedBorder: Color get() = if (isDark) Color(0xFF5A2830) else Color(0xFFF2B5B5)
    val AuthRedBorderStrong: Color get() = if (isDark) Color(0xFF6B2D36) else Color(0xFFD88C8C)
    val AuthRedTextSoft: Color get() = if (isDark) Color(0xFFE6B3B8) else Color(0xFF7A2E32)
    val AuthRedTop: Color get() = if (isDark) Color(0xFF22121A) else Color(0xFFFFF1F1)
    val AuthRedBottom: Color get() = if (isDark) Color(0xFF150B10) else Color(0xFFFFE3E3)
    val AuthPlanTop: Color get() = authPalette.planStart
    val AuthPlanBottom: Color get() = authPalette.planEnd
    val AuthPlanButton: Color get() = if (isDark) Color(0xFF2A2210) else Color(0xFFFFF8E6)
    val AuthMasterTop: Color get() = if (isDark) Color(0xFF102244) else Color(0xFFE8F2FF)
    val AuthPermissionBorder: Color get() = if (isDark) Color(0xFF5A4416) else Color(0xFFE0B24A)
}

/** ألوان شارات الرتب حسب الوثيقة. */
data class RankColors(val text: Color, val background: Color)

fun rankColors(role: String, plan: String): RankColors = when {
    role == "admin" -> RankColors(AppColors.AdminBlue, AppColors.AdminBadgeBg)
    role == "creator" || role == "moderator" -> RankColors(AppColors.Green, AppColors.GreenGlow)
    plan == "legendary" -> RankColors(AppColors.Gold, AppColors.GoldBadgeBg)
    plan == "epic" -> RankColors(AppColors.Purple, AppColors.PurpleBadgeBg)
    else -> RankColors(AppColors.FreeGray, AppColors.GrayBadgeBg)
}

/** لون اسم المستخدم في الشات حسب الرتبة (Discord-style). */
fun nameColor(role: String, plan: String): Color = when {
    role == "admin" -> AppColors.AdminBlue
    role == "moderator" || role == "creator" -> AppColors.Green
    plan == "legendary" -> AppColors.Gold
    plan == "epic" -> AppColors.Purple
    else -> AppColors.Text
}
