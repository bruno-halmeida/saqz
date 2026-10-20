package br.com.saqz.groups.presentation.ui.athleteregistration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationIntent
import br.com.saqz.groups.presentation.ui.components.HeightPickerTags
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class AthleteRegistrationScreenTest {

    @Test
    fun `court shows position secondary level and height`() = runComposeUiTest {
        setScreen(AthleteRegistrationSamples.womenCourt)

        onNodeWithTag(AthleteRegistrationTags.Position).assertExists()
        onNodeWithTag(AthleteRegistrationTags.SecondaryPosition).assertExists()
        onNodeWithTag(AthleteRegistrationTags.Level).assertExists()
        onNodeWithTag(AthleteRegistrationTags.Height).assertExists()
        onNodeWithTag(AthleteRegistrationTags.Side).assertDoesNotExist()
        onNodeWithText("Na areia não tem posição fixa — a gente pergunta só o lado que você prefere.")
            .assertDoesNotExist()
    }

    @Test
    fun `tapping the height row then 1 7 and 5 sends the height 175`() = runComposeUiTest {
        val intents = mutableListOf<AthleteRegistrationIntent>()
        setContent {
            SaqzTheme {
                AthleteRegistrationScreen(
                    state = AthleteRegistrationSamples.womenCourt.copy(heightCm = null),
                    onIntent = { intents += it },
                    onBack = {},
                )
            }
        }

        onNodeWithTag(AthleteRegistrationTags.Height).performScrollTo().performClick()
        waitForIdle()
        onNodeWithTag(HeightPickerTags.tenth(17)).performScrollTo().performClick()
        onNodeWithTag(HeightPickerTags.centimeter(5)).performScrollTo().performClick()
        onNodeWithTag(HeightPickerTags.Save).performClick()
        waitForIdle()

        assertEquals(listOf<AthleteRegistrationIntent>(AthleteRegistrationIntent.HeightSelected(175)), intents)
    }

    @Test
    fun `the height row shows the chosen height or no height and the field error`() = runComposeUiTest {
        setScreen(AthleteRegistrationSamples.womenCourt.copy(heightCm = 175))
        onNodeWithText("1,75 m").assertExists()
        setScreen(AthleteRegistrationSamples.womenCourt.copy(heightCm = null, heightError = true))
        onNodeWithText("Sem altura").assertExists()
        onNodeWithText("Escolha uma altura entre 1,00 m e 2,50 m.").assertExists()
    }

    @Test
    fun `beach shows side and hides court-only controls`() = runComposeUiTest {
        setScreen(AthleteRegistrationSamples.beach)

        onNodeWithTag(AthleteRegistrationTags.Side).assertExists()
        onNodeWithText("Lado que você joga").assertExists()
        onNodeWithTag(AthleteRegistrationTags.Position).assertDoesNotExist()
        onNodeWithTag(AthleteRegistrationTags.SecondaryPosition).assertDoesNotExist()
        onNodeWithTag(AthleteRegistrationTags.Height).assertDoesNotExist()
        onNodeWithTag(AthleteRegistrationTags.Summary).assertDoesNotExist()
        onNodeWithText("Na areia não tem posição fixa — a gente pergunta só o lado que você prefere.")
            .assertExists()
    }

    @Test
    fun `composition changes feminine and masculine position labels`() = runComposeUiTest {
        setScreen(AthleteRegistrationSamples.womenCourt)
        onNodeWithTag(AthleteRegistrationTags.position(AthletePosition.LEVANTADOR))
            .assertTextContains("Levantadora")
        onNodeWithTag(AthleteRegistrationTags.position(AthletePosition.PONTA))
            .assertTextContains("Ponteira")
        onNodeWithTag(AthleteRegistrationTags.position(AthletePosition.OPOSTO))
            .assertTextContains("Oposta")

        setScreen(AthleteRegistrationSamples.menCourt)
        onNodeWithTag(AthleteRegistrationTags.position(AthletePosition.LEVANTADOR))
            .assertTextContains("Levantador")
        onNodeWithTag(AthleteRegistrationTags.position(AthletePosition.PONTA))
            .assertTextContains("Ponteiro")
        onNodeWithTag(AthleteRegistrationTags.position(AthletePosition.OPOSTO))
            .assertTextContains("Oposto")

        setScreen(AthleteRegistrationSamples.mixedCourt)
        onNodeWithTag(AthleteRegistrationTags.position(AthletePosition.LEVANTADOR))
            .assertTextContains("Levantador")
    }

    @Test
    fun `secondary choices exclude the principal position`() = runComposeUiTest {
        setScreen(AthleteRegistrationSamples.menCourt)

        onAllNodesWithTag(AthleteRegistrationTags.secondary(AthletePosition.PONTA)).assertCountEquals(0)
        onNodeWithTag(AthleteRegistrationTags.secondary(AthletePosition.LEVANTADOR)).assertExists()
        onNodeWithTag(AthleteRegistrationTags.secondary(AthletePosition.CENTRAL)).assertExists()
    }

    @Test
    fun `summary includes selected secondary and lowercase labels`() = runComposeUiTest {
        setScreen(AthleteRegistrationSamples.womenCourt)

        onNodeWithTag(AthleteRegistrationTags.Summary).assertExists()
        onNodeWithText("Vai aparecer como Levantadora · também joga de ponteira · Intermediário")
            .assertExists()
    }

    @Test
    fun `pending request says the request was sent and keeps the same form`() = runComposeUiTest {
        setScreen(AthleteRegistrationSamples.womenCourt.copy(isPendingRequest = true))

        onNodeWithText("Pedido enviado para o Vôlei do CERET").assertExists()
        onNodeWithText(
            "Seu pedido já foi pro organizador. Conta como você joga: quando o pedido for aprovado, esses dados já entram com você no grupo.",
        ).assertExists()
        onNodeWithText("Conta pra galera como você joga. Isso ajuda a montar os times.").assertDoesNotExist()
        onNodeWithTag(AthleteRegistrationTags.Position).assertExists()
        onNodeWithTag(AthleteRegistrationTags.Save).assertExists()
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationState,
    ) = setContent {
        SaqzTheme {
            AthleteRegistrationScreen(state = state, onIntent = {}, onBack = {})
        }
    }
}
