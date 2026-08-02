package br.com.saqz.groups.presentation.ui.athleteregistration

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import kotlin.test.Test

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

        onNodeWithTag(AthleteRegistrationTags.Summary).assertTextContains(
            "Vai aparecer como Levantadora · também joga de ponteira · intermediário",
        )
    }

    private fun androidx.compose.ui.test.ComposeUiTest.setScreen(
        state: br.com.saqz.groups.presentation.athleteregistration.AthleteRegistrationState,
    ) = setContent {
        SaqzTheme {
            AthleteRegistrationScreen(state = state, onIntent = {}, onBack = {})
        }
    }
}
