package com.addonstation.mobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addonstation.mobile.AppState
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.*
import com.addonstation.mobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(state: AppState, go: (String) -> Unit) {
    val profile by state.profile.collectAsStateWithLifecycle()
    val session by state.sb.session.collectAsStateWithLifecycle()
    var username by remember(profile?.username) { mutableStateOf(profile?.username ?: "") }
    var saveLabel by remember { mutableStateOf("Save") }
    var lang by rememberSaveable { mutableStateOf("English") }
    var dark by rememberSaveable { mutableStateOf(true) }
    var confirmDelete by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uri = LocalUriHandler.current

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp)) {
        Group("Account")
        Column(
            Modifier.padding(bottom = 8.dp).fillMaxWidth().card(RoundedCornerShape(16.dp), Brush.linearGradient(listOf(C.elevated, C.surface))).padding(12.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(C.elevated2), contentAlignment = Alignment.Center) { Text("👤", fontSize = 19.sp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Username", color = C.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Shown to others as @username", color = C.muted, fontSize = 10.5.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(
                        Modifier.fillMaxWidth().height(40.dp).clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.border2, RoundedCornerShape(12.dp)),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("@", color = C.blue, fontSize = 15.sp, modifier = Modifier.padding(horizontal = 10.dp))
                        BasicTextField(username, { username = it; saveLabel = "Save" }, Modifier.weight(1f), singleLine = true,
                            textStyle = TextStyle(color = C.text, fontSize = 14.sp), cursorBrush = SolidColor(C.green))
                        Box(
                            Modifier.fillMaxHeight().clip(RoundedCornerShape(12.dp)).background(C.green).clickable {
                                val id = session?.userId ?: return@clickable
                                scope.launch {
                                    saveLabel = runCatching { state.repo.updateUsername(id, username.trim()) }
                                        .fold({ state.reload(); "Saved successfully" }, { it.message ?: "Error" })
                                    delay(2500); saveLabel = "Save"
                                }
                            }.padding(horizontal = 12.dp),
                            contentAlignment = Alignment.Center,
                        ) { Text(saveLabel, color = Color(0xFF04210F), fontWeight = FontWeight.Bold, fontSize = 10.5.sp) }
                    }
                }
            }
        }
        Column {
            SettingRow("✉️", "Email address", "Private to your account", arrow = false,
                trailing = { Text(session?.email ?: "", color = C.muted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 150.dp)) }) {}

            Group("Preferences")
            SettingRow("🔔", "Notifications", "Friend requests, replies and mentions") {}
            SettingRow("🌐", "App language", "Choose your preferred language", arrow = false, trailing = {
                var open by remember { mutableStateOf(false) }
                Box {
                    Row(
                        Modifier.clip(RoundedCornerShape(10.dp)).background(C.elevated2).border(1.dp, C.border2, RoundedCornerShape(10.dp))
                            .clickable { open = true }.padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) { Text(lang, color = C.text, fontSize = 12.sp); Spacer(Modifier.width(14.dp)); Text("⌄", color = C.text, fontSize = 12.sp) }
                    DropdownMenu(open, { open = false }, Modifier.background(C.elevated2)) {
                        listOf("English", "العربية").forEach { l ->
                            DropdownMenuItem({ Text(l, color = C.text) }, { lang = l; open = false })
                        }
                    }
                }
            }) {}
            SettingRow("🎨", "App appearance", "Dark or light mode", arrow = false, trailing = {
                Row(Modifier.clip(RoundedCornerShape(12.dp)).background(C.bg).border(1.dp, C.border2, RoundedCornerShape(12.dp)).padding(3.dp)) {
                    listOf(true to "🌙", false to "☀️").forEach { (d, e) ->
                        Box(Modifier.size(34.dp).clip(RoundedCornerShape(9.dp)).background(if (dark == d) C.elevated2 else Color.Transparent).clickable { dark = d },
                            contentAlignment = Alignment.Center) { Text(e, fontSize = 16.sp) }
                    }
                }
            }) {}

            Group("❓ Support")
            SettingRow(icon = R.drawable.help_headset, title = "Help Center", sub = "Guides and frequently asked questions") {}
            SettingRow(icon = R.drawable.report_tool, title = "Report a problem", sub = "Tell us when something is not working") {}
            SettingRow("💬", "Contact us", "Talk to the AddonStation team") {}

            Group("ℹ️ About the app")
            SettingRow("📱", "App version", "AddonStation Mobile", arrow = false, trailing = { Text("v1.2", color = C.muted, fontSize = 11.sp) }) {}
            SettingRow("📄", "Terms and conditions", "قواعد استخدام المنصة") {}
            SettingRow("🔒", "Privacy policy", "كيف نحمي بيانات حسابك") {}

            Group("Account area")
            MButton("🗑️ Delete account", Modifier.fillMaxWidth(), BtnStyle.Danger, height = 44.dp) { confirmDelete = true }
            Text("This action cannot be undone.", color = C.dim, fontSize = 10.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp))
            MButton("Sign out", Modifier.fillMaxWidth(), BtnStyle.Outline, height = 46.dp) { state.sb.signOut(); go("home") }
            Text("© 2026 Silver Fox · جميع الحقوق محفوظة", color = C.dim, fontSize = 10.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 26.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        containerColor = C.elevated,
        title = { Text("Delete account?", color = C.text) },
        text = { Text("This action cannot be undone.", color = C.muted) },
        confirmButton = {
            TextButton({ confirmDelete = false; scope.launch { runCatching { state.repo.deleteAccount() }; go("home") } }) { Text("Delete", color = C.red) }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Cancel", color = C.text) } },
    )
}

@Composable
private fun Group(t: String) = Text(t, style = mc(13), modifier = Modifier.padding(start = 2.dp, top = 18.dp, bottom = 10.dp))

@Composable
private fun SettingRow(
    emoji: String? = null, title: String, sub: String, arrow: Boolean = true, icon: Int? = null,
    trailing: (@Composable () -> Unit)? = null, onClick: () -> Unit,
) {
    Row(
        Modifier.padding(bottom = 8.dp).fillMaxWidth().card(RoundedCornerShape(16.dp), Brush.linearGradient(listOf(C.elevated, C.surface)))
            .press(onClick).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(C.elevated2), contentAlignment = Alignment.Center) {
            if (icon != null) Image(painterResource(icon), null, Modifier.size(26.dp)) else Text(emoji ?: "", fontSize = 19.sp)
        }
        Spacer(Modifier.width(10.dp))
        trailing?.invoke()
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
            Text(title, color = C.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(sub, color = C.muted, fontSize = 10.5.sp, textAlign = TextAlign.End)
        }
        if (arrow) { Spacer(Modifier.width(10.dp)); Text("←", color = C.blue, fontSize = 15.sp) }
    }
}
