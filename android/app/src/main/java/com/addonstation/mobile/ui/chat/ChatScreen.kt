@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.addonstation.mobile.ui.chat

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatMessageUi
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.RoomIcon
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * شاشة الشات بأسلوب Discord: الترويسة، قائمة الرسائل بالتجميع وفواصل التواريخ،
 * شريط الكتابة (إرفاق/ملصقات/إيموجي/إرسال)، المنشن، الردود، التفاعلات، والجديد ↓.
 */
@Composable
fun ChatScreen(
    channel: ChatChannel,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenUser: (Profile) -> Unit,
    onLogin: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val vm = remember(channel.id) { ChatViewModel(container) }

    val messages by vm.messages.collectAsState()
    val authors by vm.authors.collectAsState()
    val loading by vm.loading.collectAsState()
    val newBelow by vm.newMessagesBelow.collectAsState()
    val typing by vm.typing.collectAsState()
    val stickers by vm.stickers.collectAsState()
    val packs by vm.packs.collectAsState()
    val profile by container.state.profile.collectAsState()
    val chatStatus by container.state.chatStatus.collectAsState()

    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var attachment by remember { mutableStateOf<Pair<String, String>?>(null) } // (path, kind)
    var sending by remember { mutableStateOf(false) } // يمنع الإرسال المزدوج بنقرة مزدوجة
    var showEmoji by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showPinned by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    var mediaSheet by remember { mutableStateOf(false) }
    var imageViewerUrl by remember { mutableStateOf<String?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var pinnedMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var mediaToSend by remember { mutableStateOf<Triple<String, String, String>?>(null) } // path,mime,type

    val listState = rememberLazyListState()

    // نصوص تُستخدم داخل لامبدات (stringResource دالة Composable فلا تُستدعى داخل اللامبدا)
    val audioSentMsg = stringResource(R.string.chat_audio_sent)
    val videoSentMsg = stringResource(R.string.chat_video_sent)
    val audioLabel = stringResource(R.string.chat_audio_label)
    val videoLabel = stringResource(R.string.chat_video_label)
    val sendLabel = stringResource(R.string.chat_send)
    val reportSentMsg = stringResource(R.string.chat_report_sent)
    val deleteExpiredMsg = stringResource(R.string.chat_delete_expired)
    val mediaAdminMsg = stringResource(R.string.chat_media_admin_only)
    // الأدمن وحده يرسل الفيديو والصوت والتسجيل الصوتي (القاعدة هي الحكم)
    val isAdmin = profile?.role == "admin"
    val pinMsg = stringResource(R.string.chat_pin)
    val unpinMsg = stringResource(R.string.chat_unpin)
    val imageFormatsMsg = stringResource(R.string.chat_image_formats)
    val mediaMaxMsg = stringResource(R.string.chat_media_max)
    val failedMsg = stringResource(R.string.item_download_failed)

    LaunchedEffect(channel.id) { vm.open(channel) }
    DisposableEffect(channel.id) { onDispose { vm.close() } }

    // تمرير تلقائي للأسفل عند وصول رسائل جديدة إذا كان المستخدم قريباً من الأسفل
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !newBelow) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    LaunchedEffect(newBelow) {
        if (!newBelow) vm.markReadUpToBottom()
    }
    LaunchedEffect(Unit) { vm.markReadUpToBottom() }

    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val prepared = com.addonstation.mobile.util.prepareImageForUpload(context, uri, maxSide = 1080)
            val bytes = prepared?.bytes ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                statusMessage = failedMsg
                return@launch
            }
            val mime = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/jpeg"
            val name = prepared?.fileName ?: "chat-${System.currentTimeMillis()}.jpg"
            container.chat.uploadImage(bytes, mime, name)
                .onSuccess { path -> attachment = path to "image" }
                .onFailure { statusMessage = imageFormatsMsg }
        }
    }

    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // الوسائط (فيديو · صوت · تسجيل صوتي) للأدمن فقط — لا تُرفع ولو وصل الطلب
            // من عميل قديم لا يعرف القاعدة؛ والخادم يرفضها مرّة أخرى عند الإرسال.
            if (!isAdmin) {
                statusMessage = mediaAdminMsg
                return@launch
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mime = context.contentResolver.getType(uri) ?: "audio/mpeg"
            val isVideo = mime.startsWith("video")
            val name = (if (isVideo) "video-" else "audio-") + System.currentTimeMillis()
            container.chat.uploadMedia(bytes, mime, name)
                .onSuccess { path -> mediaToSend = Triple(path, mime, if (isVideo) "video" else "audio") }
                .onFailure { statusMessage = mediaMaxMsg }
        }
    }

    val isMuted = chatStatus?.muted == true
    val isBanned = chatStatus?.banned == true
    val signedIn = profile != null
    val roomLocked = channel.isLocked
    val canPost = signedIn && !isMuted && !isBanned && !roomLocked

    Column(
        Modifier
            .fillMaxSize()
            // الأسفل = الأكبر بين شريط التنقّل ولوحة المفاتيح: يبقى مربع الإرسال فوق
            // شريط النظام حين تُغلق اللوحة، وفوق اللوحة حين تُفتح — بلا تداخل ولا فراغ.
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        ChatHeader(
            channel = channel,
            onBack = onBack,
            onMembers = { showMembers = true },
            onPinned = {
                scope.launch {
                    pinnedMessages = container.chat.pinned(channel.id)
                    showPinned = true
                }
            },
            onSettings = onOpenSettings,
        )

        Box(Modifier.weight(1f)) {
            when {
                loading -> LoadingBox(text = stringResource(R.string.chat_loading_messages))
                messages.isEmpty() -> EmptyState(stringResource(R.string.chat_no_messages))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    itemsIndexedCompat(messages) { index, ui ->
                        val previous = messages.getOrNull(index - 1)
                        MessageBlock(
                            ui = ui,
                            previous = previous,
                            showDate = previous == null ||
                                TimeFormat.dayLabel(previous.message.createdAt, Locale.getDefault()) !=
                                TimeFormat.dayLabel(ui.message.createdAt, Locale.getDefault()),
                            isNewDivider = false,
                            myId = container.supabase.session()?.userId,
                            onLongPress = { actionsFor = ui },
                            onToggleReaction = { emoji -> vm.toggleReaction(ui.message.id, emoji) },
                            onOpenImage = { path ->
                                scope.launch { container.chat.imageUrl(path)?.let { imageViewerUrl = it } }
                            },
                            onOpenUser = { userId ->
                                authors[userId]?.let { onOpenUser(it) }
                            },
                            stickerUrlResolver = { path -> container.siteAssetUrl(path) },
                        )
                    }
                }
            }

            if (newBelow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 12.dp)
                        .clip(CircleShape)
                        .background(AppColors.Green)
                        .clickable {
                            vm.markReadUpToBottom()
                            scope.launch { messages.lastOrNull()?.let { listState.animateScrollToItem(messages.lastIndex) } }
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("↓ " + stringResource(R.string.chat_new_messages), style = AppText.small, color = AppColors.OnGreen)
                }
            }
        }

        typing.firstOrNull()?.let { name ->
            Text(
                stringResource(R.string.chat_typing, name),
                style = AppText.tiny,
                color = AppColors.Muted,
                modifier = Modifier.padding(start = 16.dp, bottom = 2.dp),
            )
        }

        replyTo?.let { original ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Elevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(
                        R.string.chat_replying_to,
                        authors[original.userId]?.username ?: "",
                    ),
                    style = AppText.tiny,
                    color = AppColors.Blue,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = AppColors.Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { replyTo = null },
                )
            }
        }

        attachment?.let { (path, kind) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Elevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (kind == "image") "🖼 " + stringResource(R.string.chat_add_photo) else "🎬 " + videoLabel,
                    style = AppText.tiny,
                    color = AppColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = AppColors.Muted,
                    modifier = Modifier.size(16.dp).clickable { attachment = null },
                )
            }
        }

        mediaToSend?.let { (path, mime, type) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Elevated)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    (if (type == "video") "🎬 " else "🎙️ ") + (if (type == "video") videoLabel else audioLabel),
                    style = AppText.tiny,
                    color = AppColors.Text,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    sendLabel,
                    style = AppText.tiny,
                    color = AppColors.Green,
                    modifier = Modifier.clickable {
                        vm.send(
                            content = null,
                            replyTo = replyTo,
                            messageType = type,
                            mediaPath = path,
                            mediaMime = mime,
                        ) { _, error ->
                            if (error == null) {
                                mediaToSend = null
                                replyTo = null
                                statusMessage = if (type == "video") videoSentMsg else audioSentMsg
                            } else {
                                statusMessage = error
                            }
                        }
                    },
                )
                Spacer(Modifier.size(10.dp))
                Icon(
                    Icons.Filled.Close,
                    contentDescription = null,
                    tint = AppColors.Muted,
                    modifier = Modifier.size(16.dp).clickable { mediaToSend = null },
                )
            }
        }

        statusMessage?.let { message ->
            Text(
                message,
                style = AppText.tiny,
                color = AppColors.Red,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
        }

        // شريط الإدخال
        if (!canPost) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.Elevated)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = when {
                        !signedIn -> stringResource(R.string.msg_sign_in_to_participate)
                        isBanned -> stringResource(R.string.chat_banned)
                        isMuted -> stringResource(R.string.chat_muted_until, TimeFormat.countdown(chatStatus?.mutedUntil))
                        else -> stringResource(R.string.comm_locked)
                    },
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                    modifier = Modifier.weight(1f),
                )
                if (!signedIn) {
                    GradientButton(
                        text = stringResource(R.string.auth_sign_in),
                        onClick = onLogin,
                        modifier = Modifier.widthIn(min = 110.dp),
                    )
                }
            }
        } else {
            ChatInputBar(
                channel = channel,
                text = text,
                onTextChange = { value ->
                    text = value
                    if (value.isNotEmpty()) vm.notifyTyping()
                },
                onAttach = { showAttach = true },
                onEmoji = { showEmoji = true },
                onSend = {
                    val content = text.trim()
                    if (content.isEmpty() && attachment == null) return@ChatInputBar
                    // نقر مزدوج على «إرسال» = رسالة واحدة (النص يُصفَّر بعد نجاح الإرسال فقط)
                    if (sending) return@ChatInputBar
                    val attach = attachment
                    sending = true
                    vm.send(
                        content = content.ifEmpty { null },
                        replyTo = replyTo,
                        imagePath = if (attach?.second == "image") attach.first else null,
                        messageType = if (attach?.second == "image") "image" else "text",
                    ) { ok, error ->
                        sending = false
                        if (ok) {
                            text = ""
                            attachment = null
                            replyTo = null
                            statusMessage = null
                        } else {
                            statusMessage = error
                        }
                    }
                },
                remaining = 500 - text.length,
                mentionSuggestions = if (text.endsWith("@")) authors.values.map { it.username ?: "" }
                    .filter { it.isNotBlank() }.take(40) else emptyList(),
                onMention = { name -> text = text.dropLast(1) + "@$name " },
                slowmodeSeconds = channel.slowmodeSeconds,
            )
        }
    }

    // النوافذ المنبثقة (E4/C4/C6/C7)
    if (showEmoji) {
        EmojiStickerSheet(
            stickers = stickers,
            packs = packs,
            resolvedStickerUrl = { container.siteAssetUrl(it) },
            onDismiss = { showEmoji = false },
            onEmoji = { emoji ->
                text += emoji
                showEmoji = false
            },
            onSticker = { stickerUrl ->
                vm.send(content = null, replyTo = replyTo, stickerUrl = stickerUrl, messageType = "sticker") { _, error ->
                    statusMessage = error
                }
                showEmoji = false
            },
        )
    }

    if (showAttach) {
        AttachSheet(
            onDismiss = { showAttach = false },
            onPhoto = {
                showAttach = false
                imagePicker.launch("image/*")
            },
            onAudioVideo = {
                showAttach = false
                // الوسائط (فيديو · صوت · تسجيل صوتي) للأدمن فقط — حماية مزدوجة مع الخادم
                if (isAdmin) mediaSheet = true else statusMessage = mediaAdminMsg
            },
            isAdmin = isAdmin,
        )
    }

    if (mediaSheet) {
        AttachMediaSheet(
            onDismiss = { mediaSheet = false },
            onAudio = {
                mediaSheet = false
                if (isAdmin) mediaPicker.launch("audio/*") else statusMessage = mediaAdminMsg
            },
            onVideo = {
                mediaSheet = false
                if (isAdmin) mediaPicker.launch("video/*") else statusMessage = mediaAdminMsg
            },
        )
    }

    if (showMembers) {
        MembersSheet(
            authors = authors.values.toList(),
            onDismiss = { showMembers = false },
            onOpenProfile = { p ->
                showMembers = false
                onOpenUser(p)
            },
        )
    }

    if (showPinned) {
        PinnedSheet(
            pinned = pinnedMessages,
            onDismiss = { showPinned = false },
        )
    }

    actionsFor?.let { ui ->
        // مدة الحذف: ٣ دقائق من وقت الإرسال — يُخفى الخيار بعدها ويُعلَّل السبب.
        val deleteLeftMinutes = deleteWindowRemainingMinutes(ui.message.createdAt)
        MessageActionsSheet(
            ui = ui,
            myId = container.supabase.session()?.userId,
            isStaff = profile?.let { it.role == "admin" || it.role == "moderator" } == true,
            canDeleteMine = canDeleteOwnMessage(ui.message.createdAt),
            deleteLeftText = if (deleteLeftMinutes > 0) {
                stringResource(R.string.chat_delete_left, deleteLeftMinutes)
            } else null,
            onDismiss = { actionsFor = null },
            onReply = {
                replyTo = ui.message
                actionsFor = null
            },
            onCopy = { copyText(context, ui.message.content.orEmpty()) },
            onReport = { reason ->
                vm.report(ui.message.id, reason, null)
                statusMessage = reportSentMsg
                actionsFor = null
            },
            onDeleteMine = {
                // داخل المدة فقط (القاعدة ترفض ما بعدها) — والنتيجة تُعرض في الشريط السفلي
                if (canDeleteOwnMessage(ui.message.createdAt)) {
                    vm.deleteMine(ui.message.id) { ok ->
                        if (!ok) statusMessage = deleteExpiredMsg // رفض الخادم: انتهت المدة
                    }
                } else {
                    statusMessage = deleteExpiredMsg
                }
                actionsFor = null
            },
            onModerate = {
                vm.moderate(ui.message.id, true)
                actionsFor = null
            },
            onPin = {
                vm.pin(ui.message.id) { ok -> statusMessage = if (ok) pinMsg else unpinMsg }
                actionsFor = null
            },
            onMute = { minutes ->
                vm.mute(ui.message.userId, minutes)
                actionsFor = null
            },
            onOpenProfile = {
                authors[ui.message.userId]?.let { onOpenUser(it) }
                actionsFor = null
            },
            onReact = { emoji ->
                vm.toggleReaction(ui.message.id, emoji)
                actionsFor = null
            },
        )
    }

    imageViewerUrl?.let { url ->
        com.addonstation.mobile.ui.common.FullScreenImageViewer(url = url) { imageViewerUrl = null }
    }
}

@Composable
private fun ChatHeader(
    channel: ChatChannel,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onPinned: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // ترويسة الشات كانت تمتد تحت شريط الحالة؛ الآن تحجز مساحته وتحته يبدأ المحتوى.
            .background(AppColors.Bg.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Elevated2)
                .clickable { onBack() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = AppColors.Text,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                RoomIcon(channelId = channel.id, size = 20.dp)
                Text(
                    // اسم غرفة اللغة بلغتها نفسها (العربية · Русский · 中文 …)
                    com.addonstation.mobile.ui.community.channelDisplayName(channel),
                    style = AppText.chatName,
                    color = AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (channel.isLocked) Text("🔒", style = AppText.tiny)
            }
            // الوصف أيضاً بلغة الغرفة نفسها لغرف اللغات
            com.addonstation.mobile.ui.community.channelDisplayDescription(channel)?.let {
                Text(it, style = AppText.tiny, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        HeaderIcon(Icons.Filled.Group, onMembers)
        HeaderIcon(Icons.Filled.PushPin, onPinned)
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Elevated)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(16.dp))
    }
}

/** شريط الإدخال: ➕ · حقل النص · 😊 · زر الإرسال الأخضر. */
@Composable
private fun ChatInputBar(
    channel: ChatChannel,
    text: String,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    onSend: () -> Unit,
    remaining: Int,
    mentionSuggestions: List<String>,
    onMention: (String) -> Unit,
    slowmodeSeconds: Int,
) {
    Column {
        if (mentionSuggestions.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .padding(horizontal = 12.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Elevated)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp)),
            ) {
                items(mentionSuggestions.size) { index ->
                    val name = mentionSuggestions[index]
                    Text(
                        "@$name",
                        style = AppText.bodySmall,
                        color = AppColors.Blue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMention(name) }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppColors.Elevated2)
                    .clickable { onAttach() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.chat_add_photo), tint = AppColors.Text)
            }
            Column(Modifier.weight(1f)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 500) onTextChange(it) },
                    placeholder = {
                        Text(
                            stringResource(
                                R.string.chat_hint,
                                com.addonstation.mobile.ui.community.channelDisplayName(channel),
                            ),
                            style = AppText.bodySmall,
                        )
                    },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (remaining in 1..49) {
                    Text(
                        "$remaining",
                        style = AppText.tiny,
                        color = if (remaining < 10) AppColors.Red else AppColors.Muted,
                        modifier = Modifier.padding(start = 6.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(AppColors.Elevated2)
                    .clickable { onEmoji() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.EmojiEmotions, contentDescription = stringResource(R.string.chat_emoji), tint = AppColors.Text)
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(if (text.isBlank()) AppColors.Elevated2 else AppColors.Green)
                    .clickable(enabled = text.isNotBlank()) { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    tint = if (text.isBlank()) AppColors.Dim else AppColors.OnGreen,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/** عنصر رسالة واحدة مع التجميع، الرد المقتبس، المنشن، والتفاعلات. */
@Composable
private fun MessageBlock(
    ui: ChatMessageUi,
    previous: ChatMessageUi?,
    showDate: Boolean,
    isNewDivider: Boolean,
    myId: String?,
    onLongPress: () -> Unit,
    onToggleReaction: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    stickerUrlResolver: (String) -> String?,
) {
    val container = rememberContainer()
    val message = ui.message
    val author = ui.author
    val grouped = previous != null &&
        previous.message.userId == message.userId &&
        TimeFormat.withinFiveMinutes(previous.message.createdAt, message.createdAt)

    if (showDate) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f).height(1.dp).background(AppColors.Border))
            Text(
                TimeFormat.dayLabel(message.createdAt, Locale.getDefault()),
                style = AppText.tiny,
                color = AppColors.Muted,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            Box(Modifier.weight(1f).height(1.dp).background(AppColors.Border))
        }
    }

    if (message.messageType == "system") {
        Text(
            message.content.orEmpty(),
            style = AppText.tiny,
            color = AppColors.Muted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    val mentionsMe = myId != null && message.mentions.contains(myId)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = if (grouped) 2.dp else 10.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    message.isAnnouncement -> AppColors.AnnouncementBg
                    mentionsMe -> AppColors.MentionTint
                    else -> Color.Transparent
                }
            )
            .combinedClickable(onClick = onLongPress, onLongClick = onLongPress)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        if (message.isAnnouncement) {
            Text(
                "📢 " + stringResource(R.string.chat_announcement),
                style = AppText.tiny,
                color = AppColors.Gold,
            )
        }

        if (!grouped) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                com.addonstation.mobile.ui.components.AvatarImage(
                    url = assetUrl(container, author?.avatarUrl),
                    size = 40.dp,
                    online = author?.showOnlineStatus == true,
                    modifier = Modifier.clickable { onOpenUser(message.userId) },
                )
                Text(
                    text = author?.displayName ?: author?.username ?: "—",
                    style = AppText.chatName,
                    color = com.addonstation.mobile.ui.theme.nameColor(author?.role ?: "user", author?.effectivePlan ?: "free"),
                    maxLines = 1,
                )
                if (author != null) {
                    com.addonstation.mobile.ui.components.RankBadge(role = author.role, plan = author.effectivePlan)
                }
                if (author?.equippedItemId != null && author.equippedItemId != "none") {
                    // الأداة المجهّزة بجانب اسم صاحبها: صورتها الحقيقية (متحرّكة إن كانت GIF)
                    val itemImg = com.addonstation.mobile.ui.equippedItemUrl(container, author.equippedItemId)
                    if (itemImg != null) {
                        com.addonstation.mobile.ui.components.CoverImage(
                            url = itemImg,
                            radius = 4.dp,
                            modifier = Modifier.size(16.dp),
                        )
                    } else {
                        Text("🎖", style = AppText.tiny)
                    }
                }
                Text(
                    TimeFormat.timeOnly(message.createdAt, Locale.getDefault()),
                    style = AppText.chatTime,
                    color = AppColors.Dim,
                )
            }
        }

        ui.replyTo?.let { original ->
            Row(
                modifier = Modifier
                    .padding(start = 48.dp, top = 2.dp)
                    .fillMaxWidth(0.9f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(width = 2.dp, height = 16.dp)
                        .background(AppColors.Dim),
                )
                Text(
                    "@${ui.replyAuthorName ?: ""}: " + original.content.orEmpty().take(60),
                    style = AppText.tiny,
                    color = AppColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        when (message.messageType) {
            "sticker" -> {
                val url = stickerUrlResolver(message.stickerUrl ?: "")
                Box(
                    modifier = Modifier
                        .padding(start = 48.dp, top = 4.dp)
                        // حجم الملصق يتبع عرض شاشة الهاتف (لا يفيض على الشاشات الضيقة)
                        .size(
                            com.addonstation.mobile.ui.theme.screenWidthFraction(
                                fraction = 0.38f, minDp = 108, maxDp = 180,
                            ),
                        )
                        .clickable { message.stickerUrl?.let(onOpenImage) },
                ) {
                    com.addonstation.mobile.ui.components.CoverImage(url = url, radius = 12.dp, modifier = Modifier.fillMaxSize())
                }
            }
            "image" -> {
                Column {
                    com.addonstation.mobile.ui.components.CoverImage(
                        url = null,
                        radius = 12.dp,
                        modifier = Modifier
                            .padding(start = 48.dp, top = 4.dp)
                            .widthIn(max = 260.dp)
                            .height(160.dp)
                            .clickable { message.imagePath?.let(onOpenImage) },
                    )
                    Text(
                        "🖼",
                        style = AppText.tiny,
                        color = AppColors.Muted,
                        modifier = Modifier
                            .padding(start = 48.dp)
                            .clickable { message.imagePath?.let(onOpenImage) },
                    )
                }
            }
            "audio", "video" -> {
                Row(
                    modifier = Modifier
                        .padding(start = 48.dp, top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(if (message.messageType == "video") "🎬" else "🎙️", style = AppText.bodySmall)
                    Column {
                        Text(
                            if (message.messageType == "video") stringResource(R.string.chat_video_label)
                            else stringResource(R.string.chat_audio_label),
                            style = AppText.tiny,
                            color = AppColors.Text,
                        )
                        Text(
                            TimeFormat.durationLabel(message.mediaDuration) + " · " + TimeFormat.fileSizeLabel(message.mediaSize),
                            style = AppText.tiny,
                            color = AppColors.Muted,
                        )
                    }
                }
            }
            else -> {
                if (message.status == "hidden" || message.status == "deleted") {
                    Text(
                        "🚫 " + stringResource(R.string.chat_hidden_message),
                        style = AppText.bodySmall,
                        color = AppColors.Muted,
                        modifier = Modifier.padding(start = 48.dp),
                    )
                } else {
                    Text(
                        text = message.content.orEmpty(),
                        style = AppText.chatBody,
                        color = AppColors.Text,
                        modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                    )
                }
            }
        }

        if (ui.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ui.reactions.forEach { (emoji, count) ->
                    val mine = ui.myReactions.contains(emoji)
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(AppColors.Elevated2)
                            .border(
                                1.dp,
                                if (mine) AppColors.Green else AppColors.Border,
                                CircleShape,
                            )
                            .clickable { onToggleReaction(emoji) }
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(emoji, style = AppText.tiny)
                        Text("$count", style = AppText.tiny, color = AppColors.Muted)
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(AppColors.Elevated)
                        .clickable { onLongPress() }
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text("+", style = AppText.tiny, color = AppColors.Muted)
                }
            }
        } else {
            Row(
                modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                listOf("👍", "❤️").forEach { emoji ->
                    Text(
                        emoji,
                        style = AppText.tiny,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { onToggleReaction(emoji) }
                            .padding(2.dp),
                    )
                }
                Text(
                    "+",
                    style = AppText.tiny,
                    color = AppColors.Dim,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onLongPress() }
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
    }
}

private fun copyText(context: android.content.Context, text: String) {
    val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
    clipboard?.setPrimaryClip(android.content.ClipData.newPlainText("AddonStation", text))
}

/** itemsIndexed مبسّط (يعمل مع LazyListScope). */
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(
    items: List<T>,
    crossinline content: @Composable (Int, T) -> Unit,
) {
    items(items.size) { index -> content(index, items[index]) }
}
