package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.ContentModerationController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.moderation.JdbcContentModerationRepository
import br.com.saqz.groups.adapter.output.jdbc.transaction.JdbcTransactionRunner
import br.com.saqz.groups.adapter.output.mail.SmtpContentReportAlertSender
import br.com.saqz.groups.application.moderation.ContentModerationService
import br.com.saqz.groups.application.moderation.ContentReportAlertSender
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.mail.javamail.JavaMailSender
import org.springframework.scheduling.annotation.Scheduled
import javax.sql.DataSource

/** Denúncia e bloqueio (Apple 1.2). Os alertas por e-mail saem de uma fila no banco, a cada minuto. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class ContentModerationConfiguration {
    @Bean fun contentModerationRepository(dataSource: DataSource) = JdbcContentModerationRepository(dataSource)

    @Bean fun contentModerationService(transaction: JdbcTransactionRunner, repository: JdbcContentModerationRepository) =
        ContentModerationService(transaction, repository)

    @Bean fun contentModerationController(actors: VerifiedGroupActorResolver, service: ContentModerationService) =
        ContentModerationController(actors, service)

    @Bean fun contentReportAlertSender(
        sender: JavaMailSender,
        @Value("\${saqz.mail.from}") from: String,
        @Value("\${saqz.moderation.alert-recipient}") recipient: String,
    ): ContentReportAlertSender = SmtpContentReportAlertSender(sender, from, recipient)

    @Bean fun contentReportAlertDispatcher(service: ContentModerationService, sender: ContentReportAlertSender) =
        ContentReportAlertDispatcher(service, sender)
}

class ContentReportAlertDispatcher(
    private val service: ContentModerationService,
    private val sender: ContentReportAlertSender,
) {
    @Scheduled(
        initialDelayString = "\${saqz.moderation.alert-delay-ms}",
        fixedDelayString = "\${saqz.moderation.alert-delay-ms}",
    )
    fun run() {
        JobLog.frequent("content_report_alert", { service.dispatchAlerts(sender) }) { mapOf("sent" to it.sent, "failed" to it.failed) }
    }
}
