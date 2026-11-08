package com.addonstation.mobile.ui.splash

import android.os.SystemClock
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.Stroke
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
import com.addonstation.mobile.util.DevicePerformance
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay

private const val FIRST_ENTRANCE_MS = 1_250
private const val RETURN_ENTRANCE_MS = 780
private const val MIN_FIRST_SPLASH_MS = 1_350L
private const val MIN_RETURN_SPLASH_MS = 900L

private val SceneNight = Color(0xFF070B12)
private val SceneText = Color(0xFFF5F8FC)
private val SceneDiamond = Color(0xFF36D7E0)
private val SceneLava = Color(0xFFFFB12B)
private val SceneMuted = Color(0xFFC7D1DC)

/**
 * A low-overhead cinematic splash built around the owner's Minecraft artwork.
 *
 * One bounded entrance animation replaces the previous perpetual loop. The
 * three translated slogans remain together at the exact visual centre, while
 * the determinate loading bar follows only real AppRoot bootstrap milestones.
 * Heavy duplicate text glows, sparkles, fake time-based fill, and background
 * zoom are intentionally avoided for smooth rendering on low-end devices.
 */
@Composable
fun SplashScreen(
    loadingProgress: Float = 0f,
    startupReady: Boolean = true,
    onFinished: () -> Unit,
) {
    val context = LocalContext.current
    val isFirstLaunch = remember(context) { !AppPrefs.splashCompleted(context) }
    val reduceMotion = remember(context.applicationContext) {
        !DevicePerformance.profile(context).allowDecorativeMotion || runCatching {
            listOf(
                Settings.Global.ANIMATOR_DURATION_SCALE,
                Settings.Global.TRANSITION_ANIMATION_SCALE,
                Settings.Global.WINDOW_ANIMATION_SCALE,
            ).any { key -> Settings.Global.getFloat(context.contentResolver, key, 1f) <= 0f }
        }.getOrDefault(false)
    }
    val startedAt = remember { SystemClock.elapsedRealtime() }
    val loadingText = stringResource(R.string.common_loading)
    val entrance = remember(reduceMotion) { Animatable(if (reduceMotion) 1f else 0f) }
    val exitProgress = remember { Animatable(0f) }
    var entranceComplete by remember(reduceMotion) { mutableStateOf(reduceMotion) }
    var completionHandled by remember { mutableStateOf(false) }

    LaunchedEffect(isFirstLaunch, reduceMotion) {
        if (reduceMotion) {
            entrance.snapTo(1f)
        } else {
            entrance.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = if (isFirstLaunch) FIRST_ENTRANCE_MS else RETURN_ENTRANCE_MS,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        entranceComplete = true
    }

    // The splash always finishes its bounded entrance and real startup work;
    // there is deliberately no user-controlled bypass path.
    LaunchedEffect(entranceComplete, startupReady) {
        if (!entranceComplete || !startupReady || completionHandled) return@LaunchedEffect
        val minimum = if (isFirstLaunch) MIN_FIRST_SPLASH_MS else MIN_RETURN_SPLASH_MS
        val elapsed = SystemClock.elapsedRealtime() - startedAt
        val remaining = (minimum - elapsed).coerceAtLeast(0L)
        if (remaining > 0L) delay(remaining)
        completionHandled = true
        if (reduceMotion) {
            exitProgress.snapTo(1f)
        } else {
            exitProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(240, easing = FastOutSlowInEasing),
            )
        }
        AppPrefs.markSplashCompleted(context)
        onFinished()
    }

    val phase: () -> Float = { entrance.value }
    val languageCode = appLocaleCode().substringBefore('-').lowercase(Locale.ROOT)
    val brandName = stringResource(R.string.brand_wordmark).uppercase(Locale.ROOT)
    val platformTagline = stringResource(R.string.home_guest_card_title_plain)
    val appName = stringResource(R.string.app_name)
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(SceneNight)
            .graphicsLayer {
                alpha = 1f - exitProgress.value
                val scale = lerp(1f, 1.012f, exitProgress.value)
                scaleX = scale
                scaleY = scale
            },
    ) {
        val shortScreen = maxHeight < 700.dp
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
            modifier = Modifier.fillMaxSize(),
        )

        // Static scrims keep every language readable without per-frame blur work.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to SceneNight.copy(alpha = 0.86f),
                            0.22f to SceneNight.copy(alpha = 0.38f),
                            0.43f to SceneNight.copy(alpha = 0.12f),
                            0.68f to SceneNight.copy(alpha = 0.12f),
                            0.86f to SceneNight.copy(alpha = 0.48f),
                            1f to SceneNight.copy(alpha = 0.94f),
                        ),
                    ),
                )
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            SceneNight.copy(alpha = 0.30f),
                            Color.Transparent,
                            Color.Transparent,
                            SceneNight.copy(alpha = 0.30f),
                        ),
                    ),
                ),
        )

        SplashBrandPill(
            name = brandName,
            appName = appName,
            phase = phase,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = topInset + 14.dp),
        )

        SplashSlogans(
            languageCode = languageCode,
            phase = phase,
            fontSize = sloganSize,
            lineHeight = sloganLineHeight,
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .padding(horizontal = 18.dp),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 14.dp + bottomInset)
                .graphicsLayer {
                    val reveal = ramp(phase(), 0.48f, 0.68f)
                    alpha = reveal
                    translationY = (1f - reveal) * 8.dp.toPx()
                },
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
                    shadow = Shadow(SceneNight, blurRadius = 4f),
                ),
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            SplashLoadingBar(
                loadingProgress = loadingProgress,
                startupReady = startupReady,
                reduceMotion = reduceMotion,
                loadingDescription = loadingText,
            )
        }
    }
}

@Composable
private fun SplashBrandPill(
    name: String,
    appName: String,
    phase: () -> Float,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    Row(
        modifier = modifier
            .graphicsLayer {
                val reveal = ramp(phase(), 0.02f, 0.18f)
                alpha = reveal
                translationY = (1f - reveal) * -12.dp.toPx()
            }
            .clip(shape)
            .background(SceneNight.copy(alpha = 0.72f))
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
    phase: () -> Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    modifier: Modifier = Modifier,
) {
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
            fontSize = fontSize,
            lineHeight = lineHeight,
            startAt = 0.10f,
            endAt = 0.30f,
        )
        SplashSloganLine(
            text = stringResource(R.string.splash_build_it),
            color = SceneDiamond,
            languageCode = languageCode,
            phase = phase,
            fontSize = fontSize,
            lineHeight = lineHeight,
            startAt = 0.24f,
            endAt = 0.44f,
        )
        SplashSloganLine(
            text = stringResource(R.string.splash_create_it),
            color = SceneLava,
            languageCode = languageCode,
            phase = phase,
            fontSize = fontSize,
            lineHeight = lineHeight,
            startAt = 0.38f,
            endAt = 0.58f,
        )
        Row(
            modifier = Modifier
                .padding(top = 7.dp)
                .width(126.dp)
                .height(3.dp)
                .graphicsLayer {
                    val reveal = ramp(phase(), 0.48f, 0.66f)
                    alpha = reveal
                    scaleX = reveal
                },
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(SceneDiamond))
            Box(Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(2.dp)).background(SceneLava))
        }
    }
}

@Composable
private fun SplashSloganLine(
    text: String,
    color: Color,
    languageCode: String,
    phase: () -> Float,
    fontSize: androidx.compose.ui.unit.TextUnit,
    lineHeight: androidx.compose.ui.unit.TextUnit,
    startAt: Float,
    endAt: Float,
) {
    val style = splashSloganStyle(text, languageCode, fontSize, lineHeight)
    val lineHeightDp = with(LocalDensity.current) { lineHeight.toDp() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(lineHeightDp + 8.dp)
            .graphicsLayer {
                val reveal = ramp(phase(), startAt, endAt)
                alpha = reveal
                translationY = (1f - reveal) * 10.dp.toPx()
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = style.copy(
                color = color,
                textAlign = TextAlign.Center,
                shadow = Shadow(SceneNight.copy(alpha = 0.96f), blurRadius = 5f),
            ),
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SplashLoadingBar(
    loadingProgress: Float,
    startupReady: Boolean,
    reduceMotion: Boolean,
    loadingDescription: String,
) {
    val targetProgress = if (startupReady) 1f else loadingProgress.coerceIn(0f, 1f)
    val displayedProgress = remember { Animatable(targetProgress) }
    LaunchedEffect(targetProgress, reduceMotion) {
        if (reduceMotion || targetProgress < displayedProgress.value) {
            displayedProgress.snapTo(targetProgress)
        } else {
            val distance = (targetProgress - displayedProgress.value).coerceAtLeast(0f)
            displayedProgress.animateTo(
                targetValue = targetProgress,
                animationSpec = tween(
                    durationMillis = (140 + distance * 360).roundToInt(),
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    val progress = displayedProgress.value.coerceIn(0f, 1f)
    val percent = (progress * 100f).roundToInt()
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val fillBrush = remember {
        Brush.horizontalGradient(listOf(SceneText, SceneDiamond, SceneLava))
    }

    Column(
        modifier = Modifier.width(252.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = loadingDescription,
                color = SceneMuted,
                fontFamily = NunitoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Text(
                text = "$percent%",
                color = SceneText,
                fontFamily = NunitoFont,
                fontSize = 10.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .semantics {
                    contentDescription = loadingDescription
                    progressBarRangeInfo = ProgressBarRangeInfo(progress, 0f..1f)
                },
        ) {
            val radius = CornerRadius(6.dp.toPx(), 6.dp.toPx())
            val inset = 3.dp.toPx()
            val innerHeight = (size.height - inset * 2f).coerceAtLeast(0f)
            val innerAvailable = (size.width - inset * 2f).coerceAtLeast(0f)
            val fillWidth = innerAvailable * progress
            val fillX = if (rtl) size.width - inset - fillWidth else inset

            drawRoundRect(
                color = SceneNight.copy(alpha = 0.82f),
                cornerRadius = radius,
            )
            if (fillWidth > 0f && innerHeight > 0f) {
                drawRoundRect(
                    brush = fillBrush,
                    topLeft = Offset(fillX, inset),
                    size = Size(fillWidth, innerHeight),
                    cornerRadius = CornerRadius(3.dp.toPx(), 3.dp.toPx()),
                )
            }
            drawRoundRect(
                color = SceneText.copy(alpha = 0.24f),
                cornerRadius = radius,
                style = Stroke(width = 1.dp.toPx()),
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

private fun ramp(value: Float, start: Float, end: Float): Float {
    if (end <= start) return if (value >= end) 1f else 0f
    return ((value - start) / (end - start)).coerceIn(0f, 1f)
}

private fun lerp(start: Float, end: Float, progress: Float): Float =
    start + (end - start) * progress
