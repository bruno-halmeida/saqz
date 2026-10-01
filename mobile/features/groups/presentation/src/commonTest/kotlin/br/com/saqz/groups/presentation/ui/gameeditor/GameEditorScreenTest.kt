package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorIntent
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class GameEditorScreenTest {
    @Test
    fun `recurrence toggle emits the intent`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        setContent { SaqzTheme { GameEditorScreen(state = create(), onBack = {}, onIntent = { intents += it }) } }

        onNodeWithTag(GameEditorTags.RecurrenceToggle).performScrollTo().assertIsOff().performClick()

        assertEquals(listOf<GameEditorIntent>(GameEditorIntent.ToggleRecurring(true)), intents)
    }

    @Test
    fun `recurring shows the group slots and the new one`() = runComposeUiTest {
        setContent {
            SaqzTheme { GameEditorScreen(state = create(recurring = true), onBack = {}, onIntent = {}) }
        }

        onNodeWithTag(GameEditorTags.RecurrenceToggle).performScrollTo().assertIsOn()
        onNodeWithText("Terça · 19h30").assertExists()
        onNodeWithText("Quinta · 20h00 · novo").assertExists()
        onNodeWithText("Os próximos jogos usam a quadra do grupo.").assertExists()
    }

    @Test
    fun `recurrence is absent when editing`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = create().copy(recurrenceOffered = false, versionToken = "etag-1"),
                    onBack = {},
                    onIntent = {},
                )
            }
        }

        onNodeWithTag(GameEditorTags.Recurrence).assertDoesNotExist()
    }

    @Test
    fun `recurrence failure offers a retry that submits again`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>()
        setContent {
            SaqzTheme {
                GameEditorScreen(
                    state = create(recurring = true).copy(recurrenceFailed = true),
                    onBack = {},
                    onIntent = { intents += it },
                )
            }
        }

        onNodeWithTag(GameEditorTags.RecurrenceFailure).assertExists()
        onNodeWithText("Tentar").performClick()

        assertEquals(listOf<GameEditorIntent>(GameEditorIntent.Submit), intents)
    }

    private fun create(recurring: Boolean = false) = GameEditorState(
        isLoading = false,
        groupName = "Vôlei do CERET",
        zoneId = "America/Sao_Paulo",
        form = GameEditorFields(
            localDate = "2026-08-06",
            localTime = "20:00",
            durationMinutes = 120,
            venue = GameVenue(name = "CERET", address = "R. Canuto Abreu"),
            capacity = 12,
            confirmationLeadMinutes = 360,
            recurring = recurring,
        ),
        recurrenceOffered = true,
        regularSlots = listOf(GroupRegularSlot(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120)),
        groupHasVenue = true,
    )
}
