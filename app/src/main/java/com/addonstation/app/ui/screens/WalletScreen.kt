package com.addonstation.app.ui.screens

import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import com.addonstation.app.core.AcEvent
import com.addonstation.app.core.AcTask
import com.addonstation.app.core.AcTransaction
import com.addonstation.app.core.Constants
import com.addonstation.app.core.Formats
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.OnResume
import com.addonstation.app.ui.navigation.Routes
import com.addonstation.app.ui.theme.LocalExtra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WalletViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val balance: Long = 0,
        val tasks: List<AcTask> = emptyList(),
        val events: List<AcEvent> = emptyList(),
        val tx: List<AcTransaction> = emptyList(),
        val claiming: Long? = null
    )

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
                val balance = app.wallet.balance(uid)
                val tasks = app.wallet.tasks()
                val events = app.wallet.events()
                val tx = app.wallet.transactions(uid)
                _ui.value = Ui(false, null, balance, tasks, events, tx)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun claimTask(id: Long, onMsg: (String) -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(claiming = id)
            try {
                app.wallet.claimTask(id)
                onMsg("✓")
                refresh()
            } catch (e: Exception) {
                onMsg(e.message ?: "Error")
            } finally {
                _ui.value = _ui.value.copy(claiming = null)
            }
        }
    }

    fun claimEvent(id: Long, onMsg: (String) -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(claiming = -id)
            try {
                app.wallet.claimEvent(id)
                onMsg("✓")
                refresh()
            } catch (e: Exception) {
                onMsg(e.message ?: "Error")
            } finally {
                _ui.value = _ui.value.copy(claiming = null)
            }
        }
    }
}

@Composable
fun WalletScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val wvm: WalletViewModel = appViewModel { WalletViewModel(app) }
    val ui by wvm.ui.collectAsState()
    val session by vm.session.collectAsState()
    OnResume { wvm.refresh(); vm.refreshBalance() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.ac_wallet), titleIcon = R.drawable.ic_store,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = {}, onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        if (session.userId == null) {
            EmptyBox(stringResource(R.string.common_login_required), Modifier.weight(1f))
            return@Column
        }
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { wvm.refresh() }, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f)) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) {
                        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                            AsImage(model = Constants.AC_COIN, contentDescription = "AC", modifier = Modifier.size(54.dp))
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text(stringResource(R.string.ac_balance), style = MaterialTheme.typography.labelLarge)
                                Text("${Formats.count(ui.balance)} AC", style = MaterialTheme.typography.displaySmall, color = LocalExtra.current.gold)
                            }
                        }
                    }
                    Text(
                        stringResource(R.string.ac_infodescription),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(stringResource(R.string.ac_tasks), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    Text(stringResource(R.string.ac_howtoearn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (ui.tasks.isEmpty()) {
                    item { EmptyBox(stringResource(R.string.ac_getmore)) }
                } else {
                    items(ui.tasks) { t ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t.title, fontWeight = FontWeight.Bold)
                                    if (!t.description.isNullOrBlank()) {
                                        Text(t.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("+${t.rewardAc} AC", color = LocalExtra.current.gold, style = MaterialTheme.typography.labelLarge)
                                }
                                Button(
                                    onClick = {
                                        wvm.claimTask(t.id) {
                                            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                                            vm.refreshBalance()
                                        }
                                    },
                                    enabled = ui.claiming != t.id
                                ) { Text(stringResource(R.string.ac_claim)) }
                            }
                        }
                    }
                }
                if (ui.events.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.ac_events), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    items(ui.events) { e ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(e.title, fontWeight = FontWeight.Bold)
                                    if (!e.description.isNullOrBlank()) {
                                        Text(e.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Text("+${e.rewardAc} AC", color = LocalExtra.current.gold, style = MaterialTheme.typography.labelLarge)
                                }
                                OutlinedButton(
                                    onClick = {
                                        wvm.claimEvent(e.id) {
                                            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
                                            vm.refreshBalance()
                                        }
                                    },
                                    enabled = ui.claiming != -e.id
                                ) { Text(stringResource(R.string.ac_claim)) }
                            }
                        }
                    }
                }
                item {
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.ac_history), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                }
                if (ui.tx.isEmpty()) {
                    item { EmptyBox(stringResource(R.string.ac_notransactions)) }
                } else {
                    items(ui.tx) { t ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(t.reason ?: "AC", style = MaterialTheme.typography.bodyMedium)
                                Text(t.createdAt?.substringBefore("T") ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(
                                "${if (t.amount >= 0) "+" else ""}${t.amount}",
                                fontWeight = FontWeight.Bold,
                                color = if (t.amount >= 0) LocalExtra.current.emerald else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
