package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O botão de fechar do cabeçalho (VUL-58) é a saída que o TalkBack encontra varrendo o
 * painel — o scrim ele só acha por acidente e o back não tem nó nenhum. Os dois casos
 * aqui são o que a árvore de semântica precisa mostrar: o botão existe, fecha, e não
 * aparece sozinho num sheet sem cabeçalho.
 */
@OptIn(ExperimentalTestApi::class)
class SaqzBottomSheetPanelTest {

    @Test
    fun theHeaderCloseButtonClosesTheSheet() = runComposeUiTest {
        var closed = false
        setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize()) {
                    SaqzBottomSheet(
                        open = true,
                        onClose = { closed = true },
                        title = "Sair da conta?",
                        description = "Você volta para a tela de entrada.",
                    ) {}
                }
            }
        }

        // Scrim e botão dividem o rótulo de propósito; o do painel vem por último na árvore.
        val exits = onAllNodesWithContentDescription("Fechar")
        assertEquals(2, exits.fetchSemanticsNodes().size)
        exits.onLast().performClick()
        waitForIdle()

        assertTrue(closed)
    }

    @Test
    fun theSheetWithoutHeaderHasNoCloseButton() = runComposeUiTest {
        setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize()) {
                    SaqzBottomSheet(open = true, onClose = {}) {}
                }
            }
        }

        // Sem título nem descrição não há cabeçalho, e o export não desenha o botão fora
        // dele: sobra só o scrim. Se aparecerem dois, o botão vazou para um painel sem faixa.
        assertEquals(1, onAllNodesWithContentDescription("Fechar").fetchSemanticsNodes().size)
    }

    @Test
    fun theFooterSurvivesContentTallerThanTheSheet() = runComposeUiTest {
        setContent {
            SaqzTheme {
                // Recorte curto de propósito: é a tela baixa (ou a fonte ampliada) em que o
                // conteúdo passa do painel. Sem rolagem no conteúdo, o rodapé é empurrado
                // para fora, o `clipToBounds` o corta e as ações ficam inalcançáveis.
                Box(Modifier.size(width = 360.dp, height = 480.dp)) {
                    SaqzBottomSheet(
                        open = true,
                        onClose = {},
                        title = "Regras do grupo",
                        description = "Quem entra concorda com tudo isto.",
                        footer = { SaqzButton("Aceitar e entrar", onClick = {}) },
                    ) {
                        repeat(30) { Text("Regra ${it + 1} do grupo") }
                    }
                }
            }
        }

        onNodeWithText("Aceitar e entrar").assertIsDisplayed()
    }
}
