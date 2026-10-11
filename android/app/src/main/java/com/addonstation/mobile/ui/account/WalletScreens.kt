package com.addonstation.mobile.ui.account

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.History
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.ApiError
import com.addonstation.mobile.data.model.AcTask
import com.addonstation.mobile.data.model.AcTransaction
import com.addonstation.mobile.data.model.ProfileStats
import com.addonstation.mobile.ui.components.AuthSettingsTopBar
import com.addonstation.mobile.ui.components.appLocaleCode
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.WalletIconTile
import com.addonstation.mobile.ui.components.WalletLoadingSkeleton
import com.addonstation.mobile.ui.components.WalletSectionLabel
import com.addonstation.mobile.ui.components.WalletTransactionRow
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.PixelFont
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import kotlin.math.abs

/** AC wallet UI; all balance, redemption and task-claim operations stay on the existing repository. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AcWalletScreen(
    onBack: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    val wallet by container.state.wallet.collectAsState()
    val profile by container.state.profile.collectAsState()
    val signedIn by container.state.signedIn.collectAsState()

    var tasks by remember { mutableStateOf<List<AcTask>>(emptyList()) }
    var transactions by remember { mutableStateOf<List<AcTransaction>>(emptyList()) }
    var dailyStats by remember { mutableStateOf<ProfileStats?>(null) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var codeInput by remember { mutableStateOf("") }
    var redeeming by remember { mutableStateOf(false) }
    var detectedTier by remember { mutableStateOf<Int?>(null) }
    var redeemMessage by remember { mutableStateOf<String?>(null) }
    var redeemMessageOk by remember { mutableStateOf(true) }
    var claimingDaily by remember { mutableStateOf(false) }
    var claimingTaskId by remember { mutableStateOf<String?>(null) }
    var showTasks by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val localeCode = appLocaleCode()
    val isArabic = localeCode.equals("ar", ignoreCase = true)
    val scrollState = rememberScrollState()
    val redeemRequester = remember { BringIntoViewRequester() }

    val msgInvalid = androidx.compose.ui.res.stringResource(R.string.ac_redeem_invalid)
    val msgUsed = androidx.compose.ui.res.stringResource(R.string.ac_redeem_used)
    val msgExpired = androidx.compose.ui.res.stringResource(R.string.ac_redeem_expired)
    val msgOff = androidx.compose.ui.res.stringResource(R.string.ac_redeem_off)
    val msgExhausted = androidx.compose.ui.res.stringResource(R.string.ac_redeem_exhausted)
    val msgSignIn = androidx.compose.ui.res.stringResource(R.string.ac_redeem_sign_in)
    val msgGeneric = androidx.compose.ui.res.stringResource(R.string.msg_generic_error)
    val msgClaimed = androidx.compose.ui.res.stringResource(R.string.ac_claimed)
    val msgSignInNeeded = androidx.compose.ui.res.stringResource(R.string.msg_sign_in_needed)
    val successTemplate = androidx.compose.ui.res.stringResource(R.string.ac_redeem_success, "%s")

    fun localizedNumber(value: Long): String = NumberFormat.getIntegerInstance(Locale.US).format(value)
    fun successMessage(amount: Int): String = successTemplate.format(localizedNumber(amount.toLong()))

    fun redeemError(raw: String?): String {
        val value = raw.orEmpty().lowercase(Locale.ROOT)
        return when {
            value.contains("code_used") -> msgUsed
            value.contains("code_expired") -> msgExpired
            value.contains("code_inactive") -> msgOff
            value.contains("code_exhausted") -> msgExhausted
            value.contains("code_invalid") -> msgInvalid
            value.contains("sign_in_required") -> msgSignIn
            raw.isNullOrBlank() -> msgGeneric
            else -> raw
        }
    }

    fun claimError(raw: String?): String {
        val value = raw.orEmpty().lowercase(Locale.ROOT)
        return when {
            value.contains("already_claimed") || value.contains("max_claims") -> msgClaimed
            value.contains("sign_in_required") -> msgSignInNeeded
            raw.isNullOrBlank() -> msgGeneric
            else -> raw
        }
    }

    suspend fun refreshDailyStats() {
        if (!signedIn) {
            dailyStats = null
            return
        }
        val userId = profile?.id ?: container.supabase.session()?.userId ?: return
        runCatching { container.profile.stats(userId) }
            .onSuccess { dailyStats = it }
    }

    suspend fun refreshAfterClaim() {
        tasks = container.ac.tasks()
        transactions = container.ac.transactions()
        refreshDailyStats()
    }

    LaunchedEffect(reloadTick, signedIn) {
        loading = true
        failed = false
        try {
            container.profile.refreshWallet()
            tasks = container.ac.tasks()
            transactions = container.ac.transactions()
            refreshDailyStats()
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    fun claimDaily() {
        if (claimingDaily || dailyStats?.dailyClaimed == true) return
        scope.launch {
            claimingDaily = true
            try {
                val taskId = runCatching {
                    container.ac.tasks()
                        .firstOrNull { it.frequency == "daily" || it.frequency == "streak" }
                        ?.id
                }.getOrNull() ?: "daily_login"
                container.ac.claimTask(taskId)
                    .onSuccess { reward ->
                        dailyStats = dailyStats?.copy(
                            streak = dailyStats?.streak?.plus(1) ?: 1,
                            dailyClaimed = true,
                        )
                        refreshAfterClaim()
                        snackbarHostState.showSnackbar(successMessage(reward))
                    }
                    .onFailure { snackbarHostState.showSnackbar(claimError(it.message)) }
            } finally {
                claimingDaily = false
            }
        }
    }

    fun claimTask(task: AcTask) {
        if (claimingTaskId != null) return
        scope.launch {
            claimingTaskId = task.id
            try {
                container.ac.claimTask(task.id)
                    .onSuccess { reward ->
                        refreshAfterClaim()
                        snackbarHostState.showSnackbar(successMessage(reward))
                    }
                    .onFailure { snackbarHostState.showSnackbar(claimError(it.message)) }
            } finally {
                claimingTaskId = null
            }
        }
    }

    fun redeemCode() {
        if (redeeming || codeInput.length != 8) return
        scope.launch {
            redeeming = true
            redeemMessage = null
            detectedTier = null
            try {
                container.ac.redeemCode(codeInput)
                    .onSuccess { amount ->
                        redeemMessageOk = true
                        detectedTier = amount
                        redeemMessage = successMessage(amount)
                        codeInput = ""
                        transactions = container.ac.transactions()
                        tasks = container.ac.tasks()
                    }
                    .onFailure {
                        redeemMessageOk = false
                        redeemMessage = redeemError((it as? ApiError)?.raw ?: it.message)
                    }
            } finally {
                redeeming = false
            }
        }
    }

    val animatedBalance by animateIntAsState(
        targetValue = wallet?.balance ?: 0,
        animationSpec = tween(durationMillis = 700),
        label = "walletBalance",
    )

    Box(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        Column(Modifier.fillMaxSize()) {
            AuthSettingsTopBar(title = androidx.compose.ui.res.stringResource(R.string.ac_title), onBack = onBack)
            Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.AuthBorder))
            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 96.dp),
            ) {
                if (loading && wallet == null) {
                    WalletLoadingSkeleton()
                } else if (failed) {
                    ErrorState(onRetry = { reloadTick += 1 })
                } else {
                    WalletBalanceCard(balance = animatedBalance)

                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.bringIntoViewRequester(redeemRequester)) {
                        WalletSectionLabel(
                            text = androidx.compose.ui.res.stringResource(R.string.ac_redeem_title),
                            accent = AppColors.AuthPurple,
                        )
                        GiftCodeCard(
                            value = codeInput,
                            onValueChange = {
                                codeInput = it.uppercase(Locale.ROOT).filterNot { char -> char.isWhitespace() }.take(8)
                                redeemMessage = null
                                detectedTier = null
                            },
                            redeeming = redeeming,
                            redeemMessage = redeemMessage,
                            redeemMessageOk = redeemMessageOk,
                            detectedTier = detectedTier,
                            onRedeem = { redeemCode() },
                        )
                    }

                    WalletSectionLabel(
                        text = androidx.compose.ui.res.stringResource(R.string.ac_tasks),
                        accent = AppColors.AuthGreen,
                        trailing = {
                            WalletViewAllLink(onClick = { showTasks = true })
                        },
                    )
                    WalletTasksPreviewCard(
                        tasks = tasks.take(3),
                        arabic = isArabic,
                        claimingTaskId = claimingTaskId,
                        onClaim = { task -> claimTask(task) },
                    )

                    WalletSectionLabel(
                        text = androidx.compose.ui.res.stringResource(R.string.ac_history),
                        accent = AppColors.AuthAccentBlue,
                        trailing = { WalletViewAllLink(onClick = onOpenHistory) },
                    )
                    WalletTransactionsCard(transactions = transactions.take(3))
                    InfoCard()
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) { data ->
            Snackbar(
                containerColor = AppColors.AuthSurfaceHigh,
                contentColor = AppColors.AuthText,
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(data.visuals.message, style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp))
            }
        }
    }

    if (showTasks) {
        ModalBottomSheet(
            onDismissRequest = { showTasks = false },
            containerColor = AppColors.AuthSurface,
            contentColor = AppColors.AuthText,
            scrimColor = Color.Black.copy(alpha = 0.60f),
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
            dragHandle = { BottomSheetDefaults.DragHandle(color = AppColors.AuthMuted) },
        ) {
            WalletTasksSheet(
                tasks = tasks,
                arabic = isArabic,
                loading = loading,
                failed = failed,
                claimingTaskId = claimingTaskId,
                onRetry = { reloadTick += 1 },
                onClaim = { task -> claimTask(task) },
                onClose = { showTasks = false },
            )
        }
    }
}

@Composable
private fun WalletBalanceCard(balance: Int) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF3E2D0C), Color(0xFF1B1405))))
            .border(1.dp, Color(0xFF8A6A22), shape)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Image(
            painter = painterResource(R.drawable.coin_ac),
            contentDescription = null,
            modifier = Modifier.size(56.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                "${androidx.compose.ui.res.stringResource(R.string.ac_title)} · ${androidx.compose.ui.res.stringResource(R.string.ac_balance)}",
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                color = Color(0xFFE8D5A3),
                maxLines = 1,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                NumberFormat.getIntegerInstance(Locale.US).format(balance),
                style = TextStyle(fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.ExtraBold),
                color = Color(0xFFFFD66B),
                maxLines = 1,
            )
        }
        Text(
            "AC",
            style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
            color = Color(0xFFE8D5A3),
        )
    }
}

@Composable
private fun CodeSlotsField(
    value: String,
    onValueChange: (String) -> Unit,
    onDone: () -> Unit,
) {
    val borderColor = AppColors.AuthBorder
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            Row(
                Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                repeat(8) { index ->
                    val char = value.getOrNull(index)?.toString().orEmpty()
                    val highlighted = index < value.length || index == value.length
                    val slotShape = RoundedCornerShape(10.dp)
                    Box(
                        Modifier.weight(1f).fillMaxSize().clip(slotShape)
                            .background(AppColors.AuthBg)
                            .border(1.dp, if (highlighted) AppColors.AuthBlueLight else borderColor, slotShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            char,
                            style = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.ExtraBold),
                            color = AppColors.AuthText,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxSize(),
                singleLine = true,
                textStyle = TextStyle(fontSize = 18.sp, lineHeight = 22.sp, color = Color.Transparent),
                cursorBrush = SolidColor(Color.Transparent),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                decorationBox = { innerTextField ->
                    Box(Modifier.fillMaxSize()) { innerTextField() }
                },
            )
        }
    }
}

@Composable
private fun WalletViewAllLink(onClick: () -> Unit) {
    Box(
        Modifier.heightIn(min = 44.dp).clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick).padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            androidx.compose.ui.res.stringResource(R.string.view_all),
            style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold),
            color = AppColors.AuthBlueLight,
            maxLines = 1,
        )
    }
}

@Composable
private fun WalletTasksPreviewCard(
    tasks: List<AcTask>,
    arabic: Boolean,
    claimingTaskId: String?,
    onClaim: (AcTask) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, shape),
    ) {
        if (tasks.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.common_none),
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = AppColors.AuthMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            tasks.forEachIndexed { index, task ->
                TaskClaimRow(
                    task = task,
                    arabic = arabic,
                    claiming = claimingTaskId == task.id,
                    anyClaiming = claimingTaskId != null,
                    onClaim = { onClaim(task) },
                )
                if (index < tasks.lastIndex) {
                    Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(AppColors.AuthDivider))
                }
            }
        }
    }
}

@Composable
private fun GiftCodeCard(
    value: String,
    onValueChange: (String) -> Unit,
    redeeming: Boolean,
    redeemMessage: String?,
    redeemMessageOk: Boolean,
    detectedTier: Int?,
    onRedeem: () -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    val helper = androidx.compose.ui.res.stringResource(R.string.ac_redeem_hint)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, shape)
            .padding(14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            WalletIconTile(Icons.Outlined.CardGiftcard, AppColors.AuthPurple)
            Column(Modifier.weight(1f)) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ac_redeem_title),
                    style = TextStyle(fontSize = 15.sp, lineHeight = 19.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppColors.AuthText,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    helper,
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                    color = AppColors.AuthMuted,
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        CodeSlotsField(value = value, onValueChange = onValueChange, onDone = onRedeem)
        Spacer(Modifier.height(12.dp))
        val enabled = !redeeming && value.length == 8
        Box(
            Modifier
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(if (enabled) AppColors.AuthBlue else AppColors.AuthBlue.copy(alpha = 0.45f))
                .clickable(enabled = enabled, onClick = onRedeem),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                androidx.compose.ui.res.stringResource(if (redeeming) R.string.common_loading else R.string.ac_redeem_action),
                style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold),
                color = AppColors.AuthWhite,
                maxLines = 1,
            )
        }
        redeemMessage?.let { message ->
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
                color = if (redeemMessageOk) AppColors.AuthGreenText else AppColors.AuthRedText,
            )
        }
        detectedTier?.let { tier ->
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp))
                    .background(AppColors.AuthBlue.copy(alpha = 0.12f))
                    .border(1.dp, AppColors.AuthBlueLight.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Outlined.AutoAwesome, contentDescription = null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(18.dp))
                Text(
                    androidx.compose.ui.res.stringResource(
                        R.string.ac_redeem_detected,
                        NumberFormat.getIntegerInstance(Locale.US).format(tier),
                    ),
                    style = TextStyle(fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppColors.AuthBlueLight,
                )
            }
        }
    }
}

private fun oncePerMemberText(source: String): String {
    val trimmed = source.trim().removeSuffix(".").removeSuffix("。")
    val splitAt = maxOf(trimmed.lastIndexOf(". "), trimmed.lastIndexOf("。"))
    return if (splitAt < 0) trimmed else trimmed.substring(splitAt + if (trimmed.startsWith(". ", splitAt)) 2 else 1).trim()
}

@Composable
private fun WalletTransactionsCard(transactions: List<AcTransaction>) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(AppColors.AuthSurface)
            .border(1.dp, AppColors.AuthBorder, shape),
    ) {
        if (transactions.isEmpty()) {
            Box(Modifier.fillMaxWidth().heightIn(min = 72.dp).padding(horizontal = 14.dp), contentAlignment = Alignment.Center) {
                Text(
                    androidx.compose.ui.res.stringResource(R.string.ac_no_transactions),
                    style = TextStyle(fontSize = 13.sp, lineHeight = 18.sp),
                    color = AppColors.AuthMuted,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            transactions.forEachIndexed { index, transaction ->
                WalletOperationRow(transaction)
                if (index < transactions.lastIndex) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .padding(start = 70.dp)
                            .height(1.dp)
                            .background(AppColors.AuthDivider),
                    )
                }
            }
        }
    }
}

@Composable
private fun WalletOperationRow(transaction: AcTransaction) {
    val balanceLabel = androidx.compose.ui.res.stringResource(R.string.admin_give_ac_balance_after)
    val locale = Locale.forLanguageTag(appLocaleCode())
    val numberFormat = NumberFormat.getIntegerInstance(Locale.US)
    val balance = numberFormat.format(transaction.balanceAfter)
    val date = TimeFormat.dateTime(transaction.createdAt, locale)
    val amount = when {
        transaction.amount > 0 -> "+${numberFormat.format(transaction.amount)} AC"
        transaction.amount < 0 -> "−${numberFormat.format(abs(transaction.amount.toLong()))} AC"
        else -> "0 AC"
    }
    WalletTransactionRow(
        transaction = transaction,
        title = acKindLabel(transaction.kind),
        balanceSummary = "$balanceLabel $balance · $date",
        amountText = amount,
    )
}

@Composable
private fun InfoCard() {
    val shape = RoundedCornerShape(16.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(shape)
            .background(AppColors.AuthBarSurface)
            .border(1.dp, AppColors.AuthBorder, shape)
            .padding(14.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(Icons.Filled.Info, contentDescription = null, tint = AppColors.AuthBlueLight, modifier = Modifier.size(22.dp))
        Text(
            androidx.compose.ui.res.stringResource(R.string.ac_desc),
            style = TextStyle(fontSize = 12.sp, lineHeight = 18.sp),
            color = AppColors.AuthChipLabel,
        )
    }
}

@Composable
private fun WalletTasksSheet(
    tasks: List<AcTask>,
    arabic: Boolean,
    loading: Boolean,
    failed: Boolean,
    claimingTaskId: String?,
    onRetry: () -> Unit,
    onClaim: (AcTask) -> Unit,
    onClose: () -> Unit,
) {
    val allTasksTitle = androidx.compose.ui.res.stringResource(R.string.ac_tasks)
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 640.dp)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                allTasksTitle,
                style = TextStyle(fontFamily = PixelFont, fontSize = 15.sp, lineHeight = 20.sp),
                color = AppColors.AuthText,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = androidx.compose.ui.res.stringResource(R.string.common_close), tint = AppColors.AuthMuted)
            }
        }
        when {
            loading -> LoadingBox(text = androidx.compose.ui.res.stringResource(R.string.common_loading))
            failed -> ErrorState(onRetry = onRetry)
            tasks.isEmpty() -> EmptyState(androidx.compose.ui.res.stringResource(R.string.common_none))
            else -> {
                val shape = RoundedCornerShape(18.dp)
                Column(Modifier.fillMaxWidth().clip(shape).background(AppColors.AuthSurface)
                    .border(1.dp, AppColors.AuthBorder, shape)) {
                    tasks.forEachIndexed { index, task ->
                        TaskClaimRow(
                            task = task,
                            arabic = arabic,
                            claiming = claimingTaskId == task.id,
                            anyClaiming = claimingTaskId != null,
                            onClaim = { onClaim(task) },
                        )
                        if (index < tasks.lastIndex) Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(AppColors.AuthDivider))
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskClaimRow(
    task: AcTask,
    arabic: Boolean,
    claiming: Boolean,
    anyClaiming: Boolean,
    onClaim: () -> Unit,
) {
    val manual = task.frequency == "manual"
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        WalletIconTile(Icons.Outlined.AutoAwesome, AppColors.AuthBlueLight, size = 40.dp)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                if (arabic) task.titleAr else (task.titleEn ?: task.titleAr),
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.ExtraBold),
                color = AppColors.AuthText,
                maxLines = 2,
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    acFrequencyLabel(task.frequency),
                    modifier = Modifier.clip(RoundedCornerShape(11.dp)).background(AppColors.AuthSurfaceHigh)
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                    style = TextStyle(fontSize = 10.sp, lineHeight = 14.sp, fontWeight = FontWeight.ExtraBold),
                    color = AppColors.AuthChipLabel,
                    maxLines = 1,
                )
                Text(
                    "+${task.reward} AC",
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Bold),
                    color = AppColors.AuthMuted,
                    maxLines = 1,
                )
            }
        }
        if (manual) {
            Box(
                Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(AppColors.AuthSurfaceHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Lock, contentDescription = null, tint = AppColors.AuthMuted, modifier = Modifier.size(18.dp))
            }
        } else {
            Box(
                Modifier.height(40.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (anyClaiming) AppColors.AuthSurfaceHigh else AppColors.AuthBlue)
                    .clickable(enabled = !anyClaiming, onClick = onClaim)
                    .padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    androidx.compose.ui.res.stringResource(if (claiming) R.string.common_loading else R.string.ac_claim),
                    style = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.ExtraBold),
                    color = if (anyClaiming) AppColors.AuthMuted else AppColors.AuthWhite,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Full history route; data is still read through AcRepository.transactions(limit). */
@Composable
fun AcTransactionHistoryScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    var transactions by remember { mutableStateOf<List<AcTransaction>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }

    LaunchedEffect(reloadTick) {
        loading = true
        failed = false
        try {
            transactions = container.ac.transactions(limit = 50)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize().background(AppColors.AuthBg)) {
        AuthSettingsTopBar(title = androidx.compose.ui.res.stringResource(R.string.ac_history), onBack = onBack)
        Box(Modifier.fillMaxWidth().height(1.dp).background(AppColors.AuthBorder))
        when {
            loading -> LoadingBox(text = androidx.compose.ui.res.stringResource(R.string.common_loading))
            failed -> ErrorState(onRetry = { reloadTick += 1 })
            transactions.isEmpty() -> EmptyState(androidx.compose.ui.res.stringResource(R.string.ac_no_transactions))
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 96.dp),
            ) {
                item {
                    WalletTransactionsCard(transactions = transactions)
                }
            }
        }
    }
}

@Composable
private fun acFrequencyLabel(frequency: String): String = when (frequency) {
    "daily" -> androidx.compose.ui.res.stringResource(R.string.ac_freq_daily)
    "streak" -> androidx.compose.ui.res.stringResource(R.string.ac_freq_streak)
    "manual" -> androidx.compose.ui.res.stringResource(R.string.ac_freq_manual)
    else -> androidx.compose.ui.res.stringResource(R.string.ac_freq_once)
}

@Composable
private fun acKindLabel(kind: String): String = when (kind) {
    "task" -> androidx.compose.ui.res.stringResource(R.string.ac_kind_task)
    "daily" -> androidx.compose.ui.res.stringResource(R.string.ac_kind_daily)
    "streak" -> androidx.compose.ui.res.stringResource(R.string.ac_kind_streak)
    "admin_reward", "admin_adjustment" -> androidx.compose.ui.res.stringResource(R.string.admin_reward)
    "unlock" -> androidx.compose.ui.res.stringResource(R.string.ac_kind_unlock)
    "event" -> androidx.compose.ui.res.stringResource(R.string.ac_kind_event)
    "welcome" -> androidx.compose.ui.res.stringResource(R.string.ac_kind_welcome)
    "code" -> androidx.compose.ui.res.stringResource(R.string.ac_kind_code)
    else -> kind
}
