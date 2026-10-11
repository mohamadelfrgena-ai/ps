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
 * رموز وثيقة التصميم الجديدة (AddonStation_handoff · SPEC.md) — حرفياً كما وردت.
 *
 * لماذا ملف مستقلّ ولم أُغيّر [AppColors]؟
 *   • هذه الرموز تخصّ **الشاشات الثلاث المعاد تصميمها** (الرئيسية · الغرف · الشات)
 *     كما طلبت الوثيقة («Dark theme only for these screens»).
 *   • بقية التطبيق (متجر · ملف شخصي · إدارة · إعدادات …) يبقى على لوحته الحالية
 *     بلا أي تغيير مفاجئ ⇒ لا انكسار بصري في شاشة لم تُراجع بعد.
 *   • عند اعتمادك للتوسعة، التغيير يصير بنقل القيم إلى [AppColors.DarkPalette] مرة واحدة.
 */
object Spec {

    // ── الألوان (جدول «Design tokens» في الوثيقة) ─────────────────────────────
    val bg = Color(0xFF0A0F1C)            // خلفية الشاشة
    val surface = Color(0xFF121A2B)       // البطاقات · الحقول · الشرائح
    val surfaceHigh = Color(0xFF1A2438)   // الأقراص · الأزرار الثانوية · خلفية الأفاتار
    val barSurface = Color(0xFF0E1526)    // الشرائط العلوية · شريط الكتابة
    val border = Color(0xFF223049)        // 1dp حدود وفواصل
    val text = Color(0xFFEAF0FA)          // النص الأساسي
    val textMuted = Color(0xFF9DB0CF)     // النص الثانوي والتلميحات
    val chipLabel = Color(0xFFC4D1E8)     // نص الشرائح غير المختارة
    val blue = Color(0xFF2E7BEF)          // الأزرار الأساسية · رسائلي · شارة NEW
    val blueLink = Color(0xFF6FA8FF)      // «عرض الكل»
    val gold = Color(0xFFF0C25E)          // عناوين البكسل · رصيد AC · Legendary
    val goldDeepBg = Color(0xFF1B1405)    // خلفية بطاقة Legendary
    val goldBorder = Color(0xFF8A6A22)    // إطار بطاقة Legendary
    val epic = Color(0xFF9B6BFF)          // شارة EPIC
    val green = Color(0xFF7FD99A)         // «متصّل الآن»
    val freeGreen = Color(0xFF3C9D5A)     // شارة FREE
    val pinnedBorder = Color(0xFF5A4416)  // حدّ شريط المثبّتات
    val heroSubtitle = Color(0xFFD3DDF0)
    val legendText = Color(0xFFE8D5A3)

    // ألوان أسماء الأعضاء الدورية (وثيقة الشات)
    val nameColors = listOf(Color(0xFF9B6BFF), Color(0xFF7FD99A), Color(0xFF6FA8FF), Color(0xFFEAF0FA))

    // ── رموز وثيقة الملف الشخصي (SPEC_PROFILES.md §1) ─────────────────────────
    val danger = Color(0xFFFF8791)         // نصّ الخطر: حظر · إبلاغ · تسجيل الخروج
    val dangerBorder = Color(0xFFB3414B)   // إطار زرّ الخطر
    val onlineFill = Color(0xFF3FBF6A)     // أخضر نقطة الحالة
    val onlineText = Color(0xFF7FD99A)     // نصّ «متصّل الآن» (قيمة الوثيقة)
    val awayAmber = Color(0xFFF0C25E)      // «بعيد/خامل»: الوثيقة قالت amber بلا قيمة ⇒ كهرمان اللوحة
    val offlineGrey = Color(0xFF6B7A94)    // «غير متصل» — بلا توهّج
    val dashedBorder = Color(0xFF2E3C58)   // إطار متقطّع (بطاقة المحفظة المخفيّة في شاشة C)


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
