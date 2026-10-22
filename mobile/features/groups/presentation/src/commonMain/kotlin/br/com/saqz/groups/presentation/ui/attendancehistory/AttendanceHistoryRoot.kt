package br.com.saqz.groups.presentation.ui.attendancehistory

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import br.com.saqz.designsystem.RefreshWhenVisible
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryIntent
import br.com.saqz.groups.presentation.attendancehistory.AttendanceHistoryViewModel
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/** JOGO-012 · "Histórico de respostas" do jogo, aberto pelo ⋯ do detalhe (gestor). */
@Composable
fun AttendanceHistoryRoot(
    groupId: String,
    gameId: String,
    /** "Jogo de quinta, 22/10 · 20h00", vindo do detalhe. */
    subtitle: String,
    onBack: () -> Unit,
) {
    val viewModel: AttendanceHistoryViewModel =
        koinViewModel(key = "attendance-history/$groupId/$gameId", parameters = { parametersOf(groupId, gameId) })
    val state by viewModel.state.collectAsStateWithLifecycle()
    RefreshWhenVisible { viewModel.onIntent(AttendanceHistoryIntent.Refresh) }
    AttendanceHistoryScreen(state = state, subtitle = subtitle, onIntent = viewModel::onIntent, onBack = onBack)
}
