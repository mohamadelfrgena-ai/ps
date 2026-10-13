package com.addonstation.mobile.ui.chat

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.zIndex
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatMessageUi
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.data.model.StickerPack
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.FilterChip
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.ui.theme.nameColor
import androidx.compose.runtime.collectAsState
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.launch
import java.util.Locale

private val RECENT_FALLBACK = listOf("👍", "❤️", "😂", "🔥", "🎉", "😮")

/** Docked emoji/sticker panel: composer remains visible above it while the message list resizes. */
@Composable
fun EmojiStickerPanel(
    stickers: List<SiteAsset>,
    packs: List<StickerPack>,
    resolvedStickerUrl: (String) -> String?,
    onEmoji: (String) -> Unit,
    onSticker: (String) -> Unit,
    /** A sticker reopened from chat selects its pack tab and scrolls into view. */
    focusSticker: String? = null,
) {
    val container = rememberContainer()
    var tab by remember { mutableStateOf(if (focusSticker != null) 1 else 0) }
    LaunchedEffect(focusSticker) {
        if (focusSticker != null) tab = 1
    }
    var selectedPack by remember { mutableStateOf<String?>(null) }
    var selectedEmojiCategory by remember { mutableIntStateOf(0) }
    val recentRaw by container.prefs.recentEmojis.collectAsState(initial = "")
    val recent = recentRaw.split(' ').filter { it.isNotBlank() }.take(8).ifEmpty { RECENT_FALLBACK }
    val panelHeight = if (tab == 0) 300.dp else 340.dp
    val panelShape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .height(panelHeight)
            .clip(panelShape)
            .background(Spec.barSurface),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.border))
        Column(
            Modifier.fillMaxSize().padding(start = 10.dp, end = 10.dp, top = 10.dp, bottom = 8.dp),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Spec.surface)
                    .border(1.dp, Spec.border, RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ChatPanelSegment(
                    label = stringResource(R.string.chat_emoji),
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    modifier = Modifier.weight(1f),
                )
                ChatPanelSegment(
                    label = stringResource(R.string.chat_stickers_tab),
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(8.dp))
            if (tab == 0) {
                val emojiGridState = rememberLazyGridState()
                val categories = listOf(
                    Triple(R.string.chat_emoji_cat_smileys, Icons.Filled.EmojiEmotions, 0),
                    Triple(R.string.chat_emoji_cat_gestures, Icons.Filled.PanTool, 21),
                    Triple(R.string.chat_emoji_cat_hearts, Icons.Filled.Favorite, 27),
                    Triple(R.string.chat_emoji_cat_objects, Icons.Filled.Lightbulb, 32),
                    Triple(R.string.chat_emoji_cat_symbols, Icons.Filled.Tag, 44),
                )
                if (recent.isNotEmpty()) {
                    Text(
                        stringResource(R.string.chat_recent),
                        style = Spec.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                        color = Spec.gold,
                        modifier = Modifier.padding(start = 2.dp, bottom = 3.dp),
                    )
                    LazyRow(
                        Modifier.fillMaxWidth().height(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        items(recent) { emoji ->
                            Text(
                                emoji,
                                style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp),
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { onEmoji(emoji) },
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.border))
                }
                LazyVerticalGrid(
                    state = emojiGridState,
                    columns = GridCells.Fixed(8),
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 2.dp),
                    verticalArrangement = Arrangement.spacedBy(0.dp),
                ) {
                    items(ChatViewModel_Emojis) { emoji ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(38.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onEmoji(emoji) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                emoji,
                                style = androidx.compose.ui.text.TextStyle(fontSize = 24.sp),
                                modifier = Modifier.semantics { contentDescription = emoji },
                            )
                        }
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.border))
                Row(
                    Modifier.fillMaxWidth().height(44.dp).padding(top = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    categories.forEachIndexed { index, (labelRes, icon, startIndex) ->
                        Box(
                            Modifier
                                .size(width = 52.dp, height = 36.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selectedEmojiCategory == index) Spec.blue.copy(alpha = 0.18f) else Color.Transparent)
                                .clickable {
                                    selectedEmojiCategory = index
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                icon,
                                contentDescription = stringResource(labelRes),
                                tint = if (selectedEmojiCategory == index) Spec.blueLink else Spec.textMuted,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                }
                // Category taps scroll to the first emoji in that group without changing the emoji set.
                androidx.compose.runtime.LaunchedEffect(selectedEmojiCategory) {
                    val target = categories.getOrNull(selectedEmojiCategory)?.third ?: 0
                    emojiGridState.animateScrollToItem(target)
                }
            } else {
                val visible = remember(stickers, selectedPack) {
                    stickers.filter { selectedPack == null || it.packId == selectedPack }
                }
                val groups = remember(visible) { com.addonstation.mobile.ui.groupAssetsByCollection(visible) }
                val stickerGridState = rememberLazyGridState()
                val focusIndex = remember(groups, focusSticker) {
                    var found = -1
                    if (focusSticker != null) {
                        var idx = 0
                        run search@{
                            for (section in groups) {
                                idx++
                                for (asset in section.assets) {
                                    val hit = asset.fileUrl == focusSticker ||
                                        resolvedStickerUrl(asset.fileUrl)?.substringBefore('?')
                                            ?.endsWith(focusSticker, ignoreCase = true) == true
                                    if (hit) { found = idx; return@search }
                                    idx++
                                }
                            }
                        }
                    }
                    found
                }
                androidx.compose.runtime.LaunchedEffect(focusIndex) {
                    if (focusIndex >= 0) stickerGridState.animateScrollToItem(focusIndex)
                }
                LazyRow(
                    Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    item {
                        ChatPackChip(
                            label = stringResource(R.string.common_all),
                            selected = selectedPack == null,
                            onClick = { selectedPack = null },
                        )
                    }
                    items(packs, key = { it.id }) { pack ->
                        ChatPackChip(
                            label = pack.name,
                            selected = selectedPack == pack.id,
                            onClick = { selectedPack = pack.id },
                        )
                    }
                }
                if (groups.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.common_none), style = Spec.secondary, color = Spec.textMuted)
                    }
                } else {
                    LazyVerticalGrid(
                        state = stickerGridState,
                        columns = GridCells.Fixed(4),
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(bottom = 2.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        groups.forEach { section ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "sec-${section.title}") {
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        section.title.uppercase(Locale.ROOT),
                                        style = androidx.compose.ui.text.TextStyle(
                                            fontSize = 12.sp,
                                            fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                                            letterSpacing = 0.6.sp,
                                        ),
                                        color = Spec.gold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    val countDescription = pluralStringResource(
                                        R.plurals.chat_stickers_count,
                                        section.assets.size,
                                        section.assets.size,
                                    )
                                    Box(
                                        Modifier
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Spec.surfaceHigh)
                                            .padding(horizontal = 8.dp, vertical = 1.dp)
                                            .semantics { contentDescription = countDescription },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(section.assets.size.toString(), style = Spec.badge, color = Spec.textMuted)
                                    }
                                    Box(Modifier.weight(1f).height(1.dp).background(Spec.border))
                                }
                            }
                            itemsIndexed(section.assets, key = { _, sticker -> sticker.id }) { index, sticker ->
                                val restored = focusSticker != null &&
                                    (sticker.fileUrl == focusSticker ||
                                        resolvedStickerUrl(sticker.fileUrl)?.substringBefore('?')
                                            ?.endsWith(focusSticker, ignoreCase = true) == true)
                                Box(
                                    Modifier
                                        .aspectRatio(1f)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Spec.surfaceHigh)
                                        .background(if (restored) AppColors.Green else Color.Transparent)
                                        .border(1.dp, if (restored) Spec.blue else Spec.border, RoundedCornerShape(14.dp))
                                        .semantics(mergeDescendants = true) { contentDescription = "${section.title}, ${index + 1}" }
                                        .clickable { onSticker(sticker.fileUrl) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    com.addonstation.mobile.ui.components.CoverImage(
                                        url = resolvedStickerUrl(sticker.fileUrl),
                                        radius = 13.dp,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatPanelSegment(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .semantics { role = Role.Tab; this.selected = selected }
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Spec.blue else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
            color = if (selected) Color.White else Spec.chipLabel,
            maxLines = 1,
        )
    }
}

@Composable
private fun ChatPackChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Box(
        Modifier
            .semantics { this.selected = selected }
            .height(36.dp)
            .clip(shape)
            .background(if (selected) Spec.blue else Spec.surface)
            .border(if (selected) 0.dp else 1.dp, if (selected) Spec.blue else Spec.border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = androidx.compose.ui.text.TextStyle(fontSize = 13.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
            color = if (selected) Color.White else Spec.chipLabel,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private val ChatViewModel_Emojis: List<String> = listOf(
    // Keep the same emoji set, ordered by the category strip for predictable scroll targets.
    "😀", "😃", "😄", "😍", "😂", "🥰", "😎", "🤣", "😅", "😇", "😉", "🤔",
    "😴", "🤯", "🥳", "😢", "😭", "😡", "🤩", "😊", "😁",
    "👍", "👎", "👏", "🙏", "💪", "🤝",
    "❤️", "💔", "🧡", "💛", "💜",
    "🔥", "💎", "👑", "🍃", "⭐", "🎮", "🕹", "🧊", "🏆", "⚽", "🚀", "💬",
    "✨", "🎉", "✅", "❌",
)

/**
 * Attachment chooser: both options remain visible; the server remains authoritative
 * and the audio/video tile explains that only admins may use it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachSheet(
    onDismiss: () -> Unit,
    onPhoto: () -> Unit,
    onAudioVideo: () -> Unit,
    isAdmin: Boolean = false,
) {
    SheetScaffold(onDismiss) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AttachmentOptionCard(
                icon = Icons.Filled.Image,
                accent = AppColors.Blue,
                title = stringResource(R.string.chat_attach_photo),
                description = stringResource(R.string.chat_image_max),
                modifier = Modifier.weight(1f),
                onClick = onPhoto,
            )
            AttachmentOptionCard(
                icon = Icons.Filled.PlayArrow,
                accent = AppColors.Purple,
                title = stringResource(R.string.chat_attach_media),
                description = stringResource(R.string.chat_media_max),
                modifier = Modifier.weight(1f),
                locked = !isAdmin,
                lockedLabel = stringResource(R.string.chat_admin_only_short),
                onClick = onAudioVideo,
            )
        }
        if (!isAdmin) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.Blue.copy(alpha = 0.12f))
                    .border(1.dp, AppColors.Blue.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Lightbulb, contentDescription = null, tint = AppColors.Blue, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.chat_media_admin_only), style = AppText.bodySmall, color = AppColors.Text)
            }
        }
        Text(
            "${stringResource(R.string.chat_image_formats)} · ${stringResource(R.string.chat_image_max)}",
            style = AppText.tiny,
            color = AppColors.Muted,
            modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun AttachmentOptionCard(
    icon: ImageVector,
    accent: Color,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    lockedLabel: String = "",
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = modifier
            .heightIn(min = 152.dp)
            .clip(shape)
            .background(if (locked) AppColors.Surface.copy(alpha = 0.72f) else AppColors.Surface)
            .border(1.dp, AppColors.Border, shape)
            .clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(56.dp).clip(RoundedCornerShape(18.dp)).background(accent.copy(alpha = if (locked) 0.12f else 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent.copy(alpha = if (locked) 0.60f else 1f), modifier = Modifier.size(28.dp))
        }
        Text(
            title,
            style = AppText.body.copy(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
            color = AppColors.Text,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Text(
            description,
            style = AppText.tiny.copy(fontSize = 12.sp),
            color = AppColors.Muted,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        if (locked) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Elevated)
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(12.dp))
                Text(lockedLabel, style = AppText.tiny, color = AppColors.Muted, maxLines = 1)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachMediaSheet(onDismiss: () -> Unit, onAudio: () -> Unit, onVideo: () -> Unit) {
    SheetScaffold(onDismiss) {
        SheetRow("🎙️", stringResource(R.string.chat_audio_label_plain), onAudio)
        SheetRow("🎬", stringResource(R.string.chat_video_label_plain), onVideo)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SheetScaffold(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
    ) {
        // اللوح المنبثق نافذة مستقلة، فلا يصله حجز الجذر ⇒ نحجز شريط الحالة هنا
        Column(
            Modifier
                .statusBarsPadding()
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) { content() }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SheetRow(emoji: String, title: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(emoji, style = AppText.body)
        Text(title, style = AppText.body, color = AppColors.Text)
    }
}

/** قائمة الأعضاء مجمّعة حسب الدور/الخطة، مع بحث محلي وملخص حالة الظهور. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersSheet(
    authors: List<Profile>,
    onDismiss: () -> Unit,
    onOpenProfile: (Profile) -> Unit,
) {
    val container = rememberContainer()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var query by rememberSaveable { mutableStateOf("") }
    val filteredAuthors = remember(authors, query) {
        val needle = query.trim()
        if (needle.isEmpty()) authors
        else authors.filter { person ->
            listOfNotNull(person.displayName, person.username, person.customStatus)
                .any { it.contains(needle, ignoreCase = true) }
        }
    }
    val groups = remember(filteredAuthors) {
        val staffOrCreator = filteredAuthors.filter { it.role in listOf("admin", "moderator", "creator") }
        val paidMembers = filteredAuthors.filter {
            it.role !in listOf("admin", "moderator", "creator") && it.effectivePlan in listOf("legendary", "epic")
        }
        val alreadyGrouped = (staffOrCreator + paidMembers).map { it.id }.toSet()
        val otherMembers = filteredAuthors.filterNot { it.id in alreadyGrouped }
        listOf(
            "admin" to staffOrCreator.filter { it.role == "admin" },
            "moderator" to staffOrCreator.filter { it.role == "moderator" },
            "creator" to staffOrCreator.filter { it.role == "creator" },
            "legendary" to paidMembers.filter { it.effectivePlan == "legendary" },
            "epic" to paidMembers.filter { it.effectivePlan == "epic" },
            "online" to otherMembers.filter { it.showOnlineStatus },
            "offline" to otherMembers.filter { !it.showOnlineStatus },
        )
    }
    val memberCount = authors.size
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Spec.barSurface,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(stringResource(R.string.chat_members), style = AppText.sectionTitle, color = Spec.text)
                Text(
                    memberCount.toString(),
                    style = Spec.badge.copy(fontSize = 13.sp),
                    color = Spec.chipLabel,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(Spec.surfaceHigh)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Spec.surface)
                    .border(1.dp, Spec.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = Spec.textMuted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.size(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.chat_search_members), style = Spec.secondary, color = Spec.textMuted)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = Spec.secondary.copy(fontSize = 14.sp, color = Spec.text),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Spec.blue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Spec.textMuted,
                        modifier = Modifier.size(18.dp).clickable { query = "" },
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 520.dp)) {
                groups.forEach { (key, people) ->
                    if (people.isEmpty()) return@forEach
                    item(key = "member-group-$key") {
                        MemberGroupHeader(key = key, count = people.size)
                    }
                    items(people, key = { it.id }) { person ->
                        MemberSheetRow(
                            profile = person,
                            avatarUrl = assetUrl(container, person.avatarUrl),
                            onClick = { onOpenProfile(person) },
                        )
                    }
                }
                if (filteredAuthors.isEmpty()) {
                    item(key = "member-search-empty") {
                        Text(
                            stringResource(R.string.chat_members_no_results),
                            style = Spec.secondary,
                            color = Spec.textMuted,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun MemberGroupHeader(key: String, count: Int) {
    val color = when (key) {
        "admin", "legendary" -> Spec.gold
        "moderator" -> Spec.blueLink
        "creator", "epic" -> Spec.epic
        "online" -> Spec.onlineFill
        else -> Spec.offlineGrey
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text(groupLabel(key), style = Spec.secondary.copy(fontWeight = FontWeight.ExtraBold), color = Spec.chipLabel)
        Text(
            count.toString(),
            style = Spec.badge.copy(fontSize = 10.sp),
            color = Spec.textMuted,
            modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(Spec.surfaceHigh).padding(horizontal = 6.dp, vertical = 3.dp),
        )
    }
}

@Composable
private fun MemberSheetRow(profile: Profile, avatarUrl: String?, onClick: () -> Unit) {
    val online = profile.showOnlineStatus
    val name = profile.displayName?.takeIf { it.isNotBlank() } ?: profile.username.orEmpty()
    val subtitle = profile.customStatus?.takeIf { it.isNotBlank() } ?: if (!online && profile.lastSeenAt != null) {
        stringResource(
            R.string.chat_last_seen,
            TimeFormat.dateTime(profile.lastSeenAt, Locale.getDefault()),
        )
    } else {
        ""
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .alpha(if (online) 1f else 0.62f)
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(44.dp)) {
            AvatarImage(url = avatarUrl, size = 44.dp, borderColor = Spec.border, borderWidth = 1.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(if (online) Spec.onlineFill else Spec.offlineGrey)
                    .border(3.dp, Spec.barSurface, CircleShape),
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name,
                    style = Spec.secondary.copy(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = Spec.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                RankBadge(role = profile.role, plan = profile.effectivePlan)
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = Spec.secondary.copy(fontSize = 12.sp),
                    color = Spec.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
    Box(
        Modifier.fillMaxWidth().padding(start = 60.dp).height(1.dp).background(Spec.border.copy(alpha = 0.65f)),
    )
}

@Composable
private fun groupLabel(key: String): String = when (key) {
    "admin" -> stringResource(R.string.grp_admin)
    "moderator" -> stringResource(R.string.grp_moderators)
    "creator" -> stringResource(R.string.grp_creators)
    "legendary" -> stringResource(R.string.grp_legendary_plain)
    "epic" -> stringResource(R.string.grp_epic_plain)
    "online" -> stringResource(R.string.grp_online)
    else -> stringResource(R.string.grp_offline)
}

/** Floating message actions with the same permissions and callbacks as the previous sheet. */
@Composable
fun MessageActionsSheet(
    ui: ChatMessageUi,
    anchorBounds: Rect?,
    myId: String?,
    isStaff: Boolean,
    canDeleteMine: Boolean = false,
    deleteLeftText: String? = null,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onReport: (String) -> Unit,
    onDeleteMine: () -> Unit,
    onDeleteStaff: () -> Unit = {},
    onModerate: () -> Unit,
    onPin: () -> Unit,
    onMute: (Int) -> Unit,
    isBlocked: Boolean = false,
    onBlockUser: () -> Unit = {},
    onUnblockUser: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onReact: (String) -> Unit,
    onMoreReactions: () -> Unit = {},
) {
    var showReport by remember { mutableStateOf(false) }
    var showMute by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    val isMine = myId != null && ui.message.userId == myId

    BoxWithConstraints(Modifier.fillMaxSize().zIndex(30f)) {
        val viewportHeight = maxHeight
        val density = LocalDensity.current
        val anchorTop = anchorBounds?.top?.let { with(density) { it.toDp() } } ?: (maxHeight / 2)
        val anchorBottom = anchorBounds?.bottom?.let { with(density) { it.toDp() } } ?: (maxHeight / 2)
        val estimatedHeight = when {
            isStaff -> 500.dp
            isMine -> 330.dp
            else -> 390.dp
        }
        val placeAbove = anchorBottom + estimatedHeight > maxHeight && anchorTop > maxHeight - anchorBottom
        val popupTop = if (placeAbove) {
            (anchorTop - estimatedHeight - 58.dp).coerceAtLeast(16.dp)
        } else {
            (anchorBottom + 8.dp).coerceAtMost((maxHeight - estimatedHeight).coerceAtLeast(16.dp))
        }
        val reactionWidth = minOf(310.dp, (maxWidth - 24.dp).coerceAtLeast(260.dp))
        val cardWidth = minOf(260.dp, (maxWidth - 32.dp).coerceAtLeast(220.dp))

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .clickable { onDismiss() },
        ) {
            drawRect(Color(0xB3050810))
            anchorBounds?.let { bounds ->
                val inset = with(density) { 5.dp.toPx() }
                val left = (bounds.left - inset).coerceAtLeast(0f)
                val top = (bounds.top - inset).coerceAtLeast(0f)
                val right = (bounds.right + inset).coerceAtMost(size.width)
                val bottom = (bounds.bottom + inset).coerceAtMost(size.height)
                drawRect(
                    color = Color.Transparent,
                    topLeft = Offset(left, top),
                    size = Size((right - left).coerceAtLeast(0f), (bottom - top).coerceAtLeast(0f)),
                    blendMode = BlendMode.Clear,
                )
            }
        }

        Column(
            modifier = Modifier
                .align(if (isMine) Alignment.TopEnd else Alignment.TopStart)
                .padding(horizontal = 16.dp)
                .offset(y = popupTop)
                .widthIn(max = reactionWidth),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) {
            ReactionQuickBar(
                reactions = ui.myReactions,
                onReact = onReact,
                onMore = onMoreReactions,
            )
            Spacer(Modifier.height(8.dp))
            Column(
                modifier = Modifier
                    .widthIn(max = cardWidth)
                    .heightIn(max = (viewportHeight - 150.dp).coerceAtLeast(200.dp))
                    .shadow(
                        elevation = 14.dp,
                        shape = RoundedCornerShape(20.dp),
                        ambientColor = Color.Black.copy(alpha = 0.55f),
                        spotColor = Color.Black.copy(alpha = 0.55f),
                    )
                    .clip(RoundedCornerShape(20.dp))
                    .background(Spec.barSurface)
                    .border(1.dp, Spec.border, RoundedCornerShape(20.dp))
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 6.dp, vertical = 4.dp),
            ) {
                ActionRow(Icons.Filled.Reply, stringResource(R.string.chat_reply), onReply)
                ActionRow(Icons.Filled.ContentCopy, stringResource(R.string.chat_copy_text), onCopy)
                if (isStaff) ActionRow(Icons.Filled.PushPin, stringResource(R.string.chat_pin), onPin)
                if (!isMine) ActionRow(Icons.Filled.Person, stringResource(R.string.chat_view_profile), onOpenProfile)
                DividerLine()
                ActionRow(Icons.Filled.Flag, stringResource(R.string.chat_report), onClick = { showReport = true })
                if (!isMine) {
                    if (isBlocked) {
                        ActionRow(Icons.Filled.Block, stringResource(R.string.block_user_undo), onUnblockUser)
                    } else {
                        ActionRow(Icons.Filled.Block, stringResource(R.string.block_user), onClick = { showBlock = true }, tint = Spec.danger)
                    }
                }
                if (isMine && ui.message.status != "deleted") {
                    if (canDeleteMine) {
                        ActionRow(Icons.Filled.Delete, stringResource(R.string.chat_delete_mine), onDeleteMine, Spec.danger)
                        if (deleteLeftText != null) {
                            Text(
                                deleteLeftText,
                                style = Spec.secondary.copy(fontSize = 11.sp),
                                color = Spec.textMuted,
                                modifier = Modifier.padding(start = 40.dp, bottom = 6.dp),
                            )
                        }
                    } else {
                        Text(
                            stringResource(R.string.chat_delete_expired),
                            style = Spec.secondary.copy(fontSize = 11.sp),
                            color = Spec.textMuted,
                            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp),
                        )
                    }
                }
                if (isStaff) {
                    if (ui.message.status != "deleted") {
                        ActionRow(
                            Icons.Filled.Delete,
                            stringResource(R.string.chat_delete_staff_plain),
                            onDeleteStaff,
                            Spec.danger,
                        )
                        Text(
                            stringResource(R.string.chat_delete_staff_note),
                            style = Spec.secondary.copy(fontSize = 11.sp),
                            color = Spec.textMuted,
                            modifier = Modifier.padding(start = 40.dp, bottom = 6.dp),
                        )
                        val isHidden = ui.message.status == "hidden"
                        ActionRow(
                            if (isHidden) Icons.Filled.Visibility else Icons.Filled.VisibilityOff,
                            stringResource(if (isHidden) R.string.chat_show else R.string.chat_hide_message),
                            onModerate,
                        )
                    }
                    ActionRow(Icons.Filled.VolumeOff, stringResource(R.string.chat_mute_user), onClick = { showMute = true })
                }
            }
        }
    }

    if (showReport) {
        ReportDialog(
            onDismiss = { showReport = false },
            onSubmit = { reason ->
                showReport = false
                onReport(reason)
            },
        )
    }
    if (showBlock) {
        BlockConfirmDialog(
            name = ui.author?.username?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.chat_view_profile),
            onDismiss = { showBlock = false },
            onConfirm = {
                showBlock = false
                onBlockUser()
            },
        )
    }
    if (showMute) {
        MuteDialog(
            onDismiss = { showMute = false },
            onConfirm = { minutes ->
                showMute = false
                onMute(minutes)
            },
        )
    }
}

@Composable
private fun ReactionQuickBar(
    reactions: Set<String>,
    onReact: (String) -> Unit,
    onMore: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .shadow(10.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.50f), spotColor = Color.Black.copy(alpha = 0.50f))
            .background(Spec.barSurface)
            .border(1.dp, Spec.border, CircleShape)
            .padding(5.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RECENT_FALLBACK.take(5).forEach { emoji ->
            val selected = emoji in reactions
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (selected) Spec.blue.copy(alpha = 0.20f) else Spec.surfaceHigh)
                    .border(if (selected) 1.dp else 0.dp, if (selected) Spec.blueLink.copy(alpha = 0.5f) else Color.Transparent, CircleShape)
                    .clickable { onReact(emoji) },
                contentAlignment = Alignment.Center,
            ) {
                Text(emoji, style = AppText.welcomeName.copy(fontSize = 24.sp))
            }
        }
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(Spec.surfaceHigh)
                .clickable { onMore() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_add_reaction), tint = Spec.textMuted, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun DividerLine() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(1.dp)
            .background(Spec.border),
    )
}

@Composable
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit, tint: Color = Spec.text) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        Text(title, style = Spec.secondary.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold), color = tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ReportDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.chat_report), style = AppText.sectionTitle, color = AppColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ReportOption("spam", stringResource(R.string.chat_reason_spam), onSubmit)
                ReportOption("insult", stringResource(R.string.chat_reason_insult), onSubmit)
                ReportOption("inappropriate", stringResource(R.string.chat_reason_inappropriate), onSubmit)
                ReportOption("other", stringResource(R.string.chat_reason_other), onSubmit)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = AppColors.Muted)
            }
        },
    )
}

/**
 * تأكيد الحظر — خطوة واحدة لازمة لأن أثره متقابل وفوري (يختفي كل كلامه)،
 * ونوضّح فيه صراحةً أن الإلغاء متاح دائماً من ملفه الشخصي (لا رجعة عنه).
 */
@Composable
private fun BlockConfirmDialog(
    name: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Text(
                stringResource(R.string.block_confirm_title),
                style = AppText.sectionTitle,
                color = AppColors.Text,
            )
        },
        text = {
            Text(
                stringResource(R.string.block_confirm_body, name),
                style = AppText.bodySmall,
                color = AppColors.Muted,
            )
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = AppColors.Muted)
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onConfirm) {
                Text(stringResource(R.string.block_user), color = AppColors.Red)
            }
        },
    )
}

@Composable
private fun ReportOption(reason: String, label: String, onSubmit: (String) -> Unit) {
    Text(
        label,
        style = AppText.bodySmall,
        color = AppColors.Text,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Elevated)
            .clickable { onSubmit(reason) }
            .padding(10.dp),
    )
}

@Composable
private fun MuteDialog(onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {
    val options = listOf(5 to "5m", 60 to "1h", 1440 to "1d", 10080 to "1w")
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = { Text(stringResource(R.string.chat_mute_user), style = AppText.sectionTitle, color = AppColors.Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                options.forEach { (minutes, label) ->
                    Text(
                        label,
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Elevated)
                            .clickable { onConfirm(minutes) }
                            .padding(10.dp),
                    )
                }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = AppColors.Muted)
            }
        },
    )
}

/** قائمة الرسائل المثبّتة (بحد أقصى 50 لكل غرفة). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinnedSheet(pinned: List<ChatMessage>, onDismiss: () -> Unit) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.PushPin, contentDescription = null, tint = AppColors.Blue, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.chat_pinned_plain), style = AppText.sectionTitle, color = AppColors.Text)
            }
            if (pinned.isEmpty()) {
                Text(
                    stringResource(R.string.chat_no_pinned),
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                    modifier = Modifier.padding(vertical = 16.dp),
                )
            } else {
                androidx.compose.foundation.lazy.LazyColumn(Modifier.height(360.dp)) {
                    items(pinned, key = { it.id }) { message ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(AppColors.Elevated)
                                .padding(10.dp),
                        ) {
                            Text(
                                message.content.orEmpty().take(120),
                                style = AppText.bodySmall,
                                color = AppColors.Text,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                TimeFormat.timeOnly(message.createdAt, Locale.getDefault()),
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

/** بطاقة ملف بأسلوب Discord — تُستخدم عند النقر على مستخدم في الشات. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscordProfileCardSheet(
    profile: Profile,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onOpenProfile: () -> Unit,
    /** Private messaging is hidden until a private-chat route/backend exists. */
    showMessage: Boolean = false,
    refreshToken: Int = 0,
    onAddFriend: suspend () -> Result<Unit> = { Result.success(Unit) },
    /** ملفي أنا: زرّ «تعديل الملف الشخصي» بدل «مراسلة» و«إضافة صديق». */
    onEditProfile: () -> Unit = {},
) {
    val container = rememberContainer()
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val signedIn by container.state.signedIn.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var friendEntry by remember { mutableStateOf<com.addonstation.mobile.data.model.FriendEntry?>(null) }
    var friendBusy by remember { mutableStateOf(false) }
    var friendError by remember { mutableStateOf<String?>(null) }
    val friendFailedMsg = stringResource(R.string.profile_action_failed)
    val friendState = friendEntry?.status ?: "none"

    androidx.compose.runtime.LaunchedEffect(profile.id, refreshToken) {
        friendEntry = container.profile.friendshipState(profile.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = AppColors.Muted) },
    ) {
        Column {
            // مربّع البروفايل راكب على حافة الغلاف: نصفه داخله ونصفه خارجه
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(100.dp + 40.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(AppColors.Elevated2),
                ) {
                    assetUrl(container, profile.bannerUrl)?.let { banner ->
                        com.addonstation.mobile.ui.components.CoverImage(
                            url = banner,
                            radius = 0.dp,
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                        )
                    }
                }
                AvatarImage(
                    url = assetUrl(container, profile.avatarUrl),
                    size = 80.dp,
                    online = profile.showOnlineStatus,
                    borderColor = AppColors.Surface,
                    borderWidth = 5.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp),
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    profile.displayName ?: profile.username ?: "",
                    style = AppText.welcomeName,
                    color = AppColors.Text,
                )
                Text("@${profile.username ?: ""}", style = AppText.tiny, color = AppColors.Muted)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.padding(top = 3.dp),
                ) {
                    Box(Modifier.size(7.dp).clip(CircleShape).background(if (profile.showOnlineStatus) AppColors.Green else AppColors.Muted))
                    Text(
                        stringResource(if (profile.showOnlineStatus) R.string.profile_online else R.string.profile_offline_plain),
                        style = AppText.tiny,
                        color = if (profile.showOnlineStatus) AppColors.Green else AppColors.Muted,
                    )
                }
                profile.customStatus?.takeIf(String::isNotBlank)?.let { status ->
                    Text(
                        status,
                        style = AppText.bodySmall,
                        color = AppColors.AuthChipLabel,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(12.dp))
                            .background(AppColors.AuthBarSurface).padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    // المجّاني بلا رتبة ⇒ بلا لقب (لا «Free 🍃» في الشات)
                    if (hasRankTitle(profile.role, profile.effectivePlan)) {
                        RankBadge(role = profile.role, plan = profile.effectivePlan)
                    }
                    Text(
                        stringResource(R.string.profile_stats_level) + " ${profile.userLevel}",
                        style = AppText.tiny,
                        color = AppColors.Muted,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.Elevated)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                    if (profile.equippedItemId != "none") {
                        // الأداة المجهّزة تظهر بصورتها الحقيقية (وتتحرّك إن كانت GIF)
                        val itemImg = com.addonstation.mobile.ui.equippedItemUrl(container, profile.equippedItemId)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppColors.Elevated)
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        ) {
                            if (itemImg != null) {
                                com.addonstation.mobile.ui.components.CoverImage(
                                    url = itemImg,
                                    radius = 4.dp,
                                    modifier = Modifier.size(16.dp),
                                )
                            } else {
                                Text("🎖", style = AppText.tiny)
                            }
                            Text(
                                profile.equippedItemId,
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (isSelf(container, profile)) {
                    // ملفي أنا: لا «مراسلة» ولا «إضافة صديق» — زرّ تعديل الملف
                    com.addonstation.mobile.ui.components.GradientButton(
                        text = stringResource(R.string.profile_edit_mine_plain),
                        onClick = onEditProfile,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (showMessage) {
                            com.addonstation.mobile.ui.components.GradientButton(
                                text = stringResource(R.string.profile_message_plain),
                                onClick = onMessage,
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (signedIn) {
                            GhostButton(
                                text = when (friendState) {
                                    "friends" -> stringResource(R.string.profile_friends_plain)
                                    "sent" -> stringResource(R.string.profile_friend_sent)
                                    else -> stringResource(R.string.profile_add_friend_plain)
                                },
                                onClick = {
                                    if (friendState == "none" && !friendBusy) {
                                        scope.launch {
                                            friendBusy = true
                                            friendError = null
                                            try {
                                                val result = onAddFriend()
                                                if (result.isSuccess) {
                                                    friendEntry = container.profile.friendshipState(profile.id)
                                                } else {
                                                    friendError = result.exceptionOrNull()?.message ?: friendFailedMsg
                                                }
                                            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                                                throw cancelled
                                            } catch (error: Throwable) {
                                                friendError = error.message ?: friendFailedMsg
                                            } finally {
                                                friendBusy = false
                                            }
                                        }
                                    }
                                },
                                enabled = friendState == "none" && !friendBusy,
                                modifier = if (showMessage) Modifier.weight(1f) else Modifier.fillMaxWidth(),
                            )
                        }
                    }
                    friendError?.let { Text(it, style = AppText.tiny, color = AppColors.Red, modifier = Modifier.padding(top = 6.dp)) }
                }
                Spacer(Modifier.height(12.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(stringResource(R.string.profile_about_me), style = AppText.tiny, color = AppColors.Muted)
                    Text(
                        profile.bio?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_about_default),
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                    )
                    Text(
                        stringResource(
                            R.string.profile_member_since,
                            TimeFormat.dateOnly(profile.createdAt, Locale.getDefault()),
                        ),
                        style = AppText.tiny,
                        color = AppColors.Muted,
                    )
                    Text(
                        stringResource(R.string.profile_level_active, profile.userLevel),
                        style = AppText.tiny,
                        color = nameColor(profile.role, profile.effectivePlan),
                    )
                }
                Box(
                    Modifier.fillMaxWidth().height(44.dp).clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onOpenProfile),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(R.string.profile_open_profile_plain),
                        style = AppText.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                        color = AppColors.Blue,
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun isSelf(container: com.addonstation.mobile.data.AppContainer, profile: Profile): Boolean =
    container.supabase.session()?.userId == profile.id

/**
 * هل يستحق هذا العضو **لقباً** (TheKing ✦ · Epic 💎 · Legendary 👑 …)؟
 *
 * طلب المالك: «أي شخص تحت الخطة المجانية ما يظهر له شيء تحت الاسم — حتى
 * Free 🍃 تكون فاضية» ⇒ المجّاني الذي بلا رتبة **لا سطر تحت اسمه**.
 * اللقب يظهر للمشترك (epic · legendary) ولصاحب رتبة (أدمن · مشرف · مبدع)،
 * ويظهر من تلقاء نفسه متى اشترك المجّاني — لأن الخطة تُقرأ لحظياً
 * عبر `Profile.effectivePlan` (المنتهية تُحسب free فيخفي اللقب).
 */
fun hasRankTitle(role: String?, plan: String?): Boolean =
    role == "admin" || role == "moderator" || role == "creator" ||
        plan == "epic" || plan == "legendary"

/** توسيع صغير لعرض بطاقة ملف عضو من الرسائل. */
@Composable
fun MemberCardHost(
    profile: Profile?,
    onDismiss: () -> Unit,
    onMessage: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onEditProfile: () -> Unit = {},
    refreshToken: Int = 0,
    onAddFriend: suspend () -> Result<Unit> = { Result.success(Unit) },
    showMessage: Boolean = false,
) {
    profile ?: return
    DiscordProfileCardSheet(
        profile = profile,
        onDismiss = onDismiss,
        onMessage = onMessage,
        onOpenProfile = onOpenProfile,
        showMessage = showMessage,
        refreshToken = refreshToken,
        onAddFriend = onAddFriend,
        onEditProfile = onEditProfile,
    )
}
