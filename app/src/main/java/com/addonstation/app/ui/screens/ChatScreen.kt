package com.addonstation.app.ui.screens

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.addonstation.app.AppContainer
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.appViewModel
import com.addonstation.app.core.ChatChannel
import com.addonstation.app.core.ChatImage
import com.addonstation.app.core.ChatMessage
import com.addonstation.app.core.ChatMute
import com.addonstation.app.core.ChatReaction
import com.addonstation.app.core.Constants
import com.addonstation.app.core.ItemCatalog
import com.addonstation.app.core.Profile
import com.addonstation.app.core.StickerCatalog
import com.addonstation.app.core.TimeAgo
import com.addonstation.app.core.extractMentions
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.components.Avatar
import com.addonstation.app.ui.components.ConnectDeviceDialog
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.FullImageDialog
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.MentionText
import com.addonstation.app.ui.components.MuteDialog
import com.addonstation.app.ui.components.ReportDialog
import com.addonstation.app.ui.components.RulesDialog
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.components.UserMiniSheet
import com.addonstation.app.ui.components.Username
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import com.addonstation.app.ui.theme.LocalExtra
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

suspend fun readUriBytes(ctx: Context, uri: Uri): Pair<ByteArray, String?> {
    val mime = ctx.contentResolver.getType(uri)
    val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
    return bytes to mime
}

private object LastChannel {
    var id: Long? = null
}

class ChatViewModel(
    private val app: AppContainer,
    channelArg: Long?,
    private val messageArg: Long?
) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val channels: List<ChatChannel> = emptyList(),
        val channel: ChatChannel? = null,
        val messages: List<ChatMessage> = emptyList(),
        val profiles: Map<String, Profile> = emptyMap(),
        val reactions: Map<Long, List<ChatReaction>> = emptyMap(),
        val quotes: Map<Long, ChatMessage> = emptyMap(),
        val images: Map<Long, ChatImage> = emptyMap(),
        val mediaUrls: Map<String, String> = emptyMap(),
        val rulesAccepted: Boolean = false,
        val rulesChecked: Boolean = false,
        val mute: ChatMute? = null,
        val members: List<Profile> = emptyList(),
        val onlineIds: Set<String> = emptySet(),
        val highlightId: Long? = null,
        val busy: Boolean = false,
        val toast: String? = null,
        val searching: Boolean = false,
        val searchResults: List<ChatMessage> = emptyList()
    )

    private val _ui = MutableStateFlow(Ui(highlightId = messageArg))
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var rtJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                val channels = app.chat.channels()
                val pick = channels.firstOrNull { it.id == (channelArg ?: LastChannel.id) }
                    ?: channels.firstOrNull { it.slug == "general" }
                    ?: channels.firstOrNull()
                _ui.value = _ui.value.copy(channels = channels, channel = pick)
                if (pick != null) {
                    loadChannel(pick)
                } else {
                    _ui.value = _ui.value.copy(loading = false)
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun selectChannel(ch: ChatChannel) {
        if (ch.id == _ui.value.channel?.id) return
        LastChannel.id = ch.id
        _ui.value = Ui(channels = _ui.value.channels, channel = ch, rulesChecked = _ui.value.rulesChecked, rulesAccepted = _ui.value.rulesAccepted)
        loadChannel(ch)
    }

    private fun loadChannel(ch: ChatChannel) {
        rtJob?.cancel()
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null, searchResults = emptyList(), searching = false)
            try {
                val uid = app.auth.currentUserId()
                if (uid != null && !_ui.value.rulesChecked) {
                    val accepted = app.chat.hasAcceptedRules(uid)
                    val mute = app.chat.activeMute(uid)
                    _ui.value = _ui.value.copy(rulesAccepted = accepted, rulesChecked = true, mute = mute)
                }
                val msgs = app.chat.messages(ch.id)
                _ui.value = _ui.value.copy(messages = msgs, loading = false)
                hydrate(msgs)
                loadMembers()
                // Live updates
                rtJob = viewModelScope.launch {
                    try {
                        app.realtime.chatMessages(ch.id).collect { change ->
                            applyChange(change.type, change.record)
                        }
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    private suspend fun hydrate(msgs: List<ChatMessage>) {
        val ids = msgs.mapNotNull { it.userId }.toSet()
        val replyIds = msgs.mapNotNull { it.replyToId }.toSet()
        val quotes = app.chat.byIds(replyIds)
        val quoteAuthors = quotes.values.mapNotNull { it.userId }.toSet()
        val profiles = try {
            app.profiles.byIds(ids + quoteAuthors) + _ui.value.profiles
        } catch (_: Exception) { _ui.value.profiles }
        val reactions = try {
            app.chat.reactionsFor(msgs.map { it.id }).groupBy { it.messageId }
        } catch (_: Exception) { emptyMap() }
        val images = try {
            app.chat.imagesFor(msgs.map { it.id })
        } catch (_: Exception) { emptyMap() }
        _ui.value = _ui.value.copy(profiles = profiles, quotes = quotes, reactions = reactions, images = images)
        // Resolve signed media urls
        val paths = (msgs.mapNotNull { it.mediaPath } + images.values.map { it.imagePath }).distinct()
            .filter { !_ui.value.mediaUrls.containsKey(it) }
        for (p in paths) {
            try {
                val url = if (images.values.any { it.imagePath == p }) app.chat.chatImageSignedUrl(p)
                else app.chat.mediaSignedUrl(p)
                _ui.value = _ui.value.copy(mediaUrls = _ui.value.mediaUrls + (p to url))
            } catch (_: Exception) { }
        }
    }

    private suspend fun applyChange(type: String, record: kotlinx.serialization.json.JsonObject?) {
        if (record == null) return
        try {
            val msg = app.backend.json.decodeFromJsonElement<ChatMessage>(record)
            val cur = _ui.value
            if (msg.channelId != null && msg.channelId != cur.channel?.id) return
            when (type) {
                "INSERT" -> {
                    if (cur.messages.any { it.id == msg.id }) return
                    val added = (cur.messages + msg).sortedWith(compareBy({ TimeAgo.millis(it.createdAt) ?: 0L }, { it.id }))
                    _ui.value = cur.copy(messages = added)
                    hydrate(listOf(msg))
                }
                "UPDATE" -> {
                    _ui.value = cur.copy(messages = cur.messages.map { if (it.id == msg.id) msg else it })
                }
                "DELETE" -> {
                    val delId = (record["id"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.toLongOrNull()
                        ?: msg.id
                    _ui.value = cur.copy(messages = cur.messages.filterNot { it.id == delId })
                }
            }
        } catch (_: Exception) { }
    }

    private suspend fun loadMembers() {
        try {
            val online = app.profiles.onlineMembers(50)
            val all = app.profiles.allMembers(200)
            val merged = (online + all).distinctBy { it.id }
            _ui.value = _ui.value.copy(members = merged, onlineIds = online.map { it.id }.toSet())
        } catch (_: Exception) { }
    }

    fun refresh() {
        _ui.value.channel?.let { loadChannel(it) }
    }

    fun acceptRules() {
        val uid = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            try {
                app.chat.acceptRules(uid)
                _ui.value = _ui.value.copy(rulesAccepted = true)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun send(content: String, replyTo: Long?, threadRoot: Long?) {
        val ch = _ui.value.channel ?: return
        val uid = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try {
                val sent = app.chat.send(ch.id, content, null, replyTo, threadRoot)
                var sentId = sent?.id
                if (sentId == null) {
                    // Fallback: find our newest matching message for mention links
                    val fresh = app.chat.messages(ch.id)
                    sentId = fresh.lastOrNull { it.userId == uid && it.content == content }?.id
                    _ui.value = _ui.value.copy(messages = fresh)
                    hydrate(fresh)
                }
                val mentions = extractMentions(content)
                if (mentions.isNotEmpty()) {
                    app.chat.notifyMentions(uid, ch.id, sentId, mentions, app.profiles)
                }
                try {
                    app.wallet.claimCommunityParticipation(sentId)
                } catch (_: Exception) { }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }

    fun sendSticker(tab: String, file: String) {
        val ch = _ui.value.channel ?: return
        viewModelScope.launch {
            try {
                app.chat.send(ch.id, "", "/stickers/$tab/$file", null, null)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun sendImage(bytes: ByteArray, mime: String, name: String) {
        val ch = _ui.value.channel ?: return
        val uid = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try {
                val sent = try {
                    app.chat.sendMedia(ch.id, bytes, mime, name)
                } catch (_: Exception) {
                    app.chat.sendImageLegacy(uid, ch.id, bytes, mime, name)
                }
                if (sent != null) {
                    val fresh = app.chat.messages(ch.id)
                    _ui.value = _ui.value.copy(messages = fresh)
                    hydrate(fresh)
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            } finally {
                _ui.value = _ui.value.copy(busy = false)
            }
        }
    }

    fun toggleReaction(msgId: Long, emoji: String) {
        val uid = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            try {
                app.chat.toggleReaction(msgId, uid, emoji)
                val all = app.chat.reactionsFor(_ui.value.messages.map { it.id })
                _ui.value = _ui.value.copy(reactions = all.groupBy { it.messageId })
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun deleteMessage(id: Long) {
        viewModelScope.launch {
            try {
                app.chat.deleteMessage(id)
                _ui.value = _ui.value.copy(messages = _ui.value.messages.filterNot { it.id == id })
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun report(id: Long, reason: String) {
        val uid = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            try {
                app.chat.report(id, uid, reason)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun mute(userId: String, minutes: Int) {
        val me = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            try {
                app.chat.muteUser(userId, minutes, me)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun search(q: String) {
        val ch = _ui.value.channel ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(searching = true)
            try {
                _ui.value = _ui.value.copy(searchResults = app.chat.search(ch.id, q))
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            } finally {
                _ui.value = _ui.value.copy(searching = false)
            }
        }
    }

    fun clearSearch() {
        _ui.value = _ui.value.copy(searchResults = emptyList())
    }

    fun setHighlight(id: Long?) {
        _ui.value = _ui.value.copy(highlightId = id)
    }

    fun consumeToast() {
        _ui.value = _ui.value.copy(toast = null)
    }

    override fun onCleared() {
        rtJob?.cancel()
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChatScreen(nav: NavHostController, vm: AppViewModel, channelArg: Long?, messageArg: Long?) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val cvm: ChatViewModel = appViewModel { ChatViewModel(app, channelArg, messageArg) }
    val ui by cvm.ui.collectAsState()
    val session by vm.session.collectAsState()
    val myId = session.userId
    val iAmMod = session.profile?.isStaff == true

    var composer by remember { mutableStateOf(TextFieldValue()) }
    var replyTo by remember { mutableStateOf<ChatMessage?>(null) }
    var showStickers by remember { mutableStateOf(false) }
    var showMembers by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var quickProfile by remember { mutableStateOf<Profile?>(null) }
    var friendStatus by remember { mutableStateOf<String?>(null) }
    var fullImage by remember { mutableStateOf<String?>(null) }
    var pendingImage by remember { mutableStateOf<Triple<ByteArray, String, String>?>(null) }
    var reportTarget by remember { mutableStateOf<ChatMessage?>(null) }
    var muteTarget by remember { mutableStateOf<ChatMessage?>(null) }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val atBottom by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            last == null || last >= ui.messages.size - 3
        }
    }

    // Initial scroll to bottom / highlight target
    LaunchedEffect(ui.messages.size) {
        if (ui.messages.isNotEmpty() && ui.highlightId == null) {
            listState.scrollToItem(ui.messages.size - 1)
        }
    }
    LaunchedEffect(ui.highlightId, ui.messages.size) {
        val target = ui.highlightId ?: return@LaunchedEffect
        val idx = ui.messages.indexOfFirst { it.id == target }
        if (idx >= 0) listState.scrollToItem(maxOf(0, idx - 2))
    }

    ui.toast?.let {
        LaunchedEffect(it) {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            cvm.consumeToast()
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, mime) = readUriBytes(ctx, uri)
            val m = mime ?: "image/jpeg"
            if (m !in Constants.CHAT_IMAGE_MIME) {
                Toast.makeText(ctx, ctx.getString(R.string.chat_image_rule), Toast.LENGTH_LONG).show()
                return@launch
            }
            if (bytes.size > Constants.CHAT_IMAGE_MAX_BYTES) {
                Toast.makeText(ctx, ctx.getString(R.string.chat_image_large), Toast.LENGTH_LONG).show()
                return@launch
            }
            val name = uri.lastPathSegment?.substringAfterLast("/")?.takeLast(40) ?: "chat.jpg"
            pendingImage = Triple(bytes, m, name)
        }
    }

    fun openQuickProfile(p: Profile?) {
        if (p == null) return
        quickProfile = p
        friendStatus = null
        if (myId != null && myId != p.id) {
            scope.launch {
                friendStatus = try {
                    app.profiles.friendStatus(myId, p.id)
                } catch (_: Exception) { null }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = ui.channel?.let { "#${it.slug}" } ?: stringResource(R.string.nav_community),
            titleIcon = R.drawable.ic_community, vm = vm,
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        // Channels row + actions
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            LazyRow(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                itemsIndexed(ui.channels) { _, ch ->
                    FilterChip(
                        selected = ch.id == ui.channel?.id,
                        onClick = { cvm.selectChannel(ch) },
                        label = { Text("#${ch.slug}") }
                    )
                }
            }
            IconButton(onClick = { showSearch = !showSearch }) {
                Text("🔍", fontSize = MaterialTheme.typography.titleMedium.fontSize)
            }
            IconButton(onClick = { showMembers = true }) {
                Text("👥", fontSize = MaterialTheme.typography.titleMedium.fontSize)
            }
        }
        if (showSearch) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it; if (it.length >= 2) cvm.search(it) else cvm.clearSearch() },
                placeholder = { Text(stringResource(R.string.chat_search_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
            )
            if (ui.searchResults.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 220.dp)) {
                    itemsIndexed(ui.searchResults) { _, m ->
                        val p = ui.profiles[m.userId]
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                cvm.setHighlight(m.id)
                                showSearch = false
                            }.padding(10.dp)
                        ) {
                            Text("@${p?.username ?: "?"}: ${m.content.take(80)}", style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                }
            }
        }
        // Rules / login / mute banners
        if (myId != null && ui.rulesChecked && !ui.rulesAccepted) {
            RulesDialog(onAccept = { cvm.acceptRules() }, onReject = { nav.navigate(Routes.HOME) })
        }
        if (myId == null) {
            Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(12.dp)) {
                TextButton(onClick = { nav.navigate(Routes.LOGIN) }) { Text(stringResource(R.string.chat_login_to_chat)) }
            }
        }
        ui.mute?.let { m ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth().padding(8.dp), shape = RoundedCornerShape(12.dp)) {
                Text(
                    stringResource(R.string.chat_muted_notice, m.expiresAt?.substringBefore("T") ?: ""),
                    modifier = Modifier.padding(10.dp),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
        // Messages
        Box(Modifier.weight(1f)) {
            when {
                ui.loading -> LoadingBox(Modifier.fillMaxSize())
                ui.error != null -> ErrorBox(ui.error!!, { cvm.refresh() }, Modifier.fillMaxSize())
                ui.messages.isEmpty() -> EmptyBox(stringResource(R.string.chat_no_messages), Modifier.fillMaxSize())
                else -> {
                    val dayGroups = remember(ui.messages) {
                        val out = mutableListOf<Pair<String?, ChatMessage>>()
                        var lastDay: String? = null
                        ui.messages.forEach { m ->
                            val day = TimeAgo.dayHeader(m.createdAt, ctx)
                            if (day != lastDay) {
                                out += (day to m.copy(id = Long.MIN_VALUE))
                                lastDay = day
                            }
                            out += (null to m)
                        }
                        out
                    }
                    LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
                        itemsIndexed(dayGroups) { _, (day, m) ->
                            if (day != null && m.id == Long.MIN_VALUE) {
                                Box(Modifier.fillMaxWidth().padding(vertical = 6.dp), contentAlignment = Alignment.Center) {
                                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = CircleShape) {
                                        Text(day, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
                                    }
                                }
                            } else {
                                val reply = m.replyToId?.let { ui.quotes[it] }
                                MessageBubble(
                                    msg = m,
                                    profile = ui.profiles[m.userId],
                                    reply = reply,
                                    replyProfile = reply?.userId?.let { ui.profiles[it] },
                                    reactions = ui.reactions[m.id].orEmpty(),
                                    myId = myId,
                                    imageUrl = imageUrlFor(m, ui),
                                    highlight = m.id == ui.highlightId,
                                    iAmMod = iAmMod,
                                    onAvatar = { openQuickProfile(ui.profiles[m.userId]) },
                                    onMention = { name ->
                                        val target = ui.members.firstOrNull { it.username.equals(name, true) }
                                        openQuickProfile(target)
                                    },
                                    onReplyQuote = { rid ->
                                        val idx = ui.messages.indexOfFirst { it.id == rid }
                                        if (idx >= 0) {
                                            cvm.setHighlight(rid)
                                            scope.launch { listState.scrollToItem(maxOf(0, idx - 2)) }
                                        }
                                    },
                                    onImage = { fullImage = it },
                                    onReply = { replyTo = m },
                                    onThread = { nav.navigate(Routes.thread(threadRootOf(m))) },
                                    onCopy = {
                                        com.addonstation.app.core.copyToClipboard(ctx, m.content)
                                        Toast.makeText(ctx, ctx.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                                    },
                                    onReact = { emoji -> cvm.toggleReaction(m.id, emoji) },
                                    onReport = { reportTarget = m },
                                    onDelete = { cvm.deleteMessage(m.id) },
                                    onMute = { muteTarget = m }
                                )
                            }
                        }
                    }
                    if (!atBottom && ui.messages.isNotEmpty()) {
                        FloatingActionButton(
                            onClick = { scope.launch { listState.scrollToItem(ui.messages.size - 1) } },
                            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
                        ) { Text("↓") }
                    }
                }
            }
        }
        // Composer
        if (myId != null && ui.mute == null) {
            replyTo?.let { r ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("↩ ${r.content.take(60).ifBlank { "📷" }}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f), maxLines = 1)
                    TextButton(onClick = { replyTo = null }) { Text("✕") }
                }
            }
            // @mention autocomplete
            val mentionQuery = remember(composer.text, composer.selection) {
                val cursor = composer.selection.start.coerceIn(0, composer.text.length)
                val before = composer.text.substring(0, cursor)
                Regex("@([A-Za-z0-9_.\\-]{1,30})$").find(before)?.groupValues?.get(1)
            }
            if (mentionQuery != null) {
                val suggestions = remember(mentionQuery, ui.members) {
                    ui.members.filter { it.username.contains(mentionQuery, true) }.take(5)
                }
                if (suggestions.isNotEmpty()) {
                    LazyColumn(Modifier.fillMaxWidth().heightIn(max = 180.dp).padding(horizontal = 12.dp)) {
                        itemsIndexed(suggestions) { _, s ->
                            Row(
                                Modifier.fillMaxWidth().clickable {
                                    val cursor = composer.selection.start
                                    val before = composer.text.substring(0, cursor)
                                    val at = before.lastIndexOf("@")
                                    val newText = composer.text.substring(0, at) + "@${s.username} " + composer.text.substring(cursor)
                                    composer = TextFieldValue(newText, TextRange(at + s.username.length + 2))
                                }.padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Avatar(url = s.avatarUrl, name = s.username, size = 26.dp)
                                Spacer(Modifier.width(8.dp))
                                Text("@${s.username}")
                            }
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
                IconButton(onClick = { showStickers = !showStickers }) { Text("😀", fontSize = MaterialTheme.typography.headlineSmall.fontSize) }
                IconButton(onClick = { picker.launch("image/*") }) { Text("🖼️", fontSize = MaterialTheme.typography.headlineSmall.fontSize) }
                OutlinedTextField(
                    value = composer,
                    onValueChange = { composer = it },
                    placeholder = { Text(stringResource(R.string.chat_type_message)) },
                    modifier = Modifier.weight(1f),
                    maxLines = 5
                )
                Spacer(Modifier.width(6.dp))
                Button(
                    onClick = {
                        val text = composer.text.trim()
                        if (text.isEmpty()) return@Button
                        val root = replyTo?.let { threadRootOf(it) }?.takeIf { it != replyTo!!.id }
                        cvm.send(text, replyTo?.id, root)
                        composer = TextFieldValue()
                        replyTo = null
                        scope.launch { listState.scrollToItem(ui.messages.size) }
                    },
                    enabled = composer.text.isNotBlank() && !ui.busy
                ) {
                    if (ui.busy) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    else Text("➤")
                }
            }
            if (showStickers) {
                StickerPanel(
                    onPick = { tab, file ->
                        cvm.sendSticker(tab, file)
                        showStickers = false
                    },
                    onClose = { showStickers = false }
                )
            }
        }
    }

    // Sheets & dialogs
    if (showMembers) {
        MembersSheet(
            members = ui.members,
            onlineIds = ui.onlineIds,
            onPick = { showMembers = false; openQuickProfile(it) },
            onDismiss = { showMembers = false }
        )
    }
    quickProfile?.let { p ->
        UserMiniSheet(
            profile = p,
            friendStatus = friendStatus,
            isMe = p.id == myId,
            onViewProfile = {
                quickProfile = null
                if (p.id == myId) nav.navigate(Routes.PROFILE) else nav.navigate(Routes.user(p.id))
            },
            onFriend = {
                scope.launch {
                    try {
                        app.profiles.sendFriendRequest(myId!!, p.id)
                        friendStatus = "pending"
                        Toast.makeText(ctx, ctx.getString(R.string.fr_sent), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onMention = {
                composer = TextFieldValue(composer.text + "@${p.username} ", TextRange(composer.text.length + p.username.length + 2))
                quickProfile = null
            },
            onDismiss = { quickProfile = null }
        )
    }
    fullImage?.let { FullImageDialog(it) { fullImage = null } }
    pendingImage?.let { (bytes, mime, name) ->
        PendingImageDialog(
            bytes = bytes,
            onSend = {
                cvm.sendImage(bytes, mime, name)
                pendingImage = null
            },
            onDismiss = { pendingImage = null }
        )
    }
    reportTarget?.let { m ->
        ReportDialog(
            onSubmit = {
                cvm.report(m.id, it)
                reportTarget = null
                Toast.makeText(ctx, ctx.getString(R.string.chat_report_sent), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { reportTarget = null }
        )
    }
    muteTarget?.let { m ->
        MuteDialog(
            onSubmit = { minutes ->
                m.userId?.let { uid -> cvm.mute(uid, minutes) }
                muteTarget = null
                Toast.makeText(ctx, ctx.getString(R.string.chat_muted), Toast.LENGTH_SHORT).show()
            },
            onDismiss = { muteTarget = null }
        )
    }
    DisposableEffect(Unit) {
        onDispose { cvm.setHighlight(null) }
    }
}

private fun threadRootOf(m: ChatMessage): Long = m.threadRootId ?: m.id

private fun imageUrlFor(m: ChatMessage, ui: ChatViewModel.Ui): String? {
    m.imageUrl?.takeIf { it.isNotBlank() }?.let {
        return if (it.startsWith("http")) it else Constants.resolveAsset(it)
    }
    m.mediaPath?.let { ui.mediaUrls[it]?.let { url -> return url } }
    val img = ui.images[m.id]
    img?.let { ui.mediaUrls[it.imagePath]?.let { url -> return url } }
    return null
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: ChatMessage,
    profile: Profile?,
    reply: ChatMessage?,
    replyProfile: Profile?,
    reactions: List<ChatReaction>,
    myId: String?,
    imageUrl: String?,
    highlight: Boolean,
    iAmMod: Boolean,
    onAvatar: () -> Unit,
    onMention: (String) -> Unit,
    onReplyQuote: (Long) -> Unit,
    onImage: (String) -> Unit,
    onReply: () -> Unit,
    onThread: () -> Unit,
    onCopy: () -> Unit,
    onReact: (String) -> Unit,
    onReport: () -> Unit,
    onDelete: () -> Unit,
    onMute: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var reactOpen by remember { mutableStateOf(false) }
    val extra = LocalExtra.current
    val bg = if (highlight) extra.diamond.copy(alpha = 0.14f) else Color.Transparent
    Row(
        Modifier.fillMaxWidth().background(bg).combinedClickable(onClick = {}, onLongClick = { menu = true })
            .padding(horizontal = 12.dp, vertical = 5.dp)
    ) {
        Box(Modifier.clickable { onAvatar() }) {
            Avatar(url = profile?.avatarUrl, name = profile?.username ?: "?", size = 38.dp)
        }
        Spacer(Modifier.width(8.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Username(profile = profile, fontSizeSp = 13, modifier = Modifier.weight(1f, fill = false))
                Spacer(Modifier.width(6.dp))
                Text(
                    TimeAgo.label(msg.createdAt, LocalContext.current),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            reply?.let { r ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.padding(vertical = 3.dp).clickable { onReplyQuote(r.id) }
                ) {
                    Text(
                        "↩ @${replyProfile?.username ?: "?"}: ${r.content.take(90).ifBlank { "📷" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
            if (!msg.stickerUrl.isNullOrBlank()) {
                AsImage(
                    model = Constants.resolveAsset(msg.stickerUrl),
                    contentDescription = "sticker",
                    modifier = Modifier.size(128.dp).padding(top = 2.dp),
                    contentScale = ContentScale.Fit
                )
            } else if (imageUrl != null) {
                AsImage(
                    model = imageUrl,
                    contentDescription = "image",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp))
                        .clickable { onImage(imageUrl) }
                        .padding(top = 2.dp)
                )
            } else if (msg.content.isNotBlank()) {
                MentionText(content = msg.content, onMention = onMention)
            }
            if (msg.threadRootId != null) {
                TextButton(onClick = onThread, contentPadding = PaddingValues(0.dp)) {
                    Text("🧵 ${stringResource(R.string.chat_open_thread)}", style = MaterialTheme.typography.labelSmall)
                }
            }
            if (reactions.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(top = 3.dp)) {
                    reactions.groupBy { it.emoji }.forEach { (emoji, list) ->
                        val mine = list.any { it.userId == myId }
                        Surface(
                            color = if (mine) extra.diamond.copy(alpha = 0.25f) else MaterialTheme.colorScheme.surfaceVariant,
                            shape = CircleShape,
                            modifier = Modifier.clickable { onReact(emoji) }
                        ) {
                            Text("$emoji ${list.size}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                        }
                    }
                }
            }
            if (reactOpen) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(top = 4.dp)) {
                    listOf("👍", "❤️", "😂", "😮", "😢", "🔥", "👏", "💎", "👑", "🍃").forEach { e ->
                        Text(e, modifier = Modifier.clickable { onReact(e); reactOpen = false }.padding(4.dp))
                    }
                }
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("↩ ${stringResource(R.string.chat_reply)}") }, onClick = { menu = false; onReply() })
                DropdownMenuItem(text = { Text("🧵 ${stringResource(R.string.chat_open_thread)}") }, onClick = { menu = false; onThread() })
                DropdownMenuItem(text = { Text("😀 ${stringResource(R.string.chat_react)}") }, onClick = { menu = false; reactOpen = true })
                DropdownMenuItem(text = { Text("📋 ${stringResource(R.string.chat_copy)}") }, onClick = { menu = false; onCopy() })
                DropdownMenuItem(text = { Text("👤 ${stringResource(R.string.chat_view_profile)}") }, onClick = { menu = false; onAvatar() })
                DropdownMenuItem(text = { Text("🚩 ${stringResource(R.string.chat_report)}") }, onClick = { menu = false; onReport() })
                if (iAmMod) {
                    DropdownMenuItem(text = { Text("🗑 ${stringResource(R.string.chat_delete_message)}") }, onClick = { menu = false; onDelete() })
                    DropdownMenuItem(text = { Text("🔇 ${stringResource(R.string.chat_mute_user)}") }, onClick = { menu = false; onMute() })
                }
            }
        }
    }
}

@Composable
private fun StickerPanel(onPick: (String, String) -> Unit, onClose: () -> Unit) {
    var tab by remember { mutableStateOf(0) }
    val tabs = remember { StickerCatalog.tabs }
    val labels = remember {
        mapOf(
            "meme" to "Meme",
            "Minecraft_Memes1" to "Memes 1",
            "Minecraft_Memes2" to "Memes 2",
            "Minecraft_Memes3" to "Memes 3",
            "Minecraft_Movie" to "Movie"
        )
    }
    Column(Modifier.fillMaxWidth().height(300.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.chat_stickers), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 12.dp).weight(1f))
            TextButton(onClick = onClose) { Text("✕") }
        }
        TabRow(selectedTabIndex = tab) {
            tabs.forEachIndexed { i, t ->
                Tab(
                    selected = tab == i,
                    onClick = { tab = i },
                    text = { Text(labels[t.id] ?: t.id, fontSize = MaterialTheme.typography.labelSmall.fontSize) },
                    icon = {
                        AsImage(
                            model = StickerCatalog.assetUri(t, t.files.first()),
                            contentDescription = null,
                            modifier = Modifier.size(30.dp),
                            contentScale = ContentScale.Fit
                        )
                    }
                )
            }
        }
        LazyVerticalGrid(columns = GridCells.Fixed(4), contentPadding = PaddingValues(8.dp)) {
            items(tabs[tab].files) { f ->
                AsImage(
                    model = StickerCatalog.assetUri(tabs[tab], f),
                    contentDescription = f,
                    modifier = Modifier.size(76.dp).clickable { onPick(tabs[tab].id, f) }.padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MembersSheet(members: List<Profile>, onlineIds: Set<String>, onPick: (Profile) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text(
            "${stringResource(R.string.chat_members)} (${members.size})",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), contentPadding = PaddingValues(12.dp)) {
            itemsIndexed(members.sortedByDescending { onlineIds.contains(it.id) }) { _, m ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(m) }.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(url = m.avatarUrl, name = m.username, size = 38.dp, online = onlineIds.contains(m.id))
                    Spacer(Modifier.width(10.dp))
                    Username(profile = m, fontSizeSp = 14, modifier = Modifier.weight(1f))
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PendingImageDialog(bytes: ByteArray, onSend: () -> Unit, onDismiss: () -> Unit) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Card {
            Column(Modifier.padding(16.dp)) {
                Text(stringResource(R.string.chat_image_rule), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                Box(Modifier.fillMaxWidth().height(240.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                    Text("🖼️ ${(bytes.size / 1024)} KB")
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onSend, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.chat_send)) }
                    TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Thread view
// ---------------------------------------------------------------------------

class ThreadViewModel(app: AppContainer, rootId: Long) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val messages: List<ChatMessage> = emptyList(),
        val profiles: Map<String, Profile> = emptyMap(),
        val error: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private val chat = app.chat
    private val profiles = app.profiles
    private val channelId = MutableStateFlow<Long?>(null)

    init {
        viewModelScope.launch {
            try {
                val msgs = chat.thread(rootId)
                channelId.value = msgs.firstOrNull()?.channelId
                val ids = msgs.mapNotNull { it.userId }.toSet()
                _ui.value = Ui(false, msgs, profiles.byIds(ids))
            } catch (e: Exception) {
                _ui.value = Ui(false, emptyList(), emptyMap(), e.message)
            }
        }
    }

    fun reply(content: String, onSent: () -> Unit) {
        val ch = channelId.value ?: return
        viewModelScope.launch {
            try {
                chat.send(ch, content, null, null, _ui.value.messages.firstOrNull()?.id)
                val msgs = chat.thread(_ui.value.messages.firstOrNull()?.id ?: return@launch)
                val ids = msgs.mapNotNull { it.userId }.toSet()
                _ui.value = _ui.value.copy(messages = msgs, profiles = profiles.byIds(ids))
                onSent()
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun ThreadScreen(nav: NavHostController, vm: AppViewModel, rootId: Long) {
    val app = LocalApp.current
    val tvm: ThreadViewModel = appViewModel { ThreadViewModel(app, rootId) }
    val ui by tvm.ui.collectAsState()
    var text by remember { mutableStateOf(TextFieldValue()) }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.chat_replies), titleIcon = R.drawable.ic_community,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, {}, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(vertical = 8.dp)) {
                itemsIndexed(ui.messages) { _, m ->
                    MessageBubble(
                        msg = m, profile = ui.profiles[m.userId], reply = null, replyProfile = null,
                        reactions = emptyList(), myId = app.auth.currentUserId(), imageUrl = null,
                        highlight = m.id == rootId, iAmMod = false,
                        onAvatar = {}, onMention = {}, onReplyQuote = {}, onImage = {},
                        onReply = {}, onThread = {}, onCopy = {},
                        onReact = {}, onReport = {}, onDelete = {}, onMute = {}
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.Bottom) {
            OutlinedTextField(
                value = text, onValueChange = { text = it },
                placeholder = { Text(stringResource(R.string.chat_reply)) },
                modifier = Modifier.weight(1f), maxLines = 4
            )
            Spacer(Modifier.width(6.dp))
            Button(
                onClick = {
                    val t = text.text.trim()
                    if (t.isEmpty()) return@Button
                    tvm.reply(t) { text = TextFieldValue() }
                },
                enabled = text.text.isNotBlank()
            ) { Text("➤") }
        }
    }
}
