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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.addonstation.mobile.data.model.ProfileStats
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
import com.addonstation.mobile.ui.components.ProfileRow
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.RowItem
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.SpecAboutCard
import com.addonstation.mobile.ui.components.SpecDangerButton
import com.addonstation.mobile.ui.components.SpecFillButton
import com.addonstation.mobile.ui.components.SpecActionRow
import com.addonstation.mobile.ui.components.SpecBadgeChip
import com.addonstation.mobile.ui.components.SpecBannerIconButton
import com.addonstation.mobile.ui.components.SpecCard
import com.addonstation.mobile.ui.components.SpecGridTile
import com.addonstation.mobile.ui.components.SpecHiddenWalletCard
import com.addonstation.mobile.ui.components.SpecOutlineButton
import com.addonstation.mobile.ui.components.SpecSmallCaps
import com.addonstation.mobile.ui.components.SpecIdentityCard
import com.addonstation.mobile.ui.components.SpecLevelCard
import com.addonstation.mobile.ui.components.SpecProfileAvatar
import com.addonstation.mobile.ui.components.SpecProfileBanner
import com.addonstation.mobile.ui.components.SpecStatsStrip
import com.addonstation.mobile.ui.components.SpecWalletCard
import com.addonstation.mobile.ui.components.specMemberSince
import com.addonstation.mobile.ui.components.specStatusOf
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.components.ThinProgress
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.NumberFormat
import java.util.Locale

/** شاشة الملف الشخصي (تتطلب تسجيل الدخول — وإلا ينتقل المستخدم لتسجيل الدخول). */
/**
 * الشاشة A — «ملفي» (تبويب الملف الشخصي) — على وثيقة `SPEC_PROFILES.md` §3 حرفياً:
 *   ١ البانر (زرّ الرجوع وحده) ٢ صفّ الأفاتار + «تعديل الملف» محاذى لأسفله
 *   ٣ بطاقة الهوية ٤ بطاقة المحفظة (تحت الهوية مباشرة) ٥ شريط الإحصاءات
 *   ٦ بطاقة المستوى ٧ بطاقة النبذة ٨ شبكة 2×2 ٩ «تسجيل الخروج».
 * الإعدادات شاشة مستقلة (تبويب الإعدادات) — لا تُعرض هنا.
 *
 * الأرقام (أصدقاء · تنزيلات · مفضّلة · المستوى · سلسلة الأيام · مكافأة اليوم)
 * تأتي من دالة الخادم `profile_stats` ⇒ صحيحة لحسابي، وبلا استعلامات محجوبة.
 */
@Composable
fun ProfileScreen(
    onSignedOut: () -> Unit,
    onBack: () -> Unit,
    onOpenEdit: () -> Unit,
    /** مدخل لوحة الإدارة — يظهر للأدمن/المشرف وحدهما (بند إضافي غير مخالف). */
    onOpenAdmin: () -> Unit,
    onOpenWallet: () -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenOrders: () -> Unit,
    onOpenMyContent: () -> Unit,
    /** شاشة C: «عرض كما يراني الآخرون». */
    onViewAsOthers: () -> Unit,
    scrollToTopRequest: Int = 0,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) scrollState.animateScrollTo(0)
    }
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val profileLoadStatus by container.state.profileLoadStatus.collectAsState()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val dailySuccessTemplate = stringResource(R.string.ac_redeem_success, "%s")
    val dailyAlreadyClaimed = stringResource(R.string.ac_claimed)
    val dailySignInNeeded = stringResource(R.string.msg_sign_in_needed)
    val dailyGenericError = stringResource(R.string.msg_generic_error)

    var profileRetryTick by remember { mutableStateOf(0) }
    var statsTick by remember { mutableStateOf(0) }
    var stats by remember { mutableStateOf<ProfileStats?>(null) }
    var claiming by remember { mutableStateOf(false) }

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

    // الإحصاءات + المحفظة معاً (تُعاد كلّها بعد استلام المكافأة)
    LaunchedEffect(signedIn, profile?.id, statsTick) {
        if (!signedIn) return@LaunchedEffect
        val id = profile?.id ?: return@LaunchedEffect
        container.profile.refreshWallet()
        stats = container.profile.stats(id)
    }

    // goTab يوجّه غير المسجّل إلى شاشة الدخول قبل فتح هذا المسار.
    if (!signedIn) return
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
    val status = specStatusOf(me, isMe = true)
    val level = stats?.level ?: me.userLevel
    val xpInLevel = stats?.xpInLevel ?: 0
    val balance = wallet?.balance ?: 0
    val streak = stats?.streak ?: 0
    val dailyClaimed = stats?.dailyClaimed ?: false

    fun claimDaily() {
        if (claiming) return
        scope.launch {
            claiming = true
            try {
                val daily = runCatching {
                    container.ac.tasks()
                        .firstOrNull { it.frequency == "daily" || it.frequency == "streak" }
                        ?.id
                }.getOrNull() ?: "daily_login"
                container.ac.claimTask(daily)
                    .onSuccess { reward ->
                        val amount = NumberFormat.getIntegerInstance(Locale.US).format(reward)
                        snackbarHostState.showSnackbar(dailySuccessTemplate.format(amount))
                    }
                    .onFailure { error ->
                        val raw = error.message.orEmpty()
                        val normalized = raw.lowercase(Locale.ROOT)
                        val message = when {
                            normalized.contains("already_claimed") || normalized.contains("max_claims") -> dailyAlreadyClaimed
                            normalized.contains("sign_in_required") -> dailySignInNeeded
                            raw.isBlank() -> dailyGenericError
                            else -> raw
                        }
                        snackbarHostState.showSnackbar(message)
                    }
                // الحالة الحقيقية («هل استُلمت اليوم؟») تُقرأ من الخادم لا من الزرّ
                statsTick += 1
            } finally {
                claiming = false
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .imePadding()
                    .verticalScroll(scrollState),
        ) {
            // ١) البانر — زرّ الرجوع وحده (بلا «المزيد» في هذه الشاشة)
            SpecProfileBanner(
                bannerUrl = assetUrl(container, me.bannerUrl),
                onBack = onBack,
            )

            // ٢) صفّ الأفاتار: يركب على البانر 46dp، و«تعديل الملف» محاذى لأسفل الأفاتار
            Box(Modifier.fillMaxWidth().height(Spec.avatarLg - Spec.avatarOverlap)) {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = Spec.screenPad),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(Modifier.offset(y = -(Spec.avatarOverlap / 2))) {
                        SpecProfileAvatar(
                            avatarUrl = assetUrl(container, me.avatarUrl),
                            status = status,
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    SpecFillButton(
                        text = stringResource(R.string.profile_edit),
                        onClick = onOpenEdit,
                        buttonHeight = 44.dp,
                        horizontalPadding = 20.dp,
                    )
                }
            }

            Column(Modifier.padding(horizontal = Spec.screenPad)) {
                // ٣) بطاقة الهوية
                Spacer(Modifier.height(12.dp))
                SpecIdentityCard(profile = me, status = status, level = level)

                // مدخل الشاشة C («عرض كما يراني الآخرون») — بند الوثيقة §5
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable(onClick = onViewAsOthers),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        tint = Spec.blueLink,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        stringResource(R.string.profile_view_as_others),
                        style = Spec.link,
                        color = Spec.blueLink,
                    )
                }

                // ٤) بطاقة المحفظة — تحت بطاقة الهوية مباشرة (ولمسها يفتح شاشة المحفظة)
                Spacer(Modifier.height(12.dp))
                SpecWalletCard(
                    balance = balance,
                    streak = streak,
                    dailyClaimed = dailyClaimed,
                    claiming = claiming,
                    onOpenWallet = onOpenWallet,
                    onClaim = { claimDaily() },
                )
                // ٥) شريط الإحصاءات
                Spacer(Modifier.height(12.dp))
                SpecStatsStrip(
                    friends = stats?.friends ?: 0,
                    downloads = stats?.downloads ?: 0,
                    favorites = stats?.favorites ?: 0,
                )

                // ٦) بطاقة المستوى
                Spacer(Modifier.height(12.dp))
                SpecLevelCard(level = level, xpInLevel = xpInLevel)

                // ٧) بطاقة النبذة («عضو منذ» داخلها)
                Spacer(Modifier.height(12.dp))
                SpecAboutCard(bio = me.bio, memberSince = specMemberSince(me.createdAt))

                // ٨) شبكة 2×2 (فراغ 10 · هامش علوي 12)
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpecGridTile(
                        stringResource(R.string.profile_tab_favorites),
                        onClick = onOpenFavorites,
                        modifier = Modifier.weight(1f),
                    )
                    SpecGridTile(
                        stringResource(R.string.profile_stats_downloads),
                        onClick = onOpenDownloads,
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SpecGridTile(
                        stringResource(R.string.profile_orders),
                        onClick = onOpenOrders,
                        modifier = Modifier.weight(1f),
                    )
                    SpecGridTile(
                        stringResource(R.string.profile_stats_content),
                        onClick = onOpenMyContent,
                        modifier = Modifier.weight(1f),
                    )
                }

                // لوحة الإدارة: مدخل لا بديل عنه للأدمن/المشرف (الوثيقة لا تذكره،
                // وإخفاؤه يقطع الطريق الوحيد إلى اللوحة — لذا يظهر لهما وحدهما).
                if (me.isStaff) {
                    Spacer(Modifier.height(10.dp))
                    SpecGridTile(
                        stringResource(R.string.profile_dashboard),
                        onClick = onOpenAdmin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                // ٩) تسجيل الخروج
                Spacer(Modifier.height(16.dp))
                SpecDangerButton(
                    text = stringResource(R.string.profile_logout),
                    onClick = {
                        scope.launch {
                            container.auth.signOut()
                            container.state.setSignedIn(false)
                            // يُلغي توكن الجهاز من الحساب (لا تصله إشعارات حساب آخر)
                            com.addonstation.mobile.push.PushRegistrar
                                .onSessionChanged(container.appContext, false)
                            onSignedOut()
                        }
                    },
                )
                Spacer(Modifier.height(96.dp))
            }
        }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { data ->
            Snackbar(
                containerColor = AppColors.AuthSurfaceHigh,
                contentColor = AppColors.AuthText,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(data.visuals.message, style = AppText.bodySmall, color = AppColors.AuthText)
            }
        }
    }
}

/**
 * شبكة محتوى داخل «ملفي» (محتواي · المفضّلة): صفّان في الصف الواحد بنفس بطاقة
 * المتجر. كانت الشاشة تنادي ContentGrid وهي غير معرَّفة أصلاً ⇒ لم يُصرَّف
 * المشروع؛ وهذه نسختها الكاملة.
 *
 * ملاحظة مهمة: الشبكة الكسولة داخل عمود قابل للتمرير تحتاج **ارتفاعاً ثابتاً**
 * و`userScrollEnabled = false` وإلا انهار القياس وقت التشغيل (نفس حلّ المتجر).
 */
@Composable
private fun ContentGrid(
    items: List<ContentItem>,
    loading: Boolean,
    failed: Boolean,
    onRetry: () -> Unit,
    emptyText: String,
    onClick: (ContentItem) -> Unit,
) {
    val container = rememberContainer()
    when {
        loading -> LoadingBox(text = stringResource(R.string.common_loading))
        failed -> com.addonstation.mobile.ui.components.ErrorState(onRetry = onRetry)
        items.isEmpty() -> EmptyState(emptyText)
        else -> LazyVerticalGrid(
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
                    onClick = { onClick(item) },
                )
            }
        }
    }
}

/**
 * تبويب «الخطة» في ملفي: الخطّة الحالية، وإن كان مجانياً فدعوة مباشرة إلى
 * الاشتراك (نفس نبرة إشعار العرض: «أفضل وأجدد الإضافات والعوالم»).
 */
@Composable
private fun PlanTab(me: Profile, onOpenPlans: () -> Unit) {
    val free = me.effectivePlan == "free"
    SectionHeader(title = stringResource(R.string.profile_tab_plan))
    GlassCard {
        Text(
            planLabelOf(me.effectivePlan),
            style = AppText.sectionTitle,
            color = if (free) AppColors.Muted else AppColors.Gold,
        )
        Text(
            when (me.effectivePlan) {
                "legendary" -> stringResource(R.string.plan_current_legendary)
                "epic" -> stringResource(R.string.plan_current_epic)
                else -> stringResource(R.string.plan_current_free)
            },
            style = AppText.bodySmall,
            color = AppColors.Muted,
        )
        Spacer(Modifier.height(10.dp))
        if (free) {
            Text(
                stringResource(R.string.plan_intro),
                style = AppText.bodySmall,
                color = AppColors.Text,
            )
            Spacer(Modifier.height(10.dp))
            GradientButton(
                text = stringResource(R.string.plan_choose),
                onClick = onOpenPlans,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            GhostButton(
                text = stringResource(R.string.plan_my_orders),
                onClick = onOpenPlans,
                modifier = Modifier.fillMaxWidth(),
            )
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

/** شاشة تعديل الملف الشخصي — تفاصيل التحرير ومعاينته في محرّر مستقل. */
@Composable
fun EditProfileScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onOpenPlans: () -> Unit = {},
) {
    ProfileEditorScreen(
        onBack = onBack,
        onSaved = onSaved,
        onOpenPlans = onOpenPlans,
    )
}

/**
 * الشاشة B — «ملف عضو» (تُفتح من اسم في الشات) على وثيقة `SPEC_PROFILES.md` §4:
 *   البانر (رجوع يسار + «المزيد» يمين) · الأفاتار بلا زرّ · الهوية · صفّ الأزرار
 *   · الإحصاءات · المستوى · النبذة · بطاقة السلامة · وبطاقة الإشراف للإدارة فقط.
 * لا محفظة ولا طلبات ولا مفضّلة ولا إعدادات هنا (نصّ الوثيقة §4).
 *
 * قرار المالك: زرّ «مراسلة [الاسم]» **مخفيّ مؤقتاً** — لا محادثات خاصة في
 * الخادم بعد؛ يُعاد بإضافة جدول الرسائل الخاصة وحدها (لا شيء آخر يتغيّر).
 *
 * الأرقام تأتي من دالة الخادم `profile_stats` (أصدقاء · تنزيلات · مفضّلة ·
 * مستوى · سلسلة أيام) لأن جداولها محميّة بسياسات «لصاحبها فقط».
 */
@Composable
fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit,
    /** فتح تعديل الملف — يظهر حين يفتح المستخدم ملفه هو. */
    onEditProfile: () -> Unit = {},
    onBlock: (Profile) -> Unit = {},
    onUnblock: (Profile) -> Unit = {},
    /** أدوات الإشراف (الأدمن/المشرف): تُفتح شاشة إشراف الشات حيث الحذف والكتم. */
    onOpenAdminTools: () -> Unit = {},
    /**
     * فتح **صفحة الاشتراك** — كتم الأعضاء ميزة اشتراك Legendary (طلب المالك:
     * «وينفتح في اشتراك ليغسي»)، فمن لا اشتراك له يُنادى هنا بدل تنفيذ الكتم.
     */
    onOpenPlans: () -> Unit = {},
) {
    val container = rememberContainer()
    val myProfile by container.state.profile.collectAsState()
    val isMyProfile = container.supabase.session()?.userId == userId
    val scope = rememberCoroutineScope()
    // كتم الأعضاء ميزة Legendary (والخادم يتحقق أيضاً — هذا للواجهة فقط)
    val canMute = myProfile?.let { it.effectivePlan == "legendary" || it.isStaff } == true

    var profile by remember { mutableStateOf<Profile?>(null) }
    var stats by remember { mutableStateOf<ProfileStats?>(null) }
    var friendEntry by remember { mutableStateOf<com.addonstation.mobile.data.model.FriendEntry?>(null) }
    // الحظر: أنا حاظره (فأُظهر «إلغاء الحظر») · أو بيننا حظر بأي اتجاه (فأُعطّل الأزرار)
    var blockedByMe by remember(userId) { mutableStateOf(false) }
    var blockedPair by remember(userId) { mutableStateOf(false) }
    var muted by remember(userId) { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }
    var showBlockConfirm by remember { mutableStateOf(false) }
    var showReport by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var showUnfriendConfirm by remember { mutableStateOf(false) }
    // إعادة القراءة بعد كل فعل (صداقة · كتم · حظر) بلا إعادة تنقّل
    var tick by remember { mutableStateOf(0) }

    val doneMsg = stringResource(R.string.profile_action_done)
    val failedMsg = stringResource(R.string.profile_action_failed)
    val muteDoneMsg = stringResource(R.string.profile_mute_done)
    val unmuteDoneMsg = stringResource(R.string.profile_unmute_done)
    val muteLockedMsg = stringResource(R.string.mute_member_needs_plan)
    val blockDoneMsg = stringResource(R.string.block_done)
    val reportSentMsg = stringResource(R.string.chat_report_sent)

    LaunchedEffect(userId, tick) {
        profile = container.profile.loadProfile(userId)
        stats = container.profile.stats(userId)
        friendEntry = container.profile.friendshipState(userId)
        blockedByMe = container.chat.iBlockedUser(userId)
        blockedPair = container.chat.blockedPair(userId)
        // قائمة المكتومين من الخادم (وتُحفظ محلياً فيُخفى كلامه فوراً في الشات)
        val mutedIds = container.chat.mutedMemberIds()
        runCatching { container.prefs.setMutedUsers(mutedIds) }
        muted = mutedIds.contains(userId)
    }

    /**
     * زرّ الكتم الواحد: يُنفَّذ على الخادم (لا رسائل ولا إشعارات)، ومن لا اشتراك
     * Legendary له تُفتح صفحة الاشتراك ويُشرح السبب في سطر واحد.
     */
    fun toggleMute() {
        if (!canMute) {
            note = muteLockedMsg
            onOpenPlans()
            return
        }
        scope.launch {
            working = true
            val ok = if (muted) container.chat.unmuteMember(userId).isSuccess
            else container.chat.muteMember(userId).isSuccess
            if (ok) {
                val ids = container.chat.mutedMemberIds()
                runCatching { container.prefs.setMutedUsers(ids) }
                muted = ids.contains(userId)
                note = if (muted) muteDoneMsg else unmuteDoneMsg
            } else {
                note = failedMsg
            }
            working = false
        }
    }

    fun doBlock() {
        val me = profile ?: return
        scope.launch {
            onBlock(me)
            note = blockDoneMsg
            tick += 1
        }
    }

    fun doUnblock() {
        val me = profile ?: return
        scope.launch {
            onUnblock(me)
            note = doneMsg
            tick += 1
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        val me = profile ?: run {
            com.addonstation.mobile.ui.components.LoadingBox(text = stringResource(R.string.common_loading))
            return@Column
        }
        val status = specStatusOf(me, isMe = isMyProfile)
        val level = stats?.level ?: me.userLevel

        // ١) البانر: رجوع يسارًا · «المزيد» يمينًا (نفس أدوات بطاقة السلامة)
        SpecProfileBanner(
            bannerUrl = assetUrl(container, me.bannerUrl),
            onBack = onBack,
            more = {
                Box {
                    SpecBannerIconButton(
                        icon = Icons.Filled.MoreVert,
                        description = stringResource(R.string.cd_more_options),
                        onClick = { showMore = true },
                    )
                    DropdownMenu(
                        expanded = showMore,
                        onDismissRequest = { showMore = false },
                        containerColor = AppColors.Surface,
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(if (muted) R.string.chat_unmute_user else R.string.chat_mute_user) +
                                        if (canMute) "" else " · " + stringResource(R.string.mute_member_locked),
                                    color = Spec.text,
                                )
                            },
                            onClick = { showMore = false; toggleMute() },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(if (blockedByMe) R.string.block_user_undo else R.string.block_user), color = Spec.danger) },
                            onClick = {
                                showMore = false
                                if (blockedByMe) doUnblock() else showBlockConfirm = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.chat_report), color = Spec.danger) },
                            onClick = { showMore = false; showReport = true },
                        )
                    }
                }
            },
        )

        // ٢) الأفاتار — بلا زرّ بجانبه (زرّ التعديل لملفي أنا وحدي)
        Box(Modifier.fillMaxWidth().height(Spec.avatarLg - Spec.avatarOverlap)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spec.screenPad),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.offset(y = -(Spec.avatarOverlap / 2))) {
                    SpecProfileAvatar(avatarUrl = assetUrl(container, me.avatarUrl), status = status)
                }
                if (isMyProfile) {
                    Spacer(Modifier.weight(1f))
                    SpecFillButton(
                        text = stringResource(R.string.profile_edit),
                        onClick = onEditProfile,
                        buttonHeight = 44.dp,
                        horizontalPadding = 20.dp,
                    )
                }
            }
        }

        Column(Modifier.padding(horizontal = Spec.screenPad)) {
            // ٣) الهوية
            Spacer(Modifier.height(12.dp))
            SpecIdentityCard(profile = me, status = status, level = level)

            // ٤) صفّ الأزرار — والحظر يُبدَّل بحالة واحدة معطَّلة («إلغاء الحظر»)
            Spacer(Modifier.height(12.dp))
            if (blockedPair) {
                val iBlockedThem = blockedByMe
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(Spec.actionBtnH)
                        .clip(RoundedCornerShape(Spec.buttonRadius))
                        .background(Spec.surfaceHigh)
                        .then(
                            if (iBlockedThem) {
                                Modifier.clickable(enabled = !working) { doUnblock() }
                            } else {
                                Modifier
                            }
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        stringResource(
                            if (iBlockedThem) R.string.block_user_undo else R.string.profile_blocked_state
                        ),
                        style = Spec.button,
                        color = if (iBlockedThem) Spec.blueLink else Spec.textMuted,
                    )
                }
            } else if (isMyProfile) {
                SpecFillButton(
                    text = stringResource(R.string.profile_edit),
                    onClick = onEditProfile,
                    buttonHeight = 44.dp,
                    horizontalPadding = 20.dp,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                // «مراسلة [الاسم]»: مخفيّ مؤقتاً بقرار المالك (لا محادثات خاصة في الخادم بعد)
                FriendActionButtons(
                    friendState = friendEntry,
                    name = me.displayName ?: me.username ?: "",
                    working = working,
                    onSend = {
                        scope.launch {
                            working = true
                            val ok = container.profile.sendFriendRequest(userId).isSuccess
                            note = if (ok) doneMsg else failedMsg
                            working = false
                            tick += 1
                        }
                    },
                    onRespond = { accept ->
                        scope.launch {
                            working = true
                            val entry = friendEntry
                            val ok = entry != null &&
                                container.profile.respondFriendRequest(entry.id, accept).isSuccess
                            note = if (ok) doneMsg else failedMsg
                            working = false
                            tick += 1
                        }
                    },
                    onUnfriend = { showUnfriendConfirm = true },
                )
            }

            // ٥) الإحصاءات
            Spacer(Modifier.height(12.dp))
            SpecStatsStrip(
                friends = stats?.friends ?: 0,
                downloads = stats?.downloads ?: 0,
                favorites = stats?.favorites ?: 0,
            )

            // بطاقة المستوى والسمعة
            Spacer(Modifier.height(12.dp))
            SpecLevelCard(level = level, xpInLevel = stats?.xpInLevel ?: 0)

            // النبذة + «عضو منذ»
            Spacer(Modifier.height(12.dp))
            SpecAboutCard(bio = me.bio, memberSince = specMemberSince(me.createdAt))

            // ٦) بطاقة السلامة (صفوف 52 · فواصل 1dp)
            Spacer(Modifier.height(12.dp))
            SpecCard(padding = 0.dp) {
                Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                    // زرّ الكتم **الواحد** (طلب المالك): يُخفي رسائله ويُسقط إشعاراته،
                    // ومعه شارة «Legendary» لمن لا اشتراك له.
                    SpecActionRow(
                        text = stringResource(if (muted) R.string.chat_unmute_user else R.string.chat_mute_user),
                        color = if (muted) Spec.blueLink else Spec.text,
                        onClick = { toggleMute() },
                        trailing = if (canMute) null else {
                            { SpecBadgeChip(stringResource(R.string.mute_member_locked), Spec.gold, Spec.goldDeepBg) }
                        },
                    )
                    SpecActionRow(
                        text = stringResource(if (blockedByMe) R.string.block_user_undo else R.string.block_user),
                        color = Spec.danger,
                        onClick = { if (blockedByMe) doUnblock() else showBlockConfirm = true },
                        showDivider = true,
                    )
                    SpecActionRow(
                        text = stringResource(R.string.chat_report),
                        color = Spec.danger,
                        onClick = { showReport = true },
                        showDivider = true,
                    )
                }
            }

            // ٧) بطاقة الإشراف — للأدمن/المشرف وحدهما
            if (me.isStaff) {
                Spacer(Modifier.height(12.dp))
                SpecCard(padding = 0.dp) {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp)) {
                        Spacer(Modifier.height(12.dp))
                        SpecSmallCaps(stringResource(R.string.profile_moderation))
                        SpecActionRow(
                            text = stringResource(R.string.chat_mute_user),
                            color = Spec.text,
                            onClick = {
                                scope.launch {
                                    working = true
                                    val ok = container.chat.muteUser(userId, 24 * 60).isSuccess
                                    note = if (ok) doneMsg else failedMsg
                                    working = false
                                }
                            },
                        )
                        SpecActionRow(
                            text = stringResource(R.string.profile_ban_user),
                            color = Spec.danger,
                            onClick = {
                                scope.launch {
                                    working = true
                                    val ok = container.chat.banUser(userId).isSuccess
                                    note = if (ok) doneMsg else failedMsg
                                    working = false
                                }
                            },
                            showDivider = true,
                        )
                        SpecActionRow(
                            text = stringResource(R.string.profile_delete_message),
                            color = Spec.blueLink,
                            onClick = onOpenAdminTools,
                            showDivider = true,
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }

            note?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, style = Spec.secondary, color = Spec.chipLabel)
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    // تأكيد الحظر (سياسة UGC) — واحد لأنّه متقابل وفوري
    if (showBlockConfirm) {
        val name = profile?.displayName ?: profile?.username ?: ""
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showBlockConfirm = false },
            containerColor = AppColors.Surface,
            title = { Text(stringResource(R.string.block_confirm_title), style = AppText.sectionTitle, color = AppColors.Text) },
            text = { Text(stringResource(R.string.block_confirm_body, name), style = AppText.bodySmall, color = AppColors.Muted) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showBlockConfirm = false; doBlock() }) {
                    Text(stringResource(R.string.block_user), color = Spec.danger)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showBlockConfirm = false }) {
                    Text(stringResource(R.string.common_cancel), color = AppColors.Muted)
                }
            },
        )
    }

    // اختيار سبب الإبلاغ — نفس أسباب الإبلاغ في الشات (الخادم يتحقّق منها)
    if (showReport) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showReport = false },
            containerColor = AppColors.Surface,
            title = { Text(stringResource(R.string.chat_report), style = AppText.sectionTitle, color = AppColors.Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        "spam" to R.string.chat_reason_spam,
                        "insult" to R.string.chat_reason_insult,
                        "inappropriate" to R.string.chat_reason_inappropriate,
                        "other" to R.string.chat_reason_other,
                    ).forEach { (code, label) ->
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable {
                                    showReport = false
                                    scope.launch {
                                        val ok = container.chat.reportUser(userId, code).isSuccess
                                        note = if (ok) reportSentMsg else failedMsg
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 11.dp),
                        ) {
                            Text(stringResource(label), style = AppText.body, color = AppColors.Text)
                        }
                    }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = { showReport = false }) {
                    Text(stringResource(R.string.common_cancel), color = AppColors.Muted)
                }
            },
        )
    }

    // إلغاء الصداقة — باللمس على «أصدقاء ✓» كما تنصّ الوثيقة
    if (showUnfriendConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showUnfriendConfirm = false },
            containerColor = AppColors.Surface,
            title = { Text(stringResource(R.string.profile_friends_state), style = AppText.sectionTitle, color = AppColors.Text) },
            text = { Text(stringResource(R.string.profile_unfriend_confirm), style = AppText.bodySmall, color = AppColors.Muted) },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    showUnfriendConfirm = false
                    scope.launch {
                        working = true
                        val ok = container.profile.removeFriend(userId).isSuccess
                        note = if (ok) doneMsg else failedMsg
                        working = false
                        tick += 1
                    }
                }) {
                    Text(stringResource(R.string.profile_unfriend), color = Spec.danger)
                }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { showUnfriendConfirm = false }) {
                    Text(stringResource(R.string.common_cancel), color = AppColors.Muted)
                }
            },
        )
    }
}

/**
 * صفّ أزرار الصداقة في ملف العضو — بحالات الوثيقة §4:
 * «طلب صداقة» ⇒ «أُرسل الطلب» (معطَّل) ⇒ «أصدقاء ✓» (لمسها يعرض إلغاء الصداقة)،
 * وإن أرسل هو الطلب: «قبول» / «رفض».
 *
 * أمّا «مراسلة [الاسم]» فمخفيّ **بقرار المالك** حتى تُضاف المحادثات الخاصة
 * إلى الخادم — لا نُظهر زرّاً لا يعمل.
 */
@Composable
private fun FriendActionButtons(
    friendState: com.addonstation.mobile.data.model.FriendEntry?,
    name: String,
    working: Boolean,
    onSend: () -> Unit,
    onRespond: (Boolean) -> Unit,
    onUnfriend: () -> Unit,
) {
    val myId = rememberContainer().supabase.session()?.userId
    val status = friendState?.status
    val incoming = status == "pending" && friendState?.requesterId != myId
    when {
        // طلب وارد لي: قبول أو رفض
        incoming -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecFillButton(
                text = stringResource(R.string.profile_accept),
                onClick = { onRespond(true) },
                enabled = !working,
                modifier = Modifier.weight(1f),
            )
            SpecOutlineButton(
                text = stringResource(R.string.profile_reject),
                onClick = { onRespond(false) },
                enabled = !working,
                modifier = Modifier.weight(1f),
            )
        }
        // أصدقاء: اللمس يعرض إلغاء الصداقة
        status == "accepted" -> SpecOutlineButton(
            text = stringResource(R.string.profile_friends_state),
            onClick = onUnfriend,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        )
        // أرسلتُ أنا: معطَّل حتى يردّ
        status == "pending" -> SpecOutlineButton(
            text = stringResource(R.string.profile_request_sent),
            onClick = {},
            enabled = false,
            modifier = Modifier.fillMaxWidth(),
        )
        else -> SpecOutlineButton(
            text = stringResource(R.string.profile_friend_request),
            onClick = onSend,
            enabled = !working,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * الشاشة C — «ملفي كما يراه الآخرون» (وثيقة `SPEC_PROFILES.md` §5):
 * نفس ترتيب A لكن بمنظور الزائر: أفاتار + «تعديل الملف» · الهوية · الإحصاءات
 * · المستوى · النبذة · وبطاقة محفظة **بحدّ متقطّع** بدل المحفظة الحقيقية.
 * بلا صفّ أزرار · بلا بطاقة سلامة · بلا «تسجيل الخروج».
 */
@Composable
fun ProfileAsOthersScreen(
    onBack: () -> Unit,
    onOpenEdit: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    var stats by remember { mutableStateOf<ProfileStats?>(null) }

    LaunchedEffect(profile?.id) {
        profile?.id?.let { stats = container.profile.stats(it) }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        val me = profile ?: run {
            com.addonstation.mobile.ui.components.LoadingBox(text = stringResource(R.string.common_loading))
            return@Column
        }
        // ما يراه الزائر: الحالة تُعرض كما أظهرها لغيري (show_online_status)
        val status = specStatusOf(me, isMe = false)

        SpecProfileBanner(
            bannerUrl = assetUrl(container, me.bannerUrl),
            onBack = onBack,
        )

        Box(Modifier.fillMaxWidth().height(Spec.avatarLg - Spec.avatarOverlap)) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = Spec.screenPad),
                verticalAlignment = Alignment.Bottom,
            ) {
                Box(Modifier.offset(y = -(Spec.avatarOverlap / 2))) {
                    SpecProfileAvatar(avatarUrl = assetUrl(container, me.avatarUrl), status = status)
                }
                Spacer(Modifier.weight(1f))
                SpecFillButton(
                    text = stringResource(R.string.profile_edit),
                    onClick = onOpenEdit,
                    buttonHeight = 44.dp,
                    horizontalPadding = 20.dp,
                )
            }
        }

        Column(Modifier.padding(horizontal = Spec.screenPad)) {
            Spacer(Modifier.height(12.dp))
            // الشارة الظاهرة للزائر: بلا «المستوى» الشخصي؟ لا — الوثيقة تُظهر الهوية كما يراها الزائر (تشمل المستوى)
            SpecIdentityCard(profile = me, status = status, level = stats?.level ?: me.userLevel)

            Spacer(Modifier.height(12.dp))
            SpecStatsStrip(
                friends = stats?.friends ?: 0,
                downloads = stats?.downloads ?: 0,
                favorites = stats?.favorites ?: 0,
            )

            Spacer(Modifier.height(12.dp))
            SpecLevelCard(level = stats?.level ?: me.userLevel, xpInLevel = stats?.xpInLevel ?: 0)

            Spacer(Modifier.height(12.dp))
            SpecAboutCard(bio = me.bio, memberSince = specMemberSince(me.createdAt))

            // بطاقة المحفظة المخفيّة (حدّ متقطّع) بدل المحفظة الحقيقية
            Spacer(Modifier.height(12.dp))
            SpecHiddenWalletCard()

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
