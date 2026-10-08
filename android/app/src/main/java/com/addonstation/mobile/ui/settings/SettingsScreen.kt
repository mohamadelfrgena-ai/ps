package com.addonstation.mobile.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.addonstation.mobile.BuildConfig
import com.addonstation.mobile.MainActivity
import com.addonstation.mobile.R
import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.ProfileLoadStatus
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.data.model.Profile
import com.addonstation.mobile.ui.apiErrorText
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.common.ConfirmDialog
import com.addonstation.mobile.ui.common.InfoDialog
import com.addonstation.mobile.ui.common.planLabelOf
import com.addonstation.mobile.ui.components.AuthSettingsDivider
import com.addonstation.mobile.ui.components.AuthSettingsGroupCard
import com.addonstation.mobile.ui.components.AuthSettingsIconTile
import com.addonstation.mobile.ui.components.AuthSettingsImageTile
import com.addonstation.mobile.ui.components.AuthSettingsPill
import com.addonstation.mobile.ui.components.AuthSettingsRow
import com.addonstation.mobile.ui.components.AuthSettingsSectionLabel
import com.addonstation.mobile.ui.components.AuthSettingsSegmented
import com.addonstation.mobile.ui.components.AuthSettingsSwitch
import com.addonstation.mobile.ui.components.AuthSettingsTopBar
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.components.langFlagRes
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.util.AppPrefs
import com.addonstation.mobile.util.LocaleManager
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/** بريد فريق الدعم — تبقى رسائل المساعدة والتواصل موجّهة إلى البريد نفسه. */
const val SUPPORT_EMAIL = "silverfoxinteractive@gmail.com"

/** يفتح تطبيق البريد برسالة جاهزة إلى بريد الدعم؛ يرجع false إن لم يوجد تطبيق بريد. */
fun openSupportEmail(context: Context, subject: String, body: String): Boolean {
    val uri = Uri.parse(
        "mailto:" + SUPPORT_EMAIL +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body),
    )
    val mailIntent = Intent(Intent.ACTION_SENDTO, uri)
    val candidates = listOf(Intent(mailIntent).setPackage("com.google.android.gm"), mailIntent)
    for (intent in candidates) {
        if (runCatching { context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }.isSuccess) return true
    }
    return runCatching {
        context.startActivity(Intent.createChooser(mailIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.isSuccess
}

@Composable
fun SettingsScreen(
    onLogin: () -> Unit,
    onNotifications: () -> Unit,
    onNotificationSettings: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onSignedOut: () -> Unit,
    onOpenPlans: () -> Unit = {},
    onOpenAdmin: () -> Unit = {},
    onBack: () -> Unit = {},
    scrollToTopRequest: Int = 0,
) {
    val scrollState = rememberScrollState()
    LaunchedEffect(scrollToTopRequest) {
        if (scrollToTopRequest > 0) scrollState.animateScrollTo(0)
    }
    val container = rememberContainer()
    val context = LocalContext.current
    val hostActivity = LocalView.current.context.findActivity()
    val scope = rememberCoroutineScope()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val profileLoadStatus by container.state.profileLoadStatus.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val currentLocale by container.prefs.locale.collectAsState(initial = "en")
    val dark by container.prefs.darkTheme.collectAsState(initial = true)

    val supportSubjectHelp = stringResource(R.string.set_support_mail_help)
    val supportSubjectReport = stringResource(R.string.set_support_mail_report)
    val supportSubjectContact = stringResource(R.string.set_support_mail_contact)
    val supportBody = stringResource(
        R.string.set_support_mail_body,
        BuildConfig.VERSION_NAME,
        (android.os.Build.MANUFACTURER + " " + android.os.Build.MODEL).trim(),
        "Android " + android.os.Build.VERSION.RELEASE,
        appLocaleCode(),
        profile?.username ?: profile?.displayName ?: "—",
    )

    var username by remember { mutableStateOf(profile?.username ?: "") }
    var usernameError by remember { mutableStateOf<String?>(null) }
    var usernameMessage by remember { mutableStateOf<String?>(null) }
    var profileRetryTick by remember { mutableStateOf(0) }
    var editUsername by remember { mutableStateOf(false) }
    var editPasswordBusy by remember { mutableStateOf(false) }
    var dialogKey by remember { mutableStateOf<String?>(null) }
    var dialogError by remember { mutableStateOf<ApiErrorKind?>(null) }
    var languageMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleteInfo by remember { mutableStateOf(false) }
    var onlineBusy by remember { mutableStateOf(false) }
    var onlineNote by remember { mutableStateOf<ApiErrorKind?>(null) }
    val signedOut = !signedIn

    LaunchedEffect(profile?.username) {
        profile?.username?.let { username = it }
    }
    LaunchedEffect(signedIn, profileRetryTick) {
        if (signedIn && container.state.currentProfile == null) {
            try {
                withContext(Dispatchers.IO) { container.profile.ensureProfileLoaded() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // تبقى الصفحة ظاهرة مع زرّ إعادة المحاولة أدناه.
            }
        }
    }

    fun saveUsername() {
        val clean = username.trim()
        val error = when {
            clean.length < 3 -> context.getString(R.string.msg_username_too_short)
            clean.length > 24 -> context.getString(R.string.msg_username_too_long)
            !Regex("^[A-Za-z0-9_]+$").matches(clean) -> context.getString(R.string.msg_username_invalid)
            else -> null
        }
        if (error != null) {
            usernameError = error
            return
        }
        scope.launch {
            val result = container.profile.updateMyProfile(username = clean)
            result.onSuccess {
                editUsername = false
                usernameError = null
                usernameMessage = null
            }.onFailure {
                usernameError = it.message ?: context.getString(R.string.msg_generic_error)
            }
        }
    }

    fun setOnline(value: Boolean) {
        val me = profile ?: return
        if (me.effectivePlan != "legendary") {
            if (signedIn) onOpenPlans() else onLogin()
            return
        }
        scope.launch {
            onlineBusy = true
            onlineNote = null
            container.profile.setShowOnline(value)
                .onFailure { onlineNote = it.toApiError().kind }
            onlineBusy = false
        }
    }

    fun requestPasswordReset() {
        val email = container.supabase.session()?.email
        if (email.isNullOrBlank()) {
            onLogin()
            return
        }
        scope.launch {
            editPasswordBusy = true
            dialogError = null
            container.auth.sendRecovery(email)
                .onSuccess { dialogKey = "password_sent" }
                .onFailure { dialogError = it.toApiError().kind; dialogKey = "password_error" }
            editPasswordBusy = false
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        AuthSettingsTopBar(
            title = stringResource(R.string.nav_settings),
            onBack = onBack,
            walletBalance = wallet?.balance,
            onWalletClick = onOpenWallet,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(scrollState),
        ) {
            Spacer(Modifier.height(16.dp))
            SettingsProfileCard(
                profile = profile,
                signedIn = signedIn,
                container = container,
                onClick = onOpenProfile,
            )
            SettingsPlanCard(profile = profile, onOpenPlans = onOpenPlans)

            // 1 · ACCOUNT
            AuthSettingsSectionLabel(stringResource(R.string.set_account), AppColors.AuthAccentBlue)
            AuthSettingsGroupCard {
                AuthSettingsRow(
                    title = stringResource(R.string.set_email_row),
                    subtitle = stringResource(R.string.set_email_note),
                    value = container.supabase.session()?.email ?: "—",
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_email) },
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.set_username_row),
                    subtitle = if (signedOut) stringResource(R.string.set_sign_in_to_edit)
                    else stringResource(R.string.auth_username_hint),
                    value = profile?.username ?: "—",
                    tile = { AuthSettingsIconTile(Icons.Outlined.Person, AppColors.AuthAccentBlue) },
                    trailing = {
                        AuthSettingsPill(
                            stringResource(R.string.common_edit),
                            AppColors.AuthBlueLight,
                            onClick = { if (signedOut) onLogin() else editUsername = true },
                        )
                    },
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.auth_password),
                    value = if (signedIn) stringResource(R.string.auth_password_placeholder) else "—",
                    tile = { AuthSettingsIconTile(Icons.Outlined.Lock, AppColors.AuthPurple) },
                    trailing = {
                        AuthSettingsPill(
                            stringResource(R.string.common_edit),
                            AppColors.AuthBlueLight,
                            onClick = { if (signedOut) onLogin() else requestPasswordReset() },
                        )
                    },
                )
                if ((profileLoadStatus == ProfileLoadStatus.FAILED || profileLoadStatus == ProfileLoadStatus.MISSING) && signedIn && profile == null) {
                    ErrorState(onRetry = { profileRetryTick += 1 })
                }
                usernameMessage?.let {
                    Text(it, style = Spec.rowSub, color = AppColors.AuthGreenText, modifier = Modifier.padding(horizontal = 14.dp))
                }
            }

            // 2 · PREFERENCES
            AuthSettingsSectionLabel(stringResource(R.string.set_prefs), AppColors.AuthGreen)
            AuthSettingsGroupCard {
                Box {
                    AuthSettingsRow(
                        title = stringResource(R.string.set_language),
                        value = LocaleManager.byCode(currentLocale).label,
                        clickable = true,
                        onClick = { languageMenu = true },
                        chevron = true,
                        tile = { AuthSettingsImageTile(R.drawable.ic_set_language) },
                    )
                    DropdownMenu(
                        expanded = languageMenu,
                        onDismissRequest = { languageMenu = false },
                        containerColor = AppColors.AuthSurfaceHigh,
                    ) {
                        LocaleManager.supported.forEach { language ->
                            DropdownMenuItem(
                                leadingIcon = {
                                    Image(
                                        painter = painterResource(langFlagRes(language.code)),
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp),
                                    )
                                },
                                text = {
                                    Text(
                                        language.label,
                                        color = if (language.code == currentLocale) AppColors.AuthGreenText else AppColors.AuthText,
                                    )
                                },
                                onClick = {
                                    languageMenu = false
                                    scope.launch {
                                        container.prefs.setLocale(language.code)
                                        if (hostActivity is MainActivity) {
                                            hostActivity.applyAppLocale(language.code)
                                        } else {
                                            AppPrefs.writeLocale(context, language.code)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.set_theme),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_appearance) },
                    trailing = {
                        AuthSettingsSegmented(
                            options = listOf(
                                stringResource(R.string.set_theme_dark),
                                stringResource(R.string.set_theme_light),
                            ),
                            selectedIndex = if (dark) 0 else 1,
                            onSelect = { index ->
                                val nextDark = index == 0
                                scope.launch {
                                    container.prefs.setDarkTheme(nextDark)
                                    AppPrefs.writeDark(context, nextDark)
                                }
                            },
                        )
                    },
                )
                AuthSettingsDivider()
                val canShowOnline = profile?.effectivePlan == "legendary"
                AuthSettingsRow(
                    title = stringResource(R.string.profile_online_status),
                    subtitle = stringResource(R.string.plan_locked_online),
                    clickable = true,
                    onClick = { if (canShowOnline) setOnline(!(profile?.showOnlineStatus ?: false)) else if (signedIn) onOpenPlans() else onLogin() },
                    tile = { AuthSettingsIconTile(Icons.Outlined.Visibility, AppColors.AuthGreen) },
                    trailing = {
                        AuthSettingsSwitch(
                            checked = profile?.showOnlineStatus == true,
                            enabled = canShowOnline && !onlineBusy,
                            onCheckedChange = { setOnline(it) },
                        )
                    },
                )
                onlineNote?.let {
                    Text(apiErrorText(it), style = Spec.rowSub, color = AppColors.AuthRed, modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp))
                }
            }

            // 3 · NOTIFICATIONS (تبقى قائمة الإشعارات وإعداداتها كلتاهما متاحتين)
            AuthSettingsSectionLabel(stringResource(R.string.set_notifications), AppColors.AuthPurple)
            AuthSettingsGroupCard {
                AuthSettingsRow(
                    title = stringResource(R.string.set_notifications),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_notifications) },
                    clickable = true,
                    onClick = onNotifications,
                    chevron = true,
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.notif_settings),
                    subtitle = stringResource(R.string.notif_settings_subtitle),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_notifications) },
                    clickable = true,
                    onClick = onNotificationSettings,
                    chevron = true,
                )
            }

            // 4 · SUPPORT
            AuthSettingsSectionLabel(stringResource(R.string.set_support), AppColors.AuthGold)
            AuthSettingsGroupCard {
                AuthSettingsRow(
                    title = stringResource(R.string.set_help),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_help) },
                    clickable = true,
                    onClick = {
                        if (!openSupportEmail(context, supportSubjectHelp, supportBody)) dialogKey = "no_mail_app"
                    },
                    chevron = true,
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.set_contact),
                    subtitle = stringResource(R.string.set_support_note_contact),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_contact) },
                    clickable = true,
                    onClick = {
                        if (!openSupportEmail(context, supportSubjectContact, supportBody)) dialogKey = "no_mail_app"
                    },
                    chevron = true,
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.set_report_problem),
                    subtitle = stringResource(R.string.set_support_note_report),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_report) },
                    clickable = true,
                    onClick = {
                        if (!openSupportEmail(context, supportSubjectReport, supportBody)) dialogKey = "no_mail_app"
                    },
                    chevron = true,
                )
            }

            // 5 · ABOUT
            AuthSettingsSectionLabel(stringResource(R.string.set_about), AppColors.AuthAccentBlue)
            AuthSettingsGroupCard {
                AuthSettingsRow(
                    title = stringResource(R.string.set_about),
                    tile = { AuthSettingsIconTile(Icons.Outlined.Info, AppColors.AuthAccentBlue) },
                    clickable = true,
                    onClick = { dialogKey = "about" },
                    chevron = true,
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.set_privacy),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_privacy) },
                    clickable = true,
                    onClick = { dialogKey = "privacy" },
                    chevron = true,
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.set_terms),
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_terms) },
                    clickable = true,
                    onClick = { dialogKey = "terms" },
                    chevron = true,
                )
                AuthSettingsDivider()
                AuthSettingsRow(
                    title = stringResource(R.string.set_version),
                    value = BuildConfig.VERSION_NAME,
                    tile = { AuthSettingsImageTile(R.drawable.ic_set_version) },
                )
            }

            // 6 · ADMIN TOOLS — لا تُرسم للمستخدمين غير المشرفين.
            if (profile?.role == "admin") {
                AuthSettingsSectionLabel(stringResource(R.string.settings_admin_tools), AppColors.AuthGold)
                AuthSettingsGroupCard {
                    AuthSettingsRow(
                        title = stringResource(R.string.admin_kicker_dashboard),
                        subtitle = stringResource(R.string.settings_admin_visible),
                        tile = { AuthSettingsIconTile(Icons.Outlined.Shield, AppColors.AuthGold) },
                        trailing = {
                            AuthSettingsPill(
                                stringResource(R.string.settings_open_dashboard),
                                AppColors.AuthGold,
                                onClick = onOpenAdmin,
                            )
                        },
                    )
                }
            }

            // 7 · ACCOUNT ACTIONS
            AuthSettingsSectionLabel(stringResource(R.string.settings_account_actions), AppColors.AuthRed)
            if (signedIn) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .height(56.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.AuthSurface)
                        .border(1.dp, AppColors.AuthRedBorder, RoundedCornerShape(16.dp))
                        .clickable {
                            scope.launch {
                                container.auth.signOut()
                                container.state.setSignedIn(false)
                                onSignedOut()
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, tint = AppColors.AuthRedText, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.common_log_out), style = Spec.primaryBtn, color = AppColors.AuthRedText)
                }
            } else {
                AuthSettingsGroupCard(modifier = Modifier.padding(horizontal = 0.dp)) {
                    GradientButton(
                        text = stringResource(R.string.auth_sign_in),
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.verticalGradient(listOf(AppColors.AuthRedTop, AppColors.AuthRedBottom)))
                    .border(1.dp, AppColors.AuthRedBorderStrong, RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.AuthRed.copy(alpha = 0.15f))
                            .border(1.dp, AppColors.AuthRed.copy(alpha = 0.40f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Warning, contentDescription = null, tint = AppColors.AuthRed, modifier = Modifier.size(22.dp))
                    }
                    Text(stringResource(R.string.set_delete_account), style = Spec.rowTitle.copy(fontSize = 16.sp), color = AppColors.AuthRedText)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.set_delete_note),
                    style = Spec.rowSub.copy(fontSize = 13.sp, lineHeight = 19.5.sp),
                    color = AppColors.AuthRedTextSoft,
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.AuthRedButton)
                        .clickable(enabled = signedIn) { confirmDelete = true }
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, tint = AppColors.AuthWhite, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(
                        stringResource(R.string.set_delete_account),
                        style = Spec.primaryBtn.copy(fontSize = 14.sp),
                        color = if (signedIn) AppColors.AuthWhite else AppColors.AuthWhite.copy(alpha = 0.5f),
                    )
                }
            }

            Text(
                stringResource(R.string.settings_footer),
                style = Spec.footerText,
                color = AppColors.AuthMuted,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 22.dp),
            )
            Spacer(Modifier.height(2.dp))
        }
    }

    if (editUsername) {
        AlertDialog(
            onDismissRequest = { editUsername = false; usernameError = null },
            containerColor = AppColors.AuthSurface,
            title = { Text(stringResource(R.string.set_username_row), color = AppColors.AuthText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it; usernameError = null },
                        label = { Text(stringResource(R.string.auth_username)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    )
                    usernameError?.let { Text(it, style = Spec.rowSub, color = AppColors.AuthRed) }
                }
            },
            confirmButton = {
                TextButton(onClick = { saveUsername() }) {
                    Text(stringResource(R.string.set_save_username), color = AppColors.AuthBlueLight)
                }
            },
            dismissButton = {
                TextButton(onClick = { editUsername = false; usernameError = null }) {
                    Text(stringResource(R.string.common_cancel), color = AppColors.AuthMuted)
                }
            },
        )
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.set_delete_account),
            text = stringResource(R.string.set_delete_note),
            confirmText = stringResource(R.string.common_delete),
            confirmColor = AppColors.AuthRed,
            onConfirm = { confirmDelete = false; deleteInfo = true },
            onDismiss = { confirmDelete = false },
        )
    }
    if (deleteInfo) {
        InfoDialog(
            title = stringResource(R.string.set_delete_account),
            text = stringResource(R.string.set_delete_confirm_text),
            onClose = { deleteInfo = false },
        )
    }

    dialogKey?.let { key ->
        val title: String
        val message: String
        when (key) {
            "no_mail_app" -> { title = stringResource(R.string.set_contact); message = stringResource(R.string.set_support_no_mail_app) }
            "terms" -> { title = stringResource(R.string.set_terms); message = stringResource(R.string.set_terms_text) }
            "privacy" -> { title = stringResource(R.string.set_privacy); message = stringResource(R.string.set_privacy_text) }
            "about" -> {
                title = stringResource(R.string.set_about)
                message = stringResource(R.string.app_build_stamp, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE.toString())
            }
            "password_sent" -> { title = stringResource(R.string.auth_recovery_title); message = stringResource(R.string.msg_check_email) }
            "password_error" -> { title = stringResource(R.string.auth_recovery_title); message = dialogError?.let { apiErrorText(it) } ?: stringResource(R.string.msg_generic_error) }
            else -> { title = stringResource(R.string.set_about); message = "" }
        }
        InfoDialog(title = title, text = message, onClose = { dialogKey = null; dialogError = null })
    }
}

@Composable
private fun SettingsPlanCard(profile: Profile?, onOpenPlans: () -> Unit) {
    val currentPlan = profile?.effectivePlan ?: "free"
    val planName = planLabelOf(currentPlan)
    val expiryDate = TimeFormat.expiryLabel(profile?.planExpiresAt, Locale.getDefault()).ifBlank { "—" }
    val description = if (currentPlan == "free") {
        stringResource(R.string.plan_free_features)
    } else {
        stringResource(R.string.settings_expires_on, expiryDate)
    }

    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(Brush.linearGradient(listOf(AppColors.AuthPlanTop, AppColors.AuthGoldDark)))
            .border(1.dp, AppColors.AuthGoldBorder, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(AppColors.AuthGold),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Star, contentDescription = null, tint = AppColors.AuthGoldDark, modifier = Modifier.size(22.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.settings_my_plan), style = Spec.planLabel, color = AppColors.AuthGoldSoft)
                Text(
                    stringResource(R.string.settings_your_plan, planName),
                    style = Spec.planName,
                    color = AppColors.AuthGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(description, style = Spec.rowSub, color = AppColors.AuthGoldSoft, maxLines = 2)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SettingsPlanButton(
                text = stringResource(R.string.settings_upgrade),
                primary = true,
                onClick = onOpenPlans,
                modifier = Modifier.weight(1f),
            )
            SettingsPlanButton(
                text = stringResource(R.string.plan_buy),
                primary = false,
                onClick = onOpenPlans,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun SettingsPlanButton(
    text: String,
    primary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (primary) AppColors.AuthGold else AppColors.AuthPlanButton)
            .then(if (primary) Modifier else Modifier.border(1.dp, AppColors.AuthGoldBorder, RoundedCornerShape(12.dp)))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Spec.buttonSmall,
            color = if (primary) AppColors.AuthGoldDark else AppColors.AuthGold,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SettingsProfileCard(
    profile: Profile?,
    signedIn: Boolean,
    container: AppContainer,
    onClick: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick),
    ) {
        Box(Modifier.fillMaxWidth().height(100.dp)) {
            if (profile?.bannerUrl.isNullOrBlank()) {
                Image(
                    painter = painterResource(R.drawable.login_bg),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
            } else {
                AsyncImage(
                    model = assetUrl(container, profile?.bannerUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.48f to AppColors.AuthSurface.copy(alpha = 0.20f),
                            1f to AppColors.AuthSurface,
                        ),
                    ),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .height(64.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (profile == null) {
                    com.addonstation.mobile.ui.components.AnonymousAvatarImage(
                        size = 64.dp,
                        borderColor = AppColors.AuthGold,
                        borderWidth = 3.dp,
                    )
                } else {
                    AvatarImage(
                        url = assetUrl(container, profile.avatarUrl),
                        size = 64.dp,
                        borderColor = AppColors.AuthGold,
                        borderWidth = 3.dp,
                    )
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                    Text(
                        profile?.displayName ?: profile?.username ?: if (signedIn) stringResource(R.string.common_loading) else stringResource(R.string.common_guest),
                        style = Spec.profileName.copy(fontSize = 18.sp),
                        color = AppColors.AuthText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "@${profile?.username ?: "—"}",
                            style = Spec.profileHandle.copy(fontSize = 12.sp),
                            color = AppColors.AuthMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (profile?.showOnlineStatus == true) {
                            Text(" · ", style = Spec.profileHandle.copy(fontSize = 12.sp), color = AppColors.AuthMuted)
                            Text(
                                stringResource(R.string.profile_status_online),
                                style = Spec.profileHandle.copy(fontSize = 12.sp),
                                color = AppColors.AuthGreenText,
                                maxLines = 1,
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.AuthSurfaceHigh)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        planLabelOf(profile?.effectivePlan ?: "free"),
                        style = Spec.badgeChip,
                        color = AppColors.AuthChipLabel,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** يصعد في شجرة Context للوصول إلى Activity وتحديث لغة Compose من دون إعادة إنشائه. */
fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/** قراءة إعداد عام (تبقى متاحة لبقية واجهات الإعدادات/الإدارة). */
suspend fun readSetting(container: AppContainer, key: String): String? =
    container.admin.appSettings()[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
