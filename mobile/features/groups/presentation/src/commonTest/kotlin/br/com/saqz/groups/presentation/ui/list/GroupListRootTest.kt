package br.com.saqz.groups.presentation.ui.list

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import br.com.saqz.groups.presentation.FakeGroupGateway
import br.com.saqz.groups.presentation.list.GroupListViewModel
import br.com.saqz.groups.presentation.sampleVersionedGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class GroupListRootTest {
    @Test
    fun `refresh version reloads the retained list after creating a group`() = runComposeUiTest {
        val athlete = FakeAthleteGateway()
        val viewModel = GroupListViewModel(
            athlete,
            FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())),
            noPlan,
        )
        var refreshVersion by mutableIntStateOf(0)

        setContent {
            SaqzTheme {
                GroupListRoot(
                    onOpenGroup = {},
                    onCreateGroup = {},
                    onOpenPlans = {},
                    refreshVersion = refreshVersion,
                    viewModel = viewModel,
                )
            }
        }
        waitForIdle()
        assertTrue(viewModel.state.value.isEmpty)
        assertEquals(1, athlete.ownProfileCalls)

        athlete.ownProfileResult = SaqzResult.Success(membershipProfile())
        runOnIdle { refreshVersion = 1 }
        waitForIdle()

        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })
        assertEquals(2, athlete.ownProfileCalls)
    }

    @Test
    fun `a fresh view model does not reload on an already bumped refresh version`() = runComposeUiTest {
        val athlete = FakeAthleteGateway()
        val viewModel = GroupListViewModel(
            athlete,
            FakeGroupGateway(),
            noPlan,
        )

        setContent {
            SaqzTheme {
                GroupListRoot(
                    onOpenGroup = {},
                    onCreateGroup = {},
                    onOpenPlans = {},
                    refreshVersion = 3,
                    viewModel = viewModel,
                )
            }
        }
        waitForIdle()

        assertEquals(1, athlete.ownProfileCalls)
    }

    @Test
    fun `returning to a stacked list reloads once for the bump it missed`() = runComposeUiTest {
        val athlete = FakeAthleteGateway()
        val viewModel = GroupListViewModel(
            athlete,
            FakeGroupGateway(readResult = SaqzResult.Success(sampleVersionedGroup())),
            noPlan,
        )
        var refreshVersion by mutableIntStateOf(0)
        var onScreen by mutableStateOf(true)

        setContent {
            val stateHolder = rememberSaveableStateHolder()
            SaqzTheme {
                if (onScreen) {
                    stateHolder.SaveableStateProvider("groups-list") {
                        GroupListRoot(
                            onOpenGroup = {},
                            onCreateGroup = {},
                            onOpenPlans = {},
                            refreshVersion = refreshVersion,
                            viewModel = viewModel,
                        )
                    }
                }
            }
        }
        waitForIdle()
        assertEquals(1, athlete.ownProfileCalls)

        runOnIdle { onScreen = false }
        waitForIdle()
        athlete.ownProfileResult = SaqzResult.Success(membershipProfile())
        runOnIdle { refreshVersion = 1 }
        waitForIdle()
        runOnIdle { onScreen = true }
        waitForIdle()

        assertEquals(2, athlete.ownProfileCalls)
        assertEquals(listOf("group-1"), viewModel.state.value.groups.map { it.id })
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
