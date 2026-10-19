package com.addonstation.mobile.ui.nav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NewReleases
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.ui.chat.ChatScreen
import com.addonstation.mobile.ui.components.BottomNavBar
import com.addonstation.mobile.ui.components.ContentColumn
import com.addonstation.mobile.ui.home.HomeActions
import com.addonstation.mobile.ui.home.HomeScreen
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.splash.SplashScreen
import com.addonstation.mobile.ui.store.StoreScreen
import com.addonstation.mobile.ui.store.ContentDetailScreen
import com.addonstation.mobile.ui.settings.SettingsScreen
import com.addonstation.mobile.ui.settings.ConsoleLinkScreen
import com.addonstation.mobile.ui.settings.LinkedConsolesScreen
import com.addonstation.mobile.ui.profile.ProfileScreen
import com.addonstation.mobile.ui.common.SetupScreen
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.AppPrefs
import android.app.Activity
import android.os.Build
import android.content.ContextWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** مسارات التنقل كلها في مكان واحد. */
object Routes {
    const val HOME = "home"
    const val STORE = "store"
    const val COMMUNITY = "community"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"
    const val APP_ABOUT = "about-app"
    const val PRIVACY = "privacy-policy"
    const val TERMS = "terms-of-use"

    const val LOGIN = "login"
    const val SIGNUP = "signup"
    const val RESET = "reset"

    const val ITEM = "item/{itemId}"
    fun item(id: String) = "item/$id"

    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val EXCLUSIVES = "exclusives"
    const val CHAT = "chat/{channelId}"
    fun chat(id: String) = "chat/$id"
    const val WALLET = "wallet"
    const val AC_HISTORY = "wallet/history"
    const val NOTIFICATIONS = "notifications"
    /** إعدادات الإشعارات (الأقسام وساعات الهدوء ووتيرة التذكير). */
    const val NOTIF_SETTINGS = "notification-settings"
    const val CONSOLE_LINK = "console-link"
    const val LINKED_CONSOLES = "linked-consoles"
    const val FRIENDS = "friends"
    const val PLANS = "plans"
    const val CHECKOUT = "checkout/{planId}"
    fun checkout(planId: String) = "checkout/$planId"
    const val ORDERS = "orders"
    const val CREATORS = "creators"
    const val CREATOR_PROFILE = "creator/{userId}"
    fun creatorProfile(id: String) = "creator/$id"
    const val CREATOR_APPLY = "creator-apply"
    const val CREATOR_STUDIO = "creator-studio"
    const val EDIT_PROFILE = "edit-profile"
    /** «محتواي» — كل ما نشرتُه (وثيقة شاشات الملف §3: بلاطة الشبكة 2×2). */
    const val MY_CONTENT = "my-content"
    /** «ملفي كما يراه الآخرون» — الشاشة C من وثيقة شاشات الملف. */
    const val PROFILE_AS_OTHERS = "profile-as-others"
    const val USER_PROFILE = "user/{userId}"
    fun userProfile(id: String) = "user/$id"
    const val ADMIN = "admin"
    const val ADMIN_CONTENT = "admin-content"
    const val ADMIN_BULK_IMPORT = "admin-content/bulk-import"
    const val ADMIN_CONTENT_EDIT = "admin-content/{contentId}"
    fun adminContentEdit(id: String) = "admin-content/$id"
    const val ADMIN_ASSETS = "admin-assets"
    const val ADMIN_CREATORS = "admin-creators"
    const val ADMIN_CREATOR_TIPS = "admin-creator-tips"
    const val ADMIN_AC = "admin-ac"
    /** شاشة AC مع مستخدم محدَّد مسبقاً (زر «+AC» في بطاقة المستخدم). */
    const val ADMIN_AC_USER = "admin-ac/user/{userId}"
    fun adminAcUser(id: String) = "admin-ac/user/$id"
    const val ADMIN_SETTINGS = "admin-settings"
    const val ADMIN_AUDIT = "admin-audit"
    const val ADMIN_CHAT = "admin-chat"
}

@Composable
fun AppRoot(
    recoveryLink: String? = null,
    onRecoveryConsumed: () -> Unit = {},
    /** وجهة طلبها إشعار FCM (chat:<id> · item:<id> · notifications · plans · orders · store). */
    pushRoute: String? = null,
    onPushConsumed: () -> Unit = {},
) {
    val container = rememberContainer()
    val dark by container.prefs.darkTheme.collectAsState(initial = true)

    var splashDone by remember { mutableStateOf(false) }
    var configured by remember { mutableStateOf(container.isConfigured) }
    var startupProgress by remember { mutableStateOf(0f) }
    var startupReady by remember { mutableStateOf(false) }
    var navigationReady by remember { mutableStateOf(false) }
    var underlayReady by remember { mutableStateOf(false) }

    // Keep the animated entrance isolated from the expensive first composition
    // of the navigation graph. Once the three centred words have settled, build
    // the destination behind the static splash and wait for two rendered frames
    // before allowing the exit transition.
    LaunchedEffect(Unit) {
        withFrameNanos { }
        delay(850L)
        navigationReady = true
        withFrameNanos { }
        withFrameNanos { }
        underlayReady = true
    }

    // أيقونات شريط الحالة تتبع وضع التطبيق نفسه (لا وضع الجهاز):
    // في الوضع الفاتح تصير الأيقونات داكنة فوق الخلفية الفاتحة.
    // (شاشة البداية داكنة دائماً، فتبقى الأيقونات فاتحة عليها إلى أن تنتهي.)
    val rootView = LocalView.current
    if (!rootView.isInEditMode) {
        SideEffect {
            val activity = generateSequence(rootView.context) { (it as? ContextWrapper)?.baseContext }
                .filterIsInstance<Activity>()
                .firstOrNull() ?: return@SideEffect
            val window = activity.window
            val bars = WindowCompat.getInsetsController(window, rootView)
            val light = !dark && splashDone
            bars.isAppearanceLightStatusBars = light
            bars.isAppearanceLightNavigationBars = light
        }
    }
    var deepLinkSession by remember { mutableStateOf(false) }

    // **رابط الاستعادة وحده في أثر مستقل**: قد يصل والتطبيق مفتوح أصلاً
    // (onNewIntent) فيتغيّر `recoveryLink` بعد الإقلاع. لو بقينا نعالجه داخل
    // أثر الإقلاع (مفتاحه Unit) لضاع الرابط ولم يحدث شيء ⇒ «الرابط لا يعمل».
    LaunchedEffect(recoveryLink) {
        val link = recoveryLink ?: return@LaunchedEffect
        val recovered = runCatching {
            withContext(Dispatchers.IO) { container.auth.acceptRecoveryLink(link) }
        }.getOrDefault(false)
        if (recovered) {
            container.state.setSignedIn(true)
            runCatching { withContext(Dispatchers.IO) { container.state.refreshAll() } }
            deepLinkSession = true
        }
        onRecoveryConsumed()
    }

    // استعادة الجلسة وتحميل أساسيات التطبيق بالتوازي مع شاشة البداية.
    // كل مرحلة تحدّث شريطاً حقيقياً؛ عند انقطاع الشبكة لا يبقى التطبيق عالقاً
    // على شاشة البداية، بل ينتهي التمهيد بعد حدّ زمني وتظهر حالات إعادة المحاولة.
    LaunchedEffect(container) {
        startupProgress = 0.04f
        try {
            withTimeoutOrNull(10_000L) {
                val signedIn = withContext(Dispatchers.IO) {
                    val hasSession = container.auth.isSignedIn()
                    if (hasSession) container.auth.ensureFreshSession()
                    hasSession
                }
                startupProgress = 0.18f

                if (signedIn) {
                    coroutineScope {
                        // ملف المستخدم والخطط والأصول تُحمّل بالتوازي كما في مسار الإقلاع السابق.
                        val refresh = async {
                            withContext(Dispatchers.IO) { container.state.refreshAll() }
                        }
                        val assets = async {
                            withContext(Dispatchers.IO) { container.assets.all() }
                        }
                        startupProgress = 0.30f
                        refresh.await()
                        startupProgress = 0.65f

                        val cashRoom = async {
                            withContext(Dispatchers.IO) {
                                // الخادم يمنع الإنشاء لغير الأدمن؛ نمرر الدور المحمّل كما كان.
                                container.chat.ensureCashServicesRoom(
                                    container.state.profile.value?.role == "admin",
                                )
                            }
                        }
                        assets.await()
                        startupProgress = 0.88f
                        cashRoom.await()
                        startupProgress = 0.96f
                    }
                } else {
                    // بيانات المتجر العامة مستقلة؛ لا نجعل الخطط وطرق الدفع متتابعتين.
                    coroutineScope {
                        val plans = async {
                            withContext(Dispatchers.IO) { container.plans.loadPlans() }
                        }
                        val paymentMethods = async {
                            withContext(Dispatchers.IO) { container.plans.loadPaymentMethods() }
                        }
                        startupProgress = 0.34f
                        plans.await()
                        startupProgress = 0.64f
                        paymentMethods.await()
                        startupProgress = 0.96f
                    }
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            // التمهيد اكتمل بحالة متدهورة؛ الشاشة التالية تملك إعادة المحاولة.
        } finally {
            configured = container.isConfigured
            startupProgress = 1f
            startupReady = true
        }
    }

    com.addonstation.mobile.ui.theme.AddonStationTheme(darkTheme = dark) {
        com.addonstation.mobile.ui.theme.AppBackground(dark = dark) {
            // **حجز منطقة شريط النظام (الساعة والبطارية والواي فاي) لكل التطبيق**:
            // التطبيق يرسم من الحافة إلى الحافة (edge-to-edge) وtargetSdk=35 يفرض
            // ذلك، فكان محتوى ٣٥ شاشة يعلو شريط الحالة ويتداخل معه. هنا نحجز
            // المنطقة مرة واحدة عند الجذر فيبدأ كل المحتوى تحتها، وخلفيتها هي
            // خلفية التطبيق نفسها ⇒ لا فواصل ولا اختلاف في الشكل.
            Column(Modifier.fillMaxSize()) {
                Spacer(
                    Modifier
                        .fillMaxWidth()
                        // مع UNION مع فتحة الكاميرا: بعض الأجهزة (ثقب/شق في الأعلى)
                        // تُبلّغ عن المنطقة عبر displayCutout لا عبر statusBars،
                        // فكان الشريط يبقى متداخلاً مع الأيقونات هناك.
                        .windowInsetsTopHeight(WindowInsets.statusBars.union(WindowInsets.displayCutout)),
                )
                Box(Modifier.fillMaxSize()) {
                    // **البناء خلف شاشة الترحيب لا بعدها**: نؤخر التركيب الثقيل
                    // حتى تستقر حركة الكلمات، ثم نبني الوجهة تحت الغطاء الساكن
                    // وننتظر إطارين قبل التلاشي. هكذا لا تنافس الحركةَ وفي الوقت
                    // نفسه تكون الشاشة التالية جاهزة حين تنقشع شاشة الترحيب.
                    if (configured && navigationReady) {
                        MainNavigation(
                            startAtReset = deepLinkSession,
                            pushRoute = pushRoute,
                            onPushConsumed = onPushConsumed,
                        )
                    } else if (splashDone) {
                        // الإعداد الأول (رابط/مفتاح Supabase) يظهر بعد انقشاع
                        // شاشة الترحيب — لا فوقها.
                        SetupScreen(onDone = { configured = container.isConfigured })
                    }
                    if (!splashDone) {
                        // الشاشة نفسها فوق كل شيء، وتخرج بتلاشٍ يكشف ما تحتها
                        SplashScreen(
                            loadingProgress = startupProgress,
                            startupReady = startupReady && (!configured || underlayReady),
                            onFinished = { splashDone = true },
                        )
                    }
                }
            }
        }
    }

    // إعادة التحميل عند تسجيل الدخول من أي شاشة
}

@Composable
private fun MainNavigation(
    startAtReset: Boolean,
    /** وجهة طلبها إشعار FCM (chat:<id> · item:<id> · notifications · plans …). */
    pushRoute: String? = null,
    onPushConsumed: () -> Unit = {},
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val mainSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val profileSavedMessage = stringResource(R.string.edit_profile_saved)
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val tab = MainTab.entries.firstOrNull { it.route == route }
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val unread by container.state.unread.collectAsState()
    var pendingFriendRequests by remember { mutableStateOf(0) }
    var scrollToTopRequest by remember { mutableStateOf(0) }

    val chatUnreadCount = unread.values.sumOf { maxOf(it.unread, it.mentions) }

    LaunchedEffect(signedIn, route) {
        if (!signedIn) {
            pendingFriendRequests = 0
        } else if (tab != null || route == Routes.NOTIFICATIONS || route == Routes.FRIENDS) {
            runCatching { container.notifications.incomingFriendRequests().size }
                .onSuccess { pendingFriendRequests = it }
        }
    }

    LaunchedEffect(startAtReset) {
        if (startAtReset) navController.navigate(Routes.RESET)
    }

    // كل تبويب يستفيد من ملف المستخدم نفسه؛ إذا كان الحساب مسجّلاً لكن الملف
    // لم يكتمل تحميله، نعيد جلبه عند الانتقال بدلاً من إرسال المستخدم للدخول.
    LaunchedEffect(signedIn, route) {
        if (!signedIn || route == null || container.state.currentProfile != null) return@LaunchedEffect
        scope.launch(Dispatchers.IO) {
            try {
                container.profile.ensureProfileLoaded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // الشاشات تعرض حالة تحميل/إعادة محاولة؛ فشل الشبكة لا يعني انتهاء الجلسة.
            }
        }
    }

    /**
     * الوجهة التي أرادها المستخدم قبل أن نوقفه عند شاشة الدخول — نعود إليها
     * بعد نجاح الدخول مباشرةً، سواء كانت شاتاً أو إحدى واجهات الاشتراك.
     */
    var afterLoginRoute by remember { mutableStateOf<String?>(null) }

    /** هل هذه الوجهة من الشات (قائمة الغرف أو غرفة بعينها)؟ */
    fun isChatRoute(destination: String): Boolean =
        destination == Routes.COMMUNITY || destination.startsWith("chat/")

    /** لا تُعرض واجهات الاشتراك وطلباته إلا بعد تسجيل الدخول. */
    fun isPlanRoute(destination: String): Boolean =
        destination == Routes.PLANS ||
            destination == Routes.ORDERS ||
            destination == Routes.CHECKOUT ||
            destination.startsWith("checkout/")

    /** يطابق المسار الفعلي نمط Compose ذي الوسيط عند الوقوف عليه. */
    fun matchesIntendedRoute(current: String?, intended: String): Boolean =
        current == intended ||
            (current == Routes.CHAT && intended.startsWith("chat/")) ||
            (current == Routes.CHECKOUT && intended.startsWith("checkout/"))

    /**
     * بوابة موحّدة للشاشات المحمية: لا تُركّب واجهة الشات أو الاشتراك للضيف،
     * بل ينتقل إلى الدخول ثم يعود إلى وجهته المقصودة بعد نجاح المصادقة.
     */
    fun requireSignIn(intended: String) {
        afterLoginRoute = intended
        // عند الوصول المباشر إلى وجهة محمية، أخرجها من الرصة أولاً كي لا يعيد
        // زر الرجوع المستخدم إليها فتفتح بوابة الدخول في حلقة متكررة.
        if (matchesIntendedRoute(route, intended)) navController.popBackStack()
        navController.navigate(Routes.LOGIN) { launchSingleTop = true }
    }

    /**
     * **رجوع واحد نظيف**: مهلة 300ms تمنع «الرجوع المزدوج» — ضغطتان سريعتان
     * على السهم (أو إيماءة بعد ضغطة) كانتا تُخرجان المستخدم خطوتين دفعة واحدة
     * فيبدو وكأنّ شاشةً قفزت من تحته (تقطّع). الضغطة الثانية تُتجاهل.
     */
    var lastBackAt by remember { mutableStateOf(0L) }

    val rootView = LocalView.current

    fun back() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastBackAt < 300) return
        lastBackAt = now
        // **لوحة المفاتيح تُخفى قبل الرجوع** (لا بعده): إغلاقها أثناء انتقال
        // الشاشة هو أكثر ما يُقطّع الحركة — خاصّةً عند الرجوع من الشات.
        runCatching {
            rootView.clearFocus()
            val imm = rootView.context
                .getSystemService(android.content.Context.INPUT_METHOD_SERVICE) as? android.view.inputmethod.InputMethodManager
            imm?.hideSoftInputFromWindow(rootView.windowToken, 0)
        }
        navController.popBackStack()
    }

    fun go(route: String) {
        if (!signedIn && (isChatRoute(route) || isPlanRoute(route))) {
            requireSignIn(route)
            return
        }
        navController.navigate(route) { launchSingleTop = true }
    }

    /**
     * عند كل فتح للتطبيق والمستخدم مسجّل:
     *   • نبضة حضور (تُحدّث «آخر ظهور») ⇒ بها يعرف الخادم من غاب فترة فيُذكّره.
     *   • تسجيل توكن الجهاز (أول مرة وبعد إعادة التثبيت) مع لغته ومنطقته الزمنية.
     */
    LaunchedEffect(signedIn) {
        val ctx = container.appContext
        if (!signedIn) {
            // خروج (أو جلسة انتهت): يُلغى توكن الجهاز من الحساب كي لا تصل
            // إشعارات صاحب الحساب السابق إلى من يستعمل الجهاز بعده.
            com.addonstation.mobile.push.PushRegistrar.onSessionChanged(ctx, false)
            return@LaunchedEffect
        }
        withContext(Dispatchers.IO) {
            com.addonstation.mobile.push.PushRegistrar.heartbeat(container)
            com.addonstation.mobile.push.PushRegistrar.register(ctx)
        }
    }

    fun goTab(target: MainTab) {
        if (target == MainTab.Profile && !signedIn) {
            // لا صفحة ضيف: افتح الدخول مباشرةً، واحذف الملف من الرصّة كي يعود
            // زرّ الرجوع إلى الرئيسية بدلاً من الدخول في حلقة تحويل.
            navController.navigate(Routes.LOGIN) {
                popUpTo(Routes.HOME) { inclusive = false }
                launchSingleTop = true
            }
            return
        }
        if (target == MainTab.Community && !signedIn) {
            requireSignIn(target.route)
            return
        }
        navController.navigate(target.route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun selectTab(target: MainTab) {
        if (tab == target && !(target == MainTab.Profile && !signedIn)) {
            scrollToTopRequest += 1
        } else {
            goTab(target)
        }
    }

    /**
     * فتح الوجهة التي طلبها الإشعار — بترتيب آمن:
     *   • غير مسجّل دخول ⇒ نحفظ الوجهة ونُنقله للدخول (وتُفتح بعد الدخول).
     *   • شاشة تحتاج تسجيلاً (الشات) ⇒ نفس المعالجة.
     *   • غير ذلك ⇒ تنقّل مباشر (singleTop حتى لا تتكدّس نسخ).
     */
    fun openPushTarget(raw: String) {
        when {
            raw.startsWith("chat:") -> {
                val id = raw.removePrefix("chat:")
                if (id.isBlank()) go(Routes.NOTIFICATIONS) else go(Routes.chat(id))
            }
            raw.startsWith("item:") -> {
                val id = raw.removePrefix("item:")
                if (id.isBlank()) goTab(MainTab.Store) else go(Routes.item(id))
            }
            raw == "notifications" -> go(Routes.NOTIFICATIONS)
            raw == "plans" -> go(Routes.PLANS)
            raw == "orders" -> go(Routes.ORDERS)
            raw == "creators" -> go(Routes.CREATORS)
            raw == "wallet" -> go(Routes.WALLET)
            raw == "store" -> goTab(MainTab.Store)
            raw == "community" -> goTab(MainTab.Community)
            else -> go(Routes.NOTIFICATIONS)
        }
    }

    // الوجهة القادمة من إشعار: أثر مستقل (قد تصل والتطبيق مفتوح ⇒ onNewIntent).
    // غير مسجّل؟ نحفظها ونُنقله للدخول، ثم تُفتح تلقائياً بعد نجاح الدخول
    // (وإلا ضاع الإشعار: المستخدم يفتحه فيرى شاشة الدخول فقط).
    var pendingPush by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(pushRoute) {
        val target = pushRoute ?: return@LaunchedEffect
        if (!signedIn) {
            pendingPush = target
            requireSignIn(target)
        } else {
            openPushTarget(target)
        }
        onPushConsumed()
    }
    LaunchedEffect(signedIn, pendingPush) {
        val target = pendingPush ?: return@LaunchedEffect
        if (!signedIn) return@LaunchedEffect
        pendingPush = null
        // مهلة قصيرة حتى تكتمل تهيئة الشاشة الأولى (الملف الشخصي والتنقّل)
        kotlinx.coroutines.delay(350)
        openPushTarget(target)
    }



    // إن رجع المستخدم من شاشة الدخول بلا إتمام نُلغي الوجهة المعلّقة، فلا يقفز
    // إلى الشات في دخولٍ تالٍ لا علاقة له به. (الدخول الناجح يُفرغها بنفسه.)
    LaunchedEffect(route) {
        val pending = afterLoginRoute
        if (
            route != null &&
            route != Routes.LOGIN &&
            route != Routes.SIGNUP &&
            pending != null &&
            !matchesIntendedRoute(route, pending)
        ) {
            afterLoginRoute = null
        }
    }

    // سطرٌ أعلى شاشة الدخول يفسّر لماذا فُتحت البوابة المحمية.
    val chatLoginNotice = stringResource(R.string.chat_login_required)
    val accountLoginNotice = stringResource(R.string.msg_sign_in_needed)

    /**
     * بعد نجاح الدخول: إن كنّا أوقفنا المستخدم عند بوابة محمية نعيده إلى
     * وجهته نفسها (الشات أو الاشتراك). وإلا يعود إلى الصفحة الرئيسية.
     *
     * @return صحيحٌ إن أعدناه إلى الوجهة المحمية؛ وإلا فالصفحة الرئيسية.
     */
    fun continueAfterLogin(): Boolean {
        val intended = afterLoginRoute
        afterLoginRoute = null
        if (intended == null) return false
        navController.navigate(intended) {
            popUpTo(Routes.LOGIN) { inclusive = true }
            launchSingleTop = true
        }
        return true
    }

    val density = LocalDensity.current
    val keyboardOpen = WindowInsets.ime.getBottom(density) > 0

    // إذن الإشعارات: مرة واحدة بعد أول دخول (انظر الدالة أدناه)
    NotificationPermissionPrompt(signedIn)

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {

                composable(Routes.HOME) {
                    HomeScreen(
                        scrollToTopRequest = scrollToTopRequest,
                        actions = HomeActions(
                            openStore = { goTab(MainTab.Store) },
                            openFavorites = { go(Routes.FAVORITES) },
                            openDownloads = { go(Routes.DOWNLOADS) },
                            openPlans = { go(Routes.PLANS) },
                            openSupport = { goTab(MainTab.Community) },
                            openCreators = { go(Routes.CREATORS) },
                            openCreatorApply = { go(Routes.CREATOR_APPLY) },
                            openStudio = { go(Routes.CREATOR_STUDIO) },
                            openExclusives = { go(Routes.EXCLUSIVES) },
                            openProfile = { goTab(MainTab.Profile) },
                            openLogin = { go(Routes.LOGIN) },
                            openSignup = { go(Routes.SIGNUP) },
                            openWallet = { go(Routes.WALLET) },
                            openNotifications = { go(Routes.NOTIFICATIONS) },
                            openItem = { item -> go(Routes.item(item.id)) },
                            openCreatorProfile = { id -> go(Routes.creatorProfile(id)) },
                        ),
                    )
                }

                composable(Routes.STORE) {
                    StoreScreen(
                        scrollToTopRequest = scrollToTopRequest,
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                        onOpenWallet = { go(Routes.WALLET) },
                    )
                }

                composable(Routes.COMMUNITY) {
                    // الشات لا يُفتح لغير مسجّل: رابطٌ خارجيّ أو استعادةُ حالة
                    // تُدخله هنا تُنقله إلى الدخول (والأزرار تمنعه أصلاً).
                    SignInGate(
                        signedIn = signedIn,
                        intended = Routes.COMMUNITY,
                        onRequireSignIn = { intended -> requireSignIn(intended) },
                    ) {
                        com.addonstation.mobile.ui.community.CommunityGate(
                            scrollToTopRequest = scrollToTopRequest,
                            onOpenRoom = { channel: ChatChannel -> go(Routes.chat(channel.id)) },
                            // ترس لوحة المستخدم في الشات → تعديل الملف الشخصي
                            onOpenEditProfile = { go(Routes.EDIT_PROFILE) },
                            onOpenWallet = { go(Routes.WALLET) },
                            onLogin = { go(Routes.LOGIN) },
                            onDeclineToHome = { goTab(MainTab.Home) },
                        )
                    }
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        scrollToTopRequest = scrollToTopRequest,
                        onLogin = { go(Routes.LOGIN) },
                        onNotifications = { go(Routes.NOTIFICATIONS) },
                        onNotificationSettings = { go(Routes.NOTIF_SETTINGS) },
                        onOpenProfile = { goTab(MainTab.Profile) },
                        onOpenWallet = { go(Routes.WALLET) },
                        onSignedOut = { goTab(MainTab.Home) },
                        onOpenPlans = { go(Routes.PLANS) },
                        onOpenAdmin = { go(Routes.ADMIN) },
                        onOpenAbout = { go(Routes.APP_ABOUT) },
                        onOpenPrivacy = { go(Routes.PRIVACY) },
                        onOpenTerms = { go(Routes.TERMS) },
                        onOpenConsoleLink = { go(Routes.CONSOLE_LINK) },
                        onOpenLinkedConsoles = { go(Routes.LINKED_CONSOLES) },
                        onBack = { back() },
                    )
                }

                composable(Routes.CONSOLE_LINK) {
                    ConsoleLinkScreen(
                        onBack = { back() },
                        onLogin = { go(Routes.LOGIN) },
                        onOpenDevices = { go(Routes.LINKED_CONSOLES) },
                    )
                }
                composable(Routes.LINKED_CONSOLES) {
                    LinkedConsolesScreen(
                        onBack = { back() },
                        onLogin = { go(Routes.LOGIN) },
                        onLinkAnother = { go(Routes.CONSOLE_LINK) },
                    )
                }

                composable(Routes.APP_ABOUT) {
                    com.addonstation.mobile.ui.settings.AboutAppScreen(
                        onBack = { back() },
                        onOpenPrivacy = { go(Routes.PRIVACY) },
                        onOpenTerms = { go(Routes.TERMS) },
                    )
                }
                composable(Routes.PRIVACY) {
                    com.addonstation.mobile.ui.settings.LegalDocumentScreen(
                        title = stringResource(R.string.set_privacy),
                        document = stringResource(R.string.set_privacy_text),
                        onBack = { back() },
                    )
                }
                composable(Routes.TERMS) {
                    com.addonstation.mobile.ui.settings.LegalDocumentScreen(
                        title = stringResource(R.string.set_terms),
                        document = stringResource(R.string.set_terms_text),
                        onBack = { back() },
                    )
                }

                composable(Routes.PROFILE) {
                    // الشاشة A من وثيقة شاشات الملف (AddonStation_profiles):
                    // بانر · أفاتار+تعديل · هوية · محفظة · إحصاءات · مستوى · نبذة
                    // · شبكة 2×2 · خروج. (الإعدادات تبويب مستقل — لا تُعرض هنا.)
                    ProfileScreen(
                        scrollToTopRequest = scrollToTopRequest,
                        onSignedOut = { goTab(MainTab.Home) },
                        onBack = { back() },
                        onOpenEdit = { go(Routes.EDIT_PROFILE) },
                        onOpenAdmin = { go(Routes.ADMIN) },
                        onOpenCreatorStudio = { go(Routes.CREATOR_STUDIO) },
                        onOpenSettings = { goTab(MainTab.Settings) },
                        onOpenWallet = { go(Routes.WALLET) },
                        onOpenFavorites = { go(Routes.FAVORITES) },
                        onOpenDownloads = { go(Routes.DOWNLOADS) },
                        onOpenOrders = { go(Routes.ORDERS) },
                        onOpenPlans = { go(Routes.PLANS) },
                        onOpenMyContent = { go(Routes.MY_CONTENT) },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                        onViewAsOthers = { go(Routes.PROFILE_AS_OTHERS) },
                    )
                }

                // المصادقة
                composable(Routes.LOGIN) {
                    com.addonstation.mobile.ui.auth.LoginScreen(
                        onBack = { back() },
                        // بعد الدخول ⟵ الصفحة الرئيسية مباشرةً، إلا إذا أوقفته
                        // بوابة محمية؛ عندها نعيده إلى الوجهة التي طلبها.
                        onSuccess = { if (!continueAfterLogin()) goTab(MainTab.Home) },
                        onOpenSignup = { go(Routes.SIGNUP) },
                        notice = afterLoginRoute?.let { intended ->
                            if (isChatRoute(intended)) chatLoginNotice else accountLoginNotice
                        },
                    )
                }
                composable(Routes.SIGNUP) {
                    com.addonstation.mobile.ui.auth.SignUpScreen(
                        onBack = { back() },
                        // بعد إنشاء الحساب ⟵ الصفحة الرئيسية مباشرةً (طلب المالك):
                        // `AuthRepository.signUp` يدخل الحساب تلقائياً أولاً.
                        onSuccess = { if (!continueAfterLogin()) goTab(MainTab.Home) },
                        onOpenLogin = {
                            navController.navigate(Routes.LOGIN) { popUpTo(Routes.SIGNUP) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.RESET) {
                    com.addonstation.mobile.ui.auth.ResetPasswordScreen(
                        onBack = { back() },
                        onDone = { go(Routes.LOGIN) },
                    )
                }

                // المحتوى
                composable(
                    route = Routes.ITEM,
                    arguments = listOf(navArgument("itemId") { type = NavType.StringType }),
                ) { entry ->
                    ContentDetailScreen(
                        itemId = entry.arguments?.getString("itemId").orEmpty(),
                        onBack = { back() },
                        onOpenProfile = { id -> go(Routes.userProfile(id)) },
                        onUpgrade = { go(Routes.PLANS) },
                        onLogin = { go(Routes.LOGIN) },
                        onOpenSimilar = { item -> go(Routes.item(item.id)) },
                        onOpenCreatorProfile = { id -> go(Routes.creatorProfile(id)) },
                    )
                }
                composable(Routes.FAVORITES) {
                    com.addonstation.mobile.ui.common.ContentListScreen(
                        title = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.home_qa_favorites
                        ),
                        emptyText = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.profile_favorites_empty
                        ),
                        loader = { container.content.favorites() },
                        onBack = { back() },
                        onOpen = { item -> go(Routes.item(item.id)) },
                    )
                }
                composable(Routes.DOWNLOADS) {
                    com.addonstation.mobile.ui.common.ContentListScreen(
                        title = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.home_qa_downloads
                        ),
                        emptyText = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.profile_my_content_empty
                        ),
                        loader = { container.content.downloads() },
                        onBack = { back() },
                        onOpen = { item -> go(Routes.item(item.id)) },
                    )
                }
                composable(Routes.EXCLUSIVES) {
                    com.addonstation.mobile.ui.common.ContentListScreen(
                        title = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.home_exclusives
                        ),
                        emptyText = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.store_empty
                        ),
                        loader = { container.content.exclusives() },
                        onBack = { back() },
                        onOpen = { item -> go(Routes.item(item.id)) },
                    )
                }

                // الشات
                composable(
                    route = Routes.CHAT,
                    arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
                ) { entry ->
                    val channelId = entry.arguments?.getString("channelId").orEmpty()
                    // الغرفة نفسها محجوبة عن غير المسجّلين: رابط إشعار أو رجوع
                    // في التنقّل لا يفتحها قبل الدخول، وبعده يعود إلى الغرفة نفسها.
                    SignInGate(
                        signedIn = signedIn,
                        intended = Routes.chat(channelId),
                        onRequireSignIn = { intended -> requireSignIn(intended) },
                    ) {
                        var channel by remember { mutableStateOf<ChatChannel?>(null) }
                        LaunchedEffect(channelId) {
                            channel = container.chat.channels().firstOrNull { it.id == channelId }
                                ?: ChatChannel(id = channelId, name = channelId)
                        }
                        val current = channel
                        if (current == null) {
                            ContentColumn { com.addonstation.mobile.ui.components.LoadingBox() }
                        } else {
                            ChatScreen(
                                channel = current,
                                onBack = { back() },
                                onOpenSettings = { goTab(MainTab.Settings) },
                                onOpenUser = { profile -> go(Routes.userProfile(profile.id)) },
                                onLogin = { go(Routes.LOGIN) },
                            )
                        }
                    }
                }

                // الحساب والمحفظة والإشعارات
                composable(Routes.WALLET) {
                    com.addonstation.mobile.ui.account.AcWalletScreen(
                        onBack = { back() },
                        onOpenHistory = { go(Routes.AC_HISTORY) },
                    )
                }
                composable(Routes.AC_HISTORY) {
                    com.addonstation.mobile.ui.account.AcTransactionHistoryScreen(onBack = { back() })
                }
                composable(Routes.NOTIFICATIONS) {
                    com.addonstation.mobile.ui.account.NotificationsScreen(
                        onBack = { back() },
                        onOpenChannel = { channelId, _ -> go(Routes.chat(channelId)) },
                        onOpenProfile = { id -> go(Routes.userProfile(id)) },
                        // ترس التخصيص داخل شاشة الإشعارات نفسها (أقرب مكان للمستخدم)
                        onOpenSettings = { go(Routes.NOTIF_SETTINGS) },
                    )
                }
                composable(Routes.NOTIF_SETTINGS) {
                    com.addonstation.mobile.ui.settings.NotificationSettingsScreen(
                        onBack = { back() },
                    )
                }
                composable(Routes.FRIENDS) {
                    com.addonstation.mobile.ui.account.FriendsScreen(
                        onBack = { back() },
                        onOpenProfile = { id -> go(Routes.userProfile(id)) },
                    )
                }

                // الخطط والدفع — واجهات حساب محمية؛ لا تُركّب للضيف حتى عبر
                // استعادة حالة التنقّل أو الوصول المباشر إلى المسار.
                composable(Routes.PLANS) {
                    SignInGate(
                        signedIn = signedIn,
                        intended = Routes.PLANS,
                        onRequireSignIn = { intended -> requireSignIn(intended) },
                    ) {
                        com.addonstation.mobile.ui.plans.PlansScreen(
                            onBack = { back() },
                            onCheckout = { plan -> go(Routes.checkout(plan.id)) },
                            onOpenOrders = { go(Routes.ORDERS) },
                        )
                    }
                }
                composable(
                    route = Routes.CHECKOUT,
                    arguments = listOf(navArgument("planId") { type = NavType.StringType }),
                ) { entry ->
                    val planId = entry.arguments?.getString("planId").orEmpty()
                    SignInGate(
                        signedIn = signedIn,
                        intended = Routes.checkout(planId),
                        onRequireSignIn = { intended -> requireSignIn(intended) },
                    ) {
                        com.addonstation.mobile.ui.plans.CheckoutScreen(
                            planId = planId,
                            onBack = { back() },
                            onOpenOrders = { go(Routes.ORDERS) },
                            onLogin = { go(Routes.LOGIN) },
                            onOpenPlans = { go(Routes.PLANS) },
                        )
                    }
                }
                composable(Routes.ORDERS) {
                    SignInGate(
                        signedIn = signedIn,
                        intended = Routes.ORDERS,
                        onRequireSignIn = { intended -> requireSignIn(intended) },
                    ) {
                        com.addonstation.mobile.ui.plans.MyOrdersScreen(onBack = { back() })
                    }
                }

                // المبدعون
                composable(Routes.CREATORS) {
                    com.addonstation.mobile.ui.creators.CreatorsScreen(
                        onBack = { back() },
                        onOpenCreator = { id -> go(Routes.creatorProfile(id)) },
                    )
                }
                composable(
                    route = Routes.CREATOR_PROFILE,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.creators.CreatorProfileScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        onBack = { back() },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                        onOpenProfile = { id -> go(Routes.userProfile(id)) },
                    )
                }
                composable(Routes.CREATOR_APPLY) {
                    com.addonstation.mobile.ui.creators.CreatorApplicationScreen(
                        onBack = { back() },
                        onSubmitted = { },
                    )
                }
                composable(Routes.CREATOR_STUDIO) {
                    com.addonstation.mobile.ui.creators.CreatorStudioScreen(
                        onBack = { back() },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                    )
                }

                // الملف الشخصي
                composable(Routes.EDIT_PROFILE) {
                    com.addonstation.mobile.ui.profile.EditProfileScreen(
                        onBack = { back() },
                        onSaved = {
                            back()
                            scope.launch { mainSnackbarHostState.showSnackbar(profileSavedMessage) }
                        },
                        onOpenPlans = { go(Routes.PLANS) },
                    )
                }
                // الشاشة C: ملفي كما يراه الآخرون (بلا محفظة حقيقية · بلا أزرار)
                composable(Routes.PROFILE_AS_OTHERS) {
                    com.addonstation.mobile.ui.profile.ProfileAsOthersScreen(
                        onBack = { back() },
                        onOpenEdit = { go(Routes.EDIT_PROFILE) },
                    )
                }
                // «محتواي»: بلاطة الشبكة 2×2 في ملفي — كل ما نشرتُه
                composable(Routes.MY_CONTENT) {
                    com.addonstation.mobile.ui.common.ContentListScreen(
                        title = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.profile_stats_content
                        ),
                        emptyText = androidx.compose.ui.res.stringResource(
                            com.addonstation.mobile.R.string.profile_my_content_empty
                        ),
                        loader = {
                            container.content.byCreator(
                                container.supabase.session()?.userId.orEmpty()
                            )
                        },
                        onBack = { back() },
                        onOpen = { item -> go(Routes.item(item.id)) },
                    )
                }
                composable(
                    route = Routes.USER_PROFILE,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    // الشاشة B من وثيقة شاشات الملف: هوية · صداقة · إحصاءات ·
                    // مستوى · نبذة · سلامة (كتم/حظر/إبلاغ) · إشراف للأدمن.
                    // بلا محفظة ولا طلبات ولا إعدادات (نصّ الوثيقة §4).
                    com.addonstation.mobile.ui.profile.UserProfileScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        onBack = { back() },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                        // ملفي أنا: الزرّ الوحيد هو «تعديل الملف الشخصي»
                        onEditProfile = { go(Routes.EDIT_PROFILE) },
                        // الحظر (سياسة UGC): يُنفَّذ على الخادم، والواجهة تُعيد
                        // قراءة حالتها في الشاشة نفسها بلا إعادة تنقّل.
                        onBlock = { profile -> scope.launch { container.chat.blockUser(profile.id) } },
                        onUnblock = { profile -> scope.launch { container.chat.unblockUser(profile.id) } },
                        // «حذف رسالة» في بطاقة الإشراف تُفتح في شاشة إشراف الشات
                        onOpenAdminTools = { go(Routes.ADMIN_CHAT) },
                        // كتم الأعضاء ميزة Legendary ⇒ صفحة اشتراك Legendary نفسها
                        onOpenPlans = { go(Routes.checkout("legendary")) },
                    )
                }

                // لوحة الإدارة — كل شاشاتها خلف بوابة AdminGate (حساب الأدمن + تحقق
                // الخادم + كلمة المرور)، فلا تُفتح برابط أو رجوع في التنقّل.
                composable(Routes.ADMIN) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { goTab(MainTab.Home) }) {
                    com.addonstation.mobile.ui.admin.AdminPanelScreen(
                        onBackToApp = { goTab(MainTab.Home) },
                        onOpenChat = { go(Routes.ADMIN_CHAT) },
                        onOpenContent = { go(Routes.ADMIN_CONTENT) },
                        onOpenBulkImport = { go(Routes.ADMIN_BULK_IMPORT) },
                        onEditContent = { item -> go(Routes.adminContentEdit(item.id)) },
                        onOpenPayments = { },
                        onOpenModeration = { },
                        onOpenAssets = { go(Routes.ADMIN_ASSETS) },
                        onOpenCreatorApps = { go(Routes.ADMIN_CREATORS) },
                        onOpenTips = { go(Routes.ADMIN_CREATOR_TIPS) },
                        onOpenAc = { userId ->
                            go(if (userId.isNullOrBlank()) Routes.ADMIN_AC else Routes.adminAcUser(userId))
                        },
                        onOpenAudit = { go(Routes.ADMIN_AUDIT) },
                        onOpenSettings = { go(Routes.ADMIN_SETTINGS) },
                    )
                    }
                }
                composable(Routes.ADMIN_CONTENT) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = 3,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminContentEditorScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_BULK_IMPORT) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = 3,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminBulkContentImportScreen(onBack = { back() })
                    }
                }
                composable(
                    Routes.ADMIN_CONTENT_EDIT,
                    arguments = listOf(navArgument("contentId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = 3,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminContentEditorScreen(
                            itemId = entry.arguments?.getString("contentId"),
                            onBack = { back() },
                        )
                    }
                }
                composable(Routes.ADMIN_ASSETS) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = null,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminAssetsScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_CREATORS) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = null,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminCreatorReviewScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_CREATOR_TIPS) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = null,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminCreatorReviewScreen(onBack = { back() }, initialTab = 2)
                    }
                }
                composable(Routes.ADMIN_AC) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = null,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminAcScreen(onBack = { back() })
                    }
                }
                // نفس الشاشة لكن مع مستخدم محدَّد (فتحها زر «+AC» من بطاقة المستخدم)
                composable(
                    Routes.ADMIN_AC_USER,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = 1,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminAcScreen(
                            onBack = { back() },
                            preselectUserId = entry.arguments?.getString("userId"),
                        )
                    }
                }
                composable(Routes.ADMIN_SETTINGS) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = null,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminSettingsScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_AUDIT) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = null,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminAuditScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_CHAT) {
                    com.addonstation.mobile.ui.admin.AdminGate(
                        onExit = { back() },
                        showAdminBottomBar = true,
                        selectedAdminTab = null,
                        onSelectAdminTab = { index ->
                            com.addonstation.mobile.ui.admin.AdminPanelTabRequest.requestTab(index)
                            if (navController.previousBackStackEntry?.destination?.route == Routes.ADMIN) back()
                            else go(Routes.ADMIN)
                        },
                    ) {
                        com.addonstation.mobile.ui.admin.AdminChatScreen(onBack = { back() })
                    }
                }
            }
            androidx.compose.material3.SnackbarHost(
                hostState = mainSnackbarHostState,
                modifier = Modifier
                    .align(androidx.compose.ui.Alignment.BottomCenter)
                    .fillMaxWidth(),
            )
        }

        // تبويب Settings الرئيسي ضمن الشريط؛ الإعدادات الفرعية ومسارات التفاصيل لا تطابق تبويباً.
        if (tab != null && !keyboardOpen) {
            BottomNavBar(
                current = tab,
                onSelect = { target -> selectTab(target) },
                chatUnreadCount = chatUnreadCount,
                hasPendingFriendRequests = pendingFriendRequests > 0,
            )
        }
    }
}

/**
 * بوابة الوجهات المحمية: لا تُركّب واجهة الشات أو الاشتراك قبل تسجيل الدخول.
 * وُضعت في التنقّل نفسه لتغطي الضغطة العادية، والرابط المباشر، والرجوع،
 * واستعادة حالة التنقّل بعد قتل العملية بلا إظهار المحتوى للضيف ولو للحظة.
 */
@Composable
private fun SignInGate(
    signedIn: Boolean,
    intended: String,
    onRequireSignIn: (String) -> Unit,
    content: @Composable () -> Unit,
) {
    if (signedIn) {
        content()
        return
    }
    // النقل يجري مرةً واحدة لكل وجهة: لا نُعيده إن تغيّرت الحالة أثناء الرحلة
    LaunchedEffect(signedIn, intended) { onRequireSignIn(intended) }
    Box(
        Modifier.fillMaxSize(),
        contentAlignment = androidx.compose.ui.Alignment.Center,
    ) {
        com.addonstation.mobile.ui.components.LoadingBox()
    }
}


/**
 * طلب إذن الإشعارات مرة واحدة بعد أول دخول (أندرويد 13+).
 *
 * لماذا هنا؟ لأن النظام لا يسأل من نفسه، وبلا هذا الإذن لا يظهر أي إشعار
 * حقيقي مهما كانت المنظومة الخلفية سليمة — يسقط الإشعار عند المتصفّح الأخير
 * ولا يشعر المستخدم بشيء. لذلك:
 *   • نسأل **مرة واحدة**، بعد ثانية ونصف من الدخول (لا في وجهه أول لحظة).
 *   • نقول الفائدة بصراحة (رسائل الشات والإضافات الجديدة والتطبيق مغلق).
 *   • ولا نُكرّر أبداً: من رفض يجد زر «اسمح بالإشعارات» في إعدادات الإشعارات.
 *   • وعند الموافقة نُسجّل الجهاز فوراً فلا ينتظر أول تشغيل قادم.
 *
 * على أندرويد 12 وأقدم لا يوجد إذن وقت تشغيل ⇒ الدالة لا تفعل شيئاً.
 */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun NotificationPermissionPrompt(signedIn: Boolean) {
    // Android 12 وما قبله لا يملك إذن الإشعارات وقت التشغيل، فلا ننشئ launcher أصلاً.
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var visible by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        visible = false
        if (granted) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    com.addonstation.mobile.push.PushRegistrar
                        .register(context.applicationContext)
                }
            }
        }
    }

    LaunchedEffect(signedIn) {
        if (!signedIn) return@LaunchedEffect
        if (com.addonstation.mobile.push.PushNotifications.permissionGranted(context)) return@LaunchedEffect
        if (AppPrefs.notificationAsked(context)) return@LaunchedEffect
        delay(1500)
        if (com.addonstation.mobile.push.PushNotifications.permissionGranted(context)) return@LaunchedEffect
        AppPrefs.markNotificationAsked(context)   // сохраняем прежнее поведение: один запрос
        visible = true
    }

    if (visible) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { visible = false },
            sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = AppColors.AuthSurface,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(72.dp).clip(CircleShape).background(AppColors.AuthBlue.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.NotificationsActive,
                        contentDescription = null,
                        tint = AppColors.AuthBlueLight,
                        modifier = Modifier.size(34.dp),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(R.string.notif_prompt_title),
                    style = AppText.sectionTitle.copy(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppColors.AuthText,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.notif_prompt_text),
                    style = AppText.bodySmall.copy(fontSize = 14.sp, lineHeight = 22.sp),
                    color = AppColors.AuthMuted,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    NotificationCategoryChip(
                        icon = Icons.Filled.Chat,
                        label = stringResource(R.string.notif_cat_chat),
                        modifier = Modifier.weight(1f),
                    )
                    NotificationCategoryChip(
                        icon = Icons.Filled.AlternateEmail,
                        label = stringResource(R.string.notif_mention),
                        modifier = Modifier.weight(1f),
                    )
                    NotificationCategoryChip(
                        icon = Icons.Filled.NewReleases,
                        label = stringResource(R.string.notif_cat_content),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(18.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.AuthBlue)
                        .clickable {
                            visible = false
                            runCatching { launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS) }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.notif_prompt_allow),
                        style = AppText.button.copy(fontSize = 15.sp),
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { visible = false },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.notif_prompt_later), style = AppText.body, color = AppColors.AuthMuted)
                }
            }
        }
    }
}

@Composable
private fun NotificationCategoryChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(AppColors.AuthSurfaceHigh)
            .border(1.dp, AppColors.AuthBorder, CircleShape)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(18.dp))
        Spacer(Modifier.size(5.dp))
        Text(
            label,
            style = AppText.small.copy(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            color = AppColors.AuthChipLabel,
            maxLines = 1,
            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        )
    }
}
