package com.addonstation.mobile.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.ApiErrorKind
import com.addonstation.mobile.ui.components.AuthSettingsPrimaryButton
import com.addonstation.mobile.ui.components.AuthSettingsTopBar
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.Spec
import kotlinx.coroutines.launch

/**
 * شاشة ربط جهاز PlayStation (Settings → Link a console).
 *
 * السوني يولّد كوداً من 6 محارف داخل Sign in، والهنا يُدخله المستخدم
 * فتُنفَّذ claim_console_link_code التي تربط الحساب بالجهاز فوراً.
 */
@Composable
fun LinkConsoleScreen(
    onBack: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()

    var codeInput by remember { mutableStateOf("") }
    var linking by remember { mutableStateOf(false) }
    var linkedDevice by remember { mutableStateOf<String?>(null) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    val msgInvalid = stringResource(R.string.link_error_invalid)
    val msgNotFound = stringResource(R.string.link_error_notfound)
    val msgExpired = stringResource(R.string.link_error_expired)
    val msgSignIn = stringResource(R.string.link_error_signin)
    val msgNetwork = stringResource(R.string.link_error_network)

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        AuthSettingsTopBar(title = stringResource(R.string.link_title), onBack = onBack)

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // بطاقة الشرح
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(AppColors.AuthSurface)
                    .border(1.dp, AppColors.AuthBorder, RoundedCornerShape(18.dp))
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Box(
                        Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(AppColors.AuthSurfaceHigh),
                        contentAlignment = Alignment.Center,
                    ) {
                        Image(
                            painter = painterResource(R.drawable.ic_ps_controller),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                    Text(
                        stringResource(R.string.link_card_title),
                        style = Spec.screenTitle.copy(fontSize = 18.sp),
                        color = AppColors.AuthText,
                    )
                }
                Text(
                    stringResource(R.string.link_intro),
                    style = Spec.rowSub,
                    color = AppColors.AuthMuted,
                )
            }

            if (linkedDevice != null) {
                // حالة النجاح
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(AppColors.AuthGreen.copy(alpha = 0.12f))
                        .border(1.dp, AppColors.AuthGreen.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("✓", fontSize = 42.sp, color = AppColors.AuthGreenText)
                    Text(
                        stringResource(R.string.link_success),
                        style = Spec.screenTitle.copy(fontSize = 18.sp),
                        color = AppColors.AuthGreenText,
                        textAlign = TextAlign.Center,
                    )
                    val device = linkedDevice
                    if (!device.isNullOrBlank()) {
                        Text(
                            device,
                            style = Spec.rowValue,
                            color = AppColors.AuthText,
                            textAlign = TextAlign.Center,
                        )
                    }
                    Text(
                        stringResource(R.string.link_success_sub),
                        style = Spec.rowSub,
                        color = AppColors.AuthMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                // إدخال الكود
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { input ->
                            codeInput = input
                                .filter { it.isLetterOrDigit() }
                                .uppercase()
                                .take(6)
                            errorMsg = null
                        },
                        label = { Text(stringResource(R.string.link_code_hint)) },
                        singleLine = true,
                        enabled = !linking,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            keyboardType = KeyboardType.Ascii,
                            imeAction = ImeAction.Done,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorMsg?.let {
                        Text(it, style = Spec.rowSub, color = AppColors.AuthRed)
                    }
                    AuthSettingsPrimaryButton(
                        text = stringResource(if (linking) R.string.link_working else R.string.link_button),
                        enabled = !linking && codeInput.length == 6,
                        loading = linking,
                        onClick = {
                            linking = true
                            errorMsg = null
                            scope.launch {
                                try {
                                    linkedDevice = container.consoleLink.claim(codeInput)
                                } catch (e: ApiError) {
                                    errorMsg = when {
                                        e.kind == ApiErrorKind.NOT_SIGNED_IN -> msgSignIn
                                        e.raw.orEmpty().contains("invalid_code") -> msgInvalid
                                        e.raw.orEmpty().contains("expired") -> msgExpired
                                        e.raw.orEmpty().contains("not_found") -> msgNotFound
                                        else -> msgNetwork
                                    }
                                } catch (e: Exception) {
                                    errorMsg = msgNetwork
                                } finally {
                                    linking = false
                                }
                            }
                        },
                    )
                }
            }

            // تذكير بأين يظهر الكود
            Text(
                stringResource(R.string.link_steps),
                style = Spec.rowSub,
                color = AppColors.AuthMuted,
            )
        }
    }
}
