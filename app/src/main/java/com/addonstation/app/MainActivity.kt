package com.addonstation.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.addonstation.app.core.LanguageManager
import com.addonstation.app.ui.navigation.AppRoot
import com.addonstation.app.ui.theme.AddonStationTheme
import kotlinx.coroutines.flow.first

val LocalApp = compositionLocalOf<AppContainer> { error("No AppContainer") }

/** Generic ViewModel factory so screens can build VMs with the container, no codegen needed. */
@Composable
inline fun <reified VM : ViewModel> appViewModel(crossinline creator: () -> VM): VM {
    val factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = creator() as T
        }
    }
    return viewModel(factory = factory)
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        parseRecoveryIntent(intent)

        val container = (application as AddonStationApp).container
        var sysReady by mutableStateOf(false)
        splash.setKeepOnScreenCondition { !sysReady }

        setContent {
            val app = LocalAppOwner.current ?: container
            CompositionLocalProvider(LocalApp provides app) {
                var ready by remember { mutableStateOf(app.ready) }
                var dark by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    // Apply saved language before first paint (mirrors the web pre-paint script)
                    try {
                        LanguageManager.apply(app.session.language.first())
                    } catch (_: Exception) { }
                    dark = try {
                        app.session.darkTheme.first()
                    } catch (_: Exception) { true }
                    app.init()
                    ready = true
                    sysReady = true
                }
                // Observe theme changes live
                val darkFlow = remember { app.session.darkTheme }
                val darkNow by darkFlow.collectAsState(initial = dark)
                AddonStationTheme(darkTheme = darkNow) {
                    AppRoot(ready = ready)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        parseRecoveryIntent(intent)
    }

    /** addonstation://recovery#access_token=…&refresh_token=… (GoTrue format). */
    private fun parseRecoveryIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme != "addonstation" || data.host != "recovery") return
        val frag = data.fragment ?: data.encodedFragment ?: ""
        val map = frag.split("&").mapNotNull {
            val kv = it.split("=", limit = 2)
            if (kv.size == 2) kv[0] to kv[1] else null
        }.toMap()
        val at = map["access_token"]
        val rt = map["refresh_token"]
        if (!at.isNullOrBlank() && !rt.isNullOrBlank()) {
            (application as AddonStationApp).container.pendingRecovery = PendingRecovery(at, rt)
        }
    }
}

/** Allows previews/tests to inject a container; null in production (uses real one). */
object LocalAppOwner {
    var current: AppContainer? = null
}
