package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.GroupDetailsResponseStatus
import br.com.saqz.groups.presentation.details.GroupDetailsResponseUi
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
        GroupDetailsTags.ViewGame,
        GroupGameResponseTags.Section,
        GroupGameResponseTags.Going,
        GroupGameResponseTags.NotGoing,
        GroupGameResponseTags.AutoConfirmation,
        GroupDetailsTags.ShortcutNotices,
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
    fun memberViewDoesNotExposeOrganizerCashboxShortcut() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.member)

        onAllNodesWithTag(GroupDetailsTags.ShortcutCashbox).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
    }

    @Test
    fun adminCashboxRowStillOpensOrganizerCashbox() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithTag(GroupDetailsTags.Cashbox).performClick()

        assertEquals(GroupDetailsIntent.OpenCashbox, intents.single())
    }

    @Test
    fun adminCashboxRowRemainsVisibleWithoutFinanceSummary() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.admin.copy(cashbox = CashboxUi()))

        onNodeWithTag(GroupDetailsTags.Cashbox).assertExists()
        onNodeWithText("Caixa do grupo").assertExists()
        onAllNodesWithText("Saldo R$ 380,00 · 8 mensalidades em aberto").assertCountEquals(0)
    }

    @Test
    fun memberViewDoesNotRenderCashboxFromStaleState() = runComposeUiTest {
        setScreen(
            GroupDetailsPreviewData.member.copy(
                cashbox = CashboxUi(summary = "Saldo R$ 380,00 · 8 mensalidades em aberto"),
            ),
        )

        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
        onAllNodesWithText("Caixa do grupo").assertCountEquals(0)
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

        onNodeWithText("Ver no mapa").performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.OpenVenueMap, intents.single())
    }

    @Test
    fun `member response is shown in the group detail`() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithTag(GroupGameResponseTags.Going).performClick()

        assertEquals(GroupDetailsIntent.Respond(br.com.saqz.groups.domain.attendance.AttendanceIntent.Confirm), intents.single())
        onNodeWithText("Você está confirmado na vaga.").assertExists()
        onAllNodesWithText("Talvez").assertCountEquals(0)
    }

    @Test
    fun `group response shows waitlist position and locks after deadline`() = runComposeUiTest {
        setScreen(
            GroupDetailsPreviewData.member.copy(
                memberResponse = GroupDetailsResponseUi(GroupDetailsResponseStatus.Waitlisted, 3),
                membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO,
                autoConfirmationVisible = false,
                nextGame = GroupDetailsPreviewData.member.nextGame?.copy(confirmationOpen = false, hasGameFee = true),
            ),
        )

        onNodeWithText("Você é o 3º da reserva.").assertExists()
        onNodeWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertExists()
        onNodeWithText("As confirmações estão encerradas.").assertExists()
        onNodeWithTag(GroupGameResponseTags.Going).assertExists()
        onNodeWithTag(GroupGameResponseTags.NotGoing).assertExists()
    }

    @Test
    fun `day-member fee notice is hidden when the next game has no fee`() = runComposeUiTest {
        setScreen(
            GroupDetailsPreviewData.member.copy(
                membershipType = br.com.saqz.groups.domain.athlete.AthleteMembershipType.AVULSO,
                nextGame = GroupDetailsPreviewData.member.nextGame?.copy(hasGameFee = false),
            ),
        )

        onAllNodesWithText("Ao confirmar, a cobrança deste jogo será gerada.").assertCountEquals(0)
    }

    @Test
    fun ownChargesShowPendingFirstHistoryBelowAndThePixToPay() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.member)

        onNodeWithTag(GroupDetailsTags.OwnCharges).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPending).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesHistory).assertExists()
        onNodeWithTag(GroupDetailsTags.ownCharge("c-1")).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesPix).assertExists()
        onNodeWithText("Venceu em 10/08").assertExists()
        onNodeWithText("Paga").assertExists()
    }

    @Test
    fun ownChargesWithoutPendingHideThePixCard() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.memberOwnChargesSettled)

        onNodeWithTag(GroupDetailsTags.OwnChargesHistory).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPending).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPix).assertCountEquals(0)
    }

    @Test
    fun ownChargesCopyAsksForThePixKey() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithTag(GroupDetailsTags.OwnChargesPixCopy).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.CopyPix, intents.single())
    }

    // A seção falha sozinha: o resto do detalhe continua na tela, com retry só dela.
    @Test
    fun ownChargesFailureKeepsTheScreenAndOffersRetry() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setScreen(GroupDetailsPreviewData.memberOwnChargesFailed) { intents += it }

        onNodeWithTag(GroupDetailsTags.Venue).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesFailure).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesRetry).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.RetryOwnCharges, intents.single())
    }

    @Test
    fun ownChargesShowASkeletonWhileLoading() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.memberOwnChargesLoading)

        onNodeWithTag(GroupDetailsTags.OwnChargesSkeleton).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPending).assertCountEquals(0)
    }

    @Test
    fun memberWithoutChargesHasNoOwnChargesSection() = runComposeUiTest {
        setScreen(GroupDetailsPreviewData.member.copy(ownCharges = null))

        onAllNodesWithTag(GroupDetailsTags.OwnCharges).assertCountEquals(0)
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
