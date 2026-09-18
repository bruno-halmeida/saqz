package br.com.saqz.receivables.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.tooling.preview.Preview
import org.koin.compose.viewmodel.koinViewModel

@Serializable data object ReceiptFinanceHomeRoute : NavKey
data class ReceiptFinanceHomeState(val loading: Boolean = false, val notices: List<ReceiptNotice> = emptyList(),
    val error: ReceiptError? = null)
class ReceiptFinanceHomeViewModel(private val gateway: ReceiptNoticesGateway, private val session: ReceivablesSessionContext) :
    MviViewModel<ReceiptFinanceHomeState, Unit, Unit>(ReceiptFinanceHomeState()) {
    private val key = session.currentKey()
    override fun handleIntent(intent: Unit) {
        if (key == null || key != session.currentKey()) { update { ReceiptFinanceHomeState(error = ReceiptError.SIGNED_OUT) }; return }
        if (state.value.loading) return
        update { it.copy(loading = true, error = null) }
        viewModelScope.launch {
            val result = gateway.active()
            if (key != session.currentKey()) { update { ReceiptFinanceHomeState(error = ReceiptError.SIGNED_OUT) }; return@launch }
            update { when (result) {
                is SaqzResult.Success -> ReceiptFinanceHomeState(notices = result.value)
                is SaqzResult.Failure -> ReceiptFinanceHomeState(error = result.error)
            } }
        }
    }
}
@Composable
fun ReceiptFinanceHomeRoot(onBack: () -> Unit, onWallet: () -> Unit, onRegistration: () -> Unit,
    viewModel: ReceiptFinanceHomeViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LifecycleResumeEffect(viewModel) { viewModel.onIntent(Unit); onPauseOrDispose { } }
    ReceiptFinanceHomeScreen(state, onBack, onWallet, onRegistration, { viewModel.onIntent(Unit) })
}
@Composable
fun ReceiptFinanceHomeScreen(state: ReceiptFinanceHomeState, onBack: () -> Unit, onWallet: () -> Unit,
    onRegistration: () -> Unit, onRefresh: () -> Unit, modifier: Modifier = Modifier,
) = PaymentPage(stringResource(Res.string.onboarding_title), "receipt-finance-home", onBack, modifier) {
    SaqzButton(stringResource(Res.string.wallet_title), onWallet, fullWidth = true,
        enabled = state.error != ReceiptError.SIGNED_OUT, modifier = Modifier.testTag("finance-home-wallet"))
    SaqzButton(stringResource(Res.string.finance_home_registration), onRegistration, fullWidth = true,
        enabled = state.error != ReceiptError.SIGNED_OUT, modifier = Modifier.testTag("finance-home-registration"),
        variant = SaqzButtonVariant.Secondary)
    Text(stringResource(Res.string.finance_home_maintenance))
    if (state.loading) SaqzSpinner()
    if (state.error != null) {
        Text(stringResource(Res.string.finance_home_notices_error))
        SaqzButton(stringResource(Res.string.finance_home_refresh), onRefresh,
            enabled = !state.loading && state.error != ReceiptError.SIGNED_OUT, fullWidth = true)
    }
    state.notices.forEach { notice -> SaqzCard(Modifier.testTag("finance-notice-${notice.id}")) {
        Column(verticalArrangement = Arrangement.spacedBy(SaqzTheme.metrics.subGrid)) {
            Text(notice.title, style = SaqzTheme.typography.body); Text(notice.message)
        }
    } }
}

@Preview
@Composable
private fun ReceiptFinanceHomePreview() {
    SaqzTheme {
        ReceiptFinanceHomeScreen(
            ReceiptFinanceHomeState(),
            onBack = {}, onWallet = {}, onRegistration = {}, onRefresh = {},
        )
    }
}
