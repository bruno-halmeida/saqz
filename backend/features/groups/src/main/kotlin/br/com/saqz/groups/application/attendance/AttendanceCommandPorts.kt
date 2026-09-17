package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.*
import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.group.PromotionMode
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

data class AttendanceAggregate(
    val groupId: UUID,
    val gameId: UUID,
    val memberId: UUID,
    val actorId: UUID,
    val actorRole: GroupRole?,
    val gameStatus: GameStatus,
    val confirmationDeadline: Instant,
    val capacity: Int,
    val confirmedCount: Int,
    val current: AttendanceRecord?,
    val gameFeeCents: Long?,
    val gameDate: LocalDate,
    val membershipType: AthleteMembershipType,
    val mensalistaPriority: Boolean = true,
    val promotionMode: PromotionMode = PromotionMode.FIFO,
    val guestSeq: Int = 0,
    val guestName: String? = null,
)

data class AttendanceRecord(
    val gameId: UUID,
    val groupId: UUID,
    val memberId: UUID,
    val status: AttendanceStatus,
    val waitlistSequence: Long?,
    val respondedAt: Instant,
    val updatedAt: Instant,
    val version: Long,
    val guestSeq: Int = 0,
)

data class AttendanceEvent(
    val id: UUID,
    val gameId: UUID,
    val groupId: UUID,
    val memberId: UUID,
    val actorId: UUID,
    val source: AttendanceSource,
    val oldStatus: AttendanceStatus?,
    val newStatus: AttendanceStatus,
    val reason: String?,
    val occurredAt: Instant,
    val requestId: UUID? = null,
    val guestSeq: Int = 0,
)

data class AttendancePromotionReplay(
    val attendance: AttendanceRecord,
    val event: AttendanceEvent,
)

data class AttendanceResponseReplay(
    val attendance: AttendanceRecord,
    val event: AttendanceEvent,
)

interface AttendanceCommandRepository {
    fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int = 0): AttendanceAggregate?
    fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate?
    fun nextWaitlistSequence(groupId: UUID, gameId: UUID): Long
    fun earliestWaitlisted(groupId: UUID, gameId: UUID): AttendanceRecord?
    fun save(record: AttendanceRecord)
    fun append(event: AttendanceEvent)
    fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int): Boolean
    fun findPromotionReplay(groupId: UUID, gameId: UUID, actorId: UUID, requestId: UUID): AttendancePromotionReplay? = null
    fun findResponseReplay(groupId: UUID, gameId: UUID, actorId: UUID, requestId: UUID): AttendanceResponseReplay? = null

    /** Próximo número de convidado do anfitrião neste jogo (linhas nunca são apagadas → max+1). */
    fun nextGuestSeq(gameId: UUID, hostId: UUID): Int = 1

    /** Convidados do anfitrião ainda no jogo (CONFIRMED ou WAITLISTED), travados para escrita. */
    fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID): List<AttendanceRecord> = emptyList()

    /** Como [save], mas gravando o nome digitado do convidado. */
    fun saveGuest(record: AttendanceRecord, guestName: String) = save(record)
}

fun interface AttendanceChargePort {
    fun confirmed(aggregate: AttendanceAggregate, actorId: UUID)
    fun promoted(aggregate: AttendanceAggregate, actorId: UUID) = confirmed(aggregate, actorId)
    fun guestRemoved(aggregate: AttendanceAggregate, actorId: UUID) {}
}

data class CapacityAggregate(
    val groupId: UUID,
    val gameId: UUID,
    val actorId: UUID,
    val actorRole: GroupRole?,
    val gameStatus: GameStatus,
    val confirmationDeadline: Instant,
    val capacity: Int,
    val confirmedCount: Int,
    val version: Long,
    val gameFeeCents: Long?,
    val gameDate: LocalDate,
    val mensalistaPriority: Boolean = true,
    val promotionMode: PromotionMode = PromotionMode.FIFO,
)

data class AttendanceDetail(
    val own: AttendanceRecord?,
    val confirmedCount: Int,
    val availableSpots: Int,
    val waitlistCount: Int,
    val capacity: Int,
    val gameVersion: Long,
    val declinedCount: Int = 0,
    val pendingCount: Int = 0,
    val autoConfirmEnabled: Boolean = false,
)

fun interface AttendanceDetailQuery {
    fun find(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceDetail?

    // As respostas do próprio ator nos jogos do grupo, por id do jogo, numa consulta só.
    // Jogo sem resposta fica fora do mapa.
    // ponytail: corpo padrão vazio para os fakes de teste não mudarem; o único adapter real
    // (JdbcAttendanceCommandRepository) sobrescreve. Tirar o padrão se surgir um segundo adapter.
    fun ownByGame(actorId: UUID, groupId: UUID): Map<UUID, AttendanceRecord> = emptyMap()
}

// Names reuse the AttendanceShareSnapshotPerson convention, read from the
// attendance row snapshot instead of the live membership name.
data class AttendanceRosterMember(
    val memberId: UUID,
    val displayName: String,
    val waitlistPosition: Long? = null,
    val guestSeq: Int = 0,
    val hostDisplayName: String? = null,
)

data class AttendanceRoster(
    val confirmed: List<AttendanceRosterMember>,
    val waitlisted: List<AttendanceRosterMember>,
)

fun interface AttendanceRosterQuery {
    fun roster(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceRoster?
}
