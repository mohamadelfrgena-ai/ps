package com.addonstation.app.core

import android.app.DownloadManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.addonstation.app.R
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlin.random.Random

object TimeAgo {
    private fun parseIso(iso: String): Long? {
        val cleaned = iso.trim().replace("Z", "+00:00")
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
            "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
            "yyyy-MM-dd'T'HH:mm:ssXXX",
            "yyyy-MM-dd HH:mm:ssXXX",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (p in patterns) {
            try {
                val sdf = SimpleDateFormat(p, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                val d = sdf.parse(cleaned) ?: continue
                return d.time
            } catch (_: Exception) { }
        }
        return null
    }

    fun millis(iso: String?): Long? = iso?.let { parseIso(it) }

    fun label(iso: String?, ctx: Context): String {
        val t = iso?.let { parseIso(it) } ?: return ""
        val now = System.currentTimeMillis()
        val diff = (now - t).coerceAtLeast(0)
        val min = diff / 60_000
        return when {
            min < 1 -> ctx.getString(R.string.common_just_now)
            min < 60 -> ctx.getString(R.string.common_minutes_ago, min.toInt())
            min < 60 * 24 -> ctx.getString(R.string.common_hours_ago, (min / 60).toInt())
            min < 60 * 24 * 7 -> ctx.getString(R.string.common_days_ago, (min / 1440).toInt())
            else -> try {
                val sdf = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
                sdf.format(Date(t))
            } catch (_: Exception) { iso.substringBefore("T") }
        }
    }

    fun dayHeader(iso: String?, ctx: Context): String {
        val t = iso?.let { parseIso(it) } ?: return ""
        val cal = java.util.Calendar.getInstance()
        val today = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val day = 24L * 3600_000
        return when {
            t >= today -> ctx.getString(R.string.common_today)
            t >= today - day -> ctx.getString(R.string.common_yesterday)
            else -> try {
                SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(t))
            } catch (_: Exception) { iso.substringBefore("T") }
        }
    }
}

object Formats {
    fun count(n: Long): String = when {
        n >= 1_000_000 -> String.format(Locale.US, "%.1fM", n / 1_000_000.0)
        n >= 1_000 -> String.format(Locale.US, "%.1fK", n / 1_000.0)
        else -> n.toString()
    }

    fun acronym(name: String): String {
        val parts = name.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
        return when {
            parts.isEmpty() -> "?"
            parts.size == 1 -> parts[0].take(2).uppercase()
            else -> (parts[0].first() + "" + parts[1].first()).uppercase()
        }
    }
}

object Plans {
    private val order = listOf("free", "epic", "legendary")
    fun atLeast(userPlan: String, required: String): Boolean =
        order.indexOf(userPlan.lowercase()).coerceAtLeast(0) >=
            order.indexOf(required.lowercase()).coerceAtLeast(0)

    fun rankLabelRes(rank: String): Int = when (rank) {
        "admin" -> R.string.rank_admin
        "moderator" -> R.string.rank_moderator
        "creator" -> R.string.rank_creator
        "legendary" -> R.string.rank_legendary
        "epic" -> R.string.rank_epic
        else -> R.string.rank_free
    }

    fun planLabelRes(plan: String): Int = when (plan.lowercase()) {
        "legendary" -> R.string.rank_legendary
        "epic" -> R.string.rank_epic
        else -> R.string.rank_free
    }
}

private val MentionRegex = Regex("@([A-Za-z0-9_.\\-]{2,30})")

/** Segments of a chat message: plain text vs @mention chips. */
sealed interface MsgSegment {
    data class Text(val text: String) : MsgSegment
    data class Mention(val username: String) : MsgSegment
}

fun splitMentions(content: String): List<MsgSegment> {
    val out = mutableListOf<MsgSegment>()
    var last = 0
    for (m in MentionRegex.findAll(content)) {
        if (m.range.first > last) out += MsgSegment.Text(content.substring(last, m.range.first))
        out += MsgSegment.Mention(m.groupValues[1])
        last = m.range.last + 1
    }
    if (last < content.length) out += MsgSegment.Text(content.substring(last))
    if (out.isEmpty()) out += MsgSegment.Text(content)
    return out
}

fun extractMentions(content: String): List<String> =
    MentionRegex.findAll(content).map { it.groupValues[1] }.distinct().toList()

fun isValidIpv4(ip: String): Boolean {
    val parts = ip.trim().split(".")
    if (parts.size != 4) return false
    return parts.all { p ->
        val n = p.toIntOrNull() ?: return false
        n in 0..255 && (p == "0" || !p.startsWith("0") || p.length == 1)
    }
}

fun referenceCode(): String {
    val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (1..6).map { chars[Random.nextInt(chars.length)] }.joinToString("")
}

/** Current UTC time as ISO-8601 (API 24 compatible, no java.time). */
fun isoNowUtc(): String {
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(Date())
}

fun copyToClipboard(ctx: Context, text: String) {
    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("AddonStation", text))
}

fun shareText(ctx: Context, text: String) {
    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }, "AddonStation"))
}

fun downloadViaManager(ctx: Context, url: String, fileName: String): Long {
    val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    val req = DownloadManager.Request(Uri.parse(url))
        .setTitle(fileName)
        .setDescription("AddonStation")
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "AddonStation/$fileName")
        .setAllowedOverMetered(true)
    return dm.enqueue(req)
}

object LanguageManager {
    val supported = listOf("ar", "en", "es", "pt", "fr")
    fun apply(lang: String) {
        val tag = if (lang in supported) lang else "en"
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(tag))
    }
    fun label(lang: String): String = when (lang) {
        "ar" -> "العربية 🇸🇦"
        "en" -> "English 🇬🇧"
        "es" -> "Español 🇪🇸"
        "pt" -> "Português 🇧🇷"
        "fr" -> "Français 🇫🇷"
        else -> lang
    }
}
