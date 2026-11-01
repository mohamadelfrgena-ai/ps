package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.apiErrorFromMessage
import com.addonstation.mobile.data.model.LinkedConsole
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * Shared phone/PS4 device-code contract owned by the production server.
 *
 * The PS4 creates and polls the one-time code. The phone normalizes the code,
 * asks for explicit confirmation locally, then calls claim_console_link with
 * the signed-in user's current session tokens exactly once. No service-role or
 * secret key is ever present in the Android application.
 */
class ConsoleLinkRepository(private val supabase: SupabaseClient) {

    /** Validate locally before showing the explicit approval sheet. */
    fun prepareCode(rawCode: String): ConsoleCodeLookup {
        if (supabase.session() == null) {
            return ConsoleCodeLookup(ok = false, message = MESSAGE_SIGN_IN_REQUIRED)
        }

        val code = normalizeCode(rawCode)
        if (!isValidCode(code)) {
            return ConsoleCodeLookup(ok = false, message = MESSAGE_INVALID_CODE)
        }

        return ConsoleCodeLookup(
            ok = true,
            message = "ready_to_claim",
            code = code,
            platform = "ps4",
        )
    }

    /**
     * Claim only after the user approves the local confirmation sheet.
     * Refresh first when the access token is near expiry so the Authorization
     * header and the two token parameters always describe the same session.
     */
    suspend fun claimCode(rawCode: String): ConsoleLinkClaim {
        var session = supabase.session()
            ?: return ConsoleLinkClaim(ok = false, message = MESSAGE_SIGN_IN_REQUIRED)

        val code = normalizeCode(rawCode)
        if (!isValidCode(code)) {
            return ConsoleLinkClaim(ok = false, message = MESSAGE_INVALID_CODE)
        }

        val nowSeconds = System.currentTimeMillis() / 1000L
        if (session.expiresAtEpochSeconds <= nowSeconds + SESSION_REFRESH_MARGIN_SECONDS) {
            if (!supabase.refreshSession()) {
                return ConsoleLinkClaim(ok = false, message = MESSAGE_SIGN_IN_REQUIRED)
            }
            session = supabase.session()
                ?: return ConsoleLinkClaim(ok = false, message = MESSAGE_SIGN_IN_REQUIRED)
        }

        val result = supabase.rpcWithToken(
            function = "claim_console_link",
            params = mapOf(
                "p_code" to JsonPrimitive(code),
                "p_access_token" to JsonPrimitive(session.accessToken),
                "p_refresh_token" to JsonPrimitive(session.refreshToken),
            ),
            accessToken = session.accessToken,
        ) as? JsonObject ?: return ConsoleLinkClaim(ok = false, message = MESSAGE_SERVICE_UNAVAILABLE)

        return ConsoleLinkClaim(
            ok = result["ok"]?.jsonPrimitive?.booleanOrNull == true,
            message = result["message"]?.jsonPrimitive?.contentOrNull,
            platform = result["platform"]?.jsonPrimitive?.contentOrNull,
            deviceName = result["device_name"]?.jsonPrimitive?.contentOrNull,
        )
    }

    suspend fun linkedConsoles(): List<LinkedConsole> {
        if (supabase.session() == null) throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        val response = supabase.rpc(
            "console_list",
            signedOnly = true,
        ) ?: JsonArray(emptyList())
        return supabase.decodeList(response)
    }

    suspend fun unlinkAndReload(id: String): List<LinkedConsole> {
        if (supabase.session() == null) throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        val cleanId = id.trim()
        require(cleanId.isNotEmpty()) { "console_id_required" }
        val result = supabase.rpc(
            "console_unlink",
            params = mapOf("p_id" to JsonPrimitive(cleanId)),
            signedOnly = true,
        ) as? JsonObject ?: throw ApiError(ApiErrorKind.GENERIC, "console_unlink_failed")
        if (result["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            throw apiErrorFromMessage(result["message"]?.jsonPrimitive?.contentOrNull)
        }
        val refreshed = linkedConsoles()
        if (refreshed.any { it.id == cleanId }) throw IllegalStateException("console_unlink_failed")
        return refreshed
    }

    suspend fun unlinkAllAndReload(): List<LinkedConsole> {
        if (supabase.session() == null) throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        val result = supabase.rpc(
            "console_unlink_all",
            signedOnly = true,
        ) as? JsonObject ?: throw ApiError(ApiErrorKind.GENERIC, "console_unlink_all_failed")
        if (result["ok"]?.jsonPrimitive?.booleanOrNull != true) {
            throw apiErrorFromMessage(result["message"]?.jsonPrimitive?.contentOrNull)
        }
        val refreshed = linkedConsoles()
        if (refreshed.isNotEmpty()) throw IllegalStateException("console_unlink_all_failed")
        return refreshed
    }

    companion object {
        const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        const val CODE_LENGTH = 6
        private const val SESSION_REFRESH_MARGIN_SECONDS = 120L

        const val MESSAGE_SIGN_IN_REQUIRED = "sign_in_required"
        const val MESSAGE_INVALID_CODE = "invalid_code"
        const val MESSAGE_CODE_NOT_FOUND = "code_not_found"
        const val MESSAGE_CODE_EXPIRED = "code_expired"
        const val MESSAGE_TOO_MANY_ATTEMPTS = "too_many_attempts"
        const val MESSAGE_SERVICE_UNAVAILABLE = "console_link_service_unavailable"

        private val CODE_SEPARATORS = setOf('-', '‐', '‑', '‒', '–', '—')

        /**
         * Codes are case-insensitive. Normalize with a locale-independent case
         * mapping and accept the separators users commonly type or paste from
         * the PlayStation display.
         */
        fun normalizeCode(input: String): String = input
            .uppercase(Locale.ROOT)
            .filterNot { it.isWhitespace() || it in CODE_SEPARATORS }

        fun isValidCode(code: String): Boolean =
            code.length == CODE_LENGTH && code.all { it in CODE_ALPHABET }
    }
}

data class ConsoleCodeLookup(
    val ok: Boolean,
    val message: String? = null,
    val code: String? = null,
    val platform: String? = null,
    val deviceName: String? = null,
    val appVersion: String? = null,
    val requestedAt: String? = null,
    val expiresAt: String? = null,
)

data class ConsoleLinkClaim(
    val ok: Boolean,
    val message: String? = null,
    val platform: String? = null,
    val deviceName: String? = null,
)
