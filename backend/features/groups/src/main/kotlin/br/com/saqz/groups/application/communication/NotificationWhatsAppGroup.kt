package br.com.saqz.groups.application.communication

import java.util.UUID

/** Rótulo e URL de um botão de menu na mensagem do grupo. */
data class WhatsAppGroupButton(val label: String, val url: String)

/**
 * Entrega de uma mensagem de grupo (NOTICE/REMINDER) ao grupo do WhatsApp vinculado.
 *
 * O destino é um JID (`...@g.us`), não um telefone: nenhuma normalização nem validação de
 * telefone se aplica aqui. Reaproveita [WhatsAppDelivery] — a classificação de erro é a mesma
 * do sender individual.
 */
fun interface NotificationWhatsAppGroupSender {
    /**
     * [buttons] vira botão de URL na mensagem; o texto nunca carrega a URL crua. Lista vazia
     * envia texto puro.
     */
    fun send(jid: String, messageId: UUID, text: String, buttons: List<WhatsAppGroupButton>): WhatsAppDelivery
}
