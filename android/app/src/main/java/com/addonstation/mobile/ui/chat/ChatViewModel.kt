package com.addonstation.mobile.ui.chat

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatMessageUi
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.data.model.StickerPack
import com.addonstation.mobile.data.toApiError
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * حالة الشات: تحميل الرسائل مع ترقيم الصفحات، التحديث اللحظي عبر Realtime،
 * تحديث احتياطي كل ثانيتين (كما في التطبيق الأصلي)، التفاعلات، الطباعة، والقوالب.
 */
class ChatViewModel(private val container: AppContainer) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var pollJob: Job? = null

    /** هل شاشة الشات معروضة الآن؟ (الاستطلاع يتوقّف تماماً في الخلفية) */
    @Volatile
    private var screenActive = true

    /** هل اتصال Realtime سليم؟ ⇒ الاستطلاع يخفّ إلى نسخة احتياطية. */
    @Volatile
    private var realtimeHealthy = false

    /**
     * تُستدعى من الشاشة: true عند العرض، و false عند مغادرتها (أو ذهاب التطبيق
     * إلى الخلفية) — فلا شبكة ولا عمل بلا داعٍ.
     */
    fun setScreenActive(value: Boolean) {
        screenActive = value
    }

    private val _channel = MutableStateFlow<ChatChannel?>(null)
    val channel: StateFlow<ChatChannel?> = _channel.asStateFlow()

    private val _messages = MutableStateFlow<List<ChatMessageUi>>(emptyList())
    val messages: StateFlow<List<ChatMessageUi>> = _messages.asStateFlow()

    private val _authors = MutableStateFlow<Map<String, Profile>>(emptyMap())
    val authors: StateFlow<Map<String, Profile>> = _authors.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _newMessagesBelow = MutableStateFlow(false)
    val newMessagesBelow: StateFlow<Boolean> = _newMessagesBelow.asStateFlow()

    /** يزيد عند كل رسالة يرسلها المستخدم ⇒ الشاشة تمرّر إليها دائماً (حتى لو كانت فوق). */
    private val _scrollTick = MutableStateFlow(0)
    val scrollTick: StateFlow<Int> = _scrollTick.asStateFlow()

    private val _newDividerId = MutableStateFlow<Long?>(null)
    val newDividerId: StateFlow<Long?> = _newDividerId.asStateFlow()

    private val _typing = MutableStateFlow<List<String>>(emptyList())
    val typing: StateFlow<List<String>> = _typing.asStateFlow()

    /** عدد الحاضرين في الغرفة الآن (Realtime presence) — «[N] online» في الشريط. */
    private val _online = MutableStateFlow(0)
    val online: StateFlow<Int> = _online.asStateFlow()

    /**
     * المثبّتات — تُجلب عند **دخول الغرفة** لا عند ضغط الزر فقط، لأن وثيقة
     * التصميم تعرض شريطاً ذهبياً أعلى الرسائل حين يوجد شيء مثبَّت.
     */
    private val _pinned = MutableStateFlow<List<ChatMessage>>(emptyList())
    val pinned: StateFlow<List<ChatMessage>> = _pinned.asStateFlow()

    /**
     * من حظرتُهم (من قاعدة البيانات). الفلترة الأصلية في الخادم (RLS)، وهذه
     * نسخة محلية للفلترة الفورية: يُخفى المحظور من اللحظة نفسها بلا انتظار
     * جلب، ويُمنع تسرّب رسالة وصلت من Realtime/الاستقصاء قبل تحديث الجلسة.
     */
    private val _blockedIds = MutableStateFlow<Set<String>>(emptySet())
    val blockedIds: StateFlow<Set<String>> = _blockedIds.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stickers = MutableStateFlow<List<SiteAsset>>(emptyList())
    val stickers: StateFlow<List<SiteAsset>> = _stickers.asStateFlow()

    private val _packs = MutableStateFlow<List<StickerPack>>(emptyList())
    val packs: StateFlow<List<StickerPack>> = _packs.asStateFlow()

    var lastReadId: Long = 0L
        private set

    fun open(channel: ChatChannel) {
        _channel.value = channel
        scope.launch {
            _loading.value = true
            // فشل أول جلب لا يجوز أن يُبقي الشات على «جارٍ التحميل» للأبد:
            // الاستطلاع (كل ثانيتين) يجلب الرسائل بعده في أي حال.
            runCatching { _blockedIds.value = container.chat.blockedIds() }
            runCatching { loadInitial(channel.id) }
            _loading.value = false
            startRealtime(channel.id)
            startPolling(channel.id)
            loadStickers()
        }
    }

    private suspend fun loadInitial(channelId: String) {
        // الرسالة أولاً (نحتاج idsها)، ثم بقية الطلبات بالتوازي لا بالتتابع
        val list = container.chat.messages(channelId, limit = 100)
        lastReadId = list.lastOrNull()?.id ?: 0L
        val ids = list.map { it.userId }.toMutableSet()
        val messageIds = list.map { it.id }
        val (profiles, reactions, myReactions) = coroutineScope {
            val a = async { container.profile.loadProfiles(ids) }
            val b = async { container.chat.reactionsFor(messageIds) }
            val c = async { container.chat.myReactionsFor(messageIds) }
            Triple(a.await(), b.await(), c.await())
        }
        val byId = list.associateBy { it.id }
        _authors.value = profiles
        // وقت آخر رسالة يُسجَّل لقائمة الغرف (تصميم ٣: وقت في كل صف)
        list.maxByOrNull { it.createdAt ?: "" }?.let { ChatPeek.note(it.channelId, it.createdAt) }
        _messages.value = list.map { message ->
            val reply = message.replyToId?.let { byId[it] }
            ChatMessageUi(
                message = message,
                author = profiles[message.userId],
                replyTo = reply,
                replyAuthorName = reply?.let { profiles[it.userId]?.username },
                reactions = reactions[message.id] ?: emptyMap(),
                myReactions = myReactions.filter { it.first == message.id }.map { it.second }.toSet(),
            )
        }
        if (container.supabase.session() != null) {
            container.chat.markRead(channelId, lastReadId)
            container.chat.refreshUnread()
        }
    }

    private fun startRealtime(channelId: String) {
        container.realtime.close()
        realtimeHealthy = false
        // قناة Realtime على جدول chat_messages مقيّدة بهذه الغرفة (نفس ما تشترطه وثيقة الشات)
        val sub = container.realtimeClient.subscribeChatMessages(
            channelId = channelId,
            onRecord = { record ->
                scope.launch { onInserted(record) }
            },
            onStatus = { status ->
                // اتصال سليم ⇒ الاستطلاع يصير نسخةً احتياطية خفيفة (كل ٤ ثوانٍ)
                realtimeHealthy = status == "joined"
                if (status == "failed") scope.launch { refreshTails() }
            },
            // عدّاد الحضور: من نفس المقبس، وبلا أي طلب إضافي
            onPresence = { count -> if (count != _online.value) _online.value = count },
        )
        container.realtime.replace(sub)
    }

    /**
     * تحديث احتياطي (الاستطلاع).
     *
     * كان يستقصي **كل ثانيتين** بطلبين (الرسائل + أسماء الكتابة) سواءٌ اتصل
     * Realtime أو لا، وسواءٌ كانت الشاشة معروضة أو في الخلفية — ويُسنِد النتيجة
     * إلى الحالة كل مرة ⇒ **إعادة تركيب الشاشة كل ثانيتين** (وهو أصل التقطّع
     * واستنزاف البطارية). الآن:
     *   • الشاشة غير معروضة ⇒ لا شبكة إطلاقاً.
     *   • Realtime سليم ⇒ كل ٤ ثوانٍ (نسخة احتياطية فقط) · وإلا كل ثانيتين.
     *   • لا يُسنَد إلى الحالة إلا ما **تغيّر** فعلاً (لا إعادة تركيب بلا سبب).
     */
    private fun startPolling(channelId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                val period = when {
                    !screenActive -> 10_000L          // في الخلفية: نائمة
                    realtimeHealthy -> 4_000L         // Realtime يكفي ⇒ نسخة خفيفة
                    else -> 2_000L                    // بلا Realtime ⇒ الاستقصاء هو الأصل
                }
                delay(period)
                if (!screenActive) continue           // بلا شبكة ولا إعادة تركيب
                // أي خطأ عابر في جلب واحد لا يجوز أن يُوقف الاستقبال كلّه
                runCatching { refreshTails() }
                runCatching {
                    val names = container.chat.typingNames(
                        channelId, container.supabase.session()?.userId,
                    )
                    if (names != _typing.value) _typing.value = names
                }
            }
        }
    }

    private suspend fun onInserted(record: JsonObject) {
        val channelId = _channel.value?.id ?: return
        val message = runCatching {
            container.supabase.json.decodeFromJsonElement(ChatMessage.serializer(), record)
        }.getOrNull() ?: return
        if (_messages.value.any { it.message.id == message.id }) return
        if (message.userId in _blockedIds.value) return          // محظور: لا يظهر
        _messages.value = _messages.value + toUi(message)
        if (message.userId != container.supabase.session()?.userId &&
            !_newMessagesBelow.value
        ) {
            // تظهر شارة «↓ New messages» ويمسحها المستخدم بالتمرير للأسفل
            _newMessagesBelow.value = true
        }
    }

    private suspend fun refreshTails() {
        val channelId = _channel.value?.id ?: return
        val current = _messages.value
        // آخر رسالة حقيقية فقط (الرسائل المؤقتة_optimistic رقمها سالب)
        val lastId = current.lastOrNull { it.message.id > 0L }?.message?.id ?: 0L
        if (lastId > 0) {
            val newer = container.chat.messagesSince(channelId, lastId)
            if (newer.isNotEmpty()) {
                val myId = container.supabase.session()?.userId
                var list = current
                val mapped = mutableListOf<ChatMessageUi>()
                for (message in newer) {
                    // نسختي المؤقتة (رقمها سالب) التي وصلت من الخادم ⇒ تُستبدل
                    // في مكانها فلا تتكرّر الرسالة مرّتين ولا يهتزّ الموضع.
                    val draft = list.firstOrNull {
                        it.message.id < 0L && it.message.userId == message.userId && sameDraft(it.message, message)
                    }
                    if (draft != null) {
                        list = list.map {
                            if (it.message.id == draft.message.id) {
                                it.copy(message = message, pending = false, failed = false)
                            } else {
                                it
                            }
                        }
                    } else if (message.userId !in _blockedIds.value &&
                        list.none { it.message.id == message.id }
                    ) {
                        mapped += toUi(message)
                    }
                }
                if (mapped.isNotEmpty() || list !== current) _messages.value = list + mapped
                // رسائل الآخرين فقط ترفع شارة «رسائل بالأسفل» — لا رسائلي أنا
                // (ولا نُسنِد القيمة إن كانت مرفوعة أصلاً: كل إسناد = إعادة تركيب)
                if (mapped.any { it.message.userId != myId } && !_newMessagesBelow.value) {
                    _newMessagesBelow.value = true
                }
            }
        }
    }

    private suspend fun toUi(message: ChatMessage): ChatMessageUi {
        val profiles = _authors.value.toMutableMap()
        if (profiles[message.userId] == null) {
            container.profile.loadProfile(message.userId)?.let { profiles[message.userId] = it }
            _authors.value = profiles
        }
        val reply = message.replyToId?.let { id -> _messages.value.firstOrNull { it.message.id == id }?.message }
        val reactions = container.chat.reactionsFor(listOf(message.id))[message.id] ?: emptyMap()
        val mine = container.chat.myReactionsFor(listOf(message.id))
            .filter { it.first == message.id }.map { it.second }.toSet()
        return ChatMessageUi(
            message = message,
            author = profiles[message.userId],
            replyTo = reply,
            replyAuthorName = reply?.let { profiles[it.userId]?.username },
            reactions = reactions,
            myReactions = mine,
        )
    }

    fun loadOlder() {
        val channel = _channel.value ?: return
        val firstId = _messages.value.firstOrNull()?.message?.id ?: return
        scope.launch {
            val older = container.chat.messages(channel.id, beforeId = firstId, limit = 100)
            if (older.isEmpty()) return@launch
            val profiles = container.profile.loadProfiles(older.map { it.userId })
            _authors.value = _authors.value + profiles
            _messages.value = older.map { toUi(it) } + _messages.value
        }
    }

    /**
     * هل الرسالتان نسختان لشيء واحد؟ (نفس المرسِل والنص والمرفقات)
     * تستعملها المطابقة لتأكيد الرسالة المؤقتة حين يسبق الاستطلاع ردّ الإرسال.
     */
    private fun sameDraft(a: ChatMessage, b: ChatMessage): Boolean =
        a.userId == b.userId &&
            a.content.orEmpty() == b.content.orEmpty() &&
            a.stickerUrl.orEmpty() == b.stickerUrl.orEmpty() &&
            a.imagePath.orEmpty() == b.imagePath.orEmpty() &&
            a.mediaPath.orEmpty() == b.mediaPath.orEmpty()

    fun send(
        content: String?,
        replyTo: ChatMessage? = null,
        stickerUrl: String? = null,
        imagePath: String? = null,
        messageType: String = "text",
        mediaPath: String? = null,
        mediaMime: String? = null,
        mediaDuration: Int? = null,
        mediaSize: Long? = null,
        /** معاينة محلية (ملف) للصورة أو لإطار الفيديو قبل توقيع رابطها. */
        localPreview: String? = null,
        /** (نجح؟، سبب الفشل) — السبب **نوع خطأ** لا نصّ خام، فيُترجم في الشاشة. */
        onDone: (Boolean, ApiErrorKind?) -> Unit,
    ) {
        val channel = _channel.value ?: return
        val myId = container.supabase.session()?.userId.orEmpty()

        // ① تظهر الرسالة فوراً (بلا انتظار رد الخادم) برقم مؤقت سالب (رقم فريد دائماً)
        var tempId = -System.currentTimeMillis()
        while (_messages.value.any { it.message.id == tempId }) tempId -= 1
        val draft = ChatMessage(
            id = tempId,
            channelId = channel.id,
            userId = myId,
            content = content,
            messageType = messageType,
            stickerUrl = stickerUrl,
            imagePath = imagePath,
            mediaPath = mediaPath,
            mediaMimeType = mediaMime,
            mediaDuration = mediaDuration,
            mediaSize = mediaSize,
            replyToId = replyTo?.id,
            createdAt = com.addonstation.mobile.util.TimeFormat.isoNow(),
        )
        val meProfile = _authors.value[myId] ?: container.state.profile.value?.takeIf { it.id == myId }
        _messages.value = _messages.value + ChatMessageUi(
            message = draft,
            author = meProfile,
            replyTo = replyTo,
            replyAuthorName = replyTo?.let { _authors.value[it.userId]?.username },
            pending = true,
            localPreview = localPreview,
        )
        // رسالتي أنا: تظهر دائماً وتُمرَّر إليها، والشارة «رسائل بالأسفل» تُصفَّر
        _newMessagesBelow.value = false
        _scrollTick.value += 1

        scope.launch {
            val members = _authors.value.values.toList()
            val mentions = content?.let { container.chat.mentionsIn(it, members) } ?: emptyList()
            runCatching {
                container.chat.send(
                    channelId = channel.id,
                    content = content,
                    replyToId = replyTo?.id,
                    stickerUrl = stickerUrl,
                    imagePath = imagePath,
                    messageType = messageType,
                    mediaPath = mediaPath,
                    mediaMime = mediaMime,
                    mediaDuration = mediaDuration,
                    mediaSize = mediaSize,
                    mentionIds = mentions,
                )
            }.onSuccess { sent ->
                // ② الخادم لم يُرجع رقماً ⇒ نُبقيها «قيد الإرسال» ويؤكّدها
                // الاستطلاع/الاستقبال بالمطابقة (لا تختفي ولا تتكرّر).
                if (sent.id <= 0L) {
                    onDone(true, null)
                    refreshTails()
                    return@onSuccess
                }
                // ③ نستبدل المؤقتة بالرسالة الحقيقية — ولو سبقها الاستقبال نُبقي نسخة واحدة فقط
                val current = _messages.value
                if (current.any { it.message.id == sent.id }) {
                    // الاستقبال اللحظي سبق ردّ الإرسال ⇒ نحذف المؤقتة المكرَّرة
                    _messages.value = current.filterNot { it.message.id == tempId }
                } else if (current.any { it.message.id == tempId }) {
                    _messages.value = current.map {
                        if (it.message.id == tempId) it.copy(message = sent, pending = false) else it
                    }
                } else {
                    _messages.value = current + ChatMessageUi(
                        message = sent,
                        author = _authors.value[sent.userId] ?: meProfile,
                        replyTo = replyTo,
                        replyAuthorName = replyTo?.let { r -> _authors.value[r.userId]?.username },
                    )
                }
                onDone(true, null)
                refreshTails()
            }.onFailure { e ->
                // ③ فشل الإرسال ⇒ الرسالة **تبقى** ظاهرة موسومة بـ«لم تُرسل»
                // ومعها زر إعادة المحاولة (بدل أن تختفي فيظنّ صاحبها أنها أُرسلت).
                _messages.value = _messages.value.map {
                    if (it.message.id == tempId) it.copy(pending = false, failed = true) else it
                }
                onDone(false, e.toApiError().kind)
            }
        }
    }

    /**
     * إعادة إرسال رسالة فاشلة من مكانها: تبقى في القائمة ويتبدّل وسمها من
     * «لم تُرسل» إلى «قيد الإرسال» ثم إلى مؤكَّدة — بلا خروج من الغرفة.
     *
     * قبل الإرسال نتحقّق أن الخادم لم يقبلها أصلاً (انقطع الردّ مثلاً): فإن
     * وُجدت نسختها الحقيقية اكتفينا بتأكيدها ولم نُرسلها مرّتين.
     */
    fun retry(messageId: Long, onDone: (Boolean, ApiErrorKind?) -> Unit = { _, _ -> }) {
        val ui = _messages.value.firstOrNull { it.message.id == messageId } ?: return
        if (!ui.failed) return
        val draft = ui.message
        _messages.value = _messages.value.map {
            if (it.message.id == messageId) it.copy(pending = true, failed = false) else it
        }
        scope.launch {
            runCatching { refreshTails() }
            // الخادم قبلها وانقطع الردّ سابقاً ⇒ المطابقة أكّدتها برقمها الحقيقي
            // فلا نُعيد إرسالها مرّة ثانية (اختفت برقمها المؤقت أو صارت مؤكَّدة).
            val current = _messages.value.firstOrNull { it.message.id == messageId }
            if (current == null || (!current.pending && !current.failed)) {
                onDone(true, null)
                return@launch
            }
            runCatching {
                container.chat.send(
                    channelId = draft.channelId,
                    content = draft.content,
                    replyToId = draft.replyToId,
                    stickerUrl = draft.stickerUrl,
                    imagePath = draft.imagePath,
                    messageType = draft.messageType,
                    mediaPath = draft.mediaPath,
                    mediaMime = draft.mediaMimeType,
                    mediaDuration = draft.mediaDuration,
                    mediaSize = draft.mediaSize,
                )
            }.onSuccess { sent ->
                val current = _messages.value      // لقطة واحدة: لا نقرأ القائمة مرّتين
                if (sent.id > 0L) {
                    _messages.value = current.map {
                        if (it.message.id == messageId) {
                            it.copy(message = sent, pending = false, failed = false)
                        } else {
                            it
                        }
                    }
                } else {
                    _messages.value = current.map {
                        if (it.message.id == messageId) it.copy(pending = false) else it
                    }
                }
                onDone(true, null)
                refreshTails()
            }.onFailure { e ->
                _messages.value = _messages.value.map {
                    if (it.message.id == messageId) it.copy(pending = false, failed = true) else it
                }
                onDone(false, e.toApiError().kind)
            }
        }
    }

    /** إسقاط رسالة فاشلة من القائمة (صاحبها قرّر ألا يعيد إرسالها). */
    fun discard(messageId: Long) {
        _messages.value = _messages.value.filterNot { it.message.id == messageId }
    }

    fun toggleReaction(messageId: Long, emoji: String) {
        scope.launch {
            val result = container.chat.toggleReaction(messageId, emoji)
            if (result.isSuccess) {
                val reactions = container.chat.reactionsFor(listOf(messageId))[messageId] ?: emptyMap()
                val mine = container.chat.myReactionsFor(listOf(messageId))
                    .filter { it.first == messageId }.map { it.second }.toSet()
                _messages.value = _messages.value.map { ui ->
                    if (ui.message.id == messageId) ui.copy(reactions = reactions, myReactions = mine) else ui
                }
            }
        }
    }

    /**
     * حذف المستخدم لرسالته — مسموح في أول ٣ دقائق فقط من الإرسال (القاعدة ترفض ما بعدها).
     * عند الرفض لانتهاء المدة نعرض السبب للمستخدم بدل الفشل الصامت.
     */
    fun deleteMine(messageId: Long, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            container.chat.deleteMyMessage(messageId)
                .onSuccess {
                    _messages.value = _messages.value.map { ui ->
                        if (ui.message.id == messageId)
                            ui.copy(message = ui.message.copy(status = "deleted", content = null))
                        else ui
                    }
                    onResult(true)
                }
                .onFailure { onResult(false) }
        }
    }

    /**
     * حذف أي رسالة **مهما كان عمرها** — للأدمن ولمشرف الغرفة (لا نافذة زمنية).
     * نافذة الثلاث دقائق تبقى على المستخدم العادي في [deleteMine] وحدها.
     */
    fun deleteAsStaff(messageId: Long, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            container.chat.deleteMessageAsStaff(messageId)
                .onSuccess {
                    _messages.value = _messages.value.map { ui ->
                        if (ui.message.id == messageId) {
                            ui.copy(
                                message = ui.message.copy(
                                    status = "deleted",
                                    content = null,
                                    stickerUrl = null,
                                    imagePath = null,
                                    mediaPath = null,
                                    isPinned = false,
                                ),
                                reactions = emptyMap(),
                                myReactions = emptySet(),
                            )
                        } else {
                            ui
                        }
                    }
                    onResult(true)
                }
                .onFailure { onResult(false) }
        }
    }

    fun moderate(messageId: Long, hide: Boolean) {
        scope.launch {
            container.chat.moderateMessage(messageId, hide).onSuccess { refreshTails(); reload() }
        }
    }

    fun pin(messageId: Long, onResult: (Boolean) -> Unit) {
        scope.launch {
            container.chat.togglePin(messageId)
                .onSuccess { onResult(true) }
                .onFailure { onResult(false) }
        }
    }

    fun mute(userId: String, minutes: Int) {
        scope.launch { container.chat.muteUser(userId, minutes) }
    }

    /**
     * حظر مستخدم (سياسة Google Play UGC): يُبلَّغ الخادم أولاً، ثم تُخفى
     * رسائله من القائمة المعروضة فوراً بلا إعادة تحميل — تجربة فورية، والقاعدة
     * تضمن الاختفاء الدائم بعدها (الاستقصاء والبثّ والبحث كلها تمرّ بـ RLS).
     */
    fun block(userId: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            container.chat.blockUser(userId)
                .onSuccess {
                    _blockedIds.value = _blockedIds.value + userId
                    _messages.value = _messages.value.filterNot { it.message.userId == userId }
                    _authors.value = _authors.value - userId
                    onResult(true)
                }
                .onFailure { onResult(false) }
        }
    }

    fun unblock(userId: String, onResult: (Boolean) -> Unit = {}) {
        scope.launch {
            container.chat.unblockUser(userId)
                .onSuccess {
                    _blockedIds.value = _blockedIds.value - userId
                    runCatching { reload() }
                    onResult(true)
                }
                .onFailure { onResult(false) }
        }
    }

    fun report(messageId: Long, reason: String, note: String?) {
        scope.launch { container.chat.report(messageId, reason, note) }
    }

    fun notifyTyping() {
        val channel = _channel.value ?: return
        scope.launch { container.chat.setTyping(channel.id) }
    }

    fun markReadUpToBottom() {
        val channel = _channel.value ?: return
        val last = _messages.value.lastOrNull()?.message?.id ?: return
        lastReadId = maxOf(lastReadId, last)
        _newMessagesBelow.value = false
        scope.launch {
            container.chat.markRead(channel.id, lastReadId)
            container.chat.refreshUnread()
        }
    }

    private suspend fun loadStickers() {
        _packs.value = container.chat.stickerPacks()
        _stickers.value = container.chat.stickers()
    }

    fun reload() {
        val channel = _channel.value ?: return
        scope.launch { loadInitial(channel.id) }
    }

    fun close() {
        pollJob?.cancel()
        container.realtime.close()
        resetPresence()
        scope.cancel()
    }
}
