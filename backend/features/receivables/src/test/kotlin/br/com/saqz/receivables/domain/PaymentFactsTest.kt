package br.com.saqz.receivables.domain

import br.com.saqz.receivables.application.*
import org.junit.jupiter.api.Test
import java.util.UUID
import kotlin.test.assertEquals

class PaymentFactsTest {
    private val quote = FeeQuote(UUID.randomUUID(), "v1", PaymentMethod.PIX, 10000, 0, 10000, 10000, 0, 0)
    private val instrument = PaymentInstrument(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), quote, "AVAILABLE")
    private fun observed(status: String) = ProviderPaymentObservation(reference = instrument.id.toString(), method = PaymentMethod.PIX, totalCents = 10000, status = status)
    @Test fun `old facts do not undo availability and reversal cannot resurrect debt`() {
        assertEquals("AVAILABLE", PaymentFacts.next(instrument, observed("ACTIVE")))
        assertEquals("AVAILABLE", PaymentFacts.next(instrument, observed("CONFIRMED")))
        assertEquals("REFUNDED", PaymentFacts.next(instrument, observed("REFUNDED")))
        assertEquals("REFUNDED", PaymentFacts.next(instrument.copy(status = "REFUNDED"), observed("AVAILABLE")))
        assertEquals("CHARGEBACK", PaymentFacts.next(instrument.copy(status = "CHARGEBACK"), observed("ACTIVE")))
    }
}
