package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.details.GroupDetailsEffect
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A tela não navega: o efeito sobe para quem registrou o destino no `NavDisplay`
 * (AGENTS.md §6). [onEffect] é o callback único; quem o liga é o ticket de navegação.
 */
@Composable
fun GroupDetailsRoot(
    groupId: String,
    onBack: () -> Unit,
    onEffect: (GroupDetailsEffect) -> Unit,
    // VUL-204: o `groupId` entra na chave do store. Quem separa o grupo A do B é o escopo
    // por destino do `NavDisplay` (cada `GroupsRoute.Details` é uma entrada com store
    // próprio); a chave é a segunda tranca, e é ela que mantém o Root correto sozinho —
    // montado fora de um `NavEntry`, sem ela A e B cairiam na mesma instância.
    viewModel: GroupDetailsViewModel = koinViewModel(key = groupId, parameters = { parametersOf(groupId) }),
    refreshVersion: Int = 0,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    LaunchedEffect(refreshVersion) {
        if (refreshVersion > 0) viewModel.onIntent(GroupDetailsIntent.Retry)
    }
    // Copiar o Pix (VUL-203) é área de transferência, não destino: morre aqui em vez de
    // subir para o `NavDisplay` como um efeito de navegação que ninguém navega.
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            is GroupDetailsEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
            else -> onEffect(effect)
        }
    }
    GroupDetailsScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
