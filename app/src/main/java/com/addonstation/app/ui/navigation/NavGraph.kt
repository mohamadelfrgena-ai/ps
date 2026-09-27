package com.addonstation.app.ui.navigation

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.addonstation.app.LocalApp
import com.addonstation.app.PendingRecovery
import com.addonstation.app.appViewModel
import com.addonstation.app.core.SessionState
import com.addonstation.app.ui.components.AppBottomBar
import com.addonstation.app.ui.screens.AdminScreen
import com.addonstation.app.ui.screens.AuthScreen
import com.addonstation.app.ui.screens.ChatScreen
import com.addonstation.app.ui.screens.CheckoutScreen
import com.addonstation.app.ui.screens.CreatorApplicationScreen
import com.addonstation.app.ui.screens.CreatorProfileScreen
import com.addonstation.app.ui.screens.CreatorStudioScreen
import com.addonstation.app.ui.screens.CreatorsScreen
import com.addonstation.app.ui.screens.ExclusivesScreen
import com.addonstation.app.ui.screens.ForgotPasswordScreen
import com.addonstation.app.ui.screens.HomeScreen
import com.addonstation.app.ui.screens.IpGuideScreen
import com.addonstation.app.ui.screens.ItemDetailScreen
import com.addonstation.app.ui.screens.LoginScreen
import com.addonstation.app.ui.screens.NotificationsScreen
import com.addonstation.app.ui.screens.OrdersScreen
import com.addonstation.app.ui.screens.ProfileScreen
import com.addonstation.app.ui.screens.ProfileSettingsScreen
import com.addonstation.app.ui.screens.RegisterScreen
import com.addonstation.app.ui.screens.ResetPasswordScreen
import com.addonstation.app.ui.screens.SettingsScreen
import com.addonstation.app.ui.screens.SplashScreen
import com.addonstation.app.ui.screens.StoreScreen
import com.addonstation.app.ui.screens.SupportScreen
import com.addonstation.app.ui.screens.ThreadScreen
import com.addonstation.app.ui.screens.TipScreen
import com.addonstation.app.ui.screens.UserScreen
import com.addonstation.app.ui.screens.WalletScreen
import com.addonstation.app.ui.screens.WelcomeScreen
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** All destinations (mirrors the /m/* routes of the original app). */
object Routes {
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val HOME = "home"
    const val STORE = "store"
    const val COMMUNITY = "community"
    const val NOTIFICATIONS = "notifications"
    const val PROFILE = "profile"
    const val SETTINGS = "settings"
    const val ITEM = "item/{id}"
    const val USER = "user/{id}"
    const val PROFILE_SETTINGS = "profile_settings"
    const val SUPPORT = "support"
    const val IP_GUIDE = "ip_guide"
    const val WALLET = "wallet"
    const val CHECKOUT = "checkout/{planId}"
    const val ORDERS = "orders"
    const val CREATORS = "creators"
    const val CREATOR = "creator/{id}"
    const val CREATOR_STUDIO = "creator_studio"
    const val CREATOR_APPLICATION = "creator_application"
    const val TIP = "tip/{creatorId}"
    const val EXCLUSIVES = "exclusives"
    const val LOGIN = "login"
    const val REGISTER = "register"
    const val FORGOT = "forgot"
    const val RESET_PASSWORD = "reset_password"
    const val ADMIN = "admin"
    const val THREAD = "thread/{rootId}"

    fun item(id: Long) = "item/$id"
    fun user(id: String) = "user/$id"
    fun checkout(planId: String) = "checkout/$planId"
    fun creator(id: String) = "creator/$id"
    fun tip(creatorId: String) = "tip/$creatorId"
    fun thread(rootId: Long) = "thread/$rootId"
    fun community(channel: Long? = null, message: Long? = null): String {
        val q = listOfNotNull(
            channel?.let { "channel=$it" },
            message?.let { "message=$it" }
        ).joinToString("&")
        return if (q.isEmpty()) COMMUNITY else "$COMMUNITY?$q"
    }

    /** Bottom-bar tabs in original order (RTL-aware). */
    val tabs = listOf(SETTINGS, COMMUNITY, HOME, STORE, PROFILE)
}

/** Session-wide state: profile, unread badge, AC balance (top bar + badges). */
class AppViewModel(private val app: com.addonstation.app.AppContainer) : ViewModel() {
    val session: StateFlow<SessionState> = app.auth.state

    private val _unread = MutableStateFlow(0)
    val unread: StateFlow<Int> = _unread.asStateFlow()

    private val _balance = MutableStateFlow(0L)
    val balance: StateFlow<Long> = _balance.asStateFlow()

    private var notifJob: Job? = null
    private var pollJob: Job? = null

    init {
        viewModelScope.launch {
            app.auth.state.collect { s ->
                notifJob?.cancel()
                pollJob?.cancel()
                if (s.userId != null && !s.loading) {
                    refreshAll()
                    // Live unread badge via realtime (best-effort) + 30s polling fallback
                    notifJob = launch {
                        try {
                            app.realtime.notifications(s.userId).collect { refreshUnread() }
                        } catch (_: Exception) { }
                    }
                    pollJob = launch {
                        while (isActive) {
                            delay(30_000)
                            refreshUnread()
                        }
                    }
                } else {
                    _unread.value = 0
                    _balance.value = 0
                }
            }
        }
    }

    fun refreshAll() {
        viewModelScope.launch {
            try {
                app.auth.refreshProfile()
            } catch (_: Exception) { }
            refreshUnread()
            refreshBalance()
        }
    }

    fun refreshUnread() {
        val uid = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            _unread.value = try {
                app.profiles.unreadCount(uid)
            } catch (_: Exception) { _unread.value }
        }
    }

    fun refreshBalance() {
        val uid = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            _balance.value = try {
                app.wallet.balance(uid)
            } catch (_: Exception) { _balance.value }
        }
    }

    /** Daily-login + welcome AC claims, exactly like the original app boot flow. */
    fun claimLoginRewards() {
        viewModelScope.launch {
            app.wallet.claimLoginRewards()
            refreshBalance()
        }
    }
}

@Composable
fun OnResume(key: Any? = Unit, block: () -> Unit) {
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner, key) {
        val obs = LifecycleEventObserver { _, e ->
            if (e == Lifecycle.Event.ON_RESUME) block()
        }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
}

@Composable
fun AppRoot(ready: Boolean) {
    val app = LocalApp.current
    if (!ready) {
        SplashScreen(animated = true)
        return
    }
    val nav = rememberNavController()
    val vm: AppViewModel = appViewModel { AppViewModel(app) }
    val session by vm.session.collectAsState()

    // Decide start destination once ready
    val start = remember {
        when {
            app.pendingRecovery != null -> Routes.RESET_PASSWORD
            else -> Routes.SPLASH
        }
    }

    OnResume { vm.refreshAll() }
    LaunchedEffect(session.userId) {
        if (session.userId != null) vm.claimLoginRewards()
    }

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.SPLASH) { SplashScreen(animated = false, onDone = { welcomeDone ->
            nav.navigate(if (welcomeDone) Routes.HOME else Routes.WELCOME) {
                popUpTo(Routes.SPLASH) { inclusive = true }
            }
        }) }
        composable(Routes.WELCOME) {
            WelcomeScreen(nav, onDone = {
                nav.navigate(Routes.HOME) { popUpTo(Routes.WELCOME) { inclusive = true } }
            })
        }
        composable(Routes.HOME) { MainScaffold(nav, Routes.HOME, vm) { HomeScreen(nav, vm) } }
        composable(Routes.STORE) { MainScaffold(nav, Routes.STORE, vm) { StoreScreen(nav, vm) } }
        composable(
            "${Routes.COMMUNITY}?channel={channel}&message={message}",
            arguments = listOf(
                navArgument("channel") { type = NavType.StringType; nullable = true; defaultValue = null },
                navArgument("message") { type = NavType.StringType; nullable = true; defaultValue = null }
            )
        ) { back ->
            MainScaffold(nav, Routes.COMMUNITY, vm) {
                ChatScreen(nav, vm, back.arguments?.getString("channel")?.toLongOrNull(), back.arguments?.getString("message")?.toLongOrNull())
            }
        }
        composable(Routes.NOTIFICATIONS) { MainScaffold(nav, null, vm) { NotificationsScreen(nav, vm) } }
        composable(Routes.PROFILE) { MainScaffold(nav, Routes.PROFILE, vm) { ProfileScreen(nav, vm) } }
        composable(Routes.SETTINGS) { MainScaffold(nav, Routes.SETTINGS, vm) { SettingsScreen(nav, vm) } }
        composable(Routes.ITEM, arguments = listOf(navArgument("id") { type = NavType.LongType })) {
            MainScaffold(nav, null, vm) { ItemDetailScreen(nav, vm, it.arguments?.getLong("id") ?: 0L) }
        }
        composable(Routes.USER, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            MainScaffold(nav, null, vm) { UserScreen(nav, vm, it.arguments?.getString("id") ?: "") }
        }
        composable(Routes.PROFILE_SETTINGS) { MainScaffold(nav, null, vm) { ProfileSettingsScreen(nav, vm) } }
        composable(Routes.SUPPORT) { MainScaffold(nav, null, vm) { SupportScreen(nav, vm) } }
        composable(Routes.IP_GUIDE) { MainScaffold(nav, null, vm) { IpGuideScreen(nav) } }
        composable(Routes.WALLET) { MainScaffold(nav, null, vm) { WalletScreen(nav, vm) } }
        composable(Routes.CHECKOUT, arguments = listOf(navArgument("planId") { type = NavType.StringType })) {
            MainScaffold(nav, null, vm) { CheckoutScreen(nav, vm, it.arguments?.getString("planId") ?: "") }
        }
        composable(Routes.ORDERS) { MainScaffold(nav, null, vm) { OrdersScreen(nav, vm) } }
        composable(Routes.CREATORS) { MainScaffold(nav, null, vm) { CreatorsScreen(nav, vm) } }
        composable(Routes.CREATOR, arguments = listOf(navArgument("id") { type = NavType.StringType })) {
            MainScaffold(nav, null, vm) { CreatorProfileScreen(nav, vm, it.arguments?.getString("id") ?: "") }
        }
        composable(Routes.CREATOR_STUDIO) { MainScaffold(nav, null, vm) { CreatorStudioScreen(nav, vm) } }
        composable(Routes.CREATOR_APPLICATION) { MainScaffold(nav, null, vm) { CreatorApplicationScreen(nav, vm) } }
        composable(Routes.TIP, arguments = listOf(navArgument("creatorId") { type = NavType.StringType })) {
            MainScaffold(nav, null, vm) { TipScreen(nav, vm, it.arguments?.getString("creatorId") ?: "") }
        }
        composable(Routes.EXCLUSIVES) { MainScaffold(nav, null, vm) { ExclusivesScreen(nav, vm) } }
        composable(Routes.LOGIN) { AuthScreen(nav, vm) { LoginScreen(nav, vm) } }
        composable(Routes.REGISTER) { AuthScreen(nav, vm) { RegisterScreen(nav, vm) } }
        composable(Routes.FORGOT) { AuthScreen(nav, vm) { ForgotPasswordScreen(nav, vm) } }
        composable(Routes.RESET_PASSWORD) {
            val pending: PendingRecovery? = app.pendingRecovery
            AuthScreen(nav, vm) { ResetPasswordScreen(nav, vm, pending) }
        }
        composable(Routes.ADMIN) { MainScaffold(nav, null, vm) { AdminScreen(nav, vm) } }
        composable(Routes.THREAD, arguments = listOf(navArgument("rootId") { type = NavType.LongType })) {
            MainScaffold(nav, null, vm) { ThreadScreen(nav, vm, it.arguments?.getLong("rootId") ?: 0L) }
        }
    }
}

/** Shared scaffold: bottom bar only on the 5 tab routes (like the original). */
@Composable
fun MainScaffold(
    nav: NavHostController,
    tab: String?,
    vm: AppViewModel,
    content: @Composable (PaddingValues) -> Unit
) {
    val entry by nav.currentBackStackEntryAsState()
    val current = entry?.destination?.route?.substringBefore("?") ?: tab
    Scaffold(
        bottomBar = {
            if (tab != null) {
                AppBottomBar(current = current, onSelect = { route ->
                    nav.navigate(route) {
                        popUpTo(Routes.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                })
            }
        },
        content = content
    )
}
