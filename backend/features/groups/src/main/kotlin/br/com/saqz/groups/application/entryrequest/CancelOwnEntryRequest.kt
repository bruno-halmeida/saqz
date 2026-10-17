package br.com.saqz.groups.application.entryrequest

import br.com.saqz.groups.application.create.TransactionRunner
import java.util.UUID

/**
 * Quem pediu para entrar desiste do próprio pedido: apaga só a linha da pessoa (o cadastro guardado
 * mora nela). Repetir, ou cancelar sem pedido, não é erro; ninguém cancela o pedido de outra pessoa
 * porque o `actor` é sempre o dono da linha.
 */
class CancelOwnEntryRequest(
    private val transactionRunner: TransactionRunner,
    private val entryRequestRepository: EntryRequestRepository,
) {
    fun execute(actor: UUID, groupId: UUID) {
        transactionRunner.inTransaction { entryRequestRepository.delete(groupId, actor) }
    }
}
