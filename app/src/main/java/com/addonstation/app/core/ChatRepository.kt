package com.addonstation.app.core

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class ChatRepository(val backend: SupabaseBackend) {

    // -- channels --
    suspend fun channels(): List<ChatChannel> =
        backend.list("chat_channels", "*", order = "position", ascending = true)

    suspend fun channelPreview(channelId: Long): ChatMessage? {
        val rows: List<ChatMessage> = backend.list(
            "chat_messages", "content,created_at,sticker_url",
            mapOf("channel_id" to "eq.$channelId", "status" to "eq.published"),
            "created_at", false, 1
        )
        return rows.firstOrNull()
    }

    // -- messages --
    suspend fun messages(channelId: Long, limit: Int = 100): List<ChatMessage> {
        val rows: List<ChatMessage> = backend.list(
            "chat_messages", "*",
            mapOf("channel_id" to "eq.$channelId", "status" to "eq.published"),
            "created_at", false, limit
        )
        return rows.sortedWith(compareBy({ TimeAgo.millis(it.createdAt) ?: 0L }, { it.id }))
    }

    suspend fun thread(rootId: Long): List<ChatMessage> {
        val rows: List<ChatMessage> = backend.list(
            "chat_messages", "*",
            mapOf("or" to "(id.eq.$rootId,thread_root_id.eq.$rootId)"),
            "created_at", true
        )
        return rows.sortedWith(compareBy({ TimeAgo.millis(it.createdAt) ?: 0L }, { it.id }))
    }

    suspend fun byIds(ids: Collection<Long>): Map<Long, ChatMessage> {
        if (ids.isEmpty()) return emptyMap()
        val rows: List<ChatMessage> = backend.list(
            "chat_messages", "*",
            mapOf("id" to "in.(${ids.distinct().joinToString(",")})")
        )
        return rows.associateBy { it.id }
    }

    suspend fun search(channelId: Long, query: String, limit: Int = 50): List<ChatMessage> =
        backend.list(
            "chat_messages", "*",
            mapOf("channel_id" to "eq.$channelId", "status" to "eq.published", "content" to "ilike.*$query*"),
            "created_at", false, limit
        )

    /** Sends via the same `send_chat_message` RPC the app uses. Returns the new row when available. */
    suspend fun send(
        channelId: Long,
        content: String,
        stickerUrl: String? = null,
        replyToId: Long? = null,
        threadRootId: Long? = null
    ): ChatMessage? {
        val el = backend.rpc(
            "send_chat_message",
            mapOf(
                "p_channel_id" to channelId,
                "p_content" to content,
                "p_sticker_url" to stickerUrl,
                "p_reply_to_id" to replyToId,
                "p_thread_root_id" to threadRootId
            )
        )
        return try {
            when (el) {
                is JsonObject -> backend.json.decodeFromJsonElement<ChatMessage>(el)
                else -> null
            }
        } catch (_: Exception) { null }
    }

    /** Media flow: upload -> send_chat_media_message -> finalize_chat_media. */
    suspend fun sendMedia(
        channelId: Long,
        bytes: ByteArray,
        mime: String,
        fileName: String,
        replyToId: Long? = null
    ): ChatMessage? {
        val path = "${channelId}/${System.currentTimeMillis()}_$fileName"
        backend.storageUpload("chat-media", path, bytes, mime)
        val el = backend.rpc(
            "send_chat_media_message",
            mapOf(
                "p_channel_id" to channelId,
                "p_message_type" to "image",
                "p_media_path" to path,
                "p_media_mime_type" to mime,
                "p_media_duration" to 0,
                "p_file_size" to bytes.size
            )
        )
        val id = ((el as? JsonObject)?.get("id")?.jsonPrimitive?.content
            ?: (el as? JsonObject)?.get("message_id")?.jsonPrimitive?.content)?.toLongOrNull()
        if (id != null) {
            try {
                backend.rpc("finalize_chat_media", mapOf("p_message_id" to id, "p_media_path" to path))
            } catch (_: Exception) { }
            return byIds(listOf(id))[id]
        }
        return null
    }

    /** Legacy image flow (message row + chat_images row), kept as fallback. */
    suspend fun sendImageLegacy(
        userId: String,
        channelId: Long,
        bytes: ByteArray,
        mime: String,
        fileName: String
    ): ChatMessage? {
        val msg = send(channelId, "")
        val id = msg?.id ?: run {
            // Fallback: direct insert like the oldest client
            val rows: List<ChatMessage> = backend.restPost(
                "chat_messages",
                mapOf("user_id" to userId, "channel_id" to channelId, "content" to "", "status" to "published")
            )
            rows.firstOrNull()
        } ?: return null
        val path = "$userId/${System.currentTimeMillis()}_$fileName"
        backend.storageUpload("chat-images", path, bytes, mime)
        backend.restPost<Unit>(
            "chat_images",
            mapOf(
                "message_id" to id.id, "uploader_id" to userId, "image_path" to path,
                "mime_type" to mime, "image_size" to bytes.size
            )
        )
        return id
    }

    suspend fun mediaSignedUrl(mediaPath: String): String =
        backend.storageSignedUrl("chat-media", mediaPath, 300)

    suspend fun chatImageSignedUrl(imagePath: String): String =
        backend.storageSignedUrl("chat-images", imagePath, 60)

    suspend fun imagesFor(messages: Collection<Long>): Map<Long, ChatImage> {
        if (messages.isEmpty()) return emptyMap()
        val rows: List<ChatImage> = backend.list(
            "chat_images", "*",
            mapOf("message_id" to "in.(${messages.distinct().joinToString(",")})")
        )
        return rows.associateBy { it.messageId }
    }

    suspend fun deleteMessage(id: Long) {
        backend.restDelete("chat_messages", mapOf("id" to "eq.$id"))
    }

    // -- reactions --
    suspend fun reactionsFor(ids: Collection<Long>): List<ChatReaction> {
        if (ids.isEmpty()) return emptyList()
        return backend.list(
            "chat_reactions", "*",
            mapOf("message_id" to "in.(${ids.distinct().joinToString(",")})")
        )
    }

    suspend fun toggleReaction(messageId: Long, userId: String, emoji: String): Boolean {
        val mine: List<ChatReaction> = backend.list(
            "chat_reactions", "*",
            mapOf("message_id" to "eq.$messageId", "user_id" to "eq.$userId", "emoji" to "eq.$emoji"), limit = 1
        )
        return if (mine.isNotEmpty()) {
            backend.restDelete(
                "chat_reactions",
                mapOf("message_id" to "eq.$messageId", "user_id" to "eq.$userId", "emoji" to "eq.$emoji")
            )
            false
        } else {
            backend.restPost<Unit>(
                "chat_reactions",
                mapOf("message_id" to messageId, "user_id" to userId, "emoji" to emoji),
                upsert = true
            )
            true
        }
    }

    // -- rules gate --
    suspend fun hasAcceptedRules(userId: String): Boolean {
        val el: JsonElement = backend.restGet(
            "chat_rule_acceptances", "user_id", mapOf("user_id" to "eq.$userId"), limit = 1
        )
        return (el as? JsonArray)?.isNotEmpty() == true
    }

    suspend fun acceptRules(userId: String) {
        backend.restPost<Unit>("chat_rule_acceptances", mapOf("user_id" to userId), upsert = true)
    }

    // -- mutes --
    suspend fun activeMute(userId: String): ChatMute? {
        val rows: List<ChatMute> = backend.list(
            "chat_mutes", "*", mapOf("user_id" to "eq.$userId"), "expires_at", false, 5
        )
        val now = System.currentTimeMillis()
        return rows.firstOrNull { m ->
            val exp = m.expiresAt?.let { TimeAgo.millis(it) } ?: Long.MAX_VALUE
            exp > now
        }
    }

    suspend fun muteUser(userId: String, minutes: Int, by: String, reason: String = "Moderator action") {
        val exp = isoNowUtcPlus(minutes * 60_000L)
        backend.restPost<Unit>(
            "chat_mutes",
            mapOf("user_id" to userId, "reason" to reason, "expires_at" to exp, "created_by" to by),
            upsert = true
        )
    }

    // -- reports --
    suspend fun report(messageId: Long, reporterId: String, reason: String) {
        backend.restPost<Unit>(
            "chat_reports",
            mapOf("message_id" to messageId, "reporter_id" to reporterId, "reason" to reason)
        )
    }

    suspend fun openReports(): List<ChatReport> = backend.list(
        "chat_reports", "id,reason,status,created_at,message_id,chat_messages(content,user_id)",
        mapOf("status" to "eq.open"), "created_at", false
    )

    suspend fun setReportStatus(id: Long, status: String) {
        backend.restPatch<Unit>("chat_reports", mapOf("id" to "eq.$id"), mapOf("status" to status))
    }

    // -- mentions --
    suspend fun notifyMentions(
        actorId: String,
        channelId: Long,
        messageId: Long?,
        usernames: List<String>,
        resolve: ProfileRepository
    ) {
        if (usernames.isEmpty()) return
        for (u in usernames.take(10)) {
            try {
                val target = resolve.search(u, 3).firstOrNull { it.username.equals(u, true) } ?: continue
                if (target.id == actorId) continue
                backend.restPost<Unit>(
                    "chat_notifications",
                    mapOf(
                        "recipient_id" to target.id, "actor_id" to actorId,
                        "notification_type" to "mention", "message_id" to messageId
                    )
                )
            } catch (_: Exception) { }
        }
    }
}

private fun isoNowUtcPlus(deltaMs: Long): String {
    val sdf = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
    sdf.timeZone = java.util.TimeZone.getTimeZone("UTC")
    return sdf.format(java.util.Date(System.currentTimeMillis() + deltaMs))
}
