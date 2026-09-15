package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.communication.JdbcNotificationWhatsApp
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.whatsapp.UazapiNotificationSender
import br.com.saqz.groups.application.communication.NotificationWhatsAppSender
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
    @Bean fun notificationWhatsAppQueue(dataSource: DataSource, transaction: JdbcTransactionRunner) =
        JdbcNotificationWhatsApp(dataSource, transaction)
    @Bean fun notificationWhatsAppWorker(queue: JdbcNotificationWhatsApp, sender: NotificationWhatsAppSender) =
        NotificationWhatsAppWorker(queue, sender)
}

class NotificationWhatsAppWorker(private val queue: JdbcNotificationWhatsApp, private val sender: NotificationWhatsAppSender) {
    @Scheduled(fixedDelayString = "\${saqz.notifications.whatsapp.delay-ms:15000}")
    fun run() = queue.drain(sender)
}
