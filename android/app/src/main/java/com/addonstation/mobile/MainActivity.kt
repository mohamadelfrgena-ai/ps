package com.addonstation.mobile

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.addonstation.mobile.ui.components.*
import com.addonstation.mobile.ui.screens.*
import com.addonstation.mobile.ui.theme.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(C.bg.toArgb()),
            navigationBarStyle = SystemBarStyle.dark(C.bg.toArgb()),
        )
        setContent { AddonStationTheme { App() } }
    }
}

@Composable
fun App(state: AppState = viewModel()) {
    var splash by rememberSaveable { mutableStateOf(true) }
    if (splash) { SplashScreen { splash = false }; return }

    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val session by state.sb.session.collectAsStateWithLifecycle()
    val balance by state.balance.collectAsStateWithLifecycle()

    fun go(r: String) = nav.navigate(r) {
        popUpTo("home") { saveState = true }; launchSingleTop = true; restoreState = true
    }

    val loggedIn = session != null
    val (title, icon) = when (route) {
        "community" -> if (loggedIn) "Community" to Icons.Outlined.Forum else "Sign in" to Icons.Outlined.Person
        "store" -> "Store" to Icons.Outlined.ShoppingCart
        "profile" -> if (loggedIn) "Profile" to Icons.Outlined.Person else "Sign in" to Icons.Outlined.Person
        "settings" -> if (loggedIn) "Settings" to Icons.Outlined.Settings else "Sign in" to Icons.Outlined.Person
        "login" -> "Sign in" to Icons.Outlined.Person
        "register" -> "Create account" to Icons.Outlined.PersonAdd
        "chat" -> "Community" to Icons.Outlined.Forum
        else -> "Home" to Icons.Outlined.Home
    }
    val tab = Tab.entries.firstOrNull { it.route == route }?.takeIf { loggedIn || it == Tab.Home || it == Tab.Store }
    val showNav = route != "chat"

    Box(
        Modifier.fillMaxSize().background(C.bg).background(
            Brush.radialGradient(listOf(Color(0x123DDC84), Color.Transparent), radius = 1400f,
                center = androidx.compose.ui.geometry.Offset(900f, -100f))
        )
    ) {
        Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
            TopBar(title, icon, balance) { go("home") }
            Box(Modifier.weight(1f)) {
                NavHost(
                    nav, "home",
                    enterTransition = { fadeIn(androidx.compose.animation.core.tween(180)) },
                    exitTransition = { fadeOut(androidx.compose.animation.core.tween(120)) },
                ) {
                    composable("home") { HomeScreen(state, ::go) }
                    composable("store") { StoreScreen(state) }
                    composable("community") { if (loggedIn) CommunityScreen(state, ::go) else LoginScreen(state, ::go) }
                    composable("chat") { ChatScreen(state) { go("home") } }
                    composable("profile") { if (loggedIn) ProfileScreen(state, ::go) else LoginScreen(state, ::go) }
                    composable("settings") { if (loggedIn) SettingsScreen(state, ::go) else LoginScreen(state, ::go) }
                    composable("login") { LoginScreen(state, ::go) }
                    composable("register") { RegisterScreen(state, ::go) }
                }
                if (showNav) Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter)) {
                    BottomNav(tab) { go(it.route) }
                }
            }
        }
    }
}
