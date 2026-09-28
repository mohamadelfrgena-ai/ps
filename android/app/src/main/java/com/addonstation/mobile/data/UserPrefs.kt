package com.addonstation.mobile.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "addonstation_prefs")

/**
 * تفضيلات المستخدم المحلية: اللغة، الثيم، مفاتيح Supabase (إن لم تُمرّر في gradle.properties)،
 * ذاكرة مؤقتة للصورة الرمزية الأخيرة، والرموز التعبيرية المستخدمة حديثاً.
 */
class UserPrefs(private val context: Context) {

    val locale: Flow<String> = context.dataStore.data.map { it[K_LOCALE] ?: "en" }
    val darkTheme: Flow<Boolean> = context.dataStore.data.map { it[K_DARK] ?: true }
    val supabaseUrl: Flow<String> = context.dataStore.data.map { it[K_URL] ?: "" }
    val supabaseAnonKey: Flow<String> = context.dataStore.data.map { it[K_KEY] ?: "" }
    val recentEmojis: Flow<String> = context.dataStore.data.map { it[K_EMOJI] ?: "" }
    val cachedProfile: Flow<String> = context.dataStore.data.map { it[K_PROFILE] ?: "" }

    suspend fun setLocale(code: String) = context.dataStore.edit { it[K_LOCALE] = code }
    suspend fun setDarkTheme(dark: Boolean) = context.dataStore.edit { it[K_DARK] = dark }
    suspend fun setSupabase(url: String, anonKey: String) = context.dataStore.edit {
        it[K_URL] = url.trim().trimEnd('/')
        it[K_KEY] = anonKey.trim()
    }
    suspend fun setCachedProfile(json: String) = context.dataStore.edit { it[K_PROFILE] = json }

    suspend fun pushRecentEmoji(emoji: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[K_EMOJI] ?: "").split(' ').filter { it.isNotBlank() }
            val next = (listOf(emoji) + current.filter { it != emoji }).take(24)
            prefs[K_EMOJI] = next.joinToString(" ")
        }
    }

    suspend fun readSupabase(): Pair<String, String> {
        val prefs = context.dataStore.data.first()
        return (prefs[K_URL] ?: "") to (prefs[K_KEY] ?: "")
    }

    suspend fun readLocale(): String = context.dataStore.data.first()[K_LOCALE] ?: "en"

    private companion object {
        val K_LOCALE = stringPreferencesKey("locale")
        val K_DARK = booleanPreferencesKey("dark_theme")
        val K_URL = stringPreferencesKey("supabase_url")
        val K_KEY = stringPreferencesKey("supabase_anon_key")
        val K_EMOJI = stringPreferencesKey("recent_emojis")
        val K_PROFILE = stringPreferencesKey("cached_profile")
    }
}
