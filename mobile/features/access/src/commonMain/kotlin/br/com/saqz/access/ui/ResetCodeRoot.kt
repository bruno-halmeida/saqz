package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.resetcode.ResetCodeEffect
import br.com.saqz.access.presentation.resetcode.ResetCodeViewModel
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * Navegação entre features é callback (AGENTS.md §6): as três saídas da 1e — voltar, o
 * "Entrar ›" do rodapé e o código aceito, que abre a 1g com o ticket — sobem para quem
 * conhece o `NavDisplay`.
 */
@Composable
fun ResetCodeRoot(
    email: String,
    onBack: () -> Unit,
    onSignIn: () -> Unit,
    onOpenNewPassword: (email: String, token: String) -> Unit,
    viewModel: ResetCodeViewModel = koinViewModel(parameters = { parametersOf(email) }),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is ResetCodeEffect.OpenNewPassword -> onOpenNewPassword(effect.email, effect.token)
        }
    }
    ResetCodeScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onSignIn = onSignIn,
    )
}
