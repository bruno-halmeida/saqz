package br.com.saqz.androidapp

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import br.com.saqz.composeapp.notifications.PushAttendance
import br.com.saqz.composeapp.notifications.PushAttendanceOutcome

/** "Confirmar" / "Não vou" tocados na notificação: responde sem abrir o app e troca o texto pelo resultado. */
class AttendanceActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val gameId = intent.getStringExtra(EXTRA_GAME_ID) ?: return
        val groupId = intent.getStringExtra(EXTRA_NOTIFICATION_GROUP_ID) ?: return
        val id = intent.getIntExtra(EXTRA_NOTIFICATION_ID, 0)
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty()
        // ponytail: goAsync dá ~10 s; se a rede estourar isso, vira WorkManager.
        val pending = goAsync()
        PushAttendance.respond(groupId, gameId, confirm = intent.action == ACTION_CONFIRM) { outcome ->
            context.getSystemService(NotificationManager::class.java)
                .notify(id, reminderNotification(context, id, title, context.getString(outcome.label()), groupId).build())
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
}

internal const val ACTION_CONFIRM = "br.com.saqz.CONFIRM_ATTENDANCE"
internal const val ACTION_DECLINE = "br.com.saqz.DECLINE_ATTENDANCE"
internal const val EXTRA_NOTIFICATION_ID = "saqz.notification.id"
internal const val EXTRA_GAME_ID = "saqz.notification.gameId"
internal const val EXTRA_TITLE = "saqz.notification.title"
