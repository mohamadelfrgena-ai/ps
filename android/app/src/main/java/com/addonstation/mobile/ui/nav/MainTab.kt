package com.addonstation.mobile.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingBag
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.ui.graphics.vector.ImageVector
import com.addonstation.mobile.R

/** ترتيب منطقي start → end؛ Compose يعكسه تلقائياً في واجهات RTL. */
enum class MainTab(
    val route: String,
    val filledIcon: ImageVector,
    val outlinedIcon: ImageVector,
    val labelRes: Int,
    val isCenter: Boolean = false,
) {
    Settings("settings", Icons.Filled.Settings, Icons.Outlined.Settings, R.string.nav_settings),
    Community("community", Icons.Filled.ChatBubble, Icons.Outlined.ChatBubbleOutline, R.string.nav_chat),
    Home("home", Icons.Filled.Home, Icons.Outlined.Home, R.string.nav_home, isCenter = true),
    Store("store", Icons.Filled.ShoppingBag, Icons.Outlined.ShoppingBag, R.string.nav_store),
    Profile("profile", Icons.Filled.Person, Icons.Outlined.Person, R.string.nav_profile),
}
