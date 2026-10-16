package com.addonstation.mobile.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** الجلسة المحلية (تُخزَّن مشفّرة في SessionStore). */
data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String?,
    val expiresAtEpochSeconds: Long,
)

/** جهاز PlayStation مرتبط بحساب المستخدم؛ صفوفه تُقرأ وتُحذف عبر RLS. */
@Serializable
data class LinkedConsole(
    val id: String,
    @SerialName("device_name") val deviceName: String? = null,
    val platform: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
)

data class AuthUser(
    val id: String,
    val email: String,
    val username: String?,
    val emailConfirmed: Boolean,
)

@Serializable
data class Profile(
    val id: String,
    val username: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    @SerialName("custom_status") val customStatus: String? = null,
    val bio: String? = null,
    val country: String? = null,
    val role: String = "user",
    val plan: String = "free",
    @SerialName("plan_expires_at") val planExpiresAt: String? = null,
    @SerialName("user_level") val userLevel: Int = 1,
    @SerialName("equipped_item_id") val equippedItemId: String = "none",
    @SerialName("show_online_status") val showOnlineStatus: Boolean = false,
    @SerialName("last_seen_at") val lastSeenAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
) {
    val isAdmin: Boolean get() = role == "admin"
    val isStaff: Boolean get() = role == "admin" || role == "moderator"
    val isCreator: Boolean get() = role == "creator"

    /** الخطة الفعلية: الخطة المنتهية تُحسب free (مطابقة لقاعدة الخادم effective_plan). */
    val effectivePlan: String
        get() {
            if (plan == "free") return "free"
            val expiry = parseIsoMillis(planExpiresAt)
            return if (expiry != null && expiry < System.currentTimeMillis()) "free" else plan
        }

    val planExpired: Boolean get() = plan != "free" && effectivePlan == "free"
}

fun parseIsoMillis(value: String?): Long? {
    if (value.isNullOrBlank()) return null
    return try {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            java.time.Instant.parse(value).toEpochMilli()
        } else {
            // بديل بسيط للأجهزة القديمة: yyyy-MM-ddTHH:mm:ss
            val cleaned = value.substringBefore('.').removeSuffix("Z").replace('T', ' ')
            val fmt = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US)
            fmt.parse(cleaned)?.time
        }
    } catch (t: Throwable) {
        null
    }
}

@Serializable
data class SubscriptionPlan(
    val id: String,
    val name: String,
    val tagline: String? = null,
    @SerialName("price_lyd") val priceLyd: Double = 0.0,
    @SerialName("price_usd") val priceUsd: Double = 0.0,
    @SerialName("duration_days") val durationDays: Int = 30,
    @SerialName("download_limit") val downloadLimit: Int? = null,
    @SerialName("limit_period") val limitPeriod: String? = "day",
    val active: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
) {
    val isFree: Boolean get() = id == "free"
}

@Serializable
data class PaymentMethodSetting(
    val code: String,
    val name: String,
    @SerialName("account_name") val accountName: String? = null,
    @SerialName("account_identifier") val accountIdentifier: String? = null,
    val instructions: String? = null,
    val provider: String = "manual",
    val enabled: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class PaymentOrder(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("plan_id") val planId: String,
    @SerialName("amount_lyd") val amountLyd: Double = 0.0,
    @SerialName("amount_usd") val amountUsd: Double = 0.0,
    val currency: String = "LYD",
    @SerialName("payment_method") val paymentMethod: String = "",
    @SerialName("reference_code") val referenceCode: String = "",
    @SerialName("transaction_reference") val transactionReference: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    val status: String = "pending",
    val provider: String = "manual",
    @SerialName("provider_payment_id") val providerPaymentId: String? = null,
    @SerialName("reject_reason") val rejectReason: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("reviewed_at") val reviewedAt: String? = null,
)

@Serializable
data class ContentItem(
    val id: String,
    val title: String,
    val slug: String? = null,
    val description: String? = null,
    @SerialName("content_type") val contentType: String = "addon",
    val category: String? = "other",
    val plan: String = "free",
    val version: String? = null,
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    @SerialName("ac_price") val acPrice: Int = 0,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("is_exclusive") val isExclusive: Boolean = false,
    @SerialName("downloads_count") val downloadsCount: Int = 0,
    @SerialName("views_count") val viewsCount: Int = 0,
    val rating: Double = 0.0,
    @SerialName("rating_count") val ratingCount: Int = 0,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AcWallet(
    @SerialName("user_id") val userId: String,
    val balance: Int = 0,
    @SerialName("total_earned") val totalEarned: Int = 0,
    @SerialName("total_spent") val totalSpent: Int = 0,
)

@Serializable
data class ProfileStats(
    val friends: Int = 0,
    val downloads: Int = 0,
    val favorites: Int = 0,
    val level: Int = 1,
    @SerialName("xp_in_level") val xpInLevel: Int = 0,
    @SerialName("xp_to_next") val xpToNext: Int = 100,
    val streak: Int = 0,
    @SerialName("daily_claimed") val dailyClaimed: Boolean = false,
    @SerialName("daily_reward") val dailyReward: Int = 0,
)

@Serializable
data class AcTransaction(
    val id: Long = 0,
    @SerialName("user_id") val userId: String = "",
    val amount: Int = 0,
    val kind: String = "",
    val reason: String? = null,
    @SerialName("task_id") val taskId: String? = null,
    @SerialName("item_id") val itemId: String? = null,
    @SerialName("balance_after") val balanceAfter: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class AcTask(
    val id: String,
    @SerialName("title_ar") val titleAr: String = "",
    @SerialName("title_en") val titleEn: String? = null,
    val description: String? = null,
    val reward: Int = 0,
    val frequency: String = "once",
    @SerialName("streak_days") val streakDaysRaw: List<Int> = emptyList(),
    @SerialName("max_claims") val maxClaims: Int? = null,
    val active: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class ChatChannel(
    val id: String,
    val name: String,
    val icon: String? = null,
    val category: String = "main",
    val description: String? = null,
    @SerialName("sort_order") val sortOrder: Int = 0,
    @SerialName("is_locked") val isLocked: Boolean = false,
    @SerialName("slowmode_seconds") val slowmodeSeconds: Int = 0,
    @SerialName("post_permission") val postPermission: String = "members",
)

@Serializable
data class ChatMessage(
    val id: Long,
    @SerialName("channel_id") val channelId: String,
    @SerialName("user_id") val userId: String,
    val content: String? = null,
    @SerialName("message_type") val messageType: String = "text",
    @SerialName("sticker_url") val stickerUrl: String? = null,
    @SerialName("image_path") val imagePath: String? = null,
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime_type") val mediaMimeType: String? = null,
    @SerialName("media_duration") val mediaDuration: Int? = null,
    @SerialName("media_size") val mediaSize: Long? = null,
    @SerialName("reply_to_id") val replyToId: Long? = null,
    @SerialName("thread_root_id") val threadRootId: Long? = null,
    val status: String = "published",
    @SerialName("is_announcement") val isAnnouncement: Boolean = false,
    @SerialName("is_pinned") val isPinned: Boolean = false,
    val mentions: List<String> = emptyList(),
    @SerialName("created_at") val createdAt: String? = null,
)

data class ChatMessageUi(
    val message: ChatMessage,
    val author: Profile?,
    val replyTo: ChatMessage?,
    val replyAuthorName: String?,
    val reactions: Map<String, Int> = emptyMap(),
    val myReactions: Set<String> = emptySet(),
    val isNew: Boolean = false,
    /** رسالة أُظهرت محلياً قبل رد الخادم (رقمها سالب) — تُعرض باهتة حتى التأكيد. */
    val pending: Boolean = false,
    /**
     * رفض الخادم إرسالها (أو انقطع الرد): تبقى **ظاهرة** موسومة بـ«لم تُرسل»
     * ومعها زر إعادة المحاولة — لا تختفي، فلا يضطر صاحبها إلى الخروج من
     * الغرفة والعودة ليعرف إن كانت وصلت.
     */
    val failed: Boolean = false,
    /**
     * معاينة محلية (ملف في ذاكرة التطبيق) للصورة أو لإطار الفيديو: تُعرض
     * فوراً من الجهاز قبل أن يوقّع الخادم رابطها — فلا تبقى الصورة بيضاء.
     */
    val localPreview: String? = null,
) {
    /**
     * مفتاح اليوم (بالتوقيت المحلي) — يُحسب **مرة واحدة** عند أول عرض ويُخزَّن.
     * تقارنه شاشة الشات نصّياً لرسم فاصل التاريخ، فلا يُحسب نصّ التاريخ مرّتين
     * لكل رسالة في كل إطار (سبب تقطيع أثناء التمرير — انظر [TimeFormat.dayLabel]).
     */
    val dayKey: String by lazy(LazyThreadSafetyMode.NONE) {
        com.addonstation.mobile.util.TimeFormat.localDayKey(message.createdAt)
    }
}

@Serializable
data class ChatReport(
    val id: Long,
    @SerialName("message_id") val messageId: Long? = null,
    @SerialName("reporter_id") val reporterId: String = "",
    @SerialName("target_user_id") val targetUserId: String? = null,
    val reason: String = "other",
    val note: String? = null,
    val status: String = "open",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class ChatNotification(
    val id: Long,
    @SerialName("recipient_id") val recipientId: String = "",
    @SerialName("actor_id") val actorId: String? = null,
    val type: String = "system",
    @SerialName("message_id") val messageId: Long? = null,
    @SerialName("channel_id") val channelId: String? = null,
    val body: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
)

/**
 * تفضيلات الإشعارات (صف واحد لكل مستخدم من `push_prefs`):
 * مفتاح رئيسي + خمسة أقسام + ساعات الهدوء + مدّة الغياب قبل التذكير.
 */
@Serializable
data class PushPrefs(
    val master: Boolean = true,
    val chat: Boolean = true,
    val content: Boolean = true,
    val social: Boolean = true,
    val account: Boolean = true,
    val reminders: Boolean = true,
    @SerialName("quiet_enabled") val quietEnabled: Boolean = true,
    @SerialName("quiet_from") val quietFrom: Int = 23,
    @SerialName("quiet_to") val quietTo: Int = 8,
    @SerialName("reengage_days") val reengageDays: Int = 7,
)

/** نتيجة «جرّب الإشعار الآن» (دالة push_test_me). */
@Serializable
data class PushTestResult(val ok: Boolean = false, val message: String? = null)

/** صف من جدول الحظر: من حظرتُه أنا (يُقرأ مرة لفلترة الرسائل فوراً في الواجهة). */
@Serializable
data class BlockedUser(
    @SerialName("blocked_id") val blockedId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class MutedUser(
    @SerialName("muted_id") val mutedId: String? = null,
)

@Serializable
data class FriendEntry(
    val id: Long,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("receiver_id") val receiverId: String,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreatorApplication(
    val id: Long,
    @SerialName("user_id") val userId: String = "",
    val country: String? = null,
    @SerialName("content_types") val contentTypes: String? = null,
    val about: String? = null,
    val experience: String? = null,
    @SerialName("publishing_plan") val publishingPlan: String? = null,
    @SerialName("previous_work_url") val previousWorkUrl: String? = null,
    @SerialName("portfolio_urls") val portfolioUrls: String? = null,
    @SerialName("wallet_links") val walletLinks: String? = null,
    val motivation: String? = null,
    val bio: String? = null,
    val status: String = "pending",
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreatorSubmission(
    val id: Long,
    @SerialName("creator_id") val creatorId: String = "",
    val title: String = "",
    @SerialName("content_type") val contentType: String = "addon",
    val description: String? = null,
    val category: String? = null,
    val version: String? = null,
    val plan: String = "free",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    val status: String = "pending",
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CreatorTip(
    val id: Long,
    @SerialName("creator_id") val creatorId: String = "",
    @SerialName("sender_id") val senderId: String = "",
    @SerialName("amount_lyd") val amount: Double = 0.0,
    @SerialName("payment_method") val paymentMethod: String = "",
    val message: String? = null,
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("transaction_reference") val transactionReference: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class FeaturedCreator(
    @SerialName("user_id") val userId: String,
    @SerialName("is_featured") val isFeatured: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class StickerPack(
    val id: String,
    val name: String,
    @SerialName("icon_url") val coverUrl: String? = null,
    @SerialName("active") val enabled: Boolean = true,
    @SerialName("sort_order") val sortOrder: Int = 0,
)

@Serializable
data class SiteAsset(
    val id: Long = 0,
    @SerialName("asset_type") val assetType: String = "avatar",
    val name: String = "",
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("required_plan") val requiredPlan: String = "free",
    val enabled: Boolean = true,
    @SerialName("sticker_pack") val packId: String? = null,
    @SerialName("animated") val animated: Boolean = false,
    @SerialName("sort_order") val sortOrder: Int = 0,
    /** القسم الذي ينتمي إليه الأصل (اسم المجلد الأصلي) — تُعرض الأصول في فقرات منفصلة. */
    val collection: String? = null,
    @SerialName("collection_order") val collectionOrder: Int = 0,
)

/** مبدع في قائمة المبدعين (يُبنى من profiles + إحصاءات). */
data class CreatorCard(
    val profile: Profile,
    val downloads: Int = 0,
    val worlds: Int = 0,
    val published: Int = 0,
    val isFeatured: Boolean = false,
)

/** حالة الشات الخاصة بالمستخدم الحالي (من my_chat_status). */
data class MyChatStatus(
    val signedIn: Boolean = false,
    val muted: Boolean = false,
    val mutedUntil: String? = null,
    val banned: Boolean = false,
    val acceptedRules: Boolean = false,
    val role: String = "user",
    val plan: String = "free",
    /**
     * نطاق الإشراف: `true` ⇒ يشرف على **كل الغرف** (الأدمن دائماً، أو مشرف
     * عُمِّم). `false` ⇒ يشرف على [moderatedChannels] فقط.
     */
    val moderatesAll: Boolean = false,
    /** الغرف التي يُسمح له بالإشراف عليها (حذف/إخفاء/تثبيت أي رسالة فيها). */
    val moderatedChannels: List<String> = emptyList(),
)

data class UnreadCount(val unread: Int = 0, val mentions: Int = 0)

/**
 * نطاق إشراف مشرف على غرف الشات (لوحة الإدارة).
 *
 * @param allRooms `true` ⇒ مشرف على كل الغرف (وكل غرفة تُضاف لاحقاً).
 * @param channels الغرف الممنوحة له حين لا يكون النطاق «كل الغرف».
 */
data class ModeratorScope(
    val allRooms: Boolean = false,
    val channels: List<String> = emptyList(),
)

data class AdminStats(
    val users: Int = 0,
    val creators: Int = 0,
    val published: Int = 0,
    val addons: Int = 0,
    val worlds: Int = 0,
    val downloads: Int = 0,
    val paidUsers: Int = 0,
    val openReports: Int = 0,
    val pendingOrders: Int = 0,
    val pendingCreatorApps: Int = 0,
    val acBalanceTotal: Int = 0,
)

/** نتيجة عامة لعمليات RPC التي تُرجع {ok, message,...}. */
data class RpcOutcome(
    val ok: Boolean,
    val message: String?,
    val data: JsonObject,
)
