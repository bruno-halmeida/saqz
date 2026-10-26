package br.com.saqz.receivables.application

import br.com.saqz.receivables.domain.FeeCalculator
import br.com.saqz.receivables.domain.FeeSchedule
import br.com.saqz.receivables.domain.PaymentMethod
import br.com.saqz.sharedkernel.group.ChargePricing
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.math.BigDecimal
import java.math.RoundingMode

enum class FeePolicy { ABSORB, PASS }

/** Tarifas do Asaas da conta, gravadas no vínculo do grupo para o cálculo sem rede na criação da cobrança. */
object FeeSnapshot {
    private val mapper = jacksonObjectMapper()
    private data class Row(val rate: BigDecimal, val fixedCents: Long, val minimumCents: Long, val maximumCents: Long?)

    fun of(schedules: List<FeeSchedule>): String = mapper.writeValueAsString(schedules.associate {
        it.method.name to Row(it.providerRate, it.providerFixedCents, it.providerMinimumCents, it.providerMaximumCents)
    })

    fun parse(json: String): Map<PaymentMethod, ProviderPaymentFee> = mapper.readValue<Map<String, Row>>(json)
        .mapNotNull { (method, row) ->
            runCatching { PaymentMethod.valueOf(method) to ProviderPaymentFee(row.rate, row.fixedCents, row.minimumCents, row.maximumCents) }
                .getOrNull()
        }.toMap()
}

/**
 * Valor do atleta (FIN-010). Absorver: o valor da cobrança. Repassar: o valor que deixa o valor cheio para o gestor no
 * meio mais caro (o cartão, na prática), então no Pix ele recebe um pouco mais. Nulo sem tabela para calcular.
 */
fun chargePricing(amountCents: Long, policy: String, schedules: List<FeeSchedule>): ChargePricing? {
    if (policy != FeePolicy.PASS.name) return ChargePricing(amountCents, FeePolicy.ABSORB.name, null)
    val (total, schedule) = schedules.map { FeeCalculator.passThrough(amountCents, it) to it }.maxByOrNull { it.first } ?: return null
    return ChargePricing(total, FeePolicy.PASS.name, schedule.id)
}

data class PolicyQuote(val athletePaysCents: Long, val receivesPixCents: Long, val receivesCardCents: Long)
data class GroupFeeQuote(val absorb: PolicyQuote, val pass: PolicyQuote, val pixFeeLabel: String, val cardFeeLabel: String)

/** Prévia "Atleta paga · Você recebe" das duas escolhas, com a tarifa da conta e a comissão vigente. */
fun groupFeeQuote(baseCents: Long, pix: FeeSchedule, card: FeeSchedule): GroupFeeQuote {
    val pass = chargePricing(baseCents, FeePolicy.PASS.name, listOf(pix, card))!!.payableCents
    return GroupFeeQuote(
        PolicyQuote(baseCents, FeeCalculator.net(baseCents, pix), FeeCalculator.net(baseCents, card)),
        PolicyQuote(pass, FeeCalculator.net(pass, pix), FeeCalculator.net(pass, card)),
        feeLabel(pix), feeLabel(card),
    )
}

/** "Taxas de serviço e pagamento" num rótulo só: a soma da tarifa do Asaas com a do Saqz (a composição fica nos termos). */
fun feeLabel(schedule: FeeSchedule): String {
    val rate = (schedule.providerRate + schedule.commissionRate).multiply(BigDecimal(100))
        .setScale(2, RoundingMode.HALF_UP).stripTrailingZeros()
    val fixed = schedule.providerFixedCents + schedule.commissionFixedCents
    val parts = buildList {
        if (rate.signum() > 0) add(rate.toPlainString().replace('.', ',') + "%")
        if (fixed > 0 || rate.signum() == 0) add(brl(fixed))
    }
    return parts.joinToString(" + ")
}

fun brl(cents: Long): String {
    val reais = (cents / 100).toString().reversed().chunked(3).joinToString(".").reversed()
    return "R$ $reais," + "%02d".format(cents % 100)
}
