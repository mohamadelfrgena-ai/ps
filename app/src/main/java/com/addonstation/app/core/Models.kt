package com.addonstation.app.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

// ---------------------------------------------------------------------------
// Auth
// ---------------------------------------------------------------------------

@Serializable
data class AuthSession(
    @SerialName("access_token") val accessToken: String = "",
    @SerialName("refresh_token") val refreshToken: String = "",
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String = "bearer",
    val user: AuthUser? = null
)

@Serializable
data class AuthUser(
    val id: String = "",
    val email: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
    @SerialName("user_metadata") val metadata: JsonObject? = null,
    @SerialName("created_at") val createdAt: String? = null
)

// ---------------------------------------------------------------------------
// Profiles & social
// ---------------------------------------------------------------------------

@Serializable
data class Profile(
    val id: String = "",
    val username: String = "",
    val email: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("banner_url") val bannerUrl: String? = null,
    val bio: String? = null,
    val role: String = "member", // member | creator | moderator | admin
    val plan: String = "free", // free | epic | legendary
    @SerialName("plan_expires_at") val planExpiresAt: String? = null,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("equipped_item_id") val equippedItemId: String? = null,
    @SerialName("show_online_status") val showOnlineStatus: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
) {
    val isStaff: Boolean get() = isAdmin || role == "admin" || role == "moderator"
    val displayRank: String
        get() = when {
            isAdmin || role == "admin" -> "admin"
            role == "moderator" -> "moderator"
            role == "creator" -> "creator"
            plan == "legendary" -> "legendary"
            plan == "epic" -> "epic"
            else -> "free"
        }
}

@Serializable
data class UserFriend(
    val id: Long = 0,
    @SerialName("requester_id") val requesterId: String = "",
    @SerialName("receiver_id") val receiverId: String = "",
    val status: String = "pending", // pending | accepted
    @SerialName("created_at") val createdAt: String? = null
)

// ---------------------------------------------------------------------------
// Chat
// ---------------------------------------------------------------------------

@Serializable
data class ChatChannel(
    val id: Long = 0,
    val slug: String = "",
    val name: String? = null,
    val description: String? = null,
    val position: Int = 0,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatMessage(
    val id: Long = 0,
    @SerialName("channel_id") val channelId: Long? = null,
    @SerialName("user_id") val userId: String? = null,
    val content: String = "",
    @SerialName("sticker_url") val stickerUrl: String? = null,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("message_type") val messageType: String? = null, // text | sticker | image | media
    @SerialName("media_path") val mediaPath: String? = null,
    @SerialName("media_mime_type") val mediaMimeType: String? = null,
    @SerialName("media_duration") val mediaDuration: Int? = null,
    @SerialName("reply_to_id") val replyToId: Long? = null,
    @SerialName("thread_root_id") val threadRootId: Long? = null,
    val status: String = "published",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatReaction(
    val id: Long = 0,
    @SerialName("message_id") val messageId: Long = 0,
    @SerialName("user_id") val userId: String = "",
    val emoji: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatNotification(
    val id: Long = 0,
    @SerialName("recipient_id") val recipientId: String = "",
    @SerialName("actor_id") val actorId: String? = null,
    @SerialName("notification_type") val type: String = "",
    @SerialName("message_id") val messageId: Long? = null,
    @SerialName("notification_text") val text: String? = null,
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class ChatReport(
    val id: Long = 0,
    @SerialName("message_id") val messageId: Long = 0,
    @SerialName("reporter_id") val reporterId: String = "",
    val reason: String = "",
    val status: String = "open",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("chat_messages") val message: ChatMessage? = null
)

@Serializable
data class ChatMute(
    @SerialName("user_id") val userId: String = "",
    val reason: String? = null,
    @SerialName("expires_at") val expiresAt: String? = null,
    @SerialName("created_by") val createdBy: String? = null
)

@Serializable
data class ChatImage(
    val id: Long = 0,
    @SerialName("message_id") val messageId: Long = 0,
    @SerialName("uploader_id") val uploaderId: String = "",
    @SerialName("image_path") val imagePath: String = "",
    @SerialName("mime_type") val mimeType: String? = null,
    @SerialName("image_size") val imageSize: Long? = null
)

// ---------------------------------------------------------------------------
// Marketplace content
// ---------------------------------------------------------------------------

@Serializable
data class ContentItem(
    val id: Long = 0,
    val slug: String = "",
    @SerialName("content_type") val contentType: String = "addon", // addon | world
    val title: String = "",
    val description: String? = null,
    val category: String? = null,
    val version: String? = null,
    val plan: String = "free",
    @SerialName("cover_url") val coverUrl: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("download_count") val downloadCount: Long = 0,
    @SerialName("view_count") val viewCount: Long = 0,
    @SerialName("rating_average") val ratingAverage: Double? = null,
    @SerialName("rating_count") val ratingCount: Long? = null,
    @SerialName("is_published") val isPublished: Boolean = false,
    @SerialName("is_featured") val isFeatured: Boolean = false,
    @SerialName("is_exclusive") val isExclusive: Boolean = false,
    @SerialName("created_by") val createdBy: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null
)

@Serializable
data class ContentFavorite(
    @SerialName("user_id") val userId: String = "",
    @SerialName("content_id") val contentId: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("content_items") val content: ContentItem? = null
)

@Serializable
data class ContentDownload(
    @SerialName("user_id") val userId: String = "",
    @SerialName("content_id") val contentId: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("content_items") val content: ContentItem? = null
)

@Serializable
data class ContentReview(
    @SerialName("user_id") val userId: String = "",
    @SerialName("content_id") val contentId: Long = 0,
    val rating: Int = 5
)

// ---------------------------------------------------------------------------
// Subscriptions & payments
// ---------------------------------------------------------------------------

@Serializable
data class SubscriptionPlan(
    val id: String = "",
    val name: String? = null,
    @SerialName("price_lyd") val priceLyd: Double = 0.0,
    @SerialName("duration_days") val durationDays: Int = 30,
    val active: Boolean = true
)

@Serializable
data class PaymentMethodSetting(
    val id: Long = 0,
    val method: String = "",
    @SerialName("account_name") val accountName: String? = null,
    @SerialName("account_identifier") val accountIdentifier: String? = null,
    val instructions: String? = null,
    val active: Boolean = true
)

@Serializable
data class PaymentOrder(
    val id: Long = 0,
    @SerialName("user_id") val userId: String = "",
    @SerialName("plan_id") val planId: String = "",
    @SerialName("amount_lyd") val amountLyd: Double = 0.0,
    @SerialName("payment_method") val paymentMethod: String = "",
    @SerialName("reference_code") val referenceCode: String = "",
    @SerialName("receipt_url") val receiptUrl: String? = null,
    @SerialName("transaction_reference") val transactionReference: String? = null,
    val status: String = "pending", // pending | submitted | approved | rejected
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null
)

// ---------------------------------------------------------------------------
// AC wallet economy
// ---------------------------------------------------------------------------

@Serializable
data class AcWallet(
    @SerialName("user_id") val userId: String = "",
    val balance: Long = 0,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class AcTransaction(
    val id: Long = 0,
    @SerialName("user_id") val userId: String = "",
    val amount: Long = 0,
    val reason: String? = null,
    @SerialName("balance_after") val balanceAfter: Long? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AcTask(
    val id: Long = 0,
    val slug: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("reward_ac") val rewardAc: Long = 0,
    val frequency: String? = null,
    @SerialName("max_claims") val maxClaims: Int? = null,
    val active: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AcEvent(
    val id: Long = 0,
    val title: String = "",
    val description: String? = null,
    @SerialName("reward_ac") val rewardAc: Long = 0,
    @SerialName("starts_at") val startsAt: String? = null,
    @SerialName("ends_at") val endsAt: String? = null,
    val active: Boolean = true
)

@Serializable
data class AcAchievement(
    val id: Long = 0,
    val slug: String = "",
    val title: String = "",
    val description: String? = null,
    @SerialName("reward_ac") val rewardAc: Long = 0
)

// ---------------------------------------------------------------------------
// Creators
// ---------------------------------------------------------------------------

@Serializable
data class CreatorApplication(
    val id: Long = 0,
    @SerialName("user_id") val userId: String = "",
    @SerialName("creator_name") val creatorName: String = "",
    val country: String? = null,
    @SerialName("content_types") val contentTypes: String? = null,
    val bio: String? = null,
    val experience: String? = null,
    @SerialName("publish_plan") val publishPlan: String? = null,
    @SerialName("previous_work") val previousWork: String? = null,
    @SerialName("portfolio_links") val portfolioLinks: String? = null,
    val motivation: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null
)

@Serializable
data class CreatorApplicationMessage(
    val id: Long = 0,
    @SerialName("application_id") val applicationId: Long = 0,
    @SerialName("sender_id") val senderId: String = "",
    val message: String = "",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreatorSubmission(
    val id: Long = 0,
    @SerialName("creator_id") val creatorId: String = "",
    @SerialName("content_type") val contentType: String = "addon",
    val title: String = "",
    val description: String? = null,
    val category: String? = null,
    val version: String? = null,
    @SerialName("preview_url") val previewUrl: String? = null,
    @SerialName("file_path") val filePath: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    @SerialName("requested_plan") val requestedPlan: String? = null,
    val status: String = "pending",
    @SerialName("reviewed_at") val reviewedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val profiles: Profile? = null
)

@Serializable
data class CreatorTipOrder(
    val id: Long = 0,
    @SerialName("creator_id") val creatorId: String = "",
    @SerialName("supporter_id") val supporterId: String? = null,
    @SerialName("amount_lyd") val amountLyd: Double = 0.0,
    @SerialName("payment_method") val paymentMethod: String? = null,
    val message: String? = null,
    @SerialName("receipt_path") val receiptPath: String? = null,
    @SerialName("transaction_reference") val transactionReference: String? = null,
    val status: String = "pending",
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreatorWallet(
    @SerialName("creator_id") val creatorId: String = "",
    val balance: Double = 0.0,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class CreatorEarning(
    val id: Long = 0,
    @SerialName("creator_id") val creatorId: String = "",
    val amount: Double = 0.0,
    val source: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class CreatorFollow(
    @SerialName("follower_id") val followerId: String = "",
    @SerialName("creator_id") val creatorId: String = ""
)

@Serializable
data class FeaturedCreator(
    val id: Long = 0,
    @SerialName("creator_id") val creatorId: String = "",
    val position: Int = 0,
    val profiles: Profile? = null
)

// ---------------------------------------------------------------------------
// Site assets (avatars / banners / chat items served from the backend)
// ---------------------------------------------------------------------------

@Serializable
data class SiteAsset(
    val id: Long = 0,
    @SerialName("asset_type") val assetType: String = "", // avatar | banner | chat_item
    val name: String? = null,
    @SerialName("file_url") val fileUrl: String = "",
    @SerialName("required_plan") val requiredPlan: String = "free",
    val enabled: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)
