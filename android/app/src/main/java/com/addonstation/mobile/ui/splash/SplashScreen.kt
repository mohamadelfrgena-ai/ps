package com.addonstation.mobile.ui.splash

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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont
import com.addonstation.mobile.ui.theme.displayStyle
import com.addonstation.mobile.util.AppPrefs
import java.util.Locale
import kotlinx.coroutines.delay

private const val FULL_TIMELINE_END = 0.9429f
private const val FULL_TIMELINE_MS = 6_600
private const val COMPACT_LOOP_MS = 1_500
private const val MIN_SPLASH_MS = 1_500L

private val SplashBackground = Color(0xFF0A0F1C)
private val SplashText = Color(0xFFEAF0FA)
private val SplashBlue = Color(0xFF6FA8FF)
private val SplashGold = Color(0xFFF0C25E)
private val SplashMuted = Color(0xFFC4D1E8)

/**
 * شاشة البداية المطابقة للجدول الزمني المرفق:
 * خلفية Minecraft تتلاشى وتبتعد، شعار Minecraft يهبط بارتداد، ثم العبارات
 * الثلاث، وبعدها شعار التطبيق والوصف وشريط تحميل من 10 شرائح.
 *
 * تعرض المقدمة الطويلة مرة واحدة فقط. في التشغيلات اللاحقة (أو أثناء انتظار
 * بيانات بطيئة بعد المقدمة) تستمر الحركة المختصرة: شعار Minecraft + أيقونة
 * التطبيق + شريط التحميل. يجري تحميل الجلسة والبيانات تحت الشاشة بالتوازي.
 * الخلفية السابقة `splash_bg_legacy.jpg` بقيت محفوظة ولم تُحذف.
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
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) <= 0f
        }.getOrDefault(false)
    }
    val startedAt = remember { SystemClock.elapsedRealtime() }
    val skipText = stringResource(R.string.splash_skip)
    val loadingText = stringResource(R.string.common_loading)
    val introProgress = remember(isFirstLaunch, reduceMotion) {
        Animatable(if (isFirstLaunch && reduceMotion) FULL_TIMELINE_END else 0f)
    }
    val compactProgress = remember { Animatable(0f) }
    var compactMode by remember(isFirstLaunch, reduceMotion) {
        mutableStateOf(!isFirstLaunch)
    }
    var introComplete by remember(isFirstLaunch, reduceMotion) {
        mutableStateOf(!isFirstLaunch || reduceMotion)
    }
    var skipRequested by remember { mutableStateOf(false) }

    // المقدمة الكاملة طولها 6.6 ثوانٍ؛ النقر يسرّع إلى إطارها الأخير.
    LaunchedEffect(isFirstLaunch, reduceMotion, skipRequested) {
        if (isFirstLaunch && !reduceMotion) {
            introProgress.animateTo(
                targetValue = FULL_TIMELINE_END,
                animationSpec = tween(
                    durationMillis = if (skipRequested) 180 else FULL_TIMELINE_MS,
                    easing = LinearEasing,
                ),
            )
            introComplete = true
            compactMode = true
        }
    }

    // حلقات قصيرة فقط عند الحاجة، وبلا حركة إذا فُعّل تقليل الحركة.
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

    // المقدمة الأولى تكتمل كاملةً (6.6 ثوانٍ) قبل الخروج؛ في التشغيلات
    // اللاحقة تبقى الحركة قصيرة (1.5 ثانية). وفي الحالتين ننتظر جاهزية الإقلاع.
    LaunchedEffect(startupReady, introComplete) {
        if (!startupReady || !introComplete) return@LaunchedEffect
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (MIN_SPLASH_MS - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        AppPrefs.markSplashCompleted(context)
        onFinished()
    }

    val timelinePhase: () -> Float = {
        if (reduceMotion) FULL_TIMELINE_END else introProgress.value
    }
    val compactPhase: () -> Float = {
        if (reduceMotion || skipRequested) 0.90f else compactProgress.value
    }
    val activePhase: () -> Float = if (compactMode) compactPhase else timelinePhase
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val arabic = appLocaleCode().equals("ar", ignoreCase = true)
    val brandName = stringResource(R.string.brand_wordmark).uppercase(Locale.ROOT)
    val platformTagline = stringResource(R.string.brand_platform)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val interactionSource = remember { MutableInteractionSource() }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SplashBackground)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
            ) { skipRequested = true }
            .semantics { contentDescription = skipText },
    ) {
        val screenHeight = maxHeight
        val shortScreen = screenHeight < 760.dp
        val sloganSize = if (shortScreen) 26.sp else 30.sp
        val sloganLineHeight = if (shortScreen) 36.sp else 42.sp
        val imageHeight = screenHeight * 0.71f

        // الخلفية والصبغة العمودية كما في النموذج: صورة في الأعلى، ثم تذوب
        // تدريجياً إلى #0A0F1C أسفل العبارات والعلامة التجارية.
        Image(
            painter = painterResource(R.drawable.splash_timeline_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier
                .fillMaxWidth()
                .height(imageHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val p = activePhase()
                    alpha = if (compactMode) ramp(p, 0f, 0.18f) else ramp(p, 0f, 0.10f)
                    val zoomOut = if (compactMode) ramp(p, 0f, 0.38f) else ramp(p, 0f, 0.5714f)
                    scaleX = if (compactMode) lerp(1.06f, 1f, zoomOut) else lerp(1.15f, 1f, zoomOut)
                    scaleY = scaleX
                },
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color(0x400A0F1C),
                            0.25f to Color(0x260A0F1C),
                            0.50f to Color(0xCC0A0F1C),
                            0.66f to SplashBackground,
                            1f to SplashBackground,
                        ),
                    ),
                ),
        )

        // شعار Minecraft — سقوط قصير مع ارتداد، ثم يبقى أثناء المقدمة.
        Image(
            painter = painterResource(R.drawable.minecraft_wordmark),
            contentDescription = null,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp)
                .offset(y = screenHeight * 0.10f)
                .fillMaxWidth(0.60f)
                .aspectRatio(480f / 95f)
                .graphicsLayer {
                    val p = activePhase()
                    if (compactMode) {
                        val enter = ramp(p, 0.08f, 0.34f)
                        alpha = enter * fadeOut(p, 0.94f, 1f)
                        translationY = (1f - enter) * -22.dp.toPx()
                    } else {
                        alpha = phaseWindow(p, 0.0429f, 0.1143f)
                        translationY = when {
                            p < 0.0429f -> -100.dp.toPx()
                            p < 0.1143f -> lerp(-100f, 10f, ramp(p, 0.0429f, 0.1143f)) * density
                            p < 0.15f -> lerp(10f, -4f, ramp(p, 0.1143f, 0.15f)) * density
                            p < 0.1786f -> lerp(-4f, 0f, ramp(p, 0.15f, 0.1786f)) * density
                            else -> 0f
                        }
                    }
                },
        )

        if (!compactMode) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .offset(y = screenHeight * 0.39f)
                    .padding(horizontal = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                SplashSloganLine(
                    text = stringResource(R.string.splash_dream_it),
                    color = SplashText,
                    phase = timelinePhase,
                    fromX = if (rtl) 30f else -30f,
                    arabic = arabic,
                    fontSize = sloganSize,
                    lineHeight = sloganLineHeight,
                )
                SplashSloganLine(
                    text = stringResource(R.string.splash_build_it),
                    color = SplashBlue,
                    phase = timelinePhase,
                    fromX = if (rtl) -30f else 30f,
                    arabic = arabic,
                    fontSize = sloganSize,
                    lineHeight = sloganLineHeight,
                )
                SplashSparkleLine(
                    text = stringResource(R.string.splash_create_it),
                    phase = timelinePhase,
                    rtl = rtl,
                    arabic = arabic,
                    fontSize = sloganSize,
                    lineHeight = sloganLineHeight,
                )
            }
        }

        SplashBrand(
            name = brandName,
            compactMode = compactMode,
            phase = activePhase,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = screenHeight * (if (compactMode) 0.43f else 0.72f)),
        )

        if (!compactMode) {
            Text(
                text = platformTagline,
                style = TextStyle(
                    fontFamily = if (arabic) TajawalFont else NunitoFont,
                    fontSize = if (shortScreen) 11.sp else 13.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 18.sp,
                    color = SplashMuted,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .offset(y = screenHeight * 0.855f)
                    .padding(horizontal = 18.dp)
                    .graphicsLayer {
                        val p = timelinePhase()
                        alpha = phaseWindow(p, 0.60f, 0.6714f)
                        translationY = (1f - ramp(p, 0.60f, 0.6714f)) * 8.dp.toPx()
                    },
            )
        }

        SplashLoadingBar(
            phase = activePhase,
            compactMode = compactMode,
            loadingDescription = loadingText,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 56.dp + bottomInset),
        )

        Text(
            text = skipText,
            style = TextStyle(
                fontFamily = if (arabic) TajawalFont else NunitoFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = SplashMuted.copy(alpha = 0.62f),
                textAlign = TextAlign.Center,
            ),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 18.dp + bottomInset)
                .graphicsLayer { alpha = if (compactMode) 1f else 0.72f },
        )
    }
}

@Composable
private fun SplashSloganLine(
    text: String,
    color: Color,
    phase: () -> Float,
    fromX: Float,
    arabic: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) {
    val style = splashSloganStyle(text, arabic, fontSize, lineHeight)
    val lineHeightDp = with(LocalDensity.current) { lineHeight.toDp() }
    Text(
        text = text,
        style = style.copy(color = color, textAlign = TextAlign.Center),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeightDp)
            .graphicsLayer {
                val p = phase()
                val enter = ramp(p, 0.1714f, 0.2357f)
                alpha = phaseWindow(p, 0.1714f, 0.2357f)
                translationX = (1f - enter) * fromX.dp.toPx()
            },
    )
}

@Composable
private fun SplashSparkleLine(
    text: String,
    phase: () -> Float,
    rtl: Boolean,
    arabic: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
) {
    val baseStyle = splashSloganStyle(text, arabic, fontSize, lineHeight)
    val density = LocalDensity.current
    val lineHeightDp = with(density) { lineHeight.toDp() }
    val glowRadius = with(density) { 11.dp.toPx() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeightDp)
            .graphicsLayer {
                val p = phase()
                val scale = when {
                    p < 0.3714f -> 0.85f
                    p < 0.4357f -> lerp(0.85f, 1.04f, ramp(p, 0.3714f, 0.4357f))
                    p < 0.50f -> lerp(1.04f, 1f, ramp(p, 0.4357f, 0.50f))
                    else -> 1f
                }
                scaleX = scale
                scaleY = scale
                alpha = phaseWindow(p, 0.3714f, 0.4357f)
            },
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            style = baseStyle.copy(
                color = SplashGold,
                textAlign = TextAlign.Center,
                shadow = Shadow(
                    color = SplashGold.copy(alpha = 0.36f),
                    blurRadius = glowRadius,
                ),
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        Canvas(
            Modifier
                .padding(start = 6.dp)
                .size(22.dp)
                .graphicsLayer {
                    val p = phase()
                    val inProgress = ramp(p, 0.4286f, 0.4857f)
                    val outProgress = ramp(p, 0.4857f, 0.5714f)
                    alpha = if (p < 0.4857f) inProgress else 1f - outProgress
                    val starScale = if (p < 0.4857f) {
                        lerp(0f, 1.2f, inProgress)
                    } else {
                        lerp(1.2f, 0.6f, outProgress)
                    }
                    scaleX = starScale
                    scaleY = starScale
                    rotationZ = if (p < 0.4857f) {
                        45f * inProgress
                    } else {
                        lerp(45f, 90f, outProgress)
                    }
                    translationY = -8.dp.toPx()
                },
        ) {
            drawFourPointSparkle(SplashGold)
        }
    }
}

@Composable
private fun SplashBrand(
    name: String,
    compactMode: Boolean,
    phase: () -> Float,
    modifier: Modifier = Modifier,
) {
    val appName = stringResource(R.string.app_name)
    Column(
        modifier = modifier
            .graphicsLayer {
                val p = phase()
                if (compactMode) {
                    val enter = ramp(p, 0.16f, 0.39f)
                    val scale = when {
                        p < 0.16f -> 0.60f
                        p < 0.39f -> lerp(0.60f, 1.08f, enter)
                        p < 0.50f -> lerp(1.08f, 1f, ramp(p, 0.39f, 0.50f))
                        else -> 1f
                    }
                    alpha = enter * fadeOut(p, 0.94f, 1f)
                    scaleX = scale
                    scaleY = scale
                } else {
                    val enter = ramp(p, 0.50f, 0.5714f)
                    val scale = when {
                        p < 0.50f -> 0.60f
                        p < 0.5714f -> lerp(0.60f, 1.08f, enter)
                        p < 0.6071f -> lerp(1.08f, 1f, ramp(p, 0.5714f, 0.6071f))
                        else -> 1f
                    }
                    alpha = phaseWindow(p, 0.50f, 0.5714f)
                    scaleX = scale
                    scaleY = scale
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val iconShape = RoundedCornerShape(18.dp)
        Box(
            modifier = Modifier
                .size(72.dp)
                .shadow(
                    elevation = 12.dp,
                    shape = iconShape,
                    ambientColor = SplashGold.copy(alpha = 0.22f),
                    spotColor = SplashGold.copy(alpha = 0.34f),
                )
                .clip(iconShape)
                .background(SplashBackground)
                .border(2.dp, SplashGold, iconShape),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = appName,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(2.dp)
                    .clip(iconShape),
            )
        }
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
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun SplashLoadingBar(
    phase: () -> Float,
    compactMode: Boolean,
    loadingDescription: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .semantics { contentDescription = loadingDescription }
            .graphicsLayer {
                val p = phase()
                alpha = if (compactMode) {
                    phaseWindow(p, 0.08f, 0.22f)
                } else {
                    phaseWindow(p, 0.5714f, 0.6143f)
                }
            },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) { index ->
            Box(
                modifier = Modifier
                    .width(22.dp)
                    .height(10.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(SplashGold)
                    .graphicsLayer {
                        val p = phase()
                        val start = if (compactMode) {
                            index / 10f
                        } else {
                            0.6143f + index * 0.0243f
                        }
                        val peak = if (compactMode) {
                            (index + 1) / 10f
                        } else {
                            start + 0.0143f
                        }
                        val end = if (compactMode) peak else peak + 0.0257f
                        val fill = ramp(p, start, peak)
                        alpha = if (p < start) 0.18f else lerp(0.18f, 1f, fill)
                        scaleY = if (p in start..peak) {
                            lerp(1f, 1.35f, fill)
                        } else if (p > peak && p < end) {
                            lerp(1.35f, 1f, ramp(p, peak, end))
                        } else {
                            1f
                        }
                    },
            )
        }
    }
}

@Composable
private fun splashSloganStyle(
    text: String,
    arabic: Boolean,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
): TextStyle = if (arabic) {
    TextStyle(
        fontFamily = TajawalFont,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = lineHeight,
    )
} else {
    displayStyle(
        text = text,
        sizeSp = fontSize.value,
        color = Color.Unspecified,
        latinLetterSpacingSp = 2f,
    ).copy(fontWeight = FontWeight.ExtraBold, lineHeight = lineHeight)
}

private fun DrawScope.drawFourPointSparkle(color: Color) {
    val centerX = size.width / 2f
    val centerY = size.height / 2f
    val path = Path().apply {
        moveTo(centerX, 0f)
        lineTo(centerX + centerX * 0.28f, centerY - centerY * 0.28f)
        lineTo(size.width, centerY)
        lineTo(centerX + centerX * 0.28f, centerY + centerY * 0.28f)
        lineTo(centerX, size.height)
        lineTo(centerX - centerX * 0.28f, centerY + centerY * 0.28f)
        lineTo(0f, centerY)
        lineTo(centerX - centerX * 0.28f, centerY - centerY * 0.28f)
        close()
    }
    drawPath(path = path, color = color)
}

private fun phaseWindow(value: Float, start: Float, fullAt: Float): Float =
    ramp(value, start, fullAt) * fadeOut(value, FULL_TIMELINE_END, 1f)

private fun ramp(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

private fun fadeOut(value: Float, start: Float, end: Float): Float =
    1f - ramp(value, start, end)

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
