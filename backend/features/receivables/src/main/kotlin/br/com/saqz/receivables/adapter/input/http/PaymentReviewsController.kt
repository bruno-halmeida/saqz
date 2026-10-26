package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

class ReviewCommand {
    var requestId: UUID? = null
    var memberUserId: UUID? = null
    var chargeIds: List<UUID>? = null
}

/** Revisão do gestor (seção D/H do plano v1): Pix fora de código. */
@RestController
@RequestMapping("/api/receivables")
class PaymentReviewsController(private val actors: FinancialActorResolver, private val service: PaymentReviews) {
    @GetMapping("/groups/{groupId}/reviews")
    fun list(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable groupId: UUID, @RequestParam accountId: UUID): ResponseEntity<*> =
        receivablesResponse(service.list(groupId, accountId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @GetMapping("/reviews/{reviewId}/suggestion")
    fun suggestion(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable reviewId: UUID,
                   @RequestParam memberUserId: UUID): ResponseEntity<*> =
        receivablesResponse(service.suggestion(reviewId, memberUserId, FinancialRequest(UUID.randomUUID(), actors.resolve(identity))))

    @PostMapping("/reviews/{reviewId}/assign")
    fun assign(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable reviewId: UUID,
               @RequestBody body: ReviewCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: return invalid()
        val member = body.memberUserId ?: return invalid(requestId)
        val charges = body.chargeIds ?: return invalid(requestId)
        return receivablesResponse(service.assign(reviewId, member, charges, FinancialRequest(requestId, actors.resolve(identity))))
    }

    @PostMapping("/reviews/{reviewId}/not-charge")
    fun notCharge(@AuthenticationPrincipal identity: RequestIdentity, @PathVariable reviewId: UUID,
                  @RequestBody body: ReviewCommand): ResponseEntity<*> {
        val requestId = body.requestId ?: return invalid()
        return receivablesResponse(service.notCharge(reviewId, FinancialRequest(requestId, actors.resolve(identity))))
    }

    private fun invalid(requestId: UUID = UUID.randomUUID()) =
        receivablesResponse(FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId))
}
