package com.addonstation.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont

/** A filter chip model shared by the five redesigned phone lists. */
data class PhoneListChip(
    val key: String,
    val label: String,
    val count: Int? = null,
)

/** Back + title + count + optional square actions. No logo is shown here. */
@Composable
fun PhoneListHeader(
    title: String,
    count: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    countLabel: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val palette = LocalHomePalette.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.barSurface)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(palette.surface)
                .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                tint = palette.text,
                modifier = Modifier.size(21.dp),
            )
        }
        Text(
            title,
            style = TextStyle(
                fontFamily = if (rtl) TajawalFont else PixelFont,
                fontSize = 21.sp,
                fontWeight = FontWeight.Black,
            ),
            color = palette.text,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            countLabel ?: count.toString(),
            style = AppText.tiny.copy(fontWeight = FontWeight.ExtraBold),
            color = Color.White,
            modifier = Modifier
                .clip(CircleShape)
                .background(palette.blue)
                .padding(horizontal = 9.dp, vertical = 5.dp),
        )
        Spacer(Modifier.weight(1f))
        actions()
    }
}

/** Standard 44 dp square action used in a [PhoneListHeader]. */
@Composable
fun PhoneHeaderAction(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    tint: Color = LocalHomePalette.current.textMuted,
) {
    val palette = LocalHomePalette.current
    Box(
        Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/** Compact text action (for example “+ New”) used in list headers. */
@Composable
fun PhoneHeaderTextAction(text: String, onClick: () -> Unit) {
    val palette = LocalHomePalette.current
    Box(
        Modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(palette.blue)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = AppText.button, color = Color.White, maxLines = 1)
    }
}

/** Horizontally scrolling 38 dp filter chips with optional count bubbles. */
@Composable
fun PhoneChipRow(
    chips: List<PhoneListChip>,
    selectedKey: String,
    onSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHomePalette.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        chips.forEach { chip ->
            val selected = chip.key == selectedKey
            Row(
                modifier = Modifier
                    .height(38.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selected) palette.blue else palette.surface)
                    .border(
                        1.dp,
                        if (selected) palette.blue else palette.border,
                        RoundedCornerShape(14.dp),
                    )
                    .clickable { onSelected(chip.key) }
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    chip.label,
                    style = AppText.tiny.copy(fontWeight = FontWeight.ExtraBold),
                    color = if (selected) Color.White else palette.chipText,
                    maxLines = 1,
                )
                chip.count?.let { count ->
                    Box(
                        Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(
                                if (selected) Color.White.copy(alpha = 0.2f)
                                else palette.surfaceHigh,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            count.toString(),
                            style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.Black),
                            color = if (selected) Color.White else palette.textMuted,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PhoneGroupLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
        color = LocalHomePalette.current.goldText,
        modifier = modifier.padding(top = 10.dp, bottom = 5.dp),
    )
}

enum class PhoneStatusTone { Green, Blue, Orange, Red }

@Composable
fun PhoneStatusChip(
    text: String,
    tone: PhoneStatusTone,
    modifier: Modifier = Modifier,
) {
    val color = when (tone) {
        PhoneStatusTone.Green -> AppColors.Green
        PhoneStatusTone.Blue -> AppColors.Blue
        PhoneStatusTone.Orange -> AppColors.Gold
        PhoneStatusTone.Red -> AppColors.Red
    }
    Text(
        text,
        style = TextStyle(fontSize = 10.sp, fontWeight = FontWeight.ExtraBold),
        color = color,
        maxLines = 1,
        modifier = modifier
            .height(22.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

/** Shared illustrated empty state used by all five lists. */
@Composable
fun PhoneEmptyState(
    icon: ImageVector,
    color: Color,
    title: String,
    body: String,
    actionText: String,
    onAction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalHomePalette.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(176.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(162.dp)
                    .drawBehind {
                        drawCircle(
                            color = color.copy(alpha = 0.42f),
                            style = Stroke(
                                width = 2.dp.toPx(),
                                pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(8.dp.toPx(), 8.dp.toPx()),
                                ),
                            ),
                        )
                    },
            )
            Box(Modifier.size(142.dp).clip(CircleShape).background(color.copy(alpha = 0.08f)))
            Box(
                Modifier
                    .size(104.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(color.copy(alpha = 0.18f))
                    .border(1.dp, color.copy(alpha = 0.42f), RoundedCornerShape(28.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(52.dp))
            }
            val squares = listOf(
                Alignment.TopStart to 22.dp,
                Alignment.TopEnd to 12.dp,
                Alignment.BottomStart to 8.dp,
                Alignment.BottomEnd to 20.dp,
            )
            squares.forEachIndexed { index, (alignment, inset) ->
                Box(
                    Modifier
                        .align(alignment)
                        .padding(inset)
                        .size(if (index % 2 == 0) 7.dp else 5.dp)
                        .background(color.copy(alpha = 0.75f)),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(
            title,
            style = TextStyle(
                fontFamily = if (LocalLayoutDirection.current == LayoutDirection.Rtl) TajawalFont else PixelFont,
                fontSize = 22.sp,
                fontWeight = FontWeight.Black,
            ),
            color = palette.text,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            body,
            style = AppText.bodySmall,
            color = palette.textMuted,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 290.dp),
        )
        Spacer(Modifier.height(20.dp))
        Box(
            modifier = Modifier
                .widthIn(max = 290.dp)
                .fillMaxWidth()
                .height(52.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(color)
                .clickable(onClick = onAction),
            contentAlignment = Alignment.Center,
        ) {
            Text(actionText, style = AppText.button, color = Color.White, textAlign = TextAlign.Center)
        }
    }
}

/** Standard 46 dp tinted icon tile used by notification/content/order rows. */
@Composable
fun PhoneIconTile(
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(46.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(color.copy(alpha = 0.18f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(23.dp))
    }
}
