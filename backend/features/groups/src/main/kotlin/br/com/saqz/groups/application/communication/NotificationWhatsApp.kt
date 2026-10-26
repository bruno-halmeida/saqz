package br.com.saqz.groups.application.communication

sealed interface WhatsAppDelivery {
    data object Accepted : WhatsAppDelivery
    data class Retry(val afterSeconds: Long = 60) : WhatsAppDelivery
    data object Failed : WhatsAppDelivery
}

/** [trackId] substitui o `notification-<id>` do Uazapi quando a mensagem não é um aviso (ex.: convite de mensalistas). */
data class WhatsAppNotification(val notificationId: Long, val phone: String, val body: String, val trackId: String? = null,
    /** Botão de URL (menu); sem ele, texto puro como sempre. */
    val button: WhatsAppGroupButton? = null,
    /** Segunda mensagem, só com o copia e cola do Pix (FIN-013): o atleta copia sem pegar o resto do texto. */
    val followUp: String? = null)
fun interface NotificationWhatsAppSender { fun send(message: WhatsAppNotification): WhatsAppDelivery }
