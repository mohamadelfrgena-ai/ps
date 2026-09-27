package com.addonstation.mobile.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.AppState
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.components.*
import com.addonstation.mobile.ui.theme.*
import kotlinx.coroutines.launch

@Composable
private fun AuthPage(subtitle: String, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize()) {
        Image(painterResource(R.drawable.auth_bg), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x2E071019), Color(0x9E071019)))))
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 6.dp, end = 6.dp, top = 20.dp, bottom = 110.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            LogoImage(Modifier.size(74.dp).shadow(18.dp, RoundedCornerShape(20.dp), spotColor = C.green).clip(RoundedCornerShape(20.dp)))
            Spacer(Modifier.height(12.dp))
            Text("AddonStation", style = mc(20, Color.White))
            Spacer(Modifier.height(8.dp))
            Text(subtitle, color = Color(0xFFC8D4E0), fontSize = 12.sp)
            Spacer(Modifier.height(16.dp))
            content()
        }
    }
}

@Composable
private fun FormCard(content: @Composable ColumnScope.() -> Unit) = Column(
    Modifier.fillMaxWidth().shadow(20.dp, RoundedCornerShape(18.dp))
        .card(RoundedCornerShape(18.dp), Brush.verticalGradient(listOf(Color(0xF21A2330), Color(0xF2131A24)))).padding(14.dp),
    content = content,
)

@Composable
private fun Label(t: String) = Text(t, color = C.muted, fontSize = 11.5.sp, modifier = Modifier.padding(bottom = 8.dp, top = 4.dp))

@Composable
fun LoginScreen(state: AppState, go: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthPage("Sign in to continue your adventure 🎮") {
        FormCard {
            Label("Email address")
            MField(email, { email = it }, "example@email.com", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email, bg = C.bg)
            Spacer(Modifier.height(12.dp))
            Label("Password")
            MField(pass, { pass = it }, "••••••••", Modifier.fillMaxWidth(), password = true, keyboard = KeyboardType.Password, bg = C.bg)
            ErrorText(err)
            if (info != null) Text(info!!, color = C.green, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            MButton(if (busy) "..." else "Sign in", Modifier.fillMaxWidth(), enabled = !busy) {
                busy = true; err = null
                scope.launch {
                    runCatching { state.sb.signIn(email.trim(), pass) }
                        .onSuccess { go("home") }.onFailure { err = it.message }
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        MButton("🔑 Forgot password?", Modifier.fillMaxWidth(), BtnStyle.Dark) {
            if (email.isBlank()) { err = "Enter your email first"; return@MButton }
            scope.launch {
                runCatching { state.sb.resetPassword(email.trim()) }
                    .onSuccess { info = "Password reset link sent to your email" }.onFailure { err = it.message }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = C.muted)) { append("Don't have an account? ") }
                withStyle(SpanStyle(color = C.green, fontWeight = FontWeight.Bold)) { append("Create account") }
            },
            fontSize = 12.sp, modifier = Modifier.clickable { go("register") },
        )
    }
}

@Composable
fun RegisterScreen(state: AppState, go: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AuthPage("Create your account and start building 🧱") {
        FormCard {
            Label("Username")
            MField(username, { username = it }, "Steve", Modifier.fillMaxWidth(), bg = C.bg)
            Spacer(Modifier.height(12.dp))
            Label("Email address")
            MField(email, { email = it }, "example@email.com", Modifier.fillMaxWidth(), keyboard = KeyboardType.Email, bg = C.bg)
            Spacer(Modifier.height(12.dp))
            Label("Password")
            MField(pass, { pass = it }, "••••••••", Modifier.fillMaxWidth(), password = true, keyboard = KeyboardType.Password, bg = C.bg)
            ErrorText(err)
            if (info != null) Text(info!!, color = C.green, fontSize = 12.sp)
            Spacer(Modifier.height(20.dp))
            MButton(if (busy) "..." else "Create account", Modifier.fillMaxWidth(), enabled = !busy) {
                if (username.length < 3) { err = "Username must be at least 3 characters"; return@MButton }
                if (pass.length < 6) { err = "Password must be at least 6 characters"; return@MButton }
                busy = true; err = null
                scope.launch {
                    runCatching { state.sb.signUp(email.trim(), pass, username.trim()) }
                        .onSuccess { if (it) go("home") else info = "Check your email to confirm your account" }
                        .onFailure { err = it.message }
                    busy = false
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(color = C.muted)) { append("Already have an account? ") }
                withStyle(SpanStyle(color = C.green, fontWeight = FontWeight.Bold)) { append("Sign in") }
            },
            fontSize = 12.sp, modifier = Modifier.clickable { go("login") },
        )
    }
}
