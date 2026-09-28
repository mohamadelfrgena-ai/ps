package com.addonstation.mobile.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.PixelFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * الإطلاق السينمائي: خلفية #020406، توهج، خطوط مسح، 8 جسيمات، شعار في الأعلى،
 * ثم الكلمات DREAM IT. (0.55s) · BUILD IT. (1.82s) · CREATE IT. (3.0s) والعلامة التجارية (4.12s)،
 * ثم خروج بتلاشي/انزلاق 0.8s مع كشف الشاشة الرئيسية.
 * تُعرض مرة واحدة في كل تشغيل للتطبيق.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alphaSky = remember { Animatable(0f) }
    val scaleSky = remember { Animatable(0.92f) }
    val line1 = remember { Animatable(0f) }
    val line2 = remember { Animatable(0f) }
    val line3 = remember { Animatable(0f) }
    val brand = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        scope.launch { alphaSky.animateTo(0.38f, tween(2700)) }
        scope.launch { scaleSky.animateTo(1.06f, tween(2700)) }
        delay(550)
        scope.launch { line1.animateTo(1f, tween(450)) }
        delay(1270)
        scope.launch { line2.animateTo(1f, tween(450)) }
        delay(1180)
        scope.launch { line3.animateTo(1f, tween(450)) }
        delay(1120)
        scope.launch { brand.animateTo(1f, tween(600)) }
        delay(820)
        exit.animateTo(0f, tween(800))
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020406))
            .graphicsLayer {
                alpha = exit.value
                translationY = (1f - exit.value) * -80f
            },
    ) {
        // الطبقة 1: سماء داكنة متدرجة
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(alphaSky.value)
                .scale(scaleSky.value)
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF10202F), Color(0xFF050A10), Color(0xFF020406)),
                    )
                ),
        )
        // الطبقة 2: خطوط مسح أفقية باهتة
        ScanLines()
        // 8 جسيمات صغيرة
        Particles(count = 8)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = stringResource(R.string.app_name),
                style = AppText.pageTitle.copy(fontSize = 30.sp),
                color = Color.White,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                Modifier
                    .width(120.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AppColors.Blue.copy(alpha = 0.6f)),
            )
            Spacer(Modifier.height(40.dp))

            SplashLine(stringResource(R.string.splash_dream_it), line1.value)
            Spacer(Modifier.height(14.dp))
            SplashLine(stringResource(R.string.splash_build_it), line2.value)
            Spacer(Modifier.height(14.dp))
            SplashLine(stringResource(R.string.splash_create_it), line3.value)

            Spacer(Modifier.height(46.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .offset(y = (1f - brand.value) * 20f)
                    .alpha(brand.value),
            ) {
                Text(
                    text = stringResource(R.string.brand_wordmark),
                    style = AppText.brand.copy(fontSize = 22.sp),
                    color = Color.White,
                )
                Box(
                    Modifier
                        .padding(top = 4.dp)
                        .width(180.dp)
                        .height(2.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Transparent, Color(0xFF0070CC), Color.Transparent),
                            )
                        ),
                )
            }
        }
    }
}

@Composable
private fun SplashLine(text: String, progress: Float) {
    Text(
        text = text,
        style = androidx.compose.ui.text.TextStyle(
            fontFamily = PixelFont,
            fontSize = 20.sp,
            color = Color.White,
            textAlign = TextAlign.Center,
            shadow = androidx.compose.ui.graphics.Shadow(
                color = Color.Black,
                offset = androidx.compose.ui.geometry.Offset(2f, 2f),
                blurRadius = 6f,
            ),
        ),
        modifier = Modifier
            .offset(x = (1f - progress) * -60f)
            .alpha(progress),
    )
}

@Composable
private fun ScanLines() {
    val transition = rememberInfiniteTransition(label = "scan")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(6000, easing = LinearEasing), RepeatMode.Restart),
        label = "scanShift",
    )
    Column(Modifier.fillMaxSize()) {
        repeat(90) { index ->
            Box(
                Modifier
                    .fillMaxSize()
                    .weight(1f)
                    .alpha(if ((index + (shift * 90).toInt()) % 3 == 0) 0.035f else 0.012f)
                    .background(Color.White),
            )
        }
    }
}

@Composable
private fun Particles(count: Int) {
    val transition = rememberInfiniteTransition(label = "particles")
    val rise by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "rise",
    )
    val random = remember { Random(7) }
    val seeds = remember { List(count) { random.nextFloat() to random.nextFloat() } }
    Box(Modifier.fillMaxSize()) {
        seeds.forEachIndexed { index, (x, offset) ->
            val progress = (rise + offset) % 1f
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(
                        x = (x * 320).dp + (progress * 24).dp,
                        y = -(progress * 620).dp,
                    )
                    .size(if (index % 2 == 0) 3.dp else 2.dp)
                    .alpha(0.55f * (1f - progress))
                    .clip(RoundedCornerShape(2.dp))
                    .background(AppColors.Green.copy(alpha = 0.8f)),
            )
        }
    }
}
