package com.addonstation.mobile.ui.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.addonstation.mobile.R
import com.addonstation.mobile.ui.theme.HomePalette
import com.addonstation.mobile.ui.theme.LocalHomePalette
import java.text.NumberFormat
import java.util.Locale

/*
 * Reviewed blue-theme UI for three admin areas.
 *
 * This file is intentionally stateless. Connect its values and callbacks to the existing state in
 * AdminAcScreen, AdminCreatorReviewScreen, and AdminAuditScreen. Server authorization, confirmation
 * dialogs, loading/error handling, creator-code generation, and audit retrieval stay in those
 * screens; this file only renders data and emits events.
 *
 * Green is reserved for positive AC values and approved/success states.
 */

// ───────────────────────── 1) AC wallet ─────────────────────────

@Immutable
data class AcUserUi(val id: String, val name: String)

@Immutable
data class AcTxUi(val name: String, val amount: Int, val balanceAfter: Int)

@Composable
fun AcWalletContent(
    selectedName: String?,
    selectedBalance: Int?,
    selectedPlan: String?,
    amount: String,
    onAmountChange: (String) -> Unit,
    note: String,
    onNoteChange: (String) -> Unit,
    quickAmounts: List<Int>,
    users: List<AcUserUi>,
    selectedId: String?,
    onSelectUser: (String) -> Unit,
    transactions: List<AcTxUi>,
    giveLabel: String,
    onGive: () -> Unit,
    onDeduct: () -> Unit,
    onGiveAll: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val validAmount = amount.toIntOrNull()?.let { it > 0 } == true
    val selectedActionEnabled = actionsEnabled && validAmount && selectedId != null

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!selectedName.isNullOrBlank() && selectedBalance != null) {
            item(key = "balance") {
                BalanceCard(selectedName, selectedBalance, selectedPlan, palette)
            }
        }

        item(key = "controls") {
            AdminBlueCard(palette) {
                FieldLabel(stringResource(R.string.admin_give_ac_amount), palette)
                AdminTextBox(
                    value = amount,
                    onChange = onAmountChange,
                    palette = palette,
                    number = true,
                    highlight = true,
                    maxLength = 9,
                    enabled = actionsEnabled,
                )

                if (quickAmounts.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        itemsIndexed(
                            items = quickAmounts,
                            key = { index, value -> "$index:$value" },
                        ) { _, value ->
                            val label = value.toString()
                            AmountChip(
                                label = label,
                                selected = amount == label,
                                palette = palette,
                                enabled = actionsEnabled,
                                modifier = Modifier.widthIn(min = 72.dp),
                                onClick = { onAmountChange(label) },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))
                FieldLabel(stringResource(R.string.admin_give_ac_note), palette)
                AdminTextBox(
                    value = note,
                    onChange = onNoteChange,
                    palette = palette,
                    maxLength = 60,
                    enabled = actionsEnabled,
                )

                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AdminPrimaryButton(
                        text = giveLabel,
                        onClick = onGive,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        height = 48.dp,
                        enabled = selectedActionEnabled,
                    )
                    AdminOutlineButton(
                        text = stringResource(R.string.admin_give_ac_deduct),
                        onClick = onDeduct,
                        palette = palette,
                        modifier = Modifier.weight(1f),
                        height = 48.dp,
                        tint = palette.text,
                        enabled = selectedActionEnabled,
                    )
                }

                Spacer(Modifier.height(8.dp))
                AdminOutlineButton(
                    text = stringResource(R.string.admin_give_ac_all),
                    onClick = onGiveAll,
                    palette = palette,
                    modifier = Modifier.fillMaxWidth(),
                    height = 44.dp,
                    tint = palette.text,
                    enabled = actionsEnabled && validAmount,
                )
            }
        }

        item(key = "users-heading") {
            AdminSectionHeading(stringResource(R.string.admin_give_ac_select), palette)
        }
        items(items = users, key = { it.id }) { user ->
            val isSelected = user.id == selectedId
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) palette.accentBlue.copy(alpha = 0.10f)
                        else palette.surface,
                    )
                    .border(
                        width = 1.dp,
                        color = if (isSelected) palette.accentBlue else palette.border,
                        shape = RoundedCornerShape(14.dp),
                    )
                    .semantics {
                        selected = isSelected
                        role = Role.RadioButton
                    }
                    .clickable(enabled = actionsEnabled) { onSelectUser(user.id) }
                    .padding(horizontal = 12.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Avatar(user.name, palette)
                Text(
                    text = user.name,
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        item(key = "history-heading") {
            AdminSectionHeading(stringResource(R.string.admin_give_ac_history), palette)
        }
        items(transactions) { transaction ->
            val positive = transaction.amount >= 0
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(palette.surface)
                    .border(1.dp, palette.border, RoundedCornerShape(14.dp))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = transaction.name,
                        color = palette.text,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.admin_give_ac_balance_after,
                        ) + " " + formatInteger(transaction.balanceAfter.toLong()),
                        color = palette.textMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = (if (positive) "+" else "−") +
                        formatInteger(kotlin.math.abs(transaction.amount.toLong())) + " AC",
                    color = if (positive) adminOkText(palette) else adminErrorText(palette),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 14.sp,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun BalanceCard(
    name: String,
    balance: Int,
    plan: String?,
    palette: HomePalette,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(
                Brush.linearGradient(
                    listOf(palette.blue.copy(alpha = 0.32f), palette.surface),
                ),
            )
            .border(1.dp, palette.blue.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                color = palette.textMuted,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatInteger(balance.toLong()) + " AC",
                color = palette.text,
                fontSize = 26.sp,
                fontWeight = FontWeight.ExtraBold,
                maxLines = 1,
            )
        }
        plan?.takeIf { it.isNotBlank() }?.let { AdminTierBadge(it, palette) }
    }
}

// ───────────────────────── 2) Creator request card ─────────────────────────

enum class ReviewStatus { Pending, Approved, Rejected }

@Composable
fun CreatorRequestCard(
    name: String,
    meta: String,
    message: String?,
    status: ReviewStatus,
    statusLabel: String,
    approveLabel: String,
    moreInfoLabel: String,
    rejectLabel: String,
    onApprove: () -> Unit,
    onMoreInfo: () -> Unit,
    onReject: () -> Unit,
    palette: HomePalette = LocalHomePalette.current,
    actionsEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    AdminBlueCard(palette = palette, modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Avatar(name, palette)
            Column(Modifier.weight(1f)) {
                Text(
                    text = name,
                    color = palette.text,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = meta,
                    color = palette.textMuted,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val (background, foreground) = when (status) {
                ReviewStatus.Pending ->
                    palette.goldFill.copy(alpha = 0.16f) to palette.goldText
                ReviewStatus.Approved ->
                    adminOk(palette).copy(alpha = 0.16f) to adminOkText(palette)
                ReviewStatus.Rejected ->
                    adminError(palette).copy(alpha = 0.16f) to adminErrorText(palette)
            }
            StatusPill(statusLabel, background, foreground)
        }

        if (!message.isNullOrBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = message,
                color = palette.textMuted,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
        }

        if (status == ReviewStatus.Pending) {
            Spacer(Modifier.height(12.dp))
            // The primary action gets a complete row so translated labels remain readable.
            AdminPrimaryButton(
                text = approveLabel,
                onClick = onApprove,
                palette = palette,
                modifier = Modifier.fillMaxWidth(),
                height = 44.dp,
                enabled = actionsEnabled,
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AdminOutlineButton(
                    text = moreInfoLabel,
                    onClick = onMoreInfo,
                    palette = palette,
                    modifier = Modifier.weight(1.3f),
                    height = 42.dp,
                    tint = palette.text,
                    enabled = actionsEnabled,
                )
                AdminOutlineButton(
                    text = rejectLabel,
                    onClick = onReject,
                    palette = palette,
                    modifier = Modifier.weight(1f),
                    height = 42.dp,
                    tint = adminErrorText(palette),
                    borderColor = adminError(palette).copy(alpha = 0.55f),
                    enabled = actionsEnabled,
                )
            }
        }
    }
}

// ───────────────────────── 3) Audit log ─────────────────────────

enum class AuditKind { Ok, Info, Warn, Error }

@Immutable
data class AuditEventUi(
    val action: String,
    val detail: String,
    val time: String,
    val kind: AuditKind,
)

@Composable
fun AuditLogContent(
    filters: List<String>,
    selectedFilter: Int,
    onFilter: (Int) -> Unit,
    events: List<AuditEventUi>,
    palette: HomePalette = LocalHomePalette.current,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (filters.isNotEmpty()) {
            item(key = "audit-filters") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(
                        items = filters,
                        key = { index, label -> "$index:$label" },
                    ) { index, label ->
                        AdminFilterChip(
                            label = label,
                            selected = index == selectedFilter,
                            onClick = { onFilter(index) },
                            palette = palette,
                            height = 36.dp,
                            modifier = Modifier.widthIn(min = 88.dp),
                        )
                    }
                }
            }
        }

        if (events.isEmpty()) {
            item(key = "audit-empty") {
                AdminBlueCard(palette) {
                    Text(
                        text = stringResource(R.string.common_none),
                        color = palette.textMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        } else {
            itemsIndexed(events) { _, event ->
                val indicator = when (event.kind) {
                    AuditKind.Ok -> adminOk(palette)
                    AuditKind.Info -> palette.accentBlue
                    AuditKind.Warn -> adminWarning(palette)
                    AuditKind.Error -> adminError(palette)
                }
                AdminBlueCard(palette = palette, padding = 0.dp) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        Box(
                            Modifier
                                .padding(top = 6.dp)
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(indicator),
                        )
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = event.action,
                                color = palette.text,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (event.detail.isNotBlank()) {
                                Text(
                                    text = event.detail,
                                    color = palette.textMuted,
                                    fontSize = 11.sp,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                        if (event.time.isNotBlank()) {
                            Spacer(Modifier.size(8.dp))
                            Text(
                                text = event.time,
                                color = palette.textMuted,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.widthIn(max = 112.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

// ───────────────────────── Shared components ─────────────────────────

@Composable
private fun AdminBlueCard(
    palette: HomePalette,
    modifier: Modifier = Modifier,
    padding: Dp = 14.dp,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.surface)
            .border(1.dp, palette.border, RoundedCornerShape(18.dp))
            .padding(padding),
        content = content,
    )
}

@Composable
private fun FieldLabel(text: String, palette: HomePalette) {
    Text(
        text = text,
        color = palette.textMuted,
        fontSize = 12.sp,
        modifier = Modifier.padding(bottom = 6.dp),
    )
}

@Composable
private fun AdminTextBox(
    value: String,
    onChange: (String) -> Unit,
    palette: HomePalette,
    number: Boolean = false,
    highlight: Boolean = false,
    maxLength: Int? = null,
    enabled: Boolean = true,
) {
    BasicTextField(
        value = value,
        onValueChange = { rawValue ->
            val filtered = if (number) rawValue.filter(Char::isDigit) else rawValue
            onChange(maxLength?.let { filtered.take(it.coerceAtLeast(0)) } ?: filtered)
        },
        enabled = enabled,
        singleLine = true,
        textStyle = TextStyle(
            color = if (enabled) palette.text else palette.textMuted,
            fontSize = 15.sp,
            fontWeight = if (number) FontWeight.Bold else FontWeight.Medium,
        ),
        cursorBrush = SolidColor(palette.accentBlue),
        keyboardOptions = KeyboardOptions(
            keyboardType = if (number) KeyboardType.Number else KeyboardType.Text,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(palette.surfaceHigh)
            .border(
                width = 1.dp,
                color = if (highlight && enabled) palette.accentBlue else palette.border,
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 12.dp),
    )
}

@Composable
private fun AmountChip(
    label: String,
    selected: Boolean,
    palette: HomePalette,
    enabled: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(42.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) palette.accentBlue.copy(alpha = 0.18f)
                else palette.surfaceHigh,
            )
            .border(
                width = 1.dp,
                color = if (selected) palette.accentBlue else palette.border,
                shape = RoundedCornerShape(12.dp),
            )
            .semantics {
                this.selected = selected
                role = Role.Button
            }
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = when {
                !enabled -> palette.textMuted
                selected -> palette.accentBlue
                else -> palette.text
            },
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun Avatar(name: String, palette: HomePalette) {
    val initial = name.trim().firstOrNull()?.toString()?.uppercase(Locale.getDefault()) ?: "?"
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(palette.surfaceHigh),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial, color = palette.accentBlue, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun StatusPill(text: String, background: Color, foreground: Color) {
    Text(
        text = text,
        color = foreground,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(background)
            .padding(horizontal = 9.dp, vertical = 3.dp),
    )
}

private fun formatInteger(value: Long): String =
    NumberFormat.getIntegerInstance(Locale.getDefault()).format(value)
