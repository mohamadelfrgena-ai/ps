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
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
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
 * الإطلاق السينمائي: صورة البداية (splash_bg — لقطة من إعلان Minecraft الرسمي)
 * مع تعتيم متدرّج، ثم خطوط مسح، 7 جسيمات، شعار التطبيق (صورة)،
 * ثم الكلمات DREAM IT. (0.4s) · BUILD IT. (1.3s) · CREATE IT. (2.2s) والعلامة التجارية (3.0s)،
 * ثم خروج بتلاشي/انزلاق 0.5s مع كشف الشاشة الرئيسية.
 *
 * الأداء: الخطوط والجسيمات تُرسم في Canvas واحد لكل طبقة (بدل ~100 عنصر Compose)،
 * وكل الحركات داخل LaunchedEffect واحد بمهام متوازية — فلا «لاق» ولا تقطيع.
 * تُعرض مرة واحدة في كل تشغيل للتطبيق.
 *
 * الخلفية: `drawable-{mdpi..xxhdpi}/splash_bg.jpg` — نسخة مضبوطة لكل كثافة
 * (360×780 · 540×1170 · 720×1560 · 1080×2340)، وهي **قصّ طولي من منتصف الصورة
 * الأصلية** بمقاس شاشة الهاتف تماماً (9:19.5) — طلب المالك: «صورة شاشة الترحيب
 * قصّها حتى تناسب مقاسات شاشة الهاتف».
 * نافذة القص 498×1080 من (711،0) — منتصف الأصل 1920×1080، وهي التي تضمّ
 * المجموعة كاملةً (الأريكة والطاولة · الشخصيات · البطل ذو القبعة يمين-وسط)
 * بلا تغبيش ولا خطّ فاصل ولا فراغ أسود؛ والارتفاع كامل (1080 من 1080) فلا
 * يُقتطع من اللقطة شيء.
 * تُولَّد بـ`tools/make_auth_backgrounds.py` من `design/splash/splash_bg_master.jpg`.
 *
 * ↩️ الصورة القديمة (قرية ماينكرافت عند الغروب) محفوظة كما هي في
 * `drawable-nodpi/splash_bg_legacy.jpg`: للإرجاع إليها بدّل السطر أدناه إلى
 * `painterResource(R.drawable.splash_bg_legacy)`.
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

    // **لمسة واحدة تتخطّى الحركة**: من لا يريد الانتظار ينقر الشاشة فتظهر
    // كل العناصر فوراً وتنتهي البداية (كان أمامه ٤ ثوانٍ إلزامية).
    var skipped by remember { mutableStateOf(false) }
    val interaction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    LaunchedEffect(Unit) {
        // الجدول أقصر من قبل (٣٦٠٠ms ← ٢٣٠٠ms) لأن المحتوى يُبنى **خلف**
        // شاشة الترحيب الآن، فلا داعي لانتظار طويل يشعر المستخدم بالثِقَل.
        coroutineScope {
            launch { alphaSky.animateTo(0.38f, tween(900, easing = FastOutSlowInEasing)) }
            launch { scaleSky.animateTo(1.05f, tween(900, easing = FastOutSlowInEasing)) }
            launch { logo.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
            // ملاحظة مهمّة: نمرّر **لامبدا** تقرأ الحالة لحظياً، لا قيمة منطقية
            // مجمّدة عند إطلاق الأثر (وإلا لن يرى الكوروتين نقرة المستخدم أبداً).
            launch { val s = waitOrSkip(250) { skipped }; line1.animateTo(1f, tween(lineMs(s, 380), easing = FastOutSlowInEasing)) }
            launch { val s = waitOrSkip(850) { skipped }; line2.animateTo(1f, tween(lineMs(s, 380), easing = FastOutSlowInEasing)) }
            launch { val s = waitOrSkip(1450) { skipped }; line3.animateTo(1f, tween(lineMs(s, 380), easing = FastOutSlowInEasing)) }
            launch { val s = waitOrSkip(1950) { skipped }; brand.animateTo(1f, tween(lineMs(s, 480), easing = FastOutSlowInEasing)) }
            launch { val s = waitOrSkip(2300) { skipped }; exit.animateTo(0f, tween(lineMs(s, 460), easing = FastOutSlowInEasing)) }
        }
        onFinished()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020406))
            .clickable(
                interactionSource = interaction,
                indication = null,
            ) { skipped = true }
            .graphicsLayer {
                // تُقرأ في مرحلة الرسم — بلا إعادة تركيب (recomposition) لكل إطار.
                alpha = exit.value
                translationY = (1f - exit.value) * -70f
            },
    ) {
        // الطبقة 1: صورة البداية (splash_bg) — تتلاشى وتكبر قليلاً. الصورة
        // مقصوصة مسبقاً إلى نسبة شاشة الهاتف (9:19.5) في ملفات كل كثافة، فيرى
        // المالك الكادر الطولي الصحيح بلا تغبيش ولا فراغ. و`Crop` هنا للتكيّف
        // مع الأجهزة التي تختلف نسبتها قليلاً (9:16 · 9:21) — يقتطع أطرافاً
        // صغيرة (سماء/عشب) لا تُخلّ بالمجموعة.
        Image(
            painter = painterResource(R.drawable.splash_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.Center,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = alphaSky.value
                    scaleX = scaleSky.value
                    scaleY = scaleSky.value
                },
        )
        // الطبقة 1ب: تعتيم متدرّج يحفظ وضوح الشعار والكلمات البيضاء فوق الصورة
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = alphaSky.value }
                .background(
                    Brush.verticalGradient(
                        0.00f to Color(0x8C000000),
                        0.26f to Color(0x45000000),
                        0.45f to Color(0x50000000),
                        0.65f to Color(0x5E000000),
                        0.85f to Color(0x6B000000),
                        1.00f to Color(0x86000000),
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
            Spacer(Modifier.height(16.dp))
            SplashLine(stringResource(R.string.splash_build_it)) { line2.value }
            Spacer(Modifier.height(16.dp))
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

        // تلميح «اضغط للتخطّي» — يظهر مع العلامة التجارية في أسفل الشاشة
        Text(
            text = stringResource(R.string.splash_skip),
            style = AppText.tiny,
            color = Color.White.copy(alpha = 0.5f),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 30.dp)
                .graphicsLayer { alpha = brand.value },
        )
    }
}

@Composable
private fun SplashLine(text: String, progress: () -> Float) {
    Text(
        text = text,
        // كلمات الشاشة **عريضة في كل اللغات**: خط البكسل للاتيني، وخط النظام العريض
        // للعربية والروسية والصينية — فلا تظهر رفيعة في لغة دون أخرى (انظر displayStyle).
        style = com.addonstation.mobile.ui.theme.displayStyle(
            text = text,
            sizeSp = 24f,
            color = Color.White,
            latinLetterSpacingSp = 2f,
        ).copy(
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

/** مدة الحركة: قصيرة إن تخطّى المستخدم الشاشة بالنقر. */
private fun lineMs(skipped: Boolean, normal: Int): Int = if (skipped) 120 else normal

/**
 * انتظار قابل للتخطي: ينتظر [ms] على دفعات صغيرة، فإن نقر المستخدم عاد فوراً
 * فتُستكمل بقية الحركات بلا انتظار. (الدفعات ٦٠ms: استجابة شبه فورية للنقر
 * بلا إبقاء الخيط مشغولاً.)
 *
 * @param skipped دالة تُقرأ في كل دفعة — لا قيمة مجمّدة — لأن أثر الإطلاق لا
 *                يُعاد تشغيله عند تغيّر الحالة.
 * @return هل تخطّى المستخدم الحركة؟
 */
private suspend fun waitOrSkip(ms: Long, skipped: () -> Boolean): Boolean {
    var left = ms
    while (left > 0) {
        if (skipped()) return true
        val slice = minOf(60L, left)
        delay(slice)
        left -= slice
    }
    return skipped()
}
