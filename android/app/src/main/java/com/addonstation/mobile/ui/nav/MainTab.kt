package com.addonstation.mobile.ui.nav

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.vector.ImageVector
import com.addonstation.mobile.R

/**
 * الشريط السفلي: الإعدادات · المجتمع · المنزل (مرتفع في الوسط) · المتجر · الملف الشخصي.
 * الترتيب في الكود = الترتيب البصري في RTL، ويتحول تلقائياً في LTR.
 */
enum class MainTab(
    val route: String,
    val icon: ImageVector,
    val labelRes: Int,
    val isCenter: Boolean = false,
) {
    Settings("settings", Icons.Filled.Settings, R.string.nav_settings),
    Community("community", Icons.Filled.ChatBubble, R.string.nav_community),
    Home("home", Icons.Filled.Home, R.string.nav_home, isCenter = true),
    Store("store", Icons.Filled.ShoppingCart, R.string.nav_store),
    Profile("profile", Icons.Filled.Person, R.string.nav_profile),
}
