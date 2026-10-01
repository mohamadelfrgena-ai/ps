package com.addonstation.mobile

import android.app.Application
import android.content.Context
import android.os.Build
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.decode.ImageDecoderDecoder
import coil.decode.GifDecoder
import com.addonstation.mobile.data.AppContainer

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
        container = AppContainer(this)
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
            .build()

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as AddonStationApp).container
    }
}
