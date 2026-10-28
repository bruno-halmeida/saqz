package br.com.saqz.designsystem

import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals

/** As peças da lista de grupos (GER-003): o alvo de 44 do botão sobre a foto e o toque da linha tracejada. */
@OptIn(ExperimentalTestApi::class)
class SaqzPhotoTagTest {

    @Test
    fun photoButtonKeepsTheTouchTargetAboveTheVisiblePill() = runComposeUiTest {
        var taps = 0
        setContent {
            SaqzTheme {
                SaqzPhotoButton(
                    label = "Confirmar",
                    onClick = { taps++ },
                    modifier = Modifier.width(96.dp).testTag("photo-button"),
                )
            }
        }
        onNodeWithTag("photo-button").assertHeightIsAtLeast(44.dp).assertHasClickAction()
        onNodeWithTag("photo-button").performClick()
        assertEquals(1, taps)
    }

    @Test
    fun disabledPhotoButtonDoesNotFire() = runComposeUiTest {
        var taps = 0
        setContent {
            SaqzTheme {
                SaqzPhotoButton(label = "Confirmar", onClick = { taps++ }, enabled = false, modifier = Modifier.testTag("b"))
            }
        }
        onNodeWithTag("b").assertIsNotEnabled()
        assertEquals(0, taps)
    }

    @Test
    fun photoTagShowsItsText() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzPhotoTag(text = "Na espera", color = SaqzTheme.colors.warningForeground, dot = SaqzTheme.colors.warning)
            }
        }
        onNodeWithText("Na espera").assertIsDisplayed()
    }

    @Test
    fun dashedRowIsOneTapWithBothLines() = runComposeUiTest {
        var taps = 0
        setContent {
            SaqzTheme {
                SaqzDashedRow(
                    icon = SaqzIcons.Plus,
                    label = "Criar um grupo",
                    supporting = "Para quem organiza os jogos",
                    onClick = { taps++ },
                    modifier = Modifier.testTag("row"),
                )
            }
        }
        onNodeWithTag("row").assertHasClickAction()
        onNodeWithText("Para quem organiza os jogos", useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag("row").performClick()
        assertEquals(1, taps)
    }
}
