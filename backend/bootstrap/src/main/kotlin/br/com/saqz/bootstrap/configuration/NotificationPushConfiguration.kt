package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.NotificationDeviceController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationPush
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.*
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class NotificationPushConfiguration {
    @Bean fun notificationDevices(dataSource: DataSource, transaction: JdbcTransactionRunner) = JdbcNotificationPush(dataSource, transaction)
    @Bean fun notificationDeviceController(actors: VerifiedGroupActorResolver, devices: JdbcNotificationPush) =
        NotificationDeviceController(actors, devices)
    @Bean fun notificationPushSender(app: FirebaseApp): NotificationPushSender = FirebaseNotificationPushSender(app)
    @Bean fun notificationPushWorker(devices: JdbcNotificationPush, sender: NotificationPushSender) = NotificationPushWorker(devices, sender)
}
class NotificationPushWorker(private val queue: JdbcNotificationPush, private val sender: NotificationPushSender) {
    @Scheduled(fixedDelayString = "\${saqz.notifications.push-delay-ms:15000}")
    fun run() = queue.drain(sender)
}
class FirebaseNotificationPushSender(private val app: FirebaseApp) : NotificationPushSender {
    override fun send(token: String, message: NotificationPush): PushDelivery {
        // O Auth Emulator não oferece FCM: preservar a fila para diagnóstico, nunca fingir entrega.
        if (app.options.projectId == "saqz-local") return PushDelivery.RETRY
        return try {
            FirebaseMessaging.getInstance(app).send(Message.builder().setToken(token)
                .setNotification(Notification.builder().setTitle(message.title).setBody(message.body).build())
                .putData("notificationId", message.notificationId.toString()).putData("groupId", message.groupId.toString())
                .setAndroidConfig(AndroidConfig.builder().setNotification(AndroidNotification.builder()
                    .setChannelId("saqz-reminders").setTag("charge-${message.notificationId}").build()).build())
                .setApnsConfig(ApnsConfig.builder().setAps(Aps.builder().setSound("default").build()).build())
                .build())
            PushDelivery.SENT
        } catch (error: FirebaseMessagingException) {
            if (error.messagingErrorCode == MessagingErrorCode.UNREGISTERED) PushDelivery.INVALID_TOKEN else PushDelivery.RETRY
        }
    }
}
