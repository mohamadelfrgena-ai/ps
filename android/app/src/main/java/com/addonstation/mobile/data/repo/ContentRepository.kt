package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.parseIsoMillis
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

/** ملف مُنزَّل ومحفوظ داخل مجلد التطبيق (Documents/AddonStation على البطاقة الخارجية). */
data class DownloadedFile(
    val file: java.io.File,
    val fileName: String,
    val signedUrl: String,
)

/** One server download row paired with its current content metadata. */
data class ContentDownloadEntry(
    val item: ContentItem,
    val downloadedAt: String?,
)

/** حالة وصول المستخدم إلى عنصر: مجاني/مشترك/يحتاج ترقية/يحتاج AC. */
sealed interface AccessState {
    data object Free : AccessState
    data object Unlocked : AccessState
    data object RequiresEpic : AccessState
    data object RequiresLegendary : AccessState
    data object RequiresLogin : AccessState
    data class RequiresPlan(val plan: String) : AccessState
    data class RequiresAc(val acPrice: Int, val balance: Int) : AccessState
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

    // ------------------------------------------------------------------
    // ذاكرة مؤقّتة قصيرة العمر — **أصل «اللاق» عند تبديل التبويبات**:
    // الشاشة الرئيسية كانت تجلب (مميّز + أحدث + مقترح + مبدعين) مع كل رجوع
    // أو تبديل تبويب ⇒ أربع رحلات شبكة ومؤقّت تحميل في كل مرة. الآن تُبذر
    // الشاشة بالمحفوظ فوراً، ويجري التحديث في الخلفية عند انقضاء المدة.
    // ------------------------------------------------------------------
    private val featuredCache = TtlCache<List<ContentItem>>(60_000L)
    private val latestCache = TtlCache<List<ContentItem>>(60_000L)
    private val recommendedCache = TtlCache<List<ContentItem>>(60_000L)

    private val weeklyFreeMutex = Mutex()
    @Volatile private var weeklyFreeItemId: String? = null
    @Volatile private var weeklyFreeEndsAt: Long? = null
    @Volatile private var weeklyFreeCheckedAt: Long = 0L

    private fun activeWeeklyFreeId(now: Long = System.currentTimeMillis()): String? {
        val end = weeklyFreeEndsAt
        return weeklyFreeItemId?.takeIf { end == null || now < end }
    }

    private fun decorateWeeklyFree(items: List<ContentItem>, activeId: String?): List<ContentItem> = items.map { item ->
        when {
            item.id == activeId -> item.copy(
                plan = "free",
                originalPlan = item.originalPlan ?: item.plan,
                weeklyFree = true,
            )
            item.weeklyFree -> item.copy(
                plan = item.originalPlan ?: "epic",
                originalPlan = null,
                weeklyFree = false,
            )
            else -> item
        }
    }

    private suspend fun currentWeeklyFreeId(): String? {
        val now = System.currentTimeMillis()
        if (now - weeklyFreeCheckedAt < 60_000L) return activeWeeklyFreeId(now)

        return weeklyFreeMutex.withLock {
            val lockedNow = System.currentTimeMillis()
            if (lockedNow - weeklyFreeCheckedAt < 60_000L) {
                return@withLock activeWeeklyFreeId(lockedNow)
            }

            runCatching {
                val outcome = supabase.rpc("current_weekly_free_epic").asOutcome().requireOk()
                val active = outcome.data["active"].asBool() == true
                weeklyFreeItemId = outcome.data["item_id"].asString().takeIf { active }
                weeklyFreeEndsAt = (
                    outcome.data["ends_at_epoch_ms"].asLong()
                        ?: parseIsoMillis(outcome.data["ends_at"].asString())
                    ).takeIf { active }
            }
            weeklyFreeCheckedAt = lockedNow
            activeWeeklyFreeId(lockedNow)
        }
    }

    private suspend fun decorateWeeklyFree(items: List<ContentItem>): List<ContentItem> =
        decorateWeeklyFree(items, currentWeeklyFreeId())

    /** آخر نسخة معروفة (بلا شبكة) لتبذر بها الشاشة فوراً. */
    fun cachedFeatured(): List<ContentItem> = decorateWeeklyFree(featuredCache.peek().orEmpty(), activeWeeklyFreeId())

    fun cachedLatest(): List<ContentItem> = decorateWeeklyFree(latestCache.peek().orEmpty(), activeWeeklyFreeId())

    fun cachedRecommended(): List<ContentItem> = decorateWeeklyFree(recommendedCache.peek().orEmpty(), activeWeeklyFreeId())

    /** إبطال الذاكرة بعد إضافة محتوى أو تعديله. */
    fun invalidate() {
        featuredCache.clear()
        latestCache.clear()
        recommendedCache.clear()
    }


    // ------------------------------------------------------------- تصفّح المتجر

    suspend fun browse(
        contentType: String? = null,
        sort: String = "newest",
        query: String = "",
        limit: Int = 60,
        category: String? = null,
        plan: String? = null,
    ): List<ContentItem> {
        val weeklyId = currentWeeklyFreeId()
        val filters = buildString {
            append("select=*&is_published=eq.true")
            contentType?.let { append("&content_type=eq.$it") }
            category?.let { append("&category=eq.$it") }
            plan?.let {
                if (it == "free" && weeklyId != null) {
                    append("&or=(plan.eq.free,id.eq.$weeklyId)")
                } else {
                    append("&plan=eq.$it")
                }
            }
            if (query.isNotBlank()) {
                val q = query.trim()
                append("&or=(title.ilike.*$q*,description.ilike.*$q*)")
            }
            append("&order=${orderFor(sort)}")
            append("&limit=$limit")
        }
        val items = supabase.decodeList<ContentItem>(supabase.select("content_items", filters))
        return decorateWeeklyFree(items, weeklyId)
    }

    private fun orderFor(sort: String): String = when (sort) {
        "popular" -> "downloads_count.desc"
        "rating" -> "rating.desc"
        "views" -> "views_count.desc"
        "oldest" -> "created_at.asc"
        else -> "created_at.desc"
    }

    suspend fun featured(limit: Int = 8, force: Boolean = false): List<ContentItem> = decorateWeeklyFree(
        through(featuredCache, force) {
            supabase.decodeList(
                supabase.select(
                    "content_items",
                    "select=*&is_published=eq.true&is_featured=eq.true&order=created_at.desc&limit=$limit",
                )
            )
        }
    )

    suspend fun latest(limit: Int = 6, force: Boolean = false): List<ContentItem> = decorateWeeklyFree(
        through(latestCache, force) {
            supabase.decodeList(
                supabase.select(
                    "content_items",
                    "select=*&is_published=eq.true&order=created_at.desc&limit=$limit",
                )
            )
        }
    )

    suspend fun recommended(limit: Int = 6, force: Boolean = false): List<ContentItem> = decorateWeeklyFree(
        through(recommendedCache, force) {
            supabase.decodeList(
                supabase.select(
                    "content_items",
                    "select=*&is_published=eq.true&order=rating.desc,downloads_count.desc&limit=$limit",
                )
            )
        }
    )

    suspend fun exclusives(limit: Int = 24): List<ContentItem> = decorateWeeklyFree(
        supabase.decodeList(
            supabase.select(
                "content_items",
                "select=*&is_published=eq.true&is_exclusive=eq.true&order=created_at.desc&limit=$limit",
            )
        )
    )

    suspend fun topDownloaded(limit: Int = 6): List<ContentItem> = decorateWeeklyFree(
        supabase.decodeList(
            supabase.select(
                "content_items",
                "select=*&is_published=eq.true&order=downloads_count.desc&limit=$limit",
            )
        )
    )

    suspend fun byId(id: String): ContentItem? {
        val item = supabase.decodeOne<ContentItem>(
            supabase.select("content_items", "select=*&id=eq.$id&limit=1")
        ) ?: return null
        return decorateWeeklyFree(listOf(item)).first()
    }

    suspend fun byIds(ids: List<String>): List<ContentItem> {
        if (ids.isEmpty()) return emptyList()
        val filter = ids.distinct().joinToString(",")
        val items = supabase.decodeList<ContentItem>(supabase.select("content_items", "select=*&id=in.($filter)"))
        return decorateWeeklyFree(items)
    }

    suspend fun byCreator(userId: String, limit: Int = 24): List<ContentItem> = decorateWeeklyFree(
        supabase.decodeList(
            supabase.select(
                "content_items",
                "select=*&created_by=eq.$userId&is_published=eq.true&order=created_at.desc&limit=$limit",
            )
        )
    )

    suspend fun similar(item: ContentItem, limit: Int = 6): List<ContentItem> = decorateWeeklyFree(
        supabase.decodeList(
            supabase.select(
                "content_items",
                "select=*&is_published=eq.true&id=neq.${item.id}&category=eq.${item.category ?: "other"}&order=downloads_count.desc&limit=$limit",
            )
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
        runCatching { supabase.rpc("record_content_view", params("p_item_id" to p(itemId))) }
    }

    // ----------------------------------------------------------------- المفضلة

    suspend fun toggleFavorite(itemId: String): Result<Boolean> = runCatching {
        val result = supabase.rpc("toggle_favorite", params("p_item_id" to p(itemId)), signedOnly = true)
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

    suspend fun downloadEntries(): List<ContentDownloadEntry> {
        val id = uid ?: return emptyList()
        val rows = supabase.select(
            "content_downloads",
            "select=item_id,created_at&user_id=eq.$id&order=created_at.desc&limit=100",
        )
        val raw = (rows as? kotlinx.serialization.json.JsonArray)
            ?.mapNotNull { row ->
                val objectRow = row as? kotlinx.serialization.json.JsonObject ?: return@mapNotNull null
                val itemId = objectRow.str("item_id") ?: return@mapNotNull null
                itemId to objectRow.str("created_at")
            }
            .orEmpty()
        val latest = LinkedHashMap<String, String?>()
        raw.forEach { (itemId, createdAt) -> latest.putIfAbsent(itemId, createdAt) }
        val indexed = byIds(latest.keys.toList()).associateBy(ContentItem::id)
        return latest.mapNotNull { (itemId, createdAt) ->
            indexed[itemId]?.let { ContentDownloadEntry(it, createdAt) }
        }
    }

    suspend fun downloads(): List<ContentItem> = downloadEntries().map(ContentDownloadEntry::item)

    /** Number of actual download events in the current local calendar month. */
    suspend fun downloadsThisMonthCount(): Int {
        val id = uid ?: return 0
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.DAY_OF_MONTH, 1)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val start = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
            .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            .format(calendar.time)
        val rows = supabase.select(
            "content_downloads",
            "select=id&user_id=eq.$id&created_at=gte.$start&limit=1000",
        )
        return (rows as? kotlinx.serialization.json.JsonArray)?.size ?: 0
    }

    /**
     * تنزيل ملف عنصر: تستدعي record_content_download → ترجع bucket/path → رابط موقّع 60 ثانية.
     * يُنقل الملف streaming إلى ملف مؤقت ثم يُنقل إلى وجهته فقط بعد اكتماله؛
     * كل شروط الخطة والحد اليومي ووجود object فعلي تُفرض داخل الدالة على الخادم.
     */
    suspend fun download(context: android.content.Context, item: ContentItem): Result<DownloadedFile> = runCatching {
        val result = supabase.rpc("record_content_download", params("p_item_id" to p(item.id)), signedOnly = true)
        val outcome = result.toOutcome()
        if (!outcome.ok) throw com.addonstation.mobile.data.apiErrorFromMessage(outcome.message)
        val bucket = outcome.data.str("bucket") ?: "content-files"
        val path = outcome.data.str("path") ?: item.filePath
            ?: throw ApiError(ApiErrorKind.FILE_NOT_UPLOADED)
        val signed = supabase.signedUrl(bucket, path, expiresInSeconds = 60)
        val fileName = path.substringAfterLast('/').takeIf {
            it.isNotBlank() && it != "." && it != ".." && '\u0000' !in it
        } ?: "${item.slug ?: item.id}.mcaddon"
        val dir = java.io.File(context.getExternalFilesDir(null) ?: context.filesDir, "AddonStation")
        val file = java.io.File(dir, fileName)
        val expectedSize = outcome.data["size"].asLong()?.takeIf { it > 0L } ?: item.fileSize
        supabase.downloadToFile(signed, file, expectedSize = expectedSize)
        DownloadedFile(file = file, fileName = fileName, signedUrl = signed)
    }

    /** فتح قفل عنصر بالعملات AC (للمحتوى المدفوع بـ AC). */
    suspend fun unlockWithAc(itemId: String): Result<Int> = runCatching {
        val outcome = supabase.rpc("ac_unlock_content", params("p_item_id" to p(itemId)), signedOnly = true).toOutcome()
        if (!outcome.ok) throw com.addonstation.mobile.data.apiErrorFromMessage(outcome.message)
        outcome.data.num("charged") ?: 0
    }

    /**
     * هل يملك المستخدم هذا المحتوى؟ يُحسب من الخطة الحالية + كونه المالك أو أدمن.
     * القرار النهائي يبقى على الخادم داخل record_content_download.
     */
    fun accessState(item: ContentItem, plan: String, isOwner: Boolean, isAdmin: Boolean): AccessState {
        if (isAdmin || isOwner) return AccessState.Unlocked
        val mine = rankOf(plan)
        val need = rankOf(item.plan)
        if (need <= mine) return if (item.plan == "free") AccessState.Free else AccessState.Unlocked
        if (item.plan == "legendary") return AccessState.RequiresLegendary
        if (item.plan == "epic") return AccessState.RequiresEpic
        return AccessState.RequiresPlan(item.plan)
    }

    private fun rankOf(plan: String?): Int = when (plan) {
        "legendary" -> 3
        "epic" -> 2
        else -> 1
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
