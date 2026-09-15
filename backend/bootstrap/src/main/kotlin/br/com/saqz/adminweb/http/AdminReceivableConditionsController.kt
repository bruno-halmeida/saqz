package br.com.saqz.adminweb.http

import br.com.saqz.access.application.admin.PlatformAdminLookup
import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.RequestIdentity
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.util.UUID

class PublishReceivableTermsRequest {
    var requestId: UUID? = null
    var version: String? = null
    var content: String? = null
    var effectiveAt: Instant? = null
}

class PublishReceivableFeesRequest {
    var requestId: UUID? = null
    var method: PaymentMethod? = null
    var providerRate: BigDecimal? = null
    var providerFixedCents: BigDecimal? = null
    var commissionRate: BigDecimal? = null
    var commissionFixedCents: BigDecimal? = null
    var termsVersion: String? = null
    var effectiveAt: Instant? = null
    var baseCents: BigDecimal? = null

    fun schedule(): FeeSchedule? = try {
        // Retain legacy input names only to reject clients attempting to set Asaas tariffs.
        require(providerRate == null && providerFixedCents == null)
        require(requireNotNull(commissionRate).stripTrailingZeros().scale() <= 10)
        // Legacy provider columns are non-authoritative; current costs are resolved by FinancialFeeProvider.
        FeeSchedule(requireNotNull(requestId), requireNotNull(method), BigDecimal.ZERO,
            0, requireNotNull(commissionRate),
            requireNotNull(commissionFixedCents).longValueExact(), requireNotNull(termsVersion))
    } catch (_: IllegalArgumentException) { null } catch (_: ArithmeticException) { null }
}

/** Platform administration only. This controller grants no authority over account money. */
@RestController
@RequestMapping("/admin/receivables")
class AdminReceivableConditionsController(private val admins: PlatformAdminLookup,
    private val publisher: FinancialConditionsPublisher, private val clock: Clock,
    private val provider: FinancialFeeProvider) {

    @PostMapping("/terms")
    fun publishTerms(@AuthenticationPrincipal identity: RequestIdentity,
                     @RequestBody body: PublishReceivableTermsRequest): ResponseEntity<*> {
        val requestId = body.requestId ?: UUID.randomUUID()
        val actor = admins.findBySubject(identity.subject)?.userId ?: return denied(requestId)
        if (body.requestId == null || body.version == null || body.content == null || body.effectiveAt == null) {
            return invalid(requestId)
        }
        return response(publisher.terms(FinancialRequest(requestId, actor), body.version!!,
            body.content!!, body.effectiveAt!!, clock.instant()))
    }

    @PostMapping("/fees")
    fun publishFees(@AuthenticationPrincipal identity: RequestIdentity,
                    @RequestBody body: PublishReceivableFeesRequest): ResponseEntity<*> {
        val requestId = body.requestId ?: UUID.randomUUID()
        val actor = admins.findBySubject(identity.subject)?.userId ?: return denied(requestId)
        val schedule = body.schedule() ?: return invalid(requestId)
        val effective = body.effectiveAt ?: return invalid(requestId)
        return response(publisher.fees(FinancialRequest(requestId, actor), schedule, effective, clock.instant()))
    }

    @PostMapping("/fees/simulate")
    fun simulateFees(@AuthenticationPrincipal identity: RequestIdentity,
                     @RequestBody body: PublishReceivableFeesRequest): ResponseEntity<*> {
        val requestId = body.requestId ?: UUID.randomUUID()
        if (admins.findBySubject(identity.subject) == null) return denied(requestId)
        val schedule = body.schedule() ?: return invalid(requestId)
        return try {
            val base = body.baseCents?.longValueExact() ?: return invalid(requestId)
            if (base <= 0) return invalid(requestId)
            val fees = provider.current(setOf(schedule.method), clock.instant(), null)[schedule.method]
                ?: throw FinancialFeesUnavailable()
            ResponseEntity.ok(FinancialResult.Success(FeeCalculator.quote(base, fees.applyTo(schedule)), requestId))
        } catch (_: FinancialFeesUnavailable) {
            ResponseEntity.status(503).body(FinancialResult.Failure(FinancialError.CONFIGURATION_UNAVAILABLE, requestId))
        } catch (_: IllegalArgumentException) { invalid(requestId) } catch (_: ArithmeticException) { invalid(requestId) }
    }

    private fun response(result: FinancialResult<*>): ResponseEntity<*> = ResponseEntity.status(when (result) {
        is FinancialResult.Success -> 200
        is FinancialResult.Failure -> if (result.error == FinancialError.CONFLICT) 409 else 400
    }).body(result)
    private fun invalid(id: UUID) = ResponseEntity.badRequest().body(FinancialResult.Failure(FinancialError.INVALID_INPUT, id))
    private fun denied(id: UUID) = ResponseEntity.status(403).body(FinancialResult.Failure(FinancialError.UNAUTHORIZED, id))
}
