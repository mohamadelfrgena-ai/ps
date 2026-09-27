package com.addonstation.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.addonstation.app.ui.AccountScreens
import com.addonstation.app.ui.AddonTheme
import com.addonstation.app.ui.AppModel
import com.addonstation.app.ui.BrowseScreens
import com.addonstation.app.ui.LocalPalette
import com.addonstation.app.ui.SocialScreens
import com.addonstation.app.ui.SplashOverlay
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val model: AppModel = viewModel()
            AddonTheme(dark = model.themeName != "light", rtl = model.rtl) {
                AddonApp(model)
            }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun AddonApp(model: AppModel) {
    val nav = rememberNavController()
    val palette = LocalPalette.current
    val backStack = nav.currentBackStackEntryAsState()
    val route = backStack.value?.destination?.route.orEmpty()
    val showNav = route.startsWith("home") || route.startsWith("store") || route.startsWith("community") ||
        route.startsWith("profile/") || route.startsWith("settings") || route.startsWith("creators")
    val tabs = listOf(
        Tab("settings", model.text.settings, Icons.Filled.Settings),
        Tab("community", model.text.community, Icons.Filled.Forum),
        Tab("home", model.text.home, Icons.Filled.Home),
        Tab("store/addon", model.text.store, Icons.Filled.ShoppingCart),
        Tab("profile/overview", model.text.profile, Icons.Filled.Person)
    )
    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Scaffold(
            containerColor = palette.bg,
            bottomBar = {
                if (showNav) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .background(palette.surface)
                            .navigationBarsPadding()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        tabs.forEach { tab ->
                            val selected = route.startsWith(tab.route.substringBefore("/"))
                            Column(
                                Modifier
                                    .clip(RoundedCornerShape(16.dp))
                                    .clickable { nav.navigate(tab.route) { launchSingleTop = true } }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(tab.icon, null, tint = if (selected) palette.green else palette.muted, modifier = Modifier.size(22.dp))
                                Text(tab.label, color = if (selected) palette.green else palette.muted, fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        ) { padding ->
            NavHost(nav, startDestination = "home", modifier = Modifier.padding(padding)) {
                composable("home") { BrowseScreens.Home(model, nav) }
                composable("store/{type}", arguments = listOf(navArgument("type") { type = NavType.StringType })) {
                    BrowseScreens.Store(model, nav, it.arguments?.getString("type") ?: "addon")
                }
                composable("item/{slug}", arguments = listOf(navArgument("slug") { type = NavType.StringType })) {
                    BrowseScreens.Item(model, nav, it.arguments?.getString("slug").orEmpty())
                }
                composable("exclusives") { BrowseScreens.Exclusives(model, nav) }
                composable("community") { SocialScreens.Rooms(model, nav) }
                composable("chat/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    SocialScreens.Chat(model, nav, it.arguments?.getString("id").orEmpty())
                }
                composable("creators") { SocialScreens.Creators(model, nav) }
                composable("creator/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    SocialScreens.Creator(model, nav, it.arguments?.getString("id").orEmpty())
                }
                composable("wallet") { SocialScreens.Wallet(model, nav) }
                composable("tip/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    SocialScreens.Tip(model, nav, it.arguments?.getString("id").orEmpty())
                }
                composable("login") { AccountScreens.Login(model, nav) }
                composable("register") { AccountScreens.Register(model, nav) }
                composable("profile/{tab}", arguments = listOf(navArgument("tab") { type = NavType.StringType })) {
                    AccountScreens.Profile(model, nav, it.arguments?.getString("tab") ?: "overview")
                }
                composable("profileSettings") { AccountScreens.ProfileSettings(model, nav) }
                composable("user/{id}", arguments = listOf(navArgument("id") { type = NavType.StringType })) {
                    AccountScreens.User(model, nav, it.arguments?.getString("id").orEmpty())
                }
                composable("notifications") { AccountScreens.Notifications(model, nav) }
                composable("settings") { AccountScreens.Settings(model, nav) }
                composable("support") { AccountScreens.Support(model, nav) }
                composable("apply") { AccountScreens.Apply(model, nav) }
                composable("studio") { AccountScreens.Studio(model, nav) }
                composable("checkout/{plan}", arguments = listOf(navArgument("plan") { type = NavType.StringType })) {
                    AccountScreens.Checkout(model, nav, it.arguments?.getString("plan") ?: "epic")
                }
                composable("admin") { AccountScreens.Admin(model, nav) }
            }
        }
        if (model.showSplash && route.startsWith("home")) SplashOverlay { model.showSplash = false }
        model.toast?.let { message ->
            LaunchedEffect(message) {
                delay(2400)
                model.toast = null
            }
            Text(
                message,
                color = palette.text,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 86.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.elevated)
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

fun NavHostController.openItem(slug: String) {
    if (slug.isNotBlank()) navigate("item/$slug")
}

fun NavHostController.needLogin(model: AppModel): Boolean {
    if (model.signedIn()) return false
    model.toast = model.text.signInFirst
    navigate("login")
    return true
}
