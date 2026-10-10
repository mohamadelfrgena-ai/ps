@file:OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)

package com.addonstation.mobile.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontSynthesis
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontVariation
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

/** Pixelify Sans: Cyrillic fallback for the Russian splash headlines. */
val PixelifyCyrillicFont: FontFamily = FontFamily(
    Font(R.font.pixelify_sans_bold, FontWeight.Bold),
)

/** Small glyph subsets of Noto Sans SC used by the Chinese splash text. */
val NotoSansScRegularFont: FontFamily = FontFamily(
    Font(R.font.noto_sans_sc_regular, FontWeight.Normal),
)
val NotoSansScBlackFont: FontFamily = FontFamily(
    Font(R.font.noto_sans_sc_black, FontWeight.Black),
)

val BodyFont: FontFamily = FontFamily.Default

/** أرقام المحفظة: أرقام روبوتو متوسطة واضحة على الخلفيات الداكنة. */
val NumberFont: FontFamily = FontFamily(
    Font(R.font.roboto_medium_numbers, FontWeight.Medium),
)

/** Body typeface for Home v2 in English (Nunito variable font, weights 400–800). */
val NunitoFont: FontFamily = FontFamily(
    Font(R.font.nunito_variable, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.nunito_variable, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.nunito_variable, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    Font(R.font.nunito_variable, FontWeight.ExtraBold, variationSettings = FontVariation.Settings(FontVariation.weight(800))),
)

/** Arabic typeface required by Home v2 (Tajawal 500/700/800). */
val TajawalFont: FontFamily = FontFamily(
    Font(R.font.tajawal_medium, FontWeight.Normal),
    Font(R.font.tajawal_medium, FontWeight.Medium),
    Font(R.font.tajawal_bold, FontWeight.Bold),
    Font(R.font.tajawal_extrabold, FontWeight.ExtraBold),
)

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


// ---------------------------------------------------------------------------------
// خطوط العرض الكبيرة (شاشة الترحيب وأمثالها)
// ---------------------------------------------------------------------------------

/**
 * هل يُظهر خط البكسل هذا النص أصلاً؟ الخط يغطي اللاتيني فقط، فالعربية والصينية
 * والسيريلية تُرسم بخط النظام — ويجب أن تُرسم **عريضة** مثل الإنجليزية تماماً.
 */
fun usesPixelGlyphs(text: String): Boolean =
    text.all { ch -> !ch.isLetter() || ch.code < 0x0250 }

/**
 * نمط كلمات العرض الكبيرة: خط البكسل للاتيني (كما في الوثيقة)، وخط النظام **العريض**
 * لبقية اللغات — فتبدو الكلمة عريضة في العربية والروسية والصينية مثل الإنجليزية.
 *
 * @param sizeSp حجم الخط بالنقاط (sp)
 * @param letterSpacing تباعد الحروف للاتيني فقط (العربية تتصل حروفها فلا تباعد)
 */
fun displayStyle(
    text: String,
    sizeSp: Float,
    color: Color = Color.Unspecified,
    latinLetterSpacingSp: Float = 1.2f,
): TextStyle {
    val latin = usesPixelGlyphs(text)
    return TextStyle(
        fontFamily = if (latin) PixelFont else FontFamily.Default,
        fontSize = sizeSp.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = if (latin) latinLetterSpacingSp.sp else 0.sp,
        color = color,
        // يسمح بالسماكة الاصطناعية إن لم يوجد وجه عريض في الخط — فلا يعود النص رفيعاً أبداً
        fontSynthesis = FontSynthesis.All,
    )
}
