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
 * Secure PlayStation linking contract.
 *
 * The phone only looks up and approves a one-time code. It never sends its
 * access token or refresh token in an RPC body. Code creation, poll-secret
 * verification, and console credential issuance belong to the trusted Edge
 * Function defined by the server deployment.
 */
class ConsoleLinkRepository(private val supabase: SupabaseClient) {

    suspend fun lookupCode(rawCode: String): ConsoleCodeLookup {
        if (supabase.session() == null) {
            return ConsoleCodeLookup(ok = false, message = MESSAGE_SIGN_IN_REQUIRED)
        }

        val code = normalizeCode(rawCode)
        if (!isValidCode(code)) {
            return ConsoleCodeLookup(ok = false, message = MESSAGE_INVALID_CODE)
        }

        val result = supabase.rpc(
            "console_code_lookup",
            params = mapOf("p_code" to JsonPrimitive(code)),
            signedOnly = true,
        ) as? JsonObject ?: return ConsoleCodeLookup(ok = false, message = MESSAGE_SERVICE_UNAVAILABLE)

        return ConsoleCodeLookup(
            ok = result["ok"]?.jsonPrimitive?.booleanOrNull == true,
            message = result["message"]?.jsonPrimitive?.contentOrNull,
            code = result["code"]?.jsonPrimitive?.contentOrNull ?: code,
            platform = result["platform"]?.jsonPrimitive?.contentOrNull,
            deviceName = result["device_name"]?.jsonPrimitive?.contentOrNull,
            appVersion = result["app_version"]?.jsonPrimitive?.contentOrNull,
            requestedAt = result["requested_at"]?.jsonPrimitive?.contentOrNull,
            expiresAt = result["expires_at"]?.jsonPrimitive?.contentOrNull,
        )
    }

    suspend fun approveCode(rawCode: String): ConsoleLinkClaim {
        if (supabase.session() == null) {
            return ConsoleLinkClaim(ok = false, message = MESSAGE_SIGN_IN_REQUIRED)
        }

        val code = normalizeCode(rawCode)
        if (!isValidCode(code)) {
            return ConsoleLinkClaim(ok = false, message = MESSAGE_INVALID_CODE)
        }

        val result = supabase.rpc(
            "console_code_approve",
            params = mapOf("p_code" to JsonPrimitive(code)),
            signedOnly = true,
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
