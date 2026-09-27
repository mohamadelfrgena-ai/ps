package com.addonstation.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Same public client the website already ships. The key is the Supabase anon
 * key, not a service-role secret. Balance changes still happen only in RPCs.
 */
object SupabaseConfig {
    const val URL = "https://hakxwvtdvsynrmkthatk.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhha3h3dnRkdnN5bnJta3RoYXRrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc0MDc0MTQsImV4cCI6MjEwMjk4MzQxNH0.fwlkrkow1qpkHfpeBSjmNe4VaDk_qvEXQZhZGNmzfSM"
}

class SupabaseClient(context: Context) {
    private val prefs = context.getSharedPreferences("addonstation", Context.MODE_PRIVATE)
    private var refreshed = false

    fun session(): Session? {
        val token = prefs.getString("access_token", null) ?: return null
        val userId = prefs.getString("user_id", null) ?: return null
        return Session(
            accessToken = token,
            refreshToken = prefs.getString("refresh_token", "").orEmpty(),
            userId = userId,
            email = prefs.getString("email", "").orEmpty(),
            username = prefs.getString("username", "").orEmpty()
        )
    }

    fun saveSession(json: JSONObject) {
        val user = json.optJSONObject("user") ?: JSONObject()
        val meta = user.optJSONObject("user_metadata") ?: JSONObject()
        prefs.edit()
            .putString("access_token", json.s("access_token"))
            .putString("refresh_token", json.s("refresh_token"))
            .putString("user_id", user.s("id"))
            .putString("email", user.s("email"))
            .putString("username", meta.s("username"))
            .apply()
    }

    fun clearSession() {
        prefs.edit()
            .remove("access_token")
            .remove("refresh_token")
            .remove("user_id")
            .remove("email")
            .remove("username")
            .apply()
    }

    fun pref(key: String, fallback: String): String = prefs.getString(key, fallback) ?: fallback
    fun putPref(key: String, value: String) = prefs.edit().putString(key, value).apply()
    fun flag(key: String): Boolean = prefs.getBoolean(key, false)
    fun putFlag(key: String, value: Boolean) = prefs.edit().putBoolean(key, value).apply()

    fun signIn(email: String, password: String): Session {
        val json = auth("token?grant_type=password", JSONObject().put("email", email).put("password", password))
        saveSession(json)
        return session() ?: throw ApiException(0, "تعذر حفظ الجلسة")
    }

    fun signUp(email: String, password: String, username: String): JSONObject {
        val data = JSONObject().put("username", username).put("avatar_url", "/imgs/profiles/onefree.png")
        val body = JSONObject().put("email", email).put("password", password).put("data", data)
        val json = auth("signup", body)
        if (json.has("access_token")) saveSession(json)
        return json
    }

    fun recover(email: String) {
        auth("recover", JSONObject().put("email", email))
    }

    fun updatePassword(password: String) {
        call("PUT", "/auth/v1/user", JSONObject().put("password", password).toString().toByteArray(), "application/json", user = true)
    }

    fun signOut() {
        try {
            call("POST", "/auth/v1/logout", "{}".toByteArray(), "application/json", user = true)
        } catch (_: Exception) {
        }
        clearSession()
    }

    fun select(table: String, query: String): JSONArray {
        val path = "/rest/v1/$table?$query"
        val raw = call("GET", path, null, null, user = false).body
        return jsonArray(raw)
    }

    fun insert(table: String, body: JSONObject): JSONArray {
        val raw = call(
            "POST",
            "/rest/v1/$table",
            body.toString().toByteArray(),
            "application/json",
            user = true,
            headers = mapOf("Prefer" to "return=representation")
        ).body
        return jsonArray(raw)
    }

    fun update(table: String, query: String, body: JSONObject): JSONArray {
        val raw = call(
            "PATCH",
            "/rest/v1/$table?$query",
            body.toString().toByteArray(),
            "application/json",
            user = true,
            headers = mapOf("Prefer" to "return=representation")
        ).body
        return jsonArray(raw)
    }

    fun delete(table: String, query: String) {
        call("DELETE", "/rest/v1/$table?$query", null, null, user = true)
    }

    fun rpc(name: String, body: JSONObject = JSONObject()): String {
        return call(
            "POST",
            "/rest/v1/rpc/$name",
            body.toString().toByteArray(),
            "application/json",
            user = true
        ).body
    }

    fun upload(bucket: String, path: String, bytes: ByteArray, mime: String) {
        val encoded = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        call("POST", "/storage/v1/object/$bucket/$encoded", bytes, mime, user = true)
    }

    fun publicUrl(bucket: String, path: String): String =
        "${SupabaseConfig.URL}/storage/v1/object/public/$bucket/$path"

    fun signedUrl(bucket: String, path: String, seconds: Int = 60): String {
        val encoded = path.split("/").joinToString("/") { URLEncoder.encode(it, "UTF-8") }
        val raw = call(
            "POST",
            "/storage/v1/object/sign/$bucket/$encoded",
            JSONObject().put("expiresIn", seconds).toString().toByteArray(),
            "application/json",
            user = true
        ).body
        val signed = JSONObject(raw).s("signedURL").ifBlank { JSONObject(raw).s("signedUrl") }
        return if (signed.startsWith("http")) signed else "${SupabaseConfig.URL}/storage/v1$signed"
    }

    private fun auth(path: String, body: JSONObject): JSONObject {
        val result = call("POST", "/auth/v1/$path", body.toString().toByteArray(), "application/json", user = false, anonOnly = true)
        if (result.body.isBlank()) return JSONObject()
        return JSONObject(result.body)
    }

    private data class Raw(val status: Int, val body: String)

    private fun call(
        method: String,
        path: String,
        body: ByteArray?,
        contentType: String?,
        user: Boolean,
        anonOnly: Boolean = false,
        headers: Map<String, String> = emptyMap(),
        retry: Boolean = true
    ): Raw {
        val token = if (anonOnly) SupabaseConfig.ANON_KEY else session()?.accessToken ?: SupabaseConfig.ANON_KEY
        val connection = (URL(SupabaseConfig.URL + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 12000
            readTimeout = 20000
            setRequestProperty("apikey", SupabaseConfig.ANON_KEY)
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("Accept", "application/json")
            headers.forEach { (key, value) -> setRequestProperty(key, value) }
            if (contentType != null && body != null) {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
                outputStream.use { it.write(body) }
            }
        }
        val status = connection.responseCode
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        val text = stream?.use { BufferedReader(InputStreamReader(it)).readText() }.orEmpty()
        connection.disconnect()
        if (status == 401 && retry && !anonOnly && refresh()) {
            return call(method, path, body, contentType, user, anonOnly, headers, retry = false)
        }
        if (status !in 200..299) {
            val message = runCatching { JSONObject(text).s("message", text) }.getOrDefault(text.ifBlank { "HTTP $status" })
            val code = runCatching { JSONObject(text).s("code") }.getOrNull()
            throw ApiException(status, message.ifBlank { "HTTP $status" }, code)
        }
        return Raw(status, text)
    }

    private fun refresh(): Boolean {
        if (refreshed) return false
        val refreshToken = prefs.getString("refresh_token", null) ?: return false
        refreshed = true
        return try {
            val json = auth("token?grant_type=refresh_token", JSONObject().put("refresh_token", refreshToken))
            if (!json.has("access_token")) return false
            saveSession(json)
            refreshed = false
            true
        } catch (_: Exception) {
            false
        }
    }
}
