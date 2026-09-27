package com.addonstation.mobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.AppState
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ChatMessage
import com.addonstation.mobile.data.Profile
import com.addonstation.mobile.ui.components.*
import com.addonstation.mobile.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private val rules = listOf(
    "🤝 Respect all members", "🚫 No insults or harassment", "📵 No racism or discrimination",
    "🔗 No suspicious links or unauthorized advertising", "🔒 Do not share personal information",
    "🛑 No inappropriate content", "🏛️ No political discussion or political content", "⚠️ Violations may lead to mute or ban",
)

/** Community tab: shows the rules gate, then opens the General chat. */
@Composable
fun CommunityScreen(state: AppState, go: (String) -> Unit) {
    val uid = state.sb.session.value?.userId ?: return
    var accepted by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(uid) { accepted = state.repo.acceptedRules(uid) }
    LaunchedEffect(accepted) { if (accepted == true) go("chat") }

    Box(Modifier.fillMaxSize()) {
        if (accepted == null) { CircularProgressIndicator(Modifier.align(Alignment.Center), color = C.green); return@Box }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 50.dp, bottom = 100.dp)) {
            Column(
                Modifier.fillMaxWidth().card(RoundedCornerShape(22.dp), Brush.linearGradient(listOf(C.elevated, C.surface)), Color(0x66FFD479))
                    .padding(18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier.size(52.dp).clip(RoundedCornerShape(16.dp)).background(Color(0x33FFD479)).border(1.dp, Color(0x66FFD479), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) { Text("⚠️", fontSize = 26.sp) }
                Text("Community Rules", style = mc(18), modifier = Modifier.padding(top = 10.dp, bottom = 8.dp))
                Text("Please agree to the community rules before entering Chat.", color = C.muted, fontSize = 11.sp, textAlign = TextAlign.Center)
                Spacer(Modifier.height(12.dp))
                rules.forEach {
                    Text(
                        it, color = C.text, fontSize = 12.sp, textAlign = TextAlign.End,
                        modifier = Modifier.padding(bottom = 8.dp).fillMaxWidth().card(RoundedCornerShape(12.dp), Brush.linearGradient(listOf(C.bg, C.bg)), C.border)
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                MButton("Agree", Modifier.fillMaxWidth(), height = 44.dp) {
                    scope.launch { runCatching { state.repo.acceptRules(uid) }; accepted = true }
                }
                Spacer(Modifier.height(8.dp))
                MButton("I disagree", Modifier.fillMaxWidth(), BtnStyle.Dark, height = 44.dp) { go("home") }
            }
        }
    }
}

@Composable
fun ChatScreen(state: AppState, onBack: () -> Unit) {
    val me = state.sb.session.value?.userId
    var channel by remember { mutableStateOf<String?>(null) }
    var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
    val profiles = remember { mutableStateMapOf<String, Profile>() }
    var text by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()

    suspend fun refresh() {
        val c = channel ?: return
        runCatching {
            val m = state.repo.messages(c)
            val missing = m.map { it.userId }.toSet() - profiles.keys
            profiles.putAll(state.repo.profilesByIds(missing))
            if (m.size != messages.size || m.lastOrNull()?.id != messages.lastOrNull()?.id) {
                messages = m
                if (m.isNotEmpty()) list.animateScrollToItem(m.size - 1)
            }
        }.onFailure { err = it.message }
    }
    LaunchedEffect(Unit) {
        channel = runCatching { state.repo.generalChannelId() }.getOrNull()
        while (true) { refresh(); delay(4000) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().background(C.surface).padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(C.elevated2).clickable { onBack() }, contentAlignment = Alignment.Center) {
                Text("→", color = C.text, fontSize = 16.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("🌍 General", style = mc(13).copy(fontWeight = FontWeight.Bold))
                Text("General community discussion", color = C.muted, fontSize = 10.sp)
            }
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(C.elevated2), contentAlignment = Alignment.Center) { Text("👥", fontSize = 16.sp) }
        }
        LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            items(messages, key = { it.id }) { MessageRow(it, profiles[it.userId]) }
            if (err != null) item { ErrorText(err) }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            MField(text, { text = it }, "Write a message...", Modifier.weight(1f), height = 40.dp)
            Spacer(Modifier.width(6.dp))
            listOf("🖼️", "+", "🎭").forEach {
                Box(Modifier.padding(horizontal = 2.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(if (it == "+") Color.Transparent else C.elevated2),
                    contentAlignment = Alignment.Center) { Text(it, color = C.green, fontSize = if (it == "+") 22.sp else 17.sp) }
            }
            Box(
                Modifier.padding(start = 2.dp).size(40.dp).clip(RoundedCornerShape(12.dp)).background(C.elevated).press {
                    val t = text.trim(); val c = channel
                    if (t.isEmpty() || c == null || me == null) return@press
                    text = ""
                    scope.launch { runCatching { state.repo.sendMessage(c, t) }.onFailure { err = it.message }; refresh() }
                },
                contentAlignment = Alignment.Center,
            ) { Text("➤", color = C.text, fontSize = 18.sp) }
        }
    }
}

@Composable
private fun MessageRow(m: ChatMessage, p: Profile?) {
    Row {
        Img(p?.avatarUrl, R.drawable.default_profile, Modifier.size(38.dp).clip(CircleShape).border(1.dp, C.border2, CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(p?.username ?: "…", color = C.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(6.dp))
                if (p?.plan != null && p.plan.lowercase() != "free") PlanBadge(p.plan)
                Spacer(Modifier.width(6.dp))
                Text(m.createdAt.take(10).replace('-', '/'), color = C.dim, fontSize = 9.sp)
            }
            Spacer(Modifier.height(4.dp))
            if (m.sticker != null) Img(m.sticker, R.drawable.onefree, Modifier.size(90.dp))
            if (m.content.isNotBlank()) Text(m.content, color = C.text, fontSize = 13.sp)
            Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                listOf("👍", "❤️", "↩ رد").forEach {
                    Text(it, fontSize = 11.sp, color = C.muted, modifier = Modifier.clip(CircleShape).background(C.elevated2)
                        .border(1.dp, C.border2, CircleShape).padding(horizontal = 10.dp, vertical = 5.dp))
                }
            }
        }
    }
}
