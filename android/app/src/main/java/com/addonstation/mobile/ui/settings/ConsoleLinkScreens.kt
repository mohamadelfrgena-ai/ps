package com.addonstation.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.data.model.LinkedConsole
import com.addonstation.mobile.data.toApiError
import com.addonstation.mobile.ui.apiErrorText
import com.addonstation.mobile.ui.components.AuthSettingsField
import com.addonstation.mobile.ui.components.AuthSettingsPrimaryButton
import com.addonstation.mobile.ui.components.AuthSettingsTopBar
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
fun ConsoleLinkScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onOpenDevices: () -> Unit,
) {
    val container = rememberContainer()
    val signedInState by container.state.signedIn.collectAsState()
    val hasSession = signedInState && container.supabase.session() != null
    val scope = rememberCoroutineScope()

    var code by remember { mutableStateOf("") }
    var submitting by remember { mutableStateOf(false) }
    var feedbackKey by remember { mutableStateOf<String?>(null) }
    var errorKind by remember { mutableStateOf<ApiErrorKind?>(null) }
    var linkedDeviceName by remember { mutableStateOf<String?>(null) }

    val feedbackText = when (feedbackKey) {
        "sign_in_required" -> stringResource(R.string.console_link_sign_in_required)
        "invalid_code" -> stringResource(R.string.console_link_invalid_code)
        "code_not_found" -> stringResource(R.string.console_link_code_not_found)
        "code_expired" -> stringResource(R.string.console_link_code_expired)
        "unknown_error" -> stringResource(R.string.msg_generic_error)
        null -> errorKind?.let { apiErrorText(it) }
        else -> stringResource(R.string.msg_generic_error)
    }
    val showSignIn = !hasSession || feedbackKey == "sign_in_required" || errorKind == ApiErrorKind.NOT_SIGNED_IN

    fun submitCode() {
        if (submitting) return
        if (!hasSession || container.supabase.session() == null) {
            feedbackKey = "sign_in_required"
            errorKind = null
            linkedDeviceName = null
            return
        }
        if (!com.addonstation.mobile.data.repo.ConsoleLinkRepository.isValidCode(code)) {
            feedbackKey = "invalid_code"
            errorKind = null
            linkedDeviceName = null
            return
        }

        val submittedCode = code
        submitting = true
        feedbackKey = null
        errorKind = null
        linkedDeviceName = null
        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    container.consoleLinks.claimCode(submittedCode)
                }
                if (result.ok) {
                    linkedDeviceName = result.deviceName?.takeIf { it.isNotBlank() }
                        ?: result.platform?.takeIf { it.isNotBlank() }
                        ?: "PlayStation"
                    code = ""
                } else {
                    feedbackKey = result.message ?: "unknown_error"
                    if (feedbackKey !in setOf("sign_in_required", "invalid_code", "code_not_found", "code_expired")) {
                        feedbackKey = "unknown_error"
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                errorKind = error.toApiError().kind
                if (errorKind == ApiErrorKind.NOT_SIGNED_IN) feedbackKey = "sign_in_required"
            } finally {
                submitting = false
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        AuthSettingsTopBar(
            title = stringResource(R.string.console_link_title),
            onBack = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                stringResource(R.string.console_link_help),
                style = Spec.rowSub.copy(fontSize = 14.sp, lineHeight = 21.sp),
                color = AppColors.AuthMuted,
            )

            if (!hasSession) {
                ConsoleSignInCard(onLogin = onLogin)
            } else {
                AuthSettingsField(
                    label = stringResource(R.string.console_link_code_label),
                    value = code,
                    onValueChange = { input ->
                        code = com.addonstation.mobile.data.repo.ConsoleLinkRepository
                            .normalizeCode(input)
                            .take(com.addonstation.mobile.data.repo.ConsoleLinkRepository.CODE_LENGTH)
                        feedbackKey = null
                        errorKind = null
                        linkedDeviceName = null
                    },
                    placeholder = "K7P4QX",
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                    onDone = ::submitCode,
                )
                AuthSettingsPrimaryButton(
                    text = stringResource(R.string.console_link_action),
                    onClick = ::submitCode,
                    enabled = !submitting && code.length == com.addonstation.mobile.data.repo.ConsoleLinkRepository.CODE_LENGTH,
                    loading = submitting,
                )
            }

            linkedDeviceName?.let { name ->
                ConsoleStatusNote(
                    text = stringResource(R.string.console_link_success, name),
                    success = true,
                )
                AuthSettingsPrimaryButton(
                    text = stringResource(R.string.console_link_view_devices),
                    onClick = onOpenDevices,
                )
            }

            if (linkedDeviceName == null && feedbackText != null) {
                ConsoleStatusNote(
                    text = feedbackText,
                    success = false,
                )
                if (showSignIn) {
                    AuthSettingsPrimaryButton(
                        text = stringResource(R.string.console_link_sign_in_action),
                        onClick = onLogin,
                    )
                }
            }

            if (hasSession) {
                TextButton(onClick = onOpenDevices, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                    Text(stringResource(R.string.console_link_view_devices), color = AppColors.AuthBlueLight)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LinkedConsolesScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    onLinkAnother: () -> Unit,
) {
    val container = rememberContainer()
    val signedInState by container.state.signedIn.collectAsState()
    val hasSession = signedInState && container.supabase.session() != null
    val localeCode by container.prefs.locale.collectAsState(initial = "en")
    val locale = remember(localeCode) { Locale.forLanguageTag(localeCode) }
    val scope = rememberCoroutineScope()

    var devices by remember { mutableStateOf<List<LinkedConsole>>(emptyList()) }
    var initialLoading by remember { mutableStateOf(false) }
    var refreshing by remember { mutableStateOf(false) }
    var loadFailed by remember { mutableStateOf(false) }
    var refreshRequest by remember { mutableStateOf(0) }
    var noticeKey by remember { mutableStateOf<String?>(null) }
    var pendingUnlink by remember { mutableStateOf<LinkedConsole?>(null) }
    var deletingId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(hasSession, refreshRequest) {
        if (!hasSession || container.supabase.session() == null) {
            devices = emptyList()
            initialLoading = false
            loadFailed = false
            return@LaunchedEffect
        }
        initialLoading = true
        loadFailed = false
        try {
            devices = withContext(Dispatchers.IO) { container.consoleLinks.linkedConsoles() }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            loadFailed = true
        } finally {
            initialLoading = false
        }
    }

    fun pullToRefresh() {
        if (!hasSession || container.supabase.session() == null || initialLoading || refreshing) return
        scope.launch {
            refreshing = true
            loadFailed = false
            noticeKey = null
            try {
                devices = withContext(Dispatchers.IO) { container.consoleLinks.linkedConsoles() }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                loadFailed = true
            } finally {
                refreshing = false
            }
        }
    }

    fun confirmUnlink(console: LinkedConsole) {
        if (deletingId != null) return
        scope.launch {
            deletingId = console.id
            noticeKey = null
            try {
                devices = withContext(Dispatchers.IO) {
                    container.consoleLinks.unlinkAndReload(console.id)
                }
                pendingUnlink = null
                noticeKey = "unlink_success"
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Throwable) {
                noticeKey = "unlink_failed"
            } finally {
                deletingId = null
            }
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        AuthSettingsTopBar(
            title = stringResource(R.string.console_devices_title),
            onBack = onBack,
        )
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = ::pullToRefresh,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!hasSession) {
                    item { ConsoleSignInCard(onLogin = onLogin) }
                } else {
                    item {
                        Text(
                            stringResource(R.string.console_devices_settings_subtitle),
                            style = Spec.rowSub.copy(fontSize = 13.sp, lineHeight = 19.sp),
                            color = AppColors.AuthMuted,
                        )
                    }
                    noticeKey?.let { key ->
                        item {
                            ConsoleStatusNote(
                                text = stringResource(
                                    if (key == "unlink_success") R.string.console_device_unlink_success
                                    else R.string.console_device_unlink_failed,
                                ),
                                success = key == "unlink_success",
                            )
                        }
                    }
                    when {
                        initialLoading -> item { LoadingBox(text = stringResource(R.string.common_loading)) }
                        loadFailed -> item {
                            ErrorState(
                                onRetry = { refreshRequest += 1 },
                                text = stringResource(R.string.console_devices_load_failed),
                            )
                        }
                        devices.isEmpty() -> item {
                            ConsoleEmptyDevices(onLinkAnother = onLinkAnother)
                        }
                        else -> items(devices, key = { it.id }) { device ->
                            LinkedConsoleCard(
                                console = device,
                                locale = locale,
                                deleting = deletingId == device.id,
                                onUnlink = { pendingUnlink = device },
                            )
                        }
                    }
                    if (devices.isNotEmpty()) {
                        item {
                            AuthSettingsPrimaryButton(
                                text = stringResource(R.string.console_link_title),
                                onClick = onLinkAnother,
                            )
                        }
                    }
                }
            }
        }
    }

    pendingUnlink?.let { console ->
        AlertDialog(
            onDismissRequest = { if (deletingId == null) pendingUnlink = null },
            containerColor = AppColors.AuthSurface,
            titleContentColor = AppColors.AuthText,
            textContentColor = AppColors.AuthMuted,
            title = { Text(stringResource(R.string.console_device_unlink_confirm_title)) },
            text = {
                Text(stringResource(
                    R.string.console_device_unlink_confirm_message,
                    console.deviceName?.takeIf { it.isNotBlank() } ?: "PlayStation",
                ))
            },
            confirmButton = {
                TextButton(
                    enabled = deletingId == null,
                    onClick = { confirmUnlink(console) },
                ) {
                    Text(stringResource(R.string.common_delete), color = AppColors.AuthRedText)
                }
            },
            dismissButton = {
                TextButton(
                    enabled = deletingId == null,
                    onClick = { pendingUnlink = null },
                ) {
                    Text(stringResource(R.string.common_cancel), color = AppColors.AuthMuted)
                }
            },
        )
    }
}

@Composable
private fun ConsoleSignInCard(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.console_link_sign_in_required),
            style = Spec.rowSub.copy(fontSize = 14.sp, lineHeight = 20.sp),
            color = AppColors.AuthText,
        )
        AuthSettingsPrimaryButton(
            text = stringResource(R.string.console_link_sign_in_action),
            onClick = onLogin,
        )
    }
}

@Composable
private fun ConsoleEmptyDevices(onLinkAnother: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            stringResource(R.string.console_devices_empty),
            style = Spec.rowSub.copy(fontSize = 14.sp),
            color = AppColors.AuthMuted,
        )
        AuthSettingsPrimaryButton(
            text = stringResource(R.string.console_link_title),
            onClick = onLinkAnother,
        )
    }
}

@Composable
private fun LinkedConsoleCard(
    console: LinkedConsole,
    locale: Locale,
    deleting: Boolean,
    onUnlink: () -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val name = console.deviceName?.takeIf { it.isNotBlank() } ?: "PlayStation"
    val platform = console.platform?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT) ?: "PlayStation"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, shape)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    name,
                    style = Spec.rowTitle.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                    color = AppColors.AuthText,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(platform, style = Spec.rowSub.copy(fontSize = 11.sp), color = AppColors.AuthBlueLight)
            }
            TextButton(onClick = onUnlink, enabled = !deleting) {
                Text(stringResource(R.string.console_device_unlink), color = AppColors.AuthRedText)
            }
        }
        Text(
            stringResource(R.string.console_device_linked_at, TimeFormat.dateTime(console.createdAt, locale)),
            style = Spec.rowSub.copy(fontSize = 11.sp),
            color = AppColors.AuthMuted,
        )
        Text(
            stringResource(
                R.string.console_device_last_seen,
                TimeFormat.dateTime(console.lastSeenAt, locale).ifBlank { "—" },
            ),
            style = Spec.rowSub.copy(fontSize = 11.sp),
            color = AppColors.AuthMuted,
        )
    }
}

@Composable
private fun ConsoleStatusNote(text: String, success: Boolean) {
    val shape = RoundedCornerShape(13.dp)
    val accent = if (success) AppColors.AuthGreenText else AppColors.AuthRedText
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(accent.copy(alpha = 0.10f))
            .border(1.dp, accent.copy(alpha = 0.28f), shape)
            .padding(12.dp),
    ) {
        Text(text, style = Spec.rowSub.copy(fontSize = 13.sp, lineHeight = 19.sp), color = accent)
    }
}

