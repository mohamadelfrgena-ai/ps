package com.addonstation.mobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addonstation.mobile.AppState
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.*
import com.addonstation.mobile.ui.theme.*

@Composable
fun ProfileScreen(state: AppState, go: (String) -> Unit) {
    val p by state.profile.collectAsStateWithLifecycle()
    val session by state.sb.session.collectAsStateWithLifecycle()
    var counts by remember { mutableStateOf(Triple(0L, 0L, 0L)) }
    var tab by rememberSaveable { mutableStateOf(0) }
    LaunchedEffect(session?.userId) { session?.userId?.let { counts = state.repo.counts(it) } }

    val profile = p ?: run {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.green) }; return
    }
    val plan = profile.plan ?: "free"

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp)) {
        Box(Modifier.fillMaxWidth().height(215.dp)) {
            Img(profile.bannerUrl, R.drawable.default_banner, Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)))
            Box(Modifier.padding(start = 18.dp).align(Alignment.BottomStart).size(92.dp)) {
                Box(Modifier.fillMaxSize().shadow(14.dp, RoundedCornerShape(24.dp)).clip(RoundedCornerShape(24.dp)).background(C.bg).padding(4.dp)) {
                    Img(profile.avatarUrl, R.drawable.onefree, Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)))
                }
                if (profile.showOnline) Box(
                    Modifier.align(Alignment.BottomStart).offset((-4).dp, (-10).dp).size(14.dp).clip(CircleShape).background(C.bg).padding(2.dp).clip(CircleShape).background(C.green)
                )
            }
        }
        Column(Modifier.padding(horizontal = 20.dp)) {
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(profile.username, color = C.text, fontWeight = FontWeight.Bold, fontSize = 21.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, false))
                Spacer(Modifier.width(8.dp)); PlanBadge(plan)
            }
            Text("@${profile.username.lowercase()} · Member Since ${profile.createdAt?.take(10)?.replace('-', '/') ?: ""}",
                color = C.dim, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
        }
        Row(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MButton("✏️ Edit Profile", Modifier.weight(1f), height = 42.dp) { go("settings") }
            MButton(when (plan.lowercase()) { "epic" -> "Epic 💎"; "free" -> "Upgrade 👑"; else -> "Legendary 👑" }, Modifier.weight(1f), BtnStyle.Gold, height = 42.dp) {}
            MButton("⚙️ Dashboard", Modifier.weight(1f), BtnStyle.Blue, height = 42.dp) { go("settings") }
        }
        Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("1" to "Level", "${counts.first}" to "My Content", "${counts.second}" to "Favorites", "${counts.third}" to "Downloads").forEach { (v, l) ->
                Column(
                    Modifier.weight(1f).card(RoundedCornerShape(15.dp), Brush.linearGradient(listOf(C.elevated, C.surface))).padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(v, style = mc(15).copy(fontWeight = FontWeight.Bold))
                    Spacer(Modifier.height(8.dp))
                    Text(l, color = C.muted, fontSize = 10.sp, maxLines = 1)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Segmented(listOf("About", "My Content", "Favorites", "My Plan"), tab) { tab = it }
        when (tab) {
            0 -> {
                InfoCard("📝 Bio") { Text(profile.bio?.takeIf { it.isNotBlank() } ?: "No bio yet.", color = C.muted, fontSize = 12.sp) }
                Spacer(Modifier.height(10.dp))
                InfoCard("⚡ Account Information") {
                    InfoRow("Plan") { PlanBadge(plan) }
                    InfoRow("Online status") { Text(if (profile.showOnline) "🟢 Online" else "⚫ Hidden", color = C.muted, fontSize = 12.sp) }
                    InfoRow("Equipped item") { Text(profile.equipped ?: "—", color = C.muted, fontSize = 12.sp) }
                    InfoRow("Email") { Text(session?.email ?: "", color = C.muted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
            1 -> EmptyState("📦", "No content yet", "Your published addons and worlds will appear here.")
            2 -> EmptyState("⭐", "No favorites yet", "Tap the star on any addon to save it here.")
            else -> InfoCard("👑 My Plan") {
                InfoRow("Current plan") { PlanBadge(plan) }
            }
        }
    }
}

@Composable
private fun InfoCard(title: String, content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().card(RoundedCornerShape(16.dp), Brush.linearGradient(listOf(C.elevated, C.surface))).padding(14.dp),
) {
    Text(title, color = C.text, fontWeight = FontWeight.Bold, fontSize = 13.sp)
    Spacer(Modifier.height(12.dp))
    content()
}

@Composable
private fun InfoRow(label: String, value: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = C.muted, fontSize = 12.sp, modifier = Modifier.weight(1f))
        value()
    }
}
