package br.com.saqz.groups.presentation.ui.finance.groupcash

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import br.com.saqz.designsystem.SaqzAvatar
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonSize
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzProgressBar
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.SaqzTopAppBar
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxAction.ChargeMissing
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxAction.CopyPix
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxAction.MarkReceived
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxAction.Register
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxAction.Retry
import br.com.saqz.groups.presentation.ui.finance.groupcash.GroupCashboxAction.ViewFullStatement
import br.com.saqz.groups.resources.Res
import br.com.saqz.groups.resources.group_cashbox_charge
import br.com.saqz.groups.resources.group_cashbox_charge_missing
import br.com.saqz.groups.resources.group_cashbox_debtors_title
import br.com.saqz.groups.resources.group_cashbox_empty_action
import br.com.saqz.groups.resources.group_cashbox_empty_body
import br.com.saqz.groups.resources.group_cashbox_empty_title
import br.com.saqz.groups.resources.group_cashbox_expenses
import br.com.saqz.groups.resources.group_cashbox_load_failure_body
import br.com.saqz.groups.resources.group_cashbox_load_failure_title
import br.com.saqz.groups.resources.group_cashbox_monthly_title
import br.com.saqz.groups.resources.group_cashbox_note
import br.com.saqz.groups.resources.group_cashbox_open
import br.com.saqz.groups.resources.group_cashbox_operation_failure
import br.com.saqz.groups.resources.group_cashbox_overdue_action
import br.com.saqz.groups.resources.group_cashbox_pix_copy
import br.com.saqz.groups.resources.group_cashbox_pix_title
import br.com.saqz.groups.resources.group_cashbox_received_action
import br.com.saqz.groups.resources.group_cashbox_register
import br.com.saqz.groups.resources.group_cashbox_received
import br.com.saqz.groups.resources.group_cashbox_retry
import br.com.saqz.groups.resources.group_cashbox_statement
import br.com.saqz.groups.resources.group_cashbox_received_monthly_suffix
import br.com.saqz.groups.resources.group_cashbox_title
import org.jetbrains.compose.resources.stringResource

internal object GroupCashboxTags {
    const val Screen = "group-cashbox"
    const val ChargeMissing = "group-cashbox-charge-missing"
    const val Register = "group-cashbox-register"
    const val Monthly = "group-cashbox-monthly"
    const val Overdue = "group-cashbox-overdue"
    const val OverdueCharge = "group-cashbox-overdue-charge"
    const val Debtors = "group-cashbox-debtors"
    const val Pix = "group-cashbox-pix"
    const val PixCopy = "group-cashbox-pix-copy"
    const val Empty = "group-cashbox-empty"
    const val Failure = "group-cashbox-failure"
    const val Retry = "group-cashbox-retry"
    const val Statement = "group-cashbox-statement"
}

@Composable
internal fun GroupCashboxScreen(
    state: GroupCashboxState,
    onBack: () -> Unit,
    onAction: (GroupCashboxAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().testTag(GroupCashboxTags.Screen)) {
        SaqzTopAppBar(title = state.groupName.ifBlank { null }, onBack = onBack)
        when {
            state.isLoading -> LoadingContent()
            state.loadFailed -> LoadFailure(onRetry = { onAction(Retry) })
            else -> LoadedContent(state = state, onAction = onAction)
        }
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
private fun LoadedContent(state: GroupCashboxState, onAction: (GroupCashboxAction) -> Unit) {
    val metrics = SaqzTheme.metrics
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalArrangement = Arrangement.spacedBy(metrics.blockGap * 2),
    ) {
        CashboxHeader(state = state, onAction = onAction)
        Text(
            text = stringResource(Res.string.group_cashbox_note),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
        if (state.cashboxEmpty) {
            EmptyCashbox(onAction = onAction)
        }
        state.overdueBanner?.let { OverdueBanner(it, onAction) }
        MonthlySection(state)
        if (state.debtors.isNotEmpty()) {
            DebtorsSection(state = state, onAction = onAction)
        }
        state.pix?.let { PixCard(pix = it, onCopy = { onAction(CopyPix) }) }
        SaqzButton(
            label = stringResource(Res.string.group_cashbox_statement),
            onClick = { onAction(ViewFullStatement) },
            variant = SaqzButtonVariant.Ghost,
            fullWidth = true,
            modifier = Modifier.testTag(GroupCashboxTags.Statement),
        )
    }
}

@Composable
private fun CashboxHeader(state: GroupCashboxState, onAction: (GroupCashboxAction) -> Unit) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    Column(verticalArrangement = Arrangement.spacedBy(metrics.subGrid)) {
        Text(
            text = stringResource(Res.string.group_cashbox_title),
            style = SaqzTheme.typography.eyebrow,
            color = colors.textSecondary,
        )
        Text(
            text = state.groupName,
            style = SaqzTheme.typography.title,
            color = colors.textPrimary,
        )
        Text(
            text = state.balanceLabel,
            style = SaqzTheme.typography.headline,
            color = colors.textPrimary,
        )
        Text(
            text = "${state.monthLabel} · ${state.monthlyMembersLabel}",
            style = SaqzTheme.typography.support,
            color = colors.textSecondary,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            SaqzButton(
                label = stringResource(Res.string.group_cashbox_charge_missing),
                onClick = { onAction(GroupCashboxAction.ChargeMissing) },
                variant = SaqzButtonVariant.Secondary,
                size = SaqzButtonSize.Sm,
                enabled = false,
                modifier = Modifier.weight(1f).testTag(GroupCashboxTags.ChargeMissing),
            )
            SaqzButton(
                label = stringResource(Res.string.group_cashbox_register),
                onClick = { onAction(Register) },
                size = SaqzButtonSize.Sm,
                modifier = Modifier.weight(1f).testTag(GroupCashboxTags.Register),
            )
        }
        if (state.operationFailed) {
            Text(
                text = stringResource(Res.string.group_cashbox_operation_failure),
                style = SaqzTheme.typography.support,
                color = colors.errorForeground,
            )
        }
    }
}

@Composable
private fun EmptyCashbox(onAction: (GroupCashboxAction) -> Unit) = SaqzCard(
    modifier = Modifier.testTag(GroupCashboxTags.Empty),
    tone = SaqzCardTone.Soft,
) {
    SaqzEmptyState(
        title = stringResource(Res.string.group_cashbox_empty_title),
        description = stringResource(Res.string.group_cashbox_empty_body),
        icon = SaqzIcons.CreditCard,
        action = stringResource(Res.string.group_cashbox_empty_action),
        onAction = { onAction(Register) },
    )
}

@Composable
private fun OverdueBanner(banner: OverdueBannerUi, onAction: (GroupCashboxAction) -> Unit) = SaqzCard(
    modifier = Modifier.testTag(GroupCashboxTags.Overdue),
    tone = SaqzCardTone.Soft,
) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
        SaqzIcon(SaqzIcons.CircleAlert, tint = SaqzTheme.colors.warningForeground)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(
                text = banner.message,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            banner.monthLabel?.let {
                SaqzStatusChip(text = it, tone = br.com.saqz.designsystem.SaqzChipTone.Warning)
            }
        }
    }
    SaqzButton(
        label = stringResource(Res.string.group_cashbox_overdue_action),
        onClick = { onAction(ChargeMissing) },
        variant = SaqzButtonVariant.Secondary,
        size = SaqzButtonSize.Sm,
        enabled = false,
        modifier = Modifier.testTag(GroupCashboxTags.OverdueCharge),
    )
}

@Composable
private fun MonthlySection(state: GroupCashboxState) = Column(
    modifier = Modifier.testTag(GroupCashboxTags.Monthly),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.group_cashbox_monthly_title))
    SaqzCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = state.monthlyProgressLabel,
                style = SaqzTheme.typography.title,
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = stringResource(Res.string.group_cashbox_received_monthly_suffix),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        SaqzProgressBar(value = state.monthlyProgress)
        Row(horizontalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap)) {
            MonthlyMetric(stringResource(Res.string.group_cashbox_received), state.receivedLabel)
            MonthlyMetric(stringResource(Res.string.group_cashbox_open), state.openLabel)
            MonthlyMetric(stringResource(Res.string.group_cashbox_expenses), state.expensesLabel)
        }
    }
}

@Composable
private fun RowScope.MonthlyMetric(label: String, value: String) {
    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        Text(text = label, style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
        Text(
            text = value,
            style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary,
        )
    }
}

@Composable
private fun DebtorsSection(state: GroupCashboxState, onAction: (GroupCashboxAction) -> Unit) = Column(
    modifier = Modifier.testTag(GroupCashboxTags.Debtors),
    verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.blockGap),
) {
    SaqzSectionHeader(title = stringResource(Res.string.group_cashbox_debtors_title))
    SaqzCard(padded = false) {
        state.debtors.forEachIndexed { index, debtor ->
            DebtorRow(debtor = debtor, onAction = onAction)
            if (index < state.debtors.lastIndex) {
                br.com.saqz.designsystem.SaqzDivider()
            }
        }
    }
}

@Composable
private fun DebtorRow(debtor: DebtorUi, onAction: (GroupCashboxAction) -> Unit) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(metrics.blockGap),
    ) {
        SaqzAvatar(name = debtor.name)
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid / 4)) {
            Text(
                text = debtor.name,
                style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                color = SaqzTheme.colors.textPrimary,
            )
            Text(
                text = "${debtor.dueLabel} · ${debtor.amountLabel}",
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            SaqzButton(
                label = stringResource(Res.string.group_cashbox_charge),
                onClick = { onAction(ChargeMissing) },
                variant = SaqzButtonVariant.Ghost,
                size = SaqzButtonSize.Sm,
                enabled = false,
            )
            SaqzButton(
                label = stringResource(Res.string.group_cashbox_received_action),
                onClick = { onAction(MarkReceived(debtor.chargeId)) },
                size = SaqzButtonSize.Sm,
                loading = debtor.isUpdating,
            )
        }
    }
}

@Composable
private fun PixCard(pix: PixUi, onCopy: () -> Unit) = SaqzCard(modifier = Modifier.testTag(GroupCashboxTags.Pix)) {
    SaqzSectionHeader(title = stringResource(Res.string.group_cashbox_pix_title))
    Text(text = pix.key, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
    pix.label?.takeIf(String::isNotBlank)?.let {
        Text(text = it, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
    }
    SaqzButton(
        label = stringResource(Res.string.group_cashbox_pix_copy),
        onClick = onCopy,
        variant = SaqzButtonVariant.Secondary,
        fullWidth = true,
        modifier = Modifier.testTag(GroupCashboxTags.PixCopy),
    )
}
