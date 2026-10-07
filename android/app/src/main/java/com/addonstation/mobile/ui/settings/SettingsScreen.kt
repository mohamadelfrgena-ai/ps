package com.addonstation.mobile.ui.settings

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.BuildConfig
import com.addonstation.mobile.R
import com.addonstation.mobile.data.AppContainer
import com.addonstation.mobile.data.ProfileLoadStatus
import com.addonstation.mobile.ui.assetUrl
import com.addonstation.mobile.ui.common.ConfirmDialog
import com.addonstation.mobile.ui.common.InfoDialog
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.components.AvatarImage
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.RankBadge
import com.addonstation.mobile.ui.components.RowItem
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.langFlagRes
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.AppPrefs
import com.addonstation.mobile.util.LocaleManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** بريد فريق الدعم — تُفتح عليه رسالة المستخدم تلقائياً من قسم الدعم. */
const val SUPPORT_EMAIL = "silverfoxinteractive@gmail.com"

/**
 * يفتح تطبيق البريد برسالة جاهزة إلى بريد الدعم (Gmail أولاً إن كان مثبتاً).
 * يرجع false إن لم يوجد أي تطبيق بريد على الجهاز.
 */
fun openSupportEmail(context: Context, subject: String, body: String): Boolean {
    val uri = Uri.parse(
        "mailto:" + SUPPORT_EMAIL +
            "?subject=" + Uri.encode(subject) +
            "&body=" + Uri.encode(body),
    )
    val mailIntent = Intent(Intent.ACTION_SENDTO, uri)
    // Gmail مباشرةً إن وُجد، ثم أي تطبيق بريد، ثم اختيار المستخدم
    val candidates = listOf(
        Intent(mailIntent).setPackage("com.google.android.gm"),
        mailIntent,
    )
    for (intent in candidates) {
        val ok = runCatching {
            context.startActivity(Intent(intent).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
        if (ok) return true
    }
    return runCatching {
        context.startActivity(
            Intent.createChooser(mailIntent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }.isSuccess
}

/** الشاشة الإعدادات — الأقسام الأربعة كما في وثيقة التصميم §9. */
@Composable
fun SettingsScreen(
    onLogin: () -> Unit,
    onNotifications: () -> Unit,
    /** الإشعارات: الأقسام الخمسة وساعات الهدوء وتذكير الغياب (34_push_notifications). */
    onNotificationSettings: () -> Unit = {},
    onOpenProfile: () -> Unit,
    onOpenWallet: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()
    val profileLoadStatus by container.state.profileLoadStatus.collectAsState()
    val wallet by container.state.wallet.collectAsState()
    val currentLocale by container.prefs.locale.collectAsState(initial = "en")
    val dark by container.prefs.darkTheme.collectAsState(initial = true)

    // نصوص/بيانات رسالة الدعم (تُبنى هنا لأن stringResource للـComposable فقط)
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
    var usernameMessage by remember { mutableStateOf<String?>(null) }
    var profileRetryTick by remember { mutableStateOf(0) }
    var dialogKey by remember { mutableStateOf<String?>(null) }
    var languageMenu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(profile?.username) {
        profile?.username?.let { username = it }
    }

    // الحساب يبقى مسجّلاً حتى لو تأخر جلب ملفه؛ أعد الجلب بدل عرض زر دخول زائف.
    LaunchedEffect(signedIn, profileRetryTick) {
        if (signedIn && container.state.currentProfile == null) {
            try {
                withContext(Dispatchers.IO) { container.profile.ensureProfileLoaded() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                // تبقى الشاشة مفتوحة ويظهر خيار إعادة المحاولة.
            }
        }
    }

    val signedOut = !signedIn

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
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(12.dp))

            // رأس الحساب
            GlassCard(padding = 12.dp) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // الزائر ⇒ صورة المجهول (نفس صورة شاشة الدخول)
                    if (profile == null) {
                        com.addonstation.mobile.ui.components.AnonymousAvatarImage(
                            size = 54.dp,
                            borderColor = AppColors.Green,
                        )
                    } else {
                        AvatarImage(url = assetUrl(container, profile?.avatarUrl), size = 54.dp, borderColor = AppColors.Green)
                    }
                    Column(Modifier.weight(1f)) {
                        // رأس الحساب: الاسم فقط — أيقونة المستخدم انتقلت إلى حقل تغيير الاسم بالأسفل
                        Text(
                            profile?.displayName ?: profile?.username ?: stringResource(
                                if (signedIn) R.string.common_loading else R.string.common_guest,
                            ),
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
            SectionHeader(title = stringResource(R.string.set_account))
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
                } else if (profile == null) {
                    if (profileLoadStatus == ProfileLoadStatus.IDLE || profileLoadStatus == ProfileLoadStatus.LOADING) {
                        com.addonstation.mobile.ui.components.LoadingBox(
                            text = stringResource(R.string.common_loading),
                        )
                    } else {
                        com.addonstation.mobile.ui.components.ErrorState(
                            onRetry = { profileRetryTick += 1 },
                        )
                    }
                } else {
                    // أيقونة المستخدم **خارج** مربع الاسم وعلى جانبه — تماماً مثل
                    // أيقونة البريد في صف البريد بالأسفل (لا leadingIcon داخل الحقل)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_user),
                            contentDescription = null,
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(11.dp)),
                        )
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                            label = { Text(stringResource(R.string.auth_username)) },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
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
                    iconRes = R.drawable.ic_set_email,
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
            SectionHeader(title = stringResource(R.string.set_prefs))
            GlassCard(padding = 10.dp) {
                RowItem(
                    iconRes = R.drawable.ic_set_notifications,
                    title = stringResource(R.string.set_notifications),
                    onClick = onNotifications,
                )
                RowItem(
                    iconRes = R.drawable.ic_set_notifications,
                    title = stringResource(R.string.notif_settings),
                    subtitle = stringResource(R.string.notif_settings_subtitle),
                    onClick = onNotificationSettings,
                )
                Column {
                    RowItem(
                        iconRes = R.drawable.ic_set_language,
                        title = stringResource(R.string.set_language),
                        subtitle = LocaleManager.byCode(currentLocale).label,
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
                val toggleTheme: () -> Unit = {
                    scope.launch {
                        container.prefs.setDarkTheme(!dark)
                        AppPrefs.writeDark(context, !dark)
                    }
                }
                RowItem(
                    iconRes = R.drawable.ic_set_appearance,
                    title = stringResource(R.string.set_theme),
                    subtitle = if (dark) stringResource(R.string.set_theme_dark)
                    else stringResource(R.string.set_theme_light),
                    onClick = toggleTheme,
                    showChevron = false,
                    trailing = {
                        Switch(
                            checked = dark,
                            onCheckedChange = { toggleTheme() },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AppColors.OnGreen,
                                checkedTrackColor = AppColors.Green,
                                uncheckedThumbColor = AppColors.Muted,
                                uncheckedTrackColor = AppColors.Elevated2,
                                uncheckedBorderColor = AppColors.Border2,
                            ),
                        )
                    },
                )
            }

            // الدعم
            SectionHeader(title = stringResource(R.string.set_support))
            GlassCard(padding = 10.dp) {
                // الثلاثة تفتح تطبيق البريد برسالة جاهزة إلى بريد الدعم (Gmail أولاً)
                RowItem(
                    iconRes = R.drawable.ic_set_help,
                    title = stringResource(R.string.set_help),
                    subtitle = SUPPORT_EMAIL,
                    onClick = {
                        if (!openSupportEmail(context, supportSubjectHelp, supportBody)) {
                            dialogKey = "no_mail_app"
                        }
                    },
                )
                RowItem(
                    iconRes = R.drawable.ic_set_report,
                    title = stringResource(R.string.set_report_problem),
                    subtitle = SUPPORT_EMAIL,
                    onClick = {
                        if (!openSupportEmail(context, supportSubjectReport, supportBody)) {
                            dialogKey = "no_mail_app"
                        }
                    },
                )
                RowItem(
                    iconRes = R.drawable.ic_set_contact,
                    title = stringResource(R.string.set_contact),
                    subtitle = SUPPORT_EMAIL,
                    onClick = {
                        if (!openSupportEmail(context, supportSubjectContact, supportBody)) {
                            dialogKey = "no_mail_app"
                        }
                    },
                )
            }

            // حول التطبيق
            SectionHeader(title = stringResource(R.string.set_about))
            GlassCard(padding = 10.dp) {
                RowItem(
                    iconRes = R.drawable.ic_set_terms,
                    title = stringResource(R.string.set_terms),
                    onClick = { dialogKey = "terms" },
                )
                RowItem(
                    iconRes = R.drawable.ic_set_privacy,
                    title = stringResource(R.string.set_privacy),
                    onClick = { dialogKey = "privacy" },
                )
                // إصدار التطبيق صفّ معلوماتي: تحت سياسة الخصوصية وبلا سهم انتقال «›»
                RowItem(
                    iconRes = R.drawable.ic_set_version,
                    title = stringResource(R.string.set_version),
                    subtitle = "AddonStation Mobile v${BuildConfig.VERSION_NAME}",
                    showChevron = false,
                )
            }

            // منطقة الحساب
            SectionHeader(title = stringResource(R.string.set_account_area))
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
            "no_mail_app" -> stringResource(R.string.set_contact) to stringResource(R.string.set_support_no_mail_app)
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
