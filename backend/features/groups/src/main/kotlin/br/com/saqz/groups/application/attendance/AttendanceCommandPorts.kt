package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.AthletePosition
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
    val startsAt: Instant,
    /**
     * Jogo com posições (DEB-102): [position] é a posição da linha em jogo, [capacity] e [confirmedCount] já vêm
     * recortados para ela (vagas e confirmados da posição). Sem posições (jogo legado) fica tudo nulo e vale o total.
     */
    val position: AthletePosition? = null,
    val profilePosition: AthletePosition? = null,
    val profileSecondaryPosition: AthletePosition? = null,
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
    /** Posição em campo da vaga (DEB-102); nula em jogo sem posições. */
    val position: AthletePosition? = null,
    /** Entrou acima das vagas da posição por escolha do organizador. */
    val forced: Boolean = false,
    /**
     * Posição na fila como o app mostra ("1º na fila"), calculada nas leituras (ver WaitlistPositionSql).
     * `null` nas escritas e fora da fila; a sequência gravada nunca é renumerada e por isso não serve de posição.
     */
    val waitlistPosition: Long? = null,
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
    /** Vagas e situação de cada posição do jogo; vazio = jogo sem posições (total, uma espera só). */
    fun positionLoads(gameId: UUID): List<PositionLoad> = emptyList()

    /** Como [earliestWaitlisted], só entre quem espera em [position] (DEB-102). */
    fun earliestWaitlistedIn(groupId: UUID, gameId: UUID, now: Instant, position: AthletePosition): AttendanceRecord? =
        earliestWaitlisted(groupId, gameId, now)

    /** Quem sobe primeiro; [now] (relógio injetado) decide se a prioridade de mensalista ainda vale (até o instante do prazo). */
    fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: Instant): AttendanceRecord?
    fun save(record: AttendanceRecord)
    fun append(event: AttendanceEvent)
    fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int): Boolean
    fun findPromotionReplay(groupId: UUID, gameId: UUID, actorId: UUID, requestId: UUID): AttendancePromotionReplay? = null
    fun findResponseReplay(groupId: UUID, gameId: UUID, actorId: UUID, requestId: UUID): AttendanceResponseReplay? = null

    /** Próximo número de convidado do anfitrião neste jogo (linhas nunca são apagadas → max+1). */
    fun nextGuestSeq(gameId: UUID, hostId: UUID): Int = 1

    /** Convidados do anfitrião ainda no jogo (CONFIRMED ou WAITLISTED), travados para escrita. */
    fun activeGuests(groupId: UUID, gameId: UUID, hostId: UUID): List<AttendanceRecord> = emptyList()

    /** Jogos futuros (PUBLISHED/DRAFT, ainda não começados) em que a pessoa ou um convidado dela ocupa vaga ou espera. */
    fun futureGamesWithActiveAttendance(groupId: UUID, memberId: UUID, after: Instant): List<UUID> = emptyList()

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
    val startsAt: Instant,
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
    /** Resumo por posição; vazio em jogo sem posições. O total acima segue sendo o total do jogo (soma das posições). */
    val positions: List<PositionLoad> = emptyList(),
)

fun interface AttendanceDetailQuery {
    fun find(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceDetail?

    // As respostas do próprio ator nos jogos do grupo, por id do jogo, numa consulta só.
    // Jogo sem resposta fica fora do mapa.
    // ponytail: corpo padrão vazio para os fakes de teste não mudarem; o único adapter real
    // (JdbcAttendanceCommandRepository) sobrescreve. Tirar o padrão se surgir um segundo adapter.
    fun ownByGame(actorId: UUID, groupId: UUID): Map<UUID, AttendanceRecord> = emptyMap()

    // Vagas por posição dos jogos [gameIds] do grupo (DEB-109, linha "Faltam" da agenda); jogo sem posições fica fora do mapa.
    // ponytail: corpo padrão vazio pelo mesmo motivo de [ownByGame].
    fun positionsByGame(groupId: UUID, gameIds: Set<UUID>): Map<UUID, List<br.com.saqz.groups.domain.attendance.PositionLoad>> =
        emptyMap()
}

// Names reuse the AttendanceShareSnapshotPerson convention, read from the
// attendance row snapshot instead of the live membership name.
data class AttendanceRosterMember(
    val memberId: UUID,
    val displayName: String,
    val waitlistPosition: Long? = null,
    val guestSeq: Int = 0,
    val hostDisplayName: String? = null,
    val position: AthletePosition? = null,
    val forced: Boolean = false,
)

/**
 * [waitlisted] traz a posição na fila de cada um: com posições é a vez na fila da própria posição (DEB-102);
 * sem posições, a fila única de sempre.
 */
data class AttendanceRoster(
    val confirmed: List<AttendanceRosterMember>,
    val waitlisted: List<AttendanceRosterMember>,
    val positions: List<PositionLoad> = emptyList(),
)

fun interface AttendanceRosterQuery {
    fun roster(actorId: UUID, groupId: UUID, gameId: UUID): AttendanceRoster?
}
