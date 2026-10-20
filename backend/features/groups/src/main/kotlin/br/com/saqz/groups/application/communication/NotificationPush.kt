package br.com.saqz.groups.application.communication

import java.util.UUID

enum class PushDelivery { SENT, INVALID_TOKEN, RETRY }

/** [recipient] é o `firebase_subject` de quem recebe: o app só age no push se for o usuário logado. */
data class NotificationPush(
    val notificationId: Long, val groupId: UUID, val title: String, val body: String, val channel: String, val recipient: String,
    val gameId: UUID? = null,
    /** O jogo tem vagas por posição (5-1, DEB-107): o "Vou" do push abre a tela de escolher posição nos apps 1.0.10+. */
    val positions: Boolean = false,
)
fun interface NotificationPushSender { fun send(token: String, message: NotificationPush): PushDelivery }
