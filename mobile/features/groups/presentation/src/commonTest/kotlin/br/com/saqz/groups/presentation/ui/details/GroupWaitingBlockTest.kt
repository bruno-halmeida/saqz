package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupWaitingBlockTest {
    @Test
    fun memberNeverSeesTheBlock() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member.copy(waiting = GroupWaitingPreviewData.waiting))

        onAllNodesWithTag(GroupDetailsTags.Waiting).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun adminWithNothingPendingDoesNotSeeTheBlock() = runComposeUiTest {
        val nobodyPending = GroupDetailsPreviewData.attendance.copy(pending = 0)
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(attendance = nobodyPending))

        onAllNodesWithTag(GroupDetailsTags.Waiting).assertCountEquals(0)
    }

    @Test
    fun quorumShowsPluralTitleAndDeadline() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.idle)

        onNodeWithTag(GroupDetailsTags.WaitingQuorum)
            .assertTextContains("2 pessoas sem resposta")
            .assertTextContains("Encerra 28/07 · 18h00")
    }

    @Test
    fun quorumShowsSingularTitle() = runComposeUiTest {
        val onePending = GroupDetailsPreviewData.attendance.copy(pending = 1)
        setDetailsScreen(GroupWaitingPreviewData.idle.copy(attendance = onePending))

        onNodeWithTag(GroupDetailsTags.WaitingQuorum).assertTextContains("1 pessoa sem resposta")
    }

    @Test
    fun notifyButtonEmitsNotifyPending() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupWaitingPreviewData.idle) { intents += it }

        onNodeWithTag(GroupDetailsTags.NotifyPending).performScrollTo().assertIsEnabled().performClick()

        assertEquals(GroupDetailsIntent.NotifyPending, intents.single())
    }

    @Test
    fun notifyingDisablesTheButton() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.notifying)

        onNodeWithTag(GroupDetailsTags.NotifyPending).assertIsNotEnabled()
    }

    // O texto é contrato do e2e (`ReminderE2eTest`): um nó só, na árvore fundida, para sempre.
    @Test
    fun reminderFeedbackStaysOnScreenWithoutTheButton() = runComposeUiTest {
        val nobodyPending = GroupDetailsPreviewData.attendance.copy(pending = 0)
        setDetailsScreen(GroupWaitingPreviewData.notified.copy(attendance = nobodyPending))

        onNodeWithText("Lembrete enviado no Saqz para 2 pessoa(s).").assertExists()
        onNodeWithTag(GroupDetailsTags.WaitingQuorum).assertTextContains("Lembrete enviado no Saqz para 2 pessoa(s).")
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun failureShowsTheErrorAndKeepsTheButton() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.failed)

        onNodeWithTag(GroupDetailsTags.WaitingQuorum).assertTextContains("Não foi possível concluir. Tente novamente.")
        onNodeWithTag(GroupDetailsTags.NotifyPending).assertIsEnabled()
    }

    // O defeito de hoje: botão ativo com as confirmações encerradas, e o toque não fazia nada.
    @Test
    fun closedConfirmationsHideTheQuorumRow() = runComposeUiTest {
        setDetailsScreen(GroupWaitingPreviewData.closed)

        onNodeWithTag(GroupDetailsTags.Waiting).assertExists()
        onAllNodesWithTag(GroupDetailsTags.WaitingQuorum).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun eachRowEmitsItsIntentAndCashboxTagStaysOutOfTheBlock() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupWaitingPreviewData.idle) { intents += it }

        onNodeWithTag(GroupDetailsTags.WaitingEntryRequests).performScrollTo().performClick()
        onNodeWithTag(GroupDetailsTags.WaitingMonthly).performScrollTo().performClick()
        onNodeWithTag(GroupDetailsTags.WaitingSettle).performScrollTo().performClick()

        assertEquals(
            listOf(
                GroupDetailsIntent.InviteByLink,
                GroupDetailsIntent.OpenCashbox,
                GroupDetailsIntent.OpenSettlement("game-0"),
            ),
            intents,
        )
        onAllNodes(
            hasTestTag(GroupDetailsTags.Cashbox) and hasAnyAncestor(hasTestTag(GroupDetailsTags.Waiting)),
            useUnmergedTree = true,
        ).assertCountEquals(0)
    }
}
