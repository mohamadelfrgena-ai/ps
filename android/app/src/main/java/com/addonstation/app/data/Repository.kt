package com.addonstation.app.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.UUID

class Repository(context: Context) {
    private val app = context.applicationContext
    val api = SupabaseClient(app)
    val catalog: List<ContentItem> by lazy { loadCatalog() }
    val fallbackCreators: List<Creator> by lazy { loadCreators() }

    fun session(): Session? = api.session()
    fun language(): String = api.pref("language", "ar")
    fun theme(): String = api.pref("theme", "dark")
    fun chatRulesAccepted(): Boolean = api.flag("chat_rules")
    fun acceptChatRules() = api.putFlag("chat_rules", true)

    fun signIn(email: String, password: String) = api.signIn(email, password)
    fun signUp(email: String, password: String, username: String) = api.signUp(email, password, username)
    fun recover(email: String) = api.recover(email)
    fun signOut() = api.signOut()

    fun profile(userId: String? = session()?.userId): Profile? {
        val id = userId ?: return null
        return firstRow("profiles", "select=*&id=eq.$id")?.toProfile()
    }

    fun home(): HomeBundle {
        val featured = queryContent("is_featured=eq.true&order=created_at.desc&limit=8")
        val latest = queryContent("order=created_at.desc&limit=8")
        val top = queryContent("order=rating_average.desc&limit=8")
        return HomeBundle(
            featured = featured.ifEmpty { catalog.take(8) },
            latest = latest.ifEmpty { catalog.take(6) },
            top = top.ifEmpty { catalog.sortedByDescending { it.rating }.take(6) },
            creators = creators().filter { it.featured }.ifEmpty { creators() }.take(8)
        )
    }

    fun listContent(type: String, sort: String, query: String): List<ContentItem> {
        val order = when (sort) {
            "downloads" -> "download_count.desc"
            "views" -> "view_count.desc"
            "rating" -> "rating_average.desc"
            else -> "created_at.desc"
        }
        val search = if (query.isBlank()) "" else "&title=ilike.*${enc(query)}*"
        val live = queryContent("content_type=eq.$type$search&order=$order&limit=80")
        val source = live.ifEmpty { catalog.filter { it.contentType == type } }
        val filtered = if (query.isBlank()) source else source.filter {
            it.title.contains(query, true) || it.description.contains(query, true)
        }
        return when (sort) {
            "downloads" -> filtered.sortedByDescending { it.downloads }
            "views" -> filtered.sortedByDescending { it.views }
            "rating" -> filtered.sortedByDescending { it.rating }
            else -> filtered
        }
    }

    fun exclusives(): List<ContentItem> {
        val live = queryContent("is_exclusive=eq.true&order=created_at.desc&limit=80")
        return live.ifEmpty { catalog.filter { it.plan != "free" } }
    }

    fun item(slug: String): ContentItem? {
        val live = queryContent("slug=eq.${enc(slug)}&limit=1").firstOrNull()
        return live ?: catalog.firstOrNull { it.slug == slug }
    }

    fun similar(item: ContentItem): List<ContentItem> {
        val live = queryContent("content_type=eq.${item.contentType}&id=neq.${enc(item.id)}&order=download_count.desc&limit=6")
        return live.ifEmpty { catalog.filter { it.contentType == item.contentType && it.slug != item.slug }.take(6) }
    }

    fun isFavorite(contentId: String): Boolean {
        val user = session()?.userId ?: return false
        return firstRow("content_favorites", "select=content_id&user_id=eq.$user&content_id=eq.$contentId") != null
    }

    fun toggleFavorite(contentId: String): Boolean {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        return if (isFavorite(contentId)) {
            api.delete("content_favorites", "user_id=eq.$user&content_id=eq.$contentId")
            false
        } else {
            api.insert("content_favorites", JSONObject().put("user_id", user).put("content_id", contentId))
            true
        }
    }

    fun acPrice(contentId: String): Int = runCatching {
        api.rpc("ac_content_price", JSONObject().put("p_content_id", contentId)).trim().trim('"').toIntOrNull() ?: 0
    }.getOrDefault(0)

    fun downloadUrl(item: ContentItem): String {
        if (session() == null) throw ApiException(401, "سجل الدخول أولًا")
        if (item.filePath.isBlank()) throw ApiException(0, "ملف التحميل غير مرفوع بعد")
        val price = acPrice(item.id)
        if (price > 0) api.rpc("ac_unlock_content", JSONObject().put("p_content_id", item.id))
        runCatching { api.rpc("record_content_download", JSONObject().put("item_id", item.id)) }
        runCatching { api.rpc("record_content_view", JSONObject().put("item_id", item.id)) }
        return api.signedUrl("content-files", item.filePath, 120)
    }

    fun creators(): List<Creator> {
        val rows = runCatching {
            api.select("profiles", "select=*&role=eq.creator&order=created_at.desc&limit=40")
        }.getOrDefault(JSONArray())
        if (rows.length() == 0) return fallbackCreators
        val ids = (0 until rows.length()).map { rows.getJSONObject(it).s("id") }.filter { it.isNotBlank() }
        val items = if (ids.isEmpty()) emptyList() else queryContent("created_by=in.(${ids.joinToString(",")})&limit=200")
        val featured = runCatching {
            api.select("featured_creators", "select=creator_id,position&active=eq.true&order=position&limit=20")
        }.getOrDefault(JSONArray())
        val positions = buildMap {
            for (i in 0 until featured.length()) {
                val row = featured.getJSONObject(i)
                put(row.s("creator_id"), row.n("position").toInt())
            }
        }
        return (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            val id = row.s("id")
            row.toCreator(items.filter { it.createdBy == id }).copy(featured = positions.containsKey(id))
        }.sortedBy { positions[it.id] ?: 999 }
    }

    fun creatorContent(id: String): List<ContentItem> = queryContent("created_by=eq.$id&order=created_at.desc&limit=40")

    fun followerCount(id: String): Int = runCatching {
        api.rpc("creator_followers_count", JSONObject().put("p_creator_id", id)).trim().trim('"').toIntOrNull() ?: 0
    }.getOrDefault(0)

    fun isFollowing(id: String): Boolean {
        val user = session()?.userId ?: return false
        return firstRow("creator_follows", "select=creator_id&follower_id=eq.$user&creator_id=eq.$id") != null
    }

    fun setFollowing(id: String, follow: Boolean) {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        if (follow) {
            api.insert("creator_follows", JSONObject().put("follower_id", user).put("creator_id", id))
        } else {
            api.delete("creator_follows", "follower_id=eq.$user&creator_id=eq.$id")
        }
    }

    fun channels(): List<ChatChannel> {
        val rows = runCatching { api.select("chat_channels", "select=*&order=position.asc") }.getOrDefault(JSONArray())
        if (rows.length() == 0) return fallbackChannels()
        return (0 until rows.length()).map {
            val row = rows.getJSONObject(it)
            ChatChannel(row.s("id"), row.s("slug"), row.s("name"), row.s("description"), row.n("position").toInt())
        }
    }

    fun messages(channelId: String): List<ChatMessage> {
        val rows = runCatching {
            api.select(
                "chat_messages",
                "select=id,content,sticker_url,created_at,user_id,channel_id,reply_to_id&channel_id=eq.$channelId&status=eq.published&order=created_at.desc&limit=80"
            )
        }.getOrElse {
            api.select(
                "chat_messages",
                "select=id,content,sticker_url,created_at,user_id,channel_id,reply_to_id&channel_id=eq.$channelId&order=created_at.desc&limit=80"
            )
        }
        val list = (0 until rows.length()).map { rows.getJSONObject(it) }.reversed()
        val ids = list.map { it.s("user_id") }.filter { it.isNotBlank() }.distinct()
        val profiles = if (ids.isEmpty()) emptyMap() else runCatching {
            val found = api.select("profiles", "select=id,username,avatar_url,plan,role&id=in.(${ids.joinToString(",")})")
            buildMap {
                for (i in 0 until found.length()) {
                    val profile = found.getJSONObject(i)
                    put(profile.s("id"), profile)
                }
            }
        }.getOrDefault(emptyMap())
        return list.map { row ->
            val profile = profiles[row.s("user_id")]
            ChatMessage(
                id = row.s("id"),
                channelId = row.s("channel_id"),
                userId = row.s("user_id"),
                content = row.s("content"),
                stickerUrl = row.s("sticker_url"),
                createdAt = row.s("created_at"),
                username = profile?.s("username", "Player") ?: "Player",
                avatarUrl = profile?.s("avatar_url", "/imgs/profiles/default-profile.jpg") ?: "/imgs/profiles/default-profile.jpg",
                plan = profile?.s("plan", "free") ?: "free",
                role = profile?.s("role", "user") ?: "user",
                replyToId = row.s("reply_to_id")
            )
        }
    }

    fun sendMessage(channelId: String, content: String, stickerUrl: String?, replyToId: String?): String {
        val body = JSONObject()
            .put("p_channel_id", channelId)
            .put("p_content", content)
            .put("p_sticker_url", stickerUrl ?: JSONObject.NULL)
            .put("p_reply_to_id", replyToId?.toLongOrNull() ?: JSONObject.NULL)
            .put("p_thread_root_id", JSONObject.NULL)
        return try {
            api.rpc("send_chat_message", body).trim().trim('"')
        } catch (error: ApiException) {
            val user = session()?.userId ?: throw error
            val inserted = api.insert(
                "chat_messages",
                JSONObject()
                    .put("user_id", user)
                    .put("channel_id", channelId)
                    .put("content", content)
                    .put("sticker_url", stickerUrl ?: JSONObject.NULL)
                    .put("status", "published")
            )
            inserted.optJSONObject(0)?.s("id").orEmpty()
        }
    }

    fun react(messageId: String, emoji: String) {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        api.insert(
            "chat_reactions",
            JSONObject().put("message_id", messageId.toLong()).put("user_id", user).put("emoji", emoji)
        )
    }

    fun friendRequest(targetId: String) {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        api.insert(
            "user_friends",
            JSONObject().put("requester_id", user).put("receiver_id", targetId).put("status", "pending")
        )
        runCatching {
            api.insert(
                "chat_notifications",
                JSONObject().put("recipient_id", targetId).put("actor_id", user).put("notification_type", "friend_request")
            )
        }
    }

    fun notifications(): List<JSONObject> {
        val user = session()?.userId ?: return emptyList()
        val rows = runCatching {
            api.select("chat_notifications", "select=*&recipient_id=eq.$user&order=created_at.desc&limit=60")
        }.getOrDefault(JSONArray())
        return (0 until rows.length()).map { rows.getJSONObject(it) }
    }

    fun markNotificationsRead() {
        val user = session()?.userId ?: return
        runCatching { api.update("chat_notifications", "recipient_id=eq.$user&is_read=eq.false", JSONObject().put("is_read", true)) }
    }

    fun acceptFriend(requesterId: String) {
        val user = session()?.userId ?: return
        api.update("user_friends", "requester_id=eq.$requesterId&receiver_id=eq.$user", JSONObject().put("status", "accepted"))
    }

    fun wallet(): WalletData {
        val user = session()?.userId ?: return WalletData(0, emptyList(), emptyList(), emptyList())
        val balance = firstRow("ac_wallets", "select=balance&user_id=eq.$user")?.n("balance")?.toInt() ?: 0
        val tx = rows("ac_transactions", "select=*&user_id=eq.$user&order=created_at.desc&limit=40")
        val tasks = rows("ac_tasks", "select=*&active=eq.true&order=created_at.asc")
        val events = rows("ac_events", "select=*&active=eq.true&order=starts_at.asc")
        return WalletData(balance, tx, tasks, events)
    }

    fun balance(): Int? {
        val user = session()?.userId ?: return null
        return firstRow("ac_wallets", "select=balance&user_id=eq.$user")?.n("balance")?.toInt()
    }

    fun claimTask(id: String) = api.rpc("ac_claim_task", JSONObject().put("p_task_id", id))
    fun claimEvent(id: String) = api.rpc("ac_claim_event", JSONObject().put("p_event_id", id))
    fun claimProfile() = api.rpc("ac_claim_profile_complete")
    fun claimLogin() = runCatching { api.rpc("ac_claim_login_rewards") }

    fun favorites(): List<ContentItem> {
        val user = session()?.userId ?: return emptyList()
        val rows = rows("content_favorites", "select=content_id&user_id=eq.$user&limit=40")
        return rows.mapNotNull { row ->
            val id = row.s("content_id")
            queryContent("id=eq.$id&limit=1").firstOrNull()
        }
    }

    fun myContent(): List<ContentItem> {
        val user = session()?.userId ?: return emptyList()
        return queryContent("created_by=eq.$user&order=created_at.desc&limit=40")
    }

    fun downloads(): List<ContentItem> {
        val rows = runCatching {
            api.select("content_downloads", "select=content_id&order=created_at.desc&limit=30")
        }.getOrDefault(JSONArray())
        return (0 until rows.length()).mapNotNull { index ->
            val id = rows.getJSONObject(index).s("content_id")
            queryContent("id=eq.$id&limit=1").firstOrNull()
        }
    }

    fun updateProfile(fields: JSONObject): Profile? {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        api.update("profiles", "id=eq.$user", fields)
        return profile(user)
    }

    fun assets(type: String): List<JSONObject> =
        rows("site_assets", "select=*&enabled=eq.true&asset_type=eq.$type&limit=80")

    fun saveUsername(username: String) {
        updateProfile(JSONObject().put("username", username))
        api.putPref("username", username)
    }

    fun deleteAccount() {
        api.rpc("delete_my_account")
        api.clearSession()
    }

    fun application(): JSONObject? {
        val user = session()?.userId ?: return null
        return rows("creator_applications", "select=*&applicant_id=eq.$user&order=created_at.desc&limit=1").firstOrNull()
    }

    fun submitApplication(fields: JSONObject, resubmitId: String?) {
        val payload = JSONObject()
            .put("p_creator_name", fields.s("creator_name"))
            .put("p_country", fields.s("country"))
            .put("p_content_types", fields.optJSONArray("content_types") ?: JSONArray())
            .put("p_bio", fields.s("bio"))
            .put("p_experience", fields.s("experience"))
            .put("p_publish_plan", fields.s("publish_plan"))
            .put("p_previous_work", fields.s("previous_work"))
            .put("p_portfolio_links", fields.s("portfolio_links"))
            .put("p_motivation", fields.s("motivation"))
            .put("p_agreed_guidelines", fields.optBoolean("agreed_guidelines"))
            .put("p_owns_content", fields.optBoolean("owns_content"))
        if (resubmitId != null) {
            api.rpc("resubmit_creator_application", payload.put("p_application_id", resubmitId))
        } else {
            api.rpc("submit_creator_application", payload)
        }
    }

    fun studio(): StudioData {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        val wallet = firstRow("creator_wallets", "select=*&creator_id=eq.$user")
        val tips = rows("creator_tip_orders", "select=*&creator_id=eq.$user&order=created_at.desc&limit=40")
        val content = queryContent("created_by=eq.$user&order=created_at.desc&limit=40", publishedOnly = false)
        return StudioData(
            availableLyd = wallet?.n("available_lyd") ?: 0.0,
            lifetimeLyd = wallet?.n("lifetime_earned_lyd") ?: 0.0,
            tips = tips,
            content = content
        )
    }

    fun uploadSubmission(type: String, title: String, category: String, version: String, description: String, preview: ByteArray, previewName: String, file: ByteArray, fileName: String) {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        val id = UUID.randomUUID().toString()
        val previewPath = "$user/$id-${clean(previewName)}"
        val contentPath = "creators/$user/$id-${clean(fileName)}"
        api.upload("creator-media", previewPath, preview, mime(previewName))
        api.upload("content-files", contentPath, file, "application/octet-stream")
        api.insert(
            "creator_submissions",
            JSONObject()
                .put("creator_id", user)
                .put("content_type", type)
                .put("title", title)
                .put("description", description)
                .put("category", category)
                .put("version", version)
                .put("preview_url", api.publicUrl("creator-media", previewPath))
                .put("file_path", contentPath)
                .put("requested_plan", "free")
        )
    }

    fun createTip(creatorId: String, amount: Double, method: String, message: String): JSONObject {
        val raw = api.rpc(
            "creator_create_tip",
            JSONObject()
                .put("p_creator_id", creatorId)
                .put("p_amount_lyd", amount)
                .put("p_payment_method", method)
                .put("p_message", message)
        )
        return if (raw.trim().startsWith("{")) JSONObject(raw) else JSONObject().put("id", raw.trim().trim('"'))
    }

    fun submitTipReceipt(tipId: String, bytes: ByteArray, name: String, reference: String) {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        val path = "$user/${UUID.randomUUID()}-${clean(name)}"
        api.upload("payment-receipts", path, bytes, mime(name))
        api.rpc(
            "creator_submit_tip_receipt",
            JSONObject()
                .put("p_tip_id", tipId)
                .put("p_receipt_path", path)
                .put("p_transaction_reference", reference.ifBlank { JSONObject.NULL })
        )
    }

    fun createSubscription(planId: String, amount: Double, method: String): String {
        val user = session()?.userId ?: throw ApiException(401, "سجل الدخول أولًا")
        val code = "AS-" + UUID.randomUUID().toString().take(8).uppercase()
        val inserted = api.insert(
            "payment_orders",
            JSONObject()
                .put("user_id", user)
                .put("plan_id", planId)
                .put("amount_lyd", amount)
                .put("payment_method", method)
                .put("reference_code", code)
                .put("status", "pending")
        )
        return inserted.optJSONObject(0)?.s("id").orEmpty().ifBlank { code }
    }

    fun attachReceipt(orderId: String, bytes: ByteArray, name: String) {
        val user = session()?.userId ?: return
        val path = "$user/${UUID.randomUUID()}-${clean(name)}"
        api.upload("payment-receipts", path, bytes, mime(name))
        val url = api.publicUrl("payment-receipts", path)
        api.update("payment_orders", "id=eq.$orderId", JSONObject().put("receipt_url", url).put("status", "submitted"))
    }

    fun adminStats(): String = runCatching { api.rpc("ac_admin_stats") }.getOrElse { it.message ?: "غير متاح" }

    private fun queryContent(filter: String, publishedOnly: Boolean = true): List<ContentItem> {
        val published = if (publishedOnly) "is_published=eq.true&" else ""
        return runCatching {
            val rows = api.select("content_items", "select=*,profiles:created_by(username,role,plan)&${published}$filter")
            (0 until rows.length()).map { rows.getJSONObject(it).toContent() }
        }.getOrElse {
            runCatching {
                val rows = api.select("content_items", "select=*&${published}$filter")
                (0 until rows.length()).map { rows.getJSONObject(it).toContent() }
            }.getOrDefault(emptyList())
        }
    }

    private fun firstRow(table: String, query: String): JSONObject? = rows(table, query).firstOrNull()

    private fun rows(table: String, query: String): List<JSONObject> = runCatching {
        val array = api.select(table, query)
        (0 until array.length()).map { array.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun loadCatalog(): List<ContentItem> = runCatching {
        val text = app.assets.open("catalog.json").bufferedReader().readText()
        val array = JSONArray(text)
        (0 until array.length()).map { array.getJSONObject(it).toContent() }
    }.getOrDefault(emptyList())

    private fun loadCreators(): List<Creator> = runCatching {
        val text = app.assets.open("creators.json").bufferedReader().readText()
        val array = JSONArray(text)
        (0 until array.length()).map { array.getJSONObject(it).toCreator() }
    }.getOrDefault(emptyList())

    private fun fallbackChannels() = listOf(
        ChatChannel("", "general", "general", "General community discussion", 1),
        ChatChannel("", "minecraft", "minecraft", "Minecraft gameplay and ideas", 2),
        ChatChannel("", "arabic", "العربية", "Arabic community chat", 3),
        ChatChannel("", "english", "English", "English community chat", 4),
        ChatChannel("", "creators", "creators", "Creator content and publishing", 5),
        ChatChannel("", "support", "support", "Help and support questions", 6)
    )

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")
    private fun clean(name: String) = name.replace(Regex("[^a-zA-Z0-9._-]"), "_")
    private fun mime(name: String) = when {
        name.endsWith(".png", true) -> "image/png"
        name.endsWith(".webp", true) -> "image/webp"
        name.endsWith(".gif", true) -> "image/gif"
        else -> "image/jpeg"
    }
}

data class HomeBundle(
    val featured: List<ContentItem>,
    val latest: List<ContentItem>,
    val top: List<ContentItem>,
    val creators: List<Creator>
)
