package com.addonstation.mobile.ui.components

import android.animation.ValueAnimator
import android.content.Context
import android.os.Build
import android.net.ConnectivityManager
import android.net.Network
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material.icons.outlined.LockClock
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.util.DevicePerformance
import kotlinx.coroutines.delay

/** Loading layouts shared by every data screen. */
enum class LoadingPattern {
    /** First page load: card-shaped placeholders preserve the final layout. */
    Skeleton,
    /** Small in-tab brand mark when there is no useful cached content. */
    CompactBrand,
    /** Full-screen session/app restoration loader. */
    FullBrand,
    /** A small pill while cached content remains visible. */
    Refreshing,
    /** End-of-list placeholders and pill. */
    More,
}

/** Error categories shown to people. Raw exception strings never enter this API. */
enum class ErrorKind {
    Network,
    Server,
    Timeout,
    SessionEnded,
    Unexpected,
}

/** Classifies failures without exposing their raw message to the UI. */
fun Throwable.toErrorKind(): ErrorKind {
    if (this is java.net.SocketTimeoutException || this is kotlinx.coroutines.TimeoutCancellationException) {
        return ErrorKind.Timeout
    }
    val api = this as? ApiError
    return when {
        api?.status == 401 || api?.status == 403 -> ErrorKind.SessionEnded
        (api?.status ?: 0) >= 500 -> ErrorKind.Server
        api?.kind == ApiErrorKind.NOT_SIGNED_IN || api?.kind == ApiErrorKind.INVALID_CREDENTIALS -> ErrorKind.SessionEnded
        api?.kind == ApiErrorKind.NETWORK || this is java.io.IOException -> ErrorKind.Network
        api?.kind == ApiErrorKind.SERVER -> ErrorKind.Server
        else -> ErrorKind.Unexpected
    }
}

/** Banner used when cached content stays on screen during refresh failures. */
enum class ContentBannerKind { Offline, BackOnline, ServerRefresh }

@Composable
private fun motionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context.applicationContext) {
        val deviceAllowsMotion = DevicePerformance.profile(context).allowDecorativeMotion
        deviceAllowsMotion && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching { ValueAnimator.areAnimatorsEnabled() }.getOrDefault(true)
        } else {
            true
        }
    }
}

/**
 * Shared loading state. It keeps headers/tabs outside this composable visible,
 * announces loading once, hides decorative skeleton blocks from accessibility,
 * and displays the slow-connection action after eight seconds.
 */
@Composable
fun LoadingState(
    modifier: Modifier = Modifier,
    pattern: LoadingPattern = LoadingPattern.Skeleton,
    onRetry: (() -> Unit)? = null,
    label: String? = null,
    skeletonRows: Int = 4,
) {
    val loadingLabel = label ?: stringResource(R.string.common_loading)
    var slow by remember(pattern) { mutableStateOf(false) }
    LaunchedEffect(pattern) {
        slow = false
        if (pattern == LoadingPattern.Skeleton || pattern == LoadingPattern.CompactBrand || pattern == LoadingPattern.FullBrand) {
            delay(8_000)
            slow = true
        }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = loadingLabel
                liveRegion = LiveRegionMode.Polite
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (slow && onRetry != null) {
            SlowLoadingBanner(onRetry = onRetry)
        }
        when (pattern) {
            LoadingPattern.Skeleton -> ListSkeleton(rows = skeletonRows)
            LoadingPattern.CompactBrand -> BrandedLoader(fullScreen = false, label = loadingLabel)
            LoadingPattern.FullBrand -> BrandedLoader(fullScreen = true, label = loadingLabel)
            LoadingPattern.Refreshing -> LoadingPill(text = stringResource(R.string.state_refreshing))
            LoadingPattern.More -> {
                ListSkeleton(rows = 2)
                LoadingPill(text = stringResource(R.string.state_loading_more))
            }
        }
    }
}

@Composable
private fun SlowLoadingBanner(onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Gold.copy(alpha = 0.16f))
            .border(1.dp, AppColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.state_loading_slow_title), style = AppText.bodySmall, color = AppColors.Gold)
            Text(stringResource(R.string.state_loading_slow_body), style = AppText.tiny, color = AppColors.Muted)
        }
        Text(
            stringResource(R.string.common_retry),
            style = AppText.button,
            color = AppColors.Blue,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onRetry)
                .padding(horizontal = 10.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun LoadingPill(text: String) {
    Row(
        modifier = Modifier
            .padding(10.dp)
            .clip(CircleShape)
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, CircleShape)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        CircularProgressIndicator(color = AppColors.Blue, strokeWidth = 2.dp, modifier = Modifier.size(15.dp))
        Text(text, style = AppText.tiny, color = AppColors.Muted)
    }
}

@Composable
private fun BrandedLoader(fullScreen: Boolean, label: String) {
    val animate = motionEnabled()
    val infinite = rememberInfiniteTransition(label = "brand-loader")
    val rotation by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 360f else 0f,
        animationSpec = infiniteRepeatable(tween(1_100, easing = LinearEasing)),
        label = "brand-loader-ring",
    )
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = if (animate) 10f else 10f,
        animationSpec = infiniteRepeatable(tween(1_900, easing = LinearEasing), RepeatMode.Restart),
        label = "brand-loader-segments",
    )
    Column(
        modifier = Modifier
            .then(if (fullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth().padding(vertical = 28.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(if (fullScreen) 96.dp else 72.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.matchParentSize().rotate(rotation)) {
                drawArc(
                    color = AppColors.Blue,
                    startAngle = 18f,
                    sweepAngle = 285f,
                    useCenter = false,
                    style = Stroke(width = 4.dp.toPx()),
                )
            }
            androidx.compose.foundation.Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                modifier = Modifier
                    .size(if (fullScreen) 66.dp else 48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(2.dp, AppColors.Gold, RoundedCornerShape(18.dp)),
            )
        }
        Spacer(Modifier.height(14.dp))
        if (fullScreen) {
            Text(
                "ADDONSTATION",
                style = TextStyle(fontFamily = PixelFont, fontSize = 21.sp, fontWeight = FontWeight.Black),
                color = AppColors.Text,
            )
            Spacer(Modifier.height(14.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), modifier = Modifier.clearAndSetSemantics { }) {
            repeat(10) { index ->
                val active = !animate || index <= phase.toInt().coerceIn(0, 9)
                Box(
                    Modifier
                        .width(13.dp)
                        .height(5.dp)
                        .clip(CircleShape)
                        .background(if (active) AppColors.Blue else AppColors.Border),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(label, style = AppText.bodySmall, color = AppColors.Muted)
    }
}

/** Card-shaped shimmer placeholders for list screens. */
@Composable
fun ListSkeleton(rows: Int = 4, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .clearAndSetSemantics { },
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(rows.coerceAtLeast(1)) { index ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(86.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppColors.Surface)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(18.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ShimmerBlock(Modifier.size(62.dp), RoundedCornerShape(14.dp), delayIndex = index)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                    ShimmerBlock(Modifier.fillMaxWidth(0.78f).height(13.dp), CircleShape, delayIndex = index)
                    ShimmerBlock(Modifier.fillMaxWidth(0.52f).height(9.dp), CircleShape, delayIndex = index + 1)
                    ShimmerBlock(Modifier.fillMaxWidth(0.34f).height(8.dp), CircleShape, delayIndex = index + 2)
                }
            }
        }
    }
}

@Composable
fun ShimmerBlock(
    modifier: Modifier,
    shape: androidx.compose.ui.graphics.Shape = RoundedCornerShape(12.dp),
    delayIndex: Int = 0,
) {
    val animate = motionEnabled()
    val transition = rememberInfiniteTransition(label = "skeleton-$delayIndex")
    val shift by transition.animateFloat(
        initialValue = -600f - delayIndex * 30f,
        targetValue = if (animate) 1_200f else -600f,
        animationSpec = infiniteRepeatable(tween(1_400, easing = LinearEasing)),
        label = "skeleton-shift-$delayIndex",
    )
    val base = AppColors.Text.copy(alpha = 0.09f)
    val highlight = AppColors.Text.copy(alpha = 0.17f)
    val brush = if (animate) {
        Brush.linearGradient(
            colors = listOf(base, highlight, base),
            start = Offset(shift - 260f, 0f),
            end = Offset(shift, 260f),
        )
    } else {
        Brush.linearGradient(listOf(base, base))
    }
    Box(modifier.clip(shape).background(brush).clearAndSetSemantics { })
}

private data class ErrorPresentation(
    val title: Int,
    val body: Int,
    val icon: ImageVector,
    val color: Color,
)

@Composable
private fun errorPresentation(kind: ErrorKind): ErrorPresentation = when (kind) {
    ErrorKind.Network -> ErrorPresentation(
        R.string.msg_network_error,
        R.string.state_network_body,
        Icons.Outlined.WifiOff,
        AppColors.Blue,
    )
    ErrorKind.Server -> ErrorPresentation(
        R.string.msg_server_error,
        R.string.state_server_body,
        Icons.Outlined.CloudOff,
        AppColors.Gold,
    )
    ErrorKind.Timeout -> ErrorPresentation(
        R.string.state_timeout_title,
        R.string.state_timeout_body,
        Icons.Outlined.HourglassEmpty,
        AppColors.Purple,
    )
    ErrorKind.SessionEnded -> ErrorPresentation(
        R.string.state_session_title,
        R.string.state_session_body,
        Icons.Outlined.LockClock,
        AppColors.Gold,
    )
    ErrorKind.Unexpected -> ErrorPresentation(
        R.string.msg_generic_error,
        R.string.state_unexpected_body,
        Icons.Outlined.ErrorOutline,
        Color(0xFFFF6FAE),
    )
}

/**
 * Shared full error page. [legacyText] exists only for source compatibility and
 * is intentionally never rendered, which prevents raw exception disclosure.
 */
@Composable
fun ErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ErrorKind = ErrorKind.Network,
    retrying: Boolean = false,
    technicalCode: Int? = null,
    onBackHome: (() -> Unit)? = null,
    onSignIn: (() -> Unit)? = null,
    onContinueGuest: (() -> Unit)? = null,
    onContactSupport: (() -> Unit)? = null,
    @Suppress("UNUSED_PARAMETER") text: String? = null,
) {
    val presentation = errorPresentation(kind)
    val clipboard = LocalClipboardManager.current
    var countdown by remember(kind) { mutableIntStateOf(if (kind == ErrorKind.Network) 5 else 0) }
    var autoRetried by remember(kind) { mutableStateOf(false) }

    LaunchedEffect(kind, retrying) {
        if (kind == ErrorKind.Network && !retrying && !autoRetried) {
            countdown = 5
            while (countdown > 0) {
                delay(1_000)
                countdown -= 1
            }
            autoRetried = true
            onRetry()
        }
    }
    RetryWhenConnectionReturns(enabled = kind == ErrorKind.Network && !retrying, onRetry = onRetry)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(PaddingValues(horizontal = 24.dp, vertical = 20.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        ErrorIllustration(icon = presentation.icon, color = presentation.color)
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(presentation.title),
            style = TextStyle(fontFamily = PixelFont, fontSize = 22.sp, fontWeight = FontWeight.Black),
            color = AppColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(9.dp))
        Text(
            stringResource(presentation.body),
            style = AppText.bodySmall,
            color = AppColors.Muted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
        if (kind == ErrorKind.Network && !autoRetried && countdown > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.state_auto_retry, countdown),
                style = AppText.tiny,
                color = presentation.color,
            )
        }
        technicalCode?.let { code ->
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.state_code, code),
                style = AppText.tiny,
                color = AppColors.Muted,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.Elevated)
                    .border(1.dp, AppColors.Border, CircleShape)
                    .clickable { clipboard.setText(AnnotatedString(code.toString())) }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            )
        }
        Spacer(Modifier.height(20.dp))
        StatePrimaryButton(
            text = stringResource(if (kind == ErrorKind.SessionEnded) R.string.auth_sign_in else R.string.common_retry),
            color = presentation.color,
            loading = retrying,
            onClick = if (kind == ErrorKind.SessionEnded) onSignIn ?: onRetry else onRetry,
        )
        val secondaryText = when {
            kind == ErrorKind.SessionEnded && onContinueGuest != null -> R.string.state_continue_guest
            (kind == ErrorKind.Server || kind == ErrorKind.Timeout) && onBackHome != null -> R.string.state_back_home
            kind == ErrorKind.Unexpected && onContactSupport != null -> R.string.state_contact_support
            else -> null
        }
        val secondaryAction = when {
            kind == ErrorKind.SessionEnded -> onContinueGuest
            kind == ErrorKind.Server || kind == ErrorKind.Timeout -> onBackHome
            kind == ErrorKind.Unexpected -> onContactSupport
            else -> null
        }
        if (secondaryText != null && secondaryAction != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(secondaryText),
                style = AppText.button,
                color = AppColors.Muted,
                modifier = Modifier
                    .widthIn(max = 290.dp)
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .clickable(onClick = secondaryAction)
                    .padding(top = 14.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun ErrorIllustration(icon: ImageVector, color: Color) {
    Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .size(162.dp)
                .drawBehind {
                    drawCircle(
                        color = color.copy(alpha = 0.42f),
                        style = Stroke(
                            width = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(), 8.dp.toPx())),
                        ),
                    )
                },
        )
        Box(Modifier.size(142.dp).clip(CircleShape).background(color.copy(alpha = 0.08f)))
        Box(
            Modifier
                .size(104.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(color.copy(alpha = 0.18f))
                .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(52.dp))
        }
        listOf(
            Alignment.TopStart to 22.dp,
            Alignment.TopEnd to 12.dp,
            Alignment.BottomStart to 8.dp,
            Alignment.BottomEnd to 20.dp,
        ).forEachIndexed { index, (alignment, inset) ->
            Box(
                Modifier
                    .align(alignment)
                    .padding(inset)
                    .size(if (index % 2 == 0) 7.dp else 5.dp)
                    .background(color.copy(alpha = 0.75f)),
            )
        }
    }
}

@Composable
private fun StatePrimaryButton(text: String, color: Color, loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .widthIn(max = 290.dp)
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(color)
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            Text(text, style = AppText.button, color = Color.White)
        }
    }
}

@Composable
private fun RetryWhenConnectionReturns(enabled: Boolean, onRetry: () -> Unit) {
    val context = LocalContext.current
    DisposableEffect(enabled, context) {
        if (!enabled) return@DisposableEffect onDispose { }
        val manager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return@DisposableEffect onDispose { }
        val startedOffline = manager.activeNetwork == null
        var fired = false
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (startedOffline && !fired) {
                    fired = true
                    onRetry()
                }
            }
        }
        runCatching { manager.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager.unregisterNetworkCallback(callback) } }
    }
}

/** Compact banner shown above cached content instead of replacing that content. */
@Composable
fun ContentStatusBanner(
    kind: ContentBannerKind,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val color = when (kind) {
        ContentBannerKind.Offline -> AppColors.Gold
        ContentBannerKind.BackOnline -> AppColors.Green
        ContentBannerKind.ServerRefresh -> AppColors.Red
    }
    val title = when (kind) {
        ContentBannerKind.Offline -> stringResource(R.string.state_offline_title)
        ContentBannerKind.BackOnline -> stringResource(R.string.state_back_online_title)
        ContentBannerKind.ServerRefresh -> stringResource(R.string.msg_server_error)
    }
    val body = when (kind) {
        ContentBannerKind.Offline -> stringResource(R.string.state_saved_content)
        ContentBannerKind.BackOnline -> stringResource(R.string.state_content_updated)
        ContentBannerKind.ServerRefresh -> stringResource(R.string.state_refresh_failed_saved)
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.38f), RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(color))
        Text("$title. $body", style = AppText.tiny, color = AppColors.Text, modifier = Modifier.weight(1f))
        if (onRetry != null && kind != ContentBannerKind.BackOnline) {
            Text(
                stringResource(R.string.common_retry),
                style = AppText.button,
                color = color,
                modifier = Modifier.clickable(onClick = onRetry).padding(5.dp),
            )
        }
    }
}
