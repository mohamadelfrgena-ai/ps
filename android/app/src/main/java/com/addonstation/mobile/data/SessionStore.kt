package com.addonstation.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.addonstation.mobile.data.model.Session

/**
 * تخزين الجلسة في مخزن مشفّر (Android Keystore) كما تطلب وثيقة الحماية §3.4.
 * لا يُخزَّن أي مفتاح خدمة في التطبيق — فقط المفتاح العام (anon) والجلسة.
 *
 * **ذاكرة داخلية (أداء):**
 * `EncryptedSharedPreferences` يفكّ تشفير **كل قيمة** عبر Keystore في كل قراءة،
 * و`current()` تُنادى ٥٠+ مرة — بعضها داخل تركيب عناصر القوائم (كل رسالة في
 * الشات، كل خلية في شبكات الأصول) ⇒ مئات عمليات فكّ التشفير في الثانية، وهذا
 * وحده كان يُنتج تقطيعاً وثِقَلاً واضحاً في التمرير.
 *
 * الحلّ: نقرأ من الذاكرة، والقرص/Keystore **مرة واحدة** فقط (أول قراءة)، ثم
 * تُحدَّث النسخة عند `save` و`clear`. لا تتغيّر الجلسة إلا من داخل التطبيق
 * نفسه، فهي المخاطب الوحيد لهذا المخزن.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = create(context)

    /** نسخة الجلسة الحالية في الذاكرة — تُقرأ في كل نداء بلا لمس القرص. */
    @Volatile
    private var cache: Session? = null

    /** هل قرأنا القرص مرة واحدة على الأقل؟ */
    @Volatile
    private var loaded = false

    private fun create(context: Context): SharedPreferences = try {
        val key = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "addonstation_session",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (t: Throwable) {
        // احتياط: بعض الأجهزة القديمة/المحاكيات تُفشل Keystore — نتراجع لمخزن خاص عادي
        context.getSharedPreferences("addonstation_session_fallback", Context.MODE_PRIVATE)
    }

    fun save(session: Session) {
        prefs.edit()
            .putString(K_ACCESS, session.accessToken)
            .putString(K_REFRESH, session.refreshToken)
            .putString(K_USER, session.userId)
            .putString(K_EMAIL, session.email)
            .putLong(K_EXPIRES, session.expiresAtEpochSeconds)
            .apply()
        // الذاكرة أولاً: كل الشاشات ترى الجلسة الجديدة فوراً بلا إعادة قراءة القرص
        cache = session
        loaded = true
    }

    /**
     * الجلسة الحالية: من الذاكرة (سريعة)، والقرص مرة واحدة فقط لملء الذاكرة.
     * كانت هذه الدالة تفكّ تشفير ٥ قيم من Keystore في كل نداء — انظر الشرح أعلى الملف.
     */
    fun current(): Session? {
        if (!loaded) ensureLoaded()
        return cache
    }

    @Synchronized
    private fun ensureLoaded() {
        // قد يكون خيط آخر سبقنا داخل القفل نفسه
        if (loaded) return
        cache = readFromDisk()
        loaded = true
    }

    private fun readFromDisk(): Session? {
        val access = prefs.getString(K_ACCESS, null) ?: return null
        val refresh = prefs.getString(K_REFRESH, null) ?: return null
        val user = prefs.getString(K_USER, null) ?: return null
        return Session(
            accessToken = access,
            refreshToken = refresh,
            userId = user,
            email = prefs.getString(K_EMAIL, null),
            expiresAtEpochSeconds = prefs.getLong(K_EXPIRES, 0L),
        )
    }

    fun clear() {
        prefs.edit().clear().apply()
        cache = null
        loaded = true
    }

    private companion object {
        const val K_ACCESS = "access_token"
        const val K_REFRESH = "refresh_token"
        const val K_USER = "user_id"
        const val K_EMAIL = "email"
        const val K_EXPIRES = "expires_at"
    }
}
