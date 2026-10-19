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

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        // احفظ سبب أي انهيار محلياً كي يستطيع صاحب الهاتف مشاركته دون ADB.
        com.addonstation.mobile.util.CrashDiagnostics.install(this)
        container = AppContainer(this)
        // الإشعارات: نُسجّل الحاوية (تحتاجها خدمة FCM) وننشئ القنوات الخمس
        // (رسائل · محتوى · اجتماعي · حساب · تذكير) فتظهر بأسماء بلغة المستخدم.
        com.addonstation.mobile.push.AppContainerHolder.set(this, container)
        com.addonstation.mobile.push.PushNotifications.ensureChannels(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.addonstation.mobile.util.LocaleManager.wrap(base))
    }

    /**
     * مُحمِّل الصور العام: يُسجّل مفكّك GIF المتحرّك فيعمل التحريك في كل الشاشات تلقائياً.
     * • أندرويد 9 (API 28) وأحدث → ImageDecoderDecoder (يدعم GIF وWebP متحرّك، الأخف).
     * • ما قبله → GifDecoder (يعمل على كل الإصدارات).
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }
            .crossfade(true)
            // كل صور الموقع من نفس المضيف ⇒ الحد الافتراضي (5 تنزيلات معاً)
            // كان يجعل شبكة الأصول تظهر ببطء شديد، فرفعناه إلى 16.
            .okHttpClient {
                OkHttpClient.Builder()
                    .dispatcher(
                        Dispatcher().apply {
                            maxRequests = 48
                            maxRequestsPerHost = 16
                        }
                    )
                    .build()
            }
            // خادم التخزين لا يسمح أحياناً بالتخزين المؤقت ⇒ كنا نعيد تنزيل كل صورة
            // في كل مرة نفتح فيها الشبكة. الآن تُحفظ على القرص.
            .respectCacheHeaders(false)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(File(cacheDir, "site-images"))
                    .maxSizeBytes(192L * 1024 * 1024)
                    .build()
            }
            .build()

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as AddonStationApp).container
    }
}
