package com.addonstation.app.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.addonstation.app.core.ItemCatalog
import com.addonstation.app.core.Plans
import com.addonstation.app.core.Profile
import com.addonstation.app.core.SiteAsset
import com.addonstation.app.core.SubscriptionPlan
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.components.Avatar
import com.addonstation.app.ui.components.ConfirmDialog
import com.addonstation.app.ui.components.ContentCard
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.EquippedItemIcon
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.PlanTag
import com.addonstation.app.ui.components.StatBlock
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.components.Username
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.OnResume
import com.addonstation.app.ui.navigation.Routes
import com.addonstation.app.ui.theme.LocalExtra
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// My profile
// ---------------------------------------------------------------------------

class ProfileViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val counts: Map<String, Int> = emptyMap(),
        val content: List<ContentItem> = emptyList(),
        val favorites: List<ContentItem> = emptyList(),
        val downloads: List<ContentItem> = emptyList(),
        val tab: Int = 0,
        val plans: List<SubscriptionPlan> = emptyList(),
        val showPlans: Boolean = false,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun refresh() {
        val uid = app.auth.currentUserId() ?: run {
            _ui.value = Ui(loading = false); return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val counts = app.profiles.counts(uid)
                val content = app.content.byCreator(uid, 100)
                val favs = app.content.myFavorites(uid)
                val dls = app.content.myDownloads(uid)
                val plans = try {
                    app.payments.plans().filter { it.active }
                } catch (_: Exception) { emptyList() }
                _ui.value = _ui.value.copy(loading = false, counts = counts, content = content, favorites = favs, downloads = dls, plans = plans)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun setTab(t: Int) {
        _ui.value = _ui.value.copy(tab = t)
    }

    fun setShowPlans(v: Boolean) {
        _ui.value = _ui.value.copy(showPlans = v)
    }
}

@Composable
fun ProfileScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val pvm: ProfileViewModel = appViewModel { ProfileViewModel(app) }
    val ui by pvm.ui.collectAsState()
    val session by vm.session.collectAsState()
    var showLogout by remember { mutableStateOf(false) }
    var showDelete by remember { mutableStateOf(false) }

    LaunchedEffect(session.userId) { pvm.refresh() }
    OnResume { pvm.refresh() }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.nav_profile), titleIcon = R.drawable.ic_user, vm = vm,
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = {}
        )
        val me = session.profile
        if (session.userId == null || me == null) {
            EmptyBox(stringResource(R.string.common_login_required), Modifier.weight(1f))
            Button(onClick = { nav.navigate(Routes.LOGIN) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.header_account_login))
            }
            return@Column
        }
        if (ui.loading) {
            LoadingBox(Modifier.weight(1f))
            return@Column
        }
        LazyColumn(Modifier.weight(1f)) {
            item {
                // Banner + avatar header
                Box(Modifier.fillMaxWidth().height(210.dp)) {
                    AsImage(
                        model = Constants.resolveAsset(me.bannerUrl) ?: Constants.DEFAULT_BANNER,
                        contentDescription = null,
                        modifier = Modifier.fillMaxWidth().height(150.dp)
                    )
                    Row(Modifier.align(Alignment.BottomStart).padding(start = 16.dp)) {
                        Avatar(url = me.avatarUrl, name = me.username, size = 84.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.padding(top = 44.dp)) {
                            Username(profile = me, fontSizeSp = 19)
                            Text(
                                stringResource(Plans.rankLabelRes(me.displayRank)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.PROFILE_SETTINGS) },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 8.dp)
                    ) { Text(stringResource(R.string.legacy_editprofile)) }
                }
                if (!me.bio.isNullOrBlank()) {
                    Text(me.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                }
                Text(
                    "${stringResource(R.string.legacy_membersince)} ${me.createdAt?.substringBefore("T") ?: ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatBlock((ui.counts["downloads"] ?: 0).toString(), stringResource(R.string.profile_stat_downloads))
                    StatBlock((ui.counts["favorites"] ?: 0).toString(), stringResource(R.string.profile_stat_favorites))
                    StatBlock((ui.counts["messages"] ?: 0).toString(), stringResource(R.string.profile_stat_messages))
                    StatBlock((ui.counts["content"] ?: 0).toString(), stringResource(R.string.profile_stat_content))
                }
                Spacer(Modifier.height(10.dp))
                // Plan card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.profile_myplan), style = MaterialTheme.typography.labelSmall)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                PlanTag(me.plan)
                                if (!me.planExpiresAt.isNullOrBlank()) {
                                    Text(
                                        " • ${stringResource(R.string.profile_expires)} ${me.planExpiresAt.substringBefore("T")}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                            Text(stringResource(R.string.profile_paymentmethods), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Button(onClick = { pvm.setShowPlans(true) }) {
                            Text(stringResource(R.string.profile_buysubscription))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (me.isStaff) {
                    Button(
                        onClick = { nav.navigate(Routes.ADMIN) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) { Text("🛡️ ${stringResource(R.string.profile_admin_panel)}") }
                    Spacer(Modifier.height(4.dp))
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.navigate(Routes.PROFILE_SETTINGS) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.profile_fullsettings))
                    }
                    OutlinedButton(onClick = { nav.navigate(Routes.CREATOR_STUDIO) }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.legacy_creatorstudio))
                    }
                }
                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { showLogout = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.settings_logout))
                    }
                    TextButton(onClick = { showDelete = true }, modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.legacy_deleteaccount), color = MaterialTheme.colorScheme.error)
                    }
                }
            }
            item {
                TabRow(selectedTabIndex = ui.tab) {
                    Tab(selected = ui.tab == 0, onClick = { pvm.setTab(0) }, text = { Text(stringResource(R.string.profile_tab_content)) })
                    Tab(selected = ui.tab == 1, onClick = { pvm.setTab(1) }, text = { Text(stringResource(R.string.profile_tab_favorites)) })
                    Tab(selected = ui.tab == 2, onClick = { pvm.setTab(2) }, text = { Text(stringResource(R.string.profile_tab_downloads)) })
                }
            }
            val list = when (ui.tab) {
                0 -> ui.content
                1 -> ui.favorites
                else -> ui.downloads
            }
            if (list.isEmpty()) {
                item {
                    EmptyBox(
                        when (ui.tab) {
                            0 -> stringResource(R.string.profile_nocontent)
                            1 -> "${stringResource(R.string.profile_nofavorites)}\n${stringResource(R.string.profile_favoritehint)}"
                            else -> stringResource(R.string.profile_nocontent)
                        }
                    )
                }
            } else {
                items(list.size) { i ->
                    val item = list[i]
                    ContentCard(item, { nav.navigate(Routes.item(item.id)) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                }
            }
        }
    }
    if (showLogout) {
        ConfirmDialog(
            title = stringResource(R.string.settings_logout),
            text = stringResource(R.string.profile_logout_confirm),
            onConfirm = {
                showLogout = false
                app.auth.signOutAsync()
                nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
            },
            onDismiss = { showLogout = false }
        )
    }
    if (showDelete) {
        var confirm by remember { mutableStateOf("") }
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text(stringResource(R.string.profile_delete_title)) },
            text = {
                Column {
                    Text(stringResource(R.string.profile_delete_warn))
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(value = confirm, onValueChange = { confirm = it }, label = { Text(stringResource(R.string.profile_delete_confirm)) }, singleLine = true)
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                            try {
                                app.profiles.deleteMyAccount()
                            } catch (_: Exception) { }
                            app.auth.signOutAsync()
                        }
                        showDelete = false
                        Toast.makeText(ctx, ctx.getString(R.string.profile_deleted), Toast.LENGTH_LONG).show()
                        nav.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } }
                    },
                    enabled = confirm == "DELETE"
                ) { Text(stringResource(R.string.profile_delete_title)) }
            },
            dismissButton = { TextButton(onClick = { showDelete = false }) { Text(stringResource(R.string.common_cancel)) } }
        )
    }
    if (ui.showPlans) {
        PlansSheet(
            plans = ui.plans,
            onPick = { plan ->
                pvm.setShowPlans(false)
                nav.navigate(Routes.checkout(plan.id))
            },
            onDismiss = { pvm.setShowPlans(false) }
        )
    }
}

// ---------------------------------------------------------------------------
// Profile settings (avatar / banner / item / privacy)
// ---------------------------------------------------------------------------

class ProfileSettingsViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val avatars: List<SiteAsset> = emptyList(),
        val banners: List<SiteAsset> = emptyList(),
        val username: String = "",
        val bio: String = "",
        val avatar: String? = null,
        val banner: String? = null,
        val item: String? = null,
        val showOnline: Boolean = true,
        val saving: Boolean = false,
        val saved: Boolean = false,
        val error: String? = null
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val assets = try {
                    app.assets.all()
                } catch (_: Exception) { emptyList() }
                val me = app.auth.currentProfile()
                _ui.value = Ui(
                    loading = false,
                    avatars = assets.filter { it.assetType == "avatar" },
                    banners = assets.filter { it.assetType == "banner" },
                    username = me?.username ?: "",
                    bio = me?.bio ?: "",
                    avatar = me?.avatarUrl,
                    banner = me?.bannerUrl,
                    item = me?.equippedItemId,
                    showOnline = me?.showOnlineStatus ?: true
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun pickAvatar(url: String) {
        _ui.value = _ui.value.copy(avatar = url)
    }

    fun pickBanner(url: String) {
        _ui.value = _ui.value.copy(banner = url)
    }

    fun pickItem(id: String?) {
        _ui.value = _ui.value.copy(item = id)
    }

    fun setUsername(v: String) {
        _ui.value = _ui.value.copy(username = v)
    }

    fun setBio(v: String) {
        _ui.value = _ui.value.copy(bio = v)
    }

    fun setOnline(v: Boolean) {
        _ui.value = _ui.value.copy(showOnline = v)
    }

    fun save() {
        val me = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(saving = true, error = null)
            try {
                val s = _ui.value
                app.profiles.update(
                    me,
                    mapOf(
                        "username" to s.username.trim(),
                        "bio" to s.bio.trim(),
                        "avatar_url" to s.avatar,
                        "banner_url" to s.banner,
                        "equipped_item_id" to s.item,
                        "show_online_status" to s.showOnline
                    )
                )
                app.auth.refreshProfile()
                _ui.value = _ui.value.copy(saving = false, saved = true)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(saving = false, error = e.message)
            }
        }
    }

    fun consumeSaved() {
        _ui.value = _ui.value.copy(saved = false)
    }
}

@Composable
fun ProfileSettingsScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val svm: ProfileSettingsViewModel = appViewModel { ProfileSettingsViewModel(app) }
    val ui by svm.ui.collectAsState()
    val session by vm.session.collectAsState()
    val me = session.profile
    if (ui.saved) {
        LaunchedEffect(Unit) {
            Toast.makeText(ctx, ctx.getString(R.string.settings_updated), Toast.LENGTH_SHORT).show()
            svm.consumeSaved()
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.settings_profilesettings), titleIcon = R.drawable.ic_user,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        if (ui.loading) {
            LoadingBox(Modifier.weight(1f))
            return@Column
        }
        val myPlan = me?.plan ?: "free"
        val isAdmin = me?.isAdmin == true
        val avatarUnlocked = myPlan == "epic" || myPlan == "legendary" || isAdmin
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(16.dp)) {
            item {
                OutlinedTextField(value = ui.username, onValueChange = { svm.setUsername(it) }, label = { Text(stringResource(R.string.auth_username)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(value = ui.bio, onValueChange = { svm.setBio(it) }, label = { Text(stringResource(R.string.legacy_bio)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                Spacer(Modifier.height(14.dp))
                Text(stringResource(R.string.settings_avatar), fontWeight = FontWeight.Bold)
                Text(
                    if (avatarUnlocked) stringResource(R.string.auth_register_lead) else "🔒 Epic / Legendary",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
            }
            item {
                LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(190.dp), userScrollEnabled = false) {
                    item {
                        AvatarPick(
                            url = "/imgs/profiles/onefree.png", selected = ui.avatar == "/imgs/profiles/onefree.png",
                            locked = false, onPick = { svm.pickAvatar("/imgs/profiles/onefree.png") }
                        )
                    }
                    items(ui.avatars) { a ->
                        val locked = !Plans.atLeast(myPlan, a.requiredPlan) && !isAdmin
                        AvatarPick(url = a.fileUrl, selected = ui.avatar == a.fileUrl, locked = locked, onPick = {
                            if (!locked) svm.pickAvatar(a.fileUrl)
                        })
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_banner), fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.settings_choosebanner), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(6.dp))
            }
            item {
                LazyVerticalGrid(columns = GridCells.Fixed(2), modifier = Modifier.height(150.dp), userScrollEnabled = false) {
                    item {
                        BannerPick(url = "/imgs/profiles/default-free-banner.jpg", selected = ui.avatar == "/imgs/profiles/default-free-banner.jpg", onPick = { svm.pickBanner("/imgs/profiles/default-free-banner.jpg") })
                    }
                    items(ui.banners) { b ->
                        BannerPick(url = b.fileUrl, selected = ui.banner == b.fileUrl, onPick = { svm.pickBanner(b.fileUrl) })
                    }
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.settings_equippedchatitem), fontWeight = FontWeight.Bold)
                if (!(myPlan == "legendary" || isAdmin)) {
                    Text("🔒 Legendary 👑", style = MaterialTheme.typography.bodySmall, color = LocalExtra.current.gold)
                }
                Spacer(Modifier.height(6.dp))
            }
            item {
                val items = ItemCatalog.items
                LazyVerticalGrid(columns = GridCells.Fixed(5), modifier = Modifier.height(320.dp), userScrollEnabled = false) {
                    items(items) { def ->
                        val locked = when (def.requiredPlan) {
                            "free" -> false
                            "legendary" -> !(myPlan == "legendary" || isAdmin)
                            "admin" -> !isAdmin
                            else -> true
                        }
                        Box(
                            modifier = Modifier
                                .padding(3.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
                                .clickable(enabled = !locked) { svm.pickItem(if (def.id == "none") null else def.id) }
                                .padding(6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                if (def.fileName == null) {
                                    Text("🚫", fontSize = MaterialTheme.typography.headlineSmall.fontSize)
                                } else {
                                    EquippedItemIcon(itemId = def.id, size = 30.dp)
                                }
                                if (locked) Text("🔒", style = MaterialTheme.typography.labelSmall)
                                else if ((ui.item ?: "none") == def.id) Text("✓", color = LocalExtra.current.emerald, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            item {
                Spacer(Modifier.height(10.dp))
                Text(stringResource(R.string.settings_privacysection), fontWeight = FontWeight.Bold)
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${stringResource(R.string.settings_showstatus)} (${stringResource(R.string.settings_onlinenow)})", modifier = Modifier.weight(1f))
                    Switch(checked = ui.showOnline, onCheckedChange = { svm.setOnline(it) })
                }
                Spacer(Modifier.height(8.dp))
                ui.error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = { svm.save() }, enabled = !ui.saving && ui.username.trim().length >= 3, modifier = Modifier.fillMaxWidth()) {
                    Text(if (ui.saving) stringResource(R.string.auth_saving) else stringResource(R.string.common_save))
                }
            }
        }
    }
}

@Composable
private fun AvatarPick(url: String, selected: Boolean, locked: Boolean, onPick: () -> Unit) {
    Box(
        modifier = Modifier.padding(4.dp).clip(CircleShape)
            .clickable(enabled = !locked) { onPick() }
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Avatar(url = url, name = "?", size = 56.dp)
        if (locked) Text("🔒", modifier = Modifier.align(Alignment.BottomEnd))
        if (selected) Text("✓", color = LocalExtra.current.emerald, modifier = Modifier.align(Alignment.TopEnd))
    }
}

@Composable
private fun BannerPick(url: String, selected: Boolean, onPick: () -> Unit) {
    Surface(
        shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
        modifier = Modifier.padding(4.dp).clickable { onPick() }
    ) {
        Box {
            AsImage(model = Constants.resolveAsset(url), contentDescription = null, modifier = Modifier.fillMaxWidth().height(64.dp))
            if (selected) Text("✓", color = LocalExtra.current.emerald, modifier = Modifier.align(Alignment.TopEnd).padding(6.dp))
        }
    }
}

// ---------------------------------------------------------------------------
// Public user profile
// ---------------------------------------------------------------------------

class UserViewModel(private val app: AppContainer, private val id: String) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val profile: Profile? = null,
        val content: List<ContentItem> = emptyList(),
        val counts: Map<String, Int> = emptyMap(),
        val friendStatus: String? = null,
        val error: String? = null
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
                val p = app.profiles.get(id)
                val content = app.content.byCreator(id, 60)
                val counts = app.profiles.counts(id)
                val me = app.auth.currentUserId()
                val fs = if (me != null && me != id) {
                    try {
                        app.profiles.friendStatus(me, id)
                    } catch (_: Exception) { null }
                } else null
                _ui.value = Ui(false, p, content, counts, fs)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun sendRequest() {
        val me = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            try {
                app.profiles.sendFriendRequest(me, id)
                _ui.value = _ui.value.copy(friendStatus = "pending")
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun UserScreen(nav: NavHostController, vm: AppViewModel, id: String) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val uvm: UserViewModel = appViewModel { UserViewModel(app, id) }
    val ui by uvm.ui.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = ui.profile?.username ?: stringResource(R.string.nav_profile),
            titleIcon = R.drawable.ic_user, vm = vm, showBack = true,
            onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null || ui.profile == null -> ErrorBox(ui.error ?: stringResource(R.string.legacy_usernotfound), { uvm.refresh() }, Modifier.weight(1f))
            else -> {
                val p = ui.profile!!
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Box(Modifier.fillMaxWidth().height(200.dp)) {
                            AsImage(
                                model = Constants.resolveAsset(p.bannerUrl) ?: Constants.DEFAULT_BANNER,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth().height(140.dp)
                            )
                            Row(Modifier.align(Alignment.BottomStart).padding(start = 16.dp)) {
                                Avatar(url = p.avatarUrl, name = p.username, size = 76.dp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.padding(top = 36.dp)) {
                                    Username(profile = p, fontSizeSp = 18)
                                    Text(stringResource(Plans.rankLabelRes(p.displayRank)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (!p.bio.isNullOrBlank()) {
                            Text(p.bio, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp))
                        }
                        Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            StatBlock((ui.counts["content"] ?: 0).toString(), stringResource(R.string.profile_stat_content))
                            StatBlock((ui.counts["messages"] ?: 0).toString(), stringResource(R.string.profile_stat_messages))
                        }
                        val me = app.auth.currentUserId()
                        if (me != null && me != p.id) {
                            Button(
                                onClick = {
                                    uvm.sendRequest()
                                    Toast.makeText(ctx, ctx.getString(R.string.fr_sent), Toast.LENGTH_SHORT).show()
                                },
                                enabled = ui.friendStatus != "pending" && ui.friendStatus != "accepted",
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) {
                                Text(
                                    when (ui.friendStatus) {
                                        "pending" -> stringResource(R.string.fr_pending)
                                        "accepted" -> stringResource(R.string.fr_accepted)
                                        else -> stringResource(R.string.fr_add)
                                    }
                                )
                            }
                        }
                        if (p.role == "creator") {
                            OutlinedButton(
                                onClick = { nav.navigate(Routes.creator(p.id)) },
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                            ) { Text(stringResource(R.string.cr_content)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (ui.content.isEmpty()) {
                        item { EmptyBox(stringResource(R.string.profile_nocontent)) }
                    } else {
                        items(ui.content.size) { i ->
                            val item = ui.content[i]
                            ContentCard(item, { nav.navigate(Routes.item(item.id)) }, modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp))
                        }
                    }
                }
            }
        }
    }
}
