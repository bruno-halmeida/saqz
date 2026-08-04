package br.com.saqz.groups.application.finance.statement

import br.com.saqz.groups.domain.GroupRole
import java.time.Instant
import java.time.YearMonth
import java.util.UUID

enum class FinanceStatementType { CHARGE, EXPENSE }
enum class FinanceStatementDirection { IN, OUT }

data class FinanceStatementItem(
    val id: UUID,
    val type: FinanceStatementType,
    val direction: FinanceStatementDirection,
    val title: String,
    val category: String,
    val paidMethod: String?,
    val occurredAt: Instant,
    val amountCents: Long,
)

data class FinanceStatementSummary(
    val totalInCents: Long,
    val totalOutCents: Long,
    val periodBalanceCents: Long,
    val accumulatedBalanceCents: Long,
)

data class FinanceStatementPage(
    val month: YearMonth,
    val items: List<FinanceStatementItem>,
    val summary: FinanceStatementSummary,
    val limit: Int,
    val offset: Int,
    val hasMore: Boolean,
)

data class FinanceStatementQuery(
    val groupId: UUID,
    val month: YearMonth,
    val direction: FinanceStatementDirection?,
    val limit: Int,
    val offset: Int,
)

interface FinanceStatementRepository {
    fun role(actorId: UUID, groupId: UUID): GroupRole?
    fun page(query: FinanceStatementQuery): FinanceStatementPage
}
