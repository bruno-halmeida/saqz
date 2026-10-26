package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

class PaymentCommand {
    var requestId: UUID? = null
    var accountId: UUID? = null
    var fingerprint: String? = null
    var accepted: Boolean = false
    var method: PaymentMethod? = null
}

@RestController
class PaymentWebhookController(private val events: PaymentEventInbox) {
    @PostMapping("/api/receivables/webhooks/asaas/{accountId}")
    fun receive(@PathVariable accountId: UUID, @RequestHeader("asaas-access-token", required = false) token: String?,
                @RequestBody body: String): ResponseEntity<*> = try {
        if (events.accept(accountId, token.orEmpty(), body)) ResponseEntity.ok().build<Void>()
        else ResponseEntity.status(401).build<Void>()
    } catch (_: IllegalArgumentException) { ResponseEntity.badRequest().build<Void>() }
}

@RestController
class PaymentWebhookSetupController(private val actors: FinancialActorResolver, private val registration: PaymentWebhookRegistration) {
    @PostMapping("/api/receivables/accounts/{accountId}/webhook")
    fun configure(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
                  @RequestBody body: PaymentCommand): ResponseEntity<*> {
        val id = body.requestId ?: UUID.randomUUID()
        if (body.requestId == null) return ResponseEntity.badRequest().body(FinancialResult.Failure(FinancialError.INVALID_INPUT, id))
        val result = registration.configure(accountId, FinancialRequest(id, actors.resolve(identity)))
        return ResponseEntity.status(if (result is FinancialResult.Success) 200 else when ((result as FinancialResult.Failure).error) {
            FinancialError.CONFLICT -> 409
            FinancialError.CONFIGURATION_UNAVAILABLE -> 503
            else -> 404
        })
            .header("Cache-Control", "no-store").body(result)
    }
}
