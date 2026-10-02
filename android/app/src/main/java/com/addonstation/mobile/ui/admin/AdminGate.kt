package com.addonstation.mobile.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import kotlinx.coroutines.launch

/**
 * حالة فتح لوحة الإدارة في هذه الجلسة (في الذاكرة فقط — لا تُحفظ على الجهاز).
 * تُقفل تلقائياً بعد [UNLOCK_MINUTES] دقيقة، وتُقفل فوراً عند تغيّر الحساب أو الخروج.
 */
object AdminSession {
    private const val UNLOCK_MINUTES = 15L
    private const val MAX_ATTEMPTS = 5
    private const val COOLDOWN_SECONDS = 120L

    private var unlockedUserId: String? = null
    private var unlockedAtMillis: Long = 0L
    private var failedAttempts: Int = 0
    private var blockedUntilMillis: Long = 0L

    /** يفتح اللوحة لهذا المستخدم لمدة ١٥ دقيقة. */
    fun unlock(userId: String?) {
        unlockedUserId = userId
        unlockedAtMillis = System.currentTimeMillis()
        failedAttempts = 0
        blockedUntilMillis = 0L
    }

    /** يقفل اللوحة فوراً (زر القفل أو تبديل الحساب). */
    fun lock() {
        unlockedUserId = null
        unlockedAtMillis = 0L
    }

    fun isUnlocked(userId: String?): Boolean {
        if (userId == null || unlockedUserId != userId) return false
        return System.currentTimeMillis() - unlockedAtMillis < UNLOCK_MINUTES * 60_000L
    }

    /** الثواني المتبقية قبل السماح بمحاولة جديدة (بعد تكرار الخطأ). */
    fun cooldownRemainingSeconds(): Long {
        val left = blockedUntilMillis - System.currentTimeMillis()
        return if (left > 0) left / 1000L else 0L
    }

    fun registerFailure() {
        failedAttempts += 1
        if (failedAttempts >= MAX_ATTEMPTS) {
            blockedUntilMillis = System.currentTimeMillis() + COOLDOWN_SECONDS * 1000L
            failedAttempts = 0
        }
    }
}

private enum class GateState { CHECKING, DENIED, LOCKED, OPEN }

/**
 * بوابة حماية لوحة الإدارة — ثلاثة أقفال متراكبة:
 *   ١) الحساب: يجب أن يكون مسجَّل الدخول ودوره admin (وإلا يُغلق الباب).
 *   ٢) الخادم: نداء `admin_panel_access()` الذي يتحقق من الدور في قاعدة البيانات
 *      ويُسجّل فتح اللوحة في سجلّ التدقيق — فلا ينفع التلاعب بحالة التطبيق محلياً.
 *   ٣) كلمة المرور: إعادة إدخال كلمة مرور الحساب لفتح اللوحة، وتبقى مفتوحة ١٥ دقيقة
 *      ثم تُقفل من جديد (مع إيقاف مؤقت بعد ٥ محاولات خاطئة).
 */
@Composable
fun AdminGate(onExit: () -> Unit, content: @Composable () -> Unit) {
    val container = rememberContainer()
    val profile by container.state.profile.collectAsState()
    val scope = rememberCoroutineScope()

    val myId = container.supabase.session()?.userId
    val myEmail = container.supabase.session()?.email

    var state by remember { mutableStateOf(GateState.CHECKING) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cooldown by remember { mutableLongStateOf(0L) }

    val wrongMsg = stringResource(R.string.admin_gate_wrong)
    val tooManyMsg = stringResource(R.string.admin_gate_too_many)
    val checkingMsg = stringResource(R.string.admin_gate_checking)

    // (١) و(٢): التحقق عند الدخول — الحساب محلياً ثم الخادم
    LaunchedEffect(myId, profile?.role) {
        state = GateState.CHECKING
        if (myId == null || profile == null || profile?.role != "admin") {
            state = GateState.DENIED
            return@LaunchedEffect
        }
        val allowed = container.admin.panelAccess().getOrDefault(false)
        state = when {
            !allowed -> GateState.DENIED
            AdminSession.isUnlocked(myId) -> GateState.OPEN
            else -> GateState.LOCKED
        }
    }

    when (state) {
        GateState.CHECKING -> GateMessage(
            title = checkingMsg,
            body = null,
            accent = AppColors.Muted,
            onExit = onExit,
        )

        GateState.DENIED -> GateMessage(
            title = stringResource(R.string.admin_not_allowed),
            body = stringResource(R.string.admin_gate_denied_body),
            onExit = onExit,
        )

        GateState.LOCKED -> Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = AppColors.Gold,
                modifier = Modifier.size(44.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(stringResource(R.string.admin_gate_title), style = AppText.sectionTitle, color = AppColors.Text)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.admin_gate_hint), style = AppText.bodySmall, color = AppColors.Muted)
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = password,
                onValueChange = {
                    password = it
                    error = null
                },
                singleLine = true,
                enabled = !busy && cooldown == 0L,
                visualTransformation = PasswordVisualTransformation(),
                label = { Text(stringResource(R.string.admin_gate_password), style = AppText.bodySmall) },
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
            )
            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Red)
            }
            if (cooldown > 0L) {
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.admin_gate_cooldown, cooldown),
                    style = AppText.bodySmall,
                    color = AppColors.Red,
                )
            }
            Spacer(Modifier.height(16.dp))
            GradientButton(
                text = if (busy) checkingMsg else stringResource(R.string.admin_gate_unlock),
                colors = listOf(AppColors.BlueLight, AppColors.BlueDark),
                textColor = AppColors.BlueText,
                enabled = !busy && cooldown == 0L,
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp),
                onClick = {
                    if (busy || cooldown > 0L) return@GradientButton
                    if (myEmail == null) {
                        error = wrongMsg
                        return@GradientButton
                    }
                    busy = true
                    error = null
                    scope.launch {
                        // التحقق الفعلي بكلمة المرور عبر Supabase (نفس مسار تسجيل الدخول)
                        val result = container.auth.signIn(myEmail, password)
                        busy = false
                        if (result.isSuccess) {
                            password = ""
                            AdminSession.unlock(myId)
                            state = GateState.OPEN
                        } else {
                            AdminSession.registerFailure()
                            cooldown = AdminSession.cooldownRemainingSeconds()
                            error = if (cooldown > 0L) tooManyMsg else wrongMsg
                        }
                    }
                },
            )
            Spacer(Modifier.height(10.dp))
            Text(
                stringResource(R.string.admin_gate_locked_note),
                style = AppText.tiny,
                color = AppColors.Dim,
            )
            Spacer(Modifier.height(14.dp))
            GhostButton(text = stringResource(R.string.common_back), onClick = onExit)
        }

        GateState.OPEN -> content()
    }
}

@Composable
private fun GateMessage(
    title: String,
    body: String?,
    accent: androidx.compose.ui.graphics.Color = AppColors.Red,
    onExit: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(18.dp))
                .background(AppColors.Surface)
                .padding(horizontal = 24.dp, vertical = 22.dp),
        ) {
            Icon(
                Icons.Filled.AdminPanelSettings,
                contentDescription = null,
                tint = AppColors.Blue,
                modifier = Modifier.size(38.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(title, style = AppText.sectionTitle, color = accent)
            if (body != null) {
                Spacer(Modifier.height(8.dp))
                Text(body, style = AppText.bodySmall, color = AppColors.Muted)
            }
            Spacer(Modifier.height(14.dp))
            GhostButton(text = stringResource(R.string.common_back), onClick = onExit)
        }
    }
}
