package br.com.saqz.sharedkernel.subscription

import java.time.Instant
import java.util.UUID

/**
 * Até quando jogos recorrentes do grupo podem ser criados: sempre o próximo aniversário mensal
 * do plano do dono. `null` = grupo em modo leitura (trial expirado sem assinatura): não cria
 * nada e os jogos futuros da recorrência são cancelados.
 */
fun interface GameCreationHorizon {
    fun until(groupId: UUID): Instant?

    companion object {
        val Unlimited = GameCreationHorizon { Instant.MAX }
    }
}
