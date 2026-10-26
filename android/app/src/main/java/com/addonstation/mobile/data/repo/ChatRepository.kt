package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.BlockedUser
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatReport
import com.addonstation.mobile.data.model.ModeratorScope
import com.addonstation.mobile.data.model.MutedUser
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
        /** غرفة خدمات النقدية: معرّف ثابت لا يتغيّر. */
        const val CASH_SERVICES_ROOM_ID = "cash-services"
        const val CASH_SERVICES_ROOM_NAME = "خدمات النقدية"
        const val MAX_TEXT = 500
        const val MAX_IMAGE_BYTES = 4L * 1024 * 1024
        const val MAX_MEDIA_BYTES = 100L * 1024 * 1024
        /** Bump only when the community-rule wording changes; keep SQL my_chat_status aligned. */
        const val COMMUNITY_RULES_VERSION = "v1"
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

    /**
     * ذاكرة مؤقّتة خفيفة لقائمة الغرف — **سببها «اللاق» عند الرجوع**:
     * شاشة الغرف تُفتح من جديد بعد كل رجوع من غرفة، فكانت تُفرغ قائمتها
     * وتنتظر ثلاث رحلات شبكة (ضمان غرفة النقدية + الغرف + غير المقروء)
     * قبل أن تظهر ⇒ مؤقّت تحميل وتقطّع في كل رجوع. الآن:
     *   • القائمة المحفوظة تُبذر الشاشة فوراً (بلا مؤقّت ولا فراغ).
     *   • وتُحدَّث في الخلفية إن مضى على حفظها أكثر من 30 ثانية.
     */
    @Volatile private var channelsCache: List<ChatChannel>? = null
    @Volatile private var channelsCachedAt = 0L
    @Volatile private var cashRoomEnsured = false
    private val channelsTtlMs = 30_000L

    /** آخر قائمة غرف معروفة (بلا شبكة) — تبذر بها الشاشة فور فتحها. */
    fun cachedChannels(): List<ChatChannel> = channelsCache.orEmpty()

    /** إبطال الذاكرة بعد أي تغيير في الغرف (من لوحة الإدارة). */
    fun invalidateChannels() {
        channelsCache = null
        cashRoomEnsured = false
    }

    suspend fun channels(force: Boolean = false): List<ChatChannel> {
        val cached = channelsCache
        val fresh = cached != null && System.currentTimeMillis() - channelsCachedAt < channelsTtlMs
        if (!force && fresh) return cached!!
        val list = runCatching {
            supabase.decodeList<ChatChannel>(
                supabase.select("chat_channels", "select=*&order=sort_order.asc")
            )
        }.getOrElse { emptyList() }
        if (list.isNotEmpty()) {
            channelsCache = list
            channelsCachedAt = System.currentTimeMillis()
        }
        // شبكة فارغة أو خطأ ⇒ نُبقي المحفوظة (أفضل من قائمة تختفي)
        return list.ifEmpty { cached.orEmpty() }
    }

    /**
     * يضمن وجود غرفة «خدمات النقدية» وضبطها للقراءة فقط.
     *
     * تُستدعى من الأدمن فقط (الخادم يرفض `chat_admin_upsert_room` لغيره):
     * - إن لم تكن الغرفة موجودة تُنشأ مقفلة بصلاحية الأدمن وحده.
     * - إن موجودة بلا قفل/بصلاحية الأدمن تُضبط كذلك، لأن المطلوب أن يقرأ
     *   الجميع ولا يكتب إلا الأدمن.
     * (نفس النتيجة لو نُفِّذ سكربت supabase/17_cash_services_room.sql يدوياً.)
     */
    suspend fun ensureCashServicesRoom(isAdmin: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        // مرة واحدة في الجلسة: كانت تُستدعى مع كل فتحٍ لقائمة الغرف (ورحلة
        // شبكة إضافية قبل ظهورها). تُستدعى عند الإقلاع وبعد أي تغيير غرف.
        if (cashRoomEnsured) return@withContext Result.success(Unit)
        runCatching {
            // `isAdmin` لا يُستخدم كشرط: لو لم تُحمَّل قيمة الدور المحلية بعد
            // لَفُقدت الغرفة. الخادم هو الحَكَم: يرفض الطلب لغير الأدمن، فمحاولة
            // غير الأدمن تنتهي برفض نظيف داخل runCatching ولا أثر لها.
            val existing = channels().firstOrNull { it.id == CASH_SERVICES_ROOM_ID }
            val needsFix = existing == null ||
                !existing.isLocked ||
                existing.postPermission.lowercase() != "admin_only"
            if (!needsFix) {
                cashRoomEnsured = true
                return@runCatching
            }
            upsertRoom(
                id = CASH_SERVICES_ROOM_ID,
                name = existing?.name?.takeIf { it.isNotBlank() } ?: CASH_SERVICES_ROOM_NAME,
                icon = existing?.icon ?: "💵",
                category = existing?.category ?: "main",
                description = existing?.description
                    ?: "مقالات وخدمات نقدية — القراءة للجميع، والكتابة للإدارة فقط.",
                sortOrder = existing?.sortOrder
                    ?: ((channels().maxOfOrNull { it.sortOrder } ?: 0) + 1),
                isLocked = true,
                slowmodeSeconds = existing?.slowmodeSeconds ?: 0,
                postPermission = "admin_only",
            ).getOrThrow()
            invalidateChannels()   // غرفة جديدة/معدَّلة ⇒ تُجلب من جديد
            cashRoomEnsured = true
            Unit
        }
    }

    /** قواعد المجتمع من الخادم (قابلة للتعديل من لوحة الإدارة) مع نص احتياطي محلي. */
    suspend fun rules(lang: String): List<String> = runCatching {
        val rows = supabase.select("chat_rules", "select=rules&lang=eq.$lang")
        val row = (rows as? JsonArray)?.firstOrNull()?.asObject()
        val array = row?.get("rules") as? JsonArray
        array?.mapNotNull { it.asString() } ?: emptyList()
    }.getOrElse { emptyList() }

    suspend fun acceptRules(version: String = COMMUNITY_RULES_VERSION): Result<Unit> = withContext(Dispatchers.IO) {
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
                moderatesAll = obj["moderates_all"]?.asBool()
                    ?: (obj["role"]?.asString() == "admin"),
                moderatedChannels = (obj["moderated_channels"] as? JsonArray)
                    ?.mapNotNull { it.asString() } ?: emptyList(),
            )
        }.getOrNull()
        state.setChatStatus(status)
        status
    }

    /**
     * هل يملك هذا المستخدم حق **الإشراف** على هذه الغرفة؟
     *
     * • الأدمن: كل الغرف (بلا جدول ولا إعداد).
     * • المشرف: الغرف الممنوحة له فقط — أو كلها إن مُنح `all_rooms`.
     * • أي دور آخر: لا شيء.
     *
     * الخادم هو الحَكَم الأخير (chat_can_moderate_channel)؛ هذه نسخة الواجهة
     * لتُخفي الأزرار في غير مكانها، لا لتسمح بما يرفضه الخادم.
     */
    fun canModerateRoom(status: MyChatStatus?, channelId: String): Boolean {
        if (status == null || !status.signedIn || channelId.isBlank()) return false
        if (status.role == "admin" || status.moderatesAll) return true
        return status.role == "moderator" && status.moderatedChannels.contains(channelId)
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

    suspend fun pinned(channelId: String): List<ChatMessage> = withContext(Dispatchers.IO) { runCatching {
        supabase.decodeList<ChatMessage>(
            supabase.select(
                "chat_messages",
                "select=*&channel_id=eq.$channelId&is_pinned=eq.true&order=created_at.desc&limit=50",
            )
        )
    }.getOrElse { emptyList() } }

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
        // ⚠️ سبب «لازم أخرج وأرجع للغرفة عشان أتأكد إن رسالتي انبعتت»:
        // الدالة تُعيد الرقم في حقل **message_id** لا `id` — فكان الرقم يقرأ 0
        // دائماً، فتُمحى الرسالة المؤقتة (التي ظهرت فوراً) ويبقى صاحبها ينتظر
        // الاستطلاع/الاستقبال اللحظي دون أن يرى تأكيداً. الآن يُقرأ الحقل
        // الصحيح (والاسم القديم يبقى احتياطاً) فتُستبدل الرسالة في مكانها.
        val id = outcome.data["message_id"]?.asLong()
            ?: outcome.data["messageId"]?.asLong()
            ?: outcome.data["id"]?.asLong()
            ?: 0L
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

    suspend fun reactionsFor(messageIds: List<Long>): Map<Long, Map<String, Int>> = withContext(Dispatchers.IO) { runCatching {
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
    }.getOrElse { emptyMap() } }

    suspend fun myReactionsFor(messageIds: List<Long>): Set<Pair<Long, String>> = withContext(Dispatchers.IO) { runCatching {
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
    }.getOrElse { emptySet() } }

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

    /**
     * حذف **أي رسالة** مهما كان عمرها — للأدمن (كل الغرف) ولمشرف الغرفة.
     *
     * نافذة الثلاث دقائق تبقى على صاحب الرسالة وحده (chat_delete_my_message)؛
     * أمّا الإدارة والمشرفون فلا حدّ زمني عليهم (طلب المالك).
     */
    suspend fun deleteMessageAsStaff(messageId: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "chat_delete_message_staff",
                mapOf("p_message_id" to JsonPrimitive(messageId)),
            ).asOutcome().requireOk()
            Unit
        }
    }

    /**
     * نطاق إشراف مستخدم: كل الغرف أم غرف بعينها (تستعملها لوحة الإدارة).
     */
    suspend fun moderatorRooms(userId: String): ModeratorScope = withContext(Dispatchers.IO) {
        runCatching {
            val rows = supabase.select(
                "chat_moderator_rooms",
                "select=channel_id,all_rooms&user_id=eq.$userId",
            ) as? JsonArray
            val objects = rows?.mapNotNull { it.asObject() } ?: emptyList()
            ModeratorScope(
                allRooms = objects.any { it["all_rooms"]?.asBool() == true },
                channels = objects.mapNotNull { it["channel_id"]?.asString() },
            )
        }.getOrElse { ModeratorScope() }
    }

    /**
     * تعيين نطاق إشراف مشرف: **كل الغرف** أو غرف محدَّدة (يستبدل السابق).
     */
    suspend fun setModeratorRooms(
        userId: String,
        channels: List<String>,
        allRooms: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "chat_set_moderator_rooms",
                mapOf(
                    "p_user_id" to JsonPrimitive(userId),
                    "p_channel_ids" to JsonArray(channels.map { JsonPrimitive(it) }),
                    "p_all_rooms" to JsonPrimitive(allRooms),
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

    /**
     * يرفع **إطاراً مصغّراً** للفيديو في مخزن صور الشات.
     *
     * مساره مشتق من مسار الفيديو نفسه (`<مسار الفيديو>.thumb.jpg`) فلا يحتاج
     * عموداً جديداً في قاعدة البيانات: أي عميل يعرف مسار الفيديو يعرف مسار
     * صورته المصغّرة، وإن لم تكن موجودة يعرض البطاقة بلا صورة (لا ينكسر شيء).
     */
    suspend fun uploadVideoThumb(bytes: ByteArray, mediaPath: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (bytes.isEmpty() || bytes.size > MAX_IMAGE_BYTES) return@runCatching ""
                val path = videoThumbPath(mediaPath)
                supabase.upload("chat-images", path, bytes, "image/jpeg")
                path
            }
        }

    suspend fun imageUrl(path: String): String? =
        runCatching { supabase.signedUrl("chat-images", path, 300) }.getOrNull()

    /** مسار الصورة المصغّرة لفيديو (مشتق من مساره) — في مخزن صور الشات. */
    fun videoThumbPath(mediaPath: String?): String =
        "${mediaPath.orEmpty().trim()}.thumb.jpg"

    /** رابط موقّع للصورة المصغّرة للفيديو (null إن لم تُرفع أو فشل التوقيع). */
    suspend fun videoThumbUrl(mediaPath: String?): String? =
        if (mediaPath.isNullOrBlank()) null else imageUrl(videoThumbPath(mediaPath))

    suspend fun mediaUrl(path: String): String? =
        runCatching { supabase.signedUrl("chat-media", path, 300) }.getOrNull()

    suspend fun contentFileUrl(path: String): String? =
        runCatching { supabase.signedUrl("content-files", path, 60) }.getOrNull()

    // ------------------------------------------------------- الملصقات والأدوات

    suspend fun stickerPacks(): List<StickerPack> = runCatching {
        val packs = supabase.decodeList<StickerPack>(
            supabase.select("sticker_packs", "select=*&active=eq.true&order=sort_order.asc")
        )
        state.setStickerPacks(packs)
        packs
    }.getOrElse { emptyList() }

    suspend fun stickers(): List<SiteAsset> = runCatching {
        // الترتيب حسب القسم أولاً (فقرات الملصقات) مع ترميز احتياطي قبل تنفيذ
        // supabase/15_assets_v2.sql — فلا تختفي الملصقات إن لم يكن العمود موجوداً بعد.
        val ordered = runCatching {
            supabase.decodeList<SiteAsset>(
                supabase.select(
                    "site_assets",
                    "select=*&asset_type=eq.sticker&enabled=eq.true&order=collection_order.asc,sort_order.asc&limit=500",
                )
            )
        }.getOrElse { emptyList() }
        if (ordered.isNotEmpty()) {
            ordered
        } else {
            supabase.decodeList<SiteAsset>(
                supabase.select(
                    "site_assets",
                    "select=*&asset_type=eq.sticker&enabled=eq.true&order=sort_order.asc&limit=500",
                )
            )
        }
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

    /** Target of a user report, or the author of a message report. */
    suspend fun reportedUserId(report: ChatReport): String? {
        report.targetUserId?.takeIf(String::isNotBlank)?.let { return it }
        val messageId = report.messageId ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                supabase.decodeList<Map<String, kotlinx.serialization.json.JsonElement>>(
                    supabase.select("chat_messages", "select=user_id&id=eq.$messageId&limit=1")
                ).firstOrNull()?.get("user_id")?.asString()
            }.getOrNull()
        }
    }

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

    // --------------------------------------------------- الحظر بين المستخدمين
    // سياسة Google Play للمحتوى تشترط على كل تطبيق فيه شات **وسيلة حظر**،
    // وهذه الثلاث وظائف تقودها. الفلترة نفسها في القاعدة (سياسة messages_read
    // في 33_user_block.sql) فلا حاجة لتعديل أي استعلام قراءة في التطبيق.

    /** يحظر مستخدماً: لا يرى أيٌّ منكما رسائل الآخر، وتُحذف صداقتهما. */
    suspend fun blockUser(userId: String, reason: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                supabase.rpc(
                    "user_block",
                    mapOf(
                        "p_user_id" to JsonPrimitive(userId),
                        "p_reason" to reason?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    suspend fun unblockUser(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("user_unblock", mapOf("p_user_id" to JsonPrimitive(userId)))
                .asOutcome().requireOk()
            Unit
        }
    }

    /**
     * الإبلاغ عن **عضو** (لا رسالة) من ملفّه الشخصي — بند السلامة في وثيقة
     * شاشات الملف (37_profile_reports.sql). السبب واحد من أربعة: spam · insult ·
     * inappropriate · other، والخادم يمنع الإبلاغ عن النفس ويردّ `reported`
     * أو `already_reported` (مرة كل ٢٤ ساعة لنفس العضو).
     */
    suspend fun reportUser(userId: String, reason: String, note: String? = null): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val outcome = supabase.rpc(
                    "chat_report_user",
                    mapOf(
                        "p_user_id" to JsonPrimitive(userId),
                        "p_reason" to JsonPrimitive(reason),
                        "p_note" to note?.let { JsonPrimitive(it) },
                    ),
                ).asOutcome().requireOk()
                Unit
            }
        }

    /** معرّفات المحظورين عندي — تُقرأ عند فتح الشات فتُفلتَر الرسائل فوراً أيضًا في الواجهة. */
    suspend fun blockedIds(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.decodeList<BlockedUser>(
                supabase.select("user_blocks", "select=blocked_id&order=created_at.desc"),
            ).mapNotNull { it.blockedId }.filter { it.isNotBlank() }.toSet()
        }.getOrElse { emptySet() }
    }

    // ── كتم الأعضاء (38_user_mute.sql) — زرّ واحد: لا رسائل ولا إشعارات ──────

    /**
     * كتم عضو: تُخفى رسائله **ولا يصل منه أي إشعار** (لا جرس ولا دفع).
     * ميزة اشتراك Legendary — والخادم يردّ `legendary_required` لغيره، والشاشة
     * تفتح صفحة الاشتراك قبل أن يُنادى الخادم أصلاً.
     */
    suspend fun muteMember(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("user_mute", mapOf("p_user_id" to JsonPrimitive(userId)))
                .asOutcome().requireOk()
            Unit
        }
    }

    suspend fun unmuteMember(userId: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("user_unmute", mapOf("p_user_id" to JsonPrimitive(userId)))
                .asOutcome().requireOk()
            Unit
        }
    }

    /** من كتمتُهم — تُحفظ نسخة محلية (UserPrefs) فيُخفى كلامهم فوراً في الشات. */
    suspend fun mutedMemberIds(): Set<String> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.decodeList<MutedUser>(
                supabase.rpc("my_muted_users", emptyMap(), signedOnly = true) ?: JsonArray(emptyList()),
            ).mapNotNull { it.mutedId }.filter { it.isNotBlank() }.toSet()
        }.getOrElse { emptySet() }
    }

    /** هل كتمتُ هذا العضو؟ (بطاقة السلامة: «كتم» أم «إلغاء الكتم»). */
    suspend fun iMutedMember(userId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("i_muted_user", mapOf("p_user_id" to JsonPrimitive(userId))).asBool() ?: false
        }.getOrElse { false }
    }

    /** هل حظرتُ هذا العضو؟ (شاشة ملفه تُظهر «إلغاء الحظر» بدل «حظر»). */
    suspend fun iBlockedUser(userId: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("i_blocked_user", mapOf("p_user_id" to JsonPrimitive(userId))).asBool() ?: false
        }.getOrElse { false }
    }

    /**
     * هل بيني وبينه حظر **بأي اتجاه**؟ (is_blocked_pair من 33_user_block.sql)
     * الحظر متقابل في الشات، فإن حظرني أحدهم كان الأليق ألّا أرى أزرار
     * «مراسلة/صداقة» في ملفه — بل حالة واحدة معطَّلة كما تنصّ الوثيقة §4.
     */
    suspend fun blockedPair(otherUserId: String): Boolean = withContext(Dispatchers.IO) {
        val me = supabase.session()?.userId
        if (me.isNullOrBlank()) return@withContext false
        runCatching {
            supabase.rpc(
                "is_blocked_pair",
                mapOf("p_a" to JsonPrimitive(me), "p_b" to JsonPrimitive(otherUserId)),
            ).asBool() ?: false
        }.getOrElse { false }
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
            invalidateChannels()   // الغرف تغيّرت ⇒ لا نُقدّم المحفوظة القديمة
            Unit
        }
    }

    suspend fun deleteRoom(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc("chat_admin_delete_room", mapOf("p_id" to JsonPrimitive(id)))
                .asOutcome().requireOk()
            invalidateChannels()
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
