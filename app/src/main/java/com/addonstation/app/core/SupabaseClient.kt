package com.addonstation.app.core

import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.headers
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.request
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BackendException(message: String, val status: Int = -1) : Exception(message)

data class BackendConfig(val url: String, val anonKey: String)

/**
 * Minimal Supabase client (PostgREST + GoTrue + Storage + RPC) over Ktor.
 * Mirrors exactly the queries the original web app performs.
 */
class SupabaseBackend(
    val config: BackendConfig,
    var tokenProvider: () -> String? = { null }
) {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
    }

    val http = HttpClient(OkHttp) {
        install(ContentNegotiation) { json(this@SupabaseBackend.json) }
        install(WebSockets) { pingInterval = 20_000 }
    }

    @PublishedApi
    internal val base: String get() = config.url.trimEnd('/')

    @PublishedApi
    internal fun HttpRequestBuilder.authHeaders() {
        header("apikey", config.anonKey)
        header(HttpHeaders.Authorization, "Bearer ${tokenProvider() ?: config.anonKey}")
    }

    @PublishedApi
    internal suspend fun ensureOk(res: HttpResponse): HttpResponse {
        if (res.status.isSuccess()) return res
        val text = try {
            res.bodyAsText()
        } catch (_: Exception) { "" }
        val msg = try {
            json.parseToJsonElement(text).jsonObject["message"]?.jsonPrimitive?.content
        } catch (_: Exception) { null }
        throw BackendException(msg?.takeIf { it.isNotBlank() } ?: "Request failed (${res.status.value})", res.status.value)
    }

    // ------------------------------------------------------------------ REST
    suspend inline fun <reified T> restGet(
        table: String,
        select: String = "*",
        filters: Map<String, String> = emptyMap(),
        order: String? = null,
        ascending: Boolean = false,
        limit: Int? = null,
        count: Boolean = false
    ): T {
        val res = http.get("$base/rest/v1/$table") {
            authHeaders()
            parameter("select", select)
            filters.forEach { (k, v) -> parameter(k, v) }
            if (order != null) parameter("order", "$order.${if (ascending) "asc" else "desc"}")
            if (limit != null) parameter("limit", limit.toString())
            if (count) header("Prefer", "count=exact")
        }
        ensureOk(res)
        val text = res.bodyAsText()
        if (T::class == Unit::class) return Unit as T
        return json.decodeFromString(text)
    }

    /** Exact row count via `Prefer: count=exact` + Content-Range header. */
    suspend fun restCount(table: String, filters: Map<String, String> = emptyMap()): Int {
        val res = http.get("$base/rest/v1/$table") {
            authHeaders()
            parameter("select", "id")
            parameter("limit", "1")
            filters.forEach { (k, v) -> parameter(k, v) }
            header("Prefer", "count=exact")
        }
        ensureOk(res)
        val range = res.headers["Content-Range"] ?: return 0
        return range.substringAfter("/").toIntOrNull() ?: 0
    }

    suspend inline fun <reified T> restPost(
        table: String,
        body: Any,
        upsert: Boolean = false,
        onConflict: String? = null
    ): T {
        val res = http.post("$base/rest/v1/$table") {
            authHeaders()
            contentType(ContentType.Application.Json)
            val prefer = buildList {
                add("return=representation")
                if (upsert) add("resolution=merge-duplicates")
            }.joinToString(",")
            header("Prefer", prefer)
            if (onConflict != null) parameter("on_conflict", onConflict)
            // Encode via JsonObject: kotlinx cannot infer a serializer for raw Maps (Any values would crash).
            setBody(body.toJsonElement())
        }
        ensureOk(res)
        val text = res.bodyAsText()
        if (T::class == Unit::class) return Unit as T
        if (text.isBlank()) return json.decodeFromString("[]")
        return json.decodeFromString(text)
    }

    suspend inline fun <reified T> restPatch(
        table: String,
        filters: Map<String, String>,
        body: Any
    ): T {
        val res = http.patch("$base/rest/v1/$table") {
            authHeaders()
            contentType(ContentType.Application.Json)
            header("Prefer", "return=representation")
            filters.forEach { (k, v) -> parameter(k, v) }
            // Encode via JsonObject: kotlinx cannot infer a serializer for raw Maps (Any values would crash).
            setBody(body.toJsonElement())
        }
        ensureOk(res)
        val text = res.bodyAsText()
        if (T::class == Unit::class) return Unit as T
        if (text.isBlank()) return json.decodeFromString("[]")
        return json.decodeFromString(text)
    }

    suspend fun restDelete(table: String, filters: Map<String, String>) {
        val res = http.delete("$base/rest/v1/$table") {
            authHeaders()
            filters.forEach { (k, v) -> parameter(k, v) }
        }
        ensureOk(res)
    }

    // ------------------------------------------------------------------ RPC
    suspend fun rpc(name: String, params: Map<String, Any?> = emptyMap()): JsonElement {
        val body = buildJsonObject {
            params.forEach { (k, v) -> put(k, v.toJsonElement()) }
        }
        val res = http.post("$base/rest/v1/rpc/$name") {
            authHeaders()
            contentType(ContentType.Application.Json)
            setBody(body)
        }
        ensureOk(res)
        val text = res.bodyAsText()
        if (text.isBlank()) return JsonNull
        return json.parseToJsonElement(text)
    }

    suspend inline fun <reified T> rpcTyped(name: String, params: Map<String, Any?> = emptyMap()): T {
        val el = rpc(name, params)
        return json.decodeFromJsonElement(el)
    }

    // ------------------------------------------------------------------ Storage
    suspend fun storageUpload(bucket: String, path: String, bytes: ByteArray, mime: String): String {
        val res = http.post("$base/storage/v1/object/$bucket/$path") {
            authHeaders()
            contentType(ContentType.parse(mime))
            header("x-upsert", "false")
            setBody(bytes)
        }
        ensureOk(res)
        return path
    }

    suspend fun storageSignedUrl(bucket: String, path: String, expiresSec: Int): String {
        val res = http.post("$base/storage/v1/object/sign/$bucket/$path") {
            authHeaders()
            contentType(ContentType.Application.Json)
            setBody(mapOf("expiresIn" to expiresSec).toJsonElement())
        }
        ensureOk(res)
        val el = json.parseToJsonElement(res.bodyAsText()).jsonObject
        val signed = el["signedURL"]?.jsonPrimitive?.content ?: throw BackendException("No signed URL")
        return base + "/storage/v1" + signed
    }

    fun storagePublicUrl(bucket: String, path: String): String =
        "$base/storage/v1/object/public/$bucket/$path"

    // ------------------------------------------------------------------ Auth (GoTrue)
    private fun HttpRequestBuilder.anonHeaders() {
        header("apikey", config.anonKey)
        contentType(ContentType.Application.Json)
    }

    suspend fun authSignIn(email: String, password: String): AuthSession {
        val res = http.post("$base/auth/v1/token?grant_type=password") {
            anonHeaders()
            setBody(mapOf("email" to email, "password" to password).toJsonElement())
        }
        ensureOk(res)
        return json.decodeFromString(res.bodyAsText())
    }

    @Serializable
    data class SignUpResponse(val user: AuthUser? = null, val session: AuthSession? = null)

    suspend fun authSignUp(email: String, password: String, username: String): SignUpResponse {
        val res = http.post("$base/auth/v1/signup") {
            anonHeaders()
            setBody(
                mapOf(
                    "email" to email,
                    "password" to password,
                    "data" to mapOf("username" to username)
                ).toJsonElement()
            )
        }
        ensureOk(res)
        return json.decodeFromString(res.bodyAsText())
    }

    suspend fun authSignOut(accessToken: String) {
        val res = http.post("$base/auth/v1/logout") {
            header("apikey", config.anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        ensureOk(res)
    }

    suspend fun authRefresh(refreshToken: String): AuthSession {
        val res = http.post("$base/auth/v1/token?grant_type=refresh_token") {
            anonHeaders()
            setBody(mapOf("refresh_token" to refreshToken).toJsonElement())
        }
        ensureOk(res)
        return json.decodeFromString(res.bodyAsText())
    }

    suspend fun authGetUser(accessToken: String): AuthUser {
        val res = http.get("$base/auth/v1/user") {
            header("apikey", config.anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
        }
        ensureOk(res)
        return json.decodeFromString(res.bodyAsText())
    }

    suspend fun authRecover(email: String, redirectTo: String? = "addonstation://recovery") {
        val res = http.post("$base/auth/v1/recover") {
            anonHeaders()
            val payload = mutableMapOf<String, Any>("email" to email)
            if (redirectTo != null) payload["redirect_to"] = redirectTo
            setBody(payload.toJsonElement())
        }
        ensureOk(res)
    }

    suspend fun authUpdateUser(accessToken: String, password: String): AuthUser {
        val res = http.put("$base/auth/v1/user") {
            header("apikey", config.anonKey)
            header(HttpHeaders.Authorization, "Bearer $accessToken")
            contentType(ContentType.Application.Json)
            setBody(mapOf("password" to password).toJsonElement())
        }
        ensureOk(res)
        return json.decodeFromString(res.bodyAsText())
    }

    fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonNull
        is JsonElement -> this
        is String -> JsonPrimitive(this)
        is Number -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        is Map<*, *> -> buildJsonObject {
            forEach { (k, v) -> put(k.toString(), v.toJsonElement()) }
        }
        is Collection<*> -> kotlinx.serialization.json.JsonArray(map { it.toJsonElement() })
        else -> JsonPrimitive(toString())
    }

    fun close() = http.close()
}
