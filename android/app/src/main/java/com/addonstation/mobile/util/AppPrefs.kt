package com.addonstation.mobile.util

import android.content.Context
import android.os.Build

/**
 * قراءة/كتابة سريعة متزامنة للتفضيلات الحسّاسة للبدء (اللغة والثيم) قبل إقلاع DataStore.
 * المصدر الرسمي هو DataStore داخل UserPrefs، وهذا الملف مخزن مرآة خفيف لا يحتاج Coroutines.
 */
object AppPrefs {
    private const val FILE = "addonstation_boot"
    private const val KEY_LOCALE = "locale"
    private const val KEY_DARK = "dark_theme"
    private const val KEY_NOTIF_ASKED = "notif_asked"
    private const val KEY_SPLASH_COMPLETED = "splash_completed"
    private const val KEY_SPLASH_APP_VERSION = "splash_app_version"
    private const val KEY_CONSOLE_LINK_OPENED = "console_link_opened"

    private fun sp(context: Context) =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun readLocale(context: Context): String =
        sp(context).getString(KEY_LOCALE, "en") ?: "en"

    fun writeLocale(context: Context, code: String) {
        sp(context).edit().putString(KEY_LOCALE, code).apply()
    }

    fun readDark(context: Context): Boolean = sp(context).getBoolean(KEY_DARK, true)

    /**
     * هل سألنا المستخدم عن إذن الإشعارات مرة واحدة من قبل؟
     * نسأل **مرة واحدة فقط** عند أول دخول على أندرويد 13+ ثم لا نُزعج أبداً،
     * ويبقى زر «اسمح بالإشعارات» حاضراً في الإعدادات لمن أراد لاحقاً.
     */
    fun notificationAsked(context: Context): Boolean =
        sp(context).getBoolean(KEY_NOTIF_ASKED, false)

    fun markNotificationAsked(context: Context) {
        sp(context).edit().putBoolean(KEY_NOTIF_ASKED, true).apply()
    }

    fun writeDark(context: Context, dark: Boolean) {
        sp(context).edit().putBoolean(KEY_DARK, dark).apply()
    }

    /** The Settings NEW badge disappears permanently after this feature is opened. */
    fun consoleLinkOpened(context: Context): Boolean =
        sp(context).getBoolean(KEY_CONSOLE_LINK_OPENED, false)

    fun markConsoleLinkOpened(context: Context) {
        sp(context).edit().putBoolean(KEY_CONSOLE_LINK_OPENED, true).apply()
    }

    /**
     * تُعرض المقدمة الطويلة عند أول تشغيل بعد التثبيت أو تحديث إصدار التطبيق.
     * تغيير تاريخ تحديث الحزمة يلتقط التحديثات حتى إن لم يتغير versionCode.
     */
    fun splashCompleted(context: Context): Boolean {
        val prefs = sp(context)
        return prefs.getBoolean(KEY_SPLASH_COMPLETED, false) &&
            prefs.getString(KEY_SPLASH_APP_VERSION, null) == installedAppVersion(context)
    }

    fun markSplashCompleted(context: Context) {
        sp(context).edit()
            .putBoolean(KEY_SPLASH_COMPLETED, true)
            .putString(KEY_SPLASH_APP_VERSION, installedAppVersion(context))
            .apply()
    }

    @Suppress("DEPRECATION")
    private fun installedAppVersion(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            info.versionCode.toLong()
        }
        "$code:${info.versionName.orEmpty()}:${info.lastUpdateTime}"
    }.getOrDefault("unknown")
}
