package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Um caso por peça que tem lógica de verdade: iniciais, limite do stepper, seleção,
 * abertura do sheet e a auto-dispensa do toast. O que é só pintura fica com a
 * @Preview — teste de cor em texto não pega regressão, só congela decisão de design.
 */
@OptIn(ExperimentalTestApi::class)
class SaqzComponentsTest {

    @Test
    fun initialsTakeFirstAndLastName() {
        assertEquals("LP", saqzInitials("Lucas Pereira"))
        assertEquals("BS", saqzInitials("bruna silva"))
        assertEquals("AS", saqzInitials("  Ana   Maria  Souza  "))
        assertEquals("T", saqzInitials("Tiago"))
        assertEquals("", saqzInitials("   "))
    }

    @Test
    fun stepperClampsToRange() {
        assertEquals(5, saqzSteppedValue(value = 4, step = 1, min = 4, max = 12))
        assertEquals(4, saqzSteppedValue(value = 4, step = -1, min = 4, max = 12))
        assertEquals(12, saqzSteppedValue(value = 12, step = 1, min = 4, max = 12))
    }

    @Test
    fun stepperDisablesTheButtonAtTheLimit() = runComposeUiTest {
        var value = 4
        setContent {
            SaqzTheme { SaqzStepper(value = value, onValueChange = { value = it }, min = 4, max = 12) }
        }
        onNodeWithContentDescription("Diminuir").assertIsNotEnabled()
        onNodeWithContentDescription("Diminuir").performClick()
        waitForIdle()
        assertEquals(4, value)
    }

    @Test
    fun switchTogglesAndAnnouncesState() = runComposeUiTest {
        var checked = false
        setContent {
            SaqzTheme {
                SaqzSwitch(
                    checked = checked,
                    onCheckedChange = { checked = it },
                    label = "Jogo toda semana",
                    modifier = Modifier.testTag("switch"),
                )
            }
        }
        onNodeWithTag("switch").assertIsOff()
        onNodeWithTag("switch").performClick()
        waitForIdle()
        assertTrue(checked)
    }

    @Test
    fun switchReflectsTheHoistedState() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzSwitch(
                    checked = true,
                    onCheckedChange = {},
                    contentDescription = "Notificações do grupo",
                    modifier = Modifier.testTag("switch"),
                )
            }
        }
        onNodeWithTag("switch").assertIsOn()
    }

    @Test
    fun switchWithoutLabelDemandsAName() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzSwitch(
                    checked = false,
                    onCheckedChange = {},
                    contentDescription = "Notificações do grupo",
                )
            }
        }
        onNodeWithContentDescription("Notificações do grupo").assertExists()
    }

    @Test
    fun segmentedReportsTheTappedIndex() = runComposeUiTest {
        var selected = 0
        setContent {
            SaqzTheme {
                SaqzSegmented(
                    options = listOf("Masculino", "Feminino", "Misto"),
                    selected = selected,
                    onSelect = { selected = it },
                )
            }
        }
        onNodeWithText("Masculino").assertIsSelected()
        onNodeWithText("Misto").performClick()
        waitForIdle()
        assertEquals(2, selected)
    }

    @Test
    fun attendanceReportsTheChosenIntent() = runComposeUiTest {
        var chosen: SaqzAttendance? = null
        setContent {
            SaqzTheme { SaqzAttendanceSelector(value = chosen, onSelect = { chosen = it }) }
        }
        onNodeWithText("Talvez").performClick()
        waitForIdle()
        assertEquals(SaqzAttendance.Maybe, chosen)
    }

    @Test
    fun attendanceMarksTheSelectedOption() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzAttendanceSelector(value = SaqzAttendance.Going, onSelect = {}) }
        }
        onNodeWithText("Vou").assertIsSelected()
    }

    @Test
    fun closedSheetIsNotInTheTree() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzBottomSheet(open = false, onClose = {}, title = "Sair da conta?") {}
            }
        }
        onNodeWithText("Sair da conta?").assertDoesNotExist()
    }

    @Test
    fun openSheetClosesOnTheScrim() = runComposeUiTest {
        var closed = false
        setContent {
            SaqzTheme {
                Box(Modifier.fillMaxSize()) {
                    SaqzBottomSheet(open = true, onClose = { closed = true }, title = "Sair da conta?") {}
                }
            }
        }
        onNodeWithText("Sair da conta?").assertExists()
        // Desde o VUL-58 duas saídas se chamam "Fechar": o scrim, que vem antes do painel
        // na árvore, e o botão do cabeçalho. Este teste é o do scrim.
        onAllNodesWithContentDescription("Fechar").onFirst().performClick()
        waitForIdle()
        assertTrue(closed)
    }

    @Test
    fun toastDismissesItself() = runComposeUiTest {
        var visible = true
        setContent {
            SaqzTheme {
                SaqzToast(visible = visible, onDismiss = { visible = false }) {
                    SaqzToastText("Presença confirmada. Bom jogo!")
                }
            }
        }
        // O relógio do teste avança sozinho: os 3s de permanência passam em tempo virtual.
        waitUntil(timeoutMillis = 10_000) { !visible }
    }

    @Test
    fun offlineBannerCarriesTheDefaultCopy() = runComposeUiTest {
        setContent { SaqzTheme { SaqzOfflineBanner() } }
        onNodeWithText("Sua resposta está na fila. Enviamos quando a internet voltar.").assertExists()
    }
}
