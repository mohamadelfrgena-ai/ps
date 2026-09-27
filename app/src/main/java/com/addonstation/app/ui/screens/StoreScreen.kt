package com.addonstation.app.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import com.addonstation.app.core.Constants
import com.addonstation.app.core.ContentItem
import com.addonstation.app.core.Formats
import com.addonstation.app.core.Plans
import com.addonstation.app.core.downloadViaManager
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.components.Avatar
import com.addonstation.app.ui.components.ConnectDeviceDialog
import com.addonstation.app.ui.components.ContentCard
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.PlanTag
import com.addonstation.app.ui.components.RatingStars
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.components.Username
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import com.addonstation.app.ui.theme.LocalExtra
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Store
// ---------------------------------------------------------------------------

class StoreViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val items: List<ContentItem> = emptyList(),
        val query: String = "",
        val type: String = "all",
        val plan: String = "all",
        val sort: String = "created_at"
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()
    private var job: Job? = null

    init {
        refresh()
    }

    fun setQuery(q: String) {
        _ui.value = _ui.value.copy(query = q)
        job?.cancel()
        job = viewModelScope.launch {
            delay(450)
            refresh()
        }
    }

    fun setType(t: String) {
        _ui.value = _ui.value.copy(type = t)
        refresh()
    }

    fun setPlan(p: String) {
        _ui.value = _ui.value.copy(plan = p)
        refresh()
    }

    fun setSort(s: String) {
        _ui.value = _ui.value.copy(sort = s)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val s = _ui.value
            _ui.value = s.copy(loading = true, error = null)
            try {
                val q = s.query.trim()
                var list = if (q.length >= 2) {
                    app.content.search(q, s.type.takeIf { it != "all" })
                } else if (s.type == "all") {
                    app.content.latest(60)
                } else {
                    app.content.byType(s.type, s.sort, 60)
                }
                if (s.plan != "all") list = list.filter { it.plan.equals(s.plan, true) }
                list = when (s.sort) {
                    "download_count" -> list.sortedByDescending { it.downloadCount }
                    "view_count" -> list.sortedByDescending { it.viewCount }
                    "rating_average" -> list.sortedByDescending { it.ratingAverage ?: 0.0 }
                    else -> list.sortedByDescending { it.createdAt ?: "" }
                }
                _ui.value = _ui.value.copy(loading = false, items = list)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Error")
            }
        }
    }
}

@Composable
fun StoreScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val svm: StoreViewModel = appViewModel { StoreViewModel(app) }
    val ui by svm.ui.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.nav_store), titleIcon = R.drawable.ic_store, vm = vm,
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        OutlinedTextField(
            value = ui.query,
            onValueChange = { svm.setQuery(it) },
            placeholder = { Text(stringResource(R.string.store_search_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        )
        TabRow(selectedTabIndex = listOf("all", "addon", "world").indexOf(ui.type)) {
            Tab(selected = ui.type == "all", onClick = { svm.setType("all") }, text = { Text(stringResource(R.string.store_tab_all)) })
            Tab(selected = ui.type == "addon", onClick = { svm.setType("addon") }, text = { Text(stringResource(R.string.store_tab_addons)) })
            Tab(selected = ui.type == "world", onClick = { svm.setType("world") }, text = { Text(stringResource(R.string.store_tab_worlds)) })
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "all" to stringResource(R.string.addons_filters_all),
                "free" to stringResource(R.string.addons_filters_free),
                "epic" to stringResource(R.string.addons_filters_normal),
                "legendary" to stringResource(R.string.addons_filters_vip)
            ).forEach { (v, label) ->
                FilterChip(selected = ui.plan == v, onClick = { svm.setPlan(v) }, label = { Text(label) })
            }
        }
        Row(modifier = Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(
                "created_at" to stringResource(R.string.store_sort_latest),
                "download_count" to stringResource(R.string.store_sort_downloads),
                "view_count" to stringResource(R.string.store_sort_views),
                "rating_average" to stringResource(R.string.store_sort_rating)
            ).forEach { (v, label) ->
                FilterChip(selected = ui.sort == v, onClick = { svm.setSort(v) }, label = { Text(label) })
            }
        }
        Spacer(Modifier.height(6.dp))
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { svm.refresh() }, Modifier.weight(1f))
            ui.items.isEmpty() -> EmptyBox(stringResource(R.string.store_no_results), Modifier.weight(1f))
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                items(ui.items) { item ->
                    ContentCard(item, onClick = { nav.navigate(Routes.item(item.id)) })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Item detail
// ---------------------------------------------------------------------------

class ItemDetailViewModel(private val app: AppContainer, private val id: Long) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val error: String? = null,
        val item: ContentItem? = null,
        val isFavorite: Boolean = false,
        val myRating: Int = 0,
        val acPrice: Long? = null,
        val unlocked: Boolean = false,
        val related: List<ContentItem> = emptyList(),
        val busy: Boolean = false,
        val toast: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val item = app.content.byId(id) ?: throw Exception("Not found")
                app.content.recordView(item.id)
                val uid = app.auth.currentUserId()
                val fav = uid?.let { app.content.isFavorite(it, item.id) } ?: false
                val rating = uid?.let { app.content.myRating(it, item.id) } ?: 0
                val price = try {
                    app.wallet.contentPrice(item.id)
                } catch (_: Exception) { null }
                val related = app.content.related(item)
                _ui.value = Ui(false, null, item, fav, rating, price, false, related)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message ?: "Error")
            }
        }
    }

    fun toggleFavorite() {
        val uid = app.auth.currentUserId() ?: return
        val item = _ui.value.item ?: return
        viewModelScope.launch {
            try {
                val now = app.content.toggleFavorite(uid, item.id)
                _ui.value = _ui.value.copy(isFavorite = now)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun rate(stars: Int) {
        val uid = app.auth.currentUserId() ?: return
        val item = _ui.value.item ?: return
        viewModelScope.launch {
            try {
                app.content.rate(uid, item.id, stars)
                _ui.value = _ui.value.copy(myRating = stars, toast = "★")
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(toast = e.message)
            }
        }
    }

    fun unlockWithAc() {
        val item = _ui.value.item ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try {
                app.wallet.unlockContent(item.id)
                _ui.value = _ui.value.copy(busy = false, unlocked = true)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, toast = e.message)
            }
        }
    }

    fun consumeToast() {
        _ui.value = _ui.value.copy(toast = null)
    }
}

@Composable
fun ItemDetailScreen(nav: NavHostController, vm: AppViewModel, id: Long) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val ivm: ItemDetailViewModel = appViewModel { ItemDetailViewModel(app, id) }
    val ui by ivm.ui.collectAsState()
    val session by vm.session.collectAsState()
    var showConnect by remember { mutableStateOf(false) }
    var deviceIp by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        deviceIp = try {
            app.session.deviceIp.first()
        } catch (_: Exception) { null }
    }
    ui.toast?.let {
        LaunchedEffect(it) {
            Toast.makeText(ctx, it, Toast.LENGTH_SHORT).show()
            ivm.consumeToast()
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = ui.item?.title ?: stringResource(R.string.nav_store),
            titleIcon = R.drawable.ic_store, vm = vm, showBack = true,
            onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { ivm.refresh() }, Modifier.weight(1f))
            ui.item != null -> {
                val item = ui.item!!
                val myPlan = session.profile?.plan ?: "free"
                val planOk = Plans.atLeast(myPlan, item.plan) || ui.unlocked
                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        AsImage(
                            model = Constants.resolveAsset(item.coverUrl)
                                ?: if (item.contentType == "world") Constants.WORLDS_PLACEHOLDER else Constants.ADDONS_PLACEHOLDER,
                            contentDescription = item.title,
                            modifier = Modifier.fillMaxWidth().height(220.dp)
                        )
                    }
                    item {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                Text(item.title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
                                Text(
                                    if (ui.isFavorite) "★" else "☆",
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(4.dp)
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                PlanTag(item.plan)
                                if (item.isExclusive) PlanTag("legendary")
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                Text(stringResource(R.string.item_downloads, Formats.count(item.downloadCount)), style = MaterialTheme.typography.bodySmall)
                                Text(stringResource(R.string.item_views, Formats.count(item.viewCount)), style = MaterialTheme.typography.bodySmall)
                                if ((item.ratingAverage ?: 0.0) > 0) {
                                    Text("★ ${String.format("%.1f", item.ratingAverage)}", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.gold)
                                }
                            }
                            if (item.profiles != null) {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                                    Text("${stringResource(R.string.detail_by)} ", style = MaterialTheme.typography.bodySmall)
                                    Avatar(url = item.profiles.avatarUrl, name = item.profiles.username, size = 22.dp)
                                    Spacer(Modifier.width(4.dp))
                                    Username(profile = item.profiles, showItem = true, fontSizeSp = 13)
                                }
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(stringResource(R.string.detail_about), fontWeight = FontWeight.Bold)
                            Text(item.description?.takeIf { it.isNotBlank() } ?: stringResource(R.string.legacy_nodescription), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "${stringResource(R.string.legacy_version)}: ${item.version ?: "1.0.0"}  •  ${item.category ?: "general"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.height(14.dp))
                            // Download / gates
                            if (session.userId == null) {
                                Button(onClick = { nav.navigate(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth()) {
                                    Text(stringResource(R.string.common_login_required))
                                }
                            } else if (!planOk) {
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                                    Column(Modifier.padding(14.dp)) {
                                        Text(
                                            stringResource(R.string.item_plan_locked, stringResource(Plans.planLabelRes(item.plan))),
                                            fontWeight = FontWeight.Bold
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Button(onClick = { nav.navigate(Routes.ORDERS) }) {
                                                Text(stringResource(R.string.profile_buysubscription))
                                            }
                                            if (ui.acPrice != null && ui.acPrice!! > 0) {
                                                OutlinedButton(onClick = { ivm.unlockWithAc() }, enabled = !ui.busy) {
                                                    Text("${stringResource(R.string.ac_unlock)} • ${stringResource(R.string.item_price_ac, ui.acPrice!!)}")
                                                }
                                            }
                                        }
                                    }
                                }
                            } else {
                                Button(
                                    onClick = {
                                        if (deviceIp.isNullOrBlank()) {
                                            showConnect = true
                                            return@Button
                                        }
                                        val it2 = ui.item ?: return@Button
                                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                            try {
                                                val url = app.content.resolveDownloadUrl(it2)
                                                if (url == null) {
                                                    launch(kotlinx.coroutines.Dispatchers.Main) {
                                                        Toast.makeText(ctx, ctx.getString(R.string.item_no_file), Toast.LENGTH_SHORT).show()
                                                    }
                                                    return@launch
                                                }
                                                val ext = url.substringAfterLast(".", "mcaddon").substringBefore("?").take(10)
                                                downloadViaManager(ctx, url, "${it2.slug}.$ext")
                                                app.content.recordDownload(it2.id)
                                                launch(kotlinx.coroutines.Dispatchers.Main) {
                                                    Toast.makeText(ctx, ctx.getString(R.string.item_download_started), Toast.LENGTH_SHORT).show()
                                                }
                                            } catch (e: Exception) {
                                                launch(kotlinx.coroutines.Dispatchers.Main) {
                                                    Toast.makeText(ctx, e.message ?: "Error", Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (item.contentType == "world") stringResource(R.string.detail_download) else stringResource(R.string.detail_download))
                                }
                                if (deviceIp.isNullOrBlank()) {
                                    Text(
                                        stringResource(R.string.detail_error_message),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = LocalExtra.current.gold,
                                        modifier = Modifier.padding(top = 6.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Row {
                                OutlinedButton(onClick = { ivm.toggleFavorite() }, modifier = Modifier.weight(1f)) {
                                    Text(if (ui.isFavorite) "★ ${stringResource(R.string.profile_myfavorites)}" else "☆ ${stringResource(R.string.profile_myfavorites)}")
                                }
                            }
                            Spacer(Modifier.height(14.dp))
                            Text(stringResource(R.string.item_your_rating), fontWeight = FontWeight.Bold)
                            RatingStars(ui.myRating, onRate = { ivm.rate(it) })
                            if (ui.related.isNotEmpty()) {
                                Spacer(Modifier.height(14.dp))
                                Text(stringResource(R.string.item_related), fontWeight = FontWeight.Bold)
                                Spacer(Modifier.height(8.dp))
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    items(ui.related) { rel ->
                                        ContentCard(rel, { nav.navigate(Routes.item(rel.id)) }, modifier = Modifier.width(200.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (showConnect) {
        ConnectDeviceDialog(
            onSaved = {
                showConnect = false
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    deviceIp = try {
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
}
