package com.addonstation.mobile.util

import android.os.Build
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

/**
 * أدوات الوقت والتاريخ: التحية حسب ساعة الهاتف (كما تطلب الوثيقة §4.1)،
 * فواصل التواريخ في الشات (اليوم/أمس/التاريخ)، وتنسيق المدد.
 */
object TimeFormat {

    /** 05:00–11:59 صباح الخير، وإلا مساء الخير (راجع دالة التحية في الشاشات). */
    fun isMorning(now: Date = Date()): Boolean {
        val cal = Calendar.getInstance().apply { time = now }
        val hour = cal.get(Calendar.HOUR_OF_DAY)
        return hour in 5..11
    }

    fun greetingRes(morning: Boolean): Int =
        if (morning) com.addonstation.mobile.R.string.greeting_morning
        else com.addonstation.mobile.R.string.greeting_evening

    fun dateOnly(iso: String?, locale: Locale): String {
        val millis = parseMillis(iso) ?: return "—"
        val fmt = SimpleDateFormat("dd MMM yyyy", locale)
        return fmt.format(Date(millis))
    }

    fun dateTime(iso: String?, locale: Locale): String {
        val millis = parseMillis(iso) ?: return "—"
        val fmt = SimpleDateFormat("dd MMM yyyy · HH:mm", locale)
        return fmt.format(Date(millis))
    }

    fun timeOnly(iso: String?, locale: Locale): String {
        val millis = parseMillis(iso) ?: return ""
        return SimpleDateFormat("HH:mm", locale).format(Date(millis))
    }

    /**
     * فاصل التواريخ في الشات: "اليوم" / "أمس" / التاريخ الكامل.
     * النصّان يُمرَّران جاهزين من الموارد (R.string.chat_today · chat_yesterday)
     * لأن كل اللغات الثمانية يجب أن تعمل — لا مقارنة على "ar" هنا.
     */
    fun dayLabel(iso: String?, locale: Locale, todayLabel: String, yesterdayLabel: String): String {
        val millis = parseMillis(iso) ?: return ""
        val now = Calendar.getInstance()
        val then = Calendar.getInstance().apply { timeInMillis = millis }
        val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
        val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
        val today = dayDiff == 0 && sameYear
        val yesterday = dayDiff == 1 && sameYear
        return when {
            today -> todayLabel
            yesterday -> yesterdayLabel
            else -> SimpleDateFormat(if (sameYear) "dd MMM" else "dd MMM yyyy", locale)
                .format(Date(millis))
        }
    }

    /**
     * مفتاح اليوم **بالتوقيت المحلي** (مثال: ‎2026-10-07‎) — للتحقّق السريع:
     * هل الرسالتان في يومين مختلفين؟
     *
     * **لماذا؟** شاشة الشات كانت تحسب [dayLabel] **مرتين لكل رسالة** في كل
     * إعادة تركيب (سطران لكل عنصر أثناء التمرير) ⇒ آلاف العمليات في الثانية
     * = تقطيع. الآن: حساب واحد لكل رسالة يُخزَّن في [ChatMessageUi.dayKey]،
     * ثم مقارنة نصّية رخيصة، ولا يُبنى نصّ التاريخ إلا للفواصل نفسها (يوم واحد
     * لكل عدة رسائل).
     */
    fun localDayKey(iso: String?): String {
        val millis = parseMillis(iso) ?: return ""
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)}"
    }

    fun parseMillis(iso: String?): Long? {
        if (iso.isNullOrBlank()) return null
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                java.time.Instant.parse(iso).toEpochMilli()
            } else {
                val pattern = if (iso.contains('.')) "yyyy-MM-dd'T'HH:mm:ss.SSS" else "yyyy-MM-dd'T'HH:mm:ss"
                val cleaned = iso.substringBefore('+').removeSuffix("Z")
                val fmt = SimpleDateFormat(pattern, Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(cleaned)?.time
            }
        } catch (t: Throwable) {
            null
        }
    }

    /** هل مرّ أقل من 5 دقائق بين الرسالتين؟ (تجميع رسائل نفس الشخص) */
    fun withinFiveMinutes(a: String?, b: String?): Boolean {
        val first = parseMillis(a) ?: return false
        val second = parseMillis(b) ?: return false
        return kotlin.math.abs(second - first) <= TimeUnit.MINUTES.toMillis(5)
    }

    fun durationLabel(seconds: Int?): String {
        if (seconds == null || seconds <= 0) return "0:00"
        val minutes = seconds / 60
        val rest = seconds % 60
        return "%d:%02d".format(minutes, rest)
    }

    fun fileSizeLabel(bytes: Long?): String {
        if (bytes == null || bytes <= 0) return "—"
        val kb = bytes / 1024.0
        return when {
            kb < 1024 -> "%.0f KB".format(kb)
            else -> "%.1f MB".format(kb / 1024.0)
        }
    }

    fun isoNow(): String =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) java.time.Instant.now().toString()
        else SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())

    /** ينتهي الاشتراك؟ يتبقى عدد الأيام (سالب = منتهي). */
    fun daysUntil(iso: String?): Int? {
        val millis = parseMillis(iso) ?: return null
        val diff = millis - System.currentTimeMillis()
        return TimeUnit.MILLISECONDS.toDays(diff).toInt()
    }

    fun expiryLabel(iso: String?, locale: Locale): String {
        val millis = parseMillis(iso) ?: return ""
        return SimpleDateFormat("dd MMM yyyy", locale).format(Date(millis))
    }

    /** صياغة عربية مبسّطة للعدّ التنازلي (يُستخدم لعرض "أنت مكتوم حتى ..."). */
    fun countdown(untilIso: String?): String {
        val millis = parseMillis(untilIso) ?: return "—"
        val remaining = millis - System.currentTimeMillis()
        if (remaining <= 0) return "0"
        val minutes = TimeUnit.MILLISECONDS.toMinutes(remaining)
        return when {
            minutes < 60 -> "$minutes min"
            minutes < 1440 -> "${minutes / 60} h"
            else -> "${minutes / 1440} d"
        }
    }
}
