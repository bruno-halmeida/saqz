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
        val push = AttendancePush.from(data, title, body, fallbackId = message.messageId)
        // Janela das 24 h que chegou depois do fim: não vale mais nada.
        if (push.isWindow && (push.remaining() ?: 0) <= 0) return
        manager.notify(push.id, attendanceNotification(push).build())
    }
}
internal fun reminderNotification(
    context: Context,
    id: Int,
    title: String,
    body: String,
    groupId: String?,
    gameId: String?,
): NotificationCompat.Builder {
    val intent = Intent(context, MainActivity::class.java)
        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        .putExtra(EXTRA_NOTIFICATION_GROUP_ID, groupId)
        .putExtra(EXTRA_GAME_ID, gameId)
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
