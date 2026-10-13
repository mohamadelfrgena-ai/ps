package com.addonstation.mobile.ui.components

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.AcTransaction
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.NumberFont
import com.addonstation.mobile.ui.theme.PixelFont
import java.text.NumberFormat
import java.util.Locale

/** Coin asset with a soft gold radial glow and a matching 14dp shadow. */
@Composable
fun WalletCoinImage(size: Dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .scale(1.7f)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AppColors.AuthGoldFill.copy(alpha = 0.55f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Image(
            painter = painterResource(R.drawable.coin_ac),
            contentDescription = null,
            modifier = Modifier
                .size(size)
                .shadow(
                    elevation = 14.dp,
                    shape = CircleShape,
                    clip = false,
                    ambientColor = AppColors.AuthGoldFill.copy(alpha = 0.55f),
                    spotColor = AppColors.AuthGoldFill.copy(alpha = 0.55f),
                ),
        )
    }
}

/** Gold balance hero, daily reward and seven-step streak tracker. */
@Composable
fun WalletHeroCard(
    balance: Int,
    streak: Int,
    dailyClaimed: Boolean,
    claiming: Boolean,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(24.dp)
    val walletLabel = stringResource(R.string.profile_wallet_title) + " · " + stringResource(R.string.ac_balance)
    val balanceText = NumberFormat.getIntegerInstance(Locale.US).format(balance)
    val streakDay = if (streak <= 0) 1 else ((streak - 1) % 7) + 1
    val todayDay = if (dailyClaimed) streakDay else (streak % 7) + 1
    val completedThrough = if (dailyClaimed) streakDay else todayDay - 1

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.AuthWalletTop, AppColors.AuthWalletBottom)))
            .border(1.dp, AppColors.AuthGoldBorder, shape)
            .padding(20.dp),
    ) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset(x = 40.dp, y = (-40).dp)
                .size(160.dp)
                .background(
                    Brush.radialGradient(
                        colors = listOf(AppColors.AuthGoldFill.copy(alpha = 0.28f), Color.Transparent),
                    ),
                    CircleShape,
                ),
        )
        Column(Modifier.fillMaxWidth()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                WalletCoinImage(72.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        walletLabel,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold, lineHeight = 16.sp),
                        color = AppColors.AuthGoldSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            balanceText,
                            style = TextStyle(
                                fontFamily = NumberFont,
                                fontSize = 44.sp,
                                lineHeight = 49.sp,
                                fontWeight = FontWeight.ExtraBold,
                            ),
                            color = AppColors.AuthGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "AC",
                            style = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
                            color = AppColors.AuthGoldSoft,
                        )
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.AuthGoldLine))
            Spacer(Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.profile_daily_reward),
                    style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppColors.AuthText,
                    modifier = Modifier.weight(1f),
                )
                WalletStreakChip(streak = streak, height = 32.dp, iconSize = 16.dp)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (1..7).forEach { day ->
                    val complete = day <= completedThrough
                    val isToday = !dailyClaimed && day == todayDay
                    StreakDay(day = day, completed = complete, today = isToday)
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 6.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                Text(
                    stringResource(R.string.ac_kind_streak),
                    style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppColors.AuthGold,
                )
            }

            Spacer(Modifier.height(16.dp))
            val enabled = !dailyClaimed && !claiming
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (enabled) AppColors.AuthGoldFill else AppColors.AuthClaimDisabled)
                    .clickable(enabled = enabled, onClick = onClaim),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (dailyClaimed) stringResource(R.string.ac_claimed) else stringResource(R.string.ac_claim),
                    style = TextStyle(fontSize = 16.sp, lineHeight = 20.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (enabled) AppColors.AuthGoldDark else AppColors.AuthGoldSoft.copy(alpha = 0.60f),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun StreakDay(day: Int, completed: Boolean, today: Boolean) {
    val dayShape = CircleShape
    val background = when {
        completed -> AppColors.AuthGoldFill
        day == 7 -> AppColors.AuthPlanButton
        else -> AppColors.AuthStreakBg
    }
    val borderColor = when {
        completed -> Color.Transparent
        today -> AppColors.AuthGold
        day == 7 -> AppColors.AuthGoldBorder
        else -> AppColors.AuthStreakBorder
    }

    Box(
        Modifier
            .size(38.dp)
            .clip(dayShape)
            .background(if (today) Color.Transparent else background)
            .then(
                if (day == 7 && !completed && !today) {
                    Modifier.dashedCircleBorder(AppColors.AuthGoldBorder)
                } else {
                    Modifier.border(if (today) 2.dp else 1.dp, borderColor, dayShape)
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        when {
            completed -> Icon(
                Icons.Filled.Check,
                contentDescription = null,
                tint = AppColors.AuthGoldDark,
                modifier = Modifier.size(16.dp),
            )
            day == 7 && !today -> Icon(
                Icons.Filled.CardGiftcard,
                contentDescription = null,
                tint = AppColors.AuthGold,
                modifier = Modifier.size(16.dp),
            )
            else -> Text(
                day.toString(),
                style = TextStyle(fontFamily = NumberFont, fontSize = 13.sp, fontWeight = FontWeight.ExtraBold),
                color = if (today) AppColors.AuthGold else AppColors.AuthStreakMuted,
            )
        }
    }
}

private fun Modifier.dashedCircleBorder(color: Color): Modifier = drawBehind {
    drawCircle(
        color = color,
        style = androidx.compose.ui.graphics.drawscope.Stroke(
            width = 1.dp.toPx(),
            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
        ),
    )
}

/** Accent bar + pixel-font all-caps wallet section heading, with an optional trailing action. */
@Composable
fun WalletSectionLabel(
    text: String,
    accent: Color,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 16.dp, top = 24.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(width = 6.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(accent))
        Spacer(Modifier.width(8.dp))
        Text(
            text.uppercase(Locale.ROOT),
            style = TextStyle(fontFamily = PixelFont, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 1.sp),
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        if (trailing != null) {
            Spacer(Modifier.weight(1f))
            trailing()
        }
    }
}

/** Reusable 44dp colored Material icon tile. */
@Composable
fun WalletIconTile(
    icon: ImageVector,
    accent: Color,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
) {
    Box(
        modifier
            .size(size)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

/** Equal-width quick action tile. */
@Composable
fun WalletQuickAction(
    icon: ImageVector,
    label: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .heightIn(min = 96.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(7.dp, Alignment.CenterVertically),
    ) {
        WalletIconTile(icon = icon, accent = accent)
        Text(
            label,
            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = AppColors.AuthText,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Compact Profile wallet card; only the daily-reward button is separate from the wallet link. */
@Composable
fun WalletCompactCard(
    balance: Int,
    streak: Int,
    dailyClaimed: Boolean,
    claiming: Boolean,
    onOpenWallet: () -> Unit,
    onClaim: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(22.dp)
    val walletLabel = stringResource(R.string.profile_wallet_title) + " · " + stringResource(R.string.ac_balance)
    val animatedBalance = animateIntAsState(
        targetValue = balance,
        animationSpec = tween(durationMillis = 700),
        label = "profileWalletBalance",
    ).value
    val balanceText = NumberFormat.getIntegerInstance(Locale.US).format(animatedBalance)
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current

    Box(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(AppColors.AuthWalletTop, AppColors.AuthWalletBottom)))
            .border(1.dp, AppColors.AuthGoldBorder, shape)
            .clickable(onClick = onOpenWallet)
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                WalletCoinImage(56.dp)
                Column(Modifier.weight(1f)) {
                    Text(
                        walletLabel,
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                        color = AppColors.AuthGoldSoft,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            balanceText,
                            style = TextStyle(fontFamily = NumberFont, fontSize = 30.sp, lineHeight = 34.sp, fontWeight = FontWeight.ExtraBold),
                            color = AppColors.AuthGold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            "AC",
                            style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
                            color = AppColors.AuthGoldSoft,
                        )
                    }
                }
                Text(
                    if (layoutDirection == androidx.compose.ui.unit.LayoutDirection.Rtl) "‹" else "›",
                    style = TextStyle(fontSize = 26.sp, lineHeight = 28.sp, fontWeight = FontWeight.Bold),
                    color = AppColors.AuthGoldSoft,
                )
            }
            Spacer(Modifier.height(14.dp))
            val streakDay = if (streak <= 0) 1 else ((streak - 1) % 7) + 1
            val todayDay = if (dailyClaimed) streakDay else (streak % 7) + 1
            val completedThrough = if (dailyClaimed) streakDay else todayDay - 1
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.AuthGoldFill.copy(alpha = 0.16f))
                        .border(1.dp, AppColors.AuthGoldBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.CardGiftcard, contentDescription = null, tint = AppColors.AuthGold, modifier = Modifier.size(22.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        stringResource(R.string.profile_daily_reward),
                        style = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold),
                        color = AppColors.AuthText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.profile_day_streak, streak),
                        style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                        color = AppColors.AuthMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                val enabled = !dailyClaimed && !claiming
                if (dailyClaimed) {
                    Row(
                        Modifier.height(44.dp).padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = null, tint = AppColors.AuthGreen, modifier = Modifier.size(17.dp))
                        Text(
                            stringResource(R.string.profile_daily_claimed),
                            style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.ExtraBold),
                            color = AppColors.AuthGreen,
                            maxLines = 1,
                        )
                    }
                } else {
                    Box(
                        Modifier
                            .widthIn(min = 92.dp)
                            .height(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Brush.linearGradient(listOf(AppColors.AuthGoldFill, Color(0xFFE0A93A))))
                            .clickable(enabled = enabled, onClick = onClaim)
                            .padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            stringResource(R.string.ac_claim),
                            style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
                            color = AppColors.AuthGoldDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            StreakProgressSegments(
                completedThrough = completedThrough,
                todayDay = todayDay,
                showToday = !dailyClaimed,
            )
        }
    }
}

@Composable
private fun StreakProgressSegments(completedThrough: Int, todayDay: Int, showToday: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        (1..7).forEach { day ->
            val complete = day <= completedThrough
            val today = showToday && day == todayDay
            val shape = RoundedCornerShape(4.dp)
            val description = stringResource(R.string.profile_day_streak, day)
            Box(
                Modifier
                    .weight(1f)
                    .height(8.dp)
                    .then(
                        if (today) Modifier.shadow(
                            elevation = 4.dp,
                            shape = shape,
                            ambientColor = AppColors.AuthGold.copy(alpha = 0.35f),
                            spotColor = AppColors.AuthGold.copy(alpha = 0.35f),
                        ) else Modifier,
                    )
                    .clip(shape)
                    .background(
                        when {
                            complete -> AppColors.AuthGoldFill
                            day == 7 && !today -> Color.Transparent
                            else -> AppColors.AuthStreakBg
                        },
                    )
                    .then(
                        when {
                            today -> Modifier.border(1.dp, AppColors.AuthGold, shape)
                            day == 7 && !complete -> Modifier.border(1.5.dp, AppColors.AuthGoldBorder, shape)
                            else -> Modifier
                        },
                    )
                    .semantics { contentDescription = description },
            )
        }
    }
}

@Composable
private fun WalletStreakChip(
    streak: Int,
    height: Dp,
    iconSize: Dp,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.AuthPlanButton)
            .border(1.dp, AppColors.AuthGoldBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = AppColors.AuthGold, modifier = Modifier.size(iconSize))
        Text(
            pluralStringResource(R.plurals.ac_streak_days, streak, streak),
            style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold),
            color = AppColors.AuthGold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Current operation icon/accent pair per wallet transaction kind. */
fun walletTransactionIcon(kind: String, amount: Int): Pair<ImageVector, Color> = when (kind) {
    "daily" -> Icons.Outlined.CalendarToday to AppColors.AuthGold
    "task" -> Icons.Outlined.Check to AppColors.AuthAccentBlue
    "unlock" -> Icons.Outlined.Lock to AppColors.AuthRed
    "streak", "code" -> Icons.Outlined.CardGiftcard to if (kind == "code") AppColors.AuthPurple else AppColors.AuthGold
    "event", "welcome" -> Icons.Outlined.Star to AppColors.AuthPurple
    "admin_reward", "admin_adjustment" -> Icons.Outlined.Shield to AppColors.AuthGold
    else -> Icons.Outlined.History to if (amount < 0) AppColors.AuthRed else AppColors.AuthAccentBlue
}

/** A single recent/full-history row with signed amount, balance and localized date. */
@Composable
fun WalletTransactionRow(
    transaction: AcTransaction,
    title: String,
    balanceSummary: String,
    amountText: String,
    modifier: Modifier = Modifier,
) {
    val (icon, accent) = walletTransactionIcon(transaction.kind, transaction.amount)
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WalletIconTile(icon = icon, accent = accent)
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
                color = AppColors.AuthText,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                balanceSummary,
                style = TextStyle(fontSize = 12.sp, lineHeight = 15.sp),
                color = AppColors.AuthMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            amountText,
            style = TextStyle(fontFamily = NumberFont, fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold),
            color = when {
                transaction.amount > 0 -> AppColors.AuthGreenText
                transaction.amount < 0 -> AppColors.AuthRedText
                else -> AppColors.AuthMuted
            },
            maxLines = 1,
        )
    }
}

/** Plain dark loading blocks matching the wallet surface. */
@Composable
fun WalletLoadingSkeleton() {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Box(Modifier.fillMaxWidth().height(276.dp).clip(RoundedCornerShape(24.dp)).background(AppColors.AuthSurface))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            repeat(3) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(98.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.AuthSurface),
                )
            }
        }
        Box(Modifier.fillMaxWidth().height(154.dp).clip(RoundedCornerShape(18.dp)).background(AppColors.AuthSurface))
        Box(Modifier.fillMaxWidth().height(124.dp).clip(RoundedCornerShape(18.dp)).background(AppColors.AuthSurface))
    }
}
