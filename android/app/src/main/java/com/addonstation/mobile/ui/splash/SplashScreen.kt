package com.addonstation.mobile.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.PixelFont
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.random.Random

/**
 * الإطلاق السينمائي: خلفية #020406، توهج، خطوط مسح، 7 جسيمات، شعار التطبيق (صورة)،
 * ثم الكلمات DREAM IT. (0.4s) · BUILD IT. (1.3s) · CREATE IT. (2.2s) والعلامة التجارية (3.0s)،
 * ثم خروج بتلاشي/انزلاق 0.5s مع كشف الشاشة الرئيسية.
 *
 * الأداء: الخطوط والجسيمات تُرسم في Canvas واحد لكل طبقة (بدل ~100 عنصر Compose)،
 * وكل الحركات داخل LaunchedEffect واحد بمهام متوازية — فلا «لاق» ولا تقطيع.
 * تُعرض مرة واحدة في كل تشغيل للتطبيق.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val alphaSky = remember { Animatable(0f) }
    val scaleSky = remember { Animatable(0.94f) }
    val logo = remember { Animatable(0f) }
    val line1 = remember { Animatable(0f) }
    val line2 = remember { Animatable(0f) }
    val line3 = remember { Animatable(0f) }
    val brand = remember { Animatable(0f) }
    val exit = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        coroutineScope {
            launch { alphaSky.animateTo(0.38f, tween(900, easing = FastOutSlowInEasing)) }
            launch { scaleSky.animateTo(1.05f, tween(900, easing = FastOutSlowInEasing)) }
            launch { logo.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            launch { delay(400); line1.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
            launch { delay(1300); line2.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
            launch { delay(2200); line3.animateTo(1f, tween(380, easing = FastOutSlowInEasing)) }
            launch { delay(3000); brand.animateTo(1f, tween(480, easing = FastOutSlowInEasing)) }
            launch { delay(3600); exit.animateTo(0f, tween(500, easing = FastOutSlowInEasing)) }
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020406))
            .graphicsLayer {
                // تُقرأ في مرحلة الرسم — بلا إعادة تركيب (recomposition) لكل إطار.
                alpha = exit.value
                translationY = (1f - exit.value) * -70f
            },
    ) {
        // الطبقة 1: سماء داكنة متدرجة
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = alphaSky.value
                    scaleX = scaleSky.value
                    scaleY = scaleSky.value
                }
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF10202F), Color(0xFF050A10), Color(0xFF020406)),
                    )
                ),
        )
        // الطبقة 2: خطوط مسح أفقية (Canvas واحد بدل 90 عنصراً)
        ScanLines()
        // الطبقة 3: جسيمات صاعدة (Canvas واحد)
        Particles(count = 7)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // شعار التطبيق (صورة) فوق أول كلمة
            Image(
                painter = painterResource(R.drawable.splash_logo),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .graphicsLayer {
                        alpha = logo.value
                        translationY = (1f - logo.value) * 16f
                    },
            )
            Spacer(Modifier.height(10.dp))
            Box(
                Modifier
                    .width(120.dp)
                    .height(2.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(AppColors.Blue.copy(alpha = 0.6f))
                    .graphicsLayer { alpha = logo.value },
            )
            Spacer(Modifier.height(34.dp))

            SplashLine(stringResource(R.string.splash_dream_it)) { line1.value }
            Spacer(Modifier.height(14.dp))
            SplashLine(stringResource(R.string.splash_build_it)) { line2.value }
            Spacer(Modifier.height(14.dp))
            SplashLine(stringResource(R.string.splash_create_it)) { line3.value }

            Spacer(Modifier.height(44.dp))
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .graphicsLayer {
                        alpha = brand.value
                        translationY = (1f - brand.value) * 18.dp.toPx()
                    },
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
private fun SplashLine(text: String, progress: () -> Float) {
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
            .graphicsLayer {
                val p = progress()
                alpha = p
                translationX = (1f - p) * -60.dp.toPx()
            },
    )
}

/** خطوط المسح: Canvas واحد يرسم ~46 خطاً شفافاً مع تحرّك بطيء. */
@Composable
private fun ScanLines() {
    val transition = rememberInfiniteTransition(label = "scan")
    val shift by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "scanShift",
    )
    Canvas(Modifier.fillMaxSize()) {
        val lines = 46
        val step = size.height / lines
        val offset = shift * step
        for (i in 0 until lines) {
            val y = i * step + offset
            if (y > size.height) continue
            val even = (i + (shift * lines).toInt()) % 3 == 0
            drawLine(
                color = Color.White.copy(alpha = if (even) 0.035f else 0.012f),
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = 1f,
                cap = StrokeCap.Butt,
            )
        }
    }
}

/** جسيمات صاعدة: Canvas واحد بدل عنصر لكل جسيم. */
@Composable
private fun Particles(count: Int) {
    val transition = rememberInfiniteTransition(label = "particles")
    val rise by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(9000, easing = LinearEasing), RepeatMode.Restart),
        label = "rise",
    )
    val seeds = remember {
        val random = Random(7)
        List(count) { random.nextFloat() to random.nextFloat() }
    }
    Canvas(Modifier.fillMaxSize()) {
        seeds.forEachIndexed { index, seed ->
            val progress = (rise + seed.second) % 1f
            val x = seed.first * size.width + progress * 24f
            val y = size.height - progress * (size.height * 0.92f)
            drawCircle(
                color = AppColors.Green.copy(alpha = 0.8f),
                radius = if (index % 2 == 0) 3f else 2f,
                center = Offset(x, y),
                alpha = 0.55f * (1f - progress),
            )
        }
    }
}
