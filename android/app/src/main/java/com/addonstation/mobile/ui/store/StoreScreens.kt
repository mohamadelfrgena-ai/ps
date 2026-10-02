package com.addonstation.mobile.ui.store

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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.FileDownload
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.addonstation.mobile.ui.components.Toasts
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
import kotlinx.coroutines.launch

/** شاشة المتجر: تبويبان (إضافات/عوالم) + بحث + شرائح ترتيب + شبكة حتى 60 عنصراً. */
@Composable
fun StoreScreen(
    onOpenItem: (ContentItem) -> Unit,
    onOpenWallet: () -> Unit,
    initialQuery: String = "",
) {
    val container = rememberContainer()
    val wallet by container.state.wallet.collectAsState()
    val scope = rememberCoroutineScope()

    var type by remember { mutableStateOf("addon") }
    var sort by remember { mutableStateOf("newest") }
    var query by remember { mutableStateOf(initialQuery) }
    var loading by remember { mutableStateOf(true) }
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var favourites by remember { mutableStateOf<Set<String>>(emptySet()) }

    suspend fun reload() {
        loading = true
        items = container.content.browse(type, sort, query, 60)
        favourites = if (container.supabase.session() == null) emptySet()
        else container.content.favorites().map { it.id }.toSet()
        loading = false
    }

    LaunchedEffect(type, sort, query) { reload() }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.nav_store),
            walletBalance = wallet?.balance,
            onWalletClick = onOpenWallet,
            icon = Icons.Filled.ShoppingCart,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.store_title), style = AppText.bodySmall, color = AppColors.Muted)

            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.store_search_hint), style = AppText.bodySmall) },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.Muted) },
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { scope.launch { reload() } }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    label = stringResource(R.string.store_tab_addons),
                    selected = type == "addon",
                    onClick = { type = "addon" },
                )
                FilterChip(
                    label = stringResource(R.string.store_tab_worlds),
                    selected = type == "world",
                    onClick = { type = "world" },
                )
            }

            Spacer(Modifier.height(10.dp))
            val sorts = listOf(
                "newest" to stringResource(R.string.store_sort_newest),
                "downloads" to stringResource(R.string.store_sort_downloads),
                "views" to stringResource(R.string.store_sort_views),
                "rating" to stringResource(R.string.store_sort_rating),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(sorts) { (key, label) ->
                    FilterChip(label = label, selected = sort == key, onClick = { sort = key })
                }
            }

            Spacer(Modifier.height(12.dp))
            when {
                loading -> LoadingBox(text = stringResource(R.string.common_loading))
                items.isEmpty() -> EmptyState(
                    stringResource(R.string.store_empty) + "\n" + stringResource(R.string.store_empty_hint)
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(((items.size + 1) / 2 * 210).dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(items, key = { it.id }) { item ->
                        com.addonstation.mobile.ui.components.ContentCard(
                            title = item.title,
                            coverUrl = assetUrl(container, item.coverUrl),
                            planLabel = planLabelOf(item.plan),
                            metaLine = metaOf(item),
                            onClick = { onOpenItem(item) },
                            favourite = favourites.contains(item.id),
                        )
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
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
        downloading = stringResource(R.string.item_downloading),
        fileSavedAt = stringResource(R.string.item_downloading),
    )
    val addedFav = stringResource(R.string.item_added_fav)
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
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Box {
                CoverImage(
                    url = assetUrl(container, current.coverUrl),
                    radius = 20.dp,
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
                        text = "← " + stringResource(R.string.item_view),
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
                    profile == null -> GradientButton(
                        text = stringResource(R.string.item_download),
                        onClick = onLogin,
                        modifier = Modifier.weight(1f),
                        icon = Icons.Filled.FileDownload,
                    )
                    access is com.addonstation.mobile.data.repo.AccessState.RequiresEpic -> GradientButton(
                        text = stringResource(R.string.item_requires_epic),
                        onClick = onUpgrade,
                        modifier = Modifier.weight(1f),
                        colors = listOf(AppColors.Purple, AppColors.Purple.copy(alpha = 0.65f)),
                        textColor = Color.Black,
                    )
                    access is com.addonstation.mobile.data.repo.AccessState.RequiresLegendary -> GradientButton(
                        text = stringResource(R.string.item_requires_legendary),
                        onClick = onUpgrade,
                        modifier = Modifier.weight(1f),
                        colors = listOf(AppColors.GoldLight, AppColors.GoldDark),
                        textColor = AppColors.GoldText,
                    )
                    else -> GradientButton(
                        text = if (current.acPrice > 0 && !unlocked)
                            stringResource(R.string.item_unlock_ac, current.acPrice)
                        else stringResource(R.string.item_download),
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
                    text = if (favourite) stringResource(R.string.item_saved) else stringResource(R.string.item_save),
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
                LoadingBox(text = stringResource(R.string.item_downloading))
            }
            // الرسالة تظهر نخبةً في أسفل الشاشة (ToastHost) لا سطراً داخل الصفحة
            LaunchedEffect(message) {
                message?.let {
                    Toasts.show(it)
                    message = null
                }
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
