package com.addonstation.mobile.ui.community

import androidx.compose.animation.core.animateFloat
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
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
@OptIn(ExperimentalMaterial3Api::class)
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
    var searchOpen by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }
    var selectedFilter by rememberSaveable { mutableStateOf(RoomsFilter.ALL) }
    var showRules by rememberSaveable { mutableStateOf(false) }
    var serverRules by remember { mutableStateOf<List<String>>(emptyList()) }
    val appLanguage = LocaleManager.byCode(appLocaleCode()).code
    val isArabic = appLanguage == "ar"
    val isCjk = appLanguage == "zh"
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
    LaunchedEffect(showRules, appLanguage) {
        if (showRules) serverRules = container.chat.rules(appLanguage)
    }

    val defaultRules = listOf(
        stringResource(R.string.chat_rule_1),
        stringResource(R.string.chat_rule_2),
        stringResource(R.string.chat_rule_3),
        stringResource(R.string.chat_rule_4),
        stringResource(R.string.chat_rule_5),
        stringResource(R.string.chat_rule_6),
        stringResource(R.string.chat_rule_7),
        stringResource(R.string.chat_rule_8),
    )
    val totalUnread = unread.values.sumOf { it.unread }
    val filterItems = listOf(
        RoomsFilter.ALL to stringResource(R.string.common_all),
        RoomsFilter.UNREAD to stringResource(R.string.chat_filter_unread),
        RoomsFilter.MAIN to stringResource(R.string.comm_main_rooms),
        RoomsFilter.LANG to stringResource(R.string.comm_lang_rooms),
    )
    val sortedChannels = remember(channels, roomPeeks) {
        channels.sortedWith(
            compareByDescending<ChatChannel> { roomPeeks[it.id]?.createdAt != null }
                .thenByDescending { roomPeeks[it.id]?.createdAt.orEmpty() }
                .thenBy { it.sortOrder },
        )
    }
    val normalizedQuery = query.trim()
    val visibleChannels = remember(sortedChannels, roomPeeks, unread, normalizedQuery, selectedFilter) {
        sortedChannels.filter { channel ->
            val languageRoom = isLanguageRoom(channel.id, channel.name)
            val peek = roomPeeks[channel.id]
            val matchingQuery = normalizedQuery.isEmpty() || listOfNotNull(
                channelDisplayName(channel),
                channelDisplayDescription(channel),
                peek?.senderName,
                peek?.preview,
            ).any { it.contains(normalizedQuery, ignoreCase = true) }
            val matchingFilter = when (selectedFilter) {
                RoomsFilter.ALL -> true
                RoomsFilter.UNREAD -> (unread[channel.id]?.unread ?: 0) > 0
                RoomsFilter.MAIN -> !languageRoom && channel.category.equals("main", ignoreCase = true)
                RoomsFilter.LANG -> languageRoom || channel.category.equals("lang", ignoreCase = true)
            }
            matchingQuery && matchingFilter
        }
    }
    val mainChannels = visibleChannels.filter { it.category.equals("main", ignoreCase = true) && !isLanguageRoom(it.id, it.name) }
    val languageChannels = visibleChannels.filter { it.category.equals("lang", ignoreCase = true) || isLanguageRoom(it.id, it.name) }
    val showMainSection = selectedFilter != RoomsFilter.LANG
    val showLanguageSection = selectedFilter != RoomsFilter.MAIN
    val titleFont = when {
        isArabic -> com.addonstation.mobile.ui.theme.TajawalFont
        isCjk || appLanguage == "ru" -> com.addonstation.mobile.ui.theme.NunitoFont
        else -> com.addonstation.mobile.ui.theme.PixelFont
    }

    Column(Modifier.fillMaxSize().background(Spec.bg)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = Spec.screenPad, end = Spec.screenPad, top = 12.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.comm_title),
                style = Spec.screenTitle.copy(
                    fontFamily = titleFont,
                    fontSize = 20.sp,
                    letterSpacing = if (isArabic || isCjk) 0.sp else 0.3.sp,
                ),
                color = Spec.gold,
                maxLines = 1,
            )
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .size(Spec.touch)
                    .clip(CircleShape)
                    .background(if (searchOpen) Spec.blue.copy(alpha = 0.16f) else Spec.surface)
                    .border(1.dp, if (searchOpen) Spec.blue.copy(alpha = 0.45f) else Spec.border, CircleShape)
                    .clickable {
                        searchOpen = !searchOpen
                        if (!searchOpen) query = ""
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = if (searchOpen) androidx.compose.material.icons.Icons.Filled.Close else androidx.compose.material.icons.Icons.Filled.Search,
                    contentDescription = stringResource(R.string.cd_search_rooms),
                    tint = if (searchOpen) Spec.blueLink else Spec.textMuted,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        if (searchOpen) {
            Row(
                modifier = Modifier
                    .padding(horizontal = Spec.screenPad, vertical = 4.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Spec.surface)
                    .border(1.dp, Spec.border, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(androidx.compose.material.icons.Icons.Filled.Search, contentDescription = null, tint = Spec.textMuted, modifier = Modifier.size(18.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(stringResource(R.string.cd_search_rooms), style = Spec.secondary, color = Spec.textMuted, maxLines = 1)
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = Spec.secondary.copy(color = Spec.text, fontSize = 14.sp),
                        cursorBrush = androidx.compose.ui.graphics.SolidColor(Spec.blue),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Close,
                        contentDescription = stringResource(R.string.common_close),
                        tint = Spec.textMuted,
                        modifier = Modifier.size(18.dp).clickable { query = "" },
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .padding(horizontal = Spec.screenPad, vertical = 5.dp)
                .fillMaxWidth()
                .height(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Spec.gold.copy(alpha = 0.10f))
                .border(1.dp, Spec.gold.copy(alpha = 0.35f), RoundedCornerShape(12.dp))
                .clickable { showRules = true }
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(androidx.compose.material.icons.Icons.Filled.Shield, contentDescription = null, tint = Spec.gold, modifier = Modifier.size(16.dp))
            Text(
                stringResource(R.string.comm_rules_banner_plain),
                style = Spec.secondary.copy(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                color = Spec.chipLabel,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Icon(
                Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = Spec.textMuted,
                modifier = Modifier.size(16.dp).rotate(-90f),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spec.screenPad, vertical = 5.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            filterItems.forEach { (filter, label) ->
                val badge = if (filter == RoomsFilter.UNREAD) totalUnread else 0
                RoomsFilterChip(
                    label = label,
                    selected = selectedFilter == filter,
                    count = badge,
                    onClick = { selectedFilter = filter },
                )
            }
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
                if (visibleChannels.isEmpty()) {
                    item(key = "rooms-empty") { RoomsEmpty() }
                }
                if (showMainSection) {
                    item(key = "rooms-main-heading") {
                        CategoryHeader(
                            title = stringResource(R.string.comm_main_rooms),
                            open = mainOpen,
                            onToggle = { mainOpen = !mainOpen },
                        )
                    }
                    if (mainOpen) {
                        items(mainChannels, key = { it.id }) { channel ->
                            RoomRow(
                                channel = channel,
                                peek = roomPeeks[channel.id],
                                unread = unread[channel.id]?.unread ?: 0,
                                mentions = unread[channel.id]?.mentions ?: 0,
                                readOnly = com.addonstation.mobile.ui.community.isReadOnlyFor(channel, profile?.role),
                                onClick = { onOpenRoom(channel) },
                            )
                        }
                    }
                }
                if (showLanguageSection) {
                    item(key = "rooms-language-heading") {
                        CategoryHeader(
                            title = stringResource(R.string.comm_lang_rooms),
                            open = langOpen,
                            onToggle = { langOpen = !langOpen },
                        )
                    }
                    if (langOpen) {
                        items(languageChannels, key = { it.id }) { channel ->
                            RoomRow(
                                channel = channel,
                                peek = roomPeeks[channel.id],
                                unread = unread[channel.id]?.unread ?: 0,
                                mentions = unread[channel.id]?.mentions ?: 0,
                                readOnly = com.addonstation.mobile.ui.community.isReadOnlyFor(channel, profile?.role),
                                onClick = { onOpenRoom(channel) },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showRules) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRules = false },
            sheetState = sheetState,
            containerColor = Spec.barSurface,
        ) {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                Text(stringResource(R.string.chat_rules_title), style = Spec.sectionTitle.copy(fontSize = 18.sp), color = Spec.gold)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.heightIn(max = 460.dp)) {
                    items(serverRules.ifEmpty { defaultRules }) { rule ->
                        Text(
                            rule,
                            style = Spec.secondary.copy(fontSize = 14.sp, lineHeight = 21.sp),
                            color = Spec.text,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        )
                    }
                }
                androidx.compose.material3.TextButton(onClick = { showRules = false }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.common_close), color = Spec.blueLink)
                }
            }
        }
    }
}

private enum class RoomsFilter { ALL, UNREAD, MAIN, LANG }

@Composable
private fun RoomsFilterChip(label: String, selected: Boolean, count: Int = 0, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(36.dp)
            .clip(CircleShape)
            .background(if (selected) Spec.blue else Spec.surface)
            .border(1.dp, if (selected) Spec.blue else Spec.border, CircleShape)
            .clickable { onClick() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            label,
            style = Spec.chip.copy(fontWeight = FontWeight.ExtraBold),
            color = if (selected) androidx.compose.ui.graphics.Color.White else Spec.chipLabel,
            maxLines = 1,
        )
        if (count > 0) {
            Text(
                if (count > 99) "99+" else count.toString(),
                style = Spec.badge.copy(fontSize = 10.sp),
                color = if (selected) Spec.blue else androidx.compose.ui.graphics.Color.White,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(if (selected) androidx.compose.ui.graphics.Color.White else Spec.blue)
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                maxLines = 1,
            )
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
    readOnly: Boolean,
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
    val sender = peek?.senderName?.takeIf { it.isNotBlank() }
    val time = lastTimeOf(peek?.createdAt)
    val badgeCount = mentions.takeIf { it > 0 } ?: unread
    val roomName = if (channel.id == "cash-services") {
        stringResource(R.string.comm_room_cash)
    } else {
        channelDisplayName(channel)
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 1.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(androidx.compose.ui.graphics.Color.Transparent)
                .alpha(if (readOnly) 0.80f else 1f)
                .clickable { onClick() }
                .heightIn(min = 72.dp)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RoomAvatarPresence(
                channelId = channel.id,
                online = (peek?.onlineCount ?: 0) > 0,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text(
                        text = roomName,
                        style = Spec.roomName.copy(fontSize = 16.sp),
                        color = Spec.text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (readOnly) {
                        Icon(
                            Icons.Filled.Lock,
                            contentDescription = stringResource(R.string.comm_locked),
                            tint = Spec.textMuted,
                            modifier = Modifier.size(14.dp),
                        )
                    }
                    Spacer(Modifier.weight(1f))
                    if (time.isNotBlank()) {
                        Text(
                            text = time,
                            style = Spec.roomTime,
                            color = Spec.textMuted,
                            maxLines = 1,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = buildAnnotatedString {
                            if (sender != null) {
                                withStyle(SpanStyle(color = Spec.chipLabel, fontWeight = FontWeight.Bold)) {
                                    append("$sender: ")
                                }
                            }
                            withStyle(SpanStyle(color = Spec.textMuted)) { append(preview) }
                        },
                        style = Spec.roomPreview.copy(fontSize = 13.sp),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
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
                                if (badgeCount > 99) "99+" else badgeCount.toString(),
                                style = Spec.badge.copy(fontSize = 11.sp),
                                color = androidx.compose.ui.graphics.Color.White,
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .padding(start = 76.dp)
                .height(1.dp)
                .background(Spec.border.copy(alpha = 0.7f)),
        )
    }
}

@Composable
private fun RoomAvatarPresence(channelId: String, online: Boolean) {
    Box(Modifier.size(60.dp), contentAlignment = Alignment.Center) {
        if (online) {
            Box(
                Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(Spec.onlineFill)
                    .padding(2.dp)
                    .clip(CircleShape)
                    .background(Spec.bg)
                    .padding(2.dp),
                contentAlignment = Alignment.Center,
            ) {
                RoomIcon(channelId = channelId, size = 52.dp)
            }
        } else {
            RoomIcon(channelId = channelId, size = 52.dp)
        }
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
