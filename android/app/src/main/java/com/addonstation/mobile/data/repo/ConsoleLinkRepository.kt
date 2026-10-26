package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * ربط جهاز PlayStation بحساب الهاتف (Settings → Link a console).
 *
 * السوني يولّد كوداً من 6 محارف عبر `create_console_link_code`، والهنا يُدخله
 * المستخدم هنا فتُنفَّذ `claim_console_link_code` التي تربط الحساب بالجهاز
 * وتمنحه جلسة (access + refresh token) يستعملها السوني لاحقاً.
 */
class ConsoleLinkRepository(
    private val supabase: SupabaseClient,
) {

    /**
     * يطالب بكود الربط. يعيد اسم الجهاز المرتبط عند النجاح،
     * ويرمي ApiError برسالة الخادم عند الفشل
     * (invalid_code · code_not_found · code_expired · sign_in_required).
     */
    suspend fun claim(code: String): String = withContext(Dispatchers.IO) {
        if (!supabase.isSignedIn()) throw ApiError(ApiErrorKind.NOT_SIGNED_IN, "sign_in_required")
        // الجلسة القريبة من الانتهاء تُحدَّث أولاً حتى لا يُرفض الطلب بتوكن قديم
        val current = supabase.session()
        if (current != null && current.expiresAtEpochSeconds < System.currentTimeMillis() / 1000L + 120L) {
            supabase.refreshSession()
        }
        val session = supabase.session() ?: throw ApiError(ApiErrorKind.NOT_SIGNED_IN, "sign_in_required")
        val clean = code.filter { it.isLetterOrDigit() }.uppercase()
        if (clean.length != 6) throw ApiError(ApiErrorKind.GENERIC, "invalid_code")
        val outcome = supabase.rpc(
            "claim_console_link_code",
            params(
                "p_code" to p(clean),
                "p_access_token" to p(session.accessToken),
                "p_refresh_token" to p(session.refreshToken),
            ),
            signedOnly = true,
        ).asOutcome().requireOk()
        outcome.data.str("device_name").orEmpty()
    }
}
