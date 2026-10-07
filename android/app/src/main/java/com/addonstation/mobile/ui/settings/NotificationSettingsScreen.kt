package com.addonstation.mobile.ui.settings

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.PushPrefs
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.components.ContentColumn
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.push.PushNotifications
import com.addonstation.mobile.push.PushRegistrar
import kotlinx.coroutines.launch

/**
 * إعدادات الإشعارات — القلب الذي يجعل الإشعارات «مرتّبة» لا مزعجة.
 *
 * ما يراه المستخدم:
 *   ١) مفتاح رئيسي واحد: أطفئه فتسكت كل الإشعارات فوراً.
 *   ٢) خمسة أقسام مستقلة: رسائل الشات · محتوى جديد · اجتماعي · الحساب ·
 *      تذكير بالعودة — يستطيع كتم أي قسم وحده وترك البقية.
 *   ٣) ساعات الهدوء: من/إلى بتوقيته هو؛ كل إشعار غير عاجل يُؤجَّل حتى تنتهي
 *      (والرسائل الشخصية: منشن وردّ — تصل فوراً لأن تأخيرها يلغي معناها).
 *   ٤) وتيرة تذكير العودة: 7 / 14 / 30 يوماً غياباً — وبحدّ أقصى تلقائي في
 *      الخادم: **تذكير واحد كل أسبوعين** مهما كان.
 *   ٥) زر «جرّب الإشعار الآن» للتأكد بلا انتظار.
 */
@Composable
fun NotificationSettingsScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var prefs by remember { mutableStateOf<PushPrefs?>(null) }
    var loading by remember { mutableStateOf(true) }
    var status by remember { mutableStateOf<String?>(null) }
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

    /** يحفظ فوراً في الخادم (والواجهة تتفاءل بالقيمة ثم تُصحَّح من رد الخادم). */
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

    ContentColumn(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        SubScreenHeader(title = stringResource(R.string.notif_settings), onBack = onBack)
        Spacer(Modifier.height(8.dp))

        if (loading || prefs == null) {
            Text(
                stringResource(R.string.common_loading),
                style = AppText.bodySmall,
                color = AppColors.Muted,
                modifier = Modifier.padding(16.dp),
            )
            return@ContentColumn
        }
        val p = prefs!!

        // ── القراءة فقط: إن كان النظام نفسه يمنع الإشعارات لا معنى للمفاتيح
        if (!deviceReady) {
            GlassCard {
                Text(
                    stringResource(R.string.notif_not_configured),
                    style = AppText.bodySmall,
                    color = AppColors.Gold,
                )
            }
            Spacer(Modifier.height(10.dp))
        } else if (!permissionGranted) {
            GlassCard {
                Text(stringResource(R.string.notif_need_permission), style = AppText.bodySmall, color = AppColors.Text)
                Spacer(Modifier.height(10.dp))
                GradientButton(
                    text = stringResource(R.string.notif_allow),
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // أندرويد 12 وأقدم: لا يوجد طلب إذن — الحل إعدادات النظام
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName),
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Spacer(Modifier.height(10.dp))
        }

        if (status != null) {
            Text(
                status!!,
                style = AppText.bodySmall,
                color = AppColors.Green,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        // ── ١) المفتاح الرئيسي
        SectionHeader(title = stringResource(R.string.notif_master_section))
        GlassCard(padding = 10.dp) {
            ToggleRow(
                title = stringResource(R.string.notif_master),
                subtitle = stringResource(R.string.notif_master_desc),
                checked = p.master,
                onCheckedChange = { value -> save { it.copy(master = value) } },
            )
        }

        // ── ٢) الأقسام الخمسة
        SectionHeader(title = stringResource(R.string.notif_categories))
        GlassCard(padding = 10.dp) {
            ToggleRow(
                title = stringResource(R.string.notif_cat_chat),
                subtitle = stringResource(R.string.notif_cat_chat_desc),
                checked = p.chat, enabled = p.master,
                onCheckedChange = { v -> save { it.copy(chat = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.notif_cat_content),
                subtitle = stringResource(R.string.notif_cat_content_desc),
                checked = p.content, enabled = p.master,
                onCheckedChange = { v -> save { it.copy(content = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.notif_cat_social),
                subtitle = stringResource(R.string.notif_cat_social_desc),
                checked = p.social, enabled = p.master,
                onCheckedChange = { v -> save { it.copy(social = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.notif_cat_account),
                subtitle = stringResource(R.string.notif_cat_account_desc),
                checked = p.account, enabled = p.master,
                onCheckedChange = { v -> save { it.copy(account = v) } },
            )
            ToggleRow(
                title = stringResource(R.string.notif_cat_reminders),
                subtitle = stringResource(R.string.notif_cat_reminders_desc),
                checked = p.reminders, enabled = p.master,
                onCheckedChange = { v -> save { it.copy(reminders = v) } },
            )
        }

        // ── ٣) ساعات الهدوء
        SectionHeader(title = stringResource(R.string.notif_quiet_section))
        GlassCard(padding = 10.dp) {
            ToggleRow(
                title = stringResource(R.string.notif_quiet),
                subtitle = stringResource(R.string.notif_quiet_desc),
                checked = p.quietEnabled, enabled = p.master,
                onCheckedChange = { v -> save { it.copy(quietEnabled = v) } },
            )
            if (p.quietEnabled) {
                HourRow(
                    label = stringResource(R.string.notif_quiet_from),
                    hour = p.quietFrom, enabled = p.master,
                    onChange = { h -> save { it.copy(quietFrom = h) } },
                )
                HourRow(
                    label = stringResource(R.string.notif_quiet_to),
                    hour = p.quietTo, enabled = p.master,
                    onChange = { h -> save { it.copy(quietTo = h) } },
                )
                Text(
                    stringResource(R.string.notif_quiet_note),
                    style = AppText.tiny,
                    color = AppColors.Dim,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }

        // ── ٤) تذكير العودة
        SectionHeader(title = stringResource(R.string.notif_reengage_section))
        GlassCard(padding = 10.dp) {
            Text(
                stringResource(R.string.notif_reengage_desc),
                style = AppText.bodySmall,
                color = AppColors.Muted,
                modifier = Modifier.padding(8.dp),
            )
            Spacer(Modifier.height(6.dp))
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(7, 14, 30).forEach { days ->
                    val selected = p.reengageDays == days
                    Text(
                        text = stringResource(R.string.notif_days, days),
                        style = AppText.bodySmall,
                        color = if (selected) AppColors.OnGreen else AppColors.Text,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) AppColors.Green else AppColors.Elevated)
                            .clickable(enabled = p.master && p.reminders) {
                                save { it.copy(reengageDays = days) }
                            }
                            .padding(vertical = 9.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }

        // ── ٥) تجربة فورية
        Spacer(Modifier.height(14.dp))
        GhostButton(
            text = stringResource(R.string.notif_test),
            onClick = {
                scope.launch {
                    val result = container.push.testMe()
                    status = when {
                        result.ok -> context.getString(R.string.notif_test_sent)
                        result.message == "no_device" -> context.getString(R.string.notif_test_no_device)
                        else -> context.getString(R.string.notif_test_failed)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            borderColor = AppColors.Green.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(10.dp))
        Text(
            stringResource(R.string.notif_group_note),
            style = AppText.tiny,
            color = AppColors.Dim,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun ToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = AppText.bodySmall,
                color = if (enabled) AppColors.Text else AppColors.Dim,
            )
            Text(subtitle, style = AppText.tiny, color = AppColors.Dim)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(
                checkedThumbColor = AppColors.OnGreen,
                checkedTrackColor = AppColors.Green,
                uncheckedThumbColor = AppColors.Muted,
                uncheckedTrackColor = AppColors.Elevated2,
                uncheckedBorderColor = AppColors.Border2,
            ),
        )
    }
}

/** صف اختيار الساعة (٠–٢٣) بأسهم − و + بدل قائمة منسدلة طويلة. */
@Composable
private fun HourRow(label: String, hour: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label,
            style = AppText.bodySmall,
            color = if (enabled) AppColors.Text else AppColors.Dim,
            modifier = Modifier.weight(1f),
        )
        StepButton("−", enabled) { onChange((hour + 23) % 24) }
        Text(
            text = "%02d:00".format(hour),
            style = AppText.bodySmall,
            color = AppColors.Text,
            modifier = Modifier.padding(horizontal = 12.dp),
        )
        StepButton("+", enabled) { onChange((hour + 1) % 24) }
    }
}

@Composable
private fun StepButton(symbol: String, enabled: Boolean, onClick: () -> Unit) {
    Text(
        symbol,
        style = AppText.bodySmall,
        color = if (enabled) AppColors.Text else AppColors.Dim,
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(AppColors.Elevated)
            .clickable(enabled = enabled) { onClick() }
            .padding(7.dp),
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
    )
}
