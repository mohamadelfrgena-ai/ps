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
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec

/** المكوّنات المشتركة لشاشات SPEC_AUTH_SETTINGS.md. */

@Composable
fun AuthSettingsHeader(
    onBack: () -> Unit,
    backgroundRes: Int = R.drawable.login_bg,
) {
    Box(Modifier.fillMaxWidth().height(Spec.authHeaderH)) {
        Image(
            painter = painterResource(backgroundRes),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to AppColors.AuthBg.copy(alpha = 0.25f),
                        0.96f to AppColors.AuthBg,
                    ),
                ),
        )
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(
                painter = painterResource(R.drawable.app_icon),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, AppColors.AuthGold, RoundedCornerShape(16.dp)),
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.brand_wordmark).uppercase(),
                style = Spec.screenTitle.copy(fontSize = 16.sp),
                color = AppColors.AuthText,
            )
        }
        Box(
            Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(AppColors.AuthSurface.copy(alpha = 0.86f))
                .border(1.dp, AppColors.AuthBorder, CircleShape)
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.common_back),
                tint = AppColors.AuthText,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

/** Label-above field styled to the auth spec, including the 44dp password-eye target. */
@Composable
fun AuthSettingsField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next,
    password: Boolean = false,
    helper: String? = null,
    error: String? = null,
    onDone: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var passwordVisible by remember { mutableStateOf(false) }
    Column(modifier.fillMaxWidth()) {
        Text(
            label,
            style = Spec.fieldLabel,
            color = AppColors.AuthChipLabel,
            modifier = Modifier.padding(start = 2.dp),
        )
        Spacer(Modifier.height(6.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(Spec.fieldH)
                .clip(RoundedCornerShape(Spec.fieldRadius))
                .background(AppColors.AuthSurface)
                .border(
                    1.dp,
                    if (error != null) AppColors.AuthRed else AppColors.AuthBorder,
                    RoundedCornerShape(Spec.fieldRadius),
                )
                .padding(start = 14.dp, end = if (password) 3.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                singleLine = true,
                textStyle = Spec.fieldHint.copy(color = AppColors.AuthText, textAlign = TextAlign.Start),
                cursorBrush = SolidColor(AppColors.AuthBlueLight),
                keyboardOptions = KeyboardOptions(keyboardType = if (password) KeyboardType.Password else keyboardType, imeAction = imeAction),
                keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
                visualTransformation = if (password && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        if (value.isEmpty()) {
                            Text(
                                placeholder,
                                style = Spec.fieldHint,
                                color = AppColors.AuthMuted,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        innerTextField()
                    }
                },
            )
            if (password) {
                IconButton(
                    onClick = { passwordVisible = !passwordVisible },
                    modifier = Modifier.size(44.dp),
                ) {
                    Icon(
                        imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = stringResource(
                            if (passwordVisible) R.string.cd_hide_password else R.string.cd_show_password,
                        ),
                        tint = if (passwordVisible) AppColors.AuthBlueLight else AppColors.AuthMuted,
                        modifier = Modifier.size(21.dp),
                    )
                }
            }
        }
        when {
            error != null -> Text(
                error,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = AppColors.AuthRed,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
            )
            helper != null -> Text(
                helper,
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                color = AppColors.AuthMuted,
                modifier = Modifier.padding(start = 2.dp, top = 4.dp),
            )
        }
    }
}

@Composable
fun AuthSettingsPrimaryButton(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    loading: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(Spec.fieldH)
            .clip(RoundedCornerShape(Spec.fieldRadius))
            .background(if (enabled) AppColors.AuthBlue else AppColors.AuthSurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(18.dp),
                color = AppColors.AuthWhite,
                strokeWidth = 2.dp,
            )
            Spacer(Modifier.size(9.dp))
        }
        Text(
            text,
            style = Spec.primaryBtn,
            color = if (enabled || loading) AppColors.AuthWhite else AppColors.AuthMuted,
        )
    }
}

@Composable
fun AuthSettingsFooter(
    prompt: String,
    action: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .heightIn(min = 44.dp)
            .padding(start = 24.dp, end = 24.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(prompt, style = Spec.authSubtitle, color = AppColors.AuthMuted, textAlign = TextAlign.Center)
        Spacer(Modifier.size(5.dp))
        Box(
            Modifier
                .heightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onClick)
                .padding(horizontal = 6.dp, vertical = 9.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(action, style = Spec.rowValue, color = AppColors.AuthGold)
        }
    }
}

@Composable
fun AuthSettingsTopBar(
    title: String,
    onBack: () -> Unit,
    walletBalance: Int? = null,
    onWalletClick: (() -> Unit)? = null,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(AppColors.AuthBarSurface)
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppColors.AuthSurface)
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back),
                    tint = AppColors.AuthText,
                    modifier = Modifier.size(22.dp),
                )
            }
            Text(
                title.uppercase(),
                style = Spec.screenTitle.copy(fontSize = 15.sp),
                color = AppColors.AuthText,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (onWalletClick != null) {
                Row(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(22.dp))
                        .background(AppColors.AuthSurfaceHigh)
                        .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(22.dp))
                        .clickable(onClick = onWalletClick)
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CoinIcon(size = 16.dp)
                    Text(walletBalance?.toString() ?: "—", style = Spec.rowValue, color = AppColors.AuthText)
                    Text("AC", style = TextStyle(fontSize = 10.sp), color = AppColors.AuthMuted)
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.AuthBorder))
    }
}

@Composable
fun AuthSettingsSectionLabel(
    text: String,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 20.dp, top = 26.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            Modifier
                .size(width = 6.dp, height = 14.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(accent),
        )
        Text(text.uppercase(), style = Spec.smallCaps, color = accent)
    }
}

@Composable
fun AuthSettingsGroupCard(
    modifier: Modifier = Modifier,
    radius: Dp = 18.dp,
    background: androidx.compose.ui.graphics.Color = AppColors.AuthSurface,
    borderColor: androidx.compose.ui.graphics.Color = AppColors.AuthBorder,
    content: @Composable () -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(radius))
            .background(background)
            .border(1.dp, borderColor, RoundedCornerShape(radius)),
    ) { content() }
}

@Composable
fun AuthSettingsImageTile(resId: Int) {
    Image(
        painter = painterResource(resId),
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp)),
    )
}

@Composable
fun AuthSettingsIconTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .size(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(accent.copy(alpha = 0.15f))
            .border(1.dp, accent.copy(alpha = 0.33f), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
    }
}

@Composable
fun AuthSettingsRow(
    title: String,
    subtitle: String? = null,
    value: String? = null,
    clickable: Boolean = false,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = false,
    tile: @Composable () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 68.dp)
            .then(if (clickable && onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        tile()
        Column(Modifier.weight(1f)) {
            Text(title, style = Spec.rowTitle, color = AppColors.AuthText)
            subtitle?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = Spec.rowSub, color = AppColors.AuthMuted)
            }
        }
        value?.let {
            Text(
                it,
                style = Spec.rowValue,
                color = AppColors.AuthChipLabel,
                textAlign = TextAlign.End,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 120.dp),
            )
        }
        trailing?.invoke()
        if (chevron) Text("›", style = Spec.rowValue.copy(fontSize = 22.sp), color = AppColors.AuthMuted)
    }
}

@Composable
fun AuthSettingsDivider(indent: Dp = 70.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = indent)
            .height(1.dp)
            .background(AppColors.AuthDivider),
    )
}

@Composable
fun AuthSettingsPill(
    text: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .heightIn(min = 34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(accent.copy(alpha = 0.13f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = Spec.pillText, color = accent, maxLines = 1)
    }
}

@Composable
fun AuthSettingsSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.AuthBg)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = selectedIndex == index
            Box(
                Modifier
                    .heightIn(min = 34.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) AppColors.AuthBlue else androidx.compose.ui.graphics.Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = Spec.segText,
                    color = if (selected) AppColors.AuthWhite else AppColors.AuthChipLabel,
                )
            }
        }
    }
}

@Composable
fun AuthSettingsSwitch(
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
        colors = SwitchDefaults.colors(
            checkedThumbColor = AppColors.AuthBg,
            checkedTrackColor = AppColors.AuthGreen,
            checkedBorderColor = AppColors.AuthGreen,
            uncheckedThumbColor = AppColors.AuthMuted,
            uncheckedTrackColor = AppColors.AuthSurfaceHigh,
            uncheckedBorderColor = AppColors.AuthBorder,
            disabledCheckedThumbColor = AppColors.AuthMuted,
            disabledCheckedTrackColor = AppColors.AuthSurfaceHigh,
            disabledUncheckedThumbColor = AppColors.AuthMuted,
            disabledUncheckedTrackColor = AppColors.AuthSurfaceHigh,
        ),
    )
}
