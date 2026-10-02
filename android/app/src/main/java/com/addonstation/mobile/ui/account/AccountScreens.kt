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
import androidx.compose.material3.OutlinedTextField
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
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.CoinIcon
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.Toasts
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
    var messageOk by remember { mutableStateOf(true) }
    var codeInput by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }
    var detectedTier by remember { mutableStateOf<Int?>(null) }

    val localeCode = appLocaleCode()
    val arabic = localeCode.equals("ar", ignoreCase = true)
    // نصوص رسائل الكود (تُقرأ خارج التزامن لأن stringResource للـComposable فقط)
    val msgInvalid = stringResource(R.string.ac_redeem_invalid)
    val msgUsed = stringResource(R.string.ac_redeem_used)
    val msgExpired = stringResource(R.string.ac_redeem_expired)
    val msgOff = stringResource(R.string.ac_redeem_off)
    val msgExhausted = stringResource(R.string.ac_redeem_exhausted)
    val msgSignIn = stringResource(R.string.ac_redeem_sign_in)
    val msgSuccess = stringResource(R.string.ac_redeem_success, "%s")
    val msgGeneric = stringResource(R.string.msg_generic_error)

    val msgClaimed = stringResource(R.string.ac_claimed)
    val msgSignInNeeded = stringResource(R.string.msg_sign_in_needed)

    fun claimMessage(raw: String?): String {
        val m = raw.orEmpty().lowercase()
        return when {
            m.contains("already_claimed") || m.contains("max_claims") -> msgClaimed
            m.contains("sign_in_required") -> msgSignInNeeded
            raw.isNullOrBlank() -> msgGeneric
            else -> raw
        }
    }

    fun redeemMessage(raw: String?): String {
        val m = raw.orEmpty().lowercase()
        return when {
            m.contains("code_used") -> msgUsed
            m.contains("code_expired") -> msgExpired
            m.contains("code_inactive") -> msgOff
            m.contains("code_exhausted") -> msgExhausted
            m.contains("code_invalid") -> msgInvalid
            m.contains("sign_in_required") -> msgSignIn
            raw.isNullOrBlank() -> msgGeneric
            else -> raw
        }
    }

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
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        CoinIcon(size = 22.dp)
                        Text("AC", style = AppText.sectionTitle, color = AppColors.Gold)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("+${wallet?.totalEarned ?: 0}", style = AppText.tiny, color = AppColors.Green)
                    Text("-${wallet?.totalSpent ?: 0}", style = AppText.tiny, color = AppColors.Red)
                }
                Text(stringResource(R.string.ac_desc), style = AppText.tiny, color = AppColors.Muted)
            }

            // نخبة بدل سطر داخل الصفحة (ToastHost في AppNav)
            LaunchedEffect(message) {
                message?.let {
                    Toasts.show(it)
                    message = null
                }
            }

            // ------------------------------------------------ كود هدية AC
            SectionHeader(title = stringResource(R.string.ac_redeem_title))
            GlassCard(padding = 12.dp) {
                Text(
                    stringResource(R.string.ac_redeem_hint),
                    style = AppText.tiny,
                    color = AppColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = codeInput,
                    onValueChange = { codeInput = it.filter { ch -> !ch.isWhitespace() }.take(12) },
                    label = { Text(stringResource(R.string.ac_redeem_title)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    text = stringResource(R.string.ac_redeem_action),
                    onClick = {
                        scope.launch {
                            redeeming = true
                            container.ac.redeemCode(codeInput)
                                .onSuccess { amount ->
                                    messageOk = true
                                    detectedTier = amount
                                    message = msgSuccess.format("%,d".format(java.util.Locale.US, amount))
                                    codeInput = ""
                                    transactions = container.ac.transactions()
                                    tasks = container.ac.tasks()
                                }
                                .onFailure {
                                    messageOk = false
                                    message = redeemMessage((it as? com.addonstation.mobile.data.ApiError)?.raw ?: it.message)
                                }
                            redeeming = false
                        }
                    },
                    enabled = !redeeming && codeInput.length >= 4,
                    modifier = Modifier.fillMaxWidth(),
                )
                // التطبيق يتعرّف على فئة الكود تلقائياً ويعرضها — لا يختار المستخدم شيئاً
                detectedTier?.let { tier ->
                    Spacer(Modifier.height(6.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        CoinIcon(size = 16.dp)
                        Text(
                            stringResource(R.string.ac_redeem_detected, "%,d".format(java.util.Locale.US, tier)),
                            style = AppText.tiny,
                            color = AppColors.Gold,
                        )
                    }
                }
            }

            SectionHeader(title = stringResource(R.string.ac_tasks))
            if (loading) {
                LoadingBox()
            } else {
                tasks.forEach { task ->
                    GlassCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (arabic) task.titleAr else (task.titleEn ?: task.titleAr),
                                    style = AppText.bodySmall,
                                    color = AppColors.Text,
                                )
                                // الوصف في قاعدة البيانات عربي فقط — لا نعرضه في الواجهات غير العربية
                                if (arabic) {
                                    task.description?.let {
                                        Text(it, style = AppText.tiny, color = AppColors.Muted, maxLines = 2)
                                    }
                                }
                                Text(
                                    "+${task.reward} AC · ${acFrequencyLabel(task.frequency)}",
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
                                                messageOk = true
                                                message = "+$reward AC"
                                                transactions = container.ac.transactions()
                                            }
                                            .onFailure {
                                                messageOk = false
                                                message = claimMessage(it.message)
                                            }
                                    }
                                },
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            SectionHeader(title = stringResource(R.string.ac_history))
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
                                tx.taskId
                                    ?.let { id -> tasks.firstOrNull { it.id == id } }
                                    ?.let { if (arabic) it.titleAr else (it.titleEn ?: it.titleAr) }
                                    ?: acKindLabel(tx.kind),
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
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            CoinIcon(size = 11.dp)
                            Text("${tx.balanceAfter}", style = AppText.tiny, color = AppColors.Muted)
                        }
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

/** وسم تكرار المهمة مترجماً (مرة واحدة · يومياً · سلسلة · من الإدارة). */
@Composable
private fun acFrequencyLabel(frequency: String): String = when (frequency) {
    "daily" -> stringResource(R.string.ac_freq_daily)
    "streak" -> stringResource(R.string.ac_freq_streak)
    "manual" -> stringResource(R.string.ac_freq_manual)
    else -> stringResource(R.string.ac_freq_once)
}

/** وسم نوع العملية في سجل AC بلغة التطبيق الحالية. */
@Composable
private fun acKindLabel(kind: String): String = when (kind) {
    "task" -> stringResource(R.string.ac_kind_task)
    "daily" -> stringResource(R.string.ac_kind_daily)
    "streak" -> stringResource(R.string.ac_kind_streak)
    "admin_reward", "admin_adjustment" -> stringResource(R.string.ac_kind_admin)
    "unlock" -> stringResource(R.string.ac_kind_unlock)
    "event" -> stringResource(R.string.ac_kind_event)
    "welcome" -> stringResource(R.string.ac_kind_welcome)
    "code" -> stringResource(R.string.ac_kind_code)
    else -> kind
}
