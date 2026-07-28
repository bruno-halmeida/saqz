package br.com.saqz.groups.presentation.list

import br.com.saqz.groups.model.GroupModality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class GroupListViewModelTest {
    private val mainDispatcher = StandardTestDispatcher()

    @BeforeTest fun setUp() = Dispatchers.setMain(mainDispatcher)

    @AfterTest fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `opening a group emits the group effect with its id`() = runTest(mainDispatcher) {
        val viewModel = GroupListViewModel(loadedState())

        viewModel.onIntent(GroupListIntent.OpenGroup("ibira"))

        assertEquals(GroupListEffect.OpenGroup("ibira"), viewModel.effects.first())
    }

    @Test
    fun `creating a group opens the plans flow and never the form`() = runTest(mainDispatcher) {
        val viewModel = GroupListViewModel(loadedState())

        viewModel.onIntent(GroupListIntent.CreateGroup)

        assertEquals(GroupListEffect.OpenPlans, viewModel.effects.first())
    }

    @Test
    fun `joining with a code emits the join effect`() = runTest(mainDispatcher) {
        val viewModel = GroupListViewModel(GroupListState(isLoading = false))

        viewModel.onIntent(GroupListIntent.JoinWithCode)

        assertEquals(GroupListEffect.OpenJoinWithCode, viewModel.effects.first())
    }

    @Test
    fun `accepting the invite drops the card right away`() = runTest(mainDispatcher) {
        val viewModel = GroupListViewModel(loadedState())

        viewModel.onIntent(GroupListIntent.AcceptInvite("convite-firma"))

        assertNull(viewModel.state.value.invite)
    }

    @Test
    fun `declining the invite drops the card right away`() = runTest(mainDispatcher) {
        val viewModel = GroupListViewModel(loadedState())

        viewModel.onIntent(GroupListIntent.DeclineInvite("convite-firma"))

        assertNull(viewModel.state.value.invite)
    }

    @Test
    fun `answering an invite that is not on screen leaves the state untouched`() = runTest(mainDispatcher) {
        val viewModel = GroupListViewModel(loadedState())

        viewModel.onIntent(GroupListIntent.AcceptInvite("outro-convite"))

        assertNotNull(viewModel.state.value.invite)
    }

    @Test
    fun `retry goes back to loading and clears the failure`() = runTest(mainDispatcher) {
        val viewModel = GroupListViewModel(GroupListState(isLoading = false, loadFailed = true))

        viewModel.onIntent(GroupListIntent.Retry)

        assertTrue(viewModel.state.value.isLoading)
        assertFalse(viewModel.state.value.loadFailed)
    }

    @Test
    fun `awaiting confirmation counts only the groups whose next game is pending`() {
        val state = loadedState()

        assertEquals(3, state.groups.size)
        assertEquals(1, state.awaitingConfirmation)
    }

    @Test
    fun `a list with no group and no invite is the first-access screen`() {
        val state = GroupListState(isLoading = false)

        assertTrue(state.isEmpty)
        assertEquals(0, state.awaitingConfirmation)
    }

    @Test
    fun `a pending invite alone is not the first-access screen`() {
        val state = GroupListState(isLoading = false, invite = invite)

        assertFalse(state.isEmpty)
    }

    @Test
    fun `loading and failure are never the first-access screen`() {
        assertFalse(GroupListState().isEmpty)
        assertFalse(GroupListState(isLoading = false, loadFailed = true).isEmpty)
    }

    private val invite = GroupInviteUi(
        id = "convite-firma",
        groupName = "Vôlei da firma",
        invitedBy = "Marina Freitas",
    )

    private fun loadedState() = GroupListState(
        isLoading = false,
        groups = listOf(
            group(id = "ceret", attendance = GroupCardAttendance.Pending),
            group(id = "ibira", attendance = GroupCardAttendance.Going),
            group(id = "vila", attendance = null),
        ),
        invite = invite,
    )

    private fun group(id: String, attendance: GroupCardAttendance?) = GroupCardUi(
        id = id,
        name = "Grupo $id",
        meta = "Quadra · Tatuapé · 26 membros",
        modality = GroupModality.COURT_VOLLEYBALL,
        isAdmin = id == "ceret",
        nextGame = attendance?.let { GroupCardGameUi(label = "Ter, 28/07 · 19h30", attendance = it) },
    )
}
