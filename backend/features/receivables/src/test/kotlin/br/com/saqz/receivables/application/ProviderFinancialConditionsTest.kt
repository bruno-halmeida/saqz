package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import kotlin.test.*

class ProviderFinancialConditionsTest {
    private val now = Instant.parse("2026-09-14T12:00:00Z")
    private val account = UUID.randomUUID()
    private val published = FeeSchedule(UUID.randomUUID(), PaymentMethod.PIX, BigDecimal("0.5"), 999,
        BigDecimal("0.02"), 100, "v1")
    private val rows = object : FinancialConditions {
        override fun current(methods: Set<PaymentMethod>, at: Instant, accountId: UUID?) = listOf(published)
        override fun currentTerms(at: Instant) = null
        override fun terms(version: String, at: Instant) = null
    }

    @Test fun `only commission comes from publication and live costs belong to the receiving account`() {
        var fixed = 99L
        val conditions = ProviderFinancialConditions(rows, FinancialFeeProvider { methods, at, id ->
            assertEquals(setOf(PaymentMethod.PIX), methods)
            assertEquals(now, at)
            assertEquals(account, id)
            mapOf(PaymentMethod.PIX to ProviderPaymentFee(BigDecimal.ZERO, fixed))
        })
        val first = FeeCalculator.quote(10000, conditions.current(setOf(PaymentMethod.PIX), now, account).single())
        fixed = 199
        val second = FeeCalculator.quote(10000, conditions.current(setOf(PaymentMethod.PIX), now, account).single())
        assertEquals(99, first.providerFeeCents)
        assertEquals(199, second.providerFeeCents)
        assertEquals(300, second.commissionCents)
        assertEquals(10499, second.totalCents)
        assertEquals(published.id, second.feeScheduleId)
        assertEquals("v1", second.termsVersion)
    }

    @Test fun `absent provider fees never fall back to obsolete manually published tariffs`() {
        for (provider in listOf(FinancialFeeProvider { _, _, _ -> throw FinancialFeesUnavailable() }, FinancialFeeProvider { _, _, _ -> emptyMap() })) {
            val simulation = SimulateFinancialCharge(ProviderFinancialConditions(rows, provider))
                .execute(UUID.randomUUID(), 10000, setOf(PaymentMethod.PIX), now)
            assertEquals(FinancialError.CONFIGURATION_UNAVAILABLE, assertIs<FinancialResult.Failure>(simulation).error)
        }
    }

    @Test fun `unpublished commissions remain unavailable even if Asaas is ready`() {
        var calls = 0
        val conditions = ProviderFinancialConditions(rows, FinancialFeeProvider { _, _, _ -> calls++; emptyMap() })
        assertTrue(conditions.current(PaymentMethod.entries.toSet(), now).isEmpty())
        assertEquals(0, calls)
    }
}
