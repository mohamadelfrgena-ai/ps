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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.ui.nav.MainTab
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText

/**
 * الشريط السفلي (5 عناصر) مع زر «المنزل» المرتفع في المنتصف:
 * 58×58dp، نصف قطر 19، تدرّج أخضر، إطار أخضر فاتح، توهّج، مرتفع 18dp فوق الشريط.
 */
@Composable
fun BottomNavBar(
    current: MainTab,
    onSelect: (MainTab) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(62.dp)
                .shadow(14.dp, RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp))
                .background(AppColors.Bg.copy(alpha = 0.96f))
                .border(
                    1.dp,
                    AppColors.Blue.copy(alpha = 0.16f),
                    RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
                ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(62.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            MainTab.entries.forEach { tab ->
                NavItem(
                    tab = tab,
                    selected = current == tab,
                    onClick = { onSelect(tab) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    tab: MainTab,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.92f else 1f, label = "navScale")

    Column(
        modifier = modifier
            .scale(scale)
            .clickable(interaction, null) { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (tab.isCenter) {
            Box(
                modifier = Modifier
                    .offset(y = (-18).dp)
                    .size(58.dp)
                    .shadow(18.dp, RoundedCornerShape(19.dp))
                    .clip(RoundedCornerShape(19.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(AppColors.GreenLight, AppColors.Green2, AppColors.GreenDark),
                        )
                    )
                    .border(1.5.dp, AppColors.GreenLight, RoundedCornerShape(19.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tab.icon,
                    contentDescription = stringResource(tab.labelRes),
                    tint = AppColors.OnGreen,
                    modifier = Modifier.size(26.dp),
                )
            }
            Text(
                text = stringResource(tab.labelRes),
                style = AppText.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default),
                color = AppColors.GreenLight,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.offset(y = (-12).dp),
            )
        } else {
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .height(3.dp)
                    .size(width = 26.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(if (selected) AppColors.Green else androidx.compose.ui.graphics.Color.Transparent),
            )
            Spacer(Modifier.height(4.dp))
            Icon(
                imageVector = tab.icon,
                contentDescription = stringResource(tab.labelRes),
                tint = if (selected) AppColors.Green else AppColors.Dim.copy(alpha = 0.75f),
                modifier = Modifier.size(22.dp),
            )
            Spacer(Modifier.height(3.dp))
            Text(
                text = stringResource(tab.labelRes),
                style = AppText.small.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Default),
                color = if (selected) AppColors.Green else AppColors.Dim,
                maxLines = 1,
            )
        }
    }
}
