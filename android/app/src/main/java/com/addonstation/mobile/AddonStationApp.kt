package com.addonstation.mobile

import android.app.Application
import android.content.Context
import com.addonstation.mobile.data.AppContainer

/**
 * نقطة بدء التطبيق: تُهيّئ الحاوية (Container) التي تحمل عميل Supabase، الجلسة، التفضيلات، والمستودعات.
 * AddonStation Mobile v1.2 — Kotlin + Jetpack Compose
 */
class AddonStationApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(com.addonstation.mobile.util.LocaleManager.wrap(base))
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as AddonStationApp).container
    }
}
