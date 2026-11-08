package com.addonstation.mobile.util

import android.app.ActivityManager
import android.content.Context

/**
 * A single, process-wide performance policy derived from the device instead of
 * applying desktop-sized caches and animation pressure to every phone.
 *
 * The values deliberately favour stable frame times over maximum parallelism:
 * decoding too many images at once and retaining 25% of the heap caused GC
 * pauses on 2–3 GB phones, while the default five HTTP requests per host queued
 * AddonStation's independent startup calls on capable phones.
 */
data class DevicePerformanceProfile(
    val lowRamDevice: Boolean,
    val memoryClassMb: Int,
    val allowDecorativeMotion: Boolean,
    val allowAnimatedPreviews: Boolean,
    val imageMemoryCachePercent: Double,
    val imageDiskCacheBytes: Long,
    val imageMaxRequests: Int,
    val imageMaxRequestsPerHost: Int,
    val apiMaxRequestsPerHost: Int,
)

object DevicePerformance {
    @Volatile
    private var cached: DevicePerformanceProfile? = null

    fun profile(context: Context): DevicePerformanceProfile {
        cached?.let { return it }
        return synchronized(this) {
            cached ?: detect(context.applicationContext).also { cached = it }
        }
    }

    private fun detect(context: Context): DevicePerformanceProfile {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val memoryClass = activityManager?.memoryClass?.coerceAtLeast(64) ?: 128
        val lowRam = activityManager?.isLowRamDevice == true || memoryClass <= 128
        val highMemory = !lowRam && memoryClass >= 384

        return when {
            lowRam -> DevicePerformanceProfile(
                lowRamDevice = true,
                memoryClassMb = memoryClass,
                allowDecorativeMotion = false,
                allowAnimatedPreviews = false,
                imageMemoryCachePercent = 0.10,
                imageDiskCacheBytes = 64L * 1024L * 1024L,
                imageMaxRequests = 12,
                imageMaxRequestsPerHost = 4,
                apiMaxRequestsPerHost = 6,
            )

            highMemory -> DevicePerformanceProfile(
                lowRamDevice = false,
                memoryClassMb = memoryClass,
                allowDecorativeMotion = true,
                allowAnimatedPreviews = true,
                imageMemoryCachePercent = 0.20,
                imageDiskCacheBytes = 192L * 1024L * 1024L,
                imageMaxRequests = 32,
                imageMaxRequestsPerHost = 12,
                apiMaxRequestsPerHost = 12,
            )

            else -> DevicePerformanceProfile(
                lowRamDevice = false,
                memoryClassMb = memoryClass,
                allowDecorativeMotion = true,
                allowAnimatedPreviews = true,
                imageMemoryCachePercent = 0.16,
                imageDiskCacheBytes = 128L * 1024L * 1024L,
                imageMaxRequests = 24,
                imageMaxRequestsPerHost = 8,
                apiMaxRequestsPerHost = 8,
            )
        }
    }
}
