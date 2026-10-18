package br.com.saqz.groups.application.finance.charge

import br.com.saqz.groups.application.create.TransactionRunner
import br.com.saqz.groups.application.game.GameSideEffect
import br.com.saqz.groups.domain.finance.charge.Charge
import br.com.saqz.groups.domain.finance.charge.ChargeIdentity
import br.com.saqz.groups.domain.game.Game
import br.com.saqz.groups.domain.game.GameDraftInput
import br.com.saqz.groups.domain.game.GameDraftValidation
import br.com.saqz.groups.domain.game.GameDraftValidator
import br.com.saqz.groups.domain.game.GameStatus
import br.com.saqz.groups.domain.game.GameVenueInput
import br.com.saqz.sharedkernel.subscription.GroupWriteAccess
import br.com.saqz.sharedkernel.subscription.SubscriptionRequiredException
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Cobrança por jogo e mensal (PROM-008, JOGO-005, JOGO-008): limites do valor e do vencimento da mensalidade,
 * o que o plano pausado barra e a delegação do cancelamento de jogo e de convidado.
 */
class ChargeTransactionsRulesTest {
    private val group = UUID.randomUUID()
    private val gameId = UUID.randomUUID()
    private val member = UUID.randomUUID()
    private val actor = UUID.randomUUID()
    private val repository = Repository()
    private var writable = true
    private val checkedGroups = mutableListOf<UUID>()
    private val transactions = ChargeTransactions(
        object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() },
        repository,
        { NOW },
        GroupWriteAccess { checkedGroups += it; writable },
    )

    @Test
    fun `valor da mensalidade vai de 1 centavo a 999999,99`() {
        listOf(1L, 99_999_999L).forEach {
            assertIs<MonthlyGenerationResult.Success>(transactions.generate(monthly(amount = it)), "valor $it")
        }
        listOf(0L, -1L, 100_000_000L).forEach {
            assertEquals(setOf("amountCents"), assertIs<MonthlyGenerationResult.Invalid>(transactions.generate(monthly(amount = it)), "valor $it").fields)
        }
    }

    @Test
    fun `vencimento da mensalidade tem de cair no mes e no ano da competencia`() {
        assertIs<MonthlyGenerationResult.Success>(transactions.generate(monthly(due = LocalDate.of(2026, 8, 1))))
        assertIs<MonthlyGenerationResult.Success>(transactions.generate(monthly(due = LocalDate.of(2026, 8, 31))))
        listOf(LocalDate.of(2026, 7, 31), LocalDate.of(2026, 9, 1), LocalDate.of(2025, 8, 10), LocalDate.of(2027, 8, 10)).forEach {
            assertEquals(setOf("dueDate"), assertIs<MonthlyGenerationResult.Invalid>(transactions.generate(monthly(due = it)), "vencimento $it").fields)
        }
    }

    @Test
    fun `todos os campos errados voltam juntos e nada e criado`() {
        val result = transactions.generate(monthly(amount = 0, due = LocalDate.of(2026, 9, 1), members = emptySet()))

        assertEquals(setOf("amountCents", "dueDate", "memberIds"), assertIs<MonthlyGenerationResult.Invalid>(result).fields)
        assertTrue(repository.monthly.isEmpty())
    }

    @Test
    fun `cada mensalidade nasce com o comando, o membro e o horario do servidor`() {
        val second = UUID.randomUUID()
        repository.members = GroupMembers(setOf(member, second), setOf(member, second))
        val command = monthly(members = setOf(member, second))

        val result = assertIs<MonthlyGenerationResult.Success>(transactions.generate(command))

        assertEquals(setOf(member, second), repository.monthly.map { it.second }.toSet())
        assertEquals(listOf(command, command), repository.monthly.map { it.first })
        assertEquals(listOf(NOW, NOW), repository.monthly.map { it.third })
        assertEquals(listOf(member, second).sortedBy(UUID::toString), result.charges.map { it.memberId })
        assertEquals(0, result.excludedInactiveCount)
    }

    @Test
    fun `plano pausado barra a geracao, a diaria, o cancelamento do jogo e o do convidado sem criar nada`() {
        writable = false

        assertFailsWith<SubscriptionRequiredException> { transactions.generate(monthly()) }
        assertFailsWith<SubscriptionRequiredException> { transactions.attendance(gameInput(AttendanceBillingOutcome.CONFIRMED), actor) }
        assertFailsWith<SubscriptionRequiredException> { transactions.cancelGame(group, gameId, actor) }
        assertFailsWith<SubscriptionRequiredException> { transactions.cancelGuest(group, gameId, member, 1, actor) }

        assertEquals(List(4) { group }, checkedGroups)
        assertTrue(repository.monthly.isEmpty())
        assertTrue(repository.gameCharges.isEmpty())
        assertNull(repository.cancelledGame)
        assertNull(repository.cancelledGuest)
    }

    @Test
    fun `cancelar o convidado entrega ao repositorio quem, qual numero e quando`() {
        transactions.cancelGuest(group, gameId, member, 2, actor)

        assertEquals(listOf(group, gameId, member, 2, actor, NOW), repository.cancelledGuest)
        assertEquals(listOf(group), checkedGroups)
    }

    @Test
    fun `convidado de numero zero ou negativo nao existe`() {
        assertFailsWith<IllegalArgumentException> { transactions.cancelGuest(group, gameId, member, 0, actor) }
        assertFailsWith<IllegalArgumentException> { transactions.cancelGuest(group, gameId, member, -1, actor) }
        assertNull(repository.cancelledGuest)
    }

    @Test
    fun `cancelar o jogo entrega ao repositorio o grupo, o jogo, o autor e o horario`() {
        transactions.cancelGame(group, gameId, actor)

        assertEquals(listOf(group, gameId, actor, NOW), repository.cancelledGame)
        assertEquals(listOf(group), checkedGroups)
    }

    @Test
    fun `a diaria nasce com o comando e o horario e o vinculo e consultado pelo membro e convidado`() {
        val input = gameInput(AttendanceBillingOutcome.PROMOTED).copy(guestSeq = 3)

        val charge = transactions.attendance(input, actor)

        assertEquals(listOf(input), repository.gameCharges)
        assertEquals(listOf(Triple(group, member, 3)), repository.billsAsked)
        assertEquals(input.memberId, charge?.memberId)
    }

    @Test
    fun `sem informacao de vinculo a diaria e cobrada`() {
        val plain = object : ChargeTransactionRepository by repository {}
        val transactions = ChargeTransactions(object : TransactionRunner { override fun <T> inTransaction(block: () -> T): T = block() }, plain, { NOW })

        assertTrue(plain.billsGameCharge(group, member, 0))
        assertTrue(transactions.attendance(gameInput(AttendanceBillingOutcome.CONFIRMED), actor) != null)
    }

    @Test
    fun `efeito de jogo cancelado cancela as cobrancas pendentes e outros efeitos nao`() {
        val effects = GameFinanceSideEffects(transactions)
        val game = game()

        effects.apply(game, actor, setOf(GameSideEffect.SCHEDULE_CHANGED, GameSideEffect.FINANCE_REVIEW_MARKED, GameSideEffect.ATTENDANCE_FROZEN))
        assertNull(repository.cancelledGame)

        effects.apply(game, actor, setOf(GameSideEffect.PENDING_CHARGES_CANCELLED, GameSideEffect.SCHEDULE_CHANGED))
        assertEquals(listOf(game.groupId, game.id, actor, NOW), repository.cancelledGame)
    }

    private fun game(): Game {
        val draft = GameDraftInput(
            "Treino", GameVenueInput(UUID.randomUUID(), "Arena Central", "Rua das Flores 100", "Quadra 2"),
            LocalDate.of(2026, 8, 12), LocalTime.of(19, 30), "America/Sao_Paulo", Instant.parse("2026-08-12T22:30:00Z"), 90, 24,
            Instant.parse("2026-08-12T21:30:00Z"), 2500, null,
        )
        val snapshot = assertIs<GameDraftValidation.Valid>(GameDraftValidator.validate(draft)).snapshot
        return Game(gameId, group, snapshot, GameStatus.CANCELLED)
    }

    private fun gameInput(outcome: AttendanceBillingOutcome) =
        GameChargeInput(group, gameId, member, 2500, LocalDate.of(2026, 8, 11), outcome)

    private fun monthly(
        amount: Long = 3000,
        due: LocalDate = LocalDate.of(2026, 8, 10),
        members: Set<UUID> = setOf(member),
    ) = MonthlyGenerationCommand(UUID.randomUUID(), group, actor, YearMonth.of(2026, 8), amount, due, members)

    private inner class Repository : ChargeTransactionRepository {
        var members: GroupMembers? = GroupMembers(setOf(member), setOf(member))
        val monthly = mutableListOf<Triple<MonthlyGenerationCommand, UUID, Instant>>()
        val gameCharges = mutableListOf<GameChargeInput>()
        val billsAsked = mutableListOf<Triple<UUID, UUID, Int>>()
        var cancelledGame: List<Any>? = null
        var cancelledGuest: List<Any>? = null

        override fun createGameCharge(input: GameChargeInput, actorId: UUID, now: Instant): Charge {
            gameCharges += input
            return Charge(UUID.randomUUID(), input.groupId, input.memberId, ChargeIdentity.Game(input.gameId), 2500, input.dueDate)
        }
        override fun reconcileGameCancellation(groupId: UUID, gameId: UUID, actorId: UUID, now: Instant) {
            cancelledGame = listOf(groupId, gameId, actorId, now)
        }
        override fun billsGameCharge(groupId: UUID, memberId: UUID, guestSeq: Int): Boolean {
            billsAsked += Triple(groupId, memberId, guestSeq)
            return true
        }
        override fun members(groupId: UUID) = members
        override fun createMonthlyCharge(command: MonthlyGenerationCommand, memberId: UUID, now: Instant): Charge {
            monthly += Triple(command, memberId, now)
            return Charge(UUID.randomUUID(), group, memberId, ChargeIdentity.Monthly(command.month), command.amountCents, command.dueDate)
        }
        override fun cancelGuestCharge(groupId: UUID, gameId: UUID, memberId: UUID, guestSeq: Int, actorId: UUID, now: Instant) {
            cancelledGuest = listOf(groupId, gameId, memberId, guestSeq, actorId, now)
        }
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-08-01T10:00:00Z")
    }
}
