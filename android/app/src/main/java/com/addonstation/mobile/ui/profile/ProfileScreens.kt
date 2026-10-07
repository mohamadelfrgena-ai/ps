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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ProfileLoadStatus
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
import com.addonstation.mobile.ui.components.CoinIcon
import com.addonstation.mobile.ui.components.ContentCard
import com.addonstation.mobile.ui.components.CoverImage
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val profileLoadStatus by container.state.profileLoadStatus.collectAsState()
    val scope = rememberCoroutineScope()

    var profileRetryTick by remember { mutableStateOf(0) }
    var tab by remember { mutableStateOf(0) }
    var myContent by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var favourites by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var downloads by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    // الجلسة قد تكون صالحة بينما الملف الشخصي لم يكتمل تحميله بعد؛ لا نحوّل
    // هذه الحالة المؤقتة إلى شاشة تسجيل دخول، بل نجلب الملف ونتيح إعادة المحاولة.
    LaunchedEffect(signedIn, profileRetryTick) {
        if (signedIn && container.state.currentProfile == null) {
            try {
                withContext(Dispatchers.IO) { container.profile.ensureProfileLoaded() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // حالة الفشل محفوظة في AppState ويظهر زر إعادة المحاولة أدناه.
            }
        }
    }

    // القوائم الثلاث تُجلب **مرة واحدة وبالتوازي**: بطاقات الإحصائيات الثلاث
    // (التنزيلات · محتواي · المفضلة) تقرأها كلّها من اللحظة الأولى، فلا أصفار
    // مؤقتة ولا طلبات متسلسلة عند كل لمسة تبويب.
    LaunchedEffect(profile?.id, reloadTick) {
        val me = profile ?: return@LaunchedEffect
        loading = true
        failed = false
        try {
            coroutineScope {
                val mine = async(Dispatchers.IO) { container.content.byCreator(me.id) }
                val favs = async(Dispatchers.IO) { container.content.favorites() }
                val downs = async(Dispatchers.IO) { container.content.downloads() }
                myContent = mine.await()
                favourites = favs.await()
                downloads = downs.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    if (!signedIn) {
        LaunchedEffect(signedIn) { onLogin() }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            LoadingBox(text = stringResource(R.string.msg_sign_in_needed))
        }
        return
    }
    if (profile == null) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            if (profileLoadStatus == ProfileLoadStatus.IDLE || profileLoadStatus == ProfileLoadStatus.LOADING) {
                LoadingBox(text = stringResource(R.string.common_loading))
            } else {
                com.addonstation.mobile.ui.components.ErrorState(
                    onRetry = { profileRetryTick += 1 },
                )
            }
        }
        return
    }

    val me = profile!!
    val level = me.userLevel
    val xpProgress = level.coerceAtMost(50) / 50f
    val xpPercent = (xpProgress * 100).toInt()

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.nav_profile),
            // الرصيد معروض على البانر كما في التصميم ⇒ لا نكرّره في الشريط
            showWallet = false,
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

            // البانر + الصورة: نصف الصورة على الحافة السفلية للبانر (كما في التصميم)
            val bannerHeight = 132.dp
            val avatarSize = 88.dp
            Box(Modifier.fillMaxWidth().height(bannerHeight + avatarSize / 2 + 12.dp)) {
                ProfileBanner(height = bannerHeight) {
                    assetUrl(container, me.bannerUrl)?.let { banner ->
                        CoverImage(url = banner, radius = 20.dp, modifier = Modifier.fillMaxSize())
                    }
                    // رصيد AC أعلى يمين البانر
                    Row(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(50))
                            .background(AppColors.Bg.copy(alpha = 0.45f))
                            .clickable { onOpenWallet() }
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CoinIcon(size = 14.dp)
                        Text(
                            "${wallet?.balance ?: 0} AC",
                            style = AppText.small,
                            color = AppColors.Gold,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                Box(Modifier.align(Alignment.BottomStart).padding(start = 4.dp)) {
                    ProfileAvatar(level = level, size = avatarSize) {
                        AvatarImage(url = assetUrl(container, me.avatarUrl), size = avatarSize - 8.dp)
                    }
                }
            }

            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    me.displayName ?: me.username ?: "",
                    style = AppText.welcomeName,
                    color = AppColors.Text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
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
            GradientButton(
                text = stringResource(R.string.profile_edit),
                onClick = onOpenEdit,
                icon = Icons.Filled.Edit,
                modifier = Modifier.fillMaxWidth(),
            )
            // أزرار الأدوار تبقى في المتناول تحت «تعديل الملف» (ترقية · لوحة إدارة · استوديو)
            if (me.effectivePlan == "free" || me.role == "admin" || me.role == "creator") {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (me.effectivePlan == "free") {
                        GhostButton(
                            text = stringResource(R.string.profile_upgrade),
                            onClick = onOpenPlans,
                            icon = Icons.Filled.WorkspacePremium,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (me.role == "admin") {
                        GhostButton(
                            text = stringResource(R.string.profile_dashboard),
                            onClick = onOpenAdmin,
                            icon = Icons.Filled.AdminPanelSettings,
                            modifier = Modifier.weight(1f),
                        )
                    } else if (me.role == "creator") {
                        GhostButton(
                            text = stringResource(R.string.home_creator_open),
                            onClick = onOpenStudio,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            ProfileXpBar(
                progress = xpProgress,
                label = stringResource(R.string.profile_xp_progress, level.toString(), xpPercent),
            )

            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ProfileStatCard(
                    value = downloads.size.toString(),
                    label = stringResource(R.string.profile_stats_downloads),
                    modifier = Modifier.weight(1f),
                )
                ProfileStatCard(
                    value = myContent.size.toString(),
                    label = stringResource(R.string.profile_stats_content),
                    modifier = Modifier.weight(1f),
                )
                ProfileStatCard(
                    value = favourites.size.toString(),
                    label = stringResource(R.string.profile_stats_favorites),
                    modifier = Modifier.weight(1f),
                )
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
                0 -> MyAccountTab(
                    me = me,
                    onOpenPlans = onOpenPlans,
                    onOpenEdit = onOpenEdit,
                    onOpenSettings = onOpenSettings,
                    onSignOut = {
                        scope.launch {
                            container.auth.signOut()
                            container.state.setSignedIn(false)
                            onLogin()
                        }
                    },
                )
                1 -> ContentGrid(
                    items = myContent,
                    loading = loading,
                    failed = failed,
                    onRetry = { reloadTick += 1 },
                    emptyText = stringResource(R.string.profile_my_content_empty),
                    onClick = onOpenItem,
                )
                2 -> ContentGrid(
                    items = favourites,
                    loading = loading,
                    failed = failed,
                    onRetry = { reloadTick += 1 },
                    emptyText = stringResource(R.string.profile_favorites_empty),
                    onClick = onOpenItem,
                )
                else -> PlanTab(me = me, onOpenPlans = onOpenPlans)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * تبويب «حول» في ملفي: النبذة، ثم قسم الحساب — بطاقة الخطّة الذهبية وشبكة ٢×٢
 * (حالة الاتصال · الأفاتار/الأداة · البريد · كل الإعدادات).
 */
@Composable
private fun MyAccountTab(
    me: Profile,
    onOpenPlans: () -> Unit,
    onOpenEdit: () -> Unit,
    onOpenSettings: () -> Unit,
    onSignOut: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val lockedHint = stringResource(R.string.plan_locked_online)
    // حالة الاتصال ميزة حصرية لـ legendary 👑 (كما في شاشة التعديل وكما يفرض الخادم)
    val legendaryUp = container.profile.planRank(me.effectivePlan) >= 3
    var online by remember(me.id) { mutableStateOf(me.showOnlineStatus) }
    var note by remember { mutableStateOf<String?>(null) }
    val toggle: (Boolean) -> Unit = { next ->
        if (!legendaryUp) {
            note = lockedHint
        } else {
            online = next
            scope.launch {
                container.profile.setShowOnline(next).onFailure { note = it.message }
            }
        }
    }

    SectionHeader(title = stringResource(R.string.profile_about))
    GlassCard {
        Text(
            me.bio?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_about_default),
            style = AppText.bodySmall,
            color = AppColors.Muted,
        )
    }

    SectionHeader(title = stringResource(R.string.profile_account_info))
    ProfilePlanCard(
        planLabel = planLabelOf(me.effectivePlan),
        subtitle = stringResource(R.string.profile_current_plan),
        onManage = onOpenPlans,
    )
    Spacer(Modifier.height(10.dp))
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileAccountTile(
                icon = "🙈",
                title = stringResource(R.string.profile_online_status),
                subtitle = if (online) stringResource(R.string.profile_online)
                else stringResource(R.string.profile_offline),
                onClick = { toggle(!online) },
                trailing = {
                    Switch(
                        checked = online,
                        onCheckedChange = { toggle(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AppColors.OnGreen,
                            checkedTrackColor = AppColors.Green,
                            uncheckedThumbColor = AppColors.Muted,
                            uncheckedTrackColor = AppColors.Elevated2,
                            uncheckedBorderColor = AppColors.Border2,
                        ),
                    )
                },
                modifier = Modifier.weight(1f),
            )
            ProfileAccountTile(
                icon = "🎽",
                title = stringResource(R.string.profile_avatar_item),
                subtitle = if (me.equippedItemId.isNullOrBlank()) stringResource(R.string.profile_item_none)
                else stringResource(R.string.profile_item_equipped),
                onClick = onOpenEdit,
                trailingImageUrl = equippedItemUrl(container, me.equippedItemId),
                modifier = Modifier.weight(1f),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileAccountTile(
                icon = "✉️",
                title = stringResource(R.string.profile_email),
                subtitle = container.supabase.session()?.email ?: "—",
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
            ProfileAccountTile(
                icon = "⚙️",
                title = stringResource(R.string.profile_full_settings),
                subtitle = stringResource(R.string.profile_all_options),
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            )
        }
    }
    note?.let {
        Spacer(Modifier.height(8.dp))
        Text(it, style = AppText.tiny, color = AppColors.Muted, modifier = Modifier.fillMaxWidth())
    }

    Spacer(Modifier.height(12.dp))
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
                                    // **إطار الاختيار**: حدّ أخضر ساطع + هالة خضراء + خلفية
                                    // مُلوّنة + علامة ✓ في الزاوية — ليعرف المستخدم فوراً
                                    // أي أصل اختاره (أفاتار · غلاف · أداة) بلا لبس.
                                    val cellShape = RoundedCornerShape(12.dp)
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height((if (isBanner) bannerCellHeight else cellHeight).dp),
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .matchParentSize()
                                                // هالة خضراء خفيفة تحيط بالخلية المختارة
                                                .then(
                                                    if (selected) Modifier.shadow(
                                                        10.dp,
                                                        cellShape,
                                                        ambientColor = AppColors.Green,
                                                        spotColor = AppColors.Green,
                                                    ) else Modifier
                                                )
                                                .clip(cellShape)
                                                .background(
                                                    if (selected) AppColors.Green.copy(alpha = 0.14f)
                                                    else AppColors.Elevated
                                                )
                                                .border(
                                                    if (selected) 2.5.dp else 1.dp,
                                                    if (selected) AppColors.Green else AppColors.Border,
                                                    cellShape,
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
                                        if (selected) {
                                            // علامة ✓ واضحة فوق زاوية الخلية المختارة
                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopStart)
                                                    .padding(3.dp)
                                                    .size(20.dp)
                                                    .background(AppColors.Green, CircleShape)
                                                    .border(1.dp, AppColors.Surface, CircleShape),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text(
                                                    "✓",
                                                    style = AppText.tiny,
                                                    color = AppColors.OnGreen,
                                                )
                                            }
                                        }
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
    /**
     * فتح **تعديل الملف الشخصي** — يظهر بدل «مراسلة» و«إضافة صديق» حين يفتح
     * المستخدم ملفه هو (لا معنى لأن يراسل نفسه أو يضيفها صديقاً).
     */
    onEditProfile: () -> Unit = {},
    /**
     * الحظر (سياسة Google Play للمحتوى): يُعرض على ملفات الآخرين فقط.
     * الحظر متقابل ويُخفى كل كلامه فوراً، والإلغاء من هنا نفسه.
     */
    onBlock: (Profile) -> Unit = {},
    onUnblock: (Profile) -> Unit = {},
) {
    val container = rememberContainer()
    // هل هذا ملفي أنا؟ (نفس مقارنة بطاقة الشات: معرّف الجلسة = معرّف الملف)
    val isMyProfile = container.supabase.session()?.userId == userId
    var profile by remember { mutableStateOf<Profile?>(null) }
    var items by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var card by remember { mutableStateOf<com.addonstation.mobile.data.model.CreatorCard?>(null) }
    var friendEntry by remember { mutableStateOf<com.addonstation.mobile.data.model.FriendEntry?>(null) }
    // هل أنا حاظر صاحب هذا الملف؟ (يُقرأ من القاعدة: جدول user_blocks)
    var blocked by remember(userId) { mutableStateOf(false) }
    val friendState = friendEntry?.status ?: "none"

    LaunchedEffect(userId) {
        profile = container.profile.loadProfile(userId)
        items = container.content.byCreator(userId, 20)
        card = container.creators.creatorStats(userId)
        friendEntry = container.profile.friendshipState(userId)
        blocked = container.chat.blockedIds().contains(userId)
    }

    ContentColumn(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        val me = profile ?: run {
            LoadingBox(text = stringResource(R.string.common_loading))
            return@ContentColumn
        }

        Spacer(Modifier.height(12.dp))
        // البانر + الصورة الراكبة على حافته السفلية، وزرّ الرجوع فوقه
        val bannerHeight = 150.dp
        val avatarSize = 92.dp
        Box(Modifier.fillMaxWidth().height(bannerHeight + avatarSize / 2 + 12.dp)) {
            ProfileBanner(height = bannerHeight) {
                assetUrl(container, me.bannerUrl)?.let { banner ->
                    CoverImage(url = banner, radius = 20.dp, modifier = Modifier.fillMaxSize())
                }
                // زرّ الرجوع: رجوعٌ واحد نظيف عبر back() في التنقّل
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(AppColors.Bg.copy(alpha = 0.45f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.common_back),
                        tint = AppColors.Text,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Box(Modifier.align(Alignment.BottomStart).padding(start = 4.dp)) {
                ProfileAvatar(level = me.userLevel, size = avatarSize) {
                    AvatarImage(url = assetUrl(container, me.avatarUrl), size = avatarSize - 8.dp)
                }
            }
        }

        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                me.displayName ?: me.username ?: "",
                style = AppText.welcomeName,
                color = AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            RankBadge(role = me.role, plan = me.effectivePlan)
        }
        Text(
            "@${me.username ?: ""} · " + if (me.showOnlineStatus) stringResource(R.string.profile_online)
            else stringResource(R.string.profile_offline),
            style = AppText.tiny,
            color = AppColors.Muted,
        )

        Spacer(Modifier.height(12.dp))
        if (isMyProfile) {
            // ملفي أنا: زرّ واحد يعرض الشاشة كاملة بدل مراسلة النفس
            GradientButton(
                text = stringResource(R.string.profile_edit_mine),
                onClick = onEditProfile,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
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
            // الحظر: صفّ مستقل أسفل الأزرار — لا يزاحم «مراسلة» و«إضافة صديق»،
            // وواضح النص (سياسة جوجل تطلب وسيلة حظر ظاهرة لا مخفية في الإعدادات).
            Spacer(Modifier.height(10.dp))
            GhostButton(
                text = if (blocked) stringResource(R.string.block_user_undo)
                else stringResource(R.string.block_user),
                onClick = {
                    if (blocked) {
                        onUnblock(me)
                        blocked = false
                    } else {
                        onBlock(me)
                        blocked = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                borderColor = AppColors.Red.copy(alpha = 0.5f),
            )
        }

        Spacer(Modifier.height(12.dp))
        SectionHeader(title = stringResource(R.string.profile_about_me), leadingEmoji = "📝")
        GlassCard {
            Text(
                me.bio?.takeIf { it.isNotBlank() } ?: stringResource(R.string.profile_about_default),
                style = AppText.bodySmall,
                color = AppColors.Muted,
            )
        }

        SectionHeader(title = stringResource(R.string.profile_level_reputation), leadingEmoji = "🏆")
        ProfileXpBar(
            progress = me.userLevel.coerceAtMost(50) / 50f,
            label = stringResource(R.string.profile_level_active, me.userLevel),
        )

        SectionHeader(title = stringResource(R.string.profile_statistics), leadingEmoji = "📊")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            ProfileStatCard(
                value = (card?.downloads ?: 0).toString(),
                label = stringResource(R.string.profile_stats_downloads),
                modifier = Modifier.weight(1f),
            )
            ProfileStatCard(
                value = (card?.worlds ?: 0).toString(),
                label = stringResource(R.string.profile_stat_worlds),
                modifier = Modifier.weight(1f),
            )
            ProfileStatCard(
                value = (card?.published ?: 0).toString(),
                label = stringResource(R.string.profile_stat_published),
                modifier = Modifier.weight(1f),
            )
        }

        SectionHeader(title = stringResource(R.string.profile_content_section), leadingEmoji = "📦")
        if (items.isEmpty()) {
            EmptyState(stringResource(R.string.profile_no_content_published))
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
