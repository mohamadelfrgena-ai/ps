package com.addonstation.app.ui.screens

import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavHostController
import com.addonstation.app.AppContainer
import com.addonstation.app.LocalApp
import com.addonstation.app.PendingRecovery
import com.addonstation.app.R
import com.addonstation.app.appViewModel
import com.addonstation.app.core.Constants
import com.addonstation.app.core.SiteAsset
import com.addonstation.app.ui.components.AsImage
import com.addonstation.app.ui.components.Avatar
import com.addonstation.app.ui.navigation.AppViewModel
import com.addonstation.app.ui.navigation.Routes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

fun authErrorText(code: String?, ctx: Context): String = when (code) {
    "AUTH_INVALID" -> ctx.getString(R.string.auth_errorcredentials)
    "AUTH_UNCONFIRMED" -> ctx.getString(R.string.auth_erroremailnotconfirmed)
    "AUTH_EXISTS" -> ctx.getString(R.string.auth_erroralreadyregistered)
    "AUTH_PASS_LEN" -> ctx.getString(R.string.auth_errorpasswordlength)
    "AUTH_NETWORK" -> ctx.getString(R.string.auth_errornetwork)
    else -> code ?: ctx.getString(R.string.auth_errorgeneric)
}

/** Auth shell: background image + centered card (same look as the web auth pages). */
@Composable
fun AuthScreen(nav: NavHostController, vm: AppViewModel, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        AsImage(
            model = Constants.AUTH_BG,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Crop
        )
        Box(
            Modifier.fillMaxSize().padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                    content()
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Login
// ---------------------------------------------------------------------------

class LoginViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(val busy: Boolean = false, val error: String? = null)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun submit(email: String, pass: String, onOk: () -> Unit) {
        viewModelScope.launch {
            _ui.value = Ui(busy = true)
            val r = app.auth.signIn(email, pass)
            if (r.isSuccess) onOk()
            else _ui.value = Ui(busy = false, error = r.exceptionOrNull()?.message)
        }
    }
}

@Composable
fun LoginScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val lvm: LoginViewModel = appViewModel { LoginViewModel(app) }
    val ui by lvm.ui.collectAsState()
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }

    Text(stringResource(R.string.auth_kicker), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
    Text(stringResource(R.string.auth_login_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.auth_login_lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(14.dp))
    OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text(stringResource(R.string.auth_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = pass, onValueChange = { pass = it },
        label = { Text(stringResource(R.string.auth_password)) }, singleLine = true,
        visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = { TextButton(onClick = { show = !show }) { Text(if (show) "🙈" else "👁") } },
        modifier = Modifier.fillMaxWidth()
    )
    ui.error?.let {
        Spacer(Modifier.height(6.dp))
        Text(authErrorText(it, ctx), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(12.dp))
    Button(
        onClick = { lvm.submit(email, pass) { nav.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } } },
        enabled = email.contains("@") && pass.length >= 6 && !ui.busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(stringResource(R.string.auth_login_submit))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        TextButton(onClick = { nav.navigate(Routes.FORGOT) }) { Text(stringResource(R.string.auth_forgottext).take(20) + "…") }
        TextButton(onClick = { nav.navigate(Routes.REGISTER) }) { Text(stringResource(R.string.auth_registerlink)) }
    }
    Text(
        "${stringResource(R.string.auth_login_switch)} ${stringResource(R.string.auth_registerlink)}",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

// ---------------------------------------------------------------------------
// Register
// ---------------------------------------------------------------------------

class RegisterViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(
        val busy: Boolean = false,
        val error: String? = null,
        val avatars: List<SiteAsset> = emptyList(),
        val donePendingEmail: Boolean = false
    )

    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                _ui.value = _ui.value.copy(avatars = app.assets.all().filter { it.assetType == "avatar" })
            } catch (_: Exception) { }
        }
    }

    fun submit(email: String, pass: String, username: String, avatar: String, onImmediate: () -> Unit) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(busy = true, error = null)
            val r = app.auth.signUp(email, pass, username)
            if (r.isFailure) {
                _ui.value = _ui.value.copy(busy = false, error = r.exceptionOrNull()?.message)
                return@launch
            }
            if (r.getOrNull() == true) {
                try {
                    app.auth.currentUserId()?.let { app.profiles.update(it, mapOf("avatar_url" to avatar)) }
                    app.auth.refreshProfile()
                } catch (_: Exception) { }
                onImmediate()
            } else {
                _ui.value = _ui.value.copy(busy = false, donePendingEmail = true)
            }
        }
    }
}

@Composable
fun RegisterScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val rvm: RegisterViewModel = appViewModel { RegisterViewModel(app) }
    val ui by rvm.ui.collectAsState()
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf("/imgs/profiles/onefree.png") }
    var agree by remember { mutableStateOf(false) }

    if (ui.donePendingEmail) {
        Text("📧", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.auth_accountcreated), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(12.dp))
        Button(onClick = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.REGISTER) { inclusive = true } } }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.auth_loginlink))
        }
        return
    }
    Text(stringResource(R.string.auth_jointitle), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.auth_joindesc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(value = username, onValueChange = { username = it.trim() }, label = { Text(stringResource(R.string.auth_username)) }, placeholder = { Text(stringResource(R.string.auth_usernameplaceholder)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text(stringResource(R.string.auth_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text(stringResource(R.string.auth_password)) }, placeholder = { Text(stringResource(R.string.auth_passwordplaceholder)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = pass2, onValueChange = { pass2 = it }, label = { Text(stringResource(R.string.auth_repeatpassword)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(10.dp))
    Text(stringResource(R.string.auth_picker_title), fontWeight = FontWeight.Bold)
    Text(stringResource(R.string.auth_register_lead), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    LazyVerticalGrid(columns = GridCells.Fixed(4), modifier = Modifier.height(120.dp), userScrollEnabled = false) {
        item {
            AvatarPickMini(url = "/imgs/profiles/onefree.png", selected = avatar == "/imgs/profiles/onefree.png") {
                avatar = "/imgs/profiles/onefree.png"
            }
        }
        items(ui.avatars.take(7)) { a ->
            AvatarPickMini(url = a.fileUrl, selected = avatar == a.fileUrl) { avatar = a.fileUrl }
        }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = agree, onCheckedChange = { agree = it })
        Text(stringResource(R.string.auth_termsagreement), style = MaterialTheme.typography.bodySmall)
    }
    ui.error?.let {
        Text(authErrorText(it, ctx), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    val mismatch = pass2.isNotEmpty() && pass != pass2
    if (mismatch) Text(stringResource(R.string.auth_passwordmismatch), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    Spacer(Modifier.height(8.dp))
    Button(
        onClick = {
            rvm.submit(email, pass, username, avatar) {
                nav.navigate(Routes.HOME) { popUpTo(Routes.REGISTER) { inclusive = true } }
            }
        },
        enabled = username.length >= 3 && email.contains("@") && pass.length >= 6 && !mismatch && agree && !ui.busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(stringResource(R.string.auth_register_submit))
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        TextButton(onClick = { nav.navigate(Routes.LOGIN) { popUpTo(Routes.REGISTER) { inclusive = true } } }) {
            Text("${stringResource(R.string.auth_register_switch)} ${stringResource(R.string.auth_loginlink)}")
        }
    }
}

@Composable
private fun AvatarPickMini(url: String, selected: Boolean, onPick: () -> Unit) {
    Box(
        Modifier.padding(4.dp).clickable { onPick() },
        contentAlignment = Alignment.Center
    ) {
        Avatar(url = url, name = "?", size = 52.dp)
        if (selected) Text("✓", color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.TopEnd))
    }
}

// ---------------------------------------------------------------------------
// Forgot + reset
// ---------------------------------------------------------------------------

class ForgotViewModel(private val app: AppContainer) : ViewModel() {
    data class Ui(val busy: Boolean = false, val sent: Boolean = false, val error: String? = null)
    private val _ui = MutableStateFlow(Ui())
    val ui: StateFlow<Ui> = _ui.asStateFlow()

    fun submit(email: String) {
        viewModelScope.launch {
            _ui.value = Ui(busy = true)
            val r = app.auth.recover(email)
            _ui.value = if (r.isSuccess) Ui(sent = true) else Ui(error = r.exceptionOrNull()?.message)
        }
    }
}

@Composable
fun ForgotPasswordScreen(nav: NavHostController, vm: AppViewModel) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    val fvm: ForgotViewModel = appViewModel { ForgotViewModel(app) }
    val ui by fvm.ui.collectAsState()
    var email by remember { mutableStateOf("") }
    Text(stringResource(R.string.auth_login_title), style = MaterialTheme.typography.headlineSmall)
    Text(stringResource(R.string.auth_forgottext), style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(12.dp))
    if (ui.sent) {
        Text("✅ ${stringResource(R.string.auth_checkemail)}")
    } else {
        OutlinedTextField(value = email, onValueChange = { email = it.trim() }, label = { Text(stringResource(R.string.auth_email)) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        ui.error?.let {
            Text(authErrorText(it, ctx), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(10.dp))
        Button(onClick = { fvm.submit(email) }, enabled = email.contains("@") && !ui.busy, modifier = Modifier.fillMaxWidth()) {
            if (ui.busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.auth_sending).let { stringResource(R.string.common_ok) })
        }
    }
    TextButton(onClick = { nav.popBackStack() }) { Text(stringResource(R.string.auth_close)) }
}

@Composable
fun ResetPasswordScreen(nav: NavHostController, vm: AppViewModel, pending: PendingRecovery?) {
    val app = LocalApp.current
    val ctx = LocalContext.current
    var pass by remember { mutableStateOf("") }
    var pass2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }
    val scope = remember { kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main) }

    Text(stringResource(R.string.auth_newpasswordlabel), style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(10.dp))
    if (pending == null) {
        Text(stringResource(R.string.auth_errorgeneric))
        TextButton(onClick = { nav.navigate(Routes.LOGIN) }) { Text(stringResource(R.string.auth_loginlink)) }
        return
    }
    if (done) {
        Text("✅ ${stringResource(R.string.auth_passwordsaved)}")
        return
    }
    OutlinedTextField(value = pass, onValueChange = { pass = it }, label = { Text(stringResource(R.string.auth_newpasswordlabel)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(value = pass2, onValueChange = { pass2 = it }, label = { Text(stringResource(R.string.auth_repeatpassword)) }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth())
    error?.let {
        Text(authErrorText(it, ctx), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            busy = true
            scope.launch {
                val r = app.auth.completeRecovery(pending.accessToken, pending.refreshToken, pass)
                busy = false
                if (r.isSuccess) {
                    done = true
                    app.pendingRecovery = null
                    kotlinx.coroutines.delay(1200)
                    nav.navigate(Routes.HOME) { popUpTo(Routes.RESET_PASSWORD) { inclusive = true } }
                } else error = r.exceptionOrNull()?.message
            }
        },
        enabled = pass.length >= 6 && pass == pass2 && !busy,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        else Text(stringResource(R.string.common_save))
    }
}
