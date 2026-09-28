package com.addonstation.mobile.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * لوحة ألوان AddonStation كما في وثيقة التصميم (القسم «الألوان») — حرفياً.
 */
object AppColors {
    val Bg = Color(0xFF0B0F14)
    val Surface = Color(0xFF131A24)
    val Elevated = Color(0xFF1A2330)
    val Elevated2 = Color(0xFF212D3D)
    val Border = Color(0xFF263240)
    val Border2 = Color(0xFF334152)

    val Text = Color(0xFFE8EEF5)
    val Muted = Color(0xFF8EA0B3)
    val Dim = Color(0xFF5C6F82)

    val Green = Color(0xFF3DDC84)
    val Green2 = Color(0xFF2FAE67)
    val GreenLight = Color(0xFF58F29B)
    val GreenDark = Color(0xFF197046)

    val Gold = Color(0xFFFFD479)
    val GoldDark = Color(0xFFE0A93C)
    val GoldLight = Color(0xFFFFE39A)
    val GoldText = Color(0xFF3A2802)

    val Blue = Color(0xFF6FB8FF)
    val BlueDark = Color(0xFF3A7BD5)
    val BlueLight = Color(0xFF7EC2FF)
    val BlueText = Color(0xFF041A30)

    val Red = Color(0xFFFF5A5A)
    val RedDark = Color(0xFFD63A3A)
    val RedLight = Color(0xFFFF7A7A)

    val Purple = Color(0xFFB98CFF)
    val AdminBlue = Color(0xFF7FD4FF)
    val FreeGray = Color(0xFFAAB9C8)

    // خلفيات شفافة جاهزة (نِسَب الوثيقة)
    val GreenGlow = Color(0x1F3DDC84)
    val BlueGlow = Color(0x0F6FB8FF)
    val PurpleBadgeBg = Color(0x29B98CFF)
    val GoldBadgeBg = Color(0x24FFD479)
    val AdminBadgeBg = Color(0x247FD4FF)
    val GrayBadgeBg = Color(0x1FAAB9C8)
    val MentionTint = Color(0x1A6FB8FF)
    val AnnouncementBg = Color(0x14FFD479)
    val DangerBg = Color(0x1AFF5A5A)

    // نصوص داكنة فوق الأزرار الملوّنة
    val OnGreen = Color(0xFF04210F)

    // ثيم فاتح اختياري
    val LightBg = Color(0xFFF5F7FA)
    val LightSurface = Color(0xFFFFFFFF)
    val LightElevated = Color(0xFFEDF1F6)
    val LightBorder = Color(0xFFD6DEE8)
    val LightText = Color(0xFF14202B)
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
