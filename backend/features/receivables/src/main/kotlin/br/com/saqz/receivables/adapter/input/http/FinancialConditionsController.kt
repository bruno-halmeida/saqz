package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.*
import br.com.saqz.receivables.domain.PaymentMethod
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.math.BigDecimal
import java.time.Clock
import java.util.UUID

class SimulateFinancialChargeRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: UUID?,
    @JsonProperty("baseCents") val baseCents: BigDecimal?,
    @JsonProperty("methods") val methods: Set<PaymentMethod>?,
)

/** Authenticated discovery is independent of financial registration and commercial rights. */
@RestController
@RequestMapping("/api/receivables")
class FinancialConditionsController(private val conditions: FinancialConditions,
    private val simulation: SimulateFinancialCharge, private val clock: Clock) {
    @PostMapping("/charges/simulate")
    fun simulate(@RequestBody body: SimulateFinancialChargeRequest): ResponseEntity<*> {
        val requestId = body.requestId ?: UUID.randomUUID()
        val cents = try { body.baseCents?.longValueExact() } catch (_: ArithmeticException) { null }
        if (body.requestId == null || cents == null || body.methods == null) {
            return ResponseEntity.badRequest().body(FinancialResult.Failure(FinancialError.INVALID_INPUT, requestId))
        }
        val result = simulation.execute(requestId, cents, body.methods, clock.instant())
        return ResponseEntity.status(when (result) {
            is FinancialResult.Success -> 200
            is FinancialResult.Failure -> if (result.error == FinancialError.INVALID_INPUT) 400 else 503
        }).body(result)
    }

    @GetMapping("/terms")
    fun currentTerms(): ResponseEntity<*> {
        val requestId = UUID.randomUUID()
        val terms = conditions.currentTerms(clock.instant())
            ?: return ResponseEntity.status(404).header("Cache-Control", "no-store")
                .body(FinancialResult.Failure(FinancialError.NOT_FOUND, requestId))
        return ResponseEntity.ok().header("Cache-Control", "no-store").body(FinancialResult.Success(terms, requestId))
    }

    @GetMapping("/terms/{version}")
    fun terms(@PathVariable version: String): ResponseEntity<*> {
        val requestId = UUID.randomUUID()
        val terms = conditions.terms(version, clock.instant())
            ?: return ResponseEntity.status(404).body(FinancialResult.Failure(FinancialError.NOT_FOUND, requestId))
        return ResponseEntity.ok(FinancialResult.Success(terms, requestId))
    }
}
