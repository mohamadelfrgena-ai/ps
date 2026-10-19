package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.CreatorApplication
import com.addonstation.mobile.data.model.CreatorCard
import com.addonstation.mobile.data.model.CreatorSubmission
import com.addonstation.mobile.data.model.CreatorTip
import com.addonstation.mobile.data.model.FeaturedCreator
import com.addonstation.mobile.data.model.Profile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

/**
 * المبدعون: القوائم، ملف المبدع، طلب الانضمام، استوديو المبدع (رفع المحتوى)،
 * وتِيبس الدعم (تحويل يدوي + إيصال).
 */
class CreatorRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

    private suspend fun selectProfiles(query: String): JsonElement =
        runCatching { supabase.select("visible_profiles", query) }
            .getOrElse { supabase.select("profiles", query) }

    suspend fun featuredIds(): List<FeaturedCreator> = runCatching {
        supabase.decodeList<FeaturedCreator>(
            supabase.select("featured_creators", "select=*&order=sort_order.asc&limit=100")
        )
    }.getOrElse { emptyList() }

    /** ذاكرة مؤقّتة: القائمة كانت تُجلب (مع عشرات الطلبات) عند كل تبديل تبويب. */
    private val listCache = TtlCache<List<CreatorCard>>(60_000L)

    /** آخر نسخة معروفة (بلا شبكة) لتبذر بها الشاشة فوراً. */
    fun cachedCreators(): List<CreatorCard> = listCache.peek().orEmpty()

    /** إبطال الذاكرة بعد رفع محتوى أو تعديل إحصاءات. */
    fun invalidate() = listCache.clear()

    /**
     * قائمة المبدعين مع إحصاءات محتواهم (تُبنى من profiles + content_items).
     *
     * **إصلاح جذري**: كانت تطلب محتوى كل مبدع على حدة ⇒ حتى ٦١ رحلة شبكة
     * (N+1) في كل مرة تُفتح فيها الشاشة. الآن **طلبان فقط**: المبدعون، ثم
     * كل المنشور دفعةً واحدة تُجمَّع محليّاً (وهو سبب «اللاق» في الرئيسية).
     */
    suspend fun creators(query: String = "", force: Boolean = false): List<CreatorCard> =
        withContext(Dispatchers.IO) {
            if (query.isBlank()) {
                val cached = listCache.fresh()
                if (!force && cached != null) return@withContext cached
            }
            val featured = featuredIds().associateBy { it.userId }
            val profiles = runCatching {
                val filter = if (query.isBlank()) "" else
                    "&or=(username.ilike.*${query.trim()}*,display_name.ilike.*${query.trim()}*,bio.ilike.*${query.trim()}*)"
                supabase.decodeList<Profile>(
                    selectProfiles("select=*&role=in.(creator,admin)&order=user_level.desc&limit=60$filter")
                ).map(state::redactAdminAssets)
            }.getOrElse { emptyList() }

            // إحصاءات كل المبدعين في **طلب واحد** بدل طلب لكل مبدع
            val stats = runCatching {
                val rows = supabase.select(
                    "content_items",
                    "select=created_by,content_type,downloads_count&is_published=eq.true&limit=1000",
                ).asArray().orEmpty()
                val map = HashMap<String, Triple<Int, Int, Int>>() // تنزيلات · عوالم · منشور
                for (row in rows) {
                    val obj = row.asObject() ?: continue
                    val by = obj["created_by"]?.asString() ?: continue
                    val downloads = obj["downloads_count"]?.asInt() ?: 0
                    val isWorld = obj["content_type"]?.asString() == "world"
                    val prev = map[by] ?: Triple(0, 0, 0)
                    map[by] = Triple(
                        prev.first + downloads,
                        prev.second + if (isWorld) 1 else 0,
                        prev.third + 1,
                    )
                }
                map
            }.getOrDefault(emptyMap())

            val cards = profiles.map { profile ->
                val (downloads, worlds, published) = stats[profile.id] ?: Triple(0, 0, 0)
                CreatorCard(
                    profile = profile,
                    downloads = downloads,
                    worlds = worlds,
                    published = published,
                    isFeatured = featured.containsKey(profile.id),
                )
            }.sortedWith(compareByDescending<CreatorCard> { it.isFeatured }.thenByDescending { it.downloads })

            if (query.isBlank() && cards.isNotEmpty()) listCache.set(cards)
            cards
        }

    suspend fun creatorStats(userId: String): CreatorCard? {
        val profile = runCatching {
            supabase.decodeOne<Profile>(selectProfiles("select=*&id=eq.$userId"))
                ?.let(state::redactAdminAssets)
        }.getOrNull() ?: return null
        val rows = (runCatching {
            supabase.select(
                "content_items",
                "select=content_type,downloads_count&created_by=eq.$userId&is_published=eq.true&limit=200",
            )
        }.getOrNull() as? kotlinx.serialization.json.JsonArray).orEmpty()
        return CreatorCard(
            profile = profile,
            downloads = rows.sumOf { it.asObject()?.get("downloads_count")?.asInt() ?: 0 },
            worlds = rows.count { it.asObject()?.get("content_type")?.asString() == "world" },
            published = rows.size,
        )
    }

    // ------------------------------------------------------- طلبات الانضمام

    suspend fun myApplication(): CreatorApplication? = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext null
        runCatching {
            supabase.decodeOne<CreatorApplication>(
                supabase.select(
                    "creator_applications",
                    "select=*&user_id=eq.$uid&order=created_at.desc&limit=1",
                )
            )
        }.getOrNull()
    }

    suspend fun submitApplication(
        country: String?,
        contentTypes: String?,
        about: String?,
        experience: String?,
        publishingPlan: String?,
        previousWorkUrl: String?,
        portfolioUrls: String?,
        walletLinks: String?,
        motivation: String?,
        bio: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "submit_creator_application",
                mapOf(
                    "p_country" to country?.let { JsonPrimitive(it) },
                    "p_content_types" to contentTypes?.let { JsonPrimitive(it) },
                    "p_about" to about?.let { JsonPrimitive(it) },
                    "p_experience" to experience?.let { JsonPrimitive(it) },
                    "p_publishing_plan" to publishingPlan?.let { JsonPrimitive(it) },
                    "p_previous_work_url" to previousWorkUrl?.let { JsonPrimitive(it) },
                    "p_portfolio_urls" to portfolioUrls?.let { JsonPrimitive(it) },
                    "p_wallet_links" to walletLinks?.let { JsonPrimitive(it) },
                    "p_motivation" to motivation?.let { JsonPrimitive(it) },
                    "p_bio" to bio?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    // ------------------------------------------------------- استوديو المبدع

    suspend fun mySubmissions(): List<CreatorSubmission> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        runCatching {
            supabase.decodeList<CreatorSubmission>(
                supabase.select(
                    "creator_submissions",
                    "select=*&creator_id=eq.$uid&order=created_at.desc&limit=60",
                )
            )
        }.getOrElse { emptyList() }
    }

    suspend fun submitContent(
        title: String,
        contentType: String,
        description: String?,
        category: String?,
        version: String?,
        plan: String,
        coverUrl: String?,
        filePath: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "creator_submit_content",
                mapOf(
                    "p_title" to JsonPrimitive(title),
                    "p_content_type" to JsonPrimitive(contentType),
                    "p_description" to description?.let { JsonPrimitive(it) },
                    "p_category" to category?.let { JsonPrimitive(it) },
                    "p_version" to version?.let { JsonPrimitive(it) },
                    "p_plan" to JsonPrimitive(plan),
                    "p_cover_url" to coverUrl?.let { JsonPrimitive(it) },
                    "p_file_path" to filePath?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun uploadCreatorFile(
        bytes: ByteArray,
        mime: String,
        fileName: String,
        kind: String,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = supabase.session()?.userId
                ?: throw com.addonstation.mobile.data.ApiError(com.addonstation.mobile.data.ApiErrorKind.NOT_SIGNED_IN)
            // غلاف المبدع → site-assets/items/<uid>/. ملف المحتوى النهائي يذهب
            // مباشرةً إلى content-files الخاص؛ عند الموافقة تشير content_items إلى
            // object موجود فعلياً، ولا تُنشر metadata مكسورة من creator-media.
            val isCover = kind == "cover"
            val bucket = if (isCover) "site-assets" else "content-files"
            val path =
                if (isCover) "items/$uid/${System.currentTimeMillis()}-$fileName"
                else "creators/$uid/${System.currentTimeMillis()}-$fileName"
            supabase.upload(bucket, path, bytes, mime)
            path
        }
    }

    // ------------------------------------------------------------ تِيبس الدعم

    suspend fun submitTip(
        creatorId: String,
        amount: Double,
        method: String,
        message: String?,
        receiptPath: String?,
        transactionReference: String?,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.rpc(
                "creator_submit_tip",
                mapOf(
                    "p_creator_id" to JsonPrimitive(creatorId),
                    "p_amount" to JsonPrimitive(amount),
                    "p_method" to JsonPrimitive(method),
                    "p_message" to message?.let { JsonPrimitive(it) },
                    "p_receipt_path" to receiptPath?.let { JsonPrimitive(it) },
                    "p_transaction_reference" to transactionReference?.let { JsonPrimitive(it) },
                ),
            ).asOutcome().requireOk()
            Unit
        }
    }

    suspend fun myTips(): List<CreatorTip> = withContext(Dispatchers.IO) {
        val uid = supabase.session()?.userId ?: return@withContext emptyList()
        runCatching {
            supabase.decodeList<CreatorTip>(
                supabase.select(
                    "creator_tips",
                    "select=*&or=(creator_id.eq.$uid,sender_id.eq.$uid)&order=created_at.desc&limit=60",
                )
            )
        }.getOrElse { emptyList() }
    }

    suspend fun uploadTipReceipt(bytes: ByteArray, mime: String, fileName: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val uid = supabase.session()?.userId
                    ?: throw com.addonstation.mobile.data.ApiError(com.addonstation.mobile.data.ApiErrorKind.NOT_SIGNED_IN)
                val path = "$uid/tips-${System.currentTimeMillis()}-$fileName"
                supabase.upload("payment-receipts", path, bytes, mime)
                path
            }
        }

    suspend fun tipReceiptUrl(path: String): String? =
        runCatching { supabase.signedUrl("payment-receipts", path, 60) }.getOrNull()
}
