package com.addonstation.mobile.ui.community

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupOff
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CollectionInfo
import androidx.compose.ui.semantics.CollectionItemInfo
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.collectionInfo
import androidx.compose.ui.semantics.collectionItemInfo
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.CancellationException
import java.util.Locale

private val RulesBlue = Color(0xFF4C9AFF)
private val RulesRed = Color(0xFFFF6B78)
private val RulesOrange = Color(0xFFF0A04B)
private val RulesPurple = Color(0xFF9B6BFF)
private val RulesButtonBlue = Color(0xFF2E7BEF)
private val RulesButtonBlueLight = Color(0xFF4C9AFF)
private val RulesWarningDarkStart = Color(0xFF3A1D0B)
private val RulesWarningDarkEnd = Color(0xFF1F0F08)
private val RulesWarningDarkBorder = Color(0xFF8A4A22)
private val RulesWarningDarkText = Color(0xFFFFD9B3)
private val RulesWarningLight = Color(0xFFFFF1E0)
private val RulesWarningLightBorder = Color(0xFFF0B97A)
private val RulesWarningLightText = Color(0xFF7A3E00)
private val RulesWarningLightIcon = Color(0xFFC26A00)

private data class CommunityRuleDesign(val icon: ImageVector, val accent: Color)

private val CommunityRuleDesigns = listOf(
    CommunityRuleDesign(Icons.Filled.Favorite, RulesBlue),
    CommunityRuleDesign(Icons.Filled.Block, RulesRed),
    CommunityRuleDesign(Icons.Filled.GroupOff, RulesRed),
    CommunityRuleDesign(Icons.Filled.LinkOff, RulesOrange),
    CommunityRuleDesign(Icons.Filled.Lock, RulesOrange),
    CommunityRuleDesign(Icons.Filled.VisibilityOff, RulesRed),
    CommunityRuleDesign(Icons.Filled.AccountBalance, RulesPurple),
)

private val LeadingRuleIcons = listOf("🏛️", "⚠️", "🤝", "🚫", "📵", "🔗", "🔒", "🛑", "🏛", "⚠")

private fun String.withoutLeadingRuleIcon(): String {
    val clean = trimStart()
    val icon = LeadingRuleIcons.firstOrNull(clean::startsWith) ?: return trim()
    return clean.removePrefix(icon).trimStart()
}

/** Redesigned rules gate; acceptance and navigation remain owned by CommunityGate. */
@Composable
internal fun CommunityRulesScreen(
    onAccepted: () -> Unit,
    onDecline: () -> Unit,
) {
    val container = rememberContainer()
    val palette = LocalHomePalette.current
    val locale = appLocaleCode()
    val isArabic = locale.equals("ar", ignoreCase = true)
    val isEnglish = locale.equals("en", ignoreCase = true)
    val bodyFont = if (isArabic) TajawalFont else NunitoFont
    val acceptFocusRequester = remember { FocusRequester() }

    var serverRules by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) {
        try {
            serverRules = container.chat.rules(container.prefs.readLocale())
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            serverRules = emptyList()
        }
    }
    LaunchedEffect(Unit) {
        acceptFocusRequester.requestFocus()
    }

    val fallbackRules = listOf(
        stringResource(R.string.chat_rule_1_plain),
        stringResource(R.string.chat_rule_2_plain),
        stringResource(R.string.chat_rule_3_plain),
        stringResource(R.string.chat_rule_4_plain),
        stringResource(R.string.chat_rule_5_plain),
        stringResource(R.string.chat_rule_6_plain),
        stringResource(R.string.chat_rule_7_plain),
        stringResource(R.string.chat_rule_8_plain),
    )
    val visibleRules = List(7) { index ->
        serverRules.getOrNull(index)
            ?.withoutLeadingRuleIcon()
            ?.takeIf { it.isNotBlank() }
            ?: fallbackRules[index]
    }
    val warningText = serverRules.getOrNull(7)
        ?.withoutLeadingRuleIcon()
        ?.takeIf { it.isNotBlank() }
        ?: fallbackRules[7]
    val title = stringResource(R.string.chat_rules_title_plain)

    BackHandler(onBack = onDecline)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(palette.bg),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CommunityRulesHeader(
            title = title,
            isArabic = isArabic,
            isEnglish = isEnglish,
            bodyFont = bodyFont,
            palette = palette,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, top = 16.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(16.dp))
            CommunityRulesCard(
                rules = visibleRules,
                listDescription = stringResource(R.string.chat_rules_list_description),
                bodyFont = bodyFont,
                palette = palette,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            CommunityRulesWarning(
                text = warningText,
                bodyFont = bodyFont,
                palette = palette,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            // The list and its warning scroll as one region; actions remain pinned.
            Spacer(Modifier.height(12.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            PrimaryRulesButton(
                text = stringResource(R.string.chat_accept_enter),
                bodyFont = bodyFont,
                focusRequester = acceptFocusRequester,
                onClick = onAccepted,
            )
            Spacer(Modifier.height(10.dp))
            SecondaryRulesButton(
                text = stringResource(R.string.chat_decline_home),
                bodyFont = bodyFont,
                palette = palette,
                onClick = onDecline,
            )
        }
    }
}

@Composable
private fun CommunityRulesHeader(
    title: String,
    isArabic: Boolean,
    isEnglish: Boolean,
    bodyFont: FontFamily,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    val titleFont = when {
        isArabic -> TajawalFont
        isEnglish -> PixelFont
        else -> bodyFont
    }
    val titleSize = when {
        isArabic -> 28.sp
        isEnglish -> 21.sp
        else -> 22.sp
    }
    Column(
        modifier = modifier.semantics { heading() },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .shadow(
                    elevation = 22.dp,
                    shape = CircleShape,
                    ambientColor = Color(0x662E7BEF),
                    spotColor = Color(0x662E7BEF),
                )
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(RulesButtonBlue, RulesButtonBlueLight))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.VerifiedUser,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(28.dp),
            )
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (isEnglish) title.uppercase(Locale.ROOT) else title,
            modifier = Modifier.fillMaxWidth(),
            style = TextStyle(
                fontFamily = titleFont,
                fontSize = titleSize,
                fontWeight = if (isArabic) FontWeight.ExtraBold else FontWeight.Bold,
                letterSpacing = if (isEnglish) 2.sp else 0.sp,
            ),
            color = palette.text,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun CommunityRulesCard(
    rules: List<String>,
    listDescription: String,
    bodyFont: FontFamily,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(
                elevation = if (palette.isDark) 0.dp else 3.dp,
                shape = shape,
                spotColor = Color(0x140E1526),
            )
            .clip(shape)
            .background(palette.surface)
            .border(1.dp, palette.border, shape)
            .semantics {
                contentDescription = listDescription
                collectionInfo = CollectionInfo(rowCount = rules.size, columnCount = 1)
            },
    ) {
        rules.forEachIndexed { index, text ->
            val design = CommunityRuleDesigns[index]
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp)
                    .semantics(mergeDescendants = true) {
                        collectionItemInfo = CollectionItemInfo(
                            rowIndex = index,
                            rowSpan = 1,
                            columnIndex = 0,
                            columnSpan = 1,
                        )
                    }
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RuleIconTile(design = design)
                Text(
                    text = text,
                    modifier = Modifier.weight(1f),
                    style = TextStyle(
                        fontFamily = bodyFont,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 21.sp,
                    ),
                    color = palette.text,
                    textAlign = TextAlign.Start,
                )
            }
            if (index != rules.lastIndex) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 68.dp, end = 14.dp)
                        .height(1.dp)
                        .background(palette.border),
                )
            }
        }
    }
}

@Composable
private fun RuleIconTile(design: CommunityRuleDesign) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(shape)
            .background(design.accent.copy(alpha = 0.15f))
            .border(1.dp, design.accent.copy(alpha = 0.33f), shape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = design.icon,
            contentDescription = null,
            tint = design.accent,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun CommunityRulesWarning(
    text: String,
    bodyFont: FontFamily,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    val background = if (palette.isDark) {
        Brush.linearGradient(listOf(RulesWarningDarkStart, RulesWarningDarkEnd))
    } else {
        Brush.linearGradient(listOf(RulesWarningLight, RulesWarningLight))
    }
    val border = if (palette.isDark) RulesWarningDarkBorder else RulesWarningLightBorder
    val textColor = if (palette.isDark) RulesWarningDarkText else RulesWarningLightText
    val iconColor = if (palette.isDark) RulesOrange else RulesWarningLightIcon

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = Icons.Filled.Warning,
            contentDescription = null,
            tint = iconColor,
            modifier = Modifier.size(26.dp),
        )
        Text(
            text = text,
            modifier = Modifier.weight(1f),
            style = TextStyle(
                fontFamily = bodyFont,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 19.6.sp,
            ),
            color = textColor,
        )
    }
}

@Composable
private fun PrimaryRulesButton(
    text: String,
    bodyFont: FontFamily,
    focusRequester: FocusRequester,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .shadow(
                elevation = 22.dp,
                shape = shape,
                ambientColor = Color(0x732E7BEF),
                spotColor = Color(0x732E7BEF),
            )
            .clip(shape)
            .background(Brush.linearGradient(listOf(RulesButtonBlue, RulesButtonBlueLight)))
            .focusRequester(focusRequester)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(22.dp),
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = text,
            style = TextStyle(fontFamily = bodyFont, fontSize = 17.sp, fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SecondaryRulesButton(
    text: String,
    bodyFont: FontFamily,
    palette: HomePalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(shape)
            .border(1.dp, palette.border, shape)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(fontFamily = bodyFont, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = palette.textMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
