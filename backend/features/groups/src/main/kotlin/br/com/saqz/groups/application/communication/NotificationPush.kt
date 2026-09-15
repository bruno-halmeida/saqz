package br.com.saqz.groups.application.communication

import java.util.UUID

enum class PushDelivery { SENT, INVALID_TOKEN, RETRY }
data class NotificationPush(val notificationId: Long, val groupId: UUID, val title: String, val body: String)
fun interface NotificationPushSender { fun send(token: String, message: NotificationPush): PushDelivery }
