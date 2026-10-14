package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import kotlin.test.assertEquals

class MonthlyChargeScheduleLateJoinTest {
    private val group = UUID.randomUUID()
    private val member = UUID.randomUUID()

    // Hoje é 13/10/2026 e o vencimento do grupo é dia 10.
    private val today = LocalDate.of(2026, 10, 13)

    private fun dueDatesFor(
        policy: MonthlyLateJoinPolicy,
        since: LocalDate?,
        dueDay: Int = 10,
        now: LocalDate = today,
    ): List<LocalDate> {
        val created = mutableListOf<LocalDate>()
        val repository = object : ChargeTransactionRepository {
            override fun createGameCharge(input: GameChargeInput, actorId: UUID, now: Instant): Charge = error("unused")
            override fun reconcileGameCancellation(groupId: UUID, gameId: UUID, actorId: UUID, now: Instant) = Unit
            override fun members(groupId: UUID) = GroupMembers(setOf(member), setOf(member))
            override fun createMonthlyCharge(command: MonthlyGenerationCommand, memberId: UUID, now: Instant): Charge {
                created += command.dueDate
                return Charge(UUID.randomUUID(), group, member, ChargeIdentity.Monthly(command.month), 100, command.dueDate)
            }
        }
        val transaction = object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }
        val charges = ChargeTransactions(transaction, repository, { Instant.parse("2026-10-13T12:00:00Z") }, GroupWriteAccess { true })
        val membership = MonthlyDueMembership(group, UUID.randomUUID(), member, 100, dueDay, policy, since)
        val skipped = mutableListOf<MonthlyDueMembership>()
        MonthlyChargeSchedule(MonthlyDueMembershipRepository { listOf(membership) }, charges, { now }) { m, _ -> skipped += m }.run()
        assertEquals(emptyList(), skipped)
        return created
    }

    @Test
    fun `joined after the due day with the default policy is born overdue on the group due date`() {
        assertEquals(listOf(LocalDate.of(2026, 10, 10)), dueDatesFor(MonthlyLateJoinPolicy.GROUP_DUE_DATE, LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun `JOIN_DATE generated on the day the person joined is due that same day`() {
        assertEquals(listOf(today), dueDatesFor(MonthlyLateJoinPolicy.JOIN_DATE, since = today))
    }

    @Test
    fun `JOIN_DATE generated the day after joining is due on the generation day, never in the past`() {
        // Entrou dia 12 às 15h; o job de 03:10 do dia 13 gera a charge. Vencer dia 12 seria nascer atrasada.
        assertEquals(listOf(today), dueDatesFor(MonthlyLateJoinPolicy.JOIN_DATE, LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun `JOIN_DATE generated days after joining, with the job down, is due on the generation day`() {
        assertEquals(
            listOf(LocalDate.of(2026, 10, 17)),
            dueDatesFor(MonthlyLateJoinPolicy.JOIN_DATE, LocalDate.of(2026, 10, 12), now = LocalDate.of(2026, 10, 17)),
        )
    }

    @Test
    fun `JOIN_DATE keeps a join date that is later than the generation day`() {
        // O dia de quem entrou vem do fuso do grupo e o job roda no fuso de São Paulo: num grupo à frente de
        // São Paulo, "hoje" do job pode ser ainda véspera do dia em que a pessoa entrou.
        assertEquals(
            listOf(LocalDate.of(2026, 10, 14)),
            dueDatesFor(MonthlyLateJoinPolicy.JOIN_DATE, LocalDate.of(2026, 10, 14), now = today),
        )
    }

    @Test
    fun `JOIN_DATE is never due before the day it was generated`() {
        for (joinDay in 11..20) {
            for (generationDay in joinDay..25) {
                assertEquals(
                    listOf(LocalDate.of(2026, 10, generationDay)),
                    dueDatesFor(MonthlyLateJoinPolicy.JOIN_DATE, LocalDate.of(2026, 10, joinDay), now = LocalDate.of(2026, 10, generationDay)),
                    "entrou dia $joinDay, gerada dia $generationDay",
                )
            }
        }
    }

    @Test
    fun `joined after the due day with NEXT_MONTH is not charged this month`() {
        assertEquals(emptyList(), dueDatesFor(MonthlyLateJoinPolicy.NEXT_MONTH, LocalDate.of(2026, 10, 12)))
    }

    @Test
    fun `NEXT_MONTH charges the following month normally`() {
        assertEquals(
            listOf(LocalDate.of(2026, 11, 10)),
            dueDatesFor(MonthlyLateJoinPolicy.NEXT_MONTH, LocalDate.of(2026, 10, 12), now = LocalDate.of(2026, 11, 10)),
        )
    }

    @Test
    fun `joined before the due day is unaffected by any policy`() {
        MonthlyLateJoinPolicy.entries.forEach { policy ->
            assertEquals(listOf(LocalDate.of(2026, 10, 10)), dueDatesFor(policy, LocalDate.of(2026, 10, 5)), policy.name)
        }
    }

    @Test
    fun `joined on the due day itself is unaffected by any policy`() {
        MonthlyLateJoinPolicy.entries.forEach { policy ->
            assertEquals(listOf(LocalDate.of(2026, 10, 10)), dueDatesFor(policy, LocalDate.of(2026, 10, 10)), policy.name)
        }
    }

    @Test
    fun `joined in an earlier month is unaffected by any policy`() {
        MonthlyLateJoinPolicy.entries.forEach { policy ->
            assertEquals(listOf(LocalDate.of(2026, 10, 10)), dueDatesFor(policy, LocalDate.of(2026, 9, 30)), policy.name)
        }
    }

    @Test
    fun `the member due day override decides whether the join was late`() {
        // Com o dia 20 do membro, entrar dia 12 ainda é antes do vencimento: nenhuma política muda nada.
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20)),
            dueDatesFor(MonthlyLateJoinPolicy.JOIN_DATE, LocalDate.of(2026, 10, 12), dueDay = 20, now = LocalDate.of(2026, 10, 21)),
        )
        assertEquals(
            listOf(LocalDate.of(2026, 10, 20)),
            dueDatesFor(MonthlyLateJoinPolicy.NEXT_MONTH, LocalDate.of(2026, 10, 12), dueDay = 20, now = LocalDate.of(2026, 10, 21)),
        )
        // Com o dia 5 do membro, entrar dia 12 foi depois do vencimento: a charge vence no dia em que é gerada (13).
        assertEquals(listOf(today), dueDatesFor(MonthlyLateJoinPolicy.JOIN_DATE, LocalDate.of(2026, 10, 12), dueDay = 5))
    }

    @Test
    fun `unknown join date keeps the group due date`() {
        assertEquals(listOf(LocalDate.of(2026, 10, 10)), dueDatesFor(MonthlyLateJoinPolicy.NEXT_MONTH, null))
    }
}
