package com.addonstation.mobile.ui.home

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ProfileLoadStatus
import com.addonstation.mobile.data.model.ContentItem
import com.addonstation.mobile.data.model.CreatorCard
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.common.metaOf
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.CoverImage
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.SpecChip
import com.addonstation.mobile.ui.components.SpecPillButton
import com.addonstation.mobile.ui.components.SpecPrimaryButton
import com.addonstation.mobile.ui.components.SpecSectionHeader
import com.addonstation.mobile.ui.components.SpecBadge
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.Spec
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
    val openNotifications: () -> Unit = {},
    val openItem: (ContentItem) -> Unit,
    val openCreatorProfile: (String) -> Unit,
)

/**
 * الصفحة الرئيسية — **التصميم الجديد** (وثيقة AddonStation_handoff · القسم ٢):
 *
 *   ١) شريط علوي: أيقونة التطبيق + ADDONSTATION + رصيد AC + زر الإشعارات.
 *   ٢) تحية + بحث.
 *   ٣) بانر الهيرو (250dp) بصورة hero_home والشعار بخط البكسل.
 *   ٤) بطاقة الخطة الحالية + بطاقة Legendary (أو بطاقة الضيف).
 *   ٥) شرائح التصنيف: الكل · إضافات · عوالم · المفضّلة.
 *   ٦) LATEST ADDONS أفقياً (150dp) · MOST DOWNLOADED صفوفاً · FEATURED CREATORS.
 *
 * ملاحظة: قائمة كسولة (LazyColumn) — كل قسم يُبنى حين يظهر ⇒ إقلاع خفيف وتمرير سلس.
 */
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

    var profileRetryTick by remember { mutableStateOf(0) }
    // تُبذر الشاشة من الذاكرة المحفوظة ⇒ لا وميض ولا مؤقّت تحميل عند الرجوع للتبويب
    var featured by remember { mutableStateOf(container.content.cachedFeatured()) }
    var latest by remember { mutableStateOf(container.content.cachedLatest()) }
    var recommended by remember { mutableStateOf(container.content.cachedRecommended()) }
    var creators by remember { mutableStateOf(container.creators.cachedCreators()) }
    var loading by remember { mutableStateOf(featured.isEmpty() && latest.isEmpty()) }
    var failed by remember { mutableStateOf(false) }

    LaunchedEffect(profileRetryTick) {
        failed = false
        try {
            coroutineScope {
                val f = async(Dispatchers.IO) { container.content.featured() }
                val l = async(Dispatchers.IO) { container.content.latest() }
                val r = async(Dispatchers.IO) { container.content.recommended() }
                val c = async(Dispatchers.IO) { container.creators.creators() }
                featured = f.await()
                latest = l.await()
                recommended = r.await()
                creators = c.await()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = latest.isEmpty() && featured.isEmpty()
        } finally {
            loading = false
        }
    }

    // شريحة التصنيف المختارة (الكل افتراضاً) — تُرشّح الأقسام أدناه
    var chip by remember { mutableStateOf(ChipFilter.All) }
    val latestShown = latest.filter { chip.accepts(it) }
    val downloaded = (latest + featured + recommended)
        .distinctBy { it.id }
        .filter { chip.accepts(it) }
        .sortedByDescending { it.downloadsCount }
        .take(6)
    // اسم المبدع لعنصر: ContentItem يحمل created_by فقط ⇒ نستنتجه من قائمة المبدعين
    val creatorNames = creators.associate { it.profile.id to (it.profile.username ?: it.profile.displayName ?: "") }
    fun creatorNameOf(item: ContentItem): String? =
        item.createdBy?.let { creatorNames[it] }?.takeIf { it.isNotBlank() }

    val creatorsShown = when {
        chip == ChipFilter.Addons || chip == ChipFilter.Worlds -> creators.filter { c -> c.profile.isCreator }
        else -> creators
    }

    Column(modifier = modifier.fillMaxSize().background(Spec.bg)) {
        SpecTopBar(
            balance = wallet?.balance,
            onWallet = actions.openWallet,
            onNotifications = actions.openNotifications,
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            // ── ٢) التحية + البحث ────────────────────────────────────────────
            item {
                Column(Modifier.padding(horizontal = Spec.screenPad).padding(top = 14.dp)) {
                    Text(
                        text = stringResource(TimeFormat.greetingRes(TimeFormat.isMorning())),
                        style = Spec.greeting,
                        color = Spec.textMuted,
                        textAlign = TextAlign.Start,
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = when {
                            !signedIn -> stringResource(R.string.home_welcome_guest)
                            profile == null -> stringResource(R.string.common_loading)
                            else -> stringResource(R.string.home_welcome_back, profile?.username ?: "")
                        },
                        style = Spec.welcomeBig,
                        color = Spec.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .clip(RoundedCornerShape(Spec.cardRadius))
                            .background(Spec.surface)
                            .border(1.dp, Spec.border, RoundedCornerShape(Spec.cardRadius))
                            .clickable { actions.openStore() }
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = Spec.textMuted, modifier = Modifier.size(20.dp))
                        Text(stringResource(R.string.home_search_hint), style = Spec.secondary, color = Spec.textMuted)
                    }
                }
            }

            // ── ٣) بانر الهيرو ───────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .padding(horizontal = Spec.screenPad)
                        .padding(top = 16.dp)
                        .fillMaxWidth()
                        .height(Spec.heroHeight)
                        .clip(RoundedCornerShape(Spec.heroRadius)),
                ) {
                    Image(
                        painter = painterResource(R.drawable.hero_home),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                    // تدرّج: أعلى شفاف ⇒ أسفل غامق (ليقرأ النصّ بوضوح)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Spec.bg.copy(alpha = 0.10f),
                                    0.55f to Spec.bg.copy(alpha = 0.55f),
                                    0.80f to Spec.bg.copy(alpha = 0.92f),
                                    1.0f to Spec.bg.copy(alpha = 0.96f),
                                )
                            ),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(Spec.screenPad),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            stringResource(R.string.home_hero_slogan),
                            style = Spec.heroSlogan,
                            color = Spec.gold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.home_hero_subtitle),
                            style = Spec.secondary,
                            color = Spec.heroSubtitle,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SpecPrimaryButton(
                                text = stringResource(R.string.home_hero_start),
                                onClick = actions.openStore,
                                height = Spec.touch,
                            )
                            // «انضم للعائلة» تختفي لصاحب الحساب (كما تنصّ الوثيقة)
                            if (!signedIn) {
                                SpecPillButton(
                                    text = stringResource(R.string.home_hero_family),
                                    onClick = actions.openSignup,
                                    height = Spec.touch,
                                    outline = Spec.gold,
                                    textColor = Spec.gold,
                                )
                            }
                        }
                    }
                }
            }

            // ── ٤) بطاقات الخطة / الضيف ──────────────────────────────────────
            item {
                Column(
                    modifier = Modifier.padding(horizontal = Spec.screenPad).padding(top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when {
                        !signedIn -> GuestCard(actions)
                        profile == null && profileLoadStatus in setOf(ProfileLoadStatus.IDLE, ProfileLoadStatus.LOADING) ->
                            LoadingBox(text = stringResource(R.string.common_loading))
                        profile == null -> Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(Spec.cardRadiusLg))
                                .background(Spec.surface)
                                .border(1.dp, Spec.border, RoundedCornerShape(Spec.cardRadiusLg))
                                .clickable { profileRetryTick += 1 }
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.home_error), style = Spec.cardTitle, color = Spec.text)
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.home_error_tap), style = Spec.secondary, color = Spec.blueLink)
                        }
                        profile?.effectivePlan == "free" -> CurrentPlanCard(
                            planName = stringResource(R.string.plan_free_name),
                            description = stringResource(R.string.home_plan_free_desc),
                            onUpgrade = actions.openPlans,
                        )
                        else -> CurrentPlanCard(
                            planName = planLabelOf(profile?.effectivePlan ?: "free"),
                            description = if (profile?.planExpiresAt.isNullOrBlank()) {
                                stringResource(R.string.home_plan_active_forever)
                            } else {
                                stringResource(
                                    R.string.home_plan_expires,
                                    TimeFormat.expiryLabel(profile?.planExpiresAt, Locale.getDefault()),
                                )
                            },
                            onUpgrade = null,
                        )
                    }
                    // بطاقة Legendary — تختفي عند من يملك الخطة الأسطورية
                    if (profile?.effectivePlan != "legendary") {
                        LegendaryCard(onView = actions.openPlans)
                    }
                }
            }

            // ── ٥) شرائح التصنيف ────────────────────────────────────────────
            item {
                LazyRow(
                    modifier = Modifier.padding(top = 22.dp),
                    contentPadding = PaddingValues(horizontal = Spec.screenPad),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    item {
                        SpecChip(
                            text = stringResource(R.string.home_chip_all),
                            selected = chip == ChipFilter.All,
                            onClick = { chip = ChipFilter.All },
                        )
                    }
                    item {
                        SpecChip(
                            text = stringResource(R.string.home_chip_addons),
                            selected = chip == ChipFilter.Addons,
                            onClick = { chip = ChipFilter.Addons },
                        )
                    }
                    item {
                        SpecChip(
                            text = stringResource(R.string.home_chip_worlds),
                            selected = chip == ChipFilter.Worlds,
                            onClick = { chip = ChipFilter.Worlds },
                        )
                    }
                    item {
                        // «المفضّلة» تنقل إلى شاشتها (لا ترشيح موضعي)
                        SpecChip(
                            text = stringResource(R.string.home_chip_favorites),
                            selected = false,
                            onClick = actions.openFavorites,
                        )
                    }
                }
            }

            // ── ٦أ) أحدث الإضافات ───────────────────────────────────────────
            item {
                SpecSectionHeader(
                    title = stringResource(R.string.home_latest_addons),
                    actionText = stringResource(R.string.view_all),
                    onAction = actions.openStore,
                )
                when {
                    loading && latestShown.isEmpty() -> Box(Modifier.padding(horizontal = Spec.screenPad)) { LoadingBox() }
                    latestShown.isEmpty() -> EmptyNote()
                    else -> LazyRow(
                        contentPadding = PaddingValues(horizontal = Spec.screenPad),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(latestShown.take(12), key = { it.id }) { item ->
                            LatestCard(item = item, byName = creatorNameOf(item), onClick = { actions.openItem(item) })
                        }
                    }
                }
            }

            // ── ٦ب) الأكثر تنزيلاً ──────────────────────────────────────────
            item {
                SpecSectionHeader(
                    title = stringResource(R.string.home_most_downloaded),
                    actionText = stringResource(R.string.view_all),
                    onAction = actions.openStore,
                )
                if (downloaded.isEmpty() && !loading) {
                    EmptyNote()
                } else {
                    Column(
                        modifier = Modifier.padding(horizontal = Spec.screenPad),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        downloaded.forEach { item ->
                            DownloadRow(item = item, byName = creatorNameOf(item), onClick = { actions.openItem(item) })
                        }
                    }
                }
            }

            // ── ٦ج) المبدعون المميزون ───────────────────────────────────────
            item {
                SpecSectionHeader(
                    title = stringResource(R.string.home_featured_creators),
                    actionText = stringResource(R.string.view_all),
                    onAction = actions.openCreators,
                )
                Text(
                    stringResource(R.string.home_creators_caption),
                    style = Spec.secondary,
                    color = Spec.textMuted,
                    modifier = Modifier.padding(start = Spec.screenPad, end = Spec.screenPad, bottom = 10.dp),
                )
                if (creatorsShown.isEmpty() && !loading) {
                    EmptyNote()
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = Spec.screenPad),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(creatorsShown.take(12), key = { it.profile.id }) { card ->
                            CreatorAvatar(card = card, onClick = { actions.openCreatorProfile(card.profile.id) })
                        }
                    }
                }
            }

            // ── روابط كانت في التصميم السابق ونُبقيت (تصميمها الجديد هادئ) ──
            item {
                Column(Modifier.padding(horizontal = Spec.screenPad).padding(top = 24.dp)) {
                    if (failed) {
                        Row(
                            Modifier.fillMaxWidth().clickable { profileRetryTick += 1 },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(stringResource(R.string.home_error), style = Spec.secondary, color = Spec.textMuted)
                            Spacer(Modifier.weight(1f))
                            Text(stringResource(R.string.home_error_tap), style = Spec.secondary, color = Spec.blueLink)
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SpecPillButton(
                            text = stringResource(R.string.home_exclusives),
                            onClick = actions.openExclusives,
                            modifier = Modifier.weight(1f),
                            height = Spec.touch,
                        )
                        SpecPillButton(
                            text = if (profile?.isCreator == true) {
                                stringResource(R.string.home_creator_dashboard)
                            } else {
                                stringResource(R.string.home_creator_cta)
                            },
                            onClick = if (profile?.isCreator == true) actions.openStudio else {
                                if (signedIn) actions.openCreatorApply else actions.openLogin
                            },
                            modifier = Modifier.weight(1f),
                            height = Spec.touch,
                        )
                    }
                    Spacer(Modifier.height(18.dp))
                    Text(
                        stringResource(R.string.brand_wordmark) + stringResource(R.string.brand_mobile),
                        style = Spec.secondary,
                        color = Spec.textMuted,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        stringResource(R.string.brand_tagline),
                        style = Spec.secondary,
                        color = Spec.textMuted.copy(alpha = 0.7f),
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

/** التصنيفات في الشرائح: الكل · إضافات · عوالم (المفضّلة تنقل لشاشتها). */
private enum class ChipFilter {
    All, Addons, Worlds;

    fun accepts(item: ContentItem): Boolean = when (this) {
        All -> true
        Addons -> item.contentType != "world"
        Worlds -> item.contentType == "world"
    }
}

/**
 * شريط علوي (القسم ٢٫١): أيقونة 40 + الاسم بخط البكسل + قرص الرصيد + الجرس.
 * لا يوجد شريط علوي مكرّر، فالشاشة هنا لا تستعمل PixelTopBar كما قبل.
 */
@Composable
private fun SpecTopBar(
    balance: Int?,
    onWallet: () -> Unit,
    onNotifications: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spec.screenPad, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)),
        )
        Text(
            stringResource(R.string.brand_wordmark),
            style = Spec.topBarName,
            color = Spec.text,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(Spec.pillRadius))
                .background(Spec.surfaceHigh)
                .clickable { onWallet() }
                .padding(start = 6.dp, end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.coin_ac),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = (balance ?: 0).toString(),
                style = Spec.cardTitle.copy(fontFamily = Spec.NumberFont),
                color = Spec.gold,
            )
        }
        Box(
            modifier = Modifier
                .size(Spec.touch)
                .clip(CircleShape)
                .background(Spec.surface)
                .clickable { onNotifications() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Notifications, contentDescription = null, tint = Spec.text, modifier = Modifier.size(22.dp))
        }
    }
}

/** بطاقة «خطتك الحالية» (٢٫٤): يسار النصوص ويمين زر الترقية. */
@Composable
private fun CurrentPlanCard(planName: String, description: String, onUpgrade: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spec.cardRadiusLg))
            .background(Spec.surface)
            .border(1.dp, Spec.border, RoundedCornerShape(Spec.cardRadiusLg))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(stringResource(R.string.home_plan_current), style = Spec.secondary, color = Spec.textMuted)
            Text(planName, style = Spec.cardTitle.copy(fontFamily = Spec.NumberFont), color = Spec.text)
            Text(description, style = Spec.secondary, color = Spec.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (onUpgrade != null) {
            SpecPillButton(
                text = stringResource(R.string.profile_upgrade),
                onClick = onUpgrade,
                height = Spec.touch,
            )
        }
    }
}

/** بطاقة Legendary (٢٫٤): تدرّج ذهبي غامق + إطار ذهبي + زر ذهبي. */
@Composable
private fun LegendaryCard(onView: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spec.cardRadiusLg))
            .background(
                Brush.linearGradient(listOf(Color(0xFF3A2A0B), Spec.goldDeepBg)),
            )
            .border(1.dp, Spec.goldBorder, RoundedCornerShape(Spec.cardRadiusLg))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.home_legend_title), style = Spec.sectionTitle, color = Spec.gold)
            Text(stringResource(R.string.home_legend_text), style = Spec.secondary, color = Spec.legendText)
        }
        Box(
            modifier = Modifier
                .height(Spec.touch)
                .clip(RoundedCornerShape(Spec.buttonRadius))
                .background(Spec.gold)
                .clickable { onView() }
                .padding(horizontal = 18.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(stringResource(R.string.home_legend_view), style = Spec.button, color = Spec.goldDeepBg)
        }
    }
}

/** بطاقة الضيف (٢٫٥) — تظهر لغير المسجّل فقط. */
@Composable
private fun GuestCard(actions: HomeActions) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spec.cardRadiusLg))
            .background(Spec.surface)
            .border(1.dp, Spec.border, RoundedCornerShape(Spec.cardRadiusLg))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(stringResource(R.string.home_guest_card_title), style = Spec.cardTitle, color = Spec.text)
        Text(stringResource(R.string.home_guest_card_text), style = Spec.secondary, color = Spec.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecPrimaryButton(
                text = stringResource(R.string.auth_sign_in),
                onClick = actions.openLogin,
                modifier = Modifier.weight(1f),
                height = Spec.touch,
            )
            SpecPillButton(
                text = stringResource(R.string.auth_new_account),
                onClick = actions.openSignup,
                modifier = Modifier.weight(1f),
                height = Spec.touch,
            )
        }
    }
}

/** بطاقة عنصر في «أحدث الإضافات» (٢٫٨): 150 عرضاً · صورة 100 · شارة زاوية · عنوان ومبدع. */
@Composable
private fun LatestCard(item: ContentItem, byName: String?, onClick: () -> Unit) {
    val container = rememberContainer()
    Column(
        modifier = Modifier
            .width(Spec.latestCardWidth)
            .clip(RoundedCornerShape(Spec.cardRadius))
            .background(Spec.surface)
            .clickable { onClick() },
    ) {
        Box(Modifier.height(Spec.latestThumbHeight).fillMaxWidth()) {
            CoverImage(
                url = assetUrl(container, item.coverUrl),
                radius = 0.dp,
                modifier = Modifier.fillMaxSize(),
            )
            badgeFor(item)?.let { (text, bg, fg) ->
                Box(Modifier.align(Alignment.TopStart).padding(6.dp)) {
                    SpecBadge(text = text, background = bg, textColor = fg)
                }
            }
        }
        Column(Modifier.padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 10.dp)) {
            Text(
                item.title,
                style = Spec.cardTitle,
                color = Spec.text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                // «بواسطة [المبدع]» — وإن لم نعرفه نُظهر التصنيف بدلاً من فراغ
                byName?.let { stringResource(R.string.home_by_creator, it) }
                    ?: (item.category ?: ""),
                style = Spec.secondary,
                color = Spec.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** صف في «الأكثر تنزيلاً» (٢٫٩): صورة 64 + النصوص + زر تنزيل أزرق 44. */
@Composable
private fun DownloadRow(item: ContentItem, byName: String?, onClick: () -> Unit) {
    val container = rememberContainer()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Spec.cardRadius))
            .background(Spec.surface)
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CoverImage(
            url = assetUrl(container, item.coverUrl),
            radius = 10.dp,
            modifier = Modifier.size(Spec.rowThumb),
        )
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(item.title, style = Spec.cardTitle, color = Spec.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                (byName?.let { stringResource(R.string.home_by_creator, it) } ?: "") +
                    if (byName != null) " · " + metaOf(item) else metaOf(item),
                style = Spec.secondary,
                color = Spec.textMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            modifier = Modifier
                .size(Spec.touch)
                .clip(RoundedCornerShape(Spec.buttonRadius))
                .background(Spec.blue)
                .clickable { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.FileDownload, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        }
    }
}

/** أيقونة مبدع في «المبدعون المميزون» (٢٫١٠): 64 دائرية بحلقة بلون الرتبة + الاسم. */
@Composable
private fun CreatorAvatar(card: CreatorCard, onClick: () -> Unit) {
    val container = rememberContainer()
    val ring = when {
        card.profile.role == "admin" -> Spec.gold
        card.profile.effectivePlan == "legendary" -> Spec.gold
        card.profile.effectivePlan == "epic" -> Spec.epic
        card.profile.isCreator -> Spec.blue
        else -> Spec.border
    }
    Column(
        modifier = Modifier
            .width(72.dp)
            .clip(RoundedCornerShape(Spec.cardRadius))
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AvatarImage(
            url = assetUrl(container, card.profile.avatarUrl),
            size = Spec.avatar64,
            borderColor = ring,
        )
        Text(
            card.profile.displayName ?: card.profile.username ?: "",
            style = Spec.roomPreview.copy(fontWeight = FontWeight.Bold),
            color = Spec.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EmptyNote() {
    Text(
        stringResource(R.string.home_no_content),
        style = Spec.secondary,
        color = Spec.textMuted,
        modifier = Modifier.padding(horizontal = Spec.screenPad, vertical = 8.dp),
    )
}

/** شارة العنصر: NEW (أزرق) · EPIC (بنفسجي) · LEGENDARY (ذهبي) — والمجاني بلا شارة. */
private fun badgeFor(item: ContentItem): Triple<String, Color, Color>? = when {
    item.plan == "legendary" -> Triple("LEGENDARY", Spec.gold, Spec.goldDeepBg)
    item.plan == "epic" -> Triple("EPIC", Spec.epic, Color.White)
    item.isFeatured -> Triple("NEW", Spec.blue, Color.White)
    else -> null
}
