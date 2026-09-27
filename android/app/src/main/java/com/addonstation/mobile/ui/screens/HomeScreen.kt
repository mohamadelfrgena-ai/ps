package com.addonstation.mobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.addonstation.mobile.AppState
import com.addonstation.mobile.R
import com.addonstation.mobile.data.Profile
import com.addonstation.mobile.ui.components.*
import com.addonstation.mobile.ui.theme.*
import java.util.Calendar

@Composable
fun HomeScreen(state: AppState, go: (String) -> Unit) {
    val profile by state.profile.collectAsStateWithLifecycle()
    val session by state.sb.session.collectAsStateWithLifecycle()
    var creators by remember { mutableStateOf<List<Pair<Profile, Pair<Long, Long>>>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        creators = state.repo.featuredCreators().map { it to state.repo.creatorStats(it.id) }
    }
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    val greeting = when (hour) { in 5..11 -> "Good morning"; in 12..17 -> "Good afternoon"; else -> "Good evening" }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp)) {
        // Welcome head
        Row(Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, top = 4.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                Text("👋 $greeting", color = C.muted, fontSize = 12.sp)
                Spacer(Modifier.height(3.dp))
                if (profile != null) {
                    Text("Welcome back", style = mc(18), maxLines = 1)
                    Text(profile!!.username, style = mc(14, Color.White), modifier = Modifier.padding(top = 5.dp))
                } else {
                    Text("Welcome to", style = mc(18), maxLines = 1)
                    Text("AddonStation", style = mc(14, Color.White), modifier = Modifier.padding(top = 5.dp))
                }
            }
            Spacer(Modifier.width(13.dp))
            Box(
                Modifier.size(64.dp).shadow(10.dp, RoundedCornerShape(22.dp), spotColor = C.green)
                    .clip(RoundedCornerShape(22.dp)).background(Brush.linearGradient(listOf(C.green, C.blue))).padding(3.dp)
                    .clickable { go("profile") }
            ) {
                Img(profile?.avatarUrl, R.drawable.onefree, Modifier.fillMaxSize().clip(RoundedCornerShape(18.dp)).background(Color(0xFF8D8D8D)))
            }
        }

        if (session == null) {
            Column(
                Modifier.fillMaxWidth().padding(bottom = 12.dp).card(RoundedCornerShape(16.dp),
                    Brush.linearGradient(listOf(C.elevated, C.surface))).padding(14.dp)
            ) {
                Text("Minecraft Addons & Worlds Store", color = C.text, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.height(6.dp))
                Text("Discover addons, worlds and a community built for your next adventure.", color = C.muted, fontSize = 11.5.sp, lineHeight = 17.sp)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MButton("Sign in", Modifier.weight(1f)) { go("login") }
                    MButton("Create account", Modifier.weight(1f), BtnStyle.Dark) { go("register") }
                }
            }
        }

        MField(search, { search = it }, "Search addons and worlds...", Modifier.fillMaxWidth(), height = 44.dp,
            trailing = { Text("🔍", fontSize = 15.sp) })

        SectionTitle("Quick actions")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("⭐" to "Favorites", "📥" to "Downloads", "👑" to "Upgrade", "🎮" to "Device").forEach { (i, l) ->
                Column(
                    Modifier.weight(1f).heightIn(min = 76.dp).card(RoundedCornerShape(15.dp)).press { go(if (l == "Upgrade") "profile" else "store") }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    Box(Modifier.size(34.dp).clip(RoundedCornerShape(11.dp)).background(C.elevated2), contentAlignment = Alignment.Center) { Text(i, fontSize = 20.sp) }
                    Spacer(Modifier.height(6.dp))
                    Text(l, fontSize = 10.sp, color = C.muted, maxLines = 1)
                }
            }
        }

        SectionTitle("Subscription Plans", icon = "✦", action = "View all") { go("profile") }
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            PlanMini("FREE", "Start exploring", C.text, C.border, Modifier.weight(1f))
            PlanMini("EPIC 💎", "More content", C.purple, Color(0x66B98CFF), Modifier.weight(1f))
            PlanMini("LEGENDARY 👑", "Exclusive access", C.gold, Color(0x80FFD479), Modifier.weight(1f))
        }

        SectionTitle("Featured Creators", icon = "👤", action = "View all")
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            creators.forEach { (p, stats) -> CreatorCard(p, stats) }
        }

        // Creator program
        Row(
            Modifier.padding(top = 20.dp).fillMaxWidth()
                .card(RoundedCornerShape(17.dp), Brush.linearGradient(listOf(Color(0x1F3DDC84), C.surface)), Color(0x593DDC84))
                .padding(15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("CREATOR PROGRAM", style = mc(9, C.blue).copy(letterSpacing = 1.4.sp))
                Text("Become a Creator", style = mc(15), modifier = Modifier.padding(vertical = 4.dp))
                Text("Share your addons and worlds with the AddonStation community and unlock a path to creator earnings.",
                    color = C.muted, fontSize = 10.5.sp, lineHeight = 17.sp)
            }
            Spacer(Modifier.width(12.dp))
            MButton("Become a Creator  →", Modifier.width(118.dp), height = 38.dp) { go("register") }
        }

        // Exclusives
        Row(
            Modifier.padding(top = 10.dp).fillMaxWidth()
                .card(RoundedCornerShape(15.dp), Brush.linearGradient(listOf(Color(0x1FFFD479), C.surface)), Color(0x4DFFD479))
                .press { go("store") }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(32.dp).clip(RoundedCornerShape(10.dp)).background(Color(0x33FFD479)), contentAlignment = Alignment.Center) {
                Text("✦", color = C.gold, fontSize = 18.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Exclusives", color = C.text, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text("Creator Specials selected by AddonStation", color = C.muted, fontSize = 10.sp)
            }
            Text("→", color = C.gold, fontSize = 16.sp)
        }

        // Footer
        Column(
            Modifier.padding(top = 26.dp, bottom = 4.dp).fillMaxWidth()
                .card(RoundedCornerShape(18.dp), Brush.linearGradient(listOf(Color(0x146FB8FF), C.surface)), Color(0x2E6FB8FF))
                .padding(vertical = 22.dp, horizontal = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("AddonStation", style = mc(16, Color.White))
            Text("mobile", style = mc(11, Color(0xFF005B9F)).copy(letterSpacing = 1.5.sp))
            Spacer(Modifier.height(8.dp))
            Text("Minecraft Bedrock addons and worlds platform", color = C.muted, fontSize = 11.5.sp, textAlign = TextAlign.Center)
            Spacer(Modifier.height(5.dp))
            Text("Silver Fox — All rights reserved 2026 ©", color = C.dim, fontSize = 10.sp)
            Spacer(Modifier.height(5.dp))
            Text("AddonStation Mobile · Version 1.2", color = C.dim, fontSize = 10.sp)
        }
    }
}

@Composable
private fun PlanMini(title: String, sub: String, color: Color, border: Color, modifier: Modifier) {
    Column(modifier.card(RoundedCornerShape(13.dp), Brush.linearGradient(listOf(C.elevated, C.surface)), border).padding(horizontal = 7.dp, vertical = 9.dp)) {
        Text(title, style = mc(11, color).copy(fontWeight = FontWeight.Bold, letterSpacing = .5.sp), maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Text(sub, color = C.muted, fontSize = 9.5.sp, maxLines = 1)
    }
}

@Composable
private fun CreatorCard(p: Profile, stats: Pair<Long, Long>) {
    Column(
        Modifier.width(178.dp).card(RoundedCornerShape(16.dp), Brush.linearGradient(listOf(C.elevated, C.surface))).clip(RoundedCornerShape(16.dp))
    ) {
        Box(Modifier.fillMaxWidth().height(66.dp).background(C.elevated2)) {
            if (p.bannerUrl != null) Img(p.bannerUrl, R.drawable.default_banner, Modifier.fillMaxSize())
            else Image(painterResource(R.drawable.grass_side), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.None)
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, bottom = 11.dp)) {
            Box(Modifier.offset(y = (-23).dp).size(46.dp)) {
                Box(Modifier.fillMaxSize().clip(RoundedCornerShape(15.dp)).background(C.elevated2).border(2.dp, C.elevated, RoundedCornerShape(15.dp)).padding(2.dp)) {
                    Img(p.avatarUrl, R.drawable.onefree, Modifier.fillMaxSize().clip(RoundedCornerShape(12.dp)))
                }
                Box(
                    Modifier.align(Alignment.BottomEnd).offset(4.dp, 4.dp).size(16.dp).clip(CircleShape).background(C.surface).padding(2.dp)
                        .clip(CircleShape).background(C.green),
                    contentAlignment = Alignment.Center,
                ) { Text("✓", color = Color(0xFF062514), fontSize = 8.sp, fontWeight = FontWeight.Bold) }
            }
            Column(Modifier.offset(y = (-16).dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(p.username, color = C.text, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1,
                        overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    Spacer(Modifier.width(5.dp))
                    Text("Creator", color = C.green, fontSize = 8.5.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.clip(CircleShape).background(Color(0x1A3DDC84)).border(1.dp, Color(0x403DDC84), CircleShape)
                            .padding(horizontal = 6.dp, vertical = 3.dp))
                }
                Text(p.bio?.takeIf { it.isNotBlank() } ?: "Minecraft Bedrock creator", color = C.muted, fontSize = 10.5.sp,
                    maxLines = 2, minLines = 2, lineHeight = 15.sp, modifier = Modifier.padding(top = 5.dp))
                Box(Modifier.padding(top = 9.dp).fillMaxWidth().height(1.dp).background(Color(0x12FFFFFF)))
                Row(Modifier.padding(top = 8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("${stats.first} Addons", color = C.dim, fontSize = 9.sp)
                    Text("${stats.second} Downloads", color = C.dim, fontSize = 9.sp)
                }
            }
        }
    }
}
