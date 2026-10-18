package br.com.saqz.groups.application.finance.charge

import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.groups.domain.group.MonthlyLateJoinPolicy
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A rotina das 03h10 que gera as mensalidades vencidas do mês: o que devolve, o que pula e o que registra
 * no log do job (candidatos, geradas, puladas). A política de quem entrou depois do vencimento está em
 * `MonthlyChargeScheduleLateJoinTest`.
 */
class MonthlyChargeScheduleTest {
    private val today = LocalDate.of(2026, 10, 13)
    private val group = UUID.randomUUID()
    private val owner = UUID.randomUUID()
    private val first = UUID.randomUUID()
    private val second = UUID.randomUUID()
    private val third = UUID.randomUUID()
    private val generated = mutableListOf<MonthlyGenerationCommand>()
    private val skipped = mutableListOf<Pair<MonthlyDueMembership, String>>()
    private var askedDays = mutableListOf<Int>()
    private var due = listOf<MonthlyDueMembership>()
    private var knownMembers = setOf(first, second, third)
    private var activeMembers = setOf(first, second, third)
    private var breakFor: UUID? = null
    private var writable = true

    private val logger = LoggerFactory.getLogger("br.com.saqz.job") as Logger
    private val appender = ListAppender<ILoggingEvent>()

    @BeforeEach fun attach() { appender.start(); logger.addAppender(appender) }
    @AfterEach fun detach() { logger.detachAppender(appender) }
    private fun lines() = appender.list.map { it.formattedMessage }

    private val repository = object : ChargeTransactionRepository {
        override fun createGameCharge(input: GameChargeInput, actorId: UUID, now: Instant): Charge = error("unused")
        override fun reconcileGameCancellation(groupId: UUID, gameId: UUID, actorId: UUID, now: Instant) = Unit
        override fun members(groupId: UUID) = GroupMembers(knownMembers, activeMembers)
        override fun createMonthlyCharge(command: MonthlyGenerationCommand, memberId: UUID, now: Instant): Charge {
            check(memberId != breakFor) { "banco fora do ar" }
            generated += command
            return Charge(UUID.randomUUID(), command.groupId, memberId, ChargeIdentity.Monthly(command.month), command.amountCents, command.dueDate)
        }
    }
    private val charges = ChargeTransactions(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        repository,
        { Instant.parse("2026-10-13T06:10:00Z") },
        GroupWriteAccess { writable },
    )
    private val schedule = MonthlyChargeSchedule(
        { day -> askedDays += day; due },
        charges,
        { today },
    ) { membership, reason -> skipped += membership to reason }

    @Test
    fun `gera uma mensalidade por candidato com o valor, o vencimento e o mes de hoje e devolve todas`() {
        due = listOf(membership(first, amount = 3000, dueDay = 10), membership(second, amount = 4500, dueDay = 5))

        val result = schedule.run()

        assertEquals(listOf(first, second), result.map { it.memberId })
        assertEquals(listOf(3000L, 4500L), result.map { it.amountCents })
        assertEquals(listOf(LocalDate.of(2026, 10, 10), LocalDate.of(2026, 10, 5)), result.map { it.dueDate })
        assertEquals(listOf(ChargeIdentity.Monthly(YearMonth.of(2026, 10))), result.map { it.identity }.distinct())
        assertEquals(listOf(setOf(first), setOf(second)), generated.map { it.selectedMemberIds })
        assertEquals(listOf(owner, owner), generated.map { it.actorId })
        assertEquals(listOf(group, group), generated.map { it.groupId })
        assertEquals(2, generated.map { it.requestId }.distinct().size)
        assertEquals(emptyList(), skipped)
    }

    @Test
    fun `pergunta ao repositorio pelo dia do mes de hoje`() {
        schedule.run()

        assertEquals(listOf(13), askedDays)
    }

    @Test
    fun `sem candidato devolve lista vazia e ainda registra a rodada com zeros`() {
        assertEquals(emptyList(), schedule.run())

        assertTrue(Regex("^job_run job=monthly_charges candidates=0 generated=0 skipped=0 durationMs=\\d+$").matches(lines().single()), lines().toString())
    }

    @Test
    fun `um grupo que falha e pulado, avisado com o motivo, e nao impede os outros`() {
        due = listOf(membership(first), membership(second), membership(third))
        breakFor = second

        val result = schedule.run()

        assertEquals(listOf(first, third), result.map { it.memberId })
        assertEquals(listOf(second), skipped.map { it.first.memberId })
        assertEquals("banco fora do ar", skipped.single().second)
    }

    @Test
    fun `resposta que nao e sucesso tambem e pulada com o que o servico respondeu`() {
        due = listOf(membership(first, amount = 0), membership(second))
        knownMembers = setOf(first, second)

        val result = schedule.run()

        assertEquals(listOf(second), result.map { it.memberId })
        assertEquals(listOf(first), skipped.map { it.first.memberId })
        assertEquals("Invalid(fields=[amountCents])", skipped.single().second)
    }

    @Test
    fun `membro inativo nao gera mensalidade nem conta como pulado`() {
        due = listOf(membership(first))
        activeMembers = emptySet()

        assertEquals(emptyList(), schedule.run())
        assertEquals(emptyList(), skipped)
    }

    @Test
    fun `plano pausado pula o grupo com o motivo`() {
        due = listOf(membership(first))
        writable = false

        assertEquals(emptyList(), schedule.run())

        assertEquals(listOf(first), skipped.map { it.first.memberId })
    }

    @Test
    fun `entrou depois do vencimento com a politica proximo mes nao e cobrado nem pulado`() {
        due = listOf(membership(first, since = LocalDate.of(2026, 10, 12), policy = MonthlyLateJoinPolicy.NEXT_MONTH))

        assertEquals(emptyList(), schedule.run())

        assertEquals(emptyList(), skipped)
        assertEquals(emptyList(), generated)
        assertTrue("candidates=1 generated=0 skipped=0" in lines().last(), lines().toString())
    }

    @Test
    fun `o log do job traz candidatos, geradas e puladas e cada geracao tem a sua linha`() {
        due = listOf(membership(first), membership(second), membership(third))
        breakFor = second

        val result = schedule.run()

        val lines = lines()
        assertEquals(
            listOf(
                "job_item job=monthly_charges event=charge_generated groupId=$group memberId=$first chargeId=${result[0].id}",
                "job_item_failed job=monthly_charges event=charge_skipped groupId=$group memberId=$second error=IllegalStateException",
                "job_item job=monthly_charges event=charge_generated groupId=$group memberId=$third chargeId=${result[1].id}",
            ),
            lines.dropLast(1),
        )
        assertTrue(Regex("^job_run job=monthly_charges candidates=3 generated=2 skipped=1 durationMs=\\d+$").matches(lines.last()), lines.last())
    }

    private fun membership(
        member: UUID,
        amount: Long = 3000,
        dueDay: Int = 10,
        since: LocalDate? = null,
        policy: MonthlyLateJoinPolicy = MonthlyLateJoinPolicy.GROUP_DUE_DATE,
    ) = MonthlyDueMembership(group, owner, member, amount, dueDay, policy, since)
}
