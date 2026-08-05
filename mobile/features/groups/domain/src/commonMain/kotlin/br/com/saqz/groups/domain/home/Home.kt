package br.com.saqz.groups.domain.home

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzError
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.GroupRole

data class HomeMemberGroup(
    val id: GroupId,
    val name: String,
    val role: GroupRole,
    val memberCount: Int,
    val gamesPlayed: Int,
)

data class HomeRosterMember(
    val displayName: String,
    val waitlistPosition: Long?,
)

data class HomeRosterPreview(
    val confirmed: List<HomeRosterMember>,
    val waitlisted: List<HomeRosterMember>,
)

data class HomeOwnAttendance(
    val status: AttendanceStatus,
    val waitlistPosition: Long?,
)

data class HomeNextGame(
    val groupId: GroupId,
    val groupName: String,
    val gameId: String,
    val local: String,
    val zoneId: String,
    val startsAt: String,
    val confirmationDeadline: String,
    val capacity: Int,
    val confirmedCount: Int,
    val declinedCount: Int,
    val pendingCount: Int,
    val waitlistCount: Int,
    val ownAttendance: HomeOwnAttendance?,
    val membershipType: AthleteMembershipType,
    val mensalistaPriority: Boolean,
    val rosterPreview: HomeRosterPreview,
)

data class HomeLastCompletedGame(
    val groupId: GroupId,
    val groupName: String,
    val gameId: String,
    val zoneId: String,
    val startsAt: String,
    val confirmedCount: Int,
    val ownPlayed: Boolean,
)

data class HomeMemberReadModel(
    val nextGame: HomeNextGame?,
    val lastCompletedGame: HomeLastCompletedGame?,
    val groups: List<HomeMemberGroup>,
)

data class HomeMonthlyCharges(
    val count: Int,
    val totalCents: Long,
    val billingMonth: String,
)

data class HomeGameToSettle(
    val gameId: String,
    val startsAt: String,
    val zoneId: String,
    val pendingCount: Int,
    val totalCents: Long,
)

data class HomeAdminGroup(
    val id: GroupId,
    val name: String,
    val entryRequestCount: Int,
    val monthlyCharges: HomeMonthlyCharges,
    val gameToSettle: HomeGameToSettle?,
)

data class HomeAdminReadModel(
    val groups: List<HomeAdminGroup>,
)

/**
 * Competência da cobrança pendente mais antiga do usuário no grupo (VUL-201). É só o
 * rótulo: o mês para a mensalidade, nada para o jogo avulso — o vencimento de quem paga
 * mora em [HomeOwnChargeGroup.nextDueDate], e o resto do bloco do jogo (id, início, fuso)
 * não é desenhado em lugar nenhum desta jornada.
 */
sealed interface HomeOwnChargeOldest {
    /** [month] é a competência crua do backend, `"YYYY-MM"`. */
    data class Monthly(val month: String) : HomeOwnChargeOldest

    data object Game : HomeOwnChargeOldest
}

/**
 * O que **o usuário deve** num grupo. [overdue] vem calculado no servidor, no fuso de
 * cobrança do contrato — o cliente não recalcula, e por isso [nextDueDate] pode ficar como
 * a data civil crua (`"YYYY-MM-DD"`) que só se formata.
 */
data class HomeOwnChargeGroup(
    val groupId: GroupId,
    val groupName: String,
    val count: Int,
    val totalCents: Long,
    val nextDueDate: String,
    val overdue: Boolean,
    val pixKey: String?,
    val pixLabel: String?,
    val oldest: HomeOwnChargeOldest,
)

/** Os agregados no topo existem para o aviso do shell: um texto, sem varrer os grupos. */
data class HomeOwnCharges(
    val groupCount: Int,
    val totalCents: Long,
    val groups: List<HomeOwnChargeGroup>,
)

data class HomeReadModel(
    val member: HomeMemberReadModel,
    val admin: HomeAdminReadModel?,
    /** `null` quando não há pendência nenhuma — é o que apaga aviso e seção. */
    val ownCharges: HomeOwnCharges? = null,
)

sealed interface HomeError : SaqzError {
    data class Data(val error: DataError) : HomeError
}

interface HomeGateway {
    suspend fun read(): SaqzResult<HomeReadModel, HomeError>
}
