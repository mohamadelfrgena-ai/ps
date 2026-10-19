package com.addonstation.mobile.ui.admin

import coil.compose.AsyncImage

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import com.addonstation.mobile.ui.components.bottomBarInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ChatReport
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorApplication
import com.addonstation.mobile.data.model.CreatorSubmission
import com.addonstation.mobile.data.model.CreatorTip
import com.addonstation.mobile.data.model.PaymentOrder
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.SiteAsset
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.common.ConfirmDialog
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.CounterCard
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.RoomIcon
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.RowItem
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.specDashedBorder
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.plans.StatusPill
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonPrimitive
import java.util.Locale

/** A bottom-bar selection made from an admin detail route is consumed by the restored panel. */
internal object AdminPanelTabRequest {
    private val state = androidx.compose.runtime.mutableIntStateOf(-1)
    private val moreState = androidx.compose.runtime.mutableIntStateOf(0)
    val tab: Int get() = state.intValue
    val moreRequest: Int get() = moreState.intValue
    fun requestTab(index: Int) { state.intValue = index }
    fun requestMore() { moreState.intValue += 1 }
    fun clear() { state.intValue = -1 }
    fun clearMore() { moreState.intValue = 0 }
}

/**
 * لوحة الإدارة (Admin فقط). الفحص الحقيقي على الخادم: كل دالة RPC تتحقق من is_admin،
 * وأي محاولة من غير الأدمن تُرفض من قاعدة البيانات لا من الواجهة فقط.
 */
@Composable
fun AdminPanelScreen(
    onBackToApp: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenContent: () -> Unit,
    onOpenBulkImport: () -> Unit,
    onEditContent: (ContentItem) -> Unit,
    onOpenPayments: () -> Unit,
    onOpenModeration: () -> Unit,
    onOpenAssets: () -> Unit,
    onOpenCreatorApps: () -> Unit,
    onOpenTips: () -> Unit,
    onOpenAc: (String?) -> Unit,
    onOpenAudit: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val stats by container.state.adminStats.collectAsState()
    val scope = rememberCoroutineScope()
    val palette = LocalHomePalette.current
    val savedTabState = androidx.compose.runtime.saveable.rememberSaveableStateHolder()

    var tab by remember { mutableIntStateOf(0) } // Overview, Users, Payments, Content, Chat
    var moreOpen by remember { mutableStateOf(false) }
    val moreRequest = AdminPanelTabRequest.moreRequest
    LaunchedEffect(moreRequest) {
        if (moreRequest > 0) {
            moreOpen = true
            AdminPanelTabRequest.clearMore()
        }
    }
    var scrollToTopRequest by remember { mutableIntStateOf(0) }
    var users by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var content by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var orders by remember { mutableStateOf<List<PaymentOrder>>(emptyList()) }
    var reports by remember { mutableStateOf<List<ChatReport>>(emptyList()) }
    var moderators by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var acHistory by remember { mutableStateOf<List<Map<String, kotlinx.serialization.json.JsonElement>>>(emptyList()) }
    var userQuery by remember { mutableStateOf("") }
    var roleFilter by remember { mutableStateOf("all") }
    var message by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var dialog by remember { mutableStateOf<String?>(null) }
    var pendingDeleteContent by remember { mutableStateOf<ContentItem?>(null) }
    var pendingBanReport by remember { mutableStateOf<ChatReport?>(null) }
    var roomsFor by remember { mutableStateOf<Profile?>(null) }
    val roomsSavedMsg = stringResource(R.string.admin_rooms_saved_plain)
    val roomsFailedMsg = stringResource(R.string.msg_generic_error)

    if (profile != null && profile?.role != "admin") {
        AdminPermissionDenied(onBack = onBackToApp, palette = palette)
        return
    }

    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableIntStateOf(0) }

    LaunchedEffect(tab, userQuery, roleFilter, reloadTick) {
        loading = true
        failed = false
        try {
            container.state.setAdminStats(container.admin.stats())
            when (tab) {
                0 -> acHistory = container.admin.acTransactions(5)
                1 -> {
                    val apiRole = roleFilter.takeUnless { it.startsWith("plan:") }
                    users = container.admin.users(userQuery, apiRole, 200)
                }
                2 -> orders = container.admin.orders()
                3 -> content = container.admin.allContent(200)
                4 -> {
                    reports = container.chat.openReports()
                    moderators = container.admin.users("", "moderator", 200)
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    fun selectTab(index: Int) {
        if (!moreOpen && tab == index) {
            scrollToTopRequest += 1
        } else {
            moreOpen = false
            tab = index
        }
    }

    val requestedTab = AdminPanelTabRequest.tab
    LaunchedEffect(requestedTab) {
        if (requestedTab in 0..4) {
            moreOpen = false
            tab = requestedTab
            scrollToTopRequest += 1
            AdminPanelTabRequest.clear()
        }
    }

    val kickerRes = when {
        moreOpen -> R.string.admin_kicker_dashboard
        tab == 2 -> R.string.admin_kicker_payments
        tab == 3 -> R.string.admin_kicker_content
        tab == 4 -> R.string.admin_kicker_chat
        else -> R.string.admin_kicker_dashboard
    }
    val titleRes = when {
        moreOpen -> R.string.admin_more
        tab == 0 -> R.string.admin_overview
        tab == 1 -> R.string.admin_users
        tab == 2 -> R.string.admin_payments
        tab == 3 -> R.string.admin_content
        else -> R.string.admin_chat_plain
    }

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        AdminHeader(
            kicker = stringResource(kickerRes),
            title = stringResource(titleRes),
            onBack = { if (moreOpen) moreOpen = false else onBackToApp() },
            onMore = { moreOpen = true },
            onLock = {
                AdminSession.lock()
                onBackToApp()
            },
            palette = palette,
        )
        Box(Modifier.weight(1f)) {
            savedTabState.SaveableStateProvider(if (moreOpen) "admin-more" else "admin-tab-$tab") {
                if (moreOpen) {
                    MoreTab(
                        onOpen = { key ->
                            when (key) {
                                "assets" -> onOpenAssets()
                                "creators" -> onOpenCreatorApps()
                                "settings" -> onOpenSettings()
                                "ac" -> onOpenAc(null)
                                "tips" -> onOpenTips()
                                "chat" -> onOpenChat()
                                "audit" -> onOpenAudit()
                            }
                        },
                        onLockNow = {
                            AdminSession.lock()
                            onBackToApp()
                        },
                        scrollToTopRequest = scrollToTopRequest,
                        palette = palette,
                    )
                } else when (tab) {
                    0 -> OverviewTab(
                        stats = stats,
                        acHistory = acHistory,
                        loading = loading,
                        failed = failed,
                        onRetry = { reloadTick += 1 },
                        onOpenTab = ::selectTab,
                        onOpenAc = { onOpenAc(null) },
                        onOpenCreators = onOpenCreatorApps,
                        scrollToTopRequest = scrollToTopRequest,
                    )
                    1 -> UsersTab(
                        users = users,
                        query = userQuery,
                        onQuery = { userQuery = it },
                        roleFilter = roleFilter,
                        onRoleFilter = { roleFilter = it },
                        loading = loading,
                        failed = failed,
                        onRetry = { reloadTick += 1 },
                        onGiveAc = { userId -> onOpenAc(userId) },
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
                        onManageRooms = { user -> roomsFor = user },
                        scrollToTopRequest = scrollToTopRequest,
                    )
                    2 -> PaymentsTab(
                        orders = orders,
                        loading = loading,
                        failed = failed,
                        onRetry = { reloadTick += 1 },
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
                        scrollToTopRequest = scrollToTopRequest,
                    )
                    3 -> ContentTab(
                        content = content,
                        loading = loading,
                        failed = failed,
                        onRetry = { reloadTick += 1 },
                        onOpenContentAdmin = onOpenContent,
                        onOpenBulkImport = onOpenBulkImport,
                        onEditContent = onEditContent,
                        onDeleteContent = { item -> pendingDeleteContent = item },
                        onRefresh = { reloadTick += 1 },
                        scrollToTopRequest = scrollToTopRequest,
                    )
                    else -> ModerationTab(
                        reports = reports,
                        moderators = moderators,
                        loading = loading,
                        failed = failed,
                        onRetry = { reloadTick += 1 },
                        onHideMessage = { report ->
                            scope.launch {
                                val messageId = report.messageId
                                if (messageId == null) {
                                    message = roomsFailedMsg
                                    return@launch
                                }
                                container.chat.moderateMessage(messageId, true)
                                    .onSuccess {
                                        container.chat.closeReport(report.id, "hide")
                                            .onSuccess {
                                                reports = container.chat.openReports()
                                                message = null
                                            }
                                            .onFailure { failure -> message = failure.message ?: roomsFailedMsg }
                                    }
                                    .onFailure { failure -> message = failure.message ?: roomsFailedMsg }
                            }
                        },
                        onMuteAuthor = { report, minutes ->
                            scope.launch {
                                val target = container.chat.reportedUserId(report)
                                if (target.isNullOrBlank()) {
                                    message = roomsFailedMsg
                                } else {
                                    container.chat.muteUser(target, minutes, "Report #${report.id}")
                                        .onSuccess {
                                            container.chat.closeReport(report.id, "mute")
                                            reports = container.chat.openReports()
                                        }
                                        .onFailure { message = it.message ?: roomsFailedMsg }
                                }
                            }
                        },
                        onBanUser = { report -> pendingBanReport = report },
                        onClose = { report ->
                            scope.launch {
                                container.chat.closeReport(report.id, "close")
                                reports = container.chat.openReports()
                            }
                        },
                        onOpenRooms = onOpenChat,
                        onManageModerator = { user -> roomsFor = user },
                        scrollToTopRequest = scrollToTopRequest,
                    )
                }
            }
        }

        message?.let { text ->
            Text(
                text,
                style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = if (text.startsWith("✓")) adminOkText(palette) else adminErrorText(palette),
                modifier = Modifier
                    .fillMaxWidth()
                    .background(palette.barSurface)
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        AdminBottomBar(
            selectedTab = if (moreOpen) null else tab,
            paymentsBadge = stats?.pendingOrders ?: 0,
            chatBadge = stats?.openReports ?: 0,
            onSelect = ::selectTab,
            palette = palette,
        )
    }

    dialog?.let { url ->
        com.addonstation.mobile.ui.common.FullScreenImageViewer(url = url) { dialog = null }
    }

    roomsFor?.let { user ->
        ModeratorRoomsDialog(
            user = user,
            onDismiss = { roomsFor = null },
            onResult = { ok ->
                roomsFor = null
                message = if (ok) roomsSavedMsg else roomsFailedMsg
            },
        )
    }

    pendingDeleteContent?.let { item ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_content_label),
            text = item.title,
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                pendingDeleteContent = null
                scope.launch {
                    container.admin.deleteContent(item.id)
                        .onSuccess {
                            content = container.admin.allContent(200)
                            message = null
                        }
                        .onFailure { message = it.message ?: roomsFailedMsg }
                }
            },
            onDismiss = { pendingDeleteContent = null },
        )
    }

    pendingBanReport?.let { report ->
        ConfirmDialog(
            title = stringResource(R.string.admin_ban_user),
            text = report.note?.takeIf(String::isNotBlank)
                ?: "#${report.id} · ${report.reason} · ${report.targetUserId?.take(8) ?: report.messageId ?: "—"}",
            confirmText = stringResource(R.string.admin_ban_user),
            onConfirm = {
                pendingBanReport = null
                scope.launch {
                    val target = container.chat.reportedUserId(report)
                    if (target.isNullOrBlank()) {
                        message = roomsFailedMsg
                        return@launch
                    }
                    container.chat.banUser(target, "Report #${report.id}")
                        .onSuccess {
                            container.chat.closeReport(report.id, "ban")
                            reports = container.chat.openReports()
                            message = "✓"
                        }
                        .onFailure { message = it.message ?: roomsFailedMsg }
                }
            },
            onDismiss = { pendingBanReport = null },
        )
    }
}

@Composable
private fun OverviewTab(
    stats: com.addonstation.mobile.data.model.AdminStats?,
    acHistory: List<Map<String, kotlinx.serialization.json.JsonElement>>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onOpenTab: (Int) -> Unit,
    onOpenAc: () -> Unit,
    onOpenCreators: () -> Unit,
    scrollToTopRequest: Int,
) {
    val palette = LocalHomePalette.current
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) scrollState.animateScrollTo(0)
    }
    Column(
        Modifier.fillMaxSize().imePadding().verticalScroll(scrollState).padding(horizontal = 16.dp),
    ) {
        Spacer(Modifier.height(12.dp))
        val statCards = listOf(
            StatAction("${stats?.users ?: 0}", stringResource(R.string.admin_users), false) { onOpenTab(1) },
            StatAction("${stats?.pendingOrders ?: 0}", stringResource(R.string.admin_payments), (stats?.pendingOrders ?: 0) > 0) { onOpenTab(2) },
            StatAction("${stats?.openReports ?: 0}", stringResource(R.string.admin_chat_reports_plain), (stats?.openReports ?: 0) > 0) { onOpenTab(4) },
            StatAction("${stats?.pendingCreatorApps ?: 0}", stringResource(R.string.admin_creator_requests_plain), (stats?.pendingCreatorApps ?: 0) > 0) { onOpenCreators() },
            StatAction("${stats?.addons ?: 0}", stringResource(R.string.admin_addons), false) { onOpenTab(3) },
            StatAction("${stats?.worlds ?: 0}", stringResource(R.string.admin_worlds), false) { onOpenTab(3) },
        )
        statCards.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { stat ->
                    AdminStatCard(
                        value = stat.value,
                        label = stat.label,
                        attention = stat.attention,
                        onClick = stat.onClick,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        AdminSectionHeading(stringResource(R.string.admin_tools), palette)
        AdminToolCard(
            icon = Icons.Filled.MonetizationOn,
            title = stringResource(R.string.admin_ac_tools),
            subtitle = stringResource(R.string.admin_give_ac_title),
            accent = palette.goldText,
            onClick = onOpenAc,
            palette = palette,
        )
        Spacer(Modifier.height(8.dp))
        AdminToolCard(
            icon = Icons.Filled.CardGiftcard,
            title = stringResource(R.string.admin_codes_title),
            subtitle = stringResource(R.string.admin_codes_hint),
            accent = palette.epic,
            onClick = onOpenAc,
            palette = palette,
        )

        AdminSectionHeading(stringResource(R.string.admin_give_ac_history), palette)
        if (acHistory.isEmpty()) {
            Text(stringResource(R.string.admin_give_ac_none), style = TextStyle(fontSize = 12.sp), color = palette.textMuted, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            acHistory.take(5).forEach { transaction ->
                val amount = (transaction["amount"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val owner = (transaction["user_id"] as? JsonPrimitive)?.content.orEmpty()
                val reason = (transaction["reason"] as? JsonPrimitive)?.content.orEmpty()
                val kind = (transaction["kind"] as? JsonPrimitive)?.content.orEmpty()
                val createdAt = (transaction["created_at"] as? JsonPrimitive)?.content
                val rowShape = RoundedCornerShape(14.dp)
                Row(
                    Modifier.fillMaxWidth().clip(rowShape).background(palette.surface).border(1.dp, palette.border, rowShape).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background((if (amount < 0) adminError(palette) else adminOk(palette)).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = if (amount < 0) adminErrorText(palette) else adminOkText(palette), modifier = Modifier.size(20.dp))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(acTransactionKindLabel(kind), style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOfNotNull(owner.takeIf(String::isNotBlank)?.take(8), TimeFormat.dateTime(createdAt, Locale.getDefault())).joinToString(" · "),
                            style = TextStyle(fontSize = 11.sp),
                            color = palette.textMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (reason.isNotBlank()) Text(reason, style = TextStyle(fontSize = 11.sp), color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(
                        (if (amount > 0) "+" else "") + amount + " AC",
                        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                        color = if (amount < 0) adminErrorText(palette) else adminOkText(palette),
                    )
                }
                Spacer(Modifier.height(6.dp))
            }
        }

        if (failed) {
            Spacer(Modifier.height(14.dp))
            AdminPanelState(
                title = stringResource(R.string.msg_generic_error),
                palette = palette,
                onRetry = onRetry,
            )
        } else if (loading && stats == null) {
            Spacer(Modifier.height(18.dp))
            LoadingBox(text = stringResource(R.string.common_loading))
        }
        Spacer(Modifier.height(24.dp))
    }
}

private data class StatAction(
    val value: String,
    val label: String,
    val attention: Boolean,
    val onClick: () -> Unit,
)

@Composable
private fun acTransactionKindLabel(kind: String): String = when (kind.lowercase()) {
    "task" -> stringResource(R.string.ac_kind_task)
    "daily" -> stringResource(R.string.ac_kind_daily)
    "streak" -> stringResource(R.string.ac_kind_streak)
    "unlock" -> stringResource(R.string.ac_kind_unlock)
    "event" -> stringResource(R.string.ac_kind_event)
    "welcome" -> stringResource(R.string.ac_kind_welcome)
    "admin_adjustment" -> stringResource(R.string.admin_reason_adjust)
    "admin_reward" -> stringResource(R.string.admin_reason_reward)
    "code" -> stringResource(R.string.ac_kind_code)
    else -> stringResource(R.string.ac_kind_admin)
}

@Composable
private fun UsersTab(
    users: List<Profile>,
    query: String,
    onQuery: (String) -> Unit,
    roleFilter: String,
    onRoleFilter: (String) -> Unit,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    onGiveAc: (String) -> Unit,
    onSaveUser: (Profile, String?, String?, Int?) -> Unit,
    onManageRooms: (Profile) -> Unit,
    scrollToTopRequest: Int,
) {
    val palette = LocalHomePalette.current
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    val visibleUsers = when (roleFilter) {
        "plan:legendary" -> users.filter { it.effectivePlan == "legendary" }
        "plan:epic" -> users.filter { it.effectivePlan == "epic" }
        "plan:free" -> users.filter { it.effectivePlan == "free" }
        else -> users
    }
    val filters = listOf(
        "all" to stringResource(R.string.common_all),
        "admin" to stringResource(R.string.grp_admin),
        "moderator" to stringResource(R.string.grp_moderators),
        "creator" to stringResource(R.string.grp_creators),
        "user" to stringResource(R.string.role_user),
        "plan:legendary" to stringResource(R.string.plan_legendary_name),
        "plan:epic" to stringResource(R.string.plan_epic_name),
        "plan:free" to stringResource(R.string.plan_free_name),
    )
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "search") {
            AdminSearchField(
                value = query,
                onValueChange = onQuery,
                placeholder = stringResource(R.string.admin_search_users),
                palette = palette,
            )
        }
        item(key = "filter-label") {
            Text(stringResource(R.string.admin_filter_role), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = palette.textMuted)
        }
        item(key = "filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { (key, label) ->
                    AdminFilterChip(label, roleFilter == key, { onRoleFilter(key) }, palette)
                }
            }
        }
        when {
            loading -> item(key = "loading") { LoadingBox(text = stringResource(R.string.common_loading)) }
            failed -> item(key = "error") { AdminPanelState(stringResource(R.string.msg_generic_error), palette, onRetry = onRetry) }
            visibleUsers.isEmpty() -> item(key = "empty") { AdminPanelState(stringResource(R.string.common_none), palette) }
            else -> items(visibleUsers.take(200), key = { it.id }) { user ->
                AdminUserRow(
                    user = user,
                    onGiveAc = onGiveAc,
                    onSave = onSaveUser,
                    onManageRooms = onManageRooms,
                )
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun AdminPanelState(
    title: String,
    palette: HomePalette,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(72.dp).clip(CircleShape).background(palette.surfaceHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(34.dp))
        }
        Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = palette.text, textAlign = TextAlign.Center)
        onRetry?.let { AdminPrimaryButton(stringResource(R.string.common_retry), it, palette, modifier = Modifier.widthIn(min = 120.dp)) }
    }
}

/**
 * نافذة **إشراف الغرف**: يمنح الأدمن مشرفاً صلاحية الإشراف على غرفة واحدة،
 * أو عدّة غرف، أو كل الغرف دفعة واحدة.
 *
 * النطاق محفوظ في جدول chat_moderator_rooms (ملف 29) ويقرأه الخادم عند كل
 * طلب إشراف (حذف/إخفاء/تثبيت) — فلا يُنفَّذ شيء خارج النطاق ولو عُبث بالواجهة.
 */
@Composable
private fun ModeratorRoomsDialog(
    user: Profile,
    onDismiss: () -> Unit,
    onResult: (Boolean) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var channels by remember { mutableStateOf<List<com.addonstation.mobile.data.model.ChatChannel>>(emptyList()) }
    var allRooms by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var loading by remember { mutableStateOf(true) }
    var saving by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    // نصوص جاهزة: لا يُستدعى stringResource داخل لامبدا غير Composable
    val saveLabel = stringResource(R.string.common_save)
    val cancelLabel = stringResource(R.string.common_cancel)

    LaunchedEffect(user.id) {
        loading = true
        channels = container.chat.channels()
        val current = container.chat.moderatorRooms(user.id)
        allRooms = current.allRooms
        selected = current.channels.toSet()
        loading = false
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        title = {
            Text(
                stringResource(R.string.admin_rooms_manage),
                style = AppText.sectionTitle,
                color = AppColors.Text,
            )
        },
        text = {
            Column {
                Text(
                    stringResource(R.string.admin_rooms_choose),
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                // «كل الغرف» — تبديل واحد يعفيه من اختيار الغرف واحدةً واحدة
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (allRooms) AppColors.GreenGlow else AppColors.Elevated)
                        .border(1.dp, if (allRooms) AppColors.Green else AppColors.Border, RoundedCornerShape(12.dp))
                        .clickable { allRooms = !allRooms }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🌐", style = AppText.body)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        stringResource(R.string.admin_rooms_all),
                        style = AppText.bodySmall,
                        color = if (allRooms) AppColors.Green else AppColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (allRooms) Text("✅", style = AppText.bodySmall)
                }
                Spacer(Modifier.height(8.dp))
                if (loading) {
                    com.addonstation.mobile.ui.components.LoadingBox()
                } else {
                    Text(
                        stringResource(R.string.admin_rooms_hint),
                        style = AppText.tiny,
                        color = AppColors.Dim,
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .height(260.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        channels.forEach { channel ->
                            val checked = selected.contains(channel.id)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(AppColors.Elevated)
                                    .clickable(enabled = !allRooms) {
                                        selected = if (checked) selected - channel.id else selected + channel.id
                                    }
                                    .padding(horizontal = 10.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(channel.icon ?: "💬", style = AppText.body)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    com.addonstation.mobile.ui.community.channelDisplayName(channel),
                                    style = AppText.bodySmall,
                                    color = if (allRooms) AppColors.Dim else AppColors.Text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(if (checked && !allRooms) "✅" else "", style = AppText.bodySmall)
                            }
                        }
                    }
                }
                if (failed) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.msg_generic_error),
                        style = AppText.tiny,
                        color = AppColors.Red,
                    )
                }
            }
        },
        confirmButton = {
            com.addonstation.mobile.ui.components.GhostButton(
                text = saveLabel,
                onClick = {
                    saving = true
                    failed = false
                    scope.launch {
                        container.chat.setModeratorRooms(user.id, selected.toList(), allRooms)
                            .onSuccess {
                                saving = false
                                onResult(true)
                            }
                            .onFailure {
                                saving = false
                                failed = true
                            }
                    }
                },
            )
        },
        dismissButton = {
            com.addonstation.mobile.ui.components.GhostButton(
                text = cancelLabel,
                onClick = onDismiss,
            )
        },
    )
    if (saving) {
        // منع الضغط المتكرر أثناء الحفظ (لا نُغلق النافذة قبل ردّ الخادم)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(androidx.compose.ui.graphics.Color(0x66000000)),
            contentAlignment = Alignment.Center,
        ) {
            com.addonstation.mobile.ui.components.LoadingBox()
        }
    }
}

@Composable
private fun AdminUserRow(
    user: Profile,
    onGiveAc: (String) -> Unit,
    onSave: (Profile, String?, String?, Int?) -> Unit,
    onManageRooms: (Profile) -> Unit = {},
) {
    val container = rememberContainer()
    val palette = LocalHomePalette.current
    var plan by remember(user.id) { mutableStateOf(user.plan) }
    var role by remember(user.id) { mutableStateOf(user.role) }
    var expanded by remember(user.id) { mutableStateOf(false) }
    val shape = RoundedCornerShape(16.dp)

    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AvatarImage(url = assetUrl(container, user.avatarUrl), size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(
                    user.displayName?.takeIf(String::isNotBlank) ?: user.username ?: user.id.take(8),
                    style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    user.planExpiresAt?.takeIf(String::isNotBlank) ?: "${user.effectivePlan} · ${user.role}",
                    style = TextStyle(fontSize = 12.sp),
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AdminRoleBadge(user.role, user.effectivePlan, palette)
            AdminPrimaryButton(
                text = stringResource(R.string.admin_give_plain),
                onClick = { onGiveAc(user.id) },
                palette = palette,
                modifier = Modifier.widthIn(min = 50.dp),
                height = 36.dp,
            )
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(palette.surfaceHigh).clickable { expanded = !expanded },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.common_edit), tint = palette.blueLink, modifier = Modifier.size(17.dp))
            }
        }
        if (expanded) {
            Text(stringResource(R.string.admin_required_plan), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = palette.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("free", "epic", "legendary").forEach { level ->
                    val label = when (level) {
                        "epic" -> stringResource(R.string.plan_epic_name)
                        "legendary" -> stringResource(R.string.plan_legendary_name)
                        else -> stringResource(R.string.plan_free_name)
                    }
                    AdminFilterChip(label, plan == level, { plan = level }, palette, height = 36.dp)
                }
            }
            Text(stringResource(R.string.profile_roles), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = palette.textMuted)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(
                    "user" to stringResource(R.string.role_user),
                    "creator" to stringResource(R.string.role_creator_plain),
                    "moderator" to stringResource(R.string.role_moderator_plain),
                    "admin" to stringResource(R.string.role_admin),
                ).forEach { (value, label) ->
                    AdminFilterChip(label, role == value, { role = value }, palette, height = 36.dp)
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminPrimaryButton(
                    text = stringResource(R.string.admin_save),
                    onClick = { onSave(user, plan, role, null) },
                    palette = palette,
                    fill = adminOk(palette),
                    modifier = Modifier.weight(1f),
                )
                AdminOutlineButton(
                    text = stringResource(R.string.admin_plan_days_plain),
                    onClick = { onSave(user, plan, role, 30) },
                    palette = palette,
                    modifier = Modifier.weight(1f),
                )
            }
            if (role == "moderator") {
                AdminOutlineButton(
                    text = stringResource(R.string.admin_rooms_manage),
                    onClick = { onManageRooms(user) },
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@Composable
private fun ContentTab(
    content: List<ContentItem>,
    loading: Boolean,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
    onOpenContentAdmin: () -> Unit,
    onOpenBulkImport: () -> Unit,
    onEditContent: (ContentItem) -> Unit,
    onDeleteContent: (ContentItem) -> Unit,
    onRefresh: () -> Unit,
    scrollToTopRequest: Int,
) {
    val container = rememberContainer()
    val palette = LocalHomePalette.current
    var contentType by remember { mutableStateOf("addon") }
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    val filteredContent = content.filter { it.contentType == contentType }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "type-tabs") {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface).border(1.dp, palette.border, RoundedCornerShape(14.dp)).padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                listOf("addon" to stringResource(R.string.admin_addons), "world" to stringResource(R.string.admin_worlds)).forEach { (value, label) ->
                    val selected = contentType == value
                    AdminSegmentTab(
                        label = label,
                        selected = selected,
                        onClick = { contentType = value },
                        palette = palette,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item(key = "add-content") {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminPrimaryButton(
                    text = stringResource(R.string.admin_add_content_plain),
                    onClick = onOpenContentAdmin,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                )
                AdminOutlineButton(
                    text = stringResource(R.string.admin_bulk_import_nav_title),
                    onClick = onOpenBulkImport,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                )
            }
        }
        item(key = "published-heading") {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AdminSectionHeading(stringResource(R.string.admin_published_content), palette, modifier = Modifier.weight(1f))
                AdminOutlineButton(stringResource(R.string.common_retry), onRefresh, palette, height = 36.dp)
            }
        }
        when {
            loading -> item(key = "loading") { LoadingBox(text = stringResource(R.string.common_loading)) }
            failed -> item(key = "error") { AdminPanelState(stringResource(R.string.msg_generic_error), palette, onRetry = onRetry) }
            filteredContent.isEmpty() -> item(key = "empty") { AdminPanelState(stringResource(R.string.common_none), palette) }
            else -> items(filteredContent, key = { it.id }) { item ->
                val rowShape = RoundedCornerShape(16.dp)
                Row(
                    Modifier.fillMaxWidth().clip(rowShape).background(palette.surface).border(1.dp, palette.border, rowShape).clickable { onEditContent(item) }.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.addonstation.mobile.ui.components.CoverImage(
                        url = assetUrl(container, item.coverUrl),
                        radius = 10.dp,
                        modifier = Modifier.size(56.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(item.title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            stringResource(R.string.item_stats_downloads_cd, item.downloadsCount.toString()) + " · ${item.acPrice} AC",
                            style = TextStyle(fontSize = 12.sp),
                            color = palette.textMuted,
                            maxLines = 1,
                        )
                    }
                    AdminTierBadge(item.plan, palette)
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { onEditContent(item) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Edit, contentDescription = stringResource(R.string.common_edit), tint = palette.blueLink, modifier = Modifier.size(18.dp))
                    }
                    Box(Modifier.size(36.dp).clip(CircleShape).clickable { onDeleteContent(item) }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), tint = adminError(palette), modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
private fun PaymentsTab(
    orders: List<PaymentOrder>,
    loading: Boolean,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
    onApprove: (PaymentOrder) -> Unit,
    onReject: (PaymentOrder, String?) -> Unit,
    onOpenReceipt: (PaymentOrder) -> Unit,
    scrollToTopRequest: Int,
) {
    val palette = LocalHomePalette.current
    val listState = rememberLazyListState()
    var statusFilter by remember { mutableStateOf("submitted") }
    var pendingApproval by remember { mutableStateOf<PaymentOrder?>(null) }
    var pendingReject by remember { mutableStateOf<PaymentOrder?>(null) }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    val filters = listOf(
        "all" to stringResource(R.string.common_all),
        "submitted" to stringResource(R.string.plan_status_submitted),
        "pending" to stringResource(R.string.plan_status_pending),
        "approved" to stringResource(R.string.plan_status_approved),
        "rejected" to stringResource(R.string.plan_status_rejected),
        "cancelled" to stringResource(R.string.common_cancel),
    )
    val visibleOrders = if (statusFilter == "all") orders else orders.filter { it.status.equals(statusFilter, true) }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "status-filters") {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filters) { (value, label) ->
                    AdminFilterChip(label, statusFilter == value, { statusFilter = value }, palette)
                }
            }
        }
        when {
            loading -> item(key = "loading") { LoadingBox(text = stringResource(R.string.common_loading)) }
            failed -> item(key = "error") { AdminPanelState(stringResource(R.string.msg_generic_error), palette, onRetry = onRetry) }
            visibleOrders.isEmpty() -> item(key = "empty") { AdminPanelState(stringResource(R.string.common_none), palette) }
            else -> items(visibleOrders, key = { it.id }) { order ->
                AdminPaymentOrderCard(
                    order = order,
                    palette = palette,
                    onOpenReceipt = { onOpenReceipt(order) },
                    onApprove = { pendingApproval = order },
                    onReject = { pendingReject = order },
                )
            }
        }
        item(key = "bottom-space") { Spacer(Modifier.height(16.dp)) }
    }

    pendingApproval?.let { order ->
        ConfirmDialog(
            title = stringResource(R.string.admin_confirm_payment),
            text = stringResource(
                R.string.admin_confirm_payment_body,
                order.referenceCode.ifBlank { order.userId.take(8) },
                order.planId,
                order.amountLyd.toString(),
            ) + " · " + stringResource(R.string.admin_plan_days),
            confirmText = stringResource(R.string.admin_approve),
            confirmColor = adminOk(palette),
            onConfirm = { pendingApproval = null; onApprove(order) },
            onDismiss = { pendingApproval = null },
        )
    }
    pendingReject?.let { order ->
        ConfirmDialog(
            title = stringResource(R.string.admin_reject),
            text = stringResource(
                R.string.admin_confirm_payment_body,
                order.referenceCode.ifBlank { order.userId.take(8) },
                order.planId,
                order.amountLyd.toString(),
            ),
            confirmText = stringResource(R.string.admin_reject),
            confirmColor = adminError(palette),
            onConfirm = { pendingReject = null; onReject(order, null) },
            onDismiss = { pendingReject = null },
        )
    }
}

@Composable
private fun AdminPaymentOrderCard(
    order: PaymentOrder,
    palette: HomePalette,
    onOpenReceipt: () -> Unit,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    val container = rememberContainer()
    var receiptPreview by remember(order.id, order.receiptUrl) { mutableStateOf<String?>(null) }
    LaunchedEffect(order.id, order.receiptUrl) {
        receiptPreview = if (order.receiptUrl.isNullOrBlank()) null else container.plans.receiptUrl(order)
    }
    val shape = RoundedCornerShape(16.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(palette.surface).border(1.dp, palette.border, shape).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarImage(url = null, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Text(order.referenceCode.ifBlank { order.userId.take(8) }, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    stringResource(R.string.plan_order_number) + " ${order.id} · " + TimeFormat.dateTime(order.createdAt, Locale.getDefault()),
                    style = TextStyle(fontSize = 12.sp),
                    color = palette.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AdminPaymentStatusChip(order.status, palette)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AdminTierBadge(order.planId, palette)
            Text(
                "${order.amountLyd} LYD · $${order.amountUsd}",
                style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                color = palette.chipText,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier.size(width = 56.dp, height = 72.dp).clip(RoundedCornerShape(10.dp)).background(palette.surfaceHigh).clickable(enabled = order.receiptUrl != null, onClick = onOpenReceipt),
                contentAlignment = Alignment.Center,
            ) {
                if (receiptPreview != null) {
                    AsyncImage(
                        model = receiptPreview,
                        contentDescription = stringResource(R.string.admin_view_receipt),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                    )
                } else {
                    Icon(Icons.Filled.Receipt, contentDescription = stringResource(R.string.admin_view_receipt), tint = palette.blueLink, modifier = Modifier.size(24.dp))
                }
            }
        }
        order.transactionReference?.takeIf(String::isNotBlank)?.let {
            Text(it, style = TextStyle(fontSize = 12.sp), color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        AdminOutlineButton(
            text = stringResource(R.string.admin_view_receipt),
            onClick = onOpenReceipt,
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
            enabled = order.receiptUrl != null,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdminPrimaryButton(
                text = stringResource(R.string.admin_approve),
                onClick = onApprove,
                palette = palette,
                fill = adminOk(palette),
                modifier = Modifier.weight(1f),
            )
            AdminOutlineButton(
                text = stringResource(R.string.admin_reject),
                onClick = onReject,
                palette = palette,
                borderColor = adminError(palette).copy(alpha = 0.65f),
                tint = adminErrorText(palette),
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun AdminPaymentStatusChip(status: String, palette: HomePalette) {
    val (labelRes, tint) = when (status.lowercase()) {
        "approved" -> R.string.plan_status_approved to adminOkText(palette)
        "rejected" -> R.string.plan_status_rejected to adminErrorText(palette)
        "pending" -> R.string.plan_status_pending to adminWarning(palette)
        "cancelled" -> R.string.common_cancel to palette.textMuted
        else -> R.string.plan_status_submitted to palette.blueLink
    }
    Text(
        stringResource(labelRes),
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold),
        color = tint,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.16f)).padding(horizontal = 10.dp, vertical = 4.dp),
        maxLines = 1,
    )
}

@Composable
private fun ModerationTab(
    reports: List<ChatReport>,
    moderators: List<Profile>,
    loading: Boolean,
    failed: Boolean = false,
    onRetry: () -> Unit = {},
    onHideMessage: (ChatReport) -> Unit,
    onMuteAuthor: (ChatReport, Int) -> Unit,
    onBanUser: (ChatReport) -> Unit,
    onClose: (ChatReport) -> Unit,
    onOpenRooms: () -> Unit,
    onManageModerator: (Profile) -> Unit,
    scrollToTopRequest: Int,
) {
    val container = rememberContainer()
    val palette = LocalHomePalette.current
    val scrollState = rememberScrollState()
    var segment by remember { mutableIntStateOf(0) }
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) scrollState.animateScrollTo(0)
    }
    val segmentLabels = listOf(
        stringResource(R.string.admin_chat_reports_plain),
        stringResource(R.string.admin_rooms_all),
        stringResource(R.string.grp_moderators),
    )
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(scrollState).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface).border(1.dp, palette.border, RoundedCornerShape(14.dp)).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            segmentLabels.forEachIndexed { index, label ->
                val selected = segment == index
                Box(
                    Modifier.weight(1f).semantics { role = Role.Tab; this.selected = selected }.height(40.dp).clip(RoundedCornerShape(10.dp))
                        .then(if (selected) Modifier.background(Brush.linearGradient(listOf(palette.blue, palette.accentBlue))) else Modifier)
                        .clickable { segment = index },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = if (selected) Color.White else palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        when (segment) {
            0 -> when {
                loading -> LoadingBox(text = stringResource(R.string.common_loading))
                failed -> AdminPanelState(stringResource(R.string.msg_generic_error), palette, onRetry = onRetry)
                reports.isEmpty() -> AdminPanelState(stringResource(R.string.admin_open_reports_none_plain), palette)
                else -> reports.forEach { report ->
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.fillMaxWidth().clip(shape).background(palette.surface).border(1.dp, palette.border, shape).padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(adminError(palette).copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Flag, contentDescription = null, tint = adminErrorText(palette), modifier = Modifier.size(22.dp))
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.chat_report) + " · " + reportReasonLabel(report.reason), style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${report.reporterId.take(8)} → ${report.targetUserId?.take(8) ?: "—"} · ${TimeFormat.dateTime(report.createdAt, Locale.getDefault())}",
                                    style = TextStyle(fontSize = 12.sp),
                                    color = palette.textMuted,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        report.note?.takeIf(String::isNotBlank)?.let { note ->
                            Text(note, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp), color = palette.chipText, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(palette.surfaceHigh).padding(10.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AdminPrimaryButton(stringResource(R.string.chat_hide_message), { onHideMessage(report) }, palette, modifier = Modifier.weight(1f), height = 44.dp, enabled = report.messageId != null, fill = adminWarning(palette))
                            AdminOutlineButton(stringResource(R.string.chat_mute_user), { onMuteAuthor(report, 60) }, palette, modifier = Modifier.weight(1f), height = 44.dp)
                        }
                        AdminOutlineButton(
                            text = stringResource(R.string.admin_ban_user),
                            onClick = { onBanUser(report) },
                            palette = palette,
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                            tint = adminErrorText(palette),
                            borderColor = adminError(palette).copy(alpha = 0.55f),
                        )
                        AdminOutlineButton(
                            text = stringResource(R.string.admin_close_report),
                            onClick = { onClose(report) },
                            palette = palette,
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                            tint = palette.blueLink,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }
            }
            1 -> {
                AdminToolCard(
                    icon = Icons.Filled.Chat,
                    title = stringResource(R.string.admin_rooms_all),
                    subtitle = stringResource(R.string.admin_room_category),
                    accent = palette.blueLink,
                    onClick = onOpenRooms,
                    palette = palette,
                )
                Spacer(Modifier.height(10.dp))
                AdminOutlineButton(stringResource(R.string.admin_add_room), onOpenRooms, palette, modifier = Modifier.fillMaxWidth())
            }
            else -> {
                AdminSectionHeading(stringResource(R.string.admin_rooms_choose), palette)
                Text(stringResource(R.string.admin_rooms_hint), style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = palette.textMuted)
                Spacer(Modifier.height(10.dp))
                when {
                    loading -> LoadingBox(text = stringResource(R.string.common_loading))
                    failed -> AdminPanelState(stringResource(R.string.msg_generic_error), palette, onRetry = onRetry)
                    moderators.isEmpty() -> AdminPanelState(stringResource(R.string.common_none), palette)
                    else -> moderators.forEach { moderator ->
                        val rowShape = RoundedCornerShape(16.dp)
                        Row(
                            Modifier.fillMaxWidth().clip(rowShape).background(palette.surface).border(1.dp, palette.border, rowShape).padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(9.dp),
                        ) {
                            AvatarImage(url = assetUrl(container, moderator.avatarUrl), size = 44.dp)
                            Column(Modifier.weight(1f)) {
                                Text(
                                    moderator.displayName?.takeIf(String::isNotBlank) ?: moderator.username ?: moderator.id.take(8),
                                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.ExtraBold),
                                    color = palette.text,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                AdminRoleBadge(moderator.role, moderator.effectivePlan, palette)
                            }
                            AdminOutlineButton(
                                text = stringResource(R.string.admin_rooms_manage),
                                onClick = { onManageModerator(moderator) },
                                palette = palette,
                                height = 44.dp,
                                tint = palette.blueLink,
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun reportReasonLabel(reason: String): String = when (reason.lowercase()) {
    "insult" -> stringResource(R.string.chat_reason_insult)
    "spam" -> stringResource(R.string.chat_reason_spam)
    "inappropriate" -> stringResource(R.string.chat_reason_inappropriate)
    else -> stringResource(R.string.chat_reason_other)
}

@Composable
private fun MoreTab(
    onOpen: (String) -> Unit,
    onLockNow: () -> Unit,
    scrollToTopRequest: Int,
    palette: HomePalette,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) scrollState.animateScrollTo(0)
    }
    val tools = listOf(
        AdminMoreTool(Icons.Filled.Image, R.string.admin_assets_plain, R.string.admin_upload, palette.blueLink, "assets"),
        AdminMoreTool(Icons.Filled.Person, R.string.admin_creator_requests_plain, R.string.admin_creator_requests_plain, palette.epic, "creators"),
        AdminMoreTool(Icons.Filled.Settings, R.string.admin_plans_and_payment_plain, R.string.admin_plans_and_payment_plain, palette.goldText, "settings"),
        AdminMoreTool(Icons.Filled.MonetizationOn, R.string.admin_ac_tools, R.string.admin_give_ac_history, adminOkText(palette), "ac"),
        AdminMoreTool(Icons.Filled.Receipt, R.string.admin_tips_title, R.string.admin_tips_title, palette.goldText, "tips"),
        AdminMoreTool(Icons.Filled.Chat, R.string.admin_chat_plain, R.string.admin_chat_reports_plain, palette.blueLink, "chat"),
        AdminMoreTool(Icons.Filled.History, R.string.admin_audit_log, R.string.admin_audit_log, palette.textMuted, "audit"),
    )
    Column(Modifier.fillMaxSize().imePadding().verticalScroll(scrollState).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        tools.forEach { tool ->
            AdminToolCard(
                icon = tool.icon,
                title = stringResource(tool.title),
                subtitle = stringResource(tool.subtitle),
                accent = tool.accent,
                onClick = { onOpen(tool.key) },
                palette = palette,
            )
            Spacer(Modifier.height(8.dp))
        }
        Spacer(Modifier.height(8.dp))
        AdminOutlineButton(
            text = stringResource(R.string.admin_lock_now),
            onClick = onLockNow,
            palette = palette,
            modifier = Modifier.fillMaxWidth(),
            tint = adminErrorText(palette),
            borderColor = adminError(palette).copy(alpha = 0.55f),
        )
        Spacer(Modifier.height(24.dp))
    }
}

private data class AdminMoreTool(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val title: Int,
    val subtitle: Int,
    val accent: Color,
    val key: String,
)

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
    var messageIsError by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(true) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            messageIsError = false
            message = context.getString(R.string.admin_asset_reading)
            // تصغير + إعادة ترميز: يمنع رفض المخزن للصور الكبيرة أو بصيغة HEIC
            val prepared = com.addonstation.mobile.util.prepareImageForUpload(context, uri)
            val bytes = prepared?.bytes
                ?: runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) {
                messageIsError = true
                message = context.getString(R.string.admin_asset_read_failed)
                return@launch
            }
            val mime = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/png"
            val fileName = prepared?.fileName
                ?: uri.lastPathSegment?.substringAfterLast('/') ?: "asset.png"
            message = context.getString(R.string.admin_asset_uploading, bytes.size / 1024)
            container.assets.create(
                assetType = type,
                name = name.ifBlank { fileName },
                requiredPlan = plan,
                bytes = bytes,
                mime = mime,
                fileName = fileName,
                packId = packId.takeIf { it.isNotBlank() },
            ).onSuccess {
                messageIsError = false
                message = context.getString(R.string.admin_asset_uploaded, fileName, bytes.size / 1024)
                assets = container.assets.all()
            }.onFailure {
                messageIsError = true
                message = context.getString(R.string.admin_asset_upload_failed, it.message ?: it::class.simpleName.orEmpty())
            }
        }
    }

    // تصغير كل صور الأصول المخزّنة: يجعل شبكة الصور خفيفة فتظهر فوراً
    var shrinking by remember { mutableStateOf(false) }
    var confirmShrink by remember { mutableStateOf(false) }
    var pendingDeleteAsset by remember { mutableStateOf<SiteAsset?>(null) }

    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        failed = false
        try {
            assets = container.assets.all()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_assets),
            title = stringResource(R.string.admin_assets_plain),
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = if (shrinking) stringResource(R.string.common_loading) else stringResource(R.string.admin_shrink_assets),
                    onClick = { if (!shrinking) confirmShrink = true },
                    enabled = !shrinking,
                )
                Text(
                    stringResource(R.string.admin_shrink_assets_hint),
                    style = AppText.tiny,
                    color = AppColors.Muted,
                )
            }
            val assetTypeFilters = listOf(
                "avatar" to stringResource(R.string.admin_tab_avatars),
                "sticker" to stringResource(R.string.chat_stickers_tab),
                "banner" to stringResource(R.string.profile_banner_section),
                "item" to stringResource(R.string.admin_tab_submissions),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(assetTypeFilters) { (assetType, label) ->
                    AdminFilterChip(label, type == assetType, { type = assetType }, height = 36.dp)
                }
            }
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("free", "epic", "legendary", "admin")) { value ->
                    AdminFilterChip(value, plan == value, { plan = value }, height = 36.dp)
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                label = { Text(stringResource(R.string.auth_username)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            if (type == "sticker") {
                OutlinedTextField(
                    value = packId,
                    onValueChange = { packId = it },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next),
                    label = { Text(stringResource(R.string.admin_pack_id_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(8.dp))
            val uploadShape = RoundedCornerShape(12.dp)
            Row(
                Modifier.fillMaxWidth().height(48.dp).clip(uploadShape)
                    .background(LocalHomePalette.current.surface)
                    .specDashedBorder(LocalHomePalette.current.blue, 12.dp, dash = 7f, gap = 5f)
                    .clickable { picker.launch("image/*") }
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Filled.Image, contentDescription = null, tint = LocalHomePalette.current.blue, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.admin_upload), style = AppText.button, color = LocalHomePalette.current.blue)
            }
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it,
                    style = AppText.tiny,
                    color = if (messageIsError) AppColors.Red else AppColors.Green,
                )
            }
            SectionHeader(title = stringResource(R.string.admin_assets_plain))
            if (loading) {
                LoadingBox()
            } else if (failed) {
                com.addonstation.mobile.ui.components.ErrorState(onRetry = { reloadTick += 1 })
            } else if (assets.isEmpty()) {
                EmptyState(stringResource(R.string.common_none))
            } else {
                val assetRows = assets.take(60).chunked(4)
                assetRows.forEach { rowAssets ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        rowAssets.forEach { asset ->
                            var missing by remember(asset.id) { mutableStateOf(false) }
                            val palette = LocalHomePalette.current
                            val shape = RoundedCornerShape(14.dp)
                            Column(
                                Modifier
                                    .weight(1f)
                                    .clip(shape)
                                    .background(palette.surface)
                                    .border(1.dp, palette.border, shape)
                                    .padding(8.dp),
                                verticalArrangement = Arrangement.spacedBy(5.dp),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(10.dp)).background(palette.surfaceHigh),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    AsyncImage(
                                        model = assetUrl(container, asset.fileUrl),
                                        contentDescription = null,
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)),
                                        onError = { missing = true },
                                        onSuccess = { missing = false },
                                    )
                                    if (missing) {
                                        Text("✕", style = AppText.body, color = adminErrorText(palette))
                                    }
                                }
                                Text(asset.name, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        if (missing) asset.fileUrl else "${asset.assetType} · ${asset.requiredPlan}",
                                        style = TextStyle(fontSize = 10.sp),
                                        color = if (missing) adminErrorText(palette) else palette.textMuted,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Box(
                                        Modifier.size(44.dp).clip(CircleShape).background(adminError(palette).copy(alpha = 0.13f)).clickable {
                                            pendingDeleteAsset = asset
                                        },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.common_delete), tint = adminErrorText(palette), modifier = Modifier.size(17.dp))
                                    }
                                }
                            }
                        }
                        repeat(4 - rowAssets.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    if (confirmShrink) {
        ConfirmDialog(
            title = stringResource(R.string.admin_shrink_assets),
            text = stringResource(R.string.admin_shrink_assets_hint),
            confirmText = stringResource(R.string.common_yes),
            confirmColor = LocalHomePalette.current.blue,
            onConfirm = {
                confirmShrink = false
                scope.launch {
                    shrinking = true
                    messageIsError = false
                    message = context.getString(R.string.admin_asset_resize_running)
                    try {
                        container.assets.shrinkStoredAssets()
                            .onSuccess { count ->
                                message = context.getString(R.string.admin_asset_resize_done, count)
                            }
                            .onFailure { error ->
                                messageIsError = true
                                message = context.getString(R.string.admin_asset_resize_failed, error.message ?: error::class.simpleName.orEmpty())
                            }
                    } finally {
                        shrinking = false
                    }
                }
            },
            onDismiss = { confirmShrink = false },
        )
    }
    pendingDeleteAsset?.let { asset ->
        ConfirmDialog(
            title = stringResource(R.string.admin_delete_asset),
            text = asset.name,
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                pendingDeleteAsset = null
                scope.launch {
                    container.assets.delete(asset.id)
                        .onSuccess {
                            messageIsError = false
                            message = null
                            assets = container.assets.all()
                        }
                        .onFailure { error ->
                            messageIsError = true
                            message = error.message ?: context.getString(R.string.msg_generic_error)
                        }
                }
            },
            onDismiss = { pendingDeleteAsset = null },
        )
    }
}

/** مراجعة طلبات المبدعين + مراجعة محتوى المبدعين + تِيبس + AC + سجل الإدارة + إعدادات الخطط. */
@Composable
fun AdminCreatorReviewScreen(onBack: () -> Unit, initialTab: Int = 0) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val palette = LocalHomePalette.current
    var applications by remember { mutableStateOf<List<CreatorApplication>>(emptyList()) }
    var submissions by remember { mutableStateOf<List<CreatorSubmission>>(emptyList()) }
    var tips by remember { mutableStateOf<List<CreatorTip>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }
    var creatorProfiles by remember { mutableStateOf<Map<String, Profile>>(emptyMap()) }
    var pendingTipReview by remember { mutableStateOf<Pair<CreatorTip, Boolean>?>(null) }
    var tipReceiptUrls by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var tipReceiptPreview by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(initialTab.coerceIn(0, 2)) }

    LaunchedEffect(tab) {
        applications = container.admin.creatorApplications()
        submissions = container.admin.submissions()
        tips = container.admin.tips()
    }
    LaunchedEffect(applications, submissions, tips) {
        val profileIds = applications.map { it.userId } + submissions.map { it.creatorId } +
            tips.flatMap { listOf(it.senderId, it.creatorId) }
        creatorProfiles = runCatching {
            container.profile.loadProfiles(profileIds.filter(String::isNotBlank).distinct())
        }.getOrDefault(emptyMap())
    }

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_dashboard),
            title = stringResource(when (tab) {
                1 -> R.string.admin_tab_submissions
                2 -> R.string.admin_tips_title
                else -> R.string.admin_creator_requests_plain
            }),
            onBack = onBack,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            AdminSegmentTab(stringResource(R.string.admin_tab_requests), tab == 0, { tab = 0 }, palette, modifier = Modifier.weight(1f), fontSize = 12.sp)
            AdminSegmentTab(stringResource(R.string.admin_tab_submissions), tab == 1, { tab = 1 }, palette, modifier = Modifier.weight(1f), fontSize = 12.sp)
            AdminSegmentTab(stringResource(R.string.admin_tips_title), tab == 2, { tab = 2 }, palette, modifier = Modifier.weight(1f), fontSize = 12.sp)
        }
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.tiny, color = if (messageIsError) AppColors.Red else AppColors.Green)
            }
            when (tab) {
                0 -> {
                    if (applications.isEmpty()) EmptyState(stringResource(R.string.common_none))
                    applications.forEach { application ->
                        val applicant = creatorProfiles[application.userId]
                        val applicantName = applicant?.displayName?.takeIf(String::isNotBlank)
                            ?: applicant?.username?.takeIf(String::isNotBlank)
                            ?: application.userId
                        val statusRes = when (application.status.lowercase()) {
                            "approved" -> R.string.creator_status_approved
                            "rejected" -> R.string.creator_status_rejected
                            "more_info" -> R.string.creator_status_more_info
                            else -> R.string.creator_status_pending
                        }
                        val typeKeys = application.contentTypes.orEmpty().split(",").map(String::trim).toSet()
                        val typeLabels = listOfNotNull(
                            if ("addon" in typeKeys) stringResource(R.string.creator_type_addons) else null,
                            if ("world" in typeKeys) stringResource(R.string.creator_type_worlds) else null,
                            if ("map" in typeKeys) stringResource(R.string.creator_type_maps) else null,
                        ).joinToString(" · ").ifBlank { stringResource(R.string.common_none) }
                        val pending = application.status.equals("pending", ignoreCase = true)
                        GlassCard(padding = 10.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AvatarImage(url = applicant?.avatarUrl?.let { assetUrl(container, it) }, size = 44.dp)
                                Column(Modifier.weight(1f)) {
                                    Text(applicantName, style = AppText.sectionTitle, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("#${application.id} · ${stringResource(statusRes)}", style = AppText.tiny, color = AppColors.Blue)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("${stringResource(R.string.creator_field_country)}: ${application.country.orEmpty().ifBlank { "—" }}", style = AppText.tiny, color = AppColors.Muted)
                            Text("${stringResource(R.string.creator_field_experience)}: ${application.experience.orEmpty().ifBlank { "—" }}", style = AppText.tiny, color = AppColors.Muted)
                            Text("${stringResource(R.string.creator_field_content_types)}: $typeLabels", style = AppText.tiny, color = AppColors.Muted)
                            val motivation = application.motivation?.takeIf(String::isNotBlank)
                                ?: application.about?.takeIf(String::isNotBlank)
                                ?: application.bio?.takeIf(String::isNotBlank)
                            motivation?.let {
                                Text(stringResource(R.string.creator_field_motivation), style = AppText.tiny, color = AppColors.Dim, modifier = Modifier.padding(top = 6.dp))
                                Text(it, style = AppText.bodySmall, color = AppColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            }
                            application.previousWorkUrl?.takeIf(String::isNotBlank)?.let {
                                Text("${stringResource(R.string.creator_field_previous_work)}: $it", style = AppText.tiny, color = AppColors.Dim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                GradientButton(
                                    text = stringResource(R.string.admin_approve),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewApplication(application.id, "approve", null)
                                                .onSuccess {
                                                    messageIsError = false
                                                    message = context.getString(R.string.admin_creator_approved)
                                                }
                                                .onFailure {
                                                    messageIsError = true
                                                    message = context.getString(R.string.admin_creator_review_failed, context.getString(R.string.msg_generic_error))
                                                }
                                            applications = container.admin.creatorApplications()
                                        }
                                    },
                                    enabled = pending,
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.admin_creator_more_info),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewApplication(application.id, "more_info", context.getString(R.string.admin_creator_more_info_note))
                                                .onSuccess {
                                                    messageIsError = false
                                                    message = context.getString(R.string.admin_creator_more_info_sent)
                                                }
                                                .onFailure {
                                                    messageIsError = true
                                                    message = context.getString(R.string.admin_creator_review_failed, context.getString(R.string.msg_generic_error))
                                                }
                                            applications = container.admin.creatorApplications()
                                        }
                                    },
                                    enabled = pending,
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.admin_reject),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewApplication(application.id, "reject", context.getString(R.string.admin_creator_reject_reason))
                                                .onSuccess {
                                                    messageIsError = false
                                                    message = context.getString(R.string.admin_creator_rejected)
                                                }
                                                .onFailure {
                                                    messageIsError = true
                                                    message = context.getString(R.string.admin_creator_review_failed, context.getString(R.string.msg_generic_error))
                                                }
                                            applications = container.admin.creatorApplications()
                                        }
                                    },
                                    enabled = pending,
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
                1 -> {
                    if (submissions.isEmpty()) EmptyState(stringResource(R.string.common_none))
                    submissions.forEach { submission ->
                        val creator = creatorProfiles[submission.creatorId]
                        val contentTypeRes = when (submission.contentType.lowercase()) {
                            "world" -> R.string.creator_type_worlds
                            "map" -> R.string.creator_type_maps
                            else -> R.string.creator_type_addons
                        }
                        val planRes = when (submission.plan.lowercase()) {
                            "epic" -> R.string.plan_epic_name
                            "legendary" -> R.string.plan_legendary_name
                            else -> R.string.plan_free_name
                        }
                        val statusRes = when (submission.status.lowercase()) {
                            "approved" -> R.string.creator_status_approved
                            "rejected" -> R.string.creator_status_rejected
                            else -> R.string.creator_status_pending
                        }
                        val pending = submission.status.equals("pending", ignoreCase = true)
                        GlassCard(padding = 10.dp) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                com.addonstation.mobile.ui.components.CoverImage(
                                    url = assetUrl(container, submission.coverUrl),
                                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp)),
                                    radius = 10.dp,
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(submission.title, style = AppText.sectionTitle, color = AppColors.Text, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                    Text(creator?.displayName ?: creator?.username.orEmpty(), style = AppText.tiny, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                            Text(
                                "${stringResource(contentTypeRes)} · ${stringResource(planRes)} · ${stringResource(statusRes)}",
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                            submission.createdAt?.let { Text(TimeFormat.dateTime(it, java.util.Locale.getDefault()), style = AppText.tiny, color = AppColors.Dim) }
                            submission.description?.let {
                                Text(it, style = AppText.bodySmall, color = AppColors.Muted, maxLines = 3)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GradientButton(
                                    text = stringResource(R.string.admin_approve),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewSubmission(submission.id, "approve", null)
                                                .onSuccess {
                                                    messageIsError = false
                                                    message = context.getString(R.string.admin_submission_approved)
                                                }
                                                .onFailure {
                                                    messageIsError = true
                                                    message = context.getString(R.string.admin_submission_review_failed, context.getString(R.string.msg_generic_error))
                                                }
                                            submissions = container.admin.submissions()
                                        }
                                    },
                                    enabled = pending,
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.admin_reject),
                                    onClick = {
                                        scope.launch {
                                            container.admin.reviewSubmission(submission.id, "reject", context.getString(R.string.admin_submission_reject_reason))
                                                .onSuccess {
                                                    messageIsError = false
                                                    message = context.getString(R.string.admin_submission_rejected)
                                                }
                                                .onFailure {
                                                    messageIsError = true
                                                    message = context.getString(R.string.admin_submission_review_failed, context.getString(R.string.msg_generic_error))
                                                }
                                            submissions = container.admin.submissions()
                                        }
                                    },
                                    enabled = pending,
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
                        val senderName = creatorProfiles[tip.senderId]?.displayName?.takeIf(String::isNotBlank)
                            ?: creatorProfiles[tip.senderId]?.username?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.common_guest)
                        val creatorName = creatorProfiles[tip.creatorId]?.displayName?.takeIf(String::isNotBlank)
                            ?: creatorProfiles[tip.creatorId]?.username?.takeIf(String::isNotBlank)
                            ?: stringResource(R.string.common_guest)
                        val tipStatusRes = when (tip.status.lowercase()) {
                            "approved" -> R.string.admin_tip_status_approved
                            "rejected" -> R.string.admin_tip_status_rejected
                            else -> R.string.admin_tip_status_pending
                        }
                        val tipIsPending = tip.status.equals("pending", ignoreCase = true)
                        val tipAmount = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).format(tip.amount)
                        val receiptThumb = tipReceiptUrls[tip.id]
                            ?: tip.receiptUrl?.takeIf { it.startsWith("http", ignoreCase = true) }
                        GlassCard(padding = 10.dp) {
                            Text(
                                stringResource(R.string.admin_tip_from_to, senderName, creatorName),
                                style = AppText.sectionTitle,
                                color = AppColors.Text,
                            )
                            Text(
                                "$tipAmount LYD · ${tip.paymentMethod} · ${stringResource(tipStatusRes)}",
                                style = AppText.bodySmall,
                                color = AppColors.Gold,
                            )
                            tip.createdAt?.let {
                                Text(TimeFormat.dateTime(it, java.util.Locale.getDefault()), style = AppText.tiny, color = AppColors.Dim)
                            }
                            tip.message?.let { Text(it, style = AppText.bodySmall, color = AppColors.Muted) }
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Box(
                                    Modifier.size(48.dp).clip(RoundedCornerShape(10.dp))
                                        .background(LocalHomePalette.current.surface)
                                        .border(1.dp, LocalHomePalette.current.border, RoundedCornerShape(10.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    if (receiptThumb != null) {
                                        AsyncImage(
                                            model = receiptThumb,
                                            contentDescription = stringResource(R.string.admin_view_receipt),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp))
                                                .clickable { tipReceiptPreview = receiptThumb },
                                        )
                                    } else {
                                        Icon(Icons.Filled.Receipt, contentDescription = null, tint = AppColors.Gold)
                                    }
                                }
                                Column(Modifier.weight(1f)) {
                                    Text(tip.transactionReference ?: "—", style = AppText.tiny, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    GhostButton(
                                        text = stringResource(R.string.admin_view_receipt),
                                        onClick = {
                                            val path = tip.receiptUrl
                                            if (path.isNullOrBlank()) {
                                                messageIsError = true
                                                message = context.getString(R.string.admin_tip_receipt_missing)
                                            } else {
                                                scope.launch {
                                                    val url = if (path.startsWith("http", ignoreCase = true)) path
                                                        else container.creators.tipReceiptUrl(path)
                                                    if (url.isNullOrBlank()) {
                                                        messageIsError = true
                                                        message = context.getString(R.string.admin_tip_receipt_missing)
                                                    } else {
                                                        tipReceiptUrls = tipReceiptUrls + (tip.id to url)
                                                        tipReceiptPreview = url
                                                    }
                                                }
                                            }
                                        },
                                    )
                                }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                GradientButton(
                                    text = stringResource(R.string.admin_approve),
                                    onClick = { pendingTipReview = tip to true },
                                    enabled = tipIsPending,
                                    modifier = Modifier.weight(1f),
                                )
                                GhostButton(
                                    text = stringResource(R.string.admin_reject),
                                    onClick = { pendingTipReview = tip to false },
                                    enabled = tipIsPending,
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
    pendingTipReview?.let { (tip, approve) ->
        val senderName = creatorProfiles[tip.senderId]?.displayName?.takeIf(String::isNotBlank)
            ?: creatorProfiles[tip.senderId]?.username?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.common_guest)
        val creatorName = creatorProfiles[tip.creatorId]?.displayName?.takeIf(String::isNotBlank)
            ?: creatorProfiles[tip.creatorId]?.username?.takeIf(String::isNotBlank)
            ?: context.getString(R.string.common_guest)
        val amount = java.text.NumberFormat.getNumberInstance(java.util.Locale.getDefault()).format(tip.amount)
        ConfirmDialog(
            title = context.getString(if (approve) R.string.admin_approve else R.string.admin_reject),
            text = context.getString(
                if (approve) R.string.admin_tip_approve_confirm else R.string.admin_tip_reject_confirm,
                amount,
                senderName,
                creatorName,
            ),
            confirmText = context.getString(if (approve) R.string.admin_approve else R.string.admin_reject),
            confirmColor = if (approve) AppColors.Green else AppColors.Red,
            onDismiss = { pendingTipReview = null },
            onConfirm = {
                pendingTipReview = null
                scope.launch {
                    val result = container.admin.reviewTip(
                        tip.id,
                        approve,
                        if (approve) null else context.getString(R.string.admin_tip_reject_reason),
                    )
                    result.onSuccess {
                        messageIsError = false
                        message = context.getString(if (approve) R.string.admin_tip_approved else R.string.admin_tip_rejected)
                        tips = container.admin.tips()
                    }.onFailure {
                        messageIsError = true
                        message = context.getString(R.string.admin_tip_review_failed, context.getString(R.string.msg_generic_error))
                    }
                }
            },
        )
    }
    tipReceiptPreview?.let { url ->
        com.addonstation.mobile.ui.common.FullScreenImageViewer(url = url) { tipReceiptPreview = null }
    }
}

/** لوحة AC: منح أي مبلغ لأي مستخدم + أكواد الهدايا + سجل العمليات. */
@Composable
fun AdminAcScreen(onBack: () -> Unit, preselectUserId: String? = null) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var users by remember { mutableStateOf<List<Profile>>(emptyList()) }
    var names by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var amount by remember { mutableStateOf("100") }
    var note by remember { mutableStateOf("") }
    // مستخدم قادم من زر «+AC» في قائمة المستخدمين → يبقى محدَّداً عند فتح الشاشة
    var selectedId by remember { mutableStateOf<String?>(preselectUserId) }
    var pending by remember { mutableStateOf<Pair<String, Int>?>(null) }
    var pendingAll by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var transactions by remember { mutableStateOf<List<Map<String, kotlinx.serialization.json.JsonElement>>>(emptyList()) }
    var result by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var allResult by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var badAmount by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var errKind by remember { mutableStateOf<com.addonstation.mobile.data.ApiErrorKind?>(null) }
    var codeAmountText by remember { mutableStateOf("30000") }
    var codeCountText by remember { mutableStateOf("10") }
    var generated by remember { mutableStateOf<List<String>>(emptyList()) }
    var existingCodes by remember { mutableStateOf<List<Map<String, kotlinx.serialization.json.JsonElement>>>(emptyList()) }
    var generating by remember { mutableStateOf(false) }
    val codeAmount = codeAmountText.toIntOrNull() ?: 0
    val codeCount = codeCountText.toIntOrNull() ?: 0
    val badAmountText = stringResource(R.string.admin_codes_bad_amount)

    // نصوص تُحسب مرة واحدة (لا تُستدعى داخل الـ coroutine)
    val rewardReason = stringResource(R.string.admin_reason_reward)
    val adjustReason = stringResource(R.string.admin_reason_adjust)

    fun label(p: Profile): String = p.username?.takeIf { it.isNotBlank() }
        ?: p.displayName?.takeIf { it.isNotBlank() }
        ?: p.id.take(8)

    val amountValue = amount.toIntOrNull() ?: 0
    val fromList = users.firstOrNull { it.id == selectedId }
    val targetId = fromList?.id ?: selectedId
    val targetLabel = fromList?.let { label(it) } ?: names[selectedId] ?: selectedId?.take(8) ?: ""

    LaunchedEffect(query) {
        users = container.admin.users(query, null, 20)
        transactions = container.admin.acTransactions(50)
        existingCodes = container.admin.acCodes(50)
    }
    // خريطة الأسماء: لعرض اسم المستخدم في سجل العمليات (وليس رقمه فقط)
    LaunchedEffect(Unit) {
        names = container.admin.users("", null, 200).associate { it.id to label(it) }
    }

    // طلب تأكيد قبل تنفيذ أي عملية (منح أو خصم)
    fun ask(id: String, value: Int) {
        if (value == 0) {
            badAmount = true
            return
        }
        badAmount = false
        result = null
        allResult = null
        pending = id to value
    }

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_dashboard),
            title = stringResource(R.string.ac_title),
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                label = { Text(stringResource(R.string.admin_search_users)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))

            // ------------------------------------------- منح AC لمستخدم
            SectionHeader(title = stringResource(R.string.admin_give_ac_title))
            GlassCard(padding = 10.dp) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it.filter { ch -> ch.isDigit() }.take(9) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                    label = { Text(stringResource(R.string.admin_give_ac_amount)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                // مبالغ سريعة — أو اكتب أي رقم في الحقل أعلاه
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.admin_give_ac_quick),
                        style = AppText.tiny,
                        color = AppColors.Muted,
                    )
                    listOf(100, 500, 1000, 5000).forEach { quick ->
                        GhostButton(
                            text = quick.toString(),
                            onClick = { amount = quick.toString() },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(60) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    label = { Text(stringResource(R.string.admin_give_ac_note)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (targetId != null) {
                    // سطر الملخّص: لمن سيُمنح المبلغ وكم
                    Text(
                        targetLabel + " · " + amountValue.toString() + " AC",
                        style = AppText.tiny,
                        color = AppColors.Green,
                    )
                } else {
                    Text(
                        stringResource(R.string.admin_give_ac_select),
                        style = AppText.tiny,
                        color = AppColors.Dim,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GradientButton(
                        text = stringResource(R.string.admin_give_plain),
                        onClick = { if (targetId != null) ask(targetId, amountValue) else badAmount = true },
                        enabled = !busy && targetId != null,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(
                        text = stringResource(R.string.admin_give_ac_deduct),
                        onClick = { if (targetId != null) ask(targetId, -amountValue) else badAmount = true },
                        enabled = !busy && targetId != null,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                GhostButton(
                    text = stringResource(R.string.admin_give_ac_all),
                    onClick = { if (amountValue > 0) { badAmount = false; pendingAll = true } else badAmount = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            if (badAmount) {
                Text(
                    stringResource(R.string.admin_give_ac_invalid),
                    style = AppText.tiny,
                    color = AppColors.Red,
                )
            }
            result?.let { (value, balance) ->
                Spacer(Modifier.height(6.dp))
                Text(
                    if (value >= 0) stringResource(R.string.admin_give_ac_done_plain, value, balance)
                    else stringResource(R.string.admin_give_ac_done_deduct_plain, -value, balance),
                    style = AppText.tiny,
                    color = AppColors.Green,
                )
            }
            allResult?.let { (value, count) ->
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.admin_give_ac_done_all, value, count),
                    style = AppText.tiny,
                    color = AppColors.Green,
                )
            }
            errKind?.let {
                Spacer(Modifier.height(6.dp))
                Text(com.addonstation.mobile.ui.apiErrorText(it), style = AppText.tiny, color = AppColors.Red)
            }
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = AppText.tiny, color = AppColors.Muted)
            }

            // ------------------------------------------- قائمة المستخدمين
            SectionHeader(title = stringResource(R.string.admin_users))
            if (users.isEmpty()) {
                Text(
                    stringResource(R.string.admin_give_ac_no_users),
                    style = AppText.tiny,
                    color = AppColors.Dim,
                )
            }
            users.forEach { user ->
                val selected = user.id == selectedId
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) AppColors.Elevated2 else AppColors.Elevated)
                        .border(
                            1.dp,
                            if (selected) AppColors.Green else AppColors.Border,
                            RoundedCornerShape(12.dp),
                        )
                        .clickable {
                            selectedId = if (selected) null else user.id
                            result = null
                            allResult = null
                            errKind = null
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        label(user),
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(
                        text = "+",
                        onClick = { ask(user.id, amountValue) },
                        enabled = !busy,
                    )
                    GhostButton(
                        text = "−",
                        onClick = { ask(user.id, -amountValue) },
                        enabled = !busy,
                    )
                }
            }


            // ------------------------------------------- أكواد هدايا AC
            SectionHeader(title = stringResource(R.string.admin_codes_title))
            GlassCard(padding = 12.dp) {
                Text(
                    stringResource(R.string.admin_codes_hint),
                    style = AppText.tiny,
                    color = AppColors.Muted,
                )
                Spacer(Modifier.height(8.dp))
                // فئة الكود: أزرار سريعة أو قيمة مخصّصة — الفئة تُحدَّد هنا عند التوليد فقط
                Text(stringResource(R.string.admin_codes_amount), style = AppText.tiny, color = AppColors.Muted)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5000, 15000, 30000, 100000).forEach { value ->
                        val selected = codeAmount == value
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (selected) AppColors.Green.copy(alpha = 0.22f) else AppColors.Elevated)
                                .border(
                                    1.dp,
                                    if (selected) AppColors.Green else AppColors.Border2,
                                    RoundedCornerShape(10.dp),
                                )
                                .clickable { codeAmountText = value.toString() }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "%,d".format(java.util.Locale.US, value),
                                style = AppText.tiny,
                                color = if (selected) AppColors.Green else AppColors.Text,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = codeAmountText,
                        onValueChange = { codeAmountText = it.filter { ch -> ch.isDigit() }.take(8) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        label = { Text(stringResource(R.string.admin_codes_amount)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = codeCountText,
                        onValueChange = { codeCountText = it.filter { ch -> ch.isDigit() }.take(3) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        label = { Text(stringResource(R.string.admin_codes_count)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                // تأكيد واضح قبل التوليد: عدد الأكواد × الفئة = الإجمالي
                Text(
                    stringResource(
                        R.string.admin_codes_summary,
                        codeCount.toString(),
                        "%,d".format(java.util.Locale.US, codeAmount),
                        "%,d".format(java.util.Locale.US, codeAmount * codeCount),
                    ),
                    style = AppText.tiny,
                    color = AppColors.Gold,
                )
                Spacer(Modifier.height(8.dp))
                GradientButton(
                    text = stringResource(R.string.admin_codes_generate),
                    onClick = {
                        scope.launch {
                            if (codeAmount < 100) {
                                message = badAmountText
                                return@launch
                            }
                            generating = true
                            container.admin
                                .acCreateCodes(codeAmount, codeCount.coerceAtLeast(1), 1, null)
                                .onSuccess { codes ->
                                    generated = codes
                                    message = null
                                    existingCodes = container.admin.acCodes(50)
                                }
                                .onFailure { message = it.message }
                            generating = false
                        }
                    },
                    enabled = !generating && codeAmount >= 100 && codeCount >= 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (generated.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        stringResource(R.string.admin_codes_created, generated.size.toString()),
                        style = AppText.tiny,
                        color = AppColors.Muted,
                    )
                    Spacer(Modifier.height(4.dp))
                    SelectionContainer {
                        Text(
                            generated.joinToString("\n"),
                            style = AppText.bodySmall,
                            color = AppColors.Gold,
                        )
                    }
                }
            }
            if (existingCodes.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.admin_codes_latest))
                existingCodes.take(12).forEach { row ->
                    val codeAmount = (row["amount"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    val usedCount = (row["used_count"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                    val maxUses = (row["max_uses"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 1
                    val expiresAt = (row["expires_at"] as? JsonPrimitive)?.content
                    val disabled = (row["active"] as? JsonPrimitive)?.content == "false"
                    val expired = com.addonstation.mobile.data.model.parseIsoMillis(expiresAt)
                        ?.let { it < System.currentTimeMillis() } == true
                    val used = usedCount >= maxUses
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(AppColors.Elevated)
                            .padding(horizontal = 10.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (row["code"] as? JsonPrimitive)?.content ?: "",
                            style = AppText.bodySmall,
                            color = AppColors.Text,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = when {
                                expired -> stringResource(R.string.admin_code_expired)
                                used -> stringResource(R.string.admin_code_used)
                                disabled -> stringResource(R.string.ac_redeem_off)
                                else -> "+%,d AC".format(java.util.Locale.US, codeAmount)
                            },
                            style = AppText.tiny,
                            color = when {
                                expired || disabled -> AppColors.Red
                                used -> AppColors.Gold
                                else -> AppColors.Green
                            },
                        )
                    }
                }
            }

            // ------------------------------------------- آخر عمليات AC
            SectionHeader(title = stringResource(R.string.admin_give_ac_history))
            if (transactions.isEmpty()) {
                Text(
                    stringResource(R.string.admin_give_ac_none),
                    style = AppText.tiny,
                    color = AppColors.Dim,
                )
            }
            transactions.take(12).forEach { tx ->
                val value = (tx["amount"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 0
                val owner = (tx["user_id"] as? JsonPrimitive)?.content ?: ""
                val balance = (tx["balance_after"] as? JsonPrimitive)?.content?.toIntOrNull()
                val why = (tx["reason"] as? JsonPrimitive)?.content ?: ""
                val whenIso = (tx["created_at"] as? JsonPrimitive)?.content
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(AppColors.Elevated)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        names[owner] ?: owner.take(8),
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    if (balance != null) {
                        Text(
                            stringResource(R.string.admin_give_ac_balance_after) + " " + balance,
                            style = AppText.tiny,
                            color = AppColors.Muted,
                        )
                    }
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Text(
                        (if (value >= 0) "+" else "") + value.toString() + " AC",
                        style = AppText.tiny,
                        color = if (value >= 0) AppColors.Green else AppColors.Red,
                    )
                    Spacer(Modifier.padding(horizontal = 6.dp))
                    Text(
                        com.addonstation.mobile.util.TimeFormat.dateTime(whenIso, java.util.Locale.getDefault()),
                        style = AppText.tiny,
                        color = AppColors.Dim,
                        maxLines = 1,
                    )
                }
                if (why.isNotBlank()) {
                    Text(
                        why,
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

    // ------------------------------------------------------ نافذة التأكيد
    pending?.let { (id, value) ->
        val who = names[id] ?: id.take(8)
        ConfirmDialog(
            title = stringResource(R.string.admin_give_ac_title),
            text = if (value >= 0) stringResource(R.string.admin_give_ac_confirm, value, who)
            else stringResource(R.string.admin_give_ac_confirm_deduct, -value, who),
            confirmText = stringResource(R.string.common_yes),
            confirmColor = if (value >= 0) AppColors.Green else AppColors.Red,
            onConfirm = {
                pending = null
                scope.launch {
                    busy = true
                    val why = note.trim().ifBlank { null } ?: if (value >= 0) rewardReason else adjustReason
                    container.admin.acAdjust(id, value, why)
                        .onSuccess { balance ->
                            result = value to balance
                            transactions = container.admin.acTransactions(50)
                            errKind = null
                        }
                        .onFailure { errKind = it.toApiError().kind }
                    busy = false
                }
            },
            onDismiss = { pending = null },
        )
    }

    if (pendingAll) {
        ConfirmDialog(
            title = stringResource(R.string.admin_give_ac_all),
            text = stringResource(R.string.admin_give_ac_confirm_all, amountValue),
            confirmText = stringResource(R.string.common_yes),
            confirmColor = AppColors.Green,
            onConfirm = {
                pendingAll = false
                scope.launch {
                    busy = true
                    container.admin.acGrantAll(amountValue, rewardReason)
                        .onSuccess { count ->
                            allResult = amountValue to count
                            transactions = container.admin.acTransactions(50)
                            errKind = null
                        }
                        .onFailure { errKind = it.toApiError().kind }
                    busy = false
                }
            },
            onDismiss = { pendingAll = false },
        )
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

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_dashboard),
            title = stringResource(R.string.admin_plans_and_payment_plain),
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
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
                var priceUsd by remember(plan.id) { mutableStateOf(plan.priceUsd.toString()) }
                var days by remember(plan.id) { mutableStateOf(plan.durationDays.toString()) }
                var limit by remember(plan.id) { mutableStateOf(plan.downloadLimit?.toString() ?: "") }
                GlassCard(padding = 10.dp) {
                    Text(plan.name, style = AppText.sectionTitle, color = AppColors.Text)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = price,
                            onValueChange = { price = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            label = { Text(stringResource(R.string.admin_price_lyd_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = priceUsd,
                            onValueChange = { priceUsd = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                            label = { Text(stringResource(R.string.admin_price_usd_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = days,
                            onValueChange = { days = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            label = { Text(stringResource(R.string.admin_duration_days_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = limit,
                            onValueChange = { limit = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            label = { Text(stringResource(R.string.admin_download_limit_label)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    GradientButton(
                        text = stringResource(R.string.admin_save),
                        onClick = {
                            scope.launch {
                                container.admin.updatePlan(
                                    planId = plan.id,
                                    priceLyd = price.toDoubleOrNull(),
                                    priceUsd = priceUsd.toDoubleOrNull(),
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
                OutlinedTextField(methodCode, { methodCode = it }, label = { Text(stringResource(R.string.admin_method_code_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(methodName, { methodName = it }, label = { Text(stringResource(R.string.admin_method_name_label)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(accountName, { accountName = it }, label = { Text(stringResource(R.string.plan_beneficiary)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(accountId, { accountId = it }, label = { Text(stringResource(R.string.plan_account_number)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(instructions, { instructions = it }, label = { Text(stringResource(R.string.plan_order_instructions)) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next), modifier = Modifier.fillMaxWidth())
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

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_dashboard),
            title = stringResource(R.string.admin_audit_log),
            onBack = onBack,
        )
        if (loading) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                LoadingBox(text = stringResource(R.string.common_loading))
            }
        } else if (rows.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                EmptyState(stringResource(R.string.common_none))
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
            ) {
                items(rows) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(LocalHomePalette.current.surface)
                            .border(1.dp, LocalHomePalette.current.border, RoundedCornerShape(10.dp))
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
    // نموذج إضافة غرفة
    var newRoomName by remember { mutableStateOf("") }
    var newRoomCategory by remember { mutableStateOf("main") }
    var newRoomPerm by remember { mutableStateOf("admin_only") }
    var addingRoom by remember { mutableStateOf(false) }
    var messageIsError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        channels = container.chat.channels()
        rulesLang = container.prefs.readLocale()
        rulesText = container.chat.rules(rulesLang).joinToString("\n")
    }

    Column(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        AdminSubscreenHeader(
            kicker = stringResource(R.string.admin_kicker_chat),
            title = stringResource(R.string.admin_chat_plain),
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            // ── إضافة غرفة جديدة (مثال: «خدمات النقدية») ──
            // تُنشأ مقفلة بصلاحية الأدمن وحده افتراضياً: يقرأ الجميع ولا يكتب إلا الأدمن.
            SectionHeader(title = stringResource(R.string.admin_add_room), leadingEmoji = "➕")
            OutlinedTextField(
                value = newRoomName,
                onValueChange = { newRoomName = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                label = { Text(stringResource(R.string.admin_room_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            val roomCategories = listOf(
                "main" to stringResource(R.string.comm_main_rooms),
                "lang" to stringResource(R.string.comm_lang_rooms),
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(roomCategories) { (value, label) ->
                    AdminFilterChip(label, newRoomCategory == value, { newRoomCategory = value })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AdminFilterChip("🔒 admin_only", newRoomPerm == "admin_only", { newRoomPerm = "admin_only" })
                AdminFilterChip("🔓 everyone", newRoomPerm == "everyone", { newRoomPerm = "everyone" })
            }
            Spacer(Modifier.height(8.dp))
            GradientButton(
                text = stringResource(R.string.admin_add_room),
                enabled = newRoomName.isNotBlank() && !addingRoom,
                onClick = {
                    scope.launch {
                        addingRoom = true
                        val name = newRoomName.trim()
                        // المعرّف من الاسم: لاتيني، بلا مسافات ⇒ يعمل كمفتاح عربي أيضاً
                        val id = name.lowercase()
                            .replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), "-")
                            .trim('-')
                            .ifBlank { "room-${System.currentTimeMillis()}" }
                        container.chat.upsertRoom(
                            id = id,
                            name = name,
                            icon = null,
                            category = newRoomCategory,
                            description = null,
                            sortOrder = (channels.maxOfOrNull { it.sortOrder } ?: 0) + 1,
                            isLocked = newRoomPerm == "admin_only",
                            slowmodeSeconds = 0,
                            postPermission = newRoomPerm,
                        ).onSuccess {
                            message = "✓ $name"
                            messageIsError = false
                            newRoomName = ""
                            channels = container.chat.channels()
                        }.onFailure {
                            message = "فشل الإضافة: ${it.message}"
                            messageIsError = true
                        }
                        addingRoom = false
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Spacer(Modifier.height(6.dp))
                Text(it, style = AppText.tiny, color = if (messageIsError) AppColors.Red else AppColors.Green)
            }
            Spacer(Modifier.height(8.dp))

            SectionHeader(title = stringResource(R.string.comm_main_rooms))
            channels.forEach { channel ->
                var slow by remember(channel.id) { mutableStateOf(channel.slowmodeSeconds) }
                GlassCard(padding = 10.dp) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            RoomIcon(channelId = channel.id, size = 22.dp)
                            Text(channel.name, style = AppText.sectionTitle, color = AppColors.Text)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(channel.category, style = AppText.tiny, color = AppColors.Muted)
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf(0, 5, 10, 30, 60, 300)) { seconds ->
                            AdminFilterChip("${seconds}s", slow == seconds, { slow = seconds })
                        }
                    }
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(listOf("everyone", "members", "creators_admin", "admin_only")) { perm ->
                            AdminFilterChip(
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

            SectionHeader(title = stringResource(R.string.chat_announcement), leadingEmoji = "📢")
            OutlinedTextField(
                value = announcement,
                onValueChange = { announcement = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                label = { Text(stringResource(R.string.chat_announcement)) },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(6.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    AdminFilterChip(stringResource(R.string.common_all), targetChannel == null, { targetChannel = null })
                }
                items(channels.take(4), key = { it.id }) { channel ->
                    AdminFilterChip(channel.name, targetChannel == channel.id, { targetChannel = channel.id })
                }
            }
            Spacer(Modifier.height(8.dp))
            GradientButton(
                text = stringResource(R.string.admin_send_announcement),
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
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("ar", "en", "tr", "fr")) { lang ->
                    AdminFilterChip(lang, rulesLang == lang, {
                        rulesLang = lang
                        scope.launch { rulesText = container.chat.rules(lang).joinToString("\n") }
                    })
                }
            }
            OutlinedTextField(
                value = rulesText,
                onValueChange = { rulesText = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
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
