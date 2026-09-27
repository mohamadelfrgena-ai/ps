package com.addonstation.mobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
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
import com.addonstation.mobile.AppState
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ContentItem
import com.addonstation.mobile.ui.components.*
import com.addonstation.mobile.ui.theme.*
import kotlinx.coroutines.delay

@Composable
fun StoreScreen(state: AppState) {
    var type by rememberSaveable { mutableStateOf("addon") }
    var sort by rememberSaveable { mutableStateOf(0) }
    var search by rememberSaveable { mutableStateOf("") }
    var items by remember { mutableStateOf<List<ContentItem>?>(null) }
    val sorts = listOf("Newest" to "created_at", "Most downloaded" to "download_count", "Most viewed" to "view_count", "Top rated" to "rating")

    LaunchedEffect(type, sort, search) {
        delay(250)
        items = null
        items = runCatching { state.repo.content(type, sorts[sort].second, search) }
            .recoverCatching { state.repo.content(type, "created_at", search) }.getOrDefault(emptyList())
    }

    LazyVerticalGrid(
        GridCells.Fixed(2), Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 14.dp, bottom = 100.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(span = { GridItemSpan(2) }) {
            Column {
                Row(Modifier.padding(start = 2.dp, end = 2.dp, top = 2.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("ADDONSTATION STORE", style = mc(9, C.blue).copy(letterSpacing = 1.4.sp))
                        Text("Store", style = mc(22), modifier = Modifier.padding(top = 4.dp, bottom = 3.dp))
                        Text("Addons and worlds ready for your next adventure", color = C.muted, fontSize = 11.5.sp)
                    }
                    Box(
                        Modifier.size(52.dp).shadow(12.dp, RoundedCornerShape(17.dp)).clip(RoundedCornerShape(17.dp))
                            .background(Brush.linearGradient(listOf(C.elevated2, C.surface))).border(1.dp, C.border2, RoundedCornerShape(17.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Text("🛒", fontSize = 25.sp) }
                }
                Segmented(listOf("Addons", "Worlds"), if (type == "addon") 0 else 1) { type = if (it == 0) "addon" else "world" }
                MField(search, { search = it }, "Search the store...", Modifier.fillMaxWidth(), height = 44.dp, trailing = { Text("🔍", fontSize = 15.sp) })
                Row(Modifier.padding(top = 12.dp, bottom = 4.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    sorts.forEachIndexed { i, (l, _) -> Chip(l, i == sort) { sort = i } }
                }
            }
        }
        val list = items
        when {
            list == null -> item(span = { GridItemSpan(2) }) {
                Box(Modifier.fillMaxWidth().padding(50.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = C.green) }
            }
            list.isEmpty() -> item(span = { GridItemSpan(2) }) {
                EmptyState("🛒", "No results", "Try another search or browse a different category.")
            }
            else -> items(list, key = { it.id }) { ContentCard(it) }
        }
    }
}

@Composable
fun Segmented(labels: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp).card(RoundedCornerShape(14.dp)).padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        labels.forEachIndexed { i, l ->
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(11.dp)).background(if (i == selected) C.elevated2 else Color.Transparent)
                    .clickable { onSelect(i) }.padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) { Text(l, color = if (i == selected) C.text else C.muted, fontWeight = FontWeight.Bold, fontSize = 12.5.sp) }
        }
    }
}

@Composable
fun Chip(label: String, active: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (active) Color(0xFF04210F) else C.muted, fontSize = 12.5.sp,
        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        modifier = Modifier.clip(CircleShape)
            .background(if (active) Brush.linearGradient(listOf(Color(0xFF3DDC84), Color(0xFF2FAE67))) else Brush.linearGradient(listOf(C.surface, C.surface)))
            .border(1.dp, if (active) Color.Transparent else C.border2, CircleShape)
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun ContentCard(it: ContentItem) {
    Column(Modifier.card(RoundedCornerShape(15.dp), Brush.linearGradient(listOf(C.elevated, C.surface))).clip(RoundedCornerShape(15.dp))) {
        Img(it.coverUrl, R.drawable.default_banner, Modifier.fillMaxWidth().aspectRatio(16f / 10f))
        Column(Modifier.padding(10.dp)) {
            Text(it.title, color = C.text, fontWeight = FontWeight.Bold, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(it.description ?: "", color = C.muted, fontSize = 10.sp, maxLines = 2, minLines = 2, lineHeight = 14.sp, modifier = Modifier.padding(top = 3.dp))
            Row(Modifier.padding(top = 7.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("⬇ ${it.downloads}", color = C.dim, fontSize = 9.5.sp)
                Text((it.plan ?: "free").uppercase(), style = mc(9, when (it.plan?.lowercase()) { "epic" -> C.purple; "legendary" -> C.gold; else -> C.green }))
            }
        }
    }
}
