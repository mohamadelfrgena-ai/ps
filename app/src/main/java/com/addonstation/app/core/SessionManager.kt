package com.addonstation.app.core

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "addonstation")

/** Persisted app state: session, language, theme, device IP, backend override. */
class SessionManager(private val context: Context) {

    private object K {
        val ACCESS = stringPreferencesKey("access_token")
        val REFRESH = stringPreferencesKey("refresh_token")
        val EXPIRES_AT = longPreferencesKey("expires_at_ms")
        val USER_ID = stringPreferencesKey("user_id")
        val USER_EMAIL = stringPreferencesKey("user_email")
        val LANGUAGE = stringPreferencesKey("language") // ar | en | es | pt | fr
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val DEVICE_TYPE = stringPreferencesKey("device_type") // ps4 | ps5
        val DEVICE_IP = stringPreferencesKey("device_ip")
        val SB_URL = stringPreferencesKey("sb_url")
        val SB_KEY = stringPreferencesKey("sb_key")
        val NOTIFS = booleanPreferencesKey("notifications_enabled")
        val WELCOME = booleanPreferencesKey("welcome_done")
    }

    val accessToken: Flow<String?> = context.dataStore.data.map { it[K.ACCESS] }
    val refreshToken: Flow<String?> = context.dataStore.data.map { it[K.REFRESH] }
    val userId: Flow<String?> = context.dataStore.data.map { it[K.USER_ID] }
    val language: Flow<String> = context.dataStore.data.map { it[K.LANGUAGE] ?: "ar" }
    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[K.DARK_THEME] ?: true }
    val deviceType: Flow<String?> = context.dataStore.data.map { it[K.DEVICE_TYPE] }
    val deviceIp: Flow<String?> = context.dataStore.data.map { it[K.DEVICE_IP] }
    val notificationsEnabled: Flow<Boolean> = context.dataStore.data.map { it[K.NOTIFS] ?: true }
    val welcomeDone: Flow<Boolean> = context.dataStore.data.map { it[K.WELCOME] ?: false }

    suspend fun backendConfig(): BackendConfig {
        val p = context.dataStore.data.first()
        val url = p[K.SB_URL]?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_SUPABASE_URL
        val key = p[K.SB_KEY]?.takeIf { it.isNotBlank() } ?: Constants.DEFAULT_SUPABASE_ANON_KEY
        return BackendConfig(url, key)
    }

    suspend fun saveSession(s: AuthSession) {
        context.dataStore.edit {
            it[K.ACCESS] = s.accessToken
            it[K.REFRESH] = s.refreshToken
            it[K.EXPIRES_AT] = System.currentTimeMillis() + s.expiresIn * 1000
            s.user?.id?.let { id -> it[K.USER_ID] = id }
            s.user?.email?.let { e -> it[K.USER_EMAIL] = e }
        }
    }

    suspend fun clearSession() {
        context.dataStore.edit {
            it.remove(K.ACCESS); it.remove(K.REFRESH); it.remove(K.EXPIRES_AT)
            it.remove(K.USER_ID); it.remove(K.USER_EMAIL)
        }
    }

    suspend fun isSessionFresh(): Boolean {
        val p = context.dataStore.data.first()
        val exp = p[K.EXPIRES_AT] ?: 0L
        return !p[K.ACCESS].isNullOrBlank() && System.currentTimeMillis() < exp - 60_000
    }

    suspend fun setLanguage(v: String) = context.dataStore.edit { it[K.LANGUAGE] = v }
    suspend fun setDarkTheme(v: Boolean) = context.dataStore.edit { it[K.DARK_THEME] = v }
    suspend fun setDevice(type: String?, ip: String?) {
        context.dataStore.edit {
            if (type == null) it.remove(K.DEVICE_TYPE) else it[K.DEVICE_TYPE] = type
            if (ip == null) it.remove(K.DEVICE_IP) else it[K.DEVICE_IP] = ip
        }
    }
    suspend fun setBackend(url: String?, key: String?) {
        context.dataStore.edit {
            if (url.isNullOrBlank()) it.remove(K.SB_URL) else it[K.SB_URL] = url.trim()
            if (key.isNullOrBlank()) it.remove(K.SB_KEY) else it[K.SB_KEY] = key.trim()
        }
    }
    suspend fun setNotifications(v: Boolean) = context.dataStore.edit { it[K.NOTIFS] = v }
    suspend fun setWelcomeDone() = context.dataStore.edit { it[K.WELCOME] = true }
}
