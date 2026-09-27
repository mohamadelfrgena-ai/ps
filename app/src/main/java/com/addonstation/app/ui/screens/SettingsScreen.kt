package com.addonstation.app.ui.screens

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
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
import androidx.navigation.NavHostController
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.core.LanguageManager
import com.addonstation.app.ui.components.ConnectDeviceDialog
import com.addonstation.app.ui.components.LanguageSheet
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

fun restartApp(ctx: Context) {
    val intent = ctx.packageManager.getLaunchIntentForPackage(ctx.packageName)
    val main = Intent.makeRestartActivityTask(intent?.component)
    ctx.startActivity(main)
    Runtime.getRuntime().exit(0)
}

@Composable
fun SettingsScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val session by vm.session.collectAsState()

    var lang by remember { mutableStateOf("ar") }
    var dark by remember { mutableStateOf(true) }
    var notifs by remember { mutableStateOf(true) }
    var devType by remember { mutableStateOf<String?>(null) }
    var devIp by remember { mutableStateOf<String?>(null) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        lang = try {
            app.session.language.first()
        } catch (_: Exception) { "ar" }
        dark = try {
            app.session.darkTheme.first()
        } catch (_: Exception) { true }
        notifs = try {
            app.session.notificationsEnabled.first()
        } catch (_: Exception) { true }
        devType = try {
            app.session.deviceType.first()
        } catch (_: Exception) { null }
        devIp = try {
            app.session.deviceIp.first()
        } catch (_: Exception) { null }
    }

    var showLang by remember { mutableStateOf(false) }
    var showConnect by remember { mutableStateOf(false) }
    var showServer by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }

    val versionName = remember {
        try {
            ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: ""
        } catch (_: Exception) { "" }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.nav_settings), titleIcon = R.drawable.ic_settings, vm = vm,
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        LazyColumn(Modifier.weight(1f)) {
            item {
                // Device
                SettingRow(title = stringResource(R.string.set_device), subtitle = if (devIp != null) "${devType?.uppercase()} • $devIp" else stringResource(R.string.set_device_none), onClick = { showConnect = true })
                // Language
                SettingRow(title = stringResource(R.string.set_language), subtitle = LanguageManager.label(lang), onClick = { showLang = true })
                // Theme
                Text(stringResource(R.string.set_theme), fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = dark, onClick = { dark = true; scope.launch { app.session.setDarkTheme(true) } })
                    Text(stringResource(R.string.set_theme_dark), modifier = Modifier.clickable { dark = true; scope.launch { app.session.setDarkTheme(true) } })
                    RadioButton(selected = !dark, onClick = { dark = false; scope.launch { app.session.setDarkTheme(false) } })
                    Text(stringResource(R.string.set_theme_light), modifier = Modifier.clickable { dark = false; scope.launch { app.session.setDarkTheme(false) } })
                }
                // Notifications
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.set_notifications), modifier = Modifier.weight(1f), fontWeight = FontWeight.Bold)
                    Switch(checked = notifs, onCheckedChange = { notifs = it; scope.launch { app.session.setNotifications(it) } })
                }
                // Account
                if (session.userId != null) {
                    SettingRow(title = stringResource(R.string.settings_profilesettings), subtitle = session.profile?.username ?: "", onClick = { nav.navigate(Routes.PROFILE_SETTINGS) })
                } else {
                    SettingRow(title = stringResource(R.string.header_account_login), subtitle = "", onClick = { nav.navigate(Routes.LOGIN) })
                }
                // Help / support / server / about
                SettingRow(title = stringResource(R.string.set_help), subtitle = "", onClick = { nav.navigate(Routes.IP_GUIDE) })
                SettingRow(title = stringResource(R.string.header_settings_support), subtitle = "", onClick = { nav.navigate(Routes.SUPPORT) })
                SettingRow(title = stringResource(R.string.set_server), subtitle = "", onClick = { showServer = true })
                SettingRow(title = stringResource(R.string.set_about), subtitle = "", onClick = { showAbout = true })
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text(stringResource(R.string.legacy_appversion), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    Text(versionName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
    if (showLang) {
        LanguageSheet(
            onPick = { l ->
                showLang = false
                scope.launch {
                    app.session.setLanguage(l)
                    lang = l
                    LanguageManager.apply(l)
                }
            },
            onDismiss = { showLang = false }
        )
    }
    if (showConnect) {
        ConnectDeviceDialog(
            onSaved = {
                showConnect = false
                scope.launch {
                    devType = try {
                        app.session.deviceType.first()
                    } catch (_: Exception) { null }
                    devIp = try {
                        app.session.deviceIp.first()
                    } catch (_: Exception) { null }
                }
            },
            onGuide = {
                showConnect = false
                nav.navigate(Routes.IP_GUIDE)
            },
            onDismiss = { showConnect = false }
        )
    }
    if (showServer) {
        var url by remember { mutableStateOf("") }
        var key by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showServer = false },
            title = { Text(stringResource(R.string.set_server)) },
            text = {
                Column {
                    OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.set_server_url)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = key, onValueChange = { key = it }, label = { Text(stringResource(R.string.set_server_key)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    scope.launch {
                        app.session.setBackend(url.ifBlank { null }, key.ifBlank { null })
                        Toast.makeText(ctx, ctx.getString(R.string.set_server_saved), Toast.LENGTH_SHORT).show()
                        restartApp(ctx)
                    }
                }) { Text(stringResource(R.string.common_save)) }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        scope.launch {
                            app.session.setBackend(null, null)
                            restartApp(ctx)
                        }
                    }) { Text(stringResource(R.string.set_server_reset)) }
                    TextButton(onClick = { showServer = false }) { Text(stringResource(R.string.common_cancel)) }
                }
            }
        )
    }
    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.set_about)) },
            text = { Text(stringResource(R.string.set_about_text)) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.common_ok)) } }
        )
    }
}

@Composable
fun SettingRow(title: String, subtitle: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Text(title, fontWeight = FontWeight.Bold)
        if (subtitle.isNotBlank()) {
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
