package com.addonstation.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import com.addonstation.app.core.PaymentMethodSetting
import com.addonstation.app.core.PaymentOrder
import com.addonstation.app.core.SubscriptionPlan
import com.addonstation.app.core.copyToClipboard
import com.addonstation.app.core.referenceCode
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Plans bottom sheet (used from Profile)
// ---------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlansSheet(plans: List<SubscriptionPlan>, onPick: (SubscriptionPlan) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.home_plans_title), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.home_plans_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(10.dp))
            plans.forEach { plan ->
                Card(
                    onClick = { onPick(plan) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(plan.name ?: plan.id.uppercase(), fontWeight = FontWeight.Bold)
                        Text("${plan.priceLyd} LYD / ${plan.durationDays}d", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        planFeatures(plan.id).forEach { f ->
                            Text("✓ $f", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun planFeatures(id: String): List<String> {
    val lower = id.lowercase()
    return when {
        lower.contains("legend") || lower.contains("vip") -> listOf(
            stringResource(R.string.plans_vip_features_0),
            stringResource(R.string.plans_vip_features_1),
            stringResource(R.string.plans_vip_features_2)
        )
        lower.contains("epic") || lower.contains("normal") || lower.contains("pro") -> listOf(
            stringResource(R.string.plans_normal_features_0),
            stringResource(R.string.plans_normal_features_1),
            stringResource(R.string.plans_normal_features_2)
        )
        else -> listOf(
            stringResource(R.string.plans_free_features_0),
            stringResource(R.string.plans_free_features_1),
            stringResource(R.string.plans_free_features_2)
        )
    }
}

// ---------------------------------------------------------------------------
// Checkout
// ---------------------------------------------------------------------------

class CheckoutViewModel(private val app: AppContainer, private val planId: String) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val plan: SubscriptionPlan? = null,
        val methods: List<PaymentMethodSetting> = emptyList(),
        val method: String? = null,
        val reference: String = referenceCode(),
        val order: PaymentOrder? = null,
        val busy: Boolean = false,
        val done: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val plans = app.payments.plans()
                val plan = plans.firstOrNull { it.id == planId } ?: plans.firstOrNull()
                val methods = app.payments.methods().filter { it.active }
                _ui.value = Ui(false, null, plan, methods, methods.firstOrNull()?.method)
            } catch (e: Exception) {
                _ui.value = Ui(false, e.message)
            }
        }
    }

    fun setMethod(m: String) {
        _ui.value = _ui.value.copy(method = m)
    }

    fun placeOrder(onErr: (String) -> Unit) {
        val uid = app.auth.currentUserId() ?: return
        val plan = _ui.value.plan ?: return
        val method = _ui.value.method ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try {
                val order = app.payments.createOrder(uid, plan, method, _ui.value.reference)
                _ui.value = _ui.value.copy(busy = false, order = order)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false)
                onErr(e.message ?: "Error")
            }
        }
    }

    fun submitReceipt(bytes: ByteArray, mime: String, txRef: String?, onErr: (String) -> Unit) {
        val uid = app.auth.currentUserId() ?: return
        val order = _ui.value.order ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try {
                val path = "$uid/${order.id}_${System.currentTimeMillis()}.jpg"
                app.backend.storageUpload("payment-receipts", path, bytes, mime)
                app.payments.submitReceipt(order.id, path, txRef?.takeIf { it.isNotBlank() })
                _ui.value = _ui.value.copy(busy = false, done = true)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false)
                onErr(e.message ?: "Error")
            }
        }
    }
}

@Composable
fun CheckoutScreen(nav: NavHostController, vm: AppViewModel, planId: String) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val cvm: CheckoutViewModel = appViewModel { CheckoutViewModel(app, planId) }
    val ui by cvm.ui.collectAsState()
    var txRef by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, mime) = readUriBytes(ctx, uri)
            cvm.submitReceipt(bytes, mime ?: "image/jpeg", txRef) {
                Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.profile_buysubscription), titleIcon = R.drawable.ic_store,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null || ui.plan == null -> ErrorBox(ui.error ?: "Error", {}, Modifier.weight(1f))
            ui.done -> {
                Column(Modifier.weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("✅", style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.pay_submitted))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { nav.navigate(Routes.ORDERS) { popUpTo(Routes.CHECKOUT) { inclusive = true } } }) {
                        Text(stringResource(R.string.pay_my_orders))
                    }
                }
            }
            else -> {
                val plan = ui.plan!!
                LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                    item {
                        Text(plan.name ?: plan.id.uppercase(), style = MaterialTheme.typography.headlineSmall)
                        Text(stringResource(R.string.pay_total, plan.priceLyd.toString()), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.pay_choose_method), fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                    }
                    items(ui.methods) { m ->
                        FilterChip(
                            selected = ui.method == m.method,
                            onClick = { cvm.setMethod(m.method) },
                            label = { Text(m.method) },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                    ui.methods.firstOrNull { it.method == ui.method }?.let { m ->
                        item {
                            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                Column(Modifier.padding(14.dp)) {
                                    if (!m.accountName.isNullOrBlank()) {
                                        Text(stringResource(R.string.pay_account, m.accountName ?: ""))
                                    }
                                    if (!m.accountIdentifier.isNullOrBlank()) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(stringResource(R.string.pay_identifier, m.accountIdentifier ?: ""), modifier = Modifier.weight(1f))
                                            Text(
                                                "📋",
                                                modifier = Modifier.clickable {
                                                    copyToClipboard(ctx, m.accountIdentifier ?: "")
                                                    Toast.makeText(ctx, ctx.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                                                }.padding(8.dp)
                                            )
                                        }
                                    }
                                    if (!m.instructions.isNullOrBlank()) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(stringResource(R.string.pay_instructions), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
                                        Text(m.instructions, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                        }
                    }
                    if (ui.order == null) {
                        item {
                            Button(
                                onClick = { cvm.placeOrder { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() } },
                                enabled = ui.method != null && !ui.busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (ui.busy) CircularProgressIndicator(androidx.compose.ui.Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(stringResource(R.string.pay_continue))
                            }
                        }
                    } else {
                        item {
                            Text(stringResource(R.string.pay_reference), fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    ui.order!!.referenceCode,
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.weight(1f)
                                )
                                OutlinedButton(onClick = {
                                    copyToClipboard(ctx, ui.order!!.referenceCode)
                                    Toast.makeText(ctx, ctx.getString(R.string.common_copied), Toast.LENGTH_SHORT).show()
                                }) { Text("📋") }
                            }
                            Text(stringResource(R.string.pay_reference_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = txRef, onValueChange = { txRef = it },
                                label = { Text(stringResource(R.string.pay_tx_ref)) },
                                singleLine = true, modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Text(stringResource(R.string.pay_receipt_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(6.dp))
                            Button(
                                onClick = { picker.launch("image/*") },
                                enabled = !ui.busy,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text(stringResource(R.string.pay_upload_receipt))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// My orders
// ---------------------------------------------------------------------------

class OrdersViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(val loading: Boolean = true, val orders: List<PaymentOrder> = emptyList(), val error: String? = null)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val uid = app.auth.currentUserId() ?: run {
            _ui.value = Ui(loading = false); return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                _ui.value = Ui(false, app.payments.myOrders(uid))
            } catch (e: Exception) {
                _ui.value = Ui(false, emptyList(), e.message)
            }
        }
    }
}

@Composable
fun OrdersScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ovm: OrdersViewModel = appViewModel { OrdersViewModel(app) }
    val ui by ovm.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.pay_my_orders), titleIcon = R.drawable.ic_store,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { ovm.refresh() }, Modifier.weight(1f))
            ui.orders.isEmpty() -> EmptyBox(stringResource(R.string.pay_no_orders), Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f)) {
                items(ui.orders) { o ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row {
                                Text("#${o.id} • ${o.planId}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                Text(
                                    when (o.status) {
                                        "approved" -> stringResource(R.string.pay_status_approved)
                                        "rejected" -> stringResource(R.string.pay_status_rejected)
                                        "submitted" -> stringResource(R.string.pay_status_submitted)
                                        else -> stringResource(R.string.pay_status_pending)
                                    },
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text("${o.amountLyd} LYD • ${o.paymentMethod} • ${o.referenceCode}", style = MaterialTheme.typography.bodySmall)
                            Text(o.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}
