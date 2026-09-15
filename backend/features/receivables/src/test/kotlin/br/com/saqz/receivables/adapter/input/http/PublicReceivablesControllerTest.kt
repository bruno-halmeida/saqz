package br.com.saqz.receivables.adapter.input.http

import br.com.saqz.receivables.application.FinancialConditions
import br.com.saqz.receivables.application.FinancialTerms
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import org.junit.jupiter.api.Test
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.util.UUID
import kotlin.test.*

class PublicReceivablesControllerTest {
    private val now = Instant.parse("2026-09-13T12:00:00Z")

    @Test
    fun `public endpoint returns only published content without hash`() {
        val controller = PublicReceivablesController(Conditions(
            FinancialTerms("v1", "Texto aprovado", "secret-internal-hash", now.minusSeconds(1), now.minusSeconds(2)),
        ), Clock.fixed(now, ZoneOffset.UTC))

        val response = controller.currentTerms()

        assertEquals(200, response.statusCode.value())
        assertEquals("Texto aprovado", response.body!!.content)
        assertEquals("no-store", response.headers.cacheControl)
        assertFalse(response.body.toString().contains("secret-internal-hash"))
    }

    @Test
    fun `future and absent terms are not exposed`() {
        val future = FinancialTerms("future", "Ainda não vigente", "hash", now.plusSeconds(1), now.minusSeconds(1))
        val controller = PublicReceivablesController(Conditions(future), Clock.fixed(now, ZoneOffset.UTC))

        assertEquals(404, controller.terms("future").statusCode.value())
        assertEquals(404, controller.terms("missing").statusCode.value())
    }

    @Test
    fun `checkout return can never claim payment`() {
        val response = PublicReceivablesController(Conditions(null), Clock.fixed(now, ZoneOffset.UTC)).checkoutReturn()
        assertEquals("PENDING_VERIFICATION", response.body!!.status)
        assertFalse(response.body!!.message.contains("confirmado", ignoreCase = true))
    }

    private class Conditions(private val terms: FinancialTerms?) : FinancialConditions {
        override fun current(methods: Set<PaymentMethod>, at: Instant, accountId: UUID?): List<FeeSchedule> = emptyList()
        override fun terms(version: String, at: Instant): FinancialTerms? = terms?.takeIf { it.version == version }
        override fun currentTerms(at: Instant): FinancialTerms? = terms?.takeIf { it.effectiveAt <= at && it.publishedAt <= at }
    }
}
