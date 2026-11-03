package com.addonstation.mobile.data

import android.content.Context
import com.addonstation.mobile.data.repo.AcRepository
import com.addonstation.mobile.data.repo.AdminRepository
import com.addonstation.mobile.data.repo.AuthRepository
import com.addonstation.mobile.data.repo.AssetsRepository
import com.addonstation.mobile.data.repo.ChatRepository
import com.addonstation.mobile.data.repo.ContentRepository
import com.addonstation.mobile.data.repo.CreatorRepository
import com.addonstation.mobile.data.repo.NotificationsRepository
import com.addonstation.mobile.data.repo.PlansRepository
import com.addonstation.mobile.data.repo.ProfileRepository
import com.addonstation.mobile.data.repo.PushRepository
import kotlinx.coroutines.runBlocking

/**
 * حاوية التطبيق (Service Locator بسيط): تحمل عميل Supabase وكل المستودعات.
 * تُنشأ مرة واحدة في AddonStationApp.
 */
class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext
    val prefs: UserPrefs = UserPrefs(appContext)
    val sessionStore: SessionStore = SessionStore(appContext)
    val realtime = RealtimeHolder()

    @Volatile
    private var config: SupabaseConfig? = null

    val supabase: SupabaseClient = SupabaseClient({ config }, sessionStore)
    val realtimeClient: SupabaseRealtime = SupabaseRealtime(supabase)

    val state: AppState = AppState(this)

    val auth = AuthRepository(supabase, sessionStore)
    val profile = ProfileRepository(supabase, state)
    val content = ContentRepository(supabase, state)
    val plans = PlansRepository(supabase, state)
    val ac = AcRepository(supabase, state)
    val chat = ChatRepository(supabase, state, profile)
    val notifications = NotificationsRepository(supabase)

    /** الإشعارات الفورية: تسجيل الجهاز · التفضيلات · إشعار تجريبي · نبضة الحضور. */
    val push = PushRepository(supabase)
    val creators = CreatorRepository(supabase)
    val admin = AdminRepository(supabase)
    val assets = AssetsRepository(supabase, state)

    init {
        config = SupabaseConfig.fromBuild.takeIf { it.isConfigured }
            ?: runBlocking {
                val (url, key) = prefs.readSupabase()
                SupabaseConfig(url, key).takeIf { it.isConfigured }
            }
    }

    val isConfigured: Boolean get() = config?.isConfigured == true

    fun saveConfig(url: String, anonKey: String) {
        runBlocking { prefs.setSupabase(url, anonKey) }
        config = SupabaseConfig(url.trim().trimEnd('/'), anonKey.trim())
    }

    fun currentConfig(): SupabaseConfig? = config

    /** رابط عام لملف داخل مخزن site-assets (الصور الرمزية والأغلفة والأدوات والملصقات). */
    fun siteAssetUrl(path: String?): String? {
        if (path.isNullOrBlank()) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        val cfg = config ?: return null
        return cfg.publicUrl("site-assets", path)
    }
}

/** يحتوي مرجع الاشتراك اللحظي الحالي في الشات لإغلاقه عند تبديل الغرفة أو مغادرة الشاشة. */
class RealtimeHolder {
    @Volatile
    var current: RealtimeSub? = null

    fun replace(sub: RealtimeSub?) {
        current?.close()
        current = sub
    }

    fun close() {
        current?.close()
        current = null
    }
}
