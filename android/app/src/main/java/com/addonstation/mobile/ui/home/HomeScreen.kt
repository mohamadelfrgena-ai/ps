package com.addonstation.mobile.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Games
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ProfileLoadStatus
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorCard
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.ContentCard
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.IconTile
import com.addonstation.mobile.ui.components.LinkRow
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.PromoCard
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.util.TimeFormat
import java.util.Locale

/** الأقسام التي تنتقل إليها بطاقات الرئيسية. */
data class HomeActions(
    val openStore: () -> Unit,
    val openFavorites: () -> Unit,
    val openDownloads: () -> Unit,
    val openPlans: () -> Unit,
    val openSupport: () -> Unit,
    val openCreators: () -> Unit,
    val openCreatorApply: () -> Unit,
    val openStudio: () -> Unit,
    val openExclusives: () -> Unit,
    val openProfile: () -> Unit,
    val openLogin: () -> Unit,
    val openSignup: () -> Unit,
    val openWallet: () -> Unit,
    val openItem: (ContentItem) -> Unit,
    val openCreatorProfile: (String) -> Unit,
)

@Composable
fun HomeScreen(
    actions: HomeActions,
    modifier: Modifier = Modifier,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val profileLoadStatus by container.state.profileLoadStatus.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val plans by container.state.plans.collectAsState()

    var profileRetryTick by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var featured by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var latest by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var recommended by remember { mutableStateOf<List<ContentItem>>(emptyList()) }
    var creators by remember { mutableStateOf<List<CreatorCard>>(emptyList()) }

    LaunchedEffect(signedIn, profileRetryTick) {
        if (signedIn && container.state.currentProfile == null) {
            try {
                withContext(Dispatchers.IO) { container.profile.ensureProfileLoaded() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // لا نعامل فشل الجلب كخروج؛ زر إعادة المحاولة يبقى متاحاً.
            }
        }
    }

    LaunchedEffect(profile?.id) {
        loading = true
        runCatching {
            // بالتوازي لا بالتتابع: الانتظار كان أربع جولات شبكة (زمنها مجموع)
            coroutineScope {
                val f = async { container.content.featured(8) }
                val l = async { container.content.latest(6) }
                val r = async { container.content.recommended(6) }
                val c = async { container.creators.creators().take(4) }
                featured = f.await()
                latest = l.await()
                recommended = r.await()
                creators = c.await()
            }
        }
        loading = false
    }

    val morning = TimeFormat.isMorning()
    val greeting = stringResource(TimeFormat.greetingRes(morning))

    Column(modifier = modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.nav_home),
            walletBalance = wallet?.balance,
            onWalletClick = actions.openWallet,
            icon = Icons.Filled.Games,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            /* 4.1 رأس الترحيب — سطرا التحية واسم AddonStation يسار مربّع البروفايل
               في LTR، والصورة يميناً؛ وفي RTL (العربية) ينعكس موضعهما تلقائياً.
               محاذاة End تُبقي النص قريباً من الصورة في كلا الاتجاهين. */
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Column(
                    Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        "$greeting 👋",
                        style = AppText.greeting,
                        color = AppColors.Muted,
                        textAlign = TextAlign.End,
                    )
                    Text(
                        text = when {
                            !signedIn -> stringResource(R.string.home_welcome_guest)
                            profile == null -> stringResource(R.string.common_loading)
                            else -> stringResource(R.string.home_welcome_back, profile?.username ?: "")
                        },
                        style = AppText.welcomeName,
                        color = AppColors.Text,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(R.string.brand_wordmark),
                        style = AppText.brand,
                        color = Color.White,
                        textAlign = TextAlign.End,
                    )
                    if (profile != null) {
                        RankBadge(role = profile!!.role, plan = profile!!.effectivePlan)
                    }
                }
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(listOf(AppColors.Green.copy(alpha = 0.35f), AppColors.Blue.copy(alpha = 0.35f))),
                        )
                        .padding(3.dp)
                        .clip(RoundedCornerShape(19.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(19.dp))
                        .clickable { if (signedIn) actions.openProfile() else actions.openLogin() },
                    contentAlignment = Alignment.Center,
                ) {
                    // الزائر (بلا حساب / بعد تسجيل الخروج) ⇒ صورة المجهول التي أرسلها المالك
                    if (profile == null) {
                        com.addonstation.mobile.ui.components.AnonymousAvatarImage(
                            size = 62.dp,
                            shape = RoundedCornerShape(19.dp),
                        )
                    } else {
                        com.addonstation.mobile.ui.components.AvatarImage(
                            url = assetUrl(container, profile?.avatarUrl),
                            size = 62.dp,
                            shape = RoundedCornerShape(19.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            /* 4.2 بطاقة الحالة */
            when {
                !signedIn -> GuestCard(actions)
                profile == null && profileLoadStatus in setOf(ProfileLoadStatus.IDLE, ProfileLoadStatus.LOADING) ->
                    LoadingBox(text = stringResource(R.string.common_loading))
                profile == null -> ErrorState(
                    onRetry = { profileRetryTick += 1 },
                )
                profile!!.effectivePlan == "free" -> FreePlanCard(planName = stringResource(R.string.plan_free_name), onUpgrade = actions.openPlans)
                else -> ActivePlanCard(
                    planLabel = stringResource(R.string.home_plan_active, planLabelOf(profile!!.effectivePlan)),
                    expiryIso = profile!!.planExpiresAt,
                    plan = profile!!.effectivePlan,
                )
            }

            /* 4.3 شريط البحث */
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(13.dp))
                    .background(AppColors.Elevated)
                    .border(1.dp, AppColors.Border, RoundedCornerShape(13.dp))
                    .clickable { actions.openStore() }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = AppColors.Muted, modifier = Modifier.size(16.dp))
                Text(stringResource(R.string.home_search_hint), style = AppText.bodySmall, color = AppColors.Dim)
            }

            /* 4.4 إجراءات سريعة */
            SectionHeader(title = stringResource(R.string.home_quick_actions))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconTile(Icons.Filled.Favorite, stringResource(R.string.home_qa_favorites), actions.openFavorites, Modifier.weight(1f), tint = AppColors.Red)
                IconTile(Icons.Filled.FileDownload, stringResource(R.string.home_qa_downloads), actions.openDownloads, Modifier.weight(1f), tint = AppColors.Blue)
                IconTile(Icons.Filled.WorkspacePremium, stringResource(R.string.home_qa_upgrade), actions.openPlans, Modifier.weight(1f), tint = AppColors.Gold)
                IconTile(Icons.Filled.Games, stringResource(R.string.home_qa_device), actions.openSupport, Modifier.weight(1f), tint = AppColors.Green)
            }

            /* 4.5 معاينة الخطط */
            SectionHeader(
                title = stringResource(R.string.home_plans),
                actionText = stringResource(R.string.view_all),
                onAction = actions.openPlans,
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(plans.sortedBy { it.sortOrder }) { plan ->
                    PlanPreviewCard(
                        title = plan.name,
                        tagline = plan.tagline.orEmpty(),
                        accent = when (plan.id) {
                            "legendary" -> AppColors.Gold
                            "epic" -> AppColors.Purple
                            else -> AppColors.FreeGray
                        },
                        emoji = when (plan.id) {
                            "legendary" -> "👑"
                            "epic" -> "💎"
                            else -> "🍃"
                        },
                        onClick = actions.openPlans,
                    )
                }
            }

            /* 4.6 المميز */
            SectionHeader(
                title = stringResource(R.string.home_featured),
                actionText = stringResource(R.string.view_all),
                onAction = actions.openStore,
            )
            if (loading) {
                LoadingBox()
            } else {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(featured, key = { it.id }) { item ->
                        ContentCard(
                            title = item.title,
                            coverUrl = assetUrl(container, item.coverUrl),
                            planLabel = planLabelOf(item.plan),
                            metaLine = metaOf(item),
                            onClick = { actions.openItem(item) },
                            large = true,
                        )
                    }
                }
            }

            /* 4.7 أحدث */
            SectionHeader(
                title = stringResource(R.string.home_latest),
                actionText = stringResource(R.string.view_all),
                onAction = actions.openStore,
            )
            if (latest.isNotEmpty()) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.height(if (latest.size > 2) 470.dp else 240.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    userScrollEnabled = false,
                ) {
                    items(latest, key = { it.id }) { item ->
                        ContentCard(
                            title = item.title,
                            coverUrl = assetUrl(container, item.coverUrl),
                            planLabel = planLabelOf(item.plan),
                            metaLine = metaOf(item),
                            onClick = { actions.openItem(item) },
                        )
                    }
                }
            }

            /* 4.8 المبدعون المميزون */
            SectionHeader(
                title = stringResource(R.string.home_creators),
                actionText = stringResource(R.string.view_all),
                onAction = actions.openCreators,
                leadingEmoji = "🎨",
            )
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(creators, key = { it.profile.id }) { card ->
                    CreatorMiniCard(card = card, onClick = { actions.openCreatorProfile(card.profile.id) })
                }
            }

            /* 4.9 كن مبدعاً */
            Spacer(Modifier.height(18.dp))
            if (profile?.isCreator == true) {
                PromoCard(
                    kicker = "CREATOR PROGRAM",
                    title = stringResource(R.string.home_creator_dashboard),
                    text = stringResource(R.string.home_creator_text),
                    buttonText = stringResource(R.string.home_creator_open),
                    onButton = actions.openStudio,
                )
            } else {
                PromoCard(
                    kicker = "CREATOR PROGRAM",
                    title = stringResource(R.string.home_creator_title),
                    text = stringResource(R.string.home_creator_text),
                    buttonText = stringResource(R.string.home_creator_cta),
                    onButton = if (signedIn) actions.openCreatorApply else actions.openLogin,
                )
            }

            /* 4.10 الحصريات */
            Spacer(Modifier.height(10.dp))
            LinkRow(
                title = stringResource(R.string.home_exclusives),
                subtitle = stringResource(R.string.home_exclusives_sub),
                onClick = actions.openExclusives,
            )

            /* 4.11 موصى به لك */
            SectionHeader(title = stringResource(R.string.home_recommended))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(recommended, key = { it.id }) { item ->
                    ContentCard(
                        title = item.title,
                        coverUrl = assetUrl(container, item.coverUrl),
                        planLabel = planLabelOf(item.plan),
                        metaLine = metaOf(item),
                        onClick = { actions.openItem(item) },
                        large = true,
                    )
                }
            }

            /* 4.12 التذييل */
            Spacer(Modifier.height(22.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AppColors.Green.copy(alpha = 0.10f), AppColors.Blue.copy(alpha = 0.10f)),
                        )
                    )
                    .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                    .padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(stringResource(R.string.brand_wordmark), style = AppText.brandFooter, color = Color.White)
                    Text(
                        stringResource(R.string.brand_mobile),
                        style = AppText.brandFooter,
                        color = Color(0xFF005B9F),
                    )
                }
                Text(stringResource(R.string.brand_platform), style = AppText.tiny, color = AppColors.Muted)
                Text(
                    stringResource(R.string.brand_rights),
                    style = AppText.tiny,
                    color = AppColors.FreeGray.copy(alpha = 0.8f),
                )
                Text(stringResource(R.string.brand_tagline), style = AppText.tiny, color = AppColors.Dim)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun GuestCard(actions: HomeActions) {
    GlassCard(
        color = AppColors.Gold.copy(alpha = 0.08f),
        borderColor = AppColors.Gold.copy(alpha = 0.35f),
    ) {
        Text(
            stringResource(R.string.home_guest_card_title),
            style = AppText.sectionTitle,
            color = AppColors.Gold,
        )
        Text(stringResource(R.string.home_guest_card_text), style = AppText.bodySmall, color = AppColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            com.addonstation.mobile.ui.components.GradientButton(
                text = stringResource(R.string.auth_sign_in),
                onClick = actions.openLogin,
                modifier = Modifier.weight(1f),
            )
            com.addonstation.mobile.ui.components.GhostButton(
                text = stringResource(R.string.auth_new_account),
                onClick = actions.openSignup,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun FreePlanCard(planName: String, onUpgrade: () -> Unit) {
    GlassCard(
        color = AppColors.Gold.copy(alpha = 0.08f),
        borderColor = AppColors.Gold.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                stringResource(R.string.home_plan_card_free_title),
                style = AppText.sectionTitle,
                color = AppColors.Gold,
            )
            Spacer(Modifier.weight(1f))
            Text(planName, style = AppText.tiny, color = AppColors.Muted)
        }
        Text(stringResource(R.string.home_plan_card_free_text), style = AppText.bodySmall, color = AppColors.Muted)
        com.addonstation.mobile.ui.components.GradientButton(
            text = stringResource(R.string.profile_upgrade),
            onClick = onUpgrade,
            modifier = Modifier.fillMaxWidth(),
            colors = listOf(AppColors.GoldLight, AppColors.GoldDark),
            textColor = AppColors.GoldText,
        )
    }
}

@Composable
private fun ActivePlanCard(planLabel: String, expiryIso: String?, plan: String) {
    GlassCard(
        color = AppColors.Green.copy(alpha = 0.08f),
        borderColor = AppColors.Green.copy(alpha = 0.35f),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(planLabel, style = AppText.sectionTitle, color = AppColors.Green)
                Text(
                    if (expiryIso.isNullOrBlank()) stringResource(R.string.home_plan_active_forever)
                    else stringResource(R.string.home_plan_expires, TimeFormat.expiryLabel(expiryIso, Locale.getDefault())),
                    style = AppText.bodySmall,
                    color = AppColors.Muted,
                )
            }
            Text(if (plan == "legendary") "👑" else "💎", style = AppText.pageTitle)
        }
    }
}

@Composable
private fun PlanPreviewCard(
    title: String,
    tagline: String,
    accent: Color,
    emoji: String,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 150.dp, height = 116.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(emoji, style = AppText.welcomeName)
        Text("$title", style = AppText.sectionTitle.copy(fontFamily = PixelFont), color = accent, maxLines = 1)
        Text(tagline, style = AppText.tiny, color = AppColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun CreatorMiniCard(card: CreatorCard, onClick: () -> Unit) {
    val container = rememberContainer()
    Column(
        modifier = Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        com.addonstation.mobile.ui.components.AvatarImage(
            url = assetUrl(container, card.profile.avatarUrl),
            size = 46.dp,
            borderColor = AppColors.Green,
        )
        Text(
            card.profile.displayName ?: card.profile.username ?: "",
            style = AppText.bodySmall,
            color = AppColors.Text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "@${card.profile.username ?: ""}",
            style = AppText.tiny,
            color = AppColors.Muted,
            maxLines = 1,
        )
        Text(
            "📥 ${card.downloads}",
            style = AppText.tiny,
            color = AppColors.Muted,
            textAlign = TextAlign.Center,
        )
    }
}
