package com.addonstation.mobile.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.components.bottomBarInsets
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont

@Composable
internal fun AdminHeader(
    kicker: String,
    title: String,
    onBack: () -> Unit,
    onMore: () -> Unit,
    onLock: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    palette: HomePalette = LocalHomePalette.current,
) {
    val isArabic = appLocaleCode().equals("ar", ignoreCase = true)
    val bodyFont = if (isArabic) TajawalFont else NunitoFont
    Column(Modifier.fillMaxWidth().background(palette.barSurface)) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AdminHeaderAction(
                icon = Icons.AutoMirrored.Filled.ArrowBack,
                description = stringResource(R.string.common_back),
                onClick = onBack,
                palette = palette,
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                Text(
                    kicker,
                    style = TextStyle(
                        fontFamily = bodyFont,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = if (isArabic) 0.sp else 1.sp,
                    ),
                    color = palette.goldText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    title,
                    style = TextStyle(
                        fontFamily = if (isArabic) TajawalFont else PixelFont,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = if (isArabic) 0.sp else 1.sp,
                    ),
                    color = palette.text,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            AdminHeaderAction(
                icon = Icons.Filled.MoreHoriz,
                description = stringResource(R.string.admin_more),
                onClick = onMore,
                palette = palette,
            )
            AdminHeaderAction(
                icon = Icons.Filled.Lock,
                description = stringResource(R.string.admin_lock_now),
                onClick = onLock,
                palette = palette,
                tint = palette.goldText,
            )
            trailing?.invoke()
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
    }
}

@Composable
internal fun AdminSubscreenHeader(
    kicker: String,
    title: String,
    onBack: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    palette: HomePalette = LocalHomePalette.current,
) {
    AdminHeader(
        kicker = kicker,
        title = title,
        onBack = onBack,
        onMore = {
            AdminPanelTabRequest.requestMore()
            onBack()
        },
        onLock = {
            AdminSession.lock()
            onBack()
        },
        trailing = trailing,
        palette = palette,
    )
}

@Composable
private fun AdminHeaderAction(
    icon: ImageVector,
    description: String,
    onClick: () -> Unit,
    palette: HomePalette,
    tint: Color = palette.text,
) {
    Box(
        Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(palette.surface)
            .border(1.dp, palette.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
internal fun AdminBottomBar(
    selectedTab: Int?,
    paymentsBadge: Int,
    chatBadge: Int,
    onSelect: (Int) -> Unit,
    palette: HomePalette = LocalHomePalette.current,
) {
    val isArabic = appLocaleCode().equals("ar", ignoreCase = true)
    val labels = listOf(
        stringResource(R.string.admin_overview),
        stringResource(R.string.admin_users),
        stringResource(R.string.admin_payments),
        stringResource(R.string.admin_content),
        stringResource(R.string.admin_chat_plain),
    )
    val icons = listOf(
        Icons.Filled.Dashboard,
        Icons.Filled.Group,
        Icons.Filled.CreditCard,
        Icons.Filled.ViewInAr,
        Icons.Filled.ChatBubble,
    )
    Column(
        Modifier.fillMaxWidth().background(palette.bg).windowInsetsPadding(bottomBarInsets()),
    ) {
        val shape = RoundedCornerShape(26.dp)
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
                .height(72.dp)
                .shadow(30.dp, shape, clip = false, ambientColor = palette.barShadow, spotColor = palette.barShadow)
                .clip(shape)
                .background(palette.barSurface)
                .border(1.dp, palette.border, shape),
        ) {
            Row(
                Modifier.fillMaxSize().padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                labels.forEachIndexed { index, label ->
                    AdminBottomItem(
                        label = label,
                        icon = icons[index],
                        selected = selectedTab == index,
                        badge = when (index) { 2 -> paymentsBadge; 4 -> chatBadge; else -> 0 },
                        isArabic = isArabic,
                        palette = palette,
                        onClick = { onSelect(index) },
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}

@Composable
private fun AdminBottomItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badge: Int,
    isArabic: Boolean,
    palette: HomePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = modifier
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label + if (badge > 0) ", $badge" else ""
            }
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.height(64.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                Modifier
                    .size(width = 44.dp, height = 32.dp)
                    .then(
                        if (selected) Modifier
                            .shadow(14.dp, shape, clip = false, ambientColor = palette.blue.copy(alpha = 0.4f), spotColor = palette.blue.copy(alpha = 0.4f))
                            .background(Brush.linearGradient(listOf(palette.blue, palette.accentBlue)), shape)
                        else Modifier,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = if (selected) Color.White else palette.inactive, modifier = Modifier.size(21.dp))
                if (badge > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 3.dp, y = (-4).dp)
                            .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                            .clip(CircleShape)
                            .background(palette.notificationBadge)
                            .border(2.dp, palette.barSurface, CircleShape)
                            .padding(horizontal = 4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(if (badge > 99) "99+" else badge.toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, maxLines = 1)
                    }
                }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                label,
                color = if (selected) palette.blueLink else palette.inactive,
                style = TextStyle(fontFamily = if (isArabic) TajawalFont else NunitoFont, fontSize = if (isArabic) 10.sp else 11.sp, fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun AdminPermissionDenied(onBack: () -> Unit, palette: HomePalette = LocalHomePalette.current) {
    val error = adminError(palette)
    Column(
        Modifier.fillMaxSize().background(palette.bg).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Lock, contentDescription = null, tint = error, modifier = Modifier.size(52.dp))
        Spacer(Modifier.height(14.dp))
        Text(stringResource(R.string.admin_not_allowed), style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold), color = error, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.admin_gate_denied_body), style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp), color = palette.textMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        AdminOutlineButton(stringResource(R.string.common_back), onBack, palette, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp))
    }
}

@Composable
internal fun AdminSectionHeading(text: String, palette: HomePalette = LocalHomePalette.current, modifier: Modifier = Modifier) {
    Text(
        text,
        style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
        color = palette.goldText,
        modifier = modifier.padding(top = 14.dp, bottom = 8.dp),
    )
}

@Composable
internal fun AdminStatCard(
    value: String,
    label: String,
    attention: Boolean,
    onClick: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val warning = adminWarning(palette)
    Column(
        modifier
            .height(88.dp)
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, if (attention) warning.copy(alpha = 0.7f) else palette.border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(value, style = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.ExtraBold), color = if (attention) warning else palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(3.dp))
        Text(label, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold), color = palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AdminToolCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accent: Color,
    onClick: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = 0.16f)).border(1.dp, accent.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = palette.text, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = TextStyle(fontSize = 12.sp), color = palette.textMuted, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = palette.inactive, modifier = Modifier.size(18.dp))
    }
}

@Composable
internal fun AdminFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
    height: Dp = 40.dp,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(height / 2)
    Box(
        modifier
            .semantics { this.selected = selected }
            .height(height)
            .clip(shape)
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(palette.blue, palette.accentBlue)))
                else Modifier.background(palette.surface).border(1.dp, palette.border, shape),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (selected) Color.White else palette.chipText, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AdminSegmentTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
    modifier: Modifier = Modifier,
    fontSize: TextUnit = 14.sp,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .semantics { role = Role.Tab; this.selected = selected }
            .height(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(
                if (selected) Modifier.background(Brush.linearGradient(listOf(palette.blue, palette.accentBlue)))
                else Modifier,
            )
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = TextStyle(fontSize = fontSize, fontWeight = FontWeight.ExtraBold),
            color = if (selected) Color.White else palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun AdminSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    palette: HomePalette = LocalHomePalette.current,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Filled.Search, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(20.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(fontSize = 14.sp, color = palette.text),
            cursorBrush = SolidColor(palette.blue),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { }),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = TextStyle(fontSize = 14.sp), color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    inner()
                }
            },
        )
    }
}

@Composable
internal fun AdminPrimaryButton(
    text: String,
    onClick: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    enabled: Boolean = true,
    fill: Color? = null,
) {
    val shape = RoundedCornerShape(if (height <= 38.dp) 10.dp else 12.dp)
    val brush = when {
        !enabled -> Brush.linearGradient(listOf(palette.surfaceHigh, palette.surfaceHigh))
        fill == null -> Brush.linearGradient(listOf(palette.blue, palette.accentBlue))
        else -> Brush.linearGradient(listOf(fill, fill))
    }
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(brush)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.ExtraBold), color = if (enabled) Color.White else palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AdminOutlineButton(
    text: String,
    onClick: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    tint: Color = palette.textMuted,
    borderColor: Color = palette.border,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(if (height <= 38.dp) 10.dp else 12.dp)
    Box(
        modifier
            .height(height)
            .clip(shape)
            .background(Color.Transparent)
            .border(1.dp, if (enabled) borderColor else palette.border.copy(alpha = 0.55f), shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Bold), color = if (enabled) tint else palette.inactive, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun AdminRoleBadge(role: String?, plan: String?, palette: HomePalette = LocalHomePalette.current) {
    val (labelRes, fill, foreground, moderator) = when {
        role == "admin" -> RoleBadgeStyle(R.string.role_admin, palette.goldFill, Color(0xFF1B1405))
        role == "moderator" -> RoleBadgeStyle(R.string.role_moderator_plain, Color(0xFF3FBF6A).copy(alpha = 0.18f), if (palette.isDark) Color(0xFF7FD99A) else Color(0xFF1E7A44), true)
        role == "creator" -> RoleBadgeStyle(R.string.role_creator_plain, palette.blue, Color.White)
        plan == "legendary" -> RoleBadgeStyle(R.string.role_legendary, palette.goldFill, Color(0xFF1B1405))
        plan == "epic" -> RoleBadgeStyle(R.string.role_epic, palette.epic, Color.White)
        else -> RoleBadgeStyle(R.string.role_user, palette.surfaceHigh, palette.textMuted)
    }
    Row(
        Modifier.clip(RoundedCornerShape(9.dp)).background(fill).padding(horizontal = 9.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (moderator) Icon(Icons.Filled.Shield, contentDescription = null, tint = foreground, modifier = Modifier.size(12.dp))
        Text(stringResource(labelRes), style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.ExtraBold), color = foreground, maxLines = 1)
    }
}

private data class RoleBadgeStyle(
    val labelRes: Int,
    val fill: Color,
    val foreground: Color,
    val moderator: Boolean = false,
)

@Composable
internal fun AdminTierBadge(plan: String, palette: HomePalette = LocalHomePalette.current) {
    val (labelRes, fill, foreground) = when (plan.lowercase()) {
        "legendary" -> Triple(R.string.plan_legendary_name, palette.goldFill, Color(0xFF1B1405))
        "epic" -> Triple(R.string.plan_epic_name, palette.epic, Color.White)
        else -> Triple(R.string.plan_free_name, palette.surfaceHigh, palette.textMuted)
    }
    Text(
        stringResource(labelRes),
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
        color = foreground,
        maxLines = 1,
        modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(fill).padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

internal fun adminWarning(palette: HomePalette): Color = if (palette.isDark) Color(0xFFF0A04B) else Color(0xFFB25E00)
internal fun adminError(palette: HomePalette): Color = if (palette.isDark) Color(0xFFFF6B78) else Color(0xFFB3262E)
internal fun adminErrorText(palette: HomePalette): Color = if (palette.isDark) Color(0xFFFF8791) else Color(0xFFB3262E)
internal fun adminOk(palette: HomePalette): Color = if (palette.isDark) Color(0xFF3FBF6A) else Color(0xFF1E7A44)
internal fun adminOkText(palette: HomePalette): Color = if (palette.isDark) Color(0xFF7FD99A) else Color(0xFF1E7A44)
