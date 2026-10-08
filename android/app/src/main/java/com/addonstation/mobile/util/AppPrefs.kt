package com.addonstation.mobile.util

import android.content.Context

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

    fun splashCompleted(context: Context): Boolean =
        sp(context).getBoolean(KEY_SPLASH_COMPLETED, false)

    fun markSplashCompleted(context: Context) {
        sp(context).edit().putBoolean(KEY_SPLASH_COMPLETED, true).apply()
    }
}
