package br.com.saqz.designsystem.theme

import br.com.saqz.designsystem.resources.Res
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlin.test.Test
import kotlin.test.assertTrue

// `_exceptions` é a lista do que NÃO sai do export, e serve para quem reconciliar um
// export novo não "corrigir" um override deliberado. Documentação que aponta para token
// que não existe mais é pior do que nenhuma: este teste prende as duas pontas.
class UiContractExceptionsTest {
    private suspend fun contract() = Json.parseToJsonElement(
        Res.readBytes("files/ui-contract.json").decodeToString(),
    ).jsonObject

    @Test
    fun everyExceptionPointsAtALiveToken() = runTest {
        val root = contract()
        val exceptions = root.getValue("_exceptions").jsonObject.keys.filterNot { it.startsWith("_") }
        for (path in exceptions) {
            val (block, token) = path.split(".").also {
                assertTrue(it.size == 2, "chave de _exceptions fora do formato bloco.token: $path")
            }
            val blockObject = root[block]?.jsonObject
            assertTrue(blockObject != null, "_exceptions cita o bloco inexistente '$block'")
            assertTrue(token in blockObject, "_exceptions cita '$path', que não existe mais no contrato")
        }
    }

    @Test
    fun everyExceptionSaysWhatTheExportWantsAndWhy() = runTest {
        val exceptions = contract().getValue("_exceptions").jsonObject
        for ((path, entry) in exceptions) {
            if (path.startsWith("_")) continue
            val fields = entry.jsonObject.keys
            assertTrue(
                fields.containsAll(setOf("export", "nosso", "motivo")),
                "exceção '$path' precisa de export/nosso/motivo, tem $fields",
            )
        }
    }
}
