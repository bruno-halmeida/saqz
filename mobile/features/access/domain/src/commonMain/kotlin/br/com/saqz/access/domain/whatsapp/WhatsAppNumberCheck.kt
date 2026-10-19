package br.com.saqz.access.domain.whatsapp

/**
 * Pergunta ao backend se o número tem WhatsApp (DEB-095). É só um aviso no cadastro: `null` é "não deu para saber"
 * (WhatsApp desligado no servidor, demora, rede, erro) e nunca atrapalha nem bloqueia o cadastro.
 */
fun interface WhatsAppNumberCheckGateway {
    suspend fun hasWhatsApp(phone: String): Boolean?
}
