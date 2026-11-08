package com.addonstation.mobile

import android.app.Application
import android.content.Context
import android.os.Build

import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.disk.DiskCache
import coil.memory.MemoryCache
import coil.request.CachePolicy

import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.util.DevicePerformance
import com.addonstation.mobile.util.DevicePerformanceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

import okhttp3.Dispatcher
import okhttp3.OkHttpClient

import java.io.File

/**
 * نقطة بدء التطبيق: تُهيّئ الحاوية (Container) التي تحمل عميل Supabase، الجلسة، التفضيلات، والمستودعات.
 *
 * كما تضبط **مُحمِّل الصور** الافتراضي لكل التطبيق بحيث تتحرّك ملفات GIF في أي موضع
 * (الأفاتار، الغلاف، الأداة، الملصق، والمعاينات) — بلا حاجة لأي إعداد داخل الشاشات.
 * AddonStation Mobile v1.2 — Kotlin + Jetpack Compose
 */
class AddonStationApp : Application(), ImageLoaderFactory {

    val performanceProfile: DevicePerformanceProfile by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        DevicePerformance.profile(this)
    }

    private val startupScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // احفظ سبب أي انهيار محلياً كي يستطيع صاحب الهاتف مشاركته دون ADB.
        com.addonstation.mobile.util.CrashDiagnostics.install(this)
        container = AppContainer(this, performanceProfile)
        // تسجيل المرجع فوري لأن خدمة FCM تحتاجه. إنشاء القنوات يتضمن وصولاً
        // إلى NotificationManager والموارد، لذلك لا نجعله ينافس أول إطار.
        com.addonstation.mobile.push.AppContainerHolder.set(this, container)
        startupScope.launch {
            com.addonstation.mobile.push.PushNotifications.ensureChannels(this@AddonStationApp)
        }
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.addonstation.mobile.util.LocaleManager.wrap(base))
    }

    /**
     * مُحمِّل الصور العام: يُسجّل مفكّك GIF المتحرّك فيعمل التحريك في كل الشاشات تلقائياً.
     * • أندرويد 9 (API 28) وأحدث → ImageDecoderDecoder (يدعم GIF وWebP متحرّك، الأخف).
     * • ما قبله → GifDecoder (يعمل على كل الإصدارات).
     */
    override fun newImageLoader(): ImageLoader {
        val profile = performanceProfile
        return ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            // الانتقال اللوني لعشرات الصور معاً يضغط GPU في الأجهزة الضعيفة.
            .crossfade(!profile.lowRamDevice)
            // التوازي وحجم الذاكرة يتبعان قدرة الهاتف. الحد السابق (16 صورة من
            // المضيف و25% من heap) كان يسبب موجات GC وتقطيعاً على أجهزة 2–3 GB.
            .okHttpClient {
                OkHttpClient.Builder()
                    .dispatcher(
                        Dispatcher().apply {
                            maxRequests = profile.imageMaxRequests
                            maxRequestsPerHost = profile.imageMaxRequestsPerHost
                        }
                    )
                    .build()
            }
            // خادم التخزين لا يسمح أحياناً بالتخزين المؤقت ⇒ كنا نعيد تنزيل كل صورة
            // في كل مرة نفتح فيها الشبكة. الآن تُحفظ على القرص بحجم مناسب للجهاز.
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(profile.imageMemoryCachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "site-images"))
                    .maxSizeBytes(profile.imageDiskCacheBytes)
                    .build()
            }
            .build()
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as AddonStationApp).container
    }
}
