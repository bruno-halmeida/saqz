package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.core.common.formatting.formatBrl
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.domain.FeePolicy
import br.com.saqz.receivables.domain.MemberAutopayStatus
import br.com.saqz.receivables.resources.*
import kotlinx.coroutines.delay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/*
 * Peças do recebimento que moram dentro das telas de grupo. A feature de grupos não enxerga esta (AGENTS.md §1): o
 * `:compose-app` passa estas funções como slots. Cada uma é um Root — resolve a ViewModel do grupo e não navega.
 */

object GroupReceivablesTags {
    const val Receiving = "group-receiving"
    const val ReceivingOffer = "group-receiving-offer"
    const val ChangeFees = "group-receiving-change-fees"
    const val Withdraw = "group-receiving-withdraw"
    const val TurnOff = "group-receiving-turn-off"
    const val FeePreview = "group-fee-preview"
    const val CashboxWallet = "cashbox-wallet"
    const val CashboxReview = "cashbox-review"
}

@Composable
fun groupReceivablesViewModel(groupId: String): GroupReceivablesViewModel =
    koinViewModel(key = "group-receivables/$groupId", parameters = { parametersOf(groupId) })

/** Se o grupo recebe pelo app, para a tela de grupo esconder a chave pessoal e trocar textos. */
@Composable
fun rememberGroupReceivingActive(groupId: String): Boolean {
    val state by groupReceivablesViewModel(groupId).state.collectAsStateWithLifecycle()
    return state.active
}

/** Gestor-Pix: "Como o grupo recebe" no Editar grupo. Sem conta financeira, só a entrada para ativar (se liberada). */
@Composable
fun GroupReceivingSection(
    groupId: String,
    onOpenFees: (accountId: String) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenActivation: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val viewModel = groupReceivablesViewModel(groupId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshWhenVisible { viewModel.onIntent(GroupReceivablesIntent.Refresh) }
    GroupReceivingContent(state, onOpenFees, onOpenWallet, onOpenActivation, modifier)
}

@Composable
fun GroupReceivingContent(
    state: GroupReceivablesState,
    onOpenFees: (accountId: String) -> Unit,
    onOpenWallet: () -> Unit,
    onOpenActivation: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val metrics = SaqzTheme.metrics
    if (!state.active) {
        if (onOpenActivation == null || state.loading) return
        SaqzCard(modifier.fillMaxWidth().testTag(GroupReceivablesTags.ReceivingOffer)) {
            Text(stringResource(Res.string.receiving_title), style = SaqzTheme.typography.subtitle, color = SaqzTheme.colors.textPrimary)
            Text(stringResource(Res.string.receiving_offer_body), style = SaqzTheme.typography.support,
                color = SaqzTheme.colors.textSecondary)
            SaqzButton(stringResource(Res.string.receiving_offer_action), onOpenActivation, fullWidth = true)
        }
        return
    }
    Column(modifier.fillMaxWidth().testTag(GroupReceivablesTags.Receiving), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
        SaqzSectionHeader(title = stringResource(Res.string.receiving_title))
        SaqzCard {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.receiving_app), Modifier.weight(1f),
                    style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = SaqzTheme.colors.textPrimary)
                SaqzStatusChip(stringResource(Res.string.receiving_active), tone = SaqzChipTone.Success, dot = true)
            }
            Text(stringResource(Res.string.receiving_body), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
            SaqzDivider()
            FeesRow(state.feePolicy, state.accountId?.let { id -> { onOpenFees(id) } })
            SaqzDivider()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(Res.string.receiving_wallet), style = SaqzTheme.typography.body,
                        color = SaqzTheme.colors.textPrimary)
                    state.walletLabel?.let {
                        Text(stringResource(Res.string.receiving_wallet_value, it), style = SaqzTheme.typography.caption,
                            color = SaqzTheme.colors.textSecondary)
                    }
                }
                SaqzButton(stringResource(Res.string.receiving_withdraw), onOpenWallet, size = SaqzButtonSize.Sm,
                    variant = SaqzButtonVariant.Secondary, modifier = Modifier.testTag(GroupReceivablesTags.Withdraw))
            }
        }
        onOpenActivation?.let {
            SaqzButton(stringResource(Res.string.receiving_turn_off), it, fullWidth = true, variant = SaqzButtonVariant.Ghost,
                contentColor = SaqzTheme.colors.errorForeground, modifier = Modifier.testTag(GroupReceivablesTags.TurnOff))
        }
    }
}

/** "Taxas de serviço e pagamento · Você absorve · Mudar". */
@Composable
internal fun FeesRow(policy: FeePolicy, onChange: (() -> Unit)?) = Row(Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically) {
    Column(Modifier.weight(1f)) {
        Text(stringResource(Res.string.fees_label), style = SaqzTheme.typography.body, color = SaqzTheme.colors.textPrimary)
        Text(stringResource(if (policy == FeePolicy.PASS) Res.string.fees_you_pass else Res.string.fees_you_absorb),
            style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
    }
    onChange?.let {
        SaqzButton(stringResource(Res.string.fees_change), it, size = SaqzButtonSize.Sm, variant = SaqzButtonVariant.Secondary,
            modifier = Modifier.testTag(GroupReceivablesTags.ChangeFees))
    }
}

/**
 * Gestor-Valores: a prévia "Atleta paga · você recebe" logo abaixo do valor, ao vivo. A consulta espera o dedo parar
 * ([QUOTE_DEBOUNCE_MILLIS]); enquanto o servidor não responde, mostra o valor base.
 */
@Composable
fun FeePreviewLine(groupId: String, baseCents: Long?, modifier: Modifier = Modifier) {
    val viewModel = groupReceivablesViewModel(groupId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    if (!state.active || baseCents == null || baseCents <= 0) return
    LaunchedEffect(baseCents) {
        delay(QUOTE_DEBOUNCE_MILLIS)
        viewModel.onIntent(GroupReceivablesIntent.Quote(baseCents))
    }
    Text(feePreviewText(state.feePolicy, baseCents, state.quotes[baseCents]), modifier.testTag(GroupReceivablesTags.FeePreview),
        style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
}

/** A linha das taxas e o aviso de reajuste que vão no cartão de valores do Editar grupo. */
@Composable
fun GroupValuesReceivables(groupId: String, onOpenFees: (accountId: String) -> Unit, modifier: Modifier = Modifier) {
    val state by groupReceivablesViewModel(groupId).state.collectAsStateWithLifecycle()
    if (!state.active) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        FeesRow(state.feePolicy, state.accountId?.let { id -> { onOpenFees(id) } })
        Text(stringResource(Res.string.values_price_change_note), style = SaqzTheme.typography.caption,
            color = SaqzTheme.colors.textSecondary)
    }
}

/** Gestor-Caixa: carteira com "Sacar" e o cartão de revisão, no topo do caixa. */
@Composable
fun CashboxReceivablesCard(
    groupId: String,
    onOpenWallet: () -> Unit,
    onOpenReview: (accountId: String, reviewId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel = groupReceivablesViewModel(groupId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshWhenVisible { viewModel.onIntent(GroupReceivablesIntent.Refresh) }
    CashboxReceivablesContent(state, onOpenWallet, onOpenReview, modifier)
}

@Composable
fun CashboxReceivablesContent(
    state: GroupReceivablesState,
    onOpenWallet: () -> Unit,
    onOpenReview: (accountId: String, reviewId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val account = state.accountId ?: return
    if (!state.active && state.reviews.isEmpty() && state.walletLabel == null) return
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.grid)) {
        state.walletLabel?.let { wallet ->
            SaqzCard(Modifier.testTag(GroupReceivablesTags.CashboxWallet)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.cashbox_wallet), style = SaqzTheme.typography.caption,
                            color = SaqzTheme.colors.textSecondary)
                        Text(stringResource(Res.string.receiving_wallet_value, wallet),
                            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold), color = SaqzTheme.colors.textPrimary)
                    }
                    SaqzButton(stringResource(Res.string.receiving_withdraw), onOpenWallet, size = SaqzButtonSize.Sm,
                        variant = SaqzButtonVariant.Secondary)
                }
            }
        }
        state.reviews.firstOrNull()?.let { review ->
            SaqzCard(Modifier.testTag(GroupReceivablesTags.CashboxReview), tone = SaqzCardTone.Soft) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(Res.string.cashbox_review_count, state.reviews.size),
                            style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
                                color = SaqzTheme.colors.warningForeground)
                        Text(stringResource(Res.string.cashbox_review_detail, formatBrl(review.amountCents), review.payerName),
                            style = SaqzTheme.typography.caption, color = SaqzTheme.colors.textSecondary)
                    }
                    SaqzButton(stringResource(Res.string.cashbox_review_action), { onOpenReview(account, review.id) },
                        size = SaqzButtonSize.Sm)
                }
            }
        }
    }
}

/** Quem está no automático, por membro (Gestor-Membros). `null` enquanto a rota da F3 não existe: a linha some. */
@Composable
fun rememberMemberAutopay(groupId: String): Map<String, MemberAutopayStatus>? {
    val viewModel = groupReceivablesViewModel(groupId)
    val state by viewModel.state.collectAsStateWithLifecycle()
    return state.autopay
}

private const val QUOTE_DEBOUNCE_MILLIS = 400L

@Preview(name = "Editar grupo · como o grupo recebe", widthDp = 390)
@Composable
private fun GroupReceivingPreview() = SaqzTheme {
    GroupReceivingContent(GroupReceivablesState(loading = false, accountId = "acc", active = true, walletLabel = "R$ 860,00"),
        {}, {}, {})
}

@Preview(name = "Caixa · carteira e revisão", widthDp = 390)
@Composable
private fun CashboxReceivablesPreview() = SaqzTheme {
    CashboxReceivablesContent(GroupReceivablesState(loading = false, accountId = "acc", active = true, walletLabel = "R$ 860,00",
        reviews = listOf(br.com.saqz.receivables.domain.PaymentReview("r1", "João P. Silva", 2000, "2026-07-28T23:04:00Z"))),
        {}, { _, _ -> })
}
