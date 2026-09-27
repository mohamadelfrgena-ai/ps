package com.addonstation.app.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/** Single-row (maybeSingle) helper. */
suspend inline fun <reified T> SupabaseBackend.one(
    table: String,
    select: String = "*",
    filters: Map<String, String> = emptyMap(),
    order: String? = null,
    ascending: Boolean = false
): T? {
    val el: JsonElement = restGet(table, select, filters, order, ascending, limit = 1)
    return when (el) {
        is JsonArray -> if (el.isEmpty()) null else json.decodeFromJsonElement(el[0])
        is JsonObject -> json.decodeFromJsonElement(el)
        is JsonNull -> null
        else -> null
    }
}

suspend inline fun <reified T> SupabaseBackend.list(
    table: String,
    select: String = "*",
    filters: Map<String, String> = emptyMap(),
    order: String? = null,
    ascending: Boolean = false,
    limit: Int? = null
): List<T> = restGet(table, select, filters, order, ascending, limit)

// ---------------------------------------------------------------------------
// Profiles & social
// ---------------------------------------------------------------------------

class ProfileRepository(val backend: SupabaseBackend) {
    suspend fun get(id: String): Profile? = backend.one("profiles", "*", mapOf("id" to "eq.$id"))
    suspend fun update(id: String, fields: Map<String, Any?>): Profile? {
        val rows: List<Profile> = backend.restPatch("profiles", mapOf("id" to "eq.$id"), fields)
        return rows.firstOrNull()
    }

    suspend fun search(query: String, limit: Int = 20): List<Profile> =
        backend.list("profiles", "id,username,avatar_url,role,plan", mapOf("username" to "ilike.*$query*"), "username", true, limit)

    suspend fun onlineMembers(limit: Int = 50): List<Profile> = backend.list(
        "profiles", "id,username,avatar_url,role,plan,show_online_status,bio",
        mapOf("show_online_status" to "eq.true"), limit = limit
    )

    suspend fun allMembers(limit: Int = 200): List<Profile> = backend.list(
        "profiles", "id,username,avatar_url,role,plan,show_online_status",
        order = "username", ascending = true, limit = limit
    )

    suspend fun byIds(ids: Collection<String>): Map<String, Profile> {
        if (ids.isEmpty()) return emptyMap()
        val rows: List<Profile> = backend.list(
            "profiles", "id,username,avatar_url,role,plan,equipped_item_id,bio",
            mapOf("id" to "in.(${ids.distinct().joinToString(",")})")
        )
        return rows.associateBy { it.id }
    }

    // -- friends --
    suspend fun friendStatus(me: String, other: String): String? {
        val row: JsonElement = backend.restGet(
            "user_friends", "status",
            mapOf("requester_id" to "eq.$me", "receiver_id" to "eq.$other"), limit = 1
        )
        val arr = row as? JsonArray ?: return null
        return (arr.firstOrNull() as? JsonObject)?.get("status")?.jsonPrimitive?.content
    }

    suspend fun sendFriendRequest(me: String, other: String) {
        backend.restPost<Unit>(
            "user_friends",
            mapOf("requester_id" to me, "receiver_id" to other, "status" to "pending"),
            upsert = true
        )
        backend.restPost<Unit>(
            "chat_notifications",
            mapOf("recipient_id" to other, "actor_id" to me, "notification_type" to "friend_request")
        )
    }

    suspend fun acceptFriend(requester: String, me: String) {
        backend.restPatch<Unit>(
            "user_friends", mapOf("requester_id" to "eq.$requester", "receiver_id" to "eq.$me"),
            mapOf("status" to "accepted")
        )
    }

    // -- notifications --
    suspend fun notifications(recipient: String, limit: Int = 50): List<ChatNotification> =
        backend.list("chat_notifications", "*", mapOf("recipient_id" to "eq.$recipient"), "created_at", false, limit)

    suspend fun unreadCount(recipient: String): Int =
        backend.restCount("chat_notifications", mapOf("recipient_id" to "eq.$recipient", "is_read" to "eq.false"))

    suspend fun markAllRead(recipient: String) {
        backend.restPatch<Unit>(
            "chat_notifications",
            mapOf("recipient_id" to "eq.$recipient", "is_read" to "eq.false"),
            mapOf("is_read" to true)
        )
    }

    suspend fun messageChannel(messageId: Long): Long? {
        val el: JsonElement = backend.restGet(
            "chat_messages", "id,channel_id", mapOf("id" to "eq.$messageId"), limit = 1
        )
        return ((el as? JsonArray)?.firstOrNull() as? JsonObject)?.get("channel_id")?.jsonPrimitive?.longOrNull
    }

    // -- stats --
    suspend fun counts(userId: String): Map<String, Int> = mapOf(
        "downloads" to backend.restCount("content_downloads", mapOf("user_id" to "eq.$userId")),
        "favorites" to backend.restCount("content_favorites", mapOf("user_id" to "eq.$userId")),
        "messages" to backend.restCount("chat_messages", mapOf("user_id" to "eq.$userId")),
        "content" to backend.restCount("content_items", mapOf("created_by" to "eq.$userId"))
    )

    suspend fun deleteMyAccount() {
        backend.rpc("delete_my_account")
    }

    // -- admin --
    suspend fun adminList(order: String = "created_at", ascending: Boolean = false, limit: Int = 100): List<Profile> =
        backend.list("profiles", "id,username,email,avatar_url,role,plan,plan_expires_at,created_at", order = order, ascending = ascending, limit = limit)

    suspend fun adminUpdateProfile(targetUser: String, plan: String, role: String, expiresIso: String?) {
        backend.rpc(
            "admin_update_profile",
            mapOf("target_user" to targetUser, "target_plan" to plan, "target_role" to role, "target_expires" to expiresIso)
        )
    }
}

// ---------------------------------------------------------------------------
// Marketplace content
// ---------------------------------------------------------------------------

class ContentRepository(val backend: SupabaseBackend) {
    suspend fun featured(limit: Int = 8): List<ContentItem> = backend.list(
        "content_items", "*", mapOf("is_published" to "eq.true", "is_featured" to "eq.true"),
        "created_at", false, limit
    )

    suspend fun latest(limit: Int = 6): List<ContentItem> = backend.list(
        "content_items", "*", mapOf("is_published" to "eq.true"), "created_at", false, limit
    )

    suspend fun byType(type: String, order: String = "created_at", limit: Int = 60): List<ContentItem> =
        backend.list(
            "content_items", "*",
            mapOf("content_type" to "eq.$type", "is_published" to "eq.true"), order, false, limit
        )

    suspend fun exclusives(limit: Int = 100): List<ContentItem> = backend.list(
        "content_items", "*",
        mapOf("is_published" to "eq.true", "is_exclusive" to "eq.true"), "created_at", false, limit
    )

    suspend fun bySlug(slug: String): ContentItem? = backend.one(
        "content_items", "*,profiles:created_by(username,role,plan)",
        mapOf("slug" to "eq.$slug", "is_published" to "eq.true")
    )

    suspend fun byId(id: Long): ContentItem? = backend.one(
        "content_items", "*,profiles:created_by(username,role,plan)", mapOf("id" to "eq.$id")
    )

    suspend fun byCreator(creatorId: String, limit: Int = 60): List<ContentItem> = backend.list(
        "content_items", "*",
        mapOf("created_by" to "eq.$creatorId", "is_published" to "eq.true"), "created_at", false, limit
    )

    suspend fun related(item: ContentItem, limit: Int = 6): List<ContentItem> = backend.list(
        "content_items", "*",
        mapOf("content_type" to "eq.${item.contentType}", "is_published" to "eq.true", "id" to "neq.${item.id}"),
        "download_count", false, limit
    )

    suspend fun search(query: String, type: String? = null, limit: Int = 60): List<ContentItem> {
        val f = mutableMapOf(
            "or" to "(title.ilike.*$query*,description.ilike.*$query*)",
            "is_published" to "eq.true"
        )
        if (type != null) f["content_type"] = "eq.$type"
        return backend.list("content_items", "*", f, "download_count", false, limit)
    }

    suspend fun recordView(id: Long) {
        try {
            backend.rpc("record_content_view", mapOf("item_id" to id))
        } catch (_: Exception) { }
    }

    suspend fun recordDownload(id: Long) {
        try {
            backend.rpc("record_content_download", mapOf("item_id" to id))
        } catch (_: Exception) { }
    }

    suspend fun resolveDownloadUrl(item: ContentItem): String? {
        item.fileUrl?.takeIf { it.startsWith("http") }?.let { return it }
        val path = item.filePath ?: item.fileUrl?.removePrefix("/") ?: return null
        return try {
            backend.storageSignedUrl("content-files", path, 300)
        } catch (_: Exception) {
            try {
                backend.storagePublicUrl("content-files", path)
            } catch (_: Exception) { null }
        }
    }

    // -- favorites --
    suspend fun isFavorite(userId: String, contentId: Long): Boolean {
        val el: JsonElement = backend.restGet(
            "content_favorites", "content_id",
            mapOf("user_id" to "eq.$userId", "content_id" to "eq.$contentId"), limit = 1
        )
        return (el as? JsonArray)?.isNotEmpty() == true
    }

    /** Returns new state (true = favorited). */
    suspend fun toggleFavorite(userId: String, contentId: Long): Boolean {
        return if (isFavorite(userId, contentId)) {
            backend.restDelete("content_favorites", mapOf("user_id" to "eq.$userId", "content_id" to "eq.$contentId"))
            false
        } else {
            backend.restPost<Unit>("content_favorites", mapOf("user_id" to userId, "content_id" to contentId))
            true
        }
    }

    suspend fun myFavorites(userId: String): List<ContentItem> {
        val rows: List<ContentFavorite> = backend.list(
            "content_favorites", "*,content_items(*)", mapOf("user_id" to "eq.$userId"),
            "created_at", false, 100
        )
        return rows.mapNotNull { it.content }
    }

    suspend fun myDownloads(userId: String, limit: Int = 30): List<ContentItem> {
        val rows: List<ContentDownload> = backend.list(
            "content_downloads", "content_items(*)", mapOf("user_id" to "eq.$userId"),
            "created_at", false, limit
        )
        return rows.mapNotNull { it.content }
    }

    // -- ratings --
    suspend fun myRating(userId: String, contentId: Long): Int? {
        val rows: List<ContentReview> = backend.list(
            "content_reviews", "*",
            mapOf("user_id" to "eq.$userId", "content_id" to "eq.$contentId"), limit = 1
        )
        return rows.firstOrNull()?.rating
    }

    suspend fun rate(userId: String, contentId: Long, rating: Int) {
        backend.restPost<Unit>(
            "content_reviews",
            mapOf("user_id" to userId, "content_id" to contentId, "rating" to rating.coerceIn(1, 5)),
            upsert = true
        )
    }

    // -- admin --
    suspend fun adminList(limit: Int = 100): List<ContentItem> =
        backend.list("content_items", "*", order = "created_at", ascending = false, limit = limit)

    suspend fun setPublished(id: Long, published: Boolean) {
        backend.restPatch<Unit>("content_items", mapOf("id" to "eq.$id"), mapOf("is_published" to published))
    }

    suspend fun delete(id: Long) {
        backend.restDelete("content_items", mapOf("id" to "eq.$id"))
    }
}

// ---------------------------------------------------------------------------
// AC wallet
// ---------------------------------------------------------------------------

class WalletRepository(val backend: SupabaseBackend) {
    suspend fun balance(userId: String): Long {
        val el: JsonElement = backend.restGet(
            "ac_wallets", "balance", mapOf("user_id" to "eq.$userId"), limit = 1
        )
        return (((el as? JsonArray)?.firstOrNull() as? JsonObject)?.get("balance")?.jsonPrimitive?.longOrNull) ?: 0L
    }

    suspend fun transactions(userId: String, limit: Int = 100): List<AcTransaction> = backend.list(
        "ac_transactions", "*", mapOf("user_id" to "eq.$userId"), "created_at", false, limit
    )

    suspend fun tasks(): List<AcTask> = backend.list(
        "ac_tasks", "*", mapOf("active" to "eq.true"), "created_at", true
    )

    suspend fun events(): List<AcEvent> = backend.list(
        "ac_events", "*", mapOf("active" to "eq.true"), "starts_at", true
    )

    suspend fun achievements(): List<AcAchievement> = try {
        backend.list("ac_achievements", "*")
    } catch (_: Exception) { emptyList() }

    suspend fun claimTask(taskId: Long): JsonElement = backend.rpc("ac_claim_task", mapOf("p_task_id" to taskId))
    suspend fun claimEvent(eventId: Long): JsonElement = backend.rpc("ac_claim_event", mapOf("p_event_id" to eventId))
    suspend fun claimLoginRewards(): JsonElement = try {
        backend.rpc("ac_claim_login_rewards")
    } catch (e: Exception) { JsonNull }

    suspend fun claimProfileComplete(): JsonElement = try {
        backend.rpc("ac_claim_profile_complete")
    } catch (e: Exception) { JsonNull }

    suspend fun claimCommunityParticipation(messageId: Long?): JsonElement = try {
        backend.rpc("ac_claim_community_participation", mapOf("p_message_id" to messageId))
    } catch (e: Exception) { JsonNull }

    suspend fun contentPrice(contentId: Long): Long? {
        val el = backend.rpc("ac_content_price", mapOf("p_content_id" to contentId))
        return (el as? JsonObject)?.values?.firstOrNull()?.jsonPrimitive?.longOrNull
            ?: (el as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
    }

    suspend fun unlockContent(contentId: Long): JsonElement =
        backend.rpc("ac_unlock_content", mapOf("p_content_id" to contentId))

    // -- admin --
    suspend fun adminPreview(): JsonElement = backend.rpc("ac_admin_preview")
    suspend fun adminStats(): JsonElement = backend.rpc("ac_admin_stats")
    suspend fun adminHistory(): JsonElement = backend.rpc("ac_admin_history")
    suspend fun adminSearchUsers(query: String): JsonElement =
        backend.rpc("ac_admin_search_users", mapOf("p_query" to query))

    suspend fun adminAdjust(userId: String, amount: Long, reason: String, action: String) {
        backend.rpc(
            "ac_admin_adjust",
            mapOf(
                "p_user_id" to userId, "p_amount" to amount, "p_reason" to reason,
                "p_action" to action, "p_request_key" to java.util.UUID.randomUUID().toString()
            )
        )
    }

    suspend fun adminGlobalReward(amount: Long, reason: String) {
        backend.rpc(
            "ac_admin_global_reward",
            mapOf("p_amount" to amount, "p_reason" to reason, "p_request_key" to java.util.UUID.randomUUID().toString())
        )
    }

    suspend fun adminTasks(): List<AcTask> = backend.list("ac_tasks", "*", order = "created_at", ascending = false)
    suspend fun adminAddTask(slug: String, title: String, desc: String, reward: Long, freq: String, maxClaims: Int?) {
        backend.restPost<Unit>(
            "ac_tasks",
            mapOf(
                "slug" to slug, "title" to title, "description" to desc, "reward_ac" to reward,
                "frequency" to freq, "max_claims" to maxClaims, "active" to true
            )
        )
    }

    suspend fun adminDeleteTask(id: Long) {
        backend.restDelete("ac_tasks", mapOf("id" to "eq.$id"))
    }
}

// ---------------------------------------------------------------------------
// Subscriptions & payments
// ---------------------------------------------------------------------------

class PaymentsRepository(val backend: SupabaseBackend) {
    suspend fun plans(): List<SubscriptionPlan> = backend.list("subscription_plans", "*")
    suspend fun methods(): List<PaymentMethodSetting> = backend.list("payment_method_settings", "*")

    suspend fun createOrder(userId: String, plan: SubscriptionPlan, method: String, reference: String): PaymentOrder {
        val rows: List<PaymentOrder> = backend.restPost(
            "payment_orders",
            mapOf(
                "user_id" to userId, "plan_id" to plan.id, "amount_lyd" to plan.priceLyd,
                "payment_method" to method, "reference_code" to reference
            )
        )
        return rows.first()
    }

    suspend fun submitReceipt(orderId: Long, receiptPath: String, txRef: String?): PaymentOrder? {
        val rows: List<PaymentOrder> = backend.restPatch(
            "payment_orders", mapOf("id" to "eq.$orderId"),
            mapOf("receipt_url" to receiptPath, "transaction_reference" to txRef, "status" to "submitted")
        )
        return rows.firstOrNull()
    }

    suspend fun myOrders(userId: String): List<PaymentOrder> = backend.list(
        "payment_orders", "*", mapOf("user_id" to "eq.$userId"), "created_at", false, 50
    )

    suspend fun receiptUrl(path: String): String = backend.storageSignedUrl("payment-receipts", path, 120)

    // -- admin --
    suspend fun adminOrders(): List<PaymentOrder> = backend.list(
        "payment_orders", "*,profiles(username,email)", order = "created_at", ascending = false
    )

    suspend fun approve(orderId: Long) {
        backend.rpc("approve_payment_order", mapOf("order_id" to orderId))
    }

    suspend fun reject(orderId: Long) {
        backend.restPatch<Unit>("payment_orders", mapOf("id" to "eq.$orderId"), mapOf("status" to "rejected"))
    }

    suspend fun adminUpdatePlan(id: String, price: Double, days: Int) {
        backend.restPatch<Unit>("subscription_plans", mapOf("id" to "eq.$id"), mapOf("price_lyd" to price, "duration_days" to days))
    }

    suspend fun adminUpsertMethod(method: String, name: String?, identifier: String?, instructions: String?) {
        backend.restPost<Unit>(
            "payment_method_settings",
            mapOf("method" to method, "account_name" to name, "account_identifier" to identifier, "instructions" to instructions),
            upsert = true, onConflict = "method"
        )
    }

    suspend fun adminDeleteMethod(id: Long) {
        backend.restDelete("payment_method_settings", mapOf("id" to "eq.$id"))
    }
}

// ---------------------------------------------------------------------------
// Site assets (avatars / banners)
// ---------------------------------------------------------------------------

class AssetsRepository(val backend: SupabaseBackend) {
    suspend fun all(): List<SiteAsset> = backend.list("site_assets", "*", mapOf("enabled" to "eq.true"))
    suspend fun adminAll(): List<SiteAsset> =
        backend.list("site_assets", "*", order = "created_at", ascending = false)

    suspend fun add(type: String, name: String, plan: String, fileUrl: String) {
        backend.restPost<Unit>(
            "site_assets",
            mapOf("asset_type" to type, "name" to name, "required_plan" to plan, "file_url" to fileUrl)
        )
    }

    suspend fun delete(id: Long) {
        backend.restDelete("site_assets", mapOf("id" to "eq.$id"))
    }
}

// ---------------------------------------------------------------------------
// Creators
// ---------------------------------------------------------------------------

class CreatorsRepository(val backend: SupabaseBackend) {
    suspend fun featured(): List<FeaturedCreator> = backend.list("featured_creators", "*", order = "position", ascending = true)

    suspend fun creators(limit: Int = 100): List<Profile> = backend.list(
        "profiles", "id,username,avatar_url,banner_url,bio,role,plan,created_at",
        mapOf("role" to "eq.creator"), "username", true, limit
    )

    suspend fun isFollowing(me: String, creator: String): Boolean {
        val el: JsonElement = backend.restGet(
            "creator_follows", "creator_id",
            mapOf("creator_id" to "eq.$creator", "follower_id" to "eq.$me"), limit = 1
        )
        return (el as? JsonArray)?.isNotEmpty() == true
    }

    suspend fun setFollowing(me: String, creator: String, follow: Boolean) {
        if (follow) {
            backend.restPost<Unit>("creator_follows", mapOf("follower_id" to me, "creator_id" to creator))
        } else {
            backend.restDelete("creator_follows", mapOf("follower_id" to "eq.$me", "creator_id" to "eq.$creator"))
        }
    }

    suspend fun followersCount(creator: String): Long {
        return try {
            val el = backend.rpc("creator_followers_count", mapOf("p_creator_id" to creator))
            (el as? JsonObject)?.values?.firstOrNull()?.jsonPrimitive?.longOrNull
                ?: (el as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull ?: 0L
        } catch (_: Exception) {
            backend.restCount("creator_follows", mapOf("creator_id" to "eq.$creator")).toLong()
        }
    }

    suspend fun myApplication(userId: String): CreatorApplication? =
        backend.one("creator_applications", "*", mapOf("user_id" to "eq.$userId"), "created_at", false)

    suspend fun submitApplication(fields: Map<String, Any?>): JsonElement =
        backend.rpc("submit_creator_application", fields)

    suspend fun resubmitApplication(appId: Long, fields: Map<String, Any?>): JsonElement =
        backend.rpc("resubmit_creator_application", fields + ("p_application_id" to appId))

    suspend fun applicationMessages(appId: Long): List<CreatorApplicationMessage> = backend.list(
        "creator_application_messages", "*", mapOf("application_id" to "eq.$appId"), "created_at", true
    )

    suspend fun mySubmissions(userId: String): List<CreatorSubmission> = backend.list(
        "creator_submissions", "*", mapOf("creator_id" to "eq.$userId"), "created_at", false
    )

    suspend fun submitContent(fields: Map<String, Any?>) {
        backend.restPost<Unit>("creator_submissions", fields)
    }

    suspend fun myTips(creatorId: String): List<CreatorTipOrder> = backend.list(
        "creator_tip_orders", "*", mapOf("creator_id" to "eq.$creatorId"), "created_at", false, 100
    )

    suspend fun myEarnings(creatorId: String): List<CreatorEarning> = backend.list(
        "creator_earnings", "*", mapOf("creator_id" to "eq.$creatorId"), "created_at", false, 100
    )

    suspend fun myWallet(creatorId: String): CreatorWallet? =
        backend.one("creator_wallets", "*", mapOf("creator_id" to "eq.$creatorId"))

    suspend fun createTip(creatorId: String, amount: Double, method: String, message: String): Long {
        val el = backend.rpc(
            "creator_create_tip",
            mapOf("p_creator_id" to creatorId, "p_amount_lyd" to amount, "p_payment_method" to method, "p_message" to message)
        )
        val obj = el as? JsonObject
        return obj?.get("id")?.jsonPrimitive?.longOrNull
            ?: obj?.values?.firstOrNull()?.jsonPrimitive?.longOrNull
            ?: (el as? kotlinx.serialization.json.JsonPrimitive)?.longOrNull
            ?: throw BackendException("Could not create tip order")
    }

    suspend fun submitTipReceipt(tipId: Long, receiptPath: String, txRef: String?) {
        backend.rpc(
            "creator_submit_tip_receipt",
            mapOf("p_tip_id" to tipId, "p_receipt_path" to receiptPath, "p_transaction_reference" to txRef)
        )
    }

    suspend fun approveTip(tipId: Long) {
        backend.rpc("creator_approve_tip", mapOf("p_tip_id" to tipId))
    }

    suspend fun rejectTip(tipId: Long) {
        backend.rpc("creator_reject_tip", mapOf("p_tip_id" to tipId))
    }

    suspend fun tipHistoryAsSupporter(supporterId: String): List<CreatorTipOrder> = try {
        backend.list("creator_tip_orders", "*", mapOf("supporter_id" to "eq.$supporterId"), "created_at", false, 100)
    } catch (_: Exception) { emptyList() }

    // -- admin --
    suspend fun pendingApplications(): List<CreatorApplication> = backend.list(
        "creator_applications", "*,profiles(username)", mapOf("status" to "eq.pending"), "created_at", false
    )

    suspend fun reviewApplication(appId: Long, status: String, note: String?) {
        backend.rpc("review_creator_application", mapOf("p_application_id" to appId, "p_status" to status, "p_note" to note))
    }

    suspend fun pendingSubmissions(): List<CreatorSubmission> = backend.list(
        "creator_submissions", "*,profiles(username)", mapOf("status" to "eq.pending"), "created_at", false
    )

    suspend fun publishSubmission(sub: CreatorSubmission) {
        val slugBase = sub.title.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').take(60)
        val slug = if (slugBase.isBlank()) "content-${sub.id}" else "$slugBase-${sub.id}"
        backend.restPost<Unit>(
            "content_items",
            mapOf(
                "slug" to slug,
                "content_type" to sub.contentType,
                "title" to sub.title,
                "description" to (sub.description ?: ""),
                "category" to (sub.category ?: "general"),
                "version" to (sub.version ?: "1.0.0"),
                "plan" to (sub.requestedPlan ?: "free"),
                "cover_url" to sub.previewUrl,
                "file_path" to sub.filePath,
                "video_url" to sub.videoUrl,
                "is_published" to true,
                "created_by" to sub.creatorId
            )
        )
        backend.restPatch<Unit>(
            "creator_submissions", mapOf("id" to "eq.${sub.id}"),
            mapOf("status" to "published", "reviewed_at" to isoNowUtc())
        )
    }

    suspend fun rejectSubmission(id: Long) {
        backend.restPatch<Unit>("creator_submissions", mapOf("id" to "eq.$id"), mapOf("status" to "rejected"))
    }
}
