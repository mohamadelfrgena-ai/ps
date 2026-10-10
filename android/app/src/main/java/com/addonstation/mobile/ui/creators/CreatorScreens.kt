package com.addonstation.mobile.ui.creators

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorCard
import com.addonstation.mobile.data.model.CreatorSubmission
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.ContentCard
import com.addonstation.mobile.ui.components.CoverImage
import com.addonstation.mobile.ui.components.DividerLine
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
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var creators by remember { mutableStateOf<List<CreatorCard>>(emptyList()) }

    LaunchedEffect(query, reloadTick) {
        loading = true
        failed = false
        try {
            creators = container.creators.creators(query)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    val featured = creators.filter { it.isFeatured }
    val popular = creators.filter { !it.isFeatured }.sortedByDescending { it.downloads }.take(12)
    val fresh = creators.filter { it.profile.userLevel <= 2 }.take(12)

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.creators_title),
            onLogoClick = onBack,
            showWallet = false,
            icon = Icons.Filled.Person,
        )
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(10.dp))
            Text(stringResource(R.string.creator_subtitle), style = AppText.bodySmall, color = AppColors.Muted)
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Search),
                placeholder = { Text(stringResource(R.string.creator_search), style = AppText.bodySmall) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                loading -> LoadingBox(text = stringResource(R.string.common_loading))
                failed -> com.addonstation.mobile.ui.components.ErrorState(onRetry = { reloadTick += 1 })
                creators.isEmpty() -> EmptyState(stringResource(R.string.creator_empty))
                else -> {
                    if (featured.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.creator_featured))
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            items(featured, key = { it.profile.id }) { card ->
                                CreatorFeaturedCard(card, onOpenCreator)
                            }
                        }
                    }
                    if (popular.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.creator_popular))
                        CreatorRows(popular, onOpenCreator)
                    }
                    if (fresh.isNotEmpty()) {
                        SectionHeader(title = stringResource(R.string.creator_new))
                        CreatorRows(fresh, onOpenCreator)
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CreatorFeaturedCard(card: CreatorCard, onOpen: (String) -> Unit) {
    val container = rememberContainer()
    val addons = (card.published - card.worlds).coerceAtLeast(0)
    Column(
        Modifier.width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .clickable { onOpen(card.profile.id) },
    ) {
        CoverImage(
            url = assetUrl(container, card.profile.bannerUrl),
            modifier = Modifier.fillMaxWidth().height(64.dp),
            radius = 0.dp,
        )
        Box(Modifier.fillMaxWidth().height(24.dp)) {
            AvatarImage(
                url = assetUrl(container, card.profile.avatarUrl),
                size = 48.dp,
                modifier = Modifier.offset(y = (-24).dp).padding(start = 12.dp),
                borderColor = AppColors.Surface,
                borderWidth = 3.dp,
            )
        }
        Text(
            card.profile.displayName ?: card.profile.username.orEmpty(),
            style = AppText.bodySmall,
            color = AppColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        Text(
            pluralStringResource(R.plurals.creator_addons_count, addons, addons),
            style = AppText.tiny,
            color = AppColors.Muted,
            maxLines = 1,
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 3.dp, bottom = 10.dp),
        )
    }
}

@Composable
private fun CreatorRows(cards: List<CreatorCard>, onOpen: (String) -> Unit) {
    val container = rememberContainer()
    Column(Modifier.fillMaxWidth()) {
        cards.forEachIndexed { index, card ->
            val addons = (card.published - card.worlds).coerceAtLeast(0)
            val addonCount = pluralStringResource(R.plurals.creator_addons_count, addons, addons)
            val downloadCount = stringResource(R.string.item_stats_downloads_cd, card.downloads)
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(card.profile.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                AvatarImage(
                    url = assetUrl(container, card.profile.avatarUrl),
                    size = 44.dp,
                    borderColor = AppColors.Blue,
                )
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        card.profile.displayName ?: card.profile.username.orEmpty(),
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "$addonCount · $downloadCount",
                        style = AppText.tiny,
                        color = AppColors.Muted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier.clip(RoundedCornerShape(9.dp))
                        .background(AppColors.Blue.copy(alpha = 0.12f))
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                ) {
                    Text(stringResource(R.string.role_creator_plain), style = AppText.tiny, color = AppColors.Blue, maxLines = 1)
                }
            }
            if (index != cards.lastIndex) DividerLine()
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
    val signedIn by container.state.signedIn.collectAsState()
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
    var showSupportSheet by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.creator_title), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
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
                text = stringResource(R.string.profile_open_profile_plain),
                onClick = { onOpenProfile(userId) },
                modifier = Modifier.fillMaxWidth(),
            )

            // زر الدعم البارز (SPEC §3): 56 high · gold gradient · heart + creator_tip_title
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(listOf(AppColors.Gold, AppColors.GoldLight)),
                    )
                    .clickable { if (signedIn) showSupportSheet = true else status = stringResource(R.string.msg_sign_in_needed) },
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = AppColors.goldDark,
                        modifier = Modifier.size(22.dp),
                    )
                    Text(
                        stringResource(R.string.creator_tip_title),
                        style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                        color = AppColors.goldDark,
                    )
                }
            }

            SectionHeader(title = stringResource(R.string.profile_tab_content), leadingEmoji = "📦")
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.profile_my_content_empty_plain))
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
    var guideline by remember { mutableStateOf(false) }
    var ownWork by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<com.addonstation.mobile.data.model.CreatorApplication?>(null) }
    val sentMsg = stringResource(R.string.plan_order_sent_plain)

    LaunchedEffect(Unit) { existing = container.creators.myApplication() }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.home_creator_cta), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
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
            // SPEC §4 — Info card: kicker + home_creator_text
            GlassCard(
                color = AppColors.Blue.copy(alpha = 0.08f),
                borderColor = AppColors.Blue.copy(alpha = 0.3f),
            ) {
                Text(
                    stringResource(R.string.home_creator_kicker),
                    style = AppText.kicker,
                    color = AppColors.Gold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.home_creator_text),
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                )
            }
            Spacer(Modifier.height(12.dp))

            // SPEC §4 — Multi-select content type chips
            Field(stringResource(R.string.creator_field_country), country) { country = it }
            Field(stringResource(R.string.creator_field_experience), experience, lines = 3) { experience = it }
            // أنواع المحتوى: multi-select chips
            Text(
                stringResource(R.string.creator_field_content_types),
                style = AppText.kicker,
                color = AppColors.Muted,
            )
            Spacer(Modifier.height(6.dp))
            val contentTypesSet = remember(contentTypes) {
                contentTypes.split(",", "،").map { it.trim() }.filter { it.isNotBlank() }.toMutableSet()
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(
                    stringResource(R.string.creator_type_addons) to "addons",
                    stringResource(R.string.creator_type_worlds) to "worlds",
                    stringResource(R.string.creator_type_maps) to "maps",
                ).forEach { (label, key) ->
                    val selected = contentTypesSet.contains(key)
                    val bg = if (selected) AppColors.Blue else AppColors.Surface
                    val fg = if (selected) androidx.compose.ui.graphics.Color.White else AppColors.Text
                    Box(
                        modifier = Modifier
                            .height(40.dp)
                            .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .background(bg)
                            .border(1.dp, AppColors.Border, androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                            .clickable {
                                val newSet = contentTypesSet.toMutableSet()
                                if (selected) newSet.remove(key) else newSet.add(key)
                                contentTypes = newSet.joinToString(",")
                            }
                            .padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(label, style = AppText.body, color = fg)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Field(stringResource(R.string.creator_field_motivation), motivation, lines = 3) { motivation = it }
            Field(stringResource(R.string.creator_field_portfolio), portfolio) { portfolio = it }
            Field(stringResource(R.string.creator_field_previous_work), previousWork) { previousWork = it }
            Field(stringResource(R.string.creator_field_publish_plan), plan, lines = 2) { plan = it }
            Field(stringResource(R.string.creator_field_wallets), wallets) { wallets = it }
            Field(stringResource(R.string.profile_about), about, lines = 3) { about = it }

            Spacer(Modifier.height(8.dp))
            // SPEC §4 — Checkboxes (24×24) للـ own_work و guidelines_ok
            SpecCheckRow(
                label = stringResource(R.string.creator_own_work),
                checked = ownWork,
                onCheckedChange = { ownWork = it },
            )
            SpecCheckRow(
                label = stringResource(R.string.creator_guidelines_ok_plain),
                checked = guideline,
                onCheckedChange = { guideline = it },
            )

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
                enabled = !busy && guideline && ownWork,
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
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
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
    val sentMsg = stringResource(R.string.plan_order_sent_plain)

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
    // نصوص جاهزة من الموارد: كانت مكتوبة في الكود (إنجليزي/عربي) ⇒ لا تُترجم
    val guidelinesLabel = stringResource(R.string.creator_guidelines_ok_plain)
    val ownWorkLabel = stringResource(R.string.creator_own_work)
    val coverFailedMsg = stringResource(R.string.creator_cover_failed)
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
                .onFailure { message = coverFailedMsg }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.home_creator_dashboard), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            GlassCard {
                Text(stringResource(R.string.home_creator_text), style = AppText.bodySmall, color = AppColors.Muted)
            }
            SectionHeader(title = stringResource(R.string.admin_tab_submissions), leadingEmoji = "📦")
            Field(stringResource(R.string.creator_field_title), title) { title = it }
            Field(stringResource(R.string.admin_content_description), description, lines = 3) { description = it }
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
                text = fileName ?: stringResource(R.string.admin_add_content_plain),
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
                text = stringResource(R.string.admin_add_content_plain),
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

            SectionHeader(title = stringResource(R.string.admin_tab_submissions), leadingEmoji = "🗂")
            if (submissions.isEmpty()) {
                EmptyState(stringResource(R.string.profile_my_content_empty_plain))
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

/**
 * SPEC §4 — صف موافقة: صندوق 24×24 مع نصف قطر 7، مختار = تعبئة زرقاء + علامة
 * صحّ بيضاء، النصّ بجانبه بارتفاع صفّ 48dp على الأقل.
 */
@Composable
private fun SpecCheckRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(7.dp))
                .background(if (checked) AppColors.Blue else androidx.compose.ui.graphics.Color.Transparent)
                .border(
                    1.dp,
                    if (checked) AppColors.Blue else AppColors.Border,
                    androidx.compose.foundation.shape.RoundedCornerShape(7.dp),
                )
                .clickable { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center,
        ) {
            if (checked) {
                Icon(
                    imageVector = androidx.compose.material.icons.Icons.Filled.Check,
                    contentDescription = null,
                    tint = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        Text(
            label,
            style = AppText.body,
            color = AppColors.Text,
            modifier = Modifier.weight(1f),
        )
    }
}
