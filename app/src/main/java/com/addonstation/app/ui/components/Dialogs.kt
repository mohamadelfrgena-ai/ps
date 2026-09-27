package com.addonstation.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.core.LanguageManager
import com.addonstation.app.core.Profile
import com.addonstation.app.core.isValidIpv4
import com.addonstation.app.ui.theme.LocalExtra
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String = stringResource(R.string.common_ok),
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { Button(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
fun RulesDialog(onAccept: () -> Unit, onReject: () -> Unit) {
    val rules = listOf(
        stringResource(R.string.chat_rules_respect),
        stringResource(R.string.chat_rules_abuse),
        stringResource(R.string.chat_rules_discrimination),
        stringResource(R.string.chat_rules_inappropriate),
        stringResource(R.string.chat_rules_links),
        stringResource(R.string.chat_rules_personal),
        stringResource(R.string.chat_rules_politics)
    )
    AlertDialog(
        onDismissRequest = {},
        title = { Text(stringResource(R.string.chat_rulestitle)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.chat_rulesintro), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                rules.forEach { r ->
                    Text("• $r", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
                }
                Spacer(Modifier.height(8.dp))
                Text("⚠️ ${stringResource(R.string.chat_rules_warning)}", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.gold)
            }
        },
        confirmButton = { Button(onClick = onAccept) { Text(stringResource(R.string.chat_rules_accept)) } },
        dismissButton = { TextButton(onClick = onReject) { Text(stringResource(R.string.chat_rules_reject)) } }
    )
}

@Composable
fun ReportDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var reason by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_report)) },
        text = {
            OutlinedTextField(
                value = reason,
                onValueChange = { reason = it },
                placeholder = { Text(stringResource(R.string.chat_report_hint)) },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(reason.trim()) }, enabled = reason.trim().length >= 3) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

@Composable
fun MuteDialog(onSubmit: (Int) -> Unit, onDismiss: () -> Unit) {
    var minutes by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chat_mute_user)) },
        text = {
            OutlinedTextField(
                value = minutes,
                onValueChange = { minutes = it.filter(Char::isDigit).take(5) },
                label = { Text(stringResource(R.string.chat_mute_minutes)) },
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = { onSubmit(minutes.toIntOrNull()?.coerceAtLeast(1) ?: 30) }) {
                Text(stringResource(R.string.common_ok))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } }
    )
}

/** Device/IP binding dialog (Connect Now), same flow as the original. */
@Composable
fun ConnectDeviceDialog(
    onSaved: () -> Unit,
    onGuide: () -> Unit,
    onDismiss: () -> Unit
) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("ps4") }
    var ip by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.connect_title)) },
        text = {
            Column {
                Text(stringResource(R.string.connect_intro), style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    listOf("ps4" to "PS4", "ps5" to "PS5").forEach { (v, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.selectable(selected = type == v, onClick = { type = v }, role = Role.RadioButton)
                        ) {
                            RadioButton(selected = type == v, onClick = { type = v })
                            Text(label)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = ip,
                    onValueChange = { ip = it.trim(); error = null },
                    label = { Text(stringResource(R.string.connect_iplabel)) },
                    placeholder = { Text("192.168.1.20") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) Text(error!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    stringResource(R.string.connect_guideLink),
                    color = LocalExtra.current.diamond,
                    modifier = Modifier.clickable { onGuide() }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!isValidIpv4(ip)) {
                        error = "IP"
                        return@Button
                    }
                    checking = true
                    scope.launch {
                        delay(700) // same "checking" beat as the original dialog
                        app.session.setDevice(type, ip.trim())
                        checking = false
                        onSaved()
                    }
                },
                enabled = !checking
            ) {
                if (checking) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.connect_check))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.connect_close)) } }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSheet(onPick: (String) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.set_language), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            LanguageManager.supported.forEach { lang ->
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onPick(lang) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(LanguageManager.label(lang), modifier = Modifier.weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun FullImageDialog(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            TextButton(onClick = onDismiss) { Text("✕ ${stringResource(R.string.common_close)}") }
            AsImage(
                model = url,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = androidx.compose.ui.layout.ContentScale.Fit
            )
        }
    }
}

/** Member quick-view bottom sheet (avatar, rank, bio, actions) — replaces the web popover. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserMiniSheet(
    profile: Profile?,
    friendStatus: String?,
    isMe: Boolean,
    onViewProfile: () -> Unit,
    onFriend: () -> Unit,
    onMention: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        if (profile == null) {
            Text(stringResource(R.string.legacy_userNotFound), modifier = Modifier.padding(24.dp))
        } else {
            Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Avatar(url = profile.avatarUrl, name = profile.username, size = 72.dp)
                Spacer(Modifier.height(8.dp))
                Username(profile = profile, fontSizeSp = 18)
                Text(stringResource(com.addonstation.app.core.Plans.rankLabelRes(profile.displayRank)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (!profile.bio.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(profile.bio, style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onViewProfile, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.chat_view_profile)) }
                    if (!isMe) {
                        Button(
                            onClick = onFriend,
                            enabled = friendStatus != "pending" && friendStatus != "accepted",
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(
                                when (friendStatus) {
                                    "pending" -> stringResource(R.string.fr_pending)
                                    "accepted" -> stringResource(R.string.fr_accepted)
                                    else -> stringResource(R.string.fr_add)
                                },
                                maxLines = 1
                            )
                        }
                        TextButton(onClick = onMention) { Text("@") }
                    }
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}
