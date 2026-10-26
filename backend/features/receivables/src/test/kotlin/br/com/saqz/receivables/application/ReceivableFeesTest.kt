package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import java.math.BigDecimal
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReceivableFeesTest {
    private val pix = FeeSchedule(UUID.randomUUID(), PaymentMethod.PIX, BigDecimal.ZERO, 99, BigDecimal("0.01"), 0, "v1")
    private val card = FeeSchedule(UUID.randomUUID(), PaymentMethod.CARD, BigDecimal("0.0299"), 49, BigDecimal("0.01"), 0, "v1")

    @Test fun `repassar cobra pela taxa do cartao e o cartao entrega o valor cheio`() {
        val price = chargePricing(2000, "PASS", listOf(pix, card))!!
        assertEquals(card.id, price.feeScheduleId)
        assertTrue(FeeCalculator.net(price.payableCents, card) >= 2000)
        assertTrue(FeeCalculator.net(price.payableCents - 1, card) < 2000)
        // No Pix a taxa é menor: o gestor recebe um pouco mais.
        assertTrue(FeeCalculator.net(price.payableCents, pix) > 2000)
    }

    @Test fun `absorver mantem o valor da cobranca`() {
        val price = chargePricing(2000, "ABSORB", listOf(pix, card))!!
        assertEquals(2000, price.payableCents)
        assertEquals(null, price.feeScheduleId)
    }

    @Test fun `comissao zero so repassa a tarifa do provedor`() {
        val free = card.copy(commissionRate = BigDecimal.ZERO, commissionFixedCents = 0)
        assertEquals(0, FeeCalculator.commission(5000, free))
        val price = chargePricing(2000, "PASS", listOf(free))!!.payableCents
        assertEquals(2000 + (price * 0.0299).let { Math.round(it) } + 49, price)
    }

    @Test fun `previa mostra absorver e repassar com rotulo unico de taxa`() {
        val quote = groupFeeQuote(2000, pix, card)
        assertEquals(2000, quote.absorb.athletePaysCents)
        assertEquals(2000 - 99 - 20, quote.absorb.receivesPixCents)
        assertTrue(quote.pass.receivesCardCents >= 2000)
        assertEquals("1% + R$ 0,99", quote.pixFeeLabel)
        assertEquals("3,99% + R$ 0,49", quote.cardFeeLabel)
    }
}
