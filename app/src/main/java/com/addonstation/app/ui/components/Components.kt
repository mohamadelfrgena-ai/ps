package com.addonstation.app.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.addonstation.app.LocalApp
import com.addonstation.app.R
import com.addonstation.app.core.Constants
import com.addonstation.app.core.ContentItem
import com.addonstation.app.core.Formats
import com.addonstation.app.core.MsgSegment
import com.addonstation.app.core.Plans
import com.addonstation.app.core.Profile
import com.addonstation.app.core.splitMentions
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import com.addonstation.app.ui.theme.LocalExtra
import com.addonstation.app.ui.theme.rankColor

// ---------------------------------------------------------------------------
// Images (network + bundled assets + gifs)
// ---------------------------------------------------------------------------

@Composable
fun AsImage(
    model: Any?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderRes: Int? = null
) {
    val app = LocalApp.current
    AsyncImage(
        model = model,
        contentDescription = contentDescription,
        imageLoader = app.imageLoader,
        modifier = modifier,
        contentScale = contentScale,
        placeholder = placeholderRes?.let { painterResource(it) },
        error = placeholderRes?.let { painterResource(it) }
    )
}

@Composable
fun Avatar(
    url: String?,
    name: String,
    size: Dp = 40.dp,
    online: Boolean? = null,
    modifier: Modifier = Modifier
) {
    val extra = LocalExtra.current
    Box(modifier = modifier.size(size)) {
        val resolved = remember(url) { Constants.resolveAsset(url) ?: Constants.DEFAULT_AVATAR }
        if (url.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape)
                    .background(extra.elevated),
                contentAlignment = Alignment.Center
            ) {
                Text(Formats.acronym(name), color = extra.dim, fontSize = (size.value / 2.6).sp, fontWeight = FontWeight.Bold)
            }
        } else {
            AsImage(
                model = resolved,
                contentDescription = name,
                modifier = Modifier.fillMaxSize().clip(CircleShape)
            )
        }
        if (online != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(size / 3.4)
                    .clip(CircleShape)
                    .background(if (online) Color(0xFF3DDC84) else extra.dim)
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Top bar + bottom nav (pixel-identical layout to the original mobile shell)
// ---------------------------------------------------------------------------

@Composable
fun TopBar(
    title: String,
    titleIcon: Int,
    vm: AppViewModel,
    showBack: Boolean = false,
    onBack: () -> Unit = {},
    onWallet: () -> Unit,
    onNotifications: () -> Unit,
    onProfile: () -> Unit
) {
    val extra = LocalExtra.current
    val balance by vm.balance.collectAsState()
    val unread by vm.unread.collectAsState()
    val session by vm.session.collectAsState()
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBack) {
                IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                    Icon(
                        androidx.compose.material.icons.Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = null
                    )
                }
            } else {
                AsImage(
                    model = Constants.APP_ICON,
                    contentDescription = "AddonStation",
                    modifier = Modifier.size(34.dp).clip(RoundedCornerShape(9.dp))
                )
            }
            Spacer(Modifier.width(10.dp))
            Icon(painterResource(titleIcon), contentDescription = null, tint = extra.diamond, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            // AC wallet pill
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(999.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onWallet() }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsImage(model = Constants.AC_COIN, contentDescription = "AC", modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text(
                    if (session.userId == null) "—" else Formats.count(balance),
                    style = MaterialTheme.typography.labelLarge
                )
                Spacer(Modifier.width(2.dp))
                Text("AC", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(4.dp))
            // Notifications bell with live badge
            IconButton(onClick = onNotifications, modifier = Modifier.size(38.dp)) {
                BadgedBox(badge = { if (unread > 0) Badge { Text(if (unread > 99) "99+" else unread.toString()) } }) {
                    Icon(painterResource(R.drawable.ic_bell), contentDescription = stringResource(R.string.nav_community))
                }
            }
            // Account avatar
            IconButton(onClick = onProfile, modifier = Modifier.size(38.dp)) {
                Avatar(url = session.profile?.avatarUrl, name = session.profile?.username ?: "?", size = 28.dp)
            }
        }
    }
}

private data class TabDef(val route: String, val label: Int, val icon: Int)

@Composable
fun AppBottomBar(current: String?, onSelect: (String) -> Unit) {
    val tabs = remember {
        listOf(
            TabDef(Routes.SETTINGS, R.string.nav_settings, R.drawable.ic_settings),
            TabDef(Routes.COMMUNITY, R.string.nav_community, R.drawable.ic_community),
            TabDef(Routes.HOME, R.string.nav_home, R.drawable.ic_home),
            TabDef(Routes.STORE, R.string.nav_store, R.drawable.ic_store),
            TabDef(Routes.PROFILE, R.string.nav_profile, R.drawable.ic_user)
        )
    }
    NavigationBar {
        tabs.forEach { t ->
            NavigationBarItem(
                selected = current == t.route || (t.route == Routes.HOME && current == null),
                onClick = { onSelect(t.route) },
                icon = { Icon(painterResource(t.icon), contentDescription = stringResource(t.label), modifier = Modifier.size(22.dp)) },
                label = { Text(stringResource(t.label), fontSize = 10.sp, maxLines = 1) }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Rank / plan / user chips
// ---------------------------------------------------------------------------

@Composable
fun PlanTag(plan: String, modifier: Modifier = Modifier) {
    val extra = LocalExtra.current
    val color = when (plan.lowercase()) {
        "legendary" -> extra.rankLegendary
        "epic" -> extra.rankEpic
        else -> extra.rankFree
    }
    Surface(color = color.copy(alpha = 0.16f), shape = RoundedCornerShape(7.dp), modifier = modifier) {
        Text(
            stringResource(Plans.planLabelRes(plan)),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

@Composable
fun Username(
    profile: Profile?,
    fallback: String = "?",
    showItem: Boolean = true,
    fontSizeSp: Int = 14,
    modifier: Modifier = Modifier
) {
    val extra = LocalExtra.current
    val rank = profile?.displayRank ?: "free"
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            profile?.username ?: fallback,
            color = rankColor(rank, extra),
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (showItem && !profile?.equippedItemId.isNullOrBlank() && profile.equippedItemId != "none") {
            Spacer(Modifier.width(4.dp))
            EquippedItemIcon(itemId = profile.equippedItemId, size = 20.dp)
        }
    }
}

@Composable
fun EquippedItemIcon(itemId: String?, size: Dp = 22.dp) {
    val def = remember(itemId) { com.addonstation.app.core.ItemCatalog.byId(itemId) }
    val file = def?.fileName ?: return
    AsImage(
        model = com.addonstation.app.core.ItemCatalog.assetUri(file),
        contentDescription = def.nameEn,
        modifier = Modifier.size(size),
        contentScale = ContentScale.Fit
    )
}

// ---------------------------------------------------------------------------
// Content cards
// ---------------------------------------------------------------------------

@Composable
fun ContentCard(
    item: ContentItem,
    onClick: () -> Unit,
    onFavorite: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box {
            AsImage(
                model = Constants.resolveAsset(item.coverUrl)
                    ?: if (item.contentType == "world") Constants.WORLDS_PLACEHOLDER else Constants.ADDONS_PLACEHOLDER,
                contentDescription = item.title,
                modifier = Modifier.fillMaxWidth().height(150.dp)
            )
            PlanTag(item.plan, modifier = Modifier.align(Alignment.TopStart).padding(8.dp))
            if (item.isExclusive) {
                Surface(
                    color = LocalExtra.current.gold.copy(alpha = 0.9f),
                    shape = RoundedCornerShape(7.dp),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
                ) {
                    Text(stringResource(R.string.store_exclusive), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.Black, modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp))
                }
            }
            if (onFavorite != null) {
                Surface(
                    color = Color.Black.copy(alpha = 0.45f),
                    shape = CircleShape,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).clickable { onFavorite() }
                ) {
                    Text(if (isFavorite) "★" else "☆", color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(6.dp))
                }
            }
        }
        Column(Modifier.padding(10.dp)) {
            Text(item.title, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("⬇ ${Formats.count(item.downloadCount)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("👁 ${Formats.count(item.viewCount)}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if ((item.ratingAverage ?: 0.0) > 0) {
                    Text("★ ${String.format("%.1f", item.ratingAverage)}", fontSize = 11.sp, color = LocalExtra.current.gold)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// States
// ---------------------------------------------------------------------------

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Text("⚠️", fontSize = 34.sp)
        Spacer(Modifier.height(8.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Button(onClick = onRetry) { Text(stringResource(R.string.common_retry)) }
    }
}

@Composable
fun EmptyBox(text: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SectionHeader(kicker: String, title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(kicker, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LocalExtra.current.diamond, letterSpacing = 1.sp)
            Text(title, style = MaterialTheme.typography.headlineSmall)
        }
        action?.invoke()
    }
}

@Composable
fun StatBlock(number: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(number, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Text(label, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------------------------------------------------------------------------
// Chat message text with @mention chips + proper LTR/RTL isolation
// ---------------------------------------------------------------------------

@Composable
fun MentionText(
    content: String,
    onMention: (String) -> Unit,
    modifier: Modifier = Modifier,
    baseColor: Color = Color.Unspecified
) {
    val context = LocalContext.current
    val extra = LocalExtra.current
    val annotated: AnnotatedString = remember(content) {
        buildAnnotatedString {
            // Isolate direction per segment so Arabic/English mixes render correctly (the LTR fix)
            splitMentions(content).forEach { seg ->
                when (seg) {
                    is MsgSegment.Text -> append(seg.text)
                    is MsgSegment.Mention -> {
                        pushStringAnnotation("mention", seg.username)
                        withStyle(SpanStyle(color = extra.diamond, fontWeight = FontWeight.Bold)) {
                            append("\u2066@${seg.username}\u2069")
                        }
                        pop()
                    }
                }
            }
        }
    }
    ClickableText(
        text = annotated,
        style = MaterialTheme.typography.bodyMedium.copy(
            color = if (baseColor == Color.Unspecified) MaterialTheme.colorScheme.onSurface else baseColor,
            // dir="auto" equivalent: paragraph direction follows the first strong character
            textDirection = androidx.compose.ui.text.style.TextDirection.ContentOrLtr
        ),
        modifier = modifier,
        onClick = { offset ->
            annotated.getStringAnnotations("mention", offset, offset).firstOrNull()?.let { onMention(it.item) }
        }
    )
}

@Composable
fun FilterChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = selected == value,
                onClick = { onSelect(value) },
                label = { Text(label, fontSize = 12.sp) }
            )
        }
    }
}

@Composable
fun RatingStars(rating: Int, onRate: ((Int) -> Unit)?, modifier: Modifier = Modifier, starSize: Int = 26) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        (1..5).forEach { i ->
            Text(
                if (i <= rating) "★" else "☆",
                fontSize = starSize.sp,
                color = if (i <= rating) LocalExtra.current.gold else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = if (onRate != null) Modifier.clickable { onRate(i) } else Modifier
            )
        }
    }
}
