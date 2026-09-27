package com.addonstation.app.ui.screens

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.addonstation.app.AppContainer
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.appViewModel
import com.addonstation.app.core.ContentItem
import com.addonstation.app.core.CreatorApplication
import com.addonstation.app.core.CreatorApplicationMessage
import com.addonstation.app.core.CreatorEarning
import com.addonstation.app.core.CreatorSubmission
import com.addonstation.app.core.CreatorTipOrder
import com.addonstation.app.core.CreatorWallet
import com.addonstation.app.core.PaymentMethodSetting
import com.addonstation.app.core.Profile
import com.addonstation.app.ui.components.Avatar
import com.addonstation.app.ui.components.ContentCard
import com.addonstation.app.ui.components.EmptyBox
import com.addonstation.app.ui.components.ErrorBox
import com.addonstation.app.ui.components.LoadingBox
import com.addonstation.app.ui.components.TopBar
import com.addonstation.app.ui.components.Username
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------
// Creators list
// ---------------------------------------------------------------------------

class CreatorsViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val featured: List<Profile> = emptyList(),
        val creators: List<Profile> = emptyList(),
        val following: Set<String> = emptySet(),
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
                val feat = app.creators.featured()
                val all = app.creators.creators(100)
                val byId = all.associateBy { it.id }
                val featProfiles = feat.mapNotNull { byId[it.creatorId] ?: it.profiles }
                val me = app.auth.currentUserId()
                val following = mutableSetOf<String>()
                if (me != null) {
                    (featProfiles + all).distinctBy { it.id }.take(60).forEach { c ->
                        try {
                            if (app.creators.isFollowing(me, c.id)) following += c.id
                        } catch (_: Exception) { }
                    }
                }
                _ui.value = Ui(false, featProfiles, all, following)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun toggleFollow(creatorId: String) {
        val me = app.auth.currentUserId() ?: return
        val now = !_ui.value.following.contains(creatorId)
        viewModelScope.launch {
            try {
                app.creators.setFollowing(me, creatorId, now)
                _ui.value = _ui.value.copy(
                    following = if (now) _ui.value.following + creatorId else _ui.value.following - creatorId
                )
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun CreatorsScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val cvm: CreatorsViewModel = appViewModel { CreatorsViewModel(app) }
    val ui by cvm.ui.collectAsState()
    val session by vm.session.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.creators_title),
            titleIcon = R.drawable.ic_creators, vm = vm, showBack = true,
            onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { cvm.refresh() }, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f)) {
                if (ui.featured.isNotEmpty()) {
                    item {
                        Text(stringResource(R.string.cr_featured), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
                    }
                    item {
                        LazyRow(modifier = Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(ui.featured, key = { it.id }) { c ->
                                CreatorCard(c, ui.following.contains(c.id), session.userId != null,
                                    onOpen = { nav.navigate(Routes.creator(c.id)) },
                                    onFollow = { cvm.toggleFollow(c.id) })
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.cr_all), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
                }
                if (ui.creators.isEmpty()) {
                    item { EmptyBox(stringResource(R.string.cr_no_creators)) }
                } else {
                    items(ui.creators, key = { it.id }) { c ->
                        CreatorRow(c, ui.following.contains(c.id), session.userId != null,
                            onOpen = { nav.navigate(Routes.creator(c.id)) },
                            onFollow = { cvm.toggleFollow(c.id) })
                    }
                }
                item {
                    OutlinedButton(
                        onClick = { nav.navigate(Routes.CREATOR_APPLICATION) },
                        modifier = Modifier.fillMaxWidth().padding(16.dp)
                    ) { Text(stringResource(R.string.legacy_becomecreator)) }
                }
            }
        }
    }
}

@Composable
private fun CreatorCard(c: Profile, following: Boolean, canFollow: Boolean, onOpen: () -> Unit, onFollow: () -> Unit) {
    Card(
        onClick = onOpen,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.width(170.dp)
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Avatar(url = c.avatarUrl, name = c.username, size = 60.dp)
            Spacer(Modifier.height(6.dp))
            Username(profile = c, fontSizeSp = 14)
            Spacer(Modifier.height(6.dp))
            if (canFollow) {
                OutlinedButton(onClick = onFollow) {
                    Text(if (following) stringResource(R.string.cr_unfollow) else stringResource(R.string.cr_follow))
                }
            }
        }
    }
}

@Composable
private fun CreatorRow(c: Profile, following: Boolean, canFollow: Boolean, onOpen: () -> Unit, onFollow: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onOpen() }.padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Avatar(url = c.avatarUrl, name = c.username, size = 46.dp)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Username(profile = c, fontSizeSp = 15)
            if (!c.bio.isNullOrBlank()) {
                Text(c.bio, style = MaterialTheme.typography.bodySmall, maxLines = 1, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (canFollow) {
            OutlinedButton(onClick = onFollow) {
                Text(if (following) stringResource(R.string.cr_unfollow) else stringResource(R.string.cr_follow))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Creator public profile
// ---------------------------------------------------------------------------

class CreatorProfileViewModel(private val app: AppContainer, private val id: String) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val profile: Profile? = null,
        val content: List<ContentItem> = emptyList(),
        val followers: Long = 0,
        val following: Boolean = false,
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
                val followers = app.creators.followersCount(id)
                val me = app.auth.currentUserId()
                val following = me?.let { app.creators.isFollowing(it, id) } ?: false
                _ui.value = Ui(false, p, content, followers, following)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun toggleFollow() {
        val me = app.auth.currentUserId() ?: return
        val now = !_ui.value.following
        viewModelScope.launch {
            try {
                app.creators.setFollowing(me, id, now)
                _ui.value = _ui.value.copy(following = now, followers = (_ui.value.followers + if (now) 1 else -1).coerceAtLeast(0))
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun CreatorProfileScreen(nav: NavHostController, vm: AppViewModel, id: String) {
    val app = LocalApp.current
    val cvm: CreatorProfileViewModel = appViewModel { CreatorProfileViewModel(app, id) }
    val ui by cvm.ui.collectAsState()
    val session by vm.session.collectAsState()
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = ui.profile?.username ?: stringResource(R.string.creators_title), titleIcon = R.drawable.ic_creators,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null || ui.profile == null -> ErrorBox(ui.error ?: "Error", { cvm.refresh() }, Modifier.weight(1f))
            else -> {
                val p = ui.profile!!
                LazyColumn(Modifier.weight(1f)) {
                    item {
                        Column(Modifier.fillMaxWidth().padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Avatar(url = p.avatarUrl, name = p.username, size = 88.dp)
                            Spacer(Modifier.height(8.dp))
                            Username(profile = p, fontSizeSp = 20)
                            if (!p.bio.isNullOrBlank()) Text(p.bio, style = MaterialTheme.typography.bodyMedium)
                            Text("${ui.followers} ${stringResource(R.string.cr_followers)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (session.userId != null && session.userId != p.id) {
                                    OutlinedButton(onClick = { cvm.toggleFollow() }) {
                                        Text(if (ui.following) stringResource(R.string.cr_unfollow) else stringResource(R.string.cr_follow))
                                    }
                                    Button(onClick = { nav.navigate(Routes.tip(p.id)) }) {
                                        Text(stringResource(R.string.cr_tip))
                                    }
                                }
                            }
                        }
                        Text(stringResource(R.string.cr_content), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 16.dp))
                    }
                    if (ui.content.isEmpty()) {
                        item { EmptyBox(stringResource(R.string.legacy_nopublished)) }
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

// ---------------------------------------------------------------------------
// Creator studio (submissions + earnings + upload)
// ---------------------------------------------------------------------------

class CreatorStudioViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val submissions: List<CreatorSubmission> = emptyList(),
        val tips: List<CreatorTipOrder> = emptyList(),
        val earnings: List<CreatorEarning> = emptyList(),
        val wallet: CreatorWallet? = null,
        val error: String? = null,
        val uploading: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val me = app.auth.currentUserId() ?: run {
            _ui.value = Ui(loading = false); return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                _ui.value = Ui(
                    false,
                    app.creators.mySubmissions(me),
                    app.creators.myTips(me),
                    app.creators.myEarnings(me),
                    try {
                        app.creators.myWallet(me)
                    } catch (_: Exception) { null }
                )
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun submit(
        type: String, title: String, desc: String, category: String, version: String, video: String?,
        fileBytes: ByteArray?, fileName: String?, previewBytes: ByteArray?, previewMime: String?,
        onOk: () -> Unit, onErr: (String) -> Unit
    ) {
        val me = app.auth.currentUserId() ?: return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(uploading = true)
            try {
                val ts = System.currentTimeMillis()
                val filePath = if (fileBytes != null) {
                    val clean = (fileName ?: "content.zip").replace(Regex("[^A-Za-z0-9._-]"), "_")
                    val path = "$me/${ts}_$clean"
                    val mime = when (clean.substringAfterLast(".", "").lowercase()) {
                        "zip" -> "application/zip"
                        "mcaddon", "mcpack" -> "application/zip"
                        "mcworld", "mctemplate" -> "application/octet-stream"
                        else -> "application/octet-stream"
                    }
                    app.backend.storageUpload("content-files", path, fileBytes, mime)
                    path
                } else null
                val previewUrl = if (previewBytes != null) {
                    val path = "$me/${ts}_preview.jpg"
                    app.backend.storageUpload("creator-media", path, previewBytes, previewMime ?: "image/jpeg")
                    app.backend.storagePublicUrl("creator-media", path)
                } else null
                app.creators.submitContent(
                    mapOf(
                        "creator_id" to me,
                        "content_type" to type,
                        "title" to title,
                        "description" to desc,
                        "category" to category.ifBlank { "general" },
                        "version" to version.ifBlank { "1.0.0" },
                        "preview_url" to previewUrl,
                        "file_path" to filePath,
                        "video_url" to video?.takeIf { it.isNotBlank() },
                        "requested_plan" to "free"
                    )
                )
                _ui.value = _ui.value.copy(uploading = false)
                refresh()
                onOk()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(uploading = false)
                onErr(e.message ?: "Error")
            }
        }
    }

    fun decideTip(tipId: Long, approve: Boolean) {
        viewModelScope.launch {
            try {
                if (approve) app.creators.approveTip(tipId) else app.creators.rejectTip(tipId)
                refresh()
            } catch (_: Exception) { }
        }
    }
}

@Composable
fun CreatorStudioScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val svm: CreatorStudioViewModel = appViewModel { CreatorStudioViewModel(app) }
    val ui by svm.ui.collectAsState()
    val session by vm.session.collectAsState()
    var showForm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.legacy_creatorstudio), titleIcon = R.drawable.ic_creators,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        if (session.userId == null) {
            EmptyBox(stringResource(R.string.common_login_required), Modifier.weight(1f))
            return@Column
        }
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { svm.refresh() }, Modifier.weight(1f))
            else -> LazyColumn(Modifier.weight(1f)) {
                item {
                    Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.studio_earnings), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Button(onClick = { showForm = !showForm }) { Text(stringResource(R.string.studio_new)) }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)
                    ) {
                        Row(Modifier.padding(14.dp)) {
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.legacy_availableearnings), style = MaterialTheme.typography.labelLarge)
                                Text("${ui.wallet?.balance ?: 0.0} LYD", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
                            }
                            Column(Modifier.weight(1f)) {
                                Text(stringResource(R.string.legacy_lifetimeearned), style = MaterialTheme.typography.labelLarge)
                                Text("${ui.earnings.sumOf { it.amount }} LYD", style = MaterialTheme.typography.headlineSmall)
                            }
                        }
                    }
                    if (showForm) {
                        SubmissionForm(
                            uploading = ui.uploading,
                            onSubmit = { f -> svm.submit(f.type, f.title, f.desc, f.category, f.version, f.video, f.fileBytes, f.fileName, f.previewBytes, f.previewMime, { showForm = false; Toast.makeText(ctx, ctx.getString(R.string.studio_submitted), Toast.LENGTH_LONG).show() }, { Toast.makeText(ctx, it, Toast.LENGTH_LONG).show() }) }
                        )
                    }
                    Text(stringResource(R.string.studio_my), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
                }
                if (ui.submissions.isEmpty()) {
                    item { EmptyBox(stringResource(R.string.legacy_nocontent)) }
                } else {
                    items(ui.submissions) { s ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Row {
                                    Text(s.title, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                                    Text(
                                        when (s.status) {
                                            "published" -> stringResource(R.string.studio_status_published)
                                            "approved" -> stringResource(R.string.studio_status_approved)
                                            "rejected" -> stringResource(R.string.studio_status_rejected)
                                            else -> stringResource(R.string.studio_status_pending)
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Text("${s.contentType} • v${s.version ?: "1.0.0"}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                item {
                    Text(stringResource(R.string.studio_tips), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(16.dp, 8.dp))
                }
                if (ui.tips.isEmpty()) {
                    item { EmptyBox(stringResource(R.string.studio_no_tips)) }
                } else {
                    items(ui.tips) { t ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Column(Modifier.padding(12.dp)) {
                                Text("${t.amountLyd} LYD • ${t.status}", fontWeight = FontWeight.Bold)
                                if (!t.message.isNullOrBlank()) Text(t.message, style = MaterialTheme.typography.bodySmall)
                                if (t.status == "pending") {
                                    Row {
                                        Button(onClick = { svm.decideTip(t.id, true) }) { Text("✓") }
                                        Spacer(Modifier.width(8.dp))
                                        OutlinedButton(onClick = { svm.decideTip(t.id, false) }) { Text("✕") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SubmissionFormData(
    val type: String,
    val title: String,
    val desc: String,
    val category: String,
    val version: String,
    val video: String?,
    val fileBytes: ByteArray?,
    val fileName: String?,
    val previewBytes: ByteArray?,
    val previewMime: String?
)

@Composable
private fun SubmissionForm(uploading: Boolean, onSubmit: (SubmissionFormData) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var type by remember { mutableStateOf("addon") }
    var title by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("general") }
    var version by remember { mutableStateOf("1.0.0") }
    var video by remember { mutableStateOf("") }
    var fileBytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var previewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var previewMime by remember { mutableStateOf<String?>(null) }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, _) = readUriBytes(ctx, uri)
            fileBytes = bytes
            fileName = uri.lastPathSegment?.substringAfterLast("/")?.takeLast(60)
        }
    }
    val imgPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, mime) = readUriBytes(ctx, uri)
            previewBytes = bytes
            previewMime = mime
        }
    }
    Card(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
        Column(Modifier.padding(14.dp)) {
            Text(stringResource(R.string.studio_new), fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = type == "addon", onClick = { type = "addon" }, label = { Text(stringResource(R.string.studio_type_addon)) })
                FilterChip(selected = type == "world", onClick = { type = "world" }, label = { Text(stringResource(R.string.studio_type_world)) })
            }
            OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.studio_title)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = desc, onValueChange = { desc = it }, label = { Text(stringResource(R.string.studio_desc)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text(stringResource(R.string.studio_category)) }, singleLine = true, modifier = Modifier.weight(1f))
                OutlinedTextField(value = version, onValueChange = { version = it }, label = { Text(stringResource(R.string.studio_version)) }, singleLine = true, modifier = Modifier.weight(1f))
            }
            OutlinedTextField(value = video, onValueChange = { video = it }, label = { Text(stringResource(R.string.studio_video)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { filePicker.launch("*/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(fileName ?: stringResource(R.string.studio_file))
            }
            OutlinedButton(onClick = { imgPicker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                Text(if (previewBytes == null) stringResource(R.string.studio_preview) else "🖼️ ${(previewBytes!!.size / 1024)} KB")
            }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    onSubmit(SubmissionFormData(type, title.trim(), desc.trim(), category.trim(), version.trim(), video.ifBlank { null }, fileBytes, fileName, previewBytes, previewMime))
                },
                enabled = title.trim().length >= 3 && fileBytes != null && !uploading,
                modifier = Modifier.fillMaxWidth()
            ) {
                if (uploading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.studio_submit))
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Creator application
// ---------------------------------------------------------------------------

class CreatorApplicationViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val current: CreatorApplication? = null,
        val messages: List<CreatorApplicationMessage> = emptyList(),
        val busy: Boolean = false,
        val error: String? = null,
        val done: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val me = app.auth.currentUserId() ?: run {
            _ui.value = Ui(loading = false); return
        }
        viewModelScope.launch {
            _ui.value = _ui.value.copy(loading = true, error = null)
            try {
                val cur = app.creators.myApplication(me)
                val msgs = cur?.let { app.creators.applicationMessages(it.id) } ?: emptyList()
                _ui.value = Ui(false, cur, msgs)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun submit(fields: Map<String, Any?>, isUpdate: Boolean, appId: Long?) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            try {
                if (isUpdate && appId != null) app.creators.resubmitApplication(appId, fields)
                else app.creators.submitApplication(fields)
                _ui.value = _ui.value.copy(busy = false, done = true)
                refresh()
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false, error = e.message)
            }
        }
    }
}

@Composable
fun CreatorApplicationScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val avm: CreatorApplicationViewModel = appViewModel { CreatorApplicationViewModel(app) }
    val ui by avm.ui.collectAsState()
    val session by vm.session.collectAsState()

    var name by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var types by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var exp by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    var work by remember { mutableStateOf("") }
    var links by remember { mutableStateOf("") }
    var motivation by remember { mutableStateOf("") }
    var agree by remember { mutableStateOf(false) }
    var owns by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = stringResource(R.string.legacy_becomecreator), titleIcon = R.drawable.ic_creators,
            vm = vm, showBack = true, onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        if (session.userId == null) {
            EmptyBox(stringResource(R.string.common_login_required), Modifier.weight(1f))
            return@Column
        }
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, { avm.refresh() }, Modifier.weight(1f))
            else -> LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                ui.current?.let { cur ->
                    item {
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                            Column(Modifier.padding(14.dp)) {
                                Text(stringResource(R.string.app_status, cur.status), fontWeight = FontWeight.Bold)
                                name = cur.creatorName
                                country = cur.country ?: ""
                                types = cur.contentTypes ?: ""
                                bio = cur.bio ?: ""
                                exp = cur.experience ?: ""
                                plan = cur.publishPlan ?: ""
                                work = cur.previousWork ?: ""
                                links = cur.portfolioLinks ?: ""
                                motivation = cur.motivation ?: ""
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (ui.messages.isNotEmpty()) {
                            Text(stringResource(R.string.app_reviewer_notes), fontWeight = FontWeight.Bold)
                            ui.messages.forEach { m ->
                                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                    Text(m.message, modifier = Modifier.padding(10.dp), style = MaterialTheme.typography.bodySmall)
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
                item {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.app_field_name)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = country, onValueChange = { country = it }, label = { Text(stringResource(R.string.app_field_country)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = types, onValueChange = { types = it }, label = { Text(stringResource(R.string.app_field_types)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = bio, onValueChange = { bio = it }, label = { Text(stringResource(R.string.app_field_bio)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = exp, onValueChange = { exp = it }, label = { Text(stringResource(R.string.app_field_exp)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = plan, onValueChange = { plan = it }, label = { Text(stringResource(R.string.app_field_plan)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = work, onValueChange = { work = it }, label = { Text(stringResource(R.string.app_field_work)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = links, onValueChange = { links = it }, label = { Text(stringResource(R.string.app_field_links)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    OutlinedTextField(value = motivation, onValueChange = { motivation = it }, label = { Text(stringResource(R.string.app_field_motivation)) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(agree, { agree = it })
                        Text(stringResource(R.string.app_field_guidelines), style = MaterialTheme.typography.bodySmall)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(owns, { owns = it })
                        Text(stringResource(R.string.app_field_owns), style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            avm.submit(
                                mapOf(
                                    "p_creator_name" to name.trim(),
                                    "p_country" to country.trim(),
                                    "p_content_types" to types.trim(),
                                    "p_bio" to bio.trim(),
                                    "p_experience" to exp.trim(),
                                    "p_publish_plan" to plan.trim(),
                                    "p_previous_work" to work.trim(),
                                    "p_portfolio_links" to links.trim(),
                                    "p_motivation" to motivation.trim(),
                                    "p_agreed_guidelines" to agree,
                                    "p_owns_content" to owns
                                ),
                                ui.current != null,
                                ui.current?.id
                            )
                            Toast.makeText(ctx, ctx.getString(R.string.app_submitted), Toast.LENGTH_LONG).show()
                        },
                        enabled = name.trim().length >= 2 && bio.trim().isNotEmpty() && agree && owns && !ui.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(if (ui.current == null) R.string.app_submit else R.string.app_resubmit))
                    }
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Tip a creator
// ---------------------------------------------------------------------------

class TipViewModel(private val app: AppContainer, private val creatorId: String) : ViewModel() {
    data class Ui(
        val loading: Boolean = true,
        val creator: Profile? = null,
        val methods: List<PaymentMethodSetting> = emptyList(),
        val history: List<CreatorTipOrder> = emptyList(),
        val error: String? = null,
        val busy: Boolean = false,
        val done: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val creator = app.profiles.get(creatorId)
                val methods = app.payments.methods().filter { it.active }
                val me = app.auth.currentUserId()
                val history = me?.let { app.creators.tipHistoryAsSupporter(it) } ?: emptyList()
                _ui.value = Ui(false, creator, methods, history)
            } catch (e: Exception) {
                _ui.value = Ui(false, error = e.message)
            }
        }
    }

    fun send(amount: Double, method: String, message: String, receipt: ByteArray?, mime: String?, txRef: String?, onErr: (String) -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true)
            try {
                val tipId = app.creators.createTip(creatorId, amount, method, message)
                if (receipt != null) {
                    val me = app.auth.currentUserId() ?: "anon"
                    val path = "$me/tip_${tipId}_${System.currentTimeMillis()}.jpg"
                    app.backend.storageUpload("payment-receipts", path, receipt, mime ?: "image/jpeg")
                    app.creators.submitTipReceipt(tipId, path, txRef?.takeIf { it.isNotBlank() })
                }
                _ui.value = _ui.value.copy(busy = false, done = true)
            } catch (e: Exception) {
                _ui.value = _ui.value.copy(busy = false)
                onErr(e.message ?: "Error")
            }
        }
    }
}

@Composable
fun TipScreen(nav: NavHostController, vm: AppViewModel, creatorId: String) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val tvm: TipViewModel = appViewModel { TipViewModel(app, creatorId) }
    val ui by tvm.ui.collectAsState()
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var txRef by remember { mutableStateOf("") }
    var receipt by remember { mutableStateOf<ByteArray?>(null) }
    var receiptMime by remember { mutableStateOf<String?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val (bytes, mime) = readUriBytes(ctx, uri)
            receipt = bytes
            receiptMime = mime
        }
    }
    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = ui.creator?.let { ctx.getString(R.string.tip_to, it.username) } ?: stringResource(R.string.legacy_supportcreator),
            titleIcon = R.drawable.ic_creators, vm = vm, showBack = true,
            onBack = { nav.popBackStack() },
            onWallet = { nav.navigate(Routes.WALLET) },
            onNotifications = { nav.navigate(Routes.NOTIFICATIONS) },
            onProfile = { nav.navigate(Routes.PROFILE) }
        )
        when {
            ui.loading -> LoadingBox(Modifier.weight(1f))
            ui.error != null -> ErrorBox(ui.error!!, {}, Modifier.weight(1f))
            ui.done -> {
                Column(Modifier.weight(1f).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("💝", style = MaterialTheme.typography.displaySmall)
                    Text(stringResource(R.string.tip_sent))
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.common_ok)) }
                }
            }
            else -> LazyColumn(modifier = Modifier.weight(1f).padding(16.dp)) {
                item {
                    OutlinedTextField(
                        value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' } },
                        label = { Text(stringResource(R.string.legacy_amountlyd)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(stringResource(R.string.legacy_paymentmethod), fontWeight = FontWeight.Bold)
                    ui.methods.forEach { m ->
                        FilterChip(
                            selected = method == m.method,
                            onClick = { method = m.method },
                            label = { Text("${m.method} ${m.accountIdentifier ?: ""}") },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = message, onValueChange = { message = it },
                        label = { Text(stringResource(R.string.legacy_saykind)) },
                        modifier = Modifier.fillMaxWidth(), minLines = 2
                    )
                    OutlinedTextField(
                        value = txRef, onValueChange = { txRef = it },
                        label = { Text(stringResource(R.string.pay_tx_ref)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { picker.launch("image/*") }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (receipt == null) stringResource(R.string.legacy_submitreceipt) else "🖼️ ${(receipt!!.size / 1024)} KB")
                    }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            val a = amount.toDoubleOrNull() ?: 0.0
                            tvm.send(a, method!!, message.trim(), receipt, receiptMime, txRef) {
                                Toast.makeText(ctx, it, Toast.LENGTH_LONG).show()
                            }
                        },
                        enabled = (amount.toDoubleOrNull() ?: 0.0) > 0 && method != null && !ui.busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        else Text(stringResource(R.string.legacy_sendsupport))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.legacy_tiphistory), fontWeight = FontWeight.Bold)
                }
                if (ui.history.isEmpty()) {
                    item { EmptyBox(stringResource(R.string.legacy_notips)) }
                } else {
                    items(ui.history) { t ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                        ) {
                            Text("${t.amountLyd} LYD • ${t.status}", modifier = Modifier.padding(10.dp))
                        }
                    }
                }
            }
        }
    }
}
