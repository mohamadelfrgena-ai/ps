package com.addonstation.mobile.ui.home

import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.text.BidiFormatter
import coil.compose.AsyncImage
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ProfileLoadStatus
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorCard
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.NumberFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** Main home screen: content-first layout, dark/light palettes and automatic RTL. */
data class HomeActions(
    val openStore: () -> Unit,
    val openFavorites: () -> Unit,
    val openDownloads: () -> Unit,
    val openPlans: () -> Unit,
    val openSupport: () -> Unit,
    val openCreators: () -> Unit,
    val openCreatorApply: () -> Unit,
    val openStudio: () -> Unit,
    val openExclusives: () -> Unit,
    val openProfile: () -> Unit,
    val openLogin: () -> Unit,
    val openSignup: () -> Unit,
    val openWallet: () -> Unit,
    val openNotifications: () -> Unit = {},
    val openItem: (ContentItem) -> Unit,
    val openCreatorProfile: (String) -> Unit,
)

@Composable
fun HomeScreen(
    actions: HomeActions,
    modifier: Modifier = Modifier,
    scrollToTopRequest: Int = 0,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val palette = LocalHomePalette.current
    val localeCode = appLocaleCode()
    val isArabic = localeCode.equals("ar", ignoreCase = true)
    val font = if (isArabic) TajawalFont else NunitoFont
    val locale = remember(localeCode) { Locale.forLanguageTag(localeCode) }
    val motionEnabled = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }

    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val profileLoadStatus by container.state.profileLoadStatus.collectAsState()
    val wallet by container.state.wallet.collectAsState()

    var profileRetryTick by remember { mutableStateOf(0) }
    var featured by remember { mutableStateOf(container.content.cachedFeatured()) }
    var latest by remember { mutableStateOf(container.content.cachedLatest()) }
    var recommended by remember { mutableStateOf(container.content.cachedRecommended()) }
    var creators by remember { mutableStateOf(container.creators.cachedCreators()) }
    var favorites by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(featured.isEmpty() && latest.isEmpty()) }
    var failed by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf(ChipFilter.All) }

    LaunchedEffect(profileRetryTick, signedIn) {
        loading = latest.isEmpty() && featured.isEmpty()
        failed = false
        try {
            coroutineScope {
                val featuredRequest = async(Dispatchers.IO) { container.content.featured() }
                val latestRequest = async(Dispatchers.IO) { container.content.latest() }
                val recommendedRequest = async(Dispatchers.IO) { container.content.recommended() }
                val creatorsRequest = async(Dispatchers.IO) { container.creators.creators() }
                val favoritesRequest = async(Dispatchers.IO) { container.content.favorites() }
                featured = featuredRequest.await()
                latest = latestRequest.await()
                recommended = recommendedRequest.await()
                creators = creatorsRequest.await()
                favorites = favoritesRequest.await()
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed = latest.isEmpty() && featured.isEmpty()
        } finally {
            loading = false
        }
    }

    fun retry() {
        profileRetryTick += 1
        if (signedIn && profile == null) {
            scope.launch {
                runCatching { withContext(Dispatchers.IO) { container.profile.ensureProfileLoaded() } }
            }
        }
    }

    val creatorNames = creators.associate { it.profile.id to (it.profile.username ?: it.profile.displayName ?: "") }
    fun creatorNameOf(item: ContentItem): String? =
        item.createdBy?.let { creatorNames[it] }?.takeIf(String::isNotBlank)

    val allItems = (latest + featured + recommended).distinctBy { it.id }
    val latestSource = if (chip == ChipFilter.Favorites) favorites else latest
    val latestShown = latestSource.filter { chip == ChipFilter.Favorites || chip.accepts(it) }
    val downloadedSource = if (chip == ChipFilter.Favorites) favorites else allItems
    val downloaded = downloadedSource
        .filter { chip == ChipFilter.Favorites || chip.accepts(it) }
        .sortedByDescending { it.downloadsCount }
        .take(6)
    val favoriteCreatorIds = favorites.mapNotNull { it.createdBy }.toSet()
    val exclusiveItems = allItems.filter { it.isExclusive }.distinctBy { it.id }
    val recommendedShown = if (chip == ChipFilter.Favorites) favorites else recommended.filter { chip.accepts(it) }
    val creatorsShown = when (chip) {
        ChipFilter.Favorites -> creators.filter { it.profile.id in favoriteCreatorIds }
        ChipFilter.Addons, ChipFilter.Worlds -> creators.filter { it.profile.isCreator }
        ChipFilter.All -> creators
    }

    Column(modifier.fillMaxSize().background(palette.bg)) {
        HomeTopBar(
            balance = wallet?.balance,
            palette = palette,
            onWallet = actions.openWallet,
            onNotifications = actions.openNotifications,
        )
        if (failed) {
            HomeErrorBanner(
                font = font,
                palette = palette,
                onRetry = ::retry,
            )
        }
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 12.dp),
        ) {
            item {
                HomeGreetingBlock(
                    signedIn = signedIn,
                    username = profile?.username.orEmpty(),
                    loadingProfile = profile == null && signedIn &&
                        profileLoadStatus in setOf(ProfileLoadStatus.IDLE, ProfileLoadStatus.LOADING),
                    profileFailed = profile == null && signedIn &&
                        profileLoadStatus !in setOf(ProfileLoadStatus.IDLE, ProfileLoadStatus.LOADING),
                    isArabic = isArabic,
                    font = font,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 4.dp),
                )
            }
            item {
                HomeSearch(
                    font = font,
                    palette = palette,
                    onClick = actions.openStore,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                )
            }
            item {
                HomeHero(
                    signedIn = signedIn,
                    isArabic = isArabic,
                    font = font,
                    palette = palette,
                    onStart = actions.openStore,
                    onJoin = actions.openSignup,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp),
                )
            }
            item {
                when {
                    !signedIn -> HomeGuestCard(
                        font = font,
                        palette = palette,
                        onSignIn = actions.openLogin,
                        onCreateAccount = actions.openSignup,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                    )
                    profile == null && profileLoadStatus in setOf(ProfileLoadStatus.IDLE, ProfileLoadStatus.LOADING) ->
                        HomePlanSkeleton(
                            palette = palette,
                            motionEnabled = motionEnabled,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                        )
                    profile == null -> HomeRetryCard(
                        title = androidx.compose.ui.res.stringResource(R.string.home_error),
                        action = androidx.compose.ui.res.stringResource(R.string.home_error_tap),
                        font = font,
                        palette = palette,
                        onRetry = ::retry,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                    )
                    profile?.effectivePlan == "legendary" -> Unit
                    profile?.effectivePlan == "free" -> HomePlanStrip(
                        title = androidx.compose.ui.res.stringResource(R.string.home_plan_card_free_title),
                        description = androidx.compose.ui.res.stringResource(R.string.home_plan_free_desc),
                        upgrade = androidx.compose.ui.res.stringResource(R.string.home_qa_upgrade),
                        font = font,
                        palette = palette,
                        onUpgrade = actions.openPlans,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                    )
                    else -> HomePlanStrip(
                        title = planLabelOf(profile?.effectivePlan ?: "free"),
                        description = if (profile?.planExpiresAt.isNullOrBlank()) {
                            androidx.compose.ui.res.stringResource(R.string.home_plan_active_forever)
                        } else {
                            androidx.compose.ui.res.stringResource(
                                R.string.home_plan_expires,
                                TimeFormat.expiryLabel(profile?.planExpiresAt, locale),
                            )
                        },
                        upgrade = null,
                        font = font,
                        palette = palette,
                        onUpgrade = null,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(top = 12.dp),
                    )
                }
            }
            item {
                HomeQuickActions(
                    font = font,
                    palette = palette,
                    actions = actions,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.padding(top = 14.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(
                        listOf(
                            ChipFilter.All to R.string.home_chip_all,
                            ChipFilter.Addons to R.string.home_chip_addons,
                            ChipFilter.Worlds to R.string.home_chip_worlds,
                            ChipFilter.Favorites to R.string.home_chip_favorites,
                        ),
                        key = { it.first.name },
                    ) { (filter, labelRes) ->
                        HomeChip(
                            text = androidx.compose.ui.res.stringResource(labelRes),
                            selected = chip == filter,
                            font = font,
                            palette = palette,
                            onClick = {
                                chip = filter
                                if (filter == ChipFilter.Favorites) {
                                    scope.launch {
                                        runCatching { withContext(Dispatchers.IO) { container.content.favorites() } }
                                            .onSuccess { favorites = it }
                                    }
                                }
                            },
                        )
                    }
                }
            }
            item {
                HomeSectionHeader(
                    title = androidx.compose.ui.res.stringResource(R.string.home_latest_plain),
                    isArabic = isArabic,
                    font = font,
                    palette = palette,
                    onAction = actions.openStore,
                )
                when {
                    loading && latestShown.isEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(4) { HomeLatestSkeleton(palette, motionEnabled) }
                    }
                    latestShown.isEmpty() -> HomeEmptyNote(font, palette)
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(latestShown.take(12), key = { it.id }) { item ->
                            HomeLatestCard(
                                item = item,
                                byName = creatorNameOf(item),
                                font = font,
                                palette = palette,
                                isArabic = isArabic,
                                onClick = { actions.openItem(item) },
                            )
                        }
                    }
                }
            }
            item {
                HomeSectionHeader(
                    title = androidx.compose.ui.res.stringResource(R.string.home_recommended_plain),
                    isArabic = isArabic,
                    font = font,
                    palette = palette,
                    onAction = actions.openStore,
                )
                when {
                    loading && recommendedShown.isEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { items(4) { HomeLatestSkeleton(palette, motionEnabled) } }
                    recommendedShown.isEmpty() -> HomeEmptyNote(font, palette)
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(recommendedShown.take(12), key = { it.id }) { item ->
                            HomeLatestCard(
                                item = item,
                                byName = creatorNameOf(item),
                                font = font,
                                palette = palette,
                                isArabic = isArabic,
                                onClick = { actions.openItem(item) },
                            )
                        }
                    }
                }
            }
            item {
                HomeSectionHeader(
                    title = androidx.compose.ui.res.stringResource(R.string.home_exclusives_plain),
                    isArabic = isArabic,
                    font = font,
                    palette = palette,
                    onAction = actions.openExclusives,
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.home_exclusives_sub),
                    style = homeTextStyle(font, 13.sp, FontWeight.Medium, 18.sp),
                    color = palette.textMuted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
                when {
                    loading && exclusiveItems.isEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) { items(2) { HomeExclusiveSkeleton(palette, motionEnabled) } }
                    exclusiveItems.isEmpty() -> HomeEmptyNote(font, palette)
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(exclusiveItems.take(12), key = { it.id }) { item ->
                            HomeExclusiveCard(
                                item = item,
                                byName = creatorNameOf(item),
                                font = font,
                                palette = palette,
                                isArabic = isArabic,
                                onClick = { actions.openItem(item) },
                            )
                        }
                    }
                }
            }
            item {
                HomeSectionHeader(
                    title = androidx.compose.ui.res.stringResource(R.string.home_most_downloaded),
                    isArabic = isArabic,
                    font = font,
                    palette = palette,
                    onAction = actions.openStore,
                )
                when {
                    loading && downloaded.isEmpty() -> Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) { repeat(3) { HomeDownloadSkeleton(palette, motionEnabled) } }
                    downloaded.isEmpty() -> HomeEmptyNote(font, palette)
                    else -> Column(
                        Modifier.padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        downloaded.forEach { item ->
                            HomeDownloadRow(
                                item = item,
                                byName = creatorNameOf(item),
                                font = font,
                                palette = palette,
                                isArabic = isArabic,
                                onClick = { actions.openItem(item) },
                            )
                        }
                    }
                }
            }
            item {
                HomeSectionHeader(
                    title = androidx.compose.ui.res.stringResource(R.string.home_featured_creators),
                    isArabic = isArabic,
                    font = font,
                    palette = palette,
                    onAction = actions.openCreators,
                )
                Text(
                    androidx.compose.ui.res.stringResource(R.string.home_creators_caption),
                    style = homeTextStyle(font, 12.sp, FontWeight.Medium, 16.sp),
                    color = palette.textMuted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                )
                when {
                    loading && creatorsShown.isEmpty() -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(4) { HomeCreatorSkeleton(palette, motionEnabled) }
                    }
                    creatorsShown.isEmpty() -> HomeEmptyNote(font, palette)
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(creatorsShown.take(12), key = { it.profile.id }) { card ->
                            HomeCreatorCard(
                                card = card,
                                palette = palette,
                                font = font,
                                onClick = { actions.openCreatorProfile(card.profile.id) },
                            )
                        }
                    }
                }
            }
            item {
                HomeCreatorProgramCard(
                    isCreator = profile?.isCreator == true,
                    font = font,
                    palette = palette,
                    onBecomeCreator = actions.openCreatorApply,
                    onOpenDashboard = actions.openStudio,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 22.dp, bottom = 30.dp),
                )
            }
        }
    }
}

private enum class ChipFilter {
    All, Addons, Worlds, Favorites;

    fun accepts(item: ContentItem): Boolean = when (this) {
        All, Favorites -> true
        Addons -> item.contentType != "world"
        Worlds -> item.contentType == "world"
    }
}

@Composable
private fun HomeTopBar(
    balance: Int?,
    palette: HomePalette,
    onWallet: () -> Unit,
    onNotifications: () -> Unit,
) {
    val walletDescription = stringResource(R.string.cd_open_wallet)
    val activityDescription = stringResource(R.string.cd_new_activity)
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
        )
        Text(
            stringResource(R.string.brand_wordmark).uppercase(Locale.ROOT),
            style = TextStyle(fontFamily = PixelFont, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = palette.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .height(44.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(palette.surfaceHigh)
                .clickable(onClick = onWallet)
                .semantics { contentDescription = walletDescription }
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.coin_ac),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
            Text(
                text = (balance ?: 0).toString(),
                style = TextStyle(fontFamily = NumberFont, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                color = palette.goldText,
                maxLines = 1,
            )
        }
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .border(1.dp, palette.border, CircleShape)
                .clickable(onClick = onNotifications)
                .semantics { contentDescription = activityDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = palette.text, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun HomeGreetingBlock(
    signedIn: Boolean,
    username: String,
    loadingProfile: Boolean,
    profileFailed: Boolean,
    isArabic: Boolean,
    font: FontFamily,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            stringResource(TimeFormat.greetingRes(TimeFormat.isMorning())),
            style = homeTextStyle(font, 13.sp, FontWeight.Bold, 18.sp),
            color = LocalHomePalette.current.textMuted,
        )
        Spacer(Modifier.height(3.dp))
        when {
            !signedIn -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    stringResource(R.string.home_welcome_guest),
                    style = homeTextStyle(font, 22.sp, FontWeight.ExtraBold, 29.sp),
                    color = LocalHomePalette.current.text,
                )
                Text(
                    stringResource(R.string.brand_wordmark).uppercase(Locale.ROOT),
                    style = TextStyle(fontFamily = PixelFont, fontSize = 15.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                    color = LocalHomePalette.current.text,
                    maxLines = 1,
                )
            }
            loadingProfile -> Text(
                stringResource(R.string.common_loading),
                style = homeTextStyle(font, 22.sp, FontWeight.ExtraBold, 29.sp),
                color = LocalHomePalette.current.text,
                maxLines = 1,
            )
            profileFailed -> Text(
                stringResource(R.string.home_error),
                style = homeTextStyle(font, 22.sp, FontWeight.ExtraBold, 29.sp),
                color = LocalHomePalette.current.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            else -> Text(
                stringResource(
                    R.string.home_welcome_back,
                    BidiFormatter.getInstance(isArabic).unicodeWrap(username),
                ),
                style = homeTextStyle(font, 22.sp, FontWeight.ExtraBold, 29.sp),
                color = LocalHomePalette.current.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeSearch(
    font: FontFamily,
    palette: HomePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(20.dp))
        Text(
            stringResource(R.string.home_search_hint),
            style = homeTextStyle(font, 14.sp, FontWeight.Medium, 19.sp),
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeHero(
    signedIn: Boolean,
    isArabic: Boolean,
    font: FontFamily,
    palette: HomePalette,
    onStart: () -> Unit,
    onJoin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.fillMaxWidth().heightIn(min = 190.dp).clip(RoundedCornerShape(20.dp)),
    ) {
        Image(
            painter = painterResource(R.drawable.hero_home),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.05f to palette.heroOverlay.copy(alpha = 0.05f),
                    0.80f to palette.heroOverlay.copy(alpha = 0.92f),
                    1f to palette.heroOverlay.copy(alpha = 0.96f),
                ),
            ),
        )
        Row(
            modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    stringResource(R.string.home_hero_slogan),
                    style = if (isArabic) {
                        homeTextStyle(font, 20.sp, FontWeight.ExtraBold, 27.sp)
                    } else {
                        TextStyle(fontFamily = PixelFont, fontSize = 15.sp, lineHeight = 22.5.sp, fontWeight = FontWeight.Bold)
                    },
                    color = palette.goldFill,
                )
                Text(
                    stringResource(R.string.home_hero_subtitle),
                    style = homeTextStyle(font, 12.sp, FontWeight.Medium, 16.sp),
                    color = palette.heroSubtitle,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (!signedIn) {
                    Box(
                        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                            .clickable(onClick = onJoin).padding(horizontal = 2.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        Text(
                            stringResource(R.string.home_hero_family),
                            style = homeTextStyle(font, 13.sp, FontWeight.Bold, 18.sp),
                            color = palette.goldFill,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            HomeActionButton(
                text = stringResource(R.string.home_hero_start),
                font = font,
                background = palette.blue,
                contentColor = palette.buttonText,
                onClick = onStart,
                horizontalPadding = 20.dp,
            )
        }
    }
}

@Composable
private fun HomePlanStrip(
    title: String,
    description: String,
    upgrade: String?,
    font: FontFamily,
    palette: HomePalette,
    onUpgrade: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier.fillMaxWidth()
            .homeCardShadow(palette, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(palette.planStart, palette.planEnd)))
            .border(1.dp, palette.planBorder, shape)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(palette.goldFill),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Star, contentDescription = null, tint = palette.goldInk, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = homeTextStyle(font, 14.sp, FontWeight.ExtraBold, 19.sp), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(description, style = homeTextStyle(font, 12.sp, FontWeight.Medium, 16.sp), color = palette.planSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (upgrade != null && onUpgrade != null) {
            HomeActionButton(
                text = upgrade,
                font = font,
                background = palette.goldFill,
                contentColor = palette.goldInk,
                onClick = onUpgrade,
                horizontalPadding = 18.dp,
                fontSize = 13.sp,
            )
        }
    }
}

@Composable
private fun HomeGuestCard(
    font: FontFamily,
    palette: HomePalette,
    onSignIn: () -> Unit,
    onCreateAccount: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier.fillMaxWidth().homeCardShadow(palette, shape).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            stringResource(R.string.auth_login_subtitle_plain),
            style = homeTextStyle(font, 14.sp, FontWeight.ExtraBold, 19.sp),
            color = palette.text,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            HomeActionButton(
                text = stringResource(R.string.auth_sign_in),
                font = font,
                background = palette.blue,
                contentColor = palette.buttonText,
                onClick = onSignIn,
                modifier = Modifier.weight(1f),
                horizontalPadding = 10.dp,
            )
            HomeActionButton(
                text = stringResource(R.string.auth_create_account),
                font = font,
                background = palette.surface,
                contentColor = palette.text,
                outline = palette.border,
                onClick = onCreateAccount,
                modifier = Modifier.weight(1f),
                horizontalPadding = 8.dp,
            )
        }
    }
}

@Composable
private fun HomeChip(
    text: String,
    selected: Boolean,
    font: FontFamily,
    palette: HomePalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier.height(44.dp).clip(shape)
            .background(if (selected) palette.blue else palette.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, palette.border, shape))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = homeTextStyle(font, 14.sp, if (selected) FontWeight.ExtraBold else FontWeight.Bold, 19.sp),
            color = if (selected) palette.buttonText else palette.chipText,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun HomeQuickActions(
    font: FontFamily,
    palette: HomePalette,
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        title = stringResource(R.string.home_quick_actions_plain),
        isArabic = appLocaleCode().equals("ar", ignoreCase = true),
        font = font,
        palette = palette,
        onAction = actions.openStore,
    )
    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        HomeQuickActionTile(
            icon = Icons.Filled.Favorite,
            label = stringResource(R.string.home_qa_favorites),
            accent = Color(0xFFFF6B78),
            font = font,
            palette = palette,
            onClick = actions.openFavorites,
            modifier = Modifier.weight(1f),
        )
        HomeQuickActionTile(
            icon = Icons.Filled.FileDownload,
            label = stringResource(R.string.home_qa_downloads),
            accent = palette.blueLink,
            font = font,
            palette = palette,
            onClick = actions.openDownloads,
            modifier = Modifier.weight(1f),
        )
        HomeQuickActionTile(
            icon = Icons.Filled.WorkspacePremium,
            label = stringResource(R.string.home_qa_upgrade),
            accent = palette.goldText,
            font = font,
            palette = palette,
            onClick = actions.openPlans,
            modifier = Modifier.weight(1f),
        )
        HomeQuickActionTile(
            icon = Icons.Filled.PhoneAndroid,
            label = stringResource(R.string.home_qa_device),
            accent = Color(0xFF3FBF6A),
            font = font,
            palette = palette,
            onClick = null, // Destination intentionally remains unset until a device action is specified.
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun HomeQuickActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    accent: Color,
    font: FontFamily,
    palette: HomePalette,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier.heightIn(min = 84.dp).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape)
            .clickable(enabled = onClick != null) { onClick?.invoke() }.padding(horizontal = 4.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(40.dp).clip(RoundedCornerShape(12.dp))
                .background(accent.copy(alpha = 0.15f))
                .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = homeTextStyle(font, 12.sp, FontWeight.ExtraBold, 15.sp),
            color = palette.text,
            maxLines = 2,
            minLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeExclusiveCard(
    item: ContentItem,
    byName: String?,
    font: FontFamily,
    palette: HomePalette,
    isArabic: Boolean,
    onClick: () -> Unit,
) {
    val container = rememberContainer()
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier.width(260.dp).height(150.dp).clip(shape)
            .border(1.dp, palette.border, shape).clickable(onClick = onClick),
    ) {
        HomeContentImage(
            url = assetUrl(container, item.coverUrl),
            contentDescription = item.title,
            modifier = Modifier.fillMaxSize(),
            radius = 0.dp,
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.30f),
                    0.35f to Color.Transparent,
                    1f to Color(0xE00A0F1C),
                ),
            ),
        )
        homeBadgeFor(item, palette)?.let { (label, color, textColor) ->
            HomeBadge(
                text = label,
                background = color,
                contentColor = textColor,
                font = font,
                modifier = Modifier.align(Alignment.TopStart).padding(10.dp),
            )
        }
        Column(
            Modifier.align(Alignment.BottomStart).fillMaxWidth().padding(start = 14.dp, end = 14.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                item.title,
                style = homeTextStyle(font, 16.sp, FontWeight.ExtraBold, 20.sp),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                byName?.let { stringResource(R.string.item_by, BidiFormatter.getInstance(isArabic).unicodeWrap(it)) }
                    ?: item.category.orEmpty(),
                style = homeTextStyle(font, 12.sp, FontWeight.Medium, 16.sp),
                color = Color(0xFFC4D1E8),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeExclusiveSkeleton(palette: HomePalette, motionEnabled: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Box(Modifier.width(260.dp).height(150.dp).clip(shape)) {
        HomeSkeletonBlock(Modifier.fillMaxSize(), palette, motionEnabled, radius = 18.dp)
        Box(Modifier.fillMaxSize().border(1.dp, palette.border, shape))
    }
}

@Composable
private fun HomeCreatorProgramCard(
    isCreator: Boolean,
    font: FontFamily,
    palette: HomePalette,
    onBecomeCreator: () -> Unit,
    onOpenDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isArabic = appLocaleCode().equals("ar", ignoreCase = true)
    val shape = RoundedCornerShape(20.dp)
    val colors = if (palette.isDark) listOf(Color(0xFF102244), Color(0xFF121A2B))
        else listOf(Color(0xFFE6EEFF), Color.White)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(colors))
            .border(1.dp, palette.blue.copy(alpha = 0.45f), shape)
            .padding(16.dp),
    ) {
        Text(
            stringResource(R.string.home_creator_kicker),
            style = TextStyle(
                fontFamily = if (isArabic) font else PixelFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (isArabic) 0.sp else 1.sp,
            ),
            color = palette.goldText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(if (isCreator) R.string.home_creator_dashboard else R.string.home_creator_title),
            style = homeTextStyle(font, 20.sp, FontWeight.ExtraBold, 25.sp),
            color = palette.text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            stringResource(R.string.home_creator_text),
            style = homeTextStyle(font, 13.sp, FontWeight.Medium, 20.sp),
            color = palette.textMuted,
        )
        Spacer(Modifier.height(14.dp))
        val label = stringResource(if (isCreator) R.string.home_creator_open else R.string.home_creator_cta)
        val buttonColors = if (isCreator) listOf(palette.goldFill, Color(0xFFFFD873))
            else listOf(palette.blue, palette.accentBlue)
        Box(
            Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(buttonColors)).clickable {
                    if (isCreator) onOpenDashboard() else onBecomeCreator()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                label,
                style = homeTextStyle(font, 14.sp, FontWeight.ExtraBold, 19.sp),
                color = if (isCreator) palette.goldInk else Color.White,
                maxLines = 2,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(
    title: String,
    isArabic: Boolean,
    font: FontFamily,
    palette: HomePalette,
    onAction: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            if (isArabic) title else title.uppercase(Locale.ROOT),
            style = TextStyle(
                fontFamily = font,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (isArabic) 0.sp else 1.sp,
                lineHeight = 19.sp,
            ),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onAction).padding(horizontal = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                stringResource(R.string.view_all),
                style = homeTextStyle(font, 13.sp, FontWeight.Bold, 18.sp),
                color = palette.blueLink,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun HomeLatestCard(
    item: ContentItem,
    byName: String?,
    font: FontFamily,
    palette: HomePalette,
    isArabic: Boolean,
    onClick: () -> Unit,
) {
    val container = rememberContainer()
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(150.dp).homeCardShadow(palette, shape).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape)
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp)) {
            HomeContentImage(
                url = assetUrl(container, item.coverUrl),
                contentDescription = item.title,
                modifier = Modifier.fillMaxSize(),
                radius = 0.dp,
            )
            homeBadgeFor(item, palette)?.let { (label, color, textColor) ->
                HomeBadge(
                    text = label,
                    background = color,
                    contentColor = textColor,
                    font = font,
                    modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
                )
            }
        }
        Column(Modifier.fillMaxWidth().padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)) {
            Text(
                item.title,
                style = homeTextStyle(font, 14.sp, FontWeight.ExtraBold, 19.sp),
                color = palette.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                byName?.let { stringResource(R.string.home_by_creator, BidiFormatter.getInstance(isArabic).unicodeWrap(it)) }
                    ?: (item.category.orEmpty()),
                style = homeTextStyle(font, 12.sp, FontWeight.Medium, 16.sp),
                color = palette.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HomeDownloadRow(
    item: ContentItem,
    byName: String?,
    font: FontFamily,
    palette: HomePalette,
    isArabic: Boolean,
    onClick: () -> Unit,
) {
    val container = rememberContainer()
    val downloadDescription = stringResource(R.string.cd_download_item, item.title)
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().homeCardShadow(palette, shape).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape)
            .clickable(onClick = onClick).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeContentImage(
            url = assetUrl(container, item.coverUrl),
            contentDescription = item.title,
            modifier = Modifier.size(64.dp),
            radius = 10.dp,
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = homeTextStyle(font, 14.sp, FontWeight.ExtraBold, 19.sp), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val creator = byName?.let { stringResource(R.string.home_by_creator, BidiFormatter.getInstance(isArabic).unicodeWrap(it)) }
            val detail = listOfNotNull(creator, metaOf(item).takeIf(String::isNotBlank)).joinToString(" · ")
            Text(detail, style = homeTextStyle(font, 12.sp, FontWeight.Medium, 16.sp), color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(palette.blue)
                .clickable(onClick = onClick)
                .semantics { contentDescription = downloadDescription },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun HomeCreatorCard(
    card: CreatorCard,
    palette: HomePalette,
    font: FontFamily,
    onClick: () -> Unit,
) {
    val container = rememberContainer()
    val ring = when {
        card.profile.role == "admin" || card.profile.effectivePlan == "legendary" -> palette.goldFill
        card.profile.effectivePlan == "epic" -> palette.epic
        card.profile.isCreator -> palette.blue
        else -> palette.border
    }
    Column(
        Modifier.width(72.dp).clip(RoundedCornerShape(14.dp)).clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier.size(64.dp).clip(CircleShape).border(2.dp, ring, CircleShape).padding(2.dp),
        ) {
            HomeContentImage(
                url = assetUrl(container, card.profile.avatarUrl),
                contentDescription = card.profile.displayName ?: card.profile.username,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
                radius = 0.dp,
                fallback = R.drawable.ic_default_profile,
            )
        }
        Text(
            card.profile.displayName ?: card.profile.username.orEmpty(),
            style = homeTextStyle(font, 12.sp, FontWeight.Bold, 16.sp),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeContentImage(
    url: String?,
    contentDescription: String?,
    modifier: Modifier,
    radius: Dp,
    fallback: Int = R.drawable.thumb_placeholder,
) {
    val placeholder = painterResource(fallback)
    AsyncImage(
        model = url,
        contentDescription = contentDescription,
        placeholder = placeholder,
        error = placeholder,
        fallback = placeholder,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(radius)),
    )
}

@Composable
private fun HomeBadge(
    text: String,
    background: Color,
    contentColor: Color,
    font: FontFamily,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier.height(20.dp).clip(RoundedCornerShape(8.dp)).background(background).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontFamily = font, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold), color = contentColor, maxLines = 1)
    }
}

@Composable
private fun HomeActionButton(
    text: String,
    font: FontFamily,
    background: Color,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outline: Color? = null,
    horizontalPadding: Dp = 16.dp,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier.heightIn(min = 44.dp).clip(shape)
            .background(if (outline == null) background else Color.Transparent)
            .then(if (outline != null) Modifier.border(1.dp, outline, shape) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = TextStyle(fontFamily = font, fontSize = fontSize, fontWeight = FontWeight.ExtraBold, lineHeight = fontSize * 1.3f),
            color = contentColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun HomeRetryCard(
    title: String,
    action: String,
    font: FontFamily,
    palette: HomePalette,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier.fillMaxWidth().homeCardShadow(palette, shape).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape)
            .clickable(onClick = onRetry).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = homeTextStyle(font, 14.sp, FontWeight.Bold, 19.sp), color = palette.text, modifier = Modifier.weight(1f))
        Text(action, style = homeTextStyle(font, 13.sp, FontWeight.Bold, 18.sp), color = palette.blueLink)
    }
}

@Composable
private fun HomeErrorBanner(font: FontFamily, palette: HomePalette, onRetry: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape).padding(start = 12.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(stringResource(R.string.home_error), style = homeTextStyle(font, 13.sp, FontWeight.Bold, 18.sp), color = palette.text, modifier = Modifier.weight(1f))
        Box(
            Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp)).clickable(onClick = onRetry).padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.home_error_tap), style = homeTextStyle(font, 12.sp, FontWeight.Bold, 16.sp), color = palette.blueLink)
        }
    }
}

@Composable
private fun HomeEmptyNote(font: FontFamily, palette: HomePalette) {
    Text(
        stringResource(R.string.home_no_content),
        style = homeTextStyle(font, 13.sp, FontWeight.Medium, 18.sp),
        color = palette.textMuted,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        textAlign = TextAlign.Center,
    )
}

@Composable
private fun HomePlanSkeleton(palette: HomePalette, motionEnabled: Boolean, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier.fillMaxWidth().homeCardShadow(palette, shape).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeSkeletonBlock(Modifier.size(40.dp), palette, motionEnabled, 20.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HomeSkeletonBlock(Modifier.fillMaxWidth(0.7f).height(14.dp), palette, motionEnabled)
            HomeSkeletonBlock(Modifier.fillMaxWidth().height(12.dp), palette, motionEnabled)
        }
        HomeSkeletonBlock(Modifier.size(width = 72.dp, height = 44.dp), palette, motionEnabled, 12.dp)
    }
}

@Composable
private fun HomeLatestSkeleton(palette: HomePalette, motionEnabled: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        Modifier.width(150.dp).homeCardShadow(palette, shape).clip(shape)
            .background(palette.surface).border(1.dp, palette.border, shape).padding(bottom = 10.dp),
    ) {
        HomeSkeletonBlock(Modifier.fillMaxWidth().height(100.dp), palette, motionEnabled, 0.dp)
        HomeSkeletonBlock(Modifier.fillMaxWidth(0.72f).padding(start = 10.dp, end = 10.dp).height(14.dp), palette, motionEnabled)
        HomeSkeletonBlock(Modifier.fillMaxWidth(0.55f).padding(start = 10.dp, end = 10.dp, top = 7.dp).height(11.dp), palette, motionEnabled)
    }
}

@Composable
private fun HomeDownloadSkeleton(palette: HomePalette, motionEnabled: Boolean) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier.fillMaxWidth().homeCardShadow(palette, shape).clip(shape).background(palette.surface)
            .border(1.dp, palette.border, shape).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        HomeSkeletonBlock(Modifier.size(64.dp), palette, motionEnabled, 10.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            HomeSkeletonBlock(Modifier.fillMaxWidth(0.75f).height(14.dp), palette, motionEnabled)
            HomeSkeletonBlock(Modifier.fillMaxWidth(0.54f).height(11.dp), palette, motionEnabled)
        }
        HomeSkeletonBlock(Modifier.size(44.dp), palette, motionEnabled, 12.dp)
    }
}

@Composable
private fun HomeCreatorSkeleton(palette: HomePalette, motionEnabled: Boolean) {
    Column(Modifier.width(72.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        HomeSkeletonBlock(Modifier.size(64.dp), palette, motionEnabled, 32.dp)
        HomeSkeletonBlock(Modifier.fillMaxWidth().height(12.dp), palette, motionEnabled)
    }
}

@Composable
private fun HomeSkeletonBlock(
    modifier: Modifier,
    palette: HomePalette,
    motionEnabled: Boolean,
    radius: Dp = 8.dp,
) {
    val brush = if (motionEnabled) {
        val transition = rememberInfiniteTransition(label = "homeSkeleton")
        val offset by transition.animateFloat(
            initialValue = -240f,
            targetValue = 480f,
            animationSpec = infiniteRepeatable(animation = tween(1100, easing = LinearEasing)),
            label = "homeSkeletonSweep",
        )
        Brush.linearGradient(
            colors = listOf(palette.surfaceHigh, palette.surface, palette.surfaceHigh),
            start = Offset(offset, 0f),
            end = Offset(offset + 240f, 120f),
        )
    } else {
        Brush.horizontalGradient(listOf(palette.surfaceHigh, palette.surfaceHigh))
    }
    Box(modifier.clip(RoundedCornerShape(radius)).background(brush))
}

@Composable
private fun homeTextStyle(font: FontFamily, size: androidx.compose.ui.unit.TextUnit, weight: FontWeight, lineHeight: androidx.compose.ui.unit.TextUnit): TextStyle =
    TextStyle(fontFamily = font, fontSize = size, fontWeight = weight, lineHeight = lineHeight)

private fun Modifier.homeCardShadow(palette: HomePalette, shape: RoundedCornerShape): Modifier = shadow(
    elevation = if (palette.isDark) 0.dp else 3.dp,
    shape = shape,
    clip = false,
    ambientColor = palette.cardShadow,
    spotColor = palette.cardShadow,
)

private fun homeBadgeFor(item: ContentItem, palette: HomePalette): Triple<String, Color, Color>? = when {
    item.plan == "legendary" -> Triple("LEGENDARY", palette.goldFill, palette.goldInk)
    item.plan == "epic" -> Triple("EPIC", palette.epic, palette.buttonText)
    item.isFeatured -> Triple("NEW", palette.blue, palette.buttonText)
    else -> null
}
