package br.com.saqz.groups.presentation.ui.gameeditor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import br.com.saqz.groups.domain.game.GameVenue
import br.com.saqz.groups.domain.group.GroupRegularSlot
import br.com.saqz.groups.domain.group.GroupWeekday
import br.com.saqz.groups.domain.place.PlaceSuggestion
import br.com.saqz.groups.presentation.gameeditor.GameEditorFields
import br.com.saqz.groups.presentation.gameeditor.GameEditorState
import br.com.saqz.groups.presentation.place.VenueSearchState
import br.com.saqz.groups.presentation.ui.components.VenueSuggestionTags
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * O formulário de jogo em telas de verdade: o texto dos chips inteiro com a fonte normal e a maior
 * (item 6) e as sugestões do Google na vista quando chegam (item 5). Layout não se vê num teste de
 * ViewModel: aqui se mede.
 */
@OptIn(ExperimentalTestApi::class)
class GameEditorLayoutTest {
    // ---- Chips: o texto nunca é cortado ("24h ante") ----

    @Test fun `chips keep the whole text with the normal font on a 360dp phone`() = assertWholeChips(1f, 360)

    @Test fun `chips keep the whole text with the normal font on a 393dp phone`() = assertWholeChips(1f, 393)

    @Test fun `chips keep the whole text with a 1_3 font on a 360dp phone`() = assertWholeChips(1.3f, 360)

    @Test fun `chips keep the whole text with a 1_3 font on a 393dp phone`() = assertWholeChips(1.3f, 393)

    @Test
    fun `chips that do not fit in one line go to the next one instead of being cut`() = runComposeUiTest {
        setContent { Phone(fontScale = 1.3f, width = 360) { GameEditorScreen(create(), {}, {}) } }

        val first = chip(GameEditorTags.ConfirmationLead, "3h antes").performScrollTo()
        val last = chip(GameEditorTags.ConfirmationLead, "24h antes").performScrollTo()
        val firstTop = first.fetchSemanticsNode().boundsInRoot.top
        val lastTop = last.fetchSemanticsNode().boundsInRoot.top

        assertTrue(lastTop > firstTop, "com a fonte maior em 360dp o último chip desce de linha: $firstTop / $lastTop")
    }

    private fun assertWholeChips(fontScale: Float, width: Int) = runComposeUiTest {
        setContent { Phone(fontScale, width) { GameEditorScreen(create(), {}, {}) } }

        // A duração virou contador (sem chips); só os chips de antecedência seguem na fileira.
        listOf("3h antes", "6h antes", "12h antes", "24h antes").forEach { label ->
            assertWhole(chip(GameEditorTags.ConfirmationLead, label), label, fontScale, width)
        }
    }

    /**
     * O texto do chip não quebra e corta no que passa da caixa (`softWrap = false`, `Clip`): cortado é o texto
     * querer mais largura do que o nó dele recebeu. `hasVisualOverflow` não serve: o texto é centralizado, o
     * parágrafo ocupa a largura toda e ele acusa "overflow" até num chip inteiro; e a largura da linha
     * (`getLineRight - getLineLeft`) devolve só a parte visível de uma linha cortada.
     */
    private fun assertWhole(chip: SemanticsNodeInteraction, label: String, fontScale: Float, width: Int) {
        chip.performScrollTo()
        val node = chip.fetchSemanticsNode()
        val layouts = mutableListOf<TextLayoutResult>()
        val action = node.config.getOrNull(SemanticsActions.GetTextLayoutResult)?.action
        assertNotNull(action, "o chip \"$label\" não expõe o layout do texto")
        assertTrue(action(layouts) && layouts.isNotEmpty(), "o chip \"$label\" não devolveu o layout do texto")
        val layout = layouts.first()
        val wanted = layout.multiParagraph.maxIntrinsicWidth
        val received = node.size.width
        assertTrue(layout.lineCount == 1, "o chip \"$label\" quebrou em ${layout.lineCount} linhas")
        assertTrue(
            wanted <= received + 1f,
            "o texto do chip \"$label\" está cortado com fonte x$fontScale em ${width}dp: precisa de $wanted, tem $received",
        )
    }

    // ---- Sugestões da quadra: chegam por baixo do campo e entram na vista ----

    private val suggestions = (1..5).map {
        PlaceSuggestion("p$it", "Arena $it", "Avenida Doutor Dante Pazzanese $it - São Paulo", "Arena $it")
    }

    /** Janela de celular: o formulário é bem mais alto que ela, e a quadra fica rente à dobra. */
    private fun ComposeUiTest.setSmallWindow(state: GameEditorState) = setContent {
        SaqzTheme {
            Box(Modifier.size(width = 360.dp, height = 640.dp)) {
                GameEditorScreen(state = state, onBack = {}, onIntent = {})
            }
        }
    }

    @Test
    fun `google suggestions that arrive under the venue field scroll into view`() = runComposeUiTest {
        setSmallWindow(create().copy(venueSearch = VenueSearchState(available = true, suggestions = suggestions)))
        waitForIdle()

        onNodeWithTag(GameEditorTags.VenueName).assertIsDisplayed()
        suggestions.forEach { onNodeWithTag(VenueSuggestionTags.item(it.placeId)).assertIsDisplayed() }
        onNodeWithTag(VenueSuggestionTags.Attribution).assertIsDisplayed()
        // A lista é mais alta que o resto da janela: para caber, o formulário rolou e a data saiu da vista.
        onNodeWithTag(GameEditorTags.Date).assertIsNotDisplayed()
    }

    @Test
    fun `without suggestions the form is not scrolled on its own`() = runComposeUiTest {
        // Guarda do teste acima: a data fora da vista lá é obra da rolagem, não da janela pequena.
        setSmallWindow(create())
        waitForIdle()

        onNodeWithTag(GameEditorTags.Date).assertIsDisplayed()
        onNodeWithTag(GameEditorTags.VenueName).assertIsDisplayed()
    }

    private fun ComposeUiTest.chip(group: String, label: String) =
        onNode(hasText(label) and hasAnyAncestor(hasTestTag(group)), useUnmergedTree = true)

    @Composable
    private fun Phone(fontScale: Float, width: Int, content: @Composable () -> Unit) {
        val density = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = fontScale)) {
            SaqzTheme { Box(Modifier.width(width.dp)) { content() } }
        }
    }

    private fun create() = GameEditorState(
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
        ),
        recurrenceOffered = true,
        regularSlots = listOf(GroupRegularSlot(weekday = GroupWeekday.TUESDAY, startTime = "19:30", durationMinutes = 120)),
        groupHasVenue = true,
    )
}
