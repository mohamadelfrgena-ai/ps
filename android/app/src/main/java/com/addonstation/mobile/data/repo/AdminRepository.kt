package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.AdminStats
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorApplication
import com.addonstation.mobile.data.model.CreatorSubmission
import com.addonstation.mobile.data.model.CreatorTip
import com.addonstation.mobile.data.model.PaymentOrder
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SubscriptionPlan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * لوحة الأدمن — كل الدوال هنا يُتحقق منها على الخادم (is_admin) وليس في الواجهة فقط.
 * أي محاولة من غير الأدمن تُرفض من قاعدة البيانات.
 */
class AdminRepository(private val supabase: SupabaseClient) {

    suspend fun stats(): AdminStats = withContext(Dispatchers.IO) {
        runCatching {
            val obj = supabase.rpc("admin_stats") as? JsonObject ?: return@runCatching AdminStats()
            AdminStats(
                users = obj["users"]?.asInt() ?: 0,
                creators = obj["creators"]?.asInt() ?: 0,
                published = obj["published"]?.asInt() ?: 0,
                addons = obj["addons"]?.asInt() ?: 0,
                worlds = obj["worlds"]?.asInt() ?: 0,
                downloads = obj["downloads"]?.asInt() ?: 0,
                paidUsers = obj["paid_users"]?.asInt() ?: 0,
                openReports = obj["open_reports"]?.asInt() ?: 0,
                pendingOrders = obj["pending_orders"]?.asInt() ?: 0,
                pendingCreatorApps = obj["pending_creator_apps"]?.asInt() ?: 0,
                acBalanceTotal = obj["ac_balance_total"]?.asInt() ?: 0,
            )
        }.getOrElse { AdminStats() }
    }

    // ------------------------------------------------------------- المستخدمون

    suspend fun users(query: String = "", role: String? = null, limit: Int = 100): List<Profile> =
        withContext(Dispatchers.IO) {
            runCatching {
                val filters = StringBuilder("select=*")
                if (query.isNotBlank()) {
                    val q = query.trim()
                    filters.append("&or=(username.ilike.*$q*,display_name.ilike.*$q*)")
                }
                if (!role.isNullOrBlank() && role != "all") filters.append("&role=eq.$role")
                filters.append("&order=created_at.desc&limit=$limit")
                supabase.decodeList<Profile>(supabase.select("profiles", filters.toString()))
            }.getOrElse { emptyList() }
        }

    suspend fun updateUserPlanRole(
        userId: String,
        plan: String?,
        role: String?,
        expiresIso: String?,
        grantDays: Int?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "admin_update_profile",
                mapOf(
                    "p_user_id" to JsonPrimitive(userId),
                    "p_plan" to plan?.let { JsonPrimitive(it) },
                    "p_role" to role?.let { JsonPrimitive(it) },
                    "p_expires" to expiresIso?.let { JsonPrimitive(it) },
                    "p_grant_days" to grantDays?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    // --------------------------------------------------------------- المحتوى

    suspend fun allContent(limit: Int = 200): List<ContentItem> = runCatching {
        supabase.decodeList<ContentItem>(
            supabase.select("content_items", "select=*&order=created_at.desc&limit=$limit")
        )
    }.getOrElse { emptyList() }

    suspend fun upsertContent(
        id: String?,
        title: String,
        description: String?,
        contentType: String,
        category: String?,
        plan: String,
        version: String?,
        coverUrl: String?,
        filePath: String?,
        fileSize: Long?,
        acPrice: Int,
        isPublished: Boolean,
        isFeatured: Boolean,
        isExclusive: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val outcome = supabase.rpc(
                "admin_upsert_content",
                mapOf(
                    "p_id" to id?.let { JsonPrimitive(it) },
                    "p_title" to JsonPrimitive(title),
                    "p_description" to description?.let { JsonPrimitive(it) },
                    "p_content_type" to JsonPrimitive(contentType),
                    "p_category" to JsonPrimitive(category ?: "other"),
                    "p_plan" to JsonPrimitive(plan),
                    "p_version" to version?.let { JsonPrimitive(it) },
                    "p_cover_url" to coverUrl?.let { JsonPrimitive(it) },
                    "p_file_path" to filePath?.let { JsonPrimitive(it) },
                    "p_file_size" to fileSize?.let { JsonPrimitive(it) },
                    "p_ac_price" to JsonPrimitive(acPrice),
                    "p_is_published" to JsonPrimitive(isPublished),
                    "p_is_featured" to JsonPrimitive(isFeatured),
                    "p_is_exclusive" to JsonPrimitive(isExclusive),
                ),
            ).asOutcome().requireOk()
            outcome.data["id"]?.asString().orEmpty()
        }
    }

    suspend fun deleteContent(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("admin_delete_content", mapOf("p_id" to JsonPrimitive(id)))
                .asOutcome().requireOk()
            Unit
        }
    }

    suspend fun uploadContentFile(
        bytes: ByteArray,
        mime: String,
        fileName: String,
        isCover: Boolean,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            // الأغلفة → مخزن site-assets العام داخل مجلد items/ (نفس المسار الذي تقرأ منه
            // الواجهة عبر assetUrl/‏siteAssetUrl)، والملفات نفسها → content-files الخاص.
            val bucket = if (isCover) "site-assets" else "content-files"
            val path =
                if (isCover) "items/${System.currentTimeMillis()}-$fileName"
                else "${System.currentTimeMillis()}-$fileName"
            supabase.upload(bucket, path, bytes, mime)
            path
        }
    }

    // -------------------------------------------------------------- المدفوعات

    suspend fun orders(status: String? = null, limit: Int = 200): List<PaymentOrder> = withContext(Dispatchers.IO) {
        runCatching {
            val filter = if (status.isNullOrBlank() || status == "all") "" else "&status=eq.$status"
            supabase.decodeList<PaymentOrder>(
                supabase.select("payment_orders", "select=*&order=created_at.desc&limit=$limit$filter")
            )
        }.getOrElse { emptyList() }
    }

    suspend fun approveOrder(orderId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("approve_payment_order", mapOf("p_order_id" to JsonPrimitive(orderId)))
                .asOutcome().requireOk()
            Unit
        }
    }

    suspend fun rejectOrder(orderId: String, reason: String?): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "reject_payment_order",
                mapOf(
                    "p_order_id" to JsonPrimitive(orderId),
                    "p_reason" to reason?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    // ------------------------------------------------------ الخطط وطرق الدفع

    suspend fun plans(): List<SubscriptionPlan> = runCatching {
        supabase.decodeList<SubscriptionPlan>(
            supabase.select("subscription_plans", "select=*&order=sort_order.asc")
        )
    }.getOrElse { emptyList() }

    suspend fun updatePlan(
        planId: String,
        priceLyd: Double?,
        priceUsd: Double?,
        durationDays: Int?,
        downloadLimit: Int?,
        limitPeriod: String?,
        active: Boolean?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "admin_update_plan",
                mapOf(
                    "p_plan_id" to JsonPrimitive(planId),
                    "p_price_lyd" to priceLyd?.let { JsonPrimitive(it) },
                    "p_price_usd" to priceUsd?.let { JsonPrimitive(it) },
                    "p_duration_days" to durationDays?.let { JsonPrimitive(it) },
                    "p_download_limit" to downloadLimit?.let { JsonPrimitive(it) },
                    "p_limit_period" to limitPeriod?.let { JsonPrimitive(it) },
                    "p_active" to active?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun upsertPaymentMethod(
        code: String,
        name: String,
        accountName: String?,
        accountIdentifier: String?,
        instructions: String?,
        enabled: Boolean,
        sortOrder: Int,
        provider: String = "manual",
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "admin_upsert_payment_method",
                mapOf(
                    "p_code" to JsonPrimitive(code),
                    "p_name" to JsonPrimitive(name),
                    "p_account_name" to accountName?.let { JsonPrimitive(it) },
                    "p_account_identifier" to accountIdentifier?.let { JsonPrimitive(it) },
                    "p_instructions" to instructions?.let { JsonPrimitive(it) },
                    "p_enabled" to JsonPrimitive(enabled),
                    "p_sort_order" to JsonPrimitive(sortOrder),
                    "p_provider" to JsonPrimitive(provider),
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun deletePaymentMethod(code: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("admin_delete_payment_method", mapOf("p_code" to JsonPrimitive(code)))
                .asOutcome().requireOk()
            Unit
        }
    }

    // -------------------------------------------------------------- الإشراف

    suspend fun auditLog(limit: Int = 200): List<Map<String, JsonElement>> = runCatching {
        supabase.decodeList<Map<String, JsonElement>>(
            supabase.select("admin_audit_log", "select=*&order=created_at.desc&limit=$limit")
        )
    }.getOrElse { emptyList() }

    /** إعدادات التطبيق العامة: مفتاح → قيمة (يحرّرها الأدمن من اللوحة). */
    suspend fun appSettings(): Map<String, JsonElement> = runCatching {
        val rows = supabase.decodeList<Map<String, JsonElement>>(
            supabase.select("app_settings", "select=key,value&limit=200")
        )
        rows.mapNotNull { row ->
            val key = row["key"]?.asString() ?: return@mapNotNull null
            key to (row["value"] ?: JsonPrimitive(""))
        }.toMap()
    }.getOrElse { emptyMap() }

    suspend fun updateSetting(key: String, value: JsonElement): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "admin_update_settings",
                mapOf("p_key" to JsonPrimitive(key), "p_value" to value),
            ).asOutcome().requireOk()
            Unit
        }
    }

    // ------------------------------------------------------------ AC Management

    suspend fun acAdjust(userId: String, amount: Int, reason: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "ac_admin_adjust",
                mapOf(
                    "p_user_id" to JsonPrimitive(userId),
                    "p_amount" to JsonPrimitive(amount),
                    "p_reason" to JsonPrimitive(reason),
                    "p_request_key" to JsonPrimitive("admin:${java.util.UUID.randomUUID()}"),
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun acGrantAll(amount: Int, reason: String): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val outcome = supabase.rpc(
                "ac_admin_grant_all",
                mapOf("p_amount" to JsonPrimitive(amount), "p_reason" to JsonPrimitive(reason)),
            ).asOutcome().requireOk()
            outcome.data["users"]?.asInt() ?: 0
        }
    }

    suspend fun acTransactions(limit: Int = 200): List<Map<String, JsonElement>> = runCatching {
        supabase.decodeList<Map<String, JsonElement>>(
            supabase.select("ac_transactions", "select=*&order=created_at.desc&limit=$limit")
        )
    }.getOrElse { emptyList() }

    suspend fun acTasks(): List<Map<String, JsonElement>> = runCatching {
        supabase.decodeList<Map<String, JsonElement>>(
            supabase.select("ac_tasks", "select=*&order=sort_order.asc")
        )
    }.getOrElse { emptyList() }

    // ------------------------------------------- المبدعون (مراجعة/تِيبس/طلبات)

    suspend fun creatorApplications(status: String? = null): List<CreatorApplication> = runCatching {
        val filter = if (status.isNullOrBlank() || status == "all") "" else "&status=eq.$status"
        supabase.decodeList<CreatorApplication>(
            supabase.select("creator_applications", "select=*&order=created_at.desc&limit=200$filter")
        )
    }.getOrElse { emptyList() }

    suspend fun reviewApplication(id: Long, action: String, note: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "review_creator_application",
                    mapOf(
                        "p_application_id" to JsonPrimitive(id),
                        "p_action" to JsonPrimitive(action),
                        "p_note" to note?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun toggleFeaturedCreator(userId: String, featured: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "toggle_featured_creator",
                    mapOf(
                        "p_user_id" to JsonPrimitive(userId),
                        "p_featured" to JsonPrimitive(featured),
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun submissions(status: String? = null): List<CreatorSubmission> = runCatching {
        val filter = if (status.isNullOrBlank() || status == "all") "" else "&status=eq.$status"
        supabase.decodeList<CreatorSubmission>(
            supabase.select("creator_submissions", "select=*&order=created_at.desc&limit=200$filter")
        )
    }.getOrElse { emptyList() }

    suspend fun reviewSubmission(id: Long, action: String, note: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "review_creator_submission",
                    mapOf(
                        "p_submission_id" to JsonPrimitive(id),
                        "p_action" to JsonPrimitive(action),
                        "p_note" to note?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun tips(): List<CreatorTip> = runCatching {
        supabase.decodeList<CreatorTip>(
            supabase.select("creator_tips", "select=*&order=created_at.desc&limit=200")
        )
    }.getOrElse { emptyList() }

    suspend fun reviewTip(tipId: Long, approve: Boolean, reason: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "creator_review_tip",
                    mapOf(
                        "p_tip_id" to JsonPrimitive(tipId),
                        "p_approve" to JsonPrimitive(approve),
                        "p_reason" to reason?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }
}
