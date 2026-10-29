package br.com.saqz.groups.presentation.ui.gamedetail

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.designsystem.RefreshWhenVisible
import br.com.saqz.groups.presentation.gamedetail.GameDetailEffect
import br.com.saqz.groups.presentation.gamedetail.GameDetailIntent
import br.com.saqz.groups.presentation.gamedetail.GameDetailViewModel
import br.com.saqz.groups.domain.map.GroupMapPort
import br.com.saqz.groups.presentation.ui.details.venueMapUrl
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun GameDetailRoot(
    groupId: String,
    gameId: String,
    onBack: () -> Unit,
    onOpenEditor: () -> Unit,
    onOpenSettlement: () -> Unit = {},
    onCancel: () -> Unit = onBack,
    /** "Vou" em jogo com posições: a tela de escolher posição (DEB-107). */
    onPickPosition: () -> Unit = {},
    /** "Histórico de respostas" do ⋯ (JOGO-012), com o subtítulo da barra. */
    onOpenHistory: (String) -> Unit = {},
    /** PROM-010: o Pagar do avulso na espera (recebimento pelo app ativo), com o grupo. */
    onPay: (groupId: String, groupName: String) -> Unit = { _, _ -> },
    mapPort: GroupMapPort = koinInject(),
    viewModel: GameDetailViewModel = koinViewModel(
        key = "game-detail/$groupId/$gameId",
        parameters = { parametersOf(groupId, gameId) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboardManager.current
    // Volta à tela (aba, filha, primeiro plano): recarga por baixo; a ViewModel ignora com carga em voo.
    RefreshWhenVisible { viewModel.onIntent(GameDetailIntent.Refresh) }
    ObserveAsEvents(viewModel.effects) { effect ->
        when (effect) {
            GameDetailEffect.OpenEditor -> onOpenEditor()
            GameDetailEffect.OpenSettlement -> onOpenSettlement()
            GameDetailEffect.Cancelled -> onCancel()
            GameDetailEffect.PickPosition -> onPickPosition()
            is GameDetailEffect.OpenHistory -> onOpenHistory(effect.subtitle)
            // Copiar o Pix é área de transferência, não destino (como no grupo).
            is GameDetailEffect.CopyPix -> clipboard.setText(AnnotatedString(effect.key))
            is GameDetailEffect.OpenPayment -> onPay(effect.groupId, effect.groupName)
            // O mesmo caminho do "Ver no mapa" do grupo: falhar deixa o aviso colado na linha da quadra.
            is GameDetailEffect.OpenMap -> mapPort.open(venueMapUrl(effect.address)) { opened ->
                if (!opened) viewModel.onIntent(GameDetailIntent.MapOpenFailed)
            }
        }
    }
    GameDetailScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
