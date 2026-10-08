package com.addonstation.mobile.ui.auth

import android.util.Patterns
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.ui.apiErrorText
import com.addonstation.mobile.ui.components.AuthSettingsField
import com.addonstation.mobile.ui.components.AuthSettingsFooter
import com.addonstation.mobile.ui.components.AuthSettingsGroupCard
import com.addonstation.mobile.ui.components.AuthSettingsHeader
import com.addonstation.mobile.ui.components.AuthSettingsPrimaryButton
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec
import kotlinx.coroutines.launch

private val USERNAME_RE = Regex("^[A-Za-z0-9_]+$")
private const val USERNAME_MIN = 3
private const val USERNAME_MAX = 24

/** Sign in — مطابق لـ SPEC_AUTH_SETTINGS.md §1. */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onOpenSignup: () -> Unit,
    notice: String? = null,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var errorKind by remember { mutableStateOf<ApiErrorKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var recoveryOpen by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }
    var recoveryMessage by remember { mutableStateOf<String?>(null) }
    var recoveryError by remember { mutableStateOf<String?>(null) }
    var recoveryBusy by remember { mutableStateOf(false) }

    val enterEmail = stringResource(R.string.msg_enter_email_first)
    val enterBoth = stringResource(R.string.msg_enter_email_password)
    val invalidEmail = stringResource(R.string.msg_invalid_email)
    val signingIn = stringResource(R.string.msg_signing_in)
    val sending = stringResource(R.string.msg_sending)
    val checkEmail = stringResource(R.string.msg_check_email)

    fun submit() {
        emailError = null
        passwordError = null
        errorKind = null
        when {
            email.isBlank() -> {
                emailError = enterEmail
                if (password.isBlank()) passwordError = enterBoth
                return
            }
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> {
                emailError = invalidEmail
                return
            }
            password.isBlank() -> {
                passwordError = enterBoth
                return
            }
        }
        scope.launch {
            busy = true
            container.auth.signIn(email.trim(), password)
                .onSuccess {
                    container.state.setSignedIn(true)
                    container.state.refreshAll()
                    onSuccess()
                }
                .onFailure { errorKind = it.toApiError().kind }
            busy = false
        }
    }

    Column(
        Modifier.fillMaxSize().background(AppColors.AuthBg).imePadding(),
    ) {
        AuthSettingsHeader(onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.auth_sign_in),
                style = Spec.authTitle,
                color = AppColors.AuthText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.auth_login_subtitle),
                style = Spec.authSubtitle,
                color = AppColors.AuthMuted,
                textAlign = TextAlign.Center,
            )
            if (notice != null) {
                Spacer(Modifier.height(8.dp))
                Text(notice, style = Spec.rowSub, color = AppColors.AuthGold, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(14.dp))
            AuthSettingsField(
                label = stringResource(R.string.auth_email),
                value = email,
                onValueChange = { email = it; emailError = null; errorKind = null },
                placeholder = stringResource(R.string.auth_email_placeholder),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                error = emailError ?: errorKind
                    ?.takeIf { it == ApiErrorKind.INVALID_EMAIL || it == ApiErrorKind.EMAIL_NOT_CONFIRMED || it == ApiErrorKind.ALREADY_REGISTERED }
                    ?.let { apiErrorText(it) },
            )
            Spacer(Modifier.height(14.dp))
            AuthSettingsField(
                label = stringResource(R.string.auth_password),
                value = password,
                onValueChange = { password = it; passwordError = null; errorKind = null },
                placeholder = stringResource(R.string.auth_password_placeholder),
                password = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onDone = { submit() },
                error = passwordError ?: errorKind
                    ?.takeIf { it == ApiErrorKind.INVALID_CREDENTIALS || it == ApiErrorKind.PASSWORD_TOO_SHORT }
                    ?.let { apiErrorText(it) },
            )
            val generalError = errorKind?.takeUnless {
                it == ApiErrorKind.INVALID_EMAIL || it == ApiErrorKind.EMAIL_NOT_CONFIRMED ||
                    it == ApiErrorKind.ALREADY_REGISTERED || it == ApiErrorKind.INVALID_CREDENTIALS ||
                    it == ApiErrorKind.PASSWORD_TOO_SHORT
            }
            if (generalError != null) {
                Spacer(Modifier.height(8.dp))
                Text(apiErrorText(generalError), style = Spec.rowSub, color = AppColors.AuthRed)
            }
            if (recoveryOpen) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(AppColors.AuthSurface)
                        .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(9.dp),
                ) {
                    Text(stringResource(R.string.auth_recovery_title), style = Spec.rowTitle, color = AppColors.AuthText)
                    Text(stringResource(R.string.auth_recovery_text), style = Spec.rowSub, color = AppColors.AuthMuted)
                    AuthSettingsField(
                        label = stringResource(R.string.auth_email),
                        value = recoveryEmail,
                        onValueChange = { recoveryEmail = it; recoveryError = null; recoveryMessage = null },
                        placeholder = stringResource(R.string.auth_email_placeholder),
                        keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                        imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                        onDone = {
                            if (recoveryEmail.isBlank()) recoveryError = enterEmail
                            else scope.launch {
                                recoveryBusy = true
                                container.auth.sendRecovery(recoveryEmail.trim())
                                    .onSuccess { recoveryMessage = checkEmail; recoveryError = null }
                                    .onFailure { errorKind = it.toApiError().kind }
                                recoveryBusy = false
                            }
                        },
                        error = recoveryError,
                    )
                    AuthSettingsPrimaryButton(
                        text = if (recoveryBusy) sending else stringResource(R.string.auth_send_recovery),
                        onClick = {
                            if (recoveryEmail.isBlank()) {
                                recoveryError = enterEmail
                            } else {
                                scope.launch {
                                    recoveryBusy = true
                                    recoveryMessage = null
                                    container.auth.sendRecovery(recoveryEmail.trim())
                                        .onSuccess { recoveryMessage = checkEmail; recoveryError = null }
                                        .onFailure { errorKind = it.toApiError().kind }
                                    recoveryBusy = false
                                }
                            }
                        },
                        enabled = !recoveryBusy,
                        loading = recoveryBusy,
                    )
                    recoveryMessage?.let {
                        Text(it, style = Spec.rowSub, color = AppColors.AuthGreenText)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().heightIn(min = 44.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .heightIn(min = 44.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable {
                            recoveryOpen = !recoveryOpen
                            recoveryError = null
                            recoveryMessage = null
                            if (recoveryEmail.isBlank()) recoveryEmail = email
                        }
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.auth_forgot), style = Spec.rowValue, color = AppColors.AuthBlueLight)
                }
            }
            AuthSettingsPrimaryButton(
                text = if (busy) signingIn else stringResource(R.string.auth_sign_in),
                onClick = { submit() },
                enabled = !busy,
                loading = busy,
            )
            Spacer(Modifier.height(20.dp))
        }
        AuthSettingsFooter(
            prompt = stringResource(R.string.auth_no_account),
            action = stringResource(R.string.auth_create_account),
            onClick = onOpenSignup,
        )
    }
}

/** Create account — مطابق لـ SPEC_AUTH_SETTINGS.md §2. */
@Composable
fun SignUpScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onOpenLogin: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var emailError by remember { mutableStateOf<String?>(null) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var errorKind by remember { mutableStateOf<ApiErrorKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var pendingConfirm by remember { mutableStateOf(false) }
    var resendMessage by remember { mutableStateOf<String?>(null) }
    var resendErrorKind by remember { mutableStateOf<ApiErrorKind?>(null) }
    var resendOk by remember { mutableStateOf(false) }

    val creating = stringResource(R.string.msg_creating_account)
    val shortUsername = stringResource(R.string.msg_username_too_short)
    val longUsername = stringResource(R.string.msg_username_too_long)
    val invalidUsername = stringResource(R.string.msg_username_invalid)
    val invalidEmail = stringResource(R.string.msg_invalid_email)
    val shortPassword = stringResource(R.string.msg_password_too_short)
    val noMatch = stringResource(R.string.msg_passwords_no_match)
    val createdMsg = stringResource(R.string.msg_account_created)
    val signingIn = stringResource(R.string.msg_signing_in)
    val resentMsg = stringResource(R.string.auth_confirm_resent)
    val resendFailedMsg = stringResource(R.string.msg_network_error)

    fun clearErrors() {
        usernameError = null
        emailError = null
        passwordError = null
        confirmError = null
        errorKind = null
    }

    fun submit() {
        clearErrors()
        val name = username.trim()
        when {
            name.length < USERNAME_MIN -> { usernameError = shortUsername; return }
            name.length > USERNAME_MAX -> { usernameError = longUsername; return }
            !USERNAME_RE.matches(name) -> { usernameError = invalidUsername; return }
            !Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> { emailError = invalidEmail; return }
            password.length < 6 -> { passwordError = shortPassword; return }
            password != confirm -> { confirmError = noMatch; return }
        }
        scope.launch {
            busy = true
            status = creating
            container.auth.signUp(name, email.trim(), password)
                .onSuccess {
                    if (container.supabase.session() != null) {
                        container.state.setSignedIn(true)
                        container.state.refreshAll()
                        status = null
                        onSuccess()
                    } else {
                        status = createdMsg
                        pendingConfirm = true
                    }
                }
                .onFailure { errorKind = it.toApiError().kind; status = null }
            busy = false
        }
    }

    fun confirmAndSignIn() {
        if (busy) return
        scope.launch {
            busy = true
            resendOk = false
            resendErrorKind = null
            resendMessage = signingIn
            container.auth.signIn(email.trim(), password)
                .onSuccess {
                    container.state.setSignedIn(true)
                    container.state.refreshAll()
                    resendMessage = null
                    onSuccess()
                }
                .onFailure { resendErrorKind = it.toApiError().kind; resendMessage = null }
            busy = false
        }
    }

    fun resend() {
        if (busy) return
        scope.launch {
            busy = true
            resendMessage = null
            resendErrorKind = null
            container.auth.resendConfirmation(email.trim())
                .onSuccess { resendOk = true; resendMessage = resentMsg }
                .onFailure { resendOk = false; resendMessage = resendFailedMsg }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg).imePadding()) {
        AuthSettingsHeader(onBack = onBack)
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(7.dp))
            Text(
                stringResource(R.string.auth_create_account),
                style = Spec.authTitle,
                color = AppColors.AuthText,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                stringResource(R.string.auth_signup_title),
                style = Spec.authSubtitle,
                color = AppColors.AuthMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            AuthSettingsField(
                label = stringResource(R.string.auth_username),
                value = username,
                onValueChange = { username = it; usernameError = null; status = null },
                placeholder = stringResource(R.string.auth_username_placeholder),
                helper = stringResource(R.string.auth_username_hint),
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                error = usernameError,
            )
            Spacer(Modifier.height(12.dp))
            AuthSettingsField(
                label = stringResource(R.string.auth_email),
                value = email,
                onValueChange = { email = it; emailError = null; status = null },
                placeholder = stringResource(R.string.auth_email_placeholder),
                keyboardType = androidx.compose.ui.text.input.KeyboardType.Email,
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                error = emailError ?: errorKind
                    ?.takeIf { it == ApiErrorKind.INVALID_EMAIL || it == ApiErrorKind.ALREADY_REGISTERED }
                    ?.let { apiErrorText(it) },
            )
            Spacer(Modifier.height(12.dp))
            AuthSettingsField(
                label = stringResource(R.string.auth_password),
                value = password,
                onValueChange = { password = it; passwordError = null; status = null },
                placeholder = stringResource(R.string.auth_password_placeholder),
                password = true,
                helper = stringResource(R.string.auth_password_hint),
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                error = passwordError ?: errorKind
                    ?.takeIf { it == ApiErrorKind.PASSWORD_TOO_SHORT }
                    ?.let { apiErrorText(it) },
            )
            Spacer(Modifier.height(12.dp))
            AuthSettingsField(
                label = stringResource(R.string.auth_confirm_password),
                value = confirm,
                onValueChange = { confirm = it; confirmError = null; status = null },
                placeholder = stringResource(R.string.auth_password_placeholder),
                password = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onDone = { submit() },
                error = confirmError,
            )
            val generalError = errorKind?.takeUnless {
                it == ApiErrorKind.INVALID_EMAIL || it == ApiErrorKind.ALREADY_REGISTERED || it == ApiErrorKind.PASSWORD_TOO_SHORT
            }
            if (generalError != null) {
                Spacer(Modifier.height(8.dp))
                Text(apiErrorText(generalError), style = Spec.rowSub, color = AppColors.AuthRed)
            }

            Spacer(Modifier.height(14.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.AuthSurface)
                    .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(14.dp))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    Modifier.size(36.dp).clip(CircleShape).background(AppColors.AuthSurfaceHigh),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Person, contentDescription = null, tint = AppColors.AuthChipLabel, modifier = Modifier.size(20.dp))
                }
                Text(
                    stringResource(R.string.auth_signup_subtitle),
                    style = Spec.rowSub,
                    color = AppColors.AuthChipLabel,
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                stringResource(R.string.auth_terms_note),
                style = Spec.rowSub.copy(fontSize = 11.sp, lineHeight = 16.5.sp),
                color = AppColors.AuthMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(14.dp))
            if (pendingConfirm) {
                AuthSettingsGroupCard(
                    modifier = Modifier.padding(horizontal = 0.dp),
                    radius = 14.dp,
                    background = AppColors.AuthSurface,
                    borderColor = AppColors.AuthGoldBorder,
                ) {
                    Column(
                        Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(stringResource(R.string.auth_confirm_title), style = Spec.rowTitle, color = AppColors.AuthGold)
                        Text(stringResource(R.string.auth_confirm_text), style = Spec.rowSub, color = AppColors.AuthMuted)
                        Text(email.trim(), style = Spec.rowSub, color = AppColors.AuthText)
                        AuthSettingsPrimaryButton(
                            text = stringResource(R.string.auth_confirm_retry),
                            onClick = { confirmAndSignIn() },
                            enabled = !busy,
                            loading = busy,
                        )
                        AuthSettingsPrimaryButton(
                            text = stringResource(R.string.auth_confirm_resend),
                            onClick = { resend() },
                            enabled = !busy,
                        )
                        resendMessage?.let {
                            Text(
                                it,
                                style = Spec.rowSub,
                                color = if (resendOk) AppColors.AuthGreenText else AppColors.AuthRed,
                            )
                        }
                        resendErrorKind?.let {
                            Text(apiErrorText(it), style = Spec.rowSub, color = AppColors.AuthRed)
                        }
                        Text(stringResource(R.string.auth_confirm_hint), style = Spec.rowSub, color = AppColors.AuthMuted)
                    }
                }
            } else {
                AuthSettingsPrimaryButton(
                    text = if (busy) creating else stringResource(R.string.auth_create_account),
                    onClick = { submit() },
                    enabled = !busy,
                    loading = busy,
                )
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = Spec.rowSub, color = AppColors.AuthGreenText, textAlign = TextAlign.Center)
            }
            Spacer(Modifier.height(16.dp))
        }
        AuthSettingsFooter(
            prompt = stringResource(R.string.auth_have_account),
            action = stringResource(R.string.auth_sign_in),
            onClick = onOpenLogin,
        )
    }
}

/** Password recovery result screen: same field language, preserving existing behavior. */
@Composable
fun ResetPasswordScreen(
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var password by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var passwordError by remember { mutableStateOf<String?>(null) }
    var confirmError by remember { mutableStateOf<String?>(null) }

    val tooShort = stringResource(R.string.msg_password_too_short)
    val noMatch = stringResource(R.string.msg_passwords_no_match)
    val saving = stringResource(R.string.msg_saving)
    val saved = stringResource(R.string.msg_password_saved)
    val genericError = stringResource(R.string.msg_generic_error)

    fun save() {
        passwordError = null
        confirmError = null
        when {
            password.length < 6 -> { passwordError = tooShort; return }
            password != confirm -> { confirmError = noMatch; return }
        }
        scope.launch {
            busy = true
            status = saving
            isError = false
            container.auth.updatePassword(password)
                .onSuccess { status = saved; onDone() }
                .onFailure { status = it.message ?: genericError; isError = true }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg).imePadding()) {
        com.addonstation.mobile.ui.components.AuthSettingsTopBar(
            title = stringResource(R.string.auth_new_password),
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(stringResource(R.string.auth_new_password), style = Spec.authTitle, color = AppColors.AuthText)
            AuthSettingsField(
                label = stringResource(R.string.auth_new_password),
                value = password,
                onValueChange = { password = it; passwordError = null },
                placeholder = stringResource(R.string.auth_password_placeholder),
                password = true,
                helper = stringResource(R.string.auth_password_hint),
                imeAction = androidx.compose.ui.text.input.ImeAction.Next,
                error = passwordError,
            )
            AuthSettingsField(
                label = stringResource(R.string.auth_confirm_password),
                value = confirm,
                onValueChange = { confirm = it; confirmError = null },
                placeholder = stringResource(R.string.auth_password_placeholder),
                password = true,
                imeAction = androidx.compose.ui.text.input.ImeAction.Done,
                onDone = { save() },
                error = confirmError,
            )
            status?.let {
                Text(it, style = Spec.rowSub, color = if (isError) AppColors.AuthRed else AppColors.AuthGreenText)
            }
            AuthSettingsPrimaryButton(
                text = if (busy) saving else stringResource(R.string.auth_save_password),
                onClick = { save() },
                enabled = !busy,
                loading = busy,
            )
        }
    }
}
