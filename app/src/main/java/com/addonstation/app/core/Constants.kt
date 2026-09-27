package com.addonstation.app.core

/** Default backend connection (same project the original app ships with). */
object Constants {
    const val DEFAULT_SUPABASE_URL = "https://hakxwvtdvsynrmkthatk.supabase.co"
    const val DEFAULT_SUPABASE_ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6Imhha3h3dnRkdnN5bnJta3RoYXRrIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODc0MDc0MTQsImV4cCI6MjEwMjk4MzQxNH0.fwlkrkow1qpkHfpeBSjmNe4VaDk_qvEXQZhZGNmzfSM"

    const val RECOVERY_SCHEME = "addonstation"
    const val RECOVERY_HOST = "recovery"

    const val CHAT_IMAGE_MAX_BYTES = 5 * 1024 * 1024
    val CHAT_IMAGE_MIME = setOf("image/jpeg", "image/png", "image/webp")

    const val DEFAULT_AVATAR = "file:///android_asset/img/onefree.png"
    const val DEFAULT_BANNER = "file:///android_asset/img/default-free-banner.jpg"
    const val DEFAULT_PROFILE = "file:///android_asset/img/default-profile.jpg"
    const val SPLASH_LOGO = "file:///android_asset/img/mobile-splash-logo.png"
    const val AC_COIN = "file:///android_asset/img/addoncoins-v2.png"
    const val APP_ICON = "file:///android_asset/img/icon-192.png"
    const val AUTH_BG = "file:///android_asset/img/auth-background.webp"
    const val ADDONS_PLACEHOLDER = "file:///android_asset/img/addons.webp"
    const val WORLDS_PLACEHOLDER = "file:///android_asset/img/worlds.webp"

    /** Resolves a bundled path ("/items/x.png", "/imgs/...", "/stickers/...") to an asset URI. */
    fun resolveAsset(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http") || path.startsWith("file://") || path.startsWith("content://")) return path
        val clean = path.trim().removePrefix("/")
        return when {
            clean.startsWith("items/") -> "file:///android_asset/items/" + clean.removePrefix("items/")
            clean.startsWith("stickers/") -> "file:///android_asset/stickers/" + clean.removePrefix("stickers/")
            clean.startsWith("imgs/") -> "file:///android_asset/img/" + clean.removePrefix("imgs/")
            clean.startsWith("icons/") -> "file:///android_asset/img/" + clean.removePrefix("icons/")
            else -> "file:///android_asset/$clean"
        }
    }
}
