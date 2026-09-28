package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatReport
import com.addonstation.mobile.data.model.MyChatStatus
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.data.model.StickerPack
import com.addonstation.mobile.data.model.UnreadCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.util.concurrent.TimeUnit

/**
 * الشات بأسلوب Discord: 13 غرفة، رسائل، تفاعلات، ردود، منشن، ملصقات،
 * كتم/حظر/بلاغات، إدارة غرف وإعلانات (للأدمن)، وسجل الإشراف.
 */
class ChatRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
    private val profiles: ProfileRepository,
) {

    companion object {
        const val MAX_TEXT = 500
        const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
        const val MAX_MEDIA_BYTES = 100L * 1024 * 1024
        val ALLOWED_IMAGE_MIME = setOf("image/jpeg", "image/jpg", "image/png", "image/webp")
        val EMOJIS = listOf(
            "😀", "😃", "😄", "😍", "😂", "🥰", "😎", "🤣", "😅", "😇", "😉", "🤔",
            "😴", "🤯", "🥳", "😢", "😭", "😡", "🤩", "👍", "👎", "👏", "🙏", "💪",
            "🔥", "❤️", "💔", "💎", "👑", "🍃", "⭐", "🎮", "🕹", "🧊", "🏆", "⚽",
            "🚀", "✨", "🎉", "💬", "✅", "❌", "😊", "😁", "🤝", "🧡", "💛", "💜",
        )
        const val PAGE_SIZE = 100
    }

    // ------------------------------------------------------------- الغرف والقواعد

    suspend fun channels(): List<ChatChannel> = runCatching {
        supabase.decodeList<ChatChannel>(
            supabase.select("chat_channels", "select=*&order=sort_order.asc")
        )
    }.getOrElse { emptyList() }

    /** قواعد المجتمع من الخادم (قابلة للتعديل من لوحة الإدارة) مع نص احتياطي محلي. */
    suspend fun rules(lang: String): List<String> = runCatching {
        val rows = supabase.select("chat_rules", "select=rules&lang=eq.$lang")
        val row = (rows as? JsonArray)?.firstOrNull()?.asObject()
        val array = row?.get("rules") as? JsonArray
        array?.mapNotNull { it.asString() } ?: emptyList()
    }.getOrElse { emptyList() }

    suspend fun acceptRules(version: String = "v1"): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("chat_accept_rules", mapOf("p_version" to JsonPrimitive(version)))
                .asOutcome().requireOk()
            refreshMyStatus()
            Unit
        }
    }

    suspend fun refreshMyStatus(): MyChatStatus? = withContext(Dispatchers.IO) {
        if (supabase.session() == null) {
            val guest = MyChatStatus(signedIn = false)
            state.setChatStatus(guest)
            return@withContext guest
        }
        val status = runCatching {
            val obj = supabase.rpc("my_chat_status") as? JsonObject ?: return@runCatching null
            MyChatStatus(
                signedIn = obj["signed_in"]?.asBool() ?: true,
                muted = obj["muted"]?.asBool() ?: false,
                mutedUntil = obj["muted_until"]?.asString(),
                banned = obj["banned"]?.asBool() ?: false,
                acceptedRules = obj["accepted_rules"]?.asBool() ?: false,
                role = obj["role"]?.asString() ?: "user",
                plan = obj["plan"]?.asString() ?: "free",
            )
        }.getOrNull()
        state.setChatStatus(status)
        status
    }

    suspend fun refreshUnread(): Map<String, UnreadCount> = withContext(Dispatchers.IO) {
        if (supabase.session() == null) {
            state.setUnread(emptyMap())
            return@withContext emptyMap()
        }
        val map = runCatching {
            val obj = supabase.rpc("chat_unread_counts") as? JsonObject ?: return@runCatching emptyMap()
            obj.mapValues { (_, value) ->
                val v = value.asObject() ?: return@mapValues UnreadCount()
                UnreadCount(v["unread"]?.asInt() ?: 0, v["mentions"]?.asInt() ?: 0)
            }
        }.getOrElse { emptyMap() }
        state.setUnread(map)
        map
    }

    // ---------------------------------------------------------------- الرسائل

    suspend fun messages(channelId: String, beforeId: Long? = null, limit: Int = PAGE_SIZE): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            val extra = if (beforeId != null) "&id=lt.$beforeId" else ""
            val rows = runCatching {
                supabase.decodeList<ChatMessage>(
                    supabase.select(
                        "chat_messages",
                        "select=*&channel_id=eq.$channelId&status=in.(published,hidden)" +
                            "&order=created_at.desc&limit=$limit$extra",
                    )
                )
            }.getOrElse { emptyList() }
            rows.reversed()
        }

    suspend fun messagesSince(channelId: String, afterId: Long, limit: Int = 50): List<ChatMessage> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.decodeList<ChatMessage>(
                    supabase.select(
                        "chat_messages",
                        "select=*&channel_id=eq.$channelId&id=gt.$afterId&order=created_at.asc&limit=$limit",
                    )
                )
            }.getOrElse { emptyList() }
        }

    suspend fun searchMessages(channelId: String, query: String): List<ChatMessage> = runCatching {
        supabase.decodeList<ChatMessage>(
            supabase.select(
                "chat_messages",
                "select=*&channel_id=eq.$channelId&status=eq.published&content=ilike.*$query*" +
                    "&order=created_at.desc&limit=50",
            )
        )
    }.getOrElse { emptyList() }

    suspend fun pinned(channelId: String): List<ChatMessage> = runCatching {
        supabase.decodeList<ChatMessage>(
            supabase.select(
                "chat_messages",
                "select=*&channel_id=eq.$channelId&is_pinned=eq.true&order=created_at.desc&limit=50",
            )
        )
    }.getOrElse { emptyList() }

    fun mentionsIn(content: String, members: List<Profile>): List<String> {
        if (!content.contains('@')) return emptyList()
        return members.filter { member ->
            val name = member.username ?: return@filter false
            content.contains("@$name", ignoreCase = true)
        }.map { it.id }
    }

    suspend fun send(
        channelId: String,
        content: String? = null,
        replyToId: Long? = null,
        threadRootId: Long? = null,
        stickerUrl: String? = null,
        imagePath: String? = null,
        messageType: String = "text",
        mediaPath: String? = null,
        mediaMime: String? = null,
        mediaDuration: Int? = null,
        mediaSize: Long? = null,
        mentionIds: List<String> = emptyList(),
        isAnnouncement: Boolean = false,
    ): ChatMessage = withContext(Dispatchers.IO) {
        if (content != null && content.length > MAX_TEXT) throw ApiError(ApiErrorKind.GENERIC, "too_long")
        val outcome = supabase.rpc(
            "send_chat_message",
            mapOf(
                "p_channel_id" to JsonPrimitive(channelId),
                "p_content" to content?.let { JsonPrimitive(it) },
                "p_reply_to_id" to replyToId?.let { JsonPrimitive(it) },
                "p_thread_root_id" to threadRootId?.let { JsonPrimitive(it) },
                "p_sticker_url" to stickerUrl?.let { JsonPrimitive(it) },
                "p_image_path" to imagePath?.let { JsonPrimitive(it) },
                "p_message_type" to JsonPrimitive(messageType),
                "p_media_path" to mediaPath?.let { JsonPrimitive(it) },
                "p_media_mime" to mediaMime?.let { JsonPrimitive(it) },
                "p_media_duration" to mediaDuration?.let { JsonPrimitive(it) },
                "p_media_size" to mediaSize?.let { JsonPrimitive(it) },
                "p_is_announcement" to JsonPrimitive(isAnnouncement),
            ),
        ).asOutcome().requireOk()
        val id = outcome.data["id"]?.asLong() ?: 0L
        ChatMessage(
            id = id,
            channelId = channelId,
            userId = supabase.session()?.userId.orEmpty(),
            content = content,
            messageType = messageType,
            stickerUrl = stickerUrl,
            imagePath = imagePath,
            mediaPath = mediaPath,
            mediaMimeType = mediaMime,
            mediaDuration = mediaDuration,
            mediaSize = mediaSize,
            replyToId = replyToId,
            threadRootId = threadRootId,
            isAnnouncement = isAnnouncement,
            mentions = mentionIds,
        )
    }

    /** فحص محلي سريع قبل الإرسال (نفس قواعد الخادم) — الرسالة النهائية من الخادم دوماً. */
    fun localContentError(content: String): ApiErrorKind? {
        val text = content.trim()
        if (text.isEmpty()) return ApiErrorKind.GENERIC
        if (text.length > MAX_TEXT) return ApiErrorKind.GENERIC
        val linkRegex = Regex("(https?://|www\\.|\\b\\w+\\.(com|net|org|io|ly|ru|cn)\\b)", RegexOption.IGNORE_CASE)
        if (linkRegex.containsMatchIn(text)) return ApiErrorKind.NO_PERMISSION
        val phoneRegex = Regex("(\\+?\\d[\\d\\s\\-]{7,}\\d)")
        if (phoneRegex.containsMatchIn(text)) return ApiErrorKind.NO_PERMISSION
        val emailRegex = Regex("[\\w.\\-]+@[\\w\\-]+\\.[a-zA-Z]{2,}")
        if (emailRegex.containsMatchIn(text)) return ApiErrorKind.NO_PERMISSION
        return null
    }

    suspend fun reactionsFor(messageIds: List<Long>): Map<Long, Map<String, Int>> = runCatching {
        if (messageIds.isEmpty()) return@runCatching emptyMap()
        val ids = messageIds.joinToString(",")
        val rows = supabase.decodeList<Map<String, kotlinx.serialization.json.JsonElement>>(
            supabase.select(
                "chat_reactions",
                "select=message_id,emoji,user_id&message_id=in.($ids)&limit=1000",
            )
        )
        rows.groupBy { it["message_id"]?.asLong() ?: 0L }
            .mapValues { (_, list) ->
                list.groupingBy { it["emoji"]?.asString().orEmpty() }.eachCount()
            }
    }.getOrElse { emptyMap() }

    suspend fun myReactionsFor(messageIds: List<Long>): Set<Pair<Long, String>> = runCatching {
        val uid = supabase.session()?.userId ?: return@runCatching emptySet()
        if (messageIds.isEmpty()) return@runCatching emptySet()
        val ids = messageIds.joinToString(",")
        val rows = supabase.decodeList<Map<String, kotlinx.serialization.json.JsonElement>>(
            supabase.select(
                "chat_reactions",
                "select=message_id,emoji&message_id=in.($ids)&user_id=eq.$uid&limit=1000",
            )
        )
        rows.mapNotNull { row ->
            val mid = row["message_id"]?.asLong() ?: return@mapNotNull null
            val emoji = row["emoji"]?.asString() ?: return@mapNotNull null
            mid to emoji
        }.toSet()
    }.getOrElse { emptySet() }

    suspend fun toggleReaction(messageId: Long, emoji: String): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "chat_toggle_reaction",
                mapOf("p_message_id" to JsonPrimitive(messageId), "p_emoji" to JsonPrimitive(emoji)),
            ).asOutcome().requireOk()
            true
        }
    }

    suspend fun deleteMyMessage(messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("chat_delete_my_message", mapOf("p_message_id" to JsonPrimitive(messageId)))
                .asOutcome().requireOk()
            Unit
        }
    }

    suspend fun moderateMessage(messageId: Long, hide: Boolean = true): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "chat_moderate_message",
                    mapOf(
                        "p_message_id" to JsonPrimitive(messageId),
                        "p_hide" to JsonPrimitive(hide),
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun togglePin(messageId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            val outcome = supabase.rpc("chat_toggle_pin", mapOf("p_message_id" to JsonPrimitive(messageId)))
                .asOutcome().requireOk()
            outcome.data["pinned"]?.asBool() ?: true
        }
    }

    suspend fun markRead(channelId: String, lastId: Long) = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "chat_mark_read",
                mapOf("p_channel_id" to JsonPrimitive(channelId), "p_last_id" to JsonPrimitive(lastId)),
            )
        }
        Unit
    }

    suspend fun setTyping(channelId: String) = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("chat_set_typing", mapOf("p_channel_id" to JsonPrimitive(channelId)))
        }
        Unit
    }

    suspend fun typingNames(channelId: String, myId: String?): List<String> = runCatching {
        val since = isoSecondsAgo(12)
        val rows = supabase.decodeList<Map<String, kotlinx.serialization.json.JsonElement>>(
            supabase.select(
                "chat_typing",
                "select=user_id&channel_id=eq.$channelId&updated_at=gt.$since&limit=20",
            )
        )
        val ids = rows.mapNotNull { it["user_id"]?.asString() }.filter { it != myId }
        if (ids.isEmpty()) return@runCatching emptyList()
        profiles.loadProfiles(ids).values.mapNotNull { it.username ?: it.displayName }
    }.getOrElse { emptyList() }

    private fun isoSecondsAgo(seconds: Long): String {
        val millis = System.currentTimeMillis() - TimeUnit.SECONDS.toMillis(seconds)
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.ofEpochMilli(millis).toString()
        } else {
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            fmt.timeZone = java.util.TimeZone.getTimeZone("UTC")
            fmt.format(java.util.Date(millis))
        }
    }

    // ------------------------------------------------------- الرفع (صور ووسائط)

    suspend fun uploadImage(bytes: ByteArray, mime: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (mime.lowercase() !in ALLOWED_IMAGE_MIME) throw ApiError(ApiErrorKind.INVALID_IMAGE_FORMAT)
                if (bytes.size > MAX_IMAGE_BYTES) throw ApiError(ApiErrorKind.IMAGE_TOO_LARGE)
                val uid = supabase.session()?.userId ?: throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
                val path = "$uid/${System.currentTimeMillis()}-$fileName"
                supabase.upload("chat-images", path, bytes, mime)
                path
            }
        }

    suspend fun uploadMedia(bytes: ByteArray, mime: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (bytes.size > MAX_MEDIA_BYTES) throw ApiError(ApiErrorKind.MEDIA_TOO_LARGE)
                val uid = supabase.session()?.userId ?: throw ApiError(ApiErrorKind.NOT_SIGNED_IN)
                val path = "$uid/${System.currentTimeMillis()}-$fileName"
                supabase.upload("chat-media", path, bytes, mime)
                path
            }
        }

    suspend fun imageUrl(path: String): String? =
        runCatching { supabase.signedUrl("chat-images", path, 300) }.getOrNull()

    suspend fun mediaUrl(path: String): String? =
        runCatching { supabase.signedUrl("chat-media", path, 300) }.getOrNull()

    suspend fun contentFileUrl(path: String): String? =
        runCatching { supabase.signedUrl("content-files", path, 60) }.getOrNull()

    // ------------------------------------------------------- الملصقات والأدوات

    suspend fun stickerPacks(): List<StickerPack> = runCatching {
        val packs = supabase.decodeList<StickerPack>(
            supabase.select("sticker_packs", "select=*&enabled=eq.true&order=sort_order.asc")
        )
        state.setStickerPacks(packs)
        packs
    }.getOrElse { emptyList() }

    suspend fun stickers(): List<SiteAsset> = runCatching {
        supabase.decodeList<SiteAsset>(
            supabase.select(
                "site_assets",
                "select=*&asset_type=eq.sticker&enabled=eq.true&order=id.asc&limit=500",
            )
        )
    }.getOrElse { emptyList() }

    // --------------------------------------------------------- الإشراف والبلاغات

    suspend fun report(messageId: Long, reason: String, note: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "chat_report_message",
                    mapOf(
                        "p_message_id" to JsonPrimitive(messageId),
                        "p_reason" to JsonPrimitive(reason),
                        "p_note" to note?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun openReports(): List<ChatReport> = runCatching {
        supabase.decodeList<ChatReport>(
            supabase.select("chat_reports", "select=*&status=eq.open&order=created_at.desc&limit=100")
        )
    }.getOrElse { emptyList() }

    suspend fun closeReport(reportId: Long, action: String = "close"): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "chat_close_report",
                    mapOf(
                        "p_report_id" to JsonPrimitive(reportId),
                        "p_action" to JsonPrimitive(action),
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun muteUser(userId: String, minutes: Int, reason: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "chat_mute_user",
                    mapOf(
                        "p_user_id" to JsonPrimitive(userId),
                        "p_minutes" to JsonPrimitive(minutes),
                        "p_reason" to reason?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun unmuteUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("chat_unmute_user", mapOf("p_user_id" to JsonPrimitive(userId)))
                .asOutcome().requireOk()
            Unit
        }
    }

    suspend fun banUser(userId: String, reason: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "chat_ban_user",
                mapOf(
                    "p_user_id" to JsonPrimitive(userId),
                    "p_reason" to reason?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun unbanUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("chat_unban_user", mapOf("p_user_id" to JsonPrimitive(userId)))
                .asOutcome().requireOk()
            Unit
        }
    }

    suspend fun mutedUsers(): List<Map<String, kotlinx.serialization.json.JsonElement>> = runCatching {
        supabase.decodeList<Map<String, kotlinx.serialization.json.JsonElement>>(
            supabase.select("chat_mutes", "select=*&active=eq.true&order=created_at.desc&limit=100")
        )
    }.getOrElse { emptyList() }

    // -------------------------------------------------------------- إدارة الغرف

    suspend fun upsertRoom(
        id: String,
        name: String,
        icon: String?,
        category: String,
        description: String?,
        sortOrder: Int,
        isLocked: Boolean,
        slowmodeSeconds: Int,
        postPermission: String,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "chat_admin_upsert_room",
                mapOf(
                    "p_id" to JsonPrimitive(id),
                    "p_name" to JsonPrimitive(name),
                    "p_icon" to icon?.let { JsonPrimitive(it) },
                    "p_category" to JsonPrimitive(category),
                    "p_description" to description?.let { JsonPrimitive(it) },
                    "p_sort_order" to JsonPrimitive(sortOrder),
                    "p_is_locked" to JsonPrimitive(isLocked),
                    "p_slowmode_seconds" to JsonPrimitive(slowmodeSeconds),
                    "p_post_permission" to JsonPrimitive(postPermission),
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun deleteRoom(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("chat_admin_delete_room", mapOf("p_id" to JsonPrimitive(id)))
                .asOutcome().requireOk()
            Unit
        }
    }

    suspend fun sendAnnouncement(channelId: String?, content: String, asMessage: Boolean = true): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "chat_send_announcement",
                    mapOf(
                        "p_channel_id" to channelId?.let { JsonPrimitive(it) },
                        "p_content" to JsonPrimitive(content),
                        "p_as_message" to JsonPrimitive(asMessage),
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun updateRules(lang: String, title: String?, rules: List<String>): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val array = JsonArray(rules.map { JsonPrimitive(it) })
                supabase.rpc(
                    "admin_update_chat_rules",
                    mapOf(
                        "p_lang" to JsonPrimitive(lang),
                        "p_rules" to array,
                        "p_title" to title?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }
}
