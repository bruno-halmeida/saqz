package br.com.saqz.receivables.presentation

import androidx.lifecycle.viewModelScope
import androidx.navigation3.runtime.NavKey
import br.com.saqz.core.common.mvi.MviViewModel
import br.com.saqz.domain.SaqzResult
import br.com.saqz.receivables.domain.PaymentError
import br.com.saqz.receivables.domain.ReceivablesSessionContext
import br.com.saqz.receivables.domain.SettleLinkGateway
import br.com.saqz.receivables.domain.SettleTarget
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.SaqzEmptyState
import br.com.saqz.designsystem.SaqzSpinner
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.receivables.resources.Res
import br.com.saqz.receivables.resources.charges_nothing_open
import br.com.saqz.receivables.resources.pay_title_many
import br.com.saqz.receivables.resources.settle_opening
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Botão da hora do acerto (`https://links.saqz.app/acerto/{codigo}`, FIN-013): resolve o código e troca esta tela
 * pelo pagamento daquele jogo ou mês. Quem chega deslogado passa pelo login antes, como no link de presença.
 */
@Serializable
data class SettleLinkRoute(val code: String) : NavKey

data class SettleLinkState(val loading: Boolean = true, val error: PaymentError? = null)

sealed interface SettleLinkIntent { data object Retry : SettleLinkIntent }

sealed interface SettleLinkEffect { data class Open(val target: SettleTarget) : SettleLinkEffect }

class SettleLinkViewModel(
    private val code: String,
    private val gateway: SettleLinkGateway,
    private val session: ReceivablesSessionContext,
) : MviViewModel<SettleLinkState, SettleLinkIntent, SettleLinkEffect>(SettleLinkState()) {
    private val sessionKey = session.currentKey()

    init { resolve() }

    override fun handleIntent(intent: SettleLinkIntent) {
        if (!state.value.loading) resolve()
    }

    private fun resolve() {
        if (sessionKey == null || sessionKey != session.currentKey()) {
            update { SettleLinkState(loading = false, error = PaymentError.SIGNED_OUT) }
            return
        }
        update { SettleLinkState(loading = true) }
        viewModelScope.launch {
            val result = gateway.resolve(code)
            if (sessionKey != session.currentKey()) return@launch
            when (result) {
                is SaqzResult.Success -> {
                    update { SettleLinkState(loading = false) }
                    emit(SettleLinkEffect.Open(result.value))
                }
                is SaqzResult.Failure -> update { SettleLinkState(loading = false, error = result.error) }
            }
        }
    }
}

/** Código do botão: o servidor gera (F4); o app só recusa o que não pode ser um código. */
fun isSettleCode(value: String?): Boolean = value != null && Regex("[A-Za-z0-9_-]{6,64}").matches(value)

@Composable
fun SettleLinkRoot(
    code: String,
    onBack: () -> Unit,
    onOpen: (SettleTarget) -> Unit,
    viewModel: SettleLinkViewModel = koinViewModel(key = "settle/$code",
        parameters = { parametersOf(code) }),
) {
    val state = viewModel.state.collectAsStateWithLifecycle().value
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) { is SettleLinkEffect.Open -> onOpen(effect.target) }
    }
    SettleLinkScreen(state, { viewModel.onIntent(SettleLinkIntent.Retry) }, onBack)
}

@Composable
fun SettleLinkScreen(state: SettleLinkState, onRetry: () -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier) = ReceivablesPage(
    stringResource(Res.string.pay_title_many),
    "settle-link", onBack, modifier,
) {
    val metrics = SaqzTheme.metrics
    val padded = Modifier.padding(metrics.horizontalPadding)
    when (val error = state.error) {
        null -> Column(padded) {
            SaqzSpinner()
            Text(stringResource(
                Res.string.settle_opening))
        }
        // Código que não resolve é como não dever nada ali: a pessoa não fica presa.
        PaymentError.UNAVAILABLE, PaymentError.INVALID -> SaqzEmptyState(
            title = stringResource(Res.string.charges_nothing_open),
            modifier = padded)
        else -> PaymentFailure(error, onRetry, padded)
    }
}
