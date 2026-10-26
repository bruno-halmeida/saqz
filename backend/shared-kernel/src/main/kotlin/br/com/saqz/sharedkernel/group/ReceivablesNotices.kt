package br.com.saqz.sharedkernel.group

import java.util.UUID

/**
 * Push do recebimento pelo app (FIN-013, FIN-014) pela fila de push dos grupos. Implementada em grupos; participa da
 * transação de quem chama, então o aviso só sai se a baixa for gravada. [requestId] fixo deixa repetir sem duplicar.
 */
fun interface ReceivablesNotices {
    fun publish(groupId: UUID, recipients: Set<UUID>, title: String, body: String, requestId: UUID)

    companion object {
        val NONE = ReceivablesNotices { _, _, _, _, _ -> }
    }
}
