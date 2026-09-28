package com.addonstation.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.addonstation.mobile.AddonStationApp
import com.addonstation.mobile.data.AppContainer

/** وصول مختصر لحاوية التطبيق من أي شاشة. */
@Composable
fun rememberContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { AddonStationApp.container(context) }
}

@Composable
fun rememberDarkTheme(container: AppContainer): Boolean {
    val dark by container.prefs.darkTheme.collectAsState(initial = true)
    return dark
}

/** يحوّل مساراً نسبياً داخل مخزن site-assets إلى رابط كامل (يتجاهل الروابط المطلقة). */
fun assetUrl(container: AppContainer, path: String?): String? = container.siteAssetUrl(path)

/** أول حرف/حرفان من الاسم لعرضها في الصور البديلة. */
fun initialsOf(name: String?): String {
    val clean = name?.trim().orEmpty()
    if (clean.isEmpty()) return "?"
    return clean.take(2).uppercase()
}
