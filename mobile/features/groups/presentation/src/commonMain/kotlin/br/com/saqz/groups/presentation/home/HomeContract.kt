package br.com.saqz.groups.presentation.home

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.presentation.GroupUiError

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val error: GroupUiError? = null,
    val displayName: String? = null,
    val home: HomeReadModel? = null,
    val member: HomeMemberUi? = null,
    val responding: Boolean = false,
    val responseFailed: Boolean = false,
    val toast: HomeToast? = null,
)

@Immutable
data class HomeMemberUi(
    val subtitle: String,
    val nextGame: HomeNextGameUi?,
    val lastCompletedGame: HomeLastCompletedGameUi?,
    val groups: List<HomeGroupUi>,
)

/**
 * Tipo de espera do membro no próximo jogo, derivado num único ponto do ViewModel:
 * `membershipType == Avulso && mensalistaPriority == true` ⇒ [AvulsoList]; caso
 * contrário ⇒ [Reserva]. O hero e o subtítulo do cabeçalho consomem o mesmo valor.
 */
enum class HomeWaitlistKind { Reserva, AvulsoList }

/**
 * Linha da lista de espera (6e): nome, posição na fila e flag do próprio usuário
 * (a linha dele recebe destaque ice e "Você" no lugar do nome). O match da própria
 * linha é por `waitlistPosition` — o backend não envia id de membro no roster.
 */
@Immutable
data class HomeWaitlistRowUi(
    val name: String,
    val position: Long,
    val isSelf: Boolean,
)

@Immutable
data class HomeNextGameUi(
    val groupId: String,
    val gameId: String,
    val groupName: String,
    val dateTime: String,
    val local: String,
    val deadline: String,
    val confirmedSummary: String,
    val confirmedCount: Int,
    val capacity: Int,
    val rosterNames: List<String>,
    val ownAttendance: AttendanceStatus?,
    val weekday: String,
    val time: String,
    val confirmationOpen: Boolean = true,
    val waitlistKind: HomeWaitlistKind? = null,
    val waitlistPosition: Long? = null,
    val confirmedRoster: List<String> = emptyList(),
    val waitlistedRoster: List<HomeWaitlistRowUi> = emptyList(),
    val mensalistaConfirmedCount: Int = 0,
    val deadlineBellLabel: String = "",
)

@Immutable
data class HomeLastCompletedGameUi(
    val day: String,
    val month: String,
    val title: String,
    val summary: String,
)

@Immutable
data class HomeGroupUi(
    val id: String,
    val name: String,
    val meta: String,
)

enum class HomeToast {
    Confirmed,
    Declined,
    Waitlisted,
}

sealed interface HomeIntent {
    data object Retry : HomeIntent
    data class Respond(val intent: br.com.saqz.groups.domain.attendance.AttendanceIntent) : HomeIntent
    data object DismissToast : HomeIntent
    data object OpenGroups : HomeIntent
    data class OpenGroup(val groupId: String) : HomeIntent
    data class OpenGame(val groupId: String, val gameId: String) : HomeIntent
}

sealed interface HomeEffect {
    data object OpenGroups : HomeEffect
    data class OpenGroup(val groupId: String) : HomeEffect
    data class OpenGame(val groupId: String, val gameId: String) : HomeEffect
}
