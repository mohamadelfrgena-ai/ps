package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.PushPrefs
import com.addonstation.mobile.data.model.PushTestResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.serializer

/**
 * الإشعارات الفورية (Push): تسجيل الجهاز، التفضيلات، الإشعار التجريبي، ونبضة الحضور.
 *
 * كل شيء عبر دوال الخادم (security definer) في `34_push_notifications.sql` —
 * لا كتابة مباشرة على الجداول، فلا يستطيع أحد إرسال إشعار لغيره ولا العبث بجهاز غيره.
 *
 * ملاحظة: دوال `my_push_prefs` و`push_set_prefs` تُرجع **صفاً واحداً** (composite)
 * ⇒ PostgREST يعيد كائناً لا مصفوفة، لذلك [composite] تقبل الحالتين.
 */
class PushRepository(private val supabase: SupabaseClient) {

    private inline fun <reified T> composite(element: JsonElement?): T? {
        val target = when (element) {
            is JsonArray -> element.firstOrNull()
            else -> element
        } ?: return null
        return runCatching { supabase.json.decodeFromJsonElement(serializer<T>(), target) }.getOrNull()
    }

    /**
     * تسجيل توكن هذا الجهاز للمستخدم الحالي.
     * تُحدَّث معه اللغة والمنطقة الزمنية في كل تشغيل ⇒ ساعات الهدوء بتوقيته.
     */
    suspend fun registerDevice(
        token: String,
        platform: String = "android",
        appVersion: String? = null,
        locale: String = "en",
        tzOffset: Int = 0,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "push_register_device",
                params(
                    "p_token" to p(token),
                    "p_platform" to p(platform),
                    "p_app_version" to p(appVersion),
                    "p_locale" to p(locale),
                    "p_tz_offset" to p(tzOffset),
                ),
                signedOnly = true,
            ).asOutcome().requireOk()
            Unit
        }
    }

    /** إلغاء تسجيل الجهاز (تسجيل الخروج). */
    suspend fun unregisterDevice(token: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "push_unregister_device",
                params("p_token" to p(token)),
                signedOnly = true,
            ).asOutcome().requireOk()
            Unit
        }
    }

    /** نبضة الحضور: تُحدّث «آخر ظهور» — وعليها يقوم تذكير الغياب الطويل. */
    suspend fun heartbeat(): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("push_heartbeat", emptyMap(), signedOnly = true)
            Unit
        }
    }

    /** تفضيلاتي الحالية (تُقرأ عند فتح شاشة الإشعارات). */
    suspend fun prefs(): PushPrefs? = withContext(Dispatchers.IO) {
        if (supabase.session() == null) return@withContext null
        runCatching { composite<PushPrefs>(supabase.rpc("my_push_prefs", emptyMap(), signedOnly = true)) }
            .getOrNull()
    }

    /** حفظ التفضيلات: أي وسيط يُترك null يبقى على حاله في الخادم. */
    suspend fun setPrefs(
        master: Boolean? = null,
        chat: Boolean? = null,
        content: Boolean? = null,
        social: Boolean? = null,
        account: Boolean? = null,
        reminders: Boolean? = null,
        quietEnabled: Boolean? = null,
        quietFrom: Int? = null,
        quietTo: Int? = null,
        reengageDays: Int? = null,
    ): PushPrefs? = withContext(Dispatchers.IO) {
        runCatching {
            composite<PushPrefs>(
                supabase.rpc(
                    "push_set_prefs",
                    params(
                        "p_master" to p(master),
                        "p_chat" to p(chat),
                        "p_content" to p(content),
                        "p_social" to p(social),
                        "p_account" to p(account),
                        "p_reminders" to p(reminders),
                        "p_quiet_enabled" to p(quietEnabled),
                        "p_quiet_from" to p(quietFrom),
                        "p_quiet_to" to p(quietTo),
                        "p_reengage_days" to p(reengageDays),
                    ),
                    signedOnly = true,
                ),
            )
        }.getOrNull()
    }

    /**
     * إشعار تجريبي لنفسي (يتجاوز ساعات الهدوء) — زر «جرّب الإشعار الآن».
     * يرجع (نجاح، سبب الفشل) — والسبب يُترجم في الشاشة.
     */
    suspend fun testMe(): PushTestResult = withContext(Dispatchers.IO) {
        runCatching {
            val el = supabase.rpc("push_test_me", emptyMap(), signedOnly = true)
            val obj = el as? kotlinx.serialization.json.JsonObject
            PushTestResult(
                ok = obj?.flag("ok") == true,
                message = obj?.str("message"),
            )
        }.getOrElse { PushTestResult(ok = false, message = null) }
    }
}
