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
import java.time.Duration
import java.time.Instant
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
            // Sem `notification` de topo: no Android a mensagem é só dados, para o app montar a notificação
            // com os botões de presença mesmo em segundo plano; no iOS o alerta vai explícito no `aps`.
            val aps = Aps.builder().setSound("default")
                .setAlert(ApsAlert.builder().setTitle(message.title).setBody(message.body).build())
            if (message.gameId != null) aps.setCategory(ATTENDANCE_CATEGORY)
            val android = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
            val apns = ApnsConfig.builder()
            message.windowEndsAt?.let { end ->
                // Fora da janela o push não vale mais: FCM e APNs descartam em vez de entregar atrasado.
                android.setTtl(Duration.between(Instant.now(), end).toMillis().coerceAtLeast(0))
                apns.putHeader("apns-expiration", end.epochSecond.toString())
            }
            FirebaseMessaging.getInstance(app).send(Message.builder().setToken(token)
                .putAllData(message.data())
                .setAndroidConfig(android.build())
                .setApnsConfig(apns.setAps(aps.build()).build())
                .build())
            PushDelivery.SENT
        } catch (error: FirebaseMessagingException) {
            if (error.messagingErrorCode == MessagingErrorCode.UNREGISTERED) PushDelivery.INVALID_TOKEN else PushDelivery.RETRY
        }
    }
}

/** Categoria APNs registrada no app iOS com as ações "Confirmar" / "Não vou". */
const val ATTENDANCE_CATEGORY = "SAQZ_ATTENDANCE"

/** Chaves lidas pelos apps: título/corpo (Android monta a notificação), o jogo quando o push pede presença e o fim da janela das 24 h. */
fun NotificationPush.data(): Map<String, String> = buildMap {
    put("notificationId", notificationId.toString())
    put("groupId", groupId.toString())
    put("channel", channel)
    put("recipient", recipient)
    put("title", title)
    put("body", body)
    gameId?.let { put("gameId", it.toString()) }
    windowEndsAt?.let { put("windowEndsAt", it.toEpochMilli().toString()) }
}
