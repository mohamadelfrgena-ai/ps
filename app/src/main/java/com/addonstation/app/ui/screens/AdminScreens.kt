package com.addonstation.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.addonstation.app.AppContainer
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.appViewModel
import com.addonstation.app.core.ChatReport
import com.addonstation.app.core.ContentItem
import com.addonstation.app.core.CreatorApplication
import com.addonstation.app.core.CreatorSubmission
import com.addonstation.app.core.PaymentMethodSetting
import com.addonstation.app.core.PaymentOrder
import com.addonstation.app.core.Profile
import com.addonstation.app.core.SiteAsset
import com.addonstation.app.core.SubscriptionPlan
import com.addonstation.app.core.AcTask
import com.addonstation.app.ui.components.ConfirmDialog
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.FullImageDialog
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.MuteDialog
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement

class AdminViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val tab: Int = 0,
        val loading: Boolean = false,
        val reports: List<ChatReport> = emptyList(),
        val content: List<ContentItem> = emptyList(),
        val users: List<Profile> = emptyList(),
        val orders: List<PaymentOrder> = emptyList(),
        val applications: List<CreatorApplication> = emptyList(),
        val submissions: List<CreatorSubmission> = emptyList(),
        val assets: List<SiteAsset> = emptyList(),
        val tasks: List<AcTask> = emptyList(),
        val methods: List<PaymentMethodSetting> = emptyList(),
        val plans: List<SubscriptionPlan> = emptyList(),
        val acInfo: String = "",
        val error: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun setTab(t: Int) {
        _ui.value = _ui.value.copy(tab = t)
        load(t)
    }

    fun load(tab: Int = _ui.value.tab) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                when (tab) {
                    0 -> _ui.value = _ui.value.copy(reports = app.chat.openReports())
                    1 -> _ui.value = _ui.value.copy(content = app.content.adminList())
                    2 -> _ui.value = _ui.value.copy(users = app.profiles.adminList())
                    3 -> _ui.value = _ui.value.copy(orders = app.payments.adminOrders())
                    4 -> _ui.value = _ui.value.copy(applications = app.creators.pendingApplications())
                    5 -> _ui.value = _ui.value.copy(submissions = app.creators.pendingSubmissions())
                    6 -> _ui.value = _ui.value.copy(assets = app.assets.adminAll())
                    7 -> _ui.value = _ui.value.copy(tasks = app.wallet.adminTasks())
                    8 -> _ui.value = _ui.value.copy(methods = app.payments.methods())
                    9 -> _ui.value = _ui.value.copy(plans = app.payments.plans())
                    10 -> {
                        val preview = safe { app.wallet.adminPreview().toString() }
                        val stats = safe { app.wallet.adminStats().toString() }
                        _ui.value = _ui.value.copy(acInfo = "Preview:\n$preview\n\nStats:\n$stats")
                    }
                }
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            } finally {
                _ui.value = _ui.value.copy(loading = false)
            }
        }
    }

    private suspend fun safe(block: suspend () -> String): String = try {
        block()
    } catch (e: Exception) {
        e.message ?: "Error"
    }

    fun act(block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(error = e.message)
            }
            load()
        }
    }
}

@Composable
fun AdminScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val avm: AdminViewModel = appViewModel { AdminViewModel(app) }
    val ui by avm.ui.collectAsState()
    val session by vm.session.collectAsState()
    val scope = rememberCoroutineScope()

    androidx.compose.runtime.LaunchedEffect(Unit) { avm.load(0) }

    val tabs = listOf(
        stringResource(R.string.ad_reports),
        stringResource(R.string.ad_content),
        stringResource(R.string.ad_users),
        stringResource(R.string.ad_payments),
        stringResource(R.string.ad_creators),
        stringResource(R.string.ad_submissions),
        stringResource(R.string.ad_assets),
        stringResource(R.string.ad_tasks),
        stringResource(R.string.ad_methods),
        stringResource(R.string.ad_plans),
        stringResource(R.string.ad_ac)
    )

    var muteUserId by remember { mutableStateOf<String?>(null) }
    var deleteContentId by remember { mutableStateOf<Long?>(null) }
    var editUser by remember { mutableStateOf<Profile?>(null) }
    var reviewApp by remember { mutableStateOf<CreatorApplication?>(null) }
    var receiptUrl by remember { mutableStateOf<String?>(null) }
    var showAssetForm by remember { mutableStateOf(false) }
    var showTaskForm by remember { mutableStateOf(false) }
    var showMethodForm by remember { mutableStateOf(false) }

    if (session.profile?.isStaff != true) {
        Column(Modifier.fillMaxSize()) {
            TopBar(
                title = stringResource(R.string.ad_title), titleIcon = R.drawable.ic_shield,
                vm = vm, showBack = true, onBack = { nav.popBackStack() },
                onWallet = {}, onNotifications = {}, onProfile = {}
            )
            EmptyBox("🛡️", Modifier.weight(1f))
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.ad_title), titleIcon = R.drawable.ic_shield,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        ScrollableTabRow(selectedTabIndex = ui.tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t ->
                Tab(selected = ui.tab == i, onClick = { avm.setTab(i) }, text = { Text(t) })
            }
        }
        if (ui.loading) {
            LoadingBox(Modifier.weight(1f))
            return@Column
        }
        ui.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(12.dp))
        }
        LazyColumn(Modifier.weight(1f)) {
            when (ui.tab) {
                0 -> items(ui.reports) { r ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${r.id} • msg ${r.messageId} • ${r.status}", fontWeight = FontWeight.Bold)
                            Text("📝 ${r.message?.content?.take(200) ?: ""}", style = MaterialTheme.typography.bodySmall)
                            Text("⚠️ ${r.reason}", style = MaterialTheme.typography.bodySmall)
                            Row {
                                OutlinedButton(onClick = { avm.act { app.chat.setReportStatus(r.id, "actioned") } }) {
                                    Text(stringResource(R.string.ad_action))
                                }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(onClick = { avm.act { app.chat.setReportStatus(r.id, "dismissed") } }) {
                                    Text(stringResource(R.string.ad_dismiss))
                                }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(onClick = { muteUserId = r.message?.userId }) {
                                    Text(stringResource(R.string.ad_mute))
                                }
                            }
                        }
                    }
                }
                1 -> items(ui.content) { c ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${c.id} ${c.title} [${c.plan}] ${if (c.isPublished) "✅" else "🚫"}", fontWeight = FontWeight.Bold)
                            Row {
                                OutlinedButton(onClick = { avm.act { app.content.setPublished(c.id, !c.isPublished) } }) {
                                    Text(stringResource(if (c.isPublished) R.string.ad_unpublish else R.string.ad_publish))
                                }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(onClick = { deleteContentId = c.id }) {
                                    Text(stringResource(R.string.ad_delete))
                                }
                            }
                        }
                    }
                }
                2 -> items(ui.users) { u ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("@${u.username} [${u.role}/${u.plan}]", fontWeight = FontWeight.Bold)
                                Text(u.id.take(8), style = MaterialTheme.typography.labelSmall)
                            }
                            OutlinedButton(onClick = { editUser = u }) { Text(stringResource(R.string.ad_apply)) }
                        }
                    }
                }
                3 -> items(ui.orders) { o ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${o.id} ${o.profiles?.username ?: o.userId.take(8)} • ${o.planId} • ${o.amountLyd} LYD • ${o.status}", fontWeight = FontWeight.Bold)
                            Text("ref: ${o.referenceCode} • ${o.paymentMethod}", style = MaterialTheme.typography.bodySmall)
                            Row {
                                if (!o.receiptUrl.isNullOrBlank()) {
                                    OutlinedButton(onClick = {
                                        scope.launch {
                                            receiptUrl = try {
                                                app.payments.receiptUrl(o.receiptUrl)
                                            } catch (e: Exception) {
                                                Toast.makeText(ctx, e.message, Toast.LENGTH_SHORT).show(); null
                                            }
                                        }
                                    }) { Text(stringResource(R.string.ad_view_receipt)) }
                                    Spacer(Modifier.width(6.dp))
                                }
                                OutlinedButton(onClick = { avm.act { app.payments.approve(o.id) } }) {
                                    Text("✓")
                                }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(onClick = { avm.act { app.payments.reject(o.id) } }) {
                                    Text("✕")
                                }
                            }
                        }
                    }
                }
                4 -> items(ui.applications) { a ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${a.id} ${a.creatorName} (${a.profiles?.username ?: ""})", fontWeight = FontWeight.Bold)
                            Text("${a.country ?: ""} • ${a.contentTypes ?: ""}", style = MaterialTheme.typography.bodySmall)
                            Text(a.bio ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            OutlinedButton(onClick = { reviewApp = a }) { Text(stringResource(R.string.ad_action)) }
                        }
                    }
                }
                5 -> items(ui.submissions) { s ->
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("#${s.id} ${s.title} [${s.contentType}]", fontWeight = FontWeight.Bold)
                            Text("by ${s.profiles?.username ?: s.creatorId.take(8)} • v${s.version ?: "?"}", style = MaterialTheme.typography.bodySmall)
                            Text(s.description?.take(200) ?: "", style = MaterialTheme.typography.bodySmall, maxLines = 3)
                            Row {
                                OutlinedButton(onClick = {
                                    avm.act { app.creators.publishSubmission(s) }
                                    Toast.makeText(ctx, ctx.getString(R.string.ad_saved), Toast.LENGTH_SHORT).show()
                                }) { Text(stringResource(R.string.ad_approve)) }
                                Spacer(Modifier.width(6.dp))
                                OutlinedButton(onClick = { avm.act { app.creators.rejectSubmission(s.id) } }) {
                                    Text(stringResource(R.string.ad_reject))
                                }
                            }
                        }
                    }
                }
                6 -> {
                    item {
                        Button(onClick = { showAssetForm = !showAssetForm }, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(stringResource(R.string.ad_asset_add))
                        }
                    }
                    if (showAssetForm) {
                        item { AssetForm(app) { showAssetForm = false; avm.load() } }
                    }
                    items(ui.assets) { a ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("#${a.id} [${a.assetType}] ${a.name ?: ""}", fontWeight = FontWeight.Bold)
                                    Text("${a.requiredPlan} • ${a.fileUrl.take(60)}", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(onClick = { avm.act { app.assets.delete(a.id) } }) {
                                    Text(stringResource(R.string.ad_delete))
                                }
                            }
                        }
                    }
                }
                7 -> {
                    item {
                        Button(onClick = { showTaskForm = !showTaskForm }, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(stringResource(R.string.ad_task_add))
                        }
                    }
                    if (showTaskForm) {
                        item { TaskForm(app) { showTaskForm = false; avm.load() } }
                    }
                    items(ui.tasks) { t ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("#${t.id} ${t.title} (+${t.rewardAc} AC)", fontWeight = FontWeight.Bold)
                                    Text("${t.slug} • ${t.frequency ?: ""}", style = MaterialTheme.typography.labelSmall)
                                }
                                OutlinedButton(onClick = { avm.act { app.wallet.adminDeleteTask(t.id) } }) {
                                    Text(stringResource(R.string.ad_delete))
                                }
                            }
                        }
                    }
                }
                8 -> {
                    item {
                        Button(onClick = { showMethodForm = !showMethodForm }, modifier = Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(stringResource(R.string.ad_method_add))
                        }
                    }
                    if (showMethodForm) {
                        item { MethodForm(app) { showMethodForm = false; avm.load() } }
                    }
                    items(ui.methods) { m ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.method, fontWeight = FontWeight.Bold)
                                    Text("${m.accountName ?: ""} ${m.accountIdentifier ?: ""}", style = MaterialTheme.typography.bodySmall)
                                }
                                OutlinedButton(onClick = { avm.act { app.payments.adminDeleteMethod(m.id) } }) {
                                    Text(stringResource(R.string.ad_delete))
                                }
                            }
                        }
                    }
                }
                9 -> items(ui.plans) { p ->
                    var price by remember(p.id) { mutableStateOf(p.priceLyd.toString()) }
                    var days by remember(p.id) { mutableStateOf(p.durationDays.toString()) }
                    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("${p.id} (${p.name ?: ""})", fontWeight = FontWeight.Bold)
                            Row {
                                OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text(stringResource(R.string.ad_price)) }, singleLine = true, modifier = Modifier.weight(1f))
                                Spacer(Modifier.width(6.dp))
                                OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.ad_days)) }, singleLine = true, modifier = Modifier.weight(1f))
                            }
                            Spacer(Modifier.height(6.dp))
                            Button(onClick = {
                                avm.act {
                                    app.payments.adminUpdatePlan(p.id, price.toDoubleOrNull() ?: p.priceLyd, days.toIntOrNull() ?: p.durationDays)
                                }
                            }) { Text(stringResource(R.string.ad_saved)) }
                        }
                    }
                }
                10 -> item { AcAdminPanel(app) }
            }
        }
    }

    muteUserId?.let { uid ->
        if (uid == null) {
            muteUserId = null
        } else {
            MuteDialog(
                onSubmit = { mins ->
                    avm.act {
                        app.auth.currentUserId()?.let { app.chat.muteUser(uid, mins, it) }
                    }
                    muteUserId = null
                },
                onDismiss = { muteUserId = null }
            )
        }
    }
    deleteContentId?.let { id ->
        ConfirmDialog(
            title = stringResource(R.string.ad_delete),
            text = stringResource(R.string.ad_delete_confirm),
            onConfirm = {
                avm.act { app.content.delete(id) }
                deleteContentId = null
            },
            onDismiss = { deleteContentId = null }
        )
    }
    editUser?.let { u ->
        UserEditDialog(u, onApply = { plan, role, expires ->
            avm.act { app.profiles.adminUpdateProfile(u.id, plan, role, expires) }
            editUser = null
        }, onDismiss = { editUser = null })
    }
    reviewApp?.let { a ->
        var status by remember { mutableStateOf("approved") }
        var note by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reviewApp = null },
            title = { Text("#${a.id} ${a.creatorName}") },
            text = {
                Column {
                    Row {
                        FilterChip(selected = status == "approved", onClick = { status = "approved" }, label = { Text(stringResource(R.string.ad_approve)) })
                        Spacer(Modifier.width(6.dp))
                        FilterChip(selected = status == "rejected", onClick = { status = "rejected" }, label = { Text(stringResource(R.string.ad_reject)) })
                    }
                    OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text(stringResource(R.string.ad_review_note)) }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    avm.act { app.creators.reviewApplication(a.id, status, note.ifBlank { null }) }
                    reviewApp = null
                }) { Text(stringResource(R.string.ad_apply)) }
            },
            dismissButton = { TextButton(onClick = { reviewApp = null }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    receiptUrl?.let { FullImageDialog(it) { receiptUrl = null } }
}

@Composable
private fun UserEditDialog(u: Profile, onApply: (String, String, String?) -> Unit, onDismiss: () -> Unit) {
    var plan by remember { mutableStateOf(u.plan) }
    var role by remember { mutableStateOf(u.role) }
    var days by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("@${u.username}") },
        text = {
            Column {
                Text(stringResource(R.string.ad_plan), fontWeight = FontWeight.Bold)
                Row {
                    listOf("free", "epic", "legendary").forEach { p ->
                        FilterChip(selected = plan == p, onClick = { plan = p }, label = { Text(p) })
                        Spacer(Modifier.width(4.dp))
                    }
                }
                Text(stringResource(R.string.ad_role), fontWeight = FontWeight.Bold)
                Row {
                    listOf("member", "creator", "moderator", "admin").forEach { r ->
                        FilterChip(selected = role == r, onClick = { role = r }, label = { Text(r) })
                        Spacer(Modifier.width(4.dp))
                    }
                }
                OutlinedTextField(value = days, onValueChange = { days = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.ad_expires)) }, singleLine = true)
            }
        },
        confirmButton = {
            Button(onClick = {
                val exp = days.toIntOrNull()?.takeIf { it > 0 }?.let { plusDaysIso(it) }
                onApply(plan, role, exp)
            }) { Text(stringResource(R.string.ad_apply)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

private fun plusDaysIso(days: Int): String {
    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
    cal.add(Calendar.DAY_OF_YEAR, days)
    val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
    sdf.timeZone = TimeZone.getTimeZone("UTC")
    return sdf.format(cal.time)
}

@Composable
private fun AssetForm(app: AppContainer, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("avatar") }
    var name by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("epic") }
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var mime by remember { mutableStateOf("image/png") }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (b, m) = readUriBytes(ctx, uri)
            bytes = b
            mime = m ?: "image/png"
        }
    }
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            Row {
                listOf("avatar", "banner", "chat_item").forEach { t ->
                    FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                    Spacer(Modifier.width(4.dp))
                }
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ad_asset_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Row {
                listOf("free", "epic", "legendary").forEach { p ->
                    FilterChip(selected = plan == p, onClick = { plan = p }, label = { Text(p) })
                    Spacer(Modifier.width(4.dp))
                }
            }
            OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (bytes == null) stringResource(R.string.ad_asset_file) else "🖼️ ${(bytes!!.size / 1024)} KB")
            }
            Button(
                onClick = {
                    scope.launch {
                        busy = true
                        try {
                            val path = "admin/${System.currentTimeMillis()}.png"
                            app.backend.storageUpload("site-assets", path, bytes!!, mime)
                            val url = app.backend.storagePublicUrl("site-assets", path)
                            app.assets.add(type, name.ifBlank { type }, plan, url)
                            Toast.makeText(ctx, ctx.getString(R.string.ad_saved), Toast.LENGTH_SHORT).show()
                            onDone()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                        } finally {
                            busy = false
                        }
                    }
                },
                enabled = bytes != null && !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.ad_asset_add))
            }
        }
    }
}

@Composable
private fun TaskForm(app: AppContainer, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var slug by remember { mutableStateOf("") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var reward by remember { mutableStateOf("10") }
    var freq by remember { mutableStateOf("daily") }
    var max by remember { mutableStateOf("0") }
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = slug, onValueChange = { slug = it }, label = { Text(stringResource(R.string.ad_task_slug)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.ad_task_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(stringResource(R.string.ad_task_desc)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = reward, onValueChange = { reward = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.ad_task_reward)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = freq, onValueChange = { freq = it }, label = { Text(stringResource(R.string.ad_task_freq)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = max, onValueChange = { max = it.filter(Char::isDigit) }, label = { Text(stringResource(R.string.ad_task_max)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    scope.launch {
                        try {
                            app.wallet.adminAddTask(slug.trim(), title.trim(), desc.trim(), reward.toLongOrNull() ?: 0, freq.trim(), max.toIntOrNull()?.takeIf { it > 0 })
                            onDone()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = slug.isNotBlank() && title.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ad_task_add)) }
        }
    }
}

@Composable
private fun MethodForm(app: AppContainer, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    Card(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(12.dp)) {
            OutlinedTextField(value = code, onValueChange = { code = it }, label = { Text(stringResource(R.string.ad_method_code)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.ad_method_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = account, onValueChange = { account = it }, label = { Text(stringResource(R.string.ad_method_account)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = instructions, onValueChange = { instructions = it }, label = { Text(stringResource(R.string.ad_method_instructions)) }, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    scope.launch {
                        try {
                            app.payments.adminUpsertMethod(code.trim(), name.ifBlank { null }, account.ifBlank { null }, instructions.ifBlank { null })
                            onDone()
                        } catch (e: Exception) {
                            Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                        }
                    }
                },
                enabled = code.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) { Text(stringResource(R.string.ad_method_add)) }
        }
    }
}

@Composable
private fun AcAdminPanel(app: AppContainer) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf("") }
    var userId by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("100") }
    var reason by remember { mutableStateOf("") }
    Column(Modifier.padding(12.dp)) {
        OutlinedTextField(value = query, onValueChange = { query = it }, label = { Text(stringResource(R.string.ad_search_user)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Button(onClick = {
            scope.launch {
                results = try {
                    app.wallet.adminSearchUsers(query).toString().take(2000)
                } catch (e: Exception) { e.message ?: "Error" }
            }
        }) { Text(stringResource(R.string.common_search)) }
        if (results.isNotBlank()) {
            Text(results, style = MaterialTheme.typography.labelSmall, modifier = Modifier.verticalScroll(rememberScrollState()).height(140.dp))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(value = userId, onValueChange = { userId = it.trim() }, label = { Text("user_id") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() } }, label = { Text(stringResource(R.string.ad_amount)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(value = reason, onValueChange = { reason = it }, label = { Text(stringResource(R.string.ad_reason)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Row {
            Button(onClick = {
                scope.launch {
                    try {
                        app.wallet.adminAdjust(userId, amount.toLongOrNull() ?: 0, reason, "give")
                        Toast.makeText(ctx, ctx.getString(R.string.ad_saved), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }, enabled = userId.isNotBlank()) { Text(stringResource(R.string.ad_give)) }
            Spacer(Modifier.width(6.dp))
            OutlinedButton(onClick = {
                scope.launch {
                    try {
                        app.wallet.adminAdjust(userId, amount.toLongOrNull() ?: 0, reason, "remove")
                        Toast.makeText(ctx, ctx.getString(R.string.ad_saved), Toast.LENGTH_SHORT).show()
                    } catch (e: Exception) {
                        Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                    }
                }
            }, enabled = userId.isNotBlank()) { Text(stringResource(R.string.ad_remove)) }
        }
        Spacer(Modifier.height(6.dp))
        OutlinedButton(onClick = {
            scope.launch {
                try {
                    app.wallet.adminGlobalReward(amount.toLongOrNull() ?: 0, reason)
                    Toast.makeText(ctx, ctx.getString(R.string.ad_saved), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(ctx, e.message, Toast.LENGTH_LONG).show()
                }
            }
        }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ad_global)) }
    }
}
