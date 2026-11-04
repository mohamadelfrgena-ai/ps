package com.addonstation.mobile.data

import android.content.Context
import com.addonstation.mobile.data.repo.AcRepository
import com.addonstation.mobile.data.repo.AdminRepository
import com.addonstation.mobile.data.repo.AuthRepository
import com.addonstation.mobile.data.repo.AssetsRepository
import com.addonstation.mobile.data.repo.ChatRepository
import com.addonstation.mobile.data.repo.ContentRepository
import com.addonstation.mobile.data.repo.ConsoleLinkRepository
import com.addonstation.mobile.data.repo.CreatorRepository
import com.addonstation.mobile.data.repo.NotificationsRepository
import com.addonstation.mobile.data.repo.PlansRepository
import com.addonstation.mobile.data.repo.ProfileRepository
import com.addonstation.mobile.data.repo.PushRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    private var config: SupabaseConfig? = SupabaseConfig.fromBuild.takeIf { it.isConfigured }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val configLoaded = CompletableDeferred<Unit>()

    val supabase: SupabaseClient = SupabaseClient({ config }, sessionStore)
    val realtimeClient: SupabaseRealtime = SupabaseRealtime(supabase)

    val state: AppState = AppState(this)

    val auth = AuthRepository(supabase, sessionStore)
    val profile = ProfileRepository(supabase, state)
    val consoleLinks = ConsoleLinkRepository(supabase)
    val content = ContentRepository(supabase, state)
    val plans = PlansRepository(supabase, state)
    val ac = AcRepository(supabase, state)
    val chat = ChatRepository(supabase, state, profile)
    val notifications = NotificationsRepository(supabase)

    /** الإشعارات الفورية: تسجيل الجهاز · التفضيلات · إشعار تجريبي · نبضة الحضور. */
    val push = PushRepository(supabase)
    val creators = CreatorRepository(supabase, state)
    val admin = AdminRepository(supabase)
    val assets = AssetsRepository(supabase, state)

    init {
        if (config != null) {
            configLoaded.complete(Unit)
        } else {
            // بناء Application لا ينتظر DataStore. AppRoot ينتظر هذه الإشارة
            // داخل coroutine الإقلاع، بينما يبقى أول إطار حراً للرسم.
            applicationScope.launch {
                try {
                    val (url, key) = prefs.readSupabase()
                    config = SupabaseConfig(url, key).takeIf { it.isConfigured }
                } catch (_: Throwable) {
                    // تلف تفضيل محلي لا يمنع شاشة الإعداد من الظهور.
                    config = null
                } finally {
                    configLoaded.complete(Unit)
                }
            }
        }
    }

    /** انتظر تحميل إعداد أول تشغيل من القرص، إن لم تكن إعدادات البناء جاهزة. */
    suspend fun awaitConfigLoaded() {
        configLoaded.await()
    }

    val isConfigured: Boolean get() = config?.isConfigured == true

    fun saveConfig(url: String, anonKey: String) {
        val normalized = SupabaseConfig(url.trim().trimEnd('/'), anonKey.trim())
        config = normalized
        // تحديث الذاكرة فوري كي تنتقل الواجهة بلا تجمّد؛ الحفظ تسلسلي في الخلفية.
        applicationScope.launch {
            try {
                prefs.setSupabase(normalized.url, normalized.anonKey)
            } catch (_: Throwable) {
                // تبقى الإعدادات الحالية صالحة في الذاكرة؛ يمكن إعادة الحفظ لاحقاً.
            }
        }
    }

    fun currentConfig(): SupabaseConfig? = config

    /**
     * رابط أصل واجهة. الأصول العادية من site-assets العام؛ أما المسار المحجوز
     * admin-private/ فلا يُرجع إلا رابطاً موقّعاً سبق أن حصل عليه حساب admin.
     */
    fun siteAssetUrl(
        path: String?,
        privateAssetUrls: Map<String, String> = state.privateAssetUrls.value,
    ): String? {
        if (path.isNullOrBlank()) return null
        val clean = path.trim().trimStart('/')
        if (!state.isAdmin && state.isAdminOnlyAssetValue(clean)) return null
        if (path.startsWith("http://") || path.startsWith("https://")) return path
        if (isPrivateAdminAssetPath(clean)) {
            return if (state.isAdmin) privateAssetUrls[clean] else null
        }
        val cfg = config ?: return null
        return cfg.publicUrl("site-assets", clean)
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
