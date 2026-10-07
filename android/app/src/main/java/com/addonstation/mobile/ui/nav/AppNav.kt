package com.addonstation.mobile.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsTopHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.Modifier
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
import com.addonstation.mobile.ui.profile.ProfileScreen
import com.addonstation.mobile.ui.common.SetupScreen
import android.app.Activity
import android.content.ContextWrapper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** مسارات التنقل كلها في مكان واحد. */
object Routes {
    const val HOME = "home"
    const val STORE = "store"
    const val COMMUNITY = "community"
    const val SETTINGS = "settings"
    const val PROFILE = "profile"

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
    const val NOTIFICATIONS = "notifications"
    /** إعدادات الإشعارات (الأقسام وساعات الهدوء ووتيرة التذكير). */
    const val NOTIF_SETTINGS = "notification-settings"
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
    const val USER_PROFILE = "user/{userId}"
    fun userProfile(id: String) = "user/$id"
    const val ADMIN = "admin"
    const val ADMIN_ASSETS = "admin-assets"
    const val ADMIN_CREATORS = "admin-creators"
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

    // استعادة الجلسة عند الإقلاع
    LaunchedEffect(Unit) {
        // كل عمل الإقلاع (شبكة + فك ترميز JSON) على خيط الإدخال/الإخراج،
        // حتى لا يتجمّد الخيط الرئيسي أثناء شاشة الترحيب (كان سبب «اللاق»).
        runCatching {
            withContext(Dispatchers.IO) {
                if (container.auth.isSignedIn()) {
                    container.auth.ensureFreshSession()
                    container.state.refreshAll()
                    container.assets.all()
                    // غرفة «خدمات النقدية» (قراءة فقط للأدمن): نضمن وجودها
                    // **عند فتح التطبيق** لا عند فتح شاشة الغرف فقط، فتظهر
                    // لكل المستخدمين من أول تشغيل. الخادم يرفض الطلب لغير الأدمن.
                    container.chat.ensureCashServicesRoom(
                        container.state.profile.value?.role == "admin",
                    )  // الخادم يمنعها لغير الأدمن، فنستدعيها دائماً
                }
                container.plans.loadPlans()
                container.plans.loadPaymentMethods()
            }
        }
        configured = container.isConfigured
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
                    when {
                        !splashDone -> SplashScreen(onFinished = { splashDone = true })
                        !configured -> SetupScreen(onDone = { configured = container.isConfigured })
                        else -> MainNavigation(
                            startAtReset = deepLinkSession,
                            pushRoute = pushRoute,
                            onPushConsumed = onPushConsumed,
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
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val tab = MainTab.entries.firstOrNull { it.route == route }
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val wallet by container.state.wallet.collectAsState()

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
     * بعد نجاح الدخول مباشرةً (قائمة الغرف، أو الغرفة نفسها إن جاء من إشعار).
     */
    var afterLoginRoute by remember { mutableStateOf<String?>(null) }

    /** هل هذه الوجهة من الشات (قائمة الغرف أو غرفة بعينها)؟ */
    fun isChatRoute(destination: String): Boolean =
        destination == Routes.COMMUNITY || destination.startsWith("chat/")

    /**
     * **الشات كلّه خلف تسجيل الدخول** (طلب المالك): لا تُفتح قائمة الغرف ولا
     * غرفةٌ منها لغير مسجّل — يُنقل إلى شاشة الدخول، وبعد نجاحه يعود إلى حيث
     * أراد تلقائياً.
     *
     * إخراج وجهة الشات من الرصة خطوة جوهريّة: لولاها صار زرّ الرجوع من شاشة
     * الدخول يعيده إلى الشات، فيُرسله إلى الدخول من جديد … حلقة لا تنتهي.
     */
    fun requireSignIn(intended: String) {
        afterLoginRoute = intended
        // إن كنّا واقفين على وجهة الشات أخرجناها من الرصة أولاً: وإلا صار زرّ
        // الرجوع من شاشة الدخول يعيده إلى الشات، فيُرسله إلى الدخول من جديد
        // … حلقة لا تنتهي. (الغرفة تُبلّغ عن نمطها chat/{channelId} لا عن
        // معرّفها، لذا نفحص العائلة بالنمط لا بالمعرّف.)
        val standingOnChat = route == Routes.COMMUNITY || route?.startsWith("chat/") == true
        if (standingOnChat) navController.popBackStack()
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
        if (isChatRoute(route) && !signedIn) {
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
        if (!signedIn) return@LaunchedEffect
        val ctx = container.appContext
        withContext(Dispatchers.IO) {
            com.addonstation.mobile.push.PushRegistrar.heartbeat(container)
            com.addonstation.mobile.push.PushRegistrar.register(ctx)
        }
    }

    fun goTab(target: MainTab) {
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
        if (route != null && route != Routes.LOGIN && route != Routes.SIGNUP) {
            afterLoginRoute = null
        }
    }

    // سطرٌ أعلى شاشة الدخول يفسّر لماذا فُتحت: «الشات للمسجّلين فقط»
    val chatLoginNotice = stringResource(R.string.chat_login_required)

    /**
     * بعد نجاح الدخول: إن كنّا أوقفنا المستخدم عند بوابة الشات نعيده إلى
     * وجهته نفسها (قائمة الغرف، أو الغرفة التي فتحها من إشعار) ونُخبر النداء.
     * وإلا لا نفعل شيئاً فيعود إلى الصفحة الرئيسية — السلوك المعتاد.
     *
     * @return صحيحٌ إن أعدناه إلى الشات؛ وإلا فالصفحة الرئيسية.
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

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            NavHost(navController = navController, startDestination = Routes.HOME) {

                composable(Routes.HOME) {
                    HomeScreen(
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
                            openItem = { item -> go(Routes.item(item.id)) },
                            openCreatorProfile = { id -> go(Routes.creatorProfile(id)) },
                        ),
                    )
                }

                composable(Routes.STORE) {
                    StoreScreen(
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
                        onLogin = { go(Routes.LOGIN) },
                        onNotifications = { go(Routes.NOTIFICATIONS) },
                        onNotificationSettings = { go(Routes.NOTIF_SETTINGS) },
                        onOpenProfile = { goTab(MainTab.Profile) },
                        onOpenWallet = { go(Routes.WALLET) },
                        onSignedOut = { goTab(MainTab.Home) },
                    )
                }

                composable(Routes.PROFILE) {
                    ProfileScreen(
                        onLogin = { go(Routes.LOGIN) },
                        onOpenSettings = { goTab(MainTab.Settings) },
                        onOpenPlans = { go(Routes.PLANS) },
                        onOpenEdit = { go(Routes.EDIT_PROFILE) },
                        onOpenAdmin = { go(Routes.ADMIN) },
                        onOpenStudio = { go(Routes.CREATOR_STUDIO) },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                        onOpenWallet = { go(Routes.WALLET) },
                    )
                }

                // المصادقة
                composable(Routes.LOGIN) {
                    com.addonstation.mobile.ui.auth.LoginScreen(
                        onBack = { back() },
                        // بعد الدخول ⟵ الصفحة الرئيسية مباشرةً (طلب المالك) …
                        // إلّا إن كان قد أُوقف عند الدخول وهو داخلٌ إلى الشات:
                        // فنردّه إلى الشات الذي أراده (قائمة الغرف أو الغرفة).
                        onSuccess = { if (!continueAfterLogin()) goTab(MainTab.Home) },
                        onOpenSignup = { go(Routes.SIGNUP) },
                        notice = if (afterLoginRoute != null) chatLoginNotice else null,
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
                        onOpenItem = { id -> go(Routes.item(id)) },
                    )
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

                // الخطط والدفع
                composable(Routes.PLANS) {
                    com.addonstation.mobile.ui.plans.PlansScreen(
                        onBack = { back() },
                        onCheckout = { plan -> go(Routes.checkout(plan.id)) },
                        onOpenOrders = { go(Routes.ORDERS) },
                    )
                }
                composable(
                    route = Routes.CHECKOUT,
                    arguments = listOf(navArgument("planId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.plans.CheckoutScreen(
                        planId = entry.arguments?.getString("planId").orEmpty(),
                        onBack = { back() },
                        onOpenOrders = { go(Routes.ORDERS) },
                        onLogin = { go(Routes.LOGIN) },
                        onOpenPlans = { go(Routes.PLANS) },
                    )
                }
                composable(Routes.ORDERS) {
                    com.addonstation.mobile.ui.plans.MyOrdersScreen(onBack = { back() })
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
                        onSaved = { back() },
                    )
                }
                composable(
                    route = Routes.USER_PROFILE,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.profile.UserProfileScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        onBack = { back() },
                        onMessage = { goTab(MainTab.Community) },
                        onAddFriend = { profile ->
                            scope.launch { container.profile.sendFriendRequest(profile.id) }
                        },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                        // الحظر (سياسة UGC): يُنفَّذ على الخادم، والواجهة تُحدّث
                        // حالتها في الشاشة نفسها (blocked) بلا إعادة تنقّل.
                        onBlock = { profile -> scope.launch { container.chat.blockUser(profile.id) } },
                        onUnblock = { profile -> scope.launch { container.chat.unblockUser(profile.id) } },
                        // ملفي أنا: الزرّ الوحيد هو «تعديل الملف الشخصي»
                        onEditProfile = { go(Routes.EDIT_PROFILE) },
                    )
                }

                // لوحة الإدارة — كل شاشاتها خلف بوابة AdminGate (حساب الأدمن + تحقق
                // الخادم + كلمة المرور)، فلا تُفتح برابط أو رجوع في التنقّل.
                composable(Routes.ADMIN) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { goTab(MainTab.Home) }) {
                    com.addonstation.mobile.ui.admin.AdminPanelScreen(
                        onBackToApp = { goTab(MainTab.Home) },
                        onOpenChat = { go(Routes.ADMIN_CHAT) },
                        onOpenContent = { go(Routes.ADMIN_CREATORS) },
                        onOpenPayments = { },
                        onOpenModeration = { },
                        onOpenAssets = { go(Routes.ADMIN_ASSETS) },
                        onOpenCreatorApps = { go(Routes.ADMIN_CREATORS) },
                        onOpenTips = { go(Routes.ADMIN_CREATORS) },
                        onOpenAc = { userId ->
                            go(if (userId.isNullOrBlank()) Routes.ADMIN_AC else Routes.adminAcUser(userId))
                        },
                        onOpenAudit = { go(Routes.ADMIN_AUDIT) },
                        onOpenSettings = { go(Routes.ADMIN_SETTINGS) },
                    )
                    }
                }
                composable(Routes.ADMIN_ASSETS) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { back() }) {
                        com.addonstation.mobile.ui.admin.AdminAssetsScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_CREATORS) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { back() }) {
                        com.addonstation.mobile.ui.admin.AdminCreatorReviewScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_AC) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { back() }) {
                        com.addonstation.mobile.ui.admin.AdminAcScreen(onBack = { back() })
                    }
                }
                // نفس الشاشة لكن مع مستخدم محدَّد (فتحها زر «+AC» من بطاقة المستخدم)
                composable(
                    Routes.ADMIN_AC_USER,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { back() }) {
                        com.addonstation.mobile.ui.admin.AdminAcScreen(
                            onBack = { back() },
                            preselectUserId = entry.arguments?.getString("userId"),
                        )
                    }
                }
                composable(Routes.ADMIN_SETTINGS) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { back() }) {
                        com.addonstation.mobile.ui.admin.AdminSettingsScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_AUDIT) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { back() }) {
                        com.addonstation.mobile.ui.admin.AdminAuditScreen(onBack = { back() })
                    }
                }
                composable(Routes.ADMIN_CHAT) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { back() }) {
                        com.addonstation.mobile.ui.admin.AdminChatScreen(onBack = { back() })
                    }
                }
            }
        }

        if (tab != null) {
            BottomNavBar(current = tab, onSelect = { target -> goTab(target) })
        }
    }
}

/**
 * **بوابة الشات**: قائمة الغرف وكل غرفةٍ فيها **خلف تسجيل الدخول** — لا تدخل
 * الشات إلا بعد الدخول، ومن يحاول وهو غير مسجّل يُنقل إلى شاشة الدخول فوراً.
 *
 * وُضعت في التنقّل نفسه لا داخل الشاشة، فتعمل مع كل الطرق: ضغطة تبويب
 * «المجتمع»، رابط إشعار، رجوعٌ في التنقّل، أو استعادةُ حالةٍ بعد قتل العملية.
 * ولا يرى غير المسجّل الشات ولا لحظةً واحدة — حتى أثناء النقل.
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
