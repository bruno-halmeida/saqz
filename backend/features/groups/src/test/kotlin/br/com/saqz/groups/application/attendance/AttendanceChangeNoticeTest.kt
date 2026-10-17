package br.com.saqz.groups.application.attendance

import br.com.saqz.groups.application.communication.attendanceConfirmedBody
import br.com.saqz.groups.application.communication.attendanceRemovedBody
import br.com.saqz.groups.domain.attendance.AttendanceSource
import br.com.saqz.groups.domain.attendance.AttendanceSource.*
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.attendance.AttendanceStatus.*
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** Quando uma mudança de presença vira aviso direto (PRES-013), uma linha por caso da regra do dono. */
class AttendanceChangeNoticeTest {
    private val member = UUID.randomUUID()
    private val other = UUID.randomUUID()

    private fun event(source: AttendanceSource, old: AttendanceStatus?, new: AttendanceStatus, actor: UUID = other, guest: Int = 0) =
        AttendanceEvent(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), member, actor, source, old, new, null, Instant.now(), guestSeq = guest)

    @Test fun `fila promove da espera para confirmada avisa a pessoa`() =
        assertEquals(AttendanceChangeKind.CONFIRMED, event(SYSTEM, WAITLISTED, CONFIRMED).changeKind())

    @Test fun `fila promove convidado avisa o anfitriao`() =
        assertEquals(AttendanceChangeKind.CONFIRMED, event(SYSTEM, WAITLISTED, CONFIRMED, guest = 1).changeKind())

    @Test fun `organizador confirma alguem avisa mesmo vindo de fora da lista`() {
        assertEquals(AttendanceChangeKind.CONFIRMED, event(ORGANIZER, null, CONFIRMED).changeKind())
        assertEquals(AttendanceChangeKind.CONFIRMED, event(ORGANIZER, DECLINED, CONFIRMED).changeKind())
        assertEquals(AttendanceChangeKind.CONFIRMED, event(ORGANIZER, WAITLISTED, CONFIRMED, guest = 2).changeKind())
    }

    @Test fun `organizador tira da lista confirmada ou da espera avisa`() {
        assertEquals(AttendanceChangeKind.REMOVED, event(ORGANIZER, CONFIRMED, DECLINED).changeKind())
        assertEquals(AttendanceChangeKind.REMOVED, event(ORGANIZER, WAITLISTED, DECLINED, guest = 1).changeKind())
    }

    @Test fun `a propria pessoa confirmou ou desistiu nao avisa`() {
        assertNull(event(SELF, null, CONFIRMED, actor = member).changeKind())
        assertNull(event(SELF, WAITLISTED, CONFIRMED).changeKind())
        assertNull(event(SELF, CONFIRMED, DECLINED, actor = member).changeKind())
    }

    @Test fun `vou depois do prazo confirmado na hora pela fila nao avisa`() =
        assertNull(event(SYSTEM, WAITLISTED, CONFIRMED, actor = member).changeKind())

    @Test fun `confirmacao automatica do mensalista tem aviso proprio e nao avisa`() =
        assertNull(event(SYSTEM, null, CONFIRMED).changeKind())

    @Test fun `saida do grupo e convidados que caem com o anfitriao nao avisam`() {
        assertNull(event(SYSTEM, CONFIRMED, DECLINED).changeKind())
        assertNull(event(SYSTEM, WAITLISTED, DECLINED, guest = 1).changeKind())
    }

    @Test fun `sem mudanca de estado nao avisa`() {
        assertNull(event(ORGANIZER, CONFIRMED, CONFIRMED).changeKind())
        assertNull(event(ORGANIZER, DECLINED, DECLINED).changeKind())
    }

    @Test fun `organizador que tira quem nunca estava no jogo nao avisa`() =
        assertNull(event(ORGANIZER, null, DECLINED).changeKind())

    @Test fun `organizador mexendo em si mesmo nao avisa`() =
        assertNull(event(ORGANIZER, WAITLISTED, CONFIRMED, actor = member).changeKind())

    @Test fun `textos sao curtos, citam o jogo e nao supoem genero`() {
        val date = LocalDate.of(2026, 10, 14)
        val time = LocalTime.of(20, 30)
        assertEquals("Sua vaga está confirmada no jogo de quarta, 14/10 às 20:30, em Arena.", attendanceConfirmedBody(date, time, "Arena"))
        assertEquals("A vaga de Rafa está confirmada no jogo de quarta, 14/10 às 20:30, em Arena.", attendanceConfirmedBody(date, time, "Arena", "Rafa"))
        assertEquals("Sua vaga no jogo de quarta, 14/10 às 20:30 foi removida pela organização.", attendanceRemovedBody(date, time))
        assertEquals("A vaga de Rafa no jogo de quarta, 14/10 às 20:30 foi removida pela organização.", attendanceRemovedBody(date, time, "Rafa"))
    }
}
