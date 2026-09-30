package com.addonstation.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.addonstation.mobile.AddonStationApp
import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.data.model.SiteAsset

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

/** قسم واحد من أصول الواجهة: عنوان القسم (اسم المجلد الأصلي) + أصوله بالترتيب. */
data class AssetSection(val title: String, val assets: List<SiteAsset>)

/**
 * يجمّع الأصول في **فقرات منفصلة** حسب القسم (collection) مع الحفاظ على ترتيب الخادم.
 * الأصول التي لا قسم لها تُوضع في فقرة واحدة بعنوان «أخرى» بدل أن تختفي.
 */
fun groupAssetsByCollection(assets: List<SiteAsset>, othersTitle: String = "Other"): List<AssetSection> {
    val groups = LinkedHashMap<String, MutableList<SiteAsset>>()
    assets.forEach { asset ->
        val key = asset.collection?.takeIf { it.isNotBlank() } ?: othersTitle
        groups.getOrPut(key) { mutableListOf() }.add(asset)
    }
    return groups.map { (title, list) -> AssetSection(title, list) }
}

/**
 * رابط صورة الأداة المجهّزة: الملف الشخصي يحفظ **اسم** الأداة، فنبحث عن ملفها
 * في أصول الواجهة ثم نحوّله إلى رابط عام — لتظهر الأداة (متحرّكة إن كانت GIF) بجانب الاسم.
 */
fun equippedItemUrl(container: AppContainer, name: String?): String? {
    val clean = name?.trim().orEmpty()
    if (clean.isEmpty() || clean.equals("none", ignoreCase = true)) return null
    val asset = container.state.siteAssets.value.firstOrNull {
        it.assetType == "item" && it.name == clean
    } ?: return null
    return container.siteAssetUrl(asset.fileUrl)
}

/** أول حرف/حرفان من الاسم لعرضها في الصور البديلة. */
fun initialsOf(name: String?): String {
    val clean = name?.trim().orEmpty()
    if (clean.isEmpty()) return "?"
    return clean.take(2).uppercase()
}
