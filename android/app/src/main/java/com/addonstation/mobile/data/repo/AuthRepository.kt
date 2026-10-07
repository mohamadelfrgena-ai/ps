package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.SessionStore
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.AuthUser

/**
 * المصادقة: تسجيل حساب، دخول، خروج، استعادة كلمة المرور.
 * كل القواعد الأمنية (طول كلمة المرور، تأكيد البريد، حدود المعدّل) على Supabase Auth.
 */
class AuthRepository(
    private val supabase: SupabaseClient,
    private val sessionStore: SessionStore,
) {

    companion object {
        const val MIN_PASSWORD = 6
        const val MIN_USERNAME = 3
        const val MAX_USERNAME = 24
    }

    fun isSignedIn(): Boolean = sessionStore.current() != null

    fun currentUserId(): String? = sessionStore.current()?.userId

    fun currentEmail(): String? = sessionStore.current()?.email

    /** تجديد الجلسة إن أوشكت على الانتهاء (يُستدعى عند كل فتح للتطبيق). */
    suspend fun ensureFreshSession() {
        val session = sessionStore.current() ?: return
        val now = System.currentTimeMillis() / 1000L
        if (session.expiresAtEpochSeconds - now < 300) {
            supabase.refreshSession()
        }
    }

    /** فتح جلسة من رابط استعادة البريد (يعمل على خيط الإدخال/الإخراج). */
    suspend fun acceptRecoveryLink(url: String): Boolean = supabase.acceptRecoveryLink(url)

    suspend fun signUp(username: String, email: String, password: String): Result<AuthUser> = runCatching {
        validateUsername(username)
        validateEmail(email)
        validatePassword(password)
        val user = supabase.signUp(email.trim(), password, username.trim())
        // **دخول تلقائي بعد التسجيل**: إن أعاد الخادم جلسة فهذا يكفي؛ وإن لم
        // يُعدها (تأكيد البريد مُعطّل لكن الجلسة لم تُحفظ) نحاول الدخول مباشرةً
        // فيدخل المستخدم التطبيق فوراً بلا شاشة إضافية. وإن فشل (البريد يحتاج
        // تأكيداً) نتركه كما هو ونُظهر رسالة «أُنشئ الحساب».
        if (supabase.session() == null) {
            runCatching { supabase.signIn(email.trim(), password) }
        }
        user
    }

    suspend fun signIn(email: String, password: String): Result<AuthUser> = runCatching {
        if (email.isBlank() || password.isBlank()) throw ApiError(ApiErrorKind.INVALID_CREDENTIALS)
        supabase.signIn(email.trim(), password)
    }

    suspend fun signOut() {
        supabase.signOut()
        // أوقات آخر رسالة في قائمة الغرف = ذاكرة جلسة: تُفرَّغ مع الخروج
        com.addonstation.mobile.ui.chat.ChatPeek.clear()
    }

    /**
     * إعادة إرسال رابط تأكيد البريد (بعد إنشاء حساب بتأكيد بريد مفعّل).
     * تُرجع `Result` لتُعرَض رسالة نجاح/فشل صريحة في الشاشة.
     */
    suspend fun resendConfirmation(email: String): Result<Unit> = runCatching {
        validateEmail(email)
        supabase.resendConfirmation(email.trim())
    }

    suspend fun sendRecovery(email: String): Result<Unit> = runCatching {
        validateEmail(email)
        supabase.sendRecovery(email.trim())
    }

    /** تعيين كلمة مرور جديدة بعد فتح رابط الاستعادة. */
    suspend fun updatePassword(password: String): Result<Unit> = runCatching {
        validatePassword(password)
        supabase.updatePassword(password)
    }

    suspend fun verifyRecoveryToken(email: String, token: String): Result<Unit> = runCatching {
        if (!supabase.verifyRecoveryToken(email.trim(), token.trim())) {
            throw ApiError(ApiErrorKind.INVALID_CREDENTIALS, "invalid recovery token")
        }
    }

    suspend fun deleteAccount(): Result<Unit> = runCatching {
        // السياسة: الحساب يُعطَّل من لوحة الإدارة؛ المستخدم يخرج محلياً فوراً.
        supabase.signOut()
    }

    private fun validateUsername(username: String) {
        val name = username.trim()
        if (name.length < MIN_USERNAME) throw ApiError(ApiErrorKind.GENERIC, "username_too_short")
        if (name.length > MAX_USERNAME) throw ApiError(ApiErrorKind.GENERIC, "username_too_long")
        if (!name.matches(Regex("^[A-Za-z0-9_]+$"))) throw ApiError(ApiErrorKind.GENERIC, "username_invalid_chars")
    }

    private fun validateEmail(email: String) {
        if (!android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches()) {
            throw ApiError(ApiErrorKind.INVALID_EMAIL)
        }
    }

    private fun validatePassword(password: String) {
        if (password.length < MIN_PASSWORD) throw ApiError(ApiErrorKind.PASSWORD_TOO_SHORT)
    }
}
