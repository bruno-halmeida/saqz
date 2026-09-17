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
class GroupOwnDebtBlockTest {
    @Test
    fun ownChargesShowPendingFirstHistoryBelowAndThePixToPay() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member)

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
        setDetailsScreen(GroupDetailsPreviewData.memberOwnChargesSettled)

        onNodeWithTag(GroupDetailsTags.OwnChargesHistory).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPending).assertCountEquals(0)
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPix).assertCountEquals(0)
    }

    @Test
    fun ownChargesCopyAsksForThePixKey() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.member) { intents += it }

        onNodeWithTag(GroupDetailsTags.OwnChargesPixCopy).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.CopyPix, intents.single())
    }

    // A seção falha sozinha: o resto do detalhe continua na tela, com retry só dela.
    @Test
    fun ownChargesFailureKeepsTheScreenAndOffersRetry() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setDetailsScreen(GroupDetailsPreviewData.memberOwnChargesFailed) { intents += it }

        onNodeWithTag(GroupDetailsTags.Mural).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesFailure).assertExists()
        onNodeWithTag(GroupDetailsTags.OwnChargesRetry).performScrollTo().performClick()

        assertEquals(GroupDetailsIntent.RetryOwnCharges, intents.single())
    }

    @Test
    fun ownChargesShowASkeletonWhileLoading() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.memberOwnChargesLoading)

        onNodeWithTag(GroupDetailsTags.OwnChargesSkeleton).assertExists()
        onAllNodesWithTag(GroupDetailsTags.OwnChargesPending).assertCountEquals(0)
    }

    @Test
    fun memberWithoutChargesHasNoOwnChargesSection() = runComposeUiTest {
        setDetailsScreen(GroupDetailsPreviewData.member.copy(ownCharges = null))

        onAllNodesWithTag(GroupDetailsTags.OwnCharges).assertCountEquals(0)
    }
}
