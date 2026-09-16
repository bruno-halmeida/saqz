package br.com.saqz.groups.application.communication

import java.util.UUID

/**
 * Entrega de uma mensagem de grupo (NOTICE/REMINDER) ao grupo do WhatsApp vinculado.
 *
 * O destino é um JID (`...@g.us`), não um telefone: nenhuma normalização nem validação de
 * telefone se aplica aqui. Reaproveita [WhatsAppDelivery] — a classificação de erro é a mesma
 * do sender individual.
 */
fun interface NotificationWhatsAppGroupSender {
    fun send(jid: String, messageId: UUID, body: String): WhatsAppDelivery
}
