package com.addonstation.mobile.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.AssetThumb
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText

/**
 * عناصر شاشتي الملف الشخصي (ملفي · ملف شخص آخر) — التصميم الجديد.
 *
 * قاعدة ثابتة في هذا الملف: **لا لون ثابت واحد** (ولا hex). كل لون إمّا رمز من
 * لوحة التطبيق `AppColors`، أو مشتقٌّ منه بالشفافية (`copy(alpha = …)`) أو
 * بتدرّج بين رمزين (`Brush`). كذلك الخط: `AppText` (خط البكسل للتسميات
 * والأرقام، وخط النظام للنصوص) — فتبقى الشاشتان على تناسق التطبيق في الوضعين
 * الداكن والفاتح، وبالعربية (RTL) كما بالإنجليزية.
 */

/** بانر الملف الشخصي: تدرّج أخضر من ألوان التطبيق، يُبنى فوقه المحتوى. */
@Composable
fun ProfileBanner(
    modifier: Modifier = Modifier,
    height: Dp = 132.dp,
    radius: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(radius))
            .background(Brush.verticalGradient(listOf(AppColors.Green2, AppColors.GreenDark))),
        content = content,
    )
}

/**
 * صورة الملف الشخصي: حلقة متدرّجة (أخضر ← أزرق ← أخضر) من ألوان التطبيق،
 * وشارة المستوى أسفلها، والصورة نفسها تُمرَّر كـ lambda فيملؤها النادِي بما يشاء.
 */
@Composable
fun ProfileAvatar(
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = 92.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier, contentAlignment = Alignment.BottomCenter) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(CircleShape)
                .border(
                    width = 3.dp,
                    brush = Brush.sweepGradient(listOf(AppColors.Green, AppColors.Blue, AppColors.Green)),
                    shape = CircleShape,
                )
                .padding(4.dp)
                .clip(CircleShape)
                .background(AppColors.Elevated2),
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
        Box(
            modifier = Modifier
                .offset(y = 10.dp)
                .clip(RoundedCornerShape(50))
                .background(AppColors.Green)
                .padding(horizontal = 10.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.profile_lv_badge, level),
                style = AppText.tiny,
                color = AppColors.OnGreen,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

/** بطاقة إحصائية: الرقم بلون التطبيق الأخضر والتسمية بلون صامت. */
@Composable
fun ProfileStatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .padding(vertical = 14.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            style = AppText.statNumber,
            color = AppColors.Green,
            maxLines = 1,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            style = AppText.tiny,
            color = AppColors.Muted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * شريط الخبرة: تقدّم بلون التطبيق الأخضر على مسار من طبقته الأغمق — بلا أي
 * عنصر من مكتبة خارجية، فيعمل على كل إصدارات Material3.
 */
@Composable
fun ProfileXpBar(
    progress: Float,
    label: String,
    modifier: Modifier = Modifier,
    height: Dp = 10.dp,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clip(CircleShape)
                .background(AppColors.Elevated2),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .height(height)
                    .clip(CircleShape)
                    .background(Brush.horizontalGradient(listOf(AppColors.Green, AppColors.Green2))),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(text = label, style = AppText.tiny, color = AppColors.Muted)
    }
}

/**
 * بطاقة الخطّة: إطار ذهبي مشتقّ من `Gold` (لا لون جديد)، أيقونة التاج،
 * اسم الخطّة، وزرّ «إدارة» يفتح شاشة الاشتراكات.
 */
@Composable
fun ProfilePlanCard(
    planLabel: String,
    onManage: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.GoldBadgeBg)
            .border(1.dp, AppColors.Gold.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .clickable(onClick = onManage)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.Gold),
            contentAlignment = Alignment.Center,
        ) {
            Text("👑", fontSize = 20.sp, color = AppColors.GoldText)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                text = planLabel,
                style = AppText.sectionTitle,
                color = AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            subtitle?.let {
                Text(
                    text = it,
                    style = AppText.tiny,
                    color = AppColors.Gold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = stringResource(R.string.profile_manage),
            style = AppText.tiny,
            color = AppColors.Gold,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .border(1.dp, AppColors.Gold, CircleShape)
                .padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}

/**
 * بلاطة حساب في شبكة ٢×٢: أيقونة في مربّع، عنوان، وصف، وملحق اختياري
 * (مفتاح تشغيل حالة الاتصال · صورة الأداة المجهّزة).
 */
@Composable
fun ProfileAccountTile(
    icon: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
    trailingImageUrl: String? = null,
) {
    Column(
        modifier = modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Elevated)
            .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.Elevated2),
                contentAlignment = Alignment.Center,
            ) {
                Text(icon, fontSize = 16.sp)
            }
            if (trailingImageUrl != null) {
                AssetThumb(url = trailingImageUrl, size = 26.dp, animate = true)
            } else {
                trailing?.invoke()
            }
        }
        Column {
            Text(
                text = title,
                style = AppText.bodySmall,
                color = AppColors.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = AppText.tiny,
                color = AppColors.Muted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
