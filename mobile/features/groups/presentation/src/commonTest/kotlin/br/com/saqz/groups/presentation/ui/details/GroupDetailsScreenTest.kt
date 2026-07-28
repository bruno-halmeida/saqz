package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupDetailsScreenTest {

    // As seções que só existem no 2f e as que só existem no 2e. A tabela do desenho virou
    // teste: nenhuma da esquerda pode aparecer na visão da direita, e vice-versa.
    private val adminOnly = listOf(
        GroupDetailsTags.CreateNextGame,
        GroupDetailsTags.EditGroup,
        GroupDetailsTags.NotifyPending,
        GroupDetailsTags.Cashbox,
        GroupDetailsTags.ManageMembers,
        GroupDetailsTags.ManageSchedule,
        GroupDetailsTags.ManageInviteLink,
    )

    private val memberOnly = listOf(
        GroupDetailsTags.ConfirmAttendance,
        GroupDetailsTags.ViewGame,
        GroupDetailsTags.ShortcutNotices,
        GroupDetailsTags.ShortcutCashbox,
        GroupDetailsTags.ShortcutSchedule,
        GroupDetailsTags.ShortcutChat,
        GroupDetailsTags.Notice,
        GroupDetailsTags.ViewAllMembers,
        GroupDetailsTags.Invite,
        GroupDetailsTags.Leave,
    )

    @Test
    fun adminViewHasNoMemberSection() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.admin)

        adminOnly.forEach { onNodeWithTag(it).assertExists() }
        memberOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun memberViewHasNoAdminSection() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.member)

        memberOnly.forEach { onNodeWithTag(it).assertExists() }
        adminOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun venueActionFollowsTheView() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithText("Editar").performClick()

        assertEquals(GroupDetailsIntent.EditVenue, intents.single())
    }

    @Test
    fun memberVenueActionOpensTheMap() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithText("Ver no mapa").performClick()

        assertEquals(GroupDetailsIntent.OpenVenueMap, intents.single())
    }

    @Test
    fun loadingShowsNoSectionAtAll() = runComposeUiTest {
        setScreen(GroupDetailsState())

        (adminOnly + memberOnly + GroupDetailsTags.Venue).forEach {
            onAllNodesWithTag(it).assertCountEquals(0)
        }
    }

    private fun ComposeUiTest.setScreen(
        state: GroupDetailsState,
        onIntent: (GroupDetailsIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            GroupDetailsScreen(state = state, onBack = {}, onIntent = onIntent)
        }
    }
}
