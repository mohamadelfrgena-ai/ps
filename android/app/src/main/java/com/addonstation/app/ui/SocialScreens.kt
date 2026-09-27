package com.addonstation.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.addonstation.app.data.ChatChannel
import com.addonstation.app.data.ChatMessage
import com.addonstation.app.data.Creator
import com.addonstation.app.data.WalletData
import com.addonstation.app.data.s
import com.addonstation.app.needLogin
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject

object SocialScreens {
    @Composable
    fun Rooms(model: AppModel, nav: NavHostController) {
        val text = model.text
        val palette = LocalPalette.current
        var accepted by remember { mutableStateOf(model.repo.chatRulesAccepted()) }
        var channels by remember { mutableStateOf<List<ChatChannel>>(emptyList()) }
        LaunchedEffect(accepted) {
            if (accepted) channels = withContext(Dispatchers.IO) { runCatching { model.repo.channels() }.getOrDefault(emptyList()) }
        }
        if (!accepted) {
            Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.Center) {
                InfoCard(text.rulesTitle, "احترم الأعضاء. ممنوع الإساءة والتمييز والروابط المشبوهة والسياسة والمحتوى غير اللائق.")
                GreenButton(text.rulesAccept, Modifier.fillMaxWidth().padding(top = 16.dp)) {
                    model.repo.acceptChatRules()
                    accepted = true
                }
                GhostButton(model.text.home, Modifier.fillMaxWidth().padding(top = 8.dp)) { nav.navigate("home") }
            }
            return
        }
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            PageHeading("ADDONSTATION", text.community, text.guestBody)
            if (!model.signedIn()) {
                InfoCard(text.signIn, text.signInFirst)
                GreenButton(text.signIn, Modifier.fillMaxWidth().padding(top = 10.dp)) { nav.navigate("login") }
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(channels) { channel ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(palette.surface)
                            .clickable {
                                if (channel.id.isBlank()) {
                                    model.toast = text.offline
                                } else if (!nav.needLogin(model)) {
                                    nav.navigate("chat/${channel.id}")
                                }
                            }
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AddonImage(channelIcon(channel.slug), Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)))
                        Column(Modifier.padding(start = 12.dp).weight(1f)) {
                            Text(channel.name, color = palette.text, fontWeight = FontWeight.Bold)
                            Text(channel.description, color = palette.muted, fontSize = 12.sp, maxLines = 1)
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun Chat(model: AppModel, nav: NavHostController, channelId: String) {
        val context = LocalContext.current
        val palette = LocalPalette.current
        val text = model.text
        var messages by remember { mutableStateOf<List<ChatMessage>>(emptyList()) }
        var draft by remember { mutableStateOf("") }
        var reply by remember { mutableStateOf<ChatMessage?>(null) }
        var showStickers by remember { mutableStateOf(false) }
        val packs = remember { stickerPacks(context) }
        var pack by remember { mutableStateOf(packs.firstOrNull()?.first.orEmpty()) }
        LaunchedEffect(channelId) {
            while (true) {
                messages = withContext(Dispatchers.IO) { runCatching { model.repo.messages(channelId) }.getOrDefault(emptyList()) }
                delay(2500)
            }
        }
        Column(Modifier.fillMaxSize().padding(12.dp)) {
            GhostButton("←") { nav.popBackStack() }
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(messages) { message ->
                    Row(Modifier.fillMaxWidth().clickable { reply = message }) {
                        AddonImage(message.avatarUrl, Modifier.size(36.dp).clip(RoundedCornerShape(12.dp)))
                        Column(Modifier.padding(start = 8.dp).weight(1f)) {
                            Text(message.username, color = palette.green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            if (message.stickerUrl.isNotBlank()) AddonImage(message.stickerUrl, Modifier.size(96.dp))
                            if (message.content.isNotBlank()) Text(message.content, color = palette.text)
                            Row {
                                listOf("👍", "🔥", "❤️").forEach { emoji ->
                                    Text(emoji, modifier = Modifier.padding(end = 8.dp).clickable {
                                        model.io({ model.repo.react(message.id, emoji) }) { model.toast = emoji }
                                    })
                                }
                            }
                        }
                    }
                }
            }
            if (reply != null) Text("↩ ${reply?.username}", color = palette.gold, fontSize = 12.sp)
            if (showStickers) {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 6.dp)) {
                    packs.forEach { (name, _) ->
                        Text(name, color = if (name == pack) palette.green else palette.muted, modifier = Modifier.padding(end = 10.dp).clickable { pack = name })
                    }
                }
                LazyVerticalGrid(GridCells.Fixed(5), Modifier.height(160.dp)) {
                    val stickers = packs.firstOrNull { it.first == pack }?.second.orEmpty()
                    gridItems(stickers.size) { index ->
                        AddonImage(stickers[index], Modifier.size(56.dp).padding(3.dp).clickable {
                            model.io({ model.repo.sendMessage(channelId, "", stickers[index], reply?.id) }) {
                                showStickers = false
                                reply = null
                            }
                        })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text.stickers, color = palette.blue, modifier = Modifier.clickable { showStickers = !showStickers }.padding(end = 8.dp))
                OutlinedTextField(draft, { draft = it }, Modifier.weight(1f), placeholder = { Text(text.send) }, singleLine = true)
                Spacer(Modifier.width(8.dp))
                GreenButton(text.send) {
                    if (draft.isNotBlank()) {
                        val textValue = draft
                        draft = ""
                        model.io({ model.repo.sendMessage(channelId, textValue, null, reply?.id) }) { reply = null }
                    }
                }
            }
        }
    }

    @Composable
    fun Creators(model: AppModel, nav: NavHostController) {
        var creators by remember { mutableStateOf<List<Creator>>(emptyList()) }
        LaunchedEffect(Unit) { creators = withContext(Dispatchers.IO) { model.repo.creators() } }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("ADDONSTATION", model.text.creators, model.text.becomeCreator)
            creators.forEach { creator ->
                Box(Modifier.padding(bottom = 10.dp)) { CreatorCard(creator) { nav.navigate("creator/${creator.id}") } }
            }
        }
    }

    @Composable
    fun Creator(model: AppModel, nav: NavHostController, id: String) {
        val palette = LocalPalette.current
        val text = model.text
        var creator by remember { mutableStateOf<Creator?>(null) }
        var following by remember { mutableStateOf(false) }
        LaunchedEffect(id) {
            val all = withContext(Dispatchers.IO) { model.repo.creators() }
            creator = all.firstOrNull { it.id == id } ?: model.repo.fallbackCreators.firstOrNull { it.id == id }
            following = withContext(Dispatchers.IO) { runCatching { model.repo.isFollowing(id) }.getOrDefault(false) }
        }
        val current = creator
        if (current == null) {
            EmptyState(text.empty, "")
            return
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            AddonImage(current.bannerUrl, Modifier.fillMaxWidth().height(160.dp))
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(current.avatarUrl, 64)
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(current.name, color = palette.text, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("@${current.username}", color = palette.muted)
                        RankChip("free", "creator")
                    }
                }
                Text(current.bio, color = palette.muted, modifier = Modifier.padding(vertical = 12.dp))
                Text("${current.addons} ${text.addons}   ${current.worlds} ${text.worlds}   ↓ ${current.downloads}", color = palette.text)
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    GreenButton(if (following) text.following else text.follow, Modifier.weight(1f)) {
                        if (!nav.needLogin(model)) {
                            model.io({ model.repo.setFollowing(id, !following) }) { following = !following }
                        }
                    }
                    GhostButton(text.tip, Modifier.weight(1f)) { nav.navigate("tip/$id") }
                }
            }
        }
    }

    @Composable
    fun Wallet(model: AppModel, nav: NavHostController) {
        val text = model.text
        val palette = LocalPalette.current
        var data by remember { mutableStateOf<WalletData?>(null) }
        LaunchedEffect(Unit) {
            if (!model.signedIn()) return@LaunchedEffect
            data = withContext(Dispatchers.IO) { runCatching { model.repo.wallet() }.getOrNull() }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("ADDONSTATION ECONOMY", text.wallet, "Earn → Save → Unlock")
            if (!model.signedIn()) {
                GreenButton(text.signIn, Modifier.fillMaxWidth()) { nav.navigate("login") }
                return@Column
            }
            InfoCard(text.balance, "${data?.balance ?: model.balance ?: 0} AC")
            Text("AC عملة مكافآت داخلية. لا يوجد شراء أو سحب نقدي.", color = palette.muted, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
            GreenButton(text.claim, Modifier.fillMaxWidth()) { model.io({ model.repo.claimProfile(); model.repo.wallet() }) { data = it } }
            SectionTitle(text.claim)
            data?.tasks.orEmpty().forEach { task ->
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text(task.s("title", task.s("slug")), color = palette.text, fontWeight = FontWeight.Bold)
                        Text(task.s("description"), color = palette.muted, fontSize = 12.sp)
                    }
                    Text(text.claim, color = palette.green, modifier = Modifier.clickable {
                        model.io({ model.repo.claimTask(task.s("id")); model.repo.wallet() }) { data = it }
                    })
                }
            }
            data?.events.orEmpty().forEach { event ->
                Text(event.s("title"), color = palette.gold, modifier = Modifier.clickable {
                    model.io({ model.repo.claimEvent(event.s("id")); model.repo.wallet() }) { data = it }
                }.padding(vertical = 4.dp))
            }
            SectionTitle(text.myDownloads)
            data?.transactions.orEmpty().take(12).forEach { tx ->
                Text("${tx.s("transaction_type")}  ${tx.s("amount", tx.s("delta"))}  ${tx.s("reason")}", color = palette.muted, fontSize = 12.sp)
            }
            if (data == null) LoadingBox()
        }
    }

    @Composable
    fun Tip(model: AppModel, nav: NavHostController, creatorId: String) {
        val context = LocalContext.current
        val text = model.text
        var amount by remember { mutableStateOf("10") }
        var method by remember { mutableStateOf("onepay") }
        var message by remember { mutableStateOf("") }
        var tipId by remember { mutableStateOf("") }
        var reference by remember { mutableStateOf("") }
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null && tipId.isNotBlank()) {
                val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                if (bytes != null) {
                    model.io({ model.repo.submitTipReceipt(tipId, bytes, "receipt.jpg", reference) }) {
                        model.toast = text.save
                    }
                }
            }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
            PageHeading("CREATOR SUPPORT", text.tip, "OnePay / LPay / Edfali / MobiCash / bank")
            if (!model.signedIn()) {
                GreenButton(text.signIn) { nav.navigate("login") }
                return@Column
            }
            OutlinedTextField(amount, { amount = it }, Modifier.fillMaxWidth(), label = { Text("LYD") })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 8.dp)) {
                listOf("onepay", "lpay", "edfali", "mobicash", "sadad", "tadawul", "bank_transfer").forEach { item ->
                    Text(
                        item,
                        color = if (item == method) LocalPalette.current.greenInk else LocalPalette.current.text,
                        modifier = Modifier.padding(end = 6.dp).clip(RoundedCornerShape(12.dp)).background(if (item == method) LocalPalette.current.green else LocalPalette.current.elevated).clickable { method = item }.padding(8.dp)
                    )
                }
            }
            OutlinedTextField(message, { message = it }, Modifier.fillMaxWidth(), label = { Text(text.description) })
            OutlinedTextField(reference, { reference = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Reference") })
            GreenButton(text.apply, Modifier.fillMaxWidth().padding(top = 12.dp)) {
                model.io({
                    model.repo.createTip(creatorId, amount.toDoubleOrNull() ?: 0.0, method, message)
                }) { created ->
                    tipId = created.s("id", created.s("tip_id"))
                    model.toast = if (tipId.isBlank()) text.genericError else text.save
                }
            }
            if (tipId.isNotBlank()) GhostButton(text.save, Modifier.fillMaxWidth().padding(top = 8.dp)) { picker.launch("image/*") }
        }
    }
}
