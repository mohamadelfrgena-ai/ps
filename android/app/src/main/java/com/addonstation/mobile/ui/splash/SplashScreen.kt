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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
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
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
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
import com.addonstation.mobile.ui.theme.NotoSansScBlackFont
import com.addonstation.mobile.ui.theme.NotoSansScRegularFont
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.PixelifyCyrillicFont
import com.addonstation.mobile.ui.theme.TajawalFont
import com.addonstation.mobile.ui.theme.displayStyle
import com.addonstation.mobile.util.AppPrefs
import java.util.Locale
import kotlinx.coroutines.delay

private const val FULL_TIMELINE_END = 0.94f
private const val FULL_TIMELINE_MS = 5_200
private const val COMPACT_LOOP_MS = 1_500
private const val MIN_SPLASH_MS = 1_500L

private val SceneNight = Color(0xFF070B12)
private val SceneText = Color(0xFFF5F8FC)
private val SceneDiamond = Color(0xFF36D7E0)
private val SceneLava = Color(0xFFFFB12B)
private val SceneMuted = Color(0xFFC7D1DC)

/**
 * Cinematic launch screen built around the owner's vertical Minecraft artwork.
 * The former splash composition and its old background/logo stack were removed.
 * The retained brand idea is the sequential DREAM IT / BUILD IT / CREATE IT
 * reveal, now coloured from the cave scene: moonlight, diamond cyan, and lava.
 *
 * Startup work still runs behind this screen. The first launch plays the full
 * sequence; later launches use a short loop while readiness is pending. Tapping
 * after the first moment skips the animation, and reduced-motion users receive
 * the final static composition.
 */
@Composable
fun SplashScreen(
    loadingProgress: Float = 0f,
    startupReady: Boolean = true,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val isFirstLaunch = remember(context) { !AppPrefs.splashCompleted(context) }
    val compactMode = !isFirstLaunch
    val reduceMotion = remember(context) {
        runCatching {
            listOf(
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.WINDOW_ANIMATION_SCALE,
            ).any { key -> Settings.Global.getFloat(context.contentResolver, key, 1f) <= 0f }
        }.getOrDefault(false)
    }
    val startedAt = remember { SystemClock.elapsedRealtime() }
    val skipText = stringResource(R.string.splash_skip)
    val loadingText = stringResource(R.string.common_loading)
    val introProgress = remember(isFirstLaunch, reduceMotion) {
        Animatable(if (reduceMotion) 0.90f else 0f)
    }
    val compactProgress = remember { Animatable(0f) }
    val sceneProgress = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
    val exitProgress = remember { Animatable(0f) }
    var introComplete by remember(isFirstLaunch, reduceMotion) {
        mutableStateOf(!isFirstLaunch || reduceMotion)
    }
    var skipRequested by remember { mutableStateOf(false) }
    var skipEnabled by remember { mutableStateOf(false) }
    var completionHandled by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        delay(900L)
        skipEnabled = true
    }

    LaunchedEffect(isFirstLaunch, reduceMotion) {
        if (!reduceMotion) {
            sceneProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (isFirstLaunch) FULL_TIMELINE_MS else COMPACT_LOOP_MS,
                    easing = LinearEasing,
                ),
            )
        }
    }

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
        }
    }

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

    // Do not cover an unfinished bootstrap unless the user explicitly skips it.
    LaunchedEffect(introComplete, skipRequested, startupReady) {
        if (!introComplete || completionHandled || (!startupReady && !skipRequested)) return@LaunchedEffect
        if (compactMode && !reduceMotion && !skipRequested) {
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
                animationSpec = tween(if (isFirstLaunch) 420 else 260),
            )
        }
        AppPrefs.markSplashCompleted(context)
        onFinished()
    }

    val activePhase: () -> Float = if (compactMode) {
        { if (reduceMotion || skipRequested) 0.90f else compactProgress.value }
    } else {
        { if (reduceMotion) 0.90f else introProgress.value }
    }
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val languageCode = appLocaleCode().substringBefore('-').lowercase(Locale.ROOT)
    val brandName = stringResource(R.string.brand_wordmark).uppercase(Locale.ROOT)
    val platformTagline = stringResource(R.string.home_guest_card_title_plain)
    val appName = stringResource(R.string.app_name)
    val interactionSource = remember { MutableInteractionSource() }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SceneNight)
            .graphicsLayer {
                alpha = 1f - exitProgress.value
                val exitScale = lerp(1f, 1.025f, exitProgress.value)
                scaleX = exitScale
                scaleY = exitScale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = skipEnabled && !completionHandled,
                onClickLabel = skipText,
            ) { skipRequested = true }
            .semantics { contentDescription = skipText },
    ) {
        val shortScreen = maxHeight < 700.dp
        val veryShortScreen = maxHeight < 590.dp
        val sloganOffset = when {
            veryShortScreen -> maxHeight * 0.135f
            shortScreen -> maxHeight * 0.145f
            else -> maxHeight * 0.155f
        }
        val sloganSize = when (languageCode) {
            "ar", "zh" -> if (shortScreen) 28.sp else 32.sp
            "ru" -> if (shortScreen) 25.sp else 28.sp
            else -> if (shortScreen) 27.sp else 31.sp
        }
        val sloganLineHeight = when (languageCode) {
            "ar", "ru", "zh" -> sloganSize * 1.30f
            else -> sloganSize * 1.22f
        }

        Image(
            painter = painterResource(R.drawable.splash_cave_chase),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = ramp(sceneProgress.value, 0f, 0.16f)
                    val zoom = lerp(1.09f, 1f, sceneProgress.value)
                    scaleX = zoom
                    scaleY = zoom
                    translationY = lerp(18.dp.toPx(), 0f, sceneProgress.value)
                },
        )

        // A dark cinematic frame keeps text readable without hiding the artwork.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to SceneNight.copy(alpha = 0.88f),
                            0.20f to SceneNight.copy(alpha = 0.43f),
                            0.42f to SceneNight.copy(alpha = 0.08f),
                            0.70f to Color.Transparent,
                            0.86f to SceneNight.copy(alpha = 0.44f),
                            1f to SceneNight.copy(alpha = 0.94f),
                        ),
                    ),
                )
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            SceneNight.copy(alpha = 0.34f),
                            Color.Transparent,
                            Color.Transparent,
                            SceneNight.copy(alpha = 0.34f),
                        ),
                    ),
                ),
        )

        SplashBrandPill(
            name = brandName,
            appName = appName,
            compactMode = compactMode,
            phase = activePhase,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset + 14.dp),
        )

        SplashSlogans(
            languageCode = languageCode,
            rtl = rtl,
            compactMode = compactMode,
            phase = activePhase,
            fontSize = sloganSize,
            lineHeight = sloganLineHeight,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = sloganOffset)
                .padding(horizontal = 18.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp + bottomInset),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = platformTagline,
                style = TextStyle(
                    fontFamily = when (languageCode) {
                        "ar" -> TajawalFont
                        "zh" -> NotoSansScRegularFont
                        else -> NunitoFont
                    },
                    fontSize = if (shortScreen) 10.sp else 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SceneMuted,
                    textAlign = TextAlign.Center,
                    shadow = Shadow(SceneNight, blurRadius = 8f),
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = activePhase()
                        val start = if (compactMode) 0.35f else 0.54f
                        val end = if (compactMode) 0.50f else 0.64f
                        alpha = animatedVisibility(p, start, end, compactMode)
                        translationY = (1f - ramp(p, start, end)) * 10.dp.toPx()
                    },
            )
            Spacer(Modifier.height(12.dp))
            SplashLoadingBar(
                phase = activePhase,
                compactMode = compactMode,
                loadingProgress = loadingProgress,
                startupReady = startupReady,
                skipRequested = skipRequested,
                reduceMotion = reduceMotion,
                loadingDescription = loadingText,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = skipText,
                style = TextStyle(
                    fontFamily = if (languageCode == "ar") TajawalFont else NunitoFont,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (languageCode == "ar") 0.sp else 0.4.sp,
                    color = SceneMuted.copy(alpha = 0.72f),
                    textAlign = TextAlign.Center,
                    shadow = Shadow(SceneNight, blurRadius = 8f),
                ),
                modifier = Modifier.graphicsLayer {
                    alpha = if (skipEnabled && !completionHandled) 1f else 0f
                },
            )
        }
    }
}

@Composable
private fun SplashBrandPill(
    name: String,
    appName: String,
    compactMode: Boolean,
    phase: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .graphicsLayer {
                val p = phase()
                val start = if (compactMode) 0.04f else 0.03f
                val end = if (compactMode) 0.20f else 0.15f
                val enter = ramp(p, start, end)
                alpha = animatedVisibility(p, start, end, compactMode)
                translationY = (1f - enter) * -18.dp.toPx()
                scaleX = lerp(0.94f, 1f, enter)
                scaleY = scaleX
            }
            .clip(shape)
            .background(SceneNight.copy(alpha = 0.70f))
            .border(1.dp, SceneText.copy(alpha = 0.16f), shape)
            .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = appName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(13.dp))
                .border(1.dp, SceneLava.copy(alpha = 0.70f), RoundedCornerShape(13.dp)),
        )
        Text(
            text = name,
            style = TextStyle(
                fontFamily = PixelFont,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.6.sp,
                color = SceneText,
                shadow = Shadow(SceneNight, blurRadius = 8f),
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SplashSlogans(
    languageCode: String,
    rtl: Boolean,
    compactMode: Boolean,
    phase: () -> Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
    val dreamStart = if (compactMode) 0.12f else 0.16f
    val dreamEnd = if (compactMode) 0.27f else 0.28f
    val buildStart = if (compactMode) 0.24f else 0.29f
    val buildEnd = if (compactMode) 0.39f else 0.41f
    val createStart = if (compactMode) 0.36f else 0.42f
    val createEnd = if (compactMode) 0.53f else 0.56f

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        SplashSloganLine(
            text = stringResource(R.string.splash_dream_it),
            color = SceneText,
            languageCode = languageCode,
            phase = phase,
            compactMode = compactMode,
            fromX = if (rtl) 44f else -44f,
            fontSize = fontSize,
            lineHeight = lineHeight,
            startAt = dreamStart,
            endAt = dreamEnd,
        )
        SplashSloganLine(
            text = stringResource(R.string.splash_build_it),
            color = SceneDiamond,
            languageCode = languageCode,
            phase = phase,
            compactMode = compactMode,
            fromX = if (rtl) -44f else 44f,
            fontSize = fontSize,
            lineHeight = lineHeight,
            startAt = buildStart,
            endAt = buildEnd,
        )
        SplashSloganLine(
            text = stringResource(R.string.splash_create_it),
            color = SceneLava,
            languageCode = languageCode,
            phase = phase,
            compactMode = compactMode,
            fromX = 0f,
            fontSize = fontSize,
            lineHeight = lineHeight,
            startAt = createStart,
            endAt = createEnd,
            glow = true,
            sparkle = true,
        )
        Row(
            modifier = Modifier
                .padding(top = 7.dp)
                .width(126.dp)
                .height(3.dp)
                .graphicsLayer {
                    val p = phase()
                    val enter = ramp(p, createStart, createEnd)
                    alpha = animatedVisibility(p, createStart, createEnd, compactMode)
                    scaleX = enter
                },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(SceneDiamond))
            Box(Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(SceneLava))
        }
    }
}

@Composable
private fun SplashSloganLine(
    text: String,
    color: Color,
    languageCode: String,
    phase: () -> Float,
    compactMode: Boolean,
    fromX: Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    startAt: Float,
    endAt: Float,
    glow: Boolean = false,
    sparkle: Boolean = false,
) {
    val style = splashSloganStyle(text, languageCode, fontSize, lineHeight)
    val lineHeightDp = with(LocalDensity.current) { lineHeight.toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeightDp)
            .graphicsLayer {
                val p = phase()
                val enter = ramp(p, startAt, endAt)
                alpha = animatedVisibility(p, startAt, endAt, compactMode)
                translationX = (1f - enter) * fromX.dp.toPx()
                val scale = if (glow) lerp(0.72f, 1f, enter) else lerp(0.96f, 1f, enter)
                scaleX = scale
                scaleY = scale
            },
        contentAlignment = Alignment.Center,
    ) {
        if (glow) {
            Text(
                text = text,
                style = style.copy(
                    color = color.copy(alpha = 0.03f),
                    textAlign = TextAlign.Center,
                    shadow = Shadow(color.copy(alpha = 0.92f), blurRadius = 22f),
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = text,
            style = style.copy(
                color = color,
                textAlign = TextAlign.Center,
                shadow = Shadow(SceneNight.copy(alpha = 0.98f), blurRadius = 12f),
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
        if (sparkle) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = 88.dp, y = (-13).dp)
                    .size(22.dp)
                    .graphicsLayer {
                        val p = phase()
                        val enter = ramp(p, endAt - 0.03f, endAt + 0.06f)
                        val leave = ramp(p, endAt + 0.08f, endAt + 0.20f)
                        alpha = enter * (1f - leave)
                        scaleX = lerp(0f, 1.2f, enter)
                        scaleY = scaleX
                        rotationZ = lerp(0f, 55f, enter)
                    },
            ) { drawFourPointSparkle(SceneLava) }
        }
    }
}

@Composable
private fun SplashLoadingBar(
    phase: () -> Float,
    compactMode: Boolean,
    loadingProgress: Float,
    startupReady: Boolean,
    skipRequested: Boolean,
    reduceMotion: Boolean,
    loadingDescription: String,
) {
    val waitingPulse = remember { Animatable(0f) }
    LaunchedEffect(startupReady, skipRequested, reduceMotion) {
        if (!startupReady && !skipRequested && !reduceMotion) {
            while (true) {
                waitingPulse.snapTo(0f)
                waitingPulse.animateTo(1f, animationSpec = tween(900, easing = LinearEasing))
            }
        }
    }

    val safeProgress = loadingProgress.coerceIn(0f, 1f)
    val semanticProgress = when {
        startupReady || skipRequested -> 1f
        else -> safeProgress.coerceAtMost(0.80f)
    }
    val shape = RoundedCornerShape(7.dp)
    Row(
        modifier = Modifier
            .width(252.dp)
            .height(14.dp)
            .semantics {
                contentDescription = loadingDescription
                progressBarRangeInfo = ProgressBarRangeInfo(semanticProgress, 0f..1f)
            }
            .graphicsLayer {
                val p = phase()
                val start = if (compactMode) 0.08f else 0.55f
                val end = if (compactMode) 0.22f else 0.64f
                alpha = animatedVisibility(p, start, end, compactMode)
            }
            .clip(shape)
            .background(SceneNight.copy(alpha = 0.78f))
            .border(1.dp, SceneLava.copy(alpha = 0.42f), shape)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(10) { index ->
            val segmentColor = when {
                index < 3 -> SceneText
                index < 7 -> SceneDiamond
                else -> SceneLava
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(segmentColor)
                    .graphicsLayer {
                        val p = phase()
                        val timeMinimum = ramp(
                            p,
                            if (compactMode) 0.14f else 0.61f,
                            if (compactMode) 0.72f else 0.84f,
                        ) * 0.80f
                        val visibleProgress = when {
                            startupReady || skipRequested -> 1f
                            else -> maxOf(safeProgress, timeMinimum).coerceAtMost(0.80f)
                        }
                        val fill = (visibleProgress * 10f - index).coerceIn(0f, 1f)
                        val pulsePosition = (visibleProgress + waitingPulse.value * 0.20f).coerceIn(0f, 1f)
                        val distance = kotlin.math.abs(pulsePosition * 10f - (index + 0.5f))
                        val pulse = if (!startupReady && !skipRequested && !reduceMotion) {
                            (1f - distance).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                        alpha = maxOf(lerp(0.12f, 1f, fill), lerp(0.12f, 1f, pulse))
                        scaleY = lerp(0.78f, 1f, maxOf(fill, pulse))
                    },
            )
        }
    }
}

@Composable
private fun splashSloganStyle(
    text: String,
    languageCode: String,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
): TextStyle = when (languageCode) {
    "ar" -> TextStyle(
        fontFamily = TajawalFont,
        fontSize = fontSize,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = lineHeight,
    )
    "ru" -> TextStyle(
        fontFamily = PixelifyCyrillicFont,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        lineHeight = lineHeight,
    )
    "zh" -> TextStyle(
        fontFamily = NotoSansScBlackFont,
        fontSize = fontSize,
        fontWeight = FontWeight.Black,
        letterSpacing = 3.sp,
        lineHeight = lineHeight,
    )
    else -> displayStyle(
        text = text,
        sizeSp = fontSize.value,
        color = Color.Unspecified,
        latinLetterSpacingSp = 2f,
    ).copy(fontWeight = FontWeight.ExtraBold, lineHeight = lineHeight)
}

private fun animatedVisibility(value: Float, start: Float, fullAt: Float, compactMode: Boolean): Float =
    ramp(value, start, fullAt) * if (compactMode) fadeOut(value, 0.92f, 1f) else 1f

private fun DrawScope.drawFourPointSparkle(color: Color) {
    val cx = size.width / 2f
    val cy = size.height / 2f
    val path = Path().apply {
        moveTo(cx, 0f)
        lineTo(cx * 1.24f, cy * 0.76f)
        lineTo(size.width, cy)
        lineTo(cx * 1.24f, cy * 1.24f)
        lineTo(cx, size.height)
        lineTo(cx * 0.76f, cy * 1.24f)
        lineTo(0f, cy)
        lineTo(cx * 0.76f, cy * 0.76f)
        close()
    }
    drawPath(path, color)
}

private fun ramp(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

private fun fadeOut(value: Float, start: Float, end: Float): Float =
    1f - ramp(value, start, end)

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
