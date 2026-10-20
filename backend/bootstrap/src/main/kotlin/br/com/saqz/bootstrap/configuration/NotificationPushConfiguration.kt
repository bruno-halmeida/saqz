package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.NotificationDeviceController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationPush
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.application.communication.*
import com.google.firebase.FirebaseApp
import br.com.saqz.sharedkernel.job.JobLog
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
    fun run() {
        JobLog.frequent("notification_push", { queue.drain(sender) }) { mapOf("processed" to it.processed, "delivered" to it.delivered, "failed" to it.failed) }
    }
}
class FirebaseNotificationPushSender(private val app: FirebaseApp) : NotificationPushSender {
    private val log = org.slf4j.LoggerFactory.getLogger(FirebaseNotificationPushSender::class.java)
    override fun send(token: String, message: NotificationPush): PushDelivery {
        // O Auth Emulator não oferece FCM: preservar a fila para diagnóstico, nunca fingir entrega.
        if (app.options.projectId == "saqz-local") return PushDelivery.RETRY
        val startToken = message.liveActivityStartToken?.takeIf { message.liveActivity != null }
        return try {
            val built = if (startToken != null) liveActivityMessage(token, startToken, message) else regularMessage(token, message)
            FirebaseMessaging.getInstance(app).send(built)
            PushDelivery.SENT
        } catch (error: FirebaseMessagingException) {
            // Só o código do FCM (nunca token nem mensagem): sem ele o log mostrava RETRY sem dizer o porquê.
            log.warn("push_fcm_error code={} startToken={}", error.messagingErrorCode, startToken != null)
            classify(error.messagingErrorCode, startToken != null)
        }
    }

    private fun regularMessage(token: String, message: NotificationPush): Message {
        // Sem `notification` de topo: no Android a mensagem é só dados, para o app montar a notificação
        // com os botões de presença mesmo em segundo plano; no iOS o alerta vai explícito no `aps`.
        val aps = Aps.builder().setSound("default")
            .setAlert(ApsAlert.builder().setTitle(message.title).setBody(message.body).build())
        if (message.offersAttendanceActions()) aps.setCategory(message.attendanceCategory())
        val android = AndroidConfig.builder().setPriority(AndroidConfig.Priority.HIGH)
        val apns = ApnsConfig.builder()
        message.windowEndsAt?.let { end ->
            // Fora da janela o push não vale mais: FCM e APNs descartam em vez de entregar atrasado.
            android.setTtl(Duration.between(Instant.now(), end).toMillis().coerceAtLeast(0))
            apns.putHeader("apns-expiration", end.epochSecond.toString())
        }
        return Message.builder().setToken(token)
            .putAllData(message.data())
            .setAndroidConfig(android.build())
            .setApnsConfig(apns.setAps(aps.build()).build())
            .build()
    }

    /** Push-to-start da Live Activity (iOS 17.2+), no formato do guia do FCM: cabeçalho `apns-priority: 10`. */
    private fun liveActivityMessage(token: String, startToken: String, message: NotificationPush): Message {
        val apns = ApnsConfig.builder().setLiveActivityToken(startToken).putHeader("apns-priority", "10")
            .setAps(Aps.builder().putAllCustomData(message.liveActivityAps(Instant.now())).build())
        message.windowEndsAt?.let { apns.putHeader("apns-expiration", it.epochSecond.toString()) }
        return Message.builder().setToken(token).setApnsConfig(apns.build()).build()
    }
}

/**
 * Token que o FCM nunca vai aceitar (app desinstalado ou token de outro projeto) é descartado; o resto
 * (cota, indisponibilidade, chave APNs, argumento) é temporário e a fila tenta de novo.
 * O FCM não diz qual token recusou: com start token, começa por ele; se era o FCM, o reenvio comum descobre.
 */
fun classify(code: MessagingErrorCode?, hasStartToken: Boolean): PushDelivery = when {
    code != MessagingErrorCode.UNREGISTERED && code != MessagingErrorCode.SENDER_ID_MISMATCH -> PushDelivery.RETRY
    hasStartToken -> PushDelivery.INVALID_START_TOKEN
    else -> PushDelivery.INVALID_TOKEN
}

/** O aviso ao organizador de primeira confirmação traz o jogo só para abrir o detalhe: sem "Confirmar" / "Não vou". */
fun NotificationPush.offersAttendanceActions() = carriesGame() && channel != "FIRST_CONFIRMATION"

/**
 * Aviso (NOTICE) com jogo é o cancelamento: sem presença para responder. Os apps 1.0.4 a 1.0.7 decidem os botões
 * (Android) e a categoria (iOS) pelo `gameId` dos dados, então ele não vai; o toque abre a central, onde o aviso tem "Abrir".
 */
fun NotificationPush.carriesGame() = gameId != null && channel != "NOTICE"

/** Categoria APNs registrada no app iOS com as ações "Confirmar" / "Não vou". */
const val ATTENDANCE_CATEGORY = "SAQZ_ATTENDANCE"

/**
 * Jogo com posições (DEB-107): o "Vou" abre o app na tela de escolher posição (ação em primeiro plano, iOS 1.0.10+).
 * Decisão do dono em 20/10: os apps iOS 1.0.6 a 1.0.9 não conhecem a categoria e mostram esse push sem botões (o toque
 * continua abrindo o jogo), para nenhum "Confirmar" responder por baixo sem a escolha da posição.
 */
const val ATTENDANCE_POSITIONS_CATEGORY = "SAQZ_ATTENDANCE_POSITIONS"

fun NotificationPush.attendanceCategory() = if (positions) ATTENDANCE_POSITIONS_CATEGORY else ATTENDANCE_CATEGORY

/** Chaves lidas pelos apps: título/corpo (Android monta a notificação), o jogo quando o push pede presença e o fim da janela das 24 h. */
fun NotificationPush.data(): Map<String, String> = buildMap {
    put("notificationId", notificationId.toString())
    put("groupId", groupId.toString())
    put("channel", channel)
    put("recipient", recipient)
    put("title", title)
    put("body", body)
    if (carriesGame()) put("gameId", gameId.toString())
    // Campo novo e opcional: os apps antigos ignoram; o Android 1.0.10+ troca o "Vou" por abrir a tela de posição.
    if (positions && offersAttendanceActions()) put("positions", "true")
    windowEndsAt?.let { put("windowEndsAt", it.toEpochMilli().toString()) }
}

/** Nome do `ActivityAttributes` no app iOS (VUL-268): o sistema só abre a Live Activity se bater. */
const val LIVE_ACTIVITY_ATTRIBUTES = "SaqzGameAttributes"

/**
 * `aps` do push-to-start da janela das 24 h. Contrato com `SaqzGameAttributes` (VUL-268): datas em
 * segundos Unix (o app converte; o `Date` padrão do Swift conta a partir de 2001) e `status` começa PENDING.
 * Em jogo com posições vai o atributo opcional `positions` (DEB-113): o "Confirmar" do cartão abre a tela de posição;
 * sem posições a chave nem vai, e o app antigo, que não a conhece, decodifica como sempre.
 */
fun NotificationPush.liveActivityAps(now: Instant): Map<String, Any> {
    val game = requireNotNull(liveActivity) { "push sem jogo da janela" }
    val attributes = buildMap<String, Any> {
        put("groupId", groupId.toString())
        put("gameId", requireNotNull(gameId) { "push sem jogo" }.toString())
        put("recipient", recipient)
        put("venue", game.venue)
        put("startsAt", game.startsAt.epochSecond)
        if (positions) put("positions", true)
    }
    return mapOf(
        "timestamp" to now.epochSecond,
        "event" to "start",
        "attributes-type" to LIVE_ACTIVITY_ATTRIBUTES,
        "attributes" to attributes,
        "content-state" to mapOf(
            "confirmed" to game.confirmed,
            "capacity" to game.capacity,
            "waitlisted" to game.waitlisted,
            "status" to "PENDING",
        ),
        "stale-date" to requireNotNull(windowEndsAt) { "push sem fim de janela" }.epochSecond,
        "alert" to mapOf("title" to title, "body" to body, "sound" to "default"),
    )
}
