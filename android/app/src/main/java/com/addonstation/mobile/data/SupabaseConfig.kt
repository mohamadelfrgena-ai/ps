package com.addonstation.mobile.data

import com.addonstation.mobile.BuildConfig

/** إعدادات الاتصال بمشروع Supabase (المفتاح العام anon فقط — لا مفاتيح خدمة في التطبيق). */
data class SupabaseConfig(val url: String, val anonKey: String) {

    val isConfigured: Boolean get() = url.isNotBlank() && anonKey.isNotBlank()

    val restUrl: String get() = "${url.trimEnd('/')}/rest/v1"
    val authUrl: String get() = "${url.trimEnd('/')}/auth/v1"
    val storageUrl: String get() = "${url.trimEnd('/')}/storage/v1"
    val realtimeUrl: String
        get() {
            val base = url.trimEnd('/').replace("https://", "wss://").replace("http://", "ws://")
            return "$base/realtime/v1/websocket"
        }

    /** رابط عام لملف داخل مخزن عام (site-assets مثلاً). */
    fun publicUrl(bucket: String, path: String): String =
        "${storageUrl}/object/public/$bucket/${path.trimStart('/')}"

    /** رابط موقّع قصير الأجل يُبنى في SupabaseClient (يحتاج توكن المستخدم). */
    fun signedUrl(bucket: String, path: String): String =
        "${storageUrl}/object/sign/$bucket/${path.trimStart('/')}"

    fun projectRef(): String =
        url.trimEnd('/').substringAfter("https://", url.trimEnd('/')).substringBefore('.')

    companion object {
        /** القيم القادمة من gradle.properties ثم تُستكمل من شاشة الإعداد الأول إن كانت فارغة. */
        val fromBuild: SupabaseConfig = SupabaseConfig(
            url = BuildConfig.DEFAULT_SUPABASE_URL.trim(),
            anonKey = BuildConfig.DEFAULT_SUPABASE_ANON_KEY.trim(),
        )
    }
}
