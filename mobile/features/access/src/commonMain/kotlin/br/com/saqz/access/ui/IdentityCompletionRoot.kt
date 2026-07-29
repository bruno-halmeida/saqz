package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.identitycompletion.IdentityCompletionViewModel
import org.koin.compose.viewmodel.koinViewModel

/**
 * A 1c não tem saída por callback: o voltar dela desiste da conta recém-autenticada, e
 * quem troca de tela nos dois casos — desistiu, concluiu — é o estado de sessão, que o
 * gate de rota do `SaqzNavHost` lê.
 */
@Composable
fun IdentityCompletionRoot(viewModel: IdentityCompletionViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    IdentityCompletionScreen(state = state, onIntent = viewModel::onIntent)
}
