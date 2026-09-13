package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

class PaymentPayerRequest {
    var name: String = ""
    var cpfCnpj: String = ""
}

class PaymentCommand {
    var requestId: UUID? = null
    var accountId: UUID? = null
    var fingerprint: String? = null
    var accepted: Boolean = false
    var method: PaymentMethod? = null
    var payer: PaymentPayerRequest? = null
}

@RestController
@RequestMapping("/api/receivables")
class OneOffPaymentsController(private val actors: FinancialActorResolver, private val service: OneOffPayments) {
    @PostMapping("/charges/{chargeId}/preview")
    fun preview(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable chargeId: UUID, @RequestBody body: PaymentCommand) =
        execute(identity, body) { service.preview(chargeId, body.accountId ?: invalid(), it) }
    @PostMapping("/charges/{chargeId}/approve")
    fun approve(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable chargeId: UUID, @RequestBody body: PaymentCommand) =
        execute(identity, body) { service.approve(chargeId, body.accountId ?: invalid(), it, body.fingerprint.orEmpty(), body.accepted) }
    @GetMapping("/orders/{orderId}")
    fun get(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable orderId: UUID): ResponseEntity<*> =
        response(service.get(orderId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))
    @PostMapping("/orders/{orderId}/instruments")
    fun instrument(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable orderId: UUID, @RequestBody body: PaymentCommand) =
        execute(identity, body) { service.instrument(orderId, it, body.method ?: invalid(), body.fingerprint.orEmpty(), body.accepted, body.payer?.let { payer -> PaymentPayer(payer.name, payer.cpfCnpj) } ?: invalid()) }
    @PostMapping("/orders/{orderId}/cancel")
    fun cancel(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable orderId: UUID, @RequestBody body: PaymentCommand) =
        execute(identity, body) { service.cancel(orderId, it) }
    @PostMapping("/orders/{orderId}/reconcile")
    fun reconcile(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable orderId: UUID, @RequestBody body: PaymentCommand) =
        execute(identity, body) { service.reconcile(orderId, it) }
    private fun invalid(): Nothing = throw IllegalArgumentException()
    private fun execute(identity: RequestIdentity, body: PaymentCommand, action: (FinancialRequest) -> FinancialResult<*>): ResponseEntity<*> {
        val requestId = body.requestId ?: UUID.randomUUID()
        val result = try {
            require(body.requestId != null)
            action(FinancialRequest(requestId, actors.resolve(identity)))
        } catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId) }
        return response(result)
    }
    private fun response(result: FinancialResult<*>): ResponseEntity<*> = ResponseEntity.status(when (result) {
        is FinancialResult.Success -> 200
        is FinancialResult.Failure -> when (result.error) {
            FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
            FinancialError.CONFLICT -> 409
            FinancialError.INVALID_INPUT -> 400
            FinancialError.CONFIGURATION_UNAVAILABLE, FinancialError.PROVIDER_UNAVAILABLE -> 503
            FinancialError.RESULT_PENDING -> 202
            else -> 403
        }
    }).header("Cache-Control", "no-store").body(result)
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
