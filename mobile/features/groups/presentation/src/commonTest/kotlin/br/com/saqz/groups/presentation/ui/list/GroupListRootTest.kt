package br.com.saqz.groups.presentation.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.athlete.OwnAthleteMembership
import br.com.saqz.groups.domain.athlete.OwnAthleteProfile
import br.com.saqz.groups.domain.group.GroupCreationEntitlement
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGameGateway
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.list.GroupListViewModel
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupListRootTest {
    @Test
    fun `the first appearance does not reload on top of the initial load`() = runComposeUiTest {
        val athlete = FakeAthleteGateway()
        val viewModel = GroupListViewModel(athlete, FakeGroupGateway(), noPlan, gameGateway = FakeGameGateway())

        setContent {
            SaqzTheme {
                GroupListRoot(onOpenGroup = {}, onCreateGroup = {}, onOpenPlans = {}, viewModel = viewModel)
            }
        }
        waitForIdle()

        assertEquals(1, athlete.ownProfileCalls)
    }

    @Test
    fun `coming back to the list reloads it while the old groups stay on screen`() = runComposeUiTest {
        val athlete = FakeAthleteGateway()
        val viewModel = GroupListViewModel(
            athlete,
            FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())),
            noPlan,
            gameGateway = FakeGameGateway(),
        )
        var onScreen by mutableStateOf(true)

        setContent {
            val stateHolder = rememberSaveableStateHolder()
            SaqzTheme {
                if (onScreen) {
                    stateHolder.SaveableStateProvider("groups-list") {
                        GroupListRoot(onOpenGroup = {}, onCreateGroup = {}, onOpenPlans = {}, viewModel = viewModel)
                    }
                }
            }
        }
        waitForIdle()
        assertTrue(viewModel.state.value.isEmpty)
        assertEquals(1, athlete.ownProfileCalls)

        // Saiu para criar o grupo (2a por cima do shell) e voltou: nenhum contador foi tocado.
        runOnIdle { onScreen = false }
        waitForIdle()
        athlete.ownProfileResult = SaqzResult.Success(membershipProfile())
        runOnIdle { onScreen = true }
        waitForIdle()

        assertEquals(2, athlete.ownProfileCalls)
        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })
        assertEquals(false, viewModel.state.value.isLoading)
    }

    @Test
    fun `every return reloads and not only the first one`() = runComposeUiTest {
        val athlete = FakeAthleteGateway()
        val viewModel = GroupListViewModel(athlete, FakeGroupGateway(), noPlan, gameGateway = FakeGameGateway())
        var onScreen by mutableStateOf(true)

        setContent {
            SaqzTheme {
                if (onScreen) {
                    GroupListRoot(onOpenGroup = {}, onCreateGroup = {}, onOpenPlans = {}, viewModel = viewModel)
                }
            }
        }
        waitForIdle()
        repeat(2) {
            runOnIdle { onScreen = false }
            waitForIdle()
            runOnIdle { onScreen = true }
            waitForIdle()
        }

        assertEquals(3, athlete.ownProfileCalls)
    }

    private fun membershipProfile() = OwnAthleteProfile(
        userId = "me",
        displayName = "Bruno",
        phone = null,
        memberships = listOf(
            OwnAthleteMembership(
                groupId = GroupId("group-1"),
                groupName = "Vôlei do CERET",
                role = GroupRole.ADMIN,
                position = null,
                membershipType = AthleteMembershipType.MENSALISTA,
                active = true,
            ),
        ),
    )

    private companion object {
        val noPlan = GroupCreationEntitlement { false }
    }
}
