package br.com.saqz.groups.presentation.ui.finance.settlement

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
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzScoreTab
import br.com.saqz.designsystem.SaqzScoreTabs
import br.com.saqz.designsystem.SaqzSheetAction
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.finance.AppPaymentMethod
import br.com.saqz.groups.domain.finance.ChargeStatus
import br.com.saqz.groups.presentation.photo.avatarSlot
import br.com.saqz.groups.presentation.ui.GroupLoadFailure
import br.com.saqz.groups.presentation.ui.PixCopiedToast
import br.com.saqz.groups.presentation.ui.finance.CashGroupHeader
import br.com.saqz.groups.presentation.ui.finance.CashIconTile
import br.com.saqz.groups.presentation.ui.finance.CashListRow
import br.com.saqz.groups.presentation.ui.finance.CashPendingStrip
import br.com.saqz.groups.presentation.ui.finance.CashRowText
import br.com.saqz.groups.presentation.ui.finance.CashTileTone
import br.com.saqz.groups.presentation.ui.finance.formatBrlShort
import br.com.saqz.groups.presentation.ui.finance.sheets.CancelChargeSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.ChargeReminderState
import br.com.saqz.groups.presentation.ui.finance.sheets.ChargeSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.ReceiptSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.ReviewSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.UndoSheet
import br.com.saqz.groups.presentation.ui.finance.sheets.WaiveSheet
import br.com.saqz.groups.resources.*
import org.jetbrains.compose.resources.stringResource

@Suppress("TooManyFunctions")
internal object GameSettlementTags {
    const val Screen = "game-settlement"
    const val Header = "game-settlement-header"
    const val Monthly = "game-settlement-monthly"
    const val Diarists = "game-settlement-diarists"
    const val Uncharged = "game-settlement-uncharged"
    const val UnchargedStrip = "game-settlement-uncharged-strip"
    const val UnchargedReview = "game-settlement-uncharged-review"
    const val NotPlayed = "game-settlement-not-played"
    const val CourtExpense = "game-settlement-court-expense"
    const val ChargeMissing = "game-settlement-charge-missing"
    const val Failure = "game-settlement-failure"
    const val WaiveFailure = "game-settlement-waive-failure"
    const val CancelFailure = "game-settlement-cancel-failure"
    const val ReviewFailure = "game-settlement-review-failure"
    const val UndoFailure = "game-settlement-undo-failure"

    fun receipt(chargeId: String) = "game-settlement-receipt-$chargeId"
    fun more(chargeId: String) = "game-settlement-more-$chargeId"
    fun waive(chargeId: String) = "game-settlement-waive-$chargeId"
    fun cancel(chargeId: String) = "game-settlement-cancel-$chargeId"
    fun withdrew(chargeId: String) = "game-settlement-withdrew-$chargeId"
    fun noShow(chargeId: String) = "game-settlement-no-show-$chargeId"
    fun notPlayed(chargeId: String) = "game-settlement-not-played-$chargeId"
    fun review(chargeId: String) = "game-settlement-review-$chargeId"
    fun reviewAction(chargeId: String) = "game-settlement-review-action-$chargeId"
    fun undo(chargeId: String) = "game-settlement-undo-$chargeId"
    fun paid(chargeId: String) = "game-settlement-paid-$chargeId"
    fun charge(chargeId: String) = "game-settlement-charge-$chargeId"
    fun uncharged(key: String) = "game-settlement-uncharged-$key"
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
    // "Cobrar os N que faltam" é de quem jogou; quem não jogou é decisão do gestor.
    val chargeDebtors = if (state.chargeSheetChargeId == null) {
        state.playedDebtors
    } else {
        state.debtors.filter { it.chargeId == state.chargeSheetChargeId }
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
        PixCopiedToast(
            visible = state.pixCopied,
            onDismiss = { onIntent(GameSettlementIntent.DismissPixCopied) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        DiaryMenuSheet(state, onIntent)
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
    Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        SettlementHero(state, onIntent)
        Failures(state)
        if (state.played.isNotEmpty()) DiariesSection(state, onIntent)
        if (state.monthlyMemberCount > 0) {
            CashGroupHeader(
                title = stringResource(Res.string.settle_monthly_header),
                trailing = stringResource(Res.string.settle_monthly_count, state.monthlyMemberCount),
                modifier = Modifier.testTag(GameSettlementTags.Monthly),
            )
        }
        if (state.uncharged.isNotEmpty()) UnchargedSection(state, onIntent)
        if (state.notPlayed.isNotEmpty()) NotPlayedSection(state, onIntent)
        CashGroupHeader(title = stringResource(Res.string.settle_exits_header))
        CashListRow(first = true, modifier = Modifier.testTag(GameSettlementTags.CourtExpense)) {
            CashIconTile(SaqzIcons.CreditCard, CashTileTone.Out)
            CashRowText(stringResource(Res.string.settle_court), stringResource(Res.string.settle_court_hint))
            SaqzButton(
                label = stringResource(Res.string.settle_court_action),
                onClick = { onIntent(GameSettlementIntent.OpenCourtExpense) },
                variant = SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
            )
        }
        val pendingPlayed = state.playedDebtors.size
        if (pendingPlayed > 0) {
            SaqzButton(
                label = stringResource(Res.string.settle_charge_all, pendingPlayed),
                onClick = { onIntent(GameSettlementIntent.ChargeMissing) },
                fullWidth = true,
                leadingContent = { SaqzIcon(SaqzIcons.Megaphone, tint = it) },
                modifier = Modifier
                    .padding(metrics.horizontalPadding)
                    .testTag(GameSettlementTags.ChargeMissing),
            )
        }
    }
}

@Composable
private fun SettlementHero(state: GameSettlementState, onIntent: (GameSettlementIntent) -> Unit) {
    val header = state.header ?: return
    val decide = state.playedPendingCents == 0L && state.notPlayed.any { it.status == ChargeStatus.Pending }
    SaqzHeroCard(
        kicker = stringResource(if (header.completed) Res.string.settle_kicker_completed else Res.string.settle_kicker_open),
        kickerMuted = header.completed,
        title = header.title,
        meta = header.meta,
        shape = RectangleShape,
        modifier = Modifier.testTag(GameSettlementTags.Header),
        footer = {
            SaqzScoreTabs(
                tabs = listOf(
                    SaqzScoreTab(header.playersCount.toString(), stringResource(Res.string.settle_tab_played)),
                    if (state.playedChargeCount > 0) {
                        SaqzScoreTab(
                            state.playedPaidCount.toString(),
                            stringResource(Res.string.settle_tab_paid),
                            suffix = "/${state.playedChargeCount}",
                        )
                    } else {
                        SaqzScoreTab("0", stringResource(Res.string.settle_tab_charges))
                    },
                    if (decide) {
                        SaqzScoreTab(
                            state.notPlayed.count { it.status == ChargeStatus.Pending }.toString(),
                            stringResource(Res.string.settle_tab_decide),
                        )
                    } else {
                        SaqzScoreTab(formatBrlShort(state.playedPendingCents), stringResource(Res.string.settle_tab_due))
                    },
                ),
                selectedIndex = -1,
                onSelect = {},
            )
        },
    ) {
        val pending = state.unchargedPending
        if (pending > 0) {
            CashPendingStrip(
                title = if (pending == 1) {
                    stringResource(Res.string.settle_uncharged_title_one)
                } else {
                    stringResource(Res.string.settle_uncharged_title, pending)
                },
                subtitle = stringResource(
                    if (state.gameHadNoFee) Res.string.settle_uncharged_no_fee else Res.string.settle_uncharged_late_fee,
                ),
                action = stringResource(Res.string.settle_review),
                onAction = { onIntent(GameSettlementIntent.ReviewUncharged) },
                modifier = Modifier.testTag(GameSettlementTags.UnchargedStrip),
                actionTag = GameSettlementTags.UnchargedReview,
            )
        }
    }
}

@Composable
private fun Failures(state: GameSettlementState) {
    val failures = listOfNotNull(
        (Res.string.game_settlement_receipt_failure to null).takeIf { state.operationFailed },
        (Res.string.game_settlement_waive_failure to GameSettlementTags.WaiveFailure).takeIf { state.waiveFailed },
        (Res.string.game_settlement_cancel_failure to GameSettlementTags.CancelFailure).takeIf { state.cancelFailed },
        (Res.string.game_settlement_review_failure to GameSettlementTags.ReviewFailure).takeIf { state.reviewFailed },
        (Res.string.game_settlement_undo_failure to GameSettlementTags.UndoFailure).takeIf { state.undoFailed },
    )
    if (failures.isEmpty()) return
    Column(
        Modifier.fillMaxWidth().padding(SaqzTheme.metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
    ) {
        failures.forEach { (text, tag) ->
            Text(
                text = stringResource(text),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
                modifier = tag?.let { Modifier.testTag(it) } ?: Modifier,
            )
        }
    }
}

@Composable
private fun DiariesSection(state: GameSettlementState, onIntent: (GameSettlementIntent) -> Unit) {
    val unit = state.played.map { it.amountCents }.distinct().singleOrNull()
    Column(Modifier.fillMaxWidth().testTag(GameSettlementTags.Diarists)) {
        CashGroupHeader(
            title = unit?.let { stringResource(Res.string.settle_diaries_header, formatBrlShort(it)) }
                ?: stringResource(Res.string.settle_diaries_header_plain),
            trailing = stringResource(Res.string.settle_paid_count, state.playedPaidCount, state.playedChargeCount),
        )
        state.played.forEachIndexed { index, diarist -> DiaryRow(diarist, first = index == 0, onIntent = onIntent) }
    }
}

@Composable
private fun DiaryRow(diarist: GameSettlementDiaristUi, first: Boolean, onIntent: (GameSettlementIntent) -> Unit) =
    CashListRow(
        first = first,
        below = if (diarist.withdrew || diarist.noShow || diarist.reviewRequired) {
            {
                Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
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
            }
        } else null,
    ) {
        SaqzAvatar(name = diarist.name, photo = diarist.photo.avatarSlot(diarist.name))
        CashRowText(diarist.name, diarist.cashMeta)
        when (diarist.status) {
            ChargeStatus.Pending -> {
                SaqzButton(
                    label = stringResource(Res.string.game_settlement_receipt),
                    onClick = { onIntent(GameSettlementIntent.OpenReceipt(diarist.chargeId)) },
                    variant = SaqzButtonVariant.Secondary,
                    size = SaqzButtonSize.Sm,
                    loading = diarist.isUpdating,
                    enabled = !diarist.isUpdating,
                    modifier = Modifier.testTag(GameSettlementTags.receipt(diarist.chargeId)),
                )
                MoreButton(diarist, onIntent)
            }
            ChargeStatus.Paid -> {
                SaqzStatusChip(
                    text = when (diarist.appMethod) {
                        AppPaymentMethod.Pix -> stringResource(Res.string.paid_via_app_pix)
                        AppPaymentMethod.Card -> stringResource(Res.string.paid_via_app_card)
                        null -> stringResource(Res.string.game_settlement_received_status)
                    },
                    tone = SaqzChipTone.Success,
                    dot = true,
                    modifier = Modifier.testTag(GameSettlementTags.paid(diarist.chargeId)),
                )
                if (diarist.canUndo) MoreButton(diarist, onIntent)
            }
            ChargeStatus.Waived -> {
                SaqzStatusChip(text = stringResource(Res.string.settle_waived_chip), tone = SaqzChipTone.Neutral)
                if (diarist.canUndo) MoreButton(diarist, onIntent)
            }
            ChargeStatus.Cancelled -> Unit
        }
    }

@Composable
private fun MoreButton(diarist: GameSettlementDiaristUi, onIntent: (GameSettlementIntent) -> Unit) = SaqzIconButton(
    onClick = { onIntent(GameSettlementIntent.OpenMenu(diarist.chargeId)) },
    contentDescription = stringResource(Res.string.settle_more, diarist.name),
    enabled = !diarist.isUpdating,
    modifier = Modifier.testTag(GameSettlementTags.more(diarist.chargeId)),
) { SaqzIcon(SaqzIcons.MoreVertical, tint = SaqzTheme.colors.textSecondary) }

/** O ⋮ de uma diária: pendente tem Cobrar, Dispensar (desistiu, faltou ou não jogou) e Cancelar; baixada, Desfazer. */
@Composable
private fun DiaryMenuSheet(state: GameSettlementState, onIntent: (GameSettlementIntent) -> Unit) {
    val diarist = state.diarists.firstOrNull { it.chargeId == state.menuChargeId }
    SaqzBottomSheet(
        open = diarist != null,
        onClose = { onIntent(GameSettlementIntent.DismissMenu) },
        title = diarist?.name,
        description = diarist?.cashMeta,
    ) {
        if (diarist == null) return@SaqzBottomSheet
        val dismiss = { onIntent(GameSettlementIntent.DismissMenu) }
        if (diarist.status == ChargeStatus.Pending) {
            SaqzSheetAction(
                icon = SaqzIcons.Megaphone,
                label = stringResource(Res.string.settle_menu_charge),
                onClick = { dismiss(); onIntent(GameSettlementIntent.ChargeIndividual(diarist.chargeId)) },
                divider = false,
                modifier = Modifier.testTag(GameSettlementTags.charge(diarist.chargeId)),
            )
            if (diarist.waivable) {
                SaqzSheetAction(
                    icon = SaqzIcons.Ban,
                    label = stringResource(Res.string.game_settlement_waive),
                    onClick = { dismiss(); onIntent(GameSettlementIntent.OpenWaive(diarist.chargeId)) },
                    modifier = Modifier.testTag(GameSettlementTags.waive(diarist.chargeId)),
                )
            }
            SaqzSheetAction(
                icon = SaqzIcons.CircleX,
                label = stringResource(Res.string.game_settlement_cancel_action),
                onClick = { dismiss(); onIntent(GameSettlementIntent.OpenCancel(diarist.chargeId)) },
                destructive = true,
                modifier = Modifier.testTag(GameSettlementTags.cancel(diarist.chargeId)),
            )
        } else if (diarist.canUndo) {
            SaqzSheetAction(
                icon = SaqzIcons.History,
                label = stringResource(Res.string.settle_menu_undo),
                onClick = { dismiss(); onIntent(GameSettlementIntent.OpenUndo(diarist.chargeId)) },
                divider = false,
                modifier = Modifier.testTag(GameSettlementTags.undo(diarist.chargeId)),
            )
        }
    }
}

@Composable
private fun UnchargedSection(state: GameSettlementState, onIntent: (GameSettlementIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(GameSettlementTags.Uncharged)) {
        CashGroupHeader(
            title = stringResource(Res.string.settle_uncharged_header),
            trailing = stringResource(Res.string.settle_uncharged_count, state.uncharged.size),
        )
        state.uncharged.forEachIndexed { index, row ->
            CashListRow(first = index == 0, modifier = Modifier.testTag(GameSettlementTags.uncharged(row.key))) {
                SaqzAvatar(name = row.name)
                CashRowText(row.name, row.meta, muted = row.noShow)
                when {
                    row.skipped -> SaqzStatusChip(stringResource(Res.string.settle_uncharged_skip_chip), tone = SaqzChipTone.Neutral)
                    row.noShow -> SaqzStatusChip(stringResource(Res.string.settle_no_show_chip), tone = SaqzChipTone.Warning)
                    else -> SaqzStatusChip(stringResource(Res.string.settle_uncharged_chip), tone = SaqzChipTone.Neutral)
                }
            }
        }
        // Todos decididos: a faixa some, mas quem ficou em Não cobrar pode ser reaberto.
        if (state.unchargedPending == 0) {
            SaqzButton(
                label = stringResource(Res.string.settle_reopen),
                onClick = { onIntent(GameSettlementIntent.ReviewUncharged) },
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.padding(horizontal = SaqzTheme.metrics.horizontalPadding),
            )
        }
    }
}

@Composable
private fun NotPlayedSection(state: GameSettlementState, onIntent: (GameSettlementIntent) -> Unit) {
    Column(Modifier.fillMaxWidth().testTag(GameSettlementTags.NotPlayed)) {
        CashGroupHeader(
            title = stringResource(Res.string.settle_not_played_header),
            trailing = stringResource(Res.string.settle_not_played_hint),
        )
        state.notPlayed.forEachIndexed { index, diarist ->
            val pending = diarist.status == ChargeStatus.Pending
            CashListRow(
                first = index == 0,
                below = {
                    Row(
                        modifier = Modifier.padding(start = SaqzTheme.metrics.grid * 6),
                        horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid),
                    ) {
                        if (pending) {
                            SaqzButton(
                                label = stringResource(Res.string.game_settlement_receipt),
                                onClick = { onIntent(GameSettlementIntent.OpenReceipt(diarist.chargeId)) },
                                variant = SaqzButtonVariant.Secondary,
                                size = SaqzButtonSize.Sm,
                                loading = diarist.isUpdating,
                                modifier = Modifier.testTag(GameSettlementTags.receipt(diarist.chargeId)),
                            )
                            SaqzButton(
                                label = stringResource(Res.string.game_settlement_waive),
                                onClick = { onIntent(GameSettlementIntent.OpenWaive(diarist.chargeId)) },
                                variant = SaqzButtonVariant.Ghost,
                                size = SaqzButtonSize.Sm,
                                enabled = !diarist.isUpdating,
                                modifier = Modifier.testTag(GameSettlementTags.waive(diarist.chargeId)),
                            )
                            SaqzButton(
                                label = stringResource(Res.string.settle_cancel_short),
                                onClick = { onIntent(GameSettlementIntent.OpenCancel(diarist.chargeId)) },
                                variant = SaqzButtonVariant.Ghost,
                                size = SaqzButtonSize.Sm,
                                contentColor = SaqzTheme.colors.errorForeground,
                                enabled = !diarist.isUpdating,
                                modifier = Modifier.testTag(GameSettlementTags.cancel(diarist.chargeId)),
                            )
                        } else if (diarist.canUndo) {
                            SaqzButton(
                                label = stringResource(Res.string.settle_undo_receipt),
                                onClick = { onIntent(GameSettlementIntent.OpenUndo(diarist.chargeId)) },
                                variant = SaqzButtonVariant.Ghost,
                                size = SaqzButtonSize.Sm,
                                enabled = !diarist.isUpdating,
                                modifier = Modifier.testTag(GameSettlementTags.undo(diarist.chargeId)),
                            )
                        }
                    }
                },
            ) {
                SaqzAvatar(name = diarist.name, photo = diarist.photo.avatarSlot(diarist.name))
                val amount = formatBrlShort(diarist.amountCents)
                CashRowText(
                    title = diarist.name,
                    subtitle = when (diarist.status) {
                        ChargeStatus.Paid -> listOfNotNull(
                            stringResource(Res.string.settle_paid_amount, amount),
                            diarist.cashMeta.substringAfter(" · ", "").ifEmpty { null },
                        ).joinToString(" · ")
                        ChargeStatus.Waived -> stringResource(Res.string.settle_waived_chip)
                        else -> stringResource(Res.string.settle_due_amount, amount)
                    },
                    muted = diarist.notPlayed == NotPlayedReason.Withdrew,
                )
                SaqzStatusChip(
                    text = stringResource(
                        when (diarist.notPlayed) {
                            NotPlayedReason.Waiting -> Res.string.settle_waiting_chip
                            NotPlayedReason.NotReleased -> Res.string.settle_not_released_chip
                            else -> Res.string.settle_withdrew_chip
                        },
                    ),
                    tone = if (diarist.notPlayed == NotPlayedReason.Withdrew) SaqzChipTone.Neutral else SaqzChipTone.Warning,
                    modifier = Modifier.testTag(GameSettlementTags.notPlayed(diarist.chargeId)),
                )
            }
        }
    }
}
