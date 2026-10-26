package br.com.saqz.sharedkernel.group

import java.util.UUID

/** Valor que o atleta paga, fixado quando a cobrança nasce (FIN-010). `feeScheduleId` é a tabela usada no repasse. */
data class ChargePricing(val payableCents: Long, val feePolicy: String, val feeScheduleId: UUID?)

/**
 * Recebimento pelo app visto pelo módulo de grupos (FIN-009/FIN-010). Implementada em recebimentos e chamada dentro da
 * transação que cria a cobrança ou lê o grupo: só banco, nunca rede.
 */
interface GroupReceivables {
    /** O grupo recebe pelo app: o app esconde a chave Pix pessoal e mostra "Pagar". */
    fun active(groupId: UUID): Boolean
    /** Nulo sem recebimentos ativos: a cobrança nasce como sempre, sem valor do atleta. */
    fun price(groupId: UUID, amountCents: Long): ChargePricing?

    companion object {
        val NONE: GroupReceivables = object : GroupReceivables {
            override fun active(groupId: UUID) = false
            override fun price(groupId: UUID, amountCents: Long): ChargePricing? = null
        }
    }
}
