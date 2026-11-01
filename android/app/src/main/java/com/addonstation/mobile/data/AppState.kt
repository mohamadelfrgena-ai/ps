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
import java.util.Locale
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

    /** النسخة الخام من الخادم؛ لا تُنشر للشاشات قبل تطبيق حجب أصول الأدوار. */
    @Volatile
    private var loadedSiteAssets: List<SiteAsset> = emptyList()

    private val _siteAssets = MutableStateFlow<List<SiteAsset>>(emptyList())
    val siteAssets: StateFlow<List<SiteAsset>> = _siteAssets.asStateFlow()

    /** روابط قصيرة العمر للأصول الموجودة في مخزن admin-assets الخاص. */
    private val _privateAssetUrls = MutableStateFlow<Map<String, String>>(emptyMap())
    val privateAssetUrls: StateFlow<Map<String, String>> = _privateAssetUrls.asStateFlow()

    /**
     * فهرس «اسم الأداة ⇒ رابطها» يُبنى **مرة واحدة** عند وصول الأصول.
     *
     * **لماذا؟** عرض الأداة المجهّزة كان يبحث في قائمة الأصول كلها (٤٩٢ أصلاً)
     * عند **كل رسالة** في الشات وعند كل إعادة تركيب: ١٥٠٠+ مقارنة نصية في
     * الإطار الواحد أثناء التمرير ⇒ تقطيع واضح. الآن البحث O(1).
     */
    private val _itemUrlIndex = MutableStateFlow<Map<String, String>>(emptyMap())
    val itemUrlIndex: StateFlow<Map<String, String>> = _itemUrlIndex.asStateFlow()

    private fun buildItemUrlIndex(assets: List<SiteAsset>): Map<String, String> {
        val out = HashMap<String, String>(assets.size * 4)
        assets.forEach { asset ->
            if (!asset.assetType.equals("item", ignoreCase = true)) return@forEach
            val url = container.siteAssetUrl(asset.fileUrl) ?: return@forEach
            val aliases = buildList {
                asset.name.trim().takeIf(String::isNotEmpty)?.let { add(it) }
                asset.fileUrl.trim().trimStart('/').takeIf(String::isNotEmpty)?.let { add(it) }
                asset.id.takeIf { it > 0L }?.toString()?.let { add(it) }
            }
            aliases.forEach { alias ->
                out.putIfAbsent(alias, url)
                out.putIfAbsent(alias.lowercase(Locale.ROOT), url)
            }
        }
        return out
    }

    private fun assetVisibleToRole(asset: SiteAsset, role: String?): Boolean = when {
        asset.requiredPlan.equals("admin", ignoreCase = true) -> role == "admin"
        // The verification pack is deliberately strict: creator rows do not
        // leak into a normal, moderator, or admin profile picker. Admins still
        // receive the raw rows in AssetsRepository.all() for management.
        asset.requiredPlan.equals("creator", ignoreCase = true) -> role == "creator"
        else -> true
    }

    private fun publishVisibleAssets() {
        val role = _profile.value?.role
        val visible = loadedSiteAssets.filter { assetVisibleToRole(it, role) }
        _siteAssets.value = visible
        _itemUrlIndex.value = buildItemUrlIndex(visible)
    }

    /** دفاع داخل التطبيق فوق RLS: أصول الأدوار لا تدخل منتقي مستخدم غير مخوّل. */
    fun visibleAssets(assets: List<SiteAsset>): List<SiteAsset> {
        val role = _profile.value?.role
        return assets.filter { assetVisibleToRole(it, role) }
    }

    fun setPrivateAssetUrls(value: Map<String, String>, replace: Boolean = true) {
        _privateAssetUrls.value = if (replace) value else _privateAssetUrls.value + value
        // بناء فهرس الأدوات مجدداً كي تتحول أداة الأدمن إلى رابطها الموقّع فور وصوله.
        publishVisibleAssets()
    }

    fun privateAssetUrl(path: String): String? =
        if (_profile.value?.role == "admin") _privateAssetUrls.value[path.trim().trimStart('/')]
        else null

    fun isAdminOnlyAssetValue(value: String?): Boolean {
        if (isKnownLegacyAdminAssetValue(value)) return true
        val clean = value?.trim().orEmpty()
        if (clean.isEmpty()) return false
        return loadedSiteAssets.any { asset ->
            asset.requiredPlan.equals("admin", ignoreCase = true) &&
                (asset.fileUrl.equals(clean, ignoreCase = true) || asset.name.equals(clean, ignoreCase = true))
        }
    }

    private fun isAdminOnlyProfileValue(kind: String, value: String?): Boolean {
        if (isKnownLegacyAdminAsset(kind, value)) return true
        val clean = value?.trim().orEmpty()
        if (clean.isEmpty()) return false
        return loadedSiteAssets.any { asset ->
            asset.requiredPlan.equals("admin", ignoreCase = true) &&
                asset.assetType.equals(kind, ignoreCase = true) &&
                (asset.fileUrl.equals(clean, ignoreCase = true) || asset.name.equals(clean, ignoreCase = true))
        }
    }

    /**
     * توافق دفاعي قبل نشر visible_profiles: ينقّح فقط الأفاتار/الغلاف/الأداة
     * الموسومة admin، ولا يمس صور المستخدمين العادية.
     */
    fun redactAdminAssets(profile: Profile): Profile {
        val viewerIsAdmin = _profile.value?.role == "admin" ||
            (profile.id == currentUserId && profile.role == "admin")
        if (viewerIsAdmin) return profile
        return profile.copy(
            avatarUrl = profile.avatarUrl.takeUnless { isAdminOnlyProfileValue("avatar", it) },
            bannerUrl = profile.bannerUrl.takeUnless { isAdminOnlyProfileValue("banner", it) },
            equippedItemId = profile.equippedItemId.takeUnless {
                isAdminOnlyProfileValue("item", it)
            } ?: "none",
        )
    }

    private val _stickerPacks = MutableStateFlow<List<StickerPack>>(emptyList())
    val stickerPacks: StateFlow<List<StickerPack>> = _stickerPacks.asStateFlow()

    private val _loadError = MutableStateFlow<ApiError?>(null)
    val loadError: StateFlow<ApiError?> = _loadError.asStateFlow()

    val currentProfile: Profile? get() = _profile.value
    val currentUserId: String? get() = container.supabase.currentUserId()
    val isAdmin: Boolean get() = _profile.value?.role == "admin"

    fun setProfile(value: Profile?) {
        _profile.value = value
        _profileLoadStatus.value = if (value == null) ProfileLoadStatus.MISSING else ProfileLoadStatus.READY
        if (value?.role != "admin") _privateAssetUrls.value = emptyMap()
        publishVisibleAssets()
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
            _privateAssetUrls.value = emptyMap()
            publishVisibleAssets()
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
        loadedSiteAssets = value
        publishVisibleAssets()
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
