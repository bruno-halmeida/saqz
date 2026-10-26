package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.resources.*
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

object PaymentReviewTags {
    const val Screen = "payment-review"
    const val Member = "payment-review-member"
    const val NotCharge = "payment-review-not-charge"
    const val Search = "payment-review-search"
    const val Confirm = "payment-review-confirm"
    fun debtor(id: String) = "payment-review-debtor-$id"
    fun charge(id: String) = "payment-review-charge-$id"
}

@Composable
fun PaymentReviewRoot(route: PaymentReviewRoute, onBack: () -> Unit, onResolve: () -> Unit,
    viewModel: PaymentReviewViewModel = koinViewModel(key = "review/${route.reviewId}",
        parameters = { parametersOf(PaymentReviewArgs(route.groupId, route.accountId, route.reviewId)) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { onResolve() }
    PaymentReviewScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun PaymentReviewScreen(state: PaymentReviewState, onIntent: (PaymentReviewIntent) -> Unit, onBack: () -> Unit,
    modifier: Modifier = Modifier) {
    if (state.assigning) {
        AssignScreen(state, onIntent, modifier)
        return
    }
    val metrics = SaqzTheme.metrics
    ReceivablesPage(stringResource(Res.string.review_title), PaymentReviewTags.Screen, onBack, modifier, footer = {
        if (!state.loading && state.amountLabel.isNotEmpty()) {
            SaqzButton(stringResource(Res.string.review_member), { onIntent(PaymentReviewIntent.StartAssign) }, fullWidth = true,
                enabled = !state.busy, modifier = Modifier.testTag(PaymentReviewTags.Member))
            SaqzButton(stringResource(Res.string.review_not_charge), { onIntent(PaymentReviewIntent.NotCharge) }, fullWidth = true,
                variant = SaqzButtonVariant.Secondary, loading = state.busy, modifier = Modifier.testTag(PaymentReviewTags.NotCharge))
            Text(stringResource(Res.string.review_not_charge_note, state.payerName), style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        }
    }) {
        when {
            state.loading -> Box(Modifier.fillMaxWidth().padding(metrics.sectionGap)) { SaqzSpinner(Modifier.align(Alignment.Center)) }
            state.amountLabel.isEmpty() && state.error != null -> Column(Modifier.padding(metrics.horizontalPadding)) {
                ReceiptErrorText(state.error)
                SaqzButton(stringResource(Res.string.payment_retry), { onIntent(PaymentReviewIntent.Retry) })
            }
            else -> {
                ReceivablesHero(kicker = stringResource(Res.string.review_kicker), amount = state.amountLabel,
                    support = stringResource(Res.string.review_from, state.payerName))
                ReceivablesRow(stringResource(Res.string.review_when), state.receivedLabel)
                SaqzDivider()
                state.error?.let { Box(Modifier.padding(metrics.horizontalPadding)) { ReceiptErrorText(it) } }
                ReceivablesNote(stringResource(Res.string.review_body))
            }
        }
    }
}

@Composable
private fun AssignScreen(state: PaymentReviewState, onIntent: (PaymentReviewIntent) -> Unit, modifier: Modifier = Modifier) {
    val metrics = SaqzTheme.metrics
    val member = state.member
    ReceivablesPage(stringResource(Res.string.assign_title, state.amountLabel), PaymentReviewTags.Screen,
        { onIntent(PaymentReviewIntent.BackToDetail) }, modifier, footer = {
            if (member != null) {
                Text(stringResource(Res.string.assign_marked, formatBrl(state.markedCents), state.amountLabel),
                    style = SaqzTheme.typography.support.copy(fontWeight = FontWeight.SemiBold), color = SaqzTheme.colors.textPrimary)
                if (state.markedCents in 1 until state.amountCents) {
                    Text(stringResource(Res.string.assign_leftover, formatBrl(state.amountCents - state.markedCents)),
                        style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
                }
                SaqzButton(stringResource(Res.string.assign_confirm, member.name.substringBefore(' ')),
                    { onIntent(PaymentReviewIntent.ConfirmAssign) }, fullWidth = true, enabled = state.canAssign, loading = state.busy,
                    modifier = Modifier.testTag(PaymentReviewTags.Confirm))
            }
        }) {
        Box(Modifier.padding(metrics.horizontalPadding)) {
            SaqzInput(value = state.query, onValueChange = { onIntent(PaymentReviewIntent.Search(it)) },
                label = stringResource(Res.string.assign_search), showLabel = false,
                placeholder = stringResource(Res.string.assign_search),
                    modifier = Modifier.fillMaxWidth().testTag(PaymentReviewTags.Search))
        }
        state.error?.let { Box(Modifier.padding(horizontal = metrics.horizontalPadding)) { ReceiptErrorText(it) } }
        state.visibleDebtors.forEach { debtor ->
            SaqzMemberRow(
                name = debtor.name,
                meta = debtor.support ?: stringResource(Res.string.assign_nothing_open),
                onClick = if (debtor.openCount > 0) ({ onIntent(PaymentReviewIntent.SelectMember(debtor.memberUserId)) }) else null,
                trailing = if (debtor.memberUserId == member?.memberUserId) ({
                    SaqzIcon(SaqzIcons.Check, tint = SaqzTheme.colors.primary)
                }) else null,
                modifier = Modifier.padding(horizontal = metrics.horizontalPadding).testTag(PaymentReviewTags.debtor(debtor.memberUserId)),
            )
            if (debtor.memberUserId == member?.memberUserId && state.charges.isNotEmpty()) {
                ReceivablesBand("${stringResource(Res.string.assign_covers)} · ${stringResource(Res.string.assign_covers_hint)}")
                state.charges.forEach { charge ->
                    ReceivablesRow(charge.label, charge.support, supportWarning = charge.overdue) {
                        Text(charge.amountLabel, style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                            color = SaqzTheme.colors.textPrimary)
                        SaqzSwitch(checked = charge.checked,
                            onCheckedChange = { onIntent(PaymentReviewIntent.ToggleCharge(charge.chargeId)) },
                            contentDescription = charge.label, modifier = Modifier.testTag(PaymentReviewTags.charge(charge.chargeId)))
                    }
                }
            }
            SaqzDivider()
        }
    }
}

private val previewReview = PaymentReviewState(loading = false, payerName = "João P. Silva", amountCents = 2000,
    amountLabel = "R$ 20,00", receivedLabel = "28/07, 23:04")

@Preview(name = "Pix para revisar", widthDp = 390)
@Composable
private fun PaymentReviewPreview() = SaqzTheme { PaymentReviewScreen(previewReview, {}, {}) }

@Preview(name = "Pix para revisar · de quem é", widthDp = 390)
@Composable
private fun PaymentAssignPreview() = SaqzTheme {
    val felipe = DebtorUi("felipe", "Felipe Nunes", "2 em aberto · R$ 40,00", 2)
    PaymentReviewScreen(previewReview.copy(assigning = true, member = felipe, debtors = listOf(felipe,
        DebtorUi("thiago", "Thiago Melo", "1 em aberto · R$ 100,00", 1), DebtorUi("carla", "Carla Mendes", null, 0)),
        charges = listOf(SuggestedChargeUi("c1", "Diária · 21/07", "Vencida", true, "R$ 20,00", 2000, true),
            SuggestedChargeUi("c2", "Diária · 28/07", "Vence em 28/07", false, "R$ 20,00", 2000, false))), {}, {})
}
