package com.addonstation.mobile.push

import android.content.Context
import android.os.Build
import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.util.AppPrefs
import com.google.firebase.messaging.FirebaseMessaging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.util.TimeZone
import kotlin.coroutines.resume

/**
 * تسجيل الجهاز على الخادم (توكن FCM) وإلغاء تسجيله.
 *
 * متى يعمل؟
 *   • عند بدء التطبيق والمستخدم مسجّل دخوله (وتُحدَّث اللغة والمنطقة الزمنية في
 *     كل مرة ⇒ ساعات الهدوء تُحسب بتوقيت المستخدم الصحيح ولغته الصحيحة).
 *   • عند وصول onNewToken من الخدمة (تسجيل سريع بلا انتظار شبكة طويلة).
 *   • عند تسجيل الخروج ⇒ إلغاء التسجيل (لا إشعارات لحساب سُلّم جهازه لغيره).
 *
 * إن لم تكن FCM مُهيّأة (لا ملف google-services.json) تخرج كل الدوال بسلام.
 */
object PushRegistrar {

    /** نطاق خاص: لا نستعمل GlobalScope ولا نُبقي الشاشة منتظرة. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** بعد فشل واحد في الوصول إلى FCM لا نُكرّر المحاولة خلال هذه الجلسة. */
    @Volatile private var fcmUnavailable = false

    /** دقائق شرق UTC — عليها تُحسب ساعات الهدوء في الخادم. */
    private fun tzOffsetMinutes(): Int =
        TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 60_000

    /** إصدار التطبيق (1.3 مثلاً) — أدقّ من إصدار نظام أندرويد للتشخيص. */
    private fun appVersion(): String =
        runCatching { com.addonstation.mobile.BuildConfig.VERSION_NAME }.getOrNull() ?: "?"

    private fun locale(context: Context): String =
        runCatching { AppPrefs.readLocale(context) }.getOrNull()?.takeIf { it.isNotBlank() } ?: "en"

    /** يطلب التوكن من FCM (يرجع null إن لم تكن مُهيّأة أو فشل الاتصال). */
    private suspend fun fetchToken(): String? {
        if (fcmUnavailable) return null
        if (!PushNotifications.configured(AppContainerHolder.current ?: return null)) return null
        return suspendCancellableCoroutine { cont ->
            runCatching {
                FirebaseMessaging.getInstance().token
                    .addOnCompleteListener { task ->
                        if (cont.isActive) cont.resume(if (task.isSuccessful) task.result else null)
                    }
            }.onFailure {
                fcmUnavailable = true
                if (cont.isActive) cont.resume(null)
            }
        }
    }

    /**
     * يُسجّل الجهاز للمستخدم الحالي. تُنادى:
     * عند الإقلاع (مسجّل الدخول) · بعد نجاح الدخول · عند تجديد التوكن.
     */
    suspend fun register(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val container = AppContainerHolder.get(app) ?: return@withContext
        if (!PushNotifications.configured(app)) return@withContext
        if (container.supabase.session() == null) return@withContext
        val token = fetchToken() ?: return@withContext
        runCatching {
            container.push.registerDevice(
                token = token,
                platform = "android",
                appVersion = appVersion(),
                locale = locale(app),
                tzOffset = tzOffsetMinutes(),
            )
        }
    }

    /** إلغاء تسجيل جهاز هذا المستخدم (عند تسجيل الخروج). */
    suspend fun unregister(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        val container = AppContainerHolder.get(app) ?: return@withContext
        if (!PushNotifications.configured(app)) return@withContext
        val token = fetchToken() ?: return@withContext
        runCatching { container.push.unregisterDevice(token) }
    }

    /** من خدمة FCM (onNewToken) — التوكن جاهز فلا نطلبه من جديد. */
    fun sendTokenToServer(context: Context, token: String) {
        val app = context.applicationContext
        scope.launch {
            runCatching {
                val container = AppContainerHolder.get(app) ?: return@launch
                if (!PushNotifications.configured(app)) return@launch
                if (container.supabase.session() == null) return@launch
                container.push.registerDevice(
                    token = token,
                    platform = "android",
                    appVersion = appVersion(),
                    locale = locale(app),
                    tzOffset = tzOffsetMinutes(),
                )
            }
        }
    }

    /** نبضة الحضور: عليها يقوم «تذكير الغياب» (تُنادى عند كل فتح للتطبيق). */
    suspend fun heartbeat(container: AppContainer) = withContext(Dispatchers.IO) {
        if (container.supabase.session() == null) return@withContext
        runCatching { container.push.heartbeat() }
    }

    /** تسجيل الدخول/الخروج يبدّل المستخدم ⇒ نُزامن الجهاز معه (بلا حجب الواجهة). */
    fun onSessionChanged(context: Context, signedIn: Boolean) {
        val app = context.applicationContext
        scope.launch {
            if (signedIn) register(app) else unregister(app)
        }
    }
}

/**
 * الوصول إلى حاوية التطبيق من داخل خدمة FCM (التي ينشئها النظام بلا وسائط).
 * تُضبط مرة واحدة في AddonStationApp، ومعها مسار احتياطي من الـApplication.
 */
object AppContainerHolder {
    @Volatile private var container: AppContainer? = null
    @Volatile var current: Context? = null
        private set

    fun set(appContext: Context, value: AppContainer) {
        current = appContext
        container = value
    }

    fun get(context: Context): AppContainer? =
        container ?: runCatching {
            (context.applicationContext as com.addonstation.mobile.AddonStationApp).container
        }.getOrNull()
}
