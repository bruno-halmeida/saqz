package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.AsaasWebhookCommand
import br.com.saqz.subscriptions.application.AsaasWebhookProcessor
import br.com.saqz.subscriptions.application.ProcessAsaasWebhook
import br.com.saqz.subscriptions.application.ProcessAsaasWebhookResult
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.mock.web.MockHttpServletRequest
import kotlin.test.assertEquals
import kotlin.test.assertNull

class AsaasWebhookControllerTest {
    @Test
    fun `parses payment confirmed payload and forwards token`() {
        val useCase = RecordingProcessor()
        val controller = AsaasWebhookController(useCase)
        val body =
            """
            {"id":"evt_abc","event":"PAYMENT_CONFIRMED","payment":{"subscription":"sub_xyz"}}
            """.trimIndent()
        val request = MockHttpServletRequest().apply {
            addHeader(ProcessAsaasWebhook.WEBHOOK_TOKEN_HEADER, "tok")
        }

        controller.handle(body, request)

        assertEquals("tok", useCase.lastToken)
        assertEquals(
            AsaasWebhookCommand(
                asaasEventId = "evt_abc",
                eventType = "PAYMENT_CONFIRMED",
                asaasSubscriptionId = "sub_xyz",
                rawPayload = body,
            ),
            useCase.lastCommand,
        )
    }

    @Test
    fun `parses subscription deleted nested object`() {
        val useCase = RecordingProcessor()
        val controller = AsaasWebhookController(useCase)
        val body =
            """
            {"id":"evt_del","event":"SUBSCRIPTION_DELETED","subscription":{"id":"sub_del"}}
            """.trimIndent()

        controller.handle(body, MockHttpServletRequest())

        assertEquals("sub_del", useCase.lastCommand?.asaasSubscriptionId)
    }

    @Test
    fun `invalid token becomes unauthorized exception`() {
        val useCase = RecordingProcessor(ProcessAsaasWebhookResult.Unauthorized)
        val controller = AsaasWebhookController(useCase)

        assertThrows<AsaasWebhookUnauthorizedException> {
            controller.handle("{}", MockHttpServletRequest())
        }
    }

    @Test
    fun `malformed json still reaches use case with empty ids`() {
        val useCase = RecordingProcessor()
        val controller = AsaasWebhookController(useCase)

        controller.handle("not-json", MockHttpServletRequest())

        assertEquals("", useCase.lastCommand?.asaasEventId)
        assertEquals("", useCase.lastCommand?.eventType)
        assertNull(useCase.lastCommand?.asaasSubscriptionId)
    }

    private class RecordingProcessor(
        private val result: ProcessAsaasWebhookResult = ProcessAsaasWebhookResult.Accepted,
    ) : AsaasWebhookProcessor {
        var lastToken: String? = null
        var lastCommand: AsaasWebhookCommand? = null

        override fun execute(providedToken: String?, command: AsaasWebhookCommand): ProcessAsaasWebhookResult {
            lastToken = providedToken
            lastCommand = command
            return result
        }
    }
}
