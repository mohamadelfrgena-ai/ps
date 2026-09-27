package com.addonstation.mobile.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

/** Same backend the Astro build used (public anon key taken from the original bundle). */
object SupabaseConfig {
    const val URL = "https://hakxwvtdvsynrmkthatk.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhha3h3dnRkdnN5bnJta3RoYXRrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc0MDc0MTQsImV4cCI6MjEwMjk4MzQxNH0.fwlkrkow1qpkHfpeBSjmNe4VaDk_qvEXQZhZGNmzfSM"
}

class SupabaseException(message: String) : Exception(message)

data class Session(val accessToken: String, val refreshToken: String, val userId: String, val email: String)

class Supabase(context: Context) {
    private val prefs = context.getSharedPreferences("auth", Context.MODE_PRIVATE)
    private val http = OkHttpClient.Builder().connectTimeout(20, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val jsonType = "application/json".toMediaType()
    val json = Json { ignoreUnknownKeys = true }

    private val _session = MutableStateFlow(loadSession())
    val session: StateFlow<Session?> = _session

    private fun loadSession(): Session? {
        val at = prefs.getString("at", null) ?: return null
        return Session(at, prefs.getString("rt", "")!!, prefs.getString("uid", "")!!, prefs.getString("email", "")!!)
    }

    private fun saveSession(s: Session?) {
        prefs.edit().apply {
            if (s == null) clear() else {
                putString("at", s.accessToken); putString("rt", s.refreshToken)
                putString("uid", s.userId); putString("email", s.email)
            }
        }.apply()
        _session.value = s
    }

    private fun parseSession(o: JsonObject): Session {
        val user = o["user"]!!.jsonObject
        return Session(
            o["access_token"]!!.jsonPrimitive.content,
            o["refresh_token"]!!.jsonPrimitive.content,
            user["id"]!!.jsonPrimitive.content,
            user["email"]?.jsonPrimitive?.contentOrNull ?: "",
        )
    }

    private suspend fun call(req: Request.Builder, auth: Boolean = true, retry: Boolean = true): JsonElement =
        withContext(Dispatchers.IO) {
            val token = if (auth) _session.value?.accessToken ?: SupabaseConfig.ANON_KEY else SupabaseConfig.ANON_KEY
            val r = req.header("apikey", SupabaseConfig.ANON_KEY).header("Authorization", "Bearer $token").build()
            http.newCall(r).execute().use { res ->
                val body = res.body?.string().orEmpty()
                if (res.code == 401 && retry && auth && _session.value != null) {
                    if (refresh()) return@withContext call(req, auth, false)
                }
                if (!res.isSuccessful) {
                    val msg = runCatching {
                        val o = json.parseToJsonElement(body).jsonObject
                        (o["msg"] ?: o["message"] ?: o["error_description"] ?: o["error"])?.jsonPrimitive?.content
                    }.getOrNull()
                    throw SupabaseException(msg ?: "Error ${res.code}")
                }
                if (body.isBlank()) JsonNull else json.parseToJsonElement(body)
            }
        }

    private suspend fun refresh(): Boolean = runCatching {
        val rt = _session.value?.refreshToken ?: return false
        val o = call(
            Request.Builder().url("${SupabaseConfig.URL}/auth/v1/token?grant_type=refresh_token")
                .post(buildJsonObject { put("refresh_token", rt) }.toString().toRequestBody(jsonType)),
            auth = false, retry = false,
        ).jsonObject
        saveSession(parseSession(o)); true
    }.getOrElse { saveSession(null); false }

    // ---------- Auth ----------
    suspend fun signIn(email: String, password: String) {
        val o = call(
            Request.Builder().url("${SupabaseConfig.URL}/auth/v1/token?grant_type=password")
                .post(buildJsonObject { put("email", email); put("password", password) }.toString().toRequestBody(jsonType)),
            auth = false,
        ).jsonObject
        saveSession(parseSession(o))
    }

    /** Returns true when the user is signed in immediately (no email confirmation required). */
    suspend fun signUp(email: String, password: String, username: String): Boolean {
        val o = call(
            Request.Builder().url("${SupabaseConfig.URL}/auth/v1/signup").post(
                buildJsonObject {
                    put("email", email); put("password", password)
                    putJsonObject("data") { put("username", username) }
                }.toString().toRequestBody(jsonType)
            ),
            auth = false,
        ).jsonObject
        return if (o["access_token"] != null) { saveSession(parseSession(o)); true } else false
    }

    suspend fun resetPassword(email: String) {
        call(
            Request.Builder().url("${SupabaseConfig.URL}/auth/v1/recover")
                .post(buildJsonObject { put("email", email) }.toString().toRequestBody(jsonType)),
            auth = false,
        )
    }

    fun signOut() = saveSession(null)

    // ---------- PostgREST ----------
    suspend fun select(table: String, query: String): JsonArray =
        call(Request.Builder().url("${SupabaseConfig.URL}/rest/v1/$table?$query").get()).jsonArray

    suspend fun selectOne(table: String, query: String): JsonObject? = select(table, "$query&limit=1").firstOrNull()?.jsonObject

    suspend fun insert(table: String, body: JsonObject, upsert: Boolean = false) {
        val b = Request.Builder().url("${SupabaseConfig.URL}/rest/v1/$table")
            .header("Prefer", if (upsert) "resolution=merge-duplicates,return=minimal" else "return=minimal")
            .post(body.toString().toRequestBody(jsonType))
        call(b)
    }

    suspend fun update(table: String, filter: String, body: JsonObject) {
        call(
            Request.Builder().url("${SupabaseConfig.URL}/rest/v1/$table?$filter")
                .header("Prefer", "return=minimal").patch(body.toString().toRequestBody(jsonType))
        )
    }

    suspend fun rpc(name: String, body: JsonObject = JsonObject(emptyMap())): JsonElement =
        call(Request.Builder().url("${SupabaseConfig.URL}/rest/v1/rpc/$name").post(body.toString().toRequestBody(jsonType)))
}

// ---------- tiny JSON helpers ----------
fun JsonObject.str(k: String): String? = (this[k] as? JsonPrimitive)?.contentOrNull
fun JsonObject.num(k: String): Long = (this[k] as? JsonPrimitive)?.longOrNull ?: (this[k] as? JsonPrimitive)?.doubleOrNull?.toLong() ?: 0
fun JsonObject.bool(k: String): Boolean = (this[k] as? JsonPrimitive)?.booleanOrNull ?: false
fun JsonObject.obj(k: String): JsonObject? = this[k] as? JsonObject
