package com.addonstation.mobile.ui.creators

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorCard
import com.addonstation.mobile.data.model.CreatorSubmission
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.ContentCard
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.FilterChip
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.StatTile
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import kotlinx.coroutines.launch

/** قائمة المبدعين: بحث + أقسام (المميزون / المشهورون / الجدد) كما في الوثيقة §10. */
@Composable
fun CreatorsScreen(
    onBack: () -> Unit,
    onOpenCreator: (String) -> Unit,
) {
    val container = rememberContainer()
    var query by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(true) }
    var creators by remember { mutableStateOf<List<CreatorCard>>(emptyList()) }

    LaunchedEffect(query) {
        loading = true
        creators = container.creators.creators(query)
        loading = false
    }

    val featured = creators.filter { it.isFeatured }
    val popular = creators.filter { !it.isFeatured }.sortedByDescending { it.downloads }.take(12)
    val fresh = creators.filter { it.profile.userLevel <= 2 }.take(12)

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.creator_title),
            onLogoClick = onBack,
            showWallet = false,
            icon = Icons.Filled.Person,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.creator_subtitle), style = AppText.bodySmall, color = AppColors.Muted)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.creator_search), style = AppText.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                loading -> LoadingBox(text = stringResource(R.string.common_loading))
                creators.isEmpty() -> EmptyState(stringResource(R.string.creator_empty))
                else -> {
                    if (featured.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.creator_featured), leadingEmoji = "⭐")
                        CreatorGrid(featured, onOpenCreator)
                    }
                    if (popular.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.creator_popular), leadingEmoji = "🔥")
                        CreatorGrid(popular, onOpenCreator)
                    }
                    if (fresh.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.creator_new), leadingEmoji = "🌱")
                        CreatorGrid(fresh, onOpenCreator)
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CreatorGrid(cards: List<CreatorCard>, onOpen: (String) -> Unit) {
    val container = rememberContainer()
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.height((((cards.size + 1) / 2) * 120).dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        userScrollEnabled = false,
    ) {
        items(cards, key = { it.profile.id }) { card ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.Surface)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                    .clickable { onOpen(card.profile.id) }
                    .padding(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                AvatarImage(url = assetUrl(container, card.profile.avatarUrl), size = 44.dp, borderColor = AppColors.Green)
                Column(Modifier.weight(1f)) {
                    Text(
                        card.profile.displayName ?: card.profile.username ?: "",
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text("@${card.profile.username ?: ""}", style = AppText.tiny, color = AppColors.Muted, maxLines = 1)
                    Text("📥 ${card.downloads}", style = AppText.tiny, color = AppColors.Muted)
                }
            }
        }
    }
}

/** ملف المبدع + تِيبس الدعم (تحويل + إيصال). */
@Composable
fun CreatorProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onOpenItem: (ContentItem) -> Unit,
    onOpenProfile: (String) -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by container.state.profile.collectAsState()
    val methods by container.state.paymentMethods.collectAsState()

    var card by remember { mutableStateOf<CreatorCard?>(null) }
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var amount by remember { mutableStateOf("") }
    var method by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf("") }
    var receiptBytes by remember { mutableStateOf<ByteArray?>(null) }
    var receiptMime by remember { mutableStateOf("image/png") }
    var receiptName by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        card = container.creators.creatorStats(userId)
        items = container.content.byCreator(userId, 12)
        container.plans.loadPaymentMethods()
        method = methods.firstOrNull()?.code
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        receiptBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
        receiptMime = context.contentResolver.getType(uri) ?: "image/png"
        receiptName = "tip-${System.currentTimeMillis()}.png"
    }
    val sentMsg = stringResource(R.string.creator_tip_sent)
    val chooseReceiptMsg = stringResource(R.string.plan_choose_receipt)

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.creator_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            val creator = card
            if (creator == null) {
                LoadingBox(text = stringResource(R.string.common_loading))
                return@Column
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AvatarImage(url = assetUrl(container, creator.profile.avatarUrl), size = 66.dp, borderColor = AppColors.Green)
                Column(Modifier.weight(1f)) {
                    Text(
                        creator.profile.displayName ?: creator.profile.username ?: "",
                        style = AppText.welcomeName,
                        color = AppColors.Text,
                    )
                    Text("@${creator.profile.username ?: ""}", style = AppText.tiny, color = AppColors.Muted)
                    RankBadge(role = creator.profile.role, plan = creator.profile.effectivePlan)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatTile(creator.downloads.toString(), stringResource(R.string.profile_stats_downloads), Modifier.weight(1f))
                StatTile(creator.worlds.toString(), stringResource(R.string.profile_stat_worlds), Modifier.weight(1f))
                StatTile(creator.published.toString(), stringResource(R.string.profile_stat_published), Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            GhostButton(
                text = stringResource(R.string.profile_open_profile),
                onClick = { onOpenProfile(userId) },
                modifier = Modifier.fillMaxWidth(),
            )

            SectionHeader(title = stringResource(R.string.creator_tip_title), leadingEmoji = "💛")
            GlassCard(color = AppColors.Gold.copy(alpha = 0.06f), borderColor = AppColors.Gold.copy(alpha = 0.3f)) {
                if (profile == null) {
                    Text(stringResource(R.string.msg_sign_in_needed), style = AppText.bodySmall, color = AppColors.Muted)
                } else {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        label = { Text(stringResource(R.string.creator_tip_amount)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(stringResource(R.string.creator_tip_method), style = AppText.tiny, color = AppColors.Muted)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        methods.take(4).forEach { option ->
                            FilterChip(
                                label = option.name,
                                selected = method == option.code,
                                onClick = { method = option.code },
                            )
                        }
                    }
                    OutlinedTextField(
                        value = message,
                        onValueChange = { if (it.length <= 200) message = it },
                        label = { Text(stringResource(R.string.creator_tip_message)) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GhostButton(
                        text = receiptName ?: stringResource(R.string.creator_tip_upload_receipt),
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GradientButton(
                        text = stringResource(R.string.creator_tip_send),
                        onClick = {
                            val value = amount.toDoubleOrNull()
                            val selectedMethod = method
                            if (value == null || selectedMethod == null || receiptBytes == null) {
                                status = chooseReceiptMsg
                                return@GradientButton
                            }
                            scope.launch {
                                busy = true
                                val path = container.creators
                                    .uploadTipReceipt(receiptBytes!!, receiptMime, receiptName ?: "tip.png")
                                    .getOrNull()
                                container.creators.submitTip(
                                    creatorId = userId,
                                    amount = value,
                                    method = selectedMethod,
                                    message = message.takeIf { it.isNotBlank() },
                                    receiptPath = path,
                                    transactionReference = null,
                                ).onSuccess { status = sentMsg }
                                    .onFailure { status = it.message }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    status?.let { Text(it, style = AppText.tiny, color = AppColors.Green) }
                }
            }

            SectionHeader(title = stringResource(R.string.profile_tab_content), leadingEmoji = "📦")
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.profile_my_content_empty))
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height((((items.size + 1) / 2) * 210).dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(items, key = { it.id }) { item ->
                        ContentCard(
                            title = item.title,
                            coverUrl = assetUrl(container, item.coverUrl),
                            planLabel = planLabelOf(item.plan),
                            metaLine = metaOf(item),
                            onClick = { onOpenItem(item) },
                        )
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** نموذج طلب الانضمام لبرنامج المبدعين (كل الحقول المذكورة في الوثيقة §10). */
@Composable
fun CreatorApplicationScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var country by remember { mutableStateOf("") }
    var contentTypes by remember { mutableStateOf("") }
    var about by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    var previousWork by remember { mutableStateOf("") }
    var portfolio by remember { mutableStateOf("") }
    var wallets by remember { mutableStateOf("") }
    var motivation by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var guideline by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<com.addonstation.mobile.data.model.CreatorApplication?>(null) }
    val sentMsg = stringResource(R.string.plan_order_sent)

    LaunchedEffect(Unit) { existing = container.creators.myApplication() }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.home_creator_cta), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            existing?.let { application ->
                GlassCard(
                    color = AppColors.Blue.copy(alpha = 0.08f),
                    borderColor = AppColors.Blue.copy(alpha = 0.3f),
                ) {
                    Text(
                        "${application.status.uppercase()} · ${application.country ?: ""}",
                        style = AppText.sectionTitle,
                        color = AppColors.Blue,
                    )
                    application.adminNote?.let {
                        Text(it, style = AppText.bodySmall, color = AppColors.Muted)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            Text(stringResource(R.string.home_creator_text), style = AppText.bodySmall, color = AppColors.Muted)
            Spacer(Modifier.height(12.dp))
            Field(stringResource(R.string.creator_field_content_types), contentTypes) { contentTypes = it }
            Field(stringResource(R.string.creator_field_country), country) { country = it }
            Field(stringResource(R.string.profile_about), about, lines = 3) { about = it }
            Field(stringResource(R.string.creator_field_experience), experience, lines = 3) { experience = it }
            Field(stringResource(R.string.creator_field_publish_plan), plan, lines = 2) { plan = it }
            Field(stringResource(R.string.creator_field_previous_work), previousWork) { previousWork = it }
            Field(stringResource(R.string.creator_field_portfolio), portfolio) { portfolio = it }
            Field(stringResource(R.string.creator_field_wallets), wallets) { wallets = it }
            Field(stringResource(R.string.creator_field_motivation), motivation, lines = 3) { motivation = it }
            Field(stringResource(R.string.profile_about_me), bio, lines = 2) { bio = it }

            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip("Guidelines ✓", guideline, { guideline = true })
                FilterChip("Own work / permission", !guideline, { guideline = false })
            }

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(R.string.home_creator_cta),
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        container.creators.submitApplication(
                            country = country.takeIf { it.isNotBlank() },
                            contentTypes = contentTypes.takeIf { it.isNotBlank() },
                            about = about.takeIf { it.isNotBlank() },
                            experience = experience.takeIf { it.isNotBlank() },
                            publishingPlan = plan.takeIf { it.isNotBlank() },
                            previousWorkUrl = previousWork.takeIf { it.isNotBlank() },
                            portfolioUrls = portfolio.takeIf { it.isNotBlank() },
                            walletLinks = wallets.takeIf { it.isNotBlank() },
                            motivation = motivation.takeIf { it.isNotBlank() },
                            bio = bio.takeIf { it.isNotBlank() },
                        ).onSuccess {
                            message = sentMsg
                            onSubmitted()
                        }.onFailure { message = it.message }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Green)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        minLines = lines,
        maxLines = if (lines > 1) lines + 3 else 1,
        singleLine = lines == 1,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
    )
}

/** استوديو المبدع: رفع المحتوى للمراجعة ومتابعة حالة الطلبات. */
@Composable
fun CreatorStudioScreen(
    onBack: () -> Unit,
    onOpenItem: (ContentItem) -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    var submissions by remember { mutableStateOf<List<CreatorSubmission>>(emptyList()) }
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var contentType by remember { mutableStateOf("addon") }
    var category by remember { mutableStateOf("adventure") }
    var plan by remember { mutableStateOf("free") }
    var version by remember { mutableStateOf("1.0") }
    var filePath by remember { mutableStateOf<String?>(null) }
    var coverPath by remember { mutableStateOf<String?>(null) }
    var fileName by remember { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val sentMsg = stringResource(R.string.plan_order_sent)

    LaunchedEffect(Unit) { submissions = container.creators.mySubmissions() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "content.mcaddon"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            container.creators.uploadCreatorFile(bytes, mime, name, "file")
                .onSuccess { filePath = it; fileName = name }
                .onFailure { message = it.message }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = null
            val prepared = com.addonstation.mobile.util.prepareImageForUpload(context, uri)
            val bytes = prepared?.bytes
                ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return@launch
            val mime = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/png"
            val fileName = prepared?.fileName ?: "cover-${System.currentTimeMillis()}.jpg"
            container.creators.uploadCreatorFile(bytes, mime, fileName, "cover")
                .onSuccess { coverPath = it }
                .onFailure { message = "فشل رفع الغلاف: ${it.message ?: it::class.simpleName}" }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.home_creator_dashboard), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            GlassCard {
                Text(stringResource(R.string.home_creator_text), style = AppText.bodySmall, color = AppColors.Muted)
            }
            SectionHeader(title = stringResource(R.string.admin_content), leadingEmoji = "📦")
            Field(stringResource(R.string.creator_field_title), title) { title = it }
            Field(stringResource(R.string.item_description), description, lines = 3) { description = it }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("addon", "world", "resource_pack", "map").forEach { type ->
                    FilterChip(type, contentType == type, { contentType = type })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("free", "epic", "legendary").forEach { level ->
                    FilterChip(level, plan == level, { plan = level })
                }
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("adventure", "survival", "pvp", "building", "skins", "other").forEach { c ->
                    FilterChip(c, category == c, { category = c })
                }
            }
            Spacer(Modifier.height(6.dp))
            Field(stringResource(R.string.creator_field_version), version) { version = it }
            GhostButton(
                text = fileName ?: stringResource(R.string.admin_add_content),
                onClick = { filePicker.launch("*/*") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = if (coverPath == null) stringResource(R.string.plan_receipt) else coverPath!!,
                onClick = { coverPicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = stringResource(R.string.admin_add_content),
                onClick = {
                    scope.launch {
                        busy = true
                        message = null
                        container.creators.submitContent(
                            title = title,
                            contentType = contentType,
                            description = description.takeIf { it.isNotBlank() },
                            category = category,
                            version = version.takeIf { it.isNotBlank() },
                            plan = plan,
                            coverUrl = coverPath,
                            filePath = filePath,
                        ).onSuccess {
                            message = sentMsg
                            title = ""
                            description = ""
                            filePath = null
                            coverPath = null
                            fileName = null
                            submissions = container.creators.mySubmissions()
                        }.onFailure { message = it.message }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Green)
            }

            SectionHeader(title = stringResource(R.string.admin_content), leadingEmoji = "🗂")
            if (submissions.isEmpty()) {
                EmptyState(stringResource(R.string.profile_my_content_empty))
            } else {
                submissions.forEach { submission ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Elevated)
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(submission.title, style = AppText.bodySmall, color = AppColors.Text)
                            Text(
                                "${submission.contentType} · ${submission.plan}",
                                style = AppText.tiny,
                                color = AppColors.Muted,
                            )
                            submission.adminNote?.let {
                                Text(it, style = AppText.tiny, color = AppColors.Red)
                            }
                        }
                        Text(submission.status, style = AppText.tiny, color = AppColors.Blue)
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}
