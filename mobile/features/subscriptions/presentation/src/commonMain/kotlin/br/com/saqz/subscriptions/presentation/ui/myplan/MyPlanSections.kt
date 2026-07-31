package br.com.saqz.subscriptions.presentation.ui.myplan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import br.com.saqz.designsystem.SaqzBottomSheet
import br.com.saqz.designsystem.SaqzButton
import br.com.saqz.designsystem.SaqzButtonVariant
import br.com.saqz.designsystem.SaqzCard
import br.com.saqz.designsystem.SaqzCardTone
import br.com.saqz.designsystem.SaqzChipTone
import br.com.saqz.designsystem.SaqzDivider
import br.com.saqz.designsystem.SaqzIcon
import br.com.saqz.designsystem.SaqzIcons
import br.com.saqz.designsystem.SaqzProgressBar
import br.com.saqz.designsystem.SaqzSectionHeader
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.SaqzStatusChip
import br.com.saqz.designsystem.asString
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.subscriptions.presentation.myplan.MyPlanCardUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanChangeOptionUi
import br.com.saqz.subscriptions.presentation.myplan.MyPlanIntent
import br.com.saqz.subscriptions.presentation.myplan.MyPlanState
import br.com.saqz.subscriptions.presentation.myplan.MyPlanStatusTone
import br.com.saqz.subscriptions.presentation.myplan.MyPlanUsageUi
import br.com.saqz.subscriptions.resources.Res
import br.com.saqz.subscriptions.resources.myplan_access_until
import br.com.saqz.subscriptions.resources.myplan_cancel_button
import br.com.saqz.subscriptions.resources.myplan_cancel_confirm
import br.com.saqz.subscriptions.resources.myplan_cancel_helper
import br.com.saqz.subscriptions.resources.myplan_cancel_keep
import br.com.saqz.subscriptions.resources.myplan_cancel_sheet_title
import br.com.saqz.subscriptions.resources.myplan_change_current_tag
import br.com.saqz.subscriptions.resources.myplan_change_sheet_description
import br.com.saqz.subscriptions.resources.myplan_change_sheet_title
import br.com.saqz.subscriptions.resources.myplan_current_plan_label
import br.com.saqz.subscriptions.resources.myplan_manage_add_coupon
import br.com.saqz.subscriptions.resources.myplan_manage_change_plan
import br.com.saqz.subscriptions.resources.myplan_manage_payment_method
import br.com.saqz.subscriptions.resources.myplan_manage_receipts
import br.com.saqz.subscriptions.resources.myplan_manage_receipts_count
import br.com.saqz.subscriptions.resources.myplan_manage_receipts_count_one
import br.com.saqz.subscriptions.resources.myplan_manage_title
import br.com.saqz.subscriptions.resources.myplan_next_charge
import br.com.saqz.subscriptions.resources.myplan_pending_payment_dismiss
import br.com.saqz.subscriptions.resources.myplan_pending_payment_invoice
import br.com.saqz.subscriptions.resources.myplan_pending_payment_title
import br.com.saqz.subscriptions.resources.myplan_receipts_empty
import br.com.saqz.subscriptions.resources.myplan_receipts_load_more
import br.com.saqz.subscriptions.resources.myplan_receipts_sheet_title
import br.com.saqz.subscriptions.resources.myplan_retry
import br.com.saqz.subscriptions.resources.myplan_usage_title
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MyPlanCurrentCard(plan: MyPlanCardUi, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    val metrics = SaqzTheme.metrics
    SaqzCard(modifier = modifier.testTag(MyPlanTags.PlanCard), tone = SaqzCardTone.Soft) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = stringResource(Res.string.myplan_current_plan_label),
                style = SaqzTheme.typography.eyebrow,
                color = colors.primary,
            )
            SaqzStatusChip(text = plan.statusLabel.asString(), tone = plan.statusTone.toChipTone(), dot = true)
        }
        Text(text = plan.name, style = SaqzTheme.typography.title, color = colors.textPrimary)
        Text(text = plan.priceLine.asString(), style = SaqzTheme.typography.support, color = colors.textSecondary)
        if (plan.nextChargeDate != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(metrics.cardRadius))
                    .background(colors.surface)
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            ) {
                Text(
                    text = stringResource(Res.string.myplan_next_charge, plan.nextChargeDate) +
                        (plan.paymentMethodLabel?.let { " · ${it.asString()}" } ?: ""),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
            }
        }
        // Assinatura cancelada não tem próxima cobrança — o acesso é o que segue até
        // `currentPeriodEnd` (ver MyPlanMappers.toCardUi).
        if (plan.accessUntilDate != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(metrics.cardRadius))
                    .background(colors.surface)
                    .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap),
            ) {
                Text(
                    text = stringResource(Res.string.myplan_access_until, plan.accessUntilDate),
                    style = SaqzTheme.typography.support,
                    color = colors.textSecondary,
                )
            }
        }
        plan.pendingChangeLine?.let {
            Text(text = it.asString(), style = SaqzTheme.typography.support, color = colors.primary)
        }
    }
}

@Composable
internal fun MyPlanUsageCard(usage: MyPlanUsageUi, modifier: Modifier = Modifier) {
    val colors = SaqzTheme.colors
    SaqzCard(modifier = modifier.testTag(MyPlanTags.UsageCard)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = stringResource(Res.string.myplan_usage_title), style = SaqzTheme.typography.subtitle, color = colors.textPrimary)
            Text(text = usage.ratioLabel.asString(), style = SaqzTheme.typography.support, color = colors.primary)
        }
        if (usage.progress != null) {
            SaqzProgressBar(value = usage.progress)
        }
        Text(text = usage.helperText.asString(), style = SaqzTheme.typography.support, color = colors.textSecondary)
    }
}

@Composable
internal fun MyPlanManageSection(
    state: MyPlanState,
    onIntent: (MyPlanIntent) -> Unit,
    modifier: Modifier = Modifier,
) = Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
    SaqzSectionHeader(title = stringResource(Res.string.myplan_manage_title))
    // Assinatura efetivamente cancelada (achado do Codex no PR #93: `canceledAt != null`,
    // não `status` cru — ver MyPlanMappers.toCardUi) não troca de plano: o próprio backend
    // rejeita `ChangePlan` quando o status já migrou, e a janela antes disso não deveria
    // deixar o usuário criar uma cobrança de upgrade que ele não quer mais.
    val canceled = state.plan?.statusTone == MyPlanStatusTone.Canceled
    SaqzCard(padded = false) {
        if (!canceled) {
            MyPlanManageRow(
                label = stringResource(Res.string.myplan_manage_change_plan),
                tag = MyPlanTags.ChangePlan,
                onClick = { onIntent(MyPlanIntent.OpenChangePlan) },
            ) {
                SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
            }
            SaqzDivider()
        }
        // `paymentMethod` chega `null` do backend hoje — `GetMySubscription` hard-coda o
        // campo (achado do Codex no PR #93, gap de origem fora do escopo deste ticket).
        // Linha em branco e sem ação é pior que omitir; fast-follow quando o backend expuser
        // o valor de verdade.
        if (state.plan?.paymentMethodLabel != null) {
            MyPlanManageRow(
                label = stringResource(Res.string.myplan_manage_payment_method),
                tag = MyPlanTags.PaymentMethod,
                onClick = null,
            ) {
                Text(
                    text = state.plan.paymentMethodLabel.asString(),
                    style = SaqzTheme.typography.caption,
                    color = SaqzTheme.colors.textSecondary,
                )
            }
            SaqzDivider()
        }
        MyPlanManageRow(
            label = stringResource(Res.string.myplan_manage_receipts),
            tag = MyPlanTags.Receipts,
            onClick = { onIntent(MyPlanIntent.OpenReceipts) },
        ) {
            Text(
                text = receiptsCountLabel(state.receipts.size),
                style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary,
            )
        }
        SaqzDivider()
        // ponytail: sem endpoint de "adicionar cupom pós-assinatura" no SubscriptionGateway
        // hoje — a linha existe pela paridade visual com o export, sem ação por trás.
        MyPlanManageRow(
            label = stringResource(Res.string.myplan_manage_add_coupon),
            tag = MyPlanTags.AddCoupon,
            onClick = null,
        ) {
            SaqzIcon(SaqzIcons.ChevronRight, tint = SaqzTheme.colors.textSecondary)
        }
    }
}

@Composable
private fun MyPlanManageRow(
    label: String,
    tag: String,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    val metrics = SaqzTheme.metrics
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(onClickLabel = label, role = Role.Button, onClick = onClick) else Modifier,
            )
            .heightIn(min = metrics.minimumTouchTarget)
            .padding(horizontal = metrics.horizontalPadding, vertical = metrics.blockGap)
            .testTag(tag),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = SaqzTheme.typography.label, color = SaqzTheme.colors.textPrimary)
        trailing()
    }
}

@Composable
private fun receiptsCountLabel(count: Int): String = stringResource(
    if (count == 1) Res.string.myplan_manage_receipts_count_one else Res.string.myplan_manage_receipts_count,
    count,
)

@Composable
internal fun MyPlanCancelSection(onIntent: (MyPlanIntent) -> Unit, modifier: Modifier = Modifier) {
    val label = stringResource(Res.string.myplan_cancel_button)
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
        SaqzCard(
            modifier = Modifier
                .clip(RoundedCornerShape(SaqzTheme.metrics.cardRadius))
                .clickable(onClickLabel = label, role = Role.Button) { onIntent(MyPlanIntent.OpenCancel) }
                .testTag(MyPlanTags.CancelButton),
        ) {
            Text(
                text = label,
                style = SaqzTheme.typography.label,
                color = SaqzTheme.colors.textSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Text(
            text = stringResource(Res.string.myplan_cancel_helper),
            style = SaqzTheme.typography.support,
            color = SaqzTheme.colors.textSecondary,
        )
    }
}

@Composable
internal fun MyPlanChangeSheet(state: MyPlanState, onIntent: (MyPlanIntent) -> Unit) {
    SaqzBottomSheet(
        open = state.isChangeSheetOpen,
        onClose = { onIntent(MyPlanIntent.DismissChangePlan) },
        modifier = Modifier.testTag(MyPlanTags.ChangeSheet),
        title = stringResource(Res.string.myplan_change_sheet_title),
        description = stringResource(Res.string.myplan_change_sheet_description),
    ) {
        state.changeError?.let {
            Text(text = it.asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
        }
        state.changeOptions.forEach { option ->
            MyPlanChangeOptionRow(
                option = option,
                enabled = !option.isCurrent && !state.isChangingPlan,
                onSelect = { onIntent(MyPlanIntent.SelectPlan(option.planId)) },
            )
        }
        if (state.isChangingPlan) {
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                SaqzSpinner()
            }
        }
    }
}

@Composable
private fun MyPlanChangeOptionRow(
    option: MyPlanChangeOptionUi,
    enabled: Boolean,
    onSelect: () -> Unit,
) {
    val colors = SaqzTheme.colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (enabled) Modifier.clickable(onClickLabel = option.name, role = Role.Button, onClick = onSelect) else Modifier)
            .padding(vertical = SaqzTheme.metrics.subGrid),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(text = option.name, style = SaqzTheme.typography.label, color = colors.textPrimary)
            Text(text = option.priceLine.asString(), style = SaqzTheme.typography.caption, color = colors.textSecondary)
        }
        if (option.isCurrent) {
            SaqzStatusChip(text = stringResource(Res.string.myplan_change_current_tag), tone = SaqzChipTone.Brand)
        }
    }
}

@Composable
internal fun MyPlanReceiptsSheet(state: MyPlanState, onIntent: (MyPlanIntent) -> Unit) {
    SaqzBottomSheet(
        open = state.isReceiptsSheetOpen,
        onClose = { onIntent(MyPlanIntent.DismissReceipts) },
        modifier = Modifier.testTag(MyPlanTags.ReceiptsSheet),
        title = stringResource(Res.string.myplan_receipts_sheet_title),
    ) {
        if (state.receiptsError != null) {
            Text(
                text = state.receiptsError.asString(),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.errorForeground,
            )
            SaqzButton(
                label = stringResource(Res.string.myplan_retry),
                onClick = { onIntent(MyPlanIntent.RetryReceipts) },
                variant = SaqzButtonVariant.Secondary,
            )
        } else if (state.receipts.isEmpty()) {
            Text(
                text = stringResource(Res.string.myplan_receipts_empty),
                style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary,
            )
        } else {
            state.receipts.forEach { receipt ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = receipt.dateLabel, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
                    Text(text = receipt.valueLabel, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textSecondary)
                }
            }
            if (state.hasMoreReceipts) {
                SaqzButton(
                    label = stringResource(Res.string.myplan_receipts_load_more),
                    onClick = { onIntent(MyPlanIntent.LoadMoreReceipts) },
                    modifier = Modifier.testTag(MyPlanTags.LoadMoreReceipts),
                    variant = SaqzButtonVariant.Secondary,
                    fullWidth = true,
                    loading = state.isLoadingMoreReceipts,
                )
            }
        }
    }
}

@Composable
internal fun MyPlanCancelSheet(state: MyPlanState, onIntent: (MyPlanIntent) -> Unit) {
    SaqzBottomSheet(
        open = state.isCancelSheetOpen,
        onClose = { onIntent(MyPlanIntent.DismissCancel) },
        modifier = Modifier.testTag(MyPlanTags.CancelSheet),
        title = stringResource(Res.string.myplan_cancel_sheet_title),
        description = stringResource(Res.string.myplan_cancel_helper),
        splitFooter = {
            SaqzButton(
                label = stringResource(Res.string.myplan_cancel_keep),
                onClick = { onIntent(MyPlanIntent.DismissCancel) },
                variant = SaqzButtonVariant.Secondary,
                modifier = Modifier.weight(1f),
            )
            SaqzButton(
                label = stringResource(Res.string.myplan_cancel_confirm),
                onClick = { onIntent(MyPlanIntent.ConfirmCancel) },
                variant = SaqzButtonVariant.Danger,
                loading = state.isCanceling,
                modifier = Modifier.weight(1f),
            )
        },
    ) {
        state.cancelError?.let {
            Text(text = it.asString(), style = SaqzTheme.typography.support, color = SaqzTheme.colors.errorForeground)
        }
    }
}

@Composable
internal fun MyPlanPendingPaymentSheet(state: MyPlanState, onIntent: (MyPlanIntent) -> Unit) {
    val payment = state.pendingPayment
    val uriHandler = LocalUriHandler.current
    SaqzBottomSheet(
        open = payment != null,
        onClose = { onIntent(MyPlanIntent.DismissPendingPayment) },
        modifier = Modifier.testTag(MyPlanTags.PendingPaymentSheet),
        title = stringResource(Res.string.myplan_pending_payment_title),
        description = payment?.message?.asString(),
        footer = {
            SaqzButton(
                label = stringResource(Res.string.myplan_pending_payment_dismiss),
                onClick = { onIntent(MyPlanIntent.DismissPendingPayment) },
                fullWidth = true,
            )
        },
    ) {
        val pixCode = payment?.pixCopyPaste
        val invoiceUrl = payment?.invoiceUrl
        when {
            pixCode != null -> SaqzCard(tone = SaqzCardTone.Soft) {
                Text(text = pixCode, style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
            }
            // Achado do Codex no PR #93: quando a regeneração do Pix falha, o backend
            // devolve só `invoiceUrl` (fatura de cartão) — sem este fallback a folha
            // ficava vazia, só com "Entendi", sem jeito de completar o pagamento.
            invoiceUrl != null -> SaqzButton(
                label = stringResource(Res.string.myplan_pending_payment_invoice),
                onClick = { uriHandler.openUri(invoiceUrl) },
                variant = SaqzButtonVariant.Secondary,
                fullWidth = true,
            )
        }
    }
}

private fun MyPlanStatusTone.toChipTone(): SaqzChipTone = when (this) {
    MyPlanStatusTone.Active -> SaqzChipTone.Success
    MyPlanStatusTone.PastDue -> SaqzChipTone.Warning
    MyPlanStatusTone.Canceled -> SaqzChipTone.Error
}
