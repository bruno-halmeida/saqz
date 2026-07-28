package br.com.saqz.designsystem.theme

import androidx.compose.ui.unit.dp
import br.com.saqz.designsystem.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * O bloco `fluxo1` do contrato é a única cópia versionada dos números das 11 telas de
 * autenticação — as telas ainda não existem, e é por isso que ele precisa de teste:
 * número que ninguém lê apodrece calado.
 *
 * Duas coisas são conferidas. A primeira é o inventário fechado: o bloco tem
 * exatamente os grupos que o export descreve, então tela nova não acrescenta número
 * solto sem passar por aqui. A segunda é a amarra com os tokens vivos — onde o fluxo 1
 * reusa um número que já é token (o voltar de 44, o botão de 52, o raio de 10 da caixa
 * de código), o teste liga os dois. Mexer no token sem mexer no contrato reprova, que é
 * exatamente o alarme que se quer.
 */
class SaqzFluxo1ContractTest {
    private val metrics = SaqzMetrics.Default

    private suspend fun contract(): JsonObject = Json.parseToJsonElement(
        Res.readBytes("files/ui-contract.json").decodeToString(),
    ).jsonObject

    private suspend fun fluxo1(): JsonObject = contract().getValue("fluxo1").jsonObject

    private fun JsonObject.group(name: String): JsonObject = getValue(name).jsonObject

    private fun JsonObject.number(name: String): Float = getValue(name).jsonPrimitive.float

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

    @Test
    fun inventoryIsClosed() = runTest {
        val groups = fluxo1().keys.filterNot { it.startsWith("_") }.toSet()
        assertEquals(
            setOf(
                "frame",
                "padding",
                "onda",
                "marcaGrande",
                "marcaPequena",
                "voltar",
                "titulo",
                "subtitulo",
                "campo",
                "botaoPrimario",
                "gapDosCampos",
                "caixaDeCodigo",
                "alertaInline",
                "senhaAlterada",
                "entrada",
            ),
            groups,
        )
    }

    @Test
    fun frameIsTheExportViewport() = runTest {
        val frame = fluxo1().group("frame")
        assertEquals(390f, frame.number("largura"))
        assertEquals(844f, frame.number("altura"))
    }

    @Test
    fun literalsAreNotRoundedToTheGrid() = runTest {
        // Meia dúzia de números do fluxo 1 caem fora da grade de 4 de propósito (54 e 74
        // do campo, 26 do padding, 13.5 do texto do alerta, 1.8 do stroke). Arredondar
        // qualquer um "para alinhar à grade" é o erro que este caso tranca.
        val f = fluxo1()
        assertEquals(54f, f.group("campo").number("altura"))
        assertEquals(74f, f.group("campo").number("alturaComErro"))
        assertEquals(26f, f.group("padding").number("horizontal"))
        assertEquals(13.5f, f.group("alertaInline").number("textoSize"))
        assertEquals(1.8f, f.group("voltar").number("chevronStroke"))
        assertEquals(2.6f, f.group("senhaAlterada").number("checkStroke"))
        assertEquals(750f, f.group("titulo").number("weight"))
    }

    @Test
    fun numbersThatAlreadyHaveATokenMatchIt() = runTest {
        val f = fluxo1()
        // O voltar do fluxo 1 é o mesmo círculo de 44 do 10e — é por isso que este
        // ticket ganhou um parâmetro no SaqzIconButton em vez de um componente novo.
        assertEquals(metrics.iconButtonSize, f.group("voltar").number("tamanho").dp)
        assertEquals(metrics.buttonHeight, f.group("botaoPrimario").number("altura").dp)
        assertEquals(metrics.inputRadius, f.group("caixaDeCodigo").number("raio").dp)
        assertEquals(metrics.blockGap, f.group("caixaDeCodigo").number("gap").dp)
        assertEquals(metrics.blockGap, f.group("gapDosCampos").number("padrao").dp)
        assertEquals(metrics.cardRadius, f.group("alertaInline").number("raio").dp)
    }

    @Test
    fun colorsThatAlreadyHaveATokenMatchIt() = runTest {
        val root = contract()
        val f = root.getValue("fluxo1").jsonObject
        val colors = root.getValue("colors").jsonObject
        fun token(name: String) = colors.text(name).uppercase()
        fun hex(group: String, key: String) = f.group(group).text(key).uppercase()

        assertEquals(token("surface"), hex("frame", "fundo"))
        assertEquals(token("surface"), hex("voltar", "fundo"))
        // --saqz-border e --saqz-navy do colors.css: a linha do voltar é a mesma de card
        // e input, e o glifo é o navy de texto. A seta azul é da SaqzTopAppBar (VUL-61) e
        // continua só dela — igualar as duas é regressão de desenho, não limpeza.
        assertEquals(token("border"), hex("voltar", "corDaBorda"))
        assertEquals(token("textPrimary"), hex("voltar", "corDoChevron"))
        assertEquals(token("textSecondary"), hex("subtitulo", "cor"))
        assertEquals(token("primary"), f.group("onda").group("camada2").text("cor").uppercase())
        assertEquals(token("primary"), f.group("caixaDeCodigo").group("foco").text("corDaBorda").uppercase())
        assertEquals(token("primary"), f.group("caixaDeCodigo").group("cursor").text("cor").uppercase())
        assertEquals(
            token("errorForeground"),
            f.group("caixaDeCodigo").group("erro").text("corDaBorda").uppercase(),
        )
        // O círculo do 1h é o próprio `--saqz-success` tintado: o export o escreve em
        // rgba, então o que se confere é que os três canais são os do token — tintar o
        // verde errado a 12% passaria despercebido a olho.
        assertEquals(token("success"), f.group("senhaAlterada").text("fundo").rgbaToHex())
    }

    // "rgba(23,178,106,.12)" -> "#17B26A". Só o que o contrato precisa: três canais
    // decimais e um alfa que o chamador aplica por token.
    private fun String.rgbaToHex(): String = substringAfter('(').substringBefore(')')
        .split(',')
        .take(3)
        .joinToString("", prefix = "#") { channel ->
            channel.trim().toInt().toString(16).uppercase().padStart(2, '0')
        }

    @Test
    fun waveLayersAreClosedPaths() = runTest {
        // As duas ondas são preenchimento, não traço: path que não fecha em Z vira uma
        // faixa aberta e o preenchimento vaza. Copiar meio path do export é o defeito
        // plausível aqui, e ele morre neste caso.
        val onda = fluxo1().group("onda")
        listOf("camada1", "camada2").forEach { camada ->
            val path = onda.group(camada).text("path")
            assertTrue(path.startsWith("M0 "), "$camada deveria começar na borda esquerda: $path")
            assertTrue(path.endsWith("Z"), "$camada deveria fechar em Z: $path")
        }
        assertEquals("0 0 390 130", onda.text("viewBox"))
        assertEquals(130f, onda.number("altura"))
    }

    @Test
    fun entranceUsesOneEasingForEveryDuration() = runTest {
        // Três durações, uma curva só: o export escreve a mesma cubic-bezier nas três
        // entradas, e é ela que dá o "assentar" do fluxo. Duração é o que varia.
        val entrada = fluxo1().group("entrada")
        assertEquals("cubic-bezier(.22,1,.36,1)", entrada.text("easing"))
        assertEquals(8f, entrada.number("translateY"))
        val duracoes = listOf(
            entrada.number("erroDurationMillis"),
            entrada.number("sucessoDurationMillis"),
            entrada.number("checkDurationMillis"),
        )
        assertEquals(listOf(280f, 320f, 400f), duracoes)
    }
}
