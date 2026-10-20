package br.com.saqz.groups.presentation.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.v2.runComposeUiTest
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertTrue

/** A linha dos confirmados: o resumo nunca quebra e a escassez é texto, não chip. */
@OptIn(ExperimentalTestApi::class)
class HeroRosterRowTest {
    private val names = listOf("Ana Lima", "Bruno Dias", "Carla Souza", "Davi Reis", "Eva Prado")

    private fun scarcityShowsAsText(spots: String) = runComposeUiTest {
        setContent {
            SaqzTheme {
                Box(Modifier.width(300.dp)) {
                    HeroRosterRow(names = names, summary = "9 de 12 confirmados", spotsLeft = spots)
                }
            }
        }
        onNodeWithText("9 de 12 confirmados").assertIsDisplayed()
        onNodeWithText(spots).assertIsDisplayed()
    }

    @Test
    fun `tres vagas aparecem como texto`() = scarcityShowsAsText("Restam 3 vagas")

    @Test
    fun `duas vagas aparecem como texto`() = scarcityShowsAsText("Restam 2 vagas")

    @Test
    fun `uma vaga aparece como texto`() = scarcityShowsAsText("Resta 1 vaga")

    @Test
    fun `sem escassez so o resumo aparece`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                Box(Modifier.width(300.dp)) {
                    HeroRosterRow(names = names, summary = "5 de 12 confirmados", spotsLeft = null)
                }
            }
        }
        onNodeWithText("5 de 12 confirmados").assertIsDisplayed()
        onAllNodesWithText("Restam", substring = true).assertCountEquals(0)
        onAllNodesWithText("Resta 1", substring = true).assertCountEquals(0)
    }

    @Test
    fun `resumo em largura estreita fica em uma linha`() = runComposeUiTest {
        setContent {
            SaqzTheme {
                Box(Modifier.width(260.dp)) {
                    HeroRosterRow(names = names, summary = "9 de 12 confirmados", spotsLeft = "Restam 3 vagas")
                }
            }
        }
        val height = onNodeWithText("9 de 12 confirmados").getBoundsInRoot().height
        assertTrue(height < 24.dp, "o resumo quebrou: altura $height")
    }
}
