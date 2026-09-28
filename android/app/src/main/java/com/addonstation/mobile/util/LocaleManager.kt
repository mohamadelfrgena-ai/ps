package com.addonstation.mobile.util

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import java.util.Locale

/**
 * إدارة لغة التطبيق (8 لغات) مع تبديل فوري.
 * القاعدة من الوثيقة: التركية LTR، والعربية RTL، وباقي اللغات LTR.
 */
object LocaleManager {

    val supported: List<AppLanguage> = listOf(
        AppLanguage("ar", "العربية", "🇱🇾", isRtl = true),
        AppLanguage("en", "English", "🇬🇧", isRtl = false),
        AppLanguage("pt", "Português", "🇧🇷", isRtl = false),
        AppLanguage("es", "Español", "🇪🇸", isRtl = false),
        AppLanguage("ru", "Русский", "🇷🇺", isRtl = false),
        AppLanguage("zh", "简体中文", "🇨🇳", isRtl = false),
        AppLanguage("fr", "Français", "🇫🇷", isRtl = false),
        AppLanguage("tr", "Türkçe", "🇹🇷", isRtl = false),
    )

    fun byCode(code: String?): AppLanguage =
        supported.firstOrNull { it.code.equals(code, ignoreCase = true) } ?: supported[1]

    /** يلفّ الـ Context بلغة المستخدم حتى تُقرأ الموارد الصحيحة. */
    fun wrap(base: Context): Context {
        val code = AppPrefs.readLocale(base)
        return applyLocale(base, code)
    }

    fun applyLocale(base: Context, languageCode: String): Context {
        val locale = Locale.forLanguageTag(languageCode)
        Locale.setDefault(locale)
        val config = Configuration(base.resources.configuration)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            config.setLocales(LocaleList(locale))
        } else {
            @Suppress("DEPRECATION")
            config.locale = locale
        }
        @Suppress("DEPRECATION")
        config.setLayoutDirection(locale)
        return base.createConfigurationContext(config)
    }

    /**
     * الحروف الكبيرة حسب قواعد اللغة — التركية تحتاج Locale("tr") حتى يصبح i → İ.
     */
    fun upperCase(text: String, languageCode: String): String =
        text.uppercase(Locale.forLanguageTag(if (languageCode == "tr") "tr" else languageCode))

    fun isRtl(languageCode: String): Boolean = byCode(languageCode).isRtl
}

data class AppLanguage(
    val code: String,
    val label: String,
    val flag: String,
    val isRtl: Boolean,
)
