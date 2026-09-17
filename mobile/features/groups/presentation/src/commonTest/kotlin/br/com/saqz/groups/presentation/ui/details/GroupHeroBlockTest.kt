package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.domain.athlete.AthleteMembershipType
import br.com.saqz.groups.domain.attendance.AttendanceIntent
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupHeroBlockTest {
    // O seletor de presença não é de nenhuma das duas visões em particular: é de quem joga —
    // e dono e admin também jogam.
    @Test
    fun adminWithNextGameStillGetsTheAttendanceSelector() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin)

        onNodeWithTag(GroupGameResponseTags.Section).assertExists()
        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertExists()
        onNodeWithTag(GroupDetailsTags.ViewGame).assertExists()
    }

    @Test
    fun withoutNextGameThereIsNoSelector() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.memberNoGame)

        onNodeWithTag(GroupGameResponseTags.Section).assertDoesNotExist()
        onNodeWithTag(GroupDetailsTags.ViewGame).assertDoesNotExist()
    }

    @Test
    fun venueActionFollowsTheView() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithText("Editar").performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.EditVenue, intents.single())
    }

    @Test
    fun memberVenueActionOpensTheMap() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithText("Ver no mapa").performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.OpenVenueMap, intents.single())
    }

    @Test
    fun `member response is shown in the group detail`() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithTag(GroupGameResponseTags.Going).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.Respond(AttendanceIntent.Confirm), intents.single())
        onNodeWithText("Sua presença está confirmada.").assertExists()
        onAllNodesWithText("Talvez").assertCountEquals(0)
    }

    @Test
    fun `group response shows waitlist position and locks after deadline`() = runComposeUiTest {
        setDetailsScreen(
            GroupDetailsPreviewData.member.copy(
                memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Waitlisted, 3),
                membershipType = AthleteMembershipType.AVULSO,
                autoConfirmationVisible = false,
                nextGame = GroupDetailsPreviewData.nextGame.copy(confirmationOpen = false, hasGameFee = true),
            ),
        )

        onNodeWithText("Você está em 3º na lista de espera.").assertExists()
        onNodeWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertExists()
        onNodeWithText("As confirmações estão encerradas.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertExists()
    }

    @Test
    fun `day-member fee notice is hidden when the next game has no fee`() = runComposeUiTest {
        setDetailsScreen(
            GroupDetailsPreviewData.member.copy(
                membershipType = AthleteMembershipType.AVULSO,
                nextGame = GroupDetailsPreviewData.nextGame.copy(hasGameFee = false),
            ),
        )

        onAllNodesWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertCountEquals(0)
    }
}
