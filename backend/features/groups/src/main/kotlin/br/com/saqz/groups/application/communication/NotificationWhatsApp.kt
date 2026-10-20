package br.com.saqz.groups.application.communication

sealed interface WhatsAppDelivery {
    data object Accepted : WhatsAppDelivery
    data class Retry(val afterSeconds: Long = 60) : WhatsAppDelivery
    data object Failed : WhatsAppDelivery
}

/** [trackId] substitui o `notification-<id>` do Uazapi quando a mensagem não é um aviso (ex.: convite de mensalistas). */
data class WhatsAppNotification(val notificationId: Long, val phone: String, val body: String, val trackId: String? = null)
fun interface NotificationWhatsAppSender { fun send(message: WhatsAppNotification): WhatsAppDelivery }
