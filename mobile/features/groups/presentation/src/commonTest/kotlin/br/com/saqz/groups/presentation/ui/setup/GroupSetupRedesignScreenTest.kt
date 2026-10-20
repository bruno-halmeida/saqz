package br.com.saqz.groups.presentation.ui.setup

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.model.GroupModality
import br.com.saqz.groups.presentation.setup.GroupSetupIntent
import br.com.saqz.groups.presentation.setup.GroupSetupMode
import br.com.saqz.groups.presentation.setup.GroupSetupState
import kotlin.test.Test
import kotlin.test.assertEquals

/** A estrutura nova da tela de criar grupo (cartões "O grupo" e "Jogos", "Mais detalhes"). */
@OptIn(ExperimentalTestApi::class)
class GroupSetupRedesignScreenTest {
    private fun create() = GroupSetupState(mode = GroupSetupMode.Create)

    @Test
    fun sportIsAVerticalSegmentedWithTheFullNames() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent { SaqzTheme { GroupSetupScreen(create(), { intents += it }, {}) } }

        onNodeWithText("Vôlei de quadra").assertExists()
        onNodeWithText("Vôlei de areia").assertExists()
        onNodeWithText("Futevôlei").assertExists()
        onNodeWithTag(GroupSetupTags.modalityOption(GroupModality.BEACH_VOLLEYBALL)).performClick()
        assertEquals(GroupSetupIntent.SelectModality(GroupModality.BEACH_VOLLEYBALL), intents.last())
    }

    @Test
    fun gamesDefaultToVariesAndHideSlotsAndVenueUntilYes() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(create(), {}, {}) } }

        onNodeWithText("Vocês jogam sempre no mesmo dia?").assertExists()
        onNodeWithText("Não, varia").assertIsSelected()
        onNodeWithText("Você marca cada jogo depois de criar o grupo.").assertExists()
        onNodeWithText("Adicionar dia e horário").assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.VenueName).assertDoesNotExist()
    }

    @Test
    fun yesShowsSlotsAndVenue() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        val state = create().copy(recurring = true)
        setContent { SaqzTheme { GroupSetupScreen(state, { intents += it }, {}) } }

        onNodeWithText("Sim, toda semana").assertIsSelected()
        onNodeWithText("O jogo de cada semana sai sozinho, 5 dias antes.").assertExists()
        onNodeWithText("Adicionar dia e horário").assertExists()
        onNodeWithTag(GroupSetupTags.VenueName).assertExists()
        onNodeWithText("Endereço (opcional)").assertExists()
        onNodeWithText("Não, varia").performClick()
        assertEquals(GroupSetupIntent.ToggleRecurring(false), intents.last())
    }

    @Test
    fun withoutFixedDayTheVenueMovesToTheRulesAsDefaultVenue() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(create(), {}, {}) } }
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()

        onNodeWithText("Quadra padrão (opcional)").performScrollTo().assertExists()
        onNodeWithTag(GroupSetupTags.VenueName).performScrollTo().assertExists()
    }

    @Test
    fun moreDetailsIsARowThatOpensAndKeepsTheSubtitleWhileClosed() = runComposeUiTest {
        setContent { SaqzTheme { GroupSetupScreen(create(), {}, {}) } }

        onNodeWithText("Nível, vagas, valor e prioridade").assertExists()
        onNodeWithTag(GroupSetupTags.Level).assertDoesNotExist()
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()
        onNodeWithText("Nível, vagas, valor e prioridade").assertDoesNotExist()
        onNodeWithText("Sobre o grupo").performScrollTo().assertExists()
        onNodeWithText("Regras dos jogos").performScrollTo().assertExists()
        onNodeWithTag(GroupSetupTags.ConfirmationLead).performScrollTo().assertExists()
    }

    @Test
    fun priorityIsASegmentedOfFourWithTheNewLabel() = runComposeUiTest {
        val intents = mutableListOf<GroupSetupIntent>()
        setContent { SaqzTheme { GroupSetupScreen(create(), { intents += it }, {}) } }
        onNodeWithTag(GroupSetupTags.Advanced).performScrollTo().performClick()

        onNodeWithText("Prioridade dos mensalistas").performScrollTo().assertExists()
        onNodeWithText("Horas antes do jogo. Depois, a vaga é de todos.").assertExists()
        onNodeWithTag("${GroupSetupTags.ConfirmationLead}-1440").performScrollTo().performClick()
        assertEquals(GroupSetupIntent.SelectConfirmationLead(1_440), intents.last())
    }

    @Test
    fun photoRowOffersAddAndThenChange() {
        runComposeUiTest {
            val intents = mutableListOf<GroupSetupIntent>()
            setContent { SaqzTheme { GroupSetupScreen(create(), { intents += it }, {}) } }
            onNodeWithText("Opcional · aparece nos convites").assertExists()
            onNodeWithText("Adicionar").performClick()
            assertEquals(GroupSetupIntent.PickPhoto, intents.last())
        }
        runComposeUiTest {
            setContent { SaqzTheme { GroupSetupScreen(create().copy(photoUrl = "https://x/y.png"), {}, {}) } }
            onNodeWithText("Trocar").assertExists()
        }
    }
}
