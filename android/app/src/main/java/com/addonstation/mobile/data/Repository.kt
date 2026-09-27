package com.addonstation.mobile.data

import kotlinx.serialization.json.*
import java.net.URLEncoder

data class Profile(
    val id: String, val username: String, val avatarUrl: String?, val bannerUrl: String?,
    val bio: String?, val role: String?, val plan: String?, val createdAt: String?,
    val showOnline: Boolean, val equipped: String?,
) {
    companion object {
        fun from(o: JsonObject) = Profile(
            o.str("id") ?: "", o.str("username") ?: "user", o.str("avatar_url"), o.str("banner_url"),
            o.str("bio"), o.str("role"), o.str("plan"), o.str("created_at"),
            o.bool("show_online_status"), o.str("equipped_item_id"),
        )
    }
}

data class ContentItem(
    val id: String, val slug: String?, val title: String, val description: String?, val coverUrl: String?,
    val plan: String?, val downloads: Long, val views: Long, val type: String?,
) {
    companion object {
        fun from(o: JsonObject) = ContentItem(
            o.str("id") ?: "", o.str("slug"), o.str("title") ?: "", o.str("description"), o.str("cover_url"),
            o.str("plan"), o.num("download_count"), o.num("view_count"), o.str("content_type"),
        )
    }
}

data class ChatMessage(val id: String, val userId: String, val content: String, val sticker: String?, val createdAt: String)

fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")

/** Resolves relative asset paths (e.g. /imgs/profiles/x.jpg) used by the web build. */
fun assetUrl(path: String?): String? = when {
    path.isNullOrBlank() -> null
    path.startsWith("http") -> path
    path.startsWith("/") -> "file:///android_asset/www$path"
    else -> path
}

class Repository(val sb: Supabase) {

    suspend fun profile(id: String): Profile? = sb.selectOne("profiles", "select=*&id=eq.$id")?.let(Profile::from)

    suspend fun wallet(userId: String): Long? =
        sb.selectOne("ac_wallets", "select=balance&user_id=eq.$userId")?.num("balance")

    suspend fun featuredCreators(): List<Profile> {
        val rows = runCatching {
            sb.select(
                "featured_creators",
                "select=creator_id,position,profiles(id,username,avatar_url,banner_url,bio,role,created_at)&active=eq.true&order=position&limit=4"
            ).mapNotNull { it.jsonObject.obj("profiles")?.let(Profile::from) }
        }.getOrDefault(emptyList())
        if (rows.isNotEmpty()) return rows
        return runCatching {
            sb.select("profiles", "select=id,username,avatar_url,banner_url,bio,role,created_at&role=eq.creator&limit=4")
                .map { Profile.from(it.jsonObject) }
        }.getOrDefault(emptyList())
    }

    suspend fun creatorStats(id: String): Pair<Long, Long> = runCatching {
        val items = sb.select("content_items", "select=download_count&created_by=eq.$id&is_published=eq.true")
        items.size.toLong() to items.sumOf { it.jsonObject.num("download_count") }
    }.getOrDefault(0L to 0L)

    suspend fun content(type: String, order: String, search: String): List<ContentItem> {
        var q = "select=*&content_type=eq.$type&is_published=eq.true&order=$order.desc&limit=60"
        if (search.isNotBlank()) q += "&title=ilike.${enc("*$search*")}"
        return sb.select("content_items", q).map { ContentItem.from(it.jsonObject) }
    }

    suspend fun counts(userId: String): Triple<Long, Long, Long> = runCatching {
        val mine = sb.select("content_items", "select=id&created_by=eq.$userId").size.toLong()
        val fav = sb.select("content_favorites", "select=user_id&user_id=eq.$userId").size.toLong()
        val dl = sb.select("content_downloads", "select=user_id&user_id=eq.$userId").size.toLong()
        Triple(mine, fav, dl)
    }.getOrDefault(Triple(0L, 0L, 0L))

    suspend fun acceptedRules(userId: String): Boolean =
        runCatching { sb.selectOne("chat_rule_acceptances", "select=user_id&user_id=eq.$userId") != null }.getOrDefault(false)

    suspend fun acceptRules(userId: String) =
        sb.insert("chat_rule_acceptances", buildJsonObject { put("user_id", userId) }, upsert = true)

    suspend fun generalChannelId(): String? = sb.selectOne("chat_channels", "select=id&slug=eq.general")?.str("id")

    suspend fun messages(channelId: String): List<ChatMessage> =
        sb.select("chat_messages", "select=id,user_id,content,sticker_url,created_at&channel_id=eq.$channelId&status=eq.published&order=created_at.desc&limit=60")
            .map {
                val o = it.jsonObject
                ChatMessage(o.str("id")!!, o.str("user_id") ?: "", o.str("content") ?: "", o.str("sticker_url"), o.str("created_at") ?: "")
            }.reversed()

    suspend fun profilesByIds(ids: Collection<String>): Map<String, Profile> {
        if (ids.isEmpty()) return emptyMap()
        return sb.select("profiles", "select=id,username,avatar_url,role,plan,equipped_item_id&id=in.(${ids.joinToString(",")})")
            .map { Profile.from(it.jsonObject) }.associateBy { it.id }
    }

    suspend fun sendMessage(channelId: String, text: String) {
        sb.rpc("send_chat_message", buildJsonObject {
            put("p_channel_id", channelId); put("p_content", text)
            put("p_sticker_url", JsonNull); put("p_reply_to_id", JsonNull); put("p_thread_root_id", JsonNull)
        })
    }

    suspend fun updateUsername(userId: String, name: String) =
        sb.update("profiles", "id=eq.$userId", buildJsonObject { put("username", name) })

    suspend fun deleteAccount() { sb.rpc("delete_my_account"); sb.signOut() }
}
