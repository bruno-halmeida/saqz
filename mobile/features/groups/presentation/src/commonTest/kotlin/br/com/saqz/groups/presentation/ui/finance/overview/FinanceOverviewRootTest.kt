package br.com.saqz.groups.presentation.ui.finance.overview

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.finance.FinanceError
import br.com.saqz.groups.domain.finance.FinanceOverview
import br.com.saqz.groups.domain.finance.FinanceOverviewGateway
import br.com.saqz.groups.domain.finance.FinanceOverviewGroup
import br.com.saqz.groups.domain.finance.FinanceOverviewPeriod
import br.com.saqz.groups.domain.finance.FinanceOverviewQuery
import br.com.saqz.groups.domain.finance.FinanceOverviewTotals
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewPeriodSelection
import br.com.saqz.groups.presentation.finance.overview.FinanceOverviewViewModel
import br.com.saqz.groups.port.GroupNowPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Instant

@OptIn(ExperimentalTestApi::class)
class FinanceOverviewRootTest {
    @Test
    fun `reentering finance tab reloads the current month after rollover`() = runComposeUiTest {
        var currentNow = Instant.parse("2026-07-31T12:00:00Z")
        val gateway = RecordingFinanceOverviewGateway()
        val viewModel = FinanceOverviewViewModel(
            gateway = gateway,
            nowPort = GroupNowPort { currentNow },
        )
        var financeActive by mutableStateOf(true)

        setContent {
            SaqzTheme {
                if (financeActive) {
                    FinanceOverviewRoot(
                        onOpenGroup = {},
                        viewModel = viewModel,
                    )
                }
            }
        }
        waitForIdle()
        gateway.queries.clear()

        currentNow = Instant.parse("2026-08-01T12:00:00Z")
        runOnIdle { financeActive = false }
        waitForIdle()
        runOnIdle { financeActive = true }
        waitForIdle()

        assertEquals(listOf(FinanceOverviewQuery(month = "2026-08")), gateway.queries)
        assertEquals(
            FinanceOverviewQuery(month = "2026-08"),
            viewModel.state.value.periods.first {
                it.selection == FinanceOverviewPeriodSelection.CurrentMonth
            }.query,
        )
    }

    private class RecordingFinanceOverviewGateway : FinanceOverviewGateway {
        val queries = mutableListOf<FinanceOverviewQuery>()

        override suspend fun overview(query: FinanceOverviewQuery): SaqzResult<FinanceOverview, FinanceError> {
            queries += query
            return SaqzResult.Success(
                FinanceOverview(
                    period = FinanceOverviewPeriod(month = query.month, year = query.year),
                    totals = FinanceOverviewTotals(0, 0, 0, 0),
                    groups = emptyList<FinanceOverviewGroup>(),
                    recentTransactions = emptyList(),
                ),
            )
        }
    }
}
