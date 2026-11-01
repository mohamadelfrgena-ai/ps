package com.addonstation.mobile.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R

/**
 * ألوان التصميم المعتمدة: تبقى قيم الوضع الداكن كما في مواصفة AddonStation_handoff،
 * وتُقرأ لها نظائر فاتحة ذات تباين مناسب عند اختيار الوضع الفاتح.
 */
object Spec {

    private val homePalette: HomePalette
        get() = if (AppColors.isDark) HomePalettes.Dark else HomePalettes.Light

    // ── ألوان عامة (تتبدّل مباشرة مع لوحة التطبيق) ────────────────────────────
    val bg: Color get() = homePalette.bg
    val surface: Color get() = homePalette.surface
    val surfaceHigh: Color get() = homePalette.surfaceHigh
    val barSurface: Color get() = homePalette.barSurface
    val border: Color get() = homePalette.border
    val text: Color get() = homePalette.text
    val textMuted: Color get() = homePalette.textMuted
    val chipLabel: Color get() = homePalette.chipText
    val blue: Color get() = homePalette.blue
    val blueLink: Color get() = homePalette.blueLink
    val gold: Color get() = homePalette.goldText
    val goldFill: Color get() = homePalette.goldFill
    val goldInk: Color get() = homePalette.goldInk
    val goldDeepBg: Color get() = homePalette.planEnd
    val goldPlanStart: Color get() = homePalette.planStart
    val goldBorder: Color get() = homePalette.planBorder
    val epic: Color get() = if (AppColors.isDark) Color(0xFF9B6BFF) else Color(0xFF7B4FD1)
    val green: Color get() = if (AppColors.isDark) Color(0xFF7FD99A) else Color(0xFF1E7A44)
    val freeGreen: Color get() = if (AppColors.isDark) Color(0xFF3C9D5A) else Color(0xFF2D7A3E)
    val pinnedBorder: Color get() = if (AppColors.isDark) Color(0xFF5A4416) else Color(0xFFE0B24A)
    val heroSubtitle: Color get() = homePalette.heroSubtitle
    val legendText: Color get() = homePalette.planSecondary
    val nameColors: List<Color>
        get() = if (AppColors.isDark) darkNameColors else lightNameColors

    private val darkNameColors = listOf(Color(0xFF9B6BFF), Color(0xFF7FD99A), Color(0xFF6FA8FF), Color(0xFFEAF0FA))
    private val lightNameColors = listOf(Color(0xFF7B4FD1), Color(0xFF1E7A44), Color(0xFF1D4FB8), Color(0xFF33425F))

    // ── رموز وثيقة الملف الشخصي (SPEC_PROFILES.md §1) ─────────────────────────
    val danger: Color get() = if (AppColors.isDark) Color(0xFFFF8791) else Color(0xFFB3262E)
    val dangerBorder: Color get() = if (AppColors.isDark) Color(0xFFB3414B) else Color(0xFFB32626)
    val onlineFill: Color get() = if (AppColors.isDark) Color(0xFF3FBF6A) else Color(0xFF17A75A)
    val onlineText: Color get() = if (AppColors.isDark) Color(0xFF7FD99A) else Color(0xFF1E7A44)
    val awayAmber: Color get() = if (AppColors.isDark) Color(0xFFF0C25E) else Color(0xFF8A5B00)
    val offlineGrey: Color get() = if (AppColors.isDark) Color(0xFF6B7A94) else Color(0xFF53627F)
    val dashedBorder: Color get() = if (AppColors.isDark) Color(0xFF2E3C58) else Color(0xFFD5DEEC)


    // ── المقاسات ──────────────────────────────────────────────────────────────
    val screenPad = 16.dp
    val chatListPad = 12.dp
    val cardRadius = 14.dp
    val cardRadiusLg = 16.dp
    val heroRadius = 20.dp
    val pillRadius = 22.dp
    val buttonRadius = 12.dp
    val touch = 44.dp              // أصغر هدف لمس
    val heroHeight = 250.dp
    val latestCardWidth = 150.dp
    val latestThumbHeight = 100.dp
    val rowThumb = 64.dp
    val avatar64 = 64.dp
    val avatar40 = 40.dp
    val composerField = 44.dp

    // مقاسات الملف الشخصي (docs/SPEC_PROFILES.md §1–§4)
    val bannerH = 150.dp           // ارتفاع بانر الملف
    val avatarLg = 96.dp           // أفاتار الملف الشخصي
    val avatarOverlap = 46.dp      // مقدار تجاوز الأفاتار للبانر
    val dotSize = 30.dp            // نقطة الحالة
    val profileCardRadius = 16.dp  // بطاقات الملف
    val badgeRadius = 8.dp         // شرائح الشارات
    val actionBtnH = 48.dp         // أزرار إجراءات الملف والصداقة
    val gridBtnH = 56.dp           // أزرار الشبكة 2×2
    val safetyRowH = 52.dp         // صفوف بطاقة السلامة
    val statBarH = 8.dp            // شريط تقدّم المستوى

    // ── الخطوط ────────────────────────────────────────────────────────────────
    /** أرقام الرصيد والعدّادات (النص الرقمي من الحقيبة). */
    val NumberFont: FontFamily = FontFamily(Font(R.font.roboto_medium_numbers, FontWeight.Medium))

    /** عنوان شاشة (بكسل · 16) — الرئيسية · COMMUNITY. */
    val screenTitle = TextStyle(fontFamily = PixelFont, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

    /** عنوان قسم (بكسل · 13) — LATEST ADDONS · MOST DOWNLOADED … */
    val sectionTitle = TextStyle(fontFamily = PixelFont, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

    /** عنوان قسم في قائمة الغرف (بكسل · 12). */
    val sectionTitleSmall = TextStyle(fontFamily = PixelFont, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

    /** شعار الهيرو (بكسل · 17 · ارتفاع سطر 1.5). */
    val heroSlogan = TextStyle(fontFamily = PixelFont, fontSize = 17.sp, fontWeight = FontWeight.Bold, lineHeight = 25.5.sp, letterSpacing = 1.sp)

    /** اسم التطبيق في الشريط العلوي (بكسل · 15). */
    val topBarName = TextStyle(fontFamily = PixelFont, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

    val greeting = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)          // سطر التحية الصغير
    val welcomeBig = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)  // السطر الكبير
    val cardTitle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    val secondary = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val button = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    val buttonSmall = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
    val badge = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
    val chip = TextStyle(fontSize = 13.sp)
    val roomName = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)
    val roomPreview = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val roomTime = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val link = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)

    // الشات
    val chatName = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    val chatOnline = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val msgAuthor = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
    val msgBody = TextStyle(fontSize = 14.sp, lineHeight = 19.6.sp)
    val msgTime = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium)
    val msgQuote = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)
    val stickerSize = 96.dp
    val ownBubbleMax = 260.dp
    val incomingImageW = 230.dp
    val incomingImageH = 150.dp

    // ── خطوط الملف الشخصي (SPEC_PROFILES.md §2) ───────────────────────────────
    /** تسمية قسم صغيرة بالأحرف الكبيرة (بكسل · 11 · ذهبي · تباعد 1). */
    val smallCaps = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)

    val profileName = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)   // اسم العرض
    val profileHandle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)      // @username
    val badgeChip = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)     // شرائح الشارات
    val statusBox = TextStyle(fontSize = 13.sp)                                        // الحالة المخصّصة
    val statNumber = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)    // رقم الإحصاء
    val statLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)          // تسمية الإحصاء
    val bioText = TextStyle(fontSize = 14.sp, lineHeight = 19.6.sp)                    // النبذة (1.4)
    val balanceBig = TextStyle(fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)    // رصيد AC
    val levelText = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)     // «المستوى N · x/100»
    val gridButton = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)    // أزرار الشبكة
    val walletCaption = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)      // «محفظة AC · الرصيد الحالي» · «N يوم متتالٍ»

    // ── مقاسات وخطوط وثيقة الدخول والإعدادات (SPEC_AUTH_SETTINGS §0) ────────
    // مقاسات الوثيقة (§0 / §1 / §3)
    val authHeaderH = 210.dp               // لوحة رأس الدخول
    val fieldH = 52.dp                     // ارتفاع الحقل
    val fieldRadius = 14.dp                // زاوية الحقل
    val iconTile = 44.dp                   // قرص الأيقونة
    val iconTileRadius = 12.dp
    val groupRowMin = 68.dp                // أدنى ارتفاع صفّ الإعدادات
    val footerText = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)   // «© 2026 …»
    val fieldLabel = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold)
    val fieldHint = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium)
    val authTitle = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)  // «Sign in»
    val authSubtitle = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium)
    val rowTitle = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold)   // عنوان صفّ إعداد
    val rowSub = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, lineHeight = 16.2.sp)
    val rowValue = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val pillText = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)   // «Edit»
    val segText = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold)
    val primaryBtn = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    val planLabel = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Bold)       // «My plan»
    val planName = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)   // «Your plan: Free»
}
