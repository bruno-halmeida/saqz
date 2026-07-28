package br.com.saqz.designsystem

import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import br.com.saqz.designsystem.theme.SaqzColorTokens
import br.com.saqz.designsystem.theme.SaqzMotionPolicy
import br.com.saqz.designsystem.theme.SaqzTheme
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.pow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalTestApi::class)
class SaqzInlineAlertTest {
    private val tokens = SaqzColorTokens.Light

    @Test
    fun emphasisIsBoldAndTheRestIsNot() {
        // O par (destaque, resto) do 1i, montado pelo componente. Se este caso precisar
        // de `buildAnnotatedString` na tela, a API falhou.
        val annotated = saqzInlineAlertText(
            text = "E-mail ou senha incorretos. Confira os dados e tente de novo.",
            emphasis = "E-mail ou senha incorretos.",
        )
        val bold = annotated.spanStyles.single()
        assertEquals(FontWeight.Bold, bold.item.fontWeight)
        assertEquals(0, bold.start)
        assertEquals("E-mail ou senha incorretos.".length, bold.end)
    }

    @Test
    fun emphasisInTheMiddleOfTheSentenceIsFound() {
        // O destaque não é obrigatoriamente prefixo: `indexOf` acha no meio, que é o
        // caso que uma implementação por `startsWith` deixaria passar.
        val annotated = saqzInlineAlertText(
            text = "Quase lá: revise 3 campos para criar sua conta.",
            emphasis = "revise 3 campos",
        )
        val bold = annotated.spanStyles.single()
        assertEquals("Quase lá: ".length, bold.start)
        assertEquals("Quase lá: revise 3 campos".length, bold.end)
    }

    @Test
    fun textWithoutEmphasisHasNoSpans() {
        // O aviso do 1k é uma frase inteira em peso normal.
        val annotated = saqzInlineAlertText("Esse código expirou. Peça um novo para continuar.", emphasis = null)
        assertTrue(annotated.spanStyles.isEmpty(), "sem destaque não deveria haver span")
        assertEquals("Esse código expirou. Peça um novo para continuar.", annotated.text)
    }

    @Test
    fun emphasisThatIsNotInTheTextIsIgnored() {
        // Falha de digitação no destaque não pode engolir a frase: o texto continua
        // inteiro, só sem o negrito.
        val annotated = saqzInlineAlertText("Código incorreto.", emphasis = "Codigo incorreto.")
        assertEquals("Código incorreto.", annotated.text)
        assertTrue(annotated.spanStyles.isEmpty())
    }

    @Test
    fun textIsRenderedWhole() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInlineAlert(
                    text = "E-mail ou senha incorretos. Confira os dados e tente de novo.",
                    emphasis = "E-mail ou senha incorretos.",
                    tone = SaqzInlineAlertTone.Error,
                )
            }
        }
        // Uma frase só, num nó só: o destaque é peso, não um segundo Text — que é o que
        // partiria o anúncio do leitor de tela em dois.
        onNodeWithText("E-mail ou senha incorretos. Confira os dados e tente de novo.").assertExists()
    }

    @Test
    fun errorAndWarningInterruptTheScreenReader() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInlineAlert(
                    text = "Esse código expirou. Peça um novo para continuar.",
                    tone = SaqzInlineAlertTone.Warning,
                    modifier = Modifier.testTag("alerta"),
                )
            }
        }
        assertEquals(LiveRegionMode.Assertive, liveRegionOf("alerta"))
    }

    @Test
    fun successWaitsItsTurn() = runComposeUiTest {
        setContent {
            SaqzTheme {
                SaqzInlineAlert(
                    text = "Enviamos um novo código para o seu e-mail.",
                    emphasis = "Enviamos um novo código para o seu e-mail.",
                    tone = SaqzInlineAlertTone.Success,
                    modifier = Modifier.testTag("alerta"),
                )
            }
        }
        assertEquals(LiveRegionMode.Polite, liveRegionOf("alerta"))
    }

    @Test
    fun durationsComeFromTheContract() = runTest {
        val entrada = fluxo1().group("entrada")
        assertEquals(
            entrada.number("erroDurationMillis").toInt(),
            saqzInlineAlertDurationMillis(SaqzInlineAlertTone.Error),
        )
        assertEquals(
            entrada.number("sucessoDurationMillis").toInt(),
            saqzInlineAlertDurationMillis(SaqzInlineAlertTone.Success),
        )
        // O aviso não tem duração própria no export e anda com o erro.
        assertEquals(
            saqzInlineAlertDurationMillis(SaqzInlineAlertTone.Error),
            saqzInlineAlertDurationMillis(SaqzInlineAlertTone.Warning),
        )
    }

    @Test
    fun reduceMotionDropsTheDisplacementOnly() = runTest {
        assertEquals(fluxo1().group("entrada").number("translateY").dp, saqzInlineAlertOffset(SaqzMotionPolicy.Normal))
        assertEquals(0.dp, saqzInlineAlertOffset(SaqzMotionPolicy.Reduced))
        // A duração não encolhe: com Reduce Motion o que some é o deslocamento, e a
        // opacidade continua sendo o sinal de "isto é novo".
        assertEquals(280, saqzInlineAlertDurationMillis(SaqzInlineAlertTone.Error))
    }

    @Test
    fun fillsAndTextsMatchTheContract() = runTest {
        val alerta = fluxo1().group("alertaInline")
        val expected = mapOf(
            SaqzInlineAlertTone.Error to Triple("erro", tokens.errorForeground, 0.08f),
            SaqzInlineAlertTone.Success to Triple("sucesso", tokens.success, 0.10f),
            SaqzInlineAlertTone.Warning to Triple("aviso", tokens.warning, 0.12f),
        )
        for ((tone, spec) in expected) {
            val (group, source, alpha) = spec
            val (foreground, container) = saqzInlineAlertColors(tone, tokens)
            assertEquals(parseHex(alerta.group(group).text("texto")), foreground, "texto do $group")
            assertEquals(source.copy(alpha = alpha), container, "fundo do $group")
        }
    }

    @Test
    fun darkenedTextsAreAAAndTheTokensAreNot() {
        // O par de asserções é o registro do porquê dos três hex escurecidos: o token de
        // feedback sobre o próprio fundo tintado não passa AA. Trocar `#a3262a` por
        // `--saqz-error` "para usar o token" reprova aqui, que é a intenção.
        val cases = listOf(
            Triple(SaqzInlineAlertTone.Error, tokens.errorForeground, 0.08f),
            Triple(SaqzInlineAlertTone.Success, tokens.success, 0.10f),
            Triple(SaqzInlineAlertTone.Warning, tokens.warning, 0.12f),
        )
        for ((tone, token, alpha) in cases) {
            val fill = composite(token, alpha, over = tokens.surface)
            val (foreground, _) = saqzInlineAlertColors(tone, tokens)
            assertAtLeast(4.5, contrast(foreground, fill))
            assertBelow(4.5, contrast(token, fill))
        }
    }

    private fun ComposeUiTest.liveRegionOf(tag: String) =
        onNodeWithTag(tag).fetchSemanticsNode().config
            .getOrElseNullable(SemanticsProperties.LiveRegion) { null }

    private suspend fun fluxo1(): JsonObject = Json.parseToJsonElement(
        Res.readBytes("files/ui-contract.json").decodeToString(),
    ).jsonObject.getValue("fluxo1").jsonObject

    private fun JsonObject.group(name: String): JsonObject = getValue(name).jsonObject

    private fun JsonObject.number(name: String): Float = getValue(name).jsonPrimitive.float

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

    private fun parseHex(hex: String): Color = Color(0xFF000000L or hex.removePrefix("#").toLong(16))

    private fun assertAtLeast(minimum: Double, actual: Double) =
        assertTrue(actual >= minimum, "expected >= $minimum but was $actual")

    private fun assertBelow(ceiling: Double, actual: Double) =
        assertTrue(actual < ceiling, "expected < $ceiling but was $actual")

    private fun composite(source: Color, alpha: Float, over: Color): Color = Color(
        red = source.red * alpha + over.red * (1 - alpha),
        green = source.green * alpha + over.green * (1 - alpha),
        blue = source.blue * alpha + over.blue * (1 - alpha),
    )

    private fun contrast(a: Color, b: Color): Double {
        val la = relativeLuminance(a)
        val lb = relativeLuminance(b)
        return (maxOf(la, lb) + 0.05) / (minOf(la, lb) + 0.05)
    }

    private fun relativeLuminance(color: Color): Double =
        0.2126 * linearize(color.red) + 0.7152 * linearize(color.green) + 0.0722 * linearize(color.blue)

    private fun linearize(channel: Float): Double {
        val c = channel.toDouble()
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
}
