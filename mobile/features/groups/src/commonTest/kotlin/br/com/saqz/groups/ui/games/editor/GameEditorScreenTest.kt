package br.com.saqz.groups.ui.games.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.game.GameVersionToken
import br.com.saqz.groups.domain.game.SeriesBoundaryScope
import br.com.saqz.groups.domain.game.Weekday
import br.com.saqz.groups.domain.game.WeeklySlot
import br.com.saqz.groups.presentation.games.editor.GameEditorDraft
import br.com.saqz.groups.presentation.games.editor.GameEditorForm
import br.com.saqz.groups.presentation.games.editor.GameEditorIntent
import br.com.saqz.groups.presentation.games.editor.GameEditorMode
import br.com.saqz.groups.presentation.games.editor.GameEditorState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

@OptIn(ExperimentalTestApi::class)
class GameEditorScreenTest {
    @Test
    fun `required and optional fields show copied values`() = runComposeUiTest {
        screen(); onNodeWithText("Treino").assertExists(); onNodeWithText("Arena").assertExists(); onNodeWithText(
        "Rua 1"
    ).assertExists(); onNodeWithText("25,00").assertExists(); onNodeWithText("Notas").assertExists()
    }

    @Test
    fun `title editing emits complete updated form`() = runComposeUiTest {
        val intents =
            mutableListOf<GameEditorIntent>(); screen(onIntent = intents::add); onNodeWithContentDescription(
        "Título",
        true
    ).performTextReplacement("Final"); waitForIdle(); assertEquals(
        "Final",
        assertIs<GameEditorIntent.UpdateForm>(intents.last()).form.title
    ); assertEquals("Arena", assertIs<GameEditorIntent.UpdateForm>(intents.last()).form.venue?.name)
    }

    @Test
    fun `venue name editing preserves address`() = runComposeUiTest {
        val intents =
            mutableListOf<GameEditorIntent>(); screen(onIntent = intents::add); onNodeWithContentDescription(
        "Nome do local",
        true
    ).performTextReplacement("Ginásio"); waitForIdle();
        val form = assertIs<GameEditorIntent.UpdateForm>(intents.last()).form; assertEquals(
        "Ginásio",
        form.venue?.name
    ); assertEquals("Rua 1", form.venue?.address)
    }

    @Test
    fun `venue address editing preserves name`() = runComposeUiTest {
        val intents =
            mutableListOf<GameEditorIntent>(); screen(onIntent = intents::add); onNodeWithContentDescription(
        "Endereço do local",
        true
    ).performTextReplacement("Rua 2"); waitForIdle();
        val form = assertIs<GameEditorIntent.UpdateForm>(intents.last()).form; assertEquals(
        "Arena",
        form.venue?.name
    ); assertEquals("Rua 2", form.venue?.address)
    }

    @Test
    fun `fee and notes remain editable`() = runComposeUiTest {
        val intents =
            mutableListOf<GameEditorIntent>(); screen(onIntent = intents::add); onNodeWithContentDescription(
        "Valor por jogo (R$)",
        true
    ).performScrollTo().performTextReplacement("12,34"); waitForIdle(); assertEquals(
        "12,34",
        assertIs<GameEditorIntent.UpdateForm>(intents.last()).form.gameFeeBrl
    ); onNodeWithContentDescription("Observações (opcional)", true).performScrollTo()
        .performTextReplacement("Nova nota"); waitForIdle(); assertEquals(
        "Nova nota",
        assertIs<GameEditorIntent.UpdateForm>(intents.last()).form.notes
    )
    }

    @Test
    fun `field errors are adjacent and semantic`() = runComposeUiTest {
        screen(state().copy(fieldErrors = mapOf("title" to listOf("Informe o título")))); onNodeWithText(
        "Informe o título"
    ).assertExists(); onNodeWithTag(GameEditorTags.field("title")).assertExists()
    }

    @Test
    fun `one time fields include deadline and exclude end date`() = runComposeUiTest {
        screen(); onNodeWithTag(GameEditorTags.field("deadline")).assertExists(); onNodeWithTag(
        GameEditorTags.field("localEndDate")
    ).assertDoesNotExist()
    }

    @Test
    fun `weekly mode emits explicit mode intent`() = runComposeUiTest {
        val intents =
            mutableListOf<GameEditorIntent>(); screen(onIntent = intents::add); onNodeWithTag(
        GameEditorTags.Weekly
    ).performClick(); waitForIdle(); assertEquals(
        GameEditorIntent.SetMode(GameEditorMode.WEEKLY),
        intents.single()
    )
    }

    @Test
    fun `weekly mode exposes optional end date and hides one time deadline`() = runComposeUiTest {
        screen(state(GameEditorMode.WEEKLY)); onNodeWithTag(
        GameEditorTags.field("localEndDate")
    ).assertExists(); onNodeWithTag(GameEditorTags.field("deadline")).assertDoesNotExist()
    }

    @Test
    fun `weekly mode renders every slot`() = runComposeUiTest {
        screen(
            state(
                GameEditorMode.WEEKLY,
                slots = listOf(slot("a"), slot("b"))
            )
        ); onNodeWithTag(GameEditorTags.slot(0)).assertExists(); onNodeWithTag(GameEditorTags.slot(1)).assertExists()
    }

    @Test
    fun `add slot delegates durable identity creation`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>(); screen(
        state(
            GameEditorMode.WEEKLY,
            slots = emptyList()
        ), intents::add
    ); onNodeWithTag(GameEditorTags.AddSlot).performScrollTo()
        .performClick(); waitForIdle(); assertEquals(GameEditorIntent.AddSlot, intents.single())
    }

    @Test
    fun `remove slot emits list without selected slot`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>(); screen(
        state(
            GameEditorMode.WEEKLY,
            slots = listOf(slot("a"), slot("b"))
        ), intents::add
    ); onNodeWithTag(GameEditorTags.removeSlot(0)).performScrollTo()
        .performClick(); waitForIdle(); assertEquals(
        listOf("b"),
        assertIs<GameEditorIntent.UpdateForm>(intents.single()).form.slots.map { it.slotKey })
    }

    @Test
    fun `weekday selection updates exact slot`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>(); screen(
        state(
            GameEditorMode.WEEKLY,
            slots = listOf(slot("a"))
        ), intents::add
    ); onNodeWithTag(GameEditorTags.weekday(0, Weekday.Friday)).performScrollTo()
        .performClick(); waitForIdle(); assertEquals(
        Weekday.Friday,
        assertIs<GameEditorIntent.UpdateForm>(intents.single()).form.slots.single().weekday
    )
    }

    @Test
    fun `slot time remains group local text`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>(); screen(
        state(
            GameEditorMode.WEEKLY,
            slots = listOf(slot("a"))
        ), intents::add
    ); onNodeWithContentDescription("Horário", true).performScrollTo()
        .performTextReplacement("20:30:00"); waitForIdle(); assertEquals(
        "20:30:00",
        assertIs<GameEditorIntent.UpdateForm>(intents.single()).form.slots.single().localTime
    )
    }

    @Test
    fun `slot venue editing preserves address`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>(); screen(
        state(
            GameEditorMode.WEEKLY,
            slots = listOf(slot("a"))
        ), intents::add
    ); onAllNodesWithContentDescription("Nome do local", true)[1].performScrollTo()
        .performTextReplacement("Quadra"); waitForIdle();
        val venue =
            assertIs<GameEditorIntent.UpdateForm>(intents.single()).form.slots.single().venue; assertEquals(
        "Quadra",
        venue.name
    ); assertEquals("Rua 1", venue.address)
    }

    @Test
    fun `weekly slot indexed error is visible`() = runComposeUiTest {
        screen(
            state(
                GameEditorMode.WEEKLY,
                slots = listOf(slot("a"))
            ).copy(fieldErrors = mapOf("slots[0].localTime" to listOf("Horário inválido")))
        ); onNodeWithText("Horário inválido").assertExists()
    }

    @Test
    fun `existing weekly edit has no silent scope selection`() = runComposeUiTest {
        screen(
            state(
                GameEditorMode.WEEKLY,
                gameId = "game"
            )
        ); onNodeWithText("Somente este jogo").assertExists(); onNodeWithText("Este e os próximos").assertExists(); onNodeWithTag(
        GameEditorTags.ScopeOnly
    ).assertExists(); onNodeWithTag(GameEditorTags.ScopeFuture).assertExists()
    }

    @Test
    fun `only this scope emits exact enum`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>(); screen(
        state(
            GameEditorMode.WEEKLY,
            gameId = "game"
        ), intents::add
    ); onNodeWithTag(GameEditorTags.ScopeOnly).performScrollTo()
        .performClick(); waitForIdle(); assertEquals(
        GameEditorIntent.SetScope(SeriesBoundaryScope.OnlyThis),
        intents.single()
    )
    }

    @Test
    fun `this and future scope emits exact enum`() = runComposeUiTest {
        val intents = mutableListOf<GameEditorIntent>(); screen(
        state(
            GameEditorMode.WEEKLY,
            gameId = "game"
        ), intents::add
    ); onNodeWithTag(GameEditorTags.ScopeFuture).performScrollTo()
        .performClick(); waitForIdle(); assertEquals(
        GameEditorIntent.SetScope(SeriesBoundaryScope.ThisAndFuture),
        intents.single()
    )
    }

    @Test
    fun `keyboard-safe form reaches submit with touch target`() = runComposeUiTest {
        setContent {
            Box(
                Modifier.size(
                    320.dp,
                    420.dp
                )
            ) { SaqzTheme { GameEditorScreen(state()) {} } }
        }; onNodeWithTag(GameEditorTags.Submit).performScrollTo().assertIsDisplayed()
        .assertHeightIsAtLeast(48.dp).assertHasClickAction()
    }

    @Test
    fun `max text keeps mode and submit actions reachable`() = runComposeUiTest {
        setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(
                    1f,
                    2f
                )
            ) { SaqzTheme { GameEditorScreen(state()) {} } }
        }; onNodeWithTag(GameEditorTags.OneTime).assertHeightIsAtLeast(48.dp); onNodeWithTag(
        GameEditorTags.Submit
    ).performScrollTo().assertHeightIsAtLeast(48.dp)
    }

    private fun androidx.compose.ui.test.ComposeUiTest.screen(
        value: GameEditorState = state(),
        onIntent: (GameEditorIntent) -> Unit = {}
    ) = setContent { SaqzTheme { GameEditorScreen(value, onIntent) } }

    private fun state(
        mode: GameEditorMode = GameEditorMode.ONE_TIME,
        slots: List<WeeklySlot> = emptyList(),
        gameId: String? = null
    ): GameEditorState {
        val form = GameEditorForm(
            "Treino",
            GameVenue(null, "Arena", "Rua 1"),
            "2026-08-12",
            "19:30:00",
            "America/Sao_Paulo",
            "2026-08-12T22:30:00Z",
            "90",
            "24",
            "2026-08-12T19:00:00Z",
            "25,00",
            "Notas",
            slots = slots
        ); return GameEditorState(
            GameEditorDraft(
                groupId = "group",
                gameId = gameId,
                seriesId = gameId?.let { "series" },
                commandKey = "key",
                version = gameId?.let { GameVersionToken("\"1\"") },
                mode = mode,
                form = form
            )
        )
    }

    private fun slot(key: String) = WeeklySlot(
        key,
        Weekday.Wednesday,
        "19:30:00",
        90,
        GameVenue(null, "Arena", "Rua 1"),
        24,
        180,
        2500,
        "Treino"
    )
}
