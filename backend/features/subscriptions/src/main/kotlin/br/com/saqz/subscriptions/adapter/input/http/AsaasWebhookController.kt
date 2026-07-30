package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.subscriptions.application.AsaasWebhookCommand
import br.com.saqz.subscriptions.application.AsaasWebhookProcessor
import br.com.saqz.subscriptions.application.ProcessAsaasWebhook
import br.com.saqz.subscriptions.application.ProcessAsaasWebhookResult
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import jakarta.servlet.http.HttpServletRequest
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

class AsaasWebhookUnauthorizedException : RuntimeException()

/** Local subscription row not ready yet — Asaas should retry the delivery. */
class AsaasWebhookSubscriptionNotReadyException : RuntimeException()

@RestController
class AsaasWebhookController(
    private val processAsaasWebhook: AsaasWebhookProcessor,
    private val objectMapper: ObjectMapper = jacksonObjectMapper(),
) {
    @PostMapping("/webhooks/asaas")
    @ResponseStatus(HttpStatus.OK)
    fun handle(
        @RequestBody rawBody: String,
        request: HttpServletRequest,
    ) {
        val token = request.getHeader(ProcessAsaasWebhook.WEBHOOK_TOKEN_HEADER)
        val root = runCatching { objectMapper.readTree(rawBody) }.getOrNull()
        val command = AsaasWebhookCommand(
            asaasEventId = root.textOrEmpty("id"),
            eventType = root.textOrEmpty("event"),
            asaasSubscriptionId = extractSubscriptionId(root),
            asaasPaymentId = extractPaymentId(root),
            rawPayload = rawBody,
        )
        when (processAsaasWebhook.execute(token, command)) {
            ProcessAsaasWebhookResult.Unauthorized -> throw AsaasWebhookUnauthorizedException()
            ProcessAsaasWebhookResult.SubscriptionNotReady -> throw AsaasWebhookSubscriptionNotReadyException()
            ProcessAsaasWebhookResult.Accepted -> Unit
        }
    }

    private fun extractSubscriptionId(root: JsonNode?): String? {
        if (root == null) return null
        root.path("payment").path("subscription").asText(null)?.takeIf { it.isNotBlank() }?.let { return it }
        root.path("subscription").path("id").asText(null)?.takeIf { it.isNotBlank() }?.let { return it }
        root.path("subscription").asText(null)?.takeIf { it.isNotBlank() && !it.startsWith("{") }?.let { return it }
        return null
    }

    private fun extractPaymentId(root: JsonNode?): String? {
        if (root == null) return null
        return root.path("payment").path("id").asText(null)?.takeIf { it.isNotBlank() }
    }

    private fun JsonNode?.textOrEmpty(field: String): String =
        this?.path(field)?.asText(null).orEmpty()
}
