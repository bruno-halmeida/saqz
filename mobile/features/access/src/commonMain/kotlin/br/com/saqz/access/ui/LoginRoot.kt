package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.login.LoginViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * Navegação entre features é callback (mobile/AGENTS.md §6): as duas saídas da 1a para o
 * resto do fluxo 1 — criar conta (1b) e esqueci a senha (1d) — sobem para quem conhece o
 * `NavDisplay`, em vez de a feature empilhar rota.
 */
@Composable
fun LoginRoot(
    onCreateAccount: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LoginScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onCreateAccount = onCreateAccount,
        onForgotPassword = onForgotPassword,
    )
}
