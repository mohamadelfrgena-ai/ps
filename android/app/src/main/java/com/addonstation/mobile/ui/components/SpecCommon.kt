package com.addonstation.mobile.ui.components

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.ui.theme.TajawalFont

/**
 * مكوّنات مشتركة جديدة للوحات التصميم الخمس (Splash · Chat · Store · Profile · Wallet · Admin · Creators).
 *  • [SpecSegmentedControl]: حاوي مقسوم بزرّين (مثل تبويبات المتجر/الـ stickers/emoji في الشات)
 *    بأسلوب الوثيقة (سطح + حد 1dp + نصف قطر 14، الجزء المحدّد = تعبئة زرقاء + نص أبيض).
 *  • [SpecStoreHeader]: ترويسة شاشة المتجر بأيقونة + كلمة + شريحة AC.
 *  • [SpecErrorState]: حالة خطأ كاملة المواصفة (دائرة 88 + أيقونة + عنوان + زر Retry).
 *  • [SpecEmptyStateIcon]: حالة فارغة بدائرة + أيقونة (مثل «لا نتائج»).
 *  • [SpecPillChip]: شريحة (badge) في رأس بطاقة المعاينة.
 *  • [SpecOutlinedButton]: زر outlined عام (مثل «Manage / Upgrade»).
 */

@Composable
fun SpecSegmentedControl(
    options: List<Pair<String, String>>, // (key, label)
    selectedKey: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    Row(
        modifier = modifier
            .height(height + 8.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Spec.surface)
            .border(1.dp, Spec.border, RoundedCornerShape(14.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        options.forEach { (key, label) ->
            val selected = key == selectedKey
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (selected) Spec.blue else Color.Transparent)
                    .clickable { onSelect(key) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = TextStyle13Bold(),
                    color = if (selected) Color.White else Spec.textMuted,
                    maxLines = 1,
                )
            }
        }
    }
}

private fun TextStyle13Bold() = androidx.compose.ui.text.TextStyle(
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
)

/**
 * نسخة مبسّطة من [SpecSegmentedControl] تأخذ قائمة عناوين نصّية وفهرس
 * الاختيار، وتختار بتمرير الفهرس. مفيدة للـ tabs في صفحات الـ profile
 * (About · My content · Favorites · My plan) حيث لا حاجة لمفتاح منفصل.
 */
@Composable
fun SpecSegmentedControl(
    labels: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 40.dp,
) {
    SpecSegmentedControl(
        options = labels.mapIndexed { i, l -> i.toString() to l },
        selectedKey = selectedIndex.toString(),
        onSelect = { onSelect(it.toIntOrNull() ?: 0) },
        modifier = modifier,
        height = height,
    )
}

/**
 * ترويسة المتجر وفق SPEC_CHAT_PANELS_STORE §3:
 *  • أيقونة التطبيق 40dp + إطار ذهبي 2dp
 *  • كلمة «STORE» (pixel font 18sp / العربية Tajawal ExtraBold بلا تباعد)
 *  • شريحة AC pill بارتفاع 36 ونصف قطر 18: شعار العملة 24 + الرصيد (— عند عدم التحميل)
 *  ضغطها ⇒ wallet.
 */
@Composable
fun SpecStoreHeader(
    balance: Int?,
    onWalletClick: () -> Unit,
    modifier: Modifier = Modifier,
    isArabic: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.app_icon),
            contentDescription = null,
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(12.dp))
                .border(2.dp, Spec.gold, RoundedCornerShape(12.dp)),
        )
        Text(
            text = stringResource(R.string.nav_store).uppercase(),
            style = if (isArabic) {
                androidx.compose.ui.text.TextStyle(
                    fontFamily = TajawalFont,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 18.sp,
                )
            } else {
                androidx.compose.ui.text.TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
            },
            color = Spec.text,
            modifier = Modifier.weight(1f),
        )
        AcBalancePill(balance = balance, onClick = onWalletClick)
    }
}

@Composable
fun AcBalancePill(
    balance: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Spec.surfaceHigh)
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.coin_ac),
            contentDescription = null,
            modifier = Modifier.size(24.dp),
        )
        Text(
            text = balance?.toString() ?: "—",
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Spec.gold,
            maxLines = 1,
        )
    }
}

/**
 * حالة خطأ كاملة: دائرة 88dp + أيقونة + عنوان + زر Retry (44dp primary gradient).
 * يمكن تمرير نص مخصّص أو استخدام [msg_network_error] الافتراضي.
 */
@Composable
fun SpecErrorState(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Spec.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.WifiOff,
                contentDescription = null,
                tint = Spec.textMuted,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            title ?: stringResource(R.string.msg_network_error),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Spec.text,
            textAlign = TextAlign.Center,
        )
        SpecRetryButton(onClick = onRetry)
    }
}

@Composable
fun SpecRetryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    text: String? = null,
) {
    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.horizontalGradient(listOf(Spec.blue, Spec.accentBlue)),
            )
            .clickable { onClick() }
            .padding(horizontal = 28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text ?: stringResource(R.string.common_retry),
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
        )
    }
}

/** حالة فارغة بدائرة + أيقونة. */
@Composable
fun SpecEmptyStateIcon(
    title: String,
    hint: String? = null,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.SearchOff,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(CircleShape)
                .background(Spec.surfaceHigh),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Spec.textMuted,
                modifier = Modifier.size(40.dp),
            )
        }
        Text(
            title,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Spec.text,
            textAlign = TextAlign.Center,
        )
        if (hint != null) {
            Text(
                hint,
                style = AppText.bodySmall,
                color = Spec.textMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** زر outlined عام (مثل «Manage / Upgrade / View receipt»). */
@Composable
fun SpecOutlinedButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 44.dp,
    borderColor: Color = Spec.border,
    textColor: Color = Spec.text,
) {
    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = androidx.compose.ui.text.TextStyle(
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = textColor,
        )
    }
}

/**
 * 8-slot code input وفق SPEC_PROFILE_WALLET §4.2:
 *  • 8 خانات منفصلة (gap 6، flex 1، ارتفاع 48، نصف قطر 10).
 *  • كل خانة surface + حد 1dp، الممتلئة = حد blueLink.
 *  • حرف 18sp/800 (LTR حتى في العربية).
 *  • كتابة/لصق يُملأ الخانات تلقائياً (auto-advance + paste).
 *  • Backspace على خانة فارغة ⇒ الرجوع للخانة السابقة.
 */
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun CodeSlotInput(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    slotCount: Int = 8,
    enabled: Boolean = true,
) {
    val focusRequesters: List<androidx.compose.ui.focus.FocusRequester> = remember {
        List(slotCount) { androidx.compose.ui.focus.FocusRequester() }
    }
    val focusManager = androidx.compose.ui.platform.LocalFocusManager.current

    fun updateCode(newValue: String) {
        // اللصق أو الإدخال يُحدَّث في onValueChange
        onValueChange(newValue.take(slotCount).uppercase())
    }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        for (i in 0 until slotCount) {
            val ch = value.getOrNull(i)?.toString() ?: ""
            val filled = ch.isNotEmpty()
            val shape = RoundedCornerShape(10.dp)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(shape)
                    .background(Spec.surface)
                    .border(
                        1.dp,
                        if (filled) Spec.blueLink else Spec.border,
                        shape,
                    )
                    .clickable(enabled = enabled) { focusRequesters[i].requestFocus() },
                contentAlignment = Alignment.Center,
            ) {
                androidx.compose.foundation.text.BasicTextField(
                    value = ch,
                    onValueChange = { input ->
                        if (input.isEmpty()) {
                            // Backspace: نظّف الخانة الحالية وانتقل للخلف
                            val newCode = value.toMutableList()
                            if (i < newCode.size) newCode.removeAt(i)
                            updateCode(newCode.joinToString(""))
                            if (i > 0) focusRequesters[i - 1].requestFocus()
                            return@BasicTextField
                        }
                        // لصق أو إدخال
                        val cleaned = input.uppercase().filter { it.isLetterOrDigit() }
                        if (cleaned.length > 1) {
                            // لصق متعدد: املأ من الخانة الحالية
                            val newCode = value.padEnd(i, ' ').toMutableList()
                            cleaned.take(slotCount - i).forEachIndexed { idx, c ->
                                if (i + idx < slotCount) newCode[i + idx] = c
                            }
                            updateCode(newCode.joinToString("").replace(" ", ""))
                            val nextFocus = (i + cleaned.length).coerceAtMost(slotCount - 1)
                            if (nextFocus < slotCount) focusRequesters[nextFocus].requestFocus()
                            else focusManager.clearFocus()
                        } else {
                            // حرف واحد: ضعه في مكانه وانتقل للتالي
                            val newCode = value.padEnd(i, ' ').toMutableList()
                            newCode[i] = cleaned[0]
                            updateCode(newCode.joinToString("").replace(" ", ""))
                            if (i < slotCount - 1) focusRequesters[i + 1].requestFocus()
                            else focusManager.clearFocus()
                        }
                    },
                    enabled = enabled,
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Spec.text,
                        textAlign = TextAlign.Center,
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(Spec.blue),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Ascii,
                        capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Characters,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequesters[i]),
                )
            }
        }
    }
}

/** مواصفة تبويب واحد من شريط الأدمن السفلي. */
data class AdminTabSpec(
    val labelRes: Int,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val badgeCount: Int = 0,
)

/**
 * شريط الأدمن السفلي العائم (SPEC §3.1) — 5 تبويبات، خلفية سطح مرتفعة،
 * حدّ علوي 1dp، لا زرّ مركزي مرتفع.
 */
@Composable
fun AdminBottomBar(
    tabs: List<AdminTabSpec>,
    current: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.foundation.layout.Box(
        modifier = modifier
            .fillMaxWidth()
            .background(androidx.compose.ui.graphics.Color(0xFF1A2438))
            .border(1.dp, androidx.compose.ui.graphics.Color(0xFF223049), RoundedCornerShape(0.dp))
            .padding(vertical = 6.dp),
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceEvenly,
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { idx, tab ->
                val selected = idx == current
                val tint = if (selected) AppColors.Blue else AppColors.Muted
                androidx.compose.foundation.layout.Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onSelect(idx) }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally,
                ) {
                    androidx.compose.foundation.layout.Box {
                        androidx.compose.material3.Icon(
                            imageVector = tab.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                        if (tab.badgeCount > 0) {
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .align(androidx.compose.ui.Alignment.TopEnd)
                                    .size(8.dp)
                                    .clip(androidx.compose.foundation.shape.CircleShape)
                                    .background(AppColors.Red),
                            )
                        }
                    }
                    androidx.compose.material3.Text(
                        text = androidx.compose.ui.res.stringResource(tab.labelRes),
                        color = tint,
                        fontSize = 10.sp,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}
