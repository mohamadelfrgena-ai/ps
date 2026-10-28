package com.addonstation.mobile.ui.plans

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.WorkspacePremium
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.addonstation.mobile.R
import com.addonstation.mobile.data.model.PaymentMethodSetting
import com.addonstation.mobile.data.model.PaymentOrder
import com.addonstation.mobile.data.model.SubscriptionPlan
import com.addonstation.mobile.ui.components.EmptyState
import com.addonstation.mobile.ui.components.FilterChip
import com.addonstation.mobile.ui.components.GhostButton
import com.addonstation.mobile.ui.components.GlassCard
import com.addonstation.mobile.ui.components.GradientButton
import com.addonstation.mobile.ui.components.LoadingBox
import com.addonstation.mobile.ui.components.PixelTopBar
import com.addonstation.mobile.ui.components.SectionHeader
import com.addonstation.mobile.ui.components.SubScreenHeader
import com.addonstation.mobile.ui.rememberContainer
import com.addonstation.mobile.ui.theme.AppColors
import com.addonstation.mobile.ui.theme.AppText
import com.addonstation.mobile.util.TimeFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

/** شاشة الاشتراكات: ثلاثة بطاقات، والأسعار تُقرأ من الخادم دائماً (لا شيء مثبّت في التطبيق). */
@Composable
fun PlansScreen(
    onBack: () -> Unit,
    onCheckout: (SubscriptionPlan) -> Unit,
    onOpenOrders: () -> Unit,
) {
    val container = rememberContainer()
    val plans by container.state.plans.collectAsState()
    val profile by container.state.profile.collectAsState()
    val wallet by container.state.wallet.collectAsState()

    LaunchedEffect(Unit) {
        container.plans.loadPlans()
        container.plans.loadPaymentMethods()
    }

    Column(Modifier.fillMaxSize()) {
        PixelTopBar(
            title = stringResource(R.string.plan_title),
            onLogoClick = onBack,
            walletBalance = wallet?.balance,
            icon = Icons.Filled.WorkspacePremium,
        )
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.plan_label), style = AppText.kicker, color = AppColors.Blue)
            Text(stringResource(R.string.plan_intro), style = AppText.bodySmall, color = AppColors.Muted)
            Spacer(Modifier.height(14.dp))

            if (plans.isEmpty()) {
                LoadingBox(text = stringResource(R.string.common_loading))
            }

            plans.sortedBy { it.sortOrder }.forEach { plan ->
                PlanCard(
                    plan = plan,
                    isCurrent = (profile?.effectivePlan ?: "free") == plan.id,
                    onSelect = { onCheckout(plan) },
                )
                Spacer(Modifier.height(12.dp))
            }

            GhostButton(
                text = stringResource(R.string.plan_my_orders),
                onClick = onOpenOrders,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.plan_payment_note), style = AppText.tiny, color = AppColors.Dim)
            Spacer(Modifier.height(96.dp))
        }
    }
}

/**
 * سعر الخطة كما يراه المستخدم: «25 د.ل · 2.99$ / 30 يوم».
 * الدينار أولاً لأنه المبلغ المدفوع فعلياً، والدولار مرجعاً بجانبه
 * (يُحذف إن كان صفراً حتى لا يظهر «0$» بجانب الخطة المجانية).
 */
@Composable
private fun planPriceLine(plan: SubscriptionPlan): String {
    val lyd = moneyText(plan.priceLyd)
    val days = plan.durationDays.toString()
    return if (plan.priceUsd > 0.0) {
        stringResource(R.string.plan_price_line_usd, lyd, moneyText(plan.priceUsd), days)
    } else {
        stringResource(R.string.plan_price_line, lyd, days)
    }
}

/** يزيل الكسر الصفري: 25.0 → «25» و2.99 → «2.99». */
private fun moneyText(value: Double): String =
    if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()

@Composable
private fun PlanCard(plan: SubscriptionPlan, isCurrent: Boolean, onSelect: () -> Unit) {
    val (accent, badgeBg, emoji) = when (plan.id) {
        "legendary" -> Triple(AppColors.Gold, AppColors.GoldBadgeBg, "👑")
        "epic" -> Triple(AppColors.Purple, AppColors.PurpleBadgeBg, "💎")
        else -> Triple(AppColors.FreeGray, AppColors.GrayBadgeBg, "🍃")
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.Surface)
            .border(
                1.dp,
                if (isCurrent) AppColors.Green else accent.copy(alpha = 0.35f),
                RoundedCornerShape(16.dp),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(emoji, style = AppText.welcomeName)
            Text(plan.name, style = AppText.sectionTitle, color = accent)
            Spacer(Modifier.weight(1f))
            if (isCurrent) {
                Text(
                    stringResource(R.string.plan_current_free).takeIf { plan.isFree } ?: "✓",
                    style = AppText.tiny,
                    color = AppColors.Green,
                )
            }
        }
        plan.tagline?.let { Text(it, style = AppText.bodySmall, color = AppColors.Muted) }
        Text(
            text = if (plan.isFree) stringResource(R.string.plan_free_features) else planPriceLine(plan),
            style = AppText.bodySmall,
            color = AppColors.Text,
        )
        if (!plan.isFree) {
            GradientButton(
                text = if (plan.id == "legendary") stringResource(R.string.plan_join_legendary_plain)
                else stringResource(R.string.plan_choose),
                onClick = onSelect,
                colors = listOf(accent, accent.copy(alpha = 0.7f)),
                textColor = if (plan.id == "legendary") AppColors.GoldText else Color.Black,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            GhostButton(
                text = stringResource(R.string.plan_start_now),
                onClick = onSelect,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

/** صفحة الدفع: تُنشئ الطلب على الخادم (المبلغ من قاعدة البيانات) ثم تُظهر بيانات التحويل والإيصال. */
@Composable
fun CheckoutScreen(
    planId: String,
    onBack: () -> Unit,
    onOpenOrders: () -> Unit,
    onLogin: () -> Unit,
    onOpenPlans: () -> Unit,
) {
    val container = rememberContainer()
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val signedIn by container.state.signedIn.collectAsState()
    val plans by container.state.plans.collectAsState()
    val methods by container.state.paymentMethods.collectAsState()

    var plan by remember { mutableStateOf<SubscriptionPlan?>(null) }
    var method by remember { mutableStateOf<PaymentMethodSetting?>(null) }
    var methodMenu by remember { mutableStateOf(false) }
    var order by remember { mutableStateOf<PaymentOrder?>(null) }
    var payUrl by remember { mutableStateOf<String?>(null) }
    var receiptBytes by remember { mutableStateOf<ByteArray?>(null) }
    var receiptName by remember { mutableStateOf<String?>(null) }
    var receiptMime by remember { mutableStateOf("image/png") }
    var txRef by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val sentMsg = stringResource(R.string.plan_order_sent_plain)
    val chooseReceipt = stringResource(R.string.plan_choose_receipt)
    val unavailable = stringResource(R.string.plan_unavailable)
    val payOnlineLabel = stringResource(R.string.plan_pay_online)
    val gatewayWaiting = stringResource(R.string.plan_gateway_waiting)
    val gatewayApproved = stringResource(R.string.plan_gateway_approved)
    val gatewayRejected = stringResource(R.string.plan_gateway_rejected)

    LaunchedEffect(planId) {
        if (plans.isEmpty()) container.plans.loadPlans()
        container.plans.loadPaymentMethods()
    }
    LaunchedEffect(plans, planId) {
        plan = plans.firstOrNull { it.id == planId }
        method = methods.firstOrNull()
    }

    // بوابة الدفع: مراقبة الطلب كل 5 ثوانٍ حتى يأكدّه المزوّد (gateway_confirm_order)
    LaunchedEffect(order?.id, payUrl) {
        val id = order?.id ?: return@LaunchedEffect
        if (payUrl == null) return@LaunchedEffect
        while (true) {
            delay(5000)
            val fresh = container.plans.orderById(id) ?: continue
            order = fresh
            if (fresh.status == "approved") {
                status = gatewayApproved
                break
            }
            if (fresh.status == "rejected" || fresh.status == "cancelled") {
                error = gatewayRejected
                break
            }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val prepared = com.addonstation.mobile.util.prepareImageForUpload(context, uri, maxSide = 1600)
            receiptBytes = prepared?.bytes
                ?: context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            receiptMime = prepared?.mime ?: context.contentResolver.getType(uri) ?: "image/png"
            receiptName = prepared?.fileName ?: "receipt-${System.currentTimeMillis()}.jpg"
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.plan_checkout), onBack = onBack)
        Column(
            Modifier
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 14.dp),
        ) {
            val currentPlan = plan
            if (currentPlan == null) {
                LoadingBox(text = stringResource(R.string.common_loading))
                return@Column
            }
            if (!currentPlan.active) {
                EmptyState(unavailable)
                return@Column
            }
            if (!signedIn) {
                EmptyState(stringResource(R.string.msg_sign_in_needed))
                GradientButton(
                    text = stringResource(R.string.auth_sign_in),
                    onClick = onLogin,
                    modifier = Modifier.fillMaxWidth(),
                )
                return@Column
            }

            GlassCard {
                Text(currentPlan.name, style = AppText.sectionTitle, color = AppColors.Gold)
                Text(
                    if (currentPlan.isFree) stringResource(R.string.plan_free_features)
                    else planPriceLine(currentPlan),
                    style = AppText.body,
                    color = AppColors.Text,
                )
                currentPlan.tagline?.let { Text(it, style = AppText.tiny, color = AppColors.Muted) }
            }

            SectionHeader(title = stringResource(R.string.plan_payment_method), leadingEmoji = "💳")
            GlassCard(padding = 10.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Elevated)
                        .clickable { methodMenu = true }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        method?.name ?: stringResource(R.string.plan_payment_method),
                        style = AppText.bodySmall,
                        color = AppColors.Text,
                        modifier = Modifier.weight(1f),
                    )
                    Text("▾", style = AppText.bodySmall, color = AppColors.Muted)
                }
                DropdownMenu(
                    expanded = methodMenu,
                    onDismissRequest = { methodMenu = false },
                    containerColor = AppColors.Elevated,
                ) {
                    methods.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option.name, style = AppText.bodySmall, color = AppColors.Text) },
                            onClick = {
                                method = option
                                methodMenu = false
                            },
                        )
                    }
                }
                if (methods.isEmpty()) {
                    Text(stringResource(R.string.plan_unavailable), style = AppText.tiny, color = AppColors.Red)
                }
            }

            Spacer(Modifier.height(14.dp))
            if (order == null) {
                GradientButton(
                    text = if (method?.provider != "manual") payOnlineLabel
                    else stringResource(R.string.plan_create_order),
                    onClick = {
                        val selectedMethod = method
                        if (selectedMethod == null) {
                            error = unavailable
                            return@GradientButton
                        }
                        scope.launch {
                            busy = true
                            error = null
                            if (selectedMethod.provider != "manual") {
                                // بوابة الدفع: طلب + رابط دفع جاهز (Key + ID تبقى على الخادم)
                                container.plans.startGatewayPayment(currentPlan.id, selectedMethod.code)
                                    .onSuccess { gp ->
                                        payUrl = gp.payUrl
                                        order = PaymentOrder(
                                            id = gp.orderId,
                                            userId = "",
                                            planId = currentPlan.id,
                                            amountLyd = currentPlan.priceLyd,
                                            paymentMethod = selectedMethod.code,
                                            referenceCode = gp.referenceCode,
                                            status = "pending",
                                        )
                                        status = gatewayWaiting
                                        runCatching {
                                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(gp.payUrl)))
                                        }
                                    }
                                    .onFailure { error = it.message }
                            } else {
                                container.plans.createOrder(currentPlan.id, selectedMethod.code)
                                    .onSuccess { created ->
                                        order = created
                                        status = null
                                    }
                                    .onFailure { error = it.message }
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                val current = order!!
                GlassCard(color = AppColors.Gold.copy(alpha = 0.08f), borderColor = AppColors.Gold.copy(alpha = 0.35f)) {
                    Text(stringResource(R.string.plan_order_ready), style = AppText.sectionTitle, color = AppColors.Gold)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.plan_order_number), style = AppText.tiny, color = AppColors.Muted)
                        Text(current.referenceCode, style = AppText.sectionTitle, color = AppColors.Text)
                        Icon(
                            Icons.Filled.ContentCopy,
                            contentDescription = stringResource(R.string.plan_copy),
                            tint = AppColors.Blue,
                            modifier = Modifier
                                .size(18.dp)
                                .clickable { clipboard.setText(AnnotatedString(current.referenceCode)) },
                        )
                    }
                    Text(stringResource(R.string.plan_order_instructions), style = AppText.bodySmall, color = AppColors.Muted)

                    if (payUrl == null) {
                    // [جديد] بيانات المستفيد ورقم الحساب وتعليمات الأدمن — مع زر نسخ
                    method?.let { selected ->
                        selected.accountName?.let {
                            DetailLine(stringResource(R.string.plan_beneficiary), it, clipboard)
                        }
                        selected.accountIdentifier?.let {
                            DetailLine(stringResource(R.string.plan_account_number), it, clipboard)
                        }
                        selected.instructions?.let {
                            DetailLine(stringResource(R.string.plan_order_instructions), it, null)
                        }
                    }

                    Text(stringResource(R.string.plan_transaction_ref), style = AppText.tiny, color = AppColors.Muted)
                    OutlinedTextField(
                        value = txRef,
                        onValueChange = { txRef = it },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GhostButton(
                        text = if (receiptName == null) stringResource(R.string.plan_receipt)
                        else receiptName!!,
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    GradientButton(
                        text = stringResource(R.string.plan_submit_receipt),
                        onClick = {
                            val bytes = receiptBytes
                            if (bytes == null) {
                                error = chooseReceipt
                                return@GradientButton
                            }
                            scope.launch {
                                busy = true
                                error = null
                                container.plans.submitReceipt(
                                    order = current,
                                    imageBytes = bytes,
                                    mimeType = receiptMime,
                                    fileName = receiptName ?: "receipt.png",
                                    transactionReference = txRef.takeIf { it.isNotBlank() },
                                ).onSuccess {
                                    status = sentMsg
                                    onOpenOrders()
                                }.onFailure { error = it.message }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    } else {
                        // بوابة الدفع: رابط الدفع + حالة الطلب (الإيصال غير مطلوب)
                        Text(
                            if (current.status == "approved") gatewayApproved else gatewayWaiting,
                            style = AppText.bodySmall,
                            color = AppColors.Muted,
                        )
                        GhostButton(
                            text = payOnlineLabel,
                            onClick = {
                                val url = payUrl
                                if (url != null) runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Red)
            }
            status?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, style = AppText.bodySmall, color = AppColors.Green)
            }
            Spacer(Modifier.height(96.dp))
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String, clipboard: androidx.compose.ui.platform.ClipboardManager?) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = AppText.tiny, color = AppColors.Muted)
        Text(value, style = AppText.bodySmall, color = AppColors.Text, modifier = Modifier.weight(1f))
        if (clipboard != null) {
            Icon(
                Icons.Filled.ContentCopy,
                contentDescription = stringResource(R.string.plan_copy),
                tint = AppColors.Blue,
                modifier = Modifier
                    .size(16.dp)
                    .clickable { clipboard.setText(AnnotatedString(value)) },
            )
        }
    }
}

/** «طلباتي»: المرجع، الخطة، المبلغ، الطريقة، وحالة الطلب مع سبب الرفض. */
@Composable
fun MyOrdersScreen(onBack: () -> Unit) {
    val container = rememberContainer()
    val scope = rememberCoroutineScope()
    var orders by remember { mutableStateOf<List<PaymentOrder>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var failed by remember { mutableStateOf(false) }
    var reloadTick by remember { mutableStateOf(0) }
    var filter by remember { mutableStateOf("all") }

    LaunchedEffect(filter, reloadTick) {
        loading = true
        failed = false
        try {
            orders = container.plans.myOrders().filter { filter == "all" || it.status == filter }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Throwable) {
            failed = true
        } finally {
            loading = false
        }
    }

    Column(Modifier.fillMaxSize()) {
        SubScreenHeader(title = stringResource(R.string.plan_my_orders), onBack = onBack)
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(
                "all" to stringResource(R.string.common_all),
                "pending" to stringResource(R.string.plan_status_pending),
                "submitted" to stringResource(R.string.plan_status_submitted),
                "approved" to stringResource(R.string.plan_status_approved),
                "rejected" to stringResource(R.string.plan_status_rejected),
            ).forEach { (key, label) ->
                FilterChip(label = label, selected = filter == key, onClick = { filter = key })
            }
        }
        if (loading) {
            LoadingBox(text = stringResource(R.string.common_loading))
        } else if (failed) {
            com.addonstation.mobile.ui.components.ErrorState(onRetry = { reloadTick += 1 })
        } else if (orders.isEmpty()) {
            EmptyState(stringResource(R.string.common_none))
        } else {
            LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(orders, key = { it.id }) { order ->
                    GlassCard(padding = 12.dp) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(order.referenceCode, style = AppText.sectionTitle, color = AppColors.Text)
                            Spacer(Modifier.weight(1f))
                            StatusPill(order.status)
                        }
                        Text(
                            "${order.planId} · ${order.amountLyd} LYD · ${order.paymentMethod}",
                            style = AppText.bodySmall,
                            color = AppColors.Muted,
                        )
                        Text(
                            TimeFormat.dateTime(order.createdAt, Locale.getDefault()),
                            style = AppText.tiny,
                            color = AppColors.Dim,
                        )
                        order.rejectReason?.let {
                            Text(it, style = AppText.tiny, color = AppColors.Red)
                        }
                        if (order.status == "pending" && order.receiptUrl == null) {
                            Text(
                                stringResource(R.string.plan_receipt),
                                style = AppText.tiny,
                                color = AppColors.Blue,
                                modifier = Modifier.clickable { },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusPill(status: String) {
    val (label, color) = when (status) {
        "approved" -> stringResource(R.string.plan_status_approved) to AppColors.Green
        "rejected" -> stringResource(R.string.plan_status_rejected) to AppColors.Red
        "submitted" -> stringResource(R.string.plan_status_submitted) to AppColors.Blue
        else -> stringResource(R.string.plan_status_pending) to AppColors.Gold
    }
    Text(
        text = label,
        style = AppText.tiny,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}
