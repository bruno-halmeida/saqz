package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

class GroupReceivablesRequest {
    var requestId: UUID? = null
    var accountId: UUID? = null
    var methods: Set<PaymentMethod>? = null
    var fingerprint: String? = null
    var accepted: Boolean = false
}

@RestController
@RequestMapping("/api/receivables/groups/{groupId}")
class GroupReceivablesController(private val actors: FinancialActorResolver, private val service: ManageGroupReceivables) {
    @GetMapping
    fun read(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
             @RequestParam accountId: UUID): ResponseEntity<*> {
        val result = service.read(accountId, groupId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity)))
        return ResponseEntity.status(if (result is FinancialResult.Success) 200 else 404)
            .header("Cache-Control", "no-store").body(result)
    }

    @PostMapping("/preview")
    fun preview(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
                @RequestBody body: GroupReceivablesRequest): ResponseEntity<*> = execute(identity, body) { account, request ->
        service.preview(account, groupId, request, body.methods.orEmpty())
    }

    @PostMapping("/activate")
    fun activate(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
                 @RequestBody body: GroupReceivablesRequest): ResponseEntity<*> = execute(identity, body) { account, request ->
        service.activate(account, groupId, request, body.methods.orEmpty(), body.fingerprint.orEmpty(), body.accepted)
    }

    @PostMapping("/deactivate")
    fun deactivate(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID,
                   @RequestBody body: GroupReceivablesRequest): ResponseEntity<*> = execute(identity, body) { account, request ->
        service.deactivate(account, groupId, request)
    }

    private fun execute(identity: RequestIdentity, body: GroupReceivablesRequest,
                        action: (UUID, FinancialRequest) -> FinancialResult<*>): ResponseEntity<*> {
        val id = body.requestId ?: UUID.randomUUID()
        if (body.requestId == null || body.accountId == null) {
            return ResponseEntity.badRequest().body(FinancialResult.Failure(FinancialError.INVALID_INPUT, id))
        }
        val result = action(body.accountId!!, FinancialRequest(id, actors.resolve(identity)))
        return ResponseEntity.status(when (result) {
            is FinancialResult.Success -> 200
            is FinancialResult.Failure -> when (result.error) {
                FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
                FinancialError.CONFLICT -> 409
                FinancialError.INVALID_INPUT -> 400
                FinancialError.CONFIGURATION_UNAVAILABLE, FinancialError.PROVIDER_UNAVAILABLE -> 503
                else -> 403
            }
        }).body(result)
    }
}
