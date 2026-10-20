package br.com.saqz.groups.presentation.positionpicker

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.athlete.AthletePosition
import br.com.saqz.groups.presentation.positionpicker.ui.PositionPickerPreviewData
import br.com.saqz.groups.presentation.positionpicker.ui.PositionPickerScreen
import br.com.saqz.groups.presentation.positionpicker.ui.PositionPickerTags
import kotlin.test.Test
import kotlin.test.assertEquals

/** DEB-107: a tela de escolher posição, quadros `Main`, `Cheia`, `SemSugestao`, `Enviando`, `Lotou` e `TodasCheias`. */
@OptIn(ExperimentalTestApi::class)
class PositionPickerScreenTest {
    private val main = PositionPickerPreviewData.main

    @Test
    fun optionsAreASingleChoiceWithTheSuggestedOneSelected() = runComposeUiTest {
        val intents = setScreen(main)
        onNodeWithTag(PositionPickerTags.option(AthletePosition.CENTRAL))
            .assertIsSelected()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
        onNodeWithTag(PositionPickerTags.option(AthletePosition.PONTA)).assertIsNotSelected()
        onNodeWithText("Do seu perfil").assertExists()
        onNodeWithText("Ponteiro(a)").assertExists()
        onNodeWithTag(PositionPickerTags.option(AthletePosition.CENTRAL)).assert(hasText("3 de 4")).assert(hasText("1 vaga"))
        onNodeWithText("1 na espera").assertExists()
        onNodeWithText("Em qual posição você vai jogar?").assertExists()
        onNodeWithText("Terça, 19h30").assertExists()
        onNodeWithText("2h de jogo").assertExists()
        onNodeWithText("Vôlei do CERET").assertExists()
        onNodeWithText("Confirmar presença").assertExists()
        onNodeWithTag(PositionPickerTags.Notice).assertDoesNotExist()
        onNodeWithTag(PositionPickerTags.option(AthletePosition.OPOSTO)).performClick()
        assertEquals(PositionPickerIntent.Select(AthletePosition.OPOSTO), intents.last())
    }

    @Test
    fun fullPositionTurnsTheButtonIntoTheWaitlistWithTheNotice() = runComposeUiTest {
        setScreen(main.copy(selected = AthletePosition.PONTA))
        onNodeWithText("Entrar na lista de espera").assertExists()
        onNodeWithText("Sem vaga de Ponteiro(a)").assertExists()
        onNodeWithText("Você entra na lista de espera dessa posição, em 2º.").assertExists()
        onNodeWithText("Central e Oposto(a) ainda têm vaga.").assertExists()
    }

    @Test
    fun nothingSelectedDisablesTheButton() = runComposeUiTest {
        setScreen(main.copy(selected = null, options = PositionPickerPreviewData.options(suggested = false)))
        onNodeWithText("Escolha uma posição").assertExists()
        onNodeWithTag(PositionPickerTags.Submit).assertIsNotEnabled()
        onNodeWithText("Do seu perfil").assertDoesNotExist()
    }

    @Test
    fun allFullShowsTheNoticeAtTheTop() = runComposeUiTest {
        setScreen(main.copy(options = PositionPickerPreviewData.options(allFull = true)))
        onNodeWithTag(PositionPickerTags.TopNotice).assertExists()
        onNodeWithText("Todas as posições estão cheias").assertExists()
        onNodeWithText("Escolha em qual lista de espera entrar.").assertExists()
        onNodeWithText("Na de Central, você fica em 1º.").assertExists()
        onNodeWithTag(PositionPickerTags.Notice).assertDoesNotExist()
    }

    @Test
    fun racedPositionExplainsInError() = runComposeUiTest {
        setScreen(main.copy(options = PositionPickerPreviewData.options(centralFull = true), raced = AthletePosition.CENTRAL))
        onNodeWithText("Central acabou de lotar").assertExists()
        onNodeWithText("Entre na lista de espera dela, em 1º, ou escolha outra posição.").assertExists()
        onNodeWithText("Oposto(a) ainda tem vaga.").assertExists()
        onNodeWithText("Entrar na lista de espera").assertExists()
    }

    @Test
    fun sendingShowsOnlyTheSpinnerAndDisablesTheOptions() = runComposeUiTest {
        setScreen(main.copy(sending = true))
        onNodeWithTag(PositionPickerTags.Submit).assert(hasContentDescription("Confirmando presença"))
        onNodeWithText("Confirmar presença").assertDoesNotExist()
        onNodeWithTag(PositionPickerTags.option(AthletePosition.OPOSTO)).assertIsNotEnabled()
    }

    @Test
    fun withoutPositionsTheButtonConfirmsDirectly() = runComposeUiTest {
        val intents = setScreen(main.copy(options = emptyList(), selected = null))
        onNodeWithTag(PositionPickerTags.Options).assertDoesNotExist()
        onNodeWithText("Confirmar presença").assertExists()
        onNodeWithTag(PositionPickerTags.Submit).assertIsEnabled().performClick()
        assertEquals(PositionPickerIntent.Submit, intents.last())
    }

    @Test
    fun failureShowsTheErrorAndRetry() = runComposeUiTest {
        val intents = setScreen(main.copy(submitFailed = true))
        onNodeWithText("Não foi possível salvar sua resposta. Tente novamente.").assertExists()
        onNodeWithTag(PositionPickerTags.Submit).assertIsEnabled().performClick()
        assertEquals(PositionPickerIntent.Submit, intents.last())
    }

    private fun ComposeUiTest.setScreen(state: PositionPickerState): MutableList<PositionPickerIntent> {
        val intents = mutableListOf<PositionPickerIntent>()
        setContent { SaqzTheme { PositionPickerScreen(state, onBack = {}, onIntent = { intents += it }) } }
        return intents
    }
}
