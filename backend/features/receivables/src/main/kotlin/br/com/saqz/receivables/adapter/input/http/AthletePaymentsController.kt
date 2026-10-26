package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

class PaymentCodeCommand {
    var requestId: UUID? = null
    var method: PaymentMethod? = null
    var chargeIds: List<UUID>? = null
}

class PayerDocumentCommand {
    var requestId: UUID? = null
    var cpf: String? = null
}

/** Atleta logado pagando as próprias cobranças (seção H do plano v1). Mesmo envelope `{value|error,requestId}` de setembro. */
@RestController
@RequestMapping("/api/receivables")
class AthletePaymentsController(private val actors: FinancialActorResolver, private val service: AthletePayments) {
    @GetMapping("/groups/{groupId}/me")
    fun me(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID): ResponseEntity<*> =
        response(service.me(groupId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @PostMapping("/groups/{groupId}/payment-codes")
    fun create(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
               @RequestBody body: PaymentCodeCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: return invalid()
        val method = body.method ?: return invalid(requestId)
        val charges = body.chargeIds?.takeIf { it.isNotEmpty() } ?: return invalid(requestId)
        return response(service.create(groupId, method, charges, FinancialRequest(requestId, actors.resolve(identity))))
    }

    @GetMapping("/payment-codes/{codeId}")
    fun code(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable codeId: UUID): ResponseEntity<*> =
        response(service.get(codeId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @PutMapping("/me/document")
    fun document(@AuthenticationPrincipal identity: RequestIdentity, @RequestBody body: PayerDocumentCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: return invalid()
        val cpf = body.cpf ?: return invalid(requestId)
        return response(service.saveDocument(cpf, FinancialRequest(requestId, actors.resolve(identity))))
    }

    private fun invalid(requestId: UUID = UUID.randomUUID()) =
        response(FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId))
}

/** Status HTTP comum às rotas v1. */
fun receivablesResponse(result: FinancialResult<*>): ResponseEntity<*> = ResponseEntity.status(when (result) {
    is FinancialResult.Success -> 200
    is FinancialResult.Failure -> when (result.error) {
        FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
        FinancialError.CONFLICT, FinancialError.DOCUMENT_REQUIRED, FinancialError.CHARGE_NOT_PAYABLE -> 409
        FinancialError.INVALID_INPUT -> 400
        FinancialError.PIX_NOT_READY, FinancialError.CONFIGURATION_UNAVAILABLE, FinancialError.PROVIDER_UNAVAILABLE -> 503
        FinancialError.RESULT_PENDING -> 202
        else -> 403
    }
}).header("Cache-Control", "no-store").body(result)

private fun response(result: FinancialResult<*>) = receivablesResponse(result)
