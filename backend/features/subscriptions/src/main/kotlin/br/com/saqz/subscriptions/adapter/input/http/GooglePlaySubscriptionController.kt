package br.com.saqz.subscriptions.adapter.input.http

import br.com.saqz.sharedkernel.RequestIdentity
import br.com.saqz.sharedkernel.actor.AuthenticatedActorResolver
import br.com.saqz.subscriptions.application.GetMySubscription
import br.com.saqz.subscriptions.application.GetMySubscriptionResult
import br.com.saqz.subscriptions.application.GooglePlayNotificationCommand
import br.com.saqz.subscriptions.application.ProcessGooglePlayNotification
import br.com.saqz.subscriptions.application.ProcessGooglePlayNotificationResult
import br.com.saqz.subscriptions.application.SubmitGooglePlayPurchase
import br.com.saqz.subscriptions.application.SubmitGooglePlayPurchaseResult
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import java.util.Base64

data class SubmitGooglePlayPurchaseRequest @JsonCreator constructor(
    @JsonProperty("productId") val productId: String?,
    @JsonProperty("purchaseToken") val purchaseToken: String?,
)

class GooglePlayPurchaseInvalidException : RuntimeException()

class GooglePlayPurchaseOwnedByAnotherAccountException : RuntimeException()

class GooglePlayNotificationUnauthorizedException : RuntimeException()

/** Contrato em docs/subscriptions/google-play.md. */
@RestController
class GooglePlaySubscriptionController(
    private val actors: AuthenticatedActorResolver,
    private val submitPurchase: SubmitGooglePlayPurchase,
    private val getMySubscription: GetMySubscription,
) {
    @PostMapping("/subscriptions/google-play/purchases")
    fun submit(
        @AuthenticationPrincipal identity: RequestIdentity,
        @RequestBody request: SubmitGooglePlayPurchaseRequest,
    ): MySubscriptionResponse {
        val ownerUserId = actors.resolve(identity).userId
        val productId = request.productId?.takeIf { it.isNotBlank() && it.length <= MAX_PRODUCT_ID }
        val token = request.purchaseToken?.takeIf { it.isNotBlank() && it.length <= MAX_PURCHASE_TOKEN }
        if (productId == null || token == null) throw GooglePlayPurchaseInvalidException()
        when (submitPurchase.execute(ownerUserId, productId, token)) {
            SubmitGooglePlayPurchaseResult.Invalid -> throw GooglePlayPurchaseInvalidException()
            SubmitGooglePlayPurchaseResult.OwnedByAnotherAccount -> throw GooglePlayPurchaseOwnedByAnotherAccountException()
            SubmitGooglePlayPurchaseResult.Accepted -> Unit
        }
        return when (val result = getMySubscription.execute(ownerUserId)) {
            GetMySubscriptionResult.NotFound -> throw SubscriptionNotFoundException()
            is GetMySubscriptionResult.Found -> result.subscription.toResponse()
        }
    }

    private companion object {
        const val MAX_PRODUCT_ID = 128
        const val MAX_PURCHASE_TOKEN = 4_096
    }
}

/**
 * Push do Pub/Sub com as Real-time Developer Notifications. O segredo vem na query da URL
 * cadastrada na assinatura push; a mensagem só aponta o token que mudou.
 */
@RestController
class GooglePlayNotificationController(
    private val processNotification: ProcessGooglePlayNotification,
    private val objectMapper: ObjectMapper = ObjectMapper(),
) {
    @PostMapping("/webhooks/google-play")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun handle(
        @RequestParam(name = "token", required = false) token: String?,
        @RequestBody rawBody: String,
    ) {
        val message = runCatching { objectMapper.readTree(rawBody) }.getOrNull()?.path("message")
        val data = message?.path("data")?.asText(null)
            ?.let { runCatching { objectMapper.readTree(Base64.getDecoder().decode(it)) }.getOrNull() }
        val command = GooglePlayNotificationCommand(
            messageId = message?.path("messageId")?.asText(null).orEmpty().ifBlank { "sem-id" },
            packageName = data?.path("packageName")?.asText(null),
            notificationType = data?.path("subscriptionNotification")?.path("notificationType")
                ?.takeIf(JsonNode::isNumber)?.intValue(),
            purchaseToken = data?.let(::purchaseToken),
        )
        when (processNotification.execute(token, command)) {
            ProcessGooglePlayNotificationResult.Unauthorized -> throw GooglePlayNotificationUnauthorizedException()
            ProcessGooglePlayNotificationResult.Accepted -> Unit
        }
    }

    /** Assinatura mudou, ou foi estornada/revogada (voided de produto do tipo assinatura = 1). */
    private fun purchaseToken(data: JsonNode): String? {
        data.path("subscriptionNotification").path("purchaseToken").asText(null)?.let { return it }
        val voided = data.path("voidedPurchaseNotification")
        return voided.path("purchaseToken").asText(null)?.takeIf { voided.path("productType").asInt() == 1 }
    }
}
