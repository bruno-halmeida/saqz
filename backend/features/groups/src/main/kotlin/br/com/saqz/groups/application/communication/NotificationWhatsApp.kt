package br.com.saqz.groups.application.communication

sealed interface WhatsAppDelivery {
    data object Accepted : WhatsAppDelivery
    data class Retry(val afterSeconds: Long = 60) : WhatsAppDelivery
    data object Failed : WhatsAppDelivery
}

data class WhatsAppNotification(val notificationId: Long, val phone: String, val body: String)
fun interface NotificationWhatsAppSender { fun send(message: WhatsAppNotification): WhatsAppDelivery }
