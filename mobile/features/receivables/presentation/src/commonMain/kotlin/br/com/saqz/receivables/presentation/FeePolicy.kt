package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.designsystem.*
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.*
import br.com.saqz.receivables.resources.*
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

/** Gestor-Taxas: absorver ou repassar a taxa (FIN-010). Vale para as cobranças novas. */
@Serializable
data class FeePolicyRoute(val groupId: String, val accountId: String) : NavKey

data class FeePolicyArgs(val groupId: String, val accountId: String)

data class FeePolicyState(
    val loading: Boolean = true,
    val current: FeePolicy? = null,
    val selected: FeePolicy = FeePolicy.ABSORB,
    val pixFee: String? = null,
    val cardFee: String? = null,
    val saving: Boolean = false,
    val error: ReceiptError? = null,
) {
    val canSave get() = !loading && !saving && current != null && selected != current
}

sealed interface FeePolicyIntent {
    data class Select(val policy: FeePolicy) : FeePolicyIntent
    data object Save : FeePolicyIntent
    data object Retry : FeePolicyIntent
}

sealed interface FeePolicyEffect { data object Saved : FeePolicyEffect }

@OptIn(ExperimentalUuidApi::class)
class FeePolicyViewModel(
    private val args: FeePolicyArgs,
    private val receivables: GroupReceivablesGateway,
    private val payments: GroupPaymentsGateway,
    private val session: ReceivablesSessionContext,
) : MviViewModel<FeePolicyState, FeePolicyIntent, FeePolicyEffect>(FeePolicyState()) {
    private val sessionKey = session.currentKey()

    init { load() }

    override fun handleIntent(intent: FeePolicyIntent) {
        if (sessionKey == null || sessionKey != session.currentKey()) return
        when (intent) {
            is FeePolicyIntent.Select -> if (!state.value.saving) update { it.copy(selected = intent.policy) }
            FeePolicyIntent.Save -> if (state.value.canSave) save()
            FeePolicyIntent.Retry -> load()
        }
    }

    private fun load() {
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val status = receivables.status(args.groupId, args.accountId)
            // Os rótulos das taxas vêm da prévia; sem ela, a tela segue sem os números.
            val quote = payments.quote(args.groupId, args.accountId, LABEL_BASE_CENTS)
            if (sessionKey != session.currentKey()) return@launch
            when (status) {
                is SaqzResult.Failure -> update { it.copy(loading = false, error = status.error) }
                is SaqzResult.Success -> update {
                    val labels = (quote as? SaqzResult.Success)?.value
                    it.copy(loading = false, current = status.value.state.feePolicy, selected = status.value.state.feePolicy,
                        pixFee = labels?.pixFeeLabel, cardFee = labels?.cardFeeLabel)
                }
            }
        }
    }

    private fun save() {
        val policy = state.value.selected
        update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            val result = payments.setFeePolicy(args.groupId, Uuid.random().toString(), args.accountId, policy)
            if (sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Failure -> update { it.copy(saving = false, error = result.error) }
                is SaqzResult.Success -> {
                    update { it.copy(saving = false, current = result.value.state.feePolicy) }
                    emit(FeePolicyEffect.Saved)
                }
            }
        }
    }

    private companion object { const val LABEL_BASE_CENTS = 2_000L }
}

object FeePolicyTags {
    const val Screen = "fee-policy"
    const val Save = "fee-policy-save"
    fun option(policy: FeePolicy) = "fee-policy-${policy.name}"
}

@Composable
fun FeePolicyRoot(route: FeePolicyRoute, onBack: () -> Unit, onSave: () -> Unit,
    viewModel: FeePolicyViewModel = koinViewModel(key = "fees/${route.groupId}",
        parameters = { parametersOf(FeePolicyArgs(route.groupId, route.accountId)) })) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { onSave() }
    FeePolicyScreen(state, viewModel::onIntent, onBack)
}

@Composable
fun FeePolicyScreen(state: FeePolicyState, onIntent: (FeePolicyIntent) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) =
    ReceivablesPage(stringResource(Res.string.fee_policy_title), FeePolicyTags.Screen, onBack, modifier, footer = {
        SaqzButton(stringResource(Res.string.fee_policy_save), { onIntent(FeePolicyIntent.Save) }, fullWidth = true,
            enabled = state.canSave, loading = state.saving, modifier = Modifier.testTag(FeePolicyTags.Save))
    }) {
        val metrics = SaqzTheme.metrics
        Column(Modifier.fillMaxWidth().padding(metrics.horizontalPadding), verticalArrangement = Arrangement.spacedBy(metrics.blockGap)) {
            if (state.loading) { SaqzSpinner(Modifier.align(Alignment.CenterHorizontally)); return@Column }
            state.error?.let { ReceiptErrorText(it) }
            Text(stringResource(Res.string.fee_policy_question), style = SaqzTheme.typography.headline,
                color = SaqzTheme.colors.textPrimary)
            Text(stringResource(Res.string.fee_policy_intro), style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
            val options = listOf(FeePolicy.ABSORB, FeePolicy.PASS)
            SaqzSegmented(
                options = listOf(stringResource(Res.string.fee_policy_absorb), stringResource(Res.string.fee_policy_pass)),
                selected = options.indexOf(state.selected),
                onSelect = { onIntent(FeePolicyIntent.Select(options[it])) },
                optionTags = options.map(FeePolicyTags::option),
            )
            SaqzCard(tone = SaqzCardTone.Soft) {
                val pass = state.selected == FeePolicy.PASS
                Text(stringResource(if (pass) Res.string.fee_policy_pass_body else Res.string.fee_policy_absorb_body),
                    style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary)
                Text(stringResource(if (state.selected == FeePolicy.PASS) Res.string.fee_policy_pass_example
                    else Res.string.fee_policy_absorb_example), style = SaqzTheme.typography.caption.copy(fontWeight = FontWeight.SemiBold),
                    color = SaqzTheme.colors.textSecondary)
            }
            FeeTable(state.pixFee, state.cardFee)
            Text(stringResource(Res.string.fee_policy_terms_note), style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary)
            Text(stringResource(Res.string.fee_policy_new_only), style = SaqzTheme.typography.caption,
                color = SaqzTheme.colors.textSecondary)
        }
    }

/** "Taxas de serviço e pagamento" com Pix e Cartão; os rótulos vêm do servidor. */
@Composable
internal fun FeeTable(pixFee: String?, cardFee: String?) {
    if (pixFee == null && cardFee == null) return
    SaqzCard {
        Text(stringResource(Res.string.fees_label), style = SaqzTheme.typography.body.copy(fontWeight = FontWeight.SemiBold),
            color = SaqzTheme.colors.textPrimary)
        pixFee?.let { FeeLine(stringResource(Res.string.fees_pix), it) }
        cardFee?.let { FeeLine(stringResource(Res.string.fees_card), it) }
    }
}

@Composable
private fun FeeLine(label: String, value: String) = Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
    Text(label, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textSecondary)
    Text(value, style = SaqzTheme.typography.support, color = SaqzTheme.colors.textPrimary)
}

@Preview(name = "Taxas do grupo", widthDp = 390)
@Composable
private fun FeePolicyPreview() = SaqzTheme {
    FeePolicyScreen(FeePolicyState(loading = false, current = FeePolicy.ABSORB, selected = FeePolicy.PASS,
        pixFee = "R$ 0,99", cardFee = "4,99% + R$ 0,49"), {}, {})
}
