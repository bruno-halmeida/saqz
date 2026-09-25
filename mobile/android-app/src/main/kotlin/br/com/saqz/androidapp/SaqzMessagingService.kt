package br.com.saqz.androidapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import br.com.saqz.groups.domain.communication.NativeNotificationPort
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import org.koin.core.context.GlobalContext

class SaqzMessagingService : FirebaseMessagingService() {
    override fun onNewToken(token: String) {
        Handler(Looper.getMainLooper()).post {
            (GlobalContext.getOrNull()?.getOrNull<NativeNotificationPort>() as? AndroidNotificationPort)?.tokenChanged()
        }
    }
    // Mensagem só de dados: chega aqui também em segundo plano, e é o app que monta a notificação.
    override fun onMessageReceived(message: RemoteMessage) {
        val data = message.data
        val title = data["title"] ?: message.notification?.title ?: return
        val body = data["body"] ?: message.notification?.body ?: return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return
        val id = data["notificationId"]?.hashCode() ?: message.messageId.hashCode()
        val notification = reminderNotification(this, id, title, body, data["groupId"])
        data["gameId"]?.let { gameId ->
            notification
                .addAction(0, getString(R.string.notification_action_confirm), attendanceAction(ACTION_CONFIRM, id, data, gameId))
                .addAction(0, getString(R.string.notification_action_decline), attendanceAction(ACTION_DECLINE, id, data, gameId))
        }
        manager.notify(id, notification.build())
    }
    private fun attendanceAction(action: String, id: Int, data: Map<String, String>, gameId: String): PendingIntent {
        val intent = Intent(this, AttendanceActionReceiver::class.java).setAction(action)
            .putExtra(EXTRA_NOTIFICATION_ID, id).putExtra(EXTRA_NOTIFICATION_GROUP_ID, data["groupId"])
            .putExtra(EXTRA_GAME_ID, gameId).putExtra(EXTRA_TITLE, data["title"]).putExtra(EXTRA_BODY, data["body"])
            .putExtra(EXTRA_RECIPIENT, data["recipient"])
        // requestCode = id: extras não distinguem PendingIntents, e cada push precisa do seu.
        return PendingIntent.getBroadcast(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
internal fun reminderNotification(context: Context, id: Int, title: String, body: String, groupId: String?): NotificationCompat.Builder {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_NOTIFICATION_GROUP_ID, groupId)
    val content = PendingIntent.getActivity(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    return NotificationCompat.Builder(context, REMINDER_CHANNEL)
        .setSmallIcon(R.drawable.ic_saqz_notification).setContentTitle(title)
        .setContentText(body).setAutoCancel(true).setOnlyAlertOnce(true).setContentIntent(content)
}
internal const val REMINDER_CHANNEL = "saqz-reminders"
internal const val EXTRA_NOTIFICATION_GROUP_ID = "saqz.notification.groupId"
internal fun initializeNotificationFirebase(application: android.app.Application) {
    val manager = application.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(
        NotificationChannel(
            REMINDER_CHANNEL,
            application.getString(R.string.notification_reminders),
            NotificationManager.IMPORTANCE_DEFAULT,
        ),
    )
    if (BuildConfig.FIREBASE_USE_EMULATOR) return
    if (FirebaseApp.getApps(application).none { it.name == FirebaseApp.DEFAULT_APP_NAME }) {
        FirebaseApp.initializeApp(application, FirebaseOptions.Builder()
            .setProjectId(BuildConfig.FIREBASE_PROJECT_ID).setApiKey(BuildConfig.FIREBASE_API_KEY)
            .setGcmSenderId(BuildConfig.FIREBASE_MESSAGING_SENDER_ID).setApplicationId(BuildConfig.FIREBASE_APPLICATION_ID).build())
    }
}
