package com.addonstation.mobile.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.addonstation.mobile.data.model.Session

/**
 * تخزين الجلسة في مخزن مشفّر (Android Keystore) كما تطلب وثيقة الحماية §3.4.
 * لا يُخزَّن أي مفتاح خدمة في التطبيق — فقط المفتاح العام (anon) والجلسة.
 */
class SessionStore(context: Context) {

    private val prefs: SharedPreferences = create(context)

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
    }

    fun current(): Session? {
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

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val K_ACCESS = "access_token"
        const val K_REFRESH = "refresh_token"
        const val K_USER = "user_id"
        const val K_EMAIL = "email"
        const val K_EXPIRES = "expires_at"
    }
}
