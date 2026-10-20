package br.com.saqz.groups.presentation.positionpicker.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.ObserveAsEvents
import br.com.saqz.groups.presentation.positionpicker.PositionPickerEffect
import br.com.saqz.groups.presentation.positionpicker.PositionPickerOrigin
import br.com.saqz.groups.presentation.positionpicker.PositionPickerViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * A tela de escolher posição (DEB-107). [onDone] é a saída depois de confirmar: Início, grupo e jogo voltam (sem aviso);
 * o link volta ao próprio cartão de resultado; a notificação abre o jogo — quem decide é o efeito, pela origem.
 */
@Composable
fun PositionPickerRoot(
    groupId: String,
    gameId: String,
    origin: String,
    onBack: () -> Unit,
    onDone: (PositionPickerEffect) -> Unit,
    viewModel: PositionPickerViewModel = koinViewModel(
        key = "position-picker/$groupId/$gameId/$origin",
        parameters = { parametersOf(groupId, gameId, PositionPickerOrigin.from(origin)) },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ObserveAsEvents(viewModel.effects, onEvent = onDone)
    PositionPickerScreen(state = state, onBack = onBack, onIntent = viewModel::onIntent)
}
