package br.com.saqz.sharedkernel.group

import java.util.UUID

/** "Cobrar" com recebimentos ativos (FIN-013): botão "Pagar no app" e o copia e cola do Pix daquelas cobranças. */
data class ChargeCollection(val buttonUrl: String, val pixPayload: String?)

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
    /**
     * Exclusão do grupo (DEB-029, SAI-008), na transação dela: desliga o recebimento, cancela os códigos em aberto e agenda
     * o cancelamento remoto das cobranças de cartão e dos QR. Sem rede aqui.
     */
    fun groupDeleted(groupId: UUID, actorId: UUID) {}
    /**
     * Nulo sem recebimentos ativos: o "Cobrar" sai como hoje, com a chave Pix pessoal. Pode consultar o Asaas (gera ou
     * reaproveita o código Pix do atleta), então é chamada na entrega da fila do WhatsApp, não na transação de quem cobra.
     */
    fun chargeCollection(groupId: UUID, payerId: UUID, chargeIds: List<UUID>): ChargeCollection? = null

    companion object {
        val NONE: GroupReceivables = object : GroupReceivables {
            override fun active(groupId: UUID) = false
            override fun price(groupId: UUID, amountCents: Long): ChargePricing? = null
        }
    }
}
