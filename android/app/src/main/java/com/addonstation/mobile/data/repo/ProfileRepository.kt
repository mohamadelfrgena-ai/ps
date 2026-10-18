package com.addonstation.mobile.data.repo

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.AcWallet
import com.addonstation.mobile.data.model.FriendEntry
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.ProfileStats
import com.addonstation.mobile.data.model.SiteAsset
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * الملفات الشخصية: القراءة، التعديل، المحفظة، الأصدقاء، والأصول المتاحة حسب الخطة.
 * المنطق الحسّاس (تغيير الخطة/الدور) موجود في دوال RPC محمية على الخادم.
 */
class ProfileRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

    private val uid: String? get() = supabase.session()?.userId
    private val refreshMutex = Mutex()

    /**
     * 40_admin_asset_privacy يسحب القراءة المباشرة من profiles ويقدّم هذا العرض.
     * ملفات الأعضاء العامة تفضّل العرض الآمن، مع رجوع مؤقت للقاعدة القديمة إلى
     * أن يُنشر SQL؛ ثم ينقّح AppState القيم المعروفة دفاعياً أثناء فترة الانتقال.
     */
    private suspend fun selectProfiles(query: String): JsonElement = try {
        supabase.select("visible_profiles", query)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        supabase.select("profiles", query)
    }

    /**
     * ملف صاحب الجلسة مسار توافق مستقل: إصدارات الخادم الحالية تسمح بقراءته
     * من profiles، بينما 40_admin_asset_privacy ينقل القراءة إلى visible_profiles.
     * البدء بالمسار الحالي يعيد فتح «ملفي» فوراً ولا يجعل غياب العرض الجديد
     * يحبس الشاشة خلف Network error / Retry؛ وبعد نشر 40 يعمل الرجوع الآمن.
     * الاستعلام مقيّد دائماً بمعرّف صاحب الجلسة، فلا يوسّع قراءة ملفات الآخرين.
     */
    private suspend fun selectOwnProfile(query: String): JsonElement = try {
        supabase.select("profiles", query)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (baseError: Throwable) {
        try {
            supabase.select("visible_profiles", query)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (viewError: Throwable) {
            viewError.addSuppressed(baseError)
            throw viewError
        }
    }

    // ------------------------------------------------------------------ قراءة

    /** تحميل أولي آمن ومتزامن: لا يكرر الطلب إذا سبق أن جُلب ملف هذا الحساب. */
    suspend fun ensureProfileLoaded(): Profile? = loadMyProfile(force = false)

    /** تحديث قسري بعد تعديل الملف أو إعادة تحميل بيانات الحساب. */
    suspend fun refreshProfile(): Profile? = loadMyProfile(force = true)

    /**
     * جلب الملف الشخصي مع حالة تحميل صريحة. يمنع هذا السباق بين استعادة الجلسة
     * عند التشغيل وفتح التبويبات، ويترك الخطأ قابلاً لإعادة المحاولة بدلاً من
     * تفسيره على أنه تسجيل خروج.
     */
    private suspend fun loadMyProfile(force: Boolean): Profile? = refreshMutex.withLock {
        val id = uid
        if (id.isNullOrBlank()) {
            state.setProfileLoadFailed()
            return@withLock null
        }
        if (!force) {
            state.currentProfile?.takeIf { it.id == id }?.let { return@withLock it }
        }

        state.setProfileLoading()
        try {
            val profile = loadProfile(id)
            state.setProfile(profile)
            profile
        } catch (cancelled: CancellationException) {
            state.setProfileLoadFailed()
            throw cancelled
        } catch (error: Throwable) {
            state.setProfileLoadFailed()
            throw error
        }
    }

    suspend fun loadProfile(userId: String): Profile? = withContext(Dispatchers.IO) {
        val query = "select=*&id=eq.$userId&limit=1"
        val rows = if (userId == uid) selectOwnProfile(query) else selectProfiles(query)
        supabase.decodeOne<Profile>(rows)?.let(state::redactAdminAssets)
    }

    suspend fun loadProfiles(ids: Collection<String>): Map<String, Profile> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyMap()
        val list = ids.distinct().chunked(60).flatMap { chunk ->
            val filter = chunk.joinToString(",")
            supabase.decodeList<Profile>(
                selectProfiles("select=*&id=in.($filter)")
            ).map(state::redactAdminAssets)
        }
        list.associateBy { it.id }
    }

    /**
     * إحصاءات أي عضو — أرقام فقط: أصدقاء · تنزيلات · مفضّلة · المستوى ·
     * سلسلة الأيام · مكافأة اليوم.
     *
     * لماذا دالة خادم ولا استعلامات مباشرة؟ لأن المفضّلة والتنزيلات والمحفظة
     * محميّة بسياسات «لصاحبها فقط»، فملف عضو آخر لا يستطيع قراءتها. الدالة
     * `profile_stats` (36_profile_stats.sql) تعمل بـ security definer وتُرجع
     * **أعداداً لا بيانات**. وإن لم تكن مُنفَّذة على الخادم بعد (404) تُرجع
     * `null` فتُخفي الواجهة الأرقام بلا كسر الشاشة.
     *
     * قاعدة المستوى: كل 100 AC مكتسَبة = مستوى (والأعلى بينها وبين تعديل الإدارة).
     */
    suspend fun stats(userId: String? = null): ProfileStats? = withContext(Dispatchers.IO) {
        runCatching {
            val body = supabase.rpc("profile_stats", params("p_user_id" to p(userId)))
            body?.let { supabase.decodeOne<ProfileStats>(it) }
        }.getOrNull()
    }

    suspend fun searchUsers(query: String, limit: Int = 30): List<Profile> {
        val q = query.trim()
        val filter = if (q.isBlank()) "" else "&or=(username.ilike.*$q*,display_name.ilike.*$q*)"
        return supabase.decodeList<Profile>(
            selectProfiles("select=*&order=created_at.desc&limit=$limit$filter")
        ).map(state::redactAdminAssets)
    }

    /** مبدعون منشور لهم محتوى (يُستخدم في شاشة المبدعين وقسم "أفضل المبدعين"). */
    suspend fun creators(query: String = "", limit: Int = 30): List<Profile> {
        val q = query.trim()
        val filter = if (q.isBlank()) "" else "&username=ilike.*$q*"
        return supabase.decodeList<Profile>(
            selectProfiles("select=*&role=eq.creator$filter&order=user_level.desc&limit=$limit")
        ).map(state::redactAdminAssets)
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
        requireRpcSuccess(
            supabase.rpc(
                "update_my_profile",
                params(
                    "p_username" to p(username.trim()),
                    "p_bio" to p(bio),
                    "p_custom_status" to p(customStatus),
                    "p_show_online" to p(showOnline),
                    "p_country" to p(country),
                ),
                signedOnly = true,
            ),
        )
        refreshProfile()
    }

    /**
     * تشغيل/إطفاء حالة الاتصال الخضراء (عمود show_online_status الموجود أصلاً في الجدول).
     * تغيير فوري بلا حفظ باقي الحقول.
     */
    suspend fun setShowOnline(value: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            requireRpcSuccess(
                supabase.rpc(
                    "update_my_profile",
                    params("p_show_online" to p(value)),
                    signedOnly = true,
                ),
            )
            refreshProfile()
            Unit
        }
    }

    /** اختيار أفاتار أو غلاف أو أداة من أصول الموقع. */
    suspend fun setProfileAsset(kind: String, value: String?): Result<Unit> = runCatching {
        requireRpcSuccess(
            supabase.rpc(
                "set_profile_asset",
                params("p_kind" to p(kind), "p_value" to p(value)),
                signedOnly = true,
            ),
        )
        refreshProfile()
        Unit
    }

    suspend fun setEquippedItem(itemId: String?): Result<Unit> =
        setProfileAsset("item", itemId ?: "none")

    /** RPCs return HTTP 200 for business-rule failures, so turn `{ ok: false }` into a Result failure. */
    private fun requireRpcSuccess(response: JsonElement?) {
        val body = response as? JsonObject ?: return
        val ok = body["ok"]?.jsonPrimitive?.booleanOrNull ?: return
        if (!ok) {
            val message = body["message"]?.jsonPrimitive?.contentOrNull
            throw IllegalStateException(message ?: "profile_update_failed")
        }
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
        supabase.rpc("send_friend_request", params("p_user_id" to p(userId)), signedOnly = true)
        Unit
    }

    suspend fun respondFriendRequest(requestId: Long, accept: Boolean): Result<Unit> = runCatching {
        supabase.rpc(
            "respond_friend_request",
            params("p_request_id" to p(requestId), "p_accept" to p(accept)),
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
        return state.visibleAssets(list)
    }

    fun canUseAsset(requiredPlan: String, profile: Profile?): Boolean =
        if (requiredPlan.equals("admin", ignoreCase = true)) profile?.role == "admin"
        else planRank(requiredPlan) <= planRank(profile?.effectivePlan ?: "free")

    /** ترتيب الخطط للمقارنة: free < epic < legendary. */
    fun planRank(plan: String?): Int = when (plan) {
        "legendary" -> 3
        "epic" -> 2
        else -> 1
    }
}
