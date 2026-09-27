package com.addonstation.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.addonstation.app.data.ContentItem
import com.addonstation.app.data.Profile
import com.addonstation.app.data.StudioData
import com.addonstation.app.data.s
import com.addonstation.app.needLogin
import com.addonstation.app.openItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

object AccountScreens {
    @Composable
    fun Login(model: AppModel, nav: NavHostController) {
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var forgot by remember { mutableStateOf(false) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
            PageHeading("ADDONSTATION", model.text.signIn, model.text.guestBody)
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth(), label = { Text(model.text.email) }, singleLine = true)
            if (!forgot) OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(model.text.password) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            GreenButton(if (forgot) model.text.forgot else model.text.signIn, Modifier.fillMaxWidth().padding(top = 14.dp)) {
                if (forgot) {
                    model.io({ model.repo.recover(email) }) { model.toast = model.text.confirmEmail }
                } else {
                    model.io({ model.repo.signIn(email.trim(), password) }) {
                        model.refreshIdentity()
                        nav.navigate("home") { popUpTo("login") { inclusive = true } }
                    }
                }
            }
            GhostButton(model.text.forgot, Modifier.fillMaxWidth().padding(top = 8.dp)) { forgot = !forgot }
            Text(model.text.noAccount, color = LocalPalette.current.green, modifier = Modifier.padding(top = 16.dp).clickable { nav.navigate("register") })
        }
    }

    @Composable
    fun Register(model: AppModel, nav: NavHostController) {
        var username by remember { mutableStateOf("") }
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            PageHeading("ADDONSTATION", model.text.register, model.text.guestBody)
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(model.text.username) })
            OutlinedTextField(email, { email = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(model.text.email) })
            OutlinedTextField(password, { password = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(model.text.password) }, visualTransformation = PasswordVisualTransformation())
            GreenButton(model.text.register, Modifier.fillMaxWidth().padding(top = 14.dp)) {
                if (username.length < 3) {
                    model.toast = model.text.username
                } else model.io({ model.repo.signUp(email.trim(), password, username.trim()) }) { json ->
                    if (json.has("access_token")) {
                        model.refreshIdentity()
                        nav.navigate("profile/overview")
                    } else model.toast = model.text.confirmEmail
                }
            }
            Text(model.text.hasAccount, color = LocalPalette.current.green, modifier = Modifier.padding(top = 12.dp).clickable { nav.navigate("login") })
        }
    }

    @Composable
    fun Profile(model: AppModel, nav: NavHostController, tab: String) {
        val text = model.text
        val palette = LocalPalette.current
        var favs by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        var mine by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        var downloads by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        LaunchedEffect(tab, model.session?.userId) {
            if (!model.signedIn()) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                favs = runCatching { model.repo.favorites() }.getOrDefault(emptyList())
                mine = runCatching { model.repo.myContent() }.getOrDefault(emptyList())
                downloads = runCatching { model.repo.downloads() }.getOrDefault(emptyList())
            }
        }
        if (!model.signedIn()) {
            Column(Modifier.padding(20.dp)) { GreenButton(text.signIn) { nav.navigate("login") } }
            return
        }
        val profile = model.profile
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            Row {
                Avatar(profile?.avatarUrl ?: "/imgs/profiles/default-profile.jpg", 72)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(profile?.username ?: model.session?.username.orEmpty(), color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    RankChip(profile?.plan ?: "free", profile?.role ?: "user")
                    Text(profile?.bio.orEmpty(), color = palette.muted, fontSize = 13.sp)
                }
            }
            Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("overview" to text.profile, "favs" to text.favorites, "downloads" to text.myDownloads, "plan" to text.upgrade).forEach { (id, label) ->
                    Text(label, color = if (tab == id) palette.greenInk else palette.text, modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(if (tab == id) palette.green else palette.elevated).clickable { nav.navigate("profile/$id") }.padding(8.dp), fontSize = 12.sp)
                }
            }
            when (tab) {
                "favs" -> ContentList(favs, nav)
                "downloads" -> ContentList(downloads, nav)
                "content" -> ContentList(mine, nav)
                "plan" -> PlanTab(model, nav, profile)
                else -> {
                    InfoCard(text.wallet, "${model.balance ?: "—"} AC")
                    GreenButton(text.wallet, Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("wallet") }
                    GhostButton(text.settings, Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("profileSettings") }
                    if (profile?.isCreator == true) GhostButton(text.creatorStudio, Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("studio") }
                    if (profile?.role == "admin" || profile?.isAdmin == true) GhostButton("Admin", Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("admin") }
                    GhostButton(text.signOut, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        model.repo.signOut()
                        model.session = null
                        model.profile = null
                        model.balance = null
                        nav.navigate("home")
                    }
                }
            }
        }
    }

    @Composable
    fun ProfileSettings(model: AppModel, nav: NavHostController) {
        var username by remember { mutableStateOf(model.profile?.username.orEmpty()) }
        var bio by remember { mutableStateOf(model.profile?.bio.orEmpty()) }
        var avatars by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
        LaunchedEffect(model.signedIn()) {
            if (model.signedIn()) avatars = withContext(Dispatchers.IO) { model.repo.assets("avatar") }
        }
        if (!model.signedIn()) {
            Column(Modifier.padding(20.dp)) { GreenButton(model.text.signIn) { nav.navigate("login") } }
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("PROFILE", model.text.settings, model.session?.email.orEmpty())
            OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(model.text.username) })
            OutlinedTextField(bio, { bio = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text(model.text.description) })
            GreenButton(model.text.save, Modifier.fillMaxWidth().padding(top = 10.dp)) {
                model.io({ model.repo.updateProfile(JSONObject().put("username", username).put("bio", bio)) }) {
                    model.profile = it
                    model.toast = model.text.save
                }
            }
            SectionTitle("Avatars")
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                avatars.forEach { asset ->
                    AddonImage(asset.s("file_url"), Modifier.padding(end = 8.dp).size(64.dp).clip(RoundedCornerShape(16.dp)).clickable {
                        model.io({ model.repo.updateProfile(JSONObject().put("avatar_url", asset.s("file_url"))) }) { model.profile = it }
                    })
                }
            }
            GhostButton(model.text.notifications, Modifier.fillMaxWidth().padding(top = 12.dp)) { nav.navigate("notifications") }
        }
    }

    @Composable
    fun User(model: AppModel, nav: NavHostController, id: String) {
        var profile by remember { mutableStateOf<Profile?>(null) }
        var content by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
        LaunchedEffect(id) {
            profile = withContext(Dispatchers.IO) { runCatching { model.repo.profile(id) }.getOrNull() }
            content = withContext(Dispatchers.IO) { model.repo.creatorContent(id) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            GhostButton("←") { nav.popBackStack() }
            Avatar(profile?.avatarUrl ?: "/imgs/profiles/default-profile.jpg", 72)
            Text(profile?.username ?: "Player", color = LocalPalette.current.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            RankChip(profile?.plan ?: "free", profile?.role ?: "user")
            Text(profile?.bio.orEmpty(), color = LocalPalette.current.muted, modifier = Modifier.padding(vertical = 8.dp))
            if (model.session?.userId != id) {
                GreenButton(model.text.follow, Modifier.fillMaxWidth()) {
                    if (!nav.needLogin(model)) model.io({ model.repo.friendRequest(id) }) { model.toast = model.text.save }
                }
            }
            ContentList(content, nav)
        }
    }

    @Composable
    fun Notifications(model: AppModel, nav: NavHostController) {
        var rows by remember { mutableStateOf<List<JSONObject>>(emptyList()) }
        LaunchedEffect(Unit) {
            if (!model.signedIn()) return@LaunchedEffect
            rows = withContext(Dispatchers.IO) { model.repo.notifications() }
            withContext(Dispatchers.IO) { runCatching { model.repo.markNotificationsRead() } }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("ADDONSTATION", model.text.notifications, "")
            if (!model.signedIn()) GreenButton(model.text.signIn) { nav.navigate("login") }
            if (rows.isEmpty()) EmptyState(model.text.empty, "")
            rows.forEach { row ->
                InfoCard(row.s("notification_type"), row.s("created_at"))
                if (row.s("notification_type") == "friend_request") {
                    Text(model.text.save, color = LocalPalette.current.green, modifier = Modifier.clickable {
                        model.io({ model.repo.acceptFriend(row.s("actor_id")) }) { model.toast = model.text.save }
                    }.padding(bottom = 8.dp))
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    @Composable
    fun Settings(model: AppModel, nav: NavHostController) {
        val text = model.text
        var username by remember { mutableStateOf(model.profile?.username.orEmpty()) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("ADDONSTATION", text.settings, text.version)
            if (model.signedIn()) {
                OutlinedTextField(username, { username = it }, Modifier.fillMaxWidth(), label = { Text(text.username) })
                GreenButton(text.save, Modifier.padding(top = 8.dp)) { model.io({ model.repo.saveUsername(username) }) { model.refreshIdentity(); model.toast = text.save } }
                Text(model.session?.email.orEmpty(), color = LocalPalette.current.muted, modifier = Modifier.padding(vertical = 8.dp))
            } else {
                GreenButton(text.signIn) { nav.navigate("login") }
            }
            SectionTitle(text.language)
            Row(Modifier.horizontalScroll(rememberScrollState())) {
                listOf("ar" to "العربية", "en" to "English", "es" to "Español", "pt" to "Português", "fr" to "Français", "ru" to "Русский", "zh" to "中文").forEach { (code, label) ->
                    Text(label, color = if (model.language == code) LocalPalette.current.greenInk else LocalPalette.current.text, modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(12.dp)).background(if (model.language == code) LocalPalette.current.green else LocalPalette.current.elevated).clickable { model.setLanguage(code) }.padding(8.dp))
                }
            }
            SectionTitle(text.appearance)
            Row {
                GhostButton(text.dark) { model.setTheme("dark") }
                Spacer(Modifier.size(8.dp))
                GhostButton(text.light) { model.setTheme("light") }
            }
            GhostButton(text.notifications, Modifier.fillMaxWidth().padding(top = 12.dp)) { nav.navigate("notifications") }
            GhostButton(text.support, Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("support") }
            InfoCard(text.terms, "باستخدام AddonStation توافق على احترام المستخدمين وعدم نشر محتوى ضار أو مخالف للقوانين.")
            Spacer(Modifier.height(8.dp))
            InfoCard(text.privacy, "البريد مرتبط بحساب Supabase ولا يظهر في الملف العام أو بجانب الرسائل.")
            if (model.signedIn()) {
                Text(text.deleteAccount, color = LocalPalette.current.red, modifier = Modifier.padding(top = 16.dp).clickable {
                    model.io({ model.repo.deleteAccount() }) {
                        model.session = null
                        model.profile = null
                        nav.navigate("home")
                    }
                })
            }
        }
    }

    @Composable
    fun Support(model: AppModel, nav: NavHostController) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeading("ADDONSTATION CARE", model.text.support, "نحن هنا لمساعدتك في كل خطوة")
            InfoCard(model.text.support, "ابدأ من مركز المساعدة أو تحدث مع مجتمع AddonStation.")
            GhostButton(model.text.community, Modifier.fillMaxWidth().padding(top = 10.dp)) { nav.navigate("community") }
            GhostButton(model.text.settings, Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("settings") }
        }
    }

    @Composable
    fun Apply(model: AppModel, nav: NavHostController) {
        var name by remember { mutableStateOf("") }
        var country by remember { mutableStateOf("") }
        var bio by remember { mutableStateOf("") }
        var experience by remember { mutableStateOf("") }
        var plan by remember { mutableStateOf("") }
        var work by remember { mutableStateOf("") }
        var links by remember { mutableStateOf("") }
        var why by remember { mutableStateOf("") }
        var agreed by remember { mutableStateOf(false) }
        var owns by remember { mutableStateOf(false) }
        var existing by remember { mutableStateOf<JSONObject?>(null) }
        LaunchedEffect(model.signedIn()) {
            if (model.signedIn()) existing = withContext(Dispatchers.IO) { runCatching { model.repo.application() }.getOrNull() }
        }
        if (!model.signedIn()) {
            Column(Modifier.padding(20.dp)) { GreenButton(model.text.signIn) { nav.navigate("login") } }
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("CREATOR PROGRAM", model.text.becomeCreator, existing?.s("status").orEmpty())
            if (existing?.s("status") == "approved") {
                GreenButton(model.text.creatorStudio) { nav.navigate("studio") }
                return@Column
            }
            Field(model.text.username, name) { name = it }
            Field("Country", country) { country = it }
            Field(model.text.description, bio) { bio = it }
            Field("Experience", experience) { experience = it }
            Field("Publish plan", plan) { plan = it }
            Field("Previous work", work) { work = it }
            Field("Links", links) { links = it }
            Field("Why", why) { why = it }
            Text(if (agreed) "✓ Guidelines" else "Guidelines", modifier = Modifier.clickable { agreed = !agreed }.padding(top = 8.dp), color = LocalPalette.current.green)
            Text(if (owns) "✓ Own work" else "Own work", modifier = Modifier.clickable { owns = !owns }.padding(bottom = 8.dp), color = LocalPalette.current.green)
            GreenButton(model.text.apply, Modifier.fillMaxWidth()) {
                val payload = JSONObject()
                    .put("creator_name", name)
                    .put("country", country)
                    .put("content_types", JSONArray().put("addons"))
                    .put("bio", bio)
                    .put("experience", experience)
                    .put("publish_plan", plan)
                    .put("previous_work", work)
                    .put("portfolio_links", links)
                    .put("motivation", why)
                    .put("agreed_guidelines", agreed)
                    .put("owns_content", owns)
                val resubmit = existing?.takeIf { it.s("status") == "more_info" }?.s("id")
                model.io({ model.repo.submitApplication(payload, resubmit?.ifBlank { null }) }) { model.toast = model.text.save }
            }
        }
    }

    @Composable
    fun Studio(model: AppModel, nav: NavHostController) {
        val context = LocalContext.current
        var data by remember { mutableStateOf<StudioData?>(null) }
        var title by remember { mutableStateOf("") }
        var category by remember { mutableStateOf("Adventure") }
        var description by remember { mutableStateOf("") }
        var preview by remember { mutableStateOf<ByteArray?>(null) }
        var file by remember { mutableStateOf<ByteArray?>(null) }
        var fileName by remember { mutableStateOf("content.mcaddon") }
        val previewPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            preview = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
        }
        val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            file = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
            fileName = "upload.bin"
        }
        LaunchedEffect(model.signedIn()) {
            if (model.signedIn()) data = withContext(Dispatchers.IO) { runCatching { model.repo.studio() }.getOrNull() }
        }
        if (!model.signedIn()) {
            Column(Modifier.padding(20.dp)) { GreenButton(model.text.signIn) { nav.navigate("login") } }
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("CREATOR PROGRAM", model.text.creatorStudio, "")
            if (model.profile?.isCreator != true && data == null) {
                EmptyState(model.text.becomeCreator, "")
                GreenButton(model.text.apply) { nav.navigate("apply") }
            }
            InfoCard("LYD", "${data?.availableLyd ?: 0} available · ${data?.lifetimeLyd ?: 0} lifetime")
            Field("Title", title) { title = it }
            Field("Category", category) { category = it }
            Field(model.text.description, description) { description = it }
            GhostButton("Preview") { previewPicker.launch("image/*") }
            GhostButton("File") { filePicker.launch("*/*") }
            GreenButton(model.text.apply, Modifier.fillMaxWidth().padding(top = 8.dp)) {
                val image = preview
                val content = file
                if (image == null || content == null || title.isBlank()) {
                    model.toast = model.text.genericError
                } else model.io({ model.repo.uploadSubmission("addon", title, category, "1.0.0", description, image, "preview.jpg", content, fileName) }) {
                    model.toast = model.text.save
                }
            }
            data?.content.orEmpty().forEach { item ->
                Text(item.title, color = LocalPalette.current.text, modifier = Modifier.padding(top = 8.dp).clickable { nav.openItem(item.slug) })
            }
        }
    }

    @Composable
    fun Checkout(model: AppModel, nav: NavHostController, plan: String) {
        val context = LocalContext.current
        val amount = if (plan == "legendary") 50.0 else 25.0
        var method by remember { mutableStateOf("onepay") }
        var orderId by remember { mutableStateOf("") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            val bytes = uri?.let { context.contentResolver.openInputStream(it)?.use { stream -> stream.readBytes() } }
            if (bytes != null && orderId.isNotBlank()) {
                model.io({ model.repo.attachReceipt(orderId, bytes, "receipt.jpg") }) { model.toast = model.text.save }
            }
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeading("SUBSCRIPTION", model.text.upgrade, "$plan · $amount LYD")
            if (!model.signedIn()) {
                GreenButton(model.text.signIn) { nav.navigate("login") }
                return@Column
            }
            Text("OnePay / LPay / Edfali / MobiCash / تحويل بنكي", color = LocalPalette.current.muted)
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                listOf("onepay", "lpay", "edfali", "mobicash", "bank_transfer").forEach { item ->
                    Text(item, modifier = Modifier.padding(end = 8.dp).clickable { method = item }, color = if (method == item) LocalPalette.current.green else LocalPalette.current.text)
                }
            }
            GreenButton(model.text.apply, Modifier.fillMaxWidth()) {
                model.io({ model.repo.createSubscription(plan, amount, method) }) { orderId = it; model.toast = it }
            }
            if (orderId.isNotBlank()) GhostButton(model.text.save, Modifier.fillMaxWidth().padding(top = 8.dp)) { picker.launch("image/*") }
        }
    }

    @Composable
    fun Admin(model: AppModel, nav: NavHostController) {
        var stats by remember { mutableStateOf("") }
        LaunchedEffect(Unit) { stats = withContext(Dispatchers.IO) { model.repo.adminStats() } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("ADMIN", "AddonStation", model.profile?.username.orEmpty())
            InfoCard("AC", stats)
            GhostButton(model.text.wallet) { nav.navigate("wallet") }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, onChange, Modifier.fillMaxWidth().padding(bottom = 8.dp), label = { Text(label) })
}

@Composable
private fun ContentList(items: List<ContentItem>, nav: NavHostController) {
    if (items.isEmpty()) EmptyState("—", "")
    items.forEach { item ->
        Text(item.title, color = LocalPalette.current.text, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable { nav.openItem(item.slug) })
    }
}

@Composable
private fun PlanTab(model: AppModel, nav: NavHostController, profile: Profile?) {
    val plan = profile?.plan ?: "free"
    InfoCard(model.text.upgrade, if (plan == "free") "Free" else "$plan · ${profile?.planExpiresAt.orEmpty()}")
    GhostButton("Epic · 25 LYD", Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("checkout/epic") }
    GhostButton("Legendary · 50 LYD", Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("checkout/legendary") }
}
