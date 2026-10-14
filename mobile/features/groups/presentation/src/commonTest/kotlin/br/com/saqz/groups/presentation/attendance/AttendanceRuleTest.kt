package br.com.saqz.groups.presentation.attendance

import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.domain.attendance.AttendanceStatus
import br.com.saqz.groups.domain.group.PromotionMode
import br.com.saqz.groups.presentation.home.HomeWaitlistKind
import kotlin.test.Test
import kotlin.test.assertEquals

class AttendanceRuleTest {
    private val mensalista = AthleteMembershipType.MENSALISTA
    private val avulso = AthleteMembershipType.AVULSO

    private fun vou(
        membership: AthleteMembershipType,
        priority: Boolean,
        mode: PromotionMode,
        late: Boolean = false,
        full: Boolean = false,
        current: AttendanceStatus? = null,
    ) = AttendanceRule.predict(AttendanceIntent.Confirm, current, membership, priority, mode, late, full)

    @Test
    fun fifoWithPriority() {
        assertEquals(AttendanceStatus.Confirmed, vou(mensalista, true, PromotionMode.FIFO))
        assertEquals(AttendanceStatus.Waitlisted, vou(mensalista, true, PromotionMode.FIFO, full = true))
        assertEquals(AttendanceStatus.Waitlisted, vou(avulso, true, PromotionMode.FIFO))
    }

    @Test
    fun fifoWithoutPriority() {
        assertEquals(AttendanceStatus.Confirmed, vou(mensalista, false, PromotionMode.FIFO))
        assertEquals(AttendanceStatus.Confirmed, vou(avulso, false, PromotionMode.FIFO))
        assertEquals(AttendanceStatus.Waitlisted, vou(avulso, false, PromotionMode.FIFO, full = true))
    }

    @Test
    fun manualWithPriorityOnlyMensalistaEntersDirectly() {
        assertEquals(AttendanceStatus.Confirmed, vou(mensalista, true, PromotionMode.MANUAL))
        assertEquals(AttendanceStatus.Waitlisted, vou(avulso, true, PromotionMode.MANUAL))
    }

    @Test
    fun manualWithoutPriorityEveryoneWaits() {
        assertEquals(AttendanceStatus.Waitlisted, vou(mensalista, false, PromotionMode.MANUAL))
        assertEquals(AttendanceStatus.Waitlisted, vou(avulso, false, PromotionMode.MANUAL))
    }

    @Test
    fun afterTheDeadlineManualWaitsAndFifoConfirmsWhileThereIsRoom() {
        for (priority in listOf(true, false)) for (membership in listOf(mensalista, avulso)) {
            assertEquals(AttendanceStatus.Waitlisted, vou(membership, priority, PromotionMode.MANUAL, late = true))
            assertEquals(AttendanceStatus.Confirmed, vou(membership, priority, PromotionMode.FIFO, late = true))
            assertEquals(AttendanceStatus.Waitlisted, vou(membership, priority, PromotionMode.FIFO, late = true, full = true))
        }
        assertEquals(AttendanceStatus.Confirmed, vou(mensalista, true, PromotionMode.FIFO, late = true, current = AttendanceStatus.Confirmed))
    }

    @Test
    fun declineIsAlwaysDecline() {
        val status = AttendanceRule.predict(AttendanceIntent.Decline, AttendanceStatus.Confirmed, mensalista, true, PromotionMode.MANUAL, true, false)
        assertEquals(AttendanceStatus.Declined, status)
    }

    @Test
    fun waitlistKindExplainsWhy() {
        assertEquals(HomeWaitlistKind.Manual, AttendanceRule.waitlistKind(avulso, true, PromotionMode.MANUAL, false))
        assertEquals(HomeWaitlistKind.Manual, AttendanceRule.waitlistKind(mensalista, false, PromotionMode.MANUAL, true))
        assertEquals(HomeWaitlistKind.AfterDeadline, AttendanceRule.waitlistKind(avulso, true, PromotionMode.FIFO, true))
        assertEquals(HomeWaitlistKind.AvulsoList, AttendanceRule.waitlistKind(avulso, true, PromotionMode.FIFO, false))
        assertEquals(HomeWaitlistKind.Reserva, AttendanceRule.waitlistKind(mensalista, true, PromotionMode.FIFO, false))
        assertEquals(HomeWaitlistKind.Reserva, AttendanceRule.waitlistKind(avulso, false, PromotionMode.FIFO, false))
    }
}
