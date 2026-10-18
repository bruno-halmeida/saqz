package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.AthleteMembershipType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.game.GameStatus
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Contrato dos padrões das portas: o adaptador que não os sobrescreve cobra a promoção como confirmação e não tem convidados. */
class AttendancePortDefaultsTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val aggregate = AttendanceAggregate(
        group, game, member, actor, GroupRole.OWNER, GameStatus.PUBLISHED, NOW, 12, 3, null, 2500, LocalDate.of(2026, 8, 11),
        AthleteMembershipType.AVULSO, startsAt = NOW.plusSeconds(3600),
    )

    @Test
    fun `promocao sem tratamento proprio cobra como confirmacao e estorno de convidado nao faz nada`() {
        val seen = mutableListOf<Pair<AttendanceAggregate, UUID>>()
        val port = AttendanceChargePort { aggregate, actorId -> seen += aggregate to actorId }

        port.promoted(aggregate, actor)
        port.guestRemoved(aggregate, actor)

        assertEquals(listOf(aggregate to actor), seen)
    }

    @Test
    fun `repositorio basico nao tem convidados, repeticoes nem jogos futuros e o proximo convidado e o 1`() {
        val saved = mutableListOf<AttendanceRecord>()
        val repository = object : AttendanceCommandRepository {
            override fun lock(groupId: UUID, gameId: UUID, memberId: UUID, actorId: UUID, guestSeq: Int): AttendanceAggregate? = null
            override fun lockCapacity(groupId: UUID, gameId: UUID, actorId: UUID): CapacityAggregate? = null
            override fun nextWaitlistSequence(groupId: UUID, gameId: UUID) = 1L
            override fun earliestWaitlisted(groupId: UUID, gameId: UUID, now: Instant): AttendanceRecord? = null
            override fun save(record: AttendanceRecord) { saved += record }
            override fun append(event: AttendanceEvent) = Unit
            override fun updateCapacity(gameId: UUID, expectedVersion: Long, capacity: Int) = false
        }
        val record = AttendanceRecord(game, group, member, AttendanceStatus.WAITLISTED, 1, NOW, NOW, 1, guestSeq = 1)

        assertNull(repository.findPromotionReplay(group, game, actor, UUID.randomUUID()))
        assertNull(repository.findResponseReplay(group, game, actor, UUID.randomUUID()))
        assertEquals(1, repository.nextGuestSeq(game, member))
        assertTrue(repository.activeGuests(group, game, member).isEmpty())
        assertTrue(repository.futureGamesWithActiveAttendance(group, member, NOW).isEmpty())
        repository.saveGuest(record, "Rafa Moreira")
        assertEquals(listOf(record), saved)
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
