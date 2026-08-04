package br.com.saqz.groups.presentation.statement

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceDirection
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceStatementGateway
import br.com.saqz.groups.domain.finance.FinanceStatementItem
import br.com.saqz.groups.domain.finance.FinanceStatementPage
import br.com.saqz.groups.domain.finance.FinanceStatementQuery
import br.com.saqz.groups.domain.finance.FinanceStatementSummary
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class StatementRootTest {
    @Test
    fun `refresh version reloads statement after returning from new entry`() = runComposeUiTest {
        val gateway = CountingStatementGateway()
        val viewModel = StatementViewModel("group-1", gateway)
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            SaqzTheme {
                StatementRoot(
                    groupId = "group-1",
                    onBack = {},
                    onEffect = {},
                    viewModel = viewModel,
                    refreshVersion = refreshVersion,
                )
            }
        }
        waitForIdle()
        assertEquals(1, gateway.queries.size)

        runOnIdle { refreshVersion = 1 }
        waitForIdle()

        assertEquals(2, gateway.queries.size)
    }
}

private class CountingStatementGateway : FinanceStatementGateway {
    val queries = mutableListOf<FinanceStatementQuery>()

    override suspend fun statement(
        groupId: GroupId,
        query: FinanceStatementQuery,
    ): SaqzResult<FinanceStatementPage, FinanceError> {
        queries += query
        return SaqzResult.Success(
            FinanceStatementPage(
                month = "2026-08",
                items = listOf(
                    FinanceStatementItem(
                        id = "entry-${queries.size}",
                        type = "EXPENSE",
                        direction = FinanceDirection.Out,
                        title = "Aluguel da quadra",
                        category = "VENUE",
                        paidMethod = null,
                        occurredAt = "2026-08-04T10:00:00Z",
                        amountCents = -8_000L,
                    ),
                ),
                summary = FinanceStatementSummary(0L, 8_000L, -8_000L, -8_000L),
                limit = 20,
                offset = query.offset,
                hasMore = false,
            ),
        )
    }
}
