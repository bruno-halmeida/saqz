package br.com.saqz.groups.presentation.ui.finance.settlement

import br.com.saqz.groups.presentation.photo.avatarSlot
import br.com.saqz.groups.presentation.ui.finance.sheets.ChargeReminderState

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzProgressBar
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.finance.sheets.CancelChargeSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.ChargeSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.ReceiptSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.ReviewSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.UndoSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.WaiveSheet
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.domain.finance.PaidMethod
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.game_settlement_charge_missing
import br.com.saqz.groups.resources.game_settlement_court_expense_description
import br.com.saqz.groups.resources.game_settlement_debt_note
import br.com.saqz.groups.resources.game_settlement_diarist_meta
import br.com.saqz.groups.resources.game_settlement_diarists_empty
import br.com.saqz.groups.resources.game_settlement_end
import br.com.saqz.groups.resources.game_settlement_undo_action
import br.com.saqz.groups.resources.game_settlement_undo_failure
import br.com.saqz.groups.resources.game_settlement_end_helper
import br.com.saqz.groups.resources.game_settlement_ended
import br.com.saqz.groups.resources.game_settlement_header
import br.com.saqz.groups.resources.game_settlement_monthly_members
import br.com.saqz.groups.resources.game_settlement_monthly_members_body
import br.com.saqz.groups.resources.game_settlement_note
import br.com.saqz.groups.resources.game_settlement_players
import br.com.saqz.groups.resources.game_settlement_progress
import br.com.saqz.groups.resources.game_settlement_receipt
import br.com.saqz.groups.resources.game_settlement_receipt_failure
import br.com.saqz.groups.resources.game_settlement_review_action
import br.com.saqz.groups.resources.game_settlement_review_failure
import br.com.saqz.groups.resources.game_settlement_review_status
import br.com.saqz.groups.resources.game_settlement_received_status
import br.com.saqz.groups.resources.paid_via_app_card
import br.com.saqz.groups.resources.paid_via_app_pix
import br.com.saqz.groups.domain.finance.AppPaymentMethod
import br.com.saqz.groups.resources.game_settlement_summary_pending
import br.com.saqz.groups.resources.game_settlement_summary
import br.com.saqz.groups.resources.game_settlement_summary_diarists_one
import br.com.saqz.groups.resources.game_settlement_summary_diarists_other
import br.com.saqz.groups.resources.game_settlement_title
import br.com.saqz.groups.resources.game_settlement_view_cashbox
import br.com.saqz.groups.resources.game_settlement_cancel_action
import br.com.saqz.groups.resources.game_settlement_cancel_failure
import br.com.saqz.groups.resources.game_settlement_waive
import br.com.saqz.groups.resources.game_settlement_waive_failure
import br.com.saqz.groups.resources.game_settlement_waived_status
import br.com.saqz.groups.resources.game_settlement_withdrew_status
import br.com.saqz.groups.resources.game_settlement_no_show_status
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

internal object GameSettlementTags {
    const val Screen = "game-settlement"
    const val Header = "game-settlement-header"
    const val Progress = "game-settlement-progress"
    const val Monthly = "game-settlement-monthly"
    const val Diarists = "game-settlement-diarists"
    const val Summary = "game-settlement-summary"
    const val CourtExpense = "game-settlement-court-expense"
    const val ChargeMissing = "game-settlement-charge-missing"
    const val End = "game-settlement-end"
    const val Cashbox = "game-settlement-cashbox"
    const val Failure = "game-settlement-failure"
    const val WaiveFailure = "game-settlement-waive-failure"
    const val CancelFailure = "game-settlement-cancel-failure"
    const val ReviewFailure = "game-settlement-review-failure"
    const val UndoFailure = "game-settlement-undo-failure"

    fun receipt(chargeId: String) = "game-settlement-receipt-$chargeId"
    fun waive(chargeId: String) = "game-settlement-waive-$chargeId"
    fun cancel(chargeId: String) = "game-settlement-cancel-$chargeId"
    fun withdrew(chargeId: String) = "game-settlement-withdrew-$chargeId"
    fun noShow(chargeId: String) = "game-settlement-no-show-$chargeId"
    fun review(chargeId: String) = "game-settlement-review-$chargeId"
    fun reviewAction(chargeId: String) = "game-settlement-review-action-$chargeId"
    fun undo(chargeId: String) = "game-settlement-undo-$chargeId"
    fun paid(chargeId: String) = "game-settlement-paid-$chargeId"
    fun charge(chargeId: String) = "game-settlement-charge-$chargeId"
}

@Composable
internal fun GameSettlementScreen(
    state: GameSettlementState,
    onBack: () -> Unit,
    onIntent: (GameSettlementIntent) -> Unit,
    modifier: Modifier = Modifier,
    reminder: ChargeReminderState = ChargeReminderState(),
    onSendReminders: (List<String>) -> Unit = {},
) {
    val receiptDebtor = state.debtors.firstOrNull { it.chargeId == state.receiptSheetChargeId }
    val waiveDebtor = state.debtors.firstOrNull { it.chargeId == state.waiveSheetChargeId }
    val cancelDebtor = state.debtors.firstOrNull { it.chargeId == state.cancelSheetChargeId }
    val reviewDiarist = state.diarists.firstOrNull { it.chargeId == state.reviewSheetChargeId }
    val undoDiarist = state.diarists.firstOrNull { it.chargeId == state.undoSheetChargeId }
    val chargeDebtors = state.debtors.filter {
        state.chargeSheetChargeId == null || it.chargeId == state.chargeSheetChargeId
    }
    Box(modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(GameSettlementTags.Screen)) {
        Column(Modifier.fillMaxSize()) {
            SaqzTopAppBar(title = stringResource(Res.string.game_settlement_title), onBack = onBack)
            when {
                state.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    SaqzSpinner()
                }
                state.loadFailed -> GroupLoadFailure(
                    error = state.error,
                    onRetry = { onIntent(GameSettlementIntent.Retry) },
                    modifier = Modifier.fillMaxSize().testTag(GameSettlementTags.Failure),
                )
                else -> LoadedContent(state, onIntent)
            }
        }
        ChargeSheet(
            open = state.chargeSheetOpen,
            debtors = chargeDebtors,
            onClose = { onIntent(GameSettlementIntent.DismissChargeSheet) },
            onSend = onSendReminders,
            delivery = reminder,
        )
        ReceiptSheet(
            open = state.receiptSheetChargeId != null,
            debtor = receiptDebtor,
            onClose = { onIntent(GameSettlementIntent.DismissReceiptSheet) },
            onConfirm = { paidMethod ->
                receiptDebtor?.let {
                    onIntent(GameSettlementIntent.MarkReceived(it.chargeId, paidMethod))
                }
            },
        )
        WaiveSheet(
            open = state.waiveSheetChargeId != null,
            debtor = waiveDebtor,
            onClose = { onIntent(GameSettlementIntent.DismissWaiveSheet) },
            onConfirm = { waiveDebtor?.let { onIntent(GameSettlementIntent.Waive(it.chargeId)) } },
        )
        CancelChargeSheet(
            open = state.cancelSheetChargeId != null,
            debtor = cancelDebtor,
            busy = state.updatingChargeId != null,
            onClose = { onIntent(GameSettlementIntent.DismissCancelSheet) },
            onConfirm = { cancelDebtor?.let { onIntent(GameSettlementIntent.Cancel(it.chargeId)) } },
        )
        ReviewSheet(
            open = state.reviewSheetChargeId != null,
            name = reviewDiarist?.name,
            referenceLabel = reviewDiarist?.referenceLabel,
            amountLabel = reviewDiarist?.amountLabel,
            busy = state.updatingChargeId != null,
            onClose = { onIntent(GameSettlementIntent.DismissReviewSheet) },
            onConfirm = { reviewDiarist?.let { onIntent(GameSettlementIntent.ResolveReview(it.chargeId)) } },
            photo = reviewDiarist?.photo,
        )
        UndoSheet(
            open = state.undoSheetChargeId != null,
            name = undoDiarist?.name,
            referenceLabel = undoDiarist?.referenceLabel,
            amountLabel = undoDiarist?.amountLabel,
            received = undoDiarist?.status == ChargeStatus.Paid,
            busy = state.updatingChargeId != null,
            onClose = { onIntent(GameSettlementIntent.DismissUndoSheet) },
            onConfirm = { note -> undoDiarist?.let { onIntent(GameSettlementIntent.Undo(it.chargeId, note)) } },
            photo = undoDiarist?.photo,
        )
    }
}

@Composable
private fun LoadedContent(state: GameSettlementState, onIntent: (GameSettlementIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    val canCharge = state.debtors.isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap * 2),
    ) {
        SettlementHeader(state)
        if (!state.isSummary) ProgressSection(state)
        MonthlySection(state)
        if (state.isSummary) SummarySection(state) else CourtExpenseSection(onIntent)
        DiaristsSection(state, onIntent)
        Text(
            text = stringResource(Res.string.game_settlement_note),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
        if (state.pendingDiaristCount > 0) {
            Text(
                text = stringResource(Res.string.game_settlement_debt_note),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.warningForeground,
            )
        }
        if (state.operationFailed) {
            Text(
                text = stringResource(Res.string.game_settlement_receipt_failure),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
            )
        }
        if (state.waiveFailed) {
            Text(
                text = stringResource(Res.string.game_settlement_waive_failure),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(GameSettlementTags.WaiveFailure),
            )
        }
        if (state.cancelFailed) {
            Text(
                text = stringResource(Res.string.game_settlement_cancel_failure),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(GameSettlementTags.CancelFailure),
            )
        }
        if (state.reviewFailed) {
            Text(
                text = stringResource(Res.string.game_settlement_review_failure),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(GameSettlementTags.ReviewFailure),
            )
        }
        if (state.undoFailed) {
            Text(
                text = stringResource(Res.string.game_settlement_undo_failure),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = Modifier.testTag(GameSettlementTags.UndoFailure),
            )
        }
        SaqzButton(
            label = stringResource(Res.string.game_settlement_charge_missing),
            onClick = { onIntent(GameSettlementIntent.ChargeMissing) },
            variant = SaqzButtonVariant.Secondary,
            fullWidth = true,
            enabled = canCharge,
            modifier = Modifier.testTag(GameSettlementTags.ChargeMissing),
        )
        SaqzButton(
            label = stringResource(Res.string.game_settlement_end),
            onClick = { onIntent(GameSettlementIntent.EndSettlement) },
            fullWidth = true,
            enabled = state.isSummary,
            modifier = Modifier.testTag(GameSettlementTags.End),
        )
        if (!state.isSummary) {
            Text(
                text = stringResource(Res.string.game_settlement_end_helper),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        } else {
            SaqzButton(
                label = stringResource(Res.string.game_settlement_view_cashbox),
                onClick = { onIntent(GameSettlementIntent.OpenCashbox) },
                fullWidth = true,
                modifier = Modifier.testTag(GameSettlementTags.Cashbox),
            )
        }
    }
}

@Composable
private fun SettlementHeader(state: GameSettlementState) {
    val header = state.header ?: return
    Column(
        modifier = Modifier.testTag(GameSettlementTags.Header),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
    ) {
        Text(
            text = stringResource(if (state.isSummary) Res.string.game_settlement_ended else Res.string.game_settlement_header),
            style = SaqzTheme.typography.eyebrow,
            color = if (state.isSummary) SaqzTheme.colors.success else SaqzTheme.colors.textSecondary,
        )
        Text(text = header.dateTime, style = SaqzTheme.typography.title, color = SaqzTheme.colors.textPrimary)
        Text(text = header.venue, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
        Text(
            text = pluralStringResource(
                Res.plurals.game_settlement_players,
                header.playersCount,
                header.playersCount,
            ),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun ProgressSection(state: GameSettlementState) = SaqzCard(
    modifier = Modifier.testTag(GameSettlementTags.Progress),
    tone = SaqzCardTone.Soft,
) {
    Text(
        text = pluralStringResource(
            Res.plurals.game_settlement_progress,
            state.totalDiaristCount,
            state.paidDiaristCount,
            state.totalDiaristCount,
            formatBrl(state.pendingDiaristCents),
        ),
        style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
        color = SaqzTheme.colors.textPrimary,
    )
    SaqzProgressBar(value = state.progress)
}

@Composable
private fun MonthlySection(state: GameSettlementState) = SaqzCard(
    modifier = Modifier.testTag(GameSettlementTags.Monthly),
) {
    SaqzSectionHeader(title = pluralStringResource(
        Res.plurals.game_settlement_monthly_members,
        state.monthlyMemberCount,
        state.monthlyMemberCount,
    ))
    Text(
        text = stringResource(Res.string.game_settlement_monthly_members_body),
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.textSecondary,
    )
}

@Composable
private fun SummarySection(state: GameSettlementState) = SaqzCard(
    modifier = Modifier.testTag(GameSettlementTags.Summary),
) {
    SaqzSectionHeader(title = stringResource(Res.string.game_settlement_summary))
    SummaryLine(
        label = if (state.totalDiaristCount == 1) {
            stringResource(Res.string.game_settlement_summary_diarists_one, formatBrl(state.unitDiaristCents))
        } else {
            stringResource(
                Res.string.game_settlement_summary_diarists_other,
                state.totalDiaristCount,
                formatBrl(state.unitDiaristCents),
            )
        },
        value = formatBrl(state.receivedDiaristCents),
    )
    SummaryLine(
        label = stringResource(Res.string.game_settlement_summary_pending),
        value = formatBrl(state.pendingDiaristCents),
    )
}

@Composable
private fun SummaryLine(label: String, value: String, valueColor: Color = SaqzTheme.colors.textPrimary) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        Text(label, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary, modifier = Modifier.weight(1f))
        Text(value, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = valueColor)
    }
}

@Composable
private fun CourtExpenseSection(
    onIntent: (GameSettlementIntent) -> Unit,
) = SaqzCard(modifier = Modifier.testTag(GameSettlementTags.CourtExpense)) {
    SaqzButton(
        label = stringResource(Res.string.game_settlement_court_expense_description),
        onClick = { onIntent(GameSettlementIntent.OpenCourtExpense) },
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
    )
}

@Composable
private fun DiaristsSection(
    state: GameSettlementState,
    onIntent: (GameSettlementIntent) -> Unit,
) = Column(
    modifier = Modifier.testTag(GameSettlementTags.Diarists),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.game_settlement_diarist_meta))
    if (state.diarists.isEmpty()) {
        SaqzEmptyState(
            title = stringResource(Res.string.game_settlement_diarists_empty),
            description = null,
            icon = br.com.saqz.designsystem.SaqzIcons.Users,
        )
    } else {
        SaqzCard(padded = false) {
            state.diarists.forEachIndexed { index, diarist ->
                DiaristRow(diarist, onIntent)
                if (index < state.diarists.lastIndex) SaqzDivider()
            }
        }
    }
}

@Composable
private fun DiaristRow(
    diarist: GameSettlementDiaristUi,
    onIntent: (GameSettlementIntent) -> Unit,
) {
    val paid = diarist.status == ChargeStatus.Paid
    val background = if (paid) SaqzTheme.colors.success.copy(alpha = 0.08f) else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(horizontal = SaqzTheme.metrics.horizontalPadding, vertical = SaqzTheme.metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
    ) {
        SaqzAvatar(name = diarist.name, photo = diarist.photo.avatarSlot(diarist.name))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = diarist.name,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            Text(text = diarist.meta, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
            Text(text = diarist.amountLabel, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary)
            if (diarist.withdrew) {
                SaqzStatusChip(
                    text = stringResource(Res.string.game_settlement_withdrew_status),
                    tone = SaqzChipTone.Warning,
                    modifier = Modifier.testTag(GameSettlementTags.withdrew(diarist.chargeId)),
                )
            }
            if (diarist.noShow) {
                SaqzStatusChip(
                    text = stringResource(Res.string.game_settlement_no_show_status),
                    tone = SaqzChipTone.Warning,
                    modifier = Modifier.testTag(GameSettlementTags.noShow(diarist.chargeId)),
                )
            }
            if (diarist.reviewRequired) {
                SaqzStatusChip(
                    text = stringResource(Res.string.game_settlement_review_status),
                    tone = SaqzChipTone.Warning,
                    modifier = Modifier.testTag(GameSettlementTags.review(diarist.chargeId)),
                )
                SaqzButton(
                    label = stringResource(Res.string.game_settlement_review_action),
                    onClick = { onIntent(GameSettlementIntent.OpenReview(diarist.chargeId)) },
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    enabled = !diarist.isUpdating,
                    modifier = Modifier.testTag(GameSettlementTags.reviewAction(diarist.chargeId)),
                )
            }
        }
        when (diarist.status) {
            ChargeStatus.Pending -> Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
                SaqzButton(
                    label = stringResource(Res.string.game_settlement_receipt),
                    onClick = { onIntent(GameSettlementIntent.OpenReceipt(diarist.chargeId)) },
                    size = SaqzButtonSize.Sm,
                    loading = diarist.isUpdating,
                    enabled = !diarist.isUpdating,
                    modifier = Modifier.testTag(GameSettlementTags.receipt(diarist.chargeId)),
                )
                if (diarist.waivable) {
                    SaqzButton(
                        label = stringResource(Res.string.game_settlement_waive),
                        onClick = { onIntent(GameSettlementIntent.OpenWaive(diarist.chargeId)) },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                        enabled = !diarist.isUpdating,
                        modifier = Modifier.testTag(GameSettlementTags.waive(diarist.chargeId)),
                    )
                }
                SaqzButton(
                    label = stringResource(Res.string.game_settlement_cancel_action),
                    onClick = { onIntent(GameSettlementIntent.OpenCancel(diarist.chargeId)) },
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    enabled = !diarist.isUpdating,
                    modifier = Modifier.testTag(GameSettlementTags.cancel(diarist.chargeId)),
                )
            }
            ChargeStatus.Paid, ChargeStatus.Waived -> Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid),
            ) {
                if (diarist.status == ChargeStatus.Paid) {
                    SaqzStatusChip(
                        text = when (diarist.appMethod) {
                            AppPaymentMethod.Pix -> stringResource(Res.string.paid_via_app_pix)
                            AppPaymentMethod.Card -> stringResource(Res.string.paid_via_app_card)
                            null -> stringResource(Res.string.game_settlement_received_status)
                        },
                        tone = SaqzChipTone.Success,
                        modifier = Modifier.testTag(GameSettlementTags.paid(diarist.chargeId)),
                    )
                } else {
                    SaqzStatusChip(
                        text = stringResource(Res.string.game_settlement_waived_status),
                        tone = SaqzChipTone.Neutral,
                    )
                }
                if (diarist.canUndo) {
                    SaqzButton(
                        label = stringResource(Res.string.game_settlement_undo_action),
                        onClick = { onIntent(GameSettlementIntent.OpenUndo(diarist.chargeId)) },
                        variant = SaqzButtonVariant.Secondary,
                        size = SaqzButtonSize.Sm,
                        loading = diarist.isUpdating,
                        enabled = !diarist.isUpdating,
                        modifier = Modifier.testTag(GameSettlementTags.undo(diarist.chargeId)),
                    )
                }
            }
            ChargeStatus.Cancelled -> Unit
        }
    }
}
