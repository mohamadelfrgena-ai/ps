package com.addonstation.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R

/**
 * خط البكسل (Minecraft-style) مرخّص OFL — يُستخدم للعناوين والأرقام والعلامة التجارية،
 * وخط النظام للنصوص العادية، تماماً كما في وثيقة التصميم.
 */
val PixelFont: FontFamily = FontFamily(
    Font(R.font.pixel_regular, FontWeight.Normal),
    Font(R.font.pixel_regular, FontWeight.Medium),
    Font(R.font.pixel_regular, FontWeight.SemiBold),
    Font(R.font.pixel_regular, FontWeight.Bold),
    Font(R.font.pixel_regular, FontWeight.Black),
)

val BodyFont: FontFamily = FontFamily.Default

/** أحجام الوثيقة: عنوان الصفحة 22، عنوان القسم 14 (بكسل، تباعد 0.5)، الجسم 11.5–13، الكيكر 9. */
object AppText {
    val pageTitle = TextStyle(fontFamily = PixelFont, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    val sectionTitle = TextStyle(
        fontFamily = PixelFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 0.5.sp,
    )
    val welcomeName = TextStyle(fontFamily = PixelFont, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    val brand = TextStyle(
        fontFamily = PixelFont,
        fontSize = 14.sp,
        fontWeight = FontWeight.Normal,
        letterSpacing = 0.5.sp,
    )
    val brandFooter = TextStyle(fontFamily = PixelFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val greeting = TextStyle(fontFamily = BodyFont, fontSize = 12.sp)
    val body = TextStyle(fontFamily = BodyFont, fontSize = 13.sp)
    val bodySmall = TextStyle(fontFamily = BodyFont, fontSize = 11.5.sp)
    val small = TextStyle(fontFamily = BodyFont, fontSize = 10.5.sp)
    val tiny = TextStyle(fontFamily = BodyFont, fontSize = 9.sp)
    val kicker = TextStyle(
        fontFamily = PixelFont,
        fontSize = 9.sp,
        letterSpacing = 1.4.sp,
        fontWeight = FontWeight.Bold,
    )
    val button = TextStyle(fontFamily = BodyFont, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    val statNumber = TextStyle(fontFamily = PixelFont, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    val chatName = TextStyle(fontFamily = BodyFont, fontSize = 13.5.sp, fontWeight = FontWeight.Bold)
    val chatBody = TextStyle(fontFamily = BodyFont, fontSize = 13.sp)
    val chatTime = TextStyle(fontFamily = BodyFont, fontSize = 9.5.sp)
}

val AppTypography = Typography(
    headlineLarge = AppText.pageTitle,
    headlineMedium = AppText.welcomeName,
    titleMedium = AppText.sectionTitle,
    bodyMedium = AppText.body,
    bodySmall = AppText.bodySmall,
    labelSmall = AppText.tiny,
)

val CenterCenter = TextAlign.Center
