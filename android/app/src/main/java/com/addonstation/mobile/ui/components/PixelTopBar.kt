package com.addonstation.mobile.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MonetizationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText

/**
 * الشريط العلوي (≈56dp + شريط الحالة): أيقونة التطبيق على اليسار، عنوان الصفحة في الوسط،
 * وشريحة محفظة AC على اليمين (تظهر «—» أثناء التحميل).
 */
@Composable
fun PixelTopBar(
    title: String,
    onLogoClick: (() -> Unit)? = null,
    walletBalance: Int? = null,
    onWalletClick: () -> Unit = {},
    icon: ImageVector = Icons.Filled.Star,
    showWallet: Boolean = true,
) {
    // الخلفية تُرسم على كامل الشريط **بما فيه منطقة شريط الحالة** (البطارية والواي فاي)،
    // والصف نفسه (56dp) يبدأ أسفل تلك المنطقة — فلا يعلو المحتوى على أيقونات النظام.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(AppColors.Bg.copy(alpha = 0.96f))
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .border(1.dp, AppColors.Blue.copy(alpha = 0.55f), RoundedCornerShape(11.dp))
                .clickable(enabled = onLogoClick != null) { onLogoClick?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.mipmap.ic_launcher),
                contentDescription = stringResourceAppName(),
                tint = androidx.compose.ui.graphics.Color.Unspecified,
                modifier = Modifier.size(24.dp),
            )
        }

        Row(
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(icon, contentDescription = null, tint = AppColors.Blue, modifier = Modifier.size(15.dp))
            Text(
                text = title,
                style = AppText.sectionTitle,
                color = AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        if (showWallet) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(AppColors.Elevated2)
                    .border(1.dp, AppColors.Border, CircleShape)
                    .clickable { onWalletClick() }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                CoinIcon(size = 15.dp)
                Text(
                    text = walletBalance?.toString() ?: "—",
                    style = AppText.small,
                    color = AppColors.Text,
                )
                Text("AC", style = AppText.tiny, color = AppColors.Muted)
            }
        }
    }
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(AppColors.Blue.copy(alpha = 0.14f))
    )
    }
}

@Composable
private fun stringResourceAppName(): String =
    androidx.compose.ui.res.stringResource(R.string.app_name)
