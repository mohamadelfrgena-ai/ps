package com.addonstation.mobile.data.repo

import android.net.Uri
import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.LinkedConsole
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * ربط أجهزة PlayStation. إنشاء الأكواد واستطلاعها مسؤولية جهاز PlayStation فقط؛
 * التطبيق يستدعي claim_console_link_code ويقرأ/يحذف صفوف linked_consoles عبر RLS.
 */
class ConsoleLinkRepository(private val supabase: SupabaseClient) {

    suspend fun claimCode(rawCode: String): ConsoleLinkClaim {
        // لا تُرسل أي RPC لزائر غير مسجّل، حتى لو استدعى الشاشة خارج مسار الإعدادات.
        var session = supabase.session()
            ?: return ConsoleLinkClaim(ok = false, message = MESSAGE_SIGN_IN_REQUIRED)

        val code = normalizeCode(rawCode)
        if (!isValidCode(code)) {
            return ConsoleLinkClaim(ok = false, message = MESSAGE_INVALID_CODE)
        }

        // إن كانت الجلسة توشك على الانتهاء، حدّثها أولاً حتى يطابق Bearer
        // في الترويسة التوكنين المرسلين إلى جهاز PlayStation داخل body.
        val now = System.currentTimeMillis() / 1000L
        if (session.expiresAtEpochSeconds <= now + 30L) {
            if (supabase.refreshSession()) session = supabase.session() ?: return ConsoleLinkClaim(false, MESSAGE_SIGN_IN_REQUIRED)
        }

        val result = supabase.rpcWithToken(
            function = "claim_console_link_code",
            accessToken = session.accessToken,
            params = mapOf(
                "p_code" to JsonPrimitive(code),
                "p_access_token" to JsonPrimitive(session.accessToken),
                "p_refresh_token" to JsonPrimitive(session.refreshToken),
            ),
        ) as? JsonObject ?: return ConsoleLinkClaim(ok = false, message = "unknown_error")

        return ConsoleLinkClaim(
            ok = result["ok"]?.jsonPrimitive?.booleanOrNull == true,
            message = result["message"]?.jsonPrimitive?.contentOrNull,
            platform = result["platform"]?.jsonPrimitive?.contentOrNull,
            deviceName = result["device_name"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /** القراءة مباشرة من linked_consoles فقط؛ RLS يقيّد الصفوف بحساب الجلسة الحالية. */
    suspend fun linkedConsoles(): List<LinkedConsole> {
        val session = supabase.session() ?: throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        val response = supabase.select(
            table = "linked_consoles",
            query = "select=id,device_name,platform,created_at,last_seen_at&order=created_at.desc.nullslast",
            token = session.accessToken,
        )
        return supabase.decodeList(response)
    }

    /** يحذف الجهاز المحدد ثم يعيد القائمة من الخادم كي تظهر الحالة الجديدة فوراً. */
    suspend fun unlinkAndReload(id: String): List<LinkedConsole> {
        if (supabase.session() == null) throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
        val cleanId = id.trim()
        require(cleanId.isNotEmpty()) { "console_id_required" }
        supabase.delete("linked_consoles", "id=eq.${Uri.encode(cleanId)}")
        val refreshed = linkedConsoles()
        if (refreshed.any { it.id == cleanId }) throw IllegalStateException("console_unlink_failed")
        return refreshed
    }

    companion object {
        const val CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ"
        const val CODE_LENGTH = 6

        const val MESSAGE_SIGN_IN_REQUIRED = "sign_in_required"
        const val MESSAGE_INVALID_CODE = "invalid_code"
        const val MESSAGE_CODE_NOT_FOUND = "code_not_found"
        const val MESSAGE_CODE_EXPIRED = "code_expired"

        /** إزالة الفراغات والشرطات وتوحيد حالة الأحرف قبل التحقق أو الإرسال. */
        fun normalizeCode(input: String): String = input
            .uppercase(Locale.ROOT)
            .filterNot { it.isWhitespace() || it == '-' }

        fun isValidCode(code: String): Boolean =
            code.length == CODE_LENGTH && code.all { it in CODE_ALPHABET }
    }
}

data class ConsoleLinkClaim(
    val ok: Boolean,
    val message: String? = null,
    val platform: String? = null,
    val deviceName: String? = null,
)
