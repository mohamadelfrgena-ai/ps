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
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.WorkspacePremium
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
    /** «الجهاز» من Quick Actions (SPEC §1.1) — قد لا يكون مُفعَّلاً في بعض الإصدارات. */
    val openDevice: () -> Unit = {},
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
    var exclusives by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var creators by remember { mutableStateOf(container.creators.cachedCreators()) }
    var favorites by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(featured.isEmpty() && latest.isEmpty()) }
    var failed by remember { mutableStateOf(false) }
    var chip by remember { mutableStateOf(ChipFilter.All) }

    // الـ loading لا يُعرض إلا حين لا توجد بيانات مخزّنة (TTL). فإذا كان
    // featured غير فارغ، لا نضع spinner فوقه أبداً (تجنّب الوميض).
    val showInitialLoading = loading && featured.isEmpty()

    LaunchedEffect(profileRetryTick, signedIn) {
        loading = latest.isEmpty() && featured.isEmpty()
        failed = false
            try {
            coroutineScope {
                val featuredRequest = async(Dispatchers.IO) { container.content.featured() }
                val latestRequest = async(Dispatchers.IO) { container.content.latest() }
                val recommendedRequest = async(Dispatchers.IO) { container.content.recommended() }
                val exclusivesRequest = async(Dispatchers.IO) {
                    runCatching { container.content.exclusives(6) }.getOrDefault(emptyList())
                }
                val creatorsRequest = async(Dispatchers.IO) { container.creators.creators() }
                val favoritesRequest = async(Dispatchers.IO) { container.content.favorites() }
                featured = featuredRequest.await()
                latest = latestRequest.await()
                recommended = recommendedRequest.await()
                exclusives = exclusivesRequest.await()
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
            // Quick Actions (SPEC §1.1) — 4 بلاطات: المفضلة · تحميلاتي · ترقية · الجهاز
            item {
                HomeQuickActions(
                    font = font,
                    palette = palette,
                    onFavorites = actions.openFavorites,
                    onDownloads = actions.openDownloads,
                    onUpgrade = actions.openPlans,
                    onDevice = actions.openDevice,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 14.dp),
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
            // Recommended for you (SPEC §1.2)
            item {
                HomeRecommendedRow(
                    items = recommended,
                    palette = palette,
                    font = font,
                    isArabic = isArabic,
                    onItem = { actions.openItem(it) },
                    onSeeAll = actions.openStore,
                )
            }
            // Exclusives (SPEC §1.3)
            item {
                HomeExclusivesRow(
                    items = exclusives,
                    palette = palette,
                    font = font,
                    isArabic = isArabic,
                    onItem = { actions.openItem(it) },
                    onSeeAll = actions.openExclusives,
                )
            }
            // Creator program card (SPEC §1.5)
            item {
                val isCreator = profile?.role?.contains("creator", ignoreCase = true) == true ||
                    profile?.role?.contains("admin", ignoreCase = true) == true
                HomeCreatorProgramCard(
                    isCreator = isCreator,
                    palette = palette,
                    font = font,
                    isArabic = isArabic,
                    onCta = if (isCreator) actions.openStudio else actions.openCreatorApply,
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
private fun HomeSectionHeader(
    title: String,
    isArabic: Boolean,
    font: FontFamily,
    palette: HomePalette,
    onAction: (() -> Unit)?,
    subtitle: String? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
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
            if (onAction != null) {
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
        if (subtitle != null) {
            Text(
                subtitle,
                style = homeTextStyle(font, 13.sp, FontWeight.Medium, 18.sp),
                color = palette.textMuted,
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
private fun HomeEmptyNote(font: FontFamily, palette: HomePalette, modifier: Modifier = Modifier) {
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

// ─── الأقسام الجديدة وفق SPEC_HOME_CREATORS_PAGES §1 ───────────────────────

/**
 * Quick Actions (SPEC §1.1): 4 بلاطات متساوية (فجوة 8) بارتفاع أدنى 84dp،
 * نصف قطر 16، خلفية surface + حد 1dp، عمود مركزي: بلاطة أيقونة 40 + تسمية 12sp/800.
 *  • المفضلة (heart · error) · التحميلات (download · blue) ·
 *    ترقية (crown · gold) · الجهاز (phone · ok).
 */
@Composable
private fun HomeQuickActions(
    font: FontFamily,
    palette: HomePalette,
    onFavorites: () -> Unit,
    onDownloads: () -> Unit,
    onUpgrade: () -> Unit,
    onDevice: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HomeSectionHeader(
            title = androidx.compose.ui.res.stringResource(R.string.home_quick_actions_plain),
            isArabic = false,
            font = font,
            palette = palette,
            onAction = null,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            QuickActionTile(
                icon = androidx.compose.material.icons.Icons.Filled.Favorite,
                label = androidx.compose.ui.res.stringResource(R.string.home_qa_favorites),
                tint = palette.danger,
                font = font,
                palette = palette,
                onClick = onFavorites,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = androidx.compose.material.icons.Icons.Filled.FileDownload,
                label = androidx.compose.ui.res.stringResource(R.string.home_qa_downloads),
                tint = palette.blue,
                font = font,
                palette = palette,
                onClick = onDownloads,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = androidx.compose.material.icons.Icons.Filled.WorkspacePremium,
                label = androidx.compose.ui.res.stringResource(R.string.home_qa_upgrade),
                tint = palette.goldFill,
                font = font,
                palette = palette,
                onClick = onUpgrade,
                modifier = Modifier.weight(1f),
            )
            QuickActionTile(
                icon = androidx.compose.material.icons.Icons.Filled.PhoneAndroid,
                label = androidx.compose.ui.res.stringResource(R.string.home_qa_device),
                tint = palette.green,
                font = font,
                palette = palette,
                onClick = onDevice,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun QuickActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color,
    font: FontFamily,
    palette: HomePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = modifier
            .heightIn(min = 84.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Text(
            label,
            style = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
            color = palette.text,
            maxLines = 2,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Exclusives (SPEC §1.3): بطاقات عريضة 260×150، تدرّج تراكب من شفاف → `#0A0F1C`@88%.
 * شارة الخطة أعلى البداية، العنوان في الأسفل.
 */
@Composable
private fun HomeExclusivesRow(
    items: List<ContentItem>,
    palette: HomePalette,
    font: FontFamily,
    isArabic: Boolean,
    onItem: (ContentItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HomeSectionHeader(
            title = androidx.compose.ui.res.stringResource(R.string.home_exclusives_plain),
            subtitle = androidx.compose.ui.res.stringResource(R.string.home_exclusives_sub),
            isArabic = isArabic,
            font = font,
            palette = palette,
            onAction = onSeeAll,
        )
        if (items.isEmpty()) {
            HomeEmptyNote(font, palette, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    ExclusiveCard(
                        item = item,
                        palette = palette,
                        font = font,
                        isArabic = isArabic,
                        onClick = { onItem(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ExclusiveCard(
    item: ContentItem,
    palette: HomePalette,
    font: FontFamily,
    isArabic: Boolean,
    onClick: () -> Unit,
) {
    val container = rememberContainer()
    val shape = RoundedCornerShape(18.dp)
    Box(
        modifier = Modifier
            .size(width = 260.dp, height = 150.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick),
    ) {
        com.addonstation.mobile.ui.components.CoverImage(
            url = assetUrl(container, item.coverUrl),
            radius = 0.dp,
            modifier = Modifier.fillMaxSize(),
        )
        // تدرّج تراكب
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    androidx.compose.ui.graphics.Brush.verticalGradient(
                        listOf(Color.Transparent, Color(0xE10A0F1C)),
                    ),
                ),
        )
        // شارة الخطة أعلى البداية
        val badge = homeBadgeFor(item, palette)
        if (badge != null) {
            Box(
                modifier = Modifier
                    .padding(10.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(badge.second)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    badge.first,
                    style = TextStyle(fontFamily = font, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.6.sp),
                    color = badge.third,
                )
            }
        }
        // العنوان + المبدع في الأسفل
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 12.dp, bottom = 12.dp),
        ) {
            Text(
                item.title,
                style = TextStyle(fontFamily = font, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                color = Color.White,
                maxLines = 2,
            )
            item.createdBy?.let { id ->
                val byName = com.addonstation.mobile.ui.common.metaOf(item).takeIf { it.isNotBlank() }
                if (!byName.isNullOrBlank()) {
                    Text(
                        byName,
                        style = TextStyle(fontFamily = font, fontSize = 12.sp, fontWeight = FontWeight.Medium),
                        color = Color(0xFFC4D1E8),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/**
 * Recommended for you (SPEC §1.2): صف أفقي من بطاقات Latest نفسها (150 wide).
 */
@Composable
private fun HomeRecommendedRow(
    items: List<ContentItem>,
    palette: HomePalette,
    font: FontFamily,
    isArabic: Boolean,
    onItem: (ContentItem) -> Unit,
    onSeeAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        HomeSectionHeader(
            title = androidx.compose.ui.res.stringResource(R.string.home_recommended_plain),
            isArabic = isArabic,
            font = font,
            palette = palette,
            onAction = onSeeAll,
        )
        if (items.isEmpty()) {
            HomeEmptyNote(font, palette, modifier = Modifier.padding(horizontal = 16.dp).padding(top = 6.dp))
        } else {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
            ) {
                items(items, key = { it.id }) { item ->
                    HomeLatestCard(
                        item = item,
                        byName = null,
                        font = font,
                        palette = palette,
                        isArabic = isArabic,
                        onClick = { onItem(item) },
                    )
                }
            }
        }
    }
}

/**
 * Creator program card (SPEC §1.5): بطاقة بتدرّج `#102244`→`#121A2B`، حد أزرق 1dp،
 * كيكر ذهبي + عنوان + نص + زر CTA (44 high primary gradient).
 *  • للمبدع الحالي: العنوان = home_creator_dashboard والزر gold يفتح لوحة المبدع.
 */
@Composable
private fun HomeCreatorProgramCard(
    isCreator: Boolean,
    palette: HomePalette,
    font: FontFamily,
    isArabic: Boolean,
    onCta: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .fillMaxWidth()
            .clip(shape)
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(Color(0xFF102244), Color(0xFF121A2B)),
                ),
            )
            .border(1.dp, palette.blue, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.home_creator_kicker),
            style = if (isArabic) {
                TextStyle(fontFamily = TajawalFont, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
            } else {
                TextStyle(fontFamily = PixelFont, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            },
            color = palette.goldFill,
        )
        Text(
            androidx.compose.ui.res.stringResource(
                if (isCreator) R.string.home_creator_dashboard else R.string.home_creator_title,
            ),
            style = TextStyle(fontFamily = font, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold),
            color = palette.text,
        )
        Text(
            androidx.compose.ui.res.stringResource(R.string.home_creator_text),
            style = TextStyle(fontFamily = font, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 20.sp),
            color = palette.textMuted,
        )
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .fillMaxWidth()
                .height(44.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (isCreator) {
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(palette.goldFill, palette.goldInk),
                        )
                    } else {
                        androidx.compose.ui.graphics.Brush.horizontalGradient(
                            listOf(palette.blue, palette.accentBlue),
                        )
                    },
                )
                .clickable(onClick = onCta),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                androidx.compose.ui.res.stringResource(
                    if (isCreator) R.string.home_creator_open else R.string.home_creator_cta,
                ),
                style = TextStyle(fontFamily = font, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                color = if (isCreator) palette.goldInk else palette.buttonText,
            )
        }
    }
}
