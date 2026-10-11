package com.addonstation.mobile.ui.community

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
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
import com.addonstation.mobile.ui.components.RoomIcon
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.util.LocaleManager
import kotlinx.coroutines.launch

/**
 * بوابة المجتمع: قواعد المجتمع قبل الدخول الأول (مرة واحدة لكل مستخدم)،
 * ثم قائمة الغرف بأسلوب Discord.
 */
@Suppress("UNUSED_PARAMETER") // Profile and wallet remain available through the app's existing navigation.
@Composable
fun CommunityGate(
    onOpenRoom: (ChatChannel) -> Unit,
    onOpenEditProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onLogin: () -> Unit,
    onDeclineToHome: () -> Unit,
    scrollToTopRequest: Int = 0,
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
        scrollToTopRequest = scrollToTopRequest,
    )
}

/** شاشة قواعد المجتمع المُعاد تصميمها؛ منطق القبول والتنقّل يبقى في CommunityGate. */
@Composable
fun RulesScreen(onAccepted: () -> Unit, onDecline: () -> Unit) {
    CommunityRulesScreen(onAccepted = onAccepted, onDecline = onDecline)
}

/** قائمة الغرف ذات المعاينات الحقيقية؛ شريط التنقل الرئيسي يبقى في مستوى التطبيق. */
@Composable
fun RoomListScreen(
    onOpenRoom: (ChatChannel) -> Unit,
    scrollToTopRequest: Int = 0,
) {
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) listState.animateScrollToItem(0)
    }
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val unread by container.state.unread.collectAsState()
    val roomPeeks by com.addonstation.mobile.ui.chat.ChatPeek.rooms.collectAsState()

    // Seed from memory so returning from a room never flashes an empty list.
    var channels by remember { mutableStateOf(container.chat.cachedChannels()) }
    var loading by remember { mutableStateOf(channels.isEmpty()) }
    var mainOpen by rememberSaveable { mutableStateOf(true) }
    var langOpen by rememberSaveable { mutableStateOf(true) }
    val appLanguage = LocaleManager.byCode(appLocaleCode()).code
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = channels.isEmpty()
        failed = false
        try {
            // Only the admin may create/repair the Cash services room; the server remains authoritative.
            container.chat.ensureCashServicesRoom(profile?.role == "admin")
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

    Column(Modifier.fillMaxSize().background(Spec.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spec.screenPad, end = Spec.screenPad, top = 16.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.comm_title),
                style = Spec.screenTitle,
                color = Spec.gold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.comm_all_rooms),
                style = Spec.secondary.copy(fontWeight = FontWeight.Bold),
                color = Spec.textMuted,
                maxLines = 1,
            )
        }

        Row(
            modifier = Modifier
                .padding(horizontal = Spec.screenPad, vertical = 8.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Spec.cardRadius))
                .background(Spec.surface)
                .border(1.dp, Spec.border, RoundedCornerShape(Spec.cardRadius))
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.comm_rules_banner),
                style = Spec.roomPreview,
                color = Spec.chipLabel,
            )
        }

        when {
            loading && channels.isEmpty() -> RoomsLoadingSkeleton()
            failed && channels.isEmpty() -> com.addonstation.mobile.ui.components.ErrorState(
                onRetry = { reloadTick += 1 },
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    start = Spec.chatListPad,
                    end = Spec.chatListPad,
                    top = 4.dp,
                    bottom = 12.dp,
                ),
            ) {
                if (channels.isEmpty()) {
                    item(key = "rooms-empty") { RoomsEmpty() }
                }
                item(key = "rooms-main-heading") {
                    CategoryHeader(
                        title = stringResource(R.string.comm_main_rooms),
                        open = mainOpen,
                        onToggle = { mainOpen = !mainOpen },
                    )
                }
                if (mainOpen) {
                    items(
                        channels.filter { it.category.equals("main", ignoreCase = true) && !isLanguageRoom(it.id, it.name) },
                        key = { it.id },
                    ) { channel ->
                        RoomRow(
                            channel = channel,
                            peek = roomPeeks[channel.id],
                            unread = unread[channel.id]?.unread ?: 0,
                            mentions = unread[channel.id]?.mentions ?: 0,
                            selected = false,
                            highlighted = false,
                            onClick = { onOpenRoom(channel) },
                        )
                    }
                }
                item(key = "rooms-language-heading") {
                    CategoryHeader(
                        title = stringResource(R.string.comm_lang_rooms),
                        open = langOpen,
                        onToggle = { langOpen = !langOpen },
                    )
                }
                if (langOpen) {
                    items(
                        channels.filter { it.category.equals("lang", ignoreCase = true) || isLanguageRoom(it.id, it.name) },
                        key = { it.id },
                    ) { channel ->
                        RoomRow(
                            channel = channel,
                            peek = roomPeeks[channel.id],
                            unread = unread[channel.id]?.unread ?: 0,
                            mentions = unread[channel.id]?.mentions ?: 0,
                            selected = false,
                            highlighted = channel.id == "lang-$appLanguage" && !signedIn,
                            onClick = { onOpenRoom(channel) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RoomsLoadingSkeleton() {
    val transition = androidx.compose.animation.core.rememberInfiniteTransition(label = "rooms-skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.32f,
        targetValue = 0.82f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation = androidx.compose.animation.core.tween(1200),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "rooms-skeleton-alpha",
    )
    val shimmer = Spec.surfaceHigh.copy(alpha = alpha)
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .clip(RoundedCornerShape(Spec.cardRadius))
                    .background(Spec.surface)
                    .padding(horizontal = 10.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(Modifier.size(48.dp).clip(RoundedCornerShape(16.dp)).background(shimmer))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.fillMaxWidth(0.62f).height(14.dp).clip(CircleShape).background(shimmer))
                    Box(Modifier.fillMaxWidth(0.82f).height(10.dp).clip(CircleShape).background(shimmer))
                }
                Box(Modifier.size(width = 28.dp, height = 12.dp).clip(CircleShape).background(shimmer))
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

/** One room row: cached last message/time and presence are shown only when observed. */
@Composable
private fun RoomRow(
    channel: ChatChannel,
    peek: com.addonstation.mobile.ui.chat.ChatRoomPeek?,
    unread: Int,
    mentions: Int,
    selected: Boolean,
    highlighted: Boolean,
    onClick: () -> Unit,
) {
    val preview = peek?.preview?.takeIf { it.isNotBlank() } ?: when (peek?.messageType) {
        "image" -> stringResource(R.string.chat_attach_photo)
        "sticker" -> stringResource(R.string.chat_stickers)
        "video" -> stringResource(R.string.chat_video_label)
        "audio" -> stringResource(R.string.chat_audio_label)
        "announcement" -> stringResource(R.string.chat_announcement)
        else -> channelDisplayDescription(channel).orEmpty()
    }
    val time = lastTimeOf(peek?.createdAt)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clip(RoundedCornerShape(Spec.cardRadius))
                .background(
                    when {
                        selected -> Spec.surfaceHigh
                        highlighted -> Spec.blue.copy(alpha = 0.12f)
                        else -> androidx.compose.ui.graphics.Color.Transparent
                    }
                )
                .clickable { onClick() }
                .heightIn(min = 68.dp)
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoomIcon(channelId = channel.id, size = 48.dp)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = channelDisplayName(channel),
                        style = Spec.roomName,
                        color = Spec.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (channel.isLocked || channel.postPermission == "admin_only" || channel.postPermission == "creators_admin") {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = null,
                            tint = Spec.textMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
                Text(
                    text = preview,
                    style = Spec.roomPreview,
                    color = Spec.textMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                peek?.onlineCount?.let { count ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            tint = Spec.green,
                            modifier = Modifier.size(12.dp),
                        )
                        Text(
                            stringResource(R.string.chat_online, count),
                            style = Spec.roomTime,
                            color = Spec.green,
                            maxLines = 1,
                        )
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                if (time.isNotBlank()) {
                    Text(
                        text = time,
                        style = Spec.roomTime,
                        color = Spec.textMuted,
                        maxLines = 1,
                    )
                }
                val badgeCount = mentions.takeIf { it > 0 } ?: unread
                if (badgeCount > 0) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 22.dp, minHeight = 22.dp)
                            .clip(CircleShape)
                            .background(Spec.blue)
                            .padding(horizontal = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            badgeCount.toString(),
                            style = Spec.badge,
                            color = androidx.compose.ui.graphics.Color.White,
                        )
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 70.dp)
                .height(1.dp)
                .background(Spec.border.copy(alpha = 0.7f)),
        )
    }
}

/** Returns a time only for a timestamp already observed from a real chat message. */
private fun lastTimeOf(iso: String?): String =
    iso?.let { com.addonstation.mobile.util.TimeFormat.timeOnly(it, java.util.Locale.getDefault()) }.orEmpty()

/** نص ترحيبي لقائمة الغرف الفارغة. */
@Composable
fun RoomsEmpty() {
    Text(
        stringResource(R.string.chat_no_messages_plain),
        style = AppText.bodySmall,
        color = AppColors.Muted,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp)
            .background(Brush.verticalGradient(listOf(AppColors.Surface, AppColors.Surface))),
    )
}
