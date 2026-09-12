package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.FinancialError
import br.com.saqz.receivables.application.FinancialRequest
import br.com.saqz.receivables.application.FinancialResult
import br.com.saqz.receivables.application.ManageFinancialDelegations
import br.com.saqz.sharedkernel.RequestIdentity
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.time.Clock
import java.util.UUID

data class GrantFinancialDelegationRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID,
    @JsonProperty("userId") val userId: UUID,
    @JsonProperty("termsVersion") val termsVersion: String,
    @JsonProperty("acknowledgedWholeAccount") val acknowledgedWholeAccount: Boolean,
)

@RestController
@RequestMapping("/api/receivables/accounts/{accountId}/delegations")
class FinancialDelegationsController(private val actors: FinancialActorResolver,
    private val service: ManageFinancialDelegations, private val clock: Clock) {
    @GetMapping
    fun list(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID): ResponseEntity<*> =
        response(service.list(accountId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @PostMapping
    fun grant(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
              @RequestBody body: GrantFinancialDelegationRequest): ResponseEntity<*> = response(service.grant(accountId,
        FinancialRequest(body.requestId, actors.resolve(identity)), body.userId, body.termsVersion,
        body.acknowledgedWholeAccount, clock.instant()))

    @DeleteMapping("/{userId}")
    fun revoke(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable accountId: UUID,
               @PathVariable userId: UUID, @RequestParam requestId: UUID): ResponseEntity<*> = response(service.revoke(
        accountId, FinancialRequest(requestId, actors.resolve(identity)), userId, clock.instant()))

    private fun response(result: FinancialResult<*>): ResponseEntity<*> = ResponseEntity.status(when (result) {
        is FinancialResult.Success -> 200
        is FinancialResult.Failure -> when (result.error) {
            FinancialError.NOT_FOUND, FinancialError.UNAUTHORIZED -> 404
            FinancialError.CONFLICT -> 409
            else -> 400
        }
    }).body(result)
}
