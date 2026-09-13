package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.LocalDate
import java.util.UUID

class RecurrenceCommand {
    var requestId: UUID? = null
    var accountId: UUID? = null
    var groupId: UUID? = null
    var method: PaymentMethod? = null
    var firstDueDate: LocalDate? = null
    var fingerprint: String? = null
    var accepted: Boolean = false
    var payer: PaymentPayerRequest? = null
}

class RecurrenceMutation { var requestId: UUID? = null }

@RestController
@RequestMapping("/api/receivables/recurrences")
class RecurrencePaymentsController(private val actors: FinancialActorResolver,
    private val service: RecurrencePayments) {
    @PostMapping("/preview")
    fun preview(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody body: RecurrenceCommand) = execute(identity, body.requestId) { request ->
        service.preview(body.accountId ?: invalid(), body.groupId ?: invalid(), body.method ?: invalid(), body.firstDueDate ?: invalid(), request)
    }

    @PostMapping
    fun authorize(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody body: RecurrenceCommand) = execute(identity, body.requestId) { request ->
        service.authorize(body.accountId ?: invalid(), body.groupId ?: invalid(), body.method ?: invalid(), body.firstDueDate ?: invalid(),
            body.fingerprint.orEmpty(), body.accepted, body.payer?.let { PaymentPayer(it.name, it.cpfCnpj) } ?: invalid(), request)
    }

    @GetMapping("/{id}")
    fun get(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable id: UUID): ResponseEntity<*> =
        response(service.get(id, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @GetMapping("/current")
    fun current(@AuthenticationPrincipal identity: RequestIdentity, @RequestParam accountId: UUID,
                @RequestParam groupId: UUID): ResponseEntity<*> =
        response(service.current(accountId, groupId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @GetMapping("/by-request/{requestId}")
    fun byRequest(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable requestId: UUID): ResponseEntity<*> =
        response(service.byRequest(FinancialRequest(requestId, actors.resolve(identity))))

    @PostMapping("/{id}/cancel")
    fun cancel(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable id: UUID, @RequestBody body: RecurrenceMutation) =
        execute(identity, body.requestId) { service.cancel(id, it) }

    @PostMapping("/{id}/resume")
    fun resume(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable id: UUID, @RequestBody body: RecurrenceCommand) = execute(identity, body.requestId) { request ->
        service.resume(id, body.accountId ?: invalid(), body.groupId ?: invalid(), body.method ?: invalid(), body.firstDueDate ?: invalid(),
            body.fingerprint.orEmpty(), body.accepted, body.payer?.let { PaymentPayer(it.name, it.cpfCnpj) } ?: invalid(), request)
    }

    private fun invalid(): Nothing = throw IllegalArgumentException()
    private fun execute(identity: RequestIdentity, requestId: UUID?, action: (FinancialRequest) -> FinancialResult<*>): ResponseEntity<*> {
        val id = requestId ?: UUID.randomUUID()
        val result = try { require(requestId != null); action(FinancialRequest(id, actors.resolve(identity))) }
        catch (_: IllegalArgumentException) { FinancialResult.Failure(FinancialError.INVALID_INPUT, id) }
        return response(result)
    }
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
