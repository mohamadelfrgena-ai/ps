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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
import com.addonstation.mobile.ui.components.specDashedBorder
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
    val fresh = creators.sortedByDescending { it.profile.createdAt.orEmpty() }.take(12)

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
                leadingIcon = { androidx.compose.material3.Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.Muted) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
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
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
    ) {
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

/** Creator profile with the existing manual tip workflow presented as a sheet. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
    var supportOpen by remember { mutableStateOf(false) }
    var successOpen by remember { mutableStateOf(false) }
    var noMailOrReceiptError by remember { mutableStateOf(false) }

    LaunchedEffect(userId) {
        card = container.creators.creatorStats(userId)
        items = container.content.byCreator(userId, 12)
        val loadedMethods = container.plans.loadPaymentMethods()
        method = loadedMethods.firstOrNull()?.code
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            receiptBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            receiptMime = context.contentResolver.getType(uri) ?: "image/png"
            receiptName = uri.lastPathSegment?.substringAfterLast('/') ?: "tip-${System.currentTimeMillis()}.png"
            noMailOrReceiptError = receiptBytes == null
        }
    }
    val sentMsg = stringResource(R.string.creator_tip_sent)
    val chooseReceiptMsg = stringResource(R.string.plan_choose_receipt)
    val successTitle = stringResource(R.string.creator_tip_sent)

    Column(Modifier.fillMaxSize().background(AppColors.Bg)) {
        val creator = card
        if (creator == null) {
            SubScreenHeader(title = stringResource(R.string.creators_title), onBack = onBack)
            LoadingBox(text = stringResource(R.string.common_loading))
            return@Column
        }
        com.addonstation.mobile.ui.components.SpecProfileBanner(
            bannerUrl = assetUrl(container, creator.profile.bannerUrl),
            onBack = onBack,
        )
        Box(Modifier.fillMaxWidth().height(68.dp)) {
            Row(
                Modifier.fillMaxWidth().offset(y = (-38).dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                com.addonstation.mobile.ui.components.SpecProfileAvatar(
                    avatarUrl = assetUrl(container, creator.profile.avatarUrl),
                    status = com.addonstation.mobile.ui.components.specStatusOf(creator.profile, isMe = false),
                    size = 80.dp,
                    borderWidth = 4.dp,
                    dotSize = 22.dp,
                )
                Column(Modifier.weight(1f)) {
                    Text(
                        creator.profile.displayName ?: creator.profile.username.orEmpty(),
                        style = AppText.welcomeName,
                        color = AppColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row {
                        com.addonstation.mobile.ui.components.SpecBadgeChip(
                            stringResource(R.string.role_creator_plain),
                            com.addonstation.mobile.ui.theme.Spec.blue,
                            Color.White,
                        )
                    }
                }
            }
        }
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 28.dp),
        ) {
            Text(
                creator.profile.bio?.takeIf(String::isNotBlank) ?: stringResource(R.string.profile_about_default),
                style = AppText.bodySmall,
                color = AppColors.Muted,
                modifier = Modifier.padding(top = 6.dp),
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CreatorStatChip(
                    value = creator.downloads.toString(),
                    label = stringResource(R.string.profile_stats_downloads),
                    icon = Icons.Filled.FileDownload,
                )
                val addons = (creator.published - creator.worlds).coerceAtLeast(0)
                CreatorStatChip(
                    value = pluralStringResource(R.plurals.creator_addons_count, addons, addons),
                    label = stringResource(R.string.profile_tab_content),
                    icon = Icons.Filled.Inventory2,
                )
            }
            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(R.string.creator_tip_title),
                onClick = { supportOpen = true },
                colors = listOf(Color(0xFFFFD66B), Color(0xFFF0C25E)),
                textColor = Color(0xFF1B1405),
                modifier = Modifier.fillMaxWidth().height(56.dp),
                leading = {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = Color(0xFF1B1405),
                    )
                },
            )
            Spacer(Modifier.height(8.dp))
            GhostButton(
                text = stringResource(R.string.profile_open_profile_plain),
                onClick = { onOpenProfile(userId) },
                modifier = Modifier.fillMaxWidth(),
            )
            SectionHeader(title = stringResource(R.string.home_latest_plain))
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.profile_my_content_empty_plain))
            } else {
                LazyRow(contentPadding = PaddingValues(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.id }) { item ->
                        CreatorLatestCard(item = item, creatorName = creator.profile.username ?: creator.profile.displayName.orEmpty(), onClick = { onOpenItem(item) })
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    if (supportOpen) {
        androidx.compose.material3.ModalBottomSheet(
            onDismissRequest = { supportOpen = false },
            containerColor = AppColors.AuthBarSurface,
            contentColor = AppColors.AuthText,
            scrimColor = Color.Black.copy(alpha = 0.62f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { androidx.compose.material3.BottomSheetDefaults.DragHandle(color = AppColors.AuthMuted) },
        ) {
            Column(
                Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp).padding(bottom = 28.dp),
            ) {
                Text(stringResource(R.string.creator_tip_title), style = AppText.sectionTitle, color = AppColors.AuthText)
                if (!signedIn) {
                    Spacer(Modifier.height(14.dp))
                    Text(stringResource(R.string.msg_sign_in_needed), style = AppText.bodySmall, color = AppColors.AuthMuted)
                } else {
                    CreatorFieldLabel(stringResource(R.string.creator_tip_amount))
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it.filter { ch -> ch.isDigit() || ch == '.' } },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                        singleLine = true,
                        textStyle = androidx.compose.ui.text.TextStyle(fontSize = 18.sp, fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("5", "10", "20", "50").forEach { preset ->
                            val active = amount == preset
                            Box(
                                Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(12.dp))
                                    .background(if (active) AppColors.AuthGold.copy(alpha = 0.12f) else AppColors.AuthSurface)
                                    .border(if (active) 2.dp else 1.dp, if (active) AppColors.AuthGold else AppColors.AuthBorder, RoundedCornerShape(12.dp))
                                    .clickable { amount = preset },
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(preset, style = AppText.bodySmall, color = if (active) AppColors.AuthGold else AppColors.AuthText)
                            }
                        }
                    }

                    CreatorFieldLabel(stringResource(R.string.creator_tip_method))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        methods.forEach { option ->
                            val selected = method == option.code
                            Row(
                                Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(14.dp))
                                    .background(AppColors.AuthSurface).border(if (selected) 2.dp else 1.dp, if (selected) AppColors.AuthBlueLight else AppColors.AuthBorder, RoundedCornerShape(14.dp))
                                    .clickable { method = option.code }.padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                androidx.compose.material3.RadioButton(selected = selected, onClick = { method = option.code })
                                Text(option.name, style = AppText.bodySmall, color = AppColors.AuthText)
                            }
                        }
                    }
                    // Public profile data has no creator-specific payout destination; do not display another user's payment details.
                    CreatorFieldLabel(stringResource(R.string.creator_tip_upload_receipt))
                    CreatorUploadCard(
                        title = stringResource(R.string.creator_tip_upload_receipt),
                        subtitle = receiptName ?: "PNG · JPEG · WEBP",
                        fileTypes = null,
                        selected = receiptBytes != null,
                        failed = noMailOrReceiptError,
                        failureText = chooseReceiptMsg,
                        cover = false,
                        onClick = { picker.launch("image/*") },
                    )
                    CreatorFieldLabel(stringResource(R.string.creator_tip_message))
                    OutlinedTextField(
                        value = message,
                        onValueChange = { if (it.length <= 200) message = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Default),
                        minLines = 3,
                        maxLines = 5,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 92.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = stringResource(R.string.creator_tip_send_plain),
                        onClick = {
                            val value = amount.toDoubleOrNull()
                            val selectedMethod = method
                            if (value == null || value <= 0.0 || selectedMethod == null || receiptBytes == null) {
                                status = chooseReceiptMsg
                                noMailOrReceiptError = true
                                return@GradientButton
                            }
                            scope.launch {
                                busy = true
                                val upload = container.creators.uploadTipReceipt(
                                    receiptBytes!!,
                                    receiptMime,
                                    receiptName ?: "tip.png",
                                )
                                val path = upload.getOrElse { error ->
                                    status = error.message ?: context.getString(R.string.msg_generic_error)
                                    noMailOrReceiptError = true
                                    busy = false
                                    return@launch
                                }
                                container.creators.submitTip(
                                    creatorId = userId,
                                    amount = value,
                                    method = selectedMethod,
                                    message = message.takeIf { it.isNotBlank() },
                                    receiptPath = path,
                                    transactionReference = null,
                                ).onSuccess {
                                    status = sentMsg
                                    supportOpen = false
                                    successOpen = true
                                }.onFailure { error ->
                                    status = error.message ?: context.getString(R.string.msg_generic_error)
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        colors = listOf(AppColors.AuthGold, AppColors.AuthGoldDark),
                        textColor = AppColors.AuthGoldDark,
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                    )
                    status?.let { Text(it, style = AppText.tiny, color = if (it == sentMsg) AppColors.AuthGreenText else AppColors.AuthRedText) }
                }
            }
        }
    }
    if (successOpen) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { successOpen = false },
            shape = RoundedCornerShape(24.dp),
            containerColor = AppColors.AuthSurface,
            icon = {
                Box(
                    Modifier.size(88.dp).clip(CircleShape).background(AppColors.AuthGreen.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.Icon(
                        Icons.Filled.Check,
                        contentDescription = null,
                        tint = AppColors.AuthGreen,
                        modifier = Modifier.size(44.dp),
                    )
                }
            },
            title = null,
            text = { Text(successTitle, color = AppColors.AuthText, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { successOpen = false }) { Text(stringResource(R.string.common_ok), color = AppColors.AuthBlueLight) } },
        )
    }
}

@Composable
private fun CreatorStatChip(
    value: String,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
) {
    Row(
        Modifier.clip(RoundedCornerShape(14.dp)).background(AppColors.Surface).border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = AppColors.Blue, modifier = Modifier.size(16.dp))
        Text(value, style = AppText.bodySmall, color = AppColors.Text, maxLines = 1)
        Text(label, style = AppText.tiny, color = AppColors.Muted, maxLines = 1)
    }
}

@Composable
private fun CreatorLatestCard(item: ContentItem, creatorName: String, onClick: () -> Unit) {
    val container = rememberContainer()
    Column(
        Modifier.width(150.dp).clip(RoundedCornerShape(16.dp)).background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp)) {
            CoverImage(url = assetUrl(container, item.coverUrl), modifier = Modifier.fillMaxSize(), radius = 0.dp)
            Text(
                planLabelOf(item.plan),
                style = AppText.tiny,
                color = Color.White,
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp).clip(RoundedCornerShape(8.dp))
                    .background(AppColors.Blue.copy(alpha = 0.85f)).padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
        Text(item.title, style = AppText.bodySmall, color = AppColors.Text, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
        Text(stringResource(R.string.item_by, creatorName), style = AppText.tiny, color = AppColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 10.dp))
    }
}

/** Request to join the creator program; submission repository and payload stay unchanged. */
@Composable
fun CreatorApplicationScreen(onBack: () -> Unit, onSubmitted: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var country by remember { mutableStateOf("") }
    var contentTypes by remember { mutableStateOf(setOf<String>()) }
    var about by remember { mutableStateOf("") }
    var experience by remember { mutableStateOf("") }
    var plan by remember { mutableStateOf("") }
    var previousWork by remember { mutableStateOf("") }
    var portfolio by remember { mutableStateOf("") }
    var wallets by remember { mutableStateOf("") }
    var motivation by remember { mutableStateOf("") }
    var bio by remember { mutableStateOf("") }
    var ownWork by remember { mutableStateOf(false) }
    var guideline by remember { mutableStateOf(false) }
    var optionalDetails by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var existing by remember { mutableStateOf<com.addonstation.mobile.data.model.CreatorApplication?>(null) }
    val sentMsg = stringResource(R.string.plan_order_sent_plain)
    val requiredMsg = stringResource(R.string.creator_confirm_required)

    LaunchedEffect(Unit) { existing = container.creators.myApplication() }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.creator_title), onBack = onBack)
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 28.dp),
        ) {
            CreatorApplicationInfoCard()
            existing?.let { application ->
                val stateLabel = when (application.status.lowercase()) {
                    "approved" -> stringResource(R.string.creator_status_approved)
                    "rejected" -> stringResource(R.string.creator_status_rejected)
                    "pending" -> stringResource(R.string.creator_status_pending)
                    "more_info" -> stringResource(R.string.creator_status_more_info)
                    else -> stringResource(R.string.creator_status_pending)
                }
                CreatorStatusCard(
                    title = "$stateLabel · ${application.country.orEmpty()}",
                    note = application.adminNote,
                )
                Spacer(Modifier.height(12.dp))
            }

            Field(stringResource(R.string.creator_field_country), country) { country = it }
            Field(stringResource(R.string.creator_field_experience), experience, lines = 3) { experience = it }
            CreatorFieldLabel(stringResource(R.string.creator_field_content_types))
            CreatorTypeChips(
                types = listOf(
                    "addon" to R.string.creator_type_addons,
                    "world" to R.string.creator_type_worlds,
                    "map" to R.string.creator_type_maps,
                ),
                selected = contentTypes,
                onToggle = { value -> contentTypes = if (value in contentTypes) contentTypes - value else contentTypes + value },
            )
            Field(stringResource(R.string.creator_field_motivation), motivation, lines = 3) { motivation = it }
            Field(stringResource(R.string.creator_field_portfolio), portfolio) { portfolio = it }
            Field(stringResource(R.string.creator_field_previous_work), previousWork) { previousWork = it }
            Field(stringResource(R.string.creator_field_publish_plan), plan, lines = 3) { plan = it }
            Field(stringResource(R.string.creator_field_wallets), wallets, lines = 2) { wallets = it }

            Spacer(Modifier.height(4.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { optionalDetails = !optionalDetails }
                    .background(AppColors.Elevated).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.creator_optional_details), style = AppText.bodySmall, color = AppColors.Blue)
            }
            if (optionalDetails) {
                Spacer(Modifier.height(8.dp))
                Field(stringResource(R.string.profile_about_plain), about, lines = 3) { about = it }
                Field(stringResource(R.string.profile_about_me), bio, lines = 2) { bio = it }
            }

            Spacer(Modifier.height(8.dp))
            CreatorRequiredCheckRow(
                checked = ownWork,
                label = stringResource(R.string.creator_own_work),
                onCheckedChange = { ownWork = it },
            )
            CreatorRequiredCheckRow(
                checked = guideline,
                label = stringResource(R.string.creator_guidelines_ok_plain),
                onCheckedChange = { guideline = it },
            )

            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(R.string.home_creator_cta),
                onClick = {
                    val selectedTypes = listOf("addon", "world", "map").filter { it in contentTypes }.joinToString(",")
                    // The backend accepts optional application fields; only the two consent checks are mandatory here.
                    if (!ownWork || !guideline) {
                        message = requiredMsg
                        return@GradientButton
                    }
                    scope.launch {
                        busy = true
                        message = null
                        container.creators.submitApplication(
                            country = country.takeIf { it.isNotBlank() },
                            contentTypes = selectedTypes.takeIf { it.isNotBlank() },
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
                            existing = container.creators.myApplication()
                            onSubmitted()
                        }.onFailure { message = it.message }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = if (it == requiredMsg) AppColors.Red else AppColors.Green)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CreatorApplicationInfoCard() {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(androidx.compose.ui.graphics.Brush.linearGradient(listOf(Color(0xFF102244), Color(0xFF121A2B))))
            .border(1.dp, AppColors.Blue.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(14.dp),
    ) {
        Text(stringResource(R.string.home_creator_kicker), style = AppText.tiny, color = AppColors.Gold)
        Spacer(Modifier.height(6.dp))
        Text(stringResource(R.string.home_creator_text), style = AppText.bodySmall, color = AppColors.Text)
    }
}

@Composable
private fun CreatorStatusCard(title: String, note: String?) {
    GlassCard(color = AppColors.Blue.copy(alpha = 0.08f), borderColor = AppColors.Blue.copy(alpha = 0.3f)) {
        Text(title, style = AppText.sectionTitle, color = AppColors.Blue)
        note?.takeIf(String::isNotBlank)?.let { Text(it, style = AppText.bodySmall, color = AppColors.Muted) }
    }
}

@Composable
private fun CreatorRequiredCheckRow(checked: Boolean, label: String, onCheckedChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onCheckedChange(!checked) },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        androidx.compose.material3.Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = AppText.bodySmall, color = AppColors.Text)
    }
}

@Composable
private fun CreatorTypeChips(
    types: List<Pair<String, Int>>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        types.forEach { (value, label) ->
            val active = value in selected
            Box(
                Modifier.clip(RoundedCornerShape(20.dp))
                    .background(if (active) AppColors.Blue else AppColors.Surface)
                    .border(1.dp, if (active) AppColors.Blue else AppColors.Border, RoundedCornerShape(20.dp))
                    .clickable { onToggle(value) }.padding(horizontal = 13.dp, vertical = 10.dp),
            ) {
                Text(stringResource(label), style = AppText.tiny, color = if (active) Color.White else AppColors.Muted, maxLines = 1)
            }
        }
    }
}

@Composable
private fun CreatorFieldLabel(text: String) {
    Text(text, style = AppText.tiny, color = AppColors.Muted, modifier = Modifier.padding(top = 10.dp, bottom = 5.dp))
}

@Composable
private fun Field(label: String, value: String, lines: Int = 1, onChange: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
        CreatorFieldLabel(label)
        OutlinedTextField(
            value = value,
            onValueChange = onChange,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
            minLines = lines,
            maxLines = if (lines > 1) lines + 3 else 1,
            singleLine = lines == 1,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth().then(if (lines == 1) Modifier.heightIn(min = 48.dp) else Modifier.heightIn(min = 92.dp)),
        )
    }
}

/** Creator Studio keeps the existing upload/submission calls, with the package's dashed upload controls. */
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
    var ownWork by remember { mutableStateOf(false) }
    var guideline by remember { mutableStateOf(false) }
    var optionalDetails by remember { mutableStateOf(false) }
    var fileFailed by remember { mutableStateOf(false) }
    var coverFailed by remember { mutableStateOf(false) }
    val sentMsg = stringResource(R.string.plan_order_sent_plain)
    val requiredMsg = stringResource(R.string.creator_required_fields)
    val coverFailedMsg = stringResource(R.string.creator_cover_failed)
    val genericFailedMsg = stringResource(R.string.msg_generic_error)
    val fileTypes = stringResource(R.string.creator_file_types)

    LaunchedEffect(Unit) { submissions = container.creators.mySubmissions() }

    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                fileFailed = true
                message = genericFailedMsg
                return@launch
            }
            val name = uri.lastPathSegment?.substringAfterLast('/') ?: "content.mcaddon"
            val mime = context.contentResolver.getType(uri) ?: "application/octet-stream"
            container.creators.uploadCreatorFile(bytes, mime, name, "file")
                .onSuccess { filePath = it; fileName = name; fileFailed = false; message = null }
                .onFailure { fileFailed = true; message = it.message ?: genericFailedMsg }
        }
    }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            message = null
            val prepared = com.addonstation.mobile.util.prepareImageForUpload(context, uri)
            val bytes = prepared?.bytes ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes == null) {
                coverFailed = true
                message = coverFailedMsg
                return@launch
            }
            val mime = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/png"
            val uploadName = prepared?.fileName ?: "cover-${System.currentTimeMillis()}.jpg"
            container.creators.uploadCreatorFile(bytes, mime, uploadName, "cover")
                .onSuccess { coverPath = it; coverFailed = false; message = null }
                .onFailure { coverFailed = true; message = coverFailedMsg }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.home_creator_dashboard), onBack = onBack)
        Column(
            Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 28.dp),
        ) {
            CreatorApplicationInfoCard()
            Field(stringResource(R.string.creator_field_title), title) { title = it }
            Field(stringResource(R.string.creator_field_version), version) { version = it }

            CreatorFieldLabel(stringResource(R.string.creator_field_content_types))
            CreatorTypeChips(
                types = listOf("addon" to R.string.creator_type_addons, "world" to R.string.creator_type_worlds),
                selected = setOf(contentType),
                onToggle = { contentType = it },
            )
            Spacer(Modifier.height(14.dp))
            CreatorUploadCard(
                title = stringResource(R.string.creator_cover_label),
                subtitle = if (coverPath == null) stringResource(R.string.creator_cover_choose) else coverPath.orEmpty().substringAfterLast('/'),
                fileTypes = null,
                selected = coverPath != null,
                failed = coverFailed,
                failureText = coverFailedMsg,
                cover = true,
                onClick = { coverPicker.launch("image/*") },
            )
            Spacer(Modifier.height(10.dp))
            CreatorUploadCard(
                title = stringResource(R.string.creator_file_label),
                subtitle = fileName ?: stringResource(R.string.creator_file_types),
                fileTypes = fileTypes,
                selected = filePath != null,
                failed = fileFailed,
                failureText = genericFailedMsg,
                cover = false,
                onClick = { filePicker.launch("*/*") },
            )

            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable { optionalDetails = !optionalDetails }
                    .background(AppColors.Elevated).padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Text(stringResource(R.string.creator_optional_details), style = AppText.bodySmall, color = AppColors.Blue)
            }
            if (optionalDetails) {
                Spacer(Modifier.height(8.dp))
                Field(stringResource(R.string.admin_content_description), description, lines = 3) { description = it }
                CreatorFieldLabel(stringResource(R.string.admin_required_plan))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(44.dp)) {
                    items(listOf(
                        "free" to R.string.plan_free_name,
                        "epic" to R.string.plan_epic_name,
                        "legendary" to R.string.plan_legendary_name,
                    )) { (value, label) ->
                        FilterChip(stringResource(label), plan == value, { plan = value })
                    }
                }
                CreatorFieldLabel(stringResource(R.string.admin_room_category))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(44.dp)) {
                    items(listOf(
                        "adventure" to R.string.creator_category_adventure,
                        "survival" to R.string.creator_category_survival,
                        "pvp" to R.string.creator_category_pvp,
                        "building" to R.string.creator_category_building,
                        "skins" to R.string.creator_category_skins,
                        "other" to R.string.creator_category_other,
                    )) { (value, label) ->
                        FilterChip(stringResource(label), category == value, { category = value })
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            CreatorRequiredCheckRow(checked = ownWork, label = stringResource(R.string.creator_own_work), onCheckedChange = { ownWork = it })
            CreatorRequiredCheckRow(checked = guideline, label = stringResource(R.string.creator_guidelines_ok_plain), onCheckedChange = { guideline = it })
            Spacer(Modifier.height(14.dp))
            GradientButton(
                text = stringResource(R.string.common_save),
                onClick = {
                    if (title.isBlank() || filePath.isNullOrBlank() || coverPath.isNullOrBlank() || !ownWork || !guideline) {
                        message = requiredMsg
                        return@GradientButton
                    }
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
                            fileFailed = false
                            coverFailed = false
                            ownWork = false
                            guideline = false
                            submissions = container.creators.mySubmissions()
                        }.onFailure { message = it.message }
                        busy = false
                    }
                },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth().height(56.dp),
            )
            message?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = if (it == requiredMsg || it == coverFailedMsg || fileFailed || coverFailed) AppColors.Red else AppColors.Green)
            }

            SectionHeader(title = stringResource(R.string.admin_tab_submissions))
            if (submissions.isEmpty()) {
                EmptyState(stringResource(R.string.profile_my_content_empty_plain))
            } else {
                submissions.forEach { submission ->
                    Row(
                        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Elevated).padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(submission.title, style = AppText.bodySmall, color = AppColors.Text)
                            val typeLabel = when (submission.contentType.lowercase()) {
                                "world" -> stringResource(R.string.creator_type_worlds)
                                "map" -> stringResource(R.string.creator_type_maps)
                                else -> stringResource(R.string.creator_type_addons)
                            }
                            val planLabel = when (submission.plan.lowercase()) {
                                "epic" -> stringResource(R.string.plan_epic_name)
                                "legendary" -> stringResource(R.string.plan_legendary_name)
                                else -> stringResource(R.string.plan_free_name)
                            }
                            Text("$typeLabel · $planLabel", style = AppText.tiny, color = AppColors.Muted)
                            submission.adminNote?.let { Text(it, style = AppText.tiny, color = AppColors.Red) }
                        }
                        val statusRes = when (submission.status.lowercase()) {
                            "approved" -> R.string.creator_status_approved
                            "rejected" -> R.string.creator_status_rejected
                            else -> R.string.creator_status_pending
                        }
                        Text(stringResource(statusRes), style = AppText.tiny, color = AppColors.Blue)
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun CreatorUploadCard(
    title: String,
    subtitle: String,
    fileTypes: String?,
    selected: Boolean,
    failed: Boolean,
    failureText: String,
    cover: Boolean,
    onClick: () -> Unit,
) {
    val borderColor = if (failed) AppColors.Red else if (selected) AppColors.Green else AppColors.Border
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(AppColors.Surface)
            .then(Modifier.specDashedBorder(borderColor, 16.dp, dash = 7f, gap = 5f))
            .clickable(onClick = onClick).padding(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        androidx.compose.material3.Icon(
            imageVector = when {
                failed -> Icons.Filled.Warning
                cover -> Icons.Filled.Image
                else -> Icons.Filled.UploadFile
            },
            contentDescription = null,
            tint = if (failed) AppColors.Red else AppColors.Blue,
            modifier = Modifier.size(26.dp),
        )
        Text(title, style = AppText.bodySmall, color = AppColors.Text)
        Text(subtitle, style = AppText.tiny, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        fileTypes?.let { Text(it, style = AppText.tiny, color = AppColors.Muted, maxLines = 1) }
        if (failed) Text(failureText, style = AppText.tiny, color = AppColors.Red, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
