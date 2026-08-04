package br.com.saqz.groups.adapter.input.http

import br.com.saqz.groups.application.finance.statement.FinanceStatementDirection
import br.com.saqz.groups.application.finance.statement.FinanceStatementItem
import br.com.saqz.groups.application.finance.statement.FinanceStatementPage
import br.com.saqz.groups.application.finance.statement.FinanceStatementRepository
import br.com.saqz.groups.application.finance.statement.FinanceStatementService
import br.com.saqz.groups.application.finance.statement.FinanceStatementSummary
import br.com.saqz.groups.application.finance.statement.FinanceStatementType
import br.com.saqz.groups.domain.GroupRole
import br.com.saqz.sharedkernel.RequestIdentity
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class FinanceStatementControllerTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val identity = RequestIdentity("subject", emailVerified = true, displayName = "Player")
    private lateinit var repository: MemoryRepository
    private lateinit var controller: FinanceStatementController

    @org.junit.jupiter.api.BeforeEach
    fun setup() {
        repository = MemoryRepository()
        controller = FinanceStatementController(
            VerifiedGroupActorResolver { actor },
            FinanceStatementService(repository) { YearMonth.of(2026, 8) },
        )
    }

    @org.junit.jupiter.api.Test
    fun `statement maps signed item metadata and all summary balances`() {
        repository.role = GroupRole.OWNER

        val response = controller.list(identity, group.toString(), "2026-08", null, 20, 0)

        assertEquals("2026-08", response.month)
        assertEquals(2, response.items.size)
        assertEquals("CHARGE", response.items.first().type)
        assertEquals("IN", response.items.first().direction)
        assertEquals("Mensalidade · Marina Freitas", response.items.first().title)
        assertEquals("MONTHLY", response.items.first().category)
        assertEquals("PIX", response.items.first().paidMethod)
        assertEquals(3000L, response.items.first().amountCents)
        assertEquals("OUT", response.items.last().direction)
        assertEquals(-700L, response.items.last().amountCents)
        assertEquals(3000L, response.summary.totalInCents)
        assertEquals(700L, response.summary.totalOutCents)
        assertEquals(2300L, response.summary.periodBalanceCents)
        assertEquals(1800L, response.summary.accumulatedBalanceCents)
        assertEquals(20, response.limit)
        assertEquals(0, response.offset)
        assertEquals(false, response.hasMore)
    }

    @org.junit.jupiter.api.Test
    fun `omitted month defaults to current month and filter is forwarded`() {
        repository.role = GroupRole.ADMIN

        controller.list(identity, group.toString(), null, "OUT", 5, 2)

        assertEquals(YearMonth.of(2026, 8), repository.lastQuery?.month)
        assertEquals(FinanceStatementDirection.OUT, repository.lastQuery?.direction)
        assertEquals(5, repository.lastQuery?.limit)
        assertEquals(2, repository.lastQuery?.offset)
    }

    @org.junit.jupiter.api.Test
    fun `athlete is forbidden and nonmember is hidden`() {
        repository.role = GroupRole.ATHLETE
        assertFailsWith<AccessForbiddenException> { controller.list(identity, group.toString()) }

        repository.role = null
        assertFailsWith<GameNotFoundException> { controller.list(identity, group.toString()) }
    }

    @org.junit.jupiter.api.Test
    fun `invalid month direction pagination and group id are validation or hidden`() {
        repository.role = GroupRole.OWNER
        assertEquals(setOf("month"), assertFailsWith<InvalidGroupRequestException> {
            controller.list(identity, group.toString(), "2026-8")
        }.fieldErrors.keys)
        assertEquals(setOf("direction"), assertFailsWith<InvalidGroupRequestException> {
            controller.list(identity, group.toString(), direction = "SIDEWAYS")
        }.fieldErrors.keys)
        assertEquals(setOf("limit"), assertFailsWith<InvalidGroupRequestException> {
            controller.list(identity, group.toString(), limit = 0)
        }.fieldErrors.keys)
        assertEquals(setOf("offset"), assertFailsWith<InvalidGroupRequestException> {
            controller.list(identity, group.toString(), offset = -1)
        }.fieldErrors.keys)
        assertFailsWith<GameNotFoundException> { controller.list(identity, "bad") }
    }

    private inner class MemoryRepository : FinanceStatementRepository {
        var role: GroupRole? = GroupRole.OWNER
        var lastQuery: br.com.saqz.groups.application.finance.statement.FinanceStatementQuery? = null
        override fun role(actorId: UUID, groupId: UUID) = role
        override fun page(query: br.com.saqz.groups.application.finance.statement.FinanceStatementQuery): FinanceStatementPage {
            lastQuery = query
            return page
        }
    }

    private val page = FinanceStatementPage(
        month = YearMonth.of(2026, 8),
        items = listOf(
            FinanceStatementItem(UUID.randomUUID(), FinanceStatementType.CHARGE, FinanceStatementDirection.IN, "Mensalidade · Marina Freitas", "MONTHLY", "PIX", Instant.parse("2026-08-20T15:00:00Z"), 3000),
            FinanceStatementItem(UUID.randomUUID(), FinanceStatementType.EXPENSE, FinanceStatementDirection.OUT, "Aluguel", "VENUE", null, Instant.parse("2026-08-19T03:00:00Z"), -700),
        ),
        summary = FinanceStatementSummary(3000, 700, 2300, 1800),
        limit = 20,
        offset = 0,
        hasMore = false,
    )
}
