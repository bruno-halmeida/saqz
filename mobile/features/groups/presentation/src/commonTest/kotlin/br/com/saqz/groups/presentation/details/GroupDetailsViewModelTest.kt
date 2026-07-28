package br.com.saqz.groups.presentation.details

import br.com.saqz.groups.presentation.ui.details.GroupDetailsPreviewData
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

private const val GROUP_ID = "grp-1"

@OptIn(ExperimentalCoroutinesApi::class)
class GroupDetailsViewModelTest {

    @BeforeTest
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @AfterTest
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun adminIntentsEmitTheirExits() = runTest {
        assertEffect(GroupDetailsIntent.CreateNextGame, GroupDetailsEffect.OpenCreateGame(GROUP_ID))
        assertEffect(GroupDetailsIntent.EditGroup, GroupDetailsEffect.OpenEdit(GROUP_ID))
        assertEffect(GroupDetailsIntent.EditVenue, GroupDetailsEffect.OpenEdit(GROUP_ID))
        assertEffect(GroupDetailsIntent.ManageMembers, GroupDetailsEffect.OpenMembers(GROUP_ID))
        assertEffect(GroupDetailsIntent.ManageSchedule, GroupDetailsEffect.OpenSchedule(GROUP_ID))
        assertEffect(GroupDetailsIntent.InviteByLink, GroupDetailsEffect.OpenInviteLink(GROUP_ID))
    }

    @Test
    fun memberIntentsEmitTheirExits() = runTest {
        assertEffect(GroupDetailsIntent.ViewAllMembers, GroupDetailsEffect.OpenMembers(GROUP_ID))
        assertEffect(GroupDetailsIntent.OpenSchedule, GroupDetailsEffect.OpenSchedule(GROUP_ID))
        assertEffect(GroupDetailsIntent.Invite, GroupDetailsEffect.OpenInviteLink(GROUP_ID))
        assertEffect(GroupDetailsIntent.OpenVenueMap, GroupDetailsEffect.OpenMap)
        assertEffect(GroupDetailsIntent.Leave, GroupDetailsEffect.Left)
    }

    /** Fluxos 3, 4 e 5 não existem, e a tela emite a saída deles do mesmo jeito. */
    @Test
    fun effectsForFlowsThatDoNotExistYetAreStillEmitted() = runTest {
        assertEffect(GroupDetailsIntent.CreateNextGame, GroupDetailsEffect.OpenCreateGame(GROUP_ID))
        assertEffect(GroupDetailsIntent.OpenCashbox, GroupDetailsEffect.OpenCashbox(GROUP_ID))
        assertEffect(GroupDetailsIntent.InviteByLink, GroupDetailsEffect.OpenInviteLink(GROUP_ID))
    }

    /**
     * Toque que o export desenha sem destino nenhum não inventa efeito nem mexe no estado:
     * o primeiro efeito do canal continua sendo o do intent seguinte.
     */
    @Test
    fun intentsWithoutADestinationStaySilent() = runTest {
        val state = GroupDetailsPreviewData.member
        val viewModel = GroupDetailsViewModel(GROUP_ID, state)

        listOf(
            GroupDetailsIntent.ConfirmAttendance,
            GroupDetailsIntent.ViewGame,
            GroupDetailsIntent.NotifyPending,
            GroupDetailsIntent.OpenNotices,
            GroupDetailsIntent.OpenChat,
        ).forEach(viewModel::onIntent)
        viewModel.onIntent(GroupDetailsIntent.Leave)

        assertEquals(GroupDetailsEffect.Left, viewModel.effects.first())
        assertEquals(state, viewModel.state.value)
    }

    private suspend fun assertEffect(intent: GroupDetailsIntent, expected: GroupDetailsEffect) {
        val viewModel = GroupDetailsViewModel(GROUP_ID, GroupDetailsPreviewData.member)
        viewModel.onIntent(intent)
        assertEquals(expected, viewModel.effects.first())
    }
}
