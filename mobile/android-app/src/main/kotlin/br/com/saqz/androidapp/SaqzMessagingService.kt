package br.com.saqz.androidapp

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
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
    override fun onMessageReceived(message: RemoteMessage) {
        val notification = message.notification ?: return
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 24 && !manager.areNotificationsEnabled()) return
        val id = message.data["notificationId"]?.hashCode() ?: message.messageId.hashCode()
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(EXTRA_NOTIFICATION_GROUP_ID, message.data["groupId"])
        val content = PendingIntent.getActivity(this, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        manager.notify(id, NotificationCompat.Builder(this, REMINDER_CHANNEL)
            .setSmallIcon(R.drawable.ic_saqz_notification).setContentTitle(notification.title)
            .setContentText(notification.body).setAutoCancel(true).setContentIntent(content).build())
    }
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
