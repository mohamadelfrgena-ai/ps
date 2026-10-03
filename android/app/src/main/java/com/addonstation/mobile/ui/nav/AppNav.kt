package com.addonstation.mobile.ui.nav

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.statusBars
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
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.splash.SplashScreen
import com.addonstation.mobile.ui.store.StoreScreen
import com.addonstation.mobile.ui.store.ContentDetailScreen
import com.addonstation.mobile.ui.settings.SettingsScreen
import com.addonstation.mobile.ui.profile.ProfileScreen
import com.addonstation.mobile.ui.common.SetupScreen
import android.app.Activity
import android.content.ContextWrapper
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

    // استعادة الجلسة عند الإقلاع + معالجة رابط الاستعادة addonstation://recovery
    LaunchedEffect(Unit) {
        // كل عمل الإقلاع (شبكة + فك ترميز JSON) على خيط الإدخال/الإخراج،
        // حتى لا يتجمّد الخيط الرئيسي أثناء شاشة الترحيب (كان سبب «اللاق»).
        val recovered = runCatching {
            withContext(Dispatchers.IO) {
                val linked = recoveryLink != null && container.auth.acceptRecoveryLink(recoveryLink)
                if (container.auth.isSignedIn()) {
                    container.auth.ensureFreshSession()
                    container.state.refreshAll()
                    container.assets.all()
                }
                container.plans.loadPlans()
                container.plans.loadPaymentMethods()
                linked
            }
        }.getOrDefault(false)
        deepLinkSession = recovered
        configured = container.isConfigured
        onRecoveryConsumed()
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
                        .windowInsetsTopHeight(WindowInsets.statusBars),
                )
                Box(Modifier.fillMaxSize()) {
                    when {
                        !splashDone -> SplashScreen(onFinished = { splashDone = true })
                        !configured -> SetupScreen(onDone = { configured = container.isConfigured })
                        else -> MainNavigation(startAtReset = deepLinkSession)
                    }
                }
            }
        }
    }

    // إعادة التحميل عند تسجيل الدخول من أي شاشة
}

@Composable
private fun MainNavigation(startAtReset: Boolean) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route
    val tab = MainTab.entries.firstOrNull { it.route == route }
    val profile by container.state.profile.collectAsState()
    val wallet by container.state.wallet.collectAsState()

    LaunchedEffect(startAtReset) {
        if (startAtReset) navController.navigate(Routes.RESET)
    }

    fun go(route: String) {
        navController.navigate(route) { launchSingleTop = true }
    }

    fun goTab(target: MainTab) {
        navController.navigate(target.route) {
            popUpTo(Routes.HOME) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
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
                    com.addonstation.mobile.ui.community.CommunityGate(
                        onOpenRoom = { channel: ChatChannel -> go(Routes.chat(channel.id)) },
                        // ترس لوحة المستخدم في الشات → تعديل الملف الشخصي (وإن لم يسجّل: شاشة الدخول)
                        onOpenEditProfile = { if (profile == null) go(Routes.LOGIN) else go(Routes.EDIT_PROFILE) },
                        onOpenWallet = { go(Routes.WALLET) },
                        onLogin = { go(Routes.LOGIN) },
                        onDeclineToHome = { goTab(MainTab.Home) },
                    )
                }

                composable(Routes.SETTINGS) {
                    SettingsScreen(
                        onLogin = { go(Routes.LOGIN) },
                        onNotifications = { go(Routes.NOTIFICATIONS) },
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
                        onBack = { navController.popBackStack() },
                        onSuccess = { goTab(MainTab.Profile) },
                        onOpenSignup = { go(Routes.SIGNUP) },
                    )
                }
                composable(Routes.SIGNUP) {
                    com.addonstation.mobile.ui.auth.SignUpScreen(
                        onBack = { navController.popBackStack() },
                        onSuccess = { goTab(MainTab.Profile) },
                        onOpenLogin = {
                            navController.navigate(Routes.LOGIN) { popUpTo(Routes.SIGNUP) { inclusive = true } }
                        },
                    )
                }
                composable(Routes.RESET) {
                    com.addonstation.mobile.ui.auth.ResetPasswordScreen(
                        onBack = { navController.popBackStack() },
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
                        onBack = { navController.popBackStack() },
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
                        onBack = { navController.popBackStack() },
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
                        onBack = { navController.popBackStack() },
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
                        onBack = { navController.popBackStack() },
                        onOpen = { item -> go(Routes.item(item.id)) },
                    )
                }

                // الشات
                composable(
                    route = Routes.CHAT,
                    arguments = listOf(navArgument("channelId") { type = NavType.StringType }),
                ) { entry ->
                    val channelId = entry.arguments?.getString("channelId").orEmpty()
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
                            onBack = { navController.popBackStack() },
                            onOpenSettings = { goTab(MainTab.Settings) },
                            onOpenUser = { profile -> go(Routes.userProfile(profile.id)) },
                            onLogin = { go(Routes.LOGIN) },
                        )
                    }
                }

                // الحساب والمحفظة والإشعارات
                composable(Routes.WALLET) {
                    com.addonstation.mobile.ui.account.AcWalletScreen(
                        onBack = { navController.popBackStack() },
                        onOpenItem = { id -> go(Routes.item(id)) },
                    )
                }
                composable(Routes.NOTIFICATIONS) {
                    com.addonstation.mobile.ui.account.NotificationsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenChannel = { channelId, _ -> go(Routes.chat(channelId)) },
                        onOpenProfile = { id -> go(Routes.userProfile(id)) },
                    )
                }
                composable(Routes.FRIENDS) {
                    com.addonstation.mobile.ui.account.FriendsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenProfile = { id -> go(Routes.userProfile(id)) },
                    )
                }

                // الخطط والدفع
                composable(Routes.PLANS) {
                    com.addonstation.mobile.ui.plans.PlansScreen(
                        onBack = { navController.popBackStack() },
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
                        onBack = { navController.popBackStack() },
                        onOpenOrders = { go(Routes.ORDERS) },
                        onLogin = { go(Routes.LOGIN) },
                        onOpenPlans = { go(Routes.PLANS) },
                    )
                }
                composable(Routes.ORDERS) {
                    com.addonstation.mobile.ui.plans.MyOrdersScreen(onBack = { navController.popBackStack() })
                }

                // المبدعون
                composable(Routes.CREATORS) {
                    com.addonstation.mobile.ui.creators.CreatorsScreen(
                        onBack = { navController.popBackStack() },
                        onOpenCreator = { id -> go(Routes.creatorProfile(id)) },
                    )
                }
                composable(
                    route = Routes.CREATOR_PROFILE,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.creators.CreatorProfileScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                        onOpenProfile = { id -> go(Routes.userProfile(id)) },
                    )
                }
                composable(Routes.CREATOR_APPLY) {
                    com.addonstation.mobile.ui.creators.CreatorApplicationScreen(
                        onBack = { navController.popBackStack() },
                        onSubmitted = { },
                    )
                }
                composable(Routes.CREATOR_STUDIO) {
                    com.addonstation.mobile.ui.creators.CreatorStudioScreen(
                        onBack = { navController.popBackStack() },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
                    )
                }

                // الملف الشخصي
                composable(Routes.EDIT_PROFILE) {
                    com.addonstation.mobile.ui.profile.EditProfileScreen(
                        onBack = { navController.popBackStack() },
                        onSaved = { navController.popBackStack() },
                    )
                }
                composable(
                    route = Routes.USER_PROFILE,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.profile.UserProfileScreen(
                        userId = entry.arguments?.getString("userId").orEmpty(),
                        onBack = { navController.popBackStack() },
                        onMessage = { goTab(MainTab.Community) },
                        onAddFriend = { profile ->
                            scope.launch { container.profile.sendFriendRequest(profile.id) }
                        },
                        onOpenItem = { item -> go(Routes.item(item.id)) },
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
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { navController.popBackStack() }) {
                        com.addonstation.mobile.ui.admin.AdminAssetsScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.ADMIN_CREATORS) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { navController.popBackStack() }) {
                        com.addonstation.mobile.ui.admin.AdminCreatorReviewScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.ADMIN_AC) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { navController.popBackStack() }) {
                        com.addonstation.mobile.ui.admin.AdminAcScreen(onBack = { navController.popBackStack() })
                    }
                }
                // نفس الشاشة لكن مع مستخدم محدَّد (فتحها زر «+AC» من بطاقة المستخدم)
                composable(
                    Routes.ADMIN_AC_USER,
                    arguments = listOf(navArgument("userId") { type = NavType.StringType }),
                ) { entry ->
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { navController.popBackStack() }) {
                        com.addonstation.mobile.ui.admin.AdminAcScreen(
                            onBack = { navController.popBackStack() },
                            preselectUserId = entry.arguments?.getString("userId"),
                        )
                    }
                }
                composable(Routes.ADMIN_SETTINGS) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { navController.popBackStack() }) {
                        com.addonstation.mobile.ui.admin.AdminSettingsScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.ADMIN_AUDIT) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { navController.popBackStack() }) {
                        com.addonstation.mobile.ui.admin.AdminAuditScreen(onBack = { navController.popBackStack() })
                    }
                }
                composable(Routes.ADMIN_CHAT) {
                    com.addonstation.mobile.ui.admin.AdminGate(onExit = { navController.popBackStack() }) {
                        com.addonstation.mobile.ui.admin.AdminChatScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }

        if (tab != null) {
            BottomNavBar(current = tab, onSelect = { target -> goTab(target) })
        }
    }
}
