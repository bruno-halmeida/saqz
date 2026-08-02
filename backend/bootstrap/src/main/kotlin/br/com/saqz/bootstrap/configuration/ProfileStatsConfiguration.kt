package br.com.saqz.bootstrap.configuration

import br.com.saqz.groups.adapter.input.http.ProfileStatsController
import br.com.saqz.groups.adapter.input.http.VerifiedGroupActorResolver
import br.com.saqz.groups.adapter.output.jdbc.profile.JdbcProfileStatsRepository
import br.com.saqz.groups.application.profile.GetProfileStats
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Instant
import javax.sql.DataSource

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty("spring.datasource.url")
class ProfileStatsConfiguration {
    @Bean
    fun profileStatsRepository(dataSource: DataSource) = JdbcProfileStatsRepository(dataSource)

    @Bean
    fun getProfileStats(repository: JdbcProfileStatsRepository) = GetProfileStats(repository, Instant::now)

    @Bean
    fun profileStatsController(
        actorResolver: VerifiedGroupActorResolver,
        getProfileStats: GetProfileStats,
    ) = ProfileStatsController(actorResolver, getProfileStats)
}
