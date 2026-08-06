package br.com.saqz.access.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.access.presentation.newpassword.NewPasswordEffect
import br.com.saqz.access.presentation.newpassword.NewPasswordViewModel
import br.com.saqz.designsystem.ObserveAsEvents
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * As duas saídas do 1g sobem por callback (mobile/AGENTS.md §6): o 1h quando a senha
 * troca, e o 1e quando o ticket morre — quem sabe empilhar rota é o `SaqzNavHost`.
 *
 * O [token] entra pelo `parametersOf` porque é argumento de rota, não dependência do
 * grafo: não existe (nem deve existir) definição de `String` em Koin.
 */
@Composable
fun NewPasswordRoot(
    token: String,
    onBack: () -> Unit,
    onFinish: () -> Unit,
    onRestartCode: () -> Unit,
    // VUL-204: o parâmetro entra na chave do store. O escopo por destino já separa duas
    // entradas com tokens diferentes; a chave é a segunda tranca — vale também para quem
    // montar o Root fora de um `NavEntry`.
    viewModel: NewPasswordViewModel = koinViewModel(key = "new-password/$token") { parametersOf(token) },
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            NewPasswordEffect.Saved -> onFinish()
            NewPasswordEffect.CodeRestartNeeded -> onRestartCode()
        }
    }
    NewPasswordScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onBack = onBack,
    )
}
