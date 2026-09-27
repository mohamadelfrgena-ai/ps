package com.addonstation.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.addonstation.app.AppContainer
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.appViewModel
import com.addonstation.app.core.ChatNotification
import com.addonstation.app.core.Profile
import com.addonstation.app.core.TimeAgo
import com.addonstation.app.ui.components.Avatar
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.components.Username
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.OnResume
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class NotificationsViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val items: List<ChatNotification> = emptyList(),
        val actors: Map<String, Profile> = emptyMap(),
        val channels: Map<Long, Long> = emptyMap()
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun refresh(markRead: Boolean = false) {
        val uid = app.auth.currentUserId() ?: run {
            _ui.value = Ui(loading = false)
            return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val items = app.profiles.notifications(uid, 50)
                val actors = app.profiles.byIds(items.mapNotNull { it.actorId })
                val chMap = mutableMapOf<Long, Long>()
                items.mapNotNull { it.messageId }.distinct().forEach { mid ->
                    try {
                        app.profiles.messageChannel(mid)?.let { chMap[mid] = it }
                    } catch (_: Exception) { }
                }
                _ui.value = Ui(false, null, items, actors, chMap)
                if (markRead) {
                    try {
                        app.profiles.markAllRead(uid)
                    } catch (_: Exception) { }
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun accept(requesterId: String, onDone: () -> Unit) {
        val me = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            try {
                app.profiles.acceptFriend(requesterId, me)
                onDone()
                refresh()
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun NotificationsScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val nvm: NotificationsViewModel = appViewModel { NotificationsViewModel(app) }
    val ui by nvm.ui.collectAsState()
    val session by vm.session.collectAsState()

    LaunchedEffect(session.userId) {
        if (session.userId != null) nvm.refresh(markRead = true)
        vm.refreshUnread()
    }
    OnResume { if (session.userId != null) nvm.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.notifications_title),
            titleIcon = R.drawable.ic_bell, vm = vm, showBack = true,
            onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = {},
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        if (session.userId == null) {
            EmptyBox(stringResource(R.string.common_login_required), Modifier.weight(1f))
            return@Column
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.weight(1f))
            TextButton(onClick = { nvm.refresh(markRead = true); vm.refreshUnread() }) {
                Text(stringResource(R.string.nt_mark_read))
            }
        }
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { nvm.refresh() }, Modifier.weight(1f))
            ui.items.isEmpty() -> EmptyBox(stringResource(R.string.nt_empty), Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(12.dp)) {
                items(ui.items) { n ->
                    val actor = ui.actors[n.actorId]
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (n.isRead) MaterialTheme.colorScheme.surfaceVariant
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 1f)
                        ),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Avatar(url = actor?.avatarUrl, name = actor?.username ?: "?", size = 44.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Username(profile = actor, fontSizeSp = 13)
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        TimeAgo.label(n.createdAt, ctx),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    when (n.type) {
                                        "friend_request" -> stringResource(R.string.nt_friend_request)
                                        "mention" -> stringResource(R.string.nt_mention)
                                        else -> n.text ?: n.type
                                    },
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Spacer(Modifier.height(6.dp))
                                Row {
                                    if (n.type == "friend_request" && n.actorId != null) {
                                        Button(onClick = {
                                            nvm.accept(n.actorId) {
                                                Toast.makeText(ctx, ctx.getString(R.string.fr_now_friends), Toast.LENGTH_SHORT).show()
                                            }
                                        }) { Text(stringResource(R.string.nt_accept)) }
                                        Spacer(Modifier.width(8.dp))
                                    }
                                    if (n.type == "mention" && n.messageId != null) {
                                        val ch = ui.channels[n.messageId]
                                        OutlinedButton(onClick = {
                                            if (ch != null) nav.navigate(Routes.community(ch, n.messageId))
                                        }) { Text(stringResource(R.string.nt_open_chat)) }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
