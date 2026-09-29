package com.addonstation.mobile.ui.admin

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ChatReport
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorApplication
import com.addonstation.mobile.data.model.CreatorSubmission
import com.addonstation.mobile.data.model.CreatorTip
import com.addonstation.mobile.data.model.PaymentOrder
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.CounterCard
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.FilterChip
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.RowItem
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.plans.StatusPill
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive

/**
 * لوحة الإدارة (Admin فقط). الفحص الحقيقي على الخادم: كل دالة RPC تتحقق من is_admin،
 * وأي محاولة من غير الأدمن تُرفض من قاعدة البيانات لا من الواجهة فقط.
 */
@Composable
fun AdminPanelScreen(
    onBackToApp: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenPayments: () -> Unit,
    onOpenModeration: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenCreatorApps: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenAc: () -> Unit,
    onOpenAudit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val stats by container.state.adminStats.collectAsState()
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(0) }
    var moreOpen by remember { mutableStateOf(false) }
    var users by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var content by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var orders by remember { mutableStateOf<List<PaymentOrder>>(emptyList()) }
    var reports by remember { mutableStateOf<List<ChatReport>>(emptyList()) }
    var userQuery by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("all") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<String?>(null) }

    // بوابة الوصول: لا يظهر شيء لغير الأدمن
    if (profile != null && profile?.role != "admin") {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.admin_not_allowed),
                    style = AppText.sectionTitle,
                    color = AppColors.Red,
                )
                Spacer(Modifier.height(10.dp))
                GhostButton(text = stringResource(R.string.common_back), onClick = onBackToApp)
            }
        }
        return
    }

    LaunchedEffect(tab, userQuery, roleFilter) {
        loading = true
        container.state.setAdminStats(container.admin.stats())
        when (tab) {
            0 -> {
                users = container.admin.users(userQuery, roleFilter)
                content = container.admin.allContent(20)
            }
            1 -> content = container.admin.allContent(200)
            2 -> orders = container.admin.orders()
            3 -> reports = container.chat.openReports()
        }
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = when (tab) {
                0 -> stringResource(R.string.admin_overview)
                1 -> stringResource(R.string.admin_content)
                2 -> stringResource(R.string.admin_payments)
                3 -> stringResource(R.string.admin_moderation)
                else -> stringResource(R.string.admin_more)
            },
            walletBalance = wallet?.balance,
            onWalletClick = { },
            icon = Icons.Filled.AdminPanelSettings,
        )
        Box(Modifier.weight(1f)) {
            when (tab) {
                0 -> OverviewTab(
                    stats = stats,
                    users = users,
                    content = content,
                    userQuery = userQuery,
                    onUserQuery = { userQuery = it },
                    roleFilter = roleFilter,
                    onRoleFilter = { roleFilter = it },
                    loading = loading,
                    onSaveUser = { user, plan, role, grantDays ->
                        scope.launch {
                            container.admin.updateUserPlanRole(
                                userId = user.id,
                                plan = plan,
                                role = role,
                                expiresIso = null,
                                grantDays = grantDays,
                            ).onSuccess { message = "✓ ${user.username}" }
                                .onFailure { message = it.message }
                        }
                    },
                    onEditContent = onOpenContent,
                    onDeleteContent = { item ->
                        scope.launch {
                            container.admin.deleteContent(item.id)
                                .onSuccess { content = container.admin.allContent(20) }
                                .onFailure { message = it.message }
                        }
                    },
                    onQuick = { index ->
                        when (index) {
                            0 -> onOpenContent()
                            1 -> tab = 2
                            2 -> tab = 3
                            3 -> onOpenCreatorApps()
                            4 -> onOpenSettings()
                            5 -> onOpenAssets()
                        }
                    },
                )
                1 -> ContentTab(
                    content = content,
                    loading = loading,
                    onOpenContentAdmin = onOpenContent,
                    onRefresh = {
                        scope.launch { content = container.admin.allContent(200) }
                    },
                )
                2 -> PaymentsTab(
                    orders = orders,
                    loading = loading,
                    onApprove = { order ->
                        scope.launch {
                            container.admin.approveOrder(order.id)
                                .onSuccess { orders = container.admin.orders() }
                                .onFailure { message = it.message }
                        }
                    },
                    onReject = { order, reason ->
                        scope.launch {
                            container.admin.rejectOrder(order.id, reason)
                                .onSuccess { orders = container.admin.orders() }
                                .onFailure { message = it.message }
                        }
                    },
                    onOpenReceipt = { order ->
                        scope.launch {
                            container.plans.receiptUrl(order)?.let { dialog = it }
                        }
                    },
                )
                3 -> ModerationTab(
                    reports = reports,
                    loading = loading,
                    onHideMessage = { report ->
                        scope.launch {
                            report.messageId?.let { container.chat.moderateMessage(it, true) }
                            container.chat.closeReport(report.id, "hide")
                            reports = container.chat.openReports()
                        }
                    },
                    onMuteAuthor = { report, minutes ->
                        scope.launch {
                            val author = report.reporterId
                            container.chat.muteUser(author, minutes, "Report #${report.id}")
                            container.chat.closeReport(report.id, "mute")
                            reports = container.chat.openReports()
                        }
                    },
                    onClose = { report ->
                        scope.launch {
                            container.chat.closeReport(report.id, "close")
                            reports = container.chat.openReports()
                        }
                    },
                )
                else -> MoreTab(
                    onOpen = { key ->
                        when (key) {
                            "assets" -> onOpenAssets()
                            "creators" -> onOpenCreatorApps()
                            "settings" -> onOpenSettings()
                            "ac" -> onOpenAc()
                            "tips" -> onOpenTips()
                            "chat" -> onOpenChat()
                            "audit" -> onOpenAudit()
                            "back" -> onBackToApp()
                        }
                    },
                )
            }
        }

        // شريط اللوحة السفلي (5 عناصر)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Bg.copy(alpha = 0.96f))
                .border(1.dp, AppColors.Border)
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            listOf(
                stringResource(R.string.admin_overview) to Icons.Filled.AdminPanelSettings,
                stringResource(R.string.admin_content) to Icons.Filled.Image,
                stringResource(R.string.admin_payments) to Icons.Filled.Receipt,
                stringResource(R.string.admin_moderation) to Icons.Filled.Flag,
                stringResource(R.string.admin_more) to Icons.Filled.Settings,
            ).forEachIndexed { index, (label, icon) ->
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { tab = index },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    androidx.compose.material3.Icon(
                        icon,
                        contentDescription = label,
                        tint = if (tab == index) AppColors.Green else AppColors.Dim,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        label,
                        style = AppText.tiny,
                        color = if (tab == index) AppColors.Green else AppColors.Dim,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }

    message?.let { text ->
        Text(
            text,
            style = AppText.tiny,
            color = AppColors.Green,
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.Bg)
                .padding(horizontal = 14.dp, vertical = 4.dp),
        )
    }
    dialog?.let { url ->
        com.addonstation.mobile.ui.common.FullScreenImageViewer(url = url) { dialog = null }
    }
}

@Composable
private fun OverviewTab(
    stats: com.addonstation.mobile.data.model.AdminStats?,
    users: List<Profile>,
    content: List<ContentItem>,
    userQuery: String,
    onUserQuery: (String) -> Unit,
    roleFilter: String,
    onRoleFilter: (String) -> Unit,
    loading: Boolean,
    onSaveUser: (Profile, String?, String?, Int?) -> Unit,
    onEditContent: () -> Unit,
    onDeleteContent: (ContentItem) -> Unit,
    onQuick: (Int) -> Unit,
) {
    val container = rememberContainer()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("ADMIN DASHBOARD", style = AppText.kicker, color = AppColors.Blue)
        Text(stringResource(R.string.admin_title), style = AppText.pageTitle, color = AppColors.Text)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CounterCard("${stats?.users ?: 0}", stringResource(R.string.admin_users), Modifier.weight(1f))
            CounterCard("${stats?.published ?: 0}", stringResource(R.string.admin_published_content), Modifier.weight(1f))
            CounterCard("${stats?.addons ?: 0}", stringResource(R.string.admin_addons), Modifier.weight(1f))
            CounterCard("${stats?.worlds ?: 0}", stringResource(R.string.admin_worlds), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            CounterCard("${stats?.pendingOrders ?: 0}", stringResource(R.string.admin_payments), Modifier.weight(1f), AppColors.Gold)
            CounterCard("${stats?.openReports ?: 0}", stringResource(R.string.admin_chat_reports), Modifier.weight(1f), AppColors.Red)
            CounterCard("${stats?.pendingCreatorApps ?: 0}", stringResource(R.string.admin_creator_requests), Modifier.weight(1f), AppColors.Purple)
            CounterCard("${stats?.acBalanceTotal ?: 0}", "AC", Modifier.weight(1f), AppColors.Green)
        }

        SectionHeader(title = stringResource(R.string.admin_tools), leadingEmoji = "⚡")
        LazyRowQuick(onQuick)

        SectionHeader(title = stringResource(R.string.admin_users), leadingEmoji = "👥")
        OutlinedTextField(
            value = userQuery,
            onValueChange = onUserQuery,
            label = { Text(stringResource(R.string.admin_search_users)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "all" to stringResource(R.string.common_all),
                "user" to stringResource(R.string.role_user),
                "creator" to stringResource(R.string.role_creator),
                "moderator" to stringResource(R.string.role_moderator),
                "admin" to stringResource(R.string.role_admin),
            ).forEach { (key, label) ->
                FilterChip(label = label, selected = roleFilter == key, onClick = { onRoleFilter(key) })
            }
        }
        Spacer(Modifier.height(8.dp))
        if (loading) {
            LoadingBox()
        } else {
            users.take(40).forEach { user ->
                AdminUserRow(user = user, onSave = onSaveUser)
                Spacer(Modifier.height(8.dp))
            }
        }

        SectionHeader(title = stringResource(R.string.admin_content), leadingEmoji = "📦")
        content.take(10).forEach { item ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(AppColors.Elevated)
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                com.addonstation.mobile.ui.components.CoverImage(
                    url = assetUrl(container, item.coverUrl),
                    radius = 10.dp,
                    modifier = Modifier.size(width = 54.dp, height = 40.dp),
                )
                Column(Modifier.weight(1f)) {
                    Text(item.title, style = AppText.bodySmall, color = AppColors.Text, maxLines = 1)
                    Text(metaOf(item), style = AppText.tiny, color = AppColors.Muted)
                }
                androidx.compose.material3.Icon(
                    Icons.Filled.Edit,
                    contentDescription = stringResource(R.string.common_edit),
                    tint = AppColors.Blue,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onEditContent() },
                )
                androidx.compose.material3.Icon(
                    Icons.Filled.Delete,
                    contentDescription = stringResource(R.string.common_delete),
                    tint = AppColors.Red,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onDeleteContent(item) },
                )
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun LazyRowQuick(onQuick: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        val buttons = listOf(
            "＋" to stringResource(R.string.admin_add_content),
            "💳" to stringResource(R.string.admin_payments),
            "⚑" to stringResource(R.string.admin_chat_reports),
            "🧪" to stringResource(R.string.admin_creator_requests),
            "⚙" to stringResource(R.string.admin_plans_and_payment),
            "🖼" to stringResource(R.string.admin_assets),
        )
        buttons.forEachIndexed { index, (emoji, label) ->
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable { onQuick(index) }
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(emoji, style = AppText.body)
                Text(
                    label,
                    style = AppText.tiny,
                    color = AppColors.Muted,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun AdminUserRow(user: Profile, onSave: (Profile, String?, String?, Int?) -> Unit) {
    val container = rememberContainer()
    var plan by remember(user.id) { mutableStateOf(user.plan) }
    var role by remember(user.id) { mutableStateOf(user.role) }

    GlassCard(padding = 10.dp) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AvatarImage(url = assetUrl(container, user.avatarUrl), size = 36.dp)
            Column(Modifier.weight(1f)) {
                Text(user.username ?: user.id.take(6), style = AppText.bodySmall, color = AppColors.Text)
                Text(user.planExpiresAt ?: "—", style = AppText.tiny, color = AppColors.Muted, maxLines = 1)
            }
            RankBadge(role = user.role, plan = user.effectivePlan)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("free", "epic", "legendary").forEach { level ->
                FilterChip(level, plan == level, { plan = level })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("user", "creator", "moderator", "admin").forEach { r ->
                FilterChip(r, role == r, { role = r })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GradientButton(
                text = stringResource(R.string.admin_save),
                onClick = { onSave(user, plan, role, null) },
                modifier = Modifier.weight(1f),
            )
            GhostButton(
                text = "＋30",
                onClick = { onSave(user, plan, role, 30) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ContentTab(
    content: List<ContentItem>,
    loading: Boolean,
    onOpenContentAdmin: () -> Unit,
    onRefresh: () -> Unit,
) {
    val container = rememberContainer()
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("CONTENT ADMINISTRATION", style = AppText.kicker, color = AppColors.Blue)
        GradientButton(
            text = stringResource(R.string.admin_add_content),
            onClick = onOpenContentAdmin,
            icon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(text = stringResource(R.string.common_retry), onClick = onRefresh, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(10.dp))
        if (loading) {
            LoadingBox()
        } else {
            content.forEach { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    com.addonstation.mobile.ui.components.CoverImage(
                        url = assetUrl(container, item.coverUrl),
                        radius = 10.dp,
                        modifier = Modifier.size(width = 54.dp, height = 40.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = AppText.bodySmall, color = AppColors.Text, maxLines = 1)
                        Text(
                            "${item.contentType} · ${item.plan} · ${if (item.isPublished) "published" else "draft"}",
                            style = AppText.tiny,
                            color = AppColors.Muted,
                        )
                    }
                    Text(metaOf(item), style = AppText.tiny, color = AppColors.Dim)
                }
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun PaymentsTab(
    orders: List<PaymentOrder>,
    loading: Boolean,
    onApprove: (PaymentOrder) -> Unit,
    onReject: (PaymentOrder, String?) -> Unit,
    onOpenReceipt: (PaymentOrder) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("PAYMENTS", style = AppText.kicker, color = AppColors.Blue)
        if (loading) {
            LoadingBox()
        } else if (orders.isEmpty()) {
            EmptyState(stringResource(R.string.common_none))
        } else {
            orders.forEach { order ->
                GlassCard(padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(order.referenceCode, style = AppText.sectionTitle, color = AppColors.Text)
                        Spacer(Modifier.weight(1f))
                        StatusPill(order.status)
                    }
                    Text(
                        "${order.planId} · ${order.amountLyd} ${order.currency} · ${order.paymentMethod}",
                        style = AppText.bodySmall,
                        color = AppColors.Muted,
                    )
                    order.transactionReference?.let {
                        Text(it, style = AppText.tiny, color = AppColors.Muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            text = stringResource(R.string.admin_view_receipt),
                            onClick = { onOpenReceipt(order) },
                            modifier = Modifier.weight(1f),
                            enabled = order.receiptUrl != null,
                        )
                        GradientButton(
                            text = stringResource(R.string.admin_confirm_payment),
                            onClick = { onApprove(order) },
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = stringResource(R.string.admin_reject),
                            onClick = { onReject(order, null) },
                            borderColor = AppColors.Red.copy(alpha = 0.5f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun ModerationTab(
    reports: List<ChatReport>,
    loading: Boolean,
    onHideMessage: (ChatReport) -> Unit,
    onMuteAuthor: (ChatReport, Int) -> Unit,
    onClose: (ChatReport) -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text("CHAT MODERATION", style = AppText.kicker, color = AppColors.Blue)
        if (loading) {
            LoadingBox()
        } else if (reports.isEmpty()) {
            EmptyState(stringResource(R.string.admin_open_reports_none))
        } else {
            reports.forEach { report ->
                GlassCard(padding = 10.dp) {
                    Text(
                        "#${report.id} · ${report.reason} · ${report.status}",
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                    )
                    Text(
                        "message: ${report.messageId ?: "—"} · author: ${report.reporterId.take(8)}",
                        style = AppText.tiny,
                        color = AppColors.Muted,
                    )
                    report.note?.let { Text(it, style = AppText.tiny, color = AppColors.Muted) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GradientButton(
                            text = stringResource(R.string.chat_hide_message),
                            onClick = { onHideMessage(report) },
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = stringResource(R.string.chat_mute_user),
                            onClick = { onMuteAuthor(report, 60) },
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = stringResource(R.string.common_close),
                            onClick = { onClose(report) },
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }
        Spacer(Modifier.height(96.dp))
    }
}

@Composable
private fun MoreTab(onOpen: (String) -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 14.dp),
    ) {
        Spacer(Modifier.height(10.dp))
        Text(stringResource(R.string.admin_tools), style = AppText.pageTitle, color = AppColors.Text)
        Spacer(Modifier.height(10.dp))
        GlassCard(padding = 8.dp) {
            RowItem(Icons.Filled.Image, stringResource(R.string.admin_assets), onClick = { onOpen("assets") }, tint = AppColors.Blue)
            RowItem(Icons.Filled.Person, stringResource(R.string.admin_creator_requests), onClick = { onOpen("creators") }, tint = AppColors.Purple)
            RowItem(Icons.Filled.Settings, stringResource(R.string.admin_plans_and_payment), onClick = { onOpen("settings") }, tint = AppColors.Gold)
            RowItem(Icons.Filled.MonetizationOn, "AC Management", onClick = { onOpen("ac") }, tint = AppColors.Green)
            RowItem(Icons.Filled.Receipt, "Creator Tips", onClick = { onOpen("tips") }, tint = AppColors.Gold)
            RowItem(Icons.Filled.Chat, stringResource(R.string.admin_chat), onClick = { onOpen("chat") }, tint = AppColors.Blue)
            RowItem(Icons.Filled.History, stringResource(R.string.admin_audit_log), onClick = { onOpen("audit") }, tint = AppColors.Muted)
            RowItem(Icons.Filled.AdminPanelSettings, stringResource(R.string.admin_title), onClick = { onOpen("back") }, tint = AppColors.Red)
        }
        Spacer(Modifier.height(96.dp))
    }
}

/* ============================ شاشات الإدارة الفرعية ============================ */

/** إدارة الأصول: أفاتار/بانر/ملصق/أداة — مع رفع الصور وحذفها. */
@Composable
fun AdminAssetsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var assets by remember { mutableStateOf<List<SiteAsset>>(emptyList()) }
    var type by remember { mutableStateOf("avatar") }
    var plan by remember { mutableStateOf("free") }
    var name by remember { mutableStateOf("") }
    var packId by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mime = context.contentResolver.getType(uri) ?: "image/png"
            val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: "asset.png"
            container.assets.create(
                assetType = type,
                name = name.ifBlank { fileName },
                requiredPlan = plan,
                bytes = bytes,
                mime = mime,
                fileName = fileName,
                packId = packId.takeIf { it.isNotBlank() },
            ).onSuccess {
                message = "✓ $fileName"
                assets = container.assets.all()
            }.onFailure { message = it.message }
        }
    }

    LaunchedEffect(Unit) {
        loading = true
        assets = container.assets.all()
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.admin_assets), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Text("ASSET MANAGEMENT", style = AppText.kicker, color = AppColors.Blue)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("avatar", "banner", "sticker", "item").forEach { t ->
                    FilterChip(t, type == t, { type = t })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("free", "epic", "legendary", "admin").forEach { p ->
                    FilterChip(p, plan == p, { plan = p })
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.auth_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (type == "sticker") {
                OutlinedTextField(
                    value = packId,
                    onValueChange = { packId = it },
                    label = { Text("Pack id (meme / Minecraft_Memes1 …)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            GradientButton(
                text = stringResource(R.string.admin_save),
                onClick = { picker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.tiny, color = AppColors.Green)
            }
            SectionHeader(title = stringResource(R.string.admin_assets), leadingEmoji = "🖼")
            if (loading) {
                LoadingBox()
            } else {
                assets.take(60).forEach { asset ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Elevated)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        AvatarImage(url = assetUrl(container, asset.fileUrl), size = 40.dp)
                        Column(Modifier.weight(1f)) {
                            Text(asset.name, style = AppText.bodySmall, color = AppColors.Text, maxLines = 1)
                            Text(
                                "${asset.assetType} · ${asset.requiredPlan}",
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                        }
                        androidx.compose.material3.Icon(
                            Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.common_delete),
                            tint = AppColors.Red,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable {
                                    scope.launch {
                                        container.assets.delete(asset.id)
                                        assets = container.assets.all()
                                    }
                                },
                        )
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** مراجعة طلبات المبدعين + مراجعة محتوى المبدعين + تِيبس + AC + سجل الإدارة + إعدادات الخطط. */
@Composable
fun AdminCreatorReviewScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var applications by remember { mutableStateOf<List<CreatorApplication>>(emptyList()) }
    var submissions by remember { mutableStateOf<List<CreatorSubmission>>(emptyList()) }
    var tips by remember { mutableStateOf<List<CreatorTip>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(0) }

    LaunchedEffect(tab) {
        applications = container.admin.creatorApplications()
        submissions = container.admin.submissions()
        tips = container.admin.tips()
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.admin_creator_requests), onBack = onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(stringResource(R.string.admin_creator_requests), tab == 0, { tab = 0 })
            FilterChip("CREATOR REVIEW", tab == 1, { tab = 1 })
            FilterChip("TIPS", tab == 2, { tab = 2 })
        }
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.tiny, color = AppColors.Green)
            }
            when (tab) {
                0 -> {
                    if (applications.isEmpty()) EmptyState(stringResource(R.string.common_none))
                    applications.forEach { application ->
                        GlassCard(padding = 10.dp) {
                            Text(
                                "#${application.id} · ${application.status}",
                                style = AppText.sectionTitle,
                                color = AppColors.Blue,
                            )
                            Text(
                                listOfNotNull(
                                    application.about,
                                    application.experience,
                                    application.publishingPlan,
                                    application.previousWorkUrl,
                                    application.portfolioUrls,
                                    application.motivation,
                                    application.bio,
                                ).joinToString("\n"),
                                style = AppText.bodySmall,
                                color = AppColors.Muted,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GradientButton(
                                    text = stringResource(R.string.admin_approve),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewApplication(application.id, "approve", null)
                                                .onSuccess { message = "✓ approved" }
                                                .onFailure { message = it.message }
                                            applications = container.admin.creatorApplications()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = "More info",
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewApplication(application.id, "more_info", "Please send more details")
                                            applications = container.admin.creatorApplications()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.admin_reject),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewApplication(application.id, "reject", "Not eligible yet")
                                            applications = container.admin.creatorApplications()
                                        }
                                    },
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                1 -> {
                    if (submissions.isEmpty()) EmptyState(stringResource(R.string.common_none))
                    submissions.forEach { submission ->
                        GlassCard(padding = 10.dp) {
                            Text(submission.title, style = AppText.sectionTitle, color = AppColors.Text)
                            Text(
                                "${submission.contentType} · ${submission.plan} · ${submission.status}",
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                            submission.description?.let {
                                Text(it, style = AppText.bodySmall, color = AppColors.Muted, maxLines = 3)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GradientButton(
                                    text = stringResource(R.string.admin_approve),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewSubmission(submission.id, "approve", null)
                                            submissions = container.admin.submissions()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.admin_reject),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewSubmission(submission.id, "reject", "Needs changes")
                                            submissions = container.admin.submissions()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                else -> {
                    if (tips.isEmpty()) EmptyState(stringResource(R.string.common_none))
                    tips.forEach { tip ->
                        GlassCard(padding = 10.dp) {
                            Text(
                                "${tip.amount} LYD · ${tip.paymentMethod} · ${tip.status}",
                                style = AppText.sectionTitle,
                                color = AppColors.Gold,
                            )
                            tip.message?.let { Text(it, style = AppText.bodySmall, color = AppColors.Muted) }
                            Text(
                                "${tip.transactionReference ?: "—"} · ${tip.receiptUrl ?: "—"}",
                                style = AppText.tiny,
                                color = AppColors.Dim,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GradientButton(
                                    text = stringResource(R.string.admin_approve),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewTip(tip.id, true, null)
                                            tips = container.admin.tips()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.admin_reject),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewTip(tip.id, false, "Receipt not verified")
                                            tips = container.admin.tips()
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** إدارة AC: بحث عن مستخدم، منح/خصم، منح جماعي، وسجل المعاملات. */
@Composable
fun AdminAcScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var amount by remember { mutableStateOf("10") }
    var transactions by remember { mutableStateOf<List<Map<String, kotlinx.serialization.json.JsonElement>>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        users = container.admin.users(query, null, 20)
        transactions = container.admin.acTransactions(50)
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = "AC Management", onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text(stringResource(R.string.admin_search_users)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it.filter { ch -> ch.isDigit() } },
                label = { Text("AC") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = stringResource(R.string.admin_reward) + " (all)",
                onClick = {
                    scope.launch {
                        container.admin.acGrantAll(amount.toIntOrNull() ?: 10, "Admin Reward")
                            .onSuccess { message = "✓ $it users" }
                            .onFailure { message = it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.tiny, color = AppColors.Green)
            }
            SectionHeader(title = stringResource(R.string.admin_users), leadingEmoji = "👥")
            users.forEach { user ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated)
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(user.username ?: user.id.take(6), style = AppText.bodySmall, color = AppColors.Text, modifier = Modifier.weight(1f))
                    GhostButton(
                        text = "+",
                        onClick = {
                            scope.launch {
                                container.admin.acAdjust(user.id, amount.toIntOrNull() ?: 10, "Admin Reward")
                                    .onSuccess { message = "✓ +$amount" }
                                    .onFailure { message = it.message }
                            }
                        },
                    )
                    GhostButton(
                        text = "−",
                        onClick = {
                            scope.launch {
                                container.admin.acAdjust(user.id, -(amount.toIntOrNull() ?: 10), "Admin Adjustment")
                                    .onFailure { message = it.message }
                            }
                        },
                    )
                }
            }
            SectionHeader(title = stringResource(R.string.ac_history), leadingEmoji = "🧾")
            transactions.forEach { tx ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        (tx["amount"] as? JsonPrimitive)?.content ?: "",
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                    )
                    Spacer(Modifier.padding(horizontal = 4.dp))
                    Text(
                        (tx["reason"] as? JsonPrimitive)?.content ?: "",
                        style = AppText.tiny,
                        color = AppColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** إعدادات الإدارة: أسعار الخطط ومدّتها وحدود التحميل + طرق الدفع. */
@Composable
fun AdminSettingsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var plans by remember { mutableStateOf<List<com.addonstation.mobile.data.model.SubscriptionPlan>>(emptyList()) }
    var methods by remember { mutableStateOf<List<com.addonstation.mobile.data.model.PaymentMethodSetting>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var methodName by remember { mutableStateOf("") }
    var methodCode by remember { mutableStateOf("") }
    var accountName by remember { mutableStateOf("") }
    var accountId by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        plans = container.admin.plans()
        methods = container.plans.loadAllPaymentMethods()
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.admin_plans_and_payment), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = AppText.tiny, color = AppColors.Green)
            }
            SectionHeader(title = stringResource(R.string.plan_title), leadingEmoji = "💠")
            plans.forEach { plan ->
                var price by remember(plan.id) { mutableStateOf(plan.priceLyd.toString()) }
                var days by remember(plan.id) { mutableStateOf(plan.durationDays.toString()) }
                var limit by remember(plan.id) { mutableStateOf(plan.downloadLimit?.toString() ?: "") }
                GlassCard(padding = 10.dp) {
                    Text(plan.name, style = AppText.sectionTitle, color = AppColors.Text)
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price LYD") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = days,
                        onValueChange = { days = it },
                        label = { Text("Duration (days)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = limit,
                        onValueChange = { limit = it },
                        label = { Text("Download limit (empty = ∞)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GradientButton(
                        text = stringResource(R.string.admin_save),
                        onClick = {
                            scope.launch {
                                container.admin.updatePlan(
                                    planId = plan.id,
                                    priceLyd = price.toDoubleOrNull(),
                                    priceUsd = null,
                                    durationDays = days.toIntOrNull(),
                                    downloadLimit = limit.toIntOrNull(),
                                    limitPeriod = if (limit.isBlank()) "none" else "day",
                                    active = true,
                                ).onSuccess { message = "✓ ${plan.id}" }
                                    .onFailure { message = it.message }
                                plans = container.admin.plans()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            SectionHeader(title = stringResource(R.string.plan_payment_method), leadingEmoji = "💳")
            methods.forEach { method ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated)
                        .padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(method.name, style = AppText.bodySmall, color = AppColors.Text)
                        Text(
                            "${method.code} · ${method.accountIdentifier ?: ""} · ${if (method.enabled) "enabled" else "disabled"}",
                            style = AppText.tiny,
                            color = AppColors.Muted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    GhostButton(
                        text = if (method.enabled) "ON" else "OFF",
                        onClick = {
                            scope.launch {
                                container.admin.upsertPaymentMethod(
                                    code = method.code,
                                    name = method.name,
                                    accountName = method.accountName,
                                    accountIdentifier = method.accountIdentifier,
                                    instructions = method.instructions,
                                    enabled = !method.enabled,
                                    sortOrder = method.sortOrder,
                                    provider = "manual",
                                )
                                methods = container.plans.loadAllPaymentMethods()
                            }
                        },
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            GlassCard(padding = 10.dp) {
                Text("+ " + stringResource(R.string.plan_payment_method), style = AppText.sectionTitle, color = AppColors.Text)
                OutlinedTextField(methodCode, { methodCode = it }, label = { Text("code") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(methodName, { methodName = it }, label = { Text("name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(accountName, { accountName = it }, label = { Text(stringResource(R.string.plan_beneficiary)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(accountId, { accountId = it }, label = { Text(stringResource(R.string.plan_account_number)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(instructions, { instructions = it }, label = { Text(stringResource(R.string.plan_order_instructions)) }, modifier = Modifier.fillMaxWidth())
                GradientButton(
                    text = stringResource(R.string.admin_save),
                    onClick = {
                        scope.launch {
                            container.admin.upsertPaymentMethod(
                                code = methodCode,
                                name = methodName,
                                accountName = accountName.takeIf { it.isNotBlank() },
                                accountIdentifier = accountId.takeIf { it.isNotBlank() },
                                instructions = instructions.takeIf { it.isNotBlank() },
                                enabled = true,
                                sortOrder = methods.size,
                                provider = if (methodCode == "stripe") "stripe" else "manual",
                            ).onSuccess { message = "✓ $methodCode" }
                                .onFailure { message = it.message }
                            methods = container.plans.loadAllPaymentMethods()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** سجل الإدارة (Audit log) — للقراءة فقط. */
@Composable
fun AdminAuditScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    var rows by remember { mutableStateOf<List<Map<String, kotlinx.serialization.json.JsonElement>>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        rows = container.admin.auditLog(200)
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.admin_audit_log), onBack = onBack)
        if (loading) {
            LoadingBox(text = stringResource(R.string.common_loading))
        } else if (rows.isEmpty()) {
            EmptyState(stringResource(R.string.common_none))
        } else {
            LazyColumn(contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp)) {
                items(rows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Elevated)
                            .padding(8.dp),
                    ) {
                        Column {
                            Text(
                                (row["action"] as? JsonPrimitive)?.content ?: "",
                                style = AppText.bodySmall,
                                color = AppColors.Text,
                            )
                            Text(
                                ((row["details"] as? JsonPrimitive)?.content ?: "") + " · " +
                                    ((row["created_at"] as? JsonPrimitive)?.content ?: ""),
                                style = AppText.tiny,
                                color = AppColors.Muted,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** إدارة الشات من اللوحة: الغرف، الإعلانات، كتم/حظر، وقواعد المجتمع لكل لغة. */
@Composable
fun AdminChatScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<com.addonstation.mobile.data.model.ChatChannel>>(emptyList()) }
    var announcement by remember { mutableStateOf("") }
    var targetChannel by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var rulesLang by remember { mutableStateOf("ar") }
    var rulesText by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        channels = container.chat.channels()
        rulesLang = container.prefs.readLocale()
        rulesText = container.chat.rules(rulesLang).joinToString("\n")
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.admin_chat), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            SectionHeader(title = stringResource(R.string.comm_main_rooms), leadingEmoji = "💬")
            channels.forEach { channel ->
                var slow by remember(channel.id) { mutableStateOf(channel.slowmodeSeconds) }
                GlassCard(padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("${channel.icon ?: ""} ${channel.name}", style = AppText.sectionTitle, color = AppColors.Text)
                        Spacer(Modifier.weight(1f))
                        Text(channel.category, style = AppText.tiny, color = AppColors.Muted)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(0, 5, 10, 30, 60, 300).forEach { seconds ->
                            FilterChip("${seconds}s", slow == seconds, { slow = seconds })
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("everyone", "members", "creators_admin", "admin_only").forEach { perm ->
                            FilterChip(
                                perm,
                                channel.postPermission == perm,
                                {
                                    scope.launch {
                                        container.chat.upsertRoom(
                                            id = channel.id,
                                            name = channel.name,
                                            icon = channel.icon,
                                            category = channel.category,
                                            description = channel.description,
                                            sortOrder = channel.sortOrder,
                                            isLocked = channel.isLocked,
                                            slowmodeSeconds = slow,
                                            postPermission = perm,
                                        )
                                        channels = container.chat.channels()
                                    }
                                },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(
                            text = if (channel.isLocked) "🔒 on" else "🔓 off",
                            onClick = {
                                scope.launch {
                                    container.chat.upsertRoom(
                                        id = channel.id,
                                        name = channel.name,
                                        icon = channel.icon,
                                        category = channel.category,
                                        description = channel.description,
                                        sortOrder = channel.sortOrder,
                                        isLocked = !channel.isLocked,
                                        slowmodeSeconds = slow,
                                        postPermission = channel.postPermission,
                                    )
                                    channels = container.chat.channels()
                                }
                            },
                            modifier = Modifier.weight(1f),
                        )
                        GhostButton(
                            text = stringResource(R.string.common_delete),
                            onClick = {
                                scope.launch {
                                    container.chat.deleteRoom(channel.id)
                                    channels = container.chat.channels()
                                }
                            },
                            borderColor = AppColors.Red.copy(alpha = 0.5f),
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            SectionHeader(title = "📢 " + stringResource(R.string.chat_announcement), leadingEmoji = "📢")
            OutlinedTextField(
                value = announcement,
                onValueChange = { announcement = it },
                label = { Text(stringResource(R.string.chat_announcement)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(stringResource(R.string.common_all), targetChannel == null, { targetChannel = null })
                channels.take(4).forEach { channel ->
                    FilterChip(channel.name, targetChannel == channel.id, { targetChannel = channel.id })
                }
            }
            Spacer(Modifier.height(8.dp))
            GradientButton(
                text = stringResource(R.string.chat_send),
                onClick = {
                    scope.launch {
                        container.chat.sendAnnouncement(targetChannel, announcement)
                            .onSuccess { message = "✓" }
                            .onFailure { message = it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = AppText.tiny, color = AppColors.Green)
            }

            SectionHeader(title = stringResource(R.string.chat_rules_title), leadingEmoji = "📜")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("ar", "en", "tr", "fr").forEach { lang ->
                    FilterChip(lang, rulesLang == lang, {
                        rulesLang = lang
                        scope.launch { rulesText = container.chat.rules(lang).joinToString("\n") }
                    })
                }
            }
            OutlinedTextField(
                value = rulesText,
                onValueChange = { rulesText = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
            )
            GradientButton(
                text = stringResource(R.string.admin_save),
                onClick = {
                    scope.launch {
                        container.chat.updateRules(
                            lang = rulesLang,
                            title = null,
                            rules = rulesText.split("\n").filter { it.isNotBlank() },
                        ).onSuccess { message = "✓ $rulesLang" }
                            .onFailure { message = it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(96.dp))
        }
    }
}
