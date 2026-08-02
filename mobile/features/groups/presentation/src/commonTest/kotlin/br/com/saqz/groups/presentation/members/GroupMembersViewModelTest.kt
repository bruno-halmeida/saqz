package br.com.saqz.groups.presentation.members

import br.com.saqz.domain.DataError
import br.com.saqz.domain.GroupId
import br.com.saqz.domain.SaqzResult
import br.com.saqz.groups.domain.athlete.AthleteError
import br.com.saqz.groups.domain.group.GroupRole
import br.com.saqz.groups.domain.membership.AssignableGroupRole
import br.com.saqz.groups.domain.membership.GroupMembership
import br.com.saqz.groups.domain.membership.GroupMembershipError
import br.com.saqz.groups.presentation.FakeAthleteGateway
import br.com.saqz.groups.presentation.FakeGroupMembershipGateway
import br.com.saqz.groups.presentation.GroupUiError
import br.com.saqz.groups.presentation.sampleRosterEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembersViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(dispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `success loads roster including unknown financial status`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry("admin"), sampleRosterEntry("athlete")),
            ),
        )
        val memberships = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(
                listOf(GroupMembership("admin", "Admin", GroupRole.ADMIN)),
            ),
        )
        val viewModel = GroupMembersViewModel("group-1", athlete, memberships)

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(2, viewModel.state.value.totalCount)
        assertEquals(listOf("admin"), viewModel.state.value.admins.map { it.id })
        assertEquals(listOf("athlete"), viewModel.state.value.members.map { it.id })
        assertEquals("Central · Mensalista", viewModel.state.value.members.single().meta)
        assertTrue(GroupMembersFilter.values().toList().none { it.name.contains("Financial", ignoreCase = true) })
    }

    @Test
    fun `signed in member is marked self and cannot open its action sheet`() = runTest {
        val athlete = FakeAthleteGateway(
            ownProfileResult = SaqzResult.Success(
                br.com.saqz.groups.domain.athlete.OwnAthleteProfile("me", "Me", null, emptyList()),
            ),
            rosterResult = SaqzResult.Success(listOf(sampleRosterEntry("me"))),
        )
        val viewModel = GroupMembersViewModel(
            "group-1",
            athlete,
            FakeGroupMembershipGateway(
                listResult = SaqzResult.Success(listOf(GroupMembership("me", "Me", GroupRole.ADMIN))),
            ),
        )

        val self = viewModel.state.value.admins.single()
        assertTrue(self.isSelf)

        viewModel.onIntent(GroupMembersIntent.OpenMember(self.id))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        assertEquals(null, viewModel.state.value.selected)
        assertEquals(null, athlete.lastRemovedUserId)
    }

    @Test
    fun `empty roster renders without sections`() = runTest {
        val viewModel = GroupMembersViewModel(
            "group-1",
            FakeAthleteGateway(),
            FakeGroupMembershipGateway(),
        )

        assertFalse(viewModel.state.value.isLoading)
        assertEquals(0, viewModel.state.value.totalCount)
        assertTrue(viewModel.state.value.admins.isEmpty())
        assertTrue(viewModel.state.value.members.isEmpty())
    }

    @Test
    fun `roster failure is visible and typed`() = runTest {
        val viewModel = GroupMembersViewModel(
            "group-1",
            FakeAthleteGateway(
                rosterResult = SaqzResult.Failure(AthleteError.DataFailure(DataError.Forbidden)),
            ),
            FakeGroupMembershipGateway(),
        )

        assertTrue(viewModel.state.value.loadFailed)
        assertEquals(GroupUiError.AccessDenied, viewModel.state.value.error)
    }

    @Test
    fun `forbidden membership lookup does not hide a readable roster`() = runTest {
        val viewModel = GroupMembersViewModel(
            "group-1",
            FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry()))),
            FakeGroupMembershipGateway(
                listResult = SaqzResult.Failure(GroupMembershipError.DataFailure(DataError.Forbidden)),
            ),
        )

        assertFalse(viewModel.state.value.loadFailed)
        assertEquals(listOf("member-1"), viewModel.state.value.members.map { it.id })
    }

    @Test
    fun `promote calls membership gateway and closes the sheet`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val membership = FakeGroupMembershipGateway()
        val viewModel = GroupMembersViewModel("group-1", athlete, membership)

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Promote))

        assertEquals(AssignableGroupRole.ADMIN, membership.lastRoleCommand?.role)
        assertEquals("member-1", membership.lastRoleCommand?.userId)
        assertEquals(null, viewModel.state.value.selected)
    }

    @Test
    fun `remove calls athlete gateway`() = runTest {
        val athlete = FakeAthleteGateway(rosterResult = SaqzResult.Success(listOf(sampleRosterEntry())))
        val viewModel = GroupMembersViewModel("group-1", athlete, FakeGroupMembershipGateway())

        viewModel.onIntent(GroupMembersIntent.OpenMember("member-1"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        assertEquals("member-1", athlete.lastRemovedUserId)
    }

    @Test
    fun `filter and search are applied to loaded roster`() = runTest {
        val athlete = FakeAthleteGateway(
            rosterResult = SaqzResult.Success(
                listOf(sampleRosterEntry("one"), sampleRosterEntry("two")),
            ),
        )
        val memberships = FakeGroupMembershipGateway(
            listResult = SaqzResult.Success(listOf(GroupMembership("one", "One", GroupRole.ADMIN))),
        )
        val viewModel = GroupMembersViewModel("group-1", athlete, memberships)

        viewModel.onIntent(GroupMembersIntent.SelectFilter(GroupMembersFilter.Admins))

        assertEquals(listOf("one"), viewModel.state.value.admins.map { it.id })
        assertTrue(viewModel.state.value.members.isEmpty())
    }
}
