package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.output.jdbc.monthlyinvite.JdbcMonthlyInviteRepository
import br.com.saqz.groups.application.monthlyinvite.PurgeMonthlyInvitees
import br.com.saqz.sharedkernel.job.JobLog
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.Scheduled
import java.time.Clock

/** Retenção dos convidados de mensalista (CONV-010): 30 dias depois de o link vencer ou ser invalidado. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class MonthlyInviteRetentionConfiguration {
    @Bean
    fun dailyMonthlyInviteRetention(repository: JdbcMonthlyInviteRepository, clock: Clock) =
        DailyMonthlyInviteRetention(PurgeMonthlyInvitees(repository, clock))
}

class DailyMonthlyInviteRetention(private val purge: PurgeMonthlyInvitees) {
    /** 4h de São Paulo, todos os dias. */
    @Scheduled(cron = "\${saqz.monthly-invites.retention.cron:0 0 4 * * *}", zone = "America/Sao_Paulo")
    fun run() {
        JobLog.cron("monthly_invite_retention", { purge.run() }) { mapOf("deleted" to it) }
    }
}
