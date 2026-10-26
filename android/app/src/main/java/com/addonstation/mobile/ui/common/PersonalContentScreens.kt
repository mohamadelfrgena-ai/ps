package com.addonstation.mobile.ui.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorSubmission
import com.addonstation.mobile.data.repo.ContentDownloadEntry
import com.addonstation.mobile.ui.components.ContentBannerKind
import com.addonstation.mobile.ui.components.ContentCard
import com.addonstation.mobile.ui.components.ContentStatusBanner
import com.addonstation.mobile.ui.components.CoverImage
import com.addonstation.mobile.ui.components.ErrorKind
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.LoadingPattern
import com.addonstation.mobile.ui.components.LoadingState
import com.addonstation.mobile.ui.components.PhoneChipRow
import com.addonstation.mobile.ui.components.PhoneEmptyState
import com.addonstation.mobile.ui.components.PhoneGroupLabel
import com.addonstation.mobile.ui.components.PhoneHeaderTextAction
import com.addonstation.mobile.ui.components.PhoneListChip
import com.addonstation.mobile.ui.components.PhoneListHeader
import com.addonstation.mobile.ui.components.PhoneStatusChip
import com.addonstation.mobile.ui.components.PhoneStatusTone
import com.addonstation.mobile.ui.components.StatTile
import com.addonstation.mobile.ui.components.toErrorKind
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.util.TimeFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Favorites with content-type segments and undoable removal. */
@Composable
fun FavoritesScreen(
    onBack: () -> Unit,
    onOpen: (ContentItem) -> Unit,
    onBrowseStore: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    var allItems by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    val removing = remember { mutableStateListOf<String>() }
    var selectedType by remember { mutableStateOf("addon") }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var errorKind by remember { mutableStateOf(ErrorKind.Network) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        failed = false
        try {
            allItems = container.content.favorites()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            android.util.Log.w("FavoritesScreen", "Favorite load failed", error)
            errorKind = error.toErrorKind()
            failed = true
        } finally {
            loading = false
        }
    }

    val addons = allItems.count { it.contentType != "world" }
    val worlds = allItems.size - addons
    val visible = allItems.filter {
        if (selectedType == "world") it.contentType == "world" else it.contentType != "world"
    }
    val removedText = stringResource(R.string.favorites_removed)
    val undoText = stringResource(R.string.common_undo)

    Box(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        Column(Modifier.fillMaxSize()) {
            PhoneListHeader(
                title = stringResource(R.string.home_qa_favorites),
                count = allItems.size,
                onBack = onBack,
            )
            PhoneChipRow(
                chips = listOf(
                    PhoneListChip("addon", stringResource(R.string.store_tab_addons), addons),
                    PhoneListChip("world", stringResource(R.string.store_tab_worlds), worlds),
                ),
                selectedKey = selectedType,
                onSelected = { selectedType = it },
            )
            if (loading && allItems.isNotEmpty()) {
                LoadingState(pattern = LoadingPattern.Refreshing)
            }
            if (failed && allItems.isNotEmpty()) {
                ContentStatusBanner(ContentBannerKind.ServerRefresh, onRetry = { reloadTick += 1 })
            }
            when {
                loading && allItems.isEmpty() -> LoadingState(
                    pattern = LoadingPattern.Skeleton,
                    onRetry = { reloadTick += 1 },
                )
                failed && allItems.isEmpty() -> ErrorState(
                    onRetry = { reloadTick += 1 },
                    kind = errorKind,
                    modifier = Modifier.weight(1f),
                )
                visible.isEmpty() -> PhoneEmptyState(
                    icon = Icons.Filled.FavoriteBorder,
                    color = Color(0xFFFF6FAE),
                    title = stringResource(R.string.favorites_empty_title),
                    body = stringResource(R.string.favorites_empty_body),
                    actionText = stringResource(R.string.browse_store),
                    onAction = onBrowseStore,
                    modifier = Modifier.weight(1f),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    gridItems(visible, key = ContentItem::id) { item ->
                        AnimatedVisibility(visible = item.id !in removing, exit = fadeOut()) {
                            ContentCard(
                                title = item.title,
                                coverUrl = container.siteAssetUrl(item.coverUrl),
                                planLabel = planLabelOf(item.plan),
                                metaLine = metaOf(item),
                                favourite = true,
                                onFavourite = {
                                    if (item.id !in removing) scope.launch {
                                        val changed = container.content.toggleFavorite(item.id).isSuccess
                                        if (!changed) return@launch
                                        removing += item.id
                                        delay(220)
                                        allItems = allItems.filterNot { it.id == item.id }
                                        removing -= item.id
                                        val result = snackbar.showSnackbar(
                                            message = removedText,
                                            actionLabel = undoText,
                                            withDismissAction = true,
                                            duration = androidx.compose.material3.SnackbarDuration.Short,
                                        )
                                        if (result == SnackbarResult.ActionPerformed) {
                                            container.content.toggleFavorite(item.id)
                                            allItems = allItems + item
                                        }
                                    }
                                },
                                onClick = { onOpen(item) },
                            )
                        }
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp))
    }
}

/** Download history with monthly plan usage and real download dates. */
@Composable
fun DownloadsScreen(
    onBack: () -> Unit,
    onOpen: (ContentItem) -> Unit,
    onBrowseStore: () -> Unit,
    onUpgrade: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val plans by container.state.plans.collectAsState()
    var entries by remember { mutableStateOf<List<ContentDownloadEntry>>(emptyList()) }
    var monthCount by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var errorKind by remember { mutableStateOf(ErrorKind.Network) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        failed = false
        try {
            entries = container.content.downloadEntries()
            monthCount = container.content.downloadsThisMonthCount()
            if (plans.isEmpty()) container.plans.loadPlans()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            android.util.Log.w("DownloadsScreen", "Download history load failed", error)
            errorKind = error.toErrorKind()
            failed = true
        } finally {
            loading = false
        }
    }

    val activePlan = plans.firstOrNull { it.id == profile?.effectivePlan }
    val monthlyLimit = (activePlan?.downloadLimit ?: 20).coerceAtLeast(1)
    val reached = monthCount >= monthlyLimit
    val week = entries.filter { isWithinDays(it.downloadedAt, 7) }
    val earlier = entries.filterNot { isWithinDays(it.downloadedAt, 7) }

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        PhoneListHeader(
            title = stringResource(R.string.home_qa_downloads),
            count = entries.size,
            onBack = onBack,
        )
        if (loading && entries.isNotEmpty()) LoadingState(pattern = LoadingPattern.Refreshing)
        if (failed && entries.isNotEmpty()) {
            ContentStatusBanner(ContentBannerKind.ServerRefresh, onRetry = { reloadTick += 1 })
        }
        when {
            loading && entries.isEmpty() -> LoadingState(
                pattern = LoadingPattern.Skeleton,
                onRetry = { reloadTick += 1 },
            )
            failed && entries.isEmpty() -> ErrorState(
                onRetry = { reloadTick += 1 },
                kind = errorKind,
                modifier = Modifier.weight(1f),
            )
            entries.isEmpty() -> PhoneEmptyState(
                icon = Icons.Filled.Inbox,
                color = AppColors.Blue,
                title = stringResource(R.string.downloads_empty_title),
                body = stringResource(R.string.downloads_empty_body),
                actionText = stringResource(R.string.browse_store),
                onAction = onBrowseStore,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                item(key = "usage") {
                    DownloadUsageCard(monthCount, monthlyLimit, reached, onUpgrade)
                }
                if (week.isNotEmpty()) {
                    item(key = "week-label") { PhoneGroupLabel(stringResource(R.string.list_this_week)) }
                    items(week, key = { "week-${it.item.id}" }) { entry -> DownloadHistoryRow(entry, onOpen) }
                }
                if (earlier.isNotEmpty()) {
                    item(key = "earlier-label") { PhoneGroupLabel(stringResource(R.string.list_earlier)) }
                    items(earlier, key = { "earlier-${it.item.id}" }) { entry -> DownloadHistoryRow(entry, onOpen) }
                }
            }
        }
    }
}

@Composable
private fun DownloadUsageCard(count: Int, limit: Int, reached: Boolean, onUpgrade: () -> Unit) {
    val palette = LocalHomePalette.current
    val color = if (reached) AppColors.Red else palette.blue
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .background(if (reached) AppColors.Red.copy(alpha = 0.10f) else palette.surface)
            .border(1.dp, if (reached) AppColors.Red.copy(alpha = 0.45f) else palette.border, RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (reached) stringResource(R.string.downloads_limit_reached)
                else stringResource(R.string.downloads_month_title),
                style = AppText.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                color = if (reached) AppColors.Red else palette.text,
                modifier = Modifier.weight(1f),
            )
            Text(stringResource(R.string.downloads_usage, count, limit), style = AppText.bodySmall, color = color)
        }
        LinearProgressIndicator(
            progress = { (count.toFloat() / limit.toFloat()).coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
            color = color,
            trackColor = palette.surfaceHigh,
        )
        if (reached) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.downloads_limit_resets, daysUntilMonthReset()),
                    style = AppText.tiny,
                    color = palette.textMuted,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    stringResource(R.string.downloads_upgrade),
                    style = AppText.button,
                    color = palette.blueLink,
                    modifier = Modifier.clickable(onClick = onUpgrade).padding(5.dp),
                )
            }
        }
    }
}

@Composable
private fun DownloadHistoryRow(entry: ContentDownloadEntry, onOpen: (ContentItem) -> Unit) {
    val item = entry.item
    val container = rememberContainer()
    val palette = LocalHomePalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp)).clickable { onOpen(item) }.padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        CoverImage(container.siteAssetUrl(item.coverUrl), modifier = Modifier.width(76.dp).height(62.dp), radius = 13.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(item.title, style = AppText.bodySmall.copy(fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                PhoneStatusChip(planLabelOf(item.plan), if (item.plan == "free") PhoneStatusTone.Green else PhoneStatusTone.Orange)
                PhoneStatusChip(
                    stringResource(if (item.contentType == "world") R.string.store_tab_worlds else R.string.store_tab_addons),
                    PhoneStatusTone.Blue,
                )
            }
            Text(
                stringResource(
                    R.string.downloads_meta,
                    TimeFormat.dateOnly(entry.downloadedAt, Locale.getDefault()),
                    TimeFormat.fileSizeLabel(item.fileSize),
                ),
                style = AppText.tiny,
                color = palette.textMuted,
                maxLines = 1,
            )
        }
        Box(
            Modifier.size(42.dp).clip(RoundedCornerShape(13.dp)).background(palette.blue.copy(alpha = 0.16f))
                .clickable { onOpen(item) },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Download, contentDescription = stringResource(R.string.downloads_again), tint = palette.blueLink, modifier = Modifier.size(21.dp))
        }
    }
}

private data class MyContentEntry(
    val key: String,
    val title: String,
    val contentType: String,
    val coverUrl: String?,
    val status: String,
    val reason: String?,
    val downloads: Int,
    val views: Int,
    val rating: Double,
    val item: ContentItem?,
)

/** Creator content plus pending/rejected submissions, keeping existing repositories. */
@Composable
fun MyContentScreen(
    onBack: () -> Unit,
    onOpen: (ContentItem) -> Unit,
    onOpenDashboard: () -> Unit,
    onBecomeCreator: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    var entries by remember { mutableStateOf<List<MyContentEntry>>(emptyList()) }
    var filter by remember { mutableStateOf("all") }
    var loading by remember { mutableStateOf(profile?.role == "creator") }
    var failed by remember { mutableStateOf(false) }
    var errorKind by remember { mutableStateOf(ErrorKind.Network) }
    var reloadTick by remember { mutableStateOf(0) }
    val isCreator = profile?.role == "creator"

    LaunchedEffect(isCreator, reloadTick) {
        if (!isCreator) {
            loading = false
            entries = emptyList()
            return@LaunchedEffect
        }
        loading = true
        failed = false
        try {
            val published = container.content.myContent()
            val submissions = container.creators.mySubmissionsStrict()
            entries = buildMyContentEntries(published, submissions)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            android.util.Log.w("MyContentScreen", "Creator content load failed", error)
            errorKind = error.toErrorKind()
            failed = true
        } finally {
            loading = false
        }
    }

    val publishedCount = entries.count { it.status == "published" }
    val reviewCount = entries.count { it.status == "review" }
    val rejectedCount = entries.count { it.status == "rejected" }
    val newContentLabel = stringResource(R.string.list_new)
    val visible = when (filter) {
        "published" -> entries.filter { it.status == "published" }
        "review" -> entries.filter { it.status == "review" }
        "rejected" -> entries.filter { it.status == "rejected" }
        else -> entries
    }

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        PhoneListHeader(
            title = stringResource(R.string.profile_stats_content),
            count = entries.size,
            onBack = onBack,
            actions = {
                if (isCreator) PhoneHeaderTextAction(
                    text = "+ $newContentLabel",
                    onClick = onOpenDashboard,
                )
            },
        )
        when {
            !isCreator -> PhoneEmptyState(
                icon = Icons.Filled.Inventory2,
                color = AppColors.Gold,
                title = stringResource(R.string.my_content_creator_only_title),
                body = stringResource(R.string.my_content_creator_only_body),
                actionText = stringResource(R.string.my_content_become_creator),
                onAction = onBecomeCreator,
                modifier = Modifier.weight(1f),
            )
            loading && entries.isEmpty() -> LoadingState(
                pattern = LoadingPattern.Skeleton,
                onRetry = { reloadTick += 1 },
            )
            failed && entries.isEmpty() -> ErrorState(
                onRetry = { reloadTick += 1 },
                kind = errorKind,
                modifier = Modifier.weight(1f),
            )
            else -> {
                if (loading) LoadingState(pattern = LoadingPattern.Refreshing)
                if (failed) ContentStatusBanner(ContentBannerKind.ServerRefresh, onRetry = { reloadTick += 1 })
                CreatorStatsRow(entries)
                PhoneChipRow(
                    chips = listOf(
                        PhoneListChip("all", stringResource(R.string.common_all), entries.size),
                        PhoneListChip("published", stringResource(R.string.list_published), publishedCount),
                        PhoneListChip("review", stringResource(R.string.list_in_review), reviewCount),
                        PhoneListChip("rejected", stringResource(R.string.plan_status_rejected), rejectedCount),
                    ),
                    selectedKey = filter,
                    onSelected = { filter = it },
                )
                if (visible.isEmpty()) {
                    PhoneEmptyState(
                        icon = Icons.Filled.Inventory2,
                        color = AppColors.Blue,
                        title = stringResource(R.string.my_content_empty_title),
                        body = stringResource(R.string.my_content_empty_body),
                        actionText = stringResource(R.string.my_content_open_dashboard),
                        onAction = onOpenDashboard,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                        verticalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        items(visible, key = MyContentEntry::key) { entry ->
                            CreatorContentRow(entry, onOpenDashboard) {
                                entry.item?.let(onOpen)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreatorStatsRow(entries: List<MyContentEntry>) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        StatTile(entries.count { it.status == "published" }.toString(), stringResource(R.string.list_published), Modifier.weight(1f), AppColors.Green)
        StatTile(entries.sumOf(MyContentEntry::downloads).toString(), stringResource(R.string.my_content_downloads_stat), Modifier.weight(1f), AppColors.Blue)
        StatTile(entries.sumOf(MyContentEntry::views).toString(), stringResource(R.string.my_content_views_stat), Modifier.weight(1f), AppColors.Purple)
    }
}

@Composable
private fun CreatorContentRow(entry: MyContentEntry, onEdit: () -> Unit, onOpen: () -> Unit) {
    val container = rememberContainer()
    val palette = LocalHomePalette.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            CoverImage(container.siteAssetUrl(entry.coverUrl), modifier = Modifier.width(76.dp).height(62.dp), radius = 13.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(entry.title, style = AppText.bodySmall.copy(fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PhoneStatusChip(
                        stringResource(if (entry.contentType == "world") R.string.store_tab_worlds else R.string.store_tab_addons),
                        PhoneStatusTone.Blue,
                    )
                    val (statusText, tone) = when (entry.status) {
                        "published" -> stringResource(R.string.list_published) to PhoneStatusTone.Green
                        "rejected" -> stringResource(R.string.plan_status_rejected) to PhoneStatusTone.Red
                        else -> stringResource(R.string.list_in_review) to PhoneStatusTone.Orange
                    }
                    PhoneStatusChip(statusText, tone)
                }
                Text(
                    "↓ ${entry.downloads}  ◉ ${entry.views}  ★ ${"%.1f".format(Locale.US, entry.rating)}",
                    style = AppText.tiny,
                    color = palette.textMuted,
                )
            }
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(palette.surfaceHigh).clickable(onClick = onEdit),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.my_content_edit), tint = palette.blueLink, modifier = Modifier.size(19.dp))
            }
        }
        entry.reason?.takeIf(String::isNotBlank)?.let { reason ->
            Text(
                stringResource(R.string.list_reason, reason),
                style = AppText.tiny,
                color = AppColors.Red,
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Red.copy(alpha = 0.10f)).padding(9.dp),
            )
        }
    }
}

private fun buildMyContentEntries(
    published: List<ContentItem>,
    submissions: List<CreatorSubmission>,
): List<MyContentEntry> {
    val content = published.map { item ->
        MyContentEntry(
            key = "item-${item.id}",
            title = item.title,
            contentType = item.contentType,
            coverUrl = item.coverUrl,
            status = "published",
            reason = null,
            downloads = item.downloadsCount,
            views = item.viewsCount,
            rating = item.rating,
            item = item,
        )
    }
    val pending = submissions
        .filterNot { it.status == "approved" && it.contentItemId != null }
        .map { submission ->
            MyContentEntry(
                key = "submission-${submission.id}",
                title = submission.title,
                contentType = submission.contentType,
                coverUrl = submission.coverUrl,
                status = if (submission.status == "rejected") "rejected" else "review",
                reason = submission.adminNote.takeIf { submission.status == "rejected" },
                downloads = 0,
                views = 0,
                rating = 0.0,
                item = null,
            )
        }
    return content + pending
}

private fun isCurrentMonth(iso: String?): Boolean {
    val millis = TimeFormat.parseMillis(iso) ?: return false
    val now = Calendar.getInstance()
    val date = Calendar.getInstance().apply { timeInMillis = millis }
    return now.get(Calendar.YEAR) == date.get(Calendar.YEAR) && now.get(Calendar.MONTH) == date.get(Calendar.MONTH)
}

private fun isWithinDays(iso: String?, days: Int): Boolean {
    val millis = TimeFormat.parseMillis(iso) ?: return false
    return System.currentTimeMillis() - millis <= TimeUnit.DAYS.toMillis(days.toLong())
}

private fun daysUntilMonthReset(): Int {
    val now = Calendar.getInstance()
    return (now.getActualMaximum(Calendar.DAY_OF_MONTH) - now.get(Calendar.DAY_OF_MONTH) + 1).coerceAtLeast(1)
}
