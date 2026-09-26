package br.com.saqz.androidapp

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

/** O que o app precisa de um push de presença para montar a notificação e remontá-la depois do toque. */
internal data class AttendancePush(
    val id: Int,
    val groupId: String?,
    val gameId: String?,
    val title: String,
    val body: String,
    val recipient: String?,
    val windowEndsAt: Long?,
) {
    /** Janela de presença das 24 h: expira sozinha e, no Android 16+, vira Live Update. */
    val isWindow get() = windowEndsAt != null && gameId != null

    fun remaining(now: Long = System.currentTimeMillis()): Long? = windowEndsAt?.minus(now)

    companion object {
        fun from(data: Map<String, String>, title: String, body: String, fallbackId: String?): AttendancePush {
            val gameId = data["gameId"]
            val windowEndsAt = data["windowEndsAt"]?.toLongOrNull()
            val id = if (windowEndsAt != null && gameId != null) attendanceWindowId(gameId)
                else (data["notificationId"] ?: fallbackId).hashCode()
            return AttendancePush(id, data["groupId"], gameId, title, body, data["recipient"], windowEndsAt)
        }
    }
}

/** Push de presença montado pelo app: botões quando há jogo e, na janela, Live Update que expira sozinho. */
internal fun Context.attendanceNotification(
    push: AttendancePush,
    body: String = push.body,
    buttons: Boolean = true,
): NotificationCompat.Builder {
    val builder = reminderNotification(this, push.id, push.title, body, push.groupId, push.gameId)
    if (push.isWindow) {
        // Live Update no Android 16+; abaixo disso o pedido é ignorado e a notificação sai comum.
        builder.setOngoing(true).setRequestPromotedOngoing(true)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setTimeoutAfter((push.remaining() ?: 0).coerceAtLeast(1))
    }
    if (buttons && push.gameId != null) {
        builder.addAction(0, getString(R.string.notification_action_confirm), attendanceAction(ACTION_CONFIRM, push))
            .addAction(0, getString(R.string.notification_action_decline), attendanceAction(ACTION_DECLINE, push))
    }
    return builder
}

/**
 * Resultado do toque. Na janela: falha devolve os botões até o fim dela; os outros resultados ficam
 * [FINAL_STATE_MS] e somem. Push comum mantém o comportamento de antes: texto, sem botões, sem prazo.
 */
internal fun Context.attendanceOutcome(push: AttendancePush, failed: Boolean, text: String): NotificationCompat.Builder = when {
    push.isWindow && failed -> attendanceNotification(push, body = text)
    push.isWindow -> reminderNotification(this, push.id, push.title, text, push.groupId, push.gameId).setTimeoutAfter(FINAL_STATE_MS)
    else -> reminderNotification(this, push.id, push.title, text, push.groupId, push.gameId)
}

internal fun Intent.attendancePush() = AttendancePush(
    id = getIntExtra(EXTRA_NOTIFICATION_ID, 0),
    groupId = getStringExtra(EXTRA_NOTIFICATION_GROUP_ID),
    gameId = getStringExtra(EXTRA_GAME_ID),
    title = getStringExtra(EXTRA_TITLE).orEmpty(),
    body = getStringExtra(EXTRA_BODY).orEmpty(),
    recipient = getStringExtra(EXTRA_RECIPIENT),
    windowEndsAt = getLongExtra(EXTRA_WINDOW_ENDS_AT, 0L).takeIf { it > 0 },
)

private fun Context.attendanceAction(action: String, push: AttendancePush): PendingIntent {
    val intent = Intent(this, AttendanceActionReceiver::class.java).setAction(action)
        .putExtra(EXTRA_NOTIFICATION_ID, push.id).putExtra(EXTRA_NOTIFICATION_GROUP_ID, push.groupId)
        .putExtra(EXTRA_GAME_ID, push.gameId).putExtra(EXTRA_TITLE, push.title).putExtra(EXTRA_BODY, push.body)
        .putExtra(EXTRA_RECIPIENT, push.recipient)
    push.windowEndsAt?.let { intent.putExtra(EXTRA_WINDOW_ENDS_AT, it) }
    // requestCode = id: extras não distinguem PendingIntents, e cada push precisa do seu.
    return PendingIntent.getBroadcast(this, push.id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
}

/** Quanto o resultado do toque fica na tela antes de sumir (decisão de produto: 2 min). */
internal const val FINAL_STATE_MS = 120_000L

/** A janela é uma por jogo: id fixo por jogo, para a notificação ser trocada no lugar e cancelada de fora. */
internal fun attendanceWindowId(gameId: String) = "window:$gameId".hashCode()
