package com.addonstation.mobile.data.repo

import com.addonstation.mobile.data.AppState
import com.addonstation.mobile.data.SupabaseClient
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.data.model.StickerPack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * إدارة الأصول من لوحة الإدارة: أفاتارات، بانرات، ملصقات وحزمها، وأدوات الشات.
 * الملفات تُرفع إلى مخزن site-assets العام، والسجلات في جدول site_assets.
 */
class AssetsRepository(
    private val supabase: SupabaseClient,
    private val state: AppState,
) {

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
        state.setSiteAssets(assets)
        assets
    }.getOrElse { emptyList() }

    /**
     * ذاكرة مؤقتة للأصول حسب النوع: تبديل تبويب (بروفايل ↔ بانر ↔ أدوات) لا يعيد
     * تنزيل مئات الصفوف في كل مرة.
     */
    private val typeCache = mutableMapOf<String, List<SiteAsset>>()

    suspend fun byType(type: String): List<SiteAsset> {
        synchronized(typeCache) { typeCache[type] }?.let { if (it.isNotEmpty()) return it }
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
            if (list.isNotEmpty()) synchronized(typeCache) { typeCache[type] = list }
            list
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
            val path = "$folder/${System.currentTimeMillis()}-$fileName"
            supabase.upload("site-assets", path, bytes, mime)
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
            path
        }
    }

    suspend fun delete(id: Long): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.delete("site_assets", "id=eq.$id")
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
            Unit
        }
    }

    suspend fun setRequiredPlan(asset: SiteAsset, plan: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            supabase.update(
                "site_assets",
                "id=eq.${asset.id}",
                buildJsonObject { put("required_plan", plan) },
            )
            Unit
        }
    }
}
