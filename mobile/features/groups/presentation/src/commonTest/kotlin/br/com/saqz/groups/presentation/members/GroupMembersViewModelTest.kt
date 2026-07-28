package br.com.saqz.groups.presentation.members

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupMembersViewModelTest {
    private val mainDispatcher = UnconfinedTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `sheet of a common member offers editing and promotion`() {
        val actions = member("thiago", admin = false).sheetActions()

        assertEquals(
            listOf(GroupMemberAction.EditMember, GroupMemberAction.Promote, GroupMemberAction.Remove),
            actions,
        )
    }

    @Test
    fun `sheet of an admin offers the profile and the demotion`() {
        val actions = member("bia", admin = true).sheetActions()

        assertEquals(
            listOf(GroupMemberAction.ViewProfile, GroupMemberAction.Demote, GroupMemberAction.Remove),
            actions,
        )
    }

    @Test
    fun `counting stays whole while the sections follow the filter`() {
        val viewModel = viewModel()

        val all = viewModel.state.value
        assertEquals(4, all.totalCount)
        assertEquals(2, all.adminCount)
        assertEquals(2, all.pendingCount)
        assertEquals(listOf("bia", "bia2"), all.admins.map { it.id })
        assertEquals(listOf("thiago", "camila"), all.members.map { it.id })
        assertEquals(2, all.shownCount)
        assertEquals(2, all.memberCount)
    }

    @Test
    fun `the admins filter hides members and join requests`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.SelectFilter(GroupMembersFilter.Admins))

        val state = viewModel.state.value
        assertEquals(listOf("bia", "bia2"), state.admins.map { it.id })
        assertTrue(state.members.isEmpty())
        assertTrue(state.joinRequests.isEmpty())
        // As pílulas seguem contando o grupo inteiro, não o recorte.
        assertEquals(4, state.totalCount)
        assertEquals(2, state.pendingCount)
    }

    @Test
    fun `the pending filter leaves only the join requests`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.SelectFilter(GroupMembersFilter.Pending))

        val state = viewModel.state.value
        assertEquals(listOf("julia", "rafael"), state.joinRequests.map { it.id })
        assertTrue(state.admins.isEmpty())
        assertTrue(state.members.isEmpty())
    }

    @Test
    fun `the all filter brings the three sections back`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.SelectFilter(GroupMembersFilter.Pending))
        viewModel.onIntent(GroupMembersIntent.SelectFilter(GroupMembersFilter.All))

        val state = viewModel.state.value
        assertEquals(2, state.admins.size)
        assertEquals(2, state.members.size)
        assertEquals(2, state.joinRequests.size)
    }

    @Test
    fun `search narrows every section and lives in the state`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.UpdateQuery("ca"))

        val state = viewModel.state.value
        assertEquals("ca", state.query)
        assertEquals(listOf("camila"), state.members.map { it.id })
        assertTrue(state.admins.isEmpty())
        assertEquals(1, state.shownCount)
        // Sem ninguém chamado "ca" entre os pedidos, a seção some — mas a pílula não.
        assertTrue(state.joinRequests.isEmpty())
        assertEquals(2, state.pendingCount)
    }

    @Test
    fun `the own row never opens the sheet`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.OpenMember("bia"))

        assertNull(viewModel.state.value.selected)
    }

    @Test
    fun `touching someone else opens the sheet on that person`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.OpenMember("thiago"))

        assertEquals("thiago", viewModel.state.value.selected?.id)
    }

    @Test
    fun `promoting moves the row to the admin section and closes the sheet`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.OpenMember("thiago"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Promote))

        val state = viewModel.state.value
        assertEquals(listOf("bia", "bia2", "thiago"), state.admins.map { it.id })
        assertEquals(listOf("camila"), state.members.map { it.id })
        assertEquals(3, state.adminCount)
        assertEquals(4, state.totalCount)
        assertNull(state.selected)
    }

    @Test
    fun `demoting sends the admin back to the member section`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.OpenMember("bia2"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Demote))

        val state = viewModel.state.value
        assertEquals(listOf("bia"), state.admins.map { it.id })
        assertEquals(listOf("bia2", "thiago", "camila"), state.members.map { it.id })
        assertEquals(1, state.adminCount)
    }

    @Test
    fun `removing takes the person out of the group`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.OpenMember("camila"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        val state = viewModel.state.value
        assertEquals(listOf("thiago"), state.members.map { it.id })
        assertEquals(3, state.totalCount)
        assertNull(state.selected)
    }

    @Test
    fun `an action without anyone selected does nothing`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.Remove))

        assertEquals(4, viewModel.state.value.totalCount)
    }

    @Test
    fun `accepting a request turns the person into a common member`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.AcceptRequest("julia"))

        val state = viewModel.state.value
        assertEquals(listOf("rafael"), state.joinRequests.map { it.id })
        assertEquals(1, state.pendingCount)
        assertEquals(listOf("thiago", "camila", "julia"), state.members.map { it.id })
        assertEquals(5, state.totalCount)
    }

    @Test
    fun `declining a request drops it without adding a member`() {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.DeclineRequest("rafael"))

        val state = viewModel.state.value
        assertEquals(listOf("julia"), state.joinRequests.map { it.id })
        assertEquals(2, state.members.size)
        assertEquals(4, state.totalCount)
    }

    @Test
    fun `viewing the profile emits the effect and closes the sheet`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.OpenMember("bia2"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.ViewProfile))

        assertEquals(GroupMembersEffect.OpenMemberProfile("bia2"), viewModel.effects.first())
        assertNull(viewModel.state.value.selected)
    }

    @Test
    fun `editing a player emits the effect that leads to flow 3`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.OpenMember("thiago"))
        viewModel.onIntent(GroupMembersIntent.PerformAction(GroupMemberAction.EditMember))

        assertEquals(GroupMembersEffect.OpenMemberEditor("thiago"), viewModel.effects.first())
    }

    @Test
    fun `inviting emits the effect carrying the group`() = runTest(mainDispatcher) {
        val viewModel = viewModel()

        viewModel.onIntent(GroupMembersIntent.Invite)

        assertEquals(GroupMembersEffect.OpenInvite("group-1"), viewModel.effects.first())
    }

    private fun viewModel() = GroupMembersViewModel(
        groupId = "group-1",
        initialState = GroupMembersState(
            isLoading = false,
            joinRequests = listOf(
                JoinRequestUi("julia", "Julia Martins", "Entrou pelo código · há 2h", awaitingReview = false),
                JoinRequestUi("rafael", "Rafael Costa", "Entrou pelo link · ontem", awaitingReview = true),
            ),
            // "bia" é quem está usando o app; "bia2" é o outro admin, o do 2l.
            admins = listOf(
                member("bia", admin = true, self = true),
                member("bia2", admin = true),
            ),
            members = listOf(member("thiago"), member("camila")),
        ),
    )

    private fun member(id: String, admin: Boolean = false, self: Boolean = false) = MemberUi(
        id = id,
        name = id.replaceFirstChar { it.uppercase() },
        meta = "Central · mensalista",
        isAdmin = admin,
        isSelf = self,
        stats = "18 jogos · 92% de presença",
    )
}
