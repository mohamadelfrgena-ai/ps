package com.addonstation.mobile.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.nav.MainTab
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.TajawalFont

/**
 * System area reserved below floating navigation, for both gesture and 3-button navigation.
 * Only the bottom gesture inset is included so the bar never gains side padding in portrait.
 */
@Composable
fun bottomBarInsets(): WindowInsets = WindowInsets.navigationBars.union(
    WindowInsets.systemGestures.only(WindowInsetsSides.Bottom),
)

/** Five-tab floating navigation, in logical start-to-end order (automatically mirrored in RTL). */
@Composable
fun BottomNavBar(
    current: MainTab,
    onSelect: (MainTab) -> Unit,
    chatUnreadCount: Int = 0,
    hasPendingFriendRequests: Boolean = false,
) {
    // Follow the selected app theme on every tab. Individual destinations must not
    // force the shared navigation chrome back to the dark palette.
    val palette = LocalHomePalette.current
    Box(Modifier.fillMaxWidth().background(palette.bg)) {
        Column(
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(bottomBarInsets()),
        ) {
            // Twenty dp of reserved overflow lets the center Home control rise above the bar.
            Box(Modifier.fillMaxWidth().height(92.dp)) {
                val shape = RoundedCornerShape(26.dp)
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(72.dp)
                        .shadow(
                            elevation = 30.dp,
                            shape = shape,
                            clip = false,
                            ambientColor = palette.barShadow,
                            spotColor = palette.barShadow,
                        )
                        .clip(shape)
                        .background(palette.barSurface.copy(alpha = 0.96f))
                        .border(1.dp, palette.border, shape),
                )
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .height(72.dp)
                        .padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    MainTab.entries.forEach { tab ->
                        NavItem(
                            tab = tab,
                            selected = current == tab,
                            palette = palette,
                            chatUnreadCount = if (tab == MainTab.Community) chatUnreadCount else 0,
                            showProfileDot = tab == MainTab.Profile && hasPendingFriendRequests,
                            onClick = { onSelect(tab) },
                            modifier = Modifier.weight(1f).fillMaxHeight(),
                        )
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun NavItem(
    tab: MainTab,
    selected: Boolean,
    palette: HomePalette,
    chatUnreadCount: Int,
    showProfileDot: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = stringResource(tab.labelRes)
    val font = if (appLocaleCode().equals("ar", ignoreCase = true)) TajawalFont else NunitoFont
    val unreadDescription = if (tab == MainTab.Community && chatUnreadCount > 0) {
        pluralStringResource(R.plurals.cd_chat_unread, chatUnreadCount, chatUnreadCount)
    } else null
    val newActivityDescription = if (showProfileDot) stringResource(R.string.cd_new_activity) else null
    val tabDescription = listOfNotNull(label, unreadDescription, newActivityDescription).joinToString(", ")
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.96f else 1f, label = "navScale")

    Box(
        modifier = modifier
            .scale(scale)
            .semantics { contentDescription = tabDescription }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (tab.isCenter) {
            Box(Modifier.fillMaxSize()) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .offset(y = (-20).dp)
                        .size(60.dp)
                        .then(
                            if (selected) {
                                Modifier.shadow(
                                    elevation = 22.dp,
                                    shape = CircleShape,
                                    clip = false,
                                    ambientColor = palette.blue.copy(alpha = 0.55f),
                                    spotColor = palette.blue.copy(alpha = 0.55f),
                                )
                            } else {
                                Modifier.shadow(
                                    elevation = 18.dp,
                                    shape = CircleShape,
                                    clip = false,
                                    ambientColor = palette.barShadow,
                                    spotColor = palette.barShadow,
                                )
                            },
                        )
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                Brush.linearGradient(listOf(palette.blue, palette.accentBlue))
                            } else {
                                Brush.linearGradient(listOf(palette.homeInactiveBg, palette.homeInactiveBg))
                            },
                        )
                        .border(3.dp, palette.bg, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                        contentDescription = null,
                        tint = if (selected) Color.White else palette.homeInactiveIcon,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Text(
                    text = label,
                    modifier = Modifier.align(Alignment.BottomCenter).offset(y = (-10).dp),
                    color = if (selected) palette.blueLink else palette.inactive,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontFamily = font,
                    maxLines = 1,
                )
            }
        } else {
            Column(
                Modifier.height(64.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier.size(width = 52.dp, height = 34.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    val pillShape = RoundedCornerShape(17.dp)
                    Box(
                        Modifier
                            .fillMaxSize()
                            .then(
                                if (selected) {
                                    Modifier.shadow(
                                        elevation = 14.dp,
                                        shape = pillShape,
                                        clip = false,
                                        ambientColor = palette.blue.copy(alpha = 0.45f),
                                        spotColor = palette.blue.copy(alpha = 0.45f),
                                    )
                                } else Modifier
                            )
                            .clip(pillShape)
                            .then(
                                if (selected) {
                                    Modifier.background(
                                        Brush.linearGradient(listOf(palette.blue, palette.accentBlue)),
                                    )
                                } else Modifier
                            ),
                    )
                    Icon(
                        imageVector = if (selected) tab.filledIcon else tab.outlinedIcon,
                        contentDescription = null,
                        tint = if (selected) Color.White else palette.inactive,
                        modifier = Modifier.size(24.dp),
                    )
                    if (chatUnreadCount > 0) {
                        val badgeText = if (chatUnreadCount > 99) "99+" else chatUnreadCount.toString()
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = 2.dp, y = (-4).dp)
                                .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                                .clip(CircleShape)
                                .background(palette.notificationBadge)
                                .border(2.dp, palette.barSurface, CircleShape)
                                .padding(horizontal = 4.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = badgeText,
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = font,
                                maxLines = 1,
                            )
                        }
                    }
                    if (showProfileDot) {
                        Box(
                            Modifier
                                .align(Alignment.TopEnd)
                                .offset(x = (-5).dp, y = (-1).dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(palette.notificationBadge)
                                .border(2.dp, palette.barSurface, CircleShape),
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                Text(
                    text = label,
                    color = if (selected) palette.blueLink else palette.inactive,
                    fontSize = 11.sp,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                    fontFamily = font,
                    maxLines = 1,
                )
            }
        }
    }
}
