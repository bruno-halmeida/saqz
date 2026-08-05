package br.com.saqz.groups.application.home

import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import java.time.Clock
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.UUID

data class HomeMemberGroup(
    val id: UUID,
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
    val groupId: UUID,
    val groupName: String,
    val gameId: UUID,
    val local: String,
    val zoneId: String,
    val startsAt: Instant,
    val confirmationDeadline: Instant,
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
    val groupId: UUID,
    val groupName: String,
    val gameId: UUID,
    val zoneId: String,
    val startsAt: Instant,
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
    val billingMonth: YearMonth,
)

data class HomeGameToSettle(
    val gameId: UUID,
    val startsAt: Instant,
    val zoneId: String,
    val pendingCount: Int,
    val totalCents: Long,
)

data class HomeAdminGroup(
    val id: UUID,
    val name: String,
    val entryRequestCount: Int,
    val monthlyCharges: HomeMonthlyCharges,
    val gameToSettle: HomeGameToSettle?,
)

data class HomeAdminReadModel(
    val groups: List<HomeAdminGroup>,
)

/** Competência da cobrança pendente mais antiga do usuário no grupo. */
sealed interface HomeOwnChargeOldest {
    val dueDate: LocalDate

    data class Monthly(val month: YearMonth, override val dueDate: LocalDate) : HomeOwnChargeOldest

    data class Game(
        val gameId: UUID,
        val startsAt: Instant,
        val zoneId: String,
        override val dueDate: LocalDate,
    ) : HomeOwnChargeOldest
}

data class HomeOwnChargeGroup(
    val groupId: UUID,
    val groupName: String,
    val count: Int,
    val totalCents: Long,
    val nextDueDate: LocalDate,
    val overdue: Boolean,
    val pixKey: String?,
    val pixLabel: String?,
    val oldest: HomeOwnChargeOldest,
)

data class HomeOwnChargesReadModel(
    val groupCount: Int,
    val totalCents: Long,
    val groups: List<HomeOwnChargeGroup>,
)

data class HomeReadModel(
    val member: HomeMemberReadModel,
    val admin: HomeAdminReadModel?,
    val ownCharges: HomeOwnChargesReadModel? = null,
)

interface HomeRepository {
    /** [today] é a data no fuso de cobrança: dele saem a competência corrente e o "vencida". */
    fun find(actorId: UUID, now: Instant, today: LocalDate): HomeReadModel
}

class HomeQuery(
    private val repository: HomeRepository,
    private val clock: Clock,
    private val zoneId: ZoneId,
) {
    fun execute(actorId: UUID): HomeReadModel {
        val now = Instant.now(clock)
        return repository.find(actorId, now, now.atZone(zoneId).toLocalDate())
    }
}
