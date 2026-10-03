package com.addonstation.mobile.data.repo

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.AcWallet
import com.addonstation.mobile.data.model.FriendEntry
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject

/**
 * الملفات الشخصية: القراءة، التعديل، المحفظة، الأصدقاء، والأصول المتاحة حسب الخطة.
 * المنطق الحسّاس (تغيير الخطة/الدور) موجود في دوال RPC محمية على الخادم.
 */
class ProfileRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

    private val uid: String? get() = supabase.session()?.userId

    // ------------------------------------------------------------------ قراءة

    suspend fun refreshProfile(): Profile? {
        val id = uid ?: return null
        val profile = loadProfile(id)
        state.setProfile(profile)
        return profile
    }

    suspend fun loadProfile(userId: String): Profile? = withContext(Dispatchers.IO) {
        supabase.decodeOne<Profile>(
            supabase.select("profiles", "select=*&id=eq.$userId&limit=1")
        )
    }

    suspend fun loadProfiles(ids: Collection<String>): Map<String, Profile> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val list = ids.distinct().chunked(60).flatMap { chunk ->
            val filter = chunk.joinToString(",")
            supabase.decodeList<Profile>(
                supabase.select("profiles", "select=*&id=in.($filter)")
            )
        }
        list.associateBy { it.id }
    }

    suspend fun searchUsers(query: String, limit: Int = 30): List<Profile> {
        val q = query.trim()
        val filter = if (q.isBlank()) "" else "&or=(username.ilike.*$q*,display_name.ilike.*$q*)"
        return supabase.decodeList(
            supabase.select("profiles", "select=*&order=created_at.desc&limit=$limit$filter")
        )
    }

    /** مبدعون منشور لهم محتوى (يُستخدم في شاشة المبدعين وقسم "أفضل المبدعين"). */
    suspend fun creators(query: String = "", limit: Int = 30): List<Profile> {
        val q = query.trim()
        val filter = if (q.isBlank()) "" else "&username=ilike.*$q*"
        return supabase.decodeList(
            supabase.select(
                "profiles",
                "select=*&role=eq.creator$filter&order=user_level.desc&limit=$limit",
            )
        )
    }

    suspend fun featuredCreatorIds(): List<String> = supabase.decodeList<com.addonstation.mobile.data.model.FeaturedCreator>(
        supabase.select("featured_creators", "select=*&order=sort_order.asc&limit=50")
    ).map { it.userId }

    suspend fun refreshWallet(): AcWallet? {
        val id = uid ?: return null
        val wallet = supabase.decodeOne<AcWallet>(
            supabase.select("ac_wallets", "select=*&user_id=eq.$id&limit=1")
        )
        state.setWallet(wallet)
        return wallet
    }

    // ------------------------------------------------------------------ تعديل

    /** تحديث الملف الشخصي (اسم المستخدم، النبذة، الحالة، البلد، ظهور الحالة). */
    suspend fun updateMyProfile(
        username: String,
        bio: String? = null,
        customStatus: String? = null,
        showOnline: Boolean? = null,
        country: String? = null,
    ): Result<Profile?> = runCatching {
        supabase.rpc(
            "update_my_profile",
            params(
                "username" to p(username.trim()),
                "bio" to p(bio),
                "custom_status" to p(customStatus),
                "show_online" to p(showOnline),
                "country" to p(country),
            ),
            signedOnly = true,
        )
        refreshProfile()
    }

    /**
     * تشغيل/إطفاء حالة الاتصال الخضراء (عمود show_online_status الموجود أصلاً في الجدول).
     * تغيير فوري بلا حفظ باقي الحقول.
     */
    suspend fun setShowOnline(value: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "update_my_profile",
                params("show_online" to p(value)),
                signedOnly = true,
            )
            refreshProfile()
            Unit
        }
    }

    /** اختيار أفاتار أو بانر من أصول الموقع (kind = avatar | banner). */
    suspend fun setProfileAsset(kind: String, value: String?): Result<Unit> = runCatching {
        supabase.rpc(
            "set_profile_asset",
            params("kind" to p(kind), "value" to p(value)),
            signedOnly = true,
        )
        refreshProfile()
    }

    suspend fun setEquippedItem(itemId: String?): Result<Unit> = runCatching {
        val id = uid ?: return@runCatching
        supabase.update(
            "profiles",
            "id=eq.$id",
            buildJsonObject { put("equipped_item_id", itemId?.let { JsonPrimitive(it) } ?: kotlinx.serialization.json.JsonNull) },
        )
        Unit
    }

    // ------------------------------------------------------------------ الأصدقاء

    suspend fun friends(): List<FriendEntry> {
        val id = uid ?: return emptyList()
        return supabase.decodeList(
            supabase.select(
                "user_friends",
                "select=*&status=eq.accepted&or=(requester_id.eq.$id,receiver_id.eq.$id)",
            )
        )
    }

    suspend fun incomingFriendRequests(): List<FriendEntry> {
        val id = uid ?: return emptyList()
        return supabase.decodeList(
            supabase.select("user_friends", "select=*&status=eq.pending&receiver_id=eq.$id")
        )
    }

    suspend fun outgoingFriendRequests(): List<FriendEntry> {
        val id = uid ?: return emptyList()
        return supabase.decodeList(
            supabase.select("user_friends", "select=*&status=eq.pending&requester_id=eq.$id")
        )
    }

    /** FriendEntry مقابل لمستخدم معيّن (أي حالة) لمعرفة زر الإضافة/القبول. */
    suspend fun friendshipState(otherUserId: String): FriendEntry? {
        val id = uid ?: return null
        return supabase.decodeOne<FriendEntry>(
            supabase.select(
                "user_friends",
                "select=*&or=(and(requester_id.eq.$id,receiver_id.eq.$otherUserId),and(requester_id.eq.$otherUserId,receiver_id.eq.$id))&limit=1",
            )
        )
    }

    suspend fun sendFriendRequest(userId: String): Result<Unit> = runCatching {
        supabase.rpc("send_friend_request", params("target_id" to p(userId)), signedOnly = true)
        Unit
    }

    suspend fun respondFriendRequest(requestId: Long, accept: Boolean): Result<Unit> = runCatching {
        supabase.rpc(
            "respond_friend_request",
            params("request_id" to p(requestId), "accept" to p(accept)),
            signedOnly = true,
        )
        Unit
    }

    suspend fun removeFriend(otherUserId: String): Result<Unit> = runCatching {
        val id = uid ?: return@runCatching
        supabase.delete(
            "user_friends",
            "or=(and(requester_id.eq.$id,receiver_id.eq.$otherUserId),and(requester_id.eq.$otherUserId,receiver_id.eq.$id))",
        )
        Unit
    }

    // --------------------------------------------------------------- الأصول

    /** كل الأصول المتاحة مقسومة حسب النوع (avatar/banner/item/sticker). */
    suspend fun loadSiteAssets(): List<SiteAsset> {
        val list = supabase.decodeList<SiteAsset>(
            supabase.select("site_assets", "select=*&enabled=eq.true&order=sort_order.asc")
        )
        state.setSiteAssets(list)
        return list
    }

    fun canUseAsset(requiredPlan: String, profile: Profile?): Boolean =
        planRank(requiredPlan) <= planRank(profile?.effectivePlan ?: "free")

    /** ترتيب الخطط للمقارنة: free < epic < legendary. */
    fun planRank(plan: String?): Int = when (plan) {
        "legendary" -> 3
        "epic" -> 2
        else -> 1
    }
}
