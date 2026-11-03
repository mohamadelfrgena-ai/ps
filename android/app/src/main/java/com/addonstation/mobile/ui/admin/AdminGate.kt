package com.addonstation.mobile.ui.admin

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.ui.theme.NunitoFont
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.ui.theme.TajawalFont
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * In-memory admin unlock state. Credentials and all authorization remain checked by the existing
 * auth/RPC flow; this object only tracks the local 15-minute UI lock window.
 */
object AdminSession {
    private const val UNLOCK_MINUTES = 15L
    private const val MAX_ATTEMPTS = 5
    private const val COOLDOWN_SECONDS = 120L

    private var unlockedUserId: String? = null
    private var unlockedAtMillis: Long = 0L
    private var failedAttempts: Int = 0
    private var blockedUntilMillis: Long = 0L

    fun unlock(userId: String?) {
        unlockedUserId = userId
        unlockedAtMillis = System.currentTimeMillis()
        failedAttempts = 0
        blockedUntilMillis = 0L
    }

    fun lock() {
        unlockedUserId = null
        unlockedAtMillis = 0L
    }

    fun isUnlocked(userId: String?): Boolean {
        if (userId == null || unlockedUserId != userId) return false
        return System.currentTimeMillis() - unlockedAtMillis < UNLOCK_MINUTES * 60_000L
    }

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

/** Password gate shown before admin content; the existing server access check is unchanged. */
@Composable
fun AdminGate(
    onExit: () -> Unit,
    showAdminBottomBar: Boolean = false,
    selectedAdminTab: Int? = null,
    onSelectAdminTab: (Int) -> Unit = {},
    content: @Composable () -> Unit,
) {
    val container = rememberContainer()
    val adminStats by container.state.adminStats.collectAsState()
    val palette = LocalHomePalette.current
    val gateError = gateErrorColor(palette)
    val gateWarning = gateWarningColor(palette)
    val profile by container.state.profile.collectAsState()
    val scope = rememberCoroutineScope()
    val myId = container.supabase.session()?.userId
    val myEmail = container.supabase.session()?.email

    var state by remember { mutableStateOf(GateState.CHECKING) }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var cooldown by remember { mutableLongStateOf(0L) }
    var visible by remember { mutableStateOf(false) }

    val wrongMsg = stringResource(R.string.admin_gate_wrong)
    val tooManyMsg = stringResource(R.string.admin_gate_too_many)
    val checkingMsg = stringResource(R.string.admin_gate_checking)
    val deniedMsg = stringResource(R.string.admin_gate_denied_body)

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

    // The countdown updates once per second and remains visible to assistive technology.
    LaunchedEffect(cooldown) {
        if (cooldown > 0L) {
            delay(1_000L)
            cooldown = (cooldown - 1L).coerceAtLeast(0L)
        }
    }

    // Re-lock an already-open route as soon as its 15-minute session expires.
    LaunchedEffect(state, myId) {
        if (state == GateState.OPEN) {
            while (true) {
                delay(1_000L)
                if (!AdminSession.isUnlocked(myId)) {
                    password = ""
                    state = GateState.LOCKED
                    break
                }
            }
        }
    }

    fun verify() {
        if (busy || cooldown > 0L) return
        val email = myEmail
        if (email == null) {
            error = wrongMsg
            return
        }
        busy = true
        error = null
        scope.launch {
            // Reuse the current account's password verification path; do not sign out or change roles.
            val result = container.auth.signIn(email, password)
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
    }

    when (state) {
        GateState.CHECKING -> GateMessage(
            title = checkingMsg,
            body = null,
            onExit = onExit,
            palette = palette,
            checking = true,
        )

        GateState.DENIED -> GateMessage(
            title = stringResource(R.string.admin_not_allowed),
            body = deniedMsg,
            onExit = onExit,
            palette = palette,
        )

        GateState.LOCKED -> Column(
            Modifier.fillMaxSize().background(palette.bg),
        ) {
            AdminGateHeader(onExit = onExit, palette = palette)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Box(
                    Modifier
                        .size(96.dp)
                        .clip(CircleShape)
                        .background(palette.goldFill.copy(alpha = 0.14f))
                        .border(1.dp, palette.goldFill.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = palette.goldText, modifier = Modifier.size(48.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(R.string.admin_gate_title),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 26.sp),
                    color = palette.text,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.admin_gate_hint),
                    style = TextStyle(fontSize = 13.sp, lineHeight = 20.8.sp, fontWeight = FontWeight.Medium),
                    color = palette.textMuted,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.widthIn(max = 360.dp),
                )
                Spacer(Modifier.height(18.dp))
                AdminPasswordField(
                    value = password,
                    onValueChange = {
                        password = it
                        error = null
                    },
                    enabled = !busy && cooldown == 0L,
                    visible = visible,
                    onToggleVisibility = { visible = !visible },
                    onDone = { verify() },
                    palette = palette,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                )
                error?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        it,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = if (cooldown > 0L) gateWarning else gateError,
                        textAlign = TextAlign.Center,
                    )
                }
                if (cooldown > 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        stringResource(R.string.admin_gate_cooldown, cooldown.toInt()),
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        color = gateWarning,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                GatePrimaryButton(
                    text = if (busy) checkingMsg else stringResource(R.string.admin_gate_unlock),
                    enabled = !busy && cooldown == 0L,
                    busy = busy,
                    palette = palette,
                    onClick = { verify() },
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 420.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Filled.Schedule, contentDescription = null, tint = palette.textMuted, modifier = Modifier.size(14.dp))
                    Text(
                        stringResource(R.string.admin_gate_locked_note),
                        style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
                        color = palette.textMuted,
                    )
                }
            }
        }

        GateState.OPEN -> if (showAdminBottomBar) {
            Column(Modifier.fillMaxSize().background(palette.bg)) {
                Box(Modifier.weight(1f)) { content() }
                AdminBottomBar(
                    selectedTab = selectedAdminTab,
                    paymentsBadge = adminStats?.pendingOrders ?: 0,
                    chatBadge = adminStats?.openReports ?: 0,
                    onSelect = onSelectAdminTab,
                    palette = palette,
                )
            }
        } else content()
    }
}

@Composable
private fun AdminGateHeader(onExit: () -> Unit, palette: HomePalette) {
    val isArabic = appLocaleCode().equals("ar", ignoreCase = true)
    val font = if (isArabic) TajawalFont else NunitoFont
    Row(
        Modifier
            .fillMaxWidth()
            .height(56.dp)
            .background(palette.barSurface)
            .border(1.dp, palette.border)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(palette.surface)
                .border(1.dp, palette.border, CircleShape)
                .clickable(onClick = onExit),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back), tint = palette.text, modifier = Modifier.size(20.dp))
        }
        Text(
            stringResource(R.string.admin_kicker_dashboard),
            style = TextStyle(
                fontFamily = if (isArabic) font else PixelFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = if (isArabic) 0.sp else 1.sp,
            ),
            color = palette.goldText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AdminPasswordField(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    visible: Boolean,
    onToggleVisibility: () -> Unit,
    onDone: () -> Unit,
    palette: HomePalette,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            enabled = enabled,
            visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDone() }),
            textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = palette.text),
            cursorBrush = SolidColor(palette.blue),
            modifier = Modifier.weight(1f),
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(
                            stringResource(R.string.admin_gate_password),
                            style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
                            color = palette.textMuted,
                            maxLines = 1,
                        )
                    }
                    inner()
                }
            },
        )
        IconButton(onClick = onToggleVisibility, enabled = enabled, modifier = Modifier.size(44.dp)) {
            Icon(
                if (visible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                contentDescription = stringResource(if (visible) R.string.cd_hide_password else R.string.cd_show_password),
                tint = if (visible) palette.blueLink else palette.textMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun GatePrimaryButton(
    text: String,
    enabled: Boolean,
    busy: Boolean,
    palette: HomePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .height(56.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                if (enabled) Brush.linearGradient(listOf(palette.blue, palette.accentBlue))
                else Brush.linearGradient(listOf(palette.surfaceHigh, palette.surfaceHigh)),
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (busy) {
            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
        }
        Text(text, style = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.ExtraBold), color = if (enabled) Color.White else palette.textMuted, maxLines = 1)
    }
}

@Composable
private fun GateMessage(
    title: String,
    body: String?,
    onExit: () -> Unit,
    palette: HomePalette,
    checking: Boolean = false,
) {
    Column(Modifier.fillMaxSize().background(palette.bg)) {
        AdminGateHeader(onExit = onExit, palette = palette)
        Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .widthIn(max = 380.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(20.dp))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    Modifier
                        .size(88.dp)
                        .clip(CircleShape)
                        .background(if (checking) palette.surfaceHigh else gateErrorColor(palette).copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (checking) {
                        CircularProgressIndicator(color = palette.blue, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
                    } else {
                        Icon(Icons.Filled.AdminPanelSettings, contentDescription = null, tint = gateErrorColor(palette), modifier = Modifier.size(42.dp))
                    }
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    title,
                    style = TextStyle(fontSize = 18.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (checking) palette.text else gateErrorColor(palette),
                    textAlign = TextAlign.Center,
                )
                if (body != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        body,
                        style = TextStyle(fontSize = 13.sp, lineHeight = 20.sp),
                        color = palette.textMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                if (!checking) {
                    Spacer(Modifier.height(18.dp))
                    GateOutlineButton(
                        text = stringResource(R.string.common_cancel),
                        palette = palette,
                        onClick = onExit,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun GateOutlineButton(
    text: String,
    palette: HomePalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(44.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color.Transparent)
            .border(1.dp, palette.border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold), color = palette.textMuted)
    }
}

private fun gateWarningColor(palette: HomePalette): Color =
    if (palette.isDark) Color(0xFFF0A04B) else Color(0xFFB25E00)

private fun gateErrorColor(palette: HomePalette): Color =
    if (palette.isDark) Color(0xFFFF6B78) else Color(0xFFB3262E)
