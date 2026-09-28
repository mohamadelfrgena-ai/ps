package com.addonstation.mobile.ui.account

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.AcTask
import com.addonstation.mobile.data.model.AcTransaction
import com.addonstation.mobile.data.model.ChatNotification
import com.addonstation.mobile.data.model.CreatorTip
import com.addonstation.mobile.data.model.FriendEntry
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.ProfileRow
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.launch
import java.util.Locale

/** محفظة AC: الرصيد، الشرح، مهام الكسب مع زر المطالبة، وسجل المعاملات. */
@Composable
fun AcWalletScreen(
    onBack: () -> Unit,
    onOpenItem: (String) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val wallet by container.state.wallet.collectAsState()

    var tasks by remember { mutableStateOf<List<AcTask>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<AcTransaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        loading = true
        container.profile.refreshWallet()
        tasks = container.ac.tasks()
        transactions = container.ac.transactions()
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.ac_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            GlassCard(color = AppColors.Gold.copy(alpha = 0.08f), borderColor = AppColors.Gold.copy(alpha = 0.35f)) {
                Text(stringResource(R.string.ac_balance), style = AppText.tiny, color = AppColors.Muted)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "${wallet?.balance ?: 0}",
                        style = AppText.pageTitle,
                        color = AppColors.Gold,
                    )
                    Text("AC 🪙", style = AppText.sectionTitle, color = AppColors.Gold)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("+${wallet?.totalEarned ?: 0}", style = AppText.tiny, color = AppColors.Green)
                    Text("-${wallet?.totalSpent ?: 0}", style = AppText.tiny, color = AppColors.Red)
                }
                Text(stringResource(R.string.ac_desc), style = AppText.tiny, color = AppColors.Muted)
            }

            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Green)
            }

            SectionHeader(title = stringResource(R.string.ac_tasks), leadingEmoji = "🎯")
            if (loading) {
                LoadingBox()
            } else {
                tasks.forEach { task ->
                    GlassCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    task.titleEn ?: task.titleAr,
                                    style = AppText.bodySmall,
                                    color = AppColors.Text,
                                )
                                task.description?.let {
                                    Text(it, style = AppText.tiny, color = AppColors.Muted, maxLines = 2)
                                }
                                Text(
                                    "+${task.reward} AC · ${task.frequency}",
                                    style = AppText.tiny,
                                    color = AppColors.Gold,
                                )
                            }
                            GhostButton(
                                text = stringResource(R.string.ac_claim),
                                onClick = {
                                    scope.launch {
                                        container.ac.claimTask(task.id)
                                            .onSuccess { reward ->
                                                message = "+$reward AC"
                                                transactions = container.ac.transactions()
                                            }
                                            .onFailure { message = it.message }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionHeader(title = stringResource(R.string.ac_history), leadingEmoji = "🧾")
            if (transactions.isEmpty()) {
                EmptyState(stringResource(R.string.ac_no_transactions))
            } else {
                transactions.forEach { tx ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Elevated)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                tx.reason ?: tx.kind,
                                style = AppText.bodySmall,
                                color = AppColors.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                TimeFormat.dateTime(tx.createdAt, Locale.getDefault()),
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                        }
                        Text(
                            (if (tx.amount > 0) "+" else "") + tx.amount,
                            style = AppText.sectionTitle,
                            color = if (tx.amount > 0) AppColors.Green else AppColors.Red,
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        Text("${tx.balanceAfter}", style = AppText.tiny, color = AppColors.Muted)
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** الإشعارات: طلب صداقة (مع قبول)، منشن، رد، نشاط موضوع — مع وسم «جديد». */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenChannel: (String, Long?) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ChatNotification>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendEntry>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<String, com.addonstation.mobile.data.model.Profile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        items = container.notifications.list()
        requests = container.notifications.incomingFriendRequests()
        val ids = (items.mapNotNull { it.actorId } + requests.map { it.requesterId }).toSet()
        profiles = container.profile.loadProfiles(ids)
        container.notifications.markRead()
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.notif_title),
            onLogoClick = onBack,
            showWallet = false,
            icon = Icons.Filled.Notifications,
        )
        if (loading) {
            LoadingBox(text = stringResource(R.string.common_loading))
        } else if (items.isEmpty() && requests.isEmpty()) {
            EmptyState(stringResource(R.string.notif_empty))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp,
                    end = 14.dp,
                    top = 10.dp,
                    bottom = 96.dp,
                ),
            ) {
                if (requests.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.notif_friend_request), leadingEmoji = "🤝") }
                    items(requests, key = { it.id }) { request ->
                        val profile = profiles[request.requesterId]
                        GlassCard(padding = 10.dp) {
                            com.addonstation.mobile.ui.components.ProfileRow(
                                profile = profile,
                                fallbackName = request.requesterId.take(6),
                                avatarUrl = assetUrl(container, profile?.avatarUrl),
                                onClick = { onOpenProfile(request.requesterId) },
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GradientButton(
                                    text = stringResource(R.string.notif_accept),
                                    onClick = {
                                        scope.launch {
                                            container.notifications.respond(request.id, true)
                                            requests = container.notifications.incomingFriendRequests()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.notif_decline),
                                    onClick = {
                                        scope.launch {
                                            container.notifications.respond(request.id, false)
                                            requests = container.notifications.incomingFriendRequests()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                items(items, key = { it.id }) { notification ->
                    val actor = notification.actorId?.let { profiles[it] }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (notification.isRead) AppColors.Surface else AppColors.Elevated)
                            .border(
                                1.dp,
                                if (notification.isRead) AppColors.Border else AppColors.Green.copy(alpha = 0.35f),
                                RoundedCornerShape(14.dp),
                            )
                            .clickable {
                                if (notification.channelId != null) {
                                    onOpenChannel(notification.channelId!!, notification.messageId)
                                } else if (actor != null) {
                                    onOpenProfile(actor.id)
                                }
                            }
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        AvatarImage(url = assetUrl(container, actor?.avatarUrl), size = 38.dp)
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = notificationBody(notification, actor?.username),
                                style = AppText.bodySmall,
                                color = AppColors.Text,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                TimeFormat.dateTime(notification.createdAt, Locale.getDefault()),
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                        }
                        if (!notification.isRead) {
                            Text(
                                stringResource(R.string.notif_new),
                                style = AppText.tiny,
                                color = AppColors.OnGreen,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(AppColors.Green)
                                    .padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                        Text("←", style = AppText.bodySmall, color = AppColors.Blue)
                    }
                }
            }
        }
    }
}

@Composable
private fun notificationBody(notification: ChatNotification, actorName: String?): String {
    val name = actorName ?: "—"
    return when (notification.type) {
        "friend_request" -> "$name · " + stringResource(R.string.notif_friend_request)
        "mention" -> "$name · " + stringResource(R.string.notif_mention)
        "reply" -> "$name · " + stringResource(R.string.notif_reply)
        "thread" -> "$name · " + stringResource(R.string.notif_thread)
        "plan", "payment" -> notification.body ?: stringResource(R.string.plan_title)
        else -> notification.body ?: stringResource(R.string.notif_title)
    }
}

/** قائمة الأصدقاء + طلبات الصداقة (تُفتح من الملف الشخصي أو من ورقة الأعضاء). */
@Composable
fun FriendsScreen(onBack: () -> Unit, onOpenProfile: (String) -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var friends by remember { mutableStateOf<List<FriendEntry>>(emptyList()) }
    var incoming by remember { mutableStateOf<List<FriendEntry>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<String, com.addonstation.mobile.data.model.Profile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        loading = true
        friends = container.notifications.acceptedFriends()
        incoming = container.notifications.incomingFriendRequests()
        val me = container.supabase.session()?.userId
        val ids = (friends.map { if (it.requesterId == me) it.receiverId else it.requesterId } +
            incoming.map { it.requesterId }).toSet()
        profiles = container.profile.loadProfiles(ids)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.profile_friends), onBack = onBack)
        if (loading) {
            LoadingBox(text = stringResource(R.string.common_loading))
        } else if (friends.isEmpty() && incoming.isEmpty()) {
            EmptyState(stringResource(R.string.common_none))
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                if (incoming.isNotEmpty()) {
                    item { SectionHeader(title = stringResource(R.string.notif_friend_request), leadingEmoji = "🤝") }
                    items(incoming, key = { it.id }) { request ->
                        val profile = profiles[request.requesterId]
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.weight(1f)) {
                                ProfileRow(
                                    profile = profile,
                                    fallbackName = request.requesterId.take(6),
                                    avatarUrl = assetUrl(container, profile?.avatarUrl),
                                    onClick = { onOpenProfile(request.requesterId) },
                                )
                            }
                            GhostButton(
                                text = stringResource(R.string.notif_accept),
                                onClick = {
                                    scope.launch {
                                        container.notifications.respond(request.id, true)
                                        incoming = container.notifications.incomingFriendRequests()
                                    }
                                },
                            )
                        }
                    }
                }
                item { SectionHeader(title = stringResource(R.string.profile_friends), leadingEmoji = "👥") }
                items(friends, key = { it.id }) { entry ->
                    val me = container.supabase.session()?.userId
                    val otherId = if (entry.requesterId == me) entry.receiverId else entry.requesterId
                    ProfileRow(
                        profile = profiles[otherId],
                        fallbackName = otherId.take(6),
                        avatarUrl = assetUrl(container, profiles[otherId]?.avatarUrl),
                        onClick = { onOpenProfile(otherId) },
                    )
                }
            }
        }
    }
}

/** قائمة التِيبس المرسلة/المستلمة للمبدع (تُستخدم في الاستوديو). */
@Composable
fun TipsList(tips: List<CreatorTip>, modifier: Modifier = Modifier) {
    Column(modifier) {
        if (tips.isEmpty()) {
            EmptyState(stringResource(R.string.common_none))
        } else {
            tips.forEach { tip ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = AppColors.Gold)
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Column(Modifier.weight(1f)) {
                        Text("${tip.amount} LYD", style = AppText.bodySmall, color = AppColors.Text)
                        Text(
                            tip.message ?: tip.paymentMethod,
                            style = AppText.tiny,
                            color = AppColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(tip.status, style = AppText.tiny, color = AppColors.Green)
                }
            }
        }
    }
}
