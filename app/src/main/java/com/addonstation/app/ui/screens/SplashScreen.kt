package com.addonstation.app.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.core.Constants
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Boot splash (animated logo + tagline) then routes to welcome/home. */
@Composable
fun SplashScreen(animated: Boolean, onDone: (welcomeDone: Boolean) -> Unit = {}) {
    val app = LocalApp.current
    val pulse by rememberInfiniteTransition(label = "splash").animateFloat(
        initialValue = 0.92f, targetValue = 1.05f,
        animationSpec = infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    LaunchedEffect(animated) {
        if (!animated) {
            delay(900)
            val done = try {
                app.session.welcomeDone.first()
            } catch (_: Exception) { false }
            onDone(done)
        }
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF101B28), Color(0xFF0B0F14)))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            AsImage(
                model = Constants.SPLASH_LOGO,
                contentDescription = "AddonStation",
                modifier = Modifier.size(150.dp).scale(if (animated) pulse else 1f),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
            Spacer(Modifier.height(14.dp))
            Text("AddonStation", style = MaterialTheme.typography.displaySmall, color = Color.White)
            Spacer(Modifier.height(4.dp))
            Text(
                "${stringResource(R.string.splash_dream)} ${stringResource(R.string.splash_build)} ${stringResource(R.string.splash_create)}",
                style = MaterialTheme.typography.labelLarge,
                color = Color(0xFF6FB8FF),
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(22.dp))
            LinearProgressIndicator(modifier = Modifier.alpha(0.85f))
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.splash_loading), style = MaterialTheme.typography.bodySmall, color = Color(0xFF8EA0B3))
        }
    }
}

/** First-run onboarding: choose console + bind IP (mirrors the web welcome overlay). */
@Composable
fun WelcomeScreen(nav: NavHostController, onDone: () -> Unit) {
    val app = LocalApp.current
    var type by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("ps4") }
    var ip by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf("") }
    var err by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(false) }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF0B0F14), Color(0xFF12202F)))),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AsImage(model = Constants.SPLASH_LOGO, contentDescription = null, modifier = Modifier.size(110.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.welcome_title), style = MaterialTheme.typography.displaySmall, color = Color.White)
            Text(stringResource(R.string.welcome_description), style = MaterialTheme.typography.bodyMedium, color = Color(0xFF8EA0B3))
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.welcome_question), color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("ps4" to "PS4 🎮", "ps5" to "PS5 🎮").forEach { (v, label) ->
                    androidx.compose.material3.FilterChip(
                        selected = type == v, onClick = { type = v }, label = { Text(label) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            androidx.compose.material3.OutlinedTextField(
                value = ip, onValueChange = { ip = it.trim(); err = false },
                label = { Text(stringResource(R.string.welcome_iplabel)) },
                placeholder = { Text("192.168.1.20") },
                singleLine = true, isError = err,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.TextButton(onClick = { nav.navigate(Routes.IP_GUIDE) }) {
                Text(stringResource(R.string.welcome_ipguide), color = Color(0xFF6FB8FF))
            }
            Spacer(Modifier.height(8.dp))
            androidx.compose.material3.Button(
                onClick = {
                    if (ip.isBlank() || !com.addonstation.app.core.isValidIpv4(ip)) {
                        err = true; return@Button
                    }
                    scope.launch {
                        app.session.setDevice(type, ip)
                        app.session.setWelcomeDone()
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.welcome_continue)) }
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.TextButton(onClick = {
                scope.launch { app.session.setWelcomeDone(); onDone() }
            }) { Text(stringResource(R.string.welcome_skip)) }
        }
    }
}
