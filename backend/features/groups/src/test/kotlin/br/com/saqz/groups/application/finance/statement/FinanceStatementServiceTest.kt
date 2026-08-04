package br.com.saqz.groups.application.finance.statement

import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertIs

class FinanceStatementServiceTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private val page = FinanceStatementPage(
        month = YearMonth.of(2026, 8),
        items = listOf(
            FinanceStatementItem(
                id = UUID.randomUUID(),
                type = FinanceStatementType.CHARGE,
                direction = FinanceStatementDirection.IN,
                title = "Mensalidade · Marina Freitas",
                category = "MONTHLY",
                paidMethod = "PIX",
                occurredAt = Instant.parse("2026-08-20T15:00:00Z"),
                amountCents = 3000,
            ),
        ),
        summary = FinanceStatementSummary(3000, 0, 3000, 3000),
        limit = 20,
        offset = 0,
        hasMore = false,
    )

    @Test
    fun `owner uses current month by default and forwards direction pagination`() {
        val repository = MemoryRepository(GroupRole.OWNER, page)
        val service = FinanceStatementService(repository) { YearMonth.of(2026, 8) }

        val result = assertIs<FinanceStatementResult.Success>(
            service.list(actor, group, null, FinanceStatementDirection.OUT, 7, 14),
        )

        assertEquals(page, result.value)
        assertEquals(FinanceStatementQuery(group, YearMonth.of(2026, 8), FinanceStatementDirection.OUT, 7, 14), repository.lastQuery)
    }

    @Test
    fun `admin has the same statement visibility as owner`() {
        val repository = MemoryRepository(GroupRole.ADMIN, page)
        val result = FinanceStatementService(repository) { YearMonth.of(2026, 8) }
            .list(actor, group, YearMonth.of(2026, 7), null, 20, 0)

        assertIs<FinanceStatementResult.Success>(result)
        assertEquals(YearMonth.of(2026, 7), repository.lastQuery?.month)
    }

    @Test
    fun `athlete is forbidden and nonmember is hidden`() {
        val athlete = FinanceStatementService(MemoryRepository(GroupRole.ATHLETE, page))
        val missing = FinanceStatementService(MemoryRepository(null, page))

        assertIs<FinanceStatementResult.Forbidden>(athlete.list(actor, group, null, null, 20, 0))
        assertIs<FinanceStatementResult.Hidden>(missing.list(actor, group, null, null, 20, 0))
    }

    private class MemoryRepository(
        private val configuredRole: GroupRole?,
        private val configuredPage: FinanceStatementPage,
    ) : FinanceStatementRepository {
        var lastQuery: FinanceStatementQuery? = null

        override fun role(actorId: UUID, groupId: UUID) = configuredRole

        override fun page(query: FinanceStatementQuery): FinanceStatementPage {
            lastQuery = query
            return configuredPage
        }
    }
}
