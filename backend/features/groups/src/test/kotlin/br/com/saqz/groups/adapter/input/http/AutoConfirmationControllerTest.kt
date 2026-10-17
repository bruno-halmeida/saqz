package br.com.saqz.groups.adapter.input.http

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull

/** DEB-040: os apps 1.0.4–1.0.6 ainda chamam os endpoints e mandam os campos da confirmação automática. */
class AutoConfirmationControllerTest {
    private val group = UUID.randomUUID()
    private val controller = AutoConfirmationController()
    private val json = ObjectMapper()

    @Test
    fun `preferencia do mensalista e aceita e ignorada, a resposta e sempre desligado`() {
        assertFalse(controller.update("$group", AutoConfirmationRequest(true)).enabled)
        assertFalse(controller.update("$group", AutoConfirmationRequest(false)).enabled)
    }

    @Test
    fun `corpo vazio ou ausente e grupo desconhecido tambem nao dao erro`() {
        assertFalse(controller.update("$group", AutoConfirmationRequest(null)).enabled)
        assertFalse(controller.update("$group", null).enabled)
        assertFalse(controller.update("nao-e-um-grupo", AutoConfirmationRequest(true)).enabled)
    }

    @Test
    fun `o corpo antigo do app continua lendo`() {
        assertEquals(true, json.readValue("""{"enabled":true}""", AutoConfirmationRequest::class.java).enabled)
        assertEquals(null, json.readValue("{}", AutoConfirmationRequest::class.java).enabled)
    }

    @Test
    fun `criar grupo e salvar ajustes aceitam o interruptor do app antigo`() {
        val create = json.readValue(
            """{"requestId":"r","name":"Grupo","autoConfirmEnabled":true}""",
            CreateGroupRequest::class.java,
        )
        val update = json.readValue(
            """{"name":"Grupo","autoConfirmEnabled":true}""",
            UpdateGroupProfileRequest::class.java,
        )

        assertNotNull(create)
        assertNotNull(update)
        assertEquals(true, create.autoConfirmEnabled)
        assertEquals(true, update.autoConfirmEnabled)
    }
}
