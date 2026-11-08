package com.addonstation.mobile.ui.settings

import android.app.TimePickerDialog
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.PushPrefs
import com.addonstation.mobile.push.PushNotifications
import com.addonstation.mobile.push.PushRegistrar
import com.addonstation.mobile.ui.components.AuthSettingsDivider
import com.addonstation.mobile.ui.components.AuthSettingsGroupCard
import com.addonstation.mobile.ui.components.AuthSettingsIconTile
import com.addonstation.mobile.ui.components.AuthSettingsRow
import com.addonstation.mobile.ui.components.AuthSettingsSectionLabel
import com.addonstation.mobile.ui.components.AuthSettingsSwitch
import com.addonstation.mobile.ui.components.AuthSettingsTopBar
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec
import kotlinx.coroutines.launch

/** إعدادات الإشعارات الحقيقية — تفضيلات الخادم وFCM كما هي؛ التغيير بصري وفق §4. */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val signedIn by container.state.signedIn.collectAsStateWithLifecycle()

    var prefs by remember { mutableStateOf<PushPrefs?>(null) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }
    var testBusy by remember { mutableStateOf(false) }
    var permissionGranted by remember { mutableStateOf(PushNotifications.permissionGranted(context)) }
    var deviceReady by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        permissionGranted = granted
        if (granted) scope.launch { PushRegistrar.register(context) }
    }

    LaunchedEffect(Unit) {
        prefs = container.push.prefs() ?: PushPrefs()
        deviceReady = PushNotifications.configured(context)
        loading = false
    }

    /** حفظ فوري للتفضيلات على الخادم؛ ترتيب الوسائط يطابق `push_set_prefs` (10). */
    fun save(update: (PushPrefs) -> PushPrefs) {
        val current = prefs ?: return
        val next = update(current)
        prefs = next
        scope.launch {
            val saved = container.push.setPrefs(
                master = next.master,
                chat = next.chat,
                content = next.content,
                social = next.social,
                account = next.account,
                reminders = next.reminders,
                quietEnabled = next.quietEnabled,
                quietFrom = next.quietFrom,
                quietTo = next.quietTo,
                reengageDays = next.reengageDays,
            )
            if (saved != null) prefs = saved
        }
    }

    fun requestPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        } else {
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                )
            }
        }
    }

    fun chooseTime(initialHour: Int, onSelected: (Int) -> Unit) {
        TimePickerDialog(
            context,
            { _, hourOfDay, _ -> onSelected(hourOfDay) },
            initialHour,
            0,
            true,
        ).show()
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        AuthSettingsTopBar(
            title = stringResource(R.string.notif_settings),
            onBack = onBack,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            Spacer(Modifier.height(12.dp))
            if (!deviceReady) {
                AuthSettingsGroupCard(
                    radius = 16.dp,
                    background = AppColors.AuthPlanBottom,
                    borderColor = AppColors.AuthPermissionBorder,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.notif_not_configured),
                            style = Spec.rowSub.copy(fontSize = 13.sp, lineHeight = 18.2.sp),
                            color = AppColors.AuthGold,
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
            } else if (!permissionGranted) {
                AuthSettingsGroupCard(
                    radius = 16.dp,
                    background = AppColors.AuthPlanBottom,
                    borderColor = AppColors.AuthPermissionBorder,
                ) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text(
                            stringResource(R.string.notif_need_permission),
                            style = Spec.rowSub.copy(fontSize = 13.sp, lineHeight = 18.2.sp),
                            color = AppColors.AuthGold,
                        )
                        Box(
                            Modifier
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AppColors.AuthGoldFill)
                                .clickable(onClick = { requestPermission() })
                                .padding(horizontal = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                stringResource(R.string.notif_allow),
                                style = Spec.buttonSmall,
                                color = AppColors.AuthGoldDark,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }

            if (loading || prefs == null) {
                Text(
                    stringResource(R.string.common_loading),
                    style = Spec.rowSub,
                    color = AppColors.AuthMuted,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                )
                return@Column
            }
            val p = prefs!!

            status?.let {
                Text(
                    it,
                    style = Spec.rowSub,
                    color = if (statusIsError) AppColors.AuthRed else AppColors.AuthGreenText,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                )
            }

            // Master switch card — one switch mutes all five categories.
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Brush.linearGradient(listOf(AppColors.AuthMasterTop, AppColors.AuthSurface)))
                    .border(1.dp, AppColors.AuthBlue, RoundedCornerShape(18.dp))
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            stringResource(R.string.notif_master_section).uppercase(),
                            style = Spec.smallCaps,
                            color = AppColors.AuthBlueLight,
                        )
                        Spacer(Modifier.height(5.dp))
                        Text(
                            stringResource(R.string.notif_allow),
                            style = Spec.planName,
                            color = AppColors.AuthText,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            stringResource(R.string.notif_master_desc),
                            style = Spec.rowSub,
                            color = AppColors.AuthChipLabel,
                        )
                    }
                    AuthSettingsSwitch(
                        checked = p.master,
                        onCheckedChange = { value -> save { it.copy(master = value) } },
                    )
                }
            }

            AuthSettingsSectionLabel(stringResource(R.string.settings_sections_five), AppColors.AuthPurple)
            Text(
                stringResource(R.string.notif_settings_subtitle),
                style = Spec.rowSub,
                color = AppColors.AuthMuted,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 10.dp),
            )
            AuthSettingsGroupCard {
                AuthNotificationToggleRow(
                    title = stringResource(R.string.notif_cat_chat),
                    subtitle = stringResource(R.string.notif_cat_chat_desc),
                    icon = Icons.Outlined.ChatBubbleOutline,
                    accent = AppColors.AuthAccentBlue,
                    checked = p.chat,
                    enabled = p.master,
                    onCheckedChange = { value -> save { it.copy(chat = value) } },
                )
                AuthSettingsDivider()
                AuthNotificationToggleRow(
                    title = stringResource(R.string.notif_cat_social),
                    subtitle = stringResource(R.string.notif_cat_social_desc),
                    icon = Icons.Outlined.Groups,
                    accent = AppColors.AuthGreen,
                    checked = p.social,
                    enabled = p.master,
                    onCheckedChange = { value -> save { it.copy(social = value) } },
                )
                AuthSettingsDivider()
                if (signedIn) {
                    AuthNotificationToggleRow(
                        title = stringResource(R.string.plan_title),
                        subtitle = stringResource(R.string.notif_cat_account_desc),
                        icon = Icons.Outlined.Star,
                        accent = AppColors.AuthGold,
                        checked = p.account,
                        enabled = p.master,
                        onCheckedChange = { value -> save { it.copy(account = value) } },
                    )
                    AuthSettingsDivider()
                }
                AuthNotificationToggleRow(
                    title = stringResource(R.string.notif_ch_content),
                    subtitle = stringResource(R.string.notif_ch_content_desc),
                    icon = Icons.Outlined.AutoAwesome,
                    accent = AppColors.AuthPurple,
                    checked = p.content,
                    enabled = p.master,
                    onCheckedChange = { value -> save { it.copy(content = value) } },
                )
                AuthSettingsDivider()
                AuthNotificationToggleRow(
                    title = stringResource(R.string.notif_ch_reminders),
                    subtitle = stringResource(R.string.notif_ch_reminders_desc),
                    icon = Icons.Outlined.Schedule,
                    accent = AppColors.AuthTeal,
                    checked = p.reminders,
                    enabled = p.master,
                    onCheckedChange = { value -> save { it.copy(reminders = value) } },
                )
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 58.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        stringResource(R.string.notif_reengage_section),
                        style = Spec.rowTitle.copy(fontSize = 13.sp),
                        color = if (p.master && p.reminders) AppColors.AuthText else AppColors.AuthMuted,
                    )
                    Text(
                        stringResource(R.string.notif_reengage_desc),
                        style = Spec.rowSub,
                        color = AppColors.AuthMuted,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(3, 7, 14).forEach { days ->
                            val selected = p.reengageDays == days || (p.reengageDays !in listOf(3, 7, 14) && days == 14)
                            ComebackDayChip(
                                text = stringResource(R.string.notif_days, days),
                                selected = selected,
                                enabled = p.master && p.reminders,
                                modifier = Modifier.weight(1f),
                                onClick = { save { it.copy(reengageDays = days) } },
                            )
                        }
                    }
                    Text(
                        stringResource(R.string.notif_cat_reminders_desc),
                        style = TextStyle(fontSize = 11.sp, lineHeight = 14.sp),
                        color = AppColors.AuthMuted,
                    )
                }
            }

            AuthSettingsSectionLabel(stringResource(R.string.notif_quiet_section), AppColors.AuthTeal)
            AuthSettingsGroupCard {
                AuthNotificationToggleRow(
                    title = stringResource(R.string.notif_quiet),
                    subtitle = stringResource(R.string.notif_quiet_desc),
                    icon = Icons.Outlined.NightsStay,
                    accent = AppColors.AuthPurple,
                    checked = p.quietEnabled,
                    enabled = p.master,
                    onCheckedChange = { value -> save { it.copy(quietEnabled = value) } },
                )
                AuthSettingsDivider()
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    QuietHourField(
                        label = stringResource(R.string.notif_quiet_from),
                        hour = p.quietFrom,
                        enabled = p.master && p.quietEnabled,
                        onClick = { chooseTime(p.quietFrom) { h -> save { it.copy(quietFrom = h) } } },
                        modifier = Modifier.weight(1f),
                    )
                    QuietHourField(
                        label = stringResource(R.string.notif_quiet_to),
                        hour = p.quietTo,
                        enabled = p.master && p.quietEnabled,
                        onClick = { chooseTime(p.quietTo) { h -> save { it.copy(quietTo = h) } } },
                        modifier = Modifier.weight(1f),
                    )
                }
                Text(
                    stringResource(R.string.notif_quiet_note),
                    style = Spec.rowSub,
                    color = AppColors.AuthMuted,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 2.dp),
                )
                Spacer(Modifier.height(12.dp))
            }

            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, AppColors.AuthBlue, RoundedCornerShape(14.dp))
                    .clickable(enabled = !testBusy) {
                        scope.launch {
                            testBusy = true
                            val result = container.push.testMe()
                            statusIsError = !result.ok
                            status = when {
                                result.ok -> context.getString(R.string.notif_test_sent)
                                result.message == "no_device" -> context.getString(R.string.notif_test_no_device)
                                else -> context.getString(R.string.notif_test_failed)
                            }
                            testBusy = false
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.notif_test),
                    style = Spec.buttonSmall,
                    color = AppColors.AuthBlueLight,
                    textAlign = TextAlign.Center,
                )
            }
            Text(
                stringResource(R.string.notif_group_note),
                style = TextStyle(fontSize = 11.sp, lineHeight = 15.sp),
                color = AppColors.AuthMuted,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AuthNotificationToggleRow(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    accent: androidx.compose.ui.graphics.Color,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    AuthSettingsRow(
        title = title,
        subtitle = subtitle,
        tile = { AuthSettingsIconTile(icon, accent) },
        trailing = {
            AuthSettingsSwitch(
                checked = checked,
                enabled = enabled,
                onCheckedChange = onCheckedChange,
            )
        },
    )
}

@Composable
private fun ComebackDayChip(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(36.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) AppColors.AuthBlue else AppColors.AuthSurfaceHigh)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = Spec.segText,
            color = if (selected) AppColors.AuthWhite else AppColors.AuthChipLabel,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun QuietHourField(
    label: String,
    hour: Int,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier) {
        Text(label, style = Spec.fieldLabel, color = AppColors.AuthMuted)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AppColors.AuthBg)
                .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(12.dp))
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "%02d:00".format(hour),
                style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.ExtraBold),
                color = if (enabled) AppColors.AuthText else AppColors.AuthMuted,
            )
        }
    }
}
