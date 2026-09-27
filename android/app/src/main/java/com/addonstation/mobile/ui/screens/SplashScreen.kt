package com.addonstation.mobile.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SplashScreen(onDone: () -> Unit) {
    val sky = remember { Animatable(0f) }
    val logo = remember { Animatable(0f) }
    val text = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        kotlinx.coroutines.coroutineScope {
            launch { delay(150); sky.animateTo(1f, tween(2700, easing = CubicBezierEasing(.2f, .7f, .2f, 1f))) }
            launch { delay(300); logo.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            launch { delay(900); text.animateTo(1f, tween(900)) }
        }
        delay(900)
        onDone()
    }
    val inf = rememberInfiniteTransition(label = "p")
    val t by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "t")

    Box(Modifier.fillMaxSize().background(C.bg)) {
        Image(
            painterResource(R.drawable.splash_sky), null, Modifier.fillMaxSize().alpha(sky.value * .38f).scale(1.08f - .08f * sky.value),
            contentScale = ContentScale.Crop,
        )
        // Floating blue pixel particles
        Canvas(Modifier.fillMaxSize()) {
            val pts = listOf(.68f to .13f, .95f to .42f, .11f to .5f, .92f to .6f, .28f to .65f, .51f to .75f)
            pts.forEachIndexed { i, (x, y) ->
                val dy = ((t + i * .17f) % 1f) * -40f
                rotate(45f + t * 90f, Offset(size.width * x, size.height * y + dy)) {
                    drawRect(Color(0xFF4F8FD8).copy(alpha = .8f), Offset(size.width * x - 8f, size.height * y + dy - 8f), Size(16f, 16f))
                }
            }
        }
        Column(
            Modifier.fillMaxSize().padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.fillMaxHeight(.16f))
            Image(
                painterResource(R.drawable.splash_logo), null,
                Modifier.fillMaxWidth().alpha(logo.value).scale(.92f + .08f * logo.value),
                contentScale = ContentScale.FillWidth,
            )
            Spacer(Modifier.height(80.dp))
            GlowLine(160.dp, text.value)
            Spacer(Modifier.height(64.dp))
            Column(Modifier.alpha(text.value), horizontalAlignment = Alignment.CenterHorizontally) {
                val s = mc(25, Color.White).copy(fontWeight = FontWeight.Bold, letterSpacing = 2.sp, textAlign = TextAlign.Center,
                    shadow = Shadow(Color.Black, Offset(4f, 4f), 0f))
                Text("DREAM IT.", style = s)
                Text("BUILD IT.", style = s)
                Text("CREATE IT.", style = s.copy(color = Color(0xFFDCEBFF)))
                Spacer(Modifier.height(10.dp))
                GlowLine(220.dp, 1f)
                Spacer(Modifier.height(8.dp))
                Text("AddonStation", style = mc(18, Color.White))
                Spacer(Modifier.height(10.dp))
                GlowLine(200.dp, 1f, blueOnly = true)
            }
        }
    }
}

@Composable
private fun GlowLine(w: androidx.compose.ui.unit.Dp, a: Float, blueOnly: Boolean = false) {
    Box(
        Modifier.width(w).height(3.dp).alpha(a).background(
            Brush.horizontalGradient(
                if (blueOnly) listOf(Color.Transparent, Color(0xFF3F86E0), Color.Transparent)
                else listOf(Color.Transparent, C.green, C.blue, Color.Transparent)
            )
        )
    )
}
