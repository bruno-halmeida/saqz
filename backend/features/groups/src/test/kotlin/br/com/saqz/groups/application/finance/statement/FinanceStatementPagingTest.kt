package br.com.saqz.groups.application.finance.statement

import br.com.saqz.groups.domain.GroupRole
import org.junit.jupiter.api.Test
import java.time.ZoneId
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

/** O extrato pagina de verdade: a página volta com o limite, o deslocamento e o "tem mais" que o repositório informou. */
class FinanceStatementPagingTest {
    private val actor = UUID.randomUUID()
    private val group = UUID.randomUUID()
    private var role: GroupRole? = GroupRole.OWNER
    private var queries = mutableListOf<FinanceStatementQuery>()
    private val service = FinanceStatementService(
        object : FinanceStatementRepository {
            override fun role(actorId: UUID, groupId: UUID) = role
            override fun timeZone(groupId: UUID): ZoneId = ZoneId.of("America/Sao_Paulo")
            override fun page(query: FinanceStatementQuery): FinanceStatementPage {
                queries += query
                return FinanceStatementPage(query.month, emptyList(), FinanceStatementSummary(10, 4, 6, 20), query.limit, query.offset, hasMore = query.offset < 40)
            }
        },
    ) { YearMonth.of(2026, 8) }

    @Test
    fun `a pagina devolvida traz limite, deslocamento e se ha mais`() {
        val more = assertIs<FinanceStatementResult.Success>(service.list(actor, group, null, null, 20, 20)).value
        val last = assertIs<FinanceStatementResult.Success>(service.list(actor, group, null, null, 20, 40)).value

        assertEquals(20, more.limit)
        assertEquals(20, more.offset)
        assertEquals(true, more.hasMore)
        assertEquals(false, last.hasMore)
        assertEquals(40, last.offset)
        assertEquals(listOf(10L, 4L, 6L, 20L), more.summary.let { listOf(it.totalInCents, it.totalOutCents, it.periodBalanceCents, it.accumulatedBalanceCents) })
    }

    @Test
    fun `limite tem de ser positivo e deslocamento nao pode ser negativo, sem consultar o repositorio`() {
        assertFailsWith<IllegalArgumentException> { service.list(actor, group, null, null, 0, 0) }
        assertFailsWith<IllegalArgumentException> { service.list(actor, group, null, null, -5, 0) }
        assertFailsWith<IllegalArgumentException> { service.list(actor, group, null, null, 20, -1) }

        assertEquals(emptyList(), queries)
    }

    @Test
    fun `limite 1 e deslocamento 0 sao validos e o mes pedido vence o mes corrente`() {
        assertIs<FinanceStatementResult.Success>(service.list(actor, group, YearMonth.of(2026, 3), FinanceStatementDirection.IN, 1, 0))

        assertEquals(FinanceStatementQuery(group, YearMonth.of(2026, 3), FinanceStatementDirection.IN, 1, 0), queries.single())
    }

    @Test
    fun `quem nao e membro nao consulta nada`() {
        role = null

        assertEquals(FinanceStatementResult.Hidden, service.list(actor, group, null, null, 20, 0))
        assertEquals(emptyList(), queries)
    }
}
