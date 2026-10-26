package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

class RecurrenceCommand {
    var requestId: UUID? = null
    var reason: CancelReason? = null
}

/** Mensalidade no cartão (seção I do plano v1). Sem nome, CPF ou aceite no corpo: o CPF é o guardado uma vez. */
@RestController
@RequestMapping("/api/receivables/groups/{groupId}")
class CardSubscriptionsController(private val actors: FinancialActorResolver, private val service: CardSubscriptions) {
    @GetMapping("/recurrence")
    fun get(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID): ResponseEntity<*> =
        receivablesResponse(service.get(groupId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @PostMapping("/recurrence")
    fun start(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
              @RequestBody body: RecurrenceCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: return invalid()
        return receivablesResponse(service.start(groupId, FinancialRequest(requestId, actors.resolve(identity))))
    }

    @PostMapping("/recurrence/card")
    fun card(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
             @RequestBody body: RecurrenceCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: return invalid()
        return receivablesResponse(service.changeCard(groupId, FinancialRequest(requestId, actors.resolve(identity))))
    }

    @PostMapping("/recurrence/cancel")
    fun cancel(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
               @RequestBody body: RecurrenceCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: return invalid()
        val reason = body.reason ?: return invalid(requestId)
        return receivablesResponse(service.cancel(groupId, reason, FinancialRequest(requestId, actors.resolve(identity))))
    }

    @GetMapping("/recurrences")
    fun members(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
                @RequestParam accountId: UUID): ResponseEntity<*> =
        receivablesResponse(service.members(groupId, accountId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    private fun invalid(requestId: UUID = UUID.randomUUID()) = receivablesResponse(FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId))
}
