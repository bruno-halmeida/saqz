package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
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
    fun adminWithNextGameCanNotifyWhoIsPending() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.admin) { intents += it }

        onNodeWithTag(GroupDetailsTags.NotifyPending).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.NotifyPending, intents.single())
    }

    @Test
    fun memberAndAdminWithoutGameNeverSeeTheNotifyAction() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    @Test
    fun adminWithoutGameHasNothingToNotify() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.adminNoGame)
        onAllNodesWithTag(GroupDetailsTags.NotifyPending).assertCountEquals(0)
    }

    // O texto é contrato do e2e (`CommunicationDetailsE2eTest`): precisa continuar na árvore.
    @Test
    fun reminderFeedbackStaysOnScreen() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.admin.copy(notifiedCount = "2"))

        onNodeWithText("Lembrete enviado no Saqz para 2 pessoa(s).").assertExists()
    }
}
