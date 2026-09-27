package com.addonstation.app.data

import org.json.JSONArray
import org.json.JSONObject

data class Session(
    val accessToken: String,
    val refreshToken: String,
    val userId: String,
    val email: String,
    val username: String
)

data class Profile(
    val id: String,
    val username: String,
    val avatarUrl: String,
    val bannerUrl: String,
    val bio: String,
    val plan: String,
    val role: String,
    val showOnline: Boolean,
    val equippedItemId: String,
    val userLevel: Int,
    val planExpiresAt: String,
    val isAdmin: Boolean
) {
    val isCreator: Boolean get() = role == "creator" || role == "admin"
    val rank: String get() = when {
        role == "admin" || isAdmin -> "TheKing"
        role == "moderator" -> "Manager"
        role == "creator" -> "Creator"
        plan == "epic" -> "Epic"
        plan == "legendary" -> "Legendary"
        else -> "Free"
    }
}

data class ContentItem(
    val id: String,
    val slug: String,
    val title: String,
    val description: String,
    val coverUrl: String,
    val plan: String,
    val contentType: String,
    val rating: Double,
    val downloads: Int,
    val views: Int,
    val filePath: String,
    val isFeatured: Boolean,
    val isExclusive: Boolean,
    val createdBy: String,
    val authorName: String,
    val category: String,
    val version: String
)

data class Creator(
    val id: String,
    val name: String,
    val username: String,
    val avatarUrl: String,
    val bannerUrl: String,
    val badge: String,
    val specialty: String,
    val bio: String,
    val followers: Int,
    val addons: Int,
    val worlds: Int,
    val downloads: Int,
    val featured: Boolean,
    val contentSlugs: List<String>
)

data class ChatChannel(
    val id: String,
    val slug: String,
    val name: String,
    val description: String,
    val position: Int
)

data class ChatMessage(
    val id: String,
    val channelId: String,
    val userId: String,
    val content: String,
    val stickerUrl: String,
    val createdAt: String,
    val username: String,
    val avatarUrl: String,
    val plan: String,
    val role: String,
    val replyToId: String
)

data class WalletData(
    val balance: Int,
    val transactions: List<JSONObject>,
    val tasks: List<JSONObject>,
    val events: List<JSONObject>
)

data class StudioData(
    val availableLyd: Double,
    val lifetimeLyd: Double,
    val tips: List<JSONObject>,
    val content: List<ContentItem>
)

class ApiException(val status: Int, message: String, val code: String? = null) : Exception(message)

fun JSONObject.s(key: String, fallback: String = ""): String {
    if (!has(key) || isNull(key)) return fallback
    return opt(key)?.toString() ?: fallback
}

fun JSONObject.n(key: String): Double {
    if (!has(key) || isNull(key)) return 0.0
    return when (val value = opt(key)) {
        is Number -> value.toDouble()
        is String -> value.toDoubleOrNull() ?: 0.0
        else -> 0.0
    }
}

fun JSONObject.b(key: String): Boolean = optBoolean(key, false)

fun JSONObject.toContent(): ContentItem {
    val author = optJSONObject("profiles")
    return ContentItem(
        id = s("id"),
        slug = s("slug"),
        title = s("title"),
        description = s("description"),
        coverUrl = s("cover_url").ifBlank { s("preview_url") },
        plan = s("plan", "free"),
        contentType = s("content_type", "addon"),
        rating = n("rating_average"),
        downloads = n("download_count").toInt(),
        views = n("view_count").toInt(),
        filePath = s("file_path"),
        isFeatured = b("is_featured"),
        isExclusive = b("is_exclusive"),
        createdBy = s("created_by"),
        authorName = author?.s("username").orEmpty(),
        category = s("category"),
        version = s("version")
    )
}

fun JSONObject.toProfile(): Profile = Profile(
    id = s("id"),
    username = s("username", "Player"),
    avatarUrl = s("avatar_url", "/imgs/profiles/default-profile.jpg"),
    bannerUrl = s("banner_url", "/imgs/profiles/default-free-banner.jpg"),
    bio = s("bio"),
    plan = s("plan", "free"),
    role = s("role", "user"),
    showOnline = if (has("show_online_status")) optBoolean("show_online_status", true) else true,
    equippedItemId = s("equipped_item_id"),
    userLevel = n("user_level").toInt().coerceAtLeast(1),
    planExpiresAt = s("plan_expires_at"),
    isAdmin = b("is_admin") || s("role") == "admin"
)

fun JSONObject.toCreator(content: List<ContentItem> = emptyList()): Creator {
    val username = s("username", s("name", "Creator"))
    val items = content.ifEmpty { emptyList() }
    return Creator(
        id = s("id"),
        name = s("name", username),
        username = username,
        avatarUrl = s("avatar_url", "/imgs/profiles/default-profile.jpg"),
        bannerUrl = s("banner_url", "/imgs/profiles/default-free-banner.jpg"),
        badge = s("badge", if (s("role") == "creator") "Creator" else "Featured Creator"),
        specialty = s("specialty", s("bio").take(42).ifBlank { "Minecraft Bedrock creator" }),
        bio = s("bio"),
        followers = n("followers").toInt(),
        addons = if (has("addons")) n("addons").toInt() else items.count { it.contentType == "addon" },
        worlds = if (has("worlds")) n("worlds").toInt() else items.count { it.contentType == "world" },
        downloads = if (has("downloads")) n("downloads").toInt() else items.sumOf { it.downloads },
        featured = b("featured") || b("is_featured"),
        contentSlugs = optJSONArray("contentSlugs")?.toStringList().orEmpty()
    )
}

fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) add(optString(i))
}

fun jsonArray(text: String): JSONArray = when {
    text.isBlank() -> JSONArray()
    text.trim().startsWith("[") -> JSONArray(text)
    text.trim().startsWith("{") -> JSONArray().put(JSONObject(text))
    else -> JSONArray()
}
