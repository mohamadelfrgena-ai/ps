package com.addonstation.mobile.ui.settings

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Article
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Report
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.BuildConfig
import com.addonstation.mobile.R
import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.common.ConfirmDialog
import com.addonstation.mobile.ui.common.InfoDialog
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.RowItem
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.AppPrefs
import com.addonstation.mobile.util.LocaleManager
import kotlinx.coroutines.launch

/** الشاشة الإعدادات — الأقسام الأربعة كما في وثيقة التصميم §9. */
@Composable
fun SettingsScreen(
    onLogin: () -> Unit,
    onNotifications: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by container.state.profile.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val currentLocale by container.prefs.locale.collectAsState(initial = "en")
    val dark by container.prefs.darkTheme.collectAsState(initial = true)

    var username by remember { mutableStateOf(profile?.username ?: "") }
    var usernameMessage by remember { mutableStateOf<String?>(null) }
    var dialogKey by remember { mutableStateOf<String?>(null) }
    var languageMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.username) {
        profile?.username?.let { username = it }
    }

    val signedOut = profile == null

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.nav_settings),
            walletBalance = wallet?.balance,
            onWalletClick = onOpenWallet,
            icon = Icons.Filled.Settings,
        )
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // رأس الحساب
            GlassCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    AvatarImage(url = assetUrl(container, profile?.avatarUrl), size = 54.dp, borderColor = AppColors.Green)
                    Column(Modifier.weight(1f)) {
                        Text(
                            profile?.displayName ?: profile?.username ?: stringResource(R.string.common_guest),
                            style = AppText.sectionTitle,
                            color = AppColors.Text,
                        )
                        Text(
                            container.supabase.session()?.email ?: stringResource(R.string.set_email_note),
                            style = AppText.tiny,
                            color = AppColors.Muted,
                        )
                        if (profile != null) {
                            RankBadge(role = profile!!.role, plan = profile!!.effectivePlan)
                        }
                    }
                }
            }

            // الحساب
            SectionHeader(title = stringResource(R.string.set_account), leadingEmoji = "👤")
            GlassCard(padding = 10.dp) {
                if (signedOut) {
                    Text(
                        stringResource(R.string.set_sign_in_to_edit),
                        style = AppText.bodySmall,
                        color = AppColors.Muted,
                    )
                    GradientButton(
                        text = stringResource(R.string.auth_sign_in),
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    OutlinedTextField(
                        value = username,
                        onValueChange = { username = it },
                        label = { Text(stringResource(R.string.auth_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GradientButton(
                        text = stringResource(R.string.set_save_username),
                        onClick = {
                            scope.launch {
                                container.profile.updateMyProfile(username = username)
                                    .onSuccess { usernameMessage = null }
                                    .onFailure { usernameMessage = it.message }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    usernameMessage?.let {
                        Text(it, style = AppText.tiny, color = AppColors.Red)
                    }
                }
                RowItem(
                    icon = Icons.Filled.Person,
                    title = stringResource(R.string.set_email_row),
                    subtitle = container.supabase.session()?.email ?: "—",
                )
                Text(
                    stringResource(R.string.set_email_note),
                    style = AppText.tiny,
                    color = AppColors.Dim,
                    modifier = Modifier.padding(start = 12.dp, top = 2.dp, bottom = 6.dp),
                )
            }

            // التفضيلات
            SectionHeader(title = stringResource(R.string.set_prefs), leadingEmoji = "🎛")
            GlassCard(padding = 10.dp) {
                RowItem(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(R.string.set_notifications),
                    onClick = onNotifications,
                )
                Column {
                    RowItem(
                        icon = Icons.Filled.Language,
                        title = stringResource(R.string.set_language),
                        subtitle = LocaleManager.byCode(currentLocale).let { "${it.flag} ${it.label}" },
                        onClick = { languageMenu = true },
                        trailing = { },
                    )
                    DropdownMenu(
                        expanded = languageMenu,
                        onDismissRequest = { languageMenu = false },
                        containerColor = AppColors.Elevated,
                    ) {
                        LocaleManager.supported.forEach { language ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        "${language.flag}  ${language.label}",
                                        style = AppText.bodySmall,
                                        color = if (language.code == currentLocale) AppColors.Green else AppColors.Text,
                                    )
                                },
                                onClick = {
                                    languageMenu = false
                                    scope.launch {
                                        container.prefs.setLocale(language.code)
                                        AppPrefs.writeLocale(context, language.code)
                                        // تطبيق اللغة فوراً (بدون إعادة تشغيل التطبيق)
                                        context.findActivity()?.recreate()
                                    }
                                },
                            )
                        }
                    }
                }
                RowItem(
                    icon = if (dark) Icons.Filled.DarkMode else Icons.Filled.LightMode,
                    title = stringResource(R.string.set_theme),
                    subtitle = if (dark) stringResource(R.string.set_theme_dark)
                    else stringResource(R.string.set_theme_light),
                    onClick = {
                        scope.launch {
                            container.prefs.setDarkTheme(!dark)
                            AppPrefs.writeDark(context, !dark)
                        }
                    },
                )
            }

            // الدعم
            SectionHeader(title = stringResource(R.string.set_support), leadingEmoji = "❓")
            GlassCard(padding = 10.dp) {
                RowItem(
                    icon = Icons.Filled.HelpOutline,
                    title = stringResource(R.string.set_help),
                    onClick = {
                        dialogKey = "help"
                    },
                )
                RowItem(
                    icon = Icons.Filled.Report,
                    title = stringResource(R.string.set_report_problem),
                    onClick = {
                        dialogKey = "report"
                    },
                )
                RowItem(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.set_contact),
                    onClick = {
                        dialogKey = "contact"
                    },
                )
            }

            // حول التطبيق
            SectionHeader(title = stringResource(R.string.set_about), leadingEmoji = "ℹ️")
            GlassCard(padding = 10.dp) {
                RowItem(
                    icon = Icons.Filled.Info,
                    title = stringResource(R.string.set_version),
                    subtitle = "AddonStation Mobile v${BuildConfig.VERSION_NAME}",
                )
                RowItem(
                    icon = Icons.Filled.Article,
                    title = stringResource(R.string.set_terms),
                    onClick = { dialogKey = "terms" },
                )
                RowItem(
                    icon = Icons.Filled.Lock,
                    title = stringResource(R.string.set_privacy),
                    onClick = { dialogKey = "privacy" },
                )
            }

            // منطقة الحساب
            SectionHeader(title = stringResource(R.string.set_account_area), leadingEmoji = "⚠️")
            GlassCard(padding = 12.dp, borderColor = AppColors.Red.copy(alpha = 0.35f)) {
                Text(stringResource(R.string.set_delete_note), style = AppText.tiny, color = AppColors.Muted)
                RowItem(
                    icon = Icons.Filled.Delete,
                    title = stringResource(R.string.set_delete_account),
                    onClick = { if (!signedOut) confirmDelete = true },
                    tint = AppColors.Red,
                )
                if (signedOut) {
                    GradientButton(
                        text = stringResource(R.string.auth_sign_in),
                        onClick = onLogin,
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    GhostButton(
                        text = stringResource(R.string.common_log_out),
                        onClick = {
                            scope.launch {
                                container.auth.signOut()
                                container.state.setSignedIn(false)
                                onSignedOut()
                            }
                        },
                        icon = Icons.Filled.Logout,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.height(96.dp))
        }
    }

    dialogKey?.let { key ->
        val info = when (key) {
            "help" -> stringResource(R.string.set_help) to stringResource(R.string.set_support_note_help)
            "report" -> stringResource(R.string.set_report_problem) to stringResource(R.string.set_support_note_report)
            "contact" -> stringResource(R.string.set_contact) to stringResource(R.string.set_support_note_contact)
            "terms" -> stringResource(R.string.set_terms) to stringResource(R.string.set_terms_text)
            "privacy" -> stringResource(R.string.set_privacy) to stringResource(R.string.set_privacy_text)
            "delete" -> stringResource(R.string.set_delete_account) to stringResource(R.string.set_delete_confirm_text)
            else -> key to ""
        }
        InfoDialog(title = info.first, text = info.second, onClose = { dialogKey = null })
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.set_delete_account),
            text = stringResource(R.string.set_delete_note),
            confirmText = stringResource(R.string.common_delete),
            onConfirm = {
                confirmDelete = false
                dialogKey = "delete"
            },
            onDismiss = { confirmDelete = false },
        )
    }
}

/** يصعد في شجرة الـ Context للوصول إلى Activity (لإعادة إنشاء الواجهة عند تغيير اللغة). */
fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/** قراءة/كتابة سريعة للإعدادات العامة (تُستخدم من لوحة الإدارة أيضاً). */
suspend fun readSetting(container: AppContainer, key: String): String? =
    container.admin.appSettings()[key]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.content }
