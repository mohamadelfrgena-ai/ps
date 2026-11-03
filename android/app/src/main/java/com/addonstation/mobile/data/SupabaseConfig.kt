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
        "${storageUrl}/object/public/$bucket/${encodeStoragePath(normalizeStoragePath(path))}"

    /**
     * بعض مسارات الأصول في قاعدة البيانات مكرّرة المجلد — مثل
     * profiles/profiles/onefree.png ( سكربت التعبئة أضاف المجلد مرّتين).
     * الرابط الناتج يفشل 404 ⇒ تظل الخانة فارغة حتى تنتهي مهلة الطلب، وهو سبب
     * ظهور صور البروفايل والبانرات والأدوات ببطء شديد.
     * إن تكرّر المجلد الأول نُسقط واحداً منه؛ وما عدا ذلك لا يُمسّ.
     */
    fun normalizeStoragePath(path: String): String {
        val clean = path.trim().trimStart('/')
        val parts = clean.split('/')
        val folders = setOf("profiles", "banners", "items", "stickers", "site-assets")
        return if (parts.size >= 3 && parts[0] == parts[1] && parts[0] in folders) {
            parts.drop(1).joinToString("/")
        } else {
            clean
        }
    }

    /** رابط موقّع قصير الأجل يُبنى في SupabaseClient (يحتاج توكن المستخدم). */
    fun signedUrl(bucket: String, path: String): String =
        "${storageUrl}/object/sign/$bucket/${encodeStoragePath(path)}"

    /** معرّف المشروع (الجزء قبل .supabase.co) — يُستخدم في Realtime. */
    fun projectRef(): String =
        url.trimEnd('/').substringAfter("https://", url.trimEnd('/')).substringBefore('.')

    companion object {
        /**
         * ترميز المسار للاستخدام في الرابط: بعض الأسماء تحتوي مسافات
         * (مثل «Waguri-chan is Minecraft.jpg») فيرفض الطلب بلا ترميز.
         * الأجزاء المُرمّزة مسبقاً (%20) تُترك كما هي حتى لا تُرمّز مرتين.
         */
        fun encodeStoragePath(path: String): String =
            path.trimStart('/').split('/').joinToString("/") { segment ->
                if (segment.contains('%')) segment
                else java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
            }

        /** القيم القادمة من gradle.properties ثم تُستكمل من شاشة الإعداد الأول إن كانت فارغة. */
        val fromBuild: SupabaseConfig = SupabaseConfig(
            url = BuildConfig.DEFAULT_SUPABASE_URL.trim(),
            anonKey = BuildConfig.DEFAULT_SUPABASE_ANON_KEY.trim(),
        )
    }
}
