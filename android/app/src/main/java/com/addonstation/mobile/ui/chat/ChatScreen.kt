@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.addonstation.mobile.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatMessageUi
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.repo.ChatRepository
import com.addonstation.mobile.ui.apiErrorText
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
/**
 * مرفق ينتظر الإرسال داخل مربع الإرسال (صورة · فيديو · صوت).
 *
 * الفكرة: يختار المستخدم الملف فتظهر **معايـنته** (صورته أو إطار الفيديو)
 * داخل مربع الإرسال — كما في تليجرام — ويكتب تحتها ما يشاء، ثم زر الإرسال
 * يخرجهما معاً في رسالة واحدة.
 *
 * @param kind نوع المرفق: "image" · "video" · "audio" (وهو نفس رسالته على الخادم).
 * @param localPreview ملف محلي للمعاينة (صورة أو إطار فيديو) يُعرض فوراً بلا شبكة.
 * @param remotePath مسار الملف في المخزن بعد نجاح الرفع — لا يُرسل بدونه.
 * @param sourceUri أصل الملف من المعرض، لإعادة الرفع إن فشل.
 */
private data class PendingAttachment(
    val kind: String,
    val localPreview: String? = null,
    val remotePath: String? = null,
    val mime: String? = null,
    val durationSeconds: Int? = null,
    val sizeBytes: Long? = null,
    val sourceUri: Uri? = null,
    val uploading: Boolean = false,
    val failed: Boolean = false,
) {
    /** جاهز للإرسال = اكتمل رفعه. */
    val isReady: Boolean get() = !remotePath.isNullOrBlank()
}

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
    val signedIn by container.state.signedIn.collectAsState()
    val chatStatus by container.state.chatStatus.collectAsState()
    // **القراءة فقط**: في الغرف المقفلة أو التي صلاحيتها admin_only لا يملك
    // المستخدم حق الكتابة ⇒ نُخفي عنه شريط الكتابة بالكامل (بما فيه زر الإرسال).
    val readOnlyRoom = isReadOnlyFor(channel, profile?.role)
    /**
     * هل يستطيع المستخدم **الإشراف على هذه الغرفة**؟ الأدمن في كل الغرف،
     * والمشرف في الغرف الممنوحة له فقط (غرفة واحدة أو عدّة أو كلها).
     * تُخفي/تُظهر أزرار الإشراف (حذف أي رسالة · إخفاء · تثبيت · كتم) —
     * والخادم يبقى الحَكَم الأخير على كل طلب.
     */
    val canModerateRoom = container.chat.canModerateRoom(chatStatus, channel.id)

    var text by remember { mutableStateOf("") }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    /** ملصق أُزيل بضغطة سريعة ⇒ لوحة الملصقات تُفتح عليه مباشرة (العودة لمكانه). */
    var resentSticker by remember { mutableStateOf<String?>(null) }
    /** المرفق الجاهز للإرسال (بعد نجاح رفعه) — صورة أو فيديو أو صوت. */
    var attachment by remember { mutableStateOf<PendingAttachment?>(null) }
    /** المرفق الذي ما زال يُرفع (أو فشل رفعه) — لا يُرسل قبل أن يجهز. */
    var uploading by remember { mutableStateOf<PendingAttachment?>(null) }
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
    // **زرّ الرجوع: السهم وإيماءة النظام سواء.** كل ما يُفتح فوق الغرفة
    // (عارض صورة · عارض فيديو · لوحة إيموجي · قائمة رسالة · الأعضاء · المثبّتات
    // · ورقة المرفقات · ورقة الوسائط) يُغلق أولاً قبل مغادرة الغرفة — فلا
    // تُقفل الغرفة كلها بضربة إيماءة واحدة كانت تقصد إغلاق ورقة.
    // (المعالج الأخير هو الذي يستلم الرجوع، فالأخصّ يُسجَّل أخيراً.)
    BackHandler(enabled = mediaSheet) { mediaSheet = false }
    BackHandler(enabled = showAttach) { showAttach = false }
    BackHandler(enabled = showMembers) { showMembers = false }
    BackHandler(enabled = showPinned) { showPinned = false }
    BackHandler(enabled = actionsFor != null) { actionsFor = null }
    BackHandler(enabled = showEmoji) { showEmoji = false }
    BackHandler(enabled = videoViewerUrl != null) { videoViewerUrl = null }
    BackHandler(enabled = imageViewerUrl != null) { imageViewerUrl = null }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    /** سبب فشل الإرسال (يُترجَم في الشاشة) — لا نعرض رمز الخادم الخام. */
    var statusError by remember { mutableStateOf<ApiErrorKind?>(null) }
    var pinnedMessages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    var videoViewerUrl by remember { mutableStateOf<String?>(null) }

    val listState = rememberLazyListState()

    // نصوص تُستخدم داخل لامبدات (stringResource دالة Composable فلا تُستدعى داخل اللامبدا)
    val audioLabel = stringResource(R.string.chat_audio_label)
    val videoLabel = stringResource(R.string.chat_video_label)
    val reportSentMsg = stringResource(R.string.chat_report_sent)
    val deleteExpiredMsg = stringResource(R.string.chat_delete_expired)
    val deleteStaffFailedMsg = stringResource(R.string.chat_delete_failed)
    val mediaAdminMsg = stringResource(R.string.chat_media_admin_only)
    // الأدمن وحده يرسل الفيديو والصوت والتسجيل الصوتي (القاعدة هي الحكم)
    val isAdmin = profile?.role == "admin"
    val pinMsg = stringResource(R.string.chat_pin)
    val unpinMsg = stringResource(R.string.chat_unpin)
    val imageFormatsMsg = stringResource(R.string.chat_image_formats)
    val imageMaxMsg = stringResource(R.string.chat_image_max)
    val mediaMaxMsg = stringResource(R.string.chat_media_max)
    val failedMsg = stringResource(R.string.item_download_failed)
    val uploadingMsg = stringResource(R.string.chat_uploading)
    val uploadFailedMsg = stringResource(R.string.chat_upload_failed)
    val retryLabel = stringResource(R.string.common_retry)
    val notSentMsg = stringResource(R.string.chat_send_failed)

    LaunchedEffect(channel.id) {
        // الرسائل أولاً (دخول الغرفة أسرع): الجلب لا ينتظر طلباً آخر.
        vm.open(channel)
        // نطاق الإشراف يُجلب مع كل دخول غرفة: فإن منح الأدمن مشرفاً غرفةً
        // جديدة (أو سحبها) تظهر أزراره الصحيحة بعد لحظة من فتح الغرفة.
        runCatching { container.chat.refreshMyStatus() }
    }
    // الغرفة معروضة ⇒ الاستطلاع يعمل · ومع مغادرتها يتوقّف (لا شبكة في الخلفية)
    DisposableEffect(channel.id) {
        vm.setScreenActive(true)
        onDispose {
            vm.setScreenActive(false)
            vm.close()
        }
    }

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

    /**
     * رفع صورة: تُقرأ وتُصغَّر، وتُحفظ منها نسخة محلية تظهر **فوراً** في مربع
     * الإرسال (معاينة كتليجرام) ثم في الرسالة لحظة إرسالها — فلا تبقى الصورة
     * بيضاء في انتظار رابط الخادم.
     */
    val imagePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val prepared = com.addonstation.mobile.util.prepareImageForUpload(context, uri, maxSide = 1080)
            val bytes = prepared?.bytes
                ?: com.addonstation.mobile.util.readBytes(context, uri, maxBytes = ChatRepository.MAX_IMAGE_BYTES)
            if (bytes == null) {
                // أكبر من ٤ ميغابايت أو ملف لا يُقرأ — نُخبره بدل صمتٍ مربك
                statusMessage = if (prepared == null) imageMaxMsg else failedMsg
                return@launch
            }
            val mime = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/jpeg"
            if (mime !in ChatRepository.ALLOWED_IMAGE_MIME) {
                statusMessage = imageFormatsMsg
                return@launch
            }
            val name = prepared?.fileName ?: "chat-${System.currentTimeMillis()}.jpg"
            val preview = com.addonstation.mobile.util.cachePreviewFile(
                context, bytes, if (mime == "image/png") "png" else "jpg",
            )
            val draft = PendingAttachment(
                kind = "image",
                localPreview = preview,
                mime = mime,
                sizeBytes = bytes.size.toLong(),
                sourceUri = uri,
                uploading = true,
            )
            uploading = draft
            container.chat.uploadImage(bytes, mime, name)
                // صورة وملصق في رسالة واحدة غير واضحين ⇒ الصورة تُسقط الملصق المنتظر
                .onSuccess { path ->
                    uploading = null
                    attachment = draft.copy(uploading = false, remotePath = path)
                    pendingSticker = null
                    statusMessage = null
                }
                .onFailure {
                    // فشل الرفع: تبقى المعاينة مع زر «إعادة المحاولة» ولا يضيع شيء
                    uploading = draft.copy(uploading = false, failed = true)
                    statusMessage = imageFormatsMsg
                }
        }
    }

    /**
     * رفع فيديو/صوت (للأدمن فقط): للفيديو نستخرج **إطاراً مصغّراً ومدّته** من
     * الملف على الجهاز فيظهر كبطاقة صورة بزر تشغيل (كتليجرام) لا صندوقاً
     * رمادياً. الصورة المصغّرة تُرفع معه ليراها بقية الأعضاء.
     */
    val mediaPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // الوسائط (فيديو · صوت · تسجيل صوتي) للأدمن فقط — لا تُرفع ولو وصل الطلب
            // من عميل قديم لا يعرف القاعدة؛ والخادم يرفضها مرّة أخرى عند الإرسال.
            if (!isAdmin) {
                statusMessage = mediaAdminMsg
                return@launch
            }
            val bytes = com.addonstation.mobile.util.readBytes(context, uri, maxBytes = ChatRepository.MAX_MEDIA_BYTES)
            if (bytes == null) {
                statusMessage = mediaMaxMsg
                return@launch
            }
            val mime = context.contentResolver.getType(uri) ?: "video/mp4"
            val isVideo = mime.startsWith("video")
            val kind = if (isVideo) "video" else "audio"
            val name = (if (isVideo) "video-" else "audio-") + System.currentTimeMillis()
            // إطار + مدّة يُستخرجان مرة واحدة، ثم تُقرأ النتائج داخل اللامبدات
            // كـ val ثابتة (المتغيّر المُلتقَط لا يقبل التحقّق من الفراغ).
            val meta = if (isVideo) com.addonstation.mobile.util.extractVideoMeta(context, uri) else null
            val duration = meta?.durationSeconds
            val thumbPath = meta?.thumbPath
            val draft = PendingAttachment(
                kind = kind,
                localPreview = thumbPath,
                mime = mime,
                durationSeconds = duration,
                sizeBytes = bytes.size.toLong(),
                sourceUri = uri,
                uploading = true,
            )
            uploading = draft
            container.chat.uploadMedia(bytes, mime, name)
                .onSuccess { path ->
                    // إطار الفيديو يُرفع معه (مساره مشتق من مسار الفيديو) ليراه الجميع
                    if (isVideo && thumbPath != null) {
                        val thumb = runCatching { java.io.File(thumbPath).readBytes() }.getOrNull()
                        if (thumb != null) runCatching { container.chat.uploadVideoThumb(thumb, path) }
                    }
                    uploading = null
                    attachment = draft.copy(uploading = false, remotePath = path)
                    statusMessage = null
                }
                .onFailure {
                    uploading = draft.copy(uploading = false, failed = true)
                    statusMessage = mediaMaxMsg
                }
        }
    }

    /**
     * إعادة رفع مرفق فاشل (نفس الملف الذي اختاره) — بلا اختيارٍ من جديد.
     */
    fun retryUpload(draft: PendingAttachment) {
        val uri = draft.sourceUri ?: return
        scope.launch {
            val bytes = com.addonstation.mobile.util.readBytes(
                context, uri, maxBytes = if (draft.kind == "image") ChatRepository.MAX_IMAGE_BYTES else ChatRepository.MAX_MEDIA_BYTES,
            )
            if (bytes == null) {
                statusMessage = if (draft.kind == "image") imageMaxMsg else mediaMaxMsg
                return@launch
            }
            uploading = draft.copy(uploading = true, failed = false)
            val result = if (draft.kind == "image") {
                val prepared = com.addonstation.mobile.util.prepareImageForUpload(context, uri, maxSide = 1080)
                val finalBytes = prepared?.bytes ?: bytes
                val finalMime = prepared?.mime ?: draft.mime ?: "image/jpeg"
                val preview = com.addonstation.mobile.util.cachePreviewFile(
                    context, finalBytes, if (finalMime == "image/png") "png" else "jpg",
                )
                uploading = draft.copy(uploading = true, failed = false, localPreview = preview, mime = finalMime)
                container.chat.uploadImage(finalBytes, finalMime, prepared?.fileName ?: "chat-${System.currentTimeMillis()}.jpg")
            } else {
                container.chat.uploadMedia(bytes, draft.mime ?: "video/mp4", (if (draft.kind == "video") "video-" else "audio-") + System.currentTimeMillis())
            }
            result.onSuccess { path ->
                val ready = (uploading ?: draft).copy(uploading = false, failed = false, remotePath = path)
                uploading = null
                attachment = ready
                statusMessage = null
            }.onFailure {
                uploading = (uploading ?: draft).copy(uploading = false, failed = true)
                statusMessage = if (draft.kind == "image") imageFormatsMsg else mediaMaxMsg
            }
        }
    }

    /**
     * مسار إرسال **واحد** لكل الأنواع (نص · صورة · فيديو · صوت · ملصق): ما
     * يُكتب في الحقل هو تعليق المرفق، فيخرجان في **رسالة واحدة** كتليجرام.
     *
     * لماذا دالة واحدة؟ كان لكل نوع مسارٌ مستقل (زر «إرسال» للنص والملصق،
     * ورابط «إرسال» صغير للوسائط) فتكرّر الكود وظهرت أخطاء الإرسال في مسار
     * دون الآخر. الآن مسار واحد: الفقاعة تظهر فوراً، وإن فشل الإرسال بقيت
     * الرسالة ظاهرة موسومة بـ«لم تُرسل» مع زر إعادة المحاولة (لا تختفي فيظنّ
     * صاحبها أنها وصلت ويضطر إلى الخروج والعودة ليتأكد).
     */
    fun sendNow() {
        val pendingUpload = uploading
        if (pendingUpload != null) {
            statusMessage = if (pendingUpload.failed) uploadFailedMsg else uploadingMsg
            return
        }
        val attach = attachment
        val body = text.trim()
        val sticker = pendingSticker
        val replySnapshot = replyTo
        if (body.isEmpty() && attach == null && sticker == null) return
        text = ""                // مسح فوري: الفقاعة تظهر الآن، ولا يمكن إرسالها مرّتين
        pendingSticker = null
        replyTo = null
        attachment = null
        vm.send(
            content = body.ifEmpty { null },
            replyTo = replySnapshot,
            stickerUrl = sticker,
            imagePath = if (attach?.kind == "image") attach.remotePath else null,
            messageType = when {
                sticker != null -> "sticker"
                attach != null -> attach.kind
                else -> "text"
            },
            mediaPath = if (attach != null && attach.kind != "image") attach.remotePath else null,
            mediaMime = attach?.mime,
            mediaDuration = attach?.durationSeconds,
            mediaSize = attach?.sizeBytes,
            localPreview = attach?.localPreview,
        ) { ok, errorKind ->
            statusError = null
            if (ok) {
                statusMessage = null
            } else {
                if (text.isEmpty()) text = body   // استرجاع ما كتبه إن فشل الإرسال
                if (pendingSticker == null) pendingSticker = sticker   // الملصق لا يضيع
                if (attachment == null) attachment = attach           // المرفق لا يضيع
                replyTo = replySnapshot
                // سبب الرفض **مترجَم** (مكتوم · ممنوع · حدّ المعدّل …) لا رمز خام
                statusMessage = notSentMsg
                statusError = errorKind
            }
        }
    }

    val isMuted = chatStatus?.muted == true
    val isBanned = chatStatus?.banned == true
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
                    itemsIndexedCompat(
                        messages,
                        // مفتاح = معرّف الرسالة: ثابت لا يتغيّر (السالب للمؤقّتة)
                        key = { it.message.id },
                        // نوع المحتوى: الرسائل من نوع واحد يعيد استعمال تركيبها
                        contentType = { it.message.messageType ?: "text" },
                    ) { index, ui ->
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
                                // ضغطة سريعة على **أي** ملصق (لي أو لغيري) ⇒ لوحة الملصقات
                                // تُفتح على مكانه وهو محدَّد — بلا حذف ولا فتح بالحجم الكامل.
                                // (الحذف يبقى من قائمة الضغط المطوّل خلال ٣ دقائق.)
                                ui.message.stickerUrl?.takeIf { it.isNotBlank() }?.let { sticker ->
                                    resentSticker = sticker
                                    showEmoji = true
                                }
                            },
                            onToggleReaction = { emoji -> vm.toggleReaction(ui.message.id, emoji) },
                            onOpenUser = { userId ->
                                authors[userId]?.let { onOpenUser(it) }
                            },
                            stickerUrlResolver = { path -> container.siteAssetUrl(path) },
                            onPlayVideo = {
                                // رابط الفيديو يُوقَّع عند الضغط (صالح دقائق) ثم يُشغَّل
                                scope.launch {
                                    val path = ui.message.mediaPath
                                    if (path.isNullOrBlank()) return@launch
                                    val url = container.chat.mediaUrl(path)
                                    if (url != null) videoViewerUrl = url else statusMessage = failedMsg
                                }
                            },
                            onRetry = { statusError = null; vm.retry(ui.message.id) },
                            onDiscard = { vm.discard(ui.message.id) },
                            notSentLabel = notSentMsg,
                            retryLabel = retryLabel,
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

        // معاينة المرفق داخل مربع الإرسال (صورة/فيديو/صوت) — تُكتب تحتها أي
        // تعليق في الحقل نفسه، ثم زر الإرسال يخرجهما **رسالة واحدة** كتليجرام.
        (uploading ?: attachment)?.let { pending ->
            PendingAttachmentPreview(
                attachment = pending,
                uploadingLabel = uploadingMsg,
                failedLabel = uploadFailedMsg,
                retryLabel = retryLabel,
                onRemove = {
                    uploading = null
                    attachment = null
                },
                onRetry = { retryUpload(pending) },
            )
        }

        // سبب الفشل **مترجَم** (إن وُجد) فوق رسالة الحالة المعتادة
        if (statusError != null) {
            Text(
                apiErrorText(statusError!!),
                style = AppText.tiny,
                color = AppColors.Red,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
            )
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
                onSend = { sendNow() },
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
            isStaff = canModerateRoom,
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
            onDeleteStaff = {
                // حذف إشرافي: أي رسالة في هذه الغرفة مهما كان عمرها
                // (الأدمن في كل الغرف · المشرف في غرفه فقط — والخادم يحسم)
                vm.deleteAsStaff(ui.message.id) { ok ->
                    statusMessage = if (ok) null else deleteStaffFailedMsg
                    statusError = null
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

    videoViewerUrl?.let { url ->
        FullScreenVideoPlayer(url = url) { videoViewerUrl = null }
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

/**
 * معاينة المرفق داخل مربع الإرسال (كتليجرام): صورة مصغّرة + اسم النوع + حالة
 * الرفع (جارٍ الرفع · فشل ⇒ إعادة المحاولة) + زر ✕ أحمر لإلغائه.
 *
 * تظهر **فور** اختيار الملف من المعرض (قبل انتهاء الرفع) حتى يرى المستخدم ما
 * أرفقه فعلاً، ويكتب تعليقه تحته ثم يضغط إرسال.
 */
@Composable
private fun PendingAttachmentPreview(
    attachment: PendingAttachment,
    uploadingLabel: String,
    failedLabel: String,
    retryLabel: String,
    onRemove: () -> Unit,
    onRetry: () -> Unit,
) {
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
        Box(contentAlignment = Alignment.Center) {
            if (attachment.localPreview != null && attachment.kind != "audio") {
                VideoTile(
                    thumbUrl = null,
                    localPreview = attachment.localPreview,
                    durationSeconds = if (attachment.kind == "video") attachment.durationSeconds else null,
                    modifier = Modifier.size(78.dp),
                    // لا تشغيل من مربع الإرسال: الضغطة = إزالة (كما في تليجرام)
                    onClick = onRemove,
                )
            } else if (attachment.kind != "audio") {
                com.addonstation.mobile.ui.components.CoverImage(
                    url = null,
                    radius = 12.dp,
                    modifier = Modifier.size(78.dp),
                )
            } else {
                // الصوت بلا صورة مصغّرة: رمز الميكروفون داخل بطاقة
                Box(
                    modifier = Modifier
                        .size(78.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated2),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("🎙️", style = AppText.body)
                }
            }
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
        Column(Modifier.weight(1f)) {
            Text(
                text = when (attachment.kind) {
                    "video" -> stringResource(R.string.chat_video_label)
                    "audio" -> stringResource(R.string.chat_audio_label)
                    else -> "🖼 " + stringResource(R.string.chat_add_photo)
                },
                style = AppText.bodySmall,
                color = AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    if (attachment.durationSeconds != null && attachment.durationSeconds > 0) {
                        append(TimeFormat.durationLabel(attachment.durationSeconds))
                        append(" · ")
                    }
                    append(TimeFormat.fileSizeLabel(attachment.sizeBytes))
                },
                style = AppText.tiny,
                color = AppColors.Muted,
            )
            Spacer(Modifier.height(4.dp))
            when {
                attachment.uploading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CircularProgressIndicator(
                        color = AppColors.Green,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(uploadingLabel, style = AppText.tiny, color = AppColors.Green)
                }
                attachment.failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(failedLabel, style = AppText.tiny, color = AppColors.Red)
                    Text(
                        retryLabel,
                        style = AppText.tiny,
                        color = AppColors.Green,
                        modifier = Modifier.clickable { onRetry() },
                    )
                }
                else -> Text(
                    stringResource(R.string.chat_attachment_ready),
                    style = AppText.tiny,
                    color = AppColors.Muted,
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
    /** ضغطة بسيطة على الرسالة ⇒ الرد عليها مباشرة (الضغط المطوّل = قائمة الإبلاغ/الحذف). */
    onReply: () -> Unit,
    /** ضغطة سريعة على أي ملصق ⇒ لوحة الملصقات تُفتح على مكانه وهو محدَّد. */
    onStickerTap: (() -> Unit)? = null,
    onToggleReaction: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    stickerUrlResolver: (String) -> String?,
    /** ضغطة على بطاقة الفيديو ⇒ تشغيله (بعد توقيع رابطه). */
    onPlayVideo: () -> Unit = {},
    /** إعادة إرسال رسالة فاشلة من مكانها (تبقى ظاهرة حتى تنجح). */
    onRetry: () -> Unit = {},
    /** إسقاط رسالة فاشلة من القائمة. */
    onDiscard: () -> Unit = {},
    notSentLabel: String = "",
    retryLabel: String = "",
) {
    val container = rememberContainer()
    val message = ui.message
    val author = ui.author
    // رابط الصورة المرفقة: يُوقَّع من الخادم مرة واحدة لكل رسالة (بدونه تظهر فارغة)
    var imageUrl by remember(message.id, message.imagePath) { mutableStateOf<String?>(null) }
    // إعادة توقيع الرابط عند الطلب (ضغطة على الصورة البيضاء) — لا ننتظر التحديث
    var imageAttempt by remember(message.id) { mutableStateOf(0) }
    LaunchedEffect(message.id, message.imagePath, imageAttempt) {
        imageUrl = message.imagePath?.let { runCatching { container.chat.imageUrl(it) }.getOrNull() }
    }
    val reloadImageUrl: () -> Unit = { imageAttempt += 1 }
    // إطار الفيديو: ملف محلي إن كانت رسالتي قيد الإرسال، وإلا رابط موقّع
    var videoThumbUrl by remember(message.id, message.mediaPath) { mutableStateOf<String?>(null) }
    LaunchedEffect(message.id, message.mediaPath, imageAttempt) {
        videoThumbUrl = if (ui.localPreview != null) null
        else runCatching { container.chat.videoThumbUrl(message.mediaPath) }.getOrNull()
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
                // الاسم كما هو (جنب الصورة) … و**اللقب (TheKing · Epic · …)
                // ينزل سطراً تحته** — طلب المالك: سطران بدل سطر واحد.
                // weight بلا fill: يأخذ العمود ما يحتاجه فقط، فتبقى الأداة
                // ملاصقةً له ولا تُدفع إلى حافة الشاشة.
                // **اللقب تحت الاسم**: لا شيء لصاحب الخطة المجانية (طلب المالك)
                // — «Free 🍃» لا تظهر أبداً تحت الاسم. يظهر اللقب للمشترك
                // (Epic 💎 · Legendary 👑) ولصاحب رتبة (أدمن · مشرف · مبدع).
                // والخطة تُقرأ لحظياً عبر effectivePlan: فإن اشترك المجّاني ظهر
                // لقبه من تلقاء نفسه، وإن انتهى اشتراكه عاد السطر فارغاً.
                val showRankTitle = hasRankTitle(author?.role, author?.effectivePlan)
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = author?.displayName ?: author?.username ?: "—",
                        style = AppText.chatName,
                        color = com.addonstation.mobile.ui.theme.nameColor(author?.role ?: "user", author?.effectivePlan ?: "free"),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (author != null && showRankTitle) {
                        Spacer(Modifier.height(3.dp))
                        com.addonstation.mobile.ui.components.RankBadge(
                            role = author.role,
                            plan = author.effectivePlan,
                        )
                    }
                }
                if (author?.equippedItemId != null && author.equippedItemId != "none") {
                    // الأداة المجهّزة **بجانبهما** (الاسم ولقبه): أكبر من قبل
                    // — 32dp بدل 16dp — وأقلّ قليلاً من الصورة (40dp) طلباً
                    // للشكل الجمالي. صورتها الحقيقية (متحرّكة إن كانت GIF).
                    val itemImg = com.addonstation.mobile.ui.equippedItemUrl(container, author.equippedItemId)
                    if (itemImg != null) {
                        com.addonstation.mobile.ui.components.CoverImage(
                            url = itemImg,
                            radius = 8.dp,
                            modifier = Modifier.size(32.dp),
                        )
                    } else {
                        Text("🎖", style = AppText.body.copy(fontSize = 24.sp))
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
                                // ضغطة مطوّلة ⇒ قائمة الرسالة نفسها (رد · نسخ · إبلاغ · حذف)
                                .combinedClickable(
                                    onClick = {
                                        // ضغطة سريعة = لوحة الملصقات على مكان هذا الملصق (بلا حذف)
                                        val takeBack = onStickerTap
                                        if (takeBack != null) takeBack() else message.stickerUrl?.let(onOpenImage)
                                    },
                                    onLongClick = onLongPress,
                                ),
                        ) {
                            com.addonstation.mobile.ui.components.CoverImage(url = url, radius = 12.dp, modifier = Modifier.fillMaxSize())
                        }
                        // الوقت في نهاية الرسالة (أسفل الملصق على الحافة)
                        MessageStatus(
                            ui = ui,
                            createdAt = message.createdAt,
                            notSentLabel = notSentLabel,
                            retryLabel = retryLabel,
                            onRetry = onRetry,
                            onDiscard = onDiscard,
                        )
                    }
                }
            }
            "image" -> {
                Column(modifier = Modifier.padding(start = 48.dp, top = 4.dp)) {
                    // النص أولاً وتحته الصورة — **رسالة واحدة** كما أُرسلت
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
                        // ⚠️ سبب «أرسلت صورة ولم تظهر»: الصورة كانت تقيس
                        // بـ widthIn(max) بلا حدٍّ أدنى فيصير عرضها **صفراً**.
                        // الآن مقاس ثابت يتبع عرض الشاشة: تظهر دائماً.
                        ChatImageTile(
                            url = imageUrl,
                            localPreview = ui.localPreview,
                            onOpen = {
                                val path = message.imagePath
                                if (imageUrl != null) onOpenImage(imageUrl!!)
                                else if (path != null) onOpenImage(path)
                            },
                            onLongPress = onLongPress,
                            onRetryUrl = { reloadImageUrl() },
                        )
                        // الوقت في نهاية الرسالة (أسفل الصورة على الحافة)
                        MessageStatus(
                            ui = ui,
                            createdAt = message.createdAt,
                            notSentLabel = notSentLabel,
                            retryLabel = retryLabel,
                            onRetry = onRetry,
                            onDiscard = onDiscard,
                        )
                    }
                }
            }
            "video" -> {
                Column(modifier = Modifier.padding(start = 48.dp, top = 4.dp)) {
                    // النص أولاً وتحته الفيديو — **رسالة واحدة** كما أُرسلت
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
                        // بطاقة فيديو كتليجرام: إطار من الفيديو + ▶ + المدّة
                        VideoTile(
                            thumbUrl = videoThumbUrl,
                            localPreview = ui.localPreview,
                            durationSeconds = message.mediaDuration,
                            modifier = Modifier.size(
                                width = com.addonstation.mobile.ui.theme.screenWidthFraction(
                                    fraction = 0.58f, minDp = 150, maxDp = 250,
                                ),
                                height = 168.dp,
                            ),
                            onClick = onPlayVideo,
                            onLongPress = onLongPress,
                        )
                        MessageStatus(
                            ui = ui,
                            createdAt = message.createdAt,
                            notSentLabel = notSentLabel,
                            retryLabel = retryLabel,
                            onRetry = onRetry,
                            onDiscard = onDiscard,
                        )
                    }
                }
            }
            "audio" -> {
                Column(modifier = Modifier.padding(start = 48.dp, top = 4.dp)) {
                    // النص أولاً وتحته شريط الصوت — **رسالة واحدة** كما أُرسلت
                    if (!message.content.isNullOrBlank()) {
                        Text(
                            text = message.content.orEmpty(),
                            style = AppText.chatBody,
                            color = AppColors.Text,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Elevated)
                            // ضغطة مطوّلة ⇒ قائمة الرسالة نفسها (رد · نسخ · إبلاغ · حذف)
                            .combinedClickable(onClick = onReply, onLongClick = onLongPress)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text("🎙️", style = AppText.bodySmall)
                        Column {
                            Text(
                                stringResource(R.string.chat_audio_label),
                                style = AppText.tiny,
                                color = AppColors.Text,
                            )
                            Text(
                                TimeFormat.durationLabel(message.mediaDuration) + " · " + TimeFormat.fileSizeLabel(message.mediaSize),
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                        }
                        MessageStatus(
                            ui = ui,
                            createdAt = message.createdAt,
                            notSentLabel = notSentLabel,
                            retryLabel = retryLabel,
                            onRetry = onRetry,
                            onDiscard = onDiscard,
                        )
                    }
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
                    // الوقت في نهاية السطر (على حافة الفقاعة) — أو حالة الإرسال
                    MessageStatus(
                        ui = ui,
                        createdAt = message.createdAt,
                        notSentLabel = notSentLabel,
                        retryLabel = retryLabel,
                        onRetry = onRetry,
                        onDiscard = onDiscard,
                    )
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

/**
 * حالة الرسالة في مكان الوقت نفسه:
 *  • قيد الإرسال ⇒ 🕓 (الرسالة باهتة حتى يردّ الخادم)؛
 *  • فشلت ⇒ ⚠️ «لم تُرسل» + «إعادة المحاولة» + ✕ (تبقى ظاهرة ولا تختفي)؛
 *  • مؤكَّدة ⇒ الوقت المعتاد.
 *
 * لماذا؟ كانت الرسالة الفاشلة **تُمحى** من القائمة فلا يعرف صاحبها إن كانت
 * وصلت، فيخرج من الغرفة ويعود ليتأكد. الآن حالته أمامه وفي مكانه زرّ إعادة.
 */
@Composable
private fun MessageStatus(
    ui: ChatMessageUi,
    createdAt: String?,
    notSentLabel: String,
    retryLabel: String,
    onRetry: () -> Unit,
    onDiscard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when {
        ui.failed -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("⚠️", style = AppText.tiny)
            Text(notSentLabel, style = AppText.chatTime, color = AppColors.Red)
            Text(
                retryLabel,
                style = AppText.chatTime,
                color = AppColors.Green,
                modifier = Modifier.clickable { onRetry() },
            )
            Text(
                "✕",
                style = AppText.chatTime,
                color = AppColors.Muted,
                modifier = Modifier.clickable { onDiscard() },
            )
        }
        ui.pending -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CircularProgressIndicator(
                color = AppColors.Muted,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(10.dp),
            )
            Text("🕓", style = AppText.tiny)
        }
        else -> MessageTime(createdAt = createdAt, modifier = modifier)
    }
}

/**
 * بطاقة صورة داخل الرسالة: **مقاس ثابت** يتبع عرض الشاشة (فلا ينهار عرضها إلى
 * صفر كما كان) + الضغطة تفتحها بكامل حجمها، والضغطة المطوّلة تفتح قائمتها.
 *
 * @param localPreview ملف الصورة على الجهاز — يُعرض فوراً للرسالة التي ما زالت
 *                     قيد الإرسال، فلا تبقى فارغة حتى يُوقَّع رابط الخادم.
 */
@Composable
private fun ChatImageTile(
    url: String?,
    localPreview: String?,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRetryUrl: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(
                width = com.addonstation.mobile.ui.theme.screenWidthFraction(
                    fraction = 0.58f, minDp = 150, maxDp = 250,
                ),
                height = 168.dp,
            )
            .clip(RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        com.addonstation.mobile.ui.components.CoverImage(
            url = url,
            model = localPreview?.let { java.io.File(it) },
            radius = 14.dp,
            modifier = Modifier.fillMaxSize(),
        )
        // لم يُوقَّع الرابط بعد (أو فشل) ⇒ زرّ إعادة محاولة واضح بدل صورة بيضاء
        if (url.isNullOrBlank() && localPreview == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onRetryUrl() },
                contentAlignment = Alignment.Center,
            ) {
                Text("🖼 " + stringResource(R.string.chat_tap_to_retry), style = AppText.tiny, color = AppColors.Muted)
            }
        }
    }
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

/**
 * itemsIndexed بمفتاح ونوع محتوى (يعمل مع LazyListScope).
 *
 * **المفتاح هو أصل الأداء في القوائم الطويلة** (وهو سبب «اللاق والتقطّع»
 * في الشات): بدونه لا يستطيع Compose أن يعيد استعمال تركيب العناصر، فيعيد
 * تركيب **كل** العناصر الظاهرة عند أي تغيير — رسالة جديدة، تفاعل، تحديث
 * استطلاع — ومعها تنتقل حالات `remember` (مثل روابط الصور الموقَّعة) إلى
 * عناصر أخرى، فتظهر الصور فارغة أو في مكانها خطأ.
 */
private inline fun <T> androidx.compose.foundation.lazy.LazyListScope.itemsIndexedCompat(
    items: List<T>,
    crossinline key: (T) -> Any,
    crossinline contentType: (T) -> Any? = { null },
    crossinline content: @Composable (Int, T) -> Unit,
) {
    items(
        count = items.size,
        key = { index -> key(items[index]) },
        contentType = { index -> contentType(items[index]) },
    ) { index -> content(index, items[index]) }
}
