package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.forgotpassword.ForgotPasswordEffect
import br.com.saqz.access.presentation.forgotpassword.ForgotPasswordViewModel
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navegação entre features é callback (mobile/AGENTS.md §6): a 1d empurra o e-mail para
 * quem conhece o `NavDisplay`, que é quem monta o `ResetCode(email)` da 1e.
 *
 * O voltar e o "Entrar ›" são a mesma saída porque são o mesmo destino: a 1d só é
 * alcançada empilhando sobre o 1a, então desempilhar já devolve ao login.
 */
@Composable
fun ForgotPasswordRoot(
    onBack: () -> Unit,
    onOpenResetCode: (String) -> Unit,
    viewModel: ForgotPasswordViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is ForgotPasswordEffect.CodeRequested -> onOpenResetCode(effect.email)
        }
    }
    ForgotPasswordScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onSignIn = onBack,
    )
}
