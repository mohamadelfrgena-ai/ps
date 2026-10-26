package com.addonstation.mobile.ui.store

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.parseIsoMillis
import com.addonstation.mobile.data.repo.AccessState
import com.addonstation.mobile.data.repo.DownloadedFile
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.CoinIcon
import com.addonstation.mobile.ui.components.CoverImage
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

private const val NEW_ITEM_WINDOW_MILLIS = 14L * 24L * 60L * 60L * 1000L
private const val MINECRAFT_ANDROID_PACKAGE = "com.mojang.minecraftpe"

private enum class MinecraftOpenResult { OPENED, NOT_INSTALLED, FAILED }

@Composable
internal fun DesignedStoreContentCard(
    item: ContentItem,
    coverUrl: String?,
    favourite: Boolean,
    creator: Profile?,
    onClick: () -> Unit,
    onToggleFavourite: () -> Unit,
    palette: HomePalette,
) {
    val shape = RoundedCornerShape(18.dp)
    val localeCode = appLocaleCode()
    val container = rememberContainer()
    val isNew = remember(item.createdAt) {
        parseIsoMillis(item.createdAt)?.let { System.currentTimeMillis() - it in 0..NEW_ITEM_WINDOW_MILLIS } == true
    }

    Column(
        Modifier
            .fillMaxWidth()
            .height(226.dp)
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
                .height(98.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            CoverImage(url = coverUrl, radius = 12.dp, modifier = Modifier.fillMaxSize())
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent, Color.Black.copy(alpha = 0.36f)),
                        ),
                    ),
            )
            ContentAccessBadge(
                item = item,
                compact = true,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color(0x990A0F1C))
                    .border(1.dp, Color.White.copy(alpha = 0.22f), CircleShape)
                    .clickable(onClick = onToggleFavourite),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    contentDescription = stringResource(if (favourite) R.string.item_saved_plain else R.string.item_save_plain),
                    tint = if (favourite) Color(0xFFFF7A9A) else Color.White,
                    modifier = Modifier.size(17.dp),
                )
            }
            if (isNew) {
                Text(
                    stringResource(R.string.phone_store_new),
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF2E7BEF))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                )
            }
        }

        Text(
            item.title,
            color = palette.text,
            fontSize = 14.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            minLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            AvatarImage(
                url = creator?.avatarUrl?.let { assetUrl(container, it) },
                size = 18.dp,
                borderColor = palette.goldFill,
            )
            Text(
                creator?.displayName ?: creator?.username ?: stringResource(R.string.common_guest),
                color = palette.blueLink,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StoreMiniStat(
                icon = R.drawable.ic_phone_stat_downloads,
                value = formatCompactCount(item.downloadsCount, localeCode),
                color = Color(0xFF6FA8FF),
            )
            StoreMiniStat(
                icon = R.drawable.ic_phone_stat_views,
                value = formatCompactCount(item.viewsCount, localeCode),
                color = Color(0xFF4FD1C5),
            )
            if (item.ratingCount > 0) {
                StoreMiniStat(
                    icon = R.drawable.ic_phone_stat_rating,
                    value = formatRating(item.rating, localeCode),
                    color = Color(0xFFF0C25E),
                )
            }
        }
    }
}

@Composable
private fun StoreMiniStat(icon: Int, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(painterResource(icon), contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Text(value, color = AppColors.AuthMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun ContentAccessBadge(
    item: ContentItem,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val plan = item.plan.lowercase(Locale.ROOT)
    val isAc = item.acPrice > 0
    val color = when {
        isAc -> Color(0xD91A2438)
        plan == "legendary" -> Color(0xFFF0C25E)
        plan == "epic" -> Color(0xFF9B6BFF)
        else -> Color(0xFF2FAE63)
    }
    val ink = if (plan == "legendary" && !isAc) Color(0xFF1B1405) else Color.White
    Row(
        modifier
            .clip(RoundedCornerShape(if (compact) 8.dp else 10.dp))
            .background(color)
            .padding(horizontal = if (compact) 8.dp else 10.dp, vertical = if (compact) 4.dp else 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        when {
            isAc -> CoinIcon(size = if (compact) 13.dp else 16.dp)
            plan == "legendary" -> Icon(
                painterResource(R.drawable.ic_phone_crown),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(if (compact) 12.dp else 15.dp),
            )
            plan == "epic" -> Icon(
                painterResource(R.drawable.ic_phone_gem),
                contentDescription = null,
                tint = ink,
                modifier = Modifier.size(if (compact) 12.dp else 15.dp),
            )
        }
        Text(
            when {
                isAc -> item.acPrice.toString()
                plan == "legendary" -> stringResource(R.string.plan_legendary_name)
                plan == "epic" -> stringResource(R.string.plan_epic_name)
                else -> stringResource(R.string.plan_free_name)
            },
            color = ink,
            fontSize = if (compact) 10.sp else 12.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
        )
    }
}

private enum class DetailStage { IDLE, DOWNLOADING, DOWNLOADED, LIMIT, FAILED, NO_FILE }

private enum class DetailMode {
    FREE,
    PAID_AC,
    NEEDS_EPIC,
    NEEDS_LEGENDARY,
    SIGNED_OUT,
    DOWNLOADING,
    DOWNLOADED,
    LIMIT,
    FAILED,
    NO_FILE,
}

@Composable
internal fun DesignedContentDetailScreen(
    itemId: String,
    onBack: () -> Unit,
    onUpgrade: () -> Unit,
    onLogin: () -> Unit,
    onOpenSimilar: (ContentItem) -> Unit,
    onOpenCreatorProfile: (String) -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val palette = LocalHomePalette.current
    val profile by container.state.profile.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val scope = rememberCoroutineScope()

    var item by remember(itemId) { mutableStateOf<ContentItem?>(null) }
    var creator by remember(itemId) { mutableStateOf<Profile?>(null) }
    var similar by remember(itemId) { mutableStateOf<List<ContentItem>>(emptyList()) }
    var favourite by remember(itemId) { mutableStateOf(false) }
    var loading by remember(itemId) { mutableStateOf(true) }
    var loadFailed by remember(itemId) { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var unlocked by remember(itemId) { mutableStateOf(false) }
    var stage by remember(itemId) { mutableStateOf(DetailStage.IDLE) }
    var downloaded by remember(itemId) { mutableStateOf<DownloadedFile?>(null) }
    var failureKind by remember(itemId) { mutableStateOf<ApiErrorKind?>(null) }
    var notice by remember(itemId) { mutableStateOf<String?>(null) }
    var favouriteBusy by remember(itemId) { mutableStateOf(false) }
    var downloadJob by remember(itemId) { mutableStateOf<Job?>(null) }

    val signInMessage = stringResource(R.string.msg_sign_in_needed)
    val genericError = stringResource(R.string.item_download_failed)
    val openFailed = stringResource(R.string.phone_item_open_failed)
    val minecraftRequired = stringResource(R.string.phone_item_minecraft_required)
    val shareFailed = stringResource(R.string.phone_item_share_failed)

    LaunchedEffect(itemId, reloadTick, signedIn) {
        loading = true
        loadFailed = false
        try {
            val loaded = withContext(Dispatchers.IO) { container.content.byId(itemId) }
            item = loaded
            if (loaded != null) {
                withContext(Dispatchers.IO) { container.content.recordView(loaded.id) }
                similar = runCatching { withContext(Dispatchers.IO) { container.content.similar(loaded) } }.getOrDefault(emptyList())
                creator = loaded.createdBy?.let { id ->
                    runCatching { withContext(Dispatchers.IO) { container.profile.loadProfile(id) } }.getOrNull()
                }
                favourite = if (container.supabase.session() == null) false else runCatching {
                    withContext(Dispatchers.IO) { container.content.isFavorite(itemId) }
                }.getOrDefault(false)
                unlocked = if (container.supabase.session() == null) false else runCatching {
                    withContext(Dispatchers.IO) { container.ac.unlockedItemIds().contains(loaded.id) }
                }.getOrDefault(false)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            loadFailed = true
        } finally {
            loading = false
        }
    }

    fun toggleFavourite() {
        if (favouriteBusy) return
        if (!signedIn || container.supabase.session() == null) {
            onLogin()
            return
        }
        favouriteBusy = true
        scope.launch {
            container.content.toggleFavorite(itemId)
                .onSuccess { favourite = it; notice = null }
                .onFailure { notice = it.toApiError().raw ?: genericError }
            favouriteBusy = false
        }
    }

    fun unlockWithAc(current: ContentItem) {
        if (!signedIn || container.supabase.session() == null) {
            onLogin()
            return
        }
        if (stage == DetailStage.DOWNLOADING) return
        scope.launch {
            notice = null
            container.content.unlockWithAc(current.id)
                .onSuccess {
                    unlocked = true
                    runCatching { container.profile.refreshWallet() }
                }
                .onFailure { error ->
                    failureKind = error.toApiError().kind
                    notice = when (failureKind) {
                        ApiErrorKind.NOT_ENOUGH_AC -> context.getString(R.string.ac_not_enough)
                        else -> error.toApiError().raw ?: genericError
                    }
                }
        }
    }

    fun startDownload(current: ContentItem) {
        if (!signedIn || container.supabase.session() == null) {
            onLogin()
            return
        }
        if (downloadJob?.isActive == true) return
        stage = DetailStage.DOWNLOADING
        failureKind = null
        notice = null
        downloadJob = scope.launch {
            try {
                val file = withContext(Dispatchers.IO) { container.content.download(context, current).getOrThrow() }
                downloaded = file
                unlocked = true
                stage = DetailStage.DOWNLOADED
                runCatching { container.profile.refreshWallet() }
            } catch (cancelled: CancellationException) {
                stage = DetailStage.IDLE
                throw cancelled
            } catch (error: Throwable) {
                val kind = error.toApiError().kind
                failureKind = kind
                stage = when (kind) {
                    ApiErrorKind.DOWNLOAD_LIMIT -> DetailStage.LIMIT
                    ApiErrorKind.FILE_NOT_UPLOADED -> DetailStage.NO_FILE
                    else -> DetailStage.FAILED
                }
            }
        }
    }

    val current = item
    if (loading) {
        Box(Modifier.fillMaxSize().background(palette.bg), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = palette.blue)
        }
        return
    }
    if (loadFailed || current == null) {
        DetailLoadState(onBack = onBack, onRetry = { reloadTick += 1 }, network = loadFailed)
        return
    }

    val access = container.content.accessState(
        item = current,
        plan = profile?.effectivePlan ?: "free",
        isOwner = current.createdBy != null && current.createdBy == profile?.id,
        isAdmin = profile?.role == "admin",
    )
    val mode = when {
        !signedIn -> DetailMode.SIGNED_OUT
        stage == DetailStage.DOWNLOADING -> DetailMode.DOWNLOADING
        stage == DetailStage.DOWNLOADED -> DetailMode.DOWNLOADED
        stage == DetailStage.LIMIT -> DetailMode.LIMIT
        stage == DetailStage.FAILED -> DetailMode.FAILED
        stage == DetailStage.NO_FILE || current.filePath.isNullOrBlank() -> DetailMode.NO_FILE
        access is AccessState.RequiresLegendary -> DetailMode.NEEDS_LEGENDARY
        access is AccessState.RequiresEpic || access is AccessState.RequiresPlan -> DetailMode.NEEDS_EPIC
        current.acPrice > 0 && !unlocked -> DetailMode.PAID_AC
        else -> DetailMode.FREE
    }

    Box(Modifier.fillMaxSize().background(palette.bg)) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 116.dp),
        ) {
            DetailCover(
                item = current,
                coverUrl = assetUrl(container, current.coverUrl),
                favourite = favourite,
                onBack = onBack,
                onShare = {
                    if (!shareItemText(context, current)) notice = shareFailed
                },
                onToggleFavourite = ::toggleFavourite,
            )

            Column(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Spacer(Modifier.height(1.dp))
                DetailTitle(current.title)
                creator?.let { creatorProfile ->
                    DetailCreatorRow(
                        creator = creatorProfile,
                        avatarUrl = assetUrl(container, creatorProfile.avatarUrl),
                        onOpen = { onOpenCreatorProfile(creatorProfile.id) },
                    )
                }
                DetailStatsCard(current)
                DetailStateNote(
                    mode = mode,
                    item = current,
                    currentPlan = profile?.effectivePlan ?: "free",
                    walletBalance = wallet?.balance ?: 0,
                    failureKind = failureKind,
                )
                DetailInfoChips(current)
                DetailDescription(current.description)
                DetailSimilarContent(
                    items = similar,
                    coverUrl = { assetUrl(container, it.coverUrl) },
                    onOpen = onOpenSimilar,
                )
                notice?.let {
                    Text(
                        it,
                        color = if (failureKind == null) palette.textMuted else Color(0xFFFF7A9A),
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.surface)
                            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
                            .padding(12.dp),
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }

        DetailActionBar(
            mode = mode,
            item = current,
            favourite = favourite,
            onMain = {
                when (mode) {
                    DetailMode.SIGNED_OUT -> onLogin()
                    DetailMode.NEEDS_EPIC, DetailMode.NEEDS_LEGENDARY, DetailMode.LIMIT -> onUpgrade()
                    DetailMode.PAID_AC -> unlockWithAc(current)
                    DetailMode.DOWNLOADED -> {
                        val file = downloaded
                        when {
                            file == null -> notice = openFailed
                            else -> when (openDownloadedFile(context, file)) {
                                MinecraftOpenResult.OPENED -> notice = null
                                MinecraftOpenResult.NOT_INSTALLED -> {
                                    notice = minecraftRequired
                                    Toast.makeText(context, minecraftRequired, Toast.LENGTH_LONG).show()
                                }
                                MinecraftOpenResult.FAILED -> notice = openFailed
                            }
                        }
                    }
                    DetailMode.FAILED -> startDownload(current)
                    DetailMode.NO_FILE, DetailMode.DOWNLOADING -> Unit
                    DetailMode.FREE -> startDownload(current)
                }
            },
            onSecondary = {
                when (mode) {
                    DetailMode.DOWNLOADING -> downloadJob?.cancel()
                    DetailMode.DOWNLOADED -> {
                        val file = downloaded
                        if (file == null || !shareDownloadedFile(context, file)) notice = shareFailed
                    }
                    else -> toggleFavourite()
                }
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun DetailLoadState(onBack: () -> Unit, onRetry: () -> Unit, network: Boolean) {
    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        Row(
            Modifier.fillMaxWidth().padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RoundIconButton(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), onBack)
        }
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                stringResource(if (network) R.string.msg_network_error else R.string.item_not_found_plain),
                color = AppColors.AuthText,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.AuthBlue)
                    .clickable(onClick = onRetry)
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White)
                Text(stringResource(R.string.common_retry), color = Color.White, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun DetailCover(
    item: ContentItem,
    coverUrl: String?,
    favourite: Boolean,
    onBack: () -> Unit,
    onShare: () -> Unit,
    onToggleFavourite: () -> Unit,
) {
    val shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp)
    Box(Modifier.fillMaxWidth().height(262.dp).clip(shape).background(AppColors.AuthSurfaceHigh)) {
        CoverImage(
            url = coverUrl,
            radius = 0.dp,
            animate = true,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(Color.Black.copy(alpha = 0.38f), Color.Transparent, Color.Black.copy(alpha = 0.58f)),
                    ),
                ),
        )
        Row(
            Modifier.align(Alignment.TopStart).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DetailGlassButton(
                icon = { Icon(Icons.AutoMirrored.Filled.ArrowBack, null, tint = Color.White, modifier = Modifier.size(23.dp)) },
                contentDescription = stringResource(R.string.common_back),
                onClick = onBack,
            )
        }
        Row(
            Modifier.align(Alignment.TopEnd).padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DetailGlassButton(
                icon = { Icon(painterResource(R.drawable.ic_phone_share), null, tint = Color.White, modifier = Modifier.size(21.dp)) },
                contentDescription = stringResource(R.string.phone_item_share),
                onClick = onShare,
            )
            DetailGlassButton(
                icon = {
                    Icon(
                        if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        null,
                        tint = if (favourite) Color(0xFFFF7A9A) else Color.White,
                        modifier = Modifier.size(22.dp),
                    )
                },
                contentDescription = stringResource(if (favourite) R.string.item_saved_plain else R.string.item_save_plain),
                onClick = onToggleFavourite,
            )
        }
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xB30A0F1C))
                .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(10.dp))
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                painterResource(R.drawable.ic_phone_stat_folder),
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp),
            )
            Text(
                stringResource(if (item.contentType.equals("world", true)) R.string.phone_item_world else R.string.phone_item_addon),
                color = Color.White,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
            )
        }
        ContentAccessBadge(
            item = item,
            compact = false,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
    }
}

@Composable
private fun DetailGlassButton(
    icon: @Composable () -> Unit,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(42.dp)
            .semantics { this.contentDescription = contentDescription }
            .clip(CircleShape)
            .background(Color(0x990A0F1C))
            .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { icon() }
    }
}

@Composable
private fun DetailTitle(title: String) {
    val arabic = appLocaleCode().equals("ar", true)
    Text(
        title,
        color = AppColors.AuthText,
        fontFamily = if (arabic) TajawalFont else PixelFont,
        fontSize = if (arabic) 24.sp else 26.sp,
        fontWeight = FontWeight.ExtraBold,
        lineHeight = 31.sp,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun DetailCreatorRow(creator: Profile, avatarUrl: String?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        AvatarImage(
            url = avatarUrl,
            size = 40.dp,
            borderColor = Color(0xFFF0C25E),
            borderWidth = 2.dp,
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    creator.displayName ?: creator.username ?: stringResource(R.string.common_guest),
                    color = AppColors.AuthText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4C9AFF), modifier = Modifier.size(15.dp))
            }
            Text(stringResource(R.string.phone_item_creator), color = AppColors.AuthMuted, fontSize = 11.sp)
        }
        Row(
            Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onOpen).padding(horizontal = 6.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.phone_item_profile), color = AppColors.AuthBlueLight, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Icon(Icons.Filled.KeyboardArrowRight, null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(17.dp))
        }
    }
}

@Composable
private fun DetailStatsCard(item: ContentItem) {
    val localeCode = appLocaleCode()
    val stats = listOf(
        StatSpec(R.drawable.ic_phone_stat_downloads, formatCompactCount(item.downloadsCount, localeCode), stringResource(R.string.phone_stat_downloads), Color(0xFF6FA8FF)),
        StatSpec(R.drawable.ic_phone_stat_views, formatCompactCount(item.viewsCount, localeCode), stringResource(R.string.phone_stat_views), Color(0xFF4FD1C5)),
        StatSpec(
            R.drawable.ic_phone_stat_rating,
            if (item.ratingCount > 0) formatRating(item.rating, localeCode) else "—",
            if (item.ratingCount > 0) stringResource(R.string.phone_stat_ratings_count, formatCompactCount(item.ratingCount, localeCode)) else stringResource(R.string.phone_stat_rating),
            Color(0xFFF0C25E),
        ),
        StatSpec(R.drawable.ic_phone_stat_size, formatFileSize(item.fileSize, localeCode), stringResource(R.string.phone_stat_size), Color(0xFF9DB0CF)),
    )
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(20.dp))
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, stat ->
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)).background(stat.color.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(painterResource(stat.icon), null, tint = stat.color, modifier = Modifier.size(20.dp))
                }
                Text(stat.value, color = AppColors.AuthText, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                Text(
                    stat.caption,
                    color = AppColors.AuthMuted,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (index < stats.lastIndex) {
                Box(Modifier.width(1.dp).height(72.dp).background(AppColors.AuthDivider))
            }
        }
    }
}

private data class StatSpec(val icon: Int, val value: String, val caption: String, val color: Color)

@Composable
private fun DetailStateNote(
    mode: DetailMode,
    item: ContentItem,
    currentPlan: String,
    walletBalance: Int,
    failureKind: ApiErrorKind?,
) {
    val note = when (mode) {
        DetailMode.PAID_AC -> NoteSpec(
            R.drawable.ic_phone_info,
            stringResource(R.string.phone_item_balance_after, walletBalance, (walletBalance - item.acPrice).coerceAtLeast(0)),
            Color(0xFFF0C25E),
        )
        DetailMode.NEEDS_EPIC -> NoteSpec(
            R.drawable.ic_phone_gem,
            stringResource(R.string.phone_item_requires_epic_note, currentPlan.replaceFirstChar { it.uppercase() }),
            Color(0xFF9B6BFF),
        )
        DetailMode.NEEDS_LEGENDARY -> NoteSpec(
            R.drawable.ic_phone_crown,
            stringResource(R.string.phone_item_requires_legendary_note, currentPlan.replaceFirstChar { it.uppercase() }),
            Color(0xFFF0C25E),
        )
        DetailMode.SIGNED_OUT -> NoteSpec(
            R.drawable.ic_phone_info,
            stringResource(R.string.phone_item_signed_out_note),
            Color(0xFF9DB0CF),
        )
        DetailMode.DOWNLOADING -> NoteSpec(
            R.drawable.ic_phone_stat_downloads,
            stringResource(R.string.phone_item_downloading_note, formatFileSize(item.fileSize, appLocaleCode())),
            Color(0xFF6FA8FF),
        )
        DetailMode.DOWNLOADED -> NoteSpec(
            R.drawable.ic_phone_check,
            stringResource(R.string.phone_item_downloaded_note),
            Color(0xFF7FD99A),
        )
        DetailMode.LIMIT -> NoteSpec(
            R.drawable.ic_phone_alert,
            stringResource(R.string.phone_item_limit_note),
            Color(0xFFF0A45E),
        )
        DetailMode.FAILED -> NoteSpec(
            R.drawable.ic_phone_alert,
            if (failureKind == ApiErrorKind.NETWORK) stringResource(R.string.phone_item_failed_note) else stringResource(R.string.item_download_failed),
            Color(0xFFFF7A9A),
        )
        DetailMode.NO_FILE -> NoteSpec(
            R.drawable.ic_phone_info,
            stringResource(R.string.item_file_missing),
            Color(0xFFF0A45E),
        )
        DetailMode.FREE -> null
    }
    if (note == null) return
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(15.dp))
            .background(note.color.copy(alpha = 0.10f))
            .border(1.dp, note.color.copy(alpha = 0.48f), RoundedCornerShape(15.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(painterResource(note.icon), null, tint = note.color, modifier = Modifier.size(18.dp))
        Text(note.text, color = note.color, fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
    }
}

private data class NoteSpec(val icon: Int, val text: String, val color: Color)

@Composable
private fun DetailInfoChips(item: ContentItem) {
    val locale = Locale.forLanguageTag(appLocaleCode())
    val date = item.createdAt?.let { TimeFormat.dateTime(it, locale) }?.takeIf { it.isNotBlank() } ?: "—"
    val chips = listOf(
        R.drawable.ic_phone_stat_version to "v${item.version?.takeIf { it.isNotBlank() } ?: "—"}",
        R.drawable.ic_phone_stat_added to stringResource(R.string.phone_item_added, date),
        R.drawable.ic_phone_stat_folder to (item.category?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.common_none)),
        R.drawable.ic_phone_pad to stringResource(R.string.phone_item_bedrock),
    )
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips) { (icon, label) ->
            Row(
                Modifier
                    .height(32.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.AuthSurfaceHigh)
                    .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(painterResource(icon), null, tint = AppColors.AuthMuted, modifier = Modifier.size(14.dp))
                Text(label, color = AppColors.AuthChipLabel, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }
}

@Composable
private fun DetailDescription(description: String?) {
    val text = description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.common_none)
    var expanded by remember(text) { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(20.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(painterResource(R.drawable.ic_phone_stat_size), null, tint = Color(0xFFF0C25E), modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.item_description), color = Color(0xFFF0C25E), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(
            text,
            color = AppColors.AuthChipLabel,
            fontSize = 13.sp,
            lineHeight = 20.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        if (text.length > 160) {
            Text(
                stringResource(if (expanded) R.string.phone_item_read_less else R.string.phone_item_read_more),
                color = AppColors.AuthBlueLight,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { expanded = !expanded }.padding(vertical = 3.dp),
            )
        }
    }
}

@Composable
private fun DetailSimilarContent(
    items: List<ContentItem>,
    coverUrl: (ContentItem) -> String?,
    onOpen: (ContentItem) -> Unit,
) {
    if (items.isEmpty()) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(painterResource(R.drawable.ic_phone_stat_folder), null, tint = Color(0xFFF0C25E), modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.item_similar), color = Color(0xFFF0C25E), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(items, key = { it.id }) { similar ->
                Column(
                    Modifier
                        .width(132.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(AppColors.AuthSurface)
                        .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(15.dp))
                        .clickable { onOpen(similar) }
                        .padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    CoverImage(url = coverUrl(similar), radius = 10.dp, modifier = Modifier.fillMaxWidth().height(74.dp))
                    Text(
                        similar.title,
                        color = AppColors.AuthText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StoreMiniStat(R.drawable.ic_phone_stat_downloads, formatCompactCount(similar.downloadsCount, appLocaleCode()), Color(0xFF6FA8FF))
                        StoreMiniStat(R.drawable.ic_phone_stat_views, formatCompactCount(similar.viewsCount, appLocaleCode()), Color(0xFF4FD1C5))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailActionBar(
    mode: DetailMode,
    item: ContentItem,
    favourite: Boolean,
    onMain: () -> Unit,
    onSecondary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = when (mode) {
        DetailMode.PAID_AC, DetailMode.NEEDS_LEGENDARY -> listOf(Color(0xFFF7CE67), Color(0xFFF0AD2E))
        DetailMode.NEEDS_EPIC, DetailMode.LIMIT -> listOf(Color(0xFFB06BFF), Color(0xFF7B3FF2))
        DetailMode.DOWNLOADED -> listOf(Color(0xFF38D17A), Color(0xFF21A95B))
        DetailMode.SIGNED_OUT, DetailMode.NO_FILE -> listOf(AppColors.AuthSurface, AppColors.AuthSurface)
        else -> listOf(Color(0xFF2E7BEF), Color(0xFF4C9AFF))
    }
    val mainTextColor = if (mode == DetailMode.PAID_AC || mode == DetailMode.NEEDS_LEGENDARY) Color(0xFF1B1405) else Color.White
    val mainEnabled = mode != DetailMode.NO_FILE && mode != DetailMode.DOWNLOADING
    val mainLabel = when (mode) {
        DetailMode.PAID_AC -> stringResource(R.string.phone_item_unlock_ac, item.acPrice)
        DetailMode.NEEDS_EPIC -> stringResource(R.string.phone_item_upgrade_epic)
        DetailMode.NEEDS_LEGENDARY -> stringResource(R.string.phone_item_upgrade_legendary)
        DetailMode.SIGNED_OUT -> stringResource(R.string.phone_item_sign_in_download)
        DetailMode.DOWNLOADING -> stringResource(R.string.item_downloading_plain)
        DetailMode.DOWNLOADED -> stringResource(R.string.phone_item_open_file)
        DetailMode.LIMIT -> stringResource(R.string.phone_item_upgrade_plan)
        DetailMode.FAILED -> stringResource(R.string.phone_item_try_again)
        DetailMode.NO_FILE, DetailMode.FREE -> stringResource(R.string.item_download_plain)
    }
    val leadingIcon = when (mode) {
        DetailMode.PAID_AC -> null
        DetailMode.NEEDS_LEGENDARY -> R.drawable.ic_phone_crown
        DetailMode.NEEDS_EPIC, DetailMode.LIMIT -> R.drawable.ic_phone_gem
        DetailMode.DOWNLOADED -> R.drawable.ic_phone_stat_folder
        DetailMode.FAILED -> null
        else -> R.drawable.ic_phone_stat_downloads
    }

    Row(
        modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, AppColors.AuthBg.copy(alpha = 0.96f), AppColors.AuthBg),
                ),
            )
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 26.dp, bottom = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Brush.linearGradient(colors))
                .border(
                    if (mode == DetailMode.SIGNED_OUT || mode == DetailMode.NO_FILE) 1.dp else 0.dp,
                    AppColors.AuthBorder,
                    RoundedCornerShape(18.dp),
                )
                .clickable(enabled = mainEnabled, onClick = onMain)
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            if (mode == DetailMode.PAID_AC) {
                CoinIcon(size = 20.dp)
                Spacer(Modifier.width(8.dp))
            } else if (mode == DetailMode.FAILED) {
                Icon(Icons.Filled.Refresh, null, tint = mainTextColor, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
            } else if (leadingIcon != null) {
                Icon(painterResource(leadingIcon), null, tint = if (mainEnabled) mainTextColor else AppColors.AuthMuted, modifier = Modifier.size(21.dp))
                Spacer(Modifier.width(8.dp))
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    mainLabel,
                    color = if (mainEnabled) mainTextColor else AppColors.AuthMuted,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (mode == DetailMode.DOWNLOADING) {
                    Spacer(Modifier.height(3.dp))
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(0.72f).height(3.dp).clip(CircleShape),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.22f),
                    )
                }
            }
        }
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.AuthSurface)
                .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(18.dp))
                .clickable(onClick = onSecondary),
            contentAlignment = Alignment.Center,
        ) {
            when (mode) {
                DetailMode.DOWNLOADING -> Icon(Icons.Filled.Close, stringResource(R.string.common_cancel), tint = AppColors.AuthText, modifier = Modifier.size(22.dp))
                DetailMode.DOWNLOADED -> Icon(painterResource(R.drawable.ic_phone_share), stringResource(R.string.phone_item_share), tint = AppColors.AuthText, modifier = Modifier.size(22.dp))
                else -> Icon(
                    if (favourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                    stringResource(if (favourite) R.string.item_saved_plain else R.string.item_save_plain),
                    tint = if (favourite) Color(0xFFFF7A9A) else AppColors.AuthText,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

@Composable
private fun RoundIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).background(AppColors.AuthSurface).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = AppColors.AuthText, modifier = Modifier.size(22.dp))
    }
}

internal fun formatCompactCount(value: Int, localeCode: String): String {
    val arabic = localeCode.equals("ar", true)
    val locale = if (arabic) Locale.forLanguageTag("ar") else Locale.US
    fun number(amount: Double): String {
        val rounded = (amount * 10.0).roundToInt() / 10.0
        val pattern = if (rounded % 1.0 == 0.0) "%.0f" else "%.1f"
        val western = String.format(Locale.US, pattern, rounded)
        if (!arabic) return western
        val formatter = java.text.NumberFormat.getNumberInstance(locale).apply {
            maximumFractionDigits = 1
            minimumFractionDigits = 0
            isGroupingUsed = false
        }
        return formatter.format(rounded)
    }
    return when {
        value >= 1_000_000 -> number(value / 1_000_000.0) + if (arabic) " م" else "M"
        value >= 1_000 -> number(value / 1_000.0) + if (arabic) " ألف" else "K"
        else -> java.text.NumberFormat.getIntegerInstance(locale).format(value)
    }
}

private fun formatRating(value: Double, localeCode: String): String {
    if (value <= 0.0) return "—"
    return java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag(localeCode)).apply {
        minimumFractionDigits = 1
        maximumFractionDigits = 1
    }.format(value)
}

private fun formatFileSize(bytes: Long?, localeCode: String): String {
    if (bytes == null || bytes <= 0L) return "—"
    val mb = bytes / (1024.0 * 1024.0)
    val formatted = java.text.NumberFormat.getNumberInstance(Locale.forLanguageTag(localeCode)).apply {
        maximumFractionDigits = if (mb >= 10) 0 else 1
        minimumFractionDigits = 0
    }.format(mb)
    return if (localeCode.equals("ar", true)) "$formatted م.ب" else "$formatted MB"
}

private fun shareItemText(context: Context, item: ContentItem): Boolean = runCatching {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, item.title)
        putExtra(Intent.EXTRA_TEXT, item.title)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, item.title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess

private fun openDownloadedFile(context: Context, downloaded: DownloadedFile): MinecraftOpenResult {
    if (context.packageManager.getLaunchIntentForPackage(MINECRAFT_ANDROID_PACKAGE) == null) {
        return MinecraftOpenResult.NOT_INSTALLED
    }

    val intent = runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", downloaded.file)
        val extension = downloaded.file.extension.lowercase(Locale.ROOT)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/zip"
        Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            setPackage(MINECRAFT_ANDROID_PACKAGE)
            clipData = ClipData.newRawUri(downloaded.fileName, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }.getOrElse { return MinecraftOpenResult.FAILED }

    if (intent.resolveActivity(context.packageManager) == null) {
        return MinecraftOpenResult.FAILED
    }

    return try {
        // An explicit package prevents Android from offering file managers,
        // browsers, archive tools, or any application other than Minecraft.
        context.startActivity(intent)
        MinecraftOpenResult.OPENED
    } catch (_: ActivityNotFoundException) {
        if (context.packageManager.getLaunchIntentForPackage(MINECRAFT_ANDROID_PACKAGE) == null) {
            MinecraftOpenResult.NOT_INSTALLED
        } else {
            MinecraftOpenResult.FAILED
        }
    } catch (_: SecurityException) {
        MinecraftOpenResult.FAILED
    }
}

private fun shareDownloadedFile(context: Context, downloaded: DownloadedFile): Boolean = runCatching {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", downloaded.file)
    val extension = downloaded.file.extension.lowercase(Locale.ROOT)
    val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "application/zip"
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = mime
        clipData = ClipData.newRawUri(downloaded.fileName, uri)
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, downloaded.fileName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}.isSuccess
