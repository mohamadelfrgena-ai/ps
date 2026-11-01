package com.addonstation.mobile.util

import android.content.Context
import android.os.Build
import android.os.Process
import com.addonstation.mobile.BuildConfig
import java.io.File
import kotlin.system.exitProcess

/** Stores a sanitized fatal-error report locally so support can be shared without ADB. */
object CrashDiagnostics {
    private const val FILE_NAME = "last_crash_report.txt"
    private const val MAX_REPORT_CHARS = 16_000

    fun install(context: Context) {
        val appContext = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val report = buildString {
                    appendLine("AddonStation crash report")
                    appendLine("App: ${BuildConfig.VERSION_NAME} · build ${BuildConfig.BUILD_STAMP}")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
                    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine()
                    append(throwable.stackTraceToString())
                }.let(::redact).take(MAX_REPORT_CHARS)
                File(appContext.filesDir, FILE_NAME).writeText(report)
            }
            if (previous != null) {
                previous.uncaughtException(thread, throwable)
            } else {
                Process.killProcess(Process.myPid())
                exitProcess(10)
            }
        }
    }

    fun read(context: Context): String? = runCatching {
        File(context.applicationContext.filesDir, FILE_NAME)
            .takeIf(File::isFile)
            ?.readText()
            ?.takeIf(String::isNotBlank)
    }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.applicationContext.filesDir, FILE_NAME).delete() }
    }

    private fun redact(input: String): String {
        var text = input
        text = Regex("sb_(?:publishable|secret)_[A-Za-z0-9_-]+")
            .replace(text, "sb_[REDACTED]")
        text = Regex("(?i)Bearer\\s+[^\\s\\\"']+")
            .replace(text, "Bearer [REDACTED]")
        text = Regex("eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")
            .replace(text, "[JWT REDACTED]")
        text = Regex("[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}", RegexOption.IGNORE_CASE)
            .replace(text, "[EMAIL REDACTED]")
        return text
    }
}
