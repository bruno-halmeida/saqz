package br.com.saqz.sharedkernel.subscription

import java.util.UUID

/**
 * Se o plano do dono do grupo inclui os relatórios (hoje, o Ilimitado). Trial e planos menores
 * respondem `false`; quem decide é o backend, o app só pergunta.
 */
fun interface ReportsEntitlement {
    fun isEnabledFor(ownerId: UUID): Boolean
}
