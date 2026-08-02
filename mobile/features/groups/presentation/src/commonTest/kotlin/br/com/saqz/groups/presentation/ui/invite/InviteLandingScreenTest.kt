package br.com.saqz.groups.presentation.ui.invite

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.presentation.invite.InviteLandingIntent
import br.com.saqz.groups.presentation.invite.InviteLandingState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class InviteLandingScreenTest {
    @Test
    fun `approval preview uses request action and shows no request sent screen`() = runComposeUiTest {
        val intents = mutableListOf<InviteLandingIntent>()
        setScreen(InviteLandingSamples.preview) { intents += it }

        onNodeWithTag(InviteLandingTags.Preview).assertExists()
        onNodeWithText("Pedir pra entrar").performClick()
        onAllNodesWithTag(InviteLandingTags.RequestSent).assertCountEquals(0)
        assertEquals(InviteLandingIntent.PrimaryAction, intents.single())
    }

    @Test
    fun `open preview uses immediate join action`() = runComposeUiTest {
        val intents = mutableListOf<InviteLandingIntent>()
        setScreen(InviteLandingSamples.openPreview) { intents += it }

        onNodeWithText("ENTRADA LIBERADA · Quem tem o link ou o QR entra na hora. Ninguém precisa aprovar.")
            .assertExists()
        onNodeWithText("Entrar no grupo").performClick()
        assertEquals(InviteLandingIntent.PrimaryAction, intents.single())
    }

    @Test
    fun `request sent screen shows organizer and both destinations`() = runComposeUiTest {
        val intents = mutableListOf<InviteLandingIntent>()
        setScreen(InviteLandingSamples.requestSent) { intents += it }

        onNodeWithTag(InviteLandingTags.RequestSent).assertExists()
        onNodeWithText("Ana Lima já recebeu seu pedido. Você recebe um aviso quando for aceito.").assertExists()
        onNodeWithText("Explorar o app").performClick()
        onNodeWithText("Entrar em outro grupo").performClick()
        assertEquals(listOf(InviteLandingIntent.ExploreApp, InviteLandingIntent.OpenAnotherGroup), intents)
    }

    @Test
    fun `all error states have the correct action and plan limit has no queue card`() = runComposeUiTest {
        setScreen(InviteLandingSamples.invalid)
        onNodeWithText("Convite inválido").assertExists()
        onNodeWithText("Pedir novo convite").assertExists()

        setScreen(InviteLandingSamples.expired)
        onNodeWithText("O link do Vôlei do CERET valeu até 31/08/2026. Peça um novo pra quem te convidou.").assertExists()

        setScreen(InviteLandingSamples.rateLimited)
        onNodeWithText("Aguarde 30 segundos antes de tentar de novo.").assertExists()
        onNodeWithText("Tentar novamente").assertExists()

        setScreen(InviteLandingSamples.planLimit)
        onNodeWithText("Este grupo atingiu o limite de atletas do plano. Não há fila de espera.").assertExists()
        onAllNodesWithTag(InviteLandingTags.RequestSent).assertCountEquals(0)

        setScreen(InviteLandingSamples.network)
        onNodeWithText("Tentar novamente").assertExists()
    }

    @Test
    fun `other groups is emitted by the funnel close action`() = runComposeUiTest {
        val intents = mutableListOf<InviteLandingIntent>()
        setScreen(InviteLandingSamples.openPreview) { intents += it }

        onNodeWithText("Ver outros grupos").performClick()
        assertEquals(InviteLandingIntent.BrowseOtherGroups, intents.single())
    }

    private fun ComposeUiTest.setScreen(
        state: InviteLandingState,
        onIntent: (InviteLandingIntent) -> Unit = {},
    ) = setContent {
        SaqzTheme {
            InviteLandingScreen(state = state, onBack = {}, onIntent = onIntent)
        }
    }
}
