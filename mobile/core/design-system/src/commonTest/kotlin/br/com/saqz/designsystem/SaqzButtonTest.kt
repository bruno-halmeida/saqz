package br.com.saqz.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsProperties as CoreSemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import br.com.saqz.designsystem.theme.SaqzAccessibilityPreferences
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMetrics
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzButtonTest {
    private val tokens = SaqzColorTokens.Light

    @Test
    fun fourVariantsUseExpectedTokens() {
        val primary = tokens.buttonColors(SaqzButtonVariant.Primary)
        assertEquals(tokens.primary, primary.container)
        assertEquals(tokens.onPrimary, primary.content)

        val secondary = tokens.buttonColors(SaqzButtonVariant.Secondary)
        assertEquals(tokens.surface, secondary.container)
        assertEquals(tokens.primary, secondary.content)
        // Linha azul, não o cinza de card/input: `.saqz-btn--secondary` do export é
        // `border-color:var(--saqz-blue)`.
        assertEquals(tokens.primary, secondary.border)

        val danger = tokens.buttonColors(SaqzButtonVariant.Danger)
        assertEquals(tokens.errorForeground, danger.container)
        assertEquals(tokens.onPrimary, danger.content)

        val ghost = tokens.buttonColors(SaqzButtonVariant.Ghost)
        assertEquals(Color.Transparent, ghost.container)
        assertEquals(tokens.primary, ghost.content)

        // accent is never an action surface.
        SaqzButtonVariant.entries.forEach { variant ->
            val c = tokens.buttonColors(variant)
            assertTrue(c.container != tokens.accent, "$variant must not use accent")
            assertTrue(c.content != tokens.accent, "$variant must not use accent")
        }
    }

    @Test
    fun focusIsThreeToOne() {
        // The focus indicator paints in primary; it must reach 3:1 against the
        // adjacent surfaces it is drawn over.
        assertAtLeast(3.0, contrast(tokens.primary, tokens.background))
        assertAtLeast(3.0, contrast(tokens.primary, tokens.surface))
    }

    @Test
    fun pressStartsBeforeRelease() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = { clicks++ }, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").performTouchInput { down(center) }
        waitForIdle()
        val feedback = pressFeedback("btn")
        // Feedback is present while the finger is still down; no activation yet.
        assertTrue(feedback.scale < 1f || feedback.alpha < 1f, "press feedback before release")
        assertEquals(0, clicks)
        onNodeWithTag("btn").performTouchInput { up() }
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun pressScaleIs098() {
        assertEquals(0.98f, saqzPressScale(pressed = true, motion = SaqzMotionPolicy.Normal))
        assertEquals(1f, saqzPressScale(pressed = false, motion = SaqzMotionPolicy.Normal))
    }

    @Test
    fun pressDurationIs120ms() = runComposeUiTest {
        var duration = -1
        setContent { SaqzTheme { duration = SaqzTheme.motion.pressDurationMillis } }
        // The button drives its press tween from exactly this value.
        assertEquals(120, duration)
    }

    @Test
    fun releaseActivatesOnce() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme { SaqzButton("Salvar", onClick = { clicks++ }, modifier = Modifier.testTag("btn")) }
        }
        onNodeWithTag("btn").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    @Test
    fun disabledHasSemantics() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = {}, enabled = false, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").assertIsNotEnabled()
    }

    @Test
    fun disabledDoesNotActivate() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = { clicks++ }, enabled = false, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").performClick()
        waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun loadingIsBusy() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzButton("Salvar", onClick = { clicks++ }, loading = true, modifier = Modifier.testTag("btn"))
            }
        }
        val node = onNodeWithTag("btn").fetchSemanticsNode()
        val state = node.config.getOrElseNullable(CoreSemanticsProperties.StateDescription) { null }
        assertEquals("Carregando", state)
        onNodeWithTag("btn").assertIsNotEnabled()
        onNodeWithTag("btn").performClick()
        waitForIdle()
        assertEquals(0, clicks)
    }

    @Test
    fun loadingKeepsName() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzButton("Salvar", onClick = {}, loading = true, modifier = Modifier.testTag("btn")) }
        }
        // The label stays in the tree (name preserved) even while the spinner shows.
        onNodeWithText("Salvar").assertExists()
    }

    // Era `loadingKeepsWidth`, que comparava a largura do ocioso com a do carregando.
    // A premissa caiu: o export desenha `◌ Criando grupo…`, spinner **ao lado** do
    // rótulo, então o carregando é legitimamente mais largo. O que importa e continua
    // testado é que o rótulo não é escondido atrás do spinner.
    @Test
    fun loadingPutsSpinnerBesideLabel() = runComposeUiTest {
        setContent {
            SaqzTheme {
                Column {
                    SaqzButton("Salvando…", onClick = {}, loading = false, modifier = Modifier.testTag("idle"))
                    SaqzButton("Salvando…", onClick = {}, loading = true, modifier = Modifier.testTag("busy"))
                }
            }
        }
        // Os dois rótulos seguem na árvore, com o mesmo texto.
        onAllNodesWithText("Salvando…").assertCountEquals(2)
        val idle = onNodeWithTag("idle").getUnclippedBoundsInRoot().width
        val busy = onNodeWithTag("busy").getUnclippedBoundsInRoot().width
        // Ao lado, não por cima: o spinner cresce o botão. Voltar a sobrepor rótulo e
        // spinner devolveria larguras iguais e reprovaria aqui.
        assertTrue(busy > idle, "carregando abre espaço para o spinner: $busy contra $idle")
    }

    @Test
    fun mdHeightComesFromToken() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzButton("Salvar", onClick = {}, modifier = Modifier.testTag("btn")) }
        }
        // 52dp do `.saqz-btn--md{min-height:52px}` do export, lido do token — não há
        // altura de botão escrita em SaqzButton.kt.
        assertEquals(SaqzMetrics.Default.buttonHeight, onNodeWithTag("btn").getUnclippedBoundsInRoot().height)
    }

    @Test
    fun pressAlsoOffsetsByToken() = runComposeUiTest {
        setContent {
            SaqzTheme { SaqzButton("Salvar", onClick = {}, modifier = Modifier.testTag("btn")) }
        }
        onNodeWithTag("btn").performTouchInput { down(center) }
        waitForIdle()
        // `translateY(1px)` do `:active` do export, vindo de motion.pressOffset.
        assertEquals(SaqzMotionPolicy.Normal.pressOffset.value, pressFeedback("btn").offsetY)
        onNodeWithTag("btn").performTouchInput { up() }
    }

    @Test
    fun reducedMotionKeepsFeedback() = runComposeUiTest {
        setContent {
            SaqzTheme(preferences = SaqzAccessibilityPreferences(reduceMotion = true)) {
                SaqzButton("Salvar", onClick = {}, modifier = Modifier.testTag("btn"))
            }
        }
        onNodeWithTag("btn").performTouchInput { down(center) }
        waitForIdle()
        val feedback = pressFeedback("btn")
        // Reduced motion drops spatial scale but must keep opacity feedback.
        assertEquals(1f, feedback.scale)
        assertEquals(0f, feedback.offsetY, "reduced motion also drops the vertical nudge")
        assertTrue(feedback.alpha < 1f, "opacity feedback kept under reduced motion")
        onNodeWithTag("btn").performTouchInput { up() }
    }

    @Test
    fun filledIconButtonKeepsNameAndTarget() = runComposeUiTest {
        var clicks = 0
        setContent {
            SaqzTheme {
                SaqzIconButton(
                    onClick = { clicks++ },
                    contentDescription = "Criar jogo",
                    filled = true,
                    modifier = Modifier.testTag("fab"),
                ) { SaqzIcon(SaqzIcons.Plus, tint = SaqzTheme.colors.onPrimary) }
            }
        }
        // O fundo azul não come nem o nome acessível nem os 44×44 que o export exige.
        val bounds = onNodeWithTag("fab").getUnclippedBoundsInRoot()
        assertEquals(SaqzMetrics.Default.iconButtonSize, bounds.width)
        assertEquals(SaqzMetrics.Default.iconButtonSize, bounds.height)
        onNodeWithContentDescription("Criar jogo").assertExists()
        onNodeWithTag("fab").performClick()
        waitForIdle()
        assertEquals(1, clicks)
    }

    private fun ComposeUiTest.pressFeedback(tag: String): SaqzPressFeedback {
        val value = onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrElseNullable(SaqzPressFeedbackKey) { null }
        assertNotNull(value, "press feedback semantics present")
        return value
    }

    private fun assertAtLeast(minimum: Double, actual: Double) =
        assertTrue(actual >= minimum, "expected >= $minimum but was $actual")

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) +
            0.7152 * linearize(color.green) +
            0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
