package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.ADMIN_ASSETS_BUCKET
import com.addonstation.mobile.data.ADMIN_PRIVATE_PREFIX
import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.isPrivateAdminAssetPath
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.util.shrinkImageBytes
import com.addonstation.mobile.data.model.StickerPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * إدارة الأصول من لوحة الإدارة: أفاتارات، بانرات، ملصقات وحزمها، وأدوات الشات.
 * الأصول العادية في site-assets العام، وأصول required_plan=admin في admin-assets الخاص.
 */
class AssetsRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

    /** يجهّز روابط قصيرة العمر للملفات الخاصة؛ سياسة Storage ترفضها لغير admin. */
    private suspend fun refreshPrivateUrls(assets: List<SiteAsset>, replace: Boolean = false) {
        val resolved = buildMap {
            assets.asSequence()
                .filter { isPrivateAdminAssetPath(it.fileUrl) }
                .forEach { asset ->
                    runCatching {
                        supabase.signedUrl(ADMIN_ASSETS_BUCKET, asset.fileUrl.trimStart('/'), 3_600)
                    }.getOrNull()?.let { put(asset.fileUrl.trimStart('/'), it) }
                }
        }
        state.setPrivateAssetUrls(resolved, replace = replace)
    }

    suspend fun all(): List<SiteAsset> = runCatching {
        val assets = runCatching {
            supabase.decodeList<SiteAsset>(
                supabase.select("site_assets", "select=*&order=collection_order.asc,sort_order.asc&limit=1000")
            )
        }.getOrElse {
            // ترميز احتياطي قبل تنفيذ supabase/15_assets_v2.sql (لا عمود collection_order بعد)
            supabase.decodeList<SiteAsset>(
                supabase.select("site_assets", "select=*&order=sort_order.asc&limit=1000")
            )
        }
        refreshPrivateUrls(assets, replace = true)
        state.setSiteAssets(assets)
        assets
    }.getOrElse { emptyList() }

    /**
     * ذاكرة مؤقتة للأصول حسب النوع: تبديل تبويب (بروفايل ↔ بانر ↔ أدوات) لا يعيد
     * تنزيل مئات الصفوف في كل مرة.
     */
    private data class CachedAssets(
        val loadedAtMillis: Long,
        val assets: List<SiteAsset>,
    )

    private val typeCache = mutableMapOf<String, CachedAssets>()
    private val typeCacheMaxAgeMillis = 5 * 60 * 1_000L

    /**
     * The visible rows are role-dependent at the RLS layer. Never reuse a guest
     * cache after an admin/creator signs in, and refresh signed admin URLs even
     * when the catalog rows themselves are still fresh.
     */
    suspend fun byType(type: String): List<SiteAsset> {
        val roleKey = state.profile.value?.role?.lowercase() ?: "guest"
        val cacheKey = "$roleKey:$type"
        val now = System.currentTimeMillis()
        synchronized(typeCache) { typeCache[cacheKey] }
            ?.takeIf { now - it.loadedAtMillis < typeCacheMaxAgeMillis }
            ?.let { cached ->
                refreshPrivateUrls(cached.assets)
                return state.visibleAssets(cached.assets)
            }
        // على خيط الشبكة: كان الفكّ يجري على خيط الواجهة فيجمّد الشاشة عند فتح القائمة
        return withContext(Dispatchers.IO) {
            val list = runCatching {
                val ordered = runCatching {
                    supabase.decodeList<SiteAsset>(
                        supabase.select(
                            "site_assets",
                            "select=*&asset_type=eq.$type&enabled=eq.true&order=collection_order.asc,sort_order.asc&limit=500",
                        )
                    )
                }.getOrElse { emptyList() }
                if (ordered.isNotEmpty()) {
                    ordered
                } else {
                    // ترميز احتياطي قبل تنفيذ supabase/15_assets_v2.sql (لا عمود collection_order بعد)
                    supabase.decodeList<SiteAsset>(
                        supabase.select(
                            "site_assets",
                            "select=*&asset_type=eq.$type&enabled=eq.true&order=sort_order.asc&limit=500",
                        )
                    )
                }
            }.getOrElse { emptyList() }
            refreshPrivateUrls(list)
            if (list.isNotEmpty()) synchronized(typeCache) {
                typeCache[cacheKey] = CachedAssets(System.currentTimeMillis(), list)
            }
            state.visibleAssets(list)
        }
    }

    /**
     * تصغير صور الأصول المخزّنة في مكانها — بلا تغيير المسارات ولا قاعدة البيانات.
     *
     * صور الأرشيف الأصلية ضخمة (حتى 5 ميغابايت للصورة) ⇒ شبكة الأصول تبدو ثقيلة
     * وتستغرق وقتاً طويلاً. هنا نُنزّل كل صورة، نصغّرها إلى مقاس صالح لخانات
     * الشبكة (512 بكسل)، ونعيد رفعها فوق الملف نفسه ⇒ ينزل حجم كل صورة إلى
     * عُشر حجمها تقريباً، والصور تظهر فوراً.
     * المتحرّكة (GIF) والمتجه (SVG) تُترك كما هي حفاظاً على حركتها.
     *
     * يعيد عدد الصور التي عولجت بنجاح.
     */
    suspend fun shrinkStoredAssets(maxSide: Int = 512): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val cfg = supabase.config()
            val list = all().filter { it.enabled }
            var done = 0
            for (asset in list) {
                val path = cfg.normalizeStoragePath(asset.fileUrl)
                val privateAdmin = isPrivateAdminAssetPath(path)
                val bucket = if (privateAdmin) ADMIN_ASSETS_BUCKET else "site-assets"
                val lower = path.lowercase()
                if (lower.endsWith(".svg")) continue
                val original = runCatching {
                    val sourceUrl = if (privateAdmin) {
                        supabase.signedUrl(bucket, path, 3_600)
                    } else {
                        cfg.publicUrl(bucket, path)
                    }
                    supabase.downloadBytes(sourceUrl)
                }.getOrNull() ?: continue
                // **المتحرّكة (GIF)**: لا نلمس الأصل (حركته مرغوبة)، بل نولّد لقطة
                // ثابتة بجانبه بنفس المسار + ‎.thumb.jpg‎ تستعملها شبكة الأصول،
                // فتكفي خانة العرض بلا تشغيل عشرات المتحرّكات معاً.
                if (lower.endsWith(".gif") || asset.animated) {
                    val frame = shrinkImageBytes(original, maxSide = 384, jpegQuality = 82, forceJpeg = true)
                    if (frame != null) {
                        runCatching {
                            supabase.upload(bucket, "$path.thumb.jpg", frame.bytes, "image/jpeg", upsert = true)
                        }.onSuccess { done++ }
                    }
                    continue
                }
                // ما هو صغير أصلاً: نعدّه ولا نلمسه
                if (original.size <= 120 * 1024) {
                    done++
                    continue
                }
                val shrunk = shrinkImageBytes(original, maxSide) ?: continue
                if (shrunk.bytes.size >= original.size) {
                    done++
                    continue
                }
                val ok = runCatching {
                    supabase.upload(bucket, path, shrunk.bytes, shrunk.mime, upsert = true)
                }.isSuccess
                if (ok) done++
            }
            done
        }
    }

    suspend fun packs(): List<StickerPack> = runCatching {
        val packs = supabase.decodeList<StickerPack>(
            supabase.select("sticker_packs", "select=*&order=sort_order.asc")
        )
        state.setStickerPacks(packs)
        packs
    }.getOrElse { emptyList() }

    /** رفع أصل جديد (صورة) إلى site-assets ثم إنشاء السجل. */
    suspend fun create(
        assetType: String,
        name: String,
        requiredPlan: String,
        bytes: ByteArray,
        mime: String,
        fileName: String,
        packId: String? = null,
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val folder = when (assetType) {
                "avatar" -> "profiles"
                "banner" -> "banners"
                "sticker" -> "stickers/${packId ?: "misc"}"
                "item" -> "items"
                else -> "misc"
            }
            val privateAdmin = requiredPlan.equals("admin", ignoreCase = true)
            val relativePath = "$folder/${System.currentTimeMillis()}-$fileName"
            val path = if (privateAdmin) "$ADMIN_PRIVATE_PREFIX$relativePath" else relativePath
            val bucket = if (privateAdmin) ADMIN_ASSETS_BUCKET else "site-assets"
            supabase.upload(bucket, path, bytes, mime)
            val body = buildJsonObject {
                put("asset_type", assetType)
                put("name", name)
                put("file_url", path)
                put("required_plan", requiredPlan)
                put("enabled", true)
                // اسم العمود في قاعدة البيانات هو sticker_pack (وليس pack_id)
                if (packId != null) put("sticker_pack", packId)
            }
            supabase.insert("site_assets", body)
            synchronized(typeCache) { typeCache.clear() }
            if (privateAdmin) {
                runCatching { supabase.signedUrl(bucket, path, 3_600) }.getOrNull()?.let {
                    state.setPrivateAssetUrls(mapOf(path to it), replace = false)
                }
            }
            path
        }
    }

    suspend fun delete(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.delete("site_assets", "id=eq.$id")
            synchronized(typeCache) { typeCache.clear() }
            Unit
        }
    }

    suspend fun createPack(id: String, name: String, coverPath: String?): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = buildJsonObject {
                    put("id", id)
                    put("name", name)
                    put("enabled", true)
                    if (coverPath != null) put("cover_url", coverPath)
                }
                supabase.insert("sticker_packs", body)
                Unit
            }
        }

    suspend fun deletePack(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.delete("sticker_packs", "id=eq.$id")
            Unit
        }
    }

    suspend fun toggle(asset: SiteAsset, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.update("site_assets", "id=eq.${asset.id}", buildJsonObject { put("enabled", enabled) })
            synchronized(typeCache) { typeCache.clear() }
            Unit
        }
    }

    suspend fun setRequiredPlan(asset: SiteAsset, plan: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // تبديل أصل عام إلى admin (أو العكس) يحتاج نقل بايتات بين المخزنين؛
            // لا نغيّر الوسم وحده حتى لا يبقى ملف خاص في رابط عام.
            val wantsAdmin = plan.equals("admin", ignoreCase = true)
            require(wantsAdmin == isPrivateAdminAssetPath(asset.fileUrl)) {
                "admin_asset_storage_mismatch"
            }
            supabase.update(
                "site_assets",
                "id=eq.${asset.id}",
                buildJsonObject { put("required_plan", plan) },
            )
            synchronized(typeCache) { typeCache.clear() }
            Unit
        }
    }
}
