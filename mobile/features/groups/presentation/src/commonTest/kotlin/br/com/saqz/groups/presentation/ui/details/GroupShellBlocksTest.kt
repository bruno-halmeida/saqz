package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.CashboxUi
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

/** Topo, mural, pessoas/gestão e sair: os blocos do ticket C2. */
@OptIn(ExperimentalTestApi::class)
class GroupShellBlocksTest {
    private val adminOnly = listOf(
        GroupDetailsTags.CreateNextGame,
        GroupDetailsTags.EditGroup,
        GroupDetailsTags.Cashbox,
        GroupDetailsTags.ManageMembers,
        GroupDetailsTags.ManageSchedule,
        GroupDetailsTags.ManageInviteLink,
    )

    private val memberOnly = listOf(
        GroupDetailsTags.ViewAllMembers,
        GroupDetailsTags.Invite,
    )

    @Test
    fun adminViewHasNoMemberSection() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin)

        adminOnly.forEach { onNodeWithTag(it).assertExists() }
        memberOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
    }

    @Test
    fun memberViewHasNoAdminSection() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

        memberOnly.forEach { onNodeWithTag(it).assertExists() }
        adminOnly.forEach { onAllNodesWithTag(it).assertCountEquals(0) }
        onNodeWithTag(GroupDetailsTags.Notice).assertExists()
    }

    @Test
    fun ownerCannotLeaveButAdminAndAthleteCan() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(isOwner = true))
        onAllNodesWithTag(GroupDetailsTags.Leave).assertCountEquals(0)
    }

    @Test
    fun adminCanRequestDeparture() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(isOwner = false)) { intents += it }
        onNodeWithTag(GroupDetailsTags.Leave).performScrollTo().performClick()
        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.Leave), intents)
    }

    @Test
    fun memberWithoutPreviewStillOpensMembers() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member.copy(memberPreview = emptyList())) { intents += it }

        onNode(
            hasClickAction() and hasAnyAncestor(hasTestTag(GroupDetailsTags.ViewAllMembers)),
        ).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.ViewAllMembers), intents)
    }

    @Test
    fun adminWithoutPreviewStillOpensManageMembers() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(memberPreview = emptyList())) { intents += it }

        onNodeWithTag(GroupDetailsTags.ManageMembers).performScrollTo().performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.ManageMembers), intents)
        onAllNodesWithTag(GroupDetailsTags.ViewAllMembers).assertCountEquals(0)
    }

    @Test
    fun memberViewDoesNotExposeOrganizerCashboxShortcut() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

        onAllNodesWithTag(GroupDetailsTags.ShortcutCashbox).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
    }

    @Test
    fun adminCashboxRowStillOpensOrganizerCashbox() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithTag(GroupDetailsTags.Cashbox).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.OpenCashbox, intents.single())
    }

    @Test
    fun adminCashboxRowRemainsVisibleWithoutFinanceSummary() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(cashbox = CashboxUi()))

        onNodeWithTag(GroupDetailsTags.Cashbox).assertExists()
        onNodeWithText("Caixa do grupo").assertExists()
        onAllNodesWithText("Saldo R$ 380,00 · 8 mensalidades em aberto").assertCountEquals(0)
    }

    @Test
    fun memberViewDoesNotRenderCashboxFromStaleState() = runComposeUiTest {
        setDetailsScreen(
            GroupDetailsPreviewData.member.copy(
                cashbox = CashboxUi(summary = "Saldo R$ 380,00 · 8 mensalidades em aberto"),
            ),
        )

        onAllNodesWithTag(GroupDetailsTags.Cashbox).assertCountEquals(0)
        onAllNodesWithText("Caixa do grupo").assertCountEquals(0)
    }

    @Test
    fun createdPhotoFailedBannerExplainsTheGroupExists() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin, photoFailed = true)

        onNodeWithTag(GroupDetailsTags.PhotoFailed).assertExists()
        onNodeWithText("Grupo criado").assertExists()
        onNodeWithText("A foto não carregou. Você pode tentar de novo em Editar grupo.").assertExists()
    }
}
