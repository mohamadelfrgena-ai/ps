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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatMessageUi
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.community.isReadOnlyFor
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
    val scrollTick by vm.scrollTick.collectAsState()
    val typing by vm.typing.collectAsState()
    val stickers by vm.stickers.collectAsState()
    val packs by vm.packs.collectAsState()
    val profile by container.state.profile.collectAsState()
    val chatStatus by container.state.chatStatus.collectAsState()
    // **القراءة فقط**: في الغرف المقفلة أو التي صلاحيتها admin_only لا يملك
    // المستخدم حق الكتابة ⇒ نُخفي عنه شريط الكتابة بالكامل (بما فيه زر الإرسال).
    val readOnlyRoom = isReadOnlyFor(channel, profile?.role)

    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    /** ملصق أُزيل بضغطة سريعة ⇒ لوحة الملصقات تُفتح عليه مباشرة (العودة لمكانه). */
    var resentSticker by remember { mutableStateOf<String?>(null) }
    var attachment by remember { mutableStateOf<Pair<String, String>?>(null) } // (path, kind)
    /**
     * ملصق اختاره المستخدم ولم يُرسله بعد: تظهر **معاينته داخل مربع الإرسال**،
     * ويستطيع أن يكتب معه نصاً إن أراد، ثم يضغط إرسال — فيخرجان في رسالة واحدة
     * (النص فوقه والملصق تحته مباشرة). زر ✕ الأحمر على يمين المعاينة يُلغيه.
     */
    var pendingSticker by remember { mutableStateOf<String?>(null) }
    var showEmoji by remember { mutableStateOf(false) }
    var showAttach by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showPinned by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    var mediaSheet by remember { mutableStateOf(false) }
    var imageViewerUrl by remember { mutableStateOf<String?>(null) }
    // فتح صورة/ملصق بالحجم الكامل — دالة واحدة يستعملها كل نداء (لا يُشار إليها من
    // داخل لامدا آخر كوسيط مجاور: وسائط الاستدعاء ليست متغيّرات مرئية في بعضها)
    val openFullSize: (String) -> Unit = { path ->
        scope.launch { container.chat.imageUrl(path)?.let { imageViewerUrl = it } }
    }
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

    // هل العرض ملتصق بالأسفل؟ (= المستخدم يقرأ آخر الرسائل)
    var atBottom by remember { mutableStateOf(true) }

    // تمرير تلقائي للأسفل: دائماً إن كان ملتصقاً بالأسفل، أو لم تُرفع شارة «رسائل بالأسفل» بعد
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && (atBottom || !newBelow)) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    // الرسالة التي أرسلها المستخدم: تُمرَّر إليها دائماً. بدون هذا تختفي تحت حافة الشاشة
    // ما دامت شارة «رسائل بالأسفل» مفعّلة، فيظنّ صاحبها أن رسالته ضاعت.
    LaunchedEffect(scrollTick) {
        if (scrollTick > 0 && messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }
    // تتبّع موضع العرض: بلوغ آخر رسالة = قرأها ⇒ شارة «رسائل بالأسفل» تنزل والتمرير يعود
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            messages.isNotEmpty() && last >= messages.lastIndex
        }.collect { bottom ->
            atBottom = bottom
            if (bottom) vm.markReadUpToBottom()
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
                // صورة وملصق في رسالة واحدة غير واضحين ⇒ الصورة تُسقط الملصق المنتظر
                .onSuccess { path -> attachment = path to "image"; pendingSticker = null }
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
                            onReply = { replyTo = ui.message },
                            onOpenImage = openFullSize,
                            onStickerTap = {
                                // ملصقي: ضغطة سريعة تُشيله من الشات وتعيده إلى لوحة الملصقات
                                val myId = container.supabase.session()?.userId
                                if (ui.message.userId == myId) {
                                    val sticker = ui.message.stickerUrl
                                    vm.deleteMine(ui.message.id) { ok ->
                                        if (ok && sticker != null) {
                                            resentSticker = sticker
                                            showEmoji = true
                                        } else if (!ok) {
                                            statusMessage = deleteExpiredMsg
                                        }
                                    }
                                } else {
                                    // ملصق غيري: ضغطة سريعة تفتحه بالحجم الكامل (لا تُحذف رسالة غيري)
                                    ui.message.stickerUrl?.let(openFullSize)
                                }
                            },
                            onToggleReaction = { emoji -> vm.toggleReaction(ui.message.id, emoji) },
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
                        // الوسائط مع أي كتابة ⇒ رسالة واحدة
                        val body = text.trim()
                        val replySnapshot = replyTo
                        text = ""            // مسح فوري: يمنع الإرسال مرّتين بالضغط المتكرر
                        replyTo = null
                        vm.send(
                            content = body.ifEmpty { null },
                            replyTo = replySnapshot,
                            messageType = type,
                            mediaPath = path,
                            mediaMime = mime,
                        ) { ok, error ->
                            if (ok) {
                                mediaToSend = null
                                statusMessage = if (type == "video") videoSentMsg else audioSentMsg
                            } else {
                                if (text.isEmpty()) text = body   // لا يضيع ما كتبه
                                replyTo = replySnapshot
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
        } else if (readOnlyRoom) {
            // لا حقل كتابة ولا زر إرسال: قراءة فقط، والإدارة وحدها تنشر هنا
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Bg.copy(alpha = 0.96f))
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🔒", style = AppText.body)
                Text(
                    stringResource(R.string.chat_read_only),
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                )
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
                    if (content.isEmpty() && attachment == null && pendingSticker == null) return@ChatInputBar
                    val attach = attachment
                    val sticker = pendingSticker
                    val replySnapshot = replyTo
                    text = ""                // مسح فوري: الفقاعة تظهر الآن، ولا يمكن إرسالها مرّتين
                    pendingSticker = null
                    replyTo = null
                    vm.send(
                        content = content.ifEmpty { null },
                        replyTo = replySnapshot,
                        stickerUrl = sticker,
                        imagePath = if (attach?.second == "image") attach.first else null,
                        messageType = when {
                            sticker != null -> "sticker"
                            attach?.second == "image" -> "image"
                            else -> "text"
                        },
                    ) { ok, error ->
                        if (ok) {
                            attachment = null
                            statusMessage = null
                        } else {
                            if (text.isEmpty()) text = content   // استرجاع ما كتبه إن فشل الإرسال
                            if (pendingSticker == null) pendingSticker = sticker   // الملصق لا يضيع
                            replyTo = replySnapshot
                            statusMessage = error
                        }
                    }
                },
                remaining = 500 - text.length,
                mentionSuggestions = if (text.endsWith("@")) authors.values.map { it.username ?: "" }
                    .filter { it.isNotBlank() }.take(40) else emptyList(),
                onMention = { name -> text = text.dropLast(1) + "@$name " },
                slowmodeSeconds = channel.slowmodeSeconds,
                pendingStickerUrl = pendingSticker?.let { container.siteAssetUrl(it) },
                onRemoveSticker = { pendingSticker = null },
                canSend = text.isNotBlank() || attachment != null || pendingSticker != null,
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
            focusSticker = resentSticker,
            onSticker = { stickerUrl ->
                // اختيار الملصق لا يُرسله فوراً: يوضعه في مربع الإرسال (معاينة + ✕ أحمر)
                // فيستطيع صاحبه أن يكتب نصاً تحته إن أراد، ثم يضغط إرسال.
                pendingSticker = stickerUrl
                resentSticker = null
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
            .background(AppColors.Bg.copy(alpha = 0.96f))
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
    /**
     * ملصق مختار ينتظر الإرسال: رابط معاينته تظهر **داخل مربع الإرسال** فوق
     * حقل الكتابة، وزر ✕ الأحمر على أقصى يمينه يُلغيه.
     */
    pendingStickerUrl: String? = null,
    onRemoveSticker: () -> Unit = {},
    /** هل يمكن الإرسال الآن؟ (نص مكتوب، أو صورة مرفقة، أو ملصق في الانتظار) */
    canSend: Boolean = text.isNotBlank(),
) {
    Column {
        // معاينة الملصق داخل مربع الإرسال (النص يُكتب تحته إن أراد، ثم إرسال)
        if (pendingStickerUrl != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.Elevated)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                PendingStickerPreview(url = pendingStickerUrl, onRemove = onRemoveSticker)
                Text(
                    stringResource(R.string.chat_sticker_ready),
                    style = AppText.tiny,
                    color = AppColors.Muted,
                    modifier = Modifier.weight(1f),
                )
            }
            Spacer(Modifier.height(6.dp))
        }
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
                    // Enter على لوحة المفاتيح يُرسل الرسالة (زر الإرسال يبقى موجوداً)
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                    // Enter على لوحة المفاتيح يُرسل ما في المربع (نصّاً أو ملصقاً)
                    keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
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
                    .background(if (canSend) AppColors.Green else AppColors.Elevated2)
                    .clickable(enabled = canSend) { onSend() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = stringResource(R.string.chat_send),
                    tint = if (canSend) AppColors.OnGreen else AppColors.Dim,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * معاينة الملصق المختار داخل مربع الإرسال: الصورة + زر ✕ أحمر دائري على أقصى
 * يمينها (فوقها) يُلغي الاختيار، فيعود مربع الإرسال نصّياً كما كان.
 */
@Composable
private fun PendingStickerPreview(
    url: String,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            // حجم المعاينة يتبع عرض شاشة الهاتف (لا يفيض على الشاشات الضيقة)
            .size(
                com.addonstation.mobile.ui.theme.screenWidthFraction(
                    fraction = 0.30f, minDp = 92, maxDp = 132,
                ),
            ),
    ) {
        com.addonstation.mobile.ui.components.CoverImage(
            url = url,
            radius = 12.dp,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp)
                .clip(CircleShape)
                .background(AppColors.Red)
                .clickable { onRemove() }
                .padding(4.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = stringResource(R.string.chat_remove_sticker),
                tint = androidx.compose.ui.graphics.Color.White,
                modifier = Modifier.fillMaxSize(),
            )
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
    /** ضغطة بسيطة على الرسالة ⇒ الرد عليها مباشرة (الضغط المطوّل = قائمة الإبلاغ/الحذف). */
    onReply: () -> Unit,
    /** ضغطة سريعة على ملصقي ⇒ يُشال ويعود إلى لوحة الملصقات. */
    onStickerTap: (() -> Unit)? = null,
    onToggleReaction: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    stickerUrlResolver: (String) -> String?,
) {
    val container = rememberContainer()
    val message = ui.message
    val author = ui.author
    // رابط الصورة المرفقة: يُوقَّع من الخادم مرة واحدة لكل رسالة (بدونه تظهر فارغة)
    var imageUrl by remember(message.id, message.imagePath) { mutableStateOf<String?>(null) }
    LaunchedEffect(message.id, message.imagePath) {
        imageUrl = message.imagePath?.let { runCatching { container.chat.imageUrl(it) }.getOrNull() }
    }
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
            .combinedClickable(onClick = onReply, onLongClick = onLongPress)
            .alpha(if (ui.pending) 0.55f else 1f)      // قيد الإرسال — تظهر فوراً
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) messageBody@{
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
                // وقت الإرسال لم يعد هنا: صار في **نهاية الرسالة نفسها** (أسفل
                // النص على حافة الفقاعة) كما في صورة المالك.
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
                    // مقتبس بلا نص (ملصق/صورة) ⇒ رمز بدل فراغ
                    "@${ui.replyAuthorName ?: ""}: " +
                        (if (original.content.isNullOrBlank() &&
                            (original.stickerUrl != null || original.imagePath != null)
                        ) "🖼 " else "") +
                        original.content.orEmpty().take(60),
                    style = AppText.tiny,
                    color = AppColors.Muted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        // رسالة محذوفة أو مخفية: سطر واحد فقط لكل الأنواع (نص/ملصق/وسائط/صورة)
        if (message.status == "hidden" || message.status == "deleted") {
            Text(
                "🚫 " + stringResource(R.string.chat_hidden_message),
                style = AppText.bodySmall,
                color = AppColors.Muted,
                modifier = Modifier.padding(start = 48.dp),
            )
            return@messageBody
        }

        when (message.messageType) {
            "sticker" -> {
                val url = stickerUrlResolver(message.stickerUrl ?: "")
                Column(modifier = Modifier.padding(start = 48.dp, top = 4.dp)) {
                    // النص أولاً، وتحته مباشرة الملصق — رسالة واحدة كما يراها صاحبها
                    if (!message.content.isNullOrBlank()) {
                        Text(
                            text = message.content.orEmpty(),
                            style = AppText.chatBody,
                            color = AppColors.Text,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                // حجم الملصق يتبع عرض شاشة الهاتف (لا يفيض على الشاشات الضيقة)
                                .size(
                                    com.addonstation.mobile.ui.theme.screenWidthFraction(
                                        fraction = 0.38f, minDp = 108, maxDp = 180,
                                    ),
                                )
                                .clickable {
                                    // ملصقي ⇒ ضغطة سريعة = إرجاعه للوحة الملصقات، وغيري = عرض بالحجم الكامل
                                    val takeBack = onStickerTap
                                    if (takeBack != null) takeBack() else message.stickerUrl?.let(onOpenImage)
                                },
                        ) {
                            com.addonstation.mobile.ui.components.CoverImage(url = url, radius = 12.dp, modifier = Modifier.fillMaxSize())
                        }
                        // الوقت في نهاية الرسالة (أسفل الملصق على الحافة)
                        MessageTime(createdAt = message.createdAt)
                    }
                }
            }
            "image" -> {
                Row(
                    modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Column {
                        com.addonstation.mobile.ui.components.CoverImage(
                            // الرابط الحقيقي للصورة (يُوقَّع من الخادم عند الحاجة)
                            url = imageUrl,
                            radius = 12.dp,
                            modifier = Modifier
                                .widthIn(max = 240.dp)
                                .height(160.dp)
                                .clickable {
                                    val path = message.imagePath
                                    if (imageUrl != null) onOpenImage(imageUrl!!)
                                    else if (path != null) onOpenImage(path)
                                },
                        )
                        Text(
                            "🖼",
                            style = AppText.tiny,
                            color = AppColors.Muted,
                            modifier = Modifier.clickable {
                                val path = message.imagePath
                                if (imageUrl != null) onOpenImage(imageUrl!!)
                                else if (path != null) onOpenImage(path)
                            },
                        )
                    }
                    // الوقت في نهاية الرسالة (أسفل الصورة على الحافة)
                    MessageTime(createdAt = message.createdAt)
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
                    // الوقت في نهاية الرسالة (على حافة شريط الوسائط)
                    MessageTime(createdAt = message.createdAt)
                }
            }
            else -> {
                Row(
                    modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = message.content.orEmpty(),
                        style = AppText.chatBody,
                        color = AppColors.Text,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // الوقت في نهاية السطر (على حافة الفقاعة) — صغير وباهت
                    MessageTime(createdAt = message.createdAt)
                }
            }
        }

        // التفاعلات بأسلوب ديسكورد: صفّ من **البسولات الدائرية** تحت الرسالة،
        // والبسولة التي تفاعلتَ بها بخلفية مضيئة وإطار أخضر ورقم أخضر، وآخر
        // الصفّ زر «إضافة تفاعل» (＋) الذي يفتح لوحة الرموز.
        if (ui.reactions.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(start = 48.dp, top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ui.reactions.forEach { (emoji, count) ->
                    ReactionPill(
                        emoji = emoji,
                        count = count,
                        selected = ui.myReactions.contains(emoji),
                        onClick = { onToggleReaction(emoji) },
                    )
                }
                AddReactionPill(onClick = onLongPress)
            }
        }
    }
}

/**
 * بسولة تفاعل واحدة (ديسكورد): رمز + عدد داخل كبسولة دائرية صغيرة.
 * البسولة التي تفاعلتَ بها = خلفية مضيئة + إطار أخضر + رقم أخضر.
 */
@Composable
private fun ReactionPill(
    emoji: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(if (selected) AppColors.GreenGlow else AppColors.Elevated)
            .border(
                width = if (selected) 1.5.dp else 1.dp,
                color = if (selected) AppColors.Green else AppColors.Border,
                shape = RoundedCornerShape(13.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(emoji, style = AppText.small)
        Text(
            text = "$count",
            style = AppText.tiny,
            color = if (selected) AppColors.Green else AppColors.Muted,
        )
    }
}

/**
 * وقت إرسال الرسالة — **في نهاية الرسالة نفسها** (أسفل النص/الملصق/الصورة على
 * حافة الفقاعة) كما في صورة المالك، لا بجانب اسم صاحبها في الأعلى: رقم صغير
 * باهت (9.5sp) فلا يزاحم النص ولا يقطع تسلسل الرسائل المجمّعة.
 */
@Composable
private fun MessageTime(
    createdAt: String?,
    modifier: Modifier = Modifier,
) {
    Text(
        text = TimeFormat.timeOnly(createdAt, Locale.getDefault()),
        style = AppText.chatTime,
        color = AppColors.Dim,
        modifier = modifier,
    )
}

/** زر «إضافة تفاعل» في آخر صفّ التفاعلات: كبسولة （＋ وجه مبتسم） بأسلوب ديسكورد. */
@Composable
private fun AddReactionPill(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(width = 46.dp, height = 26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, RoundedCornerShape(13.dp))
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.EmojiEmotions,
            contentDescription = stringResource(R.string.chat_add_reaction),
            tint = AppColors.Muted,
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 7.dp)
                .size(15.dp),
        )
        Icon(
            Icons.Filled.Add,
            contentDescription = null,
            tint = AppColors.Muted,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 6.dp)
                .size(13.dp),
        )
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
