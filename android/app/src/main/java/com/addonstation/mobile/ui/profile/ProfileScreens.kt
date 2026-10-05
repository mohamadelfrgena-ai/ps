package com.addonstation.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.theme.adaptiveColumns
import com.addonstation.mobile.ui.theme.appScreenWidthDp
import com.addonstation.mobile.ui.equippedItemUrl
import com.addonstation.mobile.ui.groupAssetsByCollection
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.AssetSectionHeader
import com.addonstation.mobile.ui.components.AssetThumb
import com.addonstation.mobile.ui.components.BannerThumb
import com.addonstation.mobile.ui.components.ContentCard
import com.addonstation.mobile.ui.components.ContentColumn
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.FilterChip
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.ProfileRow
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.RowItem
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.components.ThinProgress
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.launch
import java.util.Locale

/** شاشة الملف الشخصي (تتطلب تسجيل الدخول — وإلا ينتقل المستخدم لتسجيل الدخول). */
@Composable
fun ProfileScreen(
    onLogin: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPlans: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenAdmin: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenItem: (ContentItem) -> Unit,
    onOpenWallet: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val scope = rememberCoroutineScope()

    var tab by remember { mutableStateOf(0) }
    var myContent by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var favourites by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var downloads by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(profile?.id, tab) {
        val me = profile ?: return@LaunchedEffect
        loading = true
        when (tab) {
            1 -> myContent = container.content.byCreator(me.id)
            2 -> favourites = container.content.favorites()
            3 -> downloads = container.content.downloads()
        }
        loading = false
    }

    if (!signedIn || profile == null) {
        LaunchedEffect(Unit) { onLogin() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingBox(text = stringResource(R.string.msg_sign_in_needed))
        }
        return
    }

    val me = profile!!
    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.nav_profile),
            walletBalance = wallet?.balance,
            onWalletClick = onOpenWallet,
            icon = Icons.Filled.Settings,
        )
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // الغلاف + مربّع البروفايل: **نصف المربّع داخل الغلاف ونصفه خارجه**
            // (كما في صورة المالك) — فيبدو الأفاتار راكباً على حافة الغلاف السفلية.
            val avatarBoxSize = 74.dp                 // المربّع مع إطاره المتدرّج
            val bannerHeight = 140.dp
            Box(
                Modifier
                    .fillMaxWidth()
                    // نصف المربّع يبرز خارج الغلاف ⇒ نزيد ارتفاع الحاوية بمقداره
                    .height(bannerHeight + avatarBoxSize / 2),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(bannerHeight)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppColors.Elevated2),
                ) {
                    val banner = assetUrl(container, me.bannerUrl)
                    if (banner != null) {
                        com.addonstation.mobile.ui.components.CoverImage(
                            url = banner,
                            radius = 20.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 16.dp)
                        .size(avatarBoxSize)
                        .clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(AppColors.Green, AppColors.Blue)))
                        .padding(3.dp),
                ) {
                    AvatarImage(
                        url = assetUrl(container, me.avatarUrl),
                        size = 68.dp,
                        shape = RoundedCornerShape(19.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    me.displayName ?: me.username ?: "",
                    style = AppText.welcomeName,
                    color = AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RankBadge(role = me.role, plan = me.effectivePlan)
            }
            Text(
                "@${me.username ?: ""} · " + stringResource(
                    R.string.profile_member_since,
                    TimeFormat.dateOnly(me.createdAt, Locale.getDefault()),
                ),
                style = AppText.tiny,
                color = AppColors.Muted,
            )
            me.customStatus?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = AppText.bodySmall, color = AppColors.Blue, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton(
                    text = stringResource(R.string.profile_edit),
                    onClick = onOpenEdit,
                    icon = Icons.Filled.Edit,
                    modifier = Modifier.weight(1f),
                )
                if (me.effectivePlan == "free") {
                    GradientButton(
                        text = stringResource(R.string.profile_upgrade),
                        onClick = onOpenPlans,
                        icon = Icons.Filled.WorkspacePremium,
                        colors = listOf(AppColors.GoldLight, AppColors.GoldDark),
                        textColor = AppColors.GoldText,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (me.role == "admin") {
                    GradientButton(
                        text = stringResource(R.string.profile_dashboard),
                        onClick = onOpenAdmin,
                        icon = Icons.Filled.AdminPanelSettings,
                        colors = listOf(AppColors.BlueLight, AppColors.BlueDark),
                        textColor = AppColors.BlueText,
                        modifier = Modifier.weight(1f),
                    )
                } else if (me.role == "creator") {
                    GradientButton(
                        text = stringResource(R.string.home_creator_open),
                        onClick = onOpenStudio,
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(stringResource(R.string.profile_stats_level), me.userLevel.toString(), Modifier.weight(1f))
                StatBox(stringResource(R.string.profile_stats_content), myContentCount(me, container).toString(), Modifier.weight(1f))
                StatBox(stringResource(R.string.profile_stats_favorites), favourites.size.toString(), Modifier.weight(1f))
                StatBox(stringResource(R.string.profile_stats_downloads), downloads.size.toString(), Modifier.weight(1f))
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val tabs = listOf(
                    stringResource(R.string.profile_tab_about),
                    stringResource(R.string.profile_tab_content),
                    stringResource(R.string.profile_tab_favorites),
                    stringResource(R.string.profile_tab_plan),
                )
                tabs.forEachIndexed { index, label ->
                    FilterChip(label = label, selected = tab == index, onClick = { tab = index })
                }
            }

            Spacer(Modifier.height(12.dp))
            when (tab) {
                0 -> AboutTab(me = me, onOpenSettings = onOpenSettings, onSignOut = {
                    scope.launch {
                        container.auth.signOut()
                        container.state.setSignedIn(false)
                        onLogin()
                    }
                })
                1 -> ContentGrid(
                    items = myContent,
                    loading = loading,
                    emptyText = stringResource(R.string.profile_my_content_empty),
                    onClick = onOpenItem,
                )
                2 -> ContentGrid(
                    items = favourites,
                    loading = loading,
                    emptyText = stringResource(R.string.profile_favorites_empty),
                    onClick = onOpenItem,
                )
                else -> PlanTab(me = me, onOpenPlans = onOpenPlans)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun myContentCount(me: Profile, container: com.addonstation.mobile.data.AppContainer): Int {
    var count by remember { mutableStateOf(0) }
    LaunchedEffect(me.id) {
        count = container.content.byCreator(me.id, limit = 200).size
    }
    return count
}

@Composable
private fun StatBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(value, style = AppText.statNumber, color = AppColors.Green)
        Text(label, style = AppText.tiny, color = AppColors.Muted, maxLines = 1)
    }
}

@Composable
private fun AboutTab(me: Profile, onOpenSettings: () -> Unit, onSignOut: () -> Unit) {
    val container = rememberContainer()
    SectionHeader(title = stringResource(R.string.profile_about))
    GlassCard {
        Text(
            me.bio?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_about_default),
            style = AppText.bodySmall,
            color = AppColors.Muted,
        )
    }
    SectionHeader(title = stringResource(R.string.profile_account_info))
    GlassCard(padding = 6.dp) {
        RowItem(
            icon = Icons.Filled.WorkspacePremium,
            title = stringResource(R.string.profile_plan),
            subtitle = planLabelOf(me.effectivePlan),
            tint = AppColors.Gold,
        )
        RowItem(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.profile_online_status),
            subtitle = if (me.showOnlineStatus) stringResource(R.string.profile_online)
            else "⚪ " + stringResource(R.string.profile_offline),
        )
        RowItem(
            icon = Icons.Filled.Edit,
            title = stringResource(R.string.profile_avatar_item),
            subtitle = me.equippedItemId,
            // الأداة المجهّزة تظهر بصورتها الحقيقية (متحرّكة إن كانت GIF) بدل النص وحده
            trailingImageUrl = equippedItemUrl(container, me.equippedItemId),
        )
        RowItem(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.profile_email),
            subtitle = container.supabase.session()?.email ?: "—",
        )
        RowItem(
            icon = Icons.Filled.Settings,
            title = stringResource(R.string.profile_full_settings),
            onClick = onOpenSettings,
        )
    }
    Spacer(Modifier.height(10.dp))
    GhostButton(
        text = stringResource(R.string.common_log_out),
        onClick = onSignOut,
        icon = Icons.Filled.Logout,
        modifier = Modifier.fillMaxWidth(),
    )
    // نسخة التطبيق: سطر صغير يعرف به صاحب التطبيق أي بناء مثبّت عنده
    Spacer(Modifier.height(8.dp))
    Text(
        text = stringResource(
            R.string.app_build_stamp,
            com.addonstation.mobile.BuildConfig.VERSION_NAME,
            com.addonstation.mobile.BuildConfig.BUILD_STAMP,
        ),
        style = AppText.tiny,
        color = AppColors.Muted,
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun PlanTab(me: Profile, onOpenPlans: () -> Unit) {
    val planName = planLabelOf(me.effectivePlan)
    GlassCard(
        color = if (me.effectivePlan == "free") AppColors.Gold.copy(alpha = 0.08f)
        else AppColors.Green.copy(alpha = 0.08f),
        borderColor = if (me.effectivePlan == "free") AppColors.Gold.copy(alpha = 0.35f)
        else AppColors.Green.copy(alpha = 0.35f),
    ) {
        Text(
            text = if (me.effectivePlan == "free") stringResource(R.string.plan_current_free)
            else stringResource(R.string.home_plan_active, planName),
            style = AppText.sectionTitle,
            color = if (me.effectivePlan == "free") AppColors.Gold else AppColors.Green,
        )
        Text(
            text = when (me.effectivePlan) {
                "epic" -> stringResource(R.string.plan_current_epic)
                "legendary" -> stringResource(R.string.plan_current_legendary)
                else -> stringResource(R.string.plan_free_features)
            },
            style = AppText.bodySmall,
            color = AppColors.Muted,
        )
        if (me.effectivePlan != "free" && !me.planExpiresAt.isNullOrBlank()) {
            Text(
                stringResource(R.string.home_plan_expires, TimeFormat.expiryLabel(me.planExpiresAt, Locale.getDefault())),
                style = AppText.tiny,
                color = AppColors.Muted,
            )
        }
        GradientButton(
            text = stringResource(R.string.plan_buy),
            onClick = onOpenPlans,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(AppColors.GreenLight, AppColors.Green2),
        )
    }
    Spacer(Modifier.height(8.dp))
    Text(stringResource(R.string.plan_payment_note), style = AppText.tiny, color = AppColors.Dim)
    Spacer(Modifier.height(14.dp))
    val levels = listOf("free", "epic", "legendary")
    levels.forEach { level ->
        val name = planLabelOf(level)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 5.dp)
                .clip(RoundedCornerShape(13.dp))
                .background(AppColors.Elevated)
                .border(1.dp, AppColors.Border, RoundedCornerShape(13.dp))
                .clickable { onOpenPlans() }
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                when (level) {
                    "epic" -> "💎"
                    "legendary" -> "👑"
                    else -> "🍃"
                },
                style = AppText.welcomeName,
            )
            Column(Modifier.weight(1f)) {
                Text(name, style = AppText.sectionTitle, color = AppColors.Text)
                Text(
                    when (level) {
                        "epic" -> stringResource(R.string.plan_current_epic)
                        "legendary" -> stringResource(R.string.plan_current_legendary)
                        else -> stringResource(R.string.plan_free_tagline)
                    },
                    style = AppText.tiny,
                    color = AppColors.Muted,
                )
            }
        }
    }
}

@Composable
private fun ContentGrid(
    items: List<ContentItem>,
    loading: Boolean,
    emptyText: String,
    onClick: (ContentItem) -> Unit,
) {
    val container = rememberContainer()
    when {
        loading -> LoadingBox(text = stringResource(R.string.common_loading))
        items.isEmpty() -> EmptyState(emptyText)
        else -> LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.height(((items.size + 1) / 2 * 210).dp),
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
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

/** تعديل الملف الشخصي: الاسم، النبذة، الحالة المخصّصة، الظهور، واختيار الأفاتار/الغلاف/الأداة. */
@Composable
fun EditProfileScreen(onBack: () -> Unit, onSaved: () -> Unit) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf(profile?.username ?: "") }
    var bio by remember { mutableStateOf(profile?.bio ?: "") }
    var status by remember { mutableStateOf(profile?.customStatus ?: "") }
    var showOnline by remember { mutableStateOf(profile?.showOnlineStatus ?: false) }
    var message by remember { mutableStateOf<String?>(null) }
    var kind by remember { mutableStateOf("avatar") }
    var assetQuery by remember { mutableStateOf("") }
    var assets by remember { mutableStateOf<List<com.addonstation.mobile.data.model.SiteAsset>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    val lockedHint = stringResource(R.string.plan_locked_avatars)
    val bannerLockedHint = stringResource(R.string.plan_locked_banner_note)
    val onlineLockedHint = stringResource(R.string.plan_locked_online)

    LaunchedEffect(kind) {
        assets = container.assets.byType(kind)
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.profile_edit), onBack = onBack)
        // عدد الأعمدة يتبع عرض شاشة الهاتف: 4 على الضيّق · 5 على المعتاد · 6 على الكبير
        val gridColumns = adaptiveColumns(
            minCellDp = 62, maxCellDp = 86, maxColumns = 6,
            horizontalPaddingDp = 28, spacingDp = GRID_SPACING,
        )
        val gridContentWidth = appScreenWidthDp() - 28f - (gridColumns - 1) * GRID_SPACING
        val cellWidth = (gridContentWidth / gridColumns).coerceAtLeast(48f)
        val cellHeight = (cellWidth + 30f).coerceIn(74f, 104f).toInt()
        // الأغلفة (بانرات) صور **عريضة** ⇒ عمودان فقط بخلية 16:9 بدل مربّعات
        // الأفاتار الضيّقة — فتظهر تفاصيل الغلاف قبل الاختيار.
        val isBanner = kind == "banner"
        val bannerColumns = 2
        val bannerCellWidth = (appScreenWidthDp() - 28f - GRID_SPACING) / bannerColumns
        val bannerCellHeight = (bannerCellWidth * 9f / 16f + 30f).coerceIn(88f, 190f).toInt()
        // بحث داخل الأصول: 500 أصل بلا بحث = كل صورة مرّة بعينها
        val filteredAssets = remember(assets, assetQuery) {
            val q = assetQuery.trim()
            if (q.isBlank()) assets else assets.filter { it.name.contains(q, ignoreCase = true) }
        }
        val sections = remember(filteredAssets) { groupAssetsByCollection(filteredAssets) }
        val me = profile

        // **القفل**: كل الأصول (صور بروفايل · بانرات · items) مقفلة إلا الصورة المجانية،
        // والقفل يختفي فقط مع اشتراك epic فما فوق — **أو للأدمن**: الأدمن يفتح كل شيء
        // (حتى الأصول الحصرية للإدارة `required_plan = 'admin'`)، تماماً كما تسمح القاعدة.
        val epicUp = me != null && container.profile.planRank(me.effectivePlan) >= 2
        // حالة الاتصال feature حصرية لـ legendary فقط
        val legendaryUp = me != null && container.profile.planRank(me.effectivePlan) >= 3
        val isAdmin = me?.role == "admin"
        // الأدمن يرى كل الأصول مفتوحة (الخادم يوافقه داخل set_profile_asset)
        val canUseLocked = isAdmin || epicUp

        // قائمة كسولة: تُركَّب الصفوف الظاهرة فقط، فتفتح الشاشة فوراً بدل انتظار
        // تحميل مئات الصور دفعةً واحدة (كان التبديل بين الأنواع يتجمّد طويلاً).
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(horizontal = 14.dp),
        ) {
            item {
                Column {
                    // بحث داخل صور البروفايل والبانرات والأدوات بالاسم
                    OutlinedTextFieldBidi(
                        value = assetQuery,
                        onValueChange = { assetQuery = it },
                        label = stringResource(R.string.profile_assets_search),
                        singleLine = true,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (assets.isEmpty()) {
                            stringResource(R.string.profile_assets_loading)
                        } else {
                            stringResource(
                                R.string.profile_assets_count,
                                filteredAssets.size,
                                assets.size,
                            )
                        },
                        style = AppText.tiny,
                        color = AppColors.Muted,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextFieldBidi(
                        value = username,
                        onValueChange = { username = it },
                        label = stringResource(R.string.auth_username),
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextFieldBidi(
                        value = bio,
                        onValueChange = { if (it.length <= 400) bio = it },
                        label = stringResource(R.string.profile_about),
                        singleLine = false,
                        lines = 3,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextFieldBidi(
                        value = status,
                        onValueChange = { if (it.length <= 60) status = it },
                        label = stringResource(R.string.profile_custom_status),
                    )
                    Spacer(Modifier.height(10.dp))
                    // **حالة الاتصال الخضراء**: زر يطفئها ويشعلها، ومقفول إلا مع legendary 👑
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Switch(
                            checked = showOnline,
                            onCheckedChange = { on ->
                                if (!legendaryUp) {
                                    message = onlineLockedHint
                                } else {
                                    showOnline = on
                                    scope.launch {
                                        busy = true
                                        container.profile.setShowOnline(on)
                                            .onFailure { message = it.message }
                                        busy = false
                                    }
                                }
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppColors.OnGreen,
                                checkedTrackColor = AppColors.Green,
                                uncheckedThumbColor = AppColors.Muted,
                                uncheckedTrackColor = AppColors.Elevated2,
                                uncheckedBorderColor = AppColors.Border2,
                            ),
                        )
                        Text(
                            text = stringResource(
                                R.string.profile_online,
                            ) + if (showOnline) "" else stringResource(R.string.profile_offline_suffix),
                            style = AppText.body,
                            color = AppColors.Text,
                        )
                        if (!legendaryUp) {
                            Text("👑", style = AppText.body)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    GradientButton(
                        text = stringResource(R.string.common_save),
                        onClick = {
                            scope.launch {
                                busy = true
                                container.profile.updateMyProfile(
                                    username = username,
                                    bio = bio,
                                    customStatus = status,
                                    showOnline = if (legendaryUp) showOnline else null,
                                ).onSuccess {
                                    message = null
                                    onSaved()
                                }.onFailure { message = it.message }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    message?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = AppText.bodySmall, color = AppColors.Red)
                    }
                    Spacer(Modifier.height(18.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            label = stringResource(R.string.profile_avatar_item),
                            selected = kind == "avatar",
                            onClick = { kind = "avatar" },
                        )
                        FilterChip(
                            label = stringResource(R.string.profile_banner_section),
                            selected = kind == "banner",
                            onClick = { kind = "banner" },
                        )
                        FilterChip(
                            label = stringResource(R.string.plan_item_section),
                            selected = kind == "item",
                            onClick = { kind = "item" },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    if (!canUseLocked) {
                        Text(lockedHint, style = AppText.tiny, color = AppColors.Dim)
                    }
                    Spacer(Modifier.height(8.dp))
                    // معاينة الغلاف المختار حالياً (وتتحرّك إن كان GIF) كما ستظهر
                    // في الملف الشخصي تماماً — قبل أن يختار المستخدم غلافاً آخر.
                    if (isBanner) {
                        BannerPreviewCard(
                            url = me?.bannerUrl?.let { assetUrl(container, it) },
                            locked = !canUseLocked,
                            onLockedClick = { message = bannerLockedHint },
                            onClear = {
                                scope.launch {
                                    busy = true
                                    // 'none' = إعادة الغلاف إلى الافتراضي (مسموح للجميع)
                                    container.profile.setProfileAsset("banner", "none")
                                        .onSuccess { message = null }
                                        .onFailure { message = it.message }
                                    busy = false
                                }
                            },
                        )
                        Spacer(Modifier.height(8.dp))
                        if (!epicUp) {
                            Text(
                                text = stringResource(R.string.plan_locked_banner_note),
                                style = AppText.tiny,
                                color = AppColors.Dim,
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                }
            }

            if (assets.isEmpty()) {
                item {
                    EmptyState(text = stringResource(R.string.common_loading))
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                // المفاتيح بموقعها (لا بنص القسم) حتى لا تتصادم قسمان بنفس الاسم
                sections.forEachIndexed { sectionIndex, section ->
                    item(key = "h-$sectionIndex") {
                        AssetSectionHeader(title = section.title, count = section.assets.size)
                    }
                    section.assets.chunked(if (isBanner) bannerColumns else gridColumns)
                        .forEachIndexed { rowIndex, rowAssets ->
                        item(key = "r-$sectionIndex-$rowIndex") {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = GRID_SPACING.dp / 2),
                                horizontalArrangement = Arrangement.spacedBy(GRID_SPACING.dp),
                            ) {
                                rowAssets.forEach { asset ->
                                    // الصورة المجانية وحدها بلا قفل
                                    val isFreeAvatar = kind == "avatar" &&
                                        asset.fileUrl.contains("onefree", ignoreCase = true)
                                    val unlocked = isFreeAvatar || canUseLocked
                                    val selected = when (kind) {
                                        "avatar" -> me?.avatarUrl == asset.fileUrl
                                        "banner" -> me?.bannerUrl == asset.fileUrl
                                        else -> me?.equippedItemId == asset.name
                                    }
                                    Column(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height((if (isBanner) bannerCellHeight else cellHeight).dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(AppColors.Elevated)
                                            .border(
                                                1.dp,
                                                if (selected) AppColors.Green else AppColors.Border,
                                                RoundedCornerShape(12.dp),
                                            )
                                            // المقفول قابل للنقر أيضاً: نقره يُظهر سبب
                                            // القفل بدل أن يبدو كأنه معطّل بلا سبب
                                            .clickable {
                                                if (!unlocked) {
                                                    message = if (isBanner) bannerLockedHint else lockedHint
                                                } else {
                                                    scope.launch {
                                                        val value = if (kind == "item") asset.name else asset.fileUrl
                                                        busy = true
                                                        // القاعدة هي الحكم: قد ترفض أصلاً (خطة أو صلاحية)،
                                                        // فنُظهر سبب الرفض بدل أن يبدو وكأنه نجح بلا تغيير
                                                        container.profile.setProfileAsset(kind, value)
                                                            .onSuccess { message = null }
                                                            .onFailure { message = it.message }
                                                        busy = false
                                                    }
                                                }
                                            }
                                            .padding(4.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center,
                                    ) {
                                        if (isBanner) {
                                            // خلية غلاف عريضة 16:9، وGIF فيها **يتحرّك**
                                            Box(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .fillMaxWidth(),
                                            ) {
                                                BannerThumb(
                                                    url = assetUrl(container, asset.fileUrl),
                                                    animate = true,
                                                    modifier = Modifier.matchParentSize(),
                                                )
                                                if (asset.animated) {
                                                    Text(
                                                        "🎞",
                                                        style = AppText.tiny,
                                                        modifier = Modifier
                                                            .align(Alignment.TopEnd)
                                                            .padding(2.dp),
                                                    )
                                                }
                                                if (!unlocked) {
                                                    Box(
                                                        modifier = Modifier
                                                            .matchParentSize()
                                                            .background(AppColors.Surface.copy(alpha = 0.55f)),
                                                        contentAlignment = Alignment.Center,
                                                    ) {
                                                        Text("🔒", style = AppText.bodySmall)
                                                    }
                                                }
                                            }
                                        } else {
                                            Box(contentAlignment = Alignment.Center) {
                                                AssetThumb(
                                                    url = assetUrl(container, asset.fileUrl),
                                                    size = 40.dp,
                                                )
                                                if (!unlocked) {
                                                    Text("🔒", style = AppText.tiny)
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            asset.name,
                                            style = AppText.tiny,
                                            color = if (selected) AppColors.Green else AppColors.Muted,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                            textAlign = TextAlign.Center,
                                            lineHeight = AppText.tiny.fontSize * 1.05f,
                                        )
                                    }
                                }
                                // خلايا فارغة تكمل الصف الأخير حتى تبقى الأعمدة متساوية
                                repeat((if (isBanner) bannerColumns else gridColumns) - rowAssets.size) {
                                    Spacer(Modifier.weight(1f))
                                }
                            }
                        }
                    }
                }
            }

            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

/**
 * بطاقة **معاينة الغلاف** أعلى تبويب «الأغلفة» في تعديل الملف الشخصي:
 * تعرض الغلاف المختار حالياً بعرض الشاشة وبنسبة 16:9، وإن كان GIF **يتحرّك**
 * كما سيظهر في الملف الشخصي تماماً. وحين يكون الحساب بلا اشتراك تُغطّى البطاقة
 * بقفل 🔒 ونقرها يُظهر سبب القفل.
 */
@Composable
private fun BannerPreviewCard(
    url: String?,
    locked: Boolean,
    onLockedClick: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.profile_banner_preview),
            style = AppText.tiny,
            color = AppColors.Muted,
        )
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.Elevated2)
                .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .then(if (locked) Modifier.clickable { onLockedClick() } else Modifier),
            ) {
                if (url.isNullOrBlank()) {
                    Box(Modifier.matchParentSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = stringResource(R.string.profile_banner_none),
                            style = AppText.tiny,
                            color = AppColors.Dim,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 18.dp),
                        )
                    }
                } else {
                    BannerThumb(
                        url = url,
                        animate = true,
                        radius = 0.dp,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                if (locked) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(AppColors.Surface.copy(alpha = 0.62f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🔒", style = AppText.sectionTitle)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = stringResource(R.string.plan_locked_banner_note),
                                style = AppText.tiny,
                                color = AppColors.Text,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 20.dp),
                            )
                        }
                    }
                }
                if (!url.isNullOrBlank()) {
                    Text(
                        text = stringResource(R.string.profile_banner_clear),
                        style = AppText.tiny,
                        color = AppColors.Text,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.Surface.copy(alpha = 0.82f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { onClear() },
                    )
                }
            }
        }
    }
}

/** حقل نصي بسيط (نلفّه لتقليل التكرار مع دعم RTL التلقائي). */
@Composable
private fun OutlinedTextFieldBidi(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    singleLine: Boolean = true,
    lines: Int = 1,
) {
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
        singleLine = singleLine,
        minLines = if (singleLine) 1 else lines,
        modifier = Modifier.fillMaxWidth(),
    )
}

// تباعد ورقة الأصول؛ عدد الأعمدة وارتفاع الخلية يُحسبان من عرض شاشة الهاتف (Sizing.kt).
private const val GRID_SPACING = 6

/** ملف مستخدم عام (يُفتح من الشات أو من بطاقة المبدع). */
@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    onMessage: (Profile) -> Unit,
    onAddFriend: (Profile) -> Unit,
    onOpenItem: (ContentItem) -> Unit,
) {
    val container = rememberContainer()
    var profile by remember { mutableStateOf<Profile?>(null) }
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var card by remember { mutableStateOf<com.addonstation.mobile.data.model.CreatorCard?>(null) }
    var friendEntry by remember { mutableStateOf<com.addonstation.mobile.data.model.FriendEntry?>(null) }
    val friendState = friendEntry?.status ?: "none"

    LaunchedEffect(userId) {
        profile = container.profile.loadProfile(userId)
        items = container.content.byCreator(userId, 20)
        card = container.creators.creatorStats(userId)
        friendEntry = container.profile.friendshipState(userId)
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.profile_open_profile), onBack = onBack)
        ContentColumn(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            val me = profile ?: run {
                LoadingBox(text = stringResource(R.string.common_loading))
                return@ContentColumn
            }
            // مربّع البروفايل راكب على حافة الغلاف: نصفه داخله ونصفه خارجه
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(140.dp + 36.dp),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(140.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(AppColors.Elevated2),
                ) {
                    assetUrl(container, me.bannerUrl)?.let { banner ->
                        com.addonstation.mobile.ui.components.CoverImage(
                            url = banner,
                            radius = 20.dp,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 16.dp)) {
                    AvatarImage(url = assetUrl(container, me.avatarUrl), size = 72.dp, borderColor = AppColors.Surface, borderWidth = 3.dp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    me.displayName ?: me.username ?: "",
                    style = AppText.welcomeName,
                    color = AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                RankBadge(role = me.role, plan = me.effectivePlan)
            }
            Text("@${me.username ?: ""}", style = AppText.tiny, color = AppColors.Muted)

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GradientButton(
                    text = stringResource(R.string.profile_message),
                    onClick = { onMessage(me) },
                    modifier = Modifier.weight(1f),
                )
                val friendLabel = when (friendState) {
                    "friends" -> stringResource(R.string.profile_friends)
                    "sent" -> stringResource(R.string.profile_friend_sent)
                    else -> stringResource(R.string.profile_add_friend)
                }
                GhostButton(
                    text = friendLabel,
                    onClick = {
                        if (friendState == "none") onAddFriend(me)
                    },
                    enabled = friendState == "none",
                    modifier = Modifier.weight(1f),
                    borderColor = AppColors.Blue.copy(alpha = 0.5f),
                )
            }

            SectionHeader(title = stringResource(R.string.profile_about_me), leadingEmoji = "📝")
            GlassCard {
                Text(
                    me.bio?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_about_default),
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                )
            }

            SectionHeader(title = stringResource(R.string.profile_level_reputation), leadingEmoji = "🏆")
            GlassCard {
                ThinProgress(progress = (me.userLevel.coerceAtMost(50)) / 50f)
                Text(
                    stringResource(R.string.profile_level_active, me.userLevel),
                    style = AppText.tiny,
                    color = AppColors.Muted,
                )
            }

            SectionHeader(title = stringResource(R.string.profile_statistics), leadingEmoji = "📊")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatBox(stringResource(R.string.profile_stats_downloads), (card?.downloads ?: 0).toString(), Modifier.weight(1f))
                StatBox(stringResource(R.string.profile_stat_worlds), (card?.worlds ?: 0).toString(), Modifier.weight(1f))
                StatBox(stringResource(R.string.profile_stat_published), (card?.published ?: 0).toString(), Modifier.weight(1f))
            }

            SectionHeader(title = stringResource(R.string.profile_tab_content), leadingEmoji = "📦")
            if (items.isEmpty()) {
                EmptyState(stringResource(R.string.profile_my_content_empty))
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(items, key = { it.id }) { item ->
                        ContentCard(
                            title = item.title,
                            coverUrl = assetUrl(container, item.coverUrl),
                            planLabel = planLabelOf(item.plan),
                            metaLine = metaOf(item),
                            onClick = { onOpenItem(item) },
                            large = true,
                        )
                    }
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/** صف صديق (يُستخدم في قائمة الأصدقاء). */
@Composable
fun FriendRow(profile: Profile?, fallback: String, avatar: String?, onClick: () -> Unit) {
    val container = rememberContainer()
    ProfileRow(
        profile = profile,
        fallbackName = fallback,
        avatarUrl = assetUrl(container, avatar),
        onClick = onClick,
    )
}
