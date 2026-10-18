package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * SAI-002: nos jogos futuros, quem sai do grupo e os convidados dele viram "Não vou" (evento do sistema) e a fila
 * anda como num "Não vou" (PROM-001, PROM-003).
 */
class ReleaseMemberAttendanceTest {
    private val group = UUID.randomUUID()
    private val game = UUID.randomUUID()
    private val leaver = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val keeper = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val third = UUID.randomUUID()
    private val repository = InMemoryAttendance(group, game, NOW)
    private val charges = RecordingCharges()
    private val release = ReleaseMemberAttendance(repository, charges, { NOW }, UUID::randomUUID)

    @Test
    fun `a pessoa e os convidados saem do jogo, so os confirmados abrem vaga e a fila sobe nelas`() {
        repository.capacity = 3
        repository.record(leaver, AttendanceStatus.CONFIRMED, version = 4)
        repository.record(leaver, AttendanceStatus.CONFIRMED, guestSeq = 1)
        repository.record(leaver, AttendanceStatus.WAITLISTED, sequence = 1, guestSeq = 2)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 2)
        repository.record(second, AttendanceStatus.WAITLISTED, sequence = 3)
        repository.record(third, AttendanceStatus.WAITLISTED, sequence = 4)

        release.release(group, leaver, actor)

        assertEquals(
            listOf(AttendanceStatus.DECLINED, AttendanceStatus.DECLINED, AttendanceStatus.DECLINED),
            listOf(0, 1, 2).map { repository.status(leaver, it) },
        )
        val own = repository.records.getValue(leaver to 0)
        assertEquals(5L, own.version)
        assertEquals(null, own.waitlistSequence)
        assertEquals(null, repository.records.getValue(leaver to 2).waitlistSequence)
        // Duas vagas abertas (a pessoa e o convidado confirmado); o convidado da espera não abre vaga.
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(first))
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(second))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(third))
        assertEquals(
            listOf(
                ChargeCall("guestRemoved", leaver, 1, actor),
                ChargeCall("promoted", first, 0, actor),
                ChargeCall("promoted", second, 0, actor),
            ),
            charges.calls,
        )
        val drops = repository.events.filter { it.newStatus == AttendanceStatus.DECLINED }
        assertEquals(
            listOf(Triple(0, AttendanceStatus.CONFIRMED, leaver), Triple(1, AttendanceStatus.CONFIRMED, leaver), Triple(2, AttendanceStatus.WAITLISTED, leaver)),
            drops.map { Triple(it.guestSeq, it.oldStatus, it.memberId) },
        )
        assertTrue(drops.all { it.source == AttendanceSource.SYSTEM && it.actorId == actor && it.occurredAt == NOW && it.reason == null })
    }

    @Test
    fun `quem estava na espera sai sem abrir vaga e ninguem sobe`() {
        repository.capacity = 2
        repository.record(keeper, AttendanceStatus.CONFIRMED)
        repository.record(leaver, AttendanceStatus.WAITLISTED, sequence = 1)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 2)

        release.release(group, leaver, actor)

        assertEquals(AttendanceStatus.DECLINED, repository.status(leaver))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertEquals(1, repository.events.size)
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `quem ja tinha dito Nao vou nao gera evento, mas os convidados ativos dele saem`() {
        repository.capacity = 2
        repository.record(leaver, AttendanceStatus.DECLINED)
        repository.record(leaver, AttendanceStatus.CONFIRMED, guestSeq = 1)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)

        release.release(group, leaver, actor)

        assertEquals(AttendanceStatus.DECLINED, repository.status(leaver, 1))
        assertEquals(listOf(1), repository.events.filter { it.newStatus == AttendanceStatus.DECLINED }.map { it.guestSeq })
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(first))
        assertEquals(listOf(ChargeCall("guestRemoved", leaver, 1, actor), ChargeCall("promoted", first, 0, actor)), charges.calls)
    }

    @Test
    fun `em escolha manual libera as vagas e deixa a fila para o organizador`() {
        repository.promotionMode = PromotionMode.MANUAL
        repository.capacity = 2
        repository.record(leaver, AttendanceStatus.CONFIRMED)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)

        release.release(group, leaver, actor)

        assertEquals(AttendanceStatus.DECLINED, repository.status(leaver))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(first))
        assertTrue(charges.calls.isEmpty())
    }

    @Test
    fun `jogo que ja comecou nao e tocado`() {
        repository.startsAt = NOW
        repository.record(leaver, AttendanceStatus.CONFIRMED)

        release.release(group, leaver, actor)

        assertEquals(AttendanceStatus.CONFIRMED, repository.status(leaver))
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun `jogo que nao e do grupo informado e ignorado`() {
        repository.record(leaver, AttendanceStatus.CONFIRMED)

        release.release(UUID.randomUUID(), leaver, actor)

        assertEquals(AttendanceStatus.CONFIRMED, repository.status(leaver))
        assertTrue(repository.events.isEmpty())
    }

    @Test
    fun `vaga aberta so vale ate a lotacao quando o jogo ja estava acima dela`() {
        // Vagas reduzidas depois das confirmações: 4 confirmados para 3 vagas (PROM-006).
        repository.capacity = 3
        repository.record(keeper, AttendanceStatus.CONFIRMED)
        repository.record(leaver, AttendanceStatus.CONFIRMED)
        repository.record(leaver, AttendanceStatus.CONFIRMED, guestSeq = 1)
        repository.record(leaver, AttendanceStatus.CONFIRMED, guestSeq = 2)
        repository.record(first, AttendanceStatus.WAITLISTED, sequence = 1)
        repository.record(second, AttendanceStatus.WAITLISTED, sequence = 2)
        repository.record(third, AttendanceStatus.WAITLISTED, sequence = 3)

        release.release(group, leaver, actor)

        // Sobra 1 confirmado para 3 vagas: só 2 sobem.
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(first))
        assertEquals(AttendanceStatus.CONFIRMED, repository.status(second))
        assertEquals(AttendanceStatus.WAITLISTED, repository.status(third))
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-10T10:00:00Z")
    }
}
