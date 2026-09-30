package com.addonstation.mobile.ui.community

import com.addonstation.mobile.data.model.ChatChannel

/**
 * أسماء غرف اللغات **بلغتها نفسها** — ثابتة داخل التطبيق لا تتغيّر مع لغة الواجهة
 * ولا يعتمد صحّتها على ما هو مخزّن في قاعدة البيانات:
 *
 *     العربية · Русский · Português · 中文 · English · Español · Français · Türkçe
 *
 * السبب: غرفة اللغة مكانٌ لأهل تلك اللغة، فيجب أن يقرأ اسمها بلغته — كما لو كان
 * عنواناً على باب الغرفة. لذلك يُشتق الاسم من **معرّف الغرفة** (channel.id) لا من
 * عمود الاسم، فإن كانت القاعدة قديمة أو مُعدَّلة يظهر الاسم الصحيح دائماً.
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

/** وصف مختصر لكل غرفة لغة بلغتها (يظهر تحت الاسم عند الحاجة). */
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

/** اسم الغرفة للعرض: غرف اللغات باسمها بلغتها، وغيرها بالاسم المخزَّن كما هو. */
fun channelDisplayName(channel: ChatChannel): String =
    channelDisplayName(channel.id, channel.name)

/** اسم الغرفة من المعرّف مباشرة (للشاشات التي لا تحمل كائن الغرفة كاملاً). */
fun channelDisplayName(id: String, storedName: String): String =
    LANGUAGE_ROOM_NAMES[id] ?: storedName

/** وصف الغرفة للعرض: غرف اللغات بوصفها بلغتها، وغيرها بالمخزَّن. */
fun channelDisplayDescription(channel: ChatChannel): String? =
    LANGUAGE_ROOM_DESCRIPTIONS[channel.id] ?: channel.description

/** هل هذه غرفة لغة؟ (تُستخدم لتمييز القسم في القوائم) */
fun isLanguageRoom(id: String): Boolean = id.startsWith("lang-")
