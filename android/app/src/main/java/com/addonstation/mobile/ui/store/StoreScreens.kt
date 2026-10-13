package com.addonstation.mobile.ui.store

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.ui.apiErrorText
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.CoinIcon
import com.addonstation.mobile.ui.components.CoverImage
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.FilterChip
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.PixelFont
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

/** Store browser: current search, type and sort behaviour with the refreshed visual system. */
@Composable
fun StoreScreen(
    onOpenItem: (ContentItem) -> Unit,
    onOpenWallet: () -> Unit,
    initialQuery: String = "",
    scrollToTopRequest: Int = 0,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) scrollState.animateScrollTo(0)
    }
    val container = rememberContainer()
    val context = LocalContext.current
    val motionEnabled = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            ) > 0f
        }.getOrDefault(true)
    }
    val wallet by container.state.wallet.collectAsState()
    val scope = rememberCoroutineScope()
    val homePalette = com.addonstation.mobile.ui.theme.LocalHomePalette.current
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val networkErrorText = stringResource(R.string.msg_network_error)

    var type by remember { mutableStateOf("addon") }
    var sort by remember { mutableStateOf("newest") }
    var query by remember { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var favourites by remember { mutableStateOf<Set<String>>(emptySet()) }
    var creatorNames by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    suspend fun reload() {
        loading = true
        failed = false
        try {
            // Keep the existing parallel browse/favorites requests and all filter semantics.
            coroutineScope {
                val list = async { container.content.browse(type, sort, query, 60) }
                val fav = async {
                    if (container.supabase.session() == null) emptySet()
                    else container.content.favorites().map { it.id }.toSet()
                }
                items = list.await()
                favourites = fav.await()
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            // Keep the last successful result so a transient network failure does not blank the store.
            failed = true
        } finally {
            loading = false
        }
    }

    val signedIn by container.state.signedIn.collectAsState()
    LaunchedEffect(type, sort, query, reloadTick, signedIn) { reload() }
    LaunchedEffect(items) {
        val ids = items.mapNotNull { it.createdBy }.distinct()
        creatorNames = if (ids.isEmpty()) emptyMap() else runCatching {
            withContext(Dispatchers.IO) {
                container.profile.loadProfiles(ids).mapValues { (_, profile) ->
                    profile.username ?: profile.displayName.orEmpty()
                }
            }
        }.getOrDefault(emptyMap())
    }
    LaunchedEffect(failed, items.isNotEmpty()) {
        if (failed && items.isNotEmpty()) snackbarHostState.showSnackbar(networkErrorText)
    }

    Box(Modifier.fillMaxSize().background(homePalette.bg)) {
        Column(Modifier.fillMaxSize()) {
            StoreHeader(
                walletBalance = wallet?.balance,
                onWalletClick = onOpenWallet,
                palette = homePalette,
            )
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState),
            ) {
                Spacer(Modifier.height(2.dp))
                Text(
                    stringResource(R.string.store_title),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = homePalette.textMuted,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                )
                StoreSearchField(
                    value = query,
                    onValueChange = { query = it },
                    onSearch = { scope.launch { reload() } },
                    palette = homePalette,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(12.dp))
                StoreSegmentedControl(
                    first = stringResource(R.string.store_tab_addons),
                    second = stringResource(R.string.store_tab_worlds),
                    selectedIndex = if (type == "addon") 0 else 1,
                    onSelect = { type = if (it == 0) "addon" else "world" },
                    palette = homePalette,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Spacer(Modifier.height(8.dp))
                val sorts = listOf(
                    "newest" to stringResource(R.string.store_sort_newest),
                    "downloads" to stringResource(R.string.store_sort_downloads),
                    "views" to stringResource(R.string.store_sort_views),
                    "rating" to stringResource(R.string.store_sort_rating),
                )
                LazyRow(
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(sorts) { (key, label) ->
                        StoreSortChip(
                            label = label,
                            selected = sort == key,
                            onClick = { sort = key },
                            palette = homePalette,
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> StoreLoadingGrid(palette = homePalette, animate = motionEnabled, modifier = Modifier.padding(horizontal = 16.dp))
                    failed && items.isEmpty() -> StoreState(
                        icon = androidx.compose.material.icons.Icons.Outlined.WifiOff,
                        title = stringResource(R.string.msg_network_error),
                        subtitle = null,
                        palette = homePalette,
                        onRetry = { reloadTick += 1 },
                    )
                    items.isEmpty() -> StoreState(
                        icon = androidx.compose.material.icons.Icons.Outlined.SearchOff,
                        title = stringResource(R.string.store_empty),
                        subtitle = stringResource(R.string.store_empty_hint),
                        palette = homePalette,
                    )
                    else -> {
                        val rowCount = (items.size + 1) / 2
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .height((rowCount * 214 + (rowCount - 1).coerceAtLeast(0) * 12).dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            userScrollEnabled = false,
                        ) {
                            items(items, key = { it.id }) { item ->
                                StoreContentCard(
                                    item = item,
                                    coverUrl = assetUrl(container, item.coverUrl),
                                    favourite = favourites.contains(item.id),
                                    creatorName = item.createdBy?.let(creatorNames::get)?.takeIf(String::isNotBlank),
                                    onClick = { onOpenItem(item) },
                                    palette = homePalette,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(28.dp))
            }
        }
        androidx.compose.material3.SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
        )
    }
}

@Composable
private fun StoreHeader(
    walletBalance: Int?,
    onWalletClick: () -> Unit,
    palette: com.addonstation.mobile.ui.theme.HomePalette,
) {
    val isArabic = com.addonstation.mobile.ui.components.appLocaleCode().equals("ar", ignoreCase = true)
    val titleStyle = if (isArabic) {
        androidx.compose.ui.text.TextStyle(
            fontFamily = com.addonstation.mobile.ui.theme.TajawalFont,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
        )
    } else {
        androidx.compose.ui.text.TextStyle(
            fontFamily = PixelFont,
            fontSize = 18.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            letterSpacing = 1.sp,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(palette.barSurface)
            .padding(start = 14.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(palette.surfaceHigh)
                .border(2.dp, palette.goldFill, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.app_icon),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(32.dp).clip(RoundedCornerShape(9.dp)),
            )
        }
        Text(
            stringResource(R.string.nav_store),
            style = titleStyle,
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(palette.surfaceHigh)
                .clickable(onClick = onWalletClick)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            androidx.compose.foundation.Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.coin_ac),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                walletBalance?.toString() ?: "—",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = com.addonstation.mobile.ui.theme.NumberFont,
                    fontSize = 14.sp,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                ),
                color = palette.goldText,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun StoreSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: () -> Unit,
    palette: com.addonstation.mobile.ui.theme.HomePalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(20.dp))
        androidx.compose.foundation.text.BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, color = palette.text),
            cursorBrush = androidx.compose.ui.graphics.SolidColor(palette.blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch() }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.store_search_hint),
                            style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                            color = palette.textMuted,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
    }
}

@Composable
private fun StoreSegmentedControl(
    first: String,
    second: String,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    palette: com.addonstation.mobile.ui.theme.HomePalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(first, second).forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Box(
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.Tab; this.selected = selected }
                    .height(40.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (selected) Modifier.background(
                            androidx.compose.ui.graphics.Brush.linearGradient(listOf(palette.blue, palette.accentBlue)),
                        ) else Modifier
                    )
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                    color = if (selected) Color.White else palette.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StoreSortChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    palette: com.addonstation.mobile.ui.theme.HomePalette,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        Modifier
            .semantics { this.selected = selected }
            .height(40.dp)
            .clip(shape)
            .then(
                if (selected) Modifier.background(
                    androidx.compose.ui.graphics.Brush.linearGradient(listOf(palette.blue, palette.accentBlue)),
                ) else Modifier.background(palette.surface)
            )
            .border(if (selected) 0.dp else 1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
            color = if (selected) Color.White else palette.chipText,
            maxLines = 1,
        )
    }
}

@Composable
private fun StoreContentCard(
    item: ContentItem,
    coverUrl: String?,
    favourite: Boolean,
    creatorName: String?,
    onClick: () -> Unit,
    palette: com.addonstation.mobile.ui.theme.HomePalette,
) {
    val shape = RoundedCornerShape(16.dp)
    val tierColor = when (item.plan.lowercase()) {
        "legendary" -> palette.goldFill
        "epic" -> palette.epic
        else -> if (palette.isDark) Color(0xFF3FBF6A) else Color(0xFF1E7A44)
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            CoverImage(url = coverUrl, radius = 12.dp, modifier = Modifier.fillMaxSize())
            Text(
                planLabelOf(item.plan),
                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                color = if (item.plan.equals("legendary", true)) Color(0xFF1B1405) else Color.White,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(tierColor)
                    .padding(horizontal = 9.dp, vertical = 4.dp),
                maxLines = 1,
            )
            if (favourite) {
                Icon(
                    Icons.Filled.Favorite,
                    contentDescription = null,
                    tint = Color(0xFFFF6B78),
                    modifier = Modifier.align(Alignment.TopEnd).padding(7.dp).size(18.dp),
                )
            }
        }
        Text(
            item.title,
            style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
            color = palette.text,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            metaOf(item),
            style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp),
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                stringResource(R.string.item_by, creatorName ?: stringResource(R.string.common_guest)),
                style = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold),
                color = palette.blueLink,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StoreLoadingGrid(
    palette: com.addonstation.mobile.ui.theme.HomePalette,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val shimmerTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "store-shimmer")
    val animatedAlpha by shimmerTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1400),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "store-shimmer-alpha",
    )
    val shimmerAlpha = if (animate) animatedAlpha else 0.62f
    val shimmerColor = palette.surfaceHigh.copy(alpha = shimmerAlpha)
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth().height(420.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false,
    ) {
        items(4) {
            val shape = RoundedCornerShape(16.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(198.dp)
                    .clip(shape)
                    .background(palette.surface)
                    .border(1.dp, palette.border, shape)
                    .padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(12.dp)).background(shimmerColor))
                Box(Modifier.fillMaxWidth(0.76f).height(12.dp).clip(CircleShape).background(shimmerColor))
                Box(Modifier.fillMaxWidth(0.48f).height(10.dp).clip(CircleShape).background(shimmerColor))
            }
        }
    }
}

@Composable
private fun StoreState(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String?,
    palette: com.addonstation.mobile.ui.theme.HomePalette,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite }.padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(palette.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(40.dp))
        }
        Text(
            title,
            style = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
            color = palette.text,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        subtitle?.let {
            Text(
                it,
                style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp),
                color = palette.textMuted,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
        onRetry?.let { retry ->
            Box(
                Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(palette.blue, palette.accentBlue)))
                    .clickable(onClick = retry)
                    .padding(horizontal = 28.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.common_retry),
                    style = androidx.compose.ui.text.TextStyle(fontSize = 14.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                    color = Color.White,
                )
            }
        }
    }
}

/** نصوص الأخطاء المجمّعة (تُقرأ مرة واحدة خارج اللامبدات لأن stringResource دالة Composable). */
private data class DetailTexts(
    val signIn: String,
    val failed: String,
    val noPermission: String,
    val fileMissing: String,
    val notEnoughAc: String,
    val downloadLimit: String,
    val downloading: String,
    val fileSavedAt: String,
)

private fun errorMessageOf(error: Throwable, texts: DetailTexts): String =
    when (error.toApiError().kind) {
        com.addonstation.mobile.data.ApiErrorKind.NOT_SIGNED_IN -> texts.signIn
        com.addonstation.mobile.data.ApiErrorKind.FILE_NOT_UPLOADED -> texts.fileMissing
        com.addonstation.mobile.data.ApiErrorKind.NO_PERMISSION -> texts.noPermission
        com.addonstation.mobile.data.ApiErrorKind.NOT_ENOUGH_AC -> texts.notEnoughAc
        com.addonstation.mobile.data.ApiErrorKind.DOWNLOAD_LIMIT -> texts.downloadLimit
        else -> texts.failed
    }

/**
 * شاشة تفاصيل المحتوى: غلاف 195dp، الإحصاءات، الوصف، أزرار التحميل/الحفظ، ومحتوى مشابه.
 * زر التحميل يُظهر القفل المناسب قبل الضغط («🔒 يتطلب Epic 💎» / «🔒 يتطلب Legendary 👑»).
 */
@Composable
fun ContentDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onOpenProfile: (String) -> Unit,
    onUpgrade: () -> Unit,
    onLogin: () -> Unit,
    onOpenSimilar: (ContentItem) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val scope = rememberCoroutineScope()

    var item by remember { mutableStateOf<ContentItem?>(null) }
    var creator by remember { mutableStateOf<com.addonstation.mobile.data.model.Profile?>(null) }
    var similar by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var favourite by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var unlocked by remember { mutableStateOf(false) }

    val texts = DetailTexts(
        signIn = stringResource(R.string.msg_sign_in_needed),
        failed = stringResource(R.string.item_download_failed),
        noPermission = stringResource(R.string.item_no_permission),
        fileMissing = stringResource(R.string.item_file_missing),
        notEnoughAc = stringResource(R.string.ac_not_enough),
        downloadLimit = stringResource(R.string.plan_download_limit),
        downloading = stringResource(R.string.item_downloading_plain),
        fileSavedAt = stringResource(R.string.item_downloading_plain),
    )
    val addedFav = stringResource(R.string.item_added_fav_plain)
    val removedFav = stringResource(R.string.item_removed_fav)

    LaunchedEffect(itemId) {
        val loaded = container.content.byId(itemId)
        item = loaded
        if (loaded != null) {
            container.content.recordView(loaded.id)
            similar = container.content.similar(loaded)
            creator = loaded.createdBy?.let { container.profile.loadProfile(it) }
            unlocked = container.ac.unlockedItemIds().contains(loaded.id)
        }
        favourite = container.content.isFavorite(itemId)
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.nav_store), onBack = onBack)
        val current = item
        if (current == null) {
            LoadingBox(text = stringResource(R.string.common_loading))
            return@Column
        }
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Box {
                CoverImage(
                    url = assetUrl(container, current.coverUrl),
                    radius = 20.dp,
                    // صفحة العنصر: صورة واحدة على الشاشة ⇒ تُعرض بحركتها كاملة
                    animate = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(195.dp),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .clip(CircleShape)
                        .background(AppColors.Gold)
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(planLabelOf(current.plan), style = AppText.tiny, color = AppColors.Bg)
                }
            }

            Spacer(Modifier.height(12.dp))
            Text(current.title, style = AppText.pageTitle, color = AppColors.Text)
            Spacer(Modifier.height(4.dp))
            Text(metaOf(current), style = AppText.bodySmall, color = AppColors.Muted)

            creator?.let { creatorProfile ->
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AvatarImage(url = assetUrl(container, creatorProfile.avatarUrl), size = 34.dp)
                    Text(
                        stringResource(R.string.item_by, creatorProfile.username ?: ""),
                        style = AppText.bodySmall,
                        color = AppColors.Muted,
                    )
                    RankBadge(role = creatorProfile.role, plan = creatorProfile.effectivePlan)
                    Text(
                        text = "← " + stringResource(R.string.item_view_plain),
                        style = AppText.bodySmall,
                        color = AppColors.Blue,
                        modifier = Modifier.clickable { onOpenCreatorProfile(creatorProfile.id) },
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.item_description), leadingEmoji = "📝")
            GlassCard {
                Text(
                    current.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.common_none),
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                )
            }

            Spacer(Modifier.height(14.dp))
            val access = container.content.accessState(
                item = current,
                plan = profile?.effectivePlan ?: "free",
                isOwner = current.createdBy != null && current.createdBy == profile?.id,
                isAdmin = profile?.role == "admin",
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    !signedIn -> GradientButton(
                        text = stringResource(R.string.item_download_plain),
                        onClick = onLogin,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.FileDownload,
                    )
                    access is com.addonstation.mobile.data.repo.AccessState.RequiresEpic -> GradientButton(
                        text = stringResource(R.string.item_requires_epic_plain),
                        onClick = onUpgrade,
                        modifier = Modifier.weight(1f),
                        colors = listOf(AppColors.Purple, AppColors.Purple.copy(alpha = 0.65f)),
                        textColor = Color.Black,
                    )
                    access is com.addonstation.mobile.data.repo.AccessState.RequiresLegendary -> GradientButton(
                        text = stringResource(R.string.item_requires_legendary_plain),
                        onClick = onUpgrade,
                        modifier = Modifier.weight(1f),
                        colors = listOf(AppColors.GoldLight, AppColors.GoldDark),
                        textColor = AppColors.GoldText,
                    )
                    else -> GradientButton(
                        text = if (current.acPrice > 0 && !unlocked)
                            stringResource(R.string.item_unlock_ac, current.acPrice)
                        else stringResource(R.string.item_download_plain),
                        leading = if (current.acPrice > 0 && !unlocked) ({ CoinIcon(size = 18.dp) }) else null,
                        onClick = {
                            scope.launch {
                                busy = true
                                message = texts.downloading
                                container.content.download(context, current)
                                    .onSuccess {
                                        message = "${texts.downloading} → ${it.file.absolutePath}"
                                        unlocked = true
                                        container.profile.refreshWallet()
                                    }
                                    .onFailure { e -> message = errorMessageOf(e, texts) }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.FileDownload,
                    )
                }
                GhostButton(
                    text = if (favourite) stringResource(R.string.item_saved_plain) else stringResource(R.string.item_save_plain),
                    onClick = {
                        scope.launch {
                            if (container.supabase.session() == null) {
                                message = texts.signIn
                                return@launch
                            }
                            container.content.toggleFavorite(current.id)
                                .onSuccess { value ->
                                    favourite = value
                                    message = if (value) addedFav else removedFav
                                }
                                .onFailure { e -> message = errorMessageOf(e, texts) }
                        }
                    },
                    icon = if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                )
            }

            if (busy) {
                Spacer(Modifier.height(10.dp))
                LoadingBox(text = stringResource(R.string.item_downloading_plain))
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Green)
            }

            SectionHeader(title = stringResource(R.string.item_similar), leadingEmoji = "🧩")
            if (similar.isEmpty()) {
                EmptyState(stringResource(R.string.item_no_similar))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(similar, key = { it.id }) { sim ->
                        com.addonstation.mobile.ui.components.ContentCard(
                            title = sim.title,
                            coverUrl = assetUrl(container, sim.coverUrl),
                            planLabel = planLabelOf(sim.plan),
                            metaLine = metaOf(sim),
                            onClick = { onOpenSimilar(sim) },
                            large = true,
                        )
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
