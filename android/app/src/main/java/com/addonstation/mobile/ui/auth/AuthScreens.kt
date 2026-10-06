package com.addonstation.mobile.ui.auth

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActionScope
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.ui.apiErrorText
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import kotlinx.coroutines.launch

/**
 * ما يقبله الخادم في اسم المستخدم (نفس القاعدة في AuthRepository):
 * حروف إنجليزية وأرقام وشرطة سفلية فقط. نتحقّق منه **قبل** الإرسال لنُظهر
 * سبباً واضحاً بدل رسالة عامّة — لأن المستخدم العربي يكتب اسمه بالعربية
 * غالباً، فكان يرى «حدث خطأ ما» بلا تفسير.
 */
private val USERNAME_RE = Regex("^[A-Za-z0-9_]+$")
private const val USERNAME_MIN = 3
private const val USERNAME_MAX = 24

/**
 * حقل كلمة مرور **بعين 👁 داخل الحقل**: لمسة واحدة تُظهر كلمة المرور كما
 * كتبها المستخدم وتُخفيها ثانيةً.
 *
 * لماذا داخل الحقل؟ لأن المستخدم — خاصّة على الجوال وبكتابة عربية/لاتينية
 * مختلطة — ينسى هل كتب الحرف كبيراً أم صغيراً، فكان يضطر للحذف وإعادة
 * الكتابة. العين تمنع ذلك، وتظهر في **كل** شاشات كلمة المرور: الدخول،
 * إنشاء الحساب (الحقل وحقل التأكيد)، وكلمة المرور الجديدة.
 */
@Composable
private fun PasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    imeAction: ImeAction = ImeAction.Done,
    supportingText: String? = null,
    onDone: (KeyboardActionScope.() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visible by remember { mutableStateOf(false) }
    val hint = supportingText
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = AppText.bodySmall) },
        supportingText = hint?.let { text ->
            @Composable { Text(text, style = AppText.tiny, color = AppColors.Muted) }
        },
        keyboardActions = KeyboardActions(onDone = onDone),
        leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = AppColors.Muted) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(
                    imageVector = if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = stringResource(
                        if (visible) R.string.auth_hide_password else R.string.auth_show_password,
                    ),
                    tint = if (visible) AppColors.Green else AppColors.Muted,
                )
            }
        },
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = imeAction),
        modifier = modifier.fillMaxWidth(),
    )
}

/** تسجيل الدخول — كما في الوثيقة §7: العنوان، الحقلان، «نسيت كلمة المرور؟»، ورابط إنشاء حساب. */
@Composable
fun LoginScreen(
    onBack: () -> Unit,
    onSuccess: () -> Unit,
    onOpenSignup: () -> Unit,
    /**
     * سطر توضيحي أعلى الشاشة يُبيّن سبب فتحها — مثلاً حين يُنقل المستخدم
     * إلى الدخول لأنه حاول فتح الشات (الشات للمسجّلين فقط).
     */
    notice: String? = null,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var errorKind by remember { mutableStateOf<ApiErrorKind?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showRecovery by remember { mutableStateOf(false) }
    var recoveryEmail by remember { mutableStateOf("") }
    var recoveryStatus by remember { mutableStateOf<String?>(null) }

    val enterBoth = stringResource(R.string.msg_enter_email_password)
    val signingIn = stringResource(R.string.msg_signing_in)
    val checkEmail = stringResource(R.string.msg_check_email)
    val enterEmailFirst = stringResource(R.string.msg_enter_email_first)
    val sending = stringResource(R.string.msg_sending)

    // عملية دخول واحدة يستدعيها كلاهما: زر «تسجيل الدخول» وزر «تم» ✓
    // على لوحة المفاتيح — فلا يضطر المستخدم للمس الشاشة مرّتين.
    fun submit() {
        if (email.isBlank() || password.isBlank()) {
            status = enterBoth
            return
        }
        scope.launch {
            busy = true
            status = signingIn
            errorKind = null
            container.auth.signIn(email, password)
                .onSuccess {
                    container.state.setSignedIn(true)
                    container.state.refreshAll()
                    status = null
                    onSuccess()
                }
                .onFailure { e ->
                    status = null
                    errorKind = e.toApiError().kind
                }
            busy = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.auth_sign_in), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(18.dp))
            // صورة البروفايل الافتراضية (صورة المالك) قبل تسجيل الدخول
            com.addonstation.mobile.ui.components.DefaultProfileAvatar(size = 92.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.brand_wordmark), style = AppText.pageTitle, color = AppColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.auth_login_subtitle),
                style = AppText.bodySmall,
                color = AppColors.Muted,
                textAlign = TextAlign.Center,
            )
            if (notice != null) {
                // لماذا فُتحت شاشة الدخول؟ (مثال: الشات لا يُفتح لغير مسجّل)
                Spacer(Modifier.height(10.dp))
                Text(
                    "🔒 $notice",
                    style = AppText.bodySmall,
                    color = AppColors.Gold,
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(Modifier.height(22.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email)) },
                leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null, tint = AppColors.Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.auth_password),
                onDone = { submit() },
            )

            errorKind?.let {
                Spacer(Modifier.height(8.dp))
                Text(apiErrorText(it), style = AppText.bodySmall, color = AppColors.Red)
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Green)
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = "🔑 " + stringResource(R.string.auth_sign_in),
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.auth_forgot),
                style = AppText.bodySmall,
                color = AppColors.Blue,
                modifier = Modifier.clickable { showRecovery = !showRecovery },
            )

            if (showRecovery) {
                Spacer(Modifier.height(14.dp))
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Surface)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(stringResource(R.string.auth_recovery_title), style = AppText.sectionTitle, color = AppColors.Text)
                    Text(stringResource(R.string.auth_recovery_text), style = AppText.bodySmall, color = AppColors.Muted)
                    OutlinedTextField(
                        value = recoveryEmail,
                        onValueChange = { recoveryEmail = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                        label = { Text(stringResource(R.string.auth_email)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GradientButton(
                        text = stringResource(R.string.auth_send_recovery),
                        onClick = {
                            if (recoveryEmail.isBlank()) {
                                recoveryStatus = enterEmailFirst
                                return@GradientButton
                            }
                            scope.launch {
                                recoveryStatus = sending
                                container.auth.sendRecovery(recoveryEmail)
                                    .onSuccess { recoveryStatus = checkEmail }
                                    .onFailure { recoveryStatus = null; errorKind = it.toApiError().kind }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    recoveryStatus?.let {
                        Text(it, style = AppText.bodySmall, color = AppColors.Green)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.auth_no_account), style = AppText.bodySmall, color = AppColors.Muted)
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.auth_create_account),
                    style = AppText.bodySmall,
                    color = AppColors.Green,
                    modifier = Modifier.clickable { onOpenSignup() },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/** إنشاء حساب — الوثيقة §7: اسم المستخدم (٣ أحرف+)، البريد، كلمة المرور (٦+). */
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
    var status by remember { mutableStateOf<String?>(null) }
    var errorKind by remember { mutableStateOf<ApiErrorKind?>(null) }
    var busy by remember { mutableStateOf(false) }

    val creating = stringResource(R.string.msg_creating_account)
    val shortUsername = stringResource(R.string.msg_username_too_short)
    val shortPassword = stringResource(R.string.msg_password_too_short)
    val noMatch = stringResource(R.string.msg_passwords_no_match)
    val invalidUsername = stringResource(R.string.msg_username_invalid)
    val longUsername = stringResource(R.string.msg_username_too_long)
    val invalidEmail = stringResource(R.string.msg_invalid_email)
    val createdMsg = stringResource(R.string.msg_account_created)

    // عملية إنشاء واحدة: من زر «إنشاء حساب» أو من زر «تم» ✓ بلوحة المفاتيح.
    fun submit() {
        val name = username.trim()
        when {
            name.length < USERNAME_MIN -> status = shortUsername
            name.length > USERNAME_MAX -> status = longUsername
            !USERNAME_RE.matches(name) -> status = invalidUsername
            !android.util.Patterns.EMAIL_ADDRESS.matcher(email.trim()).matches() -> status = invalidEmail
            password.length < 6 -> status = shortPassword
            password != confirm -> status = noMatch
            else -> scope.launch {
                busy = true
                errorKind = null
                status = creating
                container.auth.signUp(username, email, password)
                    .onSuccess {
                        if (container.supabase.session() != null) {
                            container.state.setSignedIn(true)
                            container.state.refreshAll()
                            status = null
                            onSuccess()
                        } else {
                            // تأكيد البريد مفعّل: لا جلسة حتى يفتح المستخدم الرابط
                            status = createdMsg
                        }
                    }
                    .onFailure { e ->
                        status = null
                        errorKind = e.toApiError().kind
                    }
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.auth_create_account), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(16.dp))
            // صورة البروفايل الافتراضية (صورة المالك) قبل إنشاء الحساب
            com.addonstation.mobile.ui.components.DefaultProfileAvatar(size = 92.dp)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.auth_signup_title), style = AppText.pageTitle, color = AppColors.Text)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(R.string.auth_signup_subtitle),
                style = AppText.bodySmall,
                color = AppColors.Muted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Next),
                label = { Text(stringResource(R.string.auth_username)) },
                leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null, tint = AppColors.Muted) },
                supportingText = {
                    Text(
                        stringResource(R.string.auth_username_hint),
                        style = AppText.tiny,
                        color = AppColors.Muted,
                    )
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text(stringResource(R.string.auth_email)) },
                leadingIcon = { Icon(Icons.Filled.Mail, contentDescription = null, tint = AppColors.Muted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(10.dp))
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.auth_password),
                imeAction = ImeAction.Next,
                supportingText = stringResource(R.string.auth_password_hint),
            )
            Spacer(Modifier.height(10.dp))
            // **حقل التأكيد**: إن اختلف الحقلان لا نُنشئ الحساب ونُظهر
            // السبب، بدل أن يكتشف المستخدم الخطأ بعد التسجيل.
            PasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.auth_confirm_password),
                onDone = { submit() },
            )

            errorKind?.let {
                Spacer(Modifier.height(8.dp))
                Text(apiErrorText(it), style = AppText.bodySmall, color = AppColors.Red)
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Green, textAlign = TextAlign.Center)
            }

            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = stringResource(R.string.auth_create_account),
                onClick = { submit() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.auth_terms_note),
                style = AppText.tiny,
                color = AppColors.Dim,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.auth_have_account), style = AppText.bodySmall, color = AppColors.Muted)
                Spacer(Modifier.size(6.dp))
                Text(
                    stringResource(R.string.auth_sign_in),
                    style = AppText.bodySmall,
                    color = AppColors.Green,
                    modifier = Modifier.clickable { onOpenLogin() },
                )
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}

/**
 * شاشة كلمة المرور الجديدة — تُفتح بعد رابط الاستعادة addonstation://recovery
 * (الرسائل والأخطاء كما في الوثيقة §7).
 */
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

    val tooShort = stringResource(R.string.msg_password_too_short)
    val noMatch = stringResource(R.string.msg_passwords_no_match)
    val saving = stringResource(R.string.msg_saving)
    val saved = stringResource(R.string.msg_password_saved)

    // حفظ كلمة المرور: من زر «حفظ» أو من «تم» ✓ في لوحة المفاتيح.
    fun save() {
        when {
            password.length < 6 -> {
                status = tooShort; isError = true
            }
            password != confirm -> {
                status = noMatch; isError = true
            }
            else -> scope.launch {
                busy = true
                status = saving
                container.auth.updatePassword(password)
                    .onSuccess {
                        status = saved
                        isError = false
                        onDone()
                    }
                    .onFailure {
                        status = it.message
                        isError = true
                    }
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.auth_new_password), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text("🔑", style = AppText.pageTitle)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.auth_new_password),
                style = AppText.pageTitle,
                color = AppColors.Text,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            PasswordField(
                value = password,
                onValueChange = { password = it },
                label = stringResource(R.string.auth_new_password),
                imeAction = ImeAction.Next,
                supportingText = stringResource(R.string.auth_password_hint),
            )
            Spacer(Modifier.height(10.dp))
            PasswordField(
                value = confirm,
                onValueChange = { confirm = it },
                label = stringResource(R.string.auth_confirm_password),
                onDone = { save() },
            )
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = if (isError) AppColors.Red else AppColors.Green)
            }
            Spacer(Modifier.height(18.dp))
            GradientButton(
                text = stringResource(R.string.auth_save_password),
                onClick = { save() },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(24.dp))
            GhostButton(
                text = stringResource(R.string.auth_sign_in),
                onClick = onDone,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(40.dp))
        }
    }
}
