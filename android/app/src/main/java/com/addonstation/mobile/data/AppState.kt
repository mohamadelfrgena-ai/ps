package com.addonstation.mobile.data

import com.addonstation.mobile.data.model.AcWallet
import com.addonstation.mobile.data.model.AdminStats
import com.addonstation.mobile.data.model.MyChatStatus
import com.addonstation.mobile.data.model.PaymentMethodSetting
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.data.model.StickerPack
import com.addonstation.mobile.data.model.SubscriptionPlan
import com.addonstation.mobile.data.model.UnreadCount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * حالة التطبيق المشتركة: الملف الشخصي، المحفظة، الخطط، طرق الدفع، الأصول، الإشعارات.
 * كل الشاشات تقرأ منها، والمستودعات هي التي تكتب فيها.
 */
enum class ProfileLoadStatus {
    IDLE,
    LOADING,
    READY,
    MISSING,
    FAILED,
}

class AppState(private val container: AppContainer) {

    private val _profile = MutableStateFlow<Profile?>(null)
    val profile: StateFlow<Profile?> = _profile.asStateFlow()

    /** لا نخلط بين «الجلسة مسجّلة» و«بيانات الملف الشخصي وصلت بعد». */
    private val _profileLoadStatus = MutableStateFlow(ProfileLoadStatus.IDLE)
    val profileLoadStatus: StateFlow<ProfileLoadStatus> = _profileLoadStatus.asStateFlow()

    private val _signedIn = MutableStateFlow(container.supabase.isSignedIn())
    val signedIn: StateFlow<Boolean> = _signedIn.asStateFlow()

    private val _wallet = MutableStateFlow<AcWallet?>(null)
    val wallet: StateFlow<AcWallet?> = _wallet.asStateFlow()

    private val _plans = MutableStateFlow<List<SubscriptionPlan>>(emptyList())
    val plans: StateFlow<List<SubscriptionPlan>> = _plans.asStateFlow()

    private val _paymentMethods = MutableStateFlow<List<PaymentMethodSetting>>(emptyList())
    val paymentMethods: StateFlow<List<PaymentMethodSetting>> = _paymentMethods.asStateFlow()

    private val _unread = MutableStateFlow<Map<String, UnreadCount>>(emptyMap())
    val unread: StateFlow<Map<String, UnreadCount>> = _unread.asStateFlow()

    private val _notificationsUnread = MutableStateFlow(0)
    val notificationsUnread: StateFlow<Int> = _notificationsUnread.asStateFlow()

    private val _chatStatus = MutableStateFlow<MyChatStatus?>(null)
    val chatStatus: StateFlow<MyChatStatus?> = _chatStatus.asStateFlow()

    private val _adminStats = MutableStateFlow<AdminStats?>(null)
    val adminStats: StateFlow<AdminStats?> = _adminStats.asStateFlow()

    private val _siteAssets = MutableStateFlow<List<SiteAsset>>(emptyList())
    val siteAssets: StateFlow<List<SiteAsset>> = _siteAssets.asStateFlow()

    private val _stickerPacks = MutableStateFlow<List<StickerPack>>(emptyList())
    val stickerPacks: StateFlow<List<StickerPack>> = _stickerPacks.asStateFlow()

    private val _loadError = MutableStateFlow<ApiError?>(null)
    val loadError: StateFlow<ApiError?> = _loadError.asStateFlow()

    val currentProfile: Profile? get() = _profile.value
    val currentUserId: String? get() = container.supabase.session()?.userId
    val isAdmin: Boolean get() = _profile.value?.role == "admin"

    fun setProfile(value: Profile?) {
        _profile.value = value
        _profileLoadStatus.value = if (value == null) ProfileLoadStatus.MISSING else ProfileLoadStatus.READY
    }

    fun setProfileLoading() {
        _profileLoadStatus.value = ProfileLoadStatus.LOADING
    }

    fun setProfileLoadFailed() {
        _profileLoadStatus.value = if (_profile.value == null) {
            ProfileLoadStatus.FAILED
        } else {
            ProfileLoadStatus.READY
        }
    }

    fun setSignedIn(value: Boolean) {
        _signedIn.value = value
        if (!value) {
            _profile.value = null
            _profileLoadStatus.value = ProfileLoadStatus.IDLE
            _wallet.value = null
            _unread.value = emptyMap()
            _chatStatus.value = null
            _notificationsUnread.value = 0
        }
    }

    fun setWallet(value: AcWallet?) {
        _wallet.value = value
    }

    fun setPlans(value: List<SubscriptionPlan>) {
        _plans.value = value
    }

    fun setPaymentMethods(value: List<PaymentMethodSetting>) {
        _paymentMethods.value = value
    }

    fun setUnread(value: Map<String, UnreadCount>) {
        _unread.value = value
    }

    fun setNotificationsUnread(value: Int) {
        _notificationsUnread.value = value
    }

    fun setChatStatus(value: MyChatStatus?) {
        _chatStatus.value = value
    }

    fun setAdminStats(value: AdminStats?) {
        _adminStats.value = value
    }

    fun setSiteAssets(value: List<SiteAsset>) {
        _siteAssets.value = value
    }

    fun setStickerPacks(value: List<StickerPack>) {
        _stickerPacks.value = value
    }

    fun reportError(error: ApiError?) {
        _loadError.value = error
    }

    /** إعادة تحميل كل ما يعتمد على المستخدم الحالي. */
    suspend fun refreshAll() {
        val repos = container
        if (!repos.supabase.isSignedIn()) {
            setSignedIn(false)
            return
        }
        setSignedIn(true)
        runCatching { repos.profile.refreshProfile() }
        runCatching { repos.profile.refreshWallet() }
        runCatching { repos.plans.loadPlans() }
        runCatching { repos.plans.loadPaymentMethods() }
        runCatching { repos.chat.refreshMyStatus() }
        runCatching { repos.chat.refreshUnread() }
    }
}
