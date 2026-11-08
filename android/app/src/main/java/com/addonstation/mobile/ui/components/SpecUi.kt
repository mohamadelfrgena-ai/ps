package com.addonstation.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.ui.theme.Spec

/**
 * مكوّنات وثيقة التصميم الجديدة (AddonStation_handoff) — تُستعمل في الشاشات
 * الثلاث المعاد تصميمها فقط (الرئيسية · الغرف · الشات)، فلا تمسّ بقية الشاشات.
 *
 * كل الألوان والمقاسات من [Spec] (نفس قيم الوثيقة حرفياً).
 */

/** قرص/شريحة التصنيف (٢٫٦): 40 ارتفاعاً · نصف قطر 20 · مختارة = أزرق. */
@Composable
fun SpecChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (selected) Spec.blue else Spec.surface)
            .then(if (selected) Modifier else Modifier.border(1.dp, Spec.border, RoundedCornerShape(20.dp)))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = if (selected) Spec.chip.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold) else Spec.chip,
            color = if (selected) Color.White else Spec.chipLabel,
            maxLines = 1,
        )
    }
}

/** زر أساسي أزرق (٢٫٣) — نصف قطر 12. */
@Composable
fun SpecPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Spec.touch,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Spec.buttonRadius))
            .background(Spec.blue)
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Spec.button, color = Color.White, maxLines = 1)
    }
}

/** زر ثانوي (٢٫٣ / ٢٫٤): سطح مرتفع أو إطار بلون — نصّ 13–14 عريض. */
@Composable
fun SpecPillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = Spec.touch,
    outline: Color? = null,
    textColor: Color = Spec.text,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(Spec.buttonRadius))
            .background(if (outline != null) Color.Transparent else Spec.surfaceHigh)
            .then(
                if (outline != null) Modifier.border(1.dp, outline, RoundedCornerShape(Spec.buttonRadius))
                else Modifier,
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Spec.buttonSmall, color = textColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * رأس قسم (٢٫٧): العنوان بخط البكسل يساراً و«عرض الكل» يميناً (يرمش بالانعكاس في RTL).
 * ارتفاع سطر كافٍ لهدف لمس 44dp على «عرض الكل».
 */
@Composable
fun SpecSectionHeader(
    title: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = Spec.screenPad, end = Spec.screenPad, top = 22.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = Spec.sectionTitle, color = Spec.gold, maxLines = 1)
        Spacer(Modifier.weight(1f))
        if (actionText != null && onAction != null) {
            Box(
                modifier = Modifier
                    .height(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .clickable { onAction() }
                    .padding(horizontal = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(actionText, style = Spec.link, color = Spec.blueLink, maxLines = 1)
            }
        }
    }
}

/** شارة صغيرة (NEW · EPIC · LEGENDARY · FREE): نصف قطر 8 · حشو 2×8. */
@Composable
fun SpecBadge(
    text: String,
    background: Color,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(20.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Spec.badge, color = textColor, maxLines = 1)
    }
}

/** فجوة أفقية صغيرة تُستعمل بين الأزرار (تجنّب تكرار Spacer بعروض مختلفة). */
@Composable
fun SpecGap(width: Dp = 10.dp) {
    Spacer(Modifier.width(width))
}

/** ترتيب أزرار أفقي جاهز (اتّساق الحشو بين الشاشات). */
@Composable
fun SpecButtonRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** مربّع أيقونة دائري 44 بمقاس الوثيقة (يُستعمل في شريط الشات وقائمته). */
@Composable
fun SpecIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    background: Color = Spec.surface,
    tint: Color = Spec.text,
    size: Dp = Spec.touch,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(androidx.compose.foundation.shape.CircleShape)
            .background(background)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.material3.Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
    }
}
