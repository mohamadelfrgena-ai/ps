package com.addonstation.mobile.ui.community

import com.addonstation.mobile.data.model.ChatChannel

/**
 * أسماء غرف اللغات **بلغتها نفسها** — العربية · Русский · Português · 中文 … إلخ.
 *
 * القاعدة: كل غرفة لغة يظهر اسمها بلغة أهلها، تماماً كعنوانٍ على باب الغرفة.
 * والاسم يؤخذ من **أول مصدر متاح** بهذا الترتيب:
 *
 *   1) معرّف الغرفة (`lang-ar` → «العربية») — لا يتأثر بأي تعديل في قاعدة البيانات.
 *   2) اسم الغرفة المخزَّن إن طابق أحد أسماء اللغة المعروفة (بأي لغة كُتب، ومع/بلا إيموجي):
 *      «Russian» أو «الروسية» أو «Русский» → «Русский» · «English» أو «الإنجليزية» → «English».
 *   3) وإلا يُعرض الاسم المخزَّن بعد تنظيفه من الإيموجي والرموز — فلا تختفي أي غرفة.
 *
 * النتيجة: لو كانت قاعدتك تحمل أسماء إنجليزية أو عربية لغرف اللغات، أو أسماء قديمة
 * بأعلام وإيموجي، يظهر في التطبيق الاسم الصحيح بلغة الغرفة فوراً.
 */
private val LANGUAGE_ROOM_NAMES: Map<String, String> = mapOf(
    "lang-ar" to "العربية",
    "lang-en" to "English",
    "lang-es" to "Español",
    "lang-fr" to "Français",
    "lang-pt" to "Português",
    "lang-ru" to "Русский",
    "lang-tr" to "Türkçe",
    "lang-zh" to "中文",
)

/** أسماء اللغة كما قد ترد في قاعدة البيانات (بأي لغة) → الاسم المعروض بلغتها. */
private val LANGUAGE_NAME_ALIASES: Map<String, String> = buildMap {
    fun register(native: String, vararg aliases: String) {
        aliases.forEach { put(normalizeName(it), native) }
    }
    register("العربية", "العربية", "عربي", "الغرفه العربيه", "الغرفة العربية", "arabic", "arab", "ar")
    register("English", "english", "الإنجليزية", "الانجليزية", "الأنجليزية", "انجليزي", "إنجليزي", "en")
    register("Español", "español", "espanol", "Spanish", "spanish", "الإسبانية", "الاسبانية", "الأسبانية", "es")
    register("Français", "français", "francais", "French", "french", "الفرنسية", "الفرنسيه", "fr")
    register("Português", "português", "portugues", "Portuguese", "portuguese", "البرتغالية", "البرتغاليه", "pt")
    register("Русский", "русский", "Russian", "russian", "الروسية", "الروسيه", "ru")
    register("Türkçe", "türkçe", "turkce", "Turkish", "turkish", "التركية", "التركيه", "tr")
    register("中文", "中文", "简体中文", "chinese", "الصينية", "الصينيه", "zh")
}

/** أوصاف غرف اللغات بلغتها. */
private val LANGUAGE_ROOM_DESCRIPTIONS: Map<String, String> = mapOf(
    "lang-ar" to "غرفة الدردشة العربية",
    "lang-en" to "English chat room",
    "lang-es" to "Sala de chat en español",
    "lang-fr" to "Salon de discussion en français",
    "lang-pt" to "Sala de bate-papo em português",
    "lang-ru" to "Русскоязычный чат",
    "lang-tr" to "Türkçe sohbet odası",
    "lang-zh" to "中文聊天室",
)

/** تبسيط للمقارنة: حروف وأرقام فقط (تُزيل الإيموجي والأعلام والمسافات وعلامات التشكيل الشكلية). */
private fun normalizeName(text: String): String =
    text.lowercase().filter { it.isLetterOrDigit() }

/** يُزيل الإيموجي والرموز من طرفي الاسم (مثل «🇷🇺 Русский» → «Русский»). */
private fun cleanStoredName(text: String): String {
    val trimmed = text.trim().trim { !it.isLetterOrDigit() }
    return trimmed.ifBlank { text.trim() }
}

/** الاسم الأصلي لغرفة لغة إن عُرفت من معرّفها أو من اسمها المخزَّن. */
fun languageRoomNativeName(id: String, storedName: String): String? =
    LANGUAGE_ROOM_NAMES[id] ?: LANGUAGE_NAME_ALIASES[normalizeName(storedName)]

/** هل هذه غرفة لغة؟ (معرّفها يبدأ بـ lang- أو اسمها اسم لغة معروف) */
fun isLanguageRoom(id: String, storedName: String = ""): Boolean =
    id.startsWith("lang-") || LANGUAGE_NAME_ALIASES.containsKey(normalizeName(storedName))

/** اسم الغرفة للعرض: غرف اللغات باسمها بلغتها، وغيرها بالاسم المخزَّن منظَّفاً. */
fun channelDisplayName(id: String, storedName: String): String =
    languageRoomNativeName(id, storedName) ?: cleanStoredName(storedName)

fun channelDisplayName(channel: ChatChannel): String =
    channelDisplayName(channel.id, channel.name)

/** وصف الغرفة للعرض: غرف اللغات بوصفها بلغتها، وغيرها بالمخزَّن. */
fun channelDisplayDescription(channel: ChatChannel): String? =
    LANGUAGE_ROOM_DESCRIPTIONS[channel.id]
        ?: channel.description?.let { cleanStoredName(it) }
