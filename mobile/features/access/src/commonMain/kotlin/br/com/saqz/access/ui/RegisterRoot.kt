package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.register.RegisterEffect
import br.com.saqz.access.presentation.register.RegisterViewModel
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel

/**
 * As duas saídas da 1b para a 1a chegam aqui pelo mesmo `onOpenLogin` porque, do lado de
 * quem navega, elas são a mesma coisa — a diferença (levar ou não o e-mail digitado) já
 * aconteceu antes, dentro da ViewModel, que escreve no formulário compartilhado da 1a.
 *
 * O sucesso do cadastro **não** sai por aqui: ele vira sessão, e quem reage a sessão é o
 * portão do `SaqzNavHost`.
 */
@Composable
fun RegisterRoot(
    onBack: () -> Unit,
    onOpenLogin: () -> Unit,
    viewModel: RegisterViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            RegisterEffect.OpenLogin -> onOpenLogin()
        }
    }
    RegisterScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
        onSignIn = onOpenLogin,
    )
}
