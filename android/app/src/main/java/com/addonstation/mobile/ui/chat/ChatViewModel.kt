package com.addonstation.mobile.ui.chat

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.data.model.ChatMessage
import com.addonstation.mobile.data.model.ChatMessageUi
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.data.model.StickerPack
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
        // قناة Realtime على جدول chat_messages مقيّدة بهذه الغرفة (نفس ما تشترطه وثيقة الشات)
        val sub = container.realtimeClient.subscribeChatMessages(
            channelId = channelId,
            onRecord = { record ->
                scope.launch { onInserted(record) }
            },
            onStatus = { status ->
                if (status == "failed") scope.launch { refreshTails() }
            },
        )
        container.realtime.replace(sub)
    }

    /** تحديث احتياطي كل ثانيتين (منطق النسخة الأصلية). */
    private fun startPolling(channelId: String) {
        pollJob?.cancel()
        pollJob = scope.launch {
            while (true) {
                delay(2000)
                // أي خطأ عابر في جلب واحد لا يجوز أن يُوقف الاستقبال كلّه
                runCatching { refreshTails() }
                runCatching {
                    _typing.value = container.chat.typingNames(
                        channelId, container.supabase.session()?.userId,
                    )
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
        _messages.value = _messages.value + toUi(message)
        if (message.userId != container.supabase.session()?.userId) {
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
                val mapped = newer.filter { m -> current.none { it.message.id == m.id } }.map { toUi(it) }
                if (mapped.isNotEmpty()) {
                    _messages.value = current + mapped
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

    fun send(
        content: String?,
        replyTo: ChatMessage? = null,
        stickerUrl: String? = null,
        imagePath: String? = null,
        messageType: String = "text",
        mediaPath: String? = null,
        mediaMime: String? = null,
        mediaSize: Long? = null,
        onDone: (Boolean, String?) -> Unit,
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
                    mediaSize = mediaSize,
                    mentionIds = mentions,
                )
            }.onSuccess { sent ->
                // ② الخادم لم يُرجع رقماً ⇒ نحذف المؤقتة ونترك الاستطلاع يجلب النسخة الحقيقية
                if (sent.id <= 0L) {
                    _messages.value = _messages.value.filterNot { it.message.id == tempId }
                    onDone(true, null)
                    refreshTails()
                    return@onSuccess
                }
                // ③ نستبدل المؤقتة بالرسالة الحقيقية — ولو سبقها الاستقبال نُبقي نسخة واحدة فقط
                val current = _messages.value
                if (current.any { it.message.id == tempId }) {
                    _messages.value = current
                        .filterNot { it.message.id == sent.id && it.message.id != tempId }
                        .map { if (it.message.id == tempId) it.copy(message = sent, pending = false) else it }
                } else if (current.none { it.message.id == sent.id }) {
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
                // ③ فشل الإرسال ⇒ تختفي الرسالة المؤقتة حتى لا تبقى معلّقة
                _messages.value = _messages.value.filterNot { it.message.id == tempId }
                onDone(false, e.message)
            }
        }
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
        scope.cancel()
    }
}
