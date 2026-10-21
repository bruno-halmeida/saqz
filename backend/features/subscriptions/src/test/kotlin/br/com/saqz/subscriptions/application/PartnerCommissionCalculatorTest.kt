package br.com.saqz.subscriptions.application

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import java.time.Instant
import java.time.YearMonth
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartnerCommissionCalculatorTest {
    private val ana = UUID.randomUUID()
    private val now = Instant.parse("2026-10-21T15:00:00Z")
    private fun pay(ref: String, at: String, cents: Long?, refunded: Boolean = false, owner: UUID = ana) =
        CommissionPayment("SITE", ref, owner, Instant.parse(at), cents, refunded)

    // Três mensalidades de R$ 41,93 a partir de julho (meses de São Paulo).
    private val payments = listOf(
        pay("p1", "2026-07-10T15:00:00Z", 4_193),
        pay("p2", "2026-08-10T15:00:00Z", 4_193),
        pay("p3", "2026-09-10T15:00:00Z", 4_193),
        pay("p4", "2026-10-10T15:00:00Z", 4_193),
    )

    private fun open(rule: CommissionRule?, list: List<CommissionPayment> = payments, closed: List<ClosedCommissionLine> = emptyList(), months: Set<YearMonth> = emptySet()) =
        PartnerCommissionCalculator.open(rule, list, closed, months, now)

    @Test
    fun `as três durações`() {
        val ten = BigDecimal("10.00")
        assertEquals(listOf(YearMonth.of(2026, 7)), open(CommissionRule(ten, CommissionDuration.FIRST_PAYMENT)).keys.toList())
        // 2 meses do primeiro pagamento (10/07): entra 10/07 e 10/08; 10/09 já não.
        assertEquals(listOf(YearMonth.of(2026, 7), YearMonth.of(2026, 8)), open(CommissionRule(ten, CommissionDuration.MONTHS, 2)).keys.toList())
        assertEquals(4, open(CommissionRule(ten, CommissionDuration.LIFETIME)).values.flatten().size)
        assertTrue(open(null).isEmpty())
    }

    @Test
    fun `percentual com centavos arredonda por pagamento, metade para cima`() {
        // 4193 × 12,50% = 524,125 → 524; 4193 × 7,25% = 303,99 → 304; 2 × 2,50% = 0,05 → 0; 10 × 15% = 1,5 → 2.
        assertEquals(524, PartnerCommissionCalculator.commissionCents(4_193, BigDecimal("12.50")))
        assertEquals(304, PartnerCommissionCalculator.commissionCents(4_193, BigDecimal("7.25")))
        assertEquals(0, PartnerCommissionCalculator.commissionCents(2, BigDecimal("2.50")))
        assertEquals(2, PartnerCommissionCalculator.commissionCents(10, BigDecimal("15.00")))
        assertEquals(4_193, PartnerCommissionCalculator.commissionCents(4_193, BigDecimal("100.00")))
        assertThrows<IllegalArgumentException> { CommissionRule(BigDecimal("0.001"), CommissionDuration.LIFETIME) }
        assertThrows<IllegalArgumentException> { CommissionRule(BigDecimal("100.01"), CommissionDuration.LIFETIME) }
        assertThrows<IllegalArgumentException> { CommissionRule(BigDecimal("10"), CommissionDuration.MONTHS, 61) }
        val month = open(CommissionRule(BigDecimal("12.50"), CommissionDuration.LIFETIME), payments.take(2)).values.flatten()
        assertEquals(1_048, month.sumOf { it.commissionCents })
    }

    @Test
    fun `estorno antes do fechamento sai - depois do fechamento vira desconto no mês em aberto`() {
        val rule = CommissionRule(BigDecimal("10.00"), CommissionDuration.LIFETIME)
        val before = open(rule, listOf(pay("p1", "2026-07-10T15:00:00Z", 4_193, refunded = true)))
        assertTrue(before.isEmpty())

        val closed = listOf(ClosedCommissionLine(CommissionLineKind.PAYMENT, "SITE", "p1", 419))
        val after = open(rule, listOf(pay("p1", "2026-07-10T15:00:00Z", 4_193, refunded = true)), closed, setOf(YearMonth.of(2026, 7)))
        val refund = after.getValue(YearMonth.of(2026, 10)).single()
        assertEquals(CommissionLineKind.REFUND, refund.kind)
        assertEquals(-419, refund.commissionCents)
        // Desconto já fechado não volta.
        val twice = open(rule, listOf(pay("p1", "2026-07-10T15:00:00Z", 4_193, refunded = true)),
            closed + ClosedCommissionLine(CommissionLineKind.REFUND, "SITE", "p1", -419), setOf(YearMonth.of(2026, 7), YearMonth.of(2026, 10)))
        assertTrue(twice.isEmpty())
    }

    @Test
    fun `pagamento da loja sem valor só entra quando o valor chega, no mês em aberto`() {
        val rule = CommissionRule(BigDecimal("10.00"), CommissionDuration.LIFETIME)
        val play = CommissionPayment("GOOGLE_PLAY", "GPA.1", ana, Instant.parse("2026-08-05T12:00:00Z"), null, false)
        assertTrue(open(rule, listOf(play)).isEmpty())
        // Agosto fechou sem ele; o valor chegou depois: entra em outubro (mês atual).
        val later = open(rule, listOf(play.copy(grossCents = 4_190)), months = setOf(YearMonth.of(2026, 8)))
        assertEquals(419, later.getValue(YearMonth.of(2026, 10)).single().commissionCents)
        // Sem agosto fechado, fica no próprio mês.
        assertEquals(setOf(YearMonth.of(2026, 8)), open(rule, listOf(play.copy(grossCents = 4_190))).keys)
    }

    @Test
    fun `primeiro pagamento sem valor ainda é a âncora da duração`() {
        val list = listOf(pay("p1", "2026-07-10T15:00:00Z", null), pay("p2", "2026-08-10T15:00:00Z", 4_193))
        assertTrue(open(CommissionRule(BigDecimal("10.00"), CommissionDuration.FIRST_PAYMENT), list).isEmpty())
        assertEquals(1, open(CommissionRule(BigDecimal("10.00"), CommissionDuration.MONTHS, 2), list).values.flatten().size)
    }

    @Test
    fun `só fecha mês que já terminou`() {
        assertTrue(PartnerCommissionCalculator.canClose(YearMonth.of(2026, 9), emptySet(), now))
        assertEquals(false, PartnerCommissionCalculator.canClose(YearMonth.of(2026, 10), emptySet(), now))
        assertEquals(false, PartnerCommissionCalculator.canClose(YearMonth.of(2026, 9), setOf(YearMonth.of(2026, 9)), now))
        // 31/10 22h em São Paulo ainda é outubro (01/11 01h UTC).
        assertEquals(YearMonth.of(2026, 10), PartnerCommissionCalculator.monthOf(Instant.parse("2026-11-01T01:00:00Z")))
    }
}
