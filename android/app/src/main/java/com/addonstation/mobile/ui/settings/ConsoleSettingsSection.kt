package com.addonstation.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.AuthSettingsGroupCard
import com.addonstation.mobile.ui.components.AuthSettingsIconTile
import com.addonstation.mobile.ui.components.AuthSettingsRow
import com.addonstation.mobile.ui.components.AuthSettingsSectionLabel
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec

/** Devices block from the phone/PS4 design. Existing Settings and bottom-bar icons are untouched. */
@Composable
internal fun ConsoleSettingsSection(
    linkedCount: Int?,
    onOpenLink: () -> Unit,
    onOpenConsoles: () -> Unit,
) {
    AuthSettingsSectionLabel(stringResource(R.string.phone_settings_devices), AppColors.AuthGold)

    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(min = 82.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        AppColors.AuthBlue.copy(alpha = 0.24f),
                        AppColors.AuthPurple.copy(alpha = 0.13f),
                        AppColors.AuthSurface,
                    ),
                ),
            )
            .border(1.dp, AppColors.AuthBlue.copy(alpha = 0.48f), RoundedCornerShape(18.dp))
            .clickable(onClick = onOpenLink)
            .padding(horizontal = 13.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2E7BEF), Color(0xFF4C9AFF)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.SportsEsports, contentDescription = null, tint = Color.White, modifier = Modifier.size(29.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    stringResource(R.string.phone_console_link_title),
                    style = Spec.rowTitle.copy(fontSize = 15.sp),
                    color = AppColors.AuthText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
            }
            Text(
                stringResource(R.string.phone_console_settings_subtitle),
                style = Spec.rowSub.copy(fontSize = 12.sp),
                color = AppColors.AuthMuted,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text("›", style = Spec.rowValue.copy(fontSize = 24.sp), color = AppColors.AuthText)
    }

    AuthSettingsGroupCard(modifier = Modifier.padding(top = 10.dp)) {
        AuthSettingsRow(
            title = stringResource(R.string.phone_console_devices_title),
            subtitle = stringResource(R.string.phone_console_devices_subtitle),
            value = linkedCount?.toString() ?: "—",
            tile = { AuthSettingsIconTile(Icons.Outlined.Tv, AppColors.AuthBlueLight) },
            clickable = true,
            onClick = onOpenConsoles,
            chevron = true,
        )
    }
}
