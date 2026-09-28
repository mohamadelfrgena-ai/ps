package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.ContentItem
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** نتيجة تنزيل: البايتات + اسم الملف + الرابط الموقّع الذي استُخدم. */
data class DownloadPayload(
    val bytes: ByteArray,
    val fileName: String,
    val signedUrl: String,
) {
    override fun equals(other: Any?): Boolean = other is DownloadPayload && other.fileName == fileName
    override fun hashCode(): Int = fileName.hashCode()
}

/** حالة وصول المستخدم إلى عنصر: مجاني/مشترك/يحتاج ترقية/يحتاج AC. */
sealed interface AccessState {
    data object Free : AccessState
    data object Unlocked : AccessState
    data class RequiresPlan(val plan: String) : AccessState
    data class RequiresLegendary(val acPrice: Int) : AccessState
    data class RequiresAc(val acPrice: Int, val balance: Int) : AccessState
    data object RequiresLogin : AccessState
}

/**
 * المتجر والمحتوى: التصفح، التفاصيل، التحميل، المفضلة، و"محتواي".
 * التحميل يمرّ بدالة record_content_download التي تتحقق من الخطة وحدود التحميل وتعيد رابطاً موقّعاً.
 */
class ContentRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

    private val uid: String? get() = supabase.session()?.userId

    // ------------------------------------------------------------- تصفّح المتجر

    suspend fun browse(
        contentType: String? = null,
        category: String? = null,
        plan: String? = null,
        query: String = "",
        sort: String = "newest",
        limit: Int = 60,
    ): List<ContentItem> {
        val filters = buildString {
            append("select=*&is_published=eq.true")
            contentType?.let { append("&content_type=eq.$it") }
            category?.let { append("&category=eq.$it") }
            plan?.let { append("&plan=eq.$it") }
            if (query.isNotBlank()) {
                val q = query.trim()
                append("&or=(title.ilike.*$q*,description.ilike.*$q*)")
            }
            append("&order=${orderFor(sort)}")
            append("&limit=$limit")
        }
        return supabase.decodeList(supabase.select("content_items", filters))
    }

    private fun orderFor(sort: String): String = when (sort) {
        "popular" -> "downloads_count.desc"
        "rating" -> "rating.desc"
        "views" -> "views_count.desc"
        "oldest" -> "created_at.asc"
        else -> "created_at.desc"
    }

    suspend fun featured(limit: Int = 8): List<ContentItem> = supabase.decodeList(
        supabase.select(
            "content_items",
            "select=*&is_published=eq.true&is_featured=eq.true&order=created_at.desc&limit=$limit",
        )
    )

    suspend fun latest(limit: Int = 6): List<ContentItem> = supabase.decodeList(
        supabase.select(
            "content_items",
            "select=*&is_published=eq.true&order=created_at.desc&limit=$limit",
        )
    )

    suspend fun recommended(limit: Int = 6): List<ContentItem> = supabase.decodeList(
        supabase.select(
            "content_items",
            "select=*&is_published=eq.true&order=rating.desc,downloads_count.desc&limit=$limit",
        )
    )

    suspend fun exclusives(limit: Int = 24): List<ContentItem> = supabase.decodeList(
        supabase.select(
            "content_items",
            "select=*&is_published=eq.true&is_exclusive=eq.true&order=created_at.desc&limit=$limit",
        )
    )

    suspend fun topDownloaded(limit: Int = 6): List<ContentItem> = supabase.decodeList(
        supabase.select(
            "content_items",
            "select=*&is_published=eq.true&order=downloads_count.desc&limit=$limit",
        )
    )

    suspend fun byId(id: String): ContentItem? = supabase.decodeOne(
        supabase.select("content_items", "select=*&id=eq.$id&limit=1")
    )

    suspend fun byIds(ids: List<String>): List<ContentItem> {
        if (ids.isEmpty()) return emptyList()
        val filter = ids.distinct().joinToString(",")
        return supabase.decodeList(supabase.select("content_items", "select=*&id=in.($filter)"))
    }

    suspend fun byCreator(userId: String, limit: Int = 24): List<ContentItem> = supabase.decodeList(
        supabase.select(
            "content_items",
            "select=*&created_by=eq.$userId&is_published=eq.true&order=created_at.desc&limit=$limit",
        )
    )

    suspend fun similar(item: ContentItem, limit: Int = 6): List<ContentItem> = supabase.decodeList(
        supabase.select(
            "content_items",
            "select=*&is_published=eq.true&id=neq.${item.id}&category=eq.${item.category ?: "other"}&order=downloads_count.desc&limit=$limit",
        )
    )

    /** عدد العناصر لكل تصنيف (لشرائح الفلترة في المتجر). */
    suspend fun categoryCounts(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        listOf("adventure", "survival", "pvp", "building", "skins", "other").forEach { category ->
            val list = supabase.decodeList<ContentItem>(
                supabase.select("content_items", "select=id&is_published=eq.true&category=eq.$category&limit=200")
            )
            if (list.isNotEmpty()) counts[category] = list.size
        }
        return counts
    }

    // ------------------------------------------------------------------ إحصاءات

    suspend fun recordView(itemId: String) {
        runCatching { supabase.rpc("record_content_view", params("item_id" to p(itemId))) }
    }

    // ----------------------------------------------------------------- المفضلة

    suspend fun toggleFavorite(itemId: String): Result<Boolean> = runCatching {
        val result = supabase.rpc("toggle_favorite", params("item_id" to p(itemId)), signedOnly = true)
        result?.jsonPrimitive?.booleanOrNull ?: false
    }

    suspend fun isFavorite(itemId: String): Boolean {
        val id = uid ?: return false
        val list = supabase.decodeList<ContentItem>(
            supabase.select("content_favorites", "select=item_id&user_id=eq.$id&item_id=eq.$itemId&limit=1")
        )
        return list.isNotEmpty()
    }

    /** معرّفات المفضلة فقط (أسرع للفحص الجماعي). */
    suspend fun favoriteIds(): Set<String> {
        val id = uid ?: return emptySet()
        val rows = supabase.select("content_favorites", "select=item_id&user_id=eq.$id&limit=500")
        return runCatching {
            supabase.json.parseToJsonElement(rows.toString())
                .let { it as? kotlinx.serialization.json.JsonArray }
                ?.mapNotNull { row -> (row as? kotlinx.serialization.json.JsonObject)?.str("item_id") }
                ?.toSet() ?: emptySet()
        }.getOrElse { emptySet() }
    }

    suspend fun favorites(): List<ContentItem> {
        val ids = favoriteIds().toList()
        return byIds(ids)
    }

    // ---------------------------------------------------------------- التحميلات

    suspend fun downloads(): List<ContentItem> {
        val id = uid ?: return emptyList()
        val rows = supabase.select(
            "content_downloads",
            "select=item_id,created_at&user_id=eq.$id&order=created_at.desc&limit=100",
        )
        val ids = runCatching {
            supabase.json.parseToJsonElement(rows.toString())
                .let { it as? kotlinx.serialization.json.JsonArray }
                ?.mapNotNull { row -> (row as? kotlinx.serialization.json.JsonObject)?.str("item_id") }
                ?.distinct() ?: emptyList()
        }.getOrElse { emptyList() }
        return byIds(ids)
    }

    /**
     * تنزيل ملف عنصر: تستدعي record_content_download → ترجع bucket/path → رابط موقّع 60 ثانية → البايتات.
     * كل شروط الخطة والحد اليومي تُفرض داخل الدالة على الخادم.
     */
    suspend fun download(item: ContentItem): Result<DownloadPayload> = runCatching {
        val result = supabase.rpc("record_content_download", params("item_id" to p(item.id)), signedOnly = true)
        val outcome = result.toOutcome()
        if (!outcome.ok) throw ApiError(ApiErrorKind.NO_PERMISSION, outcome.message)
        val bucket = outcome.data.str("bucket") ?: "content-files"
        val path = outcome.data.str("path") ?: item.filePath
            ?: throw ApiError(ApiErrorKind.FILE_NOT_UPLOADED)
        val signed = supabase.signedUrl(bucket, path, expiresInSeconds = 60)
        val bytes = supabase.downloadBytes(signed)
        DownloadPayload(
            bytes = bytes,
            fileName = path.substringAfterLast('/').ifBlank { "${item.slug ?: item.id}.zip" },
            signedUrl = signed,
        )
    }

    /** فتح قفل عنصر بالعملات AC (للمحتوى المدفوع بـ AC). */
    suspend fun unlockWithAc(itemId: String): Result<Int> = runCatching {
        val outcome = supabase.rpc("ac_unlock_content", params("item_id" to p(itemId)), signedOnly = true).toOutcome()
        if (!outcome.ok) throw ApiError(ApiErrorKind.NOT_ENOUGH_AC, outcome.message)
        outcome.data.num("charged") ?: 0
    }

    /** هل يملك المستخدم هذا المحتوى (اشتراك مطابق أو شراء AC سابق)؟ */
    suspend fun accessFor(item: ContentItem): AccessState {
        val profile = state.profile.value ?: return AccessState.RequiresLogin
        val rank = mapOf("free" to 1, "epic" to 2, "legendary" to 3)
        val mine = rank[profile.effectivePlan] ?: 1
        val need = rank[item.plan] ?: 1
        if (need <= mine) return if (item.plan == "free") AccessState.Free else AccessState.Unlocked
        if (item.plan == "legendary") return AccessState.RequiresLegendary(item.acPrice)
        return if (item.acPrice > 0) {
            AccessState.RequiresAc(item.acPrice, state.wallet.value?.balance ?: 0)
        } else {
            AccessState.RequiresPlan(item.plan)
        }
    }

    /** محتوى المستخدم (للمبدعين) بكل الحالات. */
    suspend fun myContent(): List<ContentItem> {
        val id = uid ?: return emptyList()
        return supabase.decodeList(
            supabase.select(
                "content_items",
                "select=*&created_by=eq.$id&order=created_at.desc&limit=100",
            )
        )
    }
}
