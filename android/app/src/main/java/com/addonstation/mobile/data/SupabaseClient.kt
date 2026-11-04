package com.addonstation.mobile.data

import com.addonstation.mobile.data.model.AuthUser
import com.addonstation.mobile.data.model.Session
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** نوع رابط البريد الذي أعاده Supabase إلى التطبيق بعد التحقق. */
enum class AuthEmailLinkKind {
    RECOVERY,
    SIGNUP_CONFIRMATION,
    OTHER,
}

/** نتيجة قبول رابط البريد مع إبقاء قرار الوجهة خارج عميل الشبكة. */
data class AuthEmailLinkResult(
    val accepted: Boolean,
    val kind: AuthEmailLinkKind,
)

/**
 * عميل Supabase خفيف مبني على OkHttp (بدون مكتبات خارجية إضافية):
 * Auth · PostgREST (REST/RPC) · Storage · Realtime.
 *
 * قواعد الأمان: التطبيق يحمل المفتاح العام فقط، وكل عملية حساسة تمرّ بدوال RPC على الخادم.
 */
class SupabaseClient(
    private val configProvider: () -> SupabaseConfig?,
    private val sessionStore: SessionStore,
) {

    val json: Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
        isLenient = true
        coerceInputValues = true
    }

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .callTimeout(90, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    /**
     * Separate client for admin bulk imports. Content objects can be hundreds
     * of MiB, so the normal 90-second request cap is intentionally unsuitable.
     * The call is still cancellable and has bounded connect/read/write timeouts.
     */
    private val largeUploadHttp: OkHttpClient = http.newBuilder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(5, TimeUnit.MINUTES)
        .writeTimeout(45, TimeUnit.MINUTES)
        .callTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private val refreshMutex = Mutex()

    fun okHttp(): OkHttpClient = http

    fun configOrNull(): SupabaseConfig? = configProvider()?.takeIf { it.isConfigured }

    fun config(): SupabaseConfig =
        configOrNull() ?: throw ApiError(ApiErrorKind.NOT_CONFIGURED)

    fun session(): Session? = sessionStore.current()

    /**
     * Stable account id for profile reads. Very old/recovery-created local
     * sessions can contain a blank stored userId even though the access JWT has
     * a valid `sub`; recover it locally without sending or exposing the token.
     */
    fun currentUserId(): String? {
        val current = sessionStore.current() ?: return null
        return current.userId.trim().takeIf(String::isNotBlank)
            ?: jwtClaims(current.accessToken)
                ?.get("sub")
                ?.jsonPrimitive
                ?.contentOrNull()
    }

    fun isSignedIn(): Boolean = sessionStore.current() != null

    // ------------------------------------------------------------------ Auth

    /** تسجيل حساب جديد. إن كان تأكيد البريد مفعّلاً في Supabase فلا تُرجَع جلسة ويجب تأكيد البريد. */
    suspend fun signUp(email: String, password: String, username: String): AuthUser {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
            put("data", buildJsonObject { put("username", username) })
        }
        // نعيد رابط تأكيد التسجيل إلى التطبيق نفسه. يضيف Supabase `type=signup`
        // إلى الرابط النهائي، وبذلك تستطيع الواجهة تمييزه عن `type=recovery`.
        val text = authPost(authPathWithRedirect("signup", EMAIL_REDIRECT), body, token = null)
        val obj = json.parseToJsonElement(text).jsonObject
        val user = obj["user"]?.jsonObject ?: obj.takeIf { it.containsKey("id") } ?: JsonObject(emptyMap())
        obj["access_token"]?.jsonPrimitive?.contentOrNull()?.let { access ->
            storeSession(obj, access)
        }
        return AuthUser(
            id = user["id"]?.jsonPrimitive?.contentOrNull().orEmpty(),
            email = user["email"]?.jsonPrimitive?.contentOrNull() ?: email,
            username = user["user_metadata"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull(),
            emailConfirmed = user["email_confirmed_at"]?.jsonPrimitive?.contentOrNull() != null,
        )
    }

    suspend fun signIn(email: String, password: String): AuthUser {
        val body = buildJsonObject {
            put("email", email)
            put("password", password)
        }
        val text = authPost("token?grant_type=password", body, token = null)
        val obj = json.parseToJsonElement(text).jsonObject
        val access = obj["access_token"]?.jsonPrimitive?.contentOrNull()
            ?: throw ApiError(ApiErrorKind.SERVER, "missing access_token")
        storeSession(obj, access)
        val user = obj["user"]?.jsonObject ?: JsonObject(emptyMap())
        return AuthUser(
            id = user["id"]?.jsonPrimitive?.contentOrNull().orEmpty(),
            email = user["email"]?.jsonPrimitive?.contentOrNull() ?: email,
            username = user["user_metadata"]?.jsonObject?.get("username")?.jsonPrimitive?.contentOrNull(),
            emailConfirmed = true,
        )
    }

    /**
     * تحديث الجلسة بالـ refresh token.
     *
     * **لا نُسجّل خروج المستخدم تلقائياً** إذا فشل التجديد، حتى لو رفض الخادم
     * التوكن: لا يُمسح المخزن ولا تتغيّر حالة الحساب إلا بطلب خروج صريح من صاحبه.
     * قد تفشل بعض الطلبات المحمية ما دام الخادم لا يقبل التوكن، لكن يبقى الحساب
     * محفوظاً على الجهاز ويمكن إعادة المحاولة أو الخروج يدوياً.
     */
    suspend fun refreshSession(): Boolean = refreshMutex.withLock {
        val current = sessionStore.current() ?: return false
        return try {
            val body = buildJsonObject { put("refresh_token", current.refreshToken) }
            val text = authPost("token?grant_type=refresh_token", body, token = null)
            val obj = json.parseToJsonElement(text).jsonObject
            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull() ?: return false
            storeSession(obj, access, fallbackEmail = current.email)
            true
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // لا نمسح جلسة الحساب ولا نُخرجه تلقائياً عند أي فشل في التجديد.
            false
        }
    }

    suspend fun signOut() {
        try {
            authPost("logout", buildJsonObject { }, token = sessionStore.current()?.accessToken)
        } catch (_: Throwable) {
            // الخروج المحلي يكفي حتى لو فشل الخادم
        }
        sessionStore.clear()
    }

    /**
     * إرسال رابط استعادة كلمة المرور.
     *
     * **redirect_to ضروري**: بدونه يبني Supabase الرابط على «Site URL» فيفتح
     * متصفّحاً لا التطبيق، ويبدو للمستخدم أن «الرابط لا يعمل». بقيمته يفتح
     * الرابط التطبيق مباشرةً على شاشة كلمة المرور الجديدة.
     * (يجب أن يكون `addonstation://recovery` مسموحاً في
     *  Authentication → URL Configuration → Redirect URLs، وإلا يتجاهله الخادم.)
     */
    suspend fun sendRecovery(email: String) {
        val body = buildJsonObject {
            put("email", email)
            put("redirect_to", RECOVERY_REDIRECT)
        }
        authPost("recover", body, token = null)
    }

    /**
     * إعادة إرسال رسالة تأكيد البريد.
     *
     * لماذا؟ إن كان «Confirm email» مفعّلاً في Supabase (وهو الافتراضي) فلا
     * تُرجِع `signup` جلسة، ويبقى المستخدم خارج التطبيق حتى يفتح رابط البريد.
     * هذه الدالة تُمكّنه من طلب رسالة جديدة من داخل التطبيق بدل البحث في البريد.
     */
    suspend fun resendConfirmation(email: String) {
        authPost(
            authPathWithRedirect("resend", EMAIL_REDIRECT),
            buildJsonObject {
                put("type", "signup")
                put("email", email)
            },
            token = null,
        )
    }

    suspend fun updatePassword(newPassword: String) {
        val body = buildJsonObject { put("password", newPassword) }
        exec(
            method = "PUT",
            url = "${config().authUrl}/user",
            token = sessionStore.current()?.accessToken,
            body = body.toString().toRequestBody(JSON_MEDIA),
        )
    }

    /** إرسال رمز استرداد (OTP) ثم التحقق منه لإنشاء جلسة — يُستخدم في تدفق addonstation://recovery. */
    suspend fun verifyRecoveryToken(email: String, token: String): Boolean {
        val body = buildJsonObject {
            put("type", "recovery")
            put("email", email)
            put("token", token)
        }
        return try {
            val text = authPost("verify", body, token = null)
            val obj = json.parseToJsonElement(text).jsonObject
            val access = obj["access_token"]?.jsonPrimitive?.contentOrNull() ?: return false
            storeSession(obj, access, fallbackEmail = email)
            true
        } catch (t: Throwable) {
            false
        }
    }

    /**
     * يقبل رابط بريد Supabase ويُرجع **نوعه** مع نتيجة التحقق.
     *
     * لا يجوز اختزال كل الروابط إلى استعادة كلمة المرور: رابط `type=recovery`
     * يفتح شاشة كلمة مرور جديدة، بينما `type=signup` يؤكد البريد ثم يجب أن يفتح
     * شاشة تسجيل الدخول. تدعم الدالة توكن الوصول الجاهز و`token_hash` والرمز
     * اليدوي، وتملأ الجلسة المؤقتة من حمولة JWT كما في التدفق السابق.
     */
    suspend fun acceptEmailLink(url: String): AuthEmailLinkResult {
        val params = linkParams(url)
        val type = params["type"]?.lowercase()
        val kind = when (type) {
            "recovery" -> AuthEmailLinkKind.RECOVERY
            "signup", "email", "email_confirmation" -> AuthEmailLinkKind.SIGNUP_CONFIRMATION
            else -> {
                // توافق مع روابط الاستعادة القديمة التي لم تكن تحمل type.
                val host = url.substringAfter("://", "")
                    .substringBefore('?')
                    .substringBefore('#')
                    .substringBefore('/')
                    .lowercase()
                if (type == null && host == "recovery") AuthEmailLinkKind.RECOVERY
                else AuthEmailLinkKind.OTHER
            }
        }
        fun result(accepted: Boolean) = AuthEmailLinkResult(accepted = accepted, kind = kind)

        // ١) توكن وصول جاهز في الرابط.
        val access = params["access_token"]
        if (!access.isNullOrBlank()) {
            saveTokenSession(access, params["refresh_token"].orEmpty())
            return result(true)
        }

        // ٢) هاش توكن ({{ .TokenHash }}) ⇒ نستبدله بجلسة.
        val tokenHash = params["token_hash"]
        if (!tokenHash.isNullOrBlank()) {
            return try {
                val body = buildJsonObject {
                    put("type", type ?: "recovery")
                    put("token_hash", tokenHash)
                }
                val obj = json.parseToJsonElement(authPost("verify", body, token = null)).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.contentOrNull()
                    ?: return result(false)
                saveTokenSession(newAccess, obj["refresh_token"]?.jsonPrimitive?.contentOrNull().orEmpty())
                result(true)
            } catch (_: Throwable) {
                result(false)
            }
        }

        // ٣) رمز تحقق مُدخل يدوياً (إن وُجد).
        val token = params["token"]
        if (!token.isNullOrBlank()) {
            return try {
                val body = buildJsonObject {
                    put("type", type ?: "recovery")
                    put("token", token)
                    put("email", params["email"].orEmpty())
                }
                val obj = json.parseToJsonElement(authPost("verify", body, token = null)).jsonObject
                val newAccess = obj["access_token"]?.jsonPrimitive?.contentOrNull()
                    ?: return result(false)
                saveTokenSession(newAccess, obj["refresh_token"]?.jsonPrimitive?.contentOrNull().orEmpty())
                result(true)
            } catch (_: Throwable) {
                result(false)
            }
        }
        return result(false)
    }

    /**
     * كل معاملات الرابط: **الجزء بعد # أولاً** (حيث تضع Supabase التوكنات)
     * ثم الاستعلام `?…` — فنقبل الصيغتين بلا افتراض مسبق.
     */
    private fun linkParams(url: String): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        listOf(
            url.substringAfter('?', "").substringBefore('#'),
            url.substringAfter('#', ""),
        ).forEach { raw ->
            raw.split('&').forEach { pair ->
                val idx = pair.indexOf('=')
                if (idx > 0) {
                    val key = pair.substring(0, idx)
                    val value = pair.substring(idx + 1)
                    if (key.isNotBlank() && value.isNotBlank()) out[key] = value
                }
            }
        }
        return out
    }

    /** حمولة JWT (base64) — نقرأ منها المعرّف والبريد ومدة الصلاحية. */
    private fun jwtClaims(accessToken: String): JsonObject? {
        val parts = accessToken.split('.')
        if (parts.size < 2) return null
        return try {
            val payload = String(
                android.util.Base64.decode(parts[1], android.util.Base64.URL_SAFE or android.util.Base64.NO_PADDING or android.util.Base64.NO_WRAP),
                Charsets.UTF_8,
            )
            json.parseToJsonElement(payload).jsonObject
        } catch (_: Throwable) {
            null
        }
    }

    private fun saveTokenSession(access: String, refresh: String) {
        val claims = jwtClaims(access)
        sessionStore.save(
            Session(
                accessToken = access,
                refreshToken = refresh,
                userId = claims?.get("sub")?.jsonPrimitive?.contentOrNull().orEmpty(),
                email = claims?.get("email")?.jsonPrimitive?.contentOrNull(),
                expiresAtEpochSeconds = claims?.get("exp")?.jsonPrimitive?.contentOrNull()?.toLongOrNull()
                    ?: (System.currentTimeMillis() / 1000L + 3600L),
            )
        )
    }

    private suspend fun authPost(path: String, body: JsonObject, token: String?): String =
        exec(
            method = "POST",
            url = "${config().authUrl}/$path",
            token = token,
            body = body.toString().toRequestBody(JSON_MEDIA),
        )

    private fun authPathWithRedirect(path: String, redirect: String): String =
        "$path?redirect_to=${URLEncoder.encode(redirect, Charsets.UTF_8.name())}"

    private fun storeSession(obj: JsonObject, access: String, fallbackEmail: String? = null) {
        val user = obj["user"]?.jsonObject
        sessionStore.save(
            Session(
                accessToken = access,
                refreshToken = obj["refresh_token"]?.jsonPrimitive?.contentOrNull() ?: "",
                userId = user?.get("id")?.jsonPrimitive?.contentOrNull().orEmpty(),
                email = user?.get("email")?.jsonPrimitive?.contentOrNull() ?: fallbackEmail,
                expiresAtEpochSeconds = System.currentTimeMillis() / 1000L +
                    (obj["expires_in"]?.jsonPrimitive?.contentOrNull()?.toLongOrNull() ?: 3600L),
            )
        )
    }

    // ------------------------------------------------------------- PostgREST

    suspend fun select(
        table: String,
        query: String = "select=*",
        token: String? = sessionStore.current()?.accessToken,
    ): JsonElement = withContext(Dispatchers.IO) {
        val text = exec("GET", "${config().restUrl}/$table?$query", token, null)
        if (text.isBlank()) JsonArray(emptyList()) else json.parseToJsonElement(text)
    }

    suspend fun insert(table: String, body: JsonElement): JsonElement = withContext(Dispatchers.IO) {
        val text = exec(
            "POST", "${config().restUrl}/$table",
            sessionStore.current()?.accessToken, body.toString().toRequestBody(JSON_MEDIA),
            extra = mapOf("Prefer" to "return=representation"),
        )
        if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
    }

    suspend fun update(table: String, query: String, body: JsonElement): JsonElement = withContext(Dispatchers.IO) {
        val text = exec(
            "PATCH", "${config().restUrl}/$table?$query",
            sessionStore.current()?.accessToken, body.toString().toRequestBody(JSON_MEDIA),
            extra = mapOf("Prefer" to "return=representation"),
        )
        if (text.isBlank()) JsonNull else json.parseToJsonElement(text)
    }

    suspend fun delete(table: String, query: String) {
        withContext(Dispatchers.IO) {
            exec("DELETE", "${config().restUrl}/$table?$query", sessionStore.current()?.accessToken, null)
        }
    }

    /** استدعاء دالة قاعدة بيانات (RPC). */
    suspend fun rpc(function: String, params: Map<String, JsonElement?> = emptyMap(), signedOnly: Boolean = false): JsonElement? = withContext(Dispatchers.IO) {
        val token = sessionStore.current()?.accessToken
        if (signedOnly && token == null) throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        val body = buildJsonObject {
            params.forEach { (key, value) -> put(key, value ?: JsonNull) }
        }
        val text = exec(
            "POST", "${config().restUrl}/rpc/$function", token,
            body.toString().toRequestBody(JSON_MEDIA),
        )
        if (text.isBlank() || text == "null") null else json.parseToJsonElement(text)
    }

    /**
     * RPC يستعمل لقطة جلسة محدّدة في الترويسة دون استبدالها تلقائياً عند 401.
     * يفيد العمليات التي تحمل access/refresh tokens نفسها داخل body.
     */
    suspend fun rpcWithToken(
        function: String,
        params: Map<String, JsonElement?>,
        accessToken: String,
    ): JsonElement? = withContext(Dispatchers.IO) {
        if (accessToken.isBlank()) throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        val body = buildJsonObject {
            params.forEach { (key, value) -> put(key, value ?: JsonNull) }
        }
        val text = exec(
            method = "POST",
            url = "${config().restUrl}/rpc/$function",
            token = accessToken,
            body = body.toString().toRequestBody(JSON_MEDIA),
            allowRefresh = false,
        )
        if (text.isBlank() || text == "null") null else json.parseToJsonElement(text)
    }

    // --------------------------------------------------------------- Storage

    fun publicUrl(bucket: String, path: String): String =
        "${config().storageUrl}/object/public/$bucket/${path.trimStart('/')}"

    suspend fun upload(bucket: String, path: String, bytes: ByteArray, contentType: String, upsert: Boolean = true) {
        withContext(Dispatchers.IO) {
            val media = contentType.toMediaType()
            exec(
                "POST", "${config().storageUrl}/object/$bucket/${path.trimStart('/')}",
                sessionStore.current()?.accessToken,
                bytes.toRequestBody(media),
                extra = mapOf("x-upsert" to upsert.toString(), "cache-control" to "3600"),
            )
        }
    }

    /**
     * Stream a large file directly from disk to Storage without ever creating a
     * ByteArray of the whole addon/world. This is the upload path used by ZIP
     * batch imports (individual objects are capped by the bucket at 512 MiB).
     */
    suspend fun uploadFile(
        bucket: String,
        path: String,
        file: File,
        contentType: String,
        upsert: Boolean = true,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        require(file.isFile && file.length() > 0L) { "upload_file_missing" }
        val cfg = config()
        val media = contentType.toMediaType()
        val expectedLength = file.length()
        val body = object : RequestBody() {
            override fun contentType() = media
            override fun contentLength(): Long = expectedLength

            override fun writeTo(sink: BufferedSink) {
                var sent = 0L
                var lastReported = 0L
                var lastReportAt = 0L
                val buffer = ByteArray(256 * 1024)
                file.inputStream().buffered(buffer.size).use { input ->
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        if (read == 0) continue
                        sink.write(buffer, 0, read)
                        sent += read
                        val now = android.os.SystemClock.elapsedRealtime()
                        if (sent == expectedLength || sent - lastReported >= 512 * 1024 || now - lastReportAt >= 250L) {
                            onProgress(sent, expectedLength)
                            lastReported = sent
                            lastReportAt = now
                        }
                    }
                }
                if (sent != expectedLength) throw IOException("upload_file_size_changed:$sent/$expectedLength")
            }
        }

        val session = sessionStore.current() ?: throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        if (session.expiresAtEpochSeconds <= System.currentTimeMillis() / 1000L + 120L) {
            refreshSession()
        }

        fun request(token: String?): Request = Request.Builder()
            .url("${cfg.storageUrl}/object/$bucket/${path.trimStart('/')}")
            .header("apikey", cfg.anonKey)
            .apply {
                token?.takeIf(String::isNotBlank)?.let { header("Authorization", "Bearer $it") }
            }
            .header("Content-Type", contentType)
            .header("x-upsert", upsert.toString())
            .header("cache-control", "3600")
            .post(body)
            .build()

        var result = executeLargeUpload(request(sessionStore.current()?.accessToken))
        if (result.code == 401 && refreshSession()) {
            result = executeLargeUpload(request(sessionStore.current()?.accessToken))
        }
        if (result.code !in 200..299) {
            throw apiErrorFromMessage(extractMessage(result.body), result.code)
        }
    }

    /** Remove uploaded batch objects after a confirmed pre-commit failure/cancel. */
    suspend fun removeObjects(bucket: String, paths: List<String>) {
        paths.filter(String::isNotBlank).distinct().chunked(100).forEach { chunk ->
            val body = buildJsonObject {
                put("prefixes", JsonArray(chunk.map { JsonPrimitive(it) }))
            }
            exec(
                method = "DELETE",
                url = "${config().storageUrl}/object/$bucket",
                token = sessionStore.current()?.accessToken,
                body = body.toString().toRequestBody(JSON_MEDIA),
            )
        }
    }

    /** رابط موقّع قصير العمر (60 ثانية للمحتوى والإيصالات، 300 لوسائط الشات). */
    suspend fun signedUrl(bucket: String, path: String, expiresInSeconds: Int = 60): String = withContext(Dispatchers.IO) {
        val body = buildJsonObject { put("expiresIn", expiresInSeconds) }
        val text = exec(
            "POST", "${config().storageUrl}/object/sign/$bucket/${path.trimStart('/')}",
            sessionStore.current()?.accessToken,
            body.toString().toRequestBody(JSON_MEDIA),
        )
        val obj = json.parseToJsonElement(text).jsonObject
        val signed = obj["signedURL"]?.jsonPrimitive?.contentOrNull()
            ?: obj["signedUrl"]?.jsonPrimitive?.contentOrNull()
            ?: throw ApiError(ApiErrorKind.NO_PERMISSION, "no signed url")
        if (signed.startsWith("http")) signed else "${config().storageUrl}$signed"
    }

    suspend fun listObjects(bucket: String, prefix: String = "", token: String? = sessionStore.current()?.accessToken): JsonElement = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("prefix", prefix)
            put("limit", 200)
            put("offset", 0)
            put("sortBy", buildJsonObject {
                put("column", "name")
                put("order", "asc")
            })
        }
        val text = exec(
            "POST", "${config().storageUrl}/object/list/$bucket", token,
            body.toString().toRequestBody(JSON_MEDIA),
        )
        if (text.isBlank()) JsonArray(emptyList()) else json.parseToJsonElement(text)
    }

    /**
     * Streams a protected download to a temporary file and only replaces the
     * destination after the full response has been received and validated.
     * This avoids holding addon archives in memory and never leaves a partial
     * file that Android/Minecraft could try to open.
     */
    suspend fun downloadToFile(url: String, destination: java.io.File, expectedSize: Long? = null): Long =
        withContext(Dispatchers.IO) {
            val parent = destination.parentFile
                ?: throw ApiError(ApiErrorKind.SERVER, "download_directory_missing")
            if ((!parent.exists() && !parent.mkdirs()) || !parent.isDirectory) {
                throw ApiError(ApiErrorKind.SERVER, "download_directory_unavailable")
            }
            val temporary = java.io.File.createTempFile(".${destination.name}.", ".part", parent)
            try {
                val request = Request.Builder().url(url).get().build()
                val copied = http.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 404) throw ApiError(ApiErrorKind.FILE_NOT_UPLOADED, "file_not_uploaded", 404)
                        throw apiErrorFromMessage("download_failed", response.code)
                    }
                    val body = response.body
                        ?: throw ApiError(ApiErrorKind.SERVER, "download_body_missing")
                    val coroutine = currentCoroutineContext()
                    var total = 0L
                    body.byteStream().buffered().use { input ->
                        temporary.outputStream().buffered().use { output ->
                            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                            while (true) {
                                coroutine.ensureActive()
                                val read = input.read(buffer)
                                if (read < 0) break
                                if (read == 0) continue
                                output.write(buffer, 0, read)
                                total += read
                            }
                        }
                    }
                    total
                }
                if (copied <= 0L) throw ApiError(ApiErrorKind.SERVER, "download_empty")
                if (expectedSize != null && expectedSize > 0L && copied != expectedSize) {
                    throw ApiError(ApiErrorKind.SERVER, "download_size_mismatch:$copied/$expectedSize")
                }
                if (destination.exists() && !destination.delete()) {
                    throw ApiError(ApiErrorKind.SERVER, "download_destination_busy")
                }
                if (!temporary.renameTo(destination)) {
                    temporary.copyTo(destination, overwrite = true)
                    temporary.delete()
                }
                copied
            } finally {
                if (temporary.exists()) temporary.delete()
            }
        }

    suspend fun downloadBytes(url: String): ByteArray {
        val request = Request.Builder().url(url).get().build()
        return withContext(Dispatchers.IO) {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw apiErrorFromMessage("download_failed", response.code)
                }
                response.body?.bytes() ?: ByteArray(0)
            }
        }
    }

    // ------------------------------------------------------------------ HTTP

    private data class HttpResult(val code: Int, val body: String) {
        val isSuccessful: Boolean get() = code in 200..299
    }

    /**
     * جسر OkHttp غير حاجب: لا يحتل خيط Dispatchers.IO أثناء انتظار الشبكة،
     * وإلغاء coroutine (مثل مهلة الإقلاع) يلغي المقبس فوراً بدلاً من انتظار
     * مهلة HTTP التي قد تصل إلى 90 ثانية.
     */
    private suspend fun executeRequest(request: Request): HttpResult =
        suspendCancellableCoroutine { continuation ->
            val call = http.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            HttpResult(it.code, it.body?.string().orEmpty())
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }

    /** Async OkHttp bridge: coroutine cancellation immediately cancels a long upload socket. */
    private suspend fun executeLargeUpload(request: Request): HttpResult =
        suspendCancellableCoroutine { continuation ->
            val call = largeUploadHttp.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }

                override fun onResponse(call: Call, response: Response) {
                    try {
                        val result = response.use {
                            HttpResult(it.code, it.body?.string().orEmpty())
                        }
                        if (continuation.isActive) continuation.resume(result)
                    } catch (error: Throwable) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }

    /** @param token توكن المستخدم (JWT) فقط — لا تُمرّر مفاتيح المشروع هنا. null = طلب عام بهوية anon. */
    private suspend fun exec(
        method: String,
        url: String,
        token: String?,
        body: RequestBody?,
        extra: Map<String, String> = emptyMap(),
        allowRefresh: Boolean = true,
    ): String {
        val cfg = config()
        val builder = Request.Builder().url(url)
        // المفتاح العام يمرّ في ترويسة apikey فقط.
        // ملاحظة مهمة (تحديث Supabase 2025/2026): المفاتيح الجديدة sb_publishable_... / sb_secret_...
        // ليست JWT، ولا يجوز وضعها في Authorization — بل يُترك فارغاً ما لم يوجد توكن مستخدم.
        builder.header("apikey", cfg.anonKey)
        val bearer = token?.takeIf { it.isNotBlank() }
        if (bearer != null) builder.header("Authorization", "Bearer $bearer")
        builder.header("Accept", "application/json")
        body?.contentType()?.let { builder.header("Content-Type", it.toString()) }
        extra.forEach { (k, v) -> builder.header(k, v) }
        when (method) {
            "GET" -> builder.get()
            "POST" -> builder.post(body ?: EMPTY_BODY)
            "PATCH" -> builder.patch(body ?: EMPTY_BODY)
            "PUT" -> builder.put(body ?: EMPTY_BODY)
            "DELETE" -> builder.delete(body)
        }

        var response = executeRequest(builder.build())
        if (response.code == 401 && allowRefresh && token != null && refreshSession()) {
            val fresh = sessionStore.current()?.accessToken
            val retry = builder.header("Authorization", "Bearer ${fresh ?: cfg.anonKey}").build()
            response = executeRequest(retry)
        }
        if (!response.isSuccessful) {
            throw apiErrorFromMessage(extractMessage(response.body), response.code)
        }
        return response.body
    }

    private fun extractMessage(text: String): String {
        if (text.isBlank()) return ""
        return try {
            val obj = json.parseToJsonElement(text).jsonObject
            listOf("message", "msg", "error_description", "error", "details", "hint")
                .firstNotNullOfOrNull { key -> obj[key]?.jsonPrimitive?.contentOrNull() }
                ?: text.take(300)
        } catch (t: Throwable) {
            text.take(300)
        }
    }

    private fun JsonPrimitive.contentOrNull(): String? =
        if (this is JsonNull) null else content.takeIf { it.isNotBlank() && it != "null" }

    private companion object {
        val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        /**
         * الرابط الذي يفتح التطبيق بعد الضغط على رابط البريد.
         * يجب إضافته في لوحة Supabase: Authentication → URL Configuration →
         * Redirect URLs، وإلا يتجاهل الخادم redirect_to ويعود إلى Site URL.
         * يميّز التطبيق التدفقات من معامل `type` الذي يضيفه Supabase.
         */
        const val RECOVERY_REDIRECT = "addonstation://recovery"
        const val EMAIL_REDIRECT = RECOVERY_REDIRECT
        val EMPTY_BODY: RequestBody = ByteArray(0).toRequestBody(null)
    }
}
