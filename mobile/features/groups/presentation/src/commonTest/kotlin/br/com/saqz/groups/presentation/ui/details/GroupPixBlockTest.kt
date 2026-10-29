package br.com.saqz.groups.presentation.ui.details

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.details.GroupDetailsIntent
import br.com.saqz.groups.presentation.details.OwnChargeStatusUi
import br.com.saqz.groups.presentation.details.OwnChargeUi
import br.com.saqz.groups.presentation.details.OwnChargesUi
import br.com.saqz.groups.presentation.ui.finance.groupcash.PixUi
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GroupPixBlockTest {
    @Test
    fun `group pix shows the key the type and copies FIN-008`() = runComposeUiTest {
        val intents = mutableListOf<GroupDetailsIntent>()
        setContent {
            SaqzTheme {
                GroupDetailsScreen(GroupDetailsPreviewData.member.copy(ownCharges = null, groupPix = pix), {}, intents::add)
            }
        }

        onNodeWithTag(GroupDetailsTags.GroupPix).performScrollTo().assertExists()
        onNodeWithText("Pix do grupo").assertExists()
        onNodeWithText("ceret@volei.com.br").assertExists()
        onNodeWithText("Chave Pix · E-mail").assertExists()
        onNodeWithTag(GroupDetailsTags.GroupPixCopy).performClick()

        assertEquals(listOf<GroupDetailsIntent>(GroupDetailsIntent.CopyPix), intents.filterIsInstance<GroupDetailsIntent.CopyPix>())
    }

    @Test
    fun `group pix hides while a charge is open`() = runComposeUiTest {
        val open = OwnChargesUi(pending = listOf(OwnChargeUi("c", "Mensalidade", "Vence 10/10", "R$ 80", OwnChargeStatusUi.Pending)))
        setContent {
            SaqzTheme {
                GroupDetailsScreen(GroupDetailsPreviewData.member.copy(ownCharges = open, groupPix = pix), {}, {})
            }
        }

        onNodeWithTag(GroupDetailsTags.GroupPix).assertDoesNotExist()
    }

    private val pix = PixUi("ceret@volei.com.br", "E-mail")
}
