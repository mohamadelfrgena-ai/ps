@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.addonstation.mobile.ui.chat

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloat
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
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import com.addonstation.mobile.ui.components.SpecPrimaryButton
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.community.isReadOnlyFor
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.RoomIcon
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val vm = remember(channel.id) { ChatViewModel(container) }

    val messages by vm.messages.collectAsState()
    val authors by vm.authors.collectAsState()
    val loading by vm.loading.collectAsState()
    val newBelow by vm.newMessagesBelow.collectAsState()
    val newDividerId by vm.newDividerId.collectAsState()
    /** من حظرتُهم — يُقرأ من القاعدة ويُحدَّث فوراً عند الحظر/الإلغاء في هذه الشاشة. */
    val blockedIds by vm.blockedIds.collectAsState()
    val scrollTick by vm.scrollTick.collectAsState()
    val typing by vm.typing.collectAsState()
    val stickers by vm.stickers.collectAsState()
    val packs by vm.packs.collectAsState()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val chatStatus by container.state.chatStatus.collectAsState()
    // فهرس الأدوات حالة Compose مشتركة: وصول الأصول بعد فتح الغرفة يعيد تركيب
    // الترويسات فوراً، بدلاً من بقاء رمز بديل حتى إغلاق الشات وإعادة فتحه.
    val itemUrlIndex by container.state.itemUrlIndex.collectAsState()
    val itemUrlResolver: (String?) -> String? = remember(container, itemUrlIndex) {
        { value -> com.addonstation.mobile.ui.equippedItemUrl(container, value, itemUrlIndex) }
    }
    val equippedAuthorItems = remember(authors) {
        authors.values
            .map(Profile::equippedItemId)
            .filterNot { it.isBlank() || it.equals("none", ignoreCase = true) }
            .distinct()
    }
    LaunchedEffect(equippedAuthorItems, itemUrlIndex) {
        // قد تُفتح الغرفة قبل اكتمال تحميل مكتبة الأصول. محاولة واحدة مشتركة
        // تكفي لكل الكتّاب، ولا نجري طلباً داخل كل رسالة أو كل إعادة تركيب.
        if (equippedAuthorItems.any { itemUrlResolver(it) == null }) {
            withContext(Dispatchers.IO) { container.assets.all() }
        }
    }
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
    var reactionTargetMessageId by remember { mutableStateOf<Long?>(null) }
    var showAttach by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var memberCardProfile by remember { mutableStateOf<Profile?>(null) }
    var memberCardRefresh by remember { mutableIntStateOf(0) }
    var showPinned by remember { mutableStateOf(false) }
    var actionsFor by remember { mutableStateOf<ChatMessageUi?>(null) }
    var actionAnchor by remember { mutableStateOf<Rect?>(null) }
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
    // تعريف حالتَي العارضين **قبل** معالجات الرجوع: Kotlin يمنع استعمال
    // متغيّر محلّي قبل تعريفه داخل الدالة نفسها.
    var videoViewerUrl by remember { mutableStateOf<String?>(null) }
    BackHandler(enabled = videoViewerUrl != null) { videoViewerUrl = null }
    BackHandler(enabled = mediaSheet) { mediaSheet = false }
    BackHandler(enabled = showAttach) { showAttach = false }
    BackHandler(enabled = showMembers) { showMembers = false }
    BackHandler(enabled = showPinned) { showPinned = false }
    BackHandler(enabled = actionsFor != null) { actionsFor = null; actionAnchor = null }
    BackHandler(enabled = showEmoji) {
        showEmoji = false
        reactionTargetMessageId = null
    }
    BackHandler(enabled = imageViewerUrl != null) { imageViewerUrl = null }

    var statusMessage by remember { mutableStateOf<String?>(null) }
    /** سبب فشل الإرسال (يُترجَم في الشاشة) — لا نعرض رمز الخادم الخام. */
    var statusError by remember { mutableStateOf<ApiErrorKind?>(null) }
    // المثبّتات: من الـViewModel (تُجلب عند الدخول ليظهر الشريط الذهبي فوراً)
    val pinnedMessages by vm.pinned.collectAsState()
    val online by vm.online.collectAsState()
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
    val blockedMsg = stringResource(R.string.block_done)
    val unblockedMsg = stringResource(R.string.unblock_done)
    val blockFailedMsg = stringResource(R.string.block_failed)
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
        // شريط المثبّتات (§٤٫٢): يُجلب مع الدخول لا عند الضغط فقط
        vm.loadPinned(channel.id)
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
    val farFromBottom by remember(listState, messages.size) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            val lastVisible = visible.lastOrNull()?.index ?: messages.lastIndex
            messages.isNotEmpty() && messages.lastIndex - lastVisible > visible.size.coerceAtLeast(1)
        }
    }
    val unreadBelowCount = remember(messages, newDividerId, newBelow) {
        val dividerIndex = newDividerId?.let { id -> messages.indexOfFirst { it.message.id == id } } ?: -1
        when {
            dividerIndex >= 0 -> (messages.size - dividerIndex).coerceAtLeast(1)
            newBelow -> 1
            else -> 0
        }
    }

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
        showEmoji = false
        reactionTargetMessageId = null
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
    /**
     * معرّف المستخدم الحالي **مرة واحدة لكل تركيبة شاشة** (لا داخل كل عنصر
     * رسالة كما كان): قراءة الجلسة كانت تُنادى لكل رسالة في كل إطار.
     */
    val myId = container.supabase.session()?.userId
    /**
     * محلّل روابط الملصقات بمرجع **ثابت** (remember): لامبدا جديدة في كل
     * تركيبة تمنع Compose من تخطّي إعادة تركيب عناصر الرسائل ⇒ ترميم كل
     * العناصر المرئية مع كل تحديث.
     */
    val stickerUrlResolver = remember(container) { { path: String -> container.siteAssetUrl(path) } }
    val roomLocked = channel.isLocked
    val canPost = signedIn && !isMuted && !isBanned && !roomLocked

    BoxWithConstraints(Modifier.fillMaxSize()) {
    Column(
        Modifier
            .fillMaxSize()
            // خلفية الشاشة الداكنة (§١: bg = #0A0F1C)
            .background(Spec.bg)
            // الأسفل = الأكبر بين شريط التنقّل ولوحة المفاتيح: يبقى مربع الإرسال فوق
            // شريط النظام حين تُغلق اللوحة، وفوق اللوحة حين تُفتح — بلا تداخل ولا فراغ.
            .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
    ) {
        ChatHeader(
            channel = channel,
            onBack = onBack,
            onMembers = { showMembers = true },
            onPinned = { showPinned = true },
            onSettings = onOpenSettings,
            online = online,
            pinnedCount = pinnedMessages.size,
            // ترس الإدارة للمشرف/الأدمن فقط (زائر ومستخدم عادي: الوثيقة تعرض زرّين)
            showSettings = canModerateRoom,
        )

        // ── شريط المثبّتات (§٤٫٢): يظهر فقط حين يوجد شيء مثبَّت
        pinnedMessages.firstOrNull()?.let { pinned ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Spec.goldDeepBg)
                    .clickable { showPinned = true }
                    .padding(horizontal = Spec.screenPad, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.chat_pinned_strip, pinnedPreviewText(pinned)),
                    style = Spec.msgQuote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                    color = Spec.gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // حدّ سفلي رقيق بلون `pinnedBorder` (فاصل الشريط عن الرسائل)
            Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.pinnedBorder))
        }

        Box(Modifier.weight(1f)) {
            when {
                loading -> ChatMessagesLoadingSkeleton()
                messages.isEmpty() -> ChatMessagesEmptyState(stringResource(R.string.chat_no_messages))
                else -> LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    // حشو القائمة 14 رأسياً / 12 أفقياً (§٤٫٣)
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 14.dp),
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
                            // مقارنة مفتاح اليوم (مُخزَّن في العنصر) بدل حساب
                            // نصّ التاريخ مرّتين لكل رسالة — انظر Models.dayKey.
                            showDate = previous == null || previous.dayKey != ui.dayKey,
                            isNewDivider = newDividerId == ui.message.id,
                            myId = myId,
                            currentProfile = profile,
                            onLongPress = { bounds ->
                                actionsFor = ui
                                actionAnchor = bounds.takeIf { it.width > 0f && it.height > 0f }
                            },
                            onReply = { replyTo = ui.message },
                            onOpenImage = openFullSize,
                            onStickerTap = {
                                // ضغطة سريعة على **أي** ملصق (لي أو لغيري) ⇒ لوحة الملصقات
                                // تُفتح على مكانه وهو محدَّد — بلا حذف ولا فتح بالحجم الكامل.
                                // (الحذف يبقى من قائمة الضغط المطوّل خلال ٣ دقائق.)
                                ui.message.stickerUrl?.takeIf { it.isNotBlank() }?.let { sticker ->
                                    resentSticker = sticker
                                    reactionTargetMessageId = null
                                    keyboardController?.hide()
                                    focusManager.clearFocus(force = true)
                                    showEmoji = true
                                }
                            },
                            onToggleReaction = { emoji -> vm.toggleReaction(ui.message.id, emoji) },
                            onOpenUser = { userId ->
                                memberCardProfile = authors[userId] ?: profile?.takeIf { it.id == userId }
                            },
                            stickerUrlResolver = stickerUrlResolver,
                            equippedItemUrlResolver = itemUrlResolver,
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

            if (farFromBottom || newBelow) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 12.dp, bottom = 12.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .shadow(6.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.35f), spotColor = Color.Black.copy(alpha = 0.35f))
                            .clip(CircleShape)
                            .background(Spec.surface)
                            .border(1.dp, Spec.border, CircleShape)
                            .clickable {
                                vm.markReadUpToBottom()
                                scope.launch { messages.lastOrNull()?.let { listState.animateScrollToItem(messages.lastIndex) } }
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.cd_scroll_to_bottom),
                            tint = Spec.text,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    if (unreadBelowCount > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 6.dp, y = (-6).dp)
                                .defaultMinSize(minWidth = 20.dp, minHeight = 20.dp)
                                .clip(CircleShape)
                                .background(Spec.blue)
                                .border(2.dp, Spec.bg, CircleShape)
                                .padding(horizontal = 5.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                if (unreadBelowCount > 99) "99+" else unreadBelowCount.toString(),
                                style = Spec.badge.copy(fontSize = 10.sp),
                                color = Color.White,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }

        typing.firstOrNull()?.let { name ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Spec.barSurface)
                    .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ChatTypingDots()
                Text(
                    stringResource(R.string.chat_typing, name),
                    style = Spec.msgQuote,
                    color = Spec.textMuted,
                )
            }
        }

        replyTo?.let { original ->
            val replyPreview = when {
                original.status == "deleted" -> stringResource(R.string.chat_delete_done)
                !original.content.isNullOrBlank() -> original.content.orEmpty().replace('\n', ' ')
                original.messageType == "image" -> stringResource(R.string.chat_attach_photo)
                original.messageType == "sticker" -> stringResource(R.string.chat_stickers)
                original.messageType == "video" -> stringResource(R.string.chat_video_label)
                original.messageType == "audio" -> stringResource(R.string.chat_audio_label)
                else -> stringResource(R.string.chat_announcement)
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Spec.surface)
                    .border(1.dp, Spec.border, RoundedCornerShape(10.dp))
                    .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f).padding(vertical = 2.dp)) {
                    Text(
                        stringResource(
                            R.string.chat_replying_to,
                            authors[original.userId]?.username ?: "",
                        ),
                        style = Spec.msgQuote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                        color = Spec.blueLink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        replyPreview,
                        style = Spec.msgTime,
                        color = Spec.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // زرّ الإغلاق 44dp (هدف لمس كامل)
                Box(
                    modifier = Modifier
                        .size(Spec.touch)
                        .clip(CircleShape)
                        .clickable { replyTo = null },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, tint = Spec.textMuted, modifier = Modifier.size(18.dp))
                }
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
                    .background(Spec.barSurface)
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = when {
                        !signedIn -> stringResource(R.string.msg_sign_in_to_participate)
                        isBanned -> stringResource(R.string.chat_banned)
                        isMuted -> stringResource(R.string.chat_muted_until, TimeFormat.countdown(chatStatus?.mutedUntil))
                        else -> stringResource(R.string.comm_locked)
                    },
                    style = Spec.secondary,
                    color = Spec.textMuted,
                    modifier = Modifier.weight(1f),
                )
                if (!signedIn) {
                    SpecPrimaryButton(
                        text = stringResource(R.string.auth_sign_in),
                        onClick = onLogin,
                    )
                }
            }
        } else if (readOnlyRoom) {
            // لا حقل كتابة ولا زر إرسال: قراءة فقط، والإدارة وحدها تنشر هنا
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Spec.barSurface)
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("🔒", style = Spec.msgBody)
                Text(
                    stringResource(R.string.chat_read_only),
                    style = Spec.secondary,
                    color = Spec.textMuted,
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
                onAttach = {
                    showEmoji = false
                    reactionTargetMessageId = null
                    showAttach = true
                },
                panelOpen = showEmoji,
                focusRequester = focusRequester,
                onInputFocused = {
                    showEmoji = false
                    reactionTargetMessageId = null
                },
                onEmoji = {
                    reactionTargetMessageId = null
                    if (showEmoji) {
                        showEmoji = false
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    } else {
                        keyboardController?.hide()
                        focusManager.clearFocus(force = true)
                        showEmoji = true
                    }
                },
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
        // The picker occupies the IME area below the composer; messages resize instead of being covered.
        if (showEmoji) {
            EmojiStickerPanel(
                stickers = stickers,
                packs = packs,
                resolvedStickerUrl = { container.siteAssetUrl(it) },
                onEmoji = { emoji ->
                    val reactionTarget = reactionTargetMessageId
                    reactionTargetMessageId = null
                    resentSticker = null
                    showEmoji = false
                    if (reactionTarget != null) {
                        vm.toggleReaction(reactionTarget, emoji)
                    } else {
                        text += emoji
                        if (canPost && !readOnlyRoom) {
                            focusRequester.requestFocus()
                            keyboardController?.show()
                        }
                    }
                },
                focusSticker = resentSticker,
                showStickerTab = reactionTargetMessageId == null,
                onSticker = { stickerUrl ->
                    // اختيار الملصق لا يُرسله فوراً: يوضعه في مربع الإرسال (معاينة + ✕ أحمر)
                    // فيستطيع صاحبه أن يكتب نصاً تحته إن أراد، ثم يضغط إرسال.
                    pendingSticker = stickerUrl
                    resentSticker = null
                    reactionTargetMessageId = null
                    showEmoji = false
                    if (canPost && !readOnlyRoom) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    }
                },
            )
        }

    }

    actionsFor?.let { ui ->
        // مدة الحذف: ٣ دقائق من وقت الإرسال — يُخفى الخيار بعدها ويُعلَّل السبب.
        val deleteLeftMinutes = deleteWindowRemainingMinutes(ui.message.createdAt)
        MessageActionsSheet(
            ui = ui,
            anchorBounds = actionAnchor,
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
                vm.moderate(ui.message.id, hide = ui.message.status != "hidden")
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
            isBlocked = ui.message.userId in blockedIds,
            onBlockUser = {
                vm.block(ui.message.userId) { ok -> statusMessage = if (ok) blockedMsg else blockFailedMsg }
                actionsFor = null
            },
            onUnblockUser = {
                vm.unblock(ui.message.userId) { ok -> statusMessage = if (ok) unblockedMsg else null }
                actionsFor = null
            },
            onOpenProfile = {
                memberCardProfile = authors[ui.message.userId]
                actionsFor = null
            },
            onReact = { emoji ->
                vm.toggleReaction(ui.message.id, emoji)
                actionsFor = null
            },
            onMoreReactions = {
                reactionTargetMessageId = ui.message.id
                resentSticker = null
                keyboardController?.hide()
                focusManager.clearFocus(force = true)
                showEmoji = true
                actionsFor = null
            },
        )
    }
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
                memberCardProfile = p
            },
        )
    }

    if (showPinned) {
        PinnedSheet(
            pinned = pinnedMessages,
            onDismiss = { showPinned = false },
        )
    }

    memberCardProfile?.let { member ->
        com.addonstation.mobile.ui.chat.MemberCardHost(
            profile = member,
            onDismiss = { memberCardProfile = null },
            onOpenProfile = {
                memberCardProfile = null
                onOpenUser(member)
            },
            onEditProfile = {
                memberCardProfile = null
                onOpenUser(member)
            },
            refreshToken = memberCardRefresh,
            onAddFriend = {
                container.profile.sendFriendRequest(member.id).also { result ->
                    if (result.isSuccess) memberCardRefresh += 1
                }
            },
            // There is no private-message route or backend in this app yet. Never redirect to a public room.
            showMessage = false,
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
private fun ChatMessagesLoadingSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "chat-message-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.28f,
        targetValue = 0.82f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1100),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "chat-message-skeleton-alpha",
    )
    val shimmer = Spec.surfaceHigh.copy(alpha = alpha)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        repeat(4) { index ->
            val outgoing = index % 3 == 2
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = if (outgoing) Arrangement.End else Arrangement.Start,
            ) {
                if (!outgoing) {
                    Box(Modifier.size(36.dp).clip(CircleShape).background(shimmer))
                    Spacer(Modifier.size(8.dp))
                }
                Column(
                    Modifier
                        .fillMaxWidth(if (outgoing) 0.72f else 0.82f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Spec.surface)
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(Modifier.fillMaxWidth(0.34f).height(12.dp).clip(CircleShape).background(shimmer))
                    Box(Modifier.fillMaxWidth(if (index % 2 == 0) 0.88f else 0.64f).height(36.dp).clip(RoundedCornerShape(12.dp)).background(shimmer))
                }
            }
        }
        Text(
            stringResource(R.string.chat_loading_messages),
            style = Spec.secondary,
            color = Spec.textMuted,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ChatMessagesEmptyState(message: String) {
    Column(
        modifier = Modifier.fillMaxSize().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Spec.surface)
                .border(1.dp, Spec.border, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text("💬", style = Spec.chatName, color = Spec.blueLink)
        }
        Spacer(Modifier.height(14.dp))
        Text(
            message,
            style = Spec.msgQuote,
            color = Spec.textMuted,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun NewMessagesDivider() {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.weight(1f).height(1.dp).background(Spec.blue))
        Text(
            stringResource(R.string.chat_new_tag),
            style = Spec.badge.copy(letterSpacing = 0.8.sp),
            color = Spec.blueLink,
            maxLines = 1,
        )
        Box(Modifier.weight(1f).height(1.dp).background(Spec.blue))
    }
}


@Composable
private fun ChatTypingDots() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "chat-typing")
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            val alpha by transition.animateFloat(
                initialValue = 0.28f,
                targetValue = 1f,
                animationSpec = androidx.compose.animation.core.infiniteRepeatable(
                    animation = androidx.compose.animation.core.tween(650, delayMillis = index * 150),
                    repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
                ),
                label = "chat-typing-dot-$index",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(alpha)
                    .clip(CircleShape)
                    .background(Spec.blueLink),
            )
        }
    }
}


@Composable
private fun AudioMessageTile(
    mediaPath: String?,
    durationSeconds: Int?,
    sizeBytes: Long?,
    isMine: Boolean,
    onReply: () -> Unit,
    onLongPress: () -> Unit,
) {
    val context = LocalContext.current
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var player by remember(mediaPath) { mutableStateOf<ExoPlayer?>(null) }
    var loading by remember(mediaPath) { mutableStateOf(false) }
    var playing by remember(mediaPath) { mutableStateOf(false) }
    val activePlayer = player

    DisposableEffect(activePlayer) {
        if (activePlayer == null) {
            onDispose { }
        } else {
            val listener = object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    playing = isPlaying
                }

                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        activePlayer.seekTo(0)
                        playing = false
                    }
                }

                override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                    playing = false
                }
            }
            activePlayer.addListener(listener)
            onDispose {
                activePlayer.removeListener(listener)
                runCatching { activePlayer.stop() }
                activePlayer.release()
            }
        }
    }

    fun togglePlayback() {
        val current = player
        if (current != null) {
            if (current.isPlaying) current.pause()
            else {
                if (current.playbackState == Player.STATE_ENDED) current.seekTo(0)
                current.play()
            }
            return
        }
        if (loading || mediaPath.isNullOrBlank()) return
        loading = true
        scope.launch {
            val url = runCatching { container.chat.mediaUrl(mediaPath) }.getOrNull()
            if (url != null) {
                val created = ExoPlayer.Builder(context).build().apply {
                    setMediaItem(MediaItem.fromUri(url))
                    prepare()
                    playWhenReady = true
                }
                player = created
                playing = true
            }
            loading = false
        }
    }

    val foreground = if (isMine) Color.White else Spec.text
    val muted = if (isMine) Color.White.copy(alpha = 0.78f) else Spec.textMuted
    val background = if (isMine) Spec.blue else Spec.surface
    Row(
        modifier = Modifier
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(if (isMine) 16.dp else 14.dp))
            .background(background)
            .combinedClickable(onClick = onReply, onLongClick = onLongPress)
            .padding(horizontal = 10.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (isMine) Color.White else Spec.blue)
                .clickable(enabled = mediaPath?.isNotBlank() == true) { togglePlayback() },
            contentAlignment = Alignment.Center,
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(19.dp),
                    color = if (isMine) Spec.blue else Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(
                    imageVector = if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = stringResource(R.string.chat_audio_label),
                    tint = if (isMine) Spec.blue else Color.White,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                stringResource(R.string.chat_audio_label),
                style = Spec.msgTime.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = foreground,
                maxLines = 1,
            )
            val heights = listOf(10, 16, 23, 14, 27, 19, 11, 25, 15, 20, 28, 12, 18, 24, 14, 21)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                heights.forEachIndexed { index, height ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(height.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                if (playing && index < heights.size / 2) foreground
                                else (if (isMine) foreground else Spec.blueLink).copy(alpha = if (isMine) 0.72f else 0.84f),
                            ),
                    )
                }
            }
            Text(
                TimeFormat.durationLabel(durationSeconds) + " · " + TimeFormat.fileSizeLabel(sizeBytes),
                style = Spec.msgTime,
                color = muted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun ChatHeader(
    channel: ChatChannel,
    onBack: () -> Unit,
    onMembers: () -> Unit,
    onPinned: () -> Unit,
    onSettings: () -> Unit,
    online: Int = 0,
    pinnedCount: Int = 0,
    showSettings: Boolean = false,
) {
    // ارتفاع ≥ 64 كما تنصّ الوثيقة، وحدّ سفلي 1dp بلون `border`
    Column(modifier = Modifier.fillMaxWidth().background(Spec.barSurface)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // زرّ الرجوع 44×44 (هدف لمس كامل)
            Box(
                modifier = Modifier
                    .size(Spec.touch)
                    .clip(CircleShape)
                    .background(Spec.surface)
                    .border(1.dp, Spec.border, CircleShape)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = Spec.text,
                    modifier = Modifier.size(22.dp),
                )
            }
            // Room avatar 40dp with the design-system outline.
            Box(
                Modifier.size(40.dp).clip(CircleShape).border(2.dp, Spec.border, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                RoomIcon(channelId = channel.id, size = 36.dp)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    // اسم غرفة اللغة بلغتها نفسها (العربية · Русский · 中文 …)
                    com.addonstation.mobile.ui.community.channelDisplayName(channel),
                    style = Spec.chatName,
                    color = Spec.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    // «[N] online» أخضر — أو السطر الهادئ حين لا أحد في الغرفة
                    text = if (online > 0) stringResource(R.string.chat_online, online)
                    else stringResource(R.string.chat_nobody_online),
                    style = Spec.chatOnline,
                    color = if (online > 0) Spec.green else Spec.textMuted,
                    maxLines = 1,
                )
            }
            // Pinned action is shown only when the room has pinned messages.
            if (pinnedCount > 0) {
                HeaderIcon(
                    icon = Icons.Filled.PushPin,
                    description = stringResource(R.string.chat_pinned_plain) + " · " + pinnedCount,
                    onClick = onPinned,
                    tint = Spec.gold,
                    badge = pinnedCount,
                )
            }
            HeaderIcon(
                icon = Icons.Filled.Group,
                description = stringResource(R.string.chat_members),
                onClick = onMembers,
            )
            if (showSettings) {
                HeaderIcon(
                    icon = Icons.Filled.Settings,
                    description = stringResource(R.string.cd_room_settings),
                    onClick = onSettings,
                )
            }
        }
        // الحدّ السفلي للشريط
        Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.border))
    }
}

/**
 * لون اسم صاحب الرسالة (وثيقة الشات §٤٫٣):
 *   • الأدمن ⇒ ذهبي (وبجانبه شارة ADMIN) · المشرف والمبدع ⇒ أخضر ·
 *   • Legendary ⇒ ذهبي · Epic ⇒ بنفسجي؛
 *   • وأي عضو آخر: لون **ثابت له** من أربعة ألوان — يُشتقّ من معرّفه، فلا يتغيّر
 *     بين رسالة وأخرى (نفس العضو = نفس اللون دائماً).
 */
private fun roleColorOf(role: String?, plan: String?, userId: String): Color = when {
    role == "admin" -> Spec.gold
    role == "head_moderator" -> Spec.epic
    role == "moderator" -> Spec.green
    role == "creator" -> Spec.blueLink
    plan == "legendary" -> Spec.gold
    plan == "epic" -> Spec.epic
    else -> Spec.nameColors[Math.floorMod(userId.hashCode(), Spec.nameColors.size)]
}

/**
 * نصّ مختصر للرسالة المثبّتة (شريط §٤٫٢): نصّها، وإن كانت ملصقاً أو صورة فرمز
 * بدل فراغ مقلق — منعاً لشريط فارغ بعد «Pinned messages ·».
 */
private fun pinnedPreviewText(message: ChatMessage): String {
    val body = message.content
    if (!body.isNullOrBlank()) return body.replace('\n', ' ')
    return when (message.messageType) {
        "sticker" -> "🖼"
        "image" -> "🖼"
        "video" -> "🎬"
        "audio" -> "🎙️"
        else -> "—"
    }
}

@Composable
private fun HeaderIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    tint: Color = Spec.text,
    badge: Int = 0,
) {
    Box(
        modifier = Modifier
            .size(Spec.touch)
            .clip(CircleShape)
            .background(Spec.surface)
            .border(1.dp, Spec.border, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(22.dp))
        if (badge > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 2.dp, y = (-2).dp)
                    .sizeIn(minWidth = 16.dp, minHeight = 16.dp)
                    .clip(CircleShape)
                    .background(Spec.goldFill)
                    .border(2.dp, Spec.barSurface, CircleShape)
                    .padding(horizontal = 3.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (badge > 99) "99+" else badge.toString(),
                    style = Spec.badge,
                    color = Spec.goldInk,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * شريط الكتابة (§٤٫٤ من الوثيقة): خلفية `barSurface` بحدّ علوي 1dp، وحشو 8/8/18،
 * عناصره مُوسَّطة: زرّ صورة (44 دائري) · زرّ ملصقات (44 دائري) · حقل 44 بنصف قطر 22
 * · زرّ إرسال أزرق (44 دائري). كل الأزرار أهداف لمس ≥44dp.
 */
@Composable
private fun ChatInputBar(
    channel: ChatChannel,
    text: String,
    onTextChange: (String) -> Unit,
    onAttach: () -> Unit,
    onEmoji: () -> Unit,
    panelOpen: Boolean,
    focusRequester: FocusRequester,
    onInputFocused: () -> Unit,
    onSend: () -> Unit,
    remaining: Int,
    mentionSuggestions: List<String>,
    onMention: (String) -> Unit,
    slowmodeSeconds: Int,
    pendingStickerUrl: String? = null,
    onRemoveSticker: () -> Unit = {},
    canSend: Boolean = text.isNotBlank(),
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Spec.barSurface),
    ) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Spec.border))
        Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 14.dp)) {
            if (pendingStickerUrl != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(Spec.surface)
                        .border(1.dp, Spec.border, RoundedCornerShape(14.dp))
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PendingStickerPreview(url = pendingStickerUrl, onRemove = onRemoveSticker)
                    Text(
                        stringResource(R.string.chat_sticker_ready),
                        style = Spec.secondary,
                        color = Spec.textMuted,
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
                        .clip(RoundedCornerShape(12.dp))
                        .background(Spec.surface)
                        .border(1.dp, Spec.border, RoundedCornerShape(12.dp)),
                ) {
                    items(mentionSuggestions.size) { index ->
                        val name = mentionSuggestions[index]
                        Text(
                            "@$name",
                            style = Spec.secondary,
                            color = Spec.blueLink,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onMention(name) }
                                .padding(horizontal = 12.dp, vertical = 12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(Spec.pillRadius))
                        .background(Spec.surface)
                        .border(1.dp, Spec.border, RoundedCornerShape(Spec.pillRadius))
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (panelOpen) Spec.blue.copy(alpha = 0.18f) else Color.Transparent)
                            .clickable { onEmoji() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            if (panelOpen) Icons.Filled.Keyboard else Icons.Filled.EmojiEmotions,
                            contentDescription = stringResource(
                                if (panelOpen) R.string.cd_emoji_toggle_keyboard else R.string.cd_emoji_toggle_open,
                            ),
                            tint = if (panelOpen) Spec.blueLink else Spec.textMuted,
                            modifier = Modifier.size(21.dp),
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (text.isEmpty()) {
                            Text(
                                stringResource(
                                    R.string.chat_hint,
                                    com.addonstation.mobile.ui.community.channelDisplayName(channel),
                                ),
                                style = Spec.msgBody.copy(fontSize = 15.sp),
                                color = Spec.textMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        BasicTextField(
                            value = text,
                            onValueChange = { if (it.length <= 500) onTextChange(it) },
                            singleLine = true,
                            textStyle = Spec.msgBody.copy(fontSize = 15.sp, lineHeight = 22.sp, color = Spec.text),
                            cursorBrush = SolidColor(Spec.blue),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { if (canSend) onSend() }),
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onFocusChanged { if (it.isFocused) onInputFocused() },
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .clickable { onAttach() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.chat_add_photo),
                            tint = Spec.textMuted,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .then(if (canSend) Modifier.shadow(6.dp, CircleShape) else Modifier)
                        .clip(CircleShape)
                        .background(
                            if (canSend) Brush.linearGradient(listOf(Spec.blue, Color(0xFF4C9AFF)))
                            else Brush.linearGradient(listOf(Spec.surfaceHigh, Spec.surfaceHigh)),
                        )
                        .clickable(enabled = canSend) { onSend() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Send,
                        contentDescription = stringResource(R.string.chat_send),
                        tint = if (canSend) Color.White else Spec.textMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            val hintLine = buildString {
                if (remaining in 1..49) append(remaining)
                if (slowmodeSeconds > 0) {
                    if (isNotEmpty()) append(" · ")
                    append(stringResource(R.string.chat_slowmode, slowmodeSeconds))
                }
            }
            if (hintLine.isNotEmpty()) {
                Text(
                    hintLine,
                    style = Spec.secondary,
                    color = if (remaining in 1..9) Spec.gold else Spec.textMuted,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun SpecComposerCircle(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    onClick: () -> Unit,
    selected: Boolean = false,
) {
    val shape = CircleShape
    Box(
        modifier = Modifier
            .size(Spec.touch)
            .clip(shape)
            .background(if (selected) Spec.blue.copy(alpha = 0.18f) else Spec.surface)
            .border(if (selected) 1.dp else 0.dp, if (selected) Spec.blue.copy(alpha = 0.45f) else Color.Transparent, shape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = if (selected) Spec.blueLink else Spec.text, modifier = Modifier.size(22.dp))
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
                        color = AppColors.AuthBlue,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(uploadingLabel, style = AppText.tiny, color = AppColors.AuthBlue)
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
    currentProfile: Profile?,
    onLongPress: (Rect) -> Unit,
    /** ضغطة بسيطة على الرسالة ⇒ الرد عليها مباشرة (الضغط المطوّل = قائمة الإبلاغ/الحذف). */
    onReply: () -> Unit,
    /** ضغطة سريعة على أي ملصق ⇒ لوحة الملصقات تُفتح على مكانه وهو محدَّد. */
    onStickerTap: (() -> Unit)? = null,
    onToggleReaction: (String) -> Unit,
    onOpenImage: (String) -> Unit,
    onOpenUser: (String) -> Unit,
    stickerUrlResolver: (String) -> String?,
    equippedItemUrlResolver: (String?) -> String?,
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
    var messageBounds by remember(message.id) { mutableStateOf(Rect.Zero) }
    val haptic = LocalHapticFeedback.current
    val openActions: () -> Unit = {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        onLongPress(messageBounds)
    }
    val isMine = myId != null && message.userId == myId
    // رسائلي تستخدم ملف الجلسة المحلي إن لم تكن نسخة المرسل موجودة في قائمة المؤلفين.
    val author = ui.author ?: currentProfile?.takeIf { isMine && it.id == myId }
    /** فقاعة الرسالة الصادرة تبقى زرقاء ومحاذاة النهاية؛ الترويسة تُعرض أعلاها. */
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
    // Incoming groups get one sender header; own messages stay anonymous inside their own bubble.
    val showAuthorHeader = !isMine && !grouped

    if (showDate) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                TimeFormat.dayLabel(
                    message.createdAt, Locale.getDefault(),
                    stringResource(R.string.chat_today), stringResource(R.string.chat_yesterday),
                ),
                style = Spec.msgQuote.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                color = Spec.textMuted,
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(Spec.surfaceHigh)
                    .padding(horizontal = 14.dp, vertical = 5.dp),
            )
        }
    }

    if (isNewDivider) NewMessagesDivider()

    if (message.messageType == "system") {
        Text(
            message.content.orEmpty(),
            style = Spec.msgTime,
            color = Spec.textMuted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    val mentionsMe = myId != null && message.mentions.contains(myId)
    // التفاعلات: كبسولات بلا إطار تلتصق بالحافة السفلية للرسالة — نصفها داخل الفقاعة ونصفها خارجها.
    // الصفّ يُرسم فوق الفقاعة داخل نفس الـBox، فتبقى الضغطة على أي جزء منه تقلّب التفاعل.
    val showReactions = ui.reactions.isNotEmpty() && message.status != "hidden" && message.status != "deleted"
    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (grouped) 3.dp else 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    when {
                        message.isAnnouncement -> AppColors.AnnouncementBg
                        mentionsMe -> AppColors.MentionTint
                        else -> Color.Transparent
                    }
                )
                .onGloballyPositioned { messageBounds = it.boundsInRoot() }
                .combinedClickable(onClick = onReply, onLongClick = openActions)
                .alpha(if (ui.pending) 0.55f else 1f)      // قيد الإرسال — تظهر فوراً
                .padding(start = 8.dp, end = 8.dp, top = 2.dp, bottom = if (showReactions) 13.dp else 2.dp),
            horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
        ) messageBody@{
            if (message.isAnnouncement) {
                Text(
                    "📢 " + stringResource(R.string.chat_announcement),
                    style = AppText.tiny,
                    color = AppColors.Gold,
                )
            }

            if (showAuthorHeader) {
                Row(
                    modifier = Modifier.widthIn(max = 300.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    com.addonstation.mobile.ui.components.AvatarImage(
                        url = assetUrl(container, author?.avatarUrl),
                        size = 40.dp,
                        online = author?.showOnlineStatus == true,
                        modifier = Modifier.clickable { onOpenUser(message.userId) },
                    )
                    Column(modifier = Modifier.weight(1f, fill = false)) {
                        Text(
                            text = author?.displayName?.takeIf(String::isNotBlank)
                                ?: author?.username?.takeIf(String::isNotBlank)
                                ?: "—",
                            style = Spec.msgAuthor,
                            color = roleColorOf(author?.role, author?.effectivePlan, message.userId),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (author != null && hasRankTitle(author.role, author.effectivePlan)) {
                            RankBadge(role = author.role, plan = author.effectivePlan)
                        }
                    }
                    val equippedItem = author?.equippedItemId
                        ?.takeUnless { it.isBlank() || it.equals("none", ignoreCase = true) }
                    if (equippedItem != null) {
                        val itemImg = equippedItemUrlResolver(equippedItem)
                        if (itemImg != null) {
                            com.addonstation.mobile.ui.components.CoverImage(
                                url = itemImg,
                                radius = 8.dp,
                                modifier = Modifier.size(32.dp),
                                animate = true,
                                backgroundColor = Color.Transparent,
                            )
                        } else {
                            // لا نعرض إيموجي ميدالية لا علاقة له بالأداة. إذا تعذّر
                            // رابط الصورة بعد إعادة تحميل الأصول، يبقى اسم الأداة
                            // الحقيقي ظاهراً بدلاً من معلومة مضللة.
                            Text(
                                text = equippedItem,
                                style = AppText.tiny,
                                color = AppColors.Muted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .widthIn(max = 82.dp)
                                    .clip(RoundedCornerShape(7.dp))
                                    .background(AppColors.Elevated)
                                    .padding(horizontal = 6.dp, vertical = 3.dp),
                            )
                        }
                    }
                }
            }

            ui.replyTo?.let { original ->
                // الاقتباس (§٤٫٣): خلفية `surface` · شريط أزرق 3dp يساراً · نصف قطر 6
                Row(
                    modifier = Modifier
                        .padding(start = if (isMine) 0.dp else 48.dp, top = 4.dp)
                        .fillMaxWidth(0.92f)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Spec.surface),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(width = 3.dp, height = 30.dp).background(Spec.blue))
                    Text(
                        // مقتبس بلا نص (ملصق/صورة) ⇒ رمز بدل فراغ
                        stringResource(R.string.chat_replying_to, ui.replyAuthorName ?: "") + " · " +
                            (if (original.content.isNullOrBlank() &&
                                (original.stickerUrl != null || original.imagePath != null)
                            ) "🖼 " else "") +
                            original.content.orEmpty().replace('\n', ' ').take(60),
                        style = Spec.msgQuote,
                        color = Spec.textMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    )
                }
            }

            // رسالة محذوفة أو مخفية: سطر واحد فقط لكل الأنواع (نص/ملصق/وسائط/صورة)
            if (message.status == "hidden" || message.status == "deleted") {
                Row(
                    modifier = Modifier
                        .padding(start = if (isMine) 0.dp else 48.dp, top = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Spec.surfaceHigh)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.VisibilityOff,
                        contentDescription = null,
                        tint = Spec.textMuted,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        stringResource(R.string.chat_hidden_message_plain),
                        style = Spec.secondary.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                        color = Spec.textMuted,
                    )
                }
                return@messageBody
            }

            when (message.messageType) {
                "sticker" -> {
                    val url = stickerUrlResolver(message.stickerUrl ?: "")
                    // الملصق: 96×96 بلا فقاعة (وثيقة §٤٫٣) — يميناً إن كانت رسالتي
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (isMine) 0.dp else 48.dp, top = 4.dp),
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                    ) {
                        // النص أولاً، وتحته مباشرة الملصق — رسالة واحدة كما يراها صاحبها
                        if (!message.content.isNullOrBlank()) {
                            Text(
                                text = message.content.orEmpty(),
                                style = Spec.msgBody,
                                color = Spec.text,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(Spec.stickerSize)
                                    // ضغطة مطوّلة ⇒ قائمة الرسالة نفسها (رد · نسخ · إبلاغ · حذف)
                                    .combinedClickable(
                                        onClick = {
                                            // ضغطة سريعة = لوحة الملصقات على مكان هذا الملصق (بلا حذف)
                                            val takeBack = onStickerTap
                                            if (takeBack != null) takeBack() else message.stickerUrl?.let(onOpenImage)
                                        },
                                        onLongClick = openActions,
                                    ),
                            ) {
                                com.addonstation.mobile.ui.components.CoverImage(
                                    url = url,
                                    radius = 8.dp,
                                    modifier = Modifier.fillMaxSize(),
                                )
                            }
                            // الوقت أسفل الملصق — لرسالتي فقط (الوارد وقته في سطر الرأس)
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (isMine) 0.dp else 48.dp, top = 4.dp),
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                    ) {
                        // النص أولاً وتحته الصورة — **رسالة واحدة** كما أُرسلت
                        if (!message.content.isNullOrBlank()) {
                            Text(
                                text = message.content.orEmpty(),
                                style = Spec.msgBody,
                                color = Spec.text,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            ChatImageTile(
                                url = imageUrl,
                                localPreview = ui.localPreview,
                                isMine = isMine,
                                onOpen = {
                                    val path = message.imagePath
                                    if (imageUrl != null) onOpenImage(imageUrl!!)
                                    else if (path != null) onOpenImage(path)
                                },
                                onLongPress = openActions,
                                onRetryUrl = { reloadImageUrl() },
                                statusContent = if (isMine) {
                                    {
                                        MessageStatus(
                                            ui = ui,
                                            createdAt = message.createdAt,
                                            notSentLabel = notSentLabel,
                                            retryLabel = retryLabel,
                                            onRetry = onRetry,
                                            onDiscard = onDiscard,
                                            isMine = true,
                                        )
                                    }
                                } else null,
                            )
                            if (!isMine) {
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
                }
                "video" -> {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (isMine) 0.dp else 48.dp, top = 4.dp),
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                    ) {
                        if (!message.content.isNullOrBlank()) {
                            Text(
                                text = message.content.orEmpty(),
                                style = Spec.msgBody,
                                color = Spec.text,
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
                                onLongPress = openActions,
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
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = if (isMine) 0.dp else 48.dp, top = 4.dp),
                        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start,
                    ) {
                        if (!message.content.isNullOrBlank()) {
                            Text(
                                text = message.content.orEmpty(),
                                style = Spec.msgBody,
                                color = Spec.text,
                                modifier = Modifier.padding(bottom = 6.dp),
                            )
                        }
                        Row(
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            AudioMessageTile(
                                mediaPath = message.mediaPath,
                                durationSeconds = message.mediaDuration,
                                sizeBytes = message.mediaSize,
                                isMine = isMine,
                                onReply = onReply,
                                onLongPress = openActions,
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
                else -> {
                    if (isMine) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = if (grouped) 2.dp else 10.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                            verticalAlignment = Alignment.Bottom,
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = Spec.ownBubbleMax)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomEnd = 4.dp,
                                            bottomStart = 16.dp,
                                        ),
                                    )
                                    .background(Brush.linearGradient(listOf(Spec.blue, Color(0xFF4C9AFF))))
                                    .combinedClickable(onClick = onReply, onLongClick = openActions)
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            ) {
                                Text(
                                    text = message.content.orEmpty(),
                                    style = Spec.msgBody.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                                    color = Color.White,
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 3.dp),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    MessageStatus(
                                        ui = ui,
                                        createdAt = message.createdAt,
                                        notSentLabel = notSentLabel,
                                        retryLabel = retryLabel,
                                        onRetry = onRetry,
                                        onDiscard = onDiscard,
                                        isMine = true,
                                    )
                                }
                            }
                        }
                    } else {
                        Row(
                            modifier = Modifier.padding(start = 48.dp, top = 2.dp),
                            verticalAlignment = Alignment.Bottom,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Column(
                                modifier = Modifier
                                    .widthIn(max = 250.dp)
                                    .clip(
                                        RoundedCornerShape(
                                            topStart = 16.dp,
                                            topEnd = 16.dp,
                                            bottomEnd = 16.dp,
                                            bottomStart = 4.dp,
                                        ),
                                    )
                                    .background(Spec.surface)
                                    .border(
                                        1.dp,
                                        Spec.border,
                                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomEnd = 16.dp, bottomStart = 4.dp),
                                    )
                                    .combinedClickable(onClick = onReply, onLongClick = openActions)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                            ) {
                                Text(
                                    text = message.content.orEmpty(),
                                    style = Spec.msgBody,
                                    color = Spec.text,
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
            }
        }

        if (showReactions) {
            Row(
                modifier = Modifier
                    .align(if (isMine) Alignment.BottomEnd else Alignment.BottomStart)
                    .padding(
                        start = if (isMine) 0.dp else 64.dp,
                        end = if (isMine) 16.dp else 0.dp,
                    ),
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
                AddReactionPill(onClick = openActions)
            }
        }
    }
}

/**
 * بسولة تفاعل واحدة: رمز + عدد داخل كبسولة **بلا إطار** بخلفية صلبة.
 * تُرسم ملتصقة بحافة الرسالة السفلية (نصفها داخل الفقاعة)، لذا الخلفية صلبة
 * (غير شفافة) كي تبقى واضحة فوق الفقاعة الزرقاء أو الداكنة.
 * البسولة التي تفاعلتَ بها = خلفية زرقاء داكنة + رقم أبيض (لا أخضر).
 */
@Composable
private fun ReactionPill(
    emoji: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(13.dp)
    Row(
        modifier = modifier
            .height(26.dp)
            .shadow(2.dp, shape, clip = false)
            .clip(shape)
            .background(
                if (selected) Spec.blue.copy(alpha = 0.32f).compositeOver(Spec.surfaceHigh)
                else Spec.surfaceHigh,
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
            color = if (selected) Color.White else Spec.textMuted,
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
        style = Spec.msgTime,
        color = Spec.textMuted,
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
    isMine: Boolean = false,
    modifier: Modifier = Modifier,
) {
    when {
        ui.failed -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text("⚠️", style = Spec.msgTime)
            Text(notSentLabel, style = Spec.msgTime, color = if (isMine) Color.White else Spec.gold)
            Text(
                retryLabel,
                style = Spec.msgTime,
                color = if (isMine) Color.White else Spec.green,
                modifier = Modifier.clickable { onRetry() },
            )
            Text(
                "✕",
                style = Spec.msgTime,
                color = if (isMine) Color.White.copy(alpha = 0.8f) else Spec.textMuted,
                modifier = Modifier.clickable { onDiscard() },
            )
        }
        ui.pending -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            CircularProgressIndicator(
                color = if (isMine) Color.White.copy(alpha = 0.8f) else Spec.textMuted,
                strokeWidth = 1.5.dp,
                modifier = Modifier.size(10.dp),
            )
            Text("🕓", style = Spec.msgTime, color = if (isMine) Color.White.copy(alpha = 0.8f) else Spec.textMuted)
        }
        isMine -> Row(
            modifier = modifier,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                TimeFormat.timeOnly(createdAt, Locale.getDefault()),
                style = Spec.msgTime.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = Color.White.copy(alpha = 0.75f),
            )
            Icon(
                Icons.Filled.DoneAll,
                contentDescription = stringResource(R.string.cd_message_delivered),
                tint = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.size(14.dp),
            )
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
    isMine: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onRetryUrl: () -> Unit,
    statusContent: (@Composable () -> Unit)? = null,
) {
    val imageShape = RoundedCornerShape(
        topStart = 16.dp,
        topEnd = 16.dp,
        bottomEnd = if (isMine) 4.dp else 16.dp,
        bottomStart = if (isMine) 16.dp else 4.dp,
    )
    Box(
        modifier = Modifier
            // مقاس المواصفة: 230×150 مع زاوية سفلية مميزة لاتجاه الرسالة.
            .size(width = Spec.incomingImageW, height = Spec.incomingImageH)
            .clip(imageShape)
            .border(1.dp, Spec.border, imageShape)
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress),
    ) {
        com.addonstation.mobile.ui.components.CoverImage(
            url = url,
            model = localPreview?.let { java.io.File(it) },
            radius = 0.dp,
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
                Text("🖼 " + stringResource(R.string.chat_tap_to_retry), style = Spec.msgTime, color = Spec.textMuted)
            }
        }
        statusContent?.let { content ->
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(horizontal = 7.dp, vertical = 4.dp),
            ) {
                content()
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
