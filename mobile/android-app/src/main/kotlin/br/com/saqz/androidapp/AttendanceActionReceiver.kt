package br.com.saqz.androidapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.saqz.composeapp.di.hasSaqzPlatformDependencies
import br.com.saqz.composeapp.di.loadSaqzPlatformDependencies
import br.com.saqz.composeapp.notifications.PushAttendance
import br.com.saqz.composeapp.notifications.PushAttendanceOutcome
import org.koin.core.context.GlobalContext

/**
 * "Confirmar" / "Não vou" tocados na notificação: responde sem abrir o app e troca o texto pelo resultado
 * (na janela das 24 h, some em 2 min).
 */
class AttendanceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val push = intent.attendancePush()
        val gameId = push.gameId ?: return
        val groupId = push.groupId ?: return
        val recipient = push.recipient ?: return
        val manager = context.getSystemService(NotificationManager::class.java)
        // Sem botões enquanto responde: segundo toque não dispara pedido concorrente.
        manager.notify(push.id, context.attendanceNotification(push, buttons = false).build())
        val pending = goAsync()
        val koin = GlobalContext.get()
        if (!hasSaqzPlatformDependencies()) {
            // Processo acordado direto pelo botão: a MainActivity, que carrega a plataforma no Koin, não existiu.
            val composition = MainActivityComposition.factory().create(context.applicationContext, koin.get()) {
                error("ação de push não tem Activity")
            }
            loadSaqzPlatformDependencies(composition.dependencies, context.applicationContext)
        }
        koin.get<PushAttendance>().respond(groupId, gameId, recipient, confirm = intent.action == ACTION_CONFIRM) { outcome ->
            val text = context.getString(outcome.label())
            manager.notify(push.id, context.attendanceOutcome(push, outcome == PushAttendanceOutcome.Failed, text).build())
            pending.finish()
        }
    }
}

private fun PushAttendanceOutcome.label() = when (this) {
    PushAttendanceOutcome.Confirmed -> R.string.notification_attendance_confirmed
    PushAttendanceOutcome.Waitlisted -> R.string.notification_attendance_waitlisted
    PushAttendanceOutcome.Declined -> R.string.notification_attendance_declined
    PushAttendanceOutcome.Closed -> R.string.notification_attendance_closed
    PushAttendanceOutcome.Failed -> R.string.notification_attendance_failed
    PushAttendanceOutcome.NoResponse -> R.string.notification_attendance_no_response
}

internal const val ACTION_CONFIRM = "br.com.saqz.CONFIRM_ATTENDANCE"
internal const val ACTION_DECLINE = "br.com.saqz.DECLINE_ATTENDANCE"
internal const val EXTRA_NOTIFICATION_ID = "saqz.notification.id"
internal const val EXTRA_GAME_ID = "saqz.notification.gameId"
internal const val EXTRA_TITLE = "saqz.notification.title"
internal const val EXTRA_BODY = "saqz.notification.body"
internal const val EXTRA_RECIPIENT = "saqz.notification.recipient"
internal const val EXTRA_WINDOW_ENDS_AT = "saqz.notification.windowEndsAt"
