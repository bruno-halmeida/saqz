package br.com.saqz.groups.presentation.ui.finance.groupcash

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
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzHeroCard
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIconButton
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzPullToRefresh
import br.com.saqz.designsystem.SaqzScoreTab
import br.com.saqz.designsystem.SaqzScoreTabTone
import br.com.saqz.designsystem.SaqzScoreTabs
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSheetAction
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.photo.avatarSlot
import br.com.saqz.groups.presentation.ui.PixCopiedToast
import br.com.saqz.groups.presentation.ui.RefreshFailedToast
import br.com.saqz.groups.presentation.ui.finance.CashGroupHeader
import br.com.saqz.groups.presentation.ui.finance.CashLedgerRow
import br.com.saqz.groups.presentation.ui.finance.CashListRow
import br.com.saqz.groups.presentation.ui.finance.CashMoneyField
import br.com.saqz.groups.presentation.ui.finance.CashPendingStrip
import br.com.saqz.groups.presentation.ui.finance.CashRowText
import br.com.saqz.groups.presentation.ui.finance.PixKeyNotice
import br.com.saqz.groups.presentation.ui.finance.monthNameOf
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
internal object GroupCashboxTags {
    const val Screen = "group-cashbox"
    const val ChargeMissing = "group-cashbox-charge-missing"
    const val Income = "group-cashbox-income"
    const val Expense = "group-cashbox-expense"
    const val Menu = "group-cashbox-menu"
    const val MenuOpening = "group-cashbox-menu-opening"
    const val GenerateMonthly = "group-cashbox-generate-monthly"
    const val Hero = "group-cashbox-hero"
    const val Balance = "group-cashbox-balance"
    const val TabIn = "group-cashbox-tab-in"
    const val TabOut = "group-cashbox-tab-out"
    const val TabDue = "group-cashbox-tab-due"
    const val Uncharged = "group-cashbox-uncharged"
    const val UnchargedReview = "group-cashbox-uncharged-review"
    const val OpeningQuestion = "group-cashbox-opening-question"
    const val OpeningInput = "group-cashbox-opening-input"
    const val OpeningSave = "group-cashbox-opening-save"
    const val OpeningZero = "group-cashbox-opening-zero"
    const val OpeningConflict = "group-cashbox-opening-conflict"
    const val Monthly = "group-cashbox-monthly"
    const val PixKeyNotice = "group-cashbox-pix-key-notice"
    const val Debtors = "group-cashbox-debtors"
    const val Entries = "group-cashbox-entries"
    const val Empty = "group-cashbox-empty"
    const val Failure = "group-cashbox-failure"
    const val Retry = "group-cashbox-retry"
    const val Statement = "group-cashbox-statement"
    const val WaiveFailure = "group-cashbox-waive-failure"
    const val CancelFailure = "group-cashbox-cancel-failure"
    const val Reviews = "group-cashbox-reviews"
    const val ReviewFailure = "group-cashbox-review-failure"
    const val Settled = "group-cashbox-settled"
    const val UndoFailure = "group-cashbox-undo-failure"

    fun received(chargeId: String) = "group-cashbox-received-$chargeId"
    fun debtorMore(chargeId: String) = "group-cashbox-more-$chargeId"
    fun chargeIndividual(chargeId: String) = "group-cashbox-charge-$chargeId"
    fun chargeReminded(chargeId: String) = "group-cashbox-reminded-$chargeId"
    fun chargeWaive(chargeId: String) = "group-cashbox-waive-$chargeId"
    fun chargeCancel(chargeId: String) = "group-cashbox-cancel-$chargeId"
    fun chargeWithdrew(chargeId: String) = "group-cashbox-withdrew-$chargeId"
    fun chargeNoShow(chargeId: String) = "group-cashbox-no-show-$chargeId"
    fun review(chargeId: String) = "group-cashbox-review-$chargeId"
    fun reviewAction(chargeId: String) = "group-cashbox-review-action-$chargeId"
    fun undo(chargeId: String) = "group-cashbox-undo-$chargeId"
}

@Composable
internal fun GroupCashboxScreen(
    state: GroupCashboxState,
    onBack: () -> Unit,
    onIntent: (GroupCashboxIntent) -> Unit,
    modifier: Modifier = Modifier,
    reminder: ChargeReminderState = ChargeReminderState(),
    onSendReminders: (List<String>) -> Unit = {},
    /** FIN-009: o grupo recebe pelo app; some o aviso da chave Pix pessoal. */
    receivingInApp: Boolean = false,
    /** Carteira e Pix para revisar (Gestor-Caixa), desenhados pela feature de recebimentos. */
    receivablesHeader: (@Composable () -> Unit)? = null,
) {
    val receiptDebtor = state.debtors.firstOrNull { it.chargeId == state.receiptSheetChargeId }
    val waiveDebtor = state.debtors.firstOrNull { it.chargeId == state.waiveSheetChargeId }
    val cancelDebtor = state.debtors.firstOrNull { it.chargeId == state.cancelSheetChargeId }
    val menuDebtor = state.debtors.firstOrNull { it.chargeId == state.debtorMenuChargeId }
    val reviewCharge = state.reviews.firstOrNull { it.chargeId == state.reviewSheetChargeId }
    val undoCharge = state.settled.firstOrNull { it.chargeId == state.undoSheetChargeId }
    val chargeDebtors = state.debtors.filter {
        state.chargeSheetChargeId == null || it.chargeId == state.chargeSheetChargeId
    }
    Box(modifier = modifier.fillMaxSize().background(SaqzTheme.colors.background).testTag(GroupCashboxTags.Screen)) {
        Column(modifier = Modifier.fillMaxSize()) {
            SaqzTopAppBar(
                title = state.groupName.ifBlank { null },
                onBack = onBack,
                actions = {
                    if (!state.isLoading && !state.loadFailed) {
                        SaqzIconButton(
                            onClick = { onIntent(GroupCashboxIntent.OpenMenu) },
                            contentDescription = stringResource(Res.string.cash_more_actions),
                            modifier = Modifier.testTag(GroupCashboxTags.Menu),
                        ) { SaqzIcon(SaqzIcons.MoreHorizontal, tint = SaqzTheme.colors.primary) }
                    }
                },
            )
            when {
                state.isLoading -> LoadingContent()
                state.loadFailed -> LoadFailure(onRetry = { onIntent(GroupCashboxIntent.Retry) })
                else -> SaqzPullToRefresh(
                    refreshing = state.isRefreshing,
                    onRefresh = { onIntent(GroupCashboxIntent.PullRefresh) },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LoadedContent(
                        state = state,
                        onIntent = onIntent,
                        receivingInApp = receivingInApp,
                        receivablesHeader = receivablesHeader,
                        remindedChargeIds = reminder.remindedChargeIds,
                    )
                }
            }
        }
        RefreshFailedToast(
            visible = state.refreshFailed,
            onDismiss = { onIntent(GroupCashboxIntent.DismissRefreshFailed) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        PixCopiedToast(
            visible = state.pixCopied,
            onDismiss = { onIntent(GroupCashboxIntent.DismissPixCopied) },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
        CashboxMenuSheet(state, onIntent)
        DebtorMenuSheet(menuDebtor, onIntent)
        OpeningBalanceSheet(state, onIntent)
        ChargeSheet(
            open = state.chargeSheetOpen,
            debtors = chargeDebtors,
            onClose = { onIntent(GroupCashboxIntent.DismissChargeSheet) },
            onSend = onSendReminders,
            delivery = reminder,
        )
        ReceiptSheet(
            open = state.receiptSheetChargeId != null,
            debtor = receiptDebtor,
            onClose = { onIntent(GroupCashboxIntent.DismissReceiptSheet) },
            onConfirm = { paidMethod ->
                receiptDebtor?.let { onIntent(GroupCashboxIntent.MarkReceived(it.chargeId, paidMethod)) }
            },
        )
        WaiveSheet(
            open = state.waiveSheetChargeId != null,
            debtor = waiveDebtor,
            onClose = { onIntent(GroupCashboxIntent.DismissWaiveSheet) },
            onConfirm = { waiveDebtor?.let { onIntent(GroupCashboxIntent.Waive(it.chargeId)) } },
        )
        CancelChargeSheet(
            open = state.cancelSheetChargeId != null,
            debtor = cancelDebtor,
            busy = state.updatingChargeId != null,
            onClose = { onIntent(GroupCashboxIntent.DismissCancelSheet) },
            onConfirm = { cancelDebtor?.let { onIntent(GroupCashboxIntent.Cancel(it.chargeId)) } },
        )
        ReviewSheet(
            open = state.reviewSheetChargeId != null,
            name = reviewCharge?.name,
            referenceLabel = reviewCharge?.referenceLabel,
            amountLabel = reviewCharge?.amountLabel,
            busy = state.updatingChargeId != null,
            onClose = { onIntent(GroupCashboxIntent.DismissReviewSheet) },
            onConfirm = { reviewCharge?.let { onIntent(GroupCashboxIntent.ResolveReview(it.chargeId)) } },
            photo = reviewCharge?.photo,
        )
        UndoSheet(
            open = state.undoSheetChargeId != null,
            name = undoCharge?.name,
            referenceLabel = undoCharge?.referenceLabel,
            amountLabel = undoCharge?.amountLabel,
            received = undoCharge?.received == true,
            busy = state.updatingChargeId != null,
            onClose = { onIntent(GroupCashboxIntent.DismissUndoSheet) },
            onConfirm = { note -> undoCharge?.let { onIntent(GroupCashboxIntent.Undo(it.chargeId, note)) } },
            photo = undoCharge?.photo,
        )
    }
}

@Composable
private fun LoadingContent() = Column(
    modifier = Modifier.fillMaxSize(),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    SaqzSpinner()
}

@Composable
private fun LoadFailure(onRetry: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().testTag(GroupCashboxTags.Failure),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center,
) {
    SaqzEmptyState(
        title = stringResource(Res.string.group_cashbox_load_failure_title),
        description = stringResource(Res.string.group_cashbox_load_failure_body),
        icon = SaqzIcons.CircleAlert,
        action = stringResource(Res.string.group_cashbox_retry),
        onAction = onRetry,
        modifier = Modifier.testTag(GroupCashboxTags.Retry),
    )
}

@Composable
private fun LoadedContent(
    state: GroupCashboxState,
    onIntent: (GroupCashboxIntent) -> Unit,
    receivingInApp: Boolean,
    receivablesHeader: (@Composable () -> Unit)?,
    remindedChargeIds: Set<String>,
) {
    val metrics = SaqzTheme.metrics
    Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
        CashboxHero(state, onIntent)
        val notices = state.operationFailed || state.undoFailed || state.reviewFailed || state.cancelFailed ||
            state.waiveFailed || (state.pixKeyNotice && !receivingInApp) || receivablesHeader != null
        if (notices) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
                verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
            ) {
                Failures(state)
                receivablesHeader?.invoke()
                if (state.pixKeyNotice && !receivingInApp) {
                    PixKeyNotice(
                        onOpenSetup = { onIntent(GroupCashboxIntent.OpenPixSetup) },
                        modifier = Modifier.testTag(GroupCashboxTags.PixKeyNotice),
                    )
                }
            }
        }
        when {
            state.askOpening -> OpeningQuestion(state, onIntent)
            state.tab == CashboxTab.Due -> DueList(state, onIntent, remindedChargeIds)
            state.tab == CashboxTab.In -> LedgerList(state, out = false, onIntent = onIntent)
            else -> LedgerList(state, out = true, onIntent = onIntent)
        }
    }
}

@Composable
private fun CashboxHero(state: GroupCashboxState, onIntent: (GroupCashboxIntent) -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    val zeroed = state.askOpening
    val tabs = listOf(CashboxTab.In, CashboxTab.Out, CashboxTab.Due)
    SaqzHeroCard(
        kicker = stringResource(Res.string.cash_kicker),
        title = state.balanceLabel,
        shape = RectangleShape,
        kickerMuted = zeroed,
        meta = when {
            zeroed || state.cashboxEmpty -> stringResource(Res.string.cash_nothing_yet)
            else -> state.openingLabel?.let { stringResource(Res.string.cash_includes_opening, it) }
        },
        trailing = if (zeroed || state.monthKey.isEmpty()) null else {
            { SaqzStatusChip(text = monthNameOf(state.monthKey).replaceFirstChar(Char::uppercase), tone = SaqzChipTone.Inverse) }
        },
        modifier = Modifier.testTag(GroupCashboxTags.Hero),
        footer = if (zeroed) null else {
            {
                SaqzScoreTabs(
                    tabs = listOf(
                        SaqzScoreTab(state.monthInLabel, stringResource(Res.string.cash_tab_in), testTag = GroupCashboxTags.TabIn),
                        SaqzScoreTab(
                            state.monthOutLabel,
                            stringResource(Res.string.cash_tab_out),
                            tone = SaqzScoreTabTone.Out,
                            testTag = GroupCashboxTags.TabOut,
                        ),
                        SaqzScoreTab(state.dueLabel, stringResource(Res.string.cash_tab_due), testTag = GroupCashboxTags.TabDue),
                    ),
                    selectedIndex = tabs.indexOf(state.tab),
                    onSelect = { onIntent(GroupCashboxIntent.SelectTab(tabs[it])) },
                )
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.grid)) {
            SaqzButton(
                label = stringResource(Res.string.cash_income),
                onClick = { onIntent(GroupCashboxIntent.NewIncome) },
                variant = SaqzButtonVariant.Inverse,
                leadingContent = { SaqzIcon(SaqzIcons.Plus, tint = it) },
                modifier = Modifier.weight(1f).testTag(GroupCashboxTags.Income),
            )
            val out = if (zeroed) colors.onPrimary else colors.outOnPrimary
            SaqzButton(
                label = stringResource(Res.string.cash_expense),
                onClick = { onIntent(GroupCashboxIntent.NewExpense) },
                variant = SaqzButtonVariant.Ghost,
                contentColor = out,
                borderColor = out,
                leadingContent = { SaqzIcon(SaqzIcons.Minus, tint = it) },
                modifier = Modifier.weight(1f).testTag(GroupCashboxTags.Expense),
            )
        }
        state.uncharged?.let { pending ->
            CashPendingStrip(
                title = if (pending.peopleCount == 1) {
                    stringResource(Res.string.cash_uncharged_title_one)
                } else {
                    stringResource(Res.string.cash_uncharged_title, pending.peopleCount)
                },
                subtitle = when {
                    pending.singleGameDate != null -> stringResource(Res.string.cash_uncharged_single, pending.singleGameDate)
                    pending.monthName != null ->
                        stringResource(Res.string.cash_uncharged_games_month, pending.gamesCount, pending.monthName)
                    else -> stringResource(Res.string.cash_uncharged_games, pending.gamesCount)
                },
                action = stringResource(Res.string.cash_review),
                onAction = { onIntent(GroupCashboxIntent.ReviewUncharged) },
                modifier = Modifier.testTag(GroupCashboxTags.Uncharged),
                actionTag = GroupCashboxTags.UnchargedReview,
            )
        }
    }
}

@Composable
private fun Failures(state: GroupCashboxState) {
    val colors = SaqzTheme.colors
    listOfNotNull(
        Res.string.group_cashbox_operation_failure.takeIf { state.operationFailed }?.let { it to null },
        Res.string.group_cashbox_undo_failure.takeIf { state.undoFailed }?.let { it to GroupCashboxTags.UndoFailure },
        Res.string.group_cashbox_review_failure.takeIf { state.reviewFailed }?.let { it to GroupCashboxTags.ReviewFailure },
        Res.string.group_cashbox_cancel_failure.takeIf { state.cancelFailed }?.let { it to GroupCashboxTags.CancelFailure },
        Res.string.group_cashbox_waive_failure.takeIf { state.waiveFailed }?.let { it to GroupCashboxTags.WaiveFailure },
    ).forEach { (text, tag) ->
        Text(
            text = stringResource(text),
            style = SaqzTheme.typography.support,
            color = colors.errorForeground,
            modifier = tag?.let { Modifier.testTag(it) } ?: Modifier,
        )
    }
}

/** Caixa zerado (FIN-006): saldo inicial nunca respondido e nada lançado. */
@Composable
private fun OpeningQuestion(state: GroupCashboxState, onIntent: (GroupCashboxIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier.fillMaxWidth().padding(metrics.horizontalPadding),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzCard(modifier = Modifier.testTag(GroupCashboxTags.OpeningQuestion)) {
            Text(
                text = stringResource(Res.string.cash_opening_question),
                style = SaqzTheme.typography.title,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.cash_opening_question_body),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
            OpeningField(state, onIntent, showLabel = false)
            SaqzButton(
                label = stringResource(Res.string.cash_opening_save),
                onClick = { onIntent(GroupCashboxIntent.SaveOpeningBalance) },
                fullWidth = true,
                loading = state.openingSaving,
                modifier = Modifier.testTag(GroupCashboxTags.OpeningSave),
            )
            SaqzButton(
                label = stringResource(Res.string.cash_opening_zero),
                onClick = { onIntent(GroupCashboxIntent.StartFromZero) },
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                enabled = !state.openingSaving,
                modifier = Modifier.testTag(GroupCashboxTags.OpeningZero),
            )
        }
        Text(
            text = stringResource(Res.string.cash_opening_after),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
private fun OpeningField(state: GroupCashboxState, onIntent: (GroupCashboxIntent) -> Unit, showLabel: Boolean) = Column {
    CashMoneyField(
        value = state.openingInput,
        onValueChange = { onIntent(GroupCashboxIntent.OpeningInputChanged(it)) },
        label = stringResource(Res.string.cash_opening_label),
        showLabel = showLabel,
        errorText = when {
            state.openingInvalid -> stringResource(Res.string.cash_opening_invalid)
            state.openingFailed -> stringResource(Res.string.cash_opening_failed)
            else -> null
        },
        helperText = if (state.openingConflict) stringResource(Res.string.cash_opening_conflict) else null,
        modifier = Modifier.fillMaxWidth().testTag(GroupCashboxTags.OpeningInput),
    )
    if (state.openingConflict) {
        Box(Modifier.testTag(GroupCashboxTags.OpeningConflict))
    }
}

@Composable
private fun DueList(
    state: GroupCashboxState,
    onIntent: (GroupCashboxIntent) -> Unit,
    remindedChargeIds: Set<String>,
) {
    val metrics = SaqzTheme.metrics
    val month = monthNameOf(state.monthKey)
    Column(Modifier.fillMaxWidth().testTag(GroupCashboxTags.Debtors)) {
        if (state.reviews.isNotEmpty()) {
            Column(Modifier.padding(metrics.horizontalPadding)) { ReviewsSection(state, onIntent) }
        }
        if (state.monthlyTotalCount > 0 || state.monthlyDebtors.isNotEmpty()) {
            CashGroupHeader(
                title = stringResource(Res.string.cash_monthly_header, month),
                trailing = stringResource(Res.string.cash_monthly_paid_count, state.paidMonthlyCount, state.monthlyTotalCount),
                modifier = Modifier.testTag(GroupCashboxTags.Monthly),
            )
            state.monthlyDebtors.forEachIndexed { index, debtor ->
                DebtorRow(debtor, first = index == 0, onIntent = onIntent, reminded = debtor.chargeId in remindedChargeIds)
            }
        }
        if (state.otherMonthlyDebtors.isNotEmpty()) {
            CashGroupHeader(
                title = stringResource(Res.string.cash_other_monthly_header),
                trailing = stringResource(Res.string.cash_open_count, state.otherMonthlyDebtors.size),
            )
            state.otherMonthlyDebtors.forEachIndexed { index, debtor ->
                DebtorRow(debtor, first = index == 0, onIntent = onIntent, reminded = debtor.chargeId in remindedChargeIds)
            }
        }
        if (state.gameDebtors.isNotEmpty()) {
            CashGroupHeader(
                title = stringResource(Res.string.cash_game_header),
                trailing = stringResource(Res.string.cash_open_count, state.gameDebtors.size),
            )
            state.gameDebtors.forEachIndexed { index, debtor ->
                DebtorRow(debtor, first = index == 0, onIntent = onIntent, reminded = debtor.chargeId in remindedChargeIds)
            }
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            if (state.debtors.isEmpty()) {
                Text(
                    text = stringResource(Res.string.cash_nobody_due),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                    modifier = Modifier.testTag(GroupCashboxTags.Empty),
                )
            } else {
                SaqzButton(
                    label = stringResource(Res.string.cash_charge_all, state.debtors.size),
                    onClick = { onIntent(GroupCashboxIntent.ChargeMissing) },
                    fullWidth = true,
                    leadingContent = { SaqzIcon(SaqzIcons.Megaphone, tint = it) },
                    modifier = Modifier.testTag(GroupCashboxTags.ChargeMissing),
                )
            }
            if (state.settled.isNotEmpty()) SettledSection(state, onIntent)
        }
    }
}

@Composable
private fun LedgerList(state: GroupCashboxState, out: Boolean, onIntent: (GroupCashboxIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    val rows = if (out) state.monthOut else state.monthIn
    val month = monthNameOf(state.monthKey)
    Column(Modifier.fillMaxWidth().testTag(GroupCashboxTags.Entries)) {
        CashGroupHeader(
            title = stringResource(if (out) Res.string.cash_out_header else Res.string.cash_in_header, month),
            trailing = when (rows.size) {
                0 -> null
                1 -> stringResource(Res.string.cash_entries_one)
                else -> stringResource(Res.string.cash_entries_count, rows.size)
            },
        )
        rows.forEachIndexed { index, row ->
            CashLedgerRow(
                title = row.title,
                subtitle = row.subtitle,
                amount = row.amountLabel,
                out = row.out,
                first = index == 0,
            )
        }
        Column(
            modifier = Modifier.fillMaxWidth().padding(metrics.horizontalPadding),
            verticalArrangement = Arrangement.spacedBy(metrics.blockGap),
        ) {
            if (rows.isEmpty()) {
                Text(
                    text = stringResource(if (out) Res.string.cash_no_out else Res.string.cash_no_in),
                    style = SaqzTheme.typography.support,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            SaqzButton(
                label = stringResource(Res.string.cash_statement),
                onClick = { onIntent(GroupCashboxIntent.ViewFullStatement) },
                variant = SaqzButtonVariant.Ghost,
                fullWidth = true,
                modifier = Modifier.testTag(GroupCashboxTags.Statement),
            )
        }
    }
}

@Composable
private fun DebtorRow(
    debtor: DebtorUi,
    first: Boolean,
    onIntent: (GroupCashboxIntent) -> Unit,
    reminded: Boolean,
) = CashListRow(
    first = first,
    below = if (debtor.withdrew || debtor.noShow || reminded) {
        {
            Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
                if (debtor.withdrew) {
                    SaqzStatusChip(
                        text = stringResource(Res.string.group_cashbox_withdrew_status),
                        tone = SaqzChipTone.Warning,
                        modifier = Modifier.testTag(GroupCashboxTags.chargeWithdrew(debtor.chargeId)),
                    )
                }
                if (debtor.noShow) {
                    SaqzStatusChip(
                        text = stringResource(Res.string.group_cashbox_no_show_status),
                        tone = SaqzChipTone.Warning,
                        modifier = Modifier.testTag(GroupCashboxTags.chargeNoShow(debtor.chargeId)),
                    )
                }
                if (reminded) {
                    Text(
                        text = stringResource(Res.string.sheet_charge_already_reminded),
                        style = SaqzTheme.typography.caption,
                        color = SaqzTheme.colors.textSecondary,
                        modifier = Modifier.testTag(GroupCashboxTags.chargeReminded(debtor.chargeId)),
                    )
                }
            }
        }
    } else null,
) {
    SaqzAvatar(name = debtor.name, photo = debtor.photo.avatarSlot(debtor.name))
    CashRowText(debtor.name, debtor.cashMeta)
    SaqzButton(
        label = stringResource(Res.string.cash_received),
        onClick = { onIntent(GroupCashboxIntent.OpenReceipt(debtor.chargeId)) },
        variant = SaqzButtonVariant.Secondary,
        size = SaqzButtonSize.Sm,
        loading = debtor.isUpdating,
        modifier = Modifier.testTag(GroupCashboxTags.received(debtor.chargeId)),
    )
    SaqzIconButton(
        onClick = { onIntent(GroupCashboxIntent.OpenDebtorMenu(debtor.chargeId)) },
        contentDescription = stringResource(Res.string.cash_debtor_more, debtor.name),
        enabled = !debtor.isUpdating,
        modifier = Modifier.testTag(GroupCashboxTags.debtorMore(debtor.chargeId)),
    ) { SaqzIcon(SaqzIcons.MoreVertical, tint = SaqzTheme.colors.textSecondary) }
}

/** O ⋮ do devedor: Cobrar, Dispensar (quem desistiu ou faltou) e Cancelar (FIN-002, DEB-062). */
@Composable
private fun DebtorMenuSheet(debtor: DebtorUi?, onIntent: (GroupCashboxIntent) -> Unit) = SaqzBottomSheet(
    open = debtor != null,
    onClose = { onIntent(GroupCashboxIntent.DismissDebtorMenu) },
    title = debtor?.name,
    description = debtor?.cashMeta,
) {
    if (debtor == null) return@SaqzBottomSheet
    SaqzSheetAction(
        icon = SaqzIcons.Megaphone,
        label = stringResource(Res.string.group_cashbox_charge),
        onClick = { onIntent(GroupCashboxIntent.ChargeIndividual(debtor.chargeId)) },
        divider = false,
        modifier = Modifier.testTag(GroupCashboxTags.chargeIndividual(debtor.chargeId)),
    )
    if (debtor.waivable) {
        SaqzSheetAction(
            icon = SaqzIcons.Ban,
            label = stringResource(Res.string.group_cashbox_waive_action),
            onClick = { onIntent(GroupCashboxIntent.OpenWaive(debtor.chargeId)) },
            modifier = Modifier.testTag(GroupCashboxTags.chargeWaive(debtor.chargeId)),
        )
    }
    SaqzSheetAction(
        icon = SaqzIcons.CircleX,
        label = stringResource(Res.string.group_cashbox_cancel_action),
        onClick = { onIntent(GroupCashboxIntent.OpenCancel(debtor.chargeId)) },
        destructive = true,
        modifier = Modifier.testTag(GroupCashboxTags.chargeCancel(debtor.chargeId)),
    )
}

/** O ⋯ do caixa (decidido em 29/10): Saldo inicial e Criar cobranças do mês. */
@Composable
private fun CashboxMenuSheet(state: GroupCashboxState, onIntent: (GroupCashboxIntent) -> Unit) = SaqzBottomSheet(
    open = state.menuOpen,
    onClose = { onIntent(GroupCashboxIntent.DismissMenu) },
    title = stringResource(Res.string.cash_more_actions),
) {
    SaqzSheetAction(
        icon = SaqzIcons.Flag,
        label = stringResource(Res.string.cash_menu_opening),
        hint = state.openingBalanceCents?.let {
            stringResource(Res.string.cash_menu_opening_hint, br.com.saqz.groups.presentation.ui.finance.formatBrlShort(it))
        } ?: stringResource(Res.string.cash_menu_opening_empty),
        onClick = { onIntent(GroupCashboxIntent.OpenOpeningBalance) },
        divider = false,
        modifier = Modifier.testTag(GroupCashboxTags.MenuOpening),
    )
    SaqzSheetAction(
        icon = SaqzIcons.Calendar,
        label = stringResource(Res.string.cash_menu_monthly),
        hint = stringResource(Res.string.cash_menu_monthly_hint, monthNameOf(state.monthKey)),
        onClick = { onIntent(GroupCashboxIntent.GenerateMonthly) },
        modifier = Modifier.testTag(GroupCashboxTags.GenerateMonthly),
    )
    Text(
        text = stringResource(Res.string.cash_menu_pix_note),
        style = SaqzTheme.typography.support,
        color = SaqzTheme.colors.textSecondary,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = SaqzTheme.metrics.blockGap),
    )
}

/** Editar o saldo inicial, do ⋯ do caixa. */
@Composable
private fun OpeningBalanceSheet(state: GroupCashboxState, onIntent: (GroupCashboxIntent) -> Unit) = SaqzBottomSheet(
    open = state.openingSheetOpen,
    onClose = { onIntent(GroupCashboxIntent.DismissOpeningBalance) },
    title = stringResource(Res.string.cash_opening_sheet_title),
    description = stringResource(Res.string.cash_opening_sheet_body),
    footer = {
        SaqzButton(
            label = stringResource(Res.string.cash_opening_save_short),
            onClick = { onIntent(GroupCashboxIntent.SaveOpeningBalance) },
            fullWidth = true,
            loading = state.openingSaving,
            modifier = Modifier.testTag(GroupCashboxTags.OpeningSave),
        )
    },
) {
    OpeningField(state, onIntent, showLabel = true)
}

@Composable
private fun ReviewsSection(
    state: GroupCashboxState,
    onIntent: (GroupCashboxIntent) -> Unit,
) = Column(
    modifier = Modifier.testTag(GroupCashboxTags.Reviews),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.group_cashbox_review_title))
    SaqzCard(padded = false) {
        state.reviews.forEachIndexed { index, review ->
            ReviewRow(review, onIntent)
            if (index < state.reviews.lastIndex) br.com.saqz.designsystem.SaqzDivider()
        }
    }
}

@Composable
private fun SettledSection(
    state: GroupCashboxState,
    onIntent: (GroupCashboxIntent) -> Unit,
) = Column(
    modifier = Modifier.testTag(GroupCashboxTags.Settled),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.group_cashbox_settled_title))
    SaqzCard(padded = false) {
        state.settled.forEachIndexed { index, settled ->
            SettledRow(settled, onIntent)
            if (index < state.settled.lastIndex) br.com.saqz.designsystem.SaqzDivider()
        }
    }
}

@Composable
private fun SettledRow(settled: SettledUi, onIntent: (GroupCashboxIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzAvatar(name = settled.name, photo = settled.photo.avatarSlot(settled.name))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(
                text = settled.name,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = "${settled.referenceLabel} · ${settled.amountLabel}",
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
            SaqzStatusChip(
                text = stringResource(
                    if (settled.received) Res.string.group_cashbox_settled_received else Res.string.group_cashbox_settled_waived,
                ),
                tone = if (settled.received) SaqzChipTone.Success else SaqzChipTone.Neutral,
            )
        }
        SaqzButton(
            label = stringResource(Res.string.group_cashbox_undo_action),
            onClick = { onIntent(GroupCashboxIntent.OpenUndo(settled.chargeId)) },
            variant = SaqzButtonVariant.Secondary,
            size = SaqzButtonSize.Sm,
            loading = settled.isUpdating,
            enabled = !settled.isUpdating,
            modifier = Modifier.testTag(GroupCashboxTags.undo(settled.chargeId)),
        )
    }
}

@Composable
private fun ReviewRow(review: ReviewUi, onIntent: (GroupCashboxIntent) -> Unit) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzAvatar(name = review.name, photo = review.photo.avatarSlot(review.name))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
            Text(
                text = review.name,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = "${review.referenceLabel} · ${review.amountLabel}",
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
            SaqzStatusChip(
                text = stringResource(Res.string.group_cashbox_review_status),
                tone = SaqzChipTone.Warning,
                modifier = Modifier.testTag(GroupCashboxTags.review(review.chargeId)),
            )
        }
        SaqzButton(
            label = stringResource(Res.string.group_cashbox_review_action),
            onClick = { onIntent(GroupCashboxIntent.OpenReview(review.chargeId)) },
            variant = SaqzButtonVariant.Secondary,
            size = SaqzButtonSize.Sm,
            enabled = !review.isUpdating,
            modifier = Modifier.testTag(GroupCashboxTags.reviewAction(review.chargeId)),
        )
    }
}
