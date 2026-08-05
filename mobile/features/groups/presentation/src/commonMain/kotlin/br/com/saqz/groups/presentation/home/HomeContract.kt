package br.com.saqz.groups.presentation.home

import androidx.compose.runtime.Immutable
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.home.HomeReadModel
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi

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
    /**
     * VUL-202 — o que **o usuário deve**. Fora de [HomeMemberUi] porque o aviso do shell lê
     * daqui em qualquer aba, inclusive quando a Home nem chegou a ser montada.
     */
    val ownCharges: HomeOwnChargesUi? = null,
)

/**
 * O aviso permanente (banner) e a seção da Home saem do mesmo bloco: [bannerText] é o
 * agregado já formatado, [overdue] é o tom (warning só com alguma vencida) e [groups] são
 * as linhas. `null` no estado significa "nada em aberto" — é assim que os dois somem.
 */
@Immutable
data class HomeOwnChargesUi(
    val bannerText: String,
    val bannerContentDescription: String,
    val overdue: Boolean,
    val groups: List<HomeOwnChargeGroupUi>,
)

@Immutable
data class HomeOwnChargeGroupUi(
    val groupId: String,
    val groupName: String,
    /** "Mensalidade · Julho" ou "Jogo avulso" — a competência da mais antiga. */
    val competence: String,
    val amountLabel: String,
    /** "Vence em 05/08" × "Venceu em 05/08", pelo `overdue` que vem do servidor. */
    val dueLabel: String,
    val overdue: Boolean,
    /** Só quando há mais de uma cobrança no grupo; `null` cala a linha. */
    val countLabel: String?,
    val pix: PixUi?,
)

@Immutable
data class HomeMemberUi(
    val subtitle: String,
    val nextGame: HomeNextGameUi?,
    val lastCompletedGame: HomeLastCompletedGameUi?,
    val groups: List<HomeGroupUi>,
    val admin: HomeAdminReadModelUi? = null,
    val adminSubtitle: String? = null,
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
    val confirmedCountTotal: Int = 0,
    val deadlineBellLabel: String = "",
    val declinedCount: Int = 0,
    val pendingCount: Int = 0,
    val adminHeroDeadlineLabel: String = "",
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
    val isAdmin: Boolean = false,
)

enum class HomeToast {
    Confirmed,
    Declined,
    Waitlisted,
}

@Immutable
data class HomeMonthlyChargesUi(
    val count: Int,
    val formattedTotal: String,
    val month: String,
)

@Immutable
data class HomeGameToSettleUi(
    val gameId: String,
    val formattedDate: String,
    val diaristCount: Int,
    val formattedTotal: String,
)

@Immutable
data class HomeAdminGroupUi(
    val id: String,
    val name: String,
    val entryRequestCount: Int,
    val monthlyCharges: HomeMonthlyChargesUi?,
    val gameToSettle: HomeGameToSettleUi?,
)

@Immutable
data class HomeAdminReadModelUi(
    val groups: List<HomeAdminGroupUi>,
) {
    val totalPendingItems: Int
        get() = groups.sumOf { group ->
            (if (group.entryRequestCount > 0) 1 else 0) +
                (if (group.monthlyCharges != null) 1 else 0) +
                (if (group.gameToSettle != null) 1 else 0)
        }
}

sealed interface HomeIntent {
    data object Retry : HomeIntent

    /**
     * Recarga por baixo, sem esqueleto nem tela de erro: é a volta ao app (VUL-202). O que
     * mudou lá fora — o admin baixando a cobrança — só chega por aqui.
     */
    data object Refresh : HomeIntent
    data class Respond(val intent: br.com.saqz.groups.domain.attendance.AttendanceIntent) : HomeIntent
    data object DismissToast : HomeIntent
    data object OpenGroups : HomeIntent
    data class OpenGroup(val groupId: String) : HomeIntent
    data class OpenGame(val groupId: String, val gameId: String) : HomeIntent
    data class OpenMembers(val groupId: String) : HomeIntent
    data class OpenCashbox(val groupId: String) : HomeIntent
    data class OpenGameSettlement(val groupId: String, val gameId: String) : HomeIntent
    data class OpenGameEditor(val groupId: String) : HomeIntent
    data class OpenInvite(val groupId: String) : HomeIntent
    data class CopyPix(val groupId: String) : HomeIntent
}

sealed interface HomeEffect {
    data object OpenGroups : HomeEffect
    data class OpenGroup(val groupId: String) : HomeEffect
    data class OpenGame(val groupId: String, val gameId: String) : HomeEffect
    data class OpenMembers(val groupId: String) : HomeEffect
    data class OpenCashbox(val groupId: String) : HomeEffect
    data class OpenGameSettlement(val groupId: String, val gameId: String) : HomeEffect
    data class OpenGameEditor(val groupId: String) : HomeEffect
    data class OpenInvite(val groupId: String) : HomeEffect
    data class CopyPix(val key: String) : HomeEffect
}
