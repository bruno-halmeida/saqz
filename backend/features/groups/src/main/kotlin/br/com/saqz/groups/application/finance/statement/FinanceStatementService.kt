package br.com.saqz.groups.application.finance.statement

import br.com.saqz.groups.domain.GroupRole
import java.time.ZoneId
import java.time.YearMonth
import java.util.UUID

sealed interface FinanceStatementResult {
    data class Success(val value: FinanceStatementPage) : FinanceStatementResult
    data object Hidden : FinanceStatementResult
    data object Forbidden : FinanceStatementResult
}

class FinanceStatementService(
    private val repository: FinanceStatementRepository,
    private val currentMonth: (ZoneId) -> YearMonth = { zone -> YearMonth.now(zone) },
) {
    fun list(
        actorId: UUID,
        groupId: UUID,
        month: YearMonth?,
        direction: FinanceStatementDirection?,
        limit: Int,
        offset: Int,
    ): FinanceStatementResult {
        require(limit > 0) { "limit must be positive" }
        require(offset >= 0) { "offset must not be negative" }
        return when (repository.role(actorId, groupId)) {
            null -> FinanceStatementResult.Hidden
            GroupRole.ATHLETE -> FinanceStatementResult.Forbidden
            GroupRole.OWNER,
            GroupRole.ADMIN,
            -> FinanceStatementResult.Success(
                repository.page(
                    FinanceStatementQuery(
                        groupId = groupId,
                        month = month ?: currentMonth(repository.timeZone(groupId)),
                        direction = direction,
                        limit = limit,
                        offset = offset,
                    ),
                ),
            )
        }
    }
}
