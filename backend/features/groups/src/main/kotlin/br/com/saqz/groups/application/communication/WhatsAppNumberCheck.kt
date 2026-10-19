package br.com.saqz.groups.application.communication

/** Pergunta ao provedor se o número tem WhatsApp. `null` = não deu para saber; nunca é motivo para recusar nada. */
fun interface WhatsAppNumberCheck {
    fun hasWhatsApp(phone: String): Boolean?
}
