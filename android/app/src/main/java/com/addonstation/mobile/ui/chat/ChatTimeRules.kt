package com.addonstation.mobile.ui.chat

import com.addonstation.mobile.util.TimeFormat

/**
 * قواعد زمنية لرسائل الشات.
 *
 * القاعدة: **يحذف المستخدم رسالته خلال ٣ دقائق فقط من إرسالها**، وبعدها لا يستطيع.
 * المصدر الحاسم للقاعدة هو قاعدة البيانات (`chat_delete_my_message` في
 * supabase/03_functions_chat.sql) وهذا الملف يجعل الواجهة مطابقة لها:
 *   • يُظهر خيار «حذف رسالتي» داخل المدة فقط، ويخفيه بعدها.
 *   • يُظهر ما تبقّى من الوقت بصيغة «متبقٍ دقيقتان» حتى يعرف المستخدم مهلته.
 *
 * ونفس الرقم في القاعدة (chat_delete_window_seconds = 180) — لو تغيّر هناك يُغيَّر هنا.
 */
const val CHAT_DELETE_WINDOW_SECONDS: Long = 180L

/** الثواني المتبقية لحذف الرسالة (0 إن انتهت المدة أو تعذّر قراءة الوقت). */
fun deleteWindowRemainingSeconds(createdAt: String?, nowMillis: Long = System.currentTimeMillis()): Long {
    val sent = TimeFormat.parseMillis(createdAt) ?: return 0L
    val elapsed = (nowMillis - sent) / 1000L
    return (CHAT_DELETE_WINDOW_SECONDS - elapsed).coerceAtLeast(0L)
}

/** هل ما زال بإمكان صاحب الرسالة حذفها؟ */
fun canDeleteOwnMessage(createdAt: String?, nowMillis: Long = System.currentTimeMillis()): Boolean =
    deleteWindowRemainingSeconds(createdAt, nowMillis) > 0L

/**
 * نص المهلة المتبقية بصيغة عربية/مترجمة عبر الموارد:
 * يعيد عدد الدقائق المتبقية (1 أو 2 أو 3) ليُصاغ في نص المورد `chat_delete_left`.
 */
fun deleteWindowRemainingMinutes(createdAt: String?, nowMillis: Long = System.currentTimeMillis()): Int {
    val seconds = deleteWindowRemainingSeconds(createdAt, nowMillis)
    if (seconds <= 0L) return 0
    return ((seconds + 59L) / 60L).toInt().coerceAtLeast(1)
}
