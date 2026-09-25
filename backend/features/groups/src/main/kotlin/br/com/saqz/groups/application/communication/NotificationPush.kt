package br.com.saqz.groups.application.communication

import java.time.Instant
import java.util.UUID

enum class PushDelivery { SENT, INVALID_TOKEN, RETRY }
/** [recipient] é o `firebase_subject` de quem recebe: o app só age no push se for o usuário logado. */
data class NotificationPush(
    val notificationId: Long, val groupId: UUID, val title: String, val body: String, val channel: String, val recipient: String,
    val gameId: UUID? = null,
    /** Fim da janela de presença das 24 h: o app expira a notificação nessa hora. */
    val windowEndsAt: Instant? = null,
)
fun interface NotificationPushSender { fun send(token: String, message: NotificationPush): PushDelivery }
