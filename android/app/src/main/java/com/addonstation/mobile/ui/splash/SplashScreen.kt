package com.addonstation.mobile.ui.splash

import android.graphics.Paint
import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.theme.NotoSansScBlackFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.PixelifyCyrillicFont
import com.addonstation.mobile.ui.theme.TajawalFont
import com.addonstation.mobile.util.AppPrefs
import java.util.Locale
import kotlinx.coroutines.delay

// SPEC_SPLASH.md — الجدول الزمني الجديد 7 ثوانٍ (تم قياس الفواصل بالثواني في القسم 2).
// ملاحظة: الخلفية السابقة `splash_bg_legacy.jpg` بقيت محفوظة في `drawable-nodpi/`
// ولم تُحذف (للرجوع إذا لزم) — لا يُشار إليها هنا بعد اعتماد التصميم الجديد.
private const val TIMELINE_END_S = 7f
private const val TIMELINE_MS = 7_000
private const val COMPACT_LOOP_MS = 1_500
private const val MIN_SPLASH_MS = 1_500L

// توكنات الألوان (نفس القيم الحرفيّة في وثيقة الـ SPEC)
private val SplashBackground = Color(0xFF0A0F1C)
private val SplashText = Color(0xFFEAF0FA)
private val SplashBlue = Color(0xFF6FA8FF)
private val SplashGold = Color(0xFFF0C25E)
private val SplashMuted = Color(0xFFC4D1E8)
private val LogoDropShadow = Color(0x99000000) // 60% أسود كما في الوثيقة

/**
 * شاشة الترحيب وفق `AddonStation_splash_8lang/SPEC_SPLASH.md`:
 *  • مشهد (splash_bg_original) يتلاشى ويبتعد ببطء من 1.15 إلى 1.0.
 *  • شعار Minecraft يهبط من −100dp بارتداد (مركز أفقي، أعلى 84).
 *  • ثلاث عبارات في وسط 330dp (تدخل من اليمين/اليسار في الـ RTL حسب السطر)
 *    + توهّج ذهبي على السطر الثالث ونجمة ✦ متلاشية.
 *  • كتلة العلامة (أيقونة + ADDONSTATION) بـ overshoot ثم شعار «...» + لودر 10 شرائح.
 *  • اللغة تتبدّل بنفس الجدول؛ اللودر يتقدّم 10 قطاعات (4.3 + 0.17·i).
 *  • التشغيلات اللاحقة (بعد الـ install/update) تعرض شعار + أيقونة + لودر فقط
 *    حلقة 1.5s، مع «Tap to skip» بعد ثانية.
 *  • «حركة مقلَّلة» ⇒ عرض الإطار النهائي فوراً ثم الانتقال بعد جاهزية البيانات.
 *  • الجلسة والبيانات تُحمَّل بالتوازي؛ الخروج = نهاية الحركة + جاهزية.
 */
@Composable
fun SplashScreen(
    startupReady: Boolean = true,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val isFirstLaunch = remember(context) { !AppPrefs.splashCompleted(context) }
    val reduceMotion = remember(context) {
        runCatching {
            val scales = listOf(
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.WINDOW_ANIMATION_SCALE,
            )
            scales.any { key ->
                Settings.Global.getFloat(context.contentResolver, key, 1f) <= 0f
            }
        }.getOrDefault(false)
    }
    val startedAt = remember { SystemClock.elapsedRealtime() }
    val skipText = stringResource(R.string.splash_skip)
    val loadingText = stringResource(R.string.common_loading)
    val brandName = stringResource(R.string.brand_wordmark).uppercase(Locale.ROOT)
    val platformTagline = stringResource(R.string.home_guest_card_title_plain)

    // الجدول الزمني 0..TIMELINE_END_S، LinearEasing ثم نستخرج نطاقات من progress().
    val introProgress = remember(isFirstLaunch, reduceMotion) {
        Animatable(if (isFirstLaunch && reduceMotion) TIMELINE_END_S else 0f)
    }
    val compactProgress = remember { Animatable(0f) }
    val exitProgress = remember { Animatable(0f) }

    var compactMode by remember(isFirstLaunch) { mutableStateOf(!isFirstLaunch) }
    var introComplete by remember(isFirstLaunch, reduceMotion) {
        mutableStateOf(!isFirstLaunch || reduceMotion)
    }
    var skipRequested by remember { mutableStateOf(false) }
    var skipEnabled by remember { mutableStateOf(false) }
    var completionHandled by remember { mutableStateOf(false) }

    // تفعيل التخطي بعد 1s كما تنص الوثيقة.
    LaunchedEffect(Unit) { delay(1_000L); skipEnabled = true }

    // المقدمة الكاملة 7 ثوانٍ.
    LaunchedEffect(isFirstLaunch, reduceMotion, skipRequested) {
        if (isFirstLaunch && !reduceMotion) {
            introProgress.animateTo(
                targetValue = TIMELINE_END_S,
                animationSpec = tween(
                    durationMillis = if (skipRequested) 180 else TIMELINE_MS,
                    easing = LinearEasing,
                ),
            )
            introComplete = true
        }
    }

    // التشغيلات اللاحقة: حلقة 1.5s للشعار + اللودر.
    LaunchedEffect(compactMode, reduceMotion, skipRequested) {
        if (compactMode && !reduceMotion && !skipRequested) {
            while (true) {
                compactProgress.snapTo(0f)
                compactProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(COMPACT_LOOP_MS, easing = LinearEasing),
                )
            }
        }
    }

    LaunchedEffect(startupReady, introComplete, skipRequested) {
        if (!startupReady || !introComplete || completionHandled) return@LaunchedEffect
        if (!isFirstLaunch && !reduceMotion && !skipRequested) {
            val elapsed = SystemClock.elapsedRealtime() - startedAt
            val remaining = (MIN_SPLASH_MS - elapsed).coerceAtLeast(0L)
            if (remaining > 0L) delay(remaining)
        }
        completionHandled = true
        if (reduceMotion) {
            exitProgress.snapTo(1f)
        } else {
            exitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = if (isFirstLaunch) 400 else 240),
            )
        }
        AppPrefs.markSplashCompleted(context)
        onFinished()
    }

    val phase: () -> Float = {
        when {
            reduceMotion -> TIMELINE_END_S
            compactMode -> compactProgress.value * COMPACT_LOOP_MS / 1000f
            else -> introProgress.value
        }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val languageCode = appLocaleCode().substringBefore('-').lowercase(Locale.ROOT)
    val isArabic = languageCode == "ar"
    val isRussian = languageCode == "ru"
    val isChinese = languageCode == "zh"
    val bottomInset = 0.dp
    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = 1f - exitProgress.value }
            .background(SplashBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = skipEnabled && !completionHandled,
                onClickLabel = skipText,
            ) { skipRequested = true }
            .semantics { contentDescription = skipText },
    ) {
        val screenHeight = maxHeight

        // 1) صورة المشهد: كاملة العرض، ارتفاع ثابت 600dp، crop مع محاذاة للأعلى.
        val sceneHeight: Dp = 600.dp
        Image(
            painter = painterResource(R.drawable.splash_bg_original),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(sceneHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val p = phase()
                    alpha = if (compactMode) 1f else ramp(p, 0f, 0.7f)
                    val zoomOut = if (compactMode) 0f else ramp(p, 0f, 4f)
                    scaleX = if (compactMode) 1.02f else lerp(1.15f, 1f, zoomOut)
                    scaleY = scaleX
                    transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0f)
                },
        )

        // 2) تدرّج عمودي: شفاف → داكن صلب ليتلاشى المشهد إلى الخلفية.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to SplashBackground.copy(alpha = 0.25f),
                            0.25f to SplashBackground.copy(alpha = 0.15f),
                            0.50f to SplashBackground.copy(alpha = 0.80f),
                            0.66f to SplashBackground,
                            1f to SplashBackground,
                        ),
                    ),
                ),
        )

        // 3) شعار Minecraft: أعلى 84dp، عرض 230، ظل أسود 0/8/14 @ 60%.
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = 84.dp)
                .size(width = 230.dp, height = 76.dp)
                .graphicsLayer {
                    val p = phase()
                    if (compactMode) {
                        val enter = ramp(p, 0.08f, 0.34f)
                        alpha = enter
                        translationY = (1f - enter) * -22.dp.toPx()
                    } else {
                        // 0.3..0.8s: السقوط من −100 إلى +10 ، 1.05s: −4 ، 1.25s: 0
                        alpha = ramp(p, 0.3f, 0.5f) * (1f - ramp(p, 6.6f, 7f))
                        translationY = when {
                            p < 0.3f -> -100.dp.toPx()
                            p < 0.8f -> lerp(-100f, 10f, ramp(p, 0.3f, 0.8f)).dp.toPx()
                            p < 1.05f -> lerp(10f, -4f, ramp(p, 0.8f, 1.05f)).dp.toPx()
                            p < 1.25f -> lerp(-4f, 0f, ramp(p, 1.05f, 1.25f)).dp.toPx()
                            else -> 0f
                        }
                    }
                }
                .drawBehind {
                    drawIntoCanvas { canvas ->
                        val native = canvas.nativeCanvas
                        val paint = Paint().apply {
                            isAntiAlias = true
                            color = android.graphics.Color.TRANSPARENT
                            setShadowLayer(14f.dp.toPx(), 0f, 8f.dp.toPx(), LogoDropShadow.toArgb())
                        }
                        // نرسم المستطيل الشفاف ليُسجَّل ظل الكلمة فقط
                        native.drawRect(0f, 0f, size.width, size.height, paint)
                    }
                },
        ) {
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }

        // 4) ثلاث العبارات: وسط أفقياً، top = topInset + 330dp.
        //    السطر 1 يدخل من البداية، السطر 2 من النهاية، السطر 3 يكبر من 0.85.
        if (!compactMode) {
            SloganColumn(
                line1 = stringResource(R.string.splash_dream_it),
                line2 = stringResource(R.string.splash_build_it),
                line3 = stringResource(R.string.splash_create_it),
                phase = phase,
                rtl = rtl,
                languageCode = languageCode,
                topPadding = 330.dp,
            )
        }

        // 5) كتلة العلامة: أيقونة + اسم «ADDONSTATION»، top = topInset + 610dp.
        BrandBlock(
            name = brandName,
            phase = phase,
            compactMode = compactMode,
            topPadding = 610.dp,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        // 6) الشعار النصّي: top = topInset + 722dp (13sp/700 muted).
        if (!compactMode) {
            Text(
                text = platformTagline,
                style = platformTaglineStyle(languageCode),
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(top = 722.dp, start = 24.dp, end = 24.dp)
                    .graphicsLayer {
                        val p = phase()
                        // 4.2..4.7s: تلاشى وصعود من +8.
                        alpha = ramp(p, 4.2f, 4.7f)
                        translationY = (1f - ramp(p, 4.2f, 4.7f)) * 8.dp.toPx()
                    },
            )
        }

        // 7) لودر 10 شرائح ذهبية: مثبت في الأسفل (bottom 56dp).
        PixelLoader(
            progress = phase,
            compactMode = compactMode,
            reduceMotion = reduceMotion,
            loadingText = loadingText,
            bottomPadding = bottomInset + 56.dp,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun SloganColumn(
    line1: String,
    line2: String,
    line3: String,
    phase: () -> Float,
    rtl: Boolean,
    languageCode: String,
    topPadding: Dp,
) {
    val isArabic = languageCode == "ar"
    val isRussian = languageCode == "ru"
    val isChinese = languageCode == "zh"
    val sizeSp: Float = when {
        isArabic || isChinese -> 38f
        isRussian -> 32f
        else -> 30f
    }
    val lineHeightSp: Float = when {
        isArabic || isRussian || isChinese -> 1.3f
        else -> 1.4f
    }
    val fontSize: TextUnit = sizeSp.sp
    val lineHeight: TextUnit = (sizeSp * lineHeightSp).sp
    val letterSpacing: TextUnit = if (isArabic || isRussian || isChinese) 0.sp else 2.sp
    val family = when {
        isArabic -> TajawalFont
        isRussian -> PixelifyCyrillicFont
        isChinese -> NotoSansScBlackFont
        else -> PixelFont
    }
    val fontWeight = when {
        isArabic -> FontWeight.ExtraBold
        isRussian -> FontWeight.Bold
        isChinese -> FontWeight.Black
        else -> FontWeight.Bold
    }
    // السطر 1 يدخل من البداية (القراءة)، السطر 2 من النهاية — في العربي نعكس.
    val line1FromX = if (rtl) 30f else -30f
    val line2FromX = if (rtl) -30f else 30f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = topPadding, start = 16.dp, end = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SloganLine(
            text = line1,
            color = SplashText,
            fontFamily = family,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            phase = phase,
            fromX = line1FromX,
            startAt = 1.2f,
            endAt = 1.65f,
            glow = false,
        )
        SloganLine(
            text = line2,
            color = SplashBlue,
            fontFamily = family,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            phase = phase,
            fromX = line2FromX,
            startAt = 1.9f,
            endAt = 2.35f,
            glow = false,
        )
        SloganLineWithSparkle(
            text = line3,
            color = SplashGold,
            fontFamily = family,
            fontWeight = fontWeight,
            fontSize = fontSize,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            phase = phase,
            languageCode = languageCode,
            rtl = rtl,
        )
    }
}

@Composable
private fun SloganLine(
    text: String,
    color: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    phase: () -> Float,
    fromX: Float,
    startAt: Float,
    endAt: Float,
    glow: Boolean,
) {
    Text(
        text = text,
        style = TextStyle(
            fontFamily = fontFamily,
            fontSize = fontSize,
            fontWeight = fontWeight,
            lineHeight = lineHeight,
            letterSpacing = letterSpacing,
            color = color,
            textAlign = TextAlign.Center,
        ),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .graphicsLayer {
                val p = phase()
                val enter = ramp(p, startAt, endAt)
                alpha = enter
                translationX = (1f - enter) * fromX.dp.toPx()
            },
    )
}

@Composable
private fun SloganLineWithSparkle(
    text: String,
    color: Color,
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    fontWeight: FontWeight,
    fontSize: TextUnit,
    lineHeight: TextUnit,
    letterSpacing: TextUnit,
    phase: () -> Float,
    languageCode: String,
    rtl: Boolean,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                val p = phase()
                // 2.6..3.5s: scale 0.85 → 1.04 → 1 + opacity، ثم تلاشٍ خفيف بعد 6.6s.
                val enter = ramp(p, 2.6f, 3.05f)
                val overshoot = if (p < 3.05f) {
                    lerp(0.85f, 1.04f, enter)
                } else {
                    val settle = ramp(p, 3.05f, 3.5f)
                    lerp(1.04f, 1f, settle)
                }
                scaleX = overshoot
                scaleY = overshoot
                alpha = ramp(p, 2.6f, 3.05f) * (1f - ramp(p, 6.6f, 7f))
            },
        contentAlignment = Alignment.Center,
    ) {
        val p = phase()
        // توهّج ذهبي: 0 → 22dp → 10dp بين 2.6..3.5s، ثم يستقر عند 10dp.
        val glowAlpha: Float = if (p < 3.5f) {
            val t = ramp(p, 2.6f, 3.5f)
            lerp(0f, 22f, t)
        } else {
            val t = ramp(p, 3.5f, 4f)
            lerp(22f, 10f, t)
        }
        val shadowColor = SplashGold.copy(alpha = if (glowAlpha > 0f) 0.9f else 0f)
        val goldGlow = if (glowAlpha > 0f) Shadow(color = shadowColor, blurRadius = glowAlpha) else null
        Text(
            text = text,
            style = TextStyle(
                fontFamily = fontFamily,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                color = color,
                textAlign = TextAlign.Center,
                shadow = goldGlow,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        // النجمة ✦: 30dp، offset (−10, −34) من نهاية السطر.
        // في العربية تبقى على النهاية (اليسار)؛ في الإنجليزي أيضاً النهاية = اليمين.
        Canvas(
            modifier = Modifier
                .align(if (rtl) Alignment.CenterStart else Alignment.CenterEnd)
                .offset(x = if (rtl) -34.dp else 34.dp, y = -10.dp)
                .size(30.dp)
                .graphicsLayer {
                    val pp = phase()
                    // 3.0..4.0s: scale 0→1.2→0.6, rotate 0→45→90, alpha 0→1→0
                    val t = ramp(pp, 3.0f, 4.0f)
                    if (t <= 0f) {
                        alpha = 0f
                    } else if (t < 0.5f) {
                        val k = t / 0.5f
                        scaleX = lerp(0f, 1.2f, k)
                        scaleY = lerp(0f, 1.2f, k)
                        rotationZ = lerp(0f, 45f, k)
                        alpha = k
                    } else {
                        val k = (t - 0.5f) / 0.5f
                        scaleX = lerp(1.2f, 0.6f, k)
                        scaleY = lerp(1.2f, 0.6f, k)
                        rotationZ = lerp(45f, 90f, k)
                        alpha = 1f - k
                    }
                },
        ) {
            drawFourPointSparkle(SplashGold)
        }
    }
}

@Composable
private fun BrandBlock(
    name: String,
    phase: () -> Float,
    compactMode: Boolean,
    topPadding: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier,
    ) {
    Box(
        modifier = Modifier
            .padding(top = topPadding)
            .graphicsLayer {
                val p = phase()
                if (compactMode) {
                    alpha = ramp(p, 0.15f, 0.45f)
                } else {
                    // 3.5..4.25s: scale 0.6 → 1.08 → 1 (overshoot)
                    val enter = ramp(p, 3.5f, 3.875f)
                    val overshoot = if (p < 3.875f) {
                        lerp(0.6f, 1.08f, enter)
                    } else {
                        val settle = ramp(p, 3.875f, 4.25f)
                        lerp(1.08f, 1f, settle)
                    }
                    scaleX = overshoot
                    scaleY = overshoot
                    alpha = ramp(p, 3.5f, 3.875f) * (1f - ramp(p, 6.6f, 7f))
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
        val iconShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = 24.dp,
                    shape = iconShape,
                    ambientColor = SplashGold.copy(alpha = 0.35f),
                    spotColor = SplashGold.copy(alpha = 0.35f),
                )
                .clip(iconShape)
                .background(SplashBackground)
                .border(2.dp, SplashGold, iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(iconShape),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = name,
            style = TextStyle(
                fontFamily = PixelFont,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
                color = SplashText,
                textAlign = TextAlign.Center,
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
    }
    }
}

@Composable
private fun PixelLoader(
    progress: () -> Float,
    compactMode: Boolean,
    reduceMotion: Boolean,
    loadingText: String,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val segments = 10
    val totalDuration = if (compactMode) 1.5f else 7f
    val phaseAt = progress()
    Box(modifier = modifier) {
    Box(
        modifier = Modifier
            .padding(bottom = bottomPadding)
            .semantics { contentDescription = loadingText },
    ) {
        for (i in 0 until segments) {
            val timeAt = if (compactMode) {
                (phaseAt * 1.5f) - i * 0.17f
            } else {
                phaseAt - (4.3f + 0.17f * i)
            }
            val (alpha, scaleFactor) = if (reduceMotion) {
                1f to 1.35f
            } else {
                val lit = timeAt.coerceIn(0f, 0.28f) / 0.28f
                val a = if (timeAt >= 0.28f) 1f else 0.18f + 0.82f * lit
                val s = 1f + 0.35f * (1f - kotlin.math.abs(lit - 0.5f) * 2f).coerceIn(0f, 1f)
                a to s
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .size(width = 22.dp, height = 10.dp)
                    .graphicsLayer {
                        this.alpha = alpha
                        this.scaleY = scaleFactor
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 0.5f)
                    }
                    .background(SplashGold, RoundedCornerShape(2.dp)),
            )
        }
    }
    }
}

// ── أدوات الرسم والنصوص ──────────────────────────────────────────────────────
private fun DrawScope.drawFourPointSparkle(color: Color) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val cy = h / 2f
    val path = Path().apply {
        moveTo(cx, 0f)
        lineTo(cx + cx * 0.28f, cy - cy * 0.28f)
        lineTo(w, cy)
        lineTo(cx + cx * 0.28f, cy + cy * 0.28f)
        lineTo(cx, h)
        lineTo(cx - cx * 0.28f, cy + cy * 0.28f)
        lineTo(0f, cy)
        lineTo(cx - cx * 0.28f, cy - cy * 0.28f)
        close()
    }
    drawPath(path = path, color = color)
}

private fun platformTaglineStyle(languageCode: String): TextStyle {
    val isArabic = languageCode == "ar"
    val family = if (isArabic) TajawalFont else androidx.compose.ui.text.font.FontFamily.Default
    return TextStyle(
        fontFamily = family,
        fontSize = 13.sp,
        fontWeight = FontWeight.Bold,
        color = SplashMuted,
        textAlign = TextAlign.Center,
    )
}

private fun ramp(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
