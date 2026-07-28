package br.com.saqz.access.ui

import androidx.compose.ui.unit.dp
import br.com.saqz.access.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A amarra que o `SPEC_DEVIATION` do [AccessMetrics] promete: cada literal do chrome
 * contra a chave `fluxo1` do `ui-contract.json`.
 *
 * O contrato é o mesmo arquivo que o `SaqzFluxo1ContractTest` lê — o `build.gradle.kts`
 * desta feature acrescenta a pasta de recursos de teste do design system ao source set,
 * em vez de copiar o JSON. Mexer no número aqui sem mexer no contrato reprova, que é o
 * alarme que se quer para os seis tickets de tela que vêm depois.
 */
class AccessMetricsTest {
    private suspend fun fluxo1(): JsonObject = Json.parseToJsonElement(
        Res.readBytes("files/ui-contract.json").decodeToString(),
    ).jsonObject.getValue("fluxo1").jsonObject

    private fun JsonObject.group(name: String): JsonObject = getValue(name).jsonObject

    private fun JsonObject.number(name: String): Float = getValue(name).jsonPrimitive.float

    private fun JsonObject.text(name: String): String = getValue(name).jsonPrimitive.content

    @Test
    fun paddingMatchesTheContract() = runTest {
        val padding = fluxo1().group("padding")
        assertEquals(AccessMetrics.horizontalPadding, padding.number("horizontal").dp)
        assertEquals(AccessMetrics.topPadding, padding.number("topo").dp)
        assertEquals(AccessMetrics.spaciousTopPadding, padding.number("topoAmplo").dp)
    }

    @Test
    fun brandMatchesTheContract() = runTest {
        val large = fluxo1().group("marcaGrande")
        assertEquals(AccessMetrics.brandLargeSize, large.number("tamanho").dp)
        assertEquals(AccessMetrics.brandLargeRadius, large.number("raio").dp)
        assertEquals(AccessMetrics.brandLargeSymbol, large.number("simbolo").dp)
        assertEquals(AccessMetrics.letteringHeight, large.number("letteringAltura").dp)
        assertEquals(AccessMetrics.letteringGap, large.number("gap").dp)

        val small = fluxo1().group("marcaPequena")
        assertEquals(AccessMetrics.brandSmallSize, small.number("tamanho").dp)
        assertEquals(AccessMetrics.brandSmallRadius, small.number("raio").dp)
        assertEquals(AccessMetrics.brandSmallSymbol, small.number("simbolo").dp)
    }

    @Test
    fun titleMatchesTheContract() = runTest {
        val title = fluxo1().group("titulo")
        assertEquals(AccessMetrics.TITLE_SIZE, title.number("size"))
        assertEquals(AccessMetrics.SPACIOUS_TITLE_SIZE, title.number("sizeAmplo"))
        assertEquals(AccessMetrics.TITLE_LINE_HEIGHT_RATIO, title.number("lineHeight"))
        assertEquals(AccessMetrics.TITLE_TRACKING, title.number("tracking"))
        assertEquals(AccessMetrics.TITLE_WEIGHT, title.getValue("weight").jsonPrimitive.int)
    }

    @Test
    fun subtitleMatchesTheContract() = runTest {
        val subtitle = fluxo1().group("subtitulo")
        assertEquals(AccessMetrics.SUBTITLE_SIZE, subtitle.number("size"))
        assertEquals(AccessMetrics.SUBTITLE_LINE_HEIGHT_RATIO, subtitle.number("lineHeight"))
        // O teto é uma faixa no export (290–300) porque varia por tela; o chrome usa o
        // mais largo, e o que o contrato trava é que ele esteja dentro da faixa.
        assertEquals(AccessMetrics.subtitleMaxWidth, subtitle.number("maxWidthMax").dp)
        assertTrue(
            AccessMetrics.subtitleMaxWidth >= subtitle.number("maxWidthMin").dp,
            "o teto do subtítulo saiu da faixa do export",
        )
    }

    // O caso que importa da onda: os seis números de cada curva remontam exatamente o
    // path do export. Copiar meio path ou trocar um controle de lugar morre aqui, e é o
    // defeito plausível — as duas curvas têm a mesma forma e números parecidos.
    @Test
    fun waveCurvesRebuildTheExportPaths() = runTest {
        val onda = fluxo1().group("onda")
        assertEquals(AccessMetrics.waveHeight, onda.number("altura").dp)
        assertEquals(
            "0 0 ${AccessMetrics.WAVE_VIEWPORT_WIDTH.plain()} ${AccessMetrics.WAVE_VIEWPORT_HEIGHT.plain()}",
            onda.text("viewBox"),
        )
        assertEquals(onda.group("camada1").text("path"), AccessMetrics.waveBack.toSvgPath())
        assertEquals(onda.group("camada2").text("path"), AccessMetrics.waveFront.toSvgPath())
    }

    @Test
    fun backLayerKeepsTheExportOpacity() = runTest {
        // A camada de trás é o azul da marca com alfa; o contrato escreve a cor como
        // `rgba(6,56,223,.16)`, então o que se confere é o alfa que o chrome aplica.
        val camada1 = fluxo1().group("onda").group("camada1").text("cor")
        assertEquals("rgba(6,56,223,.16)", camada1)
        assertEquals(".${camada1.substringAfterLast(",.").removeSuffix(")")}".toFloat(), AccessMetrics.WAVE_BACK_LAYER_ALPHA)
    }

    private fun Float.plain(): String = if (this == toInt().toFloat()) toInt().toString() else toString()

    private fun AccessWaveCurve.toSvgPath(): String = buildString {
        append("M0 ${startY.plain()} C ")
        append("${control1X.plain()} ${control1Y.plain()}, ")
        append("${control2X.plain()} ${control2Y.plain()}, ")
        append("${AccessMetrics.WAVE_VIEWPORT_WIDTH.plain()} ${endY.plain()} ")
        append("L${AccessMetrics.WAVE_VIEWPORT_WIDTH.plain()} ${AccessMetrics.WAVE_VIEWPORT_HEIGHT.plain()} ")
        append("0 ${AccessMetrics.WAVE_VIEWPORT_HEIGHT.plain()}Z")
    }
}
