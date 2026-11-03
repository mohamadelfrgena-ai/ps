package com.addonstation.mobile.ui.community

import com.addonstation.mobile.data.model.ChatChannel

/**
 * صلاحية الكتابة في غرفة.
 *
 * الخادم نفسه يمنع الكتابة (يفحص `is_locked` و`post_permission` قبل الإرسال)،
 * وهذه الدالة نسخة العميل لإخفاء شريط الكتابة قبل أن يحاول المستخدم أصلاً:
 * - `is_locked = true`  ⇐ المشرف/الأدمن فقط يكتبان.
 * - `post_permission = 'admin_only'`      ⇐ الأدمن فقط.
 * - `post_permission = 'creators_admin'` ⇐ المبدعون + الأدمن.
 * - غير ذلك ⇐ الجميع.
 */
fun canPostInChannel(channel: ChatChannel, role: String?): Boolean {
    val r = (role ?: "").lowercase()
    val staff = r == "admin" || r == "moderator"
    if (channel.isLocked && !staff) return false
    return when (channel.postPermission.lowercase()) {
        "admin_only" -> r == "admin"
        "creators_admin" -> staff || r == "creator"
        "everyone" -> true
        else -> true // members أو أي قيمة غير معروفة: مسموح
    }
}

/** غرفة للقراءة فقط بالنسبة لهذا المستخدم (تُخفى عنه كل أدوات الكتابة). */
fun isReadOnlyFor(channel: ChatChannel, role: String?): Boolean = !canPostInChannel(channel, role)
