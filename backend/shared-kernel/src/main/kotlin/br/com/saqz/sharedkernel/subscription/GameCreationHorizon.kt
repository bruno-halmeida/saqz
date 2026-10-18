package br.com.saqz.sharedkernel.subscription

import java.time.Instant
import java.util.UUID

/**
 * Até quando jogos do grupo podem ser criados. `null` = grupo em modo leitura (trial expirado sem
 * assinatura): não cria nada e os jogos futuros da recorrência são cancelados.
 */
fun interface GameCreationHorizon {
    /** Horizonte da série, mês a mês: nunca passa de [startsBefore]. */
    fun until(groupId: UUID): Instant?

    /**
     * Primeiro instante em que um jogo, único ou da série, já não pode começar: 0h, no fuso do grupo, do
     * dia em que o plano ou o teste acaba (o último dia que dá para marcar é o anterior). `null` = sem plano.
     * A carência de atraso não estende o limite.
     */
    fun startsBefore(groupId: UUID): Instant? = until(groupId)

    companion object {
        val Unlimited = GameCreationHorizon { Instant.MAX }
    }
}
