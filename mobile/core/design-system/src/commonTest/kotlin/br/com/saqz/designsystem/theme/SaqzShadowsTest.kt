package br.com.saqz.designsystem.theme

import androidx.compose.ui.graphics.Color
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

class SaqzShadowsTest {
    private val shadows = SaqzShadows.Default

    // Diferente de colors, aqui o alfa é do contrato: sombra sem alfa não é sombra, então
    // guardar só o hex opaco perderia metade do token.
    private val registry: Map<String, SaqzShadow> = mapOf(
        "sheet" to shadows.sheet,
        "toast" to shadows.toast,
    )

    private suspend fun contractShadows(): Map<String, JsonObject> {
        val root = Json.parseToJsonElement(
            Res.readBytes("files/ui-contract.json").decodeToString(),
        ).jsonObject
        return root.getValue("shadows").jsonObject.mapValues { it.value.jsonObject }
    }

    @Test
    fun valuesMatchSpec() = runTest {
        val contract = contractShadows()
        for ((name, shadow) in registry) {
            val spec = contract.getValue(name)
            fun number(field: String) = spec.getValue(field).jsonPrimitive.float
            assertEquals(number("offsetX").dp, shadow.offsetX, "shadow $name offsetX")
            assertEquals(number("offsetY").dp, shadow.offsetY, "shadow $name offsetY")
            assertEquals(number("blur").dp, shadow.blur, "shadow $name blur")
            val hex = spec.getValue("color").jsonPrimitive.content.removePrefix("#").toLong(16)
            assertEquals(Color(0xFF000000L or hex).copy(alpha = number("alpha")), shadow.color, "shadow $name color")
        }
    }

    @Test
    fun inventoryIsClosed() = runTest {
        assertEquals(contractShadows().keys, registry.keys)
    }

    @Test
    fun sheetLiftsAndToastFalls() {
        // O par de sinais é o ponto do token: `--shadow-sheet` tem deslocamento NEGATIVO
        // (o painel sobe da borda de baixo) e o toast tem positivo. Quem trocar o sheet
        // por `elevation` perde exatamente isto, e o teste é o que barra a troca.
        assertTrue(shadows.sheet.offsetY < 0.dp, "sheet deveria subir, tem ${shadows.sheet.offsetY}")
        assertTrue(shadows.toast.offsetY > 0.dp, "toast deveria cair, tem ${shadows.toast.offsetY}")
    }
}
