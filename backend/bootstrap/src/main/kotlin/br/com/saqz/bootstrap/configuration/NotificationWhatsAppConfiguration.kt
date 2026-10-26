package br.com.saqz.bootstrap.configuration

import br.com.saqz.access.application.flags.FeatureFlags
import br.com.saqz.access.application.flags.FeatureKeys
import br.com.saqz.groups.adapter.input.http.GroupWhatsAppBindingController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationWhatsApp
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationWhatsAppGroup
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcGroupWhatsAppBindingRepository
import br.com.saqz.groups.adapter.output.whatsapp.UazapiGroupDirectory
import br.com.saqz.groups.adapter.output.whatsapp.UazapiGroupNotificationSender
import br.com.saqz.groups.adapter.output.whatsapp.UazapiNumberCheck
import br.com.saqz.groups.application.communication.WhatsAppNumberCheck
import br.com.saqz.groups.adapter.output.whatsapp.UazapiNotificationSender
import br.com.saqz.groups.application.communication.NotificationWhatsAppGroupSender
import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.communication.WhatsAppDelivery
import br.com.saqz.groups.application.whatsapp.GroupWhatsAppBindingRepository
import br.com.saqz.groups.application.whatsapp.LinkGroupWhatsApp
import br.com.saqz.groups.application.whatsapp.ManageGroupWhatsAppBinding
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import br.com.saqz.sharedkernel.job.JobLog
import com.uazapi.sdk.UazapiClient
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.net.URI
import java.time.Duration
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "saqz.notifications.whatsapp", name = ["enabled"], havingValue = "true")
class NotificationWhatsAppConfiguration {
    @Bean(destroyMethod = "close")
    fun uazapiClient(
        @Value("\${saqz.notifications.whatsapp.base-url:https://saqzapp.uazapi.com}") baseUrl: String,
        @Value("\${saqz.notifications.whatsapp.token}") token: String,
    ): UazapiClient {
        val uri = URI(baseUrl)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) { "WhatsApp requires an HTTPS endpoint" }
        require(token.isNotBlank()) { "WhatsApp instance token is required" }
        return UazapiClient.builder().baseUrl(baseUrl).token(token)
            .connectTimeout(Duration.ofSeconds(10)).readTimeout(Duration.ofSeconds(30)).build()
    }
    /** Toda mensagem individual (avisos, convites, confirmação de conta) passa pela flag `whatsapp`. */
    @Bean fun notificationWhatsAppSender(client: UazapiClient, features: ObjectProvider<FeatureFlags>): NotificationWhatsAppSender {
        val sender = UazapiNotificationSender(client)
        return NotificationWhatsAppSender { message ->
            if (whatsAppFlagOn(features)) sender.send(message) else WhatsAppDelivery.Failed.also { flagOffLog.info("whatsapp_skipped cause=flag_off") }
        }
    }
    @Bean fun whatsAppNumberCheck(client: UazapiClient): WhatsAppNumberCheck = UazapiNumberCheck(client)
    @Bean fun notificationWhatsAppQueue(dataSource: DataSource, transaction: JdbcTransactionRunner,
        links: br.com.saqz.groups.adapter.output.link.PublicAttendanceLinkFactory) =
        JdbcNotificationWhatsApp(dataSource, transaction, links)
    @Bean fun notificationWhatsAppWorker(queue: JdbcNotificationWhatsApp, sender: NotificationWhatsAppSender) =
        NotificationWhatsAppWorker(queue, sender)

    @Bean fun notificationWhatsAppGroupQueue(dataSource: DataSource, transaction: JdbcTransactionRunner,
        links: br.com.saqz.groups.adapter.output.link.PublicAttendanceLinkFactory) =
        JdbcNotificationWhatsAppGroup(dataSource, transaction, links)
    @Bean fun notificationWhatsAppGroupSender(
        client: UazapiClient,
        @Value("\${saqz.links.domain}") linksDomain: String,
        @Value("\${saqz.notifications.whatsapp.group-reminder-image-url:#{null}}") reminderImageUrl: String?,
        features: ObjectProvider<FeatureFlags>,
    ): NotificationWhatsAppGroupSender {
        val sender = UazapiGroupNotificationSender(client, groupReminderImageUrl(linksDomain, reminderImageUrl))
        return NotificationWhatsAppGroupSender { jid, messageId, text, buttons ->
            if (whatsAppFlagOn(features)) sender.send(jid, messageId, text, buttons)
            else WhatsAppDelivery.Failed.also { flagOffLog.info("whatsapp_group_skipped cause=flag_off") }
        }
    }
    @Bean fun notificationWhatsAppGroupWorker(
        queue: JdbcNotificationWhatsAppGroup,
        sender: NotificationWhatsAppGroupSender,
        directory: WhatsAppGroupDirectory,
    ) = NotificationWhatsAppGroupWorker(queue, sender, directory)

    @Bean fun groupWhatsAppBindingRepository(dataSource: DataSource): GroupWhatsAppBindingRepository =
        JdbcGroupWhatsAppBindingRepository(dataSource)

    @Bean fun whatsAppGroupDirectory(client: UazapiClient): WhatsAppGroupDirectory = UazapiGroupDirectory(client)

    @Bean fun linkGroupWhatsApp(
        transaction: JdbcTransactionRunner,
        groups: JdbcGroupReadRepository,
        bindings: GroupWhatsAppBindingRepository,
        directory: WhatsAppGroupDirectory,
    ) = LinkGroupWhatsApp(transaction, groups, bindings, directory)

    @Bean fun manageGroupWhatsAppBinding(
        groups: JdbcGroupReadRepository,
        bindings: GroupWhatsAppBindingRepository,
        directory: WhatsAppGroupDirectory,
    ) = ManageGroupWhatsAppBinding(groups, bindings, directory)

    @Bean fun groupWhatsAppBindingController(
        actors: VerifiedGroupActorResolver,
        link: LinkGroupWhatsApp,
        manage: ManageGroupWhatsAppBinding,
    ) = GroupWhatsAppBindingController(actors, link, manage)
}

private val flagOffLog = LoggerFactory.getLogger(NotificationWhatsAppConfiguration::class.java)

/**
 * A flag `whatsapp` decide o envio em tempo de execução (DEB-119): desligada, a mensagem é descartada
 * como falha, sem nova tentativa. Sem banco não há flags e vale só a propriedade que criou esta configuração.
 */
internal fun whatsAppFlagOn(features: ObjectProvider<FeatureFlags>): Boolean =
    features.getIfAvailable()?.enabledOnServer(FeatureKeys.WHATSAPP) ?: true

/**
 * Imagem de marca no topo do lembrete de presença no grupo. Propriedade ausente = imagem hospedada
 * no domínio de links (sem barra dupla, com ou sem `/` no fim do domínio); presente e vazia = sem
 * imagem; preenchida precisa ser HTTPS, porque o provider baixa a URL.
 */
internal fun groupReminderImageUrl(linksDomain: String, configured: String?): String? {
    val url = (configured ?: "${linksDomain.trimEnd('/')}/assets/whatsapp-lembrete.jpg").trim()
    if (url.isEmpty()) return null
    val uri = URI(url)
    require(uri.scheme == "https" && !uri.host.isNullOrBlank() && uri.userInfo == null) {
        "WhatsApp reminder image requires an HTTPS URL"
    }
    return url
}

class NotificationWhatsAppWorker(private val queue: JdbcNotificationWhatsApp, private val sender: NotificationWhatsAppSender) {
    @Scheduled(fixedDelayString = "\${saqz.notifications.whatsapp.delay-ms:15000}")
    fun run() {
        JobLog.frequent("notification_whatsapp", { queue.drain(sender) }) { mapOf("processed" to it.processed, "delivered" to it.delivered, "failed" to it.failed) }
    }
}

class NotificationWhatsAppGroupWorker(
    private val queue: JdbcNotificationWhatsAppGroup,
    private val sender: NotificationWhatsAppGroupSender,
    private val directory: WhatsAppGroupDirectory,
) {
    @Scheduled(fixedDelayString = "\${saqz.notifications.whatsapp.group-delay-ms:15000}")
    fun run() {
        JobLog.frequent("notification_whatsapp_group", { queue.drain(sender, directory) }) { mapOf("processed" to it.processed, "delivered" to it.delivered, "failed" to it.failed) }
    }
}
