package com.addonstation.mobile.ui.chat

import android.os.SystemClock
import kotlinx.coroutines.CancellationException
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
    private var openJob: Job? = null
    private var pollJob: Job? = null
    private var mutedUsersJob: Job? = null
    private var readJob: Job? = null
    private var typingJob: Job? = null
    private var olderJob: Job? = null
    private var pendingReadId = 0L
    private var lastSyncedReadId = 0L
    private var lastTypingSentAt = 0L
    private var olderCursorId: Long? = null
    private var reachedOldestPage = false

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

    /**
     * مكتومون عندي (كتم **محلي** من ملف العضو: «Mute this user»). أثره مثل
     * الحظر في العرض — تُخفى رسائلهم — لكنه قرار شخصيّ على هذا الجهاز وحده،
     * ولا يُبلَّغ به أحد. يُقرأ من إعدادات الجهاز ويُتابَع حيّاً، فبمجرّد
     * الكتم من ملف العضو تختفي رسائله في الشات المفتوح بلا إعادة فتح.
     */
    private val _mutedIds = MutableStateFlow<Set<String>>(emptySet())
    val mutedIds: StateFlow<Set<String>> = _mutedIds.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _stickers = MutableStateFlow<List<SiteAsset>>(emptyList())
    val stickers: StateFlow<List<SiteAsset>> = _stickers.asStateFlow()

    private val _packs = MutableStateFlow<List<StickerPack>>(emptyList())
    val packs: StateFlow<List<StickerPack>> = _packs.asStateFlow()

    /** Protects a just-toggled reaction from being overwritten by initial metadata. */
    private val reactionChangeRevision = mutableMapOf<Long, Long>()

    var lastReadId: Long = 0L
        private set

    fun open(channel: ChatChannel) {
        if (_channel.value?.id != channel.id) {
            readJob?.cancel()
            typingJob?.cancel()
            olderJob?.cancel()
            pendingReadId = 0L
            lastSyncedReadId = 0L
            lastReadId = 0L
            lastTypingSentAt = 0L
            olderCursorId = null
            reachedOldestPage = false
            _pinned.value = emptyList()
            _online.value = 0
            _newMessagesBelow.value = false
            _newDividerId.value = null
            _messages.value = emptyList()
            _authors.value = emptyMap()
            _blockedIds.value = emptySet()
            _mutedIds.value = emptySet()
            reactionChangeRevision.clear()
        }
        _channel.value = channel
        openJob?.cancel()
        openJob = scope.launch {
            _loading.value = true
            // Keep exactly one local-mute collector for this model. Re-opening a
            // room must not leave another infinite DataStore collector behind.
            mutedUsersJob?.cancel()
            mutedUsersJob = scope.launch {
                container.prefs.mutedUsers.collect { ids ->
                    if (_channel.value?.id == channel.id && ids != _mutedIds.value) {
                        val previouslyMuted = _mutedIds.value
                        _mutedIds.value = ids
                        filterCurrentMessages()
                        if ((previouslyMuted - ids).isNotEmpty()) reload()
                    }
                }
            }

            // Render the first page as soon as it arrives. Profiles, reactions,
            // read receipts and moderation refreshes continue in the background.
            try {
                loadInitial(channel.id)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Full-screen error state remains available; Realtime may still recover.
            }
            if (_channel.value?.id != channel.id) return@launch
            _loading.value = false
            startRealtime(channel.id)
            startPolling(channel.id)
            refreshAccessFilters(channel.id)
            loadStickers()
        }
    }

    private suspend fun loadInitial(channelId: String) {
        // Local mute state and network messages are read concurrently; the
        // local cache therefore does not add a network round trip to the skeleton.
        val (list, cachedMutedIds) = coroutineScope {
            val messages = async { container.chat.messages(channelId, limit = PAGE_SIZE) }
            val muted = async {
                runCatching { container.prefs.readMutedUsers() }.getOrDefault(_mutedIds.value)
            }
            messages.await() to muted.await()
        }
        if (_channel.value?.id != channelId) return

        _mutedIds.value = cachedMutedIds
        olderCursorId = list.firstOrNull()?.id
        reachedOldestPage = list.size < PAGE_SIZE
        lastReadId = list.lastOrNull()?.id ?: 0L
        val visible = list.filterNot { it.userId in _blockedIds.value || it.userId in cachedMutedIds }
        val byId = visible.associateBy { it.id }
        val existingAuthors = _authors.value
        val drafts = _messages.value.filter { it.message.id < 0L && it.pending }

        // Messages are useful before their optional metadata. This is the point
        // at which the room stops showing the full-screen skeleton.
        _messages.value = visible.map { message ->
            val reply = message.replyToId?.let { byId[it] }
            ChatMessageUi(
                message = message,
                author = existingAuthors[message.userId],
                replyTo = reply,
                replyAuthorName = reply?.let { existingAuthors[it.userId]?.username },
            )
        } + drafts
        _loading.value = false

        visible.maxByOrNull { it.createdAt ?: "" }?.let { last ->
            ChatPeek.note(
                last.channelId,
                last.createdAt,
                last.content,
                last.messageType,
                existingAuthors[last.userId]?.username,
            )
        }
        loadInitialMetadata(channelId, visible, byId)

        if (container.supabase.session() != null) {
            enqueueReadReceipt(channelId, lastReadId)
        }
    }

    /**
     * Profile metadata is what supplies the chat name, rank, avatar, and equipped
     * item. Keep messages non-blocking, but retry this one shared request instead
     * of permanently turning a transient profile failure into an empty author map.
     */
    private suspend fun loadAuthorProfiles(userIds: Collection<String>): Map<String, Profile> {
        val wanted = userIds.filter(String::isNotBlank).distinct()
        if (wanted.isEmpty()) return emptyMap()
        var profiles = emptyMap<String, Profile>()
        repeat(3) { attempt ->
            try {
                profiles = profiles + container.profile.loadProfiles(wanted - profiles.keys)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // Retry below; chat messages remain usable while metadata recovers.
            }
            if (profiles.keys.containsAll(wanted)) return profiles
            if (attempt < 2) delay(600L * (attempt + 1))
        }
        return profiles
    }

    /** Non-essential authors/reactions are merged after the messages are already visible. */
    private fun loadInitialMetadata(
        channelId: String,
        messages: List<ChatMessage>,
        byId: Map<Long, ChatMessage>,
    ) {
        if (messages.isEmpty()) return
        val userIds = messages.map { it.userId }.distinct()
        val messageIds = messages.map { it.id }
        val revisionsAtRequest = messageIds.associateWith { reactionChangeRevision[it] ?: 0L }

        scope.launch {
            val (profiles, reactions, myReactions) = coroutineScope {
                val a = async { loadAuthorProfiles(userIds) }
                val b = async {
                    runCatching { container.chat.reactionsFor(messageIds) }.getOrDefault(emptyMap())
                }
                val c = async {
                    runCatching { container.chat.myReactionsFor(messageIds) }.getOrDefault(emptySet())
                }
                Triple(a.await(), b.await(), c.await())
            }
            if (_channel.value?.id != channelId) return@launch

            val authors = _authors.value + profiles
            _authors.value = authors
            val myReactionsByMessage = mutableMapOf<Long, MutableSet<String>>()
            myReactions.forEach { (messageId, emoji) ->
                myReactionsByMessage.getOrPut(messageId) { linkedSetOf() }.add(emoji)
            }
            val initialById = messages.associateBy { it.id }
            _messages.value = _messages.value.map { ui ->
                val initial = initialById[ui.message.id] ?: return@map ui
                val reply = initial.replyToId?.let { byId[it] }
                val reactionWasChanged =
                    (reactionChangeRevision[initial.id] ?: 0L) != (revisionsAtRequest[initial.id] ?: 0L)
                ui.copy(
                    author = authors[initial.userId],
                    replyTo = reply,
                    replyAuthorName = reply?.let { authors[it.userId]?.username },
                    reactions = if (reactionWasChanged) ui.reactions else reactions[initial.id] ?: emptyMap(),
                    myReactions = if (reactionWasChanged) ui.myReactions
                    else myReactionsByMessage[initial.id].orEmpty(),
                )
            }
            messages.maxByOrNull { it.createdAt ?: "" }?.let { last ->
                ChatPeek.note(
                    last.channelId,
                    last.createdAt,
                    last.content,
                    last.messageType,
                    authors[last.userId]?.username,
                )
            }
        }
    }

    /** These access lists must not hold up first paint of the room. */
    private fun refreshAccessFilters(channelId: String) {
        scope.launch {
            val blocked = runCatching { container.chat.blockedIds() }.getOrNull() ?: return@launch
            if (_channel.value?.id != channelId) return@launch
            if (blocked != _blockedIds.value) {
                _blockedIds.value = blocked
                filterCurrentMessages()
            }
        }
        scope.launch {
            val muted = runCatching { container.chat.mutedMemberIds() }.getOrNull() ?: return@launch
            if (_channel.value?.id != channelId || muted.isEmpty()) return@launch
            val previous = _mutedIds.value
            if (muted != previous) {
                _mutedIds.value = muted
                filterCurrentMessages()
                if ((previous - muted).isNotEmpty()) reload()
            }
            runCatching { container.prefs.setMutedUsers(muted) }
        }
    }

    private fun filterCurrentMessages() {
        val blocked = _blockedIds.value
        val muted = _mutedIds.value
        val current = _messages.value
        val visible = current.filterNot { it.message.userId in blocked || it.message.userId in muted }
        if (visible.size != current.size) _messages.value = visible
    }

    /**
     * دمج إشعارات القراءة المتكررة الناتجة عن snapshotFlow/التمرير في طلب واحد.
     * لا نعيد RPC للمعرّف نفسه، ونرسل أعلى معرّف وصل خلال نافذة قصيرة فقط.
     */
    private fun enqueueReadReceipt(channelId: String, messageId: Long) {
        if (messageId <= 0L || messageId <= lastSyncedReadId) return
        pendingReadId = maxOf(pendingReadId, messageId)
        if (readJob?.isActive == true) return
        readJob = scope.launch {
            delay(READ_RECEIPT_DEBOUNCE_MS)
            while (_channel.value?.id == channelId && pendingReadId > lastSyncedReadId) {
                val target = pendingReadId
                container.chat.markRead(channelId, target)
                if (_channel.value?.id != channelId) return@launch
                lastSyncedReadId = maxOf(lastSyncedReadId, target)
                runCatching { container.chat.refreshUnread() }
                if (pendingReadId <= lastSyncedReadId) break
                delay(READ_RECEIPT_DEBOUNCE_MS)
            }
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
            onPresence = { count ->
                if (count != _online.value) _online.value = count
                ChatPeek.noteOnline(channelId, count)
            },
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
        if (message.userId in _mutedIds.value) return            // مكتوم عندي: لا يظهر
        // أظهر السجل فور وصول Realtime؛ الاسم/التفاعلات بيانات تزيينية تُدمج
        // لاحقاً بطلب مجمّع، فلا تنتظر الرسالة الجديدة ثلاث رحلات شبكة.
        val ui = toUiCached(message)
        val updated = _messages.value + ui
        _messages.value = updated
        loadInitialMetadata(
            channelId,
            listOf(message),
            updated.associate { it.message.id to it.message },
        )
        ChatPeek.note(
            message.channelId,
            message.createdAt,
            message.content,
            message.messageType,
            ui.author?.username,
        )
        if (message.userId != container.supabase.session()?.userId &&
            !_newMessagesBelow.value
        ) {
            // يعرض الفاصل عند أول رسالة جديدة، وتبقى الشارة حتى يمرّر المستخدم للأسفل.
            _newDividerId.value = message.id
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
                        ChatPeek.note(
                            message.channelId,
                            message.createdAt,
                            message.content,
                            message.messageType,
                            _authors.value[message.userId]?.username,
                        )
                    } else if (message.userId !in _blockedIds.value &&
                        message.userId !in _mutedIds.value &&
                        list.none { it.message.id == message.id }
                    ) {
                        mapped += toUiCached(message)
                    }
                }
                if (mapped.isNotEmpty() || list !== current) {
                    val updated = list + mapped
                    _messages.value = updated
                    if (mapped.isNotEmpty()) {
                        loadInitialMetadata(
                            channelId,
                            mapped.map { it.message },
                            updated.associate { it.message.id to it.message },
                        )
                    }
                }
                mapped.lastOrNull()?.let { lastUi ->
                    val last = lastUi.message
                    ChatPeek.note(
                        last.channelId,
                        last.createdAt,
                        last.content,
                        last.messageType,
                        lastUi.author?.username,
                    )
                }
                // رسائل الآخرين فقط ترفع شارة «رسائل بالأسفل» — لا رسائلي أنا
                // (ولا نُسنِد القيمة إن كانت مرفوعة أصلاً: كل إسناد = إعادة تركيب)
                val firstIncoming = mapped.firstOrNull { it.message.userId != myId }
                if (firstIncoming != null && !_newMessagesBelow.value) {
                    _newDividerId.value = firstIncoming.message.id
                    _newMessagesBelow.value = true
                }
            }
        }
    }

    /** بناء فوري من الذاكرة فقط؛ [loadInitialMetadata] يملأ البيانات الاختيارية لاحقاً. */
    private fun toUiCached(message: ChatMessage): ChatMessageUi {
        val authors = _authors.value
        val reply = message.replyToId?.let { id ->
            _messages.value.firstOrNull { it.message.id == id }?.message
        }
        return ChatMessageUi(
            message = message,
            author = authors[message.userId],
            replyTo = reply,
            replyAuthorName = reply?.let { authors[it.userId]?.username },
        )
    }

    fun loadOlder() {
        val channel = _channel.value ?: return
        val beforeId = olderCursorId ?: return
        if (reachedOldestPage || olderJob?.isActive == true) return

        olderJob = scope.launch {
            try {
                val page = container.chat.messages(channel.id, beforeId = beforeId, limit = PAGE_SIZE)
                if (_channel.value?.id != channel.id) return@launch
                if (page.isEmpty()) {
                    reachedOldestPage = true
                    return@launch
                }
                olderCursorId = page.first().id
                reachedOldestPage = page.size < PAGE_SIZE

                val older = page.filterNot {
                    it.userId in _blockedIds.value || it.userId in _mutedIds.value
                }
                if (older.isEmpty()) return@launch

                // صفحة من 100 رسالة كانت تستدعي reactions + myReactions لكل
                // رسالة (حتى 200 طلب). الآن ثلاثة طلبات مجمّعة ومتوازية فقط.
                val messageIds = older.map { it.id }
                val (profiles, reactions, myReactions) = coroutineScope {
                    val profileRequest = async { loadAuthorProfiles(older.map { it.userId }) }
                    val reactionsRequest = async {
                        runCatching { container.chat.reactionsFor(messageIds) }.getOrDefault(emptyMap())
                    }
                    val mineRequest = async {
                        runCatching { container.chat.myReactionsFor(messageIds) }.getOrDefault(emptySet())
                    }
                    Triple(profileRequest.await(), reactionsRequest.await(), mineRequest.await())
                }
                if (_channel.value?.id != channel.id) return@launch

                val authors = _authors.value + profiles
                _authors.value = authors
                val mineByMessage = mutableMapOf<Long, MutableSet<String>>()
                myReactions.forEach { (messageId, emoji) ->
                    mineByMessage.getOrPut(messageId) { linkedSetOf() }.add(emoji)
                }
                val current = _messages.value
                val allById = (older + current.map { it.message }).associateBy { it.id }
                val existingIds = current.asSequence().map { it.message.id }.toHashSet()
                val mapped = older
                    .asSequence()
                    .filterNot { it.id in existingIds }
                    .map { message ->
                        val reply = message.replyToId?.let(allById::get)
                        ChatMessageUi(
                            message = message,
                            author = authors[message.userId],
                            replyTo = reply,
                            replyAuthorName = reply?.let { authors[it.userId]?.username },
                            reactions = reactions[message.id].orEmpty(),
                            myReactions = mineByMessage[message.id].orEmpty(),
                        )
                    }
                    .toList()
                if (mapped.isNotEmpty()) _messages.value = mapped + current
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // يبقى موضع القائمة كما هو، ويمكن للمستخدم إعادة المحاولة بالتمرير.
            }
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
        _newDividerId.value = null

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
        val revision = (reactionChangeRevision[messageId] ?: 0L) + 1L
        reactionChangeRevision[messageId] = revision
        scope.launch {
            container.chat.toggleReaction(messageId, emoji)
            // Re-read the server state on success or failure. The revision also
            // keeps an older initial-load snapshot from replacing this result.
            val reactions = runCatching {
                container.chat.reactionsFor(listOf(messageId))[messageId] ?: emptyMap()
            }.getOrDefault(emptyMap())
            val mine = runCatching {
                container.chat.myReactionsFor(listOf(messageId))
                    .filter { it.first == messageId }.map { it.second }.toSet()
            }.getOrDefault(emptySet())
            if (reactionChangeRevision[messageId] != revision) return@launch
            _messages.value = _messages.value.map { ui ->
                if (ui.message.id == messageId) ui.copy(reactions = reactions, myReactions = mine) else ui
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
        if (!screenActive || typingJob?.isActive == true) return
        val wait = (lastTypingSentAt + TYPING_REFRESH_MS - SystemClock.elapsedRealtime())
            .coerceAtLeast(0L)
        typingJob = scope.launch {
            if (wait > 0L) delay(wait)
            if (screenActive && _channel.value?.id == channel.id) {
                container.chat.setTyping(channel.id)
                lastTypingSentAt = SystemClock.elapsedRealtime()
            }
        }
    }

    fun markReadUpToBottom() {
        val channel = _channel.value ?: return
        val last = _messages.value.lastOrNull { it.message.id > 0L }?.message?.id ?: return
        lastReadId = maxOf(lastReadId, last)
        _newMessagesBelow.value = false
        _newDividerId.value = null
        enqueueReadReceipt(channel.id, lastReadId)
    }

    fun loadPinned(channelId: String) {
        scope.launch {
            val pinned = container.chat.pinned(channelId)
            if (_channel.value?.id == channelId && _pinned.value != pinned) {
                _pinned.value = pinned
            }
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
        openJob?.cancel()
        pollJob?.cancel()
        mutedUsersJob?.cancel()
        readJob?.cancel()
        typingJob?.cancel()
        olderJob?.cancel()
        container.realtime.close()
        _online.value = 0
        scope.cancel()
    }

    private companion object {
        const val PAGE_SIZE = 100
        const val READ_RECEIPT_DEBOUNCE_MS = 300L
        const val TYPING_REFRESH_MS = 4_000L
    }
}
