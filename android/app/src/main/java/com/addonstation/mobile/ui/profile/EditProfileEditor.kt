@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.addonstation.mobile.ui.profile

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.Image as ComposeImage
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.ui.AssetSection
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.equippedItemUrl
import com.addonstation.mobile.ui.groupAssetsByCollection
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.components.BannerThumb
import com.addonstation.mobile.ui.components.CoverImage
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.NumberFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.util.Locale

private const val STATUS_MAX_LENGTH = 60
private const val BIO_MAX_LENGTH = 400
private const val ITEM_NONE = "none"

private val EditBlue = Color(0xFF2E7BEF)
private val EditBlueLight = Color(0xFF4C9AFF)
private val EditEpic = Color(0xFF9B6BFF)
private val EditGold = Color(0xFFF0C25E)
private val EditGreen = Color(0xFF3FBF6A)
private val EditOnlineText = Color(0xFF7FD99A)
private val EditDanger = Color(0xFFFF8791)
private val EditDangerBorder = Color(0xFF5A2830)
private val UpgradeStart = Color(0xFF3A2A0B)
private val UpgradeEnd = Color(0xFF1B1405)
private val UpgradeBorder = Color(0xFF8A6A22)
private val UpgradeText = Color(0xFFE8D5A3)

private enum class EditorTab { Avatar, Banners, Item, About }

private data class EditorCollectionOption(
    val key: String?,
    val title: String,
    val locked: Boolean,
)

private enum class EditorAssetEntryKind { TierHeading, CollectionHeading, Assets }

private data class EditorAssetListEntry(
    val key: String,
    val kind: EditorAssetEntryKind,
    val title: String? = null,
    val count: Int = 0,
    val assets: List<SiteAsset> = emptyList(),
)

private data class ProfileEditorBaseline(
    val username: String,
    val bio: String,
    val status: String,
    val showOnline: Boolean,
    val avatar: String?,
    val banner: String?,
    val item: String,
)

private fun profileEditorBaseline(profile: Profile?): ProfileEditorBaseline = ProfileEditorBaseline(
    username = profile?.username.orEmpty(),
    bio = profile?.bio.orEmpty(),
    status = profile?.customStatus.orEmpty(),
    showOnline = profile?.showOnlineStatus ?: false,
    avatar = profile?.avatarUrl?.normalizeAssetSelection(),
    banner = profile?.bannerUrl?.normalizeAssetSelection(),
    item = normalizeItemSelection(profile?.equippedItemId),
)

private fun String?.normalizeAssetSelection(): String? =
    this?.trim()?.takeIf { it.isNotEmpty() && !it.equals(ITEM_NONE, ignoreCase = true) }

private fun normalizeItemSelection(value: String?): String =
    value?.trim()?.takeIf { it.isNotEmpty() && !it.equals(ITEM_NONE, ignoreCase = true) } ?: ITEM_NONE

private fun collectionAssetEntries(
    sections: List<AssetSection>,
    selectedCollection: String?,
    query: String,
    columns: Int,
    keyPrefix: String,
): List<EditorAssetListEntry> = buildList {
    val normalizedQuery = query.trim()
    sections
        .filter { selectedCollection == null || it.title == selectedCollection }
        .forEach { section ->
            val matching = if (normalizedQuery.isBlank()) section.assets
            else section.assets.filter { it.name.contains(normalizedQuery, ignoreCase = true) }
            if (matching.isNotEmpty()) {
                val sectionKey = "$keyPrefix:${section.title}"
                add(EditorAssetListEntry("$sectionKey:heading", EditorAssetEntryKind.CollectionHeading, section.title, matching.size))
                matching.chunked(columns).forEachIndexed { index, row ->
                    add(EditorAssetListEntry("$sectionKey:row:$index", EditorAssetEntryKind.Assets, assets = row))
                }
            }
        }
}

private fun itemAssetEntries(
    epicTitle: String,
    epicAssets: List<SiteAsset>,
    legendaryTitle: String,
    legendaryAssets: List<SiteAsset>,
    othersTitle: String,
): List<EditorAssetListEntry> = buildList {
    listOf(epicTitle to epicAssets, legendaryTitle to legendaryAssets).forEach { (tierTitle, assets) ->
        if (assets.isEmpty()) return@forEach
        val tierKey = "item-tier:${tierTitle.lowercase(Locale.ROOT)}"
        add(EditorAssetListEntry(tierKey, EditorAssetEntryKind.TierHeading, tierTitle, assets.size))
        groupAssetsByCollection(assets, othersTitle).forEach { section ->
            val sectionKey = "$tierKey:${section.title}"
            add(EditorAssetListEntry("$sectionKey:heading", EditorAssetEntryKind.CollectionHeading, section.title, section.assets.size))
            section.assets.chunked(4).forEachIndexed { index, row ->
                add(EditorAssetListEntry("$sectionKey:row:$index", EditorAssetEntryKind.Assets, assets = row))
            }
        }
    }
}

/**
 * محرّر الملف الشخصي بتغييرات مرحلية: المعاينة تتحدّث فوراً، لكن لا يُرسل أي تعديل
 * إلى الخادم حتى يضغط المستخدم «حفظ». تبقى تعديلات التبويبين محفوظة أثناء التنقل بينهما.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileEditorScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenPlans: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val palette = LocalHomePalette.current
    val isRtl = LocalLayoutDirection.current == androidx.compose.ui.unit.LayoutDirection.Rtl
    val bodyFont = if (isRtl) TajawalFont else NunitoFont
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val snackbarHostState = remember { SnackbarHostState() }

    val profileKey = profile?.id ?: "profile-pending"
    val baseline = remember(profileKey) { profileEditorBaseline(profile) }
    var username by remember(profileKey) { mutableStateOf(baseline.username) }
    var bio by remember(profileKey) { mutableStateOf(baseline.bio) }
    var customStatus by remember(profileKey) { mutableStateOf(baseline.status) }
    var showOnline by remember(profileKey) { mutableStateOf(baseline.showOnline) }
    var selectedAvatar by remember(profileKey) { mutableStateOf(baseline.avatar) }
    var selectedBanner by remember(profileKey) { mutableStateOf(baseline.banner) }
    var selectedItem by remember(profileKey) { mutableStateOf(baseline.item) }

    var currentTab by remember(profileKey) { mutableStateOf(EditorTab.Avatar) }
    var avatarSearch by remember(profileKey) { mutableStateOf("") }
    var bannerSearch by remember(profileKey) { mutableStateOf("") }
    var selectedAvatarCollection by remember(profileKey) { mutableStateOf<String?>(null) }
    var selectedBannerCollection by remember(profileKey) { mutableStateOf<String?>(null) }
    var avatarAssets by remember {
        mutableStateOf(container.state.siteAssets.value.filter { it.assetType == "avatar" })
    }
    var bannerAssets by remember {
        mutableStateOf(container.state.siteAssets.value.filter { it.assetType == "banner" })
    }
    var itemAssets by remember {
        mutableStateOf(container.state.siteAssets.value.filter { it.assetType == "item" })
    }
    var loadingAssets by remember { mutableStateOf(false) }
    var assetsUnavailable by remember { mutableStateOf(false) }
    var assetsRetry by remember { mutableStateOf(0) }
    var saving by remember { mutableStateOf(false) }
    var saveError by remember { mutableStateOf<String?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }

    val planLockedAvatars = stringResource(R.string.plan_locked_avatars_plain)
    val planLockedBanners = stringResource(R.string.plan_locked_banners)
    val planLockedItems = stringResource(R.string.plan_locked_items_plain)
    val planLockedOnline = stringResource(R.string.plan_locked_online_plain)
    val upgradeLabel = stringResource(R.string.item_upgrade)
    val genericError = stringResource(R.string.msg_generic_error)
    val networkError = stringResource(R.string.msg_network_error)
    val usernameTooShort = stringResource(R.string.msg_username_too_short)
    val usernameTaken = stringResource(R.string.msg_username_taken)

    LaunchedEffect(profile?.id) {
        if (profile == null) {
            try {
                container.profile.ensureProfileLoaded()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // تبقى الحقول والمعاينة ظاهرة حتى يمكن إعادة المحاولة أو الرجوع.
            }
        }
    }

    LaunchedEffect(currentTab, assetsRetry, profile?.id) {
        loadingAssets = true
        assetsUnavailable = false
        try {
            when (currentTab) {
                EditorTab.Avatar, EditorTab.Item -> {
                    val loaded = coroutineScope {
                        val avatars = async { container.assets.byType("avatar") }
                        val items = async { container.assets.byType("item") }
                        avatars.await() to items.await()
                    }
                    avatarAssets = loaded.first
                    itemAssets = loaded.second
                    assetsUnavailable = loaded.first.isEmpty() && loaded.second.isEmpty()
                }
                EditorTab.Banners -> {
                    bannerAssets = container.assets.byType("banner")
                    assetsUnavailable = bannerAssets.isEmpty()
                }
                EditorTab.About -> {
                    assetsUnavailable = false
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            assetsUnavailable = true
        } finally {
            loadingAssets = false
        }
    }

    val profileRank = container.profile.planRank(profile?.effectivePlan)
    val isAdmin = profile?.role == "admin"
    val legendaryUp = profileRank >= container.profile.planRank("legendary")
    val dirty =
        username.trim() != baseline.username.trim() ||
            bio != baseline.bio ||
            customStatus != baseline.status ||
            (legendaryUp && showOnline != baseline.showOnline) ||
            selectedAvatar.normalizeAssetSelection() != baseline.avatar ||
            selectedBanner.normalizeAssetSelection() != baseline.banner ||
            normalizeItemSelection(selectedItem) != baseline.item

    val selectedItemAsset = remember(itemAssets, selectedItem) {
        itemAssets.firstOrNull {
            it.name.equals(selectedItem, ignoreCase = true) || it.fileUrl.equals(selectedItem, ignoreCase = true)
        }
    }
    val previewAvatarUrl = assetUrl(container, selectedAvatar)
    val previewBannerUrl = assetUrl(container, selectedBanner)
    val previewItemUrl = selectedItemAsset?.let { assetUrl(container, it.fileUrl) }
        ?: equippedItemUrl(container, selectedItem)
    val displayName = profile?.displayName?.takeIf { it.isNotBlank() } ?: profile?.username.orEmpty()
    val onlineLabel = stringResource(R.string.profile_online)
    val offlineSuffix = stringResource(R.string.profile_offline_suffix)
    val statusLine = "@${username.ifBlank { profile?.username.orEmpty() }}" +
        if (showOnline) " · $onlineLabel" else offlineSuffix

    fun canUseAsset(asset: SiteAsset): Boolean {
        val required = asset.requiredPlan.lowercase(Locale.ROOT)
        return isAdmin || (required != "admin" && container.profile.planRank(required) <= profileRank)
    }

    val allCollectionsLabel = stringResource(R.string.common_all)
    val otherCollectionLabel = stringResource(R.string.creator_category_other)
    val avatarSections = remember(avatarAssets, otherCollectionLabel) {
        groupAssetsByCollection(avatarAssets, otherCollectionLabel)
    }
    val bannerSections = remember(bannerAssets, otherCollectionLabel) {
        groupAssetsByCollection(bannerAssets, otherCollectionLabel)
    }
    val avatarCollectionOptions = listOf(EditorCollectionOption(null, allCollectionsLabel, false)) +
        avatarSections.map { section ->
            EditorCollectionOption(section.title, section.title, section.assets.none(::canUseAsset))
        }
    val bannerCollectionOptions = listOf(EditorCollectionOption(null, allCollectionsLabel, false)) +
        bannerSections.map { section ->
            EditorCollectionOption(section.title, section.title, section.assets.none(::canUseAsset))
        }
    val avatarEntries = remember(avatarSections, selectedAvatarCollection, avatarSearch) {
        collectionAssetEntries(avatarSections, selectedAvatarCollection, avatarSearch, 4, "avatar")
    }
    val bannerEntries = remember(bannerSections, selectedBannerCollection, bannerSearch) {
        collectionAssetEntries(bannerSections, selectedBannerCollection, bannerSearch, 2, "banner")
    }
    val epicItemAssets = remember(itemAssets) {
        itemAssets.filter { asset ->
            val plan = asset.requiredPlan.lowercase(Locale.ROOT)
            plan != "legendary" && plan != "admin"
        }
    }
    val legendaryItemAssets = remember(itemAssets, isAdmin) {
        itemAssets.filter { asset ->
            val plan = asset.requiredPlan.lowercase(Locale.ROOT)
            plan == "legendary" || (isAdmin && plan == "admin")
        }
    }
    val epicItemTitle = stringResource(R.string.edit_profile_epic_item)
    val legendaryItemTitle = stringResource(R.string.edit_profile_golden_item)
    val itemEntries = remember(
        otherCollectionLabel,
        epicItemTitle,
        legendaryItemTitle,
        epicItemAssets,
        legendaryItemAssets,
    ) {
        itemAssetEntries(
            epicTitle = epicItemTitle,
            epicAssets = epicItemAssets,
            legendaryTitle = legendaryItemTitle,
            legendaryAssets = legendaryItemAssets,
            othersTitle = otherCollectionLabel,
        )
    }
    LaunchedEffect(avatarSections) {
        if (selectedAvatarCollection != null && avatarSections.none { it.title == selectedAvatarCollection }) {
            selectedAvatarCollection = null
        }
    }
    LaunchedEffect(bannerSections) {
        if (selectedBannerCollection != null && bannerSections.none { it.title == selectedBannerCollection }) {
            selectedBannerCollection = null
        }
    }

    fun showUpgradePrompt(message: String) {
        if (saving) return
        scope.launch {
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = upgradeLabel,
                withDismissAction = true,
                duration = SnackbarDuration.Long,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) onOpenPlans()
        }
    }

    fun mutationErrorMessage(error: Throwable): String {
        val raw = error.message.orEmpty()
        return when (raw.substringBefore(':').trim().lowercase(Locale.ROOT)) {
            "username_too_short" -> usernameTooShort
            "username_taken" -> usernameTaken
            "network", "network_error", "network_unavailable", "network_timeout" -> networkError
            else -> raw.takeIf { it.isNotBlank() } ?: genericError
        }
    }

    fun requestBack() {
        if (saving) return
        if (dirty) showDiscardDialog = true else onBack()
    }

    fun saveChanges() {
        if (saving || !dirty || profile == null) return
        scope.launch {
            saving = true
            saveError = null
            try {
                val profileFieldsChanged =
                    username.trim() != baseline.username.trim() ||
                        bio != baseline.bio ||
                        customStatus != baseline.status ||
                        (legendaryUp && showOnline != baseline.showOnline)
                if (profileFieldsChanged) {
                    container.profile.updateMyProfile(
                        username = username.trim(),
                        bio = bio,
                        customStatus = customStatus,
                        showOnline = if (legendaryUp && showOnline != baseline.showOnline) showOnline else null,
                    ).getOrThrow()
                }
                if (selectedAvatar.normalizeAssetSelection() != baseline.avatar) {
                    container.profile.setProfileAsset("avatar", selectedAvatar ?: ITEM_NONE).getOrThrow()
                }
                if (selectedBanner.normalizeAssetSelection() != baseline.banner) {
                    container.profile.setProfileAsset("banner", selectedBanner ?: ITEM_NONE).getOrThrow()
                }
                if (normalizeItemSelection(selectedItem) != baseline.item) {
                    container.profile.setProfileAsset("item", normalizeItemSelection(selectedItem)).getOrThrow()
                }
                saveError = null
                onSaved()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                saveError = mutationErrorMessage(error)
            } finally {
                saving = false
            }
        }
    }

    BackHandler(enabled = !saving, onBack = ::requestBack)

    Box(Modifier.fillMaxSize().background(palette.bg).imePadding()) {
        Column(Modifier.fillMaxSize()) {
        EditorTopBar(
            title = stringResource(R.string.profile_edit_plain),
            isRtl = isRtl,
            onBack = ::requestBack,
            enabled = !saving,
        )

        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(key = "live-preview") {
                ProfilePreviewCard(
                    displayName = displayName,
                    usernameLine = statusLine,
                    avatarUrl = previewAvatarUrl,
                    bannerUrl = previewBannerUrl,
                    itemUrl = previewItemUrl,
                    customStatus = customStatus,
                    isOnline = showOnline,
                    bodyFont = bodyFont,
                )
            }
            item(key = "editor-tabs") {
                EditorTabs(
                    selected = currentTab,
                    bodyFont = bodyFont,
                    enabled = !saving,
                    onSelect = { tab ->
                        if (tab != currentTab) {
                            keyboardController?.hide()
                            focusManager.clearFocus(force = true)
                            currentTab = tab
                        }
                    },
                )
            }

            when (currentTab) {
                EditorTab.Avatar -> {
                    item(key = "avatar-section-title") {
                        EditorSectionHeader(
                            title = stringResource(R.string.edit_profile_avatar_label),
                            accent = EditBlueLight,
                            isRtl = isRtl,
                        )
                    }
                    item(key = "avatar-collections") {
                        EditorCollectionChips(
                            options = avatarCollectionOptions,
                            selected = selectedAvatarCollection,
                            bodyFont = bodyFont,
                            onSelect = { selectedAvatarCollection = it },
                        )
                    }
                    item(key = "avatar-search") {
                        EditorSearchField(
                            value = avatarSearch,
                            onValueChange = { avatarSearch = it },
                            enabled = !saving,
                            bodyFont = bodyFont,
                        )
                    }
                    item(key = "avatar-count") {
                        val visibleCount = avatarEntries
                            .filter { it.kind == EditorAssetEntryKind.Assets }
                            .sumOf { it.assets.size }
                        Text(
                            text = if (loadingAssets) stringResource(R.string.profile_assets_loading)
                            else stringResource(R.string.profile_assets_count, visibleCount, avatarAssets.size),
                            modifier = Modifier.padding(horizontal = 20.dp),
                            style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                            color = palette.textMuted,
                        )
                    }
                    when {
                        loadingAssets && avatarAssets.isEmpty() -> item(key = "avatar-skeleton") {
                            EditorAssetSkeleton(columns = 4, ratio = 1f, bodyFont = bodyFont)
                        }
                        assetsUnavailable && avatarAssets.isEmpty() -> item(key = "avatar-retry") {
                            AssetsRetryCard(bodyFont = bodyFont, onRetry = { assetsRetry += 1 })
                        }
                        avatarEntries.isEmpty() -> item(key = "avatar-empty-search") {
                            Text(
                                stringResource(R.string.edit_profile_no_results),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp),
                                color = palette.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> items(avatarEntries, key = { it.key }) { entry ->
                            when (entry.kind) {
                                EditorAssetEntryKind.TierHeading -> Unit
                                EditorAssetEntryKind.CollectionHeading -> EditorCollectionHeader(
                                    title = entry.title.orEmpty(),
                                    count = entry.count,
                                    accent = EditBlueLight,
                                    bodyFont = bodyFont,
                                )
                                EditorAssetEntryKind.Assets -> AssetGridRow {
                                    entry.assets.forEach { asset ->
                                        val unlocked = canUseAsset(asset)
                                        val planName = assetPlanName(asset.requiredPlan)
                                        val description = if (!unlocked) {
                                            stringResource(R.string.cd_avatar_locked, asset.name, planName)
                                        } else if (selectedAvatar == asset.fileUrl) {
                                            stringResource(R.string.cd_avatar_selected, asset.name)
                                        } else asset.name
                                        AvatarAssetTile(
                                            modifier = Modifier.weight(1f),
                                            asset = asset,
                                            selected = selectedAvatar == asset.fileUrl,
                                            locked = !unlocked,
                                            planName = planName,
                                            contentDescription = description,
                                            enabled = !saving,
                                            onClick = {
                                                if (!unlocked) showUpgradePrompt(planLockedAvatars)
                                                else {
                                                    selectedAvatar = asset.fileUrl
                                                    saveError = null
                                                }
                                            },
                                        )
                                    }
                                    repeat(4 - entry.assets.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (avatarAssets.any { !canUseAsset(it) }) {
                        item(key = "avatar-upgrade") {
                            UpgradeCard(
                                message = planLockedAvatars,
                                bodyFont = bodyFont,
                                onUpgrade = onOpenPlans,
                            )
                        }
                    }
                }

                EditorTab.Banners -> {
                    item(key = "banner-section-title") {
                        EditorSectionHeader(
                            title = stringResource(R.string.profile_banner_section),
                            accent = EditEpic,
                            isRtl = isRtl,
                        )
                    }
                    item(key = "banner-collections") {
                        EditorCollectionChips(
                            options = bannerCollectionOptions,
                            selected = selectedBannerCollection,
                            bodyFont = bodyFont,
                            onSelect = { selectedBannerCollection = it },
                        )
                    }
                    item(key = "banner-search") {
                        EditorSearchField(
                            value = bannerSearch,
                            onValueChange = { bannerSearch = it },
                            enabled = !saving,
                            bodyFont = bodyFont,
                        )
                    }
                    item(key = "banner-clear") {
                        RemoveBannerButton(
                            enabled = selectedBanner != null && !saving,
                            bodyFont = bodyFont,
                            onClick = {
                                selectedBanner = null
                                saveError = null
                            },
                        )
                    }
                    if (selectedBanner == null && bannerAssets.isNotEmpty() && bannerAssets.none(::canUseAsset)) {
                        item(key = "banner-free-plan-note") {
                            FreeBannerState(bodyFont = bodyFont)
                        }
                    }
                    when {
                        loadingAssets && bannerAssets.isEmpty() -> item(key = "banner-skeleton") {
                            EditorAssetSkeleton(columns = 2, ratio = 16f / 7f, bodyFont = bodyFont)
                        }
                        assetsUnavailable && bannerAssets.isEmpty() -> item(key = "banner-retry") {
                            AssetsRetryCard(bodyFont = bodyFont, onRetry = { assetsRetry += 1 })
                        }
                        bannerEntries.isEmpty() -> item(key = "banner-no-results") {
                            Text(
                                stringResource(R.string.edit_profile_no_results),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp),
                                color = palette.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> items(bannerEntries, key = { it.key }) { entry ->
                            when (entry.kind) {
                                EditorAssetEntryKind.TierHeading -> Unit
                                EditorAssetEntryKind.CollectionHeading -> EditorCollectionHeader(
                                    title = entry.title.orEmpty(),
                                    count = entry.count,
                                    accent = EditEpic,
                                    bodyFont = bodyFont,
                                )
                                EditorAssetEntryKind.Assets -> AssetGridRow {
                                    entry.assets.forEach { asset ->
                                        val unlocked = canUseAsset(asset)
                                        val planName = assetPlanName(asset.requiredPlan)
                                        BannerAssetTile(
                                            modifier = Modifier.weight(1f),
                                            asset = asset,
                                            selected = selectedBanner == asset.fileUrl,
                                            locked = !unlocked,
                                            planName = planName,
                                            enabled = !saving,
                                            onClick = {
                                                if (!unlocked) showUpgradePrompt(planLockedBanners)
                                                else {
                                                    selectedBanner = asset.fileUrl
                                                    saveError = null
                                                }
                                            },
                                        )
                                    }
                                    repeat(2 - entry.assets.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (bannerAssets.any { !canUseAsset(it) }) {
                        item(key = "banner-upgrade") {
                            UpgradeCard(
                                message = planLockedBanners,
                                bodyFont = bodyFont,
                                onUpgrade = onOpenPlans,
                            )
                        }
                    }
                }

                EditorTab.Item -> {
                    item(key = "item-section-title") {
                        EditorSectionHeader(
                            title = stringResource(R.string.plan_item_section),
                            accent = EditGold,
                            isRtl = isRtl,
                        )
                    }
                    item(key = "item-chat-preview") {
                        ItemChatPreviewCard(
                            displayName = displayName,
                            avatarUrl = previewAvatarUrl,
                            itemUrl = previewItemUrl,
                            online = showOnline,
                            bodyFont = bodyFont,
                        )
                    }
                    item(key = "item-none") {
                        NoneItemRow(
                            selected = selectedItem.equals(ITEM_NONE, ignoreCase = true),
                            bodyFont = bodyFont,
                            onClick = {
                                selectedItem = ITEM_NONE
                                saveError = null
                            },
                        )
                    }
                    when {
                        loadingAssets && itemAssets.isEmpty() -> item(key = "item-skeleton") {
                            EditorAssetSkeleton(columns = 4, ratio = 1f, bodyFont = bodyFont)
                        }
                        assetsUnavailable && itemAssets.isEmpty() -> item(key = "item-retry") {
                            AssetsRetryCard(bodyFont = bodyFont, onRetry = { assetsRetry += 1 })
                        }
                        itemEntries.isEmpty() -> item(key = "item-no-assets") {
                            Text(
                                stringResource(R.string.edit_profile_no_items),
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                                style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp),
                                color = palette.textMuted,
                                textAlign = TextAlign.Center,
                            )
                        }
                        else -> items(itemEntries, key = { it.key }) { entry ->
                            when (entry.kind) {
                                EditorAssetEntryKind.TierHeading -> EditorSectionHeader(
                                    title = entry.title.orEmpty(),
                                    accent = if (entry.title == epicItemTitle) EditEpic else EditGold,
                                    isRtl = isRtl,
                                )
                                EditorAssetEntryKind.CollectionHeading -> EditorCollectionHeader(
                                    title = entry.title.orEmpty(),
                                    count = entry.count,
                                    accent = palette.textMuted,
                                    bodyFont = bodyFont,
                                )
                                EditorAssetEntryKind.Assets -> AssetGridRow {
                                    entry.assets.forEach { asset ->
                                        val selected = selectedItem.equals(asset.name, ignoreCase = true) ||
                                            selectedItem.equals(asset.fileUrl, ignoreCase = true)
                                        val unlocked = canUseAsset(asset)
                                        ItemAssetTile(
                                            modifier = Modifier.weight(1f),
                                            asset = asset,
                                            selected = selected,
                                            locked = !unlocked,
                                            planName = assetPlanName(asset.requiredPlan),
                                            equipped = selected,
                                            enabled = !saving,
                                            bodyFont = bodyFont,
                                            onClick = {
                                                if (!unlocked) showUpgradePrompt(planLockedItems)
                                                else {
                                                    selectedItem = asset.name
                                                    saveError = null
                                                }
                                            },
                                        )
                                    }
                                    repeat(4 - entry.assets.size) { Spacer(Modifier.weight(1f)) }
                                }
                            }
                        }
                    }
                    if (itemAssets.any { it.requiredPlan.lowercase(Locale.ROOT) != "admin" && !canUseAsset(it) }) {
                        item(key = "item-upgrade") {
                            UpgradeCard(
                                message = planLockedItems,
                                bodyFont = bodyFont,
                                onUpgrade = onOpenPlans,
                            )
                        }
                    }
                }

                EditorTab.About -> {
                    item(key = "about-section-title") {
                        EditorSectionHeader(
                            title = stringResource(R.string.edit_profile_tab_about),
                            accent = EditBlueLight,
                            isRtl = isRtl,
                        )
                    }
                    item(key = "username-field") {
                        EditorTextField(
                            label = stringResource(R.string.set_username_row),
                            value = username,
                            onValueChange = { username = it; saveError = null },
                            placeholder = stringResource(R.string.set_username_row),
                            maxLength = null,
                            multiline = false,
                            enabled = !saving,
                            bodyFont = bodyFont,
                        )
                    }
                    item(key = "custom-status-field") {
                        EditorTextField(
                            label = stringResource(R.string.profile_custom_status),
                            value = customStatus,
                            onValueChange = { customStatus = it; saveError = null },
                            placeholder = stringResource(R.string.edit_profile_status_hint),
                            maxLength = STATUS_MAX_LENGTH,
                            multiline = false,
                            enabled = !saving,
                            bodyFont = bodyFont,
                        )
                    }
                    item(key = "bio-field") {
                        EditorTextField(
                            label = stringResource(R.string.profile_about_me),
                            value = bio,
                            onValueChange = { bio = it; saveError = null },
                            placeholder = stringResource(R.string.profile_about_default),
                            maxLength = BIO_MAX_LENGTH,
                            multiline = true,
                            enabled = !saving,
                            bodyFont = bodyFont,
                        )
                    }
                    item(key = "online-status") {
                        OnlineStatusEditor(
                            checked = showOnline,
                            legendary = legendaryUp,
                            lockedMessage = planLockedOnline,
                            enabled = !saving,
                            bodyFont = bodyFont,
                            onCheckedChange = { checked ->
                                if (!legendaryUp) showUpgradePrompt(planLockedOnline)
                                else {
                                    showOnline = checked
                                    saveError = null
                                }
                            },
                        )
                    }
                }
            }

            item(key = "editor-bottom-space") { Spacer(Modifier.height(4.dp)) }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(palette.barSurface)
                .navigationBarsPadding(),
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                saveError?.let { error ->
                    Text(
                        text = error,
                        modifier = Modifier.fillMaxWidth(),
                        style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = EditDanger,
                    )
                }
                SaveProfileButton(
                    enabled = dirty && profile != null && !saving,
                    saving = saving,
                    bodyFont = bodyFont,
                    onClick = ::saveChanges,
                )
            }
        }
    }

    SnackbarHost(
        hostState = snackbarHostState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 100.dp)
            .align(Alignment.BottomCenter),
    ) { data ->
        Snackbar(
            containerColor = palette.surface,
            contentColor = palette.text,
            actionContentColor = EditBlueLight,
            dismissActionContentColor = palette.textMuted,
            shape = RoundedCornerShape(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    data.visuals.message,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp),
                )
                data.visuals.actionLabel?.let { action ->
                    TextButton(onClick = { data.performAction() }) {
                        Text(action, fontFamily = bodyFont, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            containerColor = palette.surface,
            titleContentColor = palette.text,
            textContentColor = palette.textMuted,
            title = {
                Text(
                    stringResource(R.string.edit_profile_discard_title),
                    style = TextStyle(fontFamily = bodyFont, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                )
            },
            text = {
                Text(
                    stringResource(R.string.edit_profile_discard_body),
                    style = TextStyle(fontFamily = bodyFont, fontSize = 14.sp),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onBack()
                }) {
                    Text(
                        stringResource(R.string.edit_profile_discard),
                        color = EditDanger,
                        fontFamily = bodyFont,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text(
                        stringResource(R.string.edit_profile_keep_editing),
                        color = EditBlueLight,
                        fontFamily = bodyFont,
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
        )
    }
}

@Composable
private fun EditorTopBar(
    title: String,
    isRtl: Boolean,
    onBack: () -> Unit,
    enabled: Boolean,
) {
    val palette = LocalHomePalette.current
    val titleFont = if (isRtl) TajawalFont else PixelFont
    val displayTitle = if (isRtl) title else title.uppercase(Locale.ROOT)
    Column(Modifier.fillMaxWidth().background(palette.barSurface)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(palette.surface)
                    .clickable(enabled = enabled, onClick = onBack)
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = palette.text,
                    modifier = Modifier.size(20.dp),
                )
            }
            Text(
                text = displayTitle,
                modifier = Modifier.weight(1f),
                style = TextStyle(
                    fontFamily = titleFont,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = if (isRtl) 0.sp else 1.sp,
                ),
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
    }
}

@Composable
private fun EditorTabs(
    selected: EditorTab,
    bodyFont: FontFamily,
    enabled: Boolean,
    onSelect: (EditorTab) -> Unit,
) {
    val palette = LocalHomePalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        EditorTab.values().forEach { tab ->
            val isSelected = tab == selected
            val (label, icon) = when (tab) {
                EditorTab.Avatar -> stringResource(R.string.edit_profile_avatar_label) to Icons.Filled.Person
                EditorTab.Banners -> stringResource(R.string.profile_banner_section) to Icons.Filled.Image
                EditorTab.Item -> stringResource(R.string.edit_profile_tab_item) to Icons.Filled.Star
                EditorTab.About -> stringResource(R.string.edit_profile_tab_about) to Icons.Filled.Info
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) palette.blue else Color.Transparent)
                    .clickable(enabled = enabled) { onSelect(tab) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (isSelected) Color.White else palette.textMuted,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = label,
                    style = TextStyle(fontFamily = bodyFont, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (isSelected) Color.White else palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}


@Composable
private fun EditorCollectionChips(
    options: List<EditorCollectionOption>,
    selected: String?,
    bodyFont: FontFamily,
    onSelect: (String?) -> Unit,
) {
    val palette = LocalHomePalette.current
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options, key = { it.key ?: "all-collections" }) { option ->
            val active = option.key == selected
            val shape = RoundedCornerShape(20.dp)
            Row(
                modifier = Modifier
                    .height(40.dp)
                    .clip(shape)
                    .background(if (active) palette.blue else palette.surface)
                    .border(if (active) 0.dp else 1.dp, if (active) palette.blue else palette.border, shape)
                    .clickable { onSelect(option.key) }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (option.locked) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = null,
                        tint = if (active) Color.White else palette.textMuted,
                        modifier = Modifier.size(13.dp),
                    )
                }
                Text(
                    option.title,
                    style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (active) Color.White else palette.chipText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditorCollectionHeader(
    title: String,
    count: Int,
    accent: Color,
    bodyFont: FontFamily,
) {
    val palette = LocalHomePalette.current
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 5.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(width = 5.dp, height = 13.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Text(
            title,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            count.toString(),
            style = TextStyle(fontFamily = NumberFont, fontSize = 10.sp, fontWeight = FontWeight.Bold),
            color = palette.textMuted,
        )
    }
}

@Composable
private fun ProfilePreviewCard(
    displayName: String,
    usernameLine: String,
    avatarUrl: String?,
    bannerUrl: String?,
    itemUrl: String?,
    customStatus: String,
    isOnline: Boolean,
    bodyFont: FontFamily,
) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape),
    ) {
        Box(Modifier.fillMaxWidth().height(138.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .align(Alignment.TopCenter),
            ) {
                if (bannerUrl != null) {
                    BannerThumb(
                        url = bannerUrl,
                        modifier = Modifier.fillMaxSize(),
                        animate = true,
                        radius = 0.dp,
                    )
                } else {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(listOf(palette.surfaceHigh, palette.surface))),
                    )
                }
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, palette.surface.copy(alpha = 0.92f)),
                            ),
                        ),
                )
                Text(
                    text = stringResource(R.string.edit_profile_preview),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 12.dp, top = 10.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(palette.bg.copy(alpha = 0.70f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    style = TextStyle(fontFamily = bodyFont, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
                    color = palette.text,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PreviewAvatar(url = avatarUrl, online = isOnline)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Text(
                            text = displayName.ifBlank { "—" },
                            style = TextStyle(fontFamily = bodyFont, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                            color = palette.text,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!itemUrl.isNullOrBlank()) {
                            CoverImage(
                                url = itemUrl,
                                modifier = Modifier.size(20.dp),
                                radius = 6.dp,
                                animate = true,
                                backgroundColor = palette.surfaceHigh,
                            )
                        }
                    }
                    Text(
                        text = usernameLine,
                        style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = if (isOnline) EditOnlineText else palette.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        if (customStatus.isNotBlank()) {
            Text(
                text = customStatus,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 14.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(palette.barSurface)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp),
                color = palette.chipText,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun PreviewAvatar(url: String?, online: Boolean) {
    val palette = LocalHomePalette.current
    Box(Modifier.size(76.dp)) {
        ComposeImage(
            painter = painterResource(R.drawable.ic_default_profile),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .border(4.dp, palette.surface, CircleShape),
        )
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .border(4.dp, palette.surface, CircleShape),
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(
                    if (online) Brush.radialGradient(listOf(Color(0xFF8CF2AD), Color(0xFF2FB35C)))
                    else SolidColor(palette.textMuted),
                )
                .border(2.dp, palette.surface, CircleShape),
        )
    }
}


@Composable
private fun ItemChatPreviewCard(
    displayName: String,
    avatarUrl: String?,
    itemUrl: String?,
    online: Boolean,
    bodyFont: FontFamily,
) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Text(
            stringResource(R.string.plan_item_section),
            style = TextStyle(fontFamily = bodyFont, fontSize = 11.sp, fontWeight = FontWeight.Bold),
            color = palette.textMuted,
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(38.dp)) {
                ComposeImage(
                    painter = painterResource(R.drawable.ic_default_profile),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(CircleShape),
                )
                if (!avatarUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (online) EditGreen else palette.textMuted)
                        .border(1.5.dp, palette.surface, CircleShape),
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val previewName = if (displayName.isBlank()) stringResource(R.string.common_guest) else displayName
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        previewName,
                        style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                        color = palette.blueLink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!itemUrl.isNullOrBlank()) {
                        CoverImage(
                            url = itemUrl,
                            modifier = Modifier.size(22.dp),
                            radius = 6.dp,
                            animate = true,
                            backgroundColor = palette.surfaceHigh,
                        )
                    }
                }
                Text(
                    stringResource(R.string.edit_profile_message_preview),
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(palette.surfaceHigh)
                        .padding(horizontal = 10.dp, vertical = 7.dp),
                    style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp),
                    color = palette.text,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EditorSectionHeader(title: String, accent: Color, isRtl: Boolean) {
    val font = if (isRtl) TajawalFont else PixelFont
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(width = 6.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(
            text = if (isRtl) title else title.uppercase(Locale.ROOT),
            style = TextStyle(
                fontFamily = font,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (isRtl) 0.sp else 1.sp,
            ),
            color = accent,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EditorSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    bodyFont: FontFamily,
) {
    val palette = LocalHomePalette.current
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 48.dp),
        enabled = enabled,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        placeholder = {
            Text(
                stringResource(R.string.profile_assets_search),
                style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        leadingIcon = {
            Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(20.dp))
        },
        textStyle = TextStyle(fontFamily = bodyFont, fontSize = 14.sp, color = palette.text),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search, capitalization = KeyboardCapitalization.None),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = palette.text,
            unfocusedTextColor = palette.text,
            focusedContainerColor = palette.surface,
            unfocusedContainerColor = palette.surface,
            focusedBorderColor = EditBlue,
            unfocusedBorderColor = palette.border,
            focusedPlaceholderColor = palette.textMuted,
            unfocusedPlaceholderColor = palette.textMuted,
            cursorColor = EditBlueLight,
        ),
    )
}

@Composable
private fun EditorTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    maxLength: Int?,
    multiline: Boolean,
    enabled: Boolean,
    bodyFont: FontFamily,
) {
    val palette = LocalHomePalette.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp).padding(bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                label,
                modifier = Modifier.weight(1f),
                style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = palette.chipText,
            )
            maxLength?.let { limit ->
                Text(
                    stringResource(R.string.edit_profile_counter, value.length, limit),
                    style = TextStyle(fontFamily = NumberFont, fontSize = 11.sp, fontWeight = FontWeight.Medium),
                    color = palette.textMuted,
                )
            }
        }
        OutlinedTextField(
            value = value,
            onValueChange = { newValue -> onValueChange(maxLength?.let { newValue.take(it) } ?: newValue) },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (multiline) 96.dp else 44.dp),
            enabled = enabled,
            placeholder = {
                Text(
                    placeholder,
                    style = TextStyle(fontFamily = bodyFont, fontSize = 14.sp),
                    color = palette.textMuted,
                )
            },
            textStyle = TextStyle(fontFamily = bodyFont, fontSize = 14.sp, color = palette.text),
            singleLine = !multiline,
            minLines = if (multiline) 4 else 1,
            maxLines = if (multiline) 6 else 1,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(
                imeAction = if (multiline) ImeAction.Default else ImeAction.Next,
                capitalization = KeyboardCapitalization.Sentences,
            ),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = palette.text,
                unfocusedTextColor = palette.text,
                focusedContainerColor = palette.surface,
                unfocusedContainerColor = palette.surface,
                focusedBorderColor = EditBlue,
                unfocusedBorderColor = palette.border,
                focusedPlaceholderColor = palette.textMuted,
                unfocusedPlaceholderColor = palette.textMuted,
                cursorColor = EditBlueLight,
            ),
        )
    }
}

@Composable
private fun OnlineStatusEditor(
    checked: Boolean,
    legendary: Boolean,
    lockedMessage: String,
    enabled: Boolean,
    bodyFont: FontFamily,
    onCheckedChange: (Boolean) -> Unit,
) {
    val palette = LocalHomePalette.current
    val statusLabel = stringResource(R.string.profile_online_status)
    val hiddenForOthers = stringResource(R.string.profile_hidden_from_others)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = palette.barSurface,
                    checkedTrackColor = EditGreen,
                    checkedBorderColor = EditGreen,
                    uncheckedThumbColor = palette.textMuted,
                    uncheckedTrackColor = palette.surfaceHigh,
                    uncheckedBorderColor = palette.border,
                ),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = statusLabel,
                    style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    color = palette.text,
                )
                when {
                    !legendary -> Text(
                        lockedMessage,
                        style = TextStyle(fontFamily = bodyFont, fontSize = 11.sp),
                        color = palette.textMuted,
                    )
                    !checked -> Text(
                        hiddenForOthers,
                        style = TextStyle(fontFamily = bodyFont, fontSize = 11.sp),
                        color = palette.textMuted,
                    )
                }
            }
            if (!legendary) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = EditGold, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
private fun RemoveBannerButton(enabled: Boolean, bodyFont: FontFamily, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, if (enabled) EditDangerBorder else EditDangerBorder.copy(alpha = 0.38f), RoundedCornerShape(12.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            stringResource(R.string.profile_banner_clear),
            style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
            color = if (enabled) EditDanger else EditDanger.copy(alpha = 0.45f),
        )
    }
}

@Composable
private fun FreeBannerState(bodyFont: FontFamily) {
    val palette = LocalHomePalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            stringResource(R.string.profile_banner_none_plain),
            style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            color = palette.chipText,
        )
        Text(
            stringResource(R.string.plan_locked_banner_note_plain),
            style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp),
            color = palette.textMuted,
        )
    }
}

@Composable
private fun AssetGridRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        content = content,
    )
}

@Composable
private fun AvatarAssetTile(
    modifier: Modifier,
    asset: SiteAsset,
    selected: Boolean,
    locked: Boolean,
    planName: String,
    contentDescription: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .semantics(mergeDescendants = true) {
                this.contentDescription = contentDescription
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(palette.surfaceHigh)
                .border(if (selected) 3.dp else 1.dp, if (selected) EditBlue else palette.border, shape),
        ) {
            CoverImage(
                url = assetUrl(rememberContainer(), asset.fileUrl),
                modifier = Modifier.fillMaxSize(),
                radius = 18.dp,
                animate = false,
                backgroundColor = palette.surfaceHigh,
            )
            if (locked) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x9E0A0F1C), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        PlanChip(planName)
                    }
                }
            }
        }
        if (selected) SelectionBadge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
    }
}

@Composable
private fun BannerAssetTile(
    modifier: Modifier,
    asset: SiteAsset,
    selected: Boolean,
    locked: Boolean,
    planName: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val accessibilityLabel = if (locked) {
        stringResource(R.string.cd_avatar_locked, asset.name, planName)
    } else {
        asset.name
    }
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .aspectRatio(16f / 7f)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(shape)
                .background(palette.surfaceHigh)
                .border(if (selected) 3.dp else 1.dp, if (selected) EditBlue else palette.border, shape),
        ) {
            BannerThumb(
                url = assetUrl(rememberContainer(), asset.fileUrl),
                modifier = Modifier.fillMaxSize(),
                animate = false,
                radius = 14.dp,
            )
            if (locked) {
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0x9E0A0F1C), shape),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                        PlanChip(planName)
                    }
                }
            }
        }
        if (selected) SelectionBadge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
    }
}


@Composable
private fun NoneItemRow(
    selected: Boolean,
    bodyFont: FontFamily,
    onClick: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(62.dp)
            .clip(shape)
            .background(if (selected) palette.blue.copy(alpha = 0.10f) else palette.surface)
            .border(if (selected) 2.dp else 1.dp, if (selected) EditBlue else palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(42.dp).clip(CircleShape).background(palette.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Block, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(20.dp))
        }
        Text(
            stringResource(R.string.profile_item_none),
            modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
            color = palette.text,
        )
        if (selected) {
            Text(
                stringResource(R.string.profile_item_equipped),
                modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(EditBlue).padding(horizontal = 8.dp, vertical = 3.dp),
                style = TextStyle(fontFamily = bodyFont, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ItemAssetTile(
    modifier: Modifier,
    asset: SiteAsset,
    selected: Boolean,
    locked: Boolean,
    planName: String,
    equipped: Boolean,
    enabled: Boolean,
    bodyFont: FontFamily,
    onClick: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val accessibilityLabel = if (locked) {
        stringResource(R.string.cd_avatar_locked, asset.name, planName)
    } else {
        asset.name
    }
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (selected) palette.blue.copy(alpha = 0.12f) else palette.surface)
            .border(if (selected) 3.dp else 1.dp, if (selected) EditBlue else palette.border, shape)
            .semantics(mergeDescendants = true) {
                contentDescription = accessibilityLabel
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            CoverImage(
                url = assetUrl(rememberContainer(), asset.fileUrl),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                radius = 10.dp,
                animate = false,
                backgroundColor = palette.surfaceHigh,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                asset.name,
                style = TextStyle(fontFamily = bodyFont, fontSize = 9.sp, fontWeight = FontWeight.Bold),
                color = if (selected) EditBlueLight else palette.textMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
        if (locked) {
            Box(
                Modifier.fillMaxSize().clip(shape).background(palette.bg.copy(alpha = 0.58f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    PlanChip(planName)
                }
            }
        }
        if (selected) SelectionBadge(Modifier.align(Alignment.TopEnd).offset(x = 6.dp, y = (-6).dp))
        if (equipped) {
            Text(
                stringResource(R.string.profile_item_equipped),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = (-7).dp)
                    .padding(start = 6.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(EditBlue)
                    .padding(horizontal = 7.dp, vertical = 2.dp),
                style = TextStyle(fontFamily = bodyFont, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun SelectionBadge(modifier: Modifier) {
    Box(
        modifier = modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(EditBlue)
            .border(2.dp, LocalHomePalette.current.bg, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
    }
}

@Composable
private fun PlanChip(planName: String) {
    val palette = LocalHomePalette.current
    val isLegendary = planName.equals(stringResource(R.string.plan_legendary_name), ignoreCase = true)
    val isAdminOnly = planName.equals(stringResource(R.string.chat_admin_only_short), ignoreCase = true)
    val background = when {
        isLegendary -> EditGold
        isAdminOnly -> palette.surfaceHigh
        else -> EditEpic
    }
    val foreground = when {
        isLegendary -> Color(0xFF1B1405)
        isAdminOnly -> palette.text
        else -> Color.White
    }
    Text(
        text = planName,
        modifier = Modifier.clip(RoundedCornerShape(7.dp)).background(background).padding(horizontal = 7.dp, vertical = 1.dp),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
        color = foreground,
        maxLines = 1,
    )
}

@Composable
private fun UpgradeCard(message: String, bodyFont: FontFamily, onUpgrade: () -> Unit) {
    val palette = LocalHomePalette.current
    val upgradeColors = if (palette.isDark) {
        listOf(UpgradeStart, UpgradeEnd)
    } else {
        listOf(Color(0xFFFFF3D2), Color(0xFFFFFBF3))
    }
    val upgradeBorder = if (palette.isDark) UpgradeBorder else Color(0xFFE0B744)
    val upgradeText = if (palette.isDark) UpgradeText else Color(0xFF6B4D0A)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(upgradeColors))
            .border(1.dp, upgradeBorder, RoundedCornerShape(16.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = EditGold, modifier = Modifier.size(22.dp))
        Text(
            message,
            modifier = Modifier.weight(1f),
            style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            color = upgradeText,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Box(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(EditGold)
                .clickable(onClick = onUpgrade)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.item_upgrade),
                style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                color = Color(0xFF1B1405),
            )
        }
    }
}

@Composable
private fun EditorAssetSkeleton(columns: Int, ratio: Float, bodyFont: FontFamily) {
    val palette = LocalHomePalette.current
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "profile-assets-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.78f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "profile-assets-skeleton-alpha",
    )
    val shimmer = palette.surfaceHigh.copy(alpha = alpha)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        repeat(2) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                repeat(columns) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .aspectRatio(ratio)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shimmer),
                    )
                }
            }
        }
        Text(
            stringResource(R.string.profile_assets_loading),
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(fontFamily = bodyFont, fontSize = 12.sp),
            color = palette.textMuted,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun AssetsRetryCard(bodyFont: FontFamily, onRetry: () -> Unit) {
    val palette = LocalHomePalette.current
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            stringResource(R.string.msg_network_error),
            style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp),
            color = palette.textMuted,
            textAlign = TextAlign.Center,
        )
        TextButton(onClick = onRetry) {
            Text(
                stringResource(R.string.common_retry),
                style = TextStyle(fontFamily = bodyFont, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                color = EditBlueLight,
            )
        }
    }
}

@Composable
private fun SaveProfileButton(
    enabled: Boolean,
    saving: Boolean,
    bodyFont: FontFamily,
    onClick: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .then(
                if (enabled) Modifier.shadow(
                    elevation = 8.dp,
                    shape = shape,
                    ambientColor = EditBlue.copy(alpha = 0.40f),
                    spotColor = EditBlue.copy(alpha = 0.40f),
                ) else Modifier,
            )
            .clip(shape)
            .background(
                if (enabled) Brush.linearGradient(listOf(EditBlue, EditBlueLight))
                else SolidColor(palette.surfaceHigh),
                shape,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
            if (saving) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    color = Color.White,
                    strokeWidth = 2.dp,
                )
                Text(
                    stringResource(R.string.msg_saving),
                    style = TextStyle(fontFamily = bodyFont, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                    color = Color.White,
                )
            } else {
                Text(
                    stringResource(R.string.common_save),
                    style = TextStyle(fontFamily = bodyFont, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (enabled) Color.White else palette.textMuted,
                )
            }
        }
    }
}

@Composable
private fun assetPlanName(requiredPlan: String): String = when (requiredPlan.lowercase(Locale.ROOT)) {
    "free" -> stringResource(R.string.plan_free_name)
    "legendary" -> stringResource(R.string.plan_legendary_name)
    "admin" -> stringResource(R.string.chat_admin_only_short)
    else -> stringResource(R.string.plan_epic_name)
}
