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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
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

// AC wallet screens are implemented in WalletScreens.kt.

/** الإشعارات: طلب صداقة (مع قبول)، منشن، رد، نشاط موضوع — مع وسم «جديد». */
@Composable
fun NotificationsScreen(
    onBack: () -> Unit,
    onOpenChannel: (String, Long?) -> Unit,
    onOpenProfile: (String) -> Unit,
    /** فتح إعدادات الإشعارات (الأقسام وساعات الهدوء) — زر الترس في الرأس. */
    onOpenSettings: () -> Unit = {},
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf<List<ChatNotification>>(emptyList()) }
    var requests by remember { mutableStateOf<List<FriendEntry>>(emptyList()) }
    var profiles by remember { mutableStateOf<Map<String, com.addonstation.mobile.data.model.Profile>>(emptyMap()) }
    var loading by remember { mutableStateOf(true) }

    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        failed = false
        try {
            items = container.notifications.list()
            requests = container.notifications.incomingFriendRequests()
            val ids = (items.mapNotNull { it.actorId } + requests.map { it.requesterId }).toSet()
            profiles = container.profile.loadProfiles(ids)
            container.notifications.markRead()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    // نرفع النصوص خارج اللامبدا (قاعدة الفاحص: stringResource في لامبدا تُرفع إلى val)
    val notifTitle = stringResource(R.string.notif_title)
    val settingsLabel = stringResource(R.string.notif_settings)

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = notifTitle,
            onLogoClick = onBack,
            showWallet = false,
            icon = Icons.Filled.Notifications,
            actions = {
                // ترس التخصيص: مكانه الطبيعي داخل شاشة الإشعارات نفسها
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = settingsLabel,
                    tint = AppColors.Muted,
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onOpenSettings() },
                )
            },
        )
        if (loading) {
            LoadingBox(text = stringResource(R.string.common_loading))
        } else if (failed) {
            com.addonstation.mobile.ui.components.ErrorState(onRetry = { reloadTick += 1 })
        } else if (items.isEmpty() && requests.isEmpty()) {
            EmptyState(stringResource(R.string.notif_empty_plain))
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
