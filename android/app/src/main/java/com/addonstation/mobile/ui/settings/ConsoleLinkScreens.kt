package com.addonstation.mobile.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Login
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.model.LinkedConsole
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.data.model.parseIsoMillis
import com.addonstation.mobile.data.repo.ConsoleCodeLookup
import com.addonstation.mobile.data.repo.ConsoleLinkRepository
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.ui.apiErrorText
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private enum class ConsoleCodeError { INVALID, WRONG, EXPIRED, TOO_MANY, SERVICE, SIGN_IN }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConsoleLinkScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val container = rememberContainer()
    val signedInState by container.state.signedIn.collectAsStateWithLifecycle()
    val profile by container.state.profile.collectAsStateWithLifecycle()
    val hasSession = signedInState && container.supabase.session() != null
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var pendingApproval by remember { mutableStateOf<ConsoleCodeLookup?>(null) }
    var codeError by remember { mutableStateOf<ConsoleCodeError?>(null) }
    var apiErrorKind by remember { mutableStateOf<ApiErrorKind?>(null) }
    var linkedDeviceName by remember { mutableStateOf<String?>(null) }

    val errorText = when (codeError) {
        ConsoleCodeError.INVALID, ConsoleCodeError.WRONG -> stringResource(R.string.phone_console_wrong_code)
        ConsoleCodeError.EXPIRED -> stringResource(R.string.phone_console_expired)
        ConsoleCodeError.TOO_MANY -> stringResource(R.string.phone_console_too_many)
        ConsoleCodeError.SIGN_IN -> stringResource(R.string.console_link_sign_in_required)
        ConsoleCodeError.SERVICE -> when (apiErrorKind) {
            ApiErrorKind.NETWORK, ApiErrorKind.NOT_CONFIGURED -> apiErrorKind?.let { apiErrorText(it) }
            else -> stringResource(R.string.phone_console_service_error)
        }
        null -> null
    }

    fun errorFor(message: String?): ConsoleCodeError = when (message) {
        ConsoleLinkRepository.MESSAGE_SIGN_IN_REQUIRED -> ConsoleCodeError.SIGN_IN
        ConsoleLinkRepository.MESSAGE_INVALID_CODE -> ConsoleCodeError.INVALID
        ConsoleLinkRepository.MESSAGE_CODE_NOT_FOUND -> ConsoleCodeError.WRONG
        ConsoleLinkRepository.MESSAGE_CODE_EXPIRED -> ConsoleCodeError.EXPIRED
        ConsoleLinkRepository.MESSAGE_TOO_MANY_ATTEMPTS -> ConsoleCodeError.TOO_MANY
        ConsoleLinkRepository.MESSAGE_SERVICE_UNAVAILABLE -> ConsoleCodeError.SERVICE
        else -> ConsoleCodeError.SERVICE
    }

    fun requestApproval() {
        if (submitting) return
        if (!hasSession || container.supabase.session() == null) {
            codeError = ConsoleCodeError.SIGN_IN
            return
        }
        val normalized = ConsoleLinkRepository.normalizeCode(code)
        if (!ConsoleLinkRepository.isValidCode(normalized)) {
            codeError = ConsoleCodeError.INVALID
            return
        }
        code = normalized
        codeError = null
        apiErrorKind = null
        val prepared = container.consoleLinks.prepareCode(normalized)
        if (prepared.ok) {
            pendingApproval = prepared
        } else {
            codeError = errorFor(prepared.message)
        }
    }

    fun approveCode(approval: ConsoleCodeLookup) {
        if (submitting) return
        if (!hasSession || container.supabase.session() == null) {
            pendingApproval = null
            codeError = ConsoleCodeError.SIGN_IN
            return
        }
        val approvedCode = approval.code ?: code
        submitting = true
        codeError = null
        apiErrorKind = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) { container.consoleLinks.claimCode(approvedCode) }
                pendingApproval = null
                if (result.ok) {
                    linkedDeviceName = result.deviceName?.takeIf { it.isNotBlank() }
                        ?: approval.deviceName?.takeIf { it.isNotBlank() }
                        ?: result.platform?.takeIf { it.isNotBlank() }
                        ?: "PS4"
                    code = ""
                } else {
                    codeError = errorFor(result.message)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                pendingApproval = null
                apiErrorKind = error.toApiError().kind
                codeError = if (apiErrorKind == ApiErrorKind.NOT_SIGNED_IN) ConsoleCodeError.SIGN_IN else ConsoleCodeError.SERVICE
            } finally {
                submitting = false
            }
        }
    }

    Box(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        Image(
            painter = painterResource(R.drawable.console_link_cave_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            alignment = Alignment.TopCenter,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.34f),
                        0.45f to AppColors.AuthBg.copy(alpha = 0.64f),
                        1.0f to AppColors.AuthBg.copy(alpha = 0.94f),
                    ),
                ),
        )
        Column(Modifier.fillMaxSize()) {
            ConsoleTopBar(
                title = stringResource(R.string.phone_console_link_title),
                onBack = onBack,
                backgroundColor = AppColors.AuthBarSurface.copy(alpha = 0.88f),
            )
            if (linkedDeviceName != null) {
            ConsoleLinkedSuccess(
                profile = profile,
                deviceName = linkedDeviceName ?: "PS4",
                onDone = onBack,
                onManage = onOpenDevices,
            )
        } else {
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConsoleLinkHero(success = false)
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.phone_console_sign_in_without_typing),
                    style = Spec.rowTitle.copy(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppColors.AuthText,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.phone_console_short_code_help),
                    style = Spec.rowSub.copy(fontSize = 13.sp, lineHeight = 19.sp),
                    color = AppColors.AuthMuted,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(16.dp))

                ConsoleStep(1, stringResource(R.string.phone_console_step_open))
                Spacer(Modifier.height(8.dp))
                ConsoleStep(2, stringResource(R.string.phone_console_step_choose))
                Spacer(Modifier.height(8.dp))
                ConsoleStep(3, stringResource(R.string.phone_console_step_type))
                Spacer(Modifier.height(18.dp))

                Text(
                    stringResource(R.string.phone_console_code_label),
                    style = Spec.smallCaps,
                    color = AppColors.AuthGold,
                    modifier = Modifier.fillMaxWidth().padding(start = 3.dp, bottom = 7.dp),
                )
                if (errorText != null) {
                    ConsoleCodeErrorCard(
                        text = errorText,
                        warning = codeError == ConsoleCodeError.EXPIRED,
                    )
                    Spacer(Modifier.height(10.dp))
                }
                ConsoleCodeBoxes(
                    value = code,
                    onValueChange = { input ->
                        code = ConsoleLinkRepository.normalizeCode(input)
                            .filter { it in ConsoleLinkRepository.CODE_ALPHABET }
                            .take(ConsoleLinkRepository.CODE_LENGTH)
                        codeError = null
                        apiErrorKind = null
                    },
                    enabled = hasSession && codeError != ConsoleCodeError.TOO_MANY && !submitting,
                    error = codeError in setOf(ConsoleCodeError.INVALID, ConsoleCodeError.WRONG, ConsoleCodeError.EXPIRED),
                    onDone = ::requestApproval,
                )
                Spacer(Modifier.height(14.dp))
                ConsolePrimaryButton(
                    text = if (hasSession) stringResource(R.string.phone_console_link_action)
                    else stringResource(R.string.console_link_sign_in_action),
                    enabled = if (hasSession) code.length == ConsoleLinkRepository.CODE_LENGTH && codeError != ConsoleCodeError.TOO_MANY else true,
                    loading = submitting,
                    leading = R.drawable.ic_phone_link,
                    onClick = if (hasSession) ::requestApproval else onLogin,
                )
                Spacer(Modifier.height(14.dp))
                ConsoleSecurityNote()
                Spacer(Modifier.height(22.dp))
                TextButton(onClick = onOpenDevices, enabled = hasSession) {
                    Text(stringResource(R.string.phone_console_manage), color = AppColors.AuthBlueLight)
                }
            }
        }
    }
    }

    pendingApproval?.let { approval ->
        ModalBottomSheet(
            onDismissRequest = { if (!submitting) pendingApproval = null },
            containerColor = AppColors.AuthSurface,
        ) {
            ConsoleApprovalSheet(
                code = approval.code ?: code,
                deviceName = approval.deviceName,
                appVersion = approval.appVersion,
                profile = profile,
                avatarUrl = profile?.avatarUrl?.let { assetUrl(container, it) },
                busy = submitting,
                onAllow = { approveCode(approval) },
                onCancel = { if (!submitting) pendingApproval = null },
            )
        }
    }
}

@Composable
private fun ConsoleLinkHero(success: Boolean) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth(),
    ) {
        ConsoleHeroTile(R.drawable.ic_phone_phone, AppColors.AuthBlueLight, highlighted = false)
        repeat(if (success) 0 else 4) {
            Box(Modifier.padding(horizontal = 3.dp).size(6.dp).clip(CircleShape).background(AppColors.AuthBlue.copy(alpha = 0.48f)))
        }
        if (success) {
            Box(
                Modifier
                    .padding(horizontal = 7.dp)
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(AppColors.AuthGreen)
                    .border(5.dp, AppColors.AuthGreen.copy(alpha = 0.20f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(31.dp))
            }
        }
        ConsoleHeroTile(R.drawable.ic_phone_tv, Color.White, highlighted = true)
    }
}

@Composable
private fun ConsoleHeroTile(icon: Int, tint: Color, highlighted: Boolean) {
    Box(
        Modifier
            .size(70.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (highlighted) Brush.linearGradient(listOf(Color(0xFF2E7BEF), Color(0xFF4C9AFF)))
                else Brush.linearGradient(listOf(AppColors.AuthSurface, AppColors.AuthSurface)),
            )
            .border(1.dp, if (highlighted) AppColors.AuthBlue.copy(alpha = 0.50f) else AppColors.AuthBorder, RoundedCornerShape(20.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(painterResource(icon), contentDescription = null, tint = tint, modifier = Modifier.size(32.dp))
    }
}

@Composable
private fun ConsoleStep(number: Int, text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier.size(26.dp).clip(CircleShape).background(AppColors.AuthBlue),
            contentAlignment = Alignment.Center,
        ) {
            Text(number.toString(), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
        }
        Text(text, color = AppColors.AuthText, fontSize = 13.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
    }
}

@Composable
private fun ConsoleCodeBoxes(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    error: Boolean,
    onDone: () -> Unit,
) {
    val description = stringResource(R.string.phone_console_code_label)
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Ascii,
                capitalization = KeyboardCapitalization.Characters,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(color = Color.Transparent, fontSize = 1.sp),
            cursorBrush = SolidColor(Color.Transparent),
            modifier = Modifier.fillMaxWidth().semantics { contentDescription = description },
            decorationBox = { innerTextField ->
                Box(Modifier.fillMaxWidth()) {
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(ConsoleLinkRepository.CODE_LENGTH) { index ->
                            if (index == 3) {
                                Text("–", color = AppColors.AuthMuted, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                            val active = index == value.length && enabled
                            val borderColor = when {
                                error -> AppColors.AuthRed
                                active -> AppColors.AuthBlueLight
                                else -> AppColors.AuthBorder
                            }
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height(58.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(AppColors.AuthSurface)
                                    .border(if (active) 2.dp else 1.dp, borderColor, RoundedCornerShape(13.dp)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    value.getOrNull(index)?.toString().orEmpty(),
                                    color = if (enabled) (if (error) AppColors.AuthRedText else AppColors.AuthText) else AppColors.AuthMuted,
                                    fontFamily = PixelFont,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    Box(Modifier.size(1.dp).alpha(0f)) { innerTextField() }
                }
            },
        )
    }
}

@Composable
private fun ConsoleCodeErrorCard(text: String, warning: Boolean) {
    val color = if (warning) Color(0xFFF0A45E) else AppColors.AuthRedText
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(color.copy(alpha = 0.10f))
            .border(1.dp, color.copy(alpha = 0.50f), RoundedCornerShape(13.dp))
            .padding(11.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            painterResource(if (warning) R.drawable.ic_phone_info else R.drawable.ic_phone_alert),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Text(text, color = color, fontSize = 12.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ConsoleSecurityNote() {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(13.dp))
            .background(AppColors.AuthSurface.copy(alpha = 0.78f))
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(13.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            painterResource(R.drawable.ic_phone_info),
            contentDescription = null,
            tint = AppColors.AuthGreenText,
            modifier = Modifier.size(17.dp),
        )
        Text(
            stringResource(R.string.phone_console_security_note),
            color = AppColors.AuthMuted,
            fontSize = 11.sp,
            lineHeight = 17.sp,
        )
    }
}

@Composable
private fun ConsolePrimaryButton(
    text: String,
    enabled: Boolean,
    loading: Boolean,
    leading: Int? = null,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clip(RoundedCornerShape(17.dp))
            .background(
                if (enabled) Brush.linearGradient(listOf(Color(0xFF2E7BEF), Color(0xFF4C9AFF)))
                else Brush.linearGradient(listOf(AppColors.AuthSurfaceHigh, AppColors.AuthSurfaceHigh)),
            )
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
        } else if (leading != null) {
            Icon(painterResource(leading), contentDescription = null, tint = if (enabled) Color.White else AppColors.AuthMuted, modifier = Modifier.size(20.dp))
        }
        if (loading || leading != null) Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = if (enabled) Color.White else AppColors.AuthMuted,
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ConsoleApprovalSheet(
    code: String,
    deviceName: String?,
    appVersion: String?,
    profile: Profile?,
    avatarUrl: String?,
    busy: Boolean,
    onAllow: () -> Unit,
    onCancel: () -> Unit,
) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            stringResource(R.string.phone_console_allow_title),
            color = AppColors.AuthText,
            fontSize = 19.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(15.dp))
                .background(AppColors.AuthSurfaceHigh)
                .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(15.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.AuthBlue.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_phone_pad), null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(24.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    deviceName?.takeIf { it.isNotBlank() } ?: stringResource(R.string.phone_console_ps4_name),
                    color = AppColors.AuthText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                val version = appVersion?.takeIf { it.isNotBlank() }
                val requestLabel = if (version != null) {
                    stringResource(R.string.phone_console_requested_version, version)
                } else {
                    stringResource(R.string.phone_console_requested_now)
                }
                Text(requestLabel, color = AppColors.AuthMuted, fontSize = 11.sp)
            }
            Text(
                code.take(3) + " " + code.drop(3),
                color = AppColors.AuthBlueLight,
                fontFamily = PixelFont,
                fontSize = 12.sp,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(AppColors.AuthBlue.copy(alpha = 0.13f)).padding(8.dp),
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            AvatarImage(url = avatarUrl, size = 42.dp, borderColor = AppColors.AuthGold, borderWidth = 2.dp)
            Column {
                Text(stringResource(R.string.phone_console_signed_in_as), color = AppColors.AuthMuted, fontSize = 11.sp)
                Text(
                    profile?.displayName ?: profile?.username ?: stringResource(R.string.common_guest),
                    color = AppColors.AuthText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                )
                profile?.username?.let { Text("@$it", color = AppColors.AuthMuted, fontSize = 11.sp) }
            }
        }

        ConsolePermissionLine(true, stringResource(R.string.phone_console_can_browse))
        ConsolePermissionLine(true, stringResource(R.string.phone_console_can_use_ac))
        ConsolePermissionLine(false, stringResource(R.string.phone_console_cannot_credentials))
        ConsolePermissionLine(false, stringResource(R.string.phone_console_cannot_plans))

        ConsolePrimaryButton(
            text = stringResource(R.string.phone_console_allow),
            enabled = !busy,
            loading = busy,
            leading = R.drawable.ic_phone_check,
            onClick = onAllow,
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(50.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(AppColors.AuthSurfaceHigh)
                .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(15.dp))
                .clickable(enabled = !busy, onClick = onCancel),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.common_cancel), color = AppColors.AuthText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        Text(
            stringResource(R.string.phone_console_not_requested),
            color = AppColors.AuthMuted,
            fontSize = 11.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ConsolePermissionLine(allowed: Boolean, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(
            if (allowed) Icons.Filled.Check else Icons.Filled.Close,
            contentDescription = null,
            tint = if (allowed) AppColors.AuthGreenText else AppColors.AuthMuted,
            modifier = Modifier.size(17.dp),
        )
        Text(
            text,
            color = if (allowed) AppColors.AuthGreenText else AppColors.AuthMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ConsoleLinkedSuccess(
    profile: Profile?,
    deviceName: String,
    onDone: () -> Unit,
    onManage: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 34.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ConsoleLinkHero(success = true)
        Spacer(Modifier.height(22.dp))
        Text(
            stringResource(R.string.phone_console_linked_title),
            color = AppColors.AuthText,
            fontSize = 21.sp,
            fontWeight = FontWeight.ExtraBold,
        )
        Spacer(Modifier.height(7.dp))
        Text(
            stringResource(
                R.string.phone_console_linked_message,
                profile?.displayName ?: profile?.username ?: stringResource(R.string.common_guest),
            ),
            color = AppColors.AuthMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.AuthSurface)
                .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(16.dp))
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.AuthBlue.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(painterResource(R.drawable.ic_phone_pad), null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(23.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(deviceName, color = AppColors.AuthText, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                Text(stringResource(R.string.phone_console_linked_now), color = AppColors.AuthMuted, fontSize = 11.sp)
            }
            ConsoleOnlinePill()
        }
        Spacer(Modifier.height(24.dp))
        ConsolePrimaryButton(
            text = stringResource(R.string.phone_console_done),
            enabled = true,
            loading = false,
            onClick = onDone,
        )
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .height(54.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.AuthSurface)
                .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(16.dp))
                .clickable(onClick = onManage),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(stringResource(R.string.phone_console_manage), color = AppColors.AuthText, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedConsolesScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onLinkAnother: () -> Unit,
) {
    val container = rememberContainer()
    val signedInState by container.state.signedIn.collectAsStateWithLifecycle()
    val hasSession = signedInState && container.supabase.session() != null
    val localeCode by container.prefs.locale.collectAsStateWithLifecycle(initialValue = "en")
    val locale = remember(localeCode) { Locale.forLanguageTag(localeCode) }
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<LinkedConsole>>(emptyList()) }
    var initialLoading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshRequest by remember { mutableStateOf(0) }
    var pendingUnlink by remember { mutableStateOf<LinkedConsole?>(null) }
    var pendingUnlinkAll by remember { mutableStateOf(false) }
    var deletingId by remember { mutableStateOf<String?>(null) }
    var deletingAll by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasSession, refreshRequest) {
        if (!hasSession || container.supabase.session() == null) {
            devices = emptyList()
            initialLoading = false
            loadFailed = false
            return@LaunchedEffect
        }
        initialLoading = true
        loadFailed = false
        try {
            devices = withContext(Dispatchers.IO) { container.consoleLinks.linkedConsoles() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            loadFailed = true
        } finally {
            initialLoading = false
        }
    }

    fun refresh() {
        if (!hasSession || refreshing || initialLoading) return
        scope.launch {
            refreshing = true
            loadFailed = false
            try {
                devices = withContext(Dispatchers.IO) { container.consoleLinks.linkedConsoles() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                loadFailed = true
            } finally {
                refreshing = false
            }
        }
    }

    fun unlink(console: LinkedConsole) {
        if (deletingId != null || deletingAll) return
        scope.launch {
            deletingId = console.id
            notice = null
            try {
                devices = withContext(Dispatchers.IO) { container.consoleLinks.unlinkAndReload(console.id) }
                pendingUnlink = null
                notice = container.appContext.getString(R.string.console_device_unlink_success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                notice = container.appContext.getString(R.string.console_device_unlink_failed)
            } finally {
                deletingId = null
            }
        }
    }

    fun unlinkAll() {
        if (deletingAll || deletingId != null) return
        scope.launch {
            deletingAll = true
            notice = null
            try {
                devices = withContext(Dispatchers.IO) { container.consoleLinks.unlinkAllAndReload() }
                pendingUnlinkAll = false
                notice = container.appContext.getString(R.string.console_device_unlink_success)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                notice = container.appContext.getString(R.string.console_device_unlink_failed)
            } finally {
                deletingAll = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        ConsoleTopBar(
            title = stringResource(R.string.phone_console_devices_title),
            onBack = onBack,
            actionLabel = stringResource(R.string.phone_console_link_short),
            onAction = onLinkAnother,
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::refresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            when {
                !hasSession -> ConsoleSignedOutState(onLogin)
                initialLoading -> LoadingBox(text = stringResource(R.string.common_loading))
                loadFailed -> ErrorState(
                    onRetry = { refreshRequest += 1 },
                    text = stringResource(R.string.console_devices_load_failed),
                )
                devices.isEmpty() -> ConsoleEmptyState(onLinkAnother)
                else -> LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    notice?.let { message ->
                        item {
                            Text(
                                message,
                                color = AppColors.AuthGreenText,
                                fontSize = 12.sp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(AppColors.AuthGreen.copy(alpha = 0.10f))
                                    .padding(10.dp),
                            )
                        }
                    }
                    items(devices, key = { it.id }) { device ->
                        LinkedConsoleCard(
                            console = device,
                            locale = locale,
                            deleting = deletingId == device.id,
                            onUnlink = { pendingUnlink = device },
                        )
                    }
                    item { ConsoleUnlinkingNote() }
                    item {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(54.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(AppColors.AuthSurface)
                                .border(1.dp, AppColors.AuthRedBorder, RoundedCornerShape(16.dp))
                                .clickable(enabled = !deletingAll && deletingId == null) { pendingUnlinkAll = true },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            if (deletingAll) {
                                CircularProgressIndicator(color = AppColors.AuthRedText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                            } else {
                                Icon(Icons.Filled.ExitToApp, null, tint = AppColors.AuthRedText, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(8.dp))
                            }
                            Text(
                                stringResource(R.string.phone_console_unlink_all),
                                color = AppColors.AuthRedText,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.ExtraBold,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingUnlink?.let { console ->
        ConsoleUnlinkDialog(
            all = false,
            busy = deletingId != null,
            onConfirm = { unlink(console) },
            onDismiss = { if (deletingId == null) pendingUnlink = null },
        )
    }
    if (pendingUnlinkAll) {
        ConsoleUnlinkDialog(
            all = true,
            busy = deletingAll,
            onConfirm = ::unlinkAll,
            onDismiss = { if (!deletingAll) pendingUnlinkAll = false },
        )
    }
}

@Composable
private fun ConsoleTopBar(
    title: String,
    onBack: () -> Unit,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    backgroundColor: Color = AppColors.AuthBarSurface,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 66.dp)
            .background(backgroundColor)
            .border(0.dp, Color.Transparent)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(AppColors.AuthSurface).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.common_back), tint = AppColors.AuthText, modifier = Modifier.size(22.dp))
        }
        Text(
            title,
            color = AppColors.AuthText,
            fontFamily = PixelFont,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (actionLabel != null && onAction != null) {
            Row(
                Modifier
                    .height(42.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(AppColors.AuthBlue)
                    .clickable(onClick = onAction)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Icon(Icons.Filled.Add, null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text(actionLabel, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
}

@Composable
private fun LinkedConsoleCard(
    console: LinkedConsole,
    locale: Locale,
    deleting: Boolean,
    onUnlink: () -> Unit,
) {
    val name = console.deviceName?.takeIf { it.isNotBlank() }
        ?: console.platform?.takeIf { it.isNotBlank() }
        ?: "PS4"
    val online = remember(console.lastSeenAt) {
        parseIsoMillis(console.lastSeenAt)?.let { System.currentTimeMillis() - it <= 5L * 60L * 1000L } == true
    }
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(16.dp))
            .padding(13.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(AppColors.AuthBlue.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_phone_pad), null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(24.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    name,
                    color = AppColors.AuthText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (online) ConsoleOnlinePill()
            }
            Text(
                stringResource(
                    R.string.console_device_last_seen,
                    TimeFormat.dateTime(console.lastSeenAt, locale).ifBlank { "—" },
                ),
                color = AppColors.AuthMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.console_device_linked_at, TimeFormat.dateTime(console.createdAt, locale)),
                color = AppColors.AuthMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Box(
            Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.AuthRed.copy(alpha = 0.08f))
                .border(1.dp, AppColors.AuthRedBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = !deleting, onClick = onUnlink),
            contentAlignment = Alignment.Center,
        ) {
            if (deleting) {
                CircularProgressIndicator(color = AppColors.AuthRedText, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            } else {
                Icon(painterResource(R.drawable.ic_phone_trash), stringResource(R.string.console_device_unlink), tint = AppColors.AuthRedText, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun ConsoleOnlinePill() {
    Text(
        stringResource(R.string.phone_console_online),
        color = AppColors.AuthGreenText,
        fontSize = 9.sp,
        fontWeight = FontWeight.ExtraBold,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.AuthGreen.copy(alpha = 0.14f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun ConsoleUnlinkingNote() {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 5.dp, vertical = 4.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        Icon(painterResource(R.drawable.ic_phone_info), null, tint = AppColors.AuthMuted, modifier = Modifier.size(16.dp))
        Text(
            stringResource(R.string.phone_console_unlink_note),
            color = AppColors.AuthMuted,
            fontSize = 11.sp,
            lineHeight = 16.sp,
        )
    }
}

@Composable
private fun ConsoleEmptyState(onLink: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(86.dp).clip(CircleShape).background(AppColors.AuthBlue.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(painterResource(R.drawable.ic_phone_pad), null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(44.dp))
        }
        Spacer(Modifier.height(18.dp))
        Text(
            stringResource(R.string.phone_console_empty_title),
            color = AppColors.AuthText,
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(5.dp))
        Text(
            stringResource(R.string.phone_console_empty_text),
            color = AppColors.AuthMuted,
            fontSize = 13.sp,
            lineHeight = 19.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(18.dp))
        Row(
            Modifier
                .height(54.dp)
                .clip(RoundedCornerShape(17.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF2E7BEF), Color(0xFF4C9AFF))))
                .clickable(onClick = onLink)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(painterResource(R.drawable.ic_phone_link), null, tint = Color.White, modifier = Modifier.size(20.dp))
            Text(stringResource(R.string.phone_console_link_console), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun ConsoleSignedOutState(onLogin: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(Icons.Filled.Login, null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(46.dp))
        Spacer(Modifier.height(14.dp))
        Text(
            stringResource(R.string.console_link_sign_in_required),
            color = AppColors.AuthText,
            fontSize = 15.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        ConsolePrimaryButton(
            text = stringResource(R.string.console_link_sign_in_action),
            enabled = true,
            loading = false,
            onClick = onLogin,
        )
    }
}

@Composable
private fun ConsoleUnlinkDialog(
    all: Boolean,
    busy: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        containerColor = AppColors.AuthSurface,
        icon = {
            Box(
                Modifier.size(56.dp).clip(CircleShape).background(AppColors.AuthRed.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.ExitToApp, null, tint = AppColors.AuthRedText, modifier = Modifier.size(28.dp))
            }
        },
        title = {
            Text(
                stringResource(if (all) R.string.phone_console_unlink_all_title else R.string.phone_console_unlink_title),
                color = AppColors.AuthText,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        text = {
            Text(
                stringResource(if (all) R.string.phone_console_unlink_all_message else R.string.phone_console_unlink_message),
                color = AppColors.AuthMuted,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(enabled = !busy, onClick = onConfirm) {
                if (busy) CircularProgressIndicator(color = AppColors.AuthRedText, strokeWidth = 2.dp, modifier = Modifier.size(17.dp))
                else Text(stringResource(R.string.console_device_unlink), color = AppColors.AuthRedText, fontWeight = FontWeight.ExtraBold)
            }
        },
        dismissButton = {
            TextButton(enabled = !busy, onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel), color = AppColors.AuthText)
            }
        },
    )
}
