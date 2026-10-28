package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.PaymentMethodSetting
import com.addonstation.mobile.data.model.PaymentOrder
import com.addonstation.mobile.data.model.SubscriptionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * الخطط والاشتراكات والدفع اليدوي بالإيصال.
 * السعر يأتي دائماً من الخادم، والمبلغ يُحدده الخادم عند إنشاء الطلب (إصلاح ثغرة §3.3-2).
 */
/** نتيجة بدء دفع إلكتروني عبر البوابة: الطلب + الرابط الجاهز للدفع. */
data class GatewayPayment(
    val orderId: String,
    val referenceCode: String,
    val payUrl: String,
)

class PlansRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

    suspend fun loadPlans(): List<SubscriptionPlan> = withContext(Dispatchers.IO) {
        val plans = runCatching {
            supabase.decodeList<SubscriptionPlan>(
                supabase.select(
                    "subscription_plans",
                    "select=*&order=sort_order.asc",
                    token = null,   // قراءة عامة: ترويسة apikey تكفي
                )
            )
        }.getOrElse { emptyList() }
        state.setPlans(plans)
        plans
    }

    suspend fun loadPaymentMethods(): List<PaymentMethodSetting> = withContext(Dispatchers.IO) {
        val methods = runCatching {
            supabase.decodeList<PaymentMethodSetting>(
                supabase.select(
                    "payment_method_settings",
                    "select=*&enabled=eq.true&order=sort_order.asc",
                    token = null,   // قراءة عامة: ترويسة apikey تكفي
                )
            )
        }.getOrElse { emptyList() }
        state.setPaymentMethods(methods)
        methods
    }

    /** كل الطرق (بما فيها المعطّلة) للوحة الإدارة. */
    suspend fun loadAllPaymentMethods(): List<PaymentMethodSetting> = runCatching {
        supabase.decodeList<PaymentMethodSetting>(
            supabase.select("payment_method_settings", "select=*&order=sort_order.asc")
        )
    }.getOrElse { emptyList() }

    fun planById(id: String): SubscriptionPlan? = state.plans.value.firstOrNull { it.id == id }

    suspend fun createOrder(planId: String, methodCode: String): Result<PaymentOrder> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outcome = supabase.rpc(
                    "create_payment_order",
                    mapOf(
                        "p_plan_id" to JsonPrimitive(planId),
                        "p_method" to JsonPrimitive(methodCode),
                    ),
                ).asOutcome().requireOk()
                val orderId = outcome.data["order_id"]?.asString()
                    ?: throw ApiError(ApiErrorKind.GENERIC, "order_id_missing")
                myOrders(limit = 5).firstOrNull { it.id == orderId }
                    ?: PaymentOrder(
                        id = orderId,
                        userId = supabase.session()?.userId.orEmpty(),
                        planId = planId,
                        amountLyd = outcome.data["amount_lyd"]?.asString()?.toDoubleOrNull() ?: 0.0,
                        paymentMethod = methodCode,
                        referenceCode = outcome.data["reference_code"]?.asString().orEmpty(),
                    )
            }
        }

    /**
     * بدء دفع إلكتروني عبر بوابة الدفع: ينشئ الطلب (بمبلغ يحدده الخادم) ويرجع
     * رابط الدفع الجاهز. بيانات البوابة (Key + ID) لا تغادر الخادم إطلاقاً.
     */
    suspend fun startGatewayPayment(planId: String, methodCode: String): Result<GatewayPayment> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outcome = supabase.rpc(
                    "start_gateway_payment",
                    mapOf(
                        "p_plan_id" to JsonPrimitive(planId),
                        "p_method" to JsonPrimitive(methodCode),
                    ),
                ).asOutcome().requireOk()
                GatewayPayment(
                    orderId = outcome.data["order_id"]?.asString()
                        ?: throw ApiError(ApiErrorKind.GENERIC, "order_id_missing"),
                    referenceCode = outcome.data["reference_code"]?.asString().orEmpty(),
                    payUrl = outcome.data["pay_url"]?.asString()
                        ?: throw ApiError(ApiErrorKind.GENERIC, "pay_url_missing"),
                )
            }
        }

    /** رفع الإيصال إلى payment-receipts داخل مجلد المستخدم ثم تحويل الطلب إلى submitted. */
    suspend fun submitReceipt(
        order: PaymentOrder,
        imageBytes: ByteArray,
        mimeType: String,
        fileName: String,
        transactionReference: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = supabase.session()?.userId ?: throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
            val path = "$uid/${order.id}-$fileName"
            supabase.upload("payment-receipts", path, imageBytes, mimeType)
            supabase.rpc(
                "submit_payment_receipt",
                mapOf(
                    "p_order_id" to JsonPrimitive(order.id),
                    "p_receipt_path" to JsonPrimitive(path),
                    "p_transaction_reference" to transactionReference?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun myOrders(limit: Int = 50): List<PaymentOrder> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        runCatching {
            supabase.decodeList<PaymentOrder>(
                supabase.select(
                    "payment_orders",
                    "select=*&user_id=eq.$uid&order=created_at.desc&limit=$limit",
                )
            )
        }.getOrElse { emptyList() }
    }

    suspend fun orderById(id: String): PaymentOrder? = runCatching {
        supabase.decodeOne<PaymentOrder>(supabase.select("payment_orders", "select=*&id=eq.$id"))
    }.getOrNull()

    /** رابط موقّع 60 ثانية لعرض الإيصال (المستخدم لطلبه، والأدمن لأي طلب). */
    suspend fun receiptUrl(order: PaymentOrder): String? {
        val path = order.receiptUrl ?: return null
        return runCatching { supabase.signedUrl("payment-receipts", path, 60) }.getOrNull()
    }

    /**
     * بوابة الدفع الإلكتروني (اختياري): تستدعي Edge Function stripe-checkout التي تنشئ
     * طلب دفع على الخادم + جلسة Stripe، وتُرجع رابط الدفع لفتحه في المتصفح.
     * تعمل فقط بعد تفعيل طريقة stripe وضبط STRIPE_SECRET_KEY — راجع docs/03-PAYMENTS-AR.md.
     */
    suspend fun startStripeCheckout(planId: String): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = supabase.config()
            val body = buildJsonObject { put("plan_id", planId) }
            val request = Request.Builder()
                .url("${cfg.url.trimEnd('/')}/functions/v1/stripe-checkout")
                .header("apikey", cfg.anonKey)
                .header("Authorization", "Bearer ${supabase.session()?.accessToken ?: cfg.anonKey}")
                .header("Content-Type", "application/json")
                .post(body.toString().toRequestBody("application/json".toMediaType()))
                .build()
            val response = supabase.okHttp().newCall(request).execute()
            val payload = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw com.addonstation.mobile.data.apiErrorFromMessage(payload, response.code)
            }
            val obj = supabase.json.parseToJsonElement(payload) as JsonObject
            val ok = obj["ok"]?.asBool() ?: false
            if (!ok) throw com.addonstation.mobile.data.apiErrorFromMessage(obj["message"]?.asString())
            obj["url"]?.asString() ?: throw ApiError(ApiErrorKind.GENERIC, "stripe_url_missing")
        }
    }
}
