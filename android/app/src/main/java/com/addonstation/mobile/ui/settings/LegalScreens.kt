package com.addonstation.mobile.ui.settings

import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Email
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.BuildConfig
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.PixelFont

@Composable
fun LegalDocumentScreen(
    title: String,
    document: String,
    onBack: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val paragraphs = remember(document) { document.trim().split(Regex("\\n\\s*\\n")).filter(String::isNotBlank) }
    val lastUpdated = paragraphs.lastOrNull().orEmpty()
    val body = paragraphs.dropLast(1)

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        LegalTopBar(title = title, palette = palette, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 30.dp),
        ) {
            Row(
                Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(palette.surfaceHigh)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = palette.blueLink, modifier = Modifier.size(14.dp))
                Text(lastUpdated, style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = palette.chipText, maxLines = 1)
            }
            Spacer(Modifier.height(12.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp)),
            ) {
                body.forEachIndexed { index, paragraph ->
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier.size(26.dp).clip(CircleShape).background(palette.blue.copy(alpha = 0.16f)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text((index + 1).toString(), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = palette.blueLink)
                        }
                        Text(
                            paragraph,
                            style = TextStyle(fontSize = 14.sp, lineHeight = 23.sp),
                            color = palette.chipText,
                        )
                    }
                    if (index < body.lastIndex) {
                        Box(Modifier.fillMaxWidth().padding(start = 52.dp).height(1.dp).background(palette.border))
                    }
                }
            }
        }
    }
}

@Composable
fun AboutAppScreen(
    onBack: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenTerms: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val container = rememberContainer()
    val profile by container.state.profile.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var noMailApp by remember { mutableStateOf(false) }
    val subject = stringResource(R.string.set_support_mail_contact)
    val body = stringResource(
        R.string.set_support_mail_body,
        BuildConfig.VERSION_NAME,
        (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim(),
        "Android " + android.os.Build.VERSION.RELEASE,
        appLocaleCode(),
        profile?.username ?: profile?.displayName ?: "—",
    )

    Column(Modifier.fillMaxSize().background(palette.bg)) {
        LegalTopBar(title = stringResource(R.string.set_about), palette = palette, onBack = onBack)
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(top = 30.dp, bottom = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = stringResource(R.string.app_name),
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp).shadow(18.dp, RoundedCornerShape(24.dp), ambientColor = palette.goldFill.copy(alpha = 0.35f), spotColor = palette.goldFill.copy(alpha = 0.35f))
                    .clip(RoundedCornerShape(24.dp)).border(3.dp, palette.goldFill, RoundedCornerShape(24.dp)),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                stringResource(R.string.brand_wordmark),
                style = TextStyle(fontFamily = PixelFont, fontSize = 20.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp),
                color = palette.text,
                maxLines = 1,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.brand_platform),
                style = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
                color = palette.textMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
            Box(Modifier.height(32.dp).clip(RoundedCornerShape(16.dp)).background(palette.surfaceHigh).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.brand_tagline), style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold), color = palette.chipText, maxLines = 1)
            }

            Spacer(Modifier.height(24.dp))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(16.dp)),
            ) {
                AboutInfoRow(
                    icon = { Icon(Icons.Outlined.Info, contentDescription = null, tint = palette.blueLink, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.set_version),
                    value = BuildConfig.VERSION_NAME,
                    palette = palette,
                )
                AboutDivider(palette)
                AboutInfoRow(
                    icon = { Icon(Icons.Outlined.Lock, contentDescription = null, tint = palette.blueLink, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.set_privacy),
                    palette = palette,
                    onClick = onOpenPrivacy,
                )
                AboutDivider(palette)
                AboutInfoRow(
                    icon = { Icon(Icons.Outlined.Description, contentDescription = null, tint = palette.blueLink, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.set_terms),
                    palette = palette,
                    onClick = onOpenTerms,
                )
                AboutDivider(palette)
                AboutInfoRow(
                    icon = { Icon(Icons.Outlined.Email, contentDescription = null, tint = palette.blueLink, modifier = Modifier.size(20.dp)) },
                    label = stringResource(R.string.set_contact),
                    palette = palette,
                    onClick = { noMailApp = !openSupportEmail(context, subject, body) },
                )
            }
            Spacer(Modifier.height(26.dp))
            Text(stringResource(R.string.brand_rights), style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp), color = palette.textMuted, textAlign = TextAlign.Center)
        }
    }

    if (noMailApp) {
        AlertDialog(
            onDismissRequest = { noMailApp = false },
            containerColor = palette.surface,
            title = { Text(stringResource(R.string.set_contact), color = palette.text) },
            text = { Text(stringResource(R.string.set_support_no_mail_app), color = palette.textMuted) },
            confirmButton = {
                TextButton(onClick = { noMailApp = false }) {
                    Text(stringResource(R.string.common_ok), color = palette.blueLink)
                }
            },
        )
    }
}

@Composable
private fun LegalTopBar(title: String, palette: HomePalette, onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(palette.barSurface).height(56.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(palette.surfaceHigh).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = palette.text, modifier = Modifier.size(20.dp))
        }
        Text(
            title,
            style = TextStyle(fontFamily = PixelFont, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.border))
}

@Composable
private fun AboutInfoRow(
    icon: @Composable () -> Unit,
    label: String,
    palette: HomePalette,
    value: String? = null,
    onClick: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(palette.surfaceHigh), contentAlignment = Alignment.Center) { icon() }
        Text(label, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = palette.text, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        value?.let { Text(it, style = TextStyle(fontSize = 12.sp), color = palette.textMuted, maxLines = 1) }
        if (onClick != null) Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun AboutDivider(palette: HomePalette) {
    Box(Modifier.fillMaxWidth().padding(start = 58.dp).height(1.dp).background(palette.border))
}
