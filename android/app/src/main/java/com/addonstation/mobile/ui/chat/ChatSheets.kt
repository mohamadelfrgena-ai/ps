package com.addonstation.mobile.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import com.addonstation.mobile.ui.theme.nameColor
import androidx.compose.runtime.collectAsState
import com.addonstation.mobile.util.TimeFormat
import java.util.Locale

private val RECENT_FALLBACK = listOf("👍", "❤️", "😂", "🔥", "🎉", "😮")

/** نافذة الإيموجي + الملصقات (تبويبان: Emoji / Stickers). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmojiStickerSheet(
    stickers: List<SiteAsset>,
    packs: List<StickerPack>,
    resolvedStickerUrl: (String) -> String?,
    onDismiss: () -> Unit,
    onEmoji: (String) -> Unit,
    onSticker: (String) -> Unit,
) {
    val container = rememberContainer()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var tab by remember { mutableStateOf(0) }
    var selectedPack by remember { mutableStateOf<String?>(null) }
    var search by remember { mutableStateOf("") }
    val recentRaw by container.prefs.recentEmojis.collectAsState(initial = "")
    val recent = recentRaw.split(' ').filter { it.isNotBlank() }

    val filtered = if (search.isBlank()) ChatViewModel_Emojis
    else ChatViewModel_Emojis.filter { it.contains(search) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(stringResource(R.string.chat_emoji), tab == 0, { tab = 0 })
                FilterChip(stringResource(R.string.chat_stickers_tab), tab == 1, { tab = 1 })
            }
            Spacer(Modifier.height(10.dp))
            if (tab == 0) {
                androidx.compose.material3.OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    placeholder = { Text(stringResource(R.string.chat_search_emoji), style = AppText.bodySmall) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recent.isNotEmpty()) {
                    Text(stringResource(R.string.chat_recent), style = AppText.tiny, color = AppColors.Muted)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(recent) { emoji ->
                            Text(
                                emoji,
                                style = AppText.body,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onEmoji(emoji) }
                                    .padding(6.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                val emojiColumns = com.addonstation.mobile.ui.theme.adaptiveColumns(
                    minCellDp = 40, maxCellDp = 58, maxColumns = 10,
                    horizontalPaddingDp = 28, spacingDp = 4,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(emojiColumns),
                    modifier = Modifier.height(
                        com.addonstation.mobile.ui.theme.screenHeightFraction(0.36f, minDp = 210, maxDp = 330),
                    ),
                    contentPadding = PaddingValues(vertical = 6.dp),
                ) {
                    items(filtered) { emoji ->
                        Text(
                            emoji,
                            style = AppText.welcomeName,
                            modifier = Modifier
                                .padding(4.dp)
                                .clip(CircleShape)
                                .clickable { onEmoji(emoji) }
                                .padding(4.dp),
                        )
                    }
                }
            } else {
                // الملصقات في **فقرات منفصلة**: عنوان لكل قسم/مجلد (Memes · Minecraft Memes 1 …)
                // ثم شبكة ملصقاته — وعناوين الأقسام تمتد بعرض الشبكة كاملاً.
                val visible = stickers.filter { selectedPack == null || it.packId == selectedPack }
                val groups = com.addonstation.mobile.ui.groupAssetsByCollection(visible)
                // الملصقات: عدد الأعمدة والارتفاع يتبعان شاشة الهاتف (أوسع ⇒ أعمدة أكثر)
                val stickerColumns = com.addonstation.mobile.ui.theme.adaptiveColumns(
                    minCellDp = 78, maxCellDp = 104, maxColumns = 6,
                    horizontalPaddingDp = 28, spacingDp = 8,
                )
                LazyVerticalGrid(
                    columns = GridCells.Fixed(stickerColumns),
                    modifier = Modifier.height(
                        com.addonstation.mobile.ui.theme.screenHeightFraction(0.34f, minDp = 200, maxDp = 320),
                    ),
                ) {
                    groups.forEach { section ->
                        item(span = { GridItemSpan(maxLineSpan) }, key = "sec-${section.title}") {
                            com.addonstation.mobile.ui.components.AssetSectionHeader(
                                title = section.title,
                                count = section.assets.size,
                            )
                        }
                        items(section.assets, key = { it.id }) { sticker ->
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .height(84.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.Elevated)
                                    .clickable { onSticker(sticker.fileUrl) },
                                contentAlignment = Alignment.Center,
                            ) {
                                com.addonstation.mobile.ui.components.CoverImage(
                                    url = resolvedStickerUrl(sticker.fileUrl),
                                    radius = 10.dp,
                                    modifier = Modifier.fillMaxWidth().height(80.dp),
                                )
                            }
                        }
                    }
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        FilterChip("All", selectedPack == null, { selectedPack = null })
                    }
                    items(packs, key = { it.id }) { pack ->
                        FilterChip(pack.name, selectedPack == pack.id, { selectedPack = pack.id })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private val ChatViewModel_Emojis: List<String> = listOf(
    "😀", "😃", "😄", "😍", "😂", "🥰", "😎", "🤣", "😅", "😇", "😉", "🤔",
    "😴", "🤯", "🥳", "😢", "😭", "😡", "🤩", "👍", "👎", "👏", "🙏", "💪",
    "🔥", "❤️", "💔", "💎", "👑", "🍃", "⭐", "🎮", "🕹", "🧊", "🏆", "⚽",
    "🚀", "✨", "🎉", "💬", "✅", "❌", "😊", "😁", "🤝", "🧡", "💛", "💜",
)

/**
 * نافذة الإرفاق: صورة من المعرض (للجميع) أو ملف صوت/فيديو (**للأدمن فقط**).
 * غير الأدمن لا يرى خيار الوسائط إطلاقاً — والقاعدة ترفضه من جهة الخادم أيضاً.
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
        SheetRow("🖼", stringResource(R.string.chat_attach_photo), onPhoto)
        if (isAdmin) {
            SheetRow("🎬", stringResource(R.string.chat_attach_media), onAudioVideo)
        } else {
            Text(
                stringResource(R.string.chat_media_admin_only),
                style = AppText.tiny,
                color = AppColors.Muted,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachMediaSheet(onDismiss: () -> Unit, onAudio: () -> Unit, onVideo: () -> Unit) {
    SheetScaffold(onDismiss) {
        SheetRow("🎙️", stringResource(R.string.chat_audio_label), onAudio)
        SheetRow("🎬", stringResource(R.string.chat_video_label), onVideo)
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
        Column(Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) { content() }
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

/** قائمة الأعضاء مجمّعة حسب الدور/الخطة (كما في Discord). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MembersSheet(
    authors: List<Profile>,
    onDismiss: () -> Unit,
    onOpenProfile: (Profile) -> Unit,
) {
    val container = rememberContainer()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val groups = remember(authors) {
        listOf(
            "admin" to authors.filter { it.role == "admin" },
            "moderator" to authors.filter { it.role == "moderator" },
            "creator" to authors.filter { it.role == "creator" },
            "legendary" to authors.filter { it.role !in listOf("admin", "moderator", "creator") && it.effectivePlan == "legendary" },
            "epic" to authors.filter { it.role !in listOf("admin", "moderator", "creator") && it.effectivePlan == "epic" },
            "online" to authors.filter { it.showOnlineStatus },
            "offline" to authors.filter { !it.showOnlineStatus },
        )
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Text(stringResource(R.string.chat_members), style = AppText.sectionTitle, color = AppColors.Text)
            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.height(420.dp)) {
                groups.forEach { (key, people) ->
                    if (people.isEmpty()) return@forEach
                    item {
                        Text(
                            groupLabel(key) + " — ${people.size}",
                            style = AppText.tiny,
                            color = AppColors.Muted,
                            modifier = Modifier.padding(top = 10.dp, bottom = 4.dp),
                        )
                    }
                    items(people, key = { it.id }) { person ->
                        com.addonstation.mobile.ui.components.ProfileRow(
                            profile = person,
                            fallbackName = person.username ?: "",
                            avatarUrl = assetUrl(container, person.avatarUrl),
                            onClick = { onOpenProfile(person) },
                            trailing = {
                                Text(
                                    if (person.showOnlineStatus) "🟢" else "⚪",
                                    style = AppText.tiny,
                                )
                            },
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun groupLabel(key: String): String = when (key) {
    "admin" -> stringResource(R.string.grp_admin)
    "moderator" -> stringResource(R.string.grp_moderators)
    "creator" -> stringResource(R.string.grp_creators)
    "legendary" -> stringResource(R.string.grp_legendary)
    "epic" -> stringResource(R.string.grp_epic)
    "online" -> stringResource(R.string.grp_online)
    else -> stringResource(R.string.grp_offline)
}

/** إجراءات الرسالة (ضغط مطوّل): تفاعل، رد، نسخ، بلاغ، حذف/إخفاء، تثبيت، كتم، فتح الملف الشخصي. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MessageActionsSheet(
    ui: ChatMessageUi,
    myId: String?,
    isStaff: Boolean,
    /** هل ما زال صاحب الرسالة داخل مدة الحذف (٣ دقائق)؟ يُحسب في الشات من وقت الإرسال. */
    canDeleteMine: Boolean = false,
    /** ما تبقّى من مهلته بصيغة جاهزة (مثل «متبقٍ دقيقتان») — يُعرض تحت خيار الحذف. */
    deleteLeftText: String? = null,
    onDismiss: () -> Unit,
    onReply: () -> Unit,
    onCopy: () -> Unit,
    onReport: (String) -> Unit,
    onDeleteMine: () -> Unit,
    onModerate: () -> Unit,
    onPin: () -> Unit,
    onMute: (Int) -> Unit,
    onOpenProfile: () -> Unit,
    onReact: (String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showReport by remember { mutableStateOf(false) }
    var showMute by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
    ) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                RECENT_FALLBACK.forEach { emoji ->
                    Text(
                        emoji,
                        style = AppText.welcomeName,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.Elevated)
                            .clickable { onReact(emoji) }
                            .padding(8.dp),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            ActionRow(Icons.Filled.Reply, stringResource(R.string.chat_reply), onReply)
            ActionRow(Icons.Filled.ContentCopy, stringResource(R.string.chat_copy_text), onCopy)
            ActionRow(Icons.Filled.Flag, stringResource(R.string.chat_report), onClick = { showReport = true })
            // خيار الحذف: للرسالة التي أرسلها المستخدم نفسه **وفي أول ٣ دقائق فقط**.
            // بعد انتهاء المدة يختفي الخيار تماماً (والقاعدة ترفضه من جهة الخادم أيضاً).
            if (ui.message.userId == myId && ui.message.status != "deleted") {
                if (canDeleteMine) {
                    ActionRow(Icons.Filled.Delete, stringResource(R.string.chat_delete_mine), onDeleteMine, AppColors.Red)
                    if (deleteLeftText != null) {
                        Text(
                            deleteLeftText,
                            style = AppText.tiny,
                            color = AppColors.Dim,
                            modifier = Modifier.padding(start = 38.dp, bottom = 6.dp),
                        )
                    }
                } else {
                    // انتهت المدة: نوضّح السبب بلطف بدل إخفاء الأمر بلا تفسير
                    Text(
                        stringResource(R.string.chat_delete_expired),
                        style = AppText.tiny,
                        color = AppColors.Dim,
                        modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
                    )
                }
            }
            if (isStaff) {
                ActionRow(Icons.Filled.Visibility, stringResource(R.string.chat_hide_message), onModerate)
                ActionRow(Icons.Filled.PushPin, stringResource(R.string.chat_pin), onPin)
                ActionRow(Icons.Filled.VolumeOff, stringResource(R.string.chat_mute_user), onClick = { showMute = true })
            }
            ActionRow(Icons.Filled.Person, stringResource(R.string.chat_view_profile), onOpenProfile)
        }
        Spacer(Modifier.height(16.dp))
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
private fun ActionRow(icon: ImageVector, title: String, onClick: () -> Unit, tint: androidx.compose.ui.graphics.Color = AppColors.Text) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(title, style = AppText.body, color = tint)
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
            Text("📌 " + stringResource(R.string.chat_pinned), style = AppText.sectionTitle, color = AppColors.Text)
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
    onAddFriend: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    val container = rememberContainer()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var friendEntry by remember { mutableStateOf<com.addonstation.mobile.data.model.FriendEntry?>(null) }
    val friendState = friendEntry?.status ?: "none"

    androidx.compose.runtime.LaunchedEffect(profile.id) {
        friendEntry = container.profile.friendshipState(profile.id)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AppColors.Surface,
    ) {
        Column {
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
                AvatarImage(
                    url = assetUrl(container, profile.avatarUrl),
                    size = 80.dp,
                    online = profile.showOnlineStatus,
                    borderColor = AppColors.Surface,
                    borderWidth = 5.dp,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp, bottom = 0.dp),
                )
            }
            Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                Text(
                    profile.displayName ?: profile.username ?: "",
                    style = AppText.welcomeName,
                    color = AppColors.Text,
                )
                Text("@${profile.username ?: ""}", style = AppText.tiny, color = AppColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 6.dp)) {
                    RankBadge(role = profile.role, plan = profile.effectivePlan)
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.addonstation.mobile.ui.components.GradientButton(
                        text = stringResource(R.string.profile_message),
                        onClick = onMessage,
                        modifier = Modifier.weight(1f),
                    )
                    if (isSelf(container, profile)) {
                        GhostButton(
                            text = stringResource(R.string.profile_open_profile),
                            onClick = onOpenProfile,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        GhostButton(
                            text = when (friendState) {
                                "friends" -> stringResource(R.string.profile_friends)
                                "sent" -> stringResource(R.string.profile_friend_sent)
                                else -> stringResource(R.string.profile_add_friend)
                            },
                            onClick = { if (friendState == "none") onAddFriend() },
                            enabled = friendState == "none",
                            modifier = Modifier.weight(1f),
                        )
                    }
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
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

private fun isSelf(container: com.addonstation.mobile.data.AppContainer, profile: Profile): Boolean =
    container.supabase.session()?.userId == profile.id

/** توسيع صغير لعرض بطاقة ملف عضو من الرسائل. */
@Composable
fun MemberCardHost(
    profile: Profile?,
    onDismiss: () -> Unit,
    onMessage: () -> Unit,
    onAddFriend: () -> Unit,
    onOpenProfile: () -> Unit,
) {
    profile ?: return
    DiscordProfileCardSheet(
        profile = profile,
        onDismiss = onDismiss,
        onMessage = onMessage,
        onAddFriend = onAddFriend,
        onOpenProfile = onOpenProfile,
    )
}
