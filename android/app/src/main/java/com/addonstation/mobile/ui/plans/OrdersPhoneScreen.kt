package com.addonstation.mobile.ui.plans

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.PaymentOrder
import com.addonstation.mobile.data.model.SubscriptionPlan
import com.addonstation.mobile.ui.common.FullScreenImageViewer
import com.addonstation.mobile.ui.components.ContentBannerKind
import com.addonstation.mobile.ui.components.ContentStatusBanner
import com.addonstation.mobile.ui.components.ErrorKind
import com.addonstation.mobile.ui.components.ErrorState
import com.addonstation.mobile.ui.components.LoadingPattern
import com.addonstation.mobile.ui.components.LoadingState
import com.addonstation.mobile.ui.components.PhoneChipRow
import com.addonstation.mobile.ui.components.PhoneEmptyState
import com.addonstation.mobile.ui.components.PhoneListChip
import com.addonstation.mobile.ui.components.PhoneListHeader
import com.addonstation.mobile.ui.components.PhoneStatusChip
import com.addonstation.mobile.ui.components.PhoneStatusTone
import com.addonstation.mobile.ui.components.toErrorKind
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.ui.theme.LocalHomePalette
import com.addonstation.mobile.util.TimeFormat
import com.addonstation.mobile.util.prepareImageForUpload
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

/** Redesigned order history with counts, contextual actions, and in-place details. */
@Composable
fun MyOrdersScreen(
    onBack: () -> Unit,
    onViewPlans: () -> Unit = {},
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val plans by container.state.plans.collectAsState()
    var orders by remember { mutableStateOf<List<PaymentOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var errorKind by remember { mutableStateOf(ErrorKind.Network) }
    var reloadTick by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("all") }
    var detail by remember { mutableStateOf<PaymentOrder?>(null) }
    var uploadOrder by remember { mutableStateOf<PaymentOrder?>(null) }
    var receiptViewerUrl by remember { mutableStateOf<String?>(null) }

    val failedMessage = stringResource(R.string.state_refresh_failed)
    val receiptPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        val order = uploadOrder
        uploadOrder = null
        if (uri == null || order == null) return@rememberLauncherForActivityResult
        scope.launch {
            val prepared = prepareImageForUpload(context, uri)
            val bytes = prepared?.bytes
                ?: runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null) {
                snackbar.showSnackbar(failedMessage)
                return@launch
            }
            val result = container.plans.submitReceipt(
                order = order,
                imageBytes = bytes,
                mimeType = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/jpeg",
                fileName = prepared?.fileName ?: "receipt.jpg",
                transactionReference = null,
            )
            if (result.isSuccess) reloadTick += 1 else snackbar.showSnackbar(failedMessage)
        }
    }

    LaunchedEffect(reloadTick) {
        loading = true
        failed = false
        try {
            orders = container.plans.myOrdersStrict()
            if (plans.isEmpty()) container.plans.loadPlans()
        } catch (error: kotlinx.coroutines.CancellationException) {
            throw error
        } catch (error: Throwable) {
            android.util.Log.w("MyOrdersScreen", "Order history load failed", error)
            errorKind = error.toErrorKind()
            failed = true
        } finally {
            loading = false
        }
    }

    val counts = mapOf(
        "all" to orders.size,
        "pending" to orders.count { it.status == "pending" },
        "submitted" to orders.count { it.status == "submitted" },
        "approved" to orders.count { it.status == "approved" },
        "rejected" to orders.count { it.status == "rejected" },
    )
    val visible = if (filter == "all") orders else orders.filter { it.status == filter }

    Box(Modifier.fillMaxSize().background(LocalHomePalette.current.bg)) {
        Column(Modifier.fillMaxSize()) {
            PhoneListHeader(
                title = stringResource(R.string.plan_my_orders),
                count = orders.size,
                onBack = onBack,
            )
            PhoneChipRow(
                chips = listOf(
                    PhoneListChip("all", stringResource(R.string.common_all), counts["all"]),
                    PhoneListChip("pending", stringResource(R.string.plan_status_pending), counts["pending"]),
                    PhoneListChip("submitted", stringResource(R.string.list_in_review), counts["submitted"]),
                    PhoneListChip("approved", stringResource(R.string.list_active), counts["approved"]),
                    PhoneListChip("rejected", stringResource(R.string.plan_status_rejected), counts["rejected"]),
                ),
                selectedKey = filter,
                onSelected = { filter = it },
            )
            if (loading && orders.isNotEmpty()) LoadingState(pattern = LoadingPattern.Refreshing)
            if (failed && orders.isNotEmpty()) {
                ContentStatusBanner(ContentBannerKind.ServerRefresh, onRetry = { reloadTick += 1 })
            }
            when {
                loading && orders.isEmpty() -> LoadingState(
                    pattern = LoadingPattern.Skeleton,
                    onRetry = { reloadTick += 1 },
                )
                failed && orders.isEmpty() -> ErrorState(
                    onRetry = { reloadTick += 1 },
                    kind = errorKind,
                    modifier = Modifier.weight(1f),
                )
                visible.isEmpty() -> PhoneEmptyState(
                    icon = Icons.Filled.ReceiptLong,
                    color = AppColors.Purple,
                    title = stringResource(R.string.orders_empty_title),
                    body = stringResource(R.string.orders_empty_body),
                    actionText = stringResource(R.string.orders_see_plans),
                    onAction = onViewPlans,
                    modifier = Modifier.weight(1f),
                )
                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(visible, key = PaymentOrder::id) { order ->
                        OrderCard(
                            order = order,
                            plan = plans.firstOrNull { it.id == order.planId },
                            onOpen = { detail = order },
                            onUploadReceipt = {
                                uploadOrder = order
                                receiptPicker.launch("image/*")
                            },
                            onViewPlan = onViewPlans,
                            onTryAgain = onViewPlans,
                        )
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 84.dp))
    }

    detail?.let { order ->
        OrderDetailsDialog(
            order = order,
            plan = plans.firstOrNull { it.id == order.planId },
            onClose = { detail = null },
            onViewReceipt = {
                scope.launch { receiptViewerUrl = container.plans.receiptUrl(order) }
            },
        )
    }
    receiptViewerUrl?.let { url -> FullScreenImageViewer(url) { receiptViewerUrl = null } }
}

@Composable
private fun OrderCard(
    order: PaymentOrder,
    plan: SubscriptionPlan?,
    onOpen: () -> Unit,
    onUploadReceipt: () -> Unit,
    onViewPlan: () -> Unit,
    onTryAgain: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val legendary = order.planId == "legendary"
    val planColor = if (legendary) AppColors.Gold else AppColors.Purple
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp)).clickable(onClick = onOpen).padding(13.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(15.dp)).background(planColor.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(if (legendary) Icons.Filled.EmojiEvents else Icons.Filled.Diamond, null, tint = planColor, modifier = Modifier.size(25.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    plan?.name ?: order.planId.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                    style = AppText.bodySmall.copy(fontWeight = FontWeight.ExtraBold),
                    color = palette.text,
                )
                Text(
                    stringResource(R.string.orders_duration_days, plan?.durationDays ?: 30),
                    style = AppText.tiny,
                    color = palette.textMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${formatAmount(order.amountLyd)} LYD", style = AppText.sectionTitle, color = palette.text)
                Text(
                    stringResource(R.string.orders_number, order.referenceCode),
                    style = AppText.tiny,
                    color = palette.blueLink,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(palette.blue.copy(alpha = 0.11f))
                        .clickable { clipboard.setText(AnnotatedString(order.referenceCode)) }
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OrderStatusChip(order.status)
            Text(TimeFormat.dateOnly(order.createdAt, Locale.getDefault()), style = AppText.tiny, color = palette.textMuted)
            Text("· ${order.paymentMethod}", style = AppText.tiny, color = palette.textMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        when (order.status) {
            "pending" -> OrderFooterAction(
                text = stringResource(R.string.orders_transfer_upload),
                actionText = stringResource(R.string.orders_upload_receipt),
                color = palette.blue,
                onAction = onUploadReceipt,
            )
            "submitted" -> OrderFooterAction(
                text = stringResource(R.string.orders_checking_receipt),
                actionText = null,
                color = AppColors.Blue,
                onAction = {},
            )
            "approved" -> OrderFooterAction(
                text = stringResource(
                    R.string.orders_active_until,
                    approximateExpiry(order, plan?.durationDays ?: 30),
                ),
                actionText = stringResource(R.string.orders_view_plan),
                color = AppColors.Green,
                onAction = onViewPlan,
            )
            "rejected" -> Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(AppColors.Red.copy(alpha = 0.10f)).padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Text(
                    stringResource(R.string.list_reason, order.rejectReason ?: "—"),
                    style = AppText.tiny,
                    color = AppColors.Red,
                )
                Text(
                    stringResource(R.string.orders_try_again),
                    style = AppText.button,
                    color = AppColors.Red,
                    modifier = Modifier.clickable(onClick = onTryAgain),
                )
            }
        }
    }
}

@Composable
private fun OrderFooterAction(text: String, actionText: String?, color: Color, onAction: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(color.copy(alpha = 0.09f)).padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text, style = AppText.tiny, color = color, modifier = Modifier.weight(1f))
        actionText?.let {
            Text(it, style = AppText.button, color = color, modifier = Modifier.clickable(onClick = onAction).padding(4.dp))
        }
    }
}

@Composable
private fun OrderStatusChip(status: String) {
    val (text, tone) = when (status) {
        "approved" -> stringResource(R.string.list_active) to PhoneStatusTone.Green
        "submitted" -> stringResource(R.string.list_in_review) to PhoneStatusTone.Blue
        "rejected" -> stringResource(R.string.plan_status_rejected) to PhoneStatusTone.Red
        else -> stringResource(R.string.plan_status_pending) to PhoneStatusTone.Orange
    }
    PhoneStatusChip(text, tone)
}

@Composable
private fun OrderDetailsDialog(
    order: PaymentOrder,
    plan: SubscriptionPlan?,
    onClose: () -> Unit,
    onViewReceipt: () -> Unit,
) {
    val palette = LocalHomePalette.current
    val clipboard = LocalClipboardManager.current
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().heightIn(max = 680.dp)
                .clip(RoundedCornerShape(24.dp)).background(palette.bg)
                .border(1.dp, palette.border, RoundedCornerShape(24.dp))
                .verticalScroll(rememberScrollState()).padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.plan_my_orders), style = AppText.pageTitle, color = palette.text, modifier = Modifier.weight(1f))
                Text(stringResource(R.string.common_close), style = AppText.button, color = palette.blueLink, modifier = Modifier.clickable(onClick = onClose))
            }
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(palette.surface).border(1.dp, palette.border, RoundedCornerShape(16.dp)).padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(plan?.name ?: order.planId, style = AppText.sectionTitle, color = palette.text)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.orders_number, order.referenceCode),
                        style = AppText.tiny,
                        color = palette.blueLink,
                        modifier = Modifier.clickable { clipboard.setText(AnnotatedString(order.referenceCode)) },
                    )
                    Spacer(Modifier.weight(1f))
                    OrderStatusChip(order.status)
                }
            }
            DetailValue(stringResource(R.string.orders_amount), "${formatAmount(order.amountLyd)} LYD")
            DetailValue(stringResource(R.string.orders_method), order.paymentMethod)
            DetailValue(stringResource(R.string.orders_transaction_reference), order.transactionReference ?: "—")
            DetailValue(stringResource(R.string.orders_created), TimeFormat.dateTime(order.createdAt, Locale.getDefault()))
            Text(stringResource(R.string.orders_progress), style = AppText.sectionTitle, color = palette.text)
            OrderTimeline(order.status)
            order.receiptUrl?.let {
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(palette.surface).border(1.dp, palette.border, RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.ReceiptLong, null, tint = palette.blueLink)
                    Spacer(Modifier.width(9.dp))
                    Text(stringResource(R.string.orders_receipt), style = AppText.bodySmall, color = palette.text, modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.orders_view), style = AppText.button, color = palette.blueLink, modifier = Modifier.clickable(onClick = onViewReceipt))
                }
            }
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    val palette = LocalHomePalette.current
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = AppText.tiny, color = palette.textMuted, modifier = Modifier.weight(0.8f))
        Text(value, style = AppText.bodySmall, color = palette.text, modifier = Modifier.weight(1.2f), textAlign = TextAlign.End)
    }
}

@Composable
private fun OrderTimeline(status: String) {
    val labels = listOf(
        stringResource(R.string.orders_created_step),
        stringResource(R.string.orders_receipt_uploaded_step),
        stringResource(R.string.orders_review_step),
        stringResource(R.string.orders_activated_step),
    )
    val current = when (status) {
        "approved" -> 3
        "submitted" -> 2
        "rejected" -> 2
        else -> 0
    }
    val palette = LocalHomePalette.current
    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        labels.forEachIndexed { index, label ->
            val complete = index < current || status == "approved"
            val active = index == current && status != "approved"
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(24.dp).clip(CircleShape)
                            .background(if (complete) AppColors.Green else Color.Transparent)
                            .border(2.dp, when { complete -> AppColors.Green; active -> palette.blue; else -> palette.border }, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (complete) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(15.dp))
                        else if (active) Icon(Icons.Filled.HourglassTop, null, tint = palette.blue, modifier = Modifier.size(13.dp))
                    }
                    if (index != labels.lastIndex) Box(Modifier.width(2.dp).height(25.dp).background(if (index < current) AppColors.Green else palette.border))
                }
                Column(Modifier.padding(top = 3.dp)) {
                    Text(label, style = AppText.bodySmall, color = if (complete || active) palette.text else palette.textMuted)
                    if (active) Text(stringResource(R.string.orders_usually_hours), style = AppText.tiny, color = palette.textMuted)
                }
            }
        }
    }
}

private fun formatAmount(value: Double): String = if (value % 1.0 == 0.0) value.toLong().toString() else "%.2f".format(Locale.US, value)

private fun approximateExpiry(order: PaymentOrder, days: Int): String {
    val base = TimeFormat.parseMillis(order.reviewedAt ?: order.createdAt) ?: System.currentTimeMillis()
    val calendar = Calendar.getInstance().apply {
        timeInMillis = base
        add(Calendar.DAY_OF_YEAR, days)
    }
    return SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(calendar.timeInMillis))
}
