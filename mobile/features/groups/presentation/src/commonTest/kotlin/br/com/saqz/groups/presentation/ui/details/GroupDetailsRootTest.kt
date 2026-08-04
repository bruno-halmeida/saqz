package br.com.saqz.groups.presentation.ui.details

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeAttendanceGateway
import br.com.saqz.groups.presentation.FakeFinanceStatementGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.FakeOrganizerFinanceGateway
import br.com.saqz.groups.presentation.details.GroupDetailsViewModel
import br.com.saqz.groups.presentation.sampleGame
import br.com.saqz.groups.port.GroupNowPort
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

@OptIn(ExperimentalTestApi::class)
class GroupDetailsRootTest {
    @Test
    fun `refresh version retries the details view model`() = runComposeUiTest {
        val gameGateway = FakeGameGateway()
        val viewModel = GroupDetailsViewModel(
            groupId = "group-1",
            groupGateway = FakeGroupGateway(),
            gameGateway = gameGateway,
            attendanceGateway = FakeAttendanceGateway(),
            athleteGateway = FakeAthleteGateway(),
            statementGateway = FakeFinanceStatementGateway(),
            organizerFinanceGateway = FakeOrganizerFinanceGateway(),
            now = GroupNowPort { kotlin.time.Instant.parse("2026-08-01T00:00:00Z") },
        )
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            SaqzTheme {
                GroupDetailsRoot(
                    groupId = "group-1",
                    onBack = {},
                    onEffect = {},
                    viewModel = viewModel,
                    refreshVersion = refreshVersion,
                )
            }
        }
        waitForIdle()
        assertNull(viewModel.state.value.nextGame)

        gameGateway.listResult = SaqzResult.Success(listOf(sampleGame()))
        runOnIdle { refreshVersion = 1 }
        waitForIdle()

        assertEquals("game-1", viewModel.state.value.nextGame?.gameId)
    }
}
