package com.addonstation.mobile.data

/** مخزن ومسار محجوزان للأصول التي لا يجوز أن يراها إلا حساب بدور admin. */
const val ADMIN_ASSETS_BUCKET = "admin-assets"
const val ADMIN_PRIVATE_PREFIX = "admin-private/"

fun isPrivateAdminAssetPath(value: String?): Boolean =
    value?.trim()?.trimStart('/')?.startsWith(ADMIN_PRIVATE_PREFIX, ignoreCase = true) == true

fun isKnownLegacyAdminAssetValue(value: String?): Boolean =
    isKnownLegacyAdminAsset("avatar", value) ||
        isKnownLegacyAdminAsset("banner", value) ||
        isKnownLegacyAdminAsset("item", value)

/**
 * شبكة أمان للدفعة القديمة المعروفة قبل نقلها إلى [ADMIN_ASSETS_BUCKET].
 * المصدر الحاسم بعد نشر SQL هو required_plan + visible_profiles؛ هذه المطابقة تمنع
 * النسخ القديمة من التطبيق/القاعدة من إظهار الأصل أثناء فترة الانتقال.
 */
fun isKnownLegacyAdminAsset(kind: String, value: String?): Boolean {
    val clean = value?.trim()?.trimStart('/')?.lowercase().orEmpty()
    if (clean.isEmpty()) return false
    if (isPrivateAdminAssetPath(clean)) return true
    return when (kind.lowercase()) {
        "avatar" -> clean == "profiles/pfp/download-1.jpg" || clean.contains("pfp4")
        "banner" -> clean.startsWith("banners/admin/") ||
            clean.contains("downloa1d.gif") || clean.contains("animation-reference.gif") ||
            clean.contains("animation reference.gif")
        "item" -> clean == "enchanted bedrock" || clean.contains("enchanted-bedrock") ||
            clean.contains("enchanted_bedrock") || clean.contains("enchanted%20bedrock")
        else -> false
    }
}
