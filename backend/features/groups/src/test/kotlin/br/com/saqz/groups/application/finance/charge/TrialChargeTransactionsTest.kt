package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TrialChargeTransactionsTest {
    @Test
    fun `expired trial blocks automatic monthly and attendance billing without persisted charges`() {
        val group = UUID.randomUUID()
        val member = UUID.randomUUID()
        val now = Instant.parse("2026-09-26T12:00:00Z")
        val created = mutableListOf<Charge>()
        var cancelled = false
        val repository = object : ChargeTransactionRepository {
            override fun createGameCharge(input: GameChargeInput, actorId: UUID, now: Instant): Charge =
                Charge(UUID.randomUUID(), group, member, ChargeIdentity.Game(input.gameId), 100, input.dueDate).also(created::add)
            override fun reconcileGameCancellation(groupId: UUID, gameId: UUID, actorId: UUID, now: Instant) { cancelled = true }
            override fun members(groupId: UUID) = GroupMembers(setOf(member), setOf(member))
            override fun createMonthlyCharge(command: MonthlyGenerationCommand, memberId: UUID, now: Instant): Charge =
                Charge(UUID.randomUUID(), group, member, ChargeIdentity.Monthly(command.month), 100, command.dueDate).also(created::add)
        }
        val transaction = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        val charges = ChargeTransactions(transaction, repository, { now }, GroupWriteAccess { false })
        val skipped = mutableListOf<MonthlyDueMembership>()
        val due = MonthlyDueMembership(group, member, member, 100, 1)
        val schedule = MonthlyChargeSchedule(MonthlyDueMembershipRepository { listOf(due) }, charges, { LocalDate.of(2026, 9, 26) }) { membership, _ -> skipped += membership }
        assertEquals(emptyList(), schedule.run())
        assertEquals(listOf(due), skipped)
        assertFailsWith<SubscriptionRequiredException> {
            charges.attendance(GameChargeInput(group, UUID.randomUUID(), member, 100, LocalDate.of(2026, 9, 26), AttendanceBillingOutcome.CONFIRMED), member)
        }
        assertFailsWith<SubscriptionRequiredException> { charges.cancelGame(group, UUID.randomUUID(), member) }
        assertEquals(false, cancelled)
        assertTrue(created.isEmpty())
    }
}
