package br.com.saqz.groups.presentation.attendancehistory

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.photo.MemberPhoto

/** "Histórico de respostas" do jogo (JOGO-012): do mais recente para trás, separado por dia, só para o gestor. */
@Immutable
internal data class AttendanceHistoryState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val rows: List<AttendanceHistoryRowUi> = emptyList(),
    val canLoadMore: Boolean = false,
    val isLoadingMore: Boolean = false,
    val loadMoreFailed: Boolean = false,
    /** Recarga por cima (voltar à tela ou puxar para atualizar): a lista na tela fica. */
    val isRefreshing: Boolean = false,
    /** O puxão (GER-002) não conseguiu atualizar: a lista fica e o aviso aparece. */
    val refreshFailed: Boolean = false,
)

internal enum class AttendanceHistoryTone { Going, Waiting, NotGoing }

@Immutable
internal data class AttendanceHistoryRowUi(
    val id: String,
    /** "HOJE", "ONTEM", "TER, 20 DE OUTUBRO": as linhas vêm em ordem e o dia muda onde o cabeçalho muda. */
    val dayHeader: String,
    /** Nome no avatar (iniciais); convidado mostra o ícone. */
    val avatarName: String,
    val photo: MemberPhoto?,
    val guest: Boolean,
    /** A pessoa está, agora, no jogo: iniciais em azul. */
    val currentlyGoing: Boolean,
    /** "Carla Dias desistiu" */
    val title: String,
    /** "19h31 · estava confirmado(a)" */
    val meta: String,
    /** "Motivo: "pediu para sair"": só do organizador. */
    val reason: String?,
    val statusLabel: String,
    val tone: AttendanceHistoryTone,
)

internal sealed interface AttendanceHistoryIntent {
    data object Retry : AttendanceHistoryIntent

    /** Volta à tela: recarrega sem tirar a lista. */
    data object Refresh : AttendanceHistoryIntent
    data object LoadMore : AttendanceHistoryIntent

    /** Puxar para atualizar (GER-002). */
    data object PullRefresh : AttendanceHistoryIntent
    data object DismissRefreshFailed : AttendanceHistoryIntent
}
