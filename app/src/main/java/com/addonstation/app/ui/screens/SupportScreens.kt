package com.addonstation.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.components.ConnectDeviceDialog
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.launch

@Composable
fun SupportScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val scope = rememberCoroutineScope()
    var showConnect by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.support_content_title), titleIcon = R.drawable.ic_shield,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.support_content_kicker), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.support_content_title), style = MaterialTheme.typography.headlineSmall)
                    Text(stringResource(R.string.support_content_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(12.dp))
                    SupportCard(
                        emoji = "👤",
                        title = stringResource(R.string.support_card1_title),
                        desc = stringResource(R.string.support_card1_desc),
                        onClick = { nav.navigate(Routes.PROFILE) }
                    )
                    SupportCard(
                        emoji = "🎮",
                        title = stringResource(R.string.support_card2_title),
                        desc = stringResource(R.string.support_card2_desc),
                        onClick = { showConnect = true }
                    )
                    SupportCard(
                        emoji = "💬",
                        title = stringResource(R.string.support_card3_title),
                        desc = stringResource(R.string.support_card3_desc),
                        onClick = {
                            scope.launch {
                                try {
                                    val ch = app.chat.channels().firstOrNull { it.slug == "support" }
                                    if (ch != null) nav.navigate(Routes.community(ch.id, null))
                                    else nav.navigate(Routes.COMMUNITY)
                                } catch (_: Exception) {
                                    nav.navigate(Routes.COMMUNITY)
                                }
                            }
                        }
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.support_starthelp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    Row {
                        OutlinedButton(onClick = { nav.navigate(Routes.IP_GUIDE) }, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.legacy_helpcenter))
                        }
                    }
                }
            }
        }
    }
    if (showConnect) {
        ConnectDeviceDialog(
            onSaved = { showConnect = false },
            onGuide = {
                showConnect = false
                nav.navigate(Routes.IP_GUIDE)
            },
            onDismiss = { showConnect = false }
        )
    }
}

@Composable
private fun SupportCard(emoji: String, title: String, desc: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, style = MaterialTheme.typography.headlineSmall)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(title, fontWeight = FontWeight.Bold)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(
                "${stringResource(R.string.legacy_open)} →",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge
            )
        }
    }
}

// ---------------------------------------------------------------------------
// IP guide (PS4/PS5 steps with pictures)
// ---------------------------------------------------------------------------

@Composable
fun IpGuideScreen(nav: NavHostController) {
    var device by remember { mutableStateOf(0) } // 0 = PS4, 1 = PS5
    var step by remember { mutableStateOf(0) }
    val titles = if (device == 0) {
        listOf(
            stringResource(R.string.guide_step1title),
            stringResource(R.string.guide_step2title),
            stringResource(R.string.guide_step3title),
            stringResource(R.string.guide_step4title)
        )
    } else {
        listOf(
            stringResource(R.string.guide_ps5step1title),
            stringResource(R.string.guide_ps5step2title),
            stringResource(R.string.guide_ps5step3title),
            stringResource(R.string.guide_ps5step4title)
        )
    }
    val texts = if (device == 0) {
        listOf(
            stringResource(R.string.guide_step1text),
            stringResource(R.string.guide_step2text),
            stringResource(R.string.guide_step3text),
            stringResource(R.string.guide_step4text)
        )
    } else {
        listOf(
            stringResource(R.string.guide_ps5step1text),
            stringResource(R.string.guide_ps5step2text),
            stringResource(R.string.guide_ps5step3text),
            stringResource(R.string.guide_ps5step4text)
        )
    }
    val counter = stringResource(R.string.guide_counter)
        .replace("{current}", (step + 1).toString())
        .replace("{total}", "4")

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("← ${stringResource(R.string.common_close)}", modifier = Modifier.clickable { nav.popBackStack() })
        }
        Text(
            stringResource(R.string.guide_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            stringResource(R.string.guide_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        TabRow(selectedTabIndex = device) {
            Tab(selected = device == 0, onClick = { device = 0; step = 0 }, text = { Text("PS4") })
            Tab(selected = device == 1, onClick = { device = 1; step = 0 }, text = { Text("PS5") })
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                Column(Modifier.padding(16.dp)) {
                    Text(counter, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Text(titles[step], style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(texts[step], style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    Card {
                        AsImage(
                            model = "file:///android_asset/img/ip-guide/${if (device == 0) "ps4" else "ps5"}-ip-${step + 1}.webp",
                            contentDescription = titles[step],
                            modifier = Modifier.fillMaxWidth().height(260.dp)
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Row(Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { if (step > 0) step-- }, enabled = step > 0, modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.guide_prev))
                        }
                        Spacer(Modifier.width(8.dp))
                        if (step < 3) {
                            Button(onClick = { step++ }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.guide_next))
                            }
                        } else {
                            Button(onClick = { nav.popBackStack() }, modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.guide_done))
                            }
                        }
                    }
                }
            }
        }
    }
}
