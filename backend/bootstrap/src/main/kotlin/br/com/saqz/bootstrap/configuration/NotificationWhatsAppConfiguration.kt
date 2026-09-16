package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.GroupWhatsAppBindingController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationWhatsApp
import br.com.saqz.groups.adapter.output.jdbc.group.read.JdbcGroupReadRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.jdbc.whatsapp.JdbcGroupWhatsAppBindingRepository
import br.com.saqz.groups.adapter.output.whatsapp.UazapiGroupDirectory
import br.com.saqz.groups.adapter.output.whatsapp.UazapiNotificationSender
import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
import br.com.saqz.groups.application.whatsapp.GroupWhatsAppBindingRepository
import br.com.saqz.groups.application.whatsapp.LinkGroupWhatsApp
import br.com.saqz.groups.application.whatsapp.ManageGroupWhatsAppBinding
import br.com.saqz.groups.application.whatsapp.WhatsAppGroupDirectory
import com.uazapi.sdk.UazapiClient
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
    @Bean fun notificationWhatsAppSender(client: UazapiClient): NotificationWhatsAppSender = UazapiNotificationSender(client)
    @Bean fun notificationWhatsAppQueue(dataSource: DataSource, transaction: JdbcTransactionRunner,
        links: br.com.saqz.groups.adapter.output.link.BranchAttendanceLinkFactory) =
        JdbcNotificationWhatsApp(dataSource, transaction, links)
    @Bean fun notificationWhatsAppWorker(queue: JdbcNotificationWhatsApp, sender: NotificationWhatsAppSender) =
        NotificationWhatsAppWorker(queue, sender)

    @Bean fun groupWhatsAppBindingRepository(dataSource: DataSource): GroupWhatsAppBindingRepository =
        JdbcGroupWhatsAppBindingRepository(dataSource)

    @Bean fun whatsAppGroupDirectory(client: UazapiClient): WhatsAppGroupDirectory = UazapiGroupDirectory(client)

    @Bean fun linkGroupWhatsApp(
        groups: JdbcGroupReadRepository,
        bindings: GroupWhatsAppBindingRepository,
        directory: WhatsAppGroupDirectory,
    ) = LinkGroupWhatsApp(groups, bindings, directory)

    @Bean fun manageGroupWhatsAppBinding(
        groups: JdbcGroupReadRepository,
        bindings: GroupWhatsAppBindingRepository,
    ) = ManageGroupWhatsAppBinding(groups, bindings)

    @Bean fun groupWhatsAppBindingController(
        actors: VerifiedGroupActorResolver,
        link: LinkGroupWhatsApp,
        manage: ManageGroupWhatsAppBinding,
    ) = GroupWhatsAppBindingController(actors, link, manage)
}

class NotificationWhatsAppWorker(private val queue: JdbcNotificationWhatsApp, private val sender: NotificationWhatsAppSender) {
    @Scheduled(fixedDelayString = "\${saqz.notifications.whatsapp.delay-ms:15000}")
    fun run() = queue.drain(sender)
}
