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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AlternateEmail
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.NotificationsOff
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
import com.addonstation.mobile.data.model.ChatNotification
import com.addonstation.mobile.data.model.CreatorTip
import com.addonstation.mobile.data.model.FriendEntry
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.LoadingPattern
import com.addonstation.mobile.ui.components.LoadingState
import com.addonstation.mobile.ui.components.PhoneChipRow
import com.addonstation.mobile.ui.components.PhoneEmptyState
import com.addonstation.mobile.ui.components.PhoneGroupLabel
import com.addonstation.mobile.ui.components.PhoneHeaderAction
import com.addonstation.mobile.ui.components.PhoneIconTile
import com.addonstation.mobile.ui.components.PhoneListChip
import com.addonstation.mobile.ui.components.PhoneListHeader
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.ProfileRow
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.components.toErrorKind
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.launch
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

// AC wallet screens are implemented in WalletScreens.kt.

/** Notifications with localized, typed rows, filters, grouping, and swipe actions. */
private enum class NotificationFilter(val key: String) { ALL("all"), ACCOUNT("account"), CHAT("chat"), CONTENT("content"), SOCIAL("social") }

private fun notificationFilterOf(type: String, amount: Int?): NotificationFilter = when {
    amount != null || type in setOf("system", "plan", "payment", "subscription") -> NotificationFilter.ACCOUNT
    type in setOf("mention", "reply", "thread", "message") -> NotificationFilter.CHAT
    type in setOf("content", "new_content", "content_published") -> NotificationFilter.CONTENT
    type in setOf("friend_request", "friend", "follow", "social") -> NotificationFilter.SOCIAL
    else -> NotificationFilter.ACCOUNT
}

@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenChannel: (String, Long?) -> Unit,
    onOpenProfile: (String) -> Unit,
    onOpenSettings: () -> Unit = {},
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var notifications by remember { mutableStateOf<List<ChatNotification>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendEntry>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<String, com.addonstation.mobile.data.model.Profile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var errorKind by remember { mutableStateOf(com.addonstation.mobile.ui.components.ErrorKind.Network) }
    var reloadTick by remember { mutableStateOf(0) }
    var selectedFilter by remember { mutableStateOf(NotificationFilter.ALL.key) }

    LaunchedEffect(reloadTick) {
        loading = true
        failed = false
        try {
            notifications = container.notifications.list()
            requests = container.notifications.incomingFriendRequests()
            val ids = (notifications.mapNotNull { it.actorId } + requests.map { it.requesterId }).toSet()
            profiles = container.profile.loadProfiles(ids)
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            errorKind = error.toErrorKind()
            android.util.Log.w("NotificationsScreen", "Notification load failed", error)
            failed = true
        } finally {
            loading = false
        }
    }

    val unread = notifications.count { !it.isRead }
    val amountOf: (ChatNotification) -> Int? = { notification ->
        notification.meta["amount"]?.jsonPrimitive?.intOrNull
    }
    val counts = remember(notifications, requests) {
        NotificationFilter.entries.associateWith { filter ->
            when (filter) {
                NotificationFilter.ALL -> notifications.size + requests.size
                NotificationFilter.SOCIAL -> notifications.count {
                    notificationFilterOf(it.type, amountOf(it)) == filter
                } + requests.size
                else -> notifications.count { notificationFilterOf(it.type, amountOf(it)) == filter }
            }
        }
    }
    val visible = remember(notifications, selectedFilter) {
        val selected = NotificationFilter.entries.firstOrNull { it.key == selectedFilter } ?: NotificationFilter.ALL
        if (selected == NotificationFilter.ALL) notifications
        else notifications.filter { notificationFilterOf(it.type, amountOf(it)) == selected }
    }
    val visibleRequests = selectedFilter == NotificationFilter.ALL.key || selectedFilter == NotificationFilter.SOCIAL.key
    val today = stringResource(R.string.list_today)
    val yesterday = stringResource(R.string.list_yesterday)
    val earlier = stringResource(R.string.list_earlier)
    val grouped = remember(visible, today, yesterday, earlier) {
        visible.groupBy { notificationDayGroup(it.createdAt, today, yesterday, earlier) }
    }
    val title = stringResource(R.string.notif_title)
    val settingsLabel = stringResource(R.string.notif_settings)
    val markAllLabel = stringResource(R.string.notif_mark_all_read)
    val chips = listOf(
        PhoneListChip(NotificationFilter.ALL.key, stringResource(R.string.notif_category_all), counts[NotificationFilter.ALL]),
        PhoneListChip(NotificationFilter.ACCOUNT.key, stringResource(R.string.notif_category_account), counts[NotificationFilter.ACCOUNT]),
        PhoneListChip(NotificationFilter.CHAT.key, stringResource(R.string.notif_category_chat), counts[NotificationFilter.CHAT]),
        PhoneListChip(NotificationFilter.CONTENT.key, stringResource(R.string.notif_category_content), counts[NotificationFilter.CONTENT]),
        PhoneListChip(NotificationFilter.SOCIAL.key, stringResource(R.string.notif_category_social), counts[NotificationFilter.SOCIAL]),
    )

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        PhoneListHeader(
            title = title,
            count = unread,
            countLabel = stringResource(R.string.list_new_count, unread),
            onBack = onBack,
            actions = {
                PhoneHeaderAction(
                    icon = Icons.Filled.DoneAll,
                    contentDescription = markAllLabel,
                    onClick = {
                        if (unread > 0) scope.launch {
                            container.notifications.markRead()
                            notifications = notifications.map { it.copy(isRead = true) }
                        }
                    },
                    tint = LocalHomePalette.current.blueLink,
                )
                PhoneHeaderAction(Icons.Filled.Settings, settingsLabel, onOpenSettings)
            },
        )
        PhoneChipRow(chips, selectedFilter, { selectedFilter = it })
        when {
            loading -> LoadingState(
                pattern = LoadingPattern.Skeleton,
                onRetry = { reloadTick += 1 },
                skeletonRows = 5,
            )
            failed -> ErrorState(
                onRetry = { reloadTick += 1 },
                kind = errorKind,
                modifier = Modifier.weight(1f),
            )
            visible.isEmpty() && (!visibleRequests || requests.isEmpty()) -> PhoneEmptyState(
                icon = Icons.Outlined.NotificationsOff,
                color = AppColors.Blue,
                title = stringResource(R.string.notif_empty_title),
                body = stringResource(R.string.notif_empty_body),
                actionText = settingsLabel,
                onAction = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "swipe-hint") {
                    Text(
                        stringResource(R.string.list_swipe_hint),
                        style = AppText.tiny,
                        color = LocalHomePalette.current.textMuted,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
                if (visibleRequests && requests.isNotEmpty()) {
                    item(key = "request-group") { PhoneGroupLabel(today) }
                    items(requests, key = { "request-${it.id}" }) { request ->
                        val profile = profiles[request.requesterId]
                        FriendRequestNotificationRow(
                            name = profile?.displayName?.takeIf(String::isNotBlank)
                                ?: profile?.username ?: request.requesterId.take(8),
                            onOpen = { onOpenProfile(request.requesterId) },
                            onAccept = {
                                scope.launch {
                                    container.notifications.respond(request.id, true)
                                    requests = requests.filterNot { it.id == request.id }
                                }
                            },
                            onDecline = {
                                scope.launch {
                                    container.notifications.respond(request.id, false)
                                    requests = requests.filterNot { it.id == request.id }
                                }
                            },
                        )
                    }
                }
                listOf(today, yesterday, earlier).forEach { group ->
                    val rows = grouped[group].orEmpty()
                    if (rows.isNotEmpty()) {
                        item(key = "group-$group") { PhoneGroupLabel(group) }
                        items(rows, key = { "notification-${it.id}" }) { notification ->
                            val actor = notification.actorId?.let(profiles::get)
                            SwipeNotificationRow(
                                notification = notification,
                                actorName = actor?.displayName?.takeIf(String::isNotBlank)
                                    ?: actor?.username,
                                amount = amountOf(notification),
                                onOpen = {
                                    when {
                                        notification.channelId != null -> onOpenChannel(notification.channelId, notification.messageId)
                                        actor != null -> onOpenProfile(actor.id)
                                    }
                                },
                                onRead = {
                                    scope.launch {
                                        container.notifications.markRead(listOf(notification.id))
                                        notifications = notifications.map {
                                            if (it.id == notification.id) it.copy(isRead = true) else it
                                        }
                                    }
                                },
                                onDelete = {
                                    scope.launch {
                                        container.notifications.delete(notification.id)
                                        notifications = notifications.filterNot { it.id == notification.id }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun SwipeNotificationRow(
    notification: ChatNotification,
    actorName: String?,
    amount: Int?,
    onOpen: () -> Unit,
    onRead: () -> Unit,
    onDelete: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> scope.launch { onRead() }
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> scope.launch { onDelete() }
                else -> Unit
            }
            false
        },
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppColors.Elevated)
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(stringResource(R.string.list_read), style = AppText.button, color = AppColors.Blue)
                Spacer(Modifier.weight(1f))
                Text(stringResource(R.string.common_delete), style = AppText.button, color = AppColors.Red)
            }
        },
    ) {
        NotificationCard(notification, actorName, amount, onOpen)
    }
}

@Composable
private fun NotificationCard(
    notification: ChatNotification,
    actorName: String?,
    amount: Int?,
    onOpen: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val visual = notificationVisual(notification, actorName, amount)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(if (notification.isRead) palette.surface else palette.blue.copy(alpha = 0.10f))
            .border(1.dp, if (notification.isRead) palette.border else palette.blue.copy(alpha = 0.48f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpen)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        if (!notification.isRead) {
            Box(Modifier.width(3.dp).height(48.dp).clip(CircleShape).background(palette.blue))
        }
        PhoneIconTile(visual.icon, visual.color)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(visual.title, style = AppText.bodySmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold), color = palette.text, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(visual.subtitle, style = AppText.tiny, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (notification.type == "mention" && notification.channelId != null) {
                Text("@${notification.channelId}", style = AppText.tiny, color = AppColors.Purple, maxLines = 1)
            }
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                if (!notification.isRead) Box(Modifier.size(7.dp).clip(CircleShape).background(palette.blue))
                Text(notificationRelativeTime(notification.createdAt), style = AppText.tiny, color = palette.textMuted)
            }
            if (amount != null) {
                val credit = amount >= 0
                val color = if (credit) AppColors.Green else AppColors.Red
                Text(
                    (if (credit) "+" else "−") + kotlin.math.abs(amount) + " AC",
                    style = AppText.tiny.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                    color = color,
                    modifier = Modifier.clip(CircleShape).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
    }
}

private data class NotificationVisual(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val color: androidx.compose.ui.graphics.Color)

@Composable
private fun notificationVisual(notification: ChatNotification, actorName: String?, amount: Int?): NotificationVisual {
    val name = actorName ?: stringResource(R.string.role_user)
    val type = notification.type.lowercase(Locale.ROOT)
    return when {
        amount != null && amount >= 0 -> NotificationVisual(
            stringResource(R.string.notif_ac_received, amount),
            stringResource(R.string.notif_gift_team),
            Icons.Filled.MonetizationOn,
            AppColors.Green,
        )
        amount != null -> NotificationVisual(
            stringResource(R.string.notif_ac_deducted, kotlin.math.abs(amount)),
            stringResource(R.string.notif_adjusted_team),
            Icons.Filled.RemoveCircleOutline,
            AppColors.Red,
        )
        type == "friend_request" -> NotificationVisual(
            stringResource(R.string.notif_friend_request_from, name),
            stringResource(R.string.notif_friend_help),
            Icons.Filled.PersonAdd,
            AppColors.Blue,
        )
        type == "mention" -> NotificationVisual(
            stringResource(R.string.notif_mentioned_in, name, notification.channelId ?: "—"),
            stringResource(R.string.notif_mention),
            Icons.Filled.AlternateEmail,
            AppColors.Purple,
        )
        type in setOf("reply", "thread", "message") -> NotificationVisual(
            name,
            stringResource(if (type == "reply") R.string.notif_reply else R.string.notif_thread),
            Icons.Filled.ChatBubbleOutline,
            AppColors.Purple,
        )
        type in setOf("content", "new_content", "content_published") -> NotificationVisual(
            stringResource(R.string.notif_content_published, notification.meta["title"]?.jsonPrimitive?.contentOrNull ?: stringResource(R.string.admin_content)),
            stringResource(R.string.notif_category_content),
            Icons.Filled.Extension,
            AppColors.Blue,
        )
        type in setOf("plan", "payment", "subscription") -> NotificationVisual(
            stringResource(R.string.notif_plan_active, notification.meta["plan"]?.jsonPrimitive?.contentOrNull ?: stringResource(R.string.plan_epic_name)),
            stringResource(R.string.notif_category_account),
            Icons.Filled.Diamond,
            AppColors.Gold,
        )
        else -> NotificationVisual(
            stringResource(R.string.notif_title),
            stringResource(R.string.notif_category_account),
            Icons.Filled.Notifications,
            AppColors.Blue,
        )
    }
}

@Composable
private fun FriendRequestNotificationRow(
    name: String,
    onOpen: () -> Unit,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
) {
    val palette = LocalHomePalette.current
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PhoneIconTile(Icons.Filled.PersonAdd, AppColors.Blue)
        Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.notif_friend_request_from, name), style = AppText.bodySmall, color = palette.text, maxLines = 2)
            Text(stringResource(R.string.notif_friend_help), style = AppText.tiny, color = palette.textMuted, maxLines = 1)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 7.dp)) {
                GradientButton(stringResource(R.string.notif_accept), onAccept, modifier = Modifier.weight(1f))
                GhostButton(stringResource(R.string.notif_decline), onDecline, modifier = Modifier.weight(1f))
            }
        }
    }
}

private fun notificationDayGroup(iso: String?, today: String, yesterday: String, earlier: String): String {
    val millis = TimeFormat.parseMillis(iso) ?: return earlier
    val now = java.util.Calendar.getInstance()
    val then = java.util.Calendar.getInstance().apply { timeInMillis = millis }
    val sameYear = now.get(java.util.Calendar.YEAR) == then.get(java.util.Calendar.YEAR)
    val difference = now.get(java.util.Calendar.DAY_OF_YEAR) - then.get(java.util.Calendar.DAY_OF_YEAR)
    return when {
        sameYear && difference == 0 -> today
        sameYear && difference == 1 -> yesterday
        else -> earlier
    }
}

private fun notificationRelativeTime(iso: String?): String {
    val millis = TimeFormat.parseMillis(iso) ?: return "—"
    val diff = (System.currentTimeMillis() - millis).coerceAtLeast(0L)
    val minutes = java.util.concurrent.TimeUnit.MILLISECONDS.toMinutes(diff)
    return when {
        minutes < 1 -> "<1m"
        minutes < 60 -> "${minutes}m"
        minutes < 1_440 -> "${minutes / 60}h"
        else -> TimeFormat.dateOnly(iso, Locale.getDefault())
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

    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    // حالة الدخول جزء من المفاتيح: بعد تسجيل الدخول تُعاد القوائم فوراً
    // (كانت تبقى فارغة حتى يبدّل المستخدم شيئاً آخر).
    val signedIn by container.state.signedIn.collectAsState()
    LaunchedEffect(reloadTick, signedIn) {
        loading = true
        failed = false
        try {
            friends = container.notifications.acceptedFriends()
            incoming = container.notifications.incomingFriendRequests()
            val me = container.supabase.session()?.userId
            val ids = (friends.map { if (it.requesterId == me) it.receiverId else it.requesterId } +
                incoming.map { it.requesterId }).toSet()
            profiles = container.profile.loadProfiles(ids)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.profile_friends_plain), onBack = onBack)
        if (loading) {
            LoadingBox(text = stringResource(R.string.common_loading))
        } else if (failed) {
            com.addonstation.mobile.ui.components.ErrorState(onRetry = { reloadTick += 1 })
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
                item { SectionHeader(title = stringResource(R.string.profile_friends_plain), leadingEmoji = "👥") }
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
