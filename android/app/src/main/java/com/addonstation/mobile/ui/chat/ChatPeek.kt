package com.addonstation.mobile.ui.chat

/**
 * ذاكرة خفيفة جداً لـ«آخر رسالة» في كل غرفة — تُستعمل في **قائمة الغرف**
 * (التصميم الجديد يعرض وقت آخر رسالة في يمين كل صف).
 *
 * لماذا هنا؟ لأن [com.addonstation.mobile.data.model.ChatChannel] لا يحمل حقل
 * «آخر رسالة»، وجلب آخر رسالة لكل غرفة من الشبكة = عشرات الطلبات عند فتح
 * القائمة. الحل: كل غرفة **تُفتح** تُسجّل وقت آخر رسالة عندها هنا (سطر واحد في
 * ChatViewModel)، فتظهر الأوقات فوراً في القائمة بلا أي طلب إضافي، وبلا وقت
 * كاذب لغرفة لم تُفتح بعد.
 *
 * الخريطة متزامنة (Concurrent) لأن القراءة من الواجهة والكتابة من خيط الشبكة.
 */
object ChatPeek {

    private val lastAtByChannel =
        java.util.concurrent.ConcurrentHashMap<String, String>()

    /** يُسجّل وقت آخر رسالة معروضة في الغرفة (فارغاً يُتجاهل). */
    fun note(channelId: String, iso: String?) {
        if (channelId.isBlank() || iso.isNullOrBlank()) return
        lastAtByChannel[channelId] = iso
    }

    /** وقت آخر رسالة معروفة في الغرفة — أو null إن لم تُفتح بعد. */
    fun last(channelId: String): String? = lastAtByChannel[channelId]

    /** يُفرَّغ عند الخروج من الحساب (لا أوقات لحساب سابق). */
    fun clear() = lastAtByChannel.clear()
}
