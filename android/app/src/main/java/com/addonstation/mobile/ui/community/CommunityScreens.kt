package com.addonstation.mobile.ui.community

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.ChatChannel
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.CountPill
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.RoomIcon
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.LocaleManager
import kotlinx.coroutines.launch

/**
 * بوابة المجتمع: قواعد المجتمع قبل الدخول الأول (مرة واحدة لكل مستخدم)،
 * ثم قائمة الغرف بأسلوب Discord.
 */
@Composable
fun CommunityGate(
    onOpenRoom: (ChatChannel) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onLogin: () -> Unit,
    onDeclineToHome: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val status by container.state.chatStatus.collectAsState()
    val scope = rememberCoroutineScope()

    LaunchedEffect(profile?.id) {
        container.chat.refreshMyStatus()
        container.chat.refreshUnread()
    }

    val accepted = status?.acceptedRules == true

    if (signedIn && !accepted) {
        RulesScreen(
            onAccepted = {
                scope.launch { container.chat.acceptRules() }
            },
            onDecline = onDeclineToHome,
        )
        return
    }
    if (!signedIn && status == null) {
        RulesScreen(
            onAccepted = { onLogin() },
            onDecline = onDeclineToHome,
        )
        return
    }

    RoomListScreen(
        onOpenRoom = onOpenRoom,
        onOpenEditProfile = onOpenEditProfile,
        onOpenWallet = onOpenWallet,
        onLogin = onLogin,
    )
}

/** شاشة ⚠️ قواعد المجتمع (النصوص تُقرأ من موارد اللغة، ويمكن للأدمن تعديلها من الخادم). */
@Composable
fun RulesScreen(onAccepted: () -> Unit, onDecline: () -> Unit) {
    val container = rememberContainer()
    var serverRules by remember { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(Unit) {
        serverRules = runCatching { container.chat.rules(container.prefs.readLocale()) }
            .getOrElse { emptyList() }
    }

    val fallback = listOf(
        stringResource(R.string.chat_rule_1),
        stringResource(R.string.chat_rule_2),
        stringResource(R.string.chat_rule_3),
        stringResource(R.string.chat_rule_4),
        stringResource(R.string.chat_rule_5),
        stringResource(R.string.chat_rule_6),
        stringResource(R.string.chat_rule_7),
        stringResource(R.string.chat_rule_8),
    )
    val rules = if (serverRules.isNotEmpty()) serverRules else fallback

    // المقاسات تتبع الشاشة: العنوان فوق، وبطاقة القواعد تأخذ المساحة المتبقية
    // وتتمرّر إن زادت عنها، والزرّان ثابتان في الأسفل ظاهران دائماً — فلا يخرج
    // شيء عن حدود الشاشة على الهاتف الصغير ولا على الكبير.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.chat_rules_title),
            style = AppText.pageTitle,
            color = AppColors.Text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState()),
        ) {
            GlassCard(modifier = Modifier.fillMaxWidth(), padding = 12.dp) {
                rules.forEach { rule ->
                    Text("• $rule", style = AppText.bodySmall, color = AppColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        GradientButton(
            text = stringResource(R.string.chat_accept_enter),
            onClick = onAccepted,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(10.dp))
        GhostButton(
            text = stringResource(R.string.chat_decline_home),
            onClick = onDecline,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(6.dp))
    }
}

/** قائمة الغرف: تصنيفان قابلان للطي + مؤشرات غير المقروء والمنشن + لوحة المستخدم أسفل الشاشة. */
@Composable
fun RoomListScreen(
    onOpenRoom: (ChatChannel) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onLogin: () -> Unit,
) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val unread by container.state.unread.collectAsState()
    val wallet by container.state.wallet.collectAsState()

    // **لا مؤقّت تحميل عند الرجوع**: القائمة تُبذر من الذاكرة المحفوظة فوراً،
    // فلا فراغ ولا وميض ولا انتظار شبكة بعد الرجوع من غرفة.
    var channels by remember { mutableStateOf(container.chat.cachedChannels()) }
    var loading by remember { mutableStateOf(channels.isEmpty()) }
    var mainOpen by rememberSaveable { mutableStateOf(true) }
    var langOpen by rememberSaveable { mutableStateOf(true) }
    // لغة الواجهة الحالية (طبّقناها في MainActivity) — لتظليل غرفة اللغة المناسبة
    val appLanguage = LocaleManager.byCode(java.util.Locale.getDefault().language).code

    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        // قائمة محفوظة ⇒ نُحدّث في الخلفية بلا أن نُفرغ الشاشة (لا يظهر مؤقّت)
        loading = channels.isEmpty()
        failed = false
        try {
            // الأدمن: نضمن وجود غرفة «خدمات النقدية» وضبطها للقراءة فقط،
            // فلا يحتاج إلى إنشاءها يدوياً ولا إلى تشغيل أي SQL.
            // (مرة واحدة في الجلسة — لا رحلة شبكة مع كل رجوع.)
            container.chat.ensureCashServicesRoom(profile?.role == "admin") // الخادم يمنعها لغير الأدمن
            channels = container.chat.channels()
            container.chat.refreshUnread()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.nav_community),
            walletBalance = wallet?.balance,
            onWalletClick = { onOpenWallet() },
            icon = Icons.Filled.Group,
        )
        if (loading && channels.isEmpty()) {
            LoadingBox(text = stringResource(R.string.common_loading))
        } else if (failed && channels.isEmpty()) {
            com.addonstation.mobile.ui.components.ErrorState(onRetry = { reloadTick += 1 })
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 10.dp,
                    bottom = 12.dp,
                ),
            ) {
                item {
                    CategoryHeader(
                        title = stringResource(R.string.comm_main_rooms),
                        open = mainOpen,
                        onToggle = { mainOpen = !mainOpen },
                    )
                }
                if (mainOpen) {
                    items(channels.filter { it.category == "main" }, key = { it.id }) { channel ->
                        RoomRow(
                            channel = channel,
                            unread = unread[channel.id]?.unread ?: 0,
                            mentions = unread[channel.id]?.mentions ?: 0,
                            selected = false,
                            highlighted = false,
                            onClick = { onOpenRoom(channel) },
                        )
                    }
                }
                item {
                    CategoryHeader(
                        title = stringResource(R.string.comm_lang_rooms),
                        open = langOpen,
                        onToggle = { langOpen = !langOpen },
                    )
                }
                if (langOpen) {
                    items(channels.filter { it.category == "lang" }, key = { it.id }) { channel ->
                        RoomRow(
                            channel = channel,
                            unread = unread[channel.id]?.unread ?: 0,
                            mentions = unread[channel.id]?.mentions ?: 0,
                            selected = false,
                            highlighted = channel.id == "lang-$appLanguage" && !signedIn,
                            onClick = { onOpenRoom(channel) },
                        )
                    }
                }
            }

            // لوحة المستخدم أسفل الشاشة (كما في Discord)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Bg.copy(alpha = 0.96f))
                    .border(1.dp, AppColors.Border)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box {
                    // الزائر ⇒ صورة المجهول، والمسجّل ⇒ صورته مع نقطة الاتصال
                    if (profile == null) {
                        com.addonstation.mobile.ui.components.AnonymousAvatarImage(size = 40.dp)
                    } else {
                        AvatarImage(
                            url = assetUrl(container, profile?.avatarUrl),
                            size = 40.dp,
                            online = profile?.showOnlineStatus == true,
                            borderColor = AppColors.Green,
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        profile?.displayName ?: profile?.username ?: stringResource(
                            if (signedIn) R.string.common_loading else R.string.common_guest,
                        ),
                        style = AppText.chatName,
                        color = AppColors.Text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (profile != null) {
                        RankBadge(role = profile!!.role, plan = profile!!.effectivePlan)
                    } else if (!signedIn) {
                        Text(
                            stringResource(R.string.msg_sign_in_to_participate),
                            style = AppText.tiny,
                            color = AppColors.Muted,
                            modifier = Modifier.clickable { onLogin() },
                        )
                    }
                }
                // ترس لوحة المستخدم: ينقل إلى تعديل الملف الشخصي (لا إلى إعدادات التطبيق)
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated2)
                        .clickable { onOpenEditProfile() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = stringResource(R.string.profile_edit),
                        tint = AppColors.Muted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryHeader(title: String, open: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(vertical = 10.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = if (open) Icons.Filled.KeyboardArrowDown else Icons.Filled.KeyboardArrowUp,
            contentDescription = null,
            tint = AppColors.Muted,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = title.uppercase(),
            style = AppText.tiny.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
            color = AppColors.Muted,
        )
    }
}

/** صف غرفة 48dp بنصف قطر 10 مع مؤشرات غير المقروء والمنشن والقفل. */
@Composable
private fun RoomRow(
    channel: ChatChannel,
    unread: Int,
    mentions: Int,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    selected -> AppColors.Elevated
                    highlighted -> AppColors.GreenGlow
                    else -> androidx.compose.ui.graphics.Color.Transparent
                }
            )
            .clickable { onClick() }
            .height(48.dp)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (selected) {
            Box(
                Modifier
                    .size(width = 3.dp, height = 22.dp)
                    .clip(CircleShape)
                    .background(AppColors.Green),
            )
        }
        RoomIcon(channelId = channel.id, size = 22.dp)
        Text(
            // غرف اللغات باسمها بلغتها (العربية · Русский · 中文 …) كما في ChannelNames.kt
            text = channelDisplayName(channel),
            style = AppText.body.copy(fontWeight = if (unread > 0 || mentions > 0) FontWeight.Bold else FontWeight.Normal),
            color = if (unread > 0 || mentions > 0) AppColors.Text else AppColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        // قفل ظاهر لكل غرفة لا يملك صاحبُها حق الكتابة فيها (قراءة فقط)
        if (channel.isLocked || channel.postPermission == "admin_only" || channel.postPermission == "creators_admin") {
            Icon(Icons.Filled.Lock, contentDescription = null, tint = AppColors.Dim, modifier = Modifier.size(15.dp))
        }
        if (mentions > 0) {
            CountPill(mentions)
        } else if (unread > 0) {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(androidx.compose.ui.graphics.Color.White),
            )
        }
    }
}

/** نص ترحيبي لقائمة الغرف الفارغة. */
@Composable
fun RoomsEmpty() {
    Text(
        stringResource(R.string.chat_no_messages),
        style = AppText.bodySmall,
        color = AppColors.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(Brush.verticalGradient(listOf(AppColors.Surface, AppColors.Surface))),
    )
}
