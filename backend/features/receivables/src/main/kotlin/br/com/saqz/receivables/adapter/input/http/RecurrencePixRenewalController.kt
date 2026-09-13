package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

class PixRenewalCommand {
    var requestId: UUID? = null
    var dueDate: LocalDate? = null
}

@RestController
@RequestMapping("/api/receivables/orders")
class RecurrencePixRenewalController(private val actors: FinancialActorResolver,
    private val payments: OneOffPayments) {
    @PostMapping("/{orderId}/pix-renewal")
    fun renew(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable orderId: UUID,
              @RequestBody body: PixRenewalCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: UUID.randomUUID()
        val result = try {
            require(body.requestId != null && body.dueDate != null)
            payments.renewPix(orderId, body.dueDate!!, FinancialRequest(requestId, actors.resolve(identity)))
        } catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId) }
        return response(result)
    }

    @GetMapping("/{orderId}/pix-renewal/{requestId}")
    fun get(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable orderId: UUID,
            @PathVariable requestId: UUID): ResponseEntity<*> =
        response(payments.pixRenewal(orderId, FinancialRequest(requestId, actors.resolve(identity))))

    private fun response(result: FinancialResult<*>): ResponseEntity<*> = ResponseEntity.status(when (result) {
        is FinancialResult.Success -> 200
        is FinancialResult.Failure -> when (result.error) {
            FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
            FinancialError.CONFLICT -> 409
            FinancialError.INVALID_INPUT -> 400
            FinancialError.RESULT_PENDING -> 202
            FinancialError.CONFIGURATION_UNAVAILABLE, FinancialError.PROVIDER_UNAVAILABLE -> 503
            else -> 403
        }
    }).header("Cache-Control", "no-store").body(result)
}
