package com.addonstation.app.ui

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.addonstation.app.data.ContentItem
import com.addonstation.app.data.Creator
import com.addonstation.app.data.HomeBundle
import com.addonstation.app.needLogin
import com.addonstation.app.openItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object BrowseScreens {
    @Composable
    fun Home(model: AppModel, nav: NavHostController) {
        val palette = LocalPalette.current
        val text = model.text
        var data by remember { mutableStateOf<HomeBundle?>(null) }
        LaunchedEffect(Unit) { data = withContext(Dispatchers.IO) { runCatching { model.repo.home() }.getOrNull() } }
        val bundle = data
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text(text.welcomeGuest, color = palette.muted)
                    Text("AddonStation", color = palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                    profileRank(model)
                }
                Avatar(model.profile?.avatarUrl ?: "/imgs/profiles/default-profile.jpg", 58)
            }
            Spacer(Modifier.height(14.dp))
            if (model.profile == null) {
                InfoCard(text.guestTitle, text.guestBody)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GreenButton(text.signIn, Modifier.weight(1f)) { nav.navigate("login") }
                    GhostButton(text.register, Modifier.weight(1f)) { nav.navigate("register") }
                }
            }
            Box(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(palette.elevated)
                    .clickable { nav.navigate("store/addon") }
                    .padding(14.dp)
            ) { Text(text.search, color = palette.muted) }
            SectionTitle(text.quick)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Quick(text.favorites) { nav.navigate(if (model.signedIn()) "profile/favs" else "login") }
                Quick(text.myDownloads) { nav.navigate(if (model.signedIn()) "profile/downloads" else "login") }
                Quick(text.upgrade) { nav.navigate(if (model.signedIn()) "profile/plan" else "login") }
                Quick(text.device) { nav.navigate("support") }
            }
            SectionTitle(text.plans, text.viewAll) { nav.navigate("checkout/epic") }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanCard("FREE", text.welcomeGuest) { nav.navigate("store/addon") }
                PlanCard("EPIC", "25 LYD") { nav.navigate("checkout/epic") }
                PlanCard("LEGENDARY", "50 LYD") { nav.navigate("checkout/legendary") }
            }
            if (bundle == null) {
                LoadingBox()
            } else {
                SectionTitle(text.featured, text.viewAll) { nav.navigate("store/addon") }
                Rail(bundle.featured) { nav.openItem(it.slug) }
                SectionTitle(text.latest, text.viewAll) { nav.navigate("store/addon") }
                Rail(bundle.latest) { nav.openItem(it.slug) }
                SectionTitle(text.creators, text.viewAll) { nav.navigate("creators") }
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    bundle.creators.forEach { creator -> CreatorCard(creator) { nav.navigate("creator/${creator.id}") } }
                }
                SectionTitle(text.recommended)
                Rail(bundle.top) { nav.openItem(it.slug) }
            }
            Spacer(Modifier.height(12.dp))
            GreenButton(text.becomeCreator, Modifier.fillMaxWidth()) {
                nav.navigate(if (model.profile?.isCreator == true) "studio" else "apply")
            }
            GhostButton(text.exclusives, Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("exclusives") }
            Text("AddonStation Mobile · ${text.version}", color = palette.muted, fontSize = 11.sp, modifier = Modifier.padding(top = 18.dp))
        }
    }

    @Composable
    fun Store(model: AppModel, nav: NavHostController, type: String) {
        val text = model.text
        var sort by remember { mutableStateOf("newest") }
        var query by remember { mutableStateOf("") }
        var results by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        LaunchedEffect(type, sort, query) {
            results = withContext(Dispatchers.IO) { model.repo.listContent(type, sort, query) }
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeading("ADDONSTATION", if (type == "world") text.worlds else text.store, text.guestBody)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GreenButton(text.addons, Modifier.weight(1f)) { nav.navigate("store/addon") }
                GhostButton(text.worlds, Modifier.weight(1f)) { nav.navigate("store/world") }
            }
            androidx.compose.material3.OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp),
                placeholder = { Text(text.search) },
                singleLine = true
            )
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("newest" to text.newest, "downloads" to text.mostDownloads, "rating" to text.topRated).forEach { (id, label) ->
                    val selected = sort == id
                    Text(
                        label,
                        color = if (selected) LocalPalette.current.greenInk else LocalPalette.current.text,
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (selected) LocalPalette.current.green else LocalPalette.current.elevated)
                            .clickable { sort = id }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp
                    )
                }
            }
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = androidx.compose.foundation.lazy.grid.GridCells.Fixed(2),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(bottom = 24.dp)
            ) {
                items(count = results.size) { index ->
                    val item = results[index]
                    GridCard(item) { nav.openItem(item.slug) }
                }
            }
        }
    }

    @Composable
    fun Item(model: AppModel, nav: NavHostController, slug: String) {
        val context = LocalContext.current
        val text = model.text
        val palette = LocalPalette.current
        var item by remember { mutableStateOf<ContentItem?>(null) }
        var similar by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        var favorite by remember { mutableStateOf(false) }
        var price by remember { mutableStateOf(0) }
        LaunchedEffect(slug) {
            val loaded = withContext(Dispatchers.IO) { model.repo.item(slug) }
            item = loaded
            if (loaded != null) {
                similar = withContext(Dispatchers.IO) { model.repo.similar(loaded) }
                favorite = withContext(Dispatchers.IO) { runCatching { model.repo.isFavorite(loaded.id) }.getOrDefault(false) }
                price = withContext(Dispatchers.IO) { model.repo.acPrice(loaded.id) }
            }
        }
        val current = item
        if (current == null) {
            LoadingBox()
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            GhostButton("←") { nav.popBackStack() }
            Box(Modifier.padding(top = 10.dp).clip(RoundedCornerShape(22.dp))) {
                AddonImage(current.coverUrl, Modifier.fillMaxWidth().height(210.dp))
            }
            Row(Modifier.padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(current.title, color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                RankChip(current.plan)
            }
            Text("★ ${"%.1f".format(current.rating)}   ↓ ${current.downloads}   ◉ ${current.views}", color = palette.muted, modifier = Modifier.padding(top = 6.dp))
            if (current.authorName.isNotBlank()) {
                Text(current.authorName, color = palette.green, modifier = Modifier.padding(top = 8.dp).clickable {
                    if (current.createdBy.isNotBlank()) nav.navigate("user/${current.createdBy}")
                })
            }
            InfoCard(text.description, current.description.ifBlank { "—" })
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GreenButton(if (price > 0) "🪙 $price AC" else text.download, Modifier.weight(1f)) {
                    if (!nav.needLogin(model)) {
                        model.io({ model.repo.downloadUrl(current) }) { url ->
                            enqueueDownload(context, url, current.title)
                            model.toast = text.download
                            model.refreshIdentity()
                        }
                    }
                }
                GhostButton(if (favorite) text.saved else text.save) {
                    if (!nav.needLogin(model)) {
                        model.io({ model.repo.toggleFavorite(current.id) }) { favorite = it }
                    }
                }
            }
            SectionTitle(text.similar)
            Rail(similar) { nav.openItem(it.slug) }
        }
    }

    @Composable
    fun Exclusives(model: AppModel, nav: NavHostController) {
        var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        LaunchedEffect(Unit) { items = withContext(Dispatchers.IO) { model.repo.exclusives() } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("ADDONSTATION", model.text.exclusives, "Creator specials selected by AddonStation")
            if (items.isEmpty()) EmptyState(model.text.empty, "") else {
                items.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                        row.forEach { item -> ContentCard(item, Modifier.weight(1f)) { nav.openItem(item.slug) } }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun profileRank(model: AppModel) {
    val profile = model.profile ?: return
    RankChip(profile.plan, profile.role)
}

@Composable
private fun Quick(label: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) { Text(label, color = palette.text, fontSize = 12.sp) }
}

@Composable
private fun PlanCard(title: String, subtitle: String, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(palette.elevated)
            .clickable(onClick = onClick)
            .padding(14.dp)
    ) {
        Text(title, color = palette.gold, fontWeight = FontWeight.Bold)
        Text(subtitle, color = palette.muted, fontSize = 12.sp)
    }
}

@Composable
private fun Rail(items: List<ContentItem>, onClick: (ContentItem) -> Unit) {
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items.forEach { ContentCard(it) { onClick(it) } }
    }
}

@Composable
private fun GridCard(item: ContentItem, onClick: () -> Unit) {
    val palette = LocalPalette.current
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .clickable(onClick = onClick)
    ) {
        AddonImage(item.coverUrl, Modifier.fillMaxWidth().height(110.dp))
        Column(Modifier.padding(8.dp)) {
            Text(item.title, color = palette.text, fontWeight = FontWeight.Bold, maxLines = 1)
            Text("★ ${"%.1f".format(item.rating)}", color = palette.muted, fontSize = 11.sp)
        }
    }
}

@Composable
fun SplashOverlay(onDone: () -> Unit) {
    val palette = LocalPalette.current
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(4200)
        onDone()
    }
    Box(Modifier.fillMaxSize().background(ColorBlack).clickable { onDone() }) {
        AddonImage("/imgs/mobile-splash-sky.jpg", Modifier.fillMaxSize(), androidx.compose.ui.layout.ContentScale.Crop)
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            AddonImage("/imgs/mobile-splash-logo.png", Modifier.size(180.dp), androidx.compose.ui.layout.ContentScale.Fit)
            Text("DREAM IT.", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("BUILD IT.", color = palette.text, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("CREATE IT.", color = palette.blue, fontSize = 28.sp, fontWeight = FontWeight.Black)
            Text("AddonStation", color = palette.green, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 8.dp))
        }
    }
}

private val ColorBlack = androidx.compose.ui.graphics.Color(0xFF020406)

private fun enqueueDownload(context: Context, url: String, title: String) {
    val request = DownloadManager.Request(Uri.parse(url))
        .setTitle(title)
        .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, title.replace(Regex("[^a-zA-Z0-9._-]"), "_"))
    (context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager).enqueue(request)
}
